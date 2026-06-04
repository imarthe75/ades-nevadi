"""
/learning-paths — Rutas de refuerzo adaptativas para alumnos en riesgo.

  GET  /learning-paths                    — listado de rutas disponibles
  POST /learning-paths                    — crear nueva ruta (admin/coord)
  GET  /learning-paths/{path_id}          — detalle + recursos
  GET  /learning-paths/{path_id}/recursos — recursos del path
  POST /learning-paths/{path_id}/recursos — agregar recurso

  GET  /learning-paths/asignaciones              — asignaciones activas (con filtros)
  POST /learning-paths/{path_id}/asignar         — asignar a alumno
  POST /learning-paths/asignaciones/{asig_id}/progreso/{recurso_id} — marcar recurso completado
  GET  /learning-paths/asignaciones/{asig_id}    — detalle de asignación con progreso
  POST /learning-paths/asignar-automatico/{grupo_id} — asignación automática por alertas
"""
from __future__ import annotations
import uuid
from datetime import datetime, timezone
from typing import Literal

from fastapi import APIRouter, Depends, HTTPException, Query
from pydantic import BaseModel, Field
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, func
from sqlalchemy.orm import selectinload

from app.core.database import get_db
from app.core.security import get_current_user

router = APIRouter(prefix="/learning-paths", tags=["learning-paths"])


# ── Schemas ───────────────────────────────────────────────────────────────────

class RecursoIn(BaseModel):
    orden: int = 1
    tipo: Literal["VIDEO", "PDF", "EJERCICIO", "ENLACE", "QUIZ"]
    titulo: str = Field(min_length=1, max_length=200)
    descripcion: str | None = None
    url_recurso: str | None = None
    duracion_min: int | None = None
    obligatorio: bool = True


class RecursoOut(BaseModel):
    id: uuid.UUID
    orden: int
    tipo: str
    titulo: str
    descripcion: str | None
    url_recurso: str | None
    duracion_min: int | None
    obligatorio: bool
    is_active: bool

    class Config:
        from_attributes = True


class PathIn(BaseModel):
    nombre: str = Field(min_length=1, max_length=200)
    descripcion: str | None = None
    nivel_educativo_id: uuid.UUID | None = None
    materia_id: uuid.UUID | None = None
    criterio_activacion: Literal["MANUAL", "REPROBACION", "AUSENTISMO", "RIESGO_ALTO"] = "MANUAL"
    umbral_activacion: float | None = None


class PathOut(BaseModel):
    id: uuid.UUID
    nombre: str
    descripcion: str | None
    criterio_activacion: str
    umbral_activacion: float | None
    is_active: bool
    total_recursos: int = 0

    class Config:
        from_attributes = True


class PathDetalle(PathOut):
    recursos: list[RecursoOut] = []


class AsignacionIn(BaseModel):
    estudiante_id: uuid.UUID
    motivo: Literal["MANUAL", "AUTO_REPROBACION", "AUTO_AUSENTISMO", "AUTO_RIESGO"] = "MANUAL"


class ProgresoIn(BaseModel):
    tiempo_min: int | None = None
    calificacion: float | None = None


class AsignacionOut(BaseModel):
    id: uuid.UUID
    path_id: uuid.UUID
    path_nombre: str
    estudiante_id: uuid.UUID
    motivo: str
    estatus: str
    pct_completado: float
    fccreacion: datetime

    class Config:
        from_attributes = True


class AsignacionDetalle(AsignacionOut):
    recursos_completados: int = 0
    total_recursos: int = 0
    progreso: list[dict] = []


# ── Helper DB ─────────────────────────────────────────────────────────────────

async def _get_path_or_404(path_id: uuid.UUID, db: AsyncSession):
    from sqlalchemy import text
    row = (await db.execute(
        text("SELECT id, nombre, descripcion, criterio_activacion, umbral_activacion, is_active FROM ades_learning_paths WHERE id = :id"),
        {"id": path_id},
    )).fetchone()
    if not row:
        raise HTTPException(404, "Learning path no encontrado")
    return row


# ── Learning Paths ────────────────────────────────────────────────────────────

@router.get("", response_model=list[PathOut])
async def listar_paths(
    activos: bool = True,
    criterio: str | None = None,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    from sqlalchemy import text
    sql = """
        SELECT lp.id, lp.nombre, lp.descripcion, lp.criterio_activacion,
               lp.umbral_activacion, lp.is_active,
               COUNT(r.id) AS total_recursos
          FROM ades_learning_paths lp
          LEFT JOIN ades_lp_recursos r ON r.path_id = lp.id AND r.is_active = TRUE
         WHERE (:activos IS NULL OR lp.is_active = :activos)
           AND (:criterio IS NULL OR lp.criterio_activacion = :criterio)
         GROUP BY lp.id
         ORDER BY lp.nombre
    """
    rows = (await db.execute(text(sql), {"activos": activos, "criterio": criterio})).fetchall()
    return [PathOut(
        id=r.id, nombre=r.nombre, descripcion=r.descripcion,
        criterio_activacion=r.criterio_activacion, umbral_activacion=r.umbral_activacion,
        is_active=r.is_active, total_recursos=r.total_recursos,
    ) for r in rows]


@router.post("", response_model=PathOut, status_code=201)
async def crear_path(
    data: PathIn,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    from sqlalchemy import text
    row = (await db.execute(
        text("""
            INSERT INTO ades_learning_paths
                (nombre, descripcion, nivel_educativo_id, materia_id,
                 criterio_activacion, umbral_activacion, is_active)
            VALUES (:nombre, :desc, :nivel_id, :mat_id, :criterio, :umbral, TRUE)
            RETURNING id, nombre, descripcion, criterio_activacion, umbral_activacion, is_active
        """),
        {
            "nombre": data.nombre, "desc": data.descripcion,
            "nivel_id": data.nivel_educativo_id, "mat_id": data.materia_id,
            "criterio": data.criterio_activacion, "umbral": data.umbral_activacion,
        },
    )).fetchone()
    await db.commit()
    return PathOut(id=row.id, nombre=row.nombre, descripcion=row.descripcion,
                   criterio_activacion=row.criterio_activacion, umbral_activacion=row.umbral_activacion,
                   is_active=row.is_active, total_recursos=0)


@router.get("/{path_id}", response_model=PathDetalle)
async def detalle_path(
    path_id: uuid.UUID,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    from sqlalchemy import text
    path = await _get_path_or_404(path_id, db)
    recursos = (await db.execute(
        text("""
            SELECT id, orden, tipo, titulo, descripcion, url_recurso, duracion_min, obligatorio, is_active
              FROM ades_lp_recursos WHERE path_id = :pid AND is_active = TRUE ORDER BY orden
        """),
        {"pid": path_id},
    )).fetchall()
    return PathDetalle(
        id=path.id, nombre=path.nombre, descripcion=path.descripcion,
        criterio_activacion=path.criterio_activacion, umbral_activacion=path.umbral_activacion,
        is_active=path.is_active,
        total_recursos=len(recursos),
        recursos=[RecursoOut(id=r.id, orden=r.orden, tipo=r.tipo, titulo=r.titulo,
                             descripcion=r.descripcion, url_recurso=r.url_recurso,
                             duracion_min=r.duracion_min, obligatorio=r.obligatorio,
                             is_active=r.is_active) for r in recursos],
    )


@router.post("/{path_id}/recursos", response_model=RecursoOut, status_code=201)
async def agregar_recurso(
    path_id: uuid.UUID,
    data: RecursoIn,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    from sqlalchemy import text
    await _get_path_or_404(path_id, db)
    row = (await db.execute(
        text("""
            INSERT INTO ades_lp_recursos
                (path_id, orden, tipo, titulo, descripcion, url_recurso, duracion_min, obligatorio)
            VALUES (:pid, :orden, :tipo, :titulo, :desc, :url, :dur, :oblig)
            RETURNING id, orden, tipo, titulo, descripcion, url_recurso, duracion_min, obligatorio, is_active
        """),
        {
            "pid": path_id, "orden": data.orden, "tipo": data.tipo,
            "titulo": data.titulo, "desc": data.descripcion,
            "url": data.url_recurso, "dur": data.duracion_min, "oblig": data.obligatorio,
        },
    )).fetchone()
    await db.commit()
    return RecursoOut(id=row.id, orden=row.orden, tipo=row.tipo, titulo=row.titulo,
                      descripcion=row.descripcion, url_recurso=row.url_recurso,
                      duracion_min=row.duracion_min, obligatorio=row.obligatorio,
                      is_active=row.is_active)


# ── Asignaciones ──────────────────────────────────────────────────────────────

@router.get("/asignaciones", response_model=list[AsignacionOut])
async def listar_asignaciones(
    estudiante_id: uuid.UUID | None = None,
    estatus: str | None = None,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    from sqlalchemy import text
    sql = """
        SELECT a.id, a.path_id, lp.nombre AS path_nombre,
               a.estudiante_id, a.motivo, a.estatus, a.pct_completado, a.fccreacion
          FROM ades_lp_asignaciones a
          JOIN ades_learning_paths lp ON lp.id = a.path_id
         WHERE (:est_id IS NULL OR a.estudiante_id = :est_id)
           AND (:estatus IS NULL OR a.estatus = :estatus)
         ORDER BY a.fccreacion DESC
    """
    rows = (await db.execute(text(sql), {"est_id": estudiante_id, "estatus": estatus})).fetchall()
    return [AsignacionOut(
        id=r.id, path_id=r.path_id, path_nombre=r.path_nombre,
        estudiante_id=r.estudiante_id, motivo=r.motivo,
        estatus=r.estatus, pct_completado=float(r.pct_completado),
        fccreacion=r.fccreacion,
    ) for r in rows]


@router.post("/{path_id}/asignar", response_model=AsignacionOut, status_code=201)
async def asignar_path(
    path_id: uuid.UUID,
    data: AsignacionIn,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    from sqlalchemy import text
    await _get_path_or_404(path_id, db)
    asignado_por = _user.get("sub")
    row = (await db.execute(
        text("""
            INSERT INTO ades_lp_asignaciones
                (path_id, estudiante_id, asignado_por, motivo, estatus, pct_completado, fcinicio)
            VALUES (:pid, :est, :asig::uuid, :motivo, 'PENDIENTE', 0, NOW())
            ON CONFLICT (path_id, estudiante_id) DO UPDATE
                SET estatus = EXCLUDED.estatus, fcmodificacion = NOW()
            RETURNING id, path_id, estudiante_id, motivo, estatus, pct_completado, fccreacion
        """),
        {"pid": path_id, "est": data.estudiante_id, "asig": asignado_por, "motivo": data.motivo},
    )).fetchone()
    await db.commit()

    path = await _get_path_or_404(path_id, db)
    return AsignacionOut(
        id=row.id, path_id=row.path_id, path_nombre=path.nombre,
        estudiante_id=row.estudiante_id, motivo=row.motivo,
        estatus=row.estatus, pct_completado=float(row.pct_completado),
        fccreacion=row.fccreacion,
    )


@router.get("/asignaciones/{asig_id}", response_model=AsignacionDetalle)
async def detalle_asignacion(
    asig_id: uuid.UUID,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    from sqlalchemy import text
    row = (await db.execute(
        text("""
            SELECT a.id, a.path_id, lp.nombre AS path_nombre,
                   a.estudiante_id, a.motivo, a.estatus, a.pct_completado, a.fccreacion
              FROM ades_lp_asignaciones a
              JOIN ades_learning_paths lp ON lp.id = a.path_id
             WHERE a.id = :id
        """),
        {"id": asig_id},
    )).fetchone()
    if not row:
        raise HTTPException(404, "Asignación no encontrada")

    progreso_rows = (await db.execute(
        text("""
            SELECT p.recurso_id, r.titulo, r.tipo, r.orden,
                   p.completado, p.tiempo_min, p.calificacion, p.fccompletado
              FROM ades_lp_progreso p
              JOIN ades_lp_recursos r ON r.id = p.recurso_id
             WHERE p.asignacion_id = :aid
             ORDER BY r.orden
        """),
        {"aid": asig_id},
    )).fetchall()

    total_rows = (await db.execute(
        text("SELECT COUNT(*) FROM ades_lp_recursos WHERE path_id = (SELECT path_id FROM ades_lp_asignaciones WHERE id = :aid) AND is_active = TRUE"),
        {"aid": asig_id},
    )).scalar()

    completados = sum(1 for p in progreso_rows if p.completado)
    return AsignacionDetalle(
        id=row.id, path_id=row.path_id, path_nombre=row.path_nombre,
        estudiante_id=row.estudiante_id, motivo=row.motivo,
        estatus=row.estatus, pct_completado=float(row.pct_completado),
        fccreacion=row.fccreacion,
        recursos_completados=completados,
        total_recursos=total_rows or 0,
        progreso=[{
            "recurso_id": str(p.recurso_id), "titulo": p.titulo, "tipo": p.tipo,
            "orden": p.orden, "completado": p.completado,
            "tiempo_min": p.tiempo_min, "calificacion": p.calificacion,
            "fccompletado": p.fccompletado.isoformat() if p.fccompletado else None,
        } for p in progreso_rows],
    )


@router.post("/asignaciones/{asig_id}/progreso/{recurso_id}", status_code=200)
async def registrar_progreso(
    asig_id: uuid.UUID,
    recurso_id: uuid.UUID,
    data: ProgresoIn,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    """Marca un recurso como completado y recalcula el % de progreso de la asignación."""
    from sqlalchemy import text
    await db.execute(
        text("""
            INSERT INTO ades_lp_progreso
                (asignacion_id, recurso_id, completado, tiempo_min, calificacion, fccompletado)
            VALUES (:asig, :rec, TRUE, :t, :cal, NOW())
            ON CONFLICT (asignacion_id, recurso_id) DO UPDATE
                SET completado = TRUE, tiempo_min = COALESCE(EXCLUDED.tiempo_min, ades_lp_progreso.tiempo_min),
                    calificacion = COALESCE(EXCLUDED.calificacion, ades_lp_progreso.calificacion),
                    fccompletado = NOW()
        """),
        {"asig": asig_id, "rec": recurso_id, "t": data.tiempo_min, "cal": data.calificacion},
    )

    # Recalcular pct_completado en la asignación
    await db.execute(
        text("""
            UPDATE ades_lp_asignaciones
               SET pct_completado = (
                       SELECT ROUND(
                           100.0 * COUNT(CASE WHEN pr.completado THEN 1 END)
                           / NULLIF((SELECT COUNT(*) FROM ades_lp_recursos r2
                                      WHERE r2.path_id = a.path_id AND r2.is_active = TRUE), 0)
                       , 1)
                         FROM ades_lp_progreso pr
                         JOIN ades_lp_asignaciones a ON a.id = pr.asignacion_id
                        WHERE pr.asignacion_id = :asig
                   ),
                   estatus = CASE
                       WHEN (
                           SELECT COUNT(CASE WHEN pr2.completado THEN 1 END)
                             FROM ades_lp_progreso pr2
                            WHERE pr2.asignacion_id = :asig
                       ) >= (
                           SELECT COUNT(*) FROM ades_lp_recursos r3
                            WHERE r3.path_id = (SELECT path_id FROM ades_lp_asignaciones WHERE id = :asig)
                              AND r3.is_active = TRUE AND r3.obligatorio = TRUE
                       ) THEN 'COMPLETADO'
                       ELSE 'EN_PROGRESO'
                   END,
                   fccompletado = CASE
                       WHEN estatus = 'COMPLETADO' THEN NOW() ELSE fccompletado END,
                   fcmodificacion = NOW()
             WHERE id = :asig
        """),
        {"asig": asig_id},
    )
    await db.commit()
    return {"mensaje": "Progreso registrado"}


@router.post("/asignar-automatico/{grupo_id}", status_code=200)
async def asignar_automatico(
    grupo_id: uuid.UUID,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    """
    Asigna automáticamente learning paths a alumnos del grupo según alertas activas:
      - REPROBACION / AUSENTISMO_CRITICO → path con criterio correspondiente
    """
    from sqlalchemy import text

    alertas = (await db.execute(
        text("""
            SELECT a.estudiante_id, a.tipo_alerta
              FROM ades_alertas_academicas a
             WHERE a.grupo_id = :gid AND a.atendida = FALSE
        """),
        {"gid": grupo_id},
    )).fetchall()

    if not alertas:
        return {"asignadas": 0, "mensaje": "Sin alertas activas en el grupo"}

    # Obtener paths automáticos disponibles
    paths = (await db.execute(
        text("""
            SELECT id, criterio_activacion FROM ades_learning_paths
             WHERE criterio_activacion IN ('REPROBACION', 'AUSENTISMO', 'RIESGO_ALTO')
               AND is_active = TRUE
        """)
    )).fetchall()

    tipo_a_criterio = {
        "RIESGO_REPROBACION": "REPROBACION",
        "AUSENTISMO_CRITICO": "AUSENTISMO",
    }
    path_por_criterio = {p.criterio_activacion: p.id for p in paths}

    asignadas = 0
    asig_by = _user.get("sub")
    for alerta in alertas:
        criterio = tipo_a_criterio.get(alerta.tipo_alerta)
        if not criterio:
            continue
        path_id = path_por_criterio.get(criterio)
        if not path_id:
            continue
        motivo = f"AUTO_{alerta.tipo_alerta}"
        await db.execute(
            text("""
                INSERT INTO ades_lp_asignaciones
                    (path_id, estudiante_id, asignado_por, motivo, estatus, pct_completado, fcinicio)
                VALUES (:pid, :est, :asig::uuid, :motivo, 'PENDIENTE', 0, NOW())
                ON CONFLICT (path_id, estudiante_id) DO NOTHING
            """),
            {"pid": path_id, "est": alerta.estudiante_id, "asig": asig_by, "motivo": motivo},
        )
        asignadas += 1

    await db.commit()
    return {"asignadas": asignadas, "grupo_id": str(grupo_id)}
