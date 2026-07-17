"""
FASE 17 — AI Chatbot: NL→SQL (Vanna AI) + Flowise proxy con RLS por rol.

Endpoints:
  POST /chatbot/mensaje         — envía mensaje al chatbot (Flowise + Vanna)
  POST /chatbot/sql             — genera y ejecuta SQL desde lenguaje natural
  GET  /chatbot/historial       — últimas N interacciones del usuario
  POST /chatbot/entrenar        — agrega ejemplo al entrenamiento Vanna (admin)
  GET  /chatbot/status          — estado de Flowise y Vanna

Arquitectura:
  1. Angular → POST /chatbot/mensaje {pregunta, sesion_id}
  2. FastAPI aplica RLS: construye contexto (plantel_id, grupo_ids, persona_id)
  3. FastAPI llama a Flowise chatflow con la pregunta + contexto
  4. Flowise tiene un nodo "Custom Tool" que llama al endpoint /chatbot/sql
  5. Vanna AI genera SQL seguro y lo ejecuta con RLS
  6. Resultado → Flowise → respuesta en lenguaje natural → Angular

Para uso sin Flowise (modo directo), el endpoint /chatbot/sql devuelve
la respuesta completa con tabla de datos + texto explicativo.
"""

from __future__ import annotations

import logging
import re
import uuid
from typing import Any

import httpx
from fastapi import APIRouter, Depends, HTTPException, Request, status
from pydantic import BaseModel
from sqlalchemy import text

from app.core.config import settings
from app.core.chatbot_db import chatbot_readonly_session
from app.core.ratelimit import limiter, LIMITS
from app.core.security import AdesUser, get_ades_user
from app.schemas.base import AdesSchema
from app.services.llm_service import LLMService, get_llm_service

log = logging.getLogger(__name__)
router = APIRouter(prefix="/chatbot", tags=["chatbot"])


# ── Modelos ───────────────────────────────────────────────────────────────────

class MensajeIn(BaseModel):
    """Esquema de entrada para el envío de mensajes al chatbot.

    Attributes:
        pregunta: Mensaje textual en lenguaje natural provisto por el usuario.
        sesion_id: Identificador de sesión para persistencia o seguimiento en Flowise (opcional).
        contexto_extra: Parámetros y configuraciones adicionales de anulación (opcional).
    """
    pregunta: str
    sesion_id: str = ""
    contexto_extra: dict = {}


class MensajeOut(BaseModel):
    """Esquema de salida de la respuesta procesada por el chatbot.

    Attributes:
        respuesta: Texto de respuesta sintetizado en lenguaje natural.
        sql_generado: Sentencia SQL de consulta generada (si aplica).
        datos: Lista de registros mapeados como resultado de la consulta.
        sesion_id: Identificador de la sesión de conversación asociada.
        fuente: Origen del motor procesador (flowise, vanna_directo, claude).
    """
    respuesta: str
    sql_generado: str | None = None
    datos: list[dict] | None = None
    sesion_id: str
    fuente: str  # "flowise" | "vanna_directo" | "claude"


class SqlQueryIn(BaseModel):
    """Esquema de entrada para la invocación directa de consultas en lenguaje natural.

    Attributes:
        pregunta: Pregunta original del usuario.
        plantel_id: Identificador de plantel opcional para validaciones RLS.
        nivel_acceso: Nivel de acceso del rol para restringir permisos.
        persona_id: Identificador de persona (estudiante, docente) relacionado al scope.
    """
    pregunta: str
    plantel_id: str | None = None
    nivel_acceso: int = 4
    persona_id: str | None = None


class EjemploEntrenamiento(BaseModel):
    """Representa un par pregunta-SQL para el entrenamiento o refinamiento del generador Vanna.

    Attributes:
        pregunta: Enunciado de ejemplo en lenguaje natural.
        sql: Sentencia SQL correcta asociada.
        descripcion: Anotación descriptiva opcional.
    """
    pregunta: str
    sql: str
    descripcion: str = ""


# ── RLS Builder ───────────────────────────────────────────────────────────────

def _build_rls_context(user: AdesUser) -> dict:
    """Construye el contexto de seguridad que se inyecta en el prompt del chatbot.

    El LLM utiliza esta información estructurada para generar sentencias SQL que
    incorporen las cláusulas WHERE y filtros de RLS (Row-Level Security) adecuados.

    Args:
        user: Objeto del usuario autenticado actual.

    Returns:
        dict: Contexto con restricciones y pistas de construcción SQL.
    """
    ctx = {
        "nivel_acceso": user.nivel_acceso,
        "rol": user.rol,
        "plantel_id": str(user.plantel_id) if user.plantel_id else None,
        "persona_id": str(user.persona_id) if user.persona_id else None,
    }

    if user.nivel_acceso == 0:
        ctx["restriccion"] = "SIN_RESTRICCION"
        ctx["hint_sql"] = ""
    elif user.nivel_acceso in (1, 2):
        ctx["restriccion"] = "POR_PLANTEL"
        ctx["hint_sql"] = f"Agregar siempre: WHERE plantel_id = '{user.plantel_id}'" if user.plantel_id else ""
    elif user.nivel_acceso == 4:
        ctx["restriccion"] = "POR_PROFESOR"
        ctx["hint_sql"] = f"Filtrar solo grupos asignados al profesor con persona_id = '{user.persona_id}'"
    elif user.nivel_acceso == 5:
        ctx["restriccion"] = "POR_ALUMNO"
        ctx["hint_sql"] = f"Solo datos del estudiante con persona_id = '{user.persona_id}'"
    else:
        ctx["restriccion"] = "POR_PLANTEL"

    return ctx


# ── Flowise proxy ─────────────────────────────────────────────────────────────

async def _flowise_chat(pregunta: str, sesion_id: str, override_config: dict) -> str:
    """Envía un mensaje al flujo de chat (chatflow) de Flowise y retorna la respuesta.

    Args:
        pregunta: Texto de la pregunta del usuario.
        sesion_id: Identificador de la sesión de chat.
        override_config: Configuración de anulación que incluye el systemMessage con RLS.

    Returns:
        str: Respuesta de texto del chatbot procesada por Flowise.

    Raises:
        HTTPException: Si Flowise no está configurado o si la API de Flowise retorna un código de error.
    """
    if not settings.FLOWISE_CHATFLOW_ID:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Chatflow de Flowise no configurado. Ver FLOWISE_CHATFLOW_ID en .env",
        )

    headers = {}
    if settings.FLOWISE_API_KEY:
        headers["Authorization"] = f"Bearer {settings.FLOWISE_API_KEY}"

    payload = {
        "question": pregunta,
        "overrideConfig": override_config,
    }
    if sesion_id:
        payload["sessionId"] = sesion_id

    async with httpx.AsyncClient(timeout=60) as client:
        resp = await client.post(
            f"{settings.FLOWISE_URL}/api/v1/prediction/{settings.FLOWISE_CHATFLOW_ID}",
            json=payload,
            headers=headers,
        )
        if resp.status_code != 200:
            log.error("Flowise error %s: %s", resp.status_code, resp.text[:300])
            raise HTTPException(
                status_code=status.HTTP_502_BAD_GATEWAY,
                detail=f"Error en chatbot: {resp.text[:200]}",
            )
        data = resp.json()
        return data.get("text") or data.get("answer") or str(data)


# ── NL→SQL con Vanna AI ───────────────────────────────────────────────────────

# Hallazgo CRÍTICO corregido 2026-07-16 (docs/hallazgos/
# 2026-07-16_auditoria_gaps_no_revisados.md #2): el único control server-side sobre
# el SQL generado por el LLM era `sql_upper.startswith("SELECT")` — el aislamiento
# por plantel/rol se aplicaba SOLO como texto sugerido al LLM (_build_rls_context),
# nunca verificado. Un "SELECT" válido en Postgres puede seguir siendo destructivo
# (SELECT INTO crea tablas, una CTE puede envolver un DELETE/UPDATE ... RETURNING,
# una función volátil puede escribir), y `db.execute(text(sql + " LIMIT 200"))`
# concatenaba texto crudo sin bloquear ";" apilado. Mitigación en dos capas
# independientes (ninguna confía en que el LLM "se porte bien"):
#   1. Blacklist de palabras clave + rechazo de ";" (defensa superficial, se puede
#      evadir con ofuscación — no es la garantía real).
#   2. `SET TRANSACTION READ ONLY` antes de ejecutar — Postgres rechaza CUALQUIER
#      escritura a nivel de motor, sin importar cómo esté disfrazada en el SQL. Esta
#      es la garantía real; la capa 1 solo reduce ruido/costo de intentos obvios.
# RESUELTO 2026-07-17 (mig. 154 + app/core/chatbot_db.py): el filtro
# POR_PLANTEL ya no es solo un hint de prompt — _vanna_sql ejecuta el SQL
# generado en una sesión dedicada conectada como ades_app (no superusuario,
# a diferencia de ades_admin) con Row Level Security real habilitado en las
# 15 tablas de este esquema, keyed por los GUC app.rls_bypass/app.rls_plantel_id
# fijados con SET LOCAL antes de ejecutar. Un LLM que ignore hint_sql, o un
# intento de prompt injection en la pregunta del usuario, ya no puede leer
# filas de otro plantel — Postgres las descarta a nivel de motor, no de texto
# sugerido. POR_PROFESOR/POR_ALUMNO (aislamiento por persona específica,
# más granular que por plantel) queda deliberadamente fuera de esta pasada —
# ver docs/hallazgos/2026-07-16_auditoria_gaps_no_revisados.md y el
# comentario de cabecera de db/migrations/154_chatbot_rls.sql.
_SQL_PALABRAS_PROHIBIDAS = re.compile(
    r"\b(INSERT|UPDATE|DELETE|DROP|ALTER|CREATE|TRUNCATE|GRANT|REVOKE|CALL|COPY|"
    r"EXECUTE|MERGE|VACUUM|REINDEX|INTO|LISTEN|NOTIFY|LOCK|SET|RESET|DO)\b",
    re.IGNORECASE,
)


def _validar_sql_generado(sql: str) -> None:
    """Defensa superficial (capa 1) — la garantía real es READ ONLY en _vanna_sql."""
    if ";" in sql.rstrip().rstrip(";"):
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Consulta rechazada: no se permiten múltiples sentencias SQL",
        )
    if _SQL_PALABRAS_PROHIBIDAS.search(sql):
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Consulta rechazada: contiene una operación no permitida en el asistente de datos",
        )


async def _vanna_sql(pregunta: str, rls_ctx: dict, user: AdesUser, llm: LLMService) -> tuple[str, list[dict]]:
    """Genera una consulta SQL a partir de lenguaje natural usando el LLM y la ejecuta aplicando RLS.

    Consiste en:
    1. Construir un prompt del sistema que describe las tablas y las reglas RLS del usuario.
    2. Invocar al LLM para obtener la consulta SELECT SQL exclusiva.
    3. Validar y ejecutar la consulta en la base de datos limitando a un máximo de 200 filas,
       en una sesión dedicada de solo-lectura (rol ades_app) con RLS real por
       plantel (mig. 154) — no la sesión ades_admin compartida del resto de la app.

    Args:
        pregunta: Pregunta en lenguaje natural del usuario.
        rls_ctx: Contexto de seguridad de fila construido para el usuario (para el prompt del LLM).
        user: Usuario autenticado — determina el aislamiento RLS real aplicado a la ejecución.
        llm: Servicio del Large Language Model.

    Returns:
        tuple[str, list[dict]]: Una tupla con la sentencia SQL generada y la lista de registros de resultado.

    Raises:
        HTTPException: Si la query no es un SELECT o si ocurre un error durante su ejecución.
    """
    system_prompt = _build_sql_system_prompt(rls_ctx)

    response = await llm.async_complete(
        messages=[
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": f"Genera el SQL para responder: {pregunta}\n\nDevuelve SOLO el SQL, sin explicaciones ni markdown."}
        ],
        max_tokens=1024,
    )

    sql = response.choices[0].message.content.strip()
    # Limpiar posibles bloques de código
    if sql.startswith("```"):
        lines = sql.split("\n")
        sql = "\n".join(l for l in lines if not l.startswith("```")).strip()

    # Validación básica de seguridad — solo permitir SELECT
    sql_upper = sql.upper().lstrip()
    if not sql_upper.startswith("SELECT"):
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Solo se permiten consultas SELECT en el asistente de datos",
        )
    _validar_sql_generado(sql)

    # Ejecutar con límite de filas en la sesión dedicada ades_app (mig. 154):
    # SET TRANSACTION READ ONLY sigue siendo la garantía real contra escritura
    # (sin importar cómo esté disfrazada en el SQL), y ahora además la sesión
    # trae los GUC de RLS ya fijados (chatbot_readonly_session) — Postgres
    # descarta filas de otro plantel en las 15 tablas de la mig. 154 aunque el
    # LLM haya ignorado el hint_sql o el usuario haya intentado prompt injection.
    try:
        async with chatbot_readonly_session(user) as db:
            result = await db.execute(text(sql + " LIMIT 200"))
            cols = list(result.keys())
            rows = [dict(zip(cols, row)) for row in result.fetchall()]
        # Convertir tipos no serializables
        for row in rows:
            for k, v in row.items():
                if hasattr(v, 'isoformat'):
                    row[k] = v.isoformat()
                elif isinstance(v, uuid.UUID):
                    row[k] = str(v)
        return sql, rows
    except Exception as exc:
        log.error("SQL execution error: %s | SQL: %s", exc, sql[:200])
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail=f"Error al ejecutar la consulta: {str(exc)[:200]}",
        )


def _build_sql_system_prompt(rls_ctx: dict) -> str:
    """Construye el system prompt del LLM que detalla el esquema y reglas de seguridad RLS.

    Args:
        rls_ctx: Diccionario con el contexto de seguridad y restricciones RLS del usuario.

    Returns:
        str: Mensaje del sistema con la definición del esquema y limitaciones.
    """
    restriccion = rls_ctx.get("restriccion", "SIN_RESTRICCION")
    hint = rls_ctx.get("hint_sql", "")

    schema_summary = """
Tablas principales de la base de datos ADES (prefijo ades_):
- ades_personas (id, nombre, apellido_paterno, apellido_materno, curp, genero, fecha_nacimiento)
- ades_estudiantes (id, persona_id, matricula, plantel_ingreso_id)
- ades_profesores (id, persona_id, numero_empleado, especialidad, turno)
- ades_planteles (id, nombre_plantel, clave_ct)
- ades_grupos (id, nombre_grupo, grado_id, ciclo_escolar_id, plantel_id)
- ades_grados (id, numero_grado, nivel_educativo_id)
- ades_niveles_educativos (id, nombre)
- ades_inscripciones (id, estudiante_id, grupo_id, ciclo_escolar_id, is_active)
- ades_calificaciones_periodo (id, inscripcion_id, materia_plan_id, numero_periodo, calificacion_final, inasistencias, es_acreditado)
- ades_asistencias (id, clase_id, estudiante_id, presente, fecha)
- ades_clases (id, grupo_id, materia_id, fecha, hora_inicio)
- ades_materias (id, nombre, clave)
- ades_materias_plan (id, materia_id, grado_id, ciclo_escolar_id, horas_semana)
- ades_tareas (id, grupo_id, materia_id, titulo, fecha_entrega)
- ades_tareas_entregas (id, tarea_id, estudiante_id, calificacion, entregada_tiempo)
- ades_reportes_conducta (id, estudiante_id, grupo_id, tipo_incidente, descripcion, fecha)
- ades_usuarios (id, username, nivel_acceso, plantel_id)

Relaciones clave:
- estudiante → inscripcion → grupo → grado → nivel
- inscripcion → calificaciones_periodo
- clase → asistencias → estudiante

Reglas de seguridad RLS (OBLIGATORIO aplicar):
"""
    if restriccion == "SIN_RESTRICCION":
        schema_summary += "- Sin restricción: puedes consultar cualquier tabla.\n"
    elif restriccion == "POR_PLANTEL":
        schema_summary += f"- OBLIGATORIO filtrar por plantel: {hint}\n"
    elif restriccion == "POR_PROFESOR":
        schema_summary += f"- OBLIGATORIO filtrar solo por grupos del profesor: {hint}\n"
    elif restriccion == "POR_ALUMNO":
        schema_summary += f"- OBLIGATORIO filtrar solo datos del alumno: {hint}\n"

    schema_summary += """
Reglas SQL:
- Solo usar SELECT (nunca INSERT/UPDATE/DELETE)
- Usar aliases descriptivos en español para columnas
- Agregar LIMIT 100 como máximo si la query no tiene LIMIT
- Usar JOIN explícito (no subqueries implícitas cuando no sean necesarias)
- Nombres de columnas en resultado: legibles en español
"""
    return schema_summary


async def _generar_resumen(pregunta: str, sql: str, filas: list[dict], llm: LLMService) -> str:
    """Genera una explicación o síntesis en lenguaje natural sobre los registros SQL retornados.

    Args:
        pregunta: Pregunta original formulada por el usuario.
        sql: Sentencia SQL que se ejecutó.
        filas: Lista de registros obtenidos de la base de datos.
        llm: Instancia del servicio de lenguaje natural.

    Returns:
        str: Resumen conciso explicativo en español.
    """
    if not filas:
        return "No se encontraron datos para tu consulta."

    resumen_datos = str(filas[:5])
    response = await llm.async_complete(
        messages=[{
            "role": "user",
            "content": (
                f"Pregunta: {pregunta}\n"
                f"SQL ejecutado: {sql}\n"
                f"Total resultados: {len(filas)}\n"
                f"Muestra de datos: {resumen_datos}\n\n"
                "Responde la pregunta en 2-3 oraciones concisas en español, "
                "como si fueras un asistente escolar. No menciones el SQL."
            )
        }],
        max_tokens=512,
    )
    return response.choices[0].message.content.strip()


# ── Endpoints ─────────────────────────────────────────────────────────────────

@router.post("/mensaje", response_model=MensajeOut)
@limiter.limit(LIMITS["ai"])
async def enviar_mensaje(
    request: Request,
    body: MensajeIn,
    ades_user: AdesUser = Depends(get_ades_user),
    llm: LLMService = Depends(get_llm_service),
) -> MensajeOut:
    """Procesa un mensaje del usuario utilizando Flowise (si está activo) o fallback a NL→SQL directo.

    Args:
        body: Datos del mensaje y del identificador de sesión.
        ades_user: Usuario actual autenticado en ADES.
        llm: Servicio de Large Language Model.

    Returns:
        MensajeOut: Respuesta estructurada que incluye la explicación y datos tabulares (en modo directo).

    Raises:
        HTTPException: Si ocurre un error al procesar o ejecutar la consulta.
    """
    rls_ctx = _build_rls_context(ades_user)
    sesion_id = body.sesion_id or str(uuid.uuid4())

    # Si Flowise está configurado, usar como orquestador
    if settings.FLOWISE_CHATFLOW_ID:
        try:
            override = {
                "systemMessage": _build_sql_system_prompt(rls_ctx),
                **body.contexto_extra,
            }
            respuesta = await _flowise_chat(body.pregunta, sesion_id, override)
            return MensajeOut(
                respuesta=respuesta,
                sesion_id=sesion_id,
                fuente="flowise",
            )
        except HTTPException as exc:
            if exc.status_code != 503:
                raise
            # Flowise no disponible — fallback a modo directo

    # Modo directo: NL→SQL + resumen NVIDIA NIM
    try:
        sql, filas = await _vanna_sql(body.pregunta, rls_ctx, ades_user, llm)
        resumen = await _generar_resumen(body.pregunta, sql, filas, llm)
        return MensajeOut(
            respuesta=resumen,
            sql_generado=sql,
            datos=filas[:50],  # máximo 50 filas al frontend
            sesion_id=sesion_id,
            fuente="vanna_directo",
        )
    except HTTPException:
        raise
    except Exception as exc:
        log.error("Chatbot error: %s", exc)
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Error al procesar la consulta",
        )


@router.post("/sql")
@limiter.limit(LIMITS["ai"])
async def ejecutar_sql_natural(
    request: Request,
    body: SqlQueryIn,
    ades_user: AdesUser = Depends(get_ades_user),
    llm: LLMService = Depends(get_llm_service),
) -> dict:
    """Genera, valida y ejecuta una consulta SQL a partir de lenguaje natural aplicando RLS.

    Este endpoint es consumido internamente por Flowise como una herramienta personalizada (custom tool).

    Args:
        body: Estructura de entrada con la consulta de lenguaje natural y scopes.
        ades_user: Usuario actual autenticado en ADES.
        llm: Servicio de Large Language Model.

    Returns:
        dict: Datos de respuesta incluyendo el SQL generado, registros obtenidos y resumen.
    """
    rls_ctx = _build_rls_context(ades_user)
    sql, filas = await _vanna_sql(body.pregunta, rls_ctx, ades_user, llm)
    resumen = await _generar_resumen(body.pregunta, sql, filas, llm)
    return {
        "pregunta":    body.pregunta,
        "sql":         sql,
        "total_filas": len(filas),
        "datos":       filas[:100],
        "resumen":     resumen,
    }


@router.get("/status")
async def chatbot_status():
    """Verifica y devuelve el estado de conectividad e integración de los motores del Chatbot (Flowise/Vanna).

    Returns:
        dict: Estado booleano de disponibilidad de Flowise y NVIDIA NIM.
    """
    flowise_ok = False
    flowise_version = None
    try:
        async with httpx.AsyncClient(timeout=5) as client:
            resp = await client.get(f"{settings.FLOWISE_URL}/api/v1/version")
            if resp.status_code == 200:
                flowise_ok = True
                flowise_version = resp.json().get("version")
    except Exception:
        pass

    return {
        "flowise_disponible":   flowise_ok,
        "flowise_version":      flowise_version,
        "chatflow_configurado": bool(settings.FLOWISE_CHATFLOW_ID),
        "modo_fallback":        "vanna_directo (NVIDIA NIM + SQL)",
        "nvidia_nim_disponible": bool(settings.OPENAI_API_KEY),
    }
