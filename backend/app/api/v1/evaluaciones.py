"""
/evaluaciones — Exámenes y evaluaciones formales (ordinarios, finales, extraordinarios).

  GET    /evaluaciones                          — lista por grupo/periodo/tipo
  POST   /evaluaciones                          — programar evaluación
  GET    /evaluaciones/{id}                     — detalle con estadísticas
  DELETE /evaluaciones/{id}                     — baja lógica
  GET    /evaluaciones/{id}/calificaciones       — alumnos del grupo con su calificación
  POST   /evaluaciones/{id}/calificaciones/bulk  — guardar calificaciones en bloque
"""
from __future__ import annotations

import datetime
from typing import Optional
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.core.security import get_current_user

router = APIRouter(prefix="/evaluaciones", tags=["evaluaciones"])


# ── schemas ───────────────────────────────────────────────────────────────────

class EvaluacionCreate(BaseModel):
    grupo_id: UUID
    materia_id: UUID
    periodo_evaluacion_id: UUID
    nombre_evaluacion: str
    descripcion: Optional[str] = None
    fecha_evaluacion: datetime.date
    tipo_evaluacion: str = "ORDINARIO"   # ORDINARIO | FINAL | EXTRAORDINARIO | DIAGNOSTICO
    puntaje_maximo: float = 10.0


class CalificacionItem(BaseModel):
    estudiante_id: UUID
    calificacion: float
    comentarios: Optional[str] = None


class BulkCalificaciones(BaseModel):
    calificaciones: list[CalificacionItem]


# ── endpoints ─────────────────────────────────────────────────────────────────

@router.get("")
async def listar_evaluaciones(
    grupo_id: Optional[UUID] = None,
    periodo_evaluacion_id: Optional[UUID] = None,
    tipo_evaluacion: Optional[str] = None,
    ciclo_id: Optional[UUID] = None,
    limit: int = 100,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    filters = ["e.is_active = TRUE"]
    params: dict = {"limit": limit}

    if grupo_id:
        filters.append("e.grupo_id = :grupo_id::uuid")
        params["grupo_id"] = str(grupo_id)
    if periodo_evaluacion_id:
        filters.append("e.periodo_evaluacion_id = :periodo_id::uuid")
        params["periodo_id"] = str(periodo_evaluacion_id)
    if tipo_evaluacion:
        filters.append("e.tipo_evaluacion = :tipo")
        params["tipo"] = tipo_evaluacion
    if ciclo_id:
        filters.append("pe.ciclo_escolar_id = :ciclo_id::uuid")
        params["ciclo_id"] = str(ciclo_id)

    where = " AND ".join(filters)

    rows = await db.execute(text(f"""
        SELECT
            e.id, e.nombre_evaluacion, e.descripcion,
            e.grupo_id, g.nombre_grupo,
            e.materia_id, m.nombre_materia,
            e.periodo_evaluacion_id, pe.nombre_periodo, pe.numero_periodo,
            e.fecha_evaluacion, e.tipo_evaluacion, e.puntaje_maximo,
            COUNT(ce.id)                                         AS total_calificados,
            ROUND(AVG(ce.calificacion), 2)                       AS promedio,
            COUNT(ce.id) FILTER (WHERE ce.es_acreditado = TRUE)  AS aprobados,
            COUNT(ce.id) FILTER (WHERE ce.es_acreditado = FALSE) AS reprobados
        FROM ades_evaluaciones e
        JOIN ades_grupos              g   ON g.id  = e.grupo_id
        JOIN ades_materias            m   ON m.id  = e.materia_id
        JOIN ades_periodos_evaluacion pe  ON pe.id = e.periodo_evaluacion_id
        LEFT JOIN ades_calificaciones_evaluaciones ce ON ce.evaluacion_id = e.id
        WHERE {where}
        GROUP BY e.id, g.nombre_grupo, m.nombre_materia, pe.nombre_periodo, pe.numero_periodo
        ORDER BY e.fecha_evaluacion DESC
        LIMIT :limit
    """), params)

    return rows.mappings().all()


@router.post("", status_code=201)
async def crear_evaluacion(
    body: EvaluacionCreate,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    row = await db.execute(text("""
        INSERT INTO ades_evaluaciones
            (grupo_id, materia_id, periodo_evaluacion_id, nombre_evaluacion,
             descripcion, fecha_evaluacion, tipo_evaluacion, puntaje_maximo)
        VALUES
            (:grupo_id::uuid, :materia_id::uuid, :periodo_id::uuid, :nombre,
             :descripcion, :fecha, :tipo, :puntaje)
        RETURNING id, nombre_evaluacion, tipo_evaluacion, fecha_evaluacion
    """), {
        "grupo_id":    str(body.grupo_id),
        "materia_id":  str(body.materia_id),
        "periodo_id":  str(body.periodo_evaluacion_id),
        "nombre":      body.nombre_evaluacion,
        "descripcion": body.descripcion,
        "fecha":       body.fecha_evaluacion,
        "tipo":        body.tipo_evaluacion,
        "puntaje":     body.puntaje_maximo,
    })
    await db.commit()
    return row.mappings().first()


@router.get("/{evaluacion_id}")
async def detalle_evaluacion(
    evaluacion_id: UUID,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    row = await db.execute(text("""
        SELECT
            e.*,
            g.nombre_grupo, m.nombre_materia,
            pe.nombre_periodo, pe.numero_periodo,
            COUNT(ce.id)                                         AS total_calificados,
            ROUND(AVG(ce.calificacion), 2)                       AS promedio,
            ROUND(MIN(ce.calificacion), 2)                       AS minimo,
            ROUND(MAX(ce.calificacion), 2)                       AS maximo,
            COUNT(ce.id) FILTER (WHERE ce.es_acreditado = TRUE)  AS aprobados,
            COUNT(ce.id) FILTER (WHERE ce.es_acreditado = FALSE) AS reprobados
        FROM ades_evaluaciones e
        JOIN ades_grupos              g   ON g.id  = e.grupo_id
        JOIN ades_materias            m   ON m.id  = e.materia_id
        JOIN ades_periodos_evaluacion pe  ON pe.id = e.periodo_evaluacion_id
        LEFT JOIN ades_calificaciones_evaluaciones ce ON ce.evaluacion_id = e.id
        WHERE e.id = :id::uuid AND e.is_active = TRUE
        GROUP BY e.id, g.nombre_grupo, m.nombre_materia, pe.nombre_periodo, pe.numero_periodo
    """), {"id": str(evaluacion_id)})
    result = row.mappings().first()
    if not result:
        raise HTTPException(status_code=404, detail="Evaluación no encontrada")
    return result


@router.get("/{evaluacion_id}/calificaciones")
async def calificaciones_evaluacion(
    evaluacion_id: UUID,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    """Retorna lista de todos los alumnos del grupo con su calificación (NULL si no tiene)."""
    row_eval = await db.execute(text("""
        SELECT grupo_id FROM ades_evaluaciones WHERE id = :id::uuid AND is_active = TRUE
    """), {"id": str(evaluacion_id)})
    eval_row = row_eval.fetchone()
    if not eval_row:
        raise HTTPException(status_code=404, detail="Evaluación no encontrada")

    rows = await db.execute(text("""
        SELECT
            est.id                                                    AS estudiante_id,
            p.primer_apellido || ' ' || COALESCE(p.segundo_apellido,'') || ', ' || p.nombres AS nombre_alumno,
            ce.id                                                     AS calificacion_id,
            ce.calificacion,
            ce.es_acreditado,
            ce.comentarios
        FROM ades_inscripciones i
        JOIN ades_estudiantes est ON est.id = i.estudiante_id
        JOIN ades_personas     p   ON p.id  = est.persona_id
        LEFT JOIN ades_calificaciones_evaluaciones ce
            ON ce.evaluacion_id = :eval_id::uuid AND ce.estudiante_id = est.id
        WHERE i.grupo_id = :grupo_id::uuid AND i.is_active = TRUE
        ORDER BY p.primer_apellido, p.segundo_apellido, p.nombres
    """), {"eval_id": str(evaluacion_id), "grupo_id": str(eval_row[0])})

    return rows.mappings().all()


@router.post("/{evaluacion_id}/calificaciones/bulk")
async def guardar_calificaciones_bulk(
    evaluacion_id: UUID,
    body: BulkCalificaciones,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    """Guarda o actualiza calificaciones de múltiples alumnos para una evaluación."""
    total = 0
    for item in body.calificaciones:
        if item.calificacion is None:
            continue
        await db.execute(text("""
            INSERT INTO ades_calificaciones_evaluaciones
                (evaluacion_id, estudiante_id, calificacion, comentarios)
            VALUES
                (:eval_id::uuid, :est_id::uuid, :cal, :comentarios)
            ON CONFLICT (evaluacion_id, estudiante_id)
            DO UPDATE SET
                calificacion  = EXCLUDED.calificacion,
                comentarios   = EXCLUDED.comentarios,
                fecha_modificacion = NOW()
        """), {
            "eval_id":    str(evaluacion_id),
            "est_id":     str(item.estudiante_id),
            "cal":        item.calificacion,
            "comentarios": item.comentarios,
        })
        total += 1

    await db.commit()
    return {"ok": True, "guardadas": total}


@router.delete("/{evaluacion_id}", status_code=204)
async def eliminar_evaluacion(
    evaluacion_id: UUID,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    await db.execute(text("""
        UPDATE ades_evaluaciones SET is_active = FALSE WHERE id = :id::uuid
    """), {"id": str(evaluacion_id)})
    await db.commit()
