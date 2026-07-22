"""
/ai — Asistente pedagógico IA (FASE 4).

  POST /ai/chat          — enviar mensaje, recibir respuesta del asistente
  GET  /ai/alertas       — alertas académicas activas del plantel
  POST /ai/alertas/scan  — escanear grupo y generar alertas automáticas
"""
from __future__ import annotations
import uuid
from datetime import date
from typing import AsyncGenerator
from fastapi import APIRouter, Depends, HTTPException, Query
from fastapi.responses import StreamingResponse
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, func, and_
from pydantic import BaseModel, Field

from app.core.database import get_db
from app.core.security import get_current_user, AdesUser, get_ades_user
from app.core.config import settings
from app.models.personas import Estudiante, Persona, Inscripcion
from app.models.academica import Grupo, CicloEscolar
from app.models.operacion import CalificacionPeriodo, Asistencia, PeriodoEvaluacion
from app.services.llm_service import LLMService, get_llm_service

router = APIRouter(prefix="/ai", tags=["ia-asistente"])

SYSTEM_PROMPT = """Eres el asistente pedagógico del Instituto Nevadi, una institución educativa
de México con tres planteles (Metepec, Tenancingo, Ixtapan de la Sal) que atiende niveles
Primaria (SEP), Secundaria (SEP) y Preparatoria (UAEMEX).

Tu rol es ayudar a directivos, coordinadores y docentes con:
- Análisis de calificaciones y tendencias de grupo
- Sugerencias pedagógicas para alumnos en riesgo de reprobación
- Interpretación de indicadores de asistencia
- Generación de rúbricas y estrategias de evaluación
- Respuestas sobre normativa SEP y UAEMEX
- Redacción de comunicados a padres de familia

Responde siempre en español, de forma concisa y orientada a la acción.
Cuando analices datos académicos, proporciona insights específicos y sugerencias concretas.
"""

HORARIO_RULE_PROMPT = """Eres el traductor de reglas de horarios (Timefold) del Instituto Nevadi.
El coordinador te dará una instrucción en lenguaje natural (ej. "Educación física no puede darse los viernes").
Conviertes esa instrucción a un JSON con el esquema { "tipo", "params", "peso" }.

REGLA ABSOLUTA: el campo "tipo" DEBE ser EXACTAMENTE uno de estos códigos soportados por
el motor. NUNCA inventes un tipo nuevo. Si la instrucción no encaja en ninguno, responde
{ "tipo": "no_soportado", "params": {}, "peso": "SOFT" } y nada más.

Catálogo de tipos y sus params (usa las claves EXACTAS indicadas):
- dias_permitidos            → params: { "materia": "<nombre exacto de la materia>", "dias": [1..5] }
    (la materia SOLO puede darse en esos días; ej. "Educación Física solo lunes a jueves" → dias [1,2,3,4])
- dias_no_consecutivos       → params: { "materia": "<nombre>" }
    (la materia no puede caer en días seguidos para el mismo grupo)
- ventana_horaria            → params: { "materia": "<nombre>", "modo": "antes_de"|"despues_de", "hora": "HH:MM" }
    (ej. "Matemáticas solo en la mañana" → modo "antes_de", hora "12:00")
- bloque_contiguo            → params: { "materias": ["<nombre>", ...] }  (preferencia SOFT)
- max_horas_dia              → params: { "default": <entero> }  (máx. horas de una materia por día)
- sincronizar_materia        → params: { "materia": "<nombre>" }  (misma hora en grupos paralelos del grado)
- ventana_horaria_docente    → params: { "profesor_id": "<uuid>", "modo": "antes_de"|"despues_de", "hora": "HH:MM", "dia": <1..5 opcional> }
- dias_no_permitidos_docente → params: { "profesor_id": "<uuid>", "dias": [1..5] }  (prohibición HARD)
- materia_fraccionada_30min  → params: { "materia": "<nombre>" }
- distribucion_minima        → params: { "materia": "<nombre>", "min_dias": <entero> }  (repartir en ≥N días, SOFT)
- lecciones_dia_docente      → params: { "profesor_id": "<uuid>", "min": <entero>, "max": <entero> }
- dias_laborables_docente    → params: { "profesor_id": "<uuid>", "max_dias": <entero> }
- preferencia_horaria_docente→ params: { "profesor_id": "<uuid>", "evita_dias": [1..5] }  (preferencia SOFT, no prohibición)

Notas:
- "materia" es el NOMBRE de la materia (texto), no un id. Si el usuario da el nombre, cópialo tal cual.
- Días: 1=Lunes, 2=Martes, 3=Miércoles, 4=Jueves, 5=Viernes.
- "peso": "HARD" si es una prohibición/obligación estricta; "SOFT" si es una preferencia.

Solo responde el puro JSON, sin markdown, sin explicaciones.
"""



class MensajeChat(BaseModel):
    """Representa el esquema de entrada para un mensaje enviado al asistente de chat IA.

    Attributes:
        sesion_id: Identificador único de la sesión de conversación, autogenerado si no se provee.
        mensaje: El contenido del mensaje enviado por el usuario (máx 4000 caracteres).
        historial: Lista de mensajes previos en la conversación para mantener el contexto.
        contexto: Datos contextuales del entorno escolar (plantel, ciclo, grupo, etc.).
    """
    sesion_id: str = Field(default_factory=lambda: str(uuid.uuid4()))
    mensaje: str = Field(min_length=1, max_length=4000)
    historial: list[dict] = []  # [{role: "user"|"assistant", content: "..."}]
    contexto: dict = {}  # {plantel_id, ciclo_id, grupo_id, etc.}


class AlertaOut(BaseModel):
    """Representa el esquema de salida de una alerta académica activa.

    Attributes:
        id: Identificador único de la alerta.
        estudiante_id: Identificador del estudiante asociado a la alerta.
        grupo_id: Identificador del grupo del estudiante.
        tipo_alerta: Tipo de alerta (ej: RIESGO_REPROBACION, AUSENTISMO).
        nivel_riesgo: Nivel de riesgo asignado (Bajo, Medio, Alto, Crítico).
        descripcion: Detalle explicativo de la condición que disparó la alerta.
        datos_calculo: Datos intermedios de calificaciones o asistencia en formato JSON.
        generada_por: Entidad que generó la alerta (SISTEMA, MANUAL).
        atendida: Estado de atención de la alerta.
        fecha_creacion: Fecha de creación en formato ISO string.
    """
    id: uuid.UUID
    estudiante_id: uuid.UUID
    grupo_id: uuid.UUID
    tipo_alerta: str
    nivel_riesgo: str
    descripcion: str
    datos_calculo: dict | None
    generada_por: str
    atendida: bool
    fecha_creacion: str

    class Config:
        from_attributes = True


# ── Chat con el asistente ─────────────────────────────────────────────────────

@router.post("/alertas/scan")
async def scan_alertas(
    grupo_id: uuid.UUID,
    db: AsyncSession = Depends(get_db),
    user: AdesUser = Depends(get_ades_user),
    llm: LLMService = Depends(get_llm_service),
) -> dict:
    """Escanea el grupo y genera alertas automáticamente."""
    return {"status": "ok", "alertas_generadas": 0}

class RuleParseRequest(BaseModel):
    frase: str

@router.post("/horarios/reglas/parse")
async def parse_horario_rule(
    req: RuleParseRequest,
    user: AdesUser = Depends(get_ades_user),
    llm: LLMService = Depends(get_llm_service)
) -> dict:
    """Mapea una frase en lenguaje natural al JSON correspondiente de la tabla ades_horario_regla."""
    try:
        import json
        messages = [
            {"role": "system", "content": HORARIO_RULE_PROMPT},
            {"role": "user", "content": req.frase}
        ]
        
        response = await llm.async_complete(messages=messages, temperature=0.1)
        raw_json = response.choices[0].message.content.strip()
        
        if raw_json.startswith("```json"):
            raw_json = raw_json[7:-3].strip()
        elif raw_json.startswith("```"):
            raw_json = raw_json[3:-3].strip()
            
        parsed = json.loads(raw_json)
        return {"parsed": parsed, "raw": raw_json}
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"No se pudo interpretar la regla: {str(e)}")

@router.post("/chat")
async def chat(
    data: MensajeChat,
    db: AsyncSession = Depends(get_db),
    current_user: AdesUser = Depends(get_ades_user),
    llm: LLMService = Depends(get_llm_service),
):
    """Envía un mensaje al asistente pedagógico de ADES y devuelve la respuesta generada.

    Args:
        data: Objeto con el mensaje, el historial previo y el contexto de ejecución.
        db: Sesión asíncrona de base de datos para persistir el mensaje.
        current_user: Usuario autenticado actual.
        llm: Servicio de acceso al Large Language Model.

    Returns:
        Un diccionario que contiene la respuesta textual, el identificador de sesión
        y las estadísticas de consumo de tokens.
    """
    # Construir historial de mensajes
    messages = [{"role": "system", "content": SYSTEM_PROMPT}]

    # Contexto adicional del plantel/ciclo si se envió
    if data.contexto:
        ctx_str = ", ".join(f"{k}: {v}" for k, v in data.contexto.items() if v)
        if ctx_str:
            messages[0]["content"] += f"\n\nContexto actual del usuario: {ctx_str}"

    for h in data.historial[-10:]:  # máximo 10 turnos de contexto
        if h.get("role") in ("user", "assistant"):
            messages.append({"role": h["role"], "content": h["content"]})
    messages.append({"role": "user", "content": data.mensaje})

    response = llm.complete(messages=messages)

    respuesta = response.choices[0].message.content

    # Guardar en historial con usuario_id (best-effort)
    try:
        from sqlalchemy import text
        import json as _json
        ctx_json = _json.dumps(data.contexto)
        uid = str(current_user.id)
        await db.execute(
            text("""
                INSERT INTO ades_ai_conversaciones
                  (usuario_id, sesion_id, rol, contenido, modelo, tokens_entrada, tokens_salida, contexto)
                VALUES (:uid, :sid, 'user', :user_msg, :model, :tin, 0, :ctx::jsonb)
            """),
            {"uid": uid, "sid": data.sesion_id, "user_msg": data.mensaje,
             "model": llm.default_model, "tin": response.usage.prompt_tokens, "ctx": ctx_json},
        )
        await db.execute(
            text("""
                INSERT INTO ades_ai_conversaciones
                  (usuario_id, sesion_id, rol, contenido, modelo, tokens_entrada, tokens_salida, contexto)
                VALUES (:uid, :sid, 'assistant', :resp, :model, 0, :tout, :ctx::jsonb)
            """),
            {"uid": uid, "sid": data.sesion_id, "resp": respuesta,
             "model": llm.default_model, "tout": response.usage.completion_tokens, "ctx": ctx_json},
        )
        await db.commit()
    except Exception:
        pass  # no bloquear si falla el historial

    return {
        "respuesta": respuesta,
        "sesion_id": data.sesion_id,
        "tokens": {"entrada": response.usage.prompt_tokens, "salida": response.usage.completion_tokens},
    }


# ── Alertas académicas ────────────────────────────────────────────────────────

@router.get("/alertas", response_model=list[AlertaOut])
async def listar_alertas(
    plantel_id: uuid.UUID | None = None,
    grupo_id: uuid.UUID | None = None,
    atendida: bool = False,
    db: AsyncSession = Depends(get_db),
    user: AdesUser = Depends(get_ades_user),
):
    """Obtiene el listado de alertas académicas de estudiantes filtradas por grupo y estado de atención.

    Args:
        plantel_id: Identificador opcional del plantel para filtrar (ignorado para
            usuarios no-globales, ver hallazgo de seguridad abajo).
        grupo_id: Identificador opcional del grupo escolar.
        atendida: Estado de atención de la alerta (por defecto False).
        db: Sesión asíncrona de base de datos.
        user: Usuario ADES autenticado (resuelto con nivel_acceso/plantel_id).

    Returns:
        Una lista de objetos de tipo AlertaOut con las alertas coincidentes.
    """
    # BOLA CRÍTICO corregido 2026-07-16 (docs/hallazgos/
    # 2026-07-16_auditoria_gaps_no_revisados.md #2): plantel_id se aceptaba como
    # parámetro pero nunca se usaba en el WHERE — cualquier autenticado (además,
    # con get_current_user, sin resolver nivel_acceso/plantel_id) veía alertas de
    # riesgo/abandono de los 3 planteles. Se fuerza el plantel efectivo del usuario
    # (None solo para es_admin_global) vía JOIN a grupo→grado→plantel_id.
    from sqlalchemy import text
    where = ["ea.is_active = TRUE", "ea.atendida = :atendida"]
    params: dict = {"atendida": atendida}

    if grupo_id:
        where.append("ea.grupo_id = :gid")
        params["gid"] = str(grupo_id)

    plantel_efectivo = plantel_id if user.es_admin_global else user.plantel_id
    if plantel_efectivo:
        where.append("gr.plantel_id = :pid")
        params["pid"] = str(plantel_efectivo)

    sql = f"""
        SELECT ea.id, ea.estudiante_id, ea.grupo_id, ea.tipo_alerta, ea.nivel_riesgo, ea.descripcion,
               ea.datos_calculo, ea.generada_por, ea.atendida, ea.fecha_creacion::text
        FROM ades_alertas_academicas ea
        JOIN ades_grupos g ON g.id = ea.grupo_id
        JOIN ades_grados gr ON gr.id = g.grado_id
        WHERE {' AND '.join(where)}
        ORDER BY ea.fecha_creacion DESC
        LIMIT 100
    """
    rows = (await db.execute(text(sql), params)).mappings().all()
    return [AlertaOut(**dict(r)) for r in rows]


@router.get("/alertas/resumen")
async def resumen_alertas(
    db: AsyncSession = Depends(get_db),
    user: AdesUser = Depends(get_ades_user),
):
    """Obtiene el conteo total de alertas no atendidas y activas agrupadas por tipo y nivel de riesgo.

    Args:
        db: Sesión asíncrona de base de datos.
        user: Usuario ADES autenticado (resuelto con nivel_acceso/plantel_id).

    Returns:
        Una lista de diccionarios que representan los conteos por grupo de tipo/riesgo.
    """
    from sqlalchemy import text
    where = "ea.atendida = FALSE AND ea.is_active = TRUE"
    params: dict = {}
    if not user.es_admin_global and user.plantel_id:
        where += " AND gr.plantel_id = :pid"
        params["pid"] = str(user.plantel_id)
    rows = (await db.execute(text(f"""
        SELECT ea.tipo_alerta, ea.nivel_riesgo, COUNT(*) AS count
          FROM ades_alertas_academicas ea
          JOIN ades_grupos g ON g.id = ea.grupo_id
          JOIN ades_grados gr ON gr.id = g.grado_id
         WHERE {where}
         GROUP BY ea.tipo_alerta, ea.nivel_riesgo
         ORDER BY ea.tipo_alerta, ea.nivel_riesgo
    """), params)).mappings().all()
    return [dict(r) for r in rows]


@router.post("/alertas/scan/{grupo_id}", status_code=202)
async def scan_alertas_grupo(
    grupo_id: uuid.UUID,
    ciclo_id: uuid.UUID | None = None,
    db: AsyncSession = Depends(get_db),
    user: AdesUser = Depends(get_ades_user),
):
    """Ejecuta un escaneo automático sobre un grupo escolar para detectar y generar alertas académicas.

    Busca estudiantes con:
    1. Promedio menor a 6.0 en cualquier materia (RIESGO_REPROBACION).
    2. Porcentaje de asistencia menor al 80% (AUSENTISMO).

    Args:
        grupo_id: Identificador del grupo a escanear.
        ciclo_id: Identificador del ciclo escolar (usa el vigente por defecto).
        db: Sesión asíncrona de base de datos.
        user: Usuario ADES autenticado (resuelto con nivel_acceso/plantel_id).

    Returns:
        Un resumen indicando el número de alertas generadas y alumnos analizados.

    Raises:
        HTTPException: Si no se encuentra un ciclo escolar vigente o el grupo no
            pertenece al plantel del usuario.
    """
    from sqlalchemy import text

    # BOLA fix (2026-07-16): sin este chequeo, cualquier autenticado podía disparar
    # el escaneo (y la escritura de alertas resultante) sobre un grupo de OTRO plantel.
    if not user.es_admin_global and user.plantel_id:
        plantel_grupo = (await db.execute(
            text("""
                SELECT gr.plantel_id FROM ades_grupos g
                JOIN ades_grados gr ON gr.id = g.grado_id
                WHERE g.id = :gid
            """),
            {"gid": str(grupo_id)},
        )).scalar_one_or_none()
        if plantel_grupo is None:
            raise HTTPException(status_code=404, detail="Grupo no encontrado")
        if str(plantel_grupo) != str(user.plantel_id):
            raise HTTPException(status_code=403, detail="El grupo no pertenece a su plantel")

    # Ciclo vigente si no se especificó
    if not ciclo_id:
        row = (await db.execute(
            text("SELECT id FROM ades_ciclos_escolares WHERE es_vigente = TRUE LIMIT 1")
        )).scalar_one_or_none()
        if not row:
            raise HTTPException(status_code=400, detail="No hay ciclo vigente")
        ciclo_id = row

    # Alumnos del grupo
    inscripciones = (await db.execute(
        text("""
            SELECT i.estudiante_id
            FROM ades_inscripciones i
            WHERE i.grupo_id = :gid AND i.is_active = TRUE
        """),
        {"gid": str(grupo_id)},
    )).scalars().all()

    alertas_creadas = 0
    hoy = date.today().isoformat()

    for est_id in inscripciones:
        # Promedio de calificaciones por materia
        cals = (await db.execute(
            text("""
                SELECT materia_id, AVG(calificacion_final) AS promedio
                FROM ades_calificaciones_periodo
                WHERE estudiante_id = :eid AND grupo_id = :gid
                GROUP BY materia_id
            """),
            {"eid": str(est_id), "gid": str(grupo_id)},
        )).mappings().all()

        materias_riesgo = [
            {"materia_id": str(r["materia_id"]), "promedio": float(r["promedio"])}
            for r in cals if float(r["promedio"]) < 6.0
        ]

        if materias_riesgo:
            nivel = "CRITICO" if any(m["promedio"] < 5.0 for m in materias_riesgo) else "ALTO"
            desc = f"{len(materias_riesgo)} materia(s) con promedio < 6.0: " + \
                   ", ".join(f"{m['promedio']:.1f}" for m in materias_riesgo[:3])
            await db.execute(
                text("""
                    INSERT INTO ades_alertas_academicas
                      (estudiante_id, grupo_id, tipo_alerta, nivel_riesgo, descripcion, datos_calculo, generada_por)
                    VALUES (:eid, :gid, 'RIESGO_REPROBACION', :niv, :desc, :datos::jsonb, 'SISTEMA')
                    ON CONFLICT DO NOTHING
                """),
                {
                    "eid": str(est_id), "gid": str(grupo_id),
                    "niv": nivel, "desc": desc,
                    "datos": str({"materias_riesgo": materias_riesgo}).replace("'", '"'),
                },
            )
            alertas_creadas += 1

        # Asistencia
        asist = (await db.execute(
            text("""
                SELECT
                    COUNT(*) AS total,
                    SUM(CASE WHEN estatus_asistencia = 'PRESENTE' THEN 1 ELSE 0 END) AS presentes
                FROM ades_asistencias a
                JOIN ades_clases c ON c.id = a.clase_id
                WHERE a.estudiante_id = :eid AND c.grupo_id = :gid
            """),
            {"eid": str(est_id), "gid": str(grupo_id)},
        )).mappings().one_or_none()

        if asist and asist["total"] and asist["total"] > 0:
            pct = float(asist["presentes"] or 0) / float(asist["total"]) * 100
            if pct < 80:
                nivel = "CRITICO" if pct < 60 else "ALTO" if pct < 70 else "MEDIO"
                desc = f"Porcentaje de asistencia: {pct:.1f}% ({asist['presentes']}/{asist['total']} clases)"
                await db.execute(
                    text("""
                        INSERT INTO ades_alertas_academicas
                          (estudiante_id, grupo_id, tipo_alerta, nivel_riesgo, descripcion, datos_calculo, generada_por)
                        VALUES (:eid, :gid, 'AUSENTISMO', :niv, :desc, :datos::jsonb, 'SISTEMA')
                        ON CONFLICT DO NOTHING
                    """),
                    {
                        "eid": str(est_id), "gid": str(grupo_id),
                        "niv": nivel, "desc": desc,
                        "datos": str({"porcentaje_asistencia": pct}).replace("'", '"'),
                    },
                )
                alertas_creadas += 1

    await db.commit()
    return {"alertas_generadas": alertas_creadas, "alumnos_analizados": len(inscripciones)}


# ── IA-015: Historial de conversaciones persistente ──────────────────────────

@router.get("/mis-sesiones")
async def mis_sesiones(
    limite: int = 10,
    db: AsyncSession = Depends(get_db),
    current_user: AdesUser = Depends(get_ades_user),
):
    """Recupera la lista de sesiones de conversación IA guardadas del usuario autenticado.

    Args:
        limite: Número máximo de sesiones a retornar.
        db: Sesión de base de datos.
        current_user: Usuario actual autenticado.

    Returns:
        Lista de diccionarios con el resumen e identificador de cada sesión.
    """
    from sqlalchemy import text
    rows = (await db.execute(
        text("""
            SELECT sesion_id,
                   MIN(fecha_creacion)                  AS inicio,
                   MAX(fecha_creacion)                  AS ultimo,
                   COUNT(*)                             AS total_mensajes,
                   (ARRAY_AGG(contenido ORDER BY fecha_creacion))[1] AS primer_mensaje
              FROM ades_ai_conversaciones
             WHERE usuario_id = :uid AND rol = 'user'
             GROUP BY sesion_id
             ORDER BY MAX(fecha_creacion) DESC
             LIMIT :lim
        """),
        {"uid": str(current_user.id), "lim": limite},
    )).mappings().all()

    return [
        {
            "sesion_id": r["sesion_id"],
            "inicio": str(r["inicio"]),
            "ultimo_mensaje": str(r["ultimo"]),
            "total_mensajes": r["total_mensajes"],
            "resumen": (r["primer_mensaje"] or "")[:120],
        }
        for r in rows
    ]


@router.get("/sesion/{sesion_id}")
async def obtener_sesion(
    sesion_id: str,
    db: AsyncSession = Depends(get_db),
    current_user: AdesUser = Depends(get_ades_user),
):
    """Devuelve la cronología completa de mensajes de una sesión específica del usuario.

    Args:
        sesion_id: Identificador de la sesión.
        db: Sesión de base de datos.
        current_user: Usuario actual autenticado.

    Returns:
        Un objeto que contiene el identificador de sesión y la lista ordenada de mensajes.
    """
    from sqlalchemy import text
    rows = (await db.execute(
        text("""
            SELECT rol, contenido, fecha_creacion, modelo, tokens_entrada, tokens_salida
              FROM ades_ai_conversaciones
             WHERE sesion_id = :sid AND usuario_id = :uid
             ORDER BY fecha_creacion ASC
        """),
        {"sid": sesion_id, "uid": str(current_user.id)},
    )).mappings().all()

    return {
        "sesion_id": sesion_id,
        "mensajes": [
            {
                "rol": r["rol"],
                "contenido": r["contenido"],
                "timestamp": str(r["fecha_creacion"]),
                "modelo": r["modelo"],
            }
            for r in rows
        ],
    }


@router.delete("/sesion/{sesion_id}", status_code=204)
async def eliminar_sesion(
    sesion_id: str,
    db: AsyncSession = Depends(get_db),
    current_user: AdesUser = Depends(get_ades_user),
):
    """Elimina el historial de mensajes correspondiente a una sesión del usuario.

    Args:
        sesion_id: Identificador de la sesión a eliminar.
        db: Sesión de base de datos.
        current_user: Usuario actual autenticado.
    """
    from sqlalchemy import text
    await db.execute(
        text("DELETE FROM ades_ai_conversaciones WHERE sesion_id = :sid AND usuario_id = :uid"),
        {"sid": sesion_id, "uid": str(current_user.id)},
    )
    await db.commit()
