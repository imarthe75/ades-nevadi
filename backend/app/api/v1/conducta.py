"""
Endpoints FastAPI — Conducta: generación de PDF de actas de evaluación de conducta.

  GET /conducta/{reporte_id}/acta-pdf → Acta formal de evaluación de conducta (SB-017)

Solo lectura — no muta datos. Requiere JWT válido (ades_token).
Llamado internamente por el BFF Spring vía proxy (ConductaController).
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

from app.core.security import get_ades_user, AdesUser
from app.core.config import settings

router = APIRouter(prefix="/conducta", tags=["conducta"])
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


@router.get("/{reporte_id}/acta-pdf")
async def acta_conducta(
    reporte_id: uuid.UUID,
    user: AdesUser = Depends(get_ades_user),
):
    """Genera el Acta de Evaluación de Conducta (SB-017). Devuelve PDF.

    BOLA ALTO corregido 2026-07-16 (docs/hallazgos/
    2026-07-16_auditoria_gaps_no_revisados.md #2): este endpoint no verificaba
    absolutamente nada más allá de un JWT válido — a diferencia de
    ConductaController.java (Spring), que sí quedó corregido el mismo día para su
    propio acta-pdf, este lado FastAPI (llamado directo por el proxy de Spring, pero
    también alcanzable si algún día se expone sin pasar por el BFF) fallaba en
    cadena. Se agrega el mismo chequeo de plantel que el resto del módulo.
    """

    def _gen() -> bytes | None:
        with _sync_db_session() as session:
            row = session.execute(text("""
                SELECT
                    rc.fecha_reporte,
                    rc.tipo_falta,
                    rc.descripcion,
                    rc.medida_aplicada,
                    rc.compromiso_mejora,
                    rc.requiere_seguimiento,
                    rc.ref              AS folio_ref,
                    p.nombre,
                    p.apellido_paterno,
                    p.apellido_materno,
                    e.matricula,
                    e.plantel_id         AS plantel_id,
                    g.nombre_grupo       AS grupo_nombre,
                    rp.nombre || ' ' || rp.apellido_paterno AS reportado_por
                FROM ades_reportes_conducta rc
                JOIN ades_estudiantes e ON e.id = rc.estudiante_id
                JOIN ades_personas p ON p.id = e.persona_id
                LEFT JOIN ades_grupos g ON g.id = rc.grupo_id
                LEFT JOIN ades_personas rp ON rp.id = rc.reportado_por_id
                WHERE rc.id = :reporte_id
                LIMIT 1
            """), {"reporte_id": str(reporte_id)}).mappings().first()

            if not row:
                return None
            if not user.es_admin_global and user.plantel_id and str(row["plantel_id"]) != str(user.plantel_id):
                raise HTTPException(status_code=403, detail="El reporte no pertenece a su plantel")

            nombre_alumno = " ".join(filter(None, [
                row["apellido_paterno"], row["apellido_materno"], row["nombre"],
            ]))
            fecha_rep = row["fecha_reporte"]
            fecha_str = (
                fecha_rep.strftime("%d/%m/%Y") if hasattr(fecha_rep, "strftime") else str(fecha_rep)
            )
            folio = str(row["folio_ref"])[:8].upper()
            ctx = {
                "folio_acta": f"CON-{folio}",
                "fecha_registro": datetime.now().strftime("%d/%m/%Y"),
                "nombre_alumno": nombre_alumno,
                "matricula": row["matricula"] or "—",
                "grupo": row["grupo_nombre"] or "—",
                "tipo_falta": row["tipo_falta"],
                "fecha_reporte": fecha_str,
                "descripcion": row["descripcion"] or "—",
                "medida_aplicada": row["medida_aplicada"] or "—",
                "compromiso_mejora": row["compromiso_mejora"],
                "requiere_seguimiento": bool(row["requiere_seguimiento"]),
                "reportado_por": row["reportado_por"],
            }
            return _render_pdf("acta_conducta.html", ctx)

    pdf = await asyncio.to_thread(_gen)
    if not pdf:
        raise HTTPException(404, "Reporte de conducta no encontrado")
    return FastResponse(
        content=pdf,
        media_type="application/pdf",
        headers={"Content-Disposition": f"attachment; filename=acta_conducta_{reporte_id}.pdf"},
    )
