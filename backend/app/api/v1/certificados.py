"""
/certificados — Certificados digitales con folio único verificable.

  POST /certificados/emitir           — emite certificado + genera PDF (StreamingResponse)
  GET  /certificados/verificar/{folio} — verifica autenticidad por folio (público)
  GET  /certificados                  — historial de certificados de un alumno
"""
from __future__ import annotations

import datetime
import os
from pathlib import Path
from typing import Optional
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, status
from fastapi.responses import StreamingResponse
from pydantic import BaseModel
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.core.security import get_current_user

router = APIRouter(prefix="/certificados", tags=["certificados"])

_TEMPLATES_DIR = Path(__file__).parent.parent.parent / "templates" / "certificados"

TIPO_LABELS = {
    "ESTUDIOS":           "de Estudios",
    "CONDUCTA":           "de Buena Conducta",
    "PARTICIPACION":      "de Participación",
    "MERITO_ACADEMICO":   "de Mérito Académico",
    "ASISTENCIA_PERFECTA": "de Asistencia Perfecta",
}

MOTIVOS = {
    "ESTUDIOS":           "por haber cursado y acreditado satisfactoriamente el nivel educativo correspondiente.",
    "CONDUCTA":           "por demostrar a lo largo del ciclo escolar una conducta ejemplar y respetuosa.",
    "PARTICIPACION":      "por su destacada participación en actividades académicas y extracurriculares.",
    "MERITO_ACADEMICO":   "por haber obtenido un rendimiento académico sobresaliente durante el ciclo escolar.",
    "ASISTENCIA_PERFECTA": "por haber asistido de manera puntual y constante durante todo el ciclo escolar.",
}


# ── schemas ───────────────────────────────────────────────────────────────────

class CertificadoCreate(BaseModel):
    estudiante_id: UUID
    ciclo_escolar_id: UUID
    tipo_certificado: str = "ESTUDIOS"
    grado_completado: Optional[str] = None
    promedio_final: Optional[float] = None
    fecha_vencimiento: Optional[datetime.date] = None
    datos_adicionales: Optional[dict] = None


# ── helper ────────────────────────────────────────────────────────────────────

async def _get_datos_alumno(db: AsyncSession, estudiante_id: str, ciclo_id: str) -> dict:
    row = await db.execute(text("""
        SELECT
            p.nombres || ' ' || p.primer_apellido
                || COALESCE(' ' || p.segundo_apellido, '') AS nombre_alumno,
            pl.nombre_plantel,
            ne.nombre_nivel,
            ce.nombre_ciclo,
            ROUND(AVG(cp.calificacion), 2) AS promedio_calculado
        FROM ades_estudiantes est
        JOIN ades_personas p ON p.id = est.persona_id
        LEFT JOIN ades_inscripciones i
            ON i.estudiante_id = est.id AND i.ciclo_escolar_id = :ciclo_id::uuid
        LEFT JOIN ades_grupos g ON g.id = i.grupo_id
        LEFT JOIN ades_grados gr ON gr.id = g.grado_id
        LEFT JOIN ades_planteles pl ON pl.id = gr.plantel_id
        LEFT JOIN ades_niveles_educativos ne ON ne.id = gr.nivel_educativo_id
        LEFT JOIN ades_ciclos_escolares ce ON ce.id = :ciclo_id::uuid
        LEFT JOIN ades_calificaciones_periodo cp
            ON cp.inscripcion_id = i.id AND cp.calificacion IS NOT NULL
        WHERE est.id = :est_id::uuid
        GROUP BY p.nombres, p.primer_apellido, p.segundo_apellido,
                 pl.nombre_plantel, ne.nombre_nivel, ce.nombre_ciclo
    """), {"est_id": estudiante_id, "ciclo_id": ciclo_id})
    r = row.mappings().first()
    return dict(r) if r else {}


def _render_pdf(context: dict) -> bytes:
    from jinja2 import Environment, FileSystemLoader
    from weasyprint import HTML

    env = Environment(loader=FileSystemLoader(str(_TEMPLATES_DIR)))
    template = env.get_template("certificado.html")
    html_str = template.render(**context)
    return HTML(string=html_str, base_url=str(_TEMPLATES_DIR)).write_pdf()


# ── endpoints ─────────────────────────────────────────────────────────────────

@router.get("")
async def listar_certificados(
    estudiante_id: Optional[UUID] = None,
    tipo_certificado: Optional[str] = None,
    limit: int = 50,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    filters = ["c.is_active = TRUE"]
    params: dict = {"limit": limit}

    if estudiante_id:
        filters.append("c.estudiante_id = :est_id::uuid")
        params["est_id"] = str(estudiante_id)
    if tipo_certificado:
        filters.append("c.tipo_certificado = :tipo")
        params["tipo"] = tipo_certificado

    where = " AND ".join(filters)
    rows = await db.execute(text(f"""
        SELECT
            c.id, c.folio, c.tipo_certificado, c.nivel_educativo,
            c.grado_completado, c.promedio_final,
            c.fecha_emision, c.fecha_vencimiento, c.vigente,
            p.nombres || ' ' || p.primer_apellido AS nombre_alumno,
            ce.nombre_ciclo
        FROM ades_certificados c
        JOIN ades_estudiantes  est ON est.id = c.estudiante_id
        JOIN ades_personas     p   ON p.id   = est.persona_id
        JOIN ades_ciclos_escolares ce ON ce.id = c.ciclo_escolar_id
        WHERE {where}
        ORDER BY c.fecha_emision DESC
        LIMIT :limit
    """), params)
    return rows.mappings().all()


@router.post("/emitir")
async def emitir_certificado(
    body: CertificadoCreate,
    db: AsyncSession = Depends(get_db),
    current_user: dict = Depends(get_current_user),
):
    """Emite un certificado digital y retorna el PDF generado con WeasyPrint."""
    # Obtener datos del alumno
    datos = await _get_datos_alumno(db, str(body.estudiante_id), str(body.ciclo_escolar_id))
    if not datos.get("nombre_alumno"):
        raise HTTPException(status_code=404, detail="Alumno no encontrado")

    # Resolver usuario emisor
    jwt_sub = current_user.get("sub", "")
    uid_row = await db.execute(
        text("SELECT id FROM ades_usuarios WHERE oidc_sub = :sub"),
        {"sub": jwt_sub}
    )
    uid = uid_row.scalar()

    # Insertar registro del certificado
    promedio = body.promedio_final or datos.get("promedio_calculado")
    cert_row = await db.execute(text("""
        INSERT INTO ades_certificados
            (estudiante_id, ciclo_escolar_id, tipo_certificado,
             nivel_educativo, grado_completado, promedio_final,
             fecha_vencimiento, datos_adicionales, emitido_por_id)
        VALUES
            (:est_id::uuid, :ciclo_id::uuid, :tipo,
             :nivel, :grado, :promedio,
             :fecha_venc, :datos::jsonb, :emitido_por)
        RETURNING id, folio, fecha_emision
    """), {
        "est_id":      str(body.estudiante_id),
        "ciclo_id":    str(body.ciclo_escolar_id),
        "tipo":        body.tipo_certificado,
        "nivel":       datos.get("nombre_nivel", ""),
        "grado":       body.grado_completado,
        "promedio":    promedio,
        "fecha_venc":  body.fecha_vencimiento,
        "datos":       str(body.datos_adicionales) if body.datos_adicionales else None,
        "emitido_por": str(uid) if uid else None,
    })
    await db.commit()
    cert = cert_row.mappings().first()

    # Preparar contexto para el template
    context = {
        "nombre_alumno":   datos["nombre_alumno"],
        "plantel":         datos.get("nombre_plantel", "Instituto Nevadi"),
        "nivel_educativo": datos.get("nombre_nivel", body.tipo_certificado),
        "ciclo":           datos.get("nombre_ciclo", ""),
        "grado_completado": body.grado_completado or "",
        "promedio_final":  promedio,
        "tipo_label":      TIPO_LABELS.get(body.tipo_certificado, body.tipo_certificado),
        "motivo":          MOTIVOS.get(body.tipo_certificado, ""),
        "folio":           cert["folio"],
        "fecha_emision":   cert["fecha_emision"].strftime("%d de %B de %Y") if cert["fecha_emision"] else "",
    }

    pdf_bytes = _render_pdf(context)

    return StreamingResponse(
        iter([pdf_bytes]),
        media_type="application/pdf",
        headers={
            "Content-Disposition": f'attachment; filename="certificado_{cert["folio"]}.pdf"',
            "X-Folio": cert["folio"],
            "X-Certificado-Id": str(cert["id"]) if cert["id"] else "",
        },
    )


@router.get("/verificar/{folio}")
async def verificar_certificado(
    folio: str,
    db: AsyncSession = Depends(get_db),
):
    """Endpoint público — verifica la autenticidad de un certificado por folio."""
    row = await db.execute(text("""
        SELECT
            c.folio, c.tipo_certificado, c.nivel_educativo,
            c.grado_completado, c.promedio_final,
            c.fecha_emision, c.fecha_vencimiento, c.vigente,
            p.nombres || ' ' || p.primer_apellido AS nombre_alumno,
            ce.nombre_ciclo,
            u.nombre_usuario AS emitido_por
        FROM ades_certificados c
        JOIN ades_estudiantes  est ON est.id = c.estudiante_id
        JOIN ades_personas     p   ON p.id   = est.persona_id
        JOIN ades_ciclos_escolares ce ON ce.id = c.ciclo_escolar_id
        LEFT JOIN ades_usuarios u ON u.id = c.emitido_por_id
        WHERE c.folio = :folio AND c.is_active = TRUE
    """), {"folio": folio.upper()})

    result = row.mappings().first()
    if not result:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Certificado con folio '{folio}' no encontrado o inválido."
        )

    return {
        **dict(result),
        "autenticidad": "VERIFICADO",
        "mensaje": "Este certificado es auténtico y fue emitido por Instituto Nevadi.",
    }
