from __future__ import annotations
import uuid
from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from sqlalchemy.orm import selectinload
from app.core.database import get_db
from app.core.security import get_current_user
from app.models.materias import Materia, MateriaPlan, Tema
from app.models.academica import NivelEducativo, Grado, CicloEscolar
from app.schemas.materias import MateriaOut, MateriaPlanOut, TemaOut

router = APIRouter(tags=["materias"])


@router.get("/materias", response_model=list[MateriaOut])
async def listar_materias(
    nivel: str | None = Query(None, description="PRIMARIA | SECUNDARIA | PREPARATORIA"),
    buscar: str | None = None,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    q = select(Materia).join(NivelEducativo, NivelEducativo.id == Materia.nivel_educativo_id)
    if nivel:
        q = q.where(NivelEducativo.nombre_nivel == nivel.upper())
    if buscar:
        q = q.where(Materia.nombre_materia.ilike(f"%{buscar}%"))
    q = q.where(Materia.is_active == True).order_by(NivelEducativo.nombre_nivel, Materia.nombre_materia)
    rows = await db.execute(q)
    return rows.scalars().all()


@router.get("/materias/{materia_id}", response_model=MateriaOut)
async def obtener_materia(
    materia_id: uuid.UUID,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    row = await db.get(Materia, materia_id)
    if not row:
        raise HTTPException(status_code=404, detail="Materia no encontrada")
    return row


@router.get("/planes-estudio", response_model=list[MateriaPlanOut])
async def listar_plan_estudios(
    grado_id: uuid.UUID | None = None,
    ciclo_id: uuid.UUID | None = None,
    plantel_id: uuid.UUID | None = None,
    nivel: str | None = None,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    q = (
        select(MateriaPlan)
        .options(selectinload(MateriaPlan.materia))
        .join(Grado, Grado.id == MateriaPlan.grado_id)
        .join(NivelEducativo, NivelEducativo.id == Grado.nivel_educativo_id)
    )
    if grado_id:
        q = q.where(MateriaPlan.grado_id == grado_id)
    if ciclo_id:
        q = q.where(MateriaPlan.ciclo_escolar_id == ciclo_id)
    if plantel_id:
        q = q.where(Grado.plantel_id == plantel_id)
    if nivel:
        q = q.where(NivelEducativo.nombre_nivel == nivel.upper())
    q = q.where(MateriaPlan.is_active == True).order_by(Grado.numero_grado, MateriaPlan.orden)
    rows = await db.execute(q)
    return rows.scalars().all()


@router.get("/planes-estudio/{plan_id}/temas", response_model=list[TemaOut])
async def temas_del_plan(
    plan_id: uuid.UUID,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    q = (
        select(Tema)
        .where(Tema.materia_plan_id == plan_id, Tema.is_active == True)
        .order_by(Tema.numero_tema)
    )
    rows = await db.execute(q)
    return rows.scalars().all()
