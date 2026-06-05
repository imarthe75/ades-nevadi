"""
/asistencias — Registro y reporte de asistencias.

Flujo estándar:
  1. GET  /clases/{clase_id}/alumnos-esperados  → lista pase de lista
  2. POST /asistencias/clase/{clase_id}          → registra asistencia bulk
  3. GET  /asistencias/grupo/{grupo_id}/reporte  → reporte del grupo
  4. GET  /asistencias/alumno/{alumno_id}/reporte → reporte individual
"""
from __future__ import annotations
import asyncio
import uuid
from datetime import date
from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, func, case
from app.core.database import get_db
from app.core.security import get_current_user
from app.models.operacion import Clase, Asistencia
from app.models.personas import Estudiante, Inscripcion
from app.schemas.operacion import (
    AsistenciaOut, RegistrarAsistenciaIn,
    ReporteAsistenciaAlumno, ReporteAsistenciaGrupo,
)

router = APIRouter(prefix="/asistencias", tags=["asistencias"])


@router.post("/clase/{clase_id}", response_model=list[AsistenciaOut], status_code=status.HTTP_201_CREATED)
async def registrar_asistencia_clase(
    clase_id: uuid.UUID,
    data: RegistrarAsistenciaIn,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    """
    Registra o actualiza la asistencia de todos los alumnos en una clase.
    Usa upsert (ON CONFLICT UPDATE) para permitir re-registro.
    """
    clase = await db.get(Clase, clase_id)
    if not clase:
        raise HTTPException(status_code=404, detail="Clase no encontrada")

    resultados: list[Asistencia] = []
    for item in data.asistencias:
        # Buscar asistencia existente
        existing = (await db.execute(
            select(Asistencia).where(
                Asistencia.clase_id == clase_id,
                Asistencia.estudiante_id == item.estudiante_id,
            )
        )).scalar_one_or_none()

        if existing:
            existing.estatus_asistencia = item.estatus_asistencia
            existing.observacion = item.observacion
            resultados.append(existing)
        else:
            asistencia = Asistencia(
                clase_id=clase_id,
                estudiante_id=item.estudiante_id,
                estatus_asistencia=item.estatus_asistencia,
                observacion=item.observacion,
            )
            db.add(asistencia)
            resultados.append(asistencia)

    # Marcar clase como impartida si se registró asistencia
    if clase.estatus_clase == "PROGRAMADA":
        clase.estatus_clase = "IMPARTIDA"

    await db.commit()
    for r in resultados:
        await db.refresh(r)

    # FASE 20 — Verificar asistencia de alumnos ausentes y alertar si baja del umbral
    ausentes = [
        item.estudiante_id for item in data.asistencias
        if item.estatus_asistencia in ("AUSENTE",)
    ]
    if ausentes and clase:
        asyncio.create_task(_check_asistencia_y_alertar(db, ausentes, clase))

    return resultados


async def _check_asistencia_y_alertar(db: AsyncSession, estudiante_ids, clase) -> None:
    """
    Para cada alumno ausente, calcula su % de asistencia acumulado.
    Si cae por debajo del 85%, dispara push al padre + webhook n8n.
    Corre en background para no retrasar la respuesta.
    """
    from sqlalchemy import text
    from app.services.notification_triggers import on_asistencia_baja

    for est_id in estudiante_ids:
        try:
            row = await db.execute(
                text("""
                    SELECT
                        COUNT(*) FILTER (WHERE a.estatus_asistencia = 'PRESENTE') AS presentes,
                        COUNT(*) AS total
                    FROM ades_asistencias a
                    JOIN ades_clases cl ON cl.id = a.clase_id
                    WHERE a.estudiante_id = :eid
                      AND cl.grupo_id = :gid
                """),
                {"eid": str(est_id), "gid": str(clase.grupo_id) if clase.grupo_id else ""},
            )
            r = row.fetchone()
            if not r or r[1] == 0:
                continue
            pct = round(r[0] / r[1] * 100, 1)
            inasistencias = r[1] - r[0]
            if pct < 85.0:
                await on_asistencia_baja(
                    db, est_id, pct, inasistencias,
                    plantel_id=getattr(clase, "plantel_id", None),
                    grupo_id=getattr(clase, "grupo_id", None),
                )
        except Exception:
            pass


@router.patch("/clase/{clase_id}/alumno/{estudiante_id}", response_model=AsistenciaOut)
async def actualizar_asistencia(
    clase_id: uuid.UUID,
    estudiante_id: uuid.UUID,
    estatus: str = Query(..., description="PRESENTE | AUSENTE | TARDE | JUSTIFICADO"),
    observacion: str | None = None,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    """Actualiza la asistencia de un alumno específico."""
    asistencia = (await db.execute(
        select(Asistencia).where(
            Asistencia.clase_id == clase_id,
            Asistencia.estudiante_id == estudiante_id,
        )
    )).scalar_one_or_none()

    if not asistencia:
        raise HTTPException(status_code=404, detail="Asistencia no registrada — use POST primero")

    estatus = estatus.upper()
    if estatus not in ("PRESENTE", "AUSENTE", "TARDE", "JUSTIFICADO"):
        raise HTTPException(status_code=422, detail="Estatus inválido")

    asistencia.estatus_asistencia = estatus
    asistencia.observacion = observacion
    await db.commit()
    await db.refresh(asistencia)
    return asistencia


@router.get("/clase/{clase_id}", response_model=list[AsistenciaOut])
async def asistencias_de_clase(
    clase_id: uuid.UUID,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    rows = await db.execute(
        select(Asistencia)
        .where(Asistencia.clase_id == clase_id, Asistencia.is_active == True)
        .order_by(Asistencia.estudiante_id)
    )
    return rows.scalars().all()


@router.get("/grupo/{grupo_id}/reporte", response_model=ReporteAsistenciaGrupo)
async def reporte_grupo(
    grupo_id: uuid.UUID,
    fecha_desde: date | None = None,
    fecha_hasta: date | None = None,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    """Estadísticas de asistencia agregadas por alumno para un grupo."""
    # Clases del grupo en el rango de fechas
    clases_q = select(Clase.id).where(Clase.grupo_id == grupo_id, Clase.is_active == True)
    if fecha_desde:
        clases_q = clases_q.where(Clase.fecha_clase >= fecha_desde)
    if fecha_hasta:
        clases_q = clases_q.where(Clase.fecha_clase <= fecha_hasta)
    clase_ids = [r[0] for r in (await db.execute(clases_q)).all()]

    total_clases = len(clase_ids)

    if not clase_ids:
        return ReporteAsistenciaGrupo(grupo_id=grupo_id, total_clases=0, alumnos=[])

    # Estadísticas por alumno
    q = (
        select(
            Asistencia.estudiante_id,
            func.count().label("total"),
            func.sum(case((Asistencia.estatus_asistencia == "PRESENTE", 1), else_=0)).label("presentes"),
            func.sum(case((Asistencia.estatus_asistencia == "AUSENTE", 1), else_=0)).label("ausentes"),
            func.sum(case((Asistencia.estatus_asistencia == "TARDE", 1), else_=0)).label("tardes"),
            func.sum(case((Asistencia.estatus_asistencia == "JUSTIFICADO", 1), else_=0)).label("justificados"),
        )
        .where(Asistencia.clase_id.in_(clase_ids))
        .group_by(Asistencia.estudiante_id)
    )
    rows = (await db.execute(q)).all()

    alumnos = [
        ReporteAsistenciaAlumno(
            estudiante_id=r.estudiante_id,
            total_clases=total_clases,
            presentes=r.presentes or 0,
            ausentes=r.ausentes or 0,
            tardes=r.tardes or 0,
            justificados=r.justificados or 0,
            porcentaje_asistencia=round(
                ((r.presentes or 0) + (r.tardes or 0)) / total_clases * 100, 1
            ) if total_clases > 0 else 0.0,
        )
        for r in rows
    ]

    return ReporteAsistenciaGrupo(
        grupo_id=grupo_id,
        total_clases=total_clases,
        alumnos=alumnos,
    )


@router.get("/alumno/{estudiante_id}/reporte", response_model=ReporteAsistenciaAlumno)
async def reporte_alumno(
    estudiante_id: uuid.UUID,
    grupo_id: uuid.UUID | None = None,
    fecha_desde: date | None = None,
    fecha_hasta: date | None = None,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    clases_q = select(Clase.id)
    if grupo_id:
        clases_q = clases_q.where(Clase.grupo_id == grupo_id)
    if fecha_desde:
        clases_q = clases_q.where(Clase.fecha_clase >= fecha_desde)
    if fecha_hasta:
        clases_q = clases_q.where(Clase.fecha_clase <= fecha_hasta)
    clase_ids = [r[0] for r in (await db.execute(clases_q)).all()]

    if not clase_ids:
        return ReporteAsistenciaAlumno(
            estudiante_id=estudiante_id, total_clases=0,
            presentes=0, ausentes=0, tardes=0, justificados=0, porcentaje_asistencia=0.0,
        )

    q = (
        select(
            func.count().label("total"),
            func.sum(case((Asistencia.estatus_asistencia == "PRESENTE", 1), else_=0)).label("presentes"),
            func.sum(case((Asistencia.estatus_asistencia == "AUSENTE", 1), else_=0)).label("ausentes"),
            func.sum(case((Asistencia.estatus_asistencia == "TARDE", 1), else_=0)).label("tardes"),
            func.sum(case((Asistencia.estatus_asistencia == "JUSTIFICADO", 1), else_=0)).label("justificados"),
        )
        .where(
            Asistencia.estudiante_id == estudiante_id,
            Asistencia.clase_id.in_(clase_ids),
        )
    )
    r = (await db.execute(q)).one()
    total = len(clase_ids)
    presentes = r.presentes or 0
    tardes = r.tardes or 0

    return ReporteAsistenciaAlumno(
        estudiante_id=estudiante_id,
        total_clases=total,
        presentes=presentes,
        ausentes=r.ausentes or 0,
        tardes=tardes,
        justificados=r.justificados or 0,
        porcentaje_asistencia=round((presentes + tardes) / total * 100, 1) if total > 0 else 0.0,
    )
