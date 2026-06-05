"""
/stats/resumen — KPIs del plantel para el dashboard.
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
from app.models.academica import Grupo, Grado, CicloEscolar
from app.models.operacion import Clase

router = APIRouter(prefix="/stats", tags=["stats"])


class ResumenPlantel(BaseModel):
    total_alumnos: int
    total_profesores: int
    total_grupos_activos: int
    total_clases_hoy: int


@router.get("/resumen", response_model=ResumenPlantel)
async def resumen_plantel(
    plantel_id: uuid.UUID | None = Query(None),
    db: AsyncSession = Depends(get_db),
    ades_user: AdesUser = Depends(get_ades_user),
):
    # Resolver plantel efectivo según scope RBAC
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
