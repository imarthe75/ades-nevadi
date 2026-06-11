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
from app.core.security import get_current_user
from app.core.config import settings
from app.models.personas import Estudiante, Persona, Inscripcion
from app.models.academica import Grupo, CicloEscolar
from app.models.operacion import CalificacionPeriodo, Asistencia, PeriodoEvaluacion

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


class MensajeChat(BaseModel):
    sesion_id: str = Field(default_factory=lambda: str(uuid.uuid4()))
    mensaje: str = Field(min_length=1, max_length=4000)
    historial: list[dict] = []  # [{role: "user"|"assistant", content: "..."}]
    contexto: dict = {}  # {plantel_id, ciclo_id, grupo_id, etc.}


class AlertaOut(BaseModel):
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

@router.post("/chat")
async def chat(
    data: MensajeChat,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    """Envía un mensaje al asistente pedagógico y devuelve la respuesta."""
    try:
        import anthropic
    except ImportError:
        raise HTTPException(status_code=503, detail="Cliente Anthropic no disponible")

    if not settings.ANTHROPIC_API_KEY:
        raise HTTPException(status_code=503, detail="ANTHROPIC_API_KEY no configurada")

    client = anthropic.Anthropic(api_key=settings.ANTHROPIC_API_KEY)

    # Construir historial de mensajes
    messages = []
    for h in data.historial[-10:]:  # máximo 10 turnos de contexto
        if h.get("role") in ("user", "assistant"):
            messages.append({"role": h["role"], "content": h["content"]})
    messages.append({"role": "user", "content": data.mensaje})

    # Contexto adicional del plantel/ciclo si se envió
    system = SYSTEM_PROMPT
    if data.contexto:
        ctx_str = ", ".join(f"{k}: {v}" for k, v in data.contexto.items() if v)
        if ctx_str:
            system += f"\n\nContexto actual del usuario: {ctx_str}"

    response = client.messages.create(
        model="claude-sonnet-4-6",
        max_tokens=1024,
        system=system,
        messages=messages,
    )

    respuesta = response.content[0].text

    # Guardar en historial (best-effort, sin bloquear la respuesta)
    try:
        from sqlalchemy import text
        await db.execute(
            text("""
                INSERT INTO ades_ai_conversaciones (sesion_id, rol, contenido, modelo, tokens_entrada, tokens_salida, contexto)
                VALUES (:sid, 'user', :user_msg, 'claude-sonnet-4-6', :tin, 0, :ctx::jsonb)
            """),
            {"sid": data.sesion_id, "user_msg": data.mensaje, "tin": response.usage.input_tokens, "ctx": str(data.contexto).replace("'", '"')},
        )
        await db.execute(
            text("""
                INSERT INTO ades_ai_conversaciones (sesion_id, rol, contenido, modelo, tokens_entrada, tokens_salida, contexto)
                VALUES (:sid, 'assistant', :resp, 'claude-sonnet-4-6', 0, :tout, :ctx::jsonb)
            """),
            {"sid": data.sesion_id, "resp": respuesta, "tout": response.usage.output_tokens, "ctx": str(data.contexto).replace("'", '"')},
        )
        await db.commit()
    except Exception:
        pass  # no bloquear si falla el historial

    return {
        "respuesta": respuesta,
        "sesion_id": data.sesion_id,
        "tokens": {"entrada": response.usage.input_tokens, "salida": response.usage.output_tokens},
    }


# ── Alertas académicas ────────────────────────────────────────────────────────

@router.get("/alertas", response_model=list[AlertaOut])
async def listar_alertas(
    plantel_id: uuid.UUID | None = None,
    grupo_id: uuid.UUID | None = None,
    atendida: bool = False,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    from sqlalchemy import text
    where = ["is_active = TRUE", "atendida = :atendida"]
    params: dict = {"atendida": atendida}

    if grupo_id:
        where.append("grupo_id = :gid")
        params["gid"] = str(grupo_id)

    sql = f"""
        SELECT id, estudiante_id, grupo_id, tipo_alerta, nivel_riesgo, descripcion,
               datos_calculo, generada_por, atendida, fecha_creacion::text
        FROM ades_alertas_academicas
        WHERE {' AND '.join(where)}
        ORDER BY fecha_creacion DESC
        LIMIT 100
    """
    rows = (await db.execute(text(sql), params)).mappings().all()
    return [AlertaOut(**dict(r)) for r in rows]


@router.get("/alertas/resumen")
async def resumen_alertas(
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    """Conteo de alertas activas agrupadas por tipo y nivel de riesgo."""
    from sqlalchemy import text
    rows = (await db.execute(text("""
        SELECT tipo_alerta, nivel_riesgo, COUNT(*) AS count
          FROM ades_alertas_academicas
         WHERE atendida = FALSE AND is_active = TRUE
         GROUP BY tipo_alerta, nivel_riesgo
         ORDER BY tipo_alerta, nivel_riesgo
    """))).mappings().all()
    return [dict(r) for r in rows]


@router.post("/alertas/scan/{grupo_id}", status_code=202)
async def scan_alertas_grupo(
    grupo_id: uuid.UUID,
    ciclo_id: uuid.UUID | None = None,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    """
    Escanea el grupo y genera alertas automáticas para alumnos con:
    - Promedio < 6.0 en alguna materia (RIESGO_REPROBACION)
    - Promedio de asistencia < 80% (AUSENTISMO)
    """
    from sqlalchemy import text

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
