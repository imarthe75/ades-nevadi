"""
Triggers de notificación automática (ntfy + n8n).

Llamadas desde los endpoints críticos para disparar push notifications
y webhooks de automatización. Todas son fire-and-forget: no bloquean
la respuesta HTTP y absorben excepciones silenciosamente.

Integra FASE 20 (ntfy push) y FASE 23 (n8n webhooks).
"""

from __future__ import annotations

import asyncio
import uuid
import logging

from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession

log = logging.getLogger(__name__)


async def _padre_usuario_id(db: AsyncSession, estudiante_id: uuid.UUID) -> uuid.UUID | None:
    """
    Resuelve el usuario_id del padre/tutor de un estudiante.
    Ruta: ades_contactos_familiares.persona_id → ades_usuarios.persona_id → ades_usuarios.id
    Devuelve el primer tutor activo o None.
    """
    row = await db.execute(
        text("""
            SELECT u.id
            FROM ades_contactos_familiares cf
            JOIN ades_usuarios u ON u.persona_id = cf.persona_id
            WHERE cf.estudiante_id = :eid
              AND u.is_active = TRUE
              AND cf.is_active = TRUE
            LIMIT 1
        """),
        {"eid": str(estudiante_id)},
    )
    result = row.fetchone()
    return result[0] if result else None


async def _nombre_alumno(db: AsyncSession, estudiante_id: uuid.UUID) -> str:
    """Devuelve 'Nombre Apellido' del alumno."""
    row = await db.execute(
        text("""
            SELECT p.nombre, p.apellido_paterno
            FROM ades_estudiantes e
            JOIN ades_personas p ON p.id = e.persona_id
            WHERE e.id = :eid
        """),
        {"eid": str(estudiante_id)},
    )
    r = row.fetchone()
    return f"{r[0]} {r[1]}" if r else "alumno"


# ── FASE 20: Calificación reprobatoria ────────────────────────────────────────

async def on_calificacion_reprobatoria(
    db: AsyncSession,
    estudiante_id: uuid.UUID,
    materia: str,
    periodo: int,
    calificacion: float,
) -> None:
    """
    Dispara push al padre cuando su hijo obtiene calificación < 6.0.
    Llamar con asyncio.create_task() desde el endpoint de calificaciones.
    """
    try:
        from app.services import push_service
        from app.services.notification_triggers import _padre_usuario_id, _nombre_alumno

        padre_id = await _padre_usuario_id(db, estudiante_id)
        if not padre_id:
            return

        nombre = await _nombre_alumno(db, estudiante_id)
        await push_service.send(
            usuario_id=padre_id,
            titulo=f"Calificación baja — {materia}",
            mensaje=f"{nombre} obtuvo {calificacion:.1f} en {materia} (Periodo {periodo}). Ingresa a ADES para ver el detalle.",
            prioridad="high",
            tags=["warning", "calificacion"],
            url="https://ades.setag.mx/padres",
        )
    except Exception as exc:
        log.debug("on_calificacion_reprobatoria failed: %s", exc)


# ── FASE 20 + 23: Asistencia baja ─────────────────────────────────────────────

async def on_asistencia_baja(
    db: AsyncSession,
    estudiante_id: uuid.UUID,
    pct_asistencia: float,
    inasistencias: int,
    plantel_id: uuid.UUID | None = None,
    grupo_id: uuid.UUID | None = None,
) -> None:
    """
    Dispara push al padre y webhook n8n cuando la asistencia de un alumno cae por debajo del 85%.
    """
    try:
        from app.services import push_service
        from app.services.notification_triggers import _padre_usuario_id, _nombre_alumno
        import httpx
        from app.core.config import settings

        padre_id = await _padre_usuario_id(db, estudiante_id)
        nombre = await _nombre_alumno(db, estudiante_id)

        # Push directo al padre
        if padre_id:
            await push_service.send(
                usuario_id=padre_id,
                titulo="Alerta de asistencia",
                mensaje=f"{nombre} tiene {pct_asistencia:.0f}% de asistencia ({inasistencias} inasistencias). Revisa su expediente en ADES.",
                prioridad="high",
                tags=["warning", "asistencia"],
                url="https://ades.setag.mx/padres",
            )

        # Webhook n8n para procesamiento adicional (puede enviar email, SMS, etc.)
        if settings.N8N_WEBHOOK_URL:
            async with httpx.AsyncClient(timeout=5) as c:
                await c.post(
                    f"{settings.N8N_WEBHOOK_URL}/webhook/ades-attendance-alert",
                    json={
                        "estudiante_id": str(estudiante_id),
                        "nombre_alumno": nombre,
                        "pct_asistencia": pct_asistencia,
                        "inasistencias": inasistencias,
                        "padre_usuario_id": str(padre_id) if padre_id else None,
                        "plantel_id": str(plantel_id) if plantel_id else None,
                        "grupo_id": str(grupo_id) if grupo_id else None,
                    },
                )
    except Exception as exc:
        log.debug("on_asistencia_baja failed: %s", exc)


# ── FASE 20 + 23: Nuevo comunicado ────────────────────────────────────────────

async def on_comunicado_publicado(
    db: AsyncSession,
    comunicado_id: uuid.UUID,
    titulo: str,
    tipo: str,
    plantel_id: uuid.UUID | None = None,
    nivel_id: uuid.UUID | None = None,
    grupo_id: uuid.UUID | None = None,
) -> None:
    """
    Dispara push batch a todos los usuarios alcanzados por el comunicado.
    El scope se determina por plantel/nivel/grupo.
    Máximo 500 usuarios para no saturar ntfy.
    """
    try:
        from app.services import push_service

        # Determinar usuarios destinatarios según el alcance del comunicado
        where_clauses = ["u.is_active = TRUE"]
        params: dict = {}

        if grupo_id:
            where_clauses.append("""
                u.id IN (
                    SELECT u2.id FROM ades_usuarios u2
                    JOIN ades_estudiantes est ON est.id = (
                        SELECT i.estudiante_id FROM ades_inscripciones i
                        WHERE i.grupo_id = :grupo_id AND i.is_active = TRUE LIMIT 1
                    )
                )
            """)
            params["grupo_id"] = str(grupo_id)
        elif nivel_id:
            where_clauses.append("u.nivel_educativo_id = :nivel_id")
            params["nivel_id"] = str(nivel_id)
        elif plantel_id:
            where_clauses.append("(u.plantel_id = :plantel_id OR u.nivel_acceso <= 2)")
            params["plantel_id"] = str(plantel_id)

        q = f"""
            SELECT DISTINCT u.id FROM ades_usuarios u
            WHERE {" AND ".join(where_clauses)}
            LIMIT 500
        """
        rows = await db.execute(text(q), params)
        usuario_ids = [uuid.UUID(str(r[0])) for r in rows]

        if not usuario_ids:
            return

        prioridad = "urgent" if tipo == "URGENTE" else "default"
        await push_service.send_batch(
            usuario_ids=usuario_ids,
            titulo=f"{'🚨 ' if tipo == 'URGENTE' else ''}Nuevo comunicado",
            mensaje=titulo,
            prioridad=prioridad,
            tags=["comunicado", tipo.lower()],
            url="https://ades.setag.mx/comunicados",
        )
        log.info("Comunicado %s: push enviado a %d usuarios", comunicado_id, len(usuario_ids))
    except Exception as exc:
        log.debug("on_comunicado_publicado failed: %s", exc)
