"""
/grade-analytics — Analítica de calificaciones y riesgo académico.

Consume las vistas materializadas ades_bi para ofrecer insights consolidados
sin tocar las tablas transaccionales en tiempo real.

  GET /grade-analytics/tendencias/{grupo_id}   — promedios por periodo y materia
  GET /grade-analytics/distribucion/{grupo_id} — distribución de calificaciones (histogram)
  GET /grade-analytics/riesgo                  — alumnos en riesgo con filtros
  GET /grade-analytics/resumen-plantel         — KPIs ejecutivos por plantel/nivel
  GET /grade-analytics/cobertura               — cobertura curricular por grupo
  GET /grade-analytics/alertas-umbral          — alumnos por debajo de umbral configurable
"""
from __future__ import annotations

from typing import Optional
from uuid import UUID

from fastapi import APIRouter, Depends, Query
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.core.security import get_current_user

router = APIRouter(prefix="/grade-analytics", tags=["grade-analytics"])


@router.get("/tendencias/{grupo_id}")
async def tendencias_grupo(
    grupo_id: UUID,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    """Promedios por periodo y materia para un grupo — alimenta el line chart."""
    rows = await db.execute(text("""
        SELECT
            nombre_materia,
            nombre_periodo,
            numero_periodo,
            alumnos_evaluados,
            ROUND(promedio, 2)   AS promedio,
            ROUND(minimo,   2)   AS minimo,
            ROUND(maximo,   2)   AS maximo,
            aprobados,
            reprobados,
            CASE WHEN alumnos_evaluados > 0
                 THEN ROUND(aprobados::numeric / alumnos_evaluados * 100, 1)
                 ELSE 0 END       AS pct_aprobados
        FROM ades_bi.mv_calificaciones_grupo
        WHERE grupo_id = :gid::uuid
        ORDER BY numero_periodo, nombre_materia
    """), {"gid": str(grupo_id)})
    return rows.mappings().all()


@router.get("/distribucion/{grupo_id}")
async def distribucion_calificaciones(
    grupo_id: UUID,
    numero_periodo: Optional[int] = None,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    """Distribución de alumnos por rango de calificación (histogram) para el grupo."""
    extra = "AND cp.periodo_evaluacion_id = pe.id AND pe.numero_periodo = :periodo" if numero_periodo else ""
    params: dict = {"gid": str(grupo_id)}
    if numero_periodo:
        params["periodo"] = numero_periodo

    rows = await db.execute(text(f"""
        SELECT
            CASE
                WHEN cp.calificacion < 6  THEN '< 6.0'
                WHEN cp.calificacion < 7  THEN '6.0 – 6.9'
                WHEN cp.calificacion < 8  THEN '7.0 – 7.9'
                WHEN cp.calificacion < 9  THEN '8.0 – 8.9'
                WHEN cp.calificacion < 10 THEN '9.0 – 9.9'
                ELSE '10.0'
            END                               AS rango,
            COUNT(*)                          AS total_alumnos,
            ROUND(COUNT(*)::numeric / SUM(COUNT(*)) OVER() * 100, 1) AS pct
        FROM ades_calificaciones_periodo cp
        JOIN ades_periodos_evaluacion pe      ON pe.id = cp.periodo_evaluacion_id
        JOIN ades_inscripciones i             ON i.id = cp.inscripcion_id
        WHERE i.grupo_id = :gid::uuid
          AND cp.calificacion IS NOT NULL
          {extra}
        GROUP BY rango
        ORDER BY MIN(cp.calificacion)
    """), params)
    return rows.mappings().all()


@router.get("/riesgo")
async def alumnos_en_riesgo(
    plantel_id: Optional[UUID] = None,
    grupo_id: Optional[UUID] = None,
    nivel_riesgo: Optional[str] = None,
    limit: int = Query(50, le=200),
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    """Lista de alumnos en riesgo académico desde la vista materializada."""
    filters = ["1=1"]
    params: dict = {"limit": limit}

    if plantel_id:
        filters.append("""nombre_plantel = (
            SELECT nombre_plantel FROM ades_planteles WHERE id = :plantel_id::uuid)""")
        params["plantel_id"] = str(plantel_id)
    if grupo_id:
        filters.append("grupo_id = :grupo_id::uuid")
        params["grupo_id"] = str(grupo_id)
    if nivel_riesgo:
        filters.append("nivel_riesgo = :nivel_riesgo")
        params["nivel_riesgo"] = nivel_riesgo

    where = " AND ".join(filters)
    rows = await db.execute(text(f"""
        SELECT
            estudiante_id,
            nombre_alumno,
            nombre_grupo,
            nombre_grado,
            nombre_plantel,
            nombre_nivel,
            ROUND(promedio_general, 2) AS promedio_general,
            ROUND(pct_asistencia, 1)   AS pct_asistencia,
            materias_reprobadas,
            nivel_riesgo
        FROM ades_bi.mv_riesgo_academico
        WHERE {where}
        ORDER BY
            CASE nivel_riesgo WHEN 'ALTO' THEN 1 WHEN 'MEDIO' THEN 2 ELSE 3 END,
            promedio_general ASC
        LIMIT :limit
    """), params)
    return rows.mappings().all()


@router.get("/resumen-plantel")
async def resumen_plantel(
    plantel_id: Optional[UUID] = None,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    """KPIs ejecutivos por plantel y nivel educativo."""
    extra = ""
    params: dict = {}
    if plantel_id:
        extra = "WHERE nombre_plantel = (SELECT nombre_plantel FROM ades_planteles WHERE id = :pid::uuid)"
        params["pid"] = str(plantel_id)

    rows = await db.execute(text(f"""
        SELECT *
        FROM ades_bi.mv_resumen_plantel
        {extra}
        ORDER BY nombre_plantel, nombre_nivel
    """), params)
    return rows.mappings().all()


@router.get("/cobertura")
async def cobertura_curricular(
    plantel_id: Optional[UUID] = None,
    grupo_id: Optional[UUID] = None,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    """Cobertura curricular: % del plan de estudios impartido por grupo/materia."""
    filters = ["1=1"]
    params: dict = {}

    if plantel_id:
        filters.append("""nombre_plantel = (
            SELECT nombre_plantel FROM ades_planteles WHERE id = :plantel_id::uuid)""")
        params["plantel_id"] = str(plantel_id)
    if grupo_id:
        filters.append("grupo_id = :grupo_id::uuid")
        params["grupo_id"] = str(grupo_id)

    where = " AND ".join(filters)
    rows = await db.execute(text(f"""
        SELECT *
        FROM ades_bi.mv_cobertura_curricular
        WHERE {where}
        ORDER BY nombre_plantel, nombre_grupo, nombre_materia
    """), params)
    return rows.mappings().all()


@router.get("/alertas-umbral")
async def alertas_umbral(
    umbral: float = Query(7.0, ge=0.0, le=10.0, description="Umbral de calificación mínima"),
    plantel_id: Optional[UUID] = None,
    grupo_id: Optional[UUID] = None,
    limit: int = Query(100, le=500),
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    """Alumnos con promedio general por debajo del umbral especificado."""
    filters = ["promedio_general < :umbral"]
    params: dict = {"umbral": umbral, "limit": limit}

    if plantel_id:
        filters.append("""nombre_plantel = (
            SELECT nombre_plantel FROM ades_planteles WHERE id = :plantel_id::uuid)""")
        params["plantel_id"] = str(plantel_id)
    if grupo_id:
        filters.append("grupo_id = :grupo_id::uuid")
        params["grupo_id"] = str(grupo_id)

    where = " AND ".join(filters)
    rows = await db.execute(text(f"""
        SELECT
            estudiante_id,
            nombre_alumno,
            nombre_grupo,
            nombre_grado,
            nombre_plantel,
            ROUND(promedio_general, 2) AS promedio_general,
            ROUND(pct_asistencia, 1)   AS pct_asistencia,
            materias_reprobadas,
            nivel_riesgo
        FROM ades_bi.mv_riesgo_academico
        WHERE {where}
        ORDER BY promedio_general ASC
        LIMIT :limit
    """), params)
    return rows.mappings().all()
