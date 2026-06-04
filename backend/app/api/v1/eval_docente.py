"""
/eval-docente — Evaluación docente 360°.
  GET  /eval-docente/criterios                  — catálogo de criterios
  GET  /eval-docente/profesor/{id}/resumen      — resumen por ciclo
  POST /eval-docente                            — nueva evaluación
  POST /eval-docente/{eval_id}/criterios        — guardar calificaciones por criterio
  PATCH /eval-docente/{eval_id}/enviar          — enviar evaluación (BORRADOR → ENVIADA)
"""
from __future__ import annotations
import uuid
from decimal import Decimal
from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, func
from pydantic import BaseModel, Field

from app.core.database import get_db
from app.core.security import get_current_user
from app.models.fase3 import ReporteAcademico  # reutilizamos imports del módulo
from app.schemas.base import AdesSchema, AdesResponse

router = APIRouter(prefix="/eval-docente", tags=["evaluacion-docente"])


class CriterioOut(BaseModel):
    id: uuid.UUID
    nombre_criterio: str
    descripcion: str | None
    categoria: str
    peso_porcentual: float
    escala_min: int
    escala_max: int

    class Config:
        from_attributes = True


class EvaluacionCreate(AdesSchema):
    profesor_id: uuid.UUID
    ciclo_escolar_id: uuid.UUID
    evaluador_id: uuid.UUID
    tipo_evaluador: str = Field(description="DIRECTOR | COORDINADOR | PAR | AUTO")
    comentarios: str | None = None


class CriterioCalificacion(AdesSchema):
    criterio_id: uuid.UUID
    calificacion: int = Field(ge=1, le=5)
    observacion: str | None = None


class EvaluacionOut(BaseModel):
    id: uuid.UUID
    profesor_id: uuid.UUID
    ciclo_escolar_id: uuid.UUID
    evaluador_id: uuid.UUID
    tipo_evaluador: str
    fecha_evaluacion: str
    calificacion_global: float | None
    comentarios: str | None
    estatus: str

    class Config:
        from_attributes = True


class ResumenEvaluacion(BaseModel):
    profesor_id: uuid.UUID
    ciclo_escolar_id: uuid.UUID
    total_evaluaciones: int
    promedio_global: float | None
    por_tipo: dict[str, float]


# ── Endpoints ─────────────────────────────────────────────────────────────────

@router.get("/criterios", response_model=list[CriterioOut])
async def listar_criterios(
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    from sqlalchemy import text
    rows = (await db.execute(
        text("SELECT id, nombre_criterio, descripcion, categoria, peso_porcentual, escala_min, escala_max FROM ades_criterios_eval_docente WHERE is_active = TRUE ORDER BY categoria, nombre_criterio")
    )).mappings().all()
    return [CriterioOut(**dict(r)) for r in rows]


@router.get("/profesor/{profesor_id}/resumen", response_model=ResumenEvaluacion)
async def resumen_docente(
    profesor_id: uuid.UUID,
    ciclo_id: uuid.UUID,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    from sqlalchemy import text
    rows = (await db.execute(
        text("""
            SELECT tipo_evaluador, AVG(calificacion_global) AS promedio, COUNT(*) AS total
            FROM ades_evaluacion_docente
            WHERE profesor_id = :pid AND ciclo_escolar_id = :cid
              AND is_active = TRUE AND estatus != 'BORRADOR'
            GROUP BY tipo_evaluador
        """),
        {"pid": str(profesor_id), "cid": str(ciclo_id)},
    )).mappings().all()

    por_tipo = {r["tipo_evaluador"]: round(float(r["promedio"]), 2) for r in rows}
    total = sum(int(r["total"]) for r in rows)
    promedio = round(sum(por_tipo.values()) / len(por_tipo), 2) if por_tipo else None

    return ResumenEvaluacion(
        profesor_id=profesor_id,
        ciclo_escolar_id=ciclo_id,
        total_evaluaciones=total,
        promedio_global=promedio,
        por_tipo=por_tipo,
    )


@router.post("", status_code=status.HTTP_201_CREATED)
async def crear_evaluacion(
    data: EvaluacionCreate,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    from sqlalchemy import text, insert
    result = await db.execute(
        text("""
            INSERT INTO ades_evaluacion_docente
              (profesor_id, ciclo_escolar_id, evaluador_id, tipo_evaluador, comentarios)
            VALUES (:pid, :cid, :eid, :tipo, :com)
            RETURNING id, profesor_id, ciclo_escolar_id, evaluador_id,
                      tipo_evaluador, fecha_evaluacion, calificacion_global,
                      comentarios, estatus
        """),
        {
            "pid": str(data.profesor_id),
            "cid": str(data.ciclo_escolar_id),
            "eid": str(data.evaluador_id),
            "tipo": data.tipo_evaluador,
            "com": data.comentarios,
        },
    )
    await db.commit()
    row = result.mappings().one()
    return dict(row)


@router.post("/{eval_id}/criterios", status_code=status.HTTP_201_CREATED)
async def guardar_criterios(
    eval_id: uuid.UUID,
    criterios: list[CriterioCalificacion],
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    """Guarda/actualiza las calificaciones por criterio y recalcula calificacion_global."""
    from sqlalchemy import text

    # Verificar que la evaluación existe y está en borrador
    eval_row = (await db.execute(
        text("SELECT id, estatus FROM ades_evaluacion_docente WHERE id = :eid"),
        {"eid": str(eval_id)},
    )).mappings().one_or_none()
    if not eval_row:
        raise HTTPException(status_code=404, detail="Evaluación no encontrada")
    if eval_row["estatus"] == "APROBADA":
        raise HTTPException(status_code=409, detail="La evaluación ya está aprobada")

    # Upsert criterios
    for c in criterios:
        await db.execute(
            text("""
                INSERT INTO ades_eval_docente_criterios (evaluacion_id, criterio_id, calificacion, observacion)
                VALUES (:eid, :cid, :cal, :obs)
                ON CONFLICT (evaluacion_id, criterio_id)
                DO UPDATE SET calificacion = EXCLUDED.calificacion, observacion = EXCLUDED.observacion
            """),
            {"eid": str(eval_id), "cid": str(c.criterio_id), "cal": c.calificacion, "obs": c.observacion},
        )

    # Recalcular promedio ponderado
    await db.execute(
        text("""
            UPDATE ades_evaluacion_docente
            SET calificacion_global = (
                SELECT ROUND(SUM(edc.calificacion * cr.peso_porcentual) / SUM(cr.peso_porcentual), 2)
                FROM ades_eval_docente_criterios edc
                JOIN ades_criterios_eval_docente cr ON cr.id = edc.criterio_id
                WHERE edc.evaluacion_id = :eid
            )
            WHERE id = :eid
        """),
        {"eid": str(eval_id)},
    )
    await db.commit()
    return {"ok": True, "eval_id": str(eval_id)}


@router.patch("/{eval_id}/enviar")
async def enviar_evaluacion(
    eval_id: uuid.UUID,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    from sqlalchemy import text
    await db.execute(
        text("UPDATE ades_evaluacion_docente SET estatus = 'ENVIADA' WHERE id = :eid AND estatus = 'BORRADOR'"),
        {"eid": str(eval_id)},
    )
    await db.commit()
    return {"ok": True}
