from __future__ import annotations
import uuid
from fastapi import APIRouter, Depends, HTTPException, status, Query
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, func
from app.core.database import get_db
from app.core.security import get_current_user, get_ades_user, AdesUser
from app.models.academica import Plantel, Grado, NivelEducativo, PlantelNivel, CicloEscolar, Grupo
from app.models.personas import Estudiante, Profesor
from app.schemas.academica import PlantelOut, PlantelCreate, PlantelUpdate, GradoOut, NivelOut
from app.schemas.base import AdesSchema

router = APIRouter(prefix="/planteles", tags=["planteles"])


class NivelStats(AdesSchema):
    nivel_educativo_id: uuid.UUID
    nombre_nivel: str
    grupos_activos: int
    grados: int


class PlantelStats(AdesSchema):
    id: uuid.UUID
    nombre_plantel: str
    clave_ct: str | None
    total_alumnos: int
    total_profesores: int
    total_grupos: int
    niveles: list[NivelStats]


@router.get("", response_model=list[PlantelOut])
async def listar_planteles(
    is_active: bool = True,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    q = select(Plantel).where(Plantel.is_active == is_active).order_by(Plantel.nombre_plantel)
    rows = await db.execute(q)
    return rows.scalars().all()


@router.get("/stats", response_model=list[PlantelStats])
async def stats_todos_planteles(
    db: AsyncSession = Depends(get_db),
    ades_user: AdesUser = Depends(get_ades_user),
):
    """KPIs consolidados por plantel con desglose por nivel educativo."""
    q = select(Plantel).where(Plantel.is_active == True).order_by(Plantel.nombre_plantel)
    if ades_user.plantel_id:
        q = q.where(Plantel.id == ades_user.plantel_id)
    planteles = (await db.execute(q)).scalars().all()

    result = []
    for p in planteles:
        # Totales
        alumnos = (await db.execute(
            select(func.count(Estudiante.id))
            .where(Estudiante.plantel_id == p.id, Estudiante.is_active == True)
        )).scalar_one()

        profesores = (await db.execute(
            select(func.count(Profesor.id))
            .where(Profesor.plantel_id == p.id, Profesor.is_active == True)
        )).scalar_one()

        grupos_total = (await db.execute(
            select(func.count(Grupo.id))
            .join(Grado, Grado.id == Grupo.grado_id)
            .join(CicloEscolar, CicloEscolar.id == Grupo.ciclo_escolar_id)
            .where(Grado.plantel_id == p.id, Grupo.is_active == True, CicloEscolar.es_vigente == True)
        )).scalar_one()

        # Desglose por nivel
        niveles_q = (
            select(NivelEducativo)
            .join(PlantelNivel, PlantelNivel.nivel_educativo_id == NivelEducativo.id)
            .where(PlantelNivel.plantel_id == p.id, PlantelNivel.is_active == True)
            .order_by(NivelEducativo.nombre_nivel)
        )
        niveles = (await db.execute(niveles_q)).scalars().all()

        nivel_stats = []
        for n in niveles:
            grupos_nivel = (await db.execute(
                select(func.count(Grupo.id))
                .join(Grado, Grado.id == Grupo.grado_id)
                .join(CicloEscolar, CicloEscolar.id == Grupo.ciclo_escolar_id)
                .where(
                    Grado.plantel_id == p.id,
                    Grado.nivel_educativo_id == n.id,
                    Grupo.is_active == True,
                    CicloEscolar.es_vigente == True,
                )
            )).scalar_one()

            grados_nivel = (await db.execute(
                select(func.count(Grado.id))
                .where(Grado.plantel_id == p.id, Grado.nivel_educativo_id == n.id, Grado.is_active == True)
            )).scalar_one()

            nivel_stats.append(NivelStats(
                nivel_educativo_id=n.id,
                nombre_nivel=n.nombre_nivel,
                grupos_activos=grupos_nivel,
                grados=grados_nivel,
            ))

        result.append(PlantelStats(
            id=p.id,
            nombre_plantel=p.nombre_plantel,
            clave_ct=p.clave_ct,
            total_alumnos=alumnos,
            total_profesores=profesores,
            total_grupos=grupos_total,
            niveles=nivel_stats,
        ))
    return result


@router.get("/{plantel_id}", response_model=PlantelOut)
async def obtener_plantel(
    plantel_id: uuid.UUID,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    row = await db.get(Plantel, plantel_id)
    if not row:
        raise HTTPException(status_code=404, detail="Plantel no encontrado")
    return row


@router.get("/{plantel_id}/niveles", response_model=list[NivelOut])
async def niveles_del_plantel(
    plantel_id: uuid.UUID,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    q = (
        select(NivelEducativo)
        .join(PlantelNivel, PlantelNivel.nivel_educativo_id == NivelEducativo.id)
        .where(PlantelNivel.plantel_id == plantel_id, PlantelNivel.is_active == True)
        .order_by(NivelEducativo.nombre_nivel)
    )
    rows = await db.execute(q)
    return rows.scalars().all()


@router.get("/{plantel_id}/grados", response_model=list[GradoOut])
async def grados_del_plantel(
    plantel_id: uuid.UUID,
    nivel: str | None = Query(None, description="Filtrar por nombre_nivel: PRIMARIA, SECUNDARIA, PREPARATORIA"),
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    q = (
        select(Grado)
        .join(NivelEducativo, NivelEducativo.id == Grado.nivel_educativo_id)
        .where(Grado.plantel_id == plantel_id, Grado.is_active == True)
    )
    if nivel:
        q = q.where(NivelEducativo.nombre_nivel == nivel.upper())
    q = q.order_by(NivelEducativo.nombre_nivel, Grado.numero_grado)
    rows = await db.execute(q)
    return rows.scalars().all()
