"""
FASE 18 — Carbone: gestión de plantillas y generación de reportes PDF.

Endpoints:
  GET  /carbone/templates                 — lista plantillas disponibles
  POST /carbone/templates                 — sube una plantilla (admin)
  DELETE /carbone/templates/{id}          — elimina plantilla (admin)
  POST /carbone/render/{template_id}      — genera PDF con datos
  POST /carbone/boleta/{estudiante_id}    — boleta oficial por periodo
  POST /carbone/constancia/{estudiante_id}— constancia de estudios
  POST /carbone/kardex/{estudiante_id}    — kardex académico completo
"""

from __future__ import annotations

import uuid
import logging
from typing import Literal

import httpx
from fastapi import APIRouter, Depends, HTTPException, UploadFile, File, Form, status, Request
from fastapi.responses import StreamingResponse
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, text
from sqlalchemy.orm import selectinload

from app.core.config import settings
from app.core.database import get_db
from app.core.security import AdesUser, get_ades_user
from app.core.ratelimit import limiter, LIMITS
from app.models.personas import Estudiante, Persona, Inscripcion
from app.models.academica import CicloEscolar, Grupo, Grado, Plantel, NivelEducativo, PlantelNivel
from app.models.operacion import PeriodoEvaluacion, CalificacionPeriodo
from app.models.materias import MateriaPlan, Materia
from app.schemas.base import AdesSchema

log = logging.getLogger(__name__)
router = APIRouter(prefix="/carbone", tags=["carbone"])

TipoDocumento = Literal[
    "BOLETA", "CONSTANCIA_ESTUDIOS", "CONSTANCIA_CALIFICACIONES",
    "KARDEX", "CERTIFICADO", "CONDUCTA", "GENERICO"
]


class PlantillaOut(AdesSchema):
    id: str
    nombre: str
    tipo_documento: str
    descripcion: str = ""
    extension: str
    tamano_bytes: int = 0
    fecha_creacion: str


# ── Helpers ───────────────────────────────────────────────────────────────────

async def _check_student_access(db: AsyncSession, ades_user: AdesUser, estudiante_id: uuid.UUID) -> bool:
    """
    ✅ IDOR FIX: Validar que ades_user tiene acceso al estudiante.

    Permisos por rol:
    - ADMIN_GLOBAL (plantel_id=None): Acceso a todo
    - ADMIN_PLANTEL: Solo estudiantes de su plantel
    - MAESTRO: Solo estudiantes de sus grupos
    - ESTUDIANTE: Solo a sí mismo
    - PADRE: Solo a sus hijos
    """

    # ADMIN GLOBAL: acceso a todo
    if ades_user.plantel_id is None and ades_user.rol == "ADMIN":
        return True

    # Obtener datos del estudiante
    est = await db.get(Estudiante, estudiante_id)
    if not est:
        return False

    # ADMIN DE PLANTEL: acceso si estudiante está en su plantel
    if ades_user.plantel_id is not None and ades_user.rol == "ADMIN":
        return est.plantel_id == ades_user.plantel_id

    # MAESTRO: acceso si es maestro de un grupo que contiene al estudiante
    if ades_user.rol == "MAESTRO":
        stmt = await db.execute(
            text("""
                SELECT 1 FROM ades_grupo_maestro gm
                INNER JOIN ades_alumnos a ON gm.grupo_id = a.grupo_id
                WHERE gm.maestro_id = :maestro_id
                  AND a.id = :est_id
                LIMIT 1
            """),
            {"maestro_id": str(ades_user.persona_id), "est_id": str(estudiante_id)},
        )
        return stmt.fetchone() is not None

    # ESTUDIANTE: acceso solo a sí mismo
    if ades_user.rol == "ESTUDIANTE":
        return est.persona_id == ades_user.persona_id

    # PADRE: acceso a sus hijos
    if ades_user.rol == "PADRE":
        stmt = await db.execute(
            text("""
                SELECT 1 FROM ades_tutor_relacion
                WHERE padre_id = :padre_id AND hijo_id = :hijo_id
                LIMIT 1
            """),
            {"padre_id": str(ades_user.persona_id), "hijo_id": str(est.persona_id)},
        )
        return stmt.fetchone() is not None

    return False


async def _carbone_get(path: str) -> dict | list:
    async with httpx.AsyncClient(timeout=10) as client:
        resp = await client.get(f"{settings.CARBONE_URL}{path}")
        resp.raise_for_status()
        return resp.json()


async def _carbone_render(template_id: str, data: dict) -> bytes:
    async with httpx.AsyncClient(timeout=60) as client:
        resp = await client.post(
            f"{settings.CARBONE_URL}/render/{template_id}",
            json={"data": data},
        )
        if resp.status_code != 200:
            raise HTTPException(
                status_code=status.HTTP_502_BAD_GATEWAY,
                detail=f"Carbone render error: {resp.text[:300]}",
            )
        return resp.content


def _disponible() -> None:
    """Verifica que el servicio Carbone esté configurado."""
    if not settings.CARBONE_URL:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Servicio Carbone no configurado",
        )


# ── Gestión de plantillas ─────────────────────────────────────────────────────

@router.get("/templates", response_model=list[PlantillaOut])
async def listar_plantillas(
    ades_user: AdesUser = Depends(get_ades_user),
):
    _disponible()
    data = await _carbone_get("/templates")
    return data


@router.post("/templates", response_model=PlantillaOut, status_code=201)
async def subir_plantilla(
    template: UploadFile = File(..., description="Archivo DOCX, XLSX u ODS"),
    nombre: str = Form(...),
    tipo_documento: TipoDocumento = Form("GENERICO"),
    descripcion: str = Form(""),
    ades_user: AdesUser = Depends(get_ades_user),
):
    """Sube una nueva plantilla Carbone. Solo ADMIN_GLOBAL y ADMIN_PLANTEL."""
    _disponible()
    if ades_user.nivel_acceso > 1:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Solo administradores")

    content = await template.read()
    async with httpx.AsyncClient(timeout=30) as client:
        resp = await client.post(
            f"{settings.CARBONE_URL}/templates",
            files={"template": (template.filename, content, template.content_type)},
            data={"nombre": nombre, "tipo_documento": tipo_documento, "descripcion": descripcion},
        )
        if resp.status_code not in (200, 201):
            raise HTTPException(status_code=502, detail=f"Carbone upload error: {resp.text[:300]}")
        return resp.json()


@router.delete("/templates/{template_id}", status_code=204)
async def eliminar_plantilla(
    template_id: str,
    ades_user: AdesUser = Depends(get_ades_user),
):
    if ades_user.nivel_acceso > 1:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Solo administradores")
    _disponible()
    async with httpx.AsyncClient(timeout=10) as client:
        await client.delete(f"{settings.CARBONE_URL}/templates/{template_id}")


# ── Renderizado genérico ──────────────────────────────────────────────────────

@router.post("/render/{template_id}")
async def renderizar(
    template_id: str,
    data: dict,
    ades_user: AdesUser = Depends(get_ades_user),
):
    """Renderiza cualquier plantilla con datos arbitrarios (JSON). Solo admin."""
    if ades_user.nivel_acceso > 1:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN)
    _disponible()
    pdf_bytes = await _carbone_render(template_id, data)
    return StreamingResponse(
        iter([pdf_bytes]),
        media_type="application/pdf",
        headers={"Content-Disposition": "attachment; filename=reporte.pdf"},
    )


# ── Boleta oficial ────────────────────────────────────────────────────────────

async def _build_boleta_data(estudiante_id: uuid.UUID, periodo: int | None, db: AsyncSession) -> dict:
    """Construye el payload de datos para la boleta de un estudiante."""
    est = await db.get(
        Estudiante,
        estudiante_id,
        options=[selectinload(Estudiante.persona), selectinload(Estudiante.inscripciones)],
    )
    if not est:
        raise HTTPException(404, "Estudiante no encontrado")

    ins = next((i for i in est.inscripciones if i.is_active), None)
    if not ins:
        raise HTTPException(404, "Sin inscripción activa")

    grupo = await db.get(
        Grupo, ins.grupo_id,
        options=[selectinload(Grupo.grado), selectinload(Grupo.ciclo)],
    )
    grado = grupo.grado if grupo else None
    ciclo = grupo.ciclo if grupo else None

    # Calificaciones del periodo
    q = (
        select(CalificacionPeriodo)
        .where(CalificacionPeriodo.inscripcion_id == ins.id)
        .options(selectinload(CalificacionPeriodo.materia_plan).selectinload(MateriaPlan.materia))
    )
    if periodo:
        q = q.where(CalificacionPeriodo.numero_periodo == periodo)
    cals = (await db.execute(q)).scalars().all()

    calificaciones = []
    for c in sorted(cals, key=lambda x: x.numero_periodo):
        mat = c.materia_plan.materia if c.materia_plan else None
        calificaciones.append({
            "materia":          mat.nombre if mat else "—",
            "periodo":          c.numero_periodo,
            "calificacion":     float(c.calificacion_final or 0),
            "inasistencias":    c.inasistencias or 0,
            "justificadas":     c.justificadas or 0,
            "es_acreditado":    c.es_acreditado,
        })

    p = est.persona
    return {
        "alumno": {
            "nombre_completo":  f"{p.nombre} {p.apellido_paterno} {p.apellido_materno or ''}".strip(),
            "curp":             p.curp or "",
            "matricula":        est.matricula or "",
            "grupo":            f"{grado.numero_grado}° {grupo.nombre_grupo}" if grado and grupo else "",
            "nivel":            grado.nivel_educativo_id if grado else "",
            "plantel":          "",
        },
        "ciclo":           ciclo.nombre_ciclo if ciclo else "",
        "calificaciones":  calificaciones,
        "promedio_general": round(
            sum(c["calificacion"] for c in calificaciones) / len(calificaciones), 2
        ) if calificaciones else 0,
        "fecha_impresion": __import__("datetime").date.today().strftime("%d/%m/%Y"),
        "institucion":     "Instituto Nevadi",
    }


@router.post("/boleta/{estudiante_id}")
@limiter.limit(LIMITS["write"])
async def generar_boleta(
    request: Request,
    estudiante_id: uuid.UUID,
    template_id: str,
    periodo: int | None = None,
    db: AsyncSession = Depends(get_db),
    ades_user: AdesUser = Depends(get_ades_user),
):
    """
    Genera la boleta oficial de un estudiante usando la plantilla indicada.
    Devuelve un PDF listo para descarga.

    ✅ Validación IDOR: El usuario solo puede generar boletas de estudiantes
    a los que tiene acceso (su grupo, su plantel, etc.)
    """
    # ✅ IDOR CHECK: Verificar acceso al estudiante
    if not await _check_student_access(db, ades_user, estudiante_id):
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="No tienes acceso a este estudiante",
        )

    _disponible()
    data = await _build_boleta_data(estudiante_id, periodo, db)
    pdf_bytes = await _carbone_render(template_id, data)
    nombre = data["alumno"]["nombre_completo"].replace(" ", "_")
    filename = f"Boleta_{nombre}_P{periodo or 'all'}.pdf"
    return StreamingResponse(
        iter([pdf_bytes]),
        media_type="application/pdf",
        headers={"Content-Disposition": f"attachment; filename={filename}"},
    )


@router.post("/constancia/{estudiante_id}")
@limiter.limit(LIMITS["write"])
async def generar_constancia(
    request: Request,
    estudiante_id: uuid.UUID,
    template_id: str,
    db: AsyncSession = Depends(get_db),
    ades_user: AdesUser = Depends(get_ades_user),
):
    """
    Genera una constancia de estudios o calificaciones.

    ✅ Validación IDOR: El usuario solo puede generar constancias de estudiantes
    a los que tiene acceso.
    """
    # ✅ IDOR CHECK: Verificar acceso al estudiante
    if not await _check_student_access(db, ades_user, estudiante_id):
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="No tienes acceso a este estudiante",
        )

    _disponible()
    data = await _build_boleta_data(estudiante_id, None, db)
    data["tipo_documento"] = "CONSTANCIA DE ESTUDIOS"
    pdf_bytes = await _carbone_render(template_id, data)
    nombre = data["alumno"]["nombre_completo"].replace(" ", "_")
    return StreamingResponse(
        iter([pdf_bytes]),
        media_type="application/pdf",
        headers={"Content-Disposition": f"attachment; filename=Constancia_{nombre}.pdf"},
    )


@router.get("/status")
async def carbone_status():
    """Verifica si el servicio Carbone está disponible."""
    try:
        async with httpx.AsyncClient(timeout=5) as client:
            resp = await client.get(f"{settings.CARBONE_URL}/health")
            return {"disponible": resp.status_code == 200, "detalle": resp.json()}
    except Exception as exc:
        return {"disponible": False, "detalle": str(exc)}
