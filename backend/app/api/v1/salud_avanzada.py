"""
Endpoints FastAPI — Salud Avanzada: generación de PDFs médicos escolares.

  GET  /salud-avanzada/certificado-deportivo/{alumno_id}  → Certificado Médico de Aptitud Física
  GET  /salud-avanzada/incidentes/{incidente_id}/acta-pdf → Acta formal de incidente médico

Solo lectura — no muta datos. Requiere JWT válido (ades_token).
Los endpoints son llamados internamente por el BFF Spring vía proxy (SaludAvanzadaController).
"""
from __future__ import annotations

import asyncio
import uuid
from datetime import datetime
from pathlib import Path

from fastapi import APIRouter, Depends, HTTPException
from fastapi.responses import Response as FastResponse
from sqlalchemy import create_engine, text
from sqlalchemy.orm import Session

from app.core.security import get_ades_user
from app.core.config import settings

router = APIRouter(prefix="/salud-avanzada", tags=["salud-avanzada"])
TEMPLATES_DIR = Path(__file__).parent.parent.parent / "templates" / "salud"


def _sync_db_session() -> Session:
    engine = create_engine(settings.DATABASE_URL_SYNC, pool_pre_ping=True)
    return Session(engine)


def _render_pdf(template_name: str, ctx: dict) -> bytes:
    from jinja2 import Environment, FileSystemLoader
    from weasyprint import HTML

    jinja_env = Environment(loader=FileSystemLoader(str(TEMPLATES_DIR)), autoescape=True)
    html_str = jinja_env.get_template(template_name).render(**ctx)
    return HTML(string=html_str, base_url=str(TEMPLATES_DIR)).write_pdf()


# ── Certificado Médico de Aptitud Física ────────────────────────────────────

@router.get("/certificado-deportivo/{alumno_id}")
async def certificado_deportivo(
    alumno_id: uuid.UUID,
    _user=Depends(get_ades_user),
):
    """Genera el Certificado Médico de Aptitud Física de un alumno. Devuelve PDF."""

    def _gen() -> bytes | None:
        with _sync_db_session() as session:
            row = session.execute(text("""
                SELECT
                    p.nombre,
                    p.apellido_paterno,
                    p.apellido_materno,
                    e.matricula,
                    g.nombre            AS grupo_nombre,
                    em.tipo_sangre,
                    em.alergias,
                    em.condiciones_cronicas,
                    ps_p.nombre || ' ' || ps_p.apellido_paterno AS especialista,
                    ps.cedula_profesional
                FROM ades_estudiantes e
                JOIN ades_personas p ON p.id = e.persona_id
                LEFT JOIN ades_grupos g ON g.id = e.grupo_id
                LEFT JOIN ades_expedientes_medicos em ON em.estudiante_id = e.id AND em.is_active = true
                LEFT JOIN ades_personal_salud ps
                    ON ps.plantel_id = e.plantel_id AND ps.is_active = true
                LEFT JOIN ades_personas ps_p ON ps_p.id = ps.persona_id
                WHERE e.id = :alumno_id
                LIMIT 1
            """), {"alumno_id": str(alumno_id)}).mappings().first()

            if not row:
                return None

            nombre_alumno = " ".join(filter(None, [
                row["apellido_paterno"],
                row["apellido_materno"],
                row["nombre"],
            ]))
            ctx = {
                "nombre_alumno":      nombre_alumno,
                "matricula":          row["matricula"] or "—",
                "grupo":              row["grupo_nombre"] or "—",
                "tipo_sangre":        row["tipo_sangre"],
                "alergias":           row["alergias"],
                "condiciones_cronicas": row["condiciones_cronicas"],
                "especialista":       row["especialista"],
                "cedula":             row["cedula_profesional"],
                "fecha_emision":      datetime.now().strftime("%d de %B de %Y"),
            }
            return _render_pdf("certificado_deportivo.html", ctx)

    pdf = await asyncio.to_thread(_gen)
    if not pdf:
        raise HTTPException(404, "Alumno no encontrado")
    return FastResponse(
        content=pdf,
        media_type="application/pdf",
        headers={"Content-Disposition": f"attachment; filename=certificado_deportivo_{alumno_id}.pdf"},
    )


# ── Acta de Incidente Médico ─────────────────────────────────────────────────

@router.get("/incidentes/{incidente_id}/acta-pdf")
async def acta_incidente(
    incidente_id: uuid.UUID,
    _user=Depends(get_ades_user),
):
    """Genera el Acta formal de un incidente médico. Devuelve PDF."""

    def _gen() -> bytes | None:
        with _sync_db_session() as session:
            row = session.execute(text("""
                SELECT
                    im.fecha_incidente,
                    im.descripcion,
                    im.tratamiento_aplicado,
                    im.requirio_traslado,
                    im.notificado_tutor,
                    im.ref              AS folio_ref,
                    p.nombre,
                    p.apellido_paterno,
                    p.apellido_materno,
                    e.matricula,
                    g.nombre            AS grupo_nombre,
                    ps_p.nombre || ' ' || ps_p.apellido_paterno AS especialista
                FROM ades_incidentes_medicos im
                JOIN ades_estudiantes e ON e.id = im.estudiante_id
                JOIN ades_personas p ON p.id = e.persona_id
                LEFT JOIN ades_grupos g ON g.id = e.grupo_id
                LEFT JOIN ades_personal_salud ps
                    ON ps.plantel_id = e.plantel_id AND ps.is_active = true
                LEFT JOIN ades_personas ps_p ON ps_p.id = ps.persona_id
                WHERE im.id = :incidente_id
                LIMIT 1
            """), {"incidente_id": str(incidente_id)}).mappings().first()

            if not row:
                return None

            nombre_alumno = " ".join(filter(None, [
                row["apellido_paterno"],
                row["apellido_materno"],
                row["nombre"],
            ]))
            fecha_inc = row["fecha_incidente"]
            fecha_str = (
                fecha_inc.strftime("%d/%m/%Y %H:%M")
                if hasattr(fecha_inc, "strftime")
                else str(fecha_inc)
            )
            folio = str(row["folio_ref"])[:8].upper()
            ctx = {
                "folio_acta":           f"INC-{folio}",
                "fecha_registro":       datetime.now().strftime("%d/%m/%Y"),
                "nombre_alumno":        nombre_alumno,
                "matricula":            row["matricula"] or "—",
                "grupo":                row["grupo_nombre"] or "—",
                "tipo_incidente":       "Emergencia" if row["requirio_traslado"] else "Incidente médico",
                "fecha_incidente":      fecha_str,
                "descripcion_detallada": row["descripcion"] or "—",
                "tratamiento_aplicado": row["tratamiento_aplicado"] or "Primeros auxilios aplicados",
                "requirio_traslado":    bool(row["requirio_traslado"]),
                "notificado_familia":   bool(row["notificado_tutor"]),
                "hospital_destino":     None,
                "testigos":             None,
                "especialista":         row["especialista"],
            }
            return _render_pdf("acta_incidente.html", ctx)

    pdf = await asyncio.to_thread(_gen)
    if not pdf:
        raise HTTPException(404, "Incidente no encontrado")
    return FastResponse(
        content=pdf,
        media_type="application/pdf",
        headers={"Content-Disposition": f"attachment; filename=acta_incidente_{incidente_id}.pdf"},
    )
