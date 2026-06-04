from __future__ import annotations
import uuid
from fastapi import APIRouter, Depends, HTTPException, status, Query
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, func
from app.core.database import get_db
from app.core.security import get_current_user
from app.models.academica import Plantel, Grado, NivelEducativo
from app.schemas.academica import PlantelOut, PlantelCreate, PlantelUpdate, GradoOut, NivelOut

router = APIRouter(prefix="/planteles", tags=["planteles"])

_ADMIN = ["ADMIN_GLOBAL", "ADMIN_PLANTEL", "DIRECTOR"]


@router.get("", response_model=list[PlantelOut])
async def listar_planteles(
    is_active: bool = True,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    q = select(Plantel).where(Plantel.is_active == is_active).order_by(Plantel.nombre_plantel)
    rows = await db.execute(q)
    return rows.scalars().all()


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
    from app.models.academica import PlantelNivel
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
