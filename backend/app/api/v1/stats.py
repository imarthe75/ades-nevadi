"""
/stats/resumen — KPIs del plantel para el dashboard.
/stats/distribucion — Distribución de alumnos/grupos por nivel educativo.
"""
from __future__ import annotations
import uuid
from datetime import date
from fastapi import APIRouter, Depends, Query
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, func
from pydantic import BaseModel

from app.core.database import get_db
from app.core.security import get_ades_user, AdesUser
from app.models.personas import Estudiante, Profesor, Inscripcion
from app.models.academica import Grupo, Grado, CicloEscolar, NivelEducativo
from app.models.operacion import Clase

router = APIRouter(prefix="/stats", tags=["stats"])


class ResumenPlantel(BaseModel):
    total_alumnos: int
    total_profesores: int
    total_grupos_activos: int
    total_clases_hoy: int


class DistribucionNivel(BaseModel):
    nombre_nivel: str
    total_alumnos: int
    total_grupos: int


@router.get("/resumen", response_model=ResumenPlantel)
async def resumen_plantel(
    plantel_id: uuid.UUID | None = Query(None),
    db: AsyncSession = Depends(get_db),
    ades_user: AdesUser = Depends(get_ades_user),
):
    pid = ades_user.plantel_id or plantel_id
    nid = ades_user.nivel_educativo_id

    # Alumnos activos
    if nid and pid:
        nivel_subq = (
            select(Inscripcion.estudiante_id)
            .join(Grupo, Grupo.id == Inscripcion.grupo_id)
            .join(Grado, Grado.id == Grupo.grado_id)
            .where(Grado.nivel_educativo_id == nid, Grado.plantel_id == pid)
            .scalar_subquery()
        )
        q_alumnos = select(func.count()).where(
            Estudiante.is_active == True,
            Estudiante.id.in_(nivel_subq),
        )
    else:
        q_alumnos = select(func.count(Estudiante.id)).where(Estudiante.is_active == True)
        if pid:
            q_alumnos = q_alumnos.where(Estudiante.plantel_id == pid)
    total_alumnos = (await db.execute(q_alumnos)).scalar_one()

    # Profesores activos (por plantel)
    q_profesores = select(func.count(Profesor.id)).where(Profesor.is_active == True)
    if pid:
        q_profesores = q_profesores.where(Profesor.plantel_id == pid)
    total_profesores = (await db.execute(q_profesores)).scalar_one()

    # Grupos activos (ciclo vigente, filtrado por scope)
    q_grupos = (
        select(func.count(Grupo.id))
        .join(CicloEscolar, CicloEscolar.id == Grupo.ciclo_escolar_id)
        .join(Grado, Grado.id == Grupo.grado_id)
        .where(Grupo.is_active == True, CicloEscolar.es_vigente == True)
    )
    if nid and pid:
        q_grupos = q_grupos.where(Grado.nivel_educativo_id == nid, Grado.plantel_id == pid)
    elif pid:
        q_grupos = q_grupos.where(Grado.plantel_id == pid)
    total_grupos = (await db.execute(q_grupos)).scalar_one()

    # Clases de hoy
    hoy = date.today()
    q_clases = select(func.count(Clase.id)).where(Clase.fecha_clase == hoy)
    total_clases_hoy = (await db.execute(q_clases)).scalar_one()

    return ResumenPlantel(
        total_alumnos=total_alumnos,
        total_profesores=total_profesores,
        total_grupos_activos=total_grupos,
        total_clases_hoy=total_clases_hoy,
    )


@router.get("/distribucion", response_model=list[DistribucionNivel])
async def distribucion_por_nivel(
    plantel_id: uuid.UUID | None = Query(None),
    db: AsyncSession = Depends(get_db),
    ades_user: AdesUser = Depends(get_ades_user),
):
    """Distribución de alumnos y grupos activos por nivel educativo (ciclo vigente)."""
    pid = ades_user.plantel_id or plantel_id

    # Alumnos por nivel (via inscripciones activas en ciclo vigente)
    q_alumnos_nivel = (
        select(NivelEducativo.nombre_nivel, func.count(Inscripcion.estudiante_id.distinct()).label("total_alumnos"))
        .join(Grado, Grado.nivel_educativo_id == NivelEducativo.id)
        .join(Grupo, Grupo.grado_id == Grado.id)
        .join(CicloEscolar, CicloEscolar.id == Grupo.ciclo_escolar_id)
        .join(Inscripcion, Inscripcion.grupo_id == Grupo.id)
        .where(Grupo.is_active == True, CicloEscolar.es_vigente == True)
    )
    if pid:
        q_alumnos_nivel = q_alumnos_nivel.where(Grado.plantel_id == pid)
    q_alumnos_nivel = q_alumnos_nivel.group_by(NivelEducativo.nombre_nivel)

    # Grupos por nivel
    q_grupos_nivel = (
        select(NivelEducativo.nombre_nivel, func.count(Grupo.id).label("total_grupos"))
        .join(Grado, Grado.nivel_educativo_id == NivelEducativo.id)
        .join(Grupo, Grupo.grado_id == Grado.id)
        .join(CicloEscolar, CicloEscolar.id == Grupo.ciclo_escolar_id)
        .where(Grupo.is_active == True, CicloEscolar.es_vigente == True)
    )
    if pid:
        q_grupos_nivel = q_grupos_nivel.where(Grado.plantel_id == pid)
    q_grupos_nivel = q_grupos_nivel.group_by(NivelEducativo.nombre_nivel)

    alumnos_rows = (await db.execute(q_alumnos_nivel)).all()
    grupos_rows = (await db.execute(q_grupos_nivel)).all()

    alumnos_map = {r.nombre_nivel: r.total_alumnos for r in alumnos_rows}
    grupos_map = {r.nombre_nivel: r.total_grupos for r in grupos_rows}

    niveles = sorted(set(alumnos_map) | set(grupos_map))
    return [
        DistribucionNivel(
            nombre_nivel=n,
            total_alumnos=alumnos_map.get(n, 0),
            total_grupos=grupos_map.get(n, 0),
        )
        for n in niveles
    ]
