"""
Endpoints de catálogos de solo lectura:
  GET /niveles
  GET /ciclos
  GET /grados
  GET /roles
  GET /estatus
"""
from __future__ import annotations
import uuid
from fastapi import APIRouter, Depends, Query
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from app.core.database import get_db
from app.core.security import get_current_user
from app.models.academica import NivelEducativo, Grado, CicloEscolar
from app.models.personas import Rol, Estatus
from app.schemas.academica import NivelOut, GradoOut, CicloOut
from app.schemas.base import AdesResponse, AdesSchema

router = APIRouter(prefix="/catalogs", tags=["catálogos"])


# ── Niveles ────────────────────────────────────────────────────────────────────

@router.get("/niveles", response_model=list[NivelOut])
async def listar_niveles(
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    rows = await db.execute(select(NivelEducativo).order_by(NivelEducativo.nombre_nivel))
    return rows.scalars().all()


# ── Ciclos Escolares ───────────────────────────────────────────────────────────

@router.get("/ciclos", response_model=list[CicloOut])
async def listar_ciclos(
    nivel: str | None = Query(None, description="PRIMARIA | SECUNDARIA | PREPARATORIA"),
    solo_vigentes: bool = False,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    q = (
        select(CicloEscolar)
        .join(NivelEducativo, NivelEducativo.id == CicloEscolar.nivel_educativo_id)
    )
    if nivel:
        q = q.where(NivelEducativo.nombre_nivel == nivel.upper())
    if solo_vigentes:
        q = q.where(CicloEscolar.es_vigente == True)
    q = q.order_by(CicloEscolar.fecha_inicio.desc())
    rows = await db.execute(q)
    return rows.scalars().all()


# ── Grados (global) ────────────────────────────────────────────────────────────

@router.get("/grados", response_model=list[GradoOut])
async def listar_grados(
    plantel_id: uuid.UUID | None = None,
    nivel: str | None = None,
    nivel_id: uuid.UUID | None = None,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    q = (
        select(Grado)
        .join(NivelEducativo, NivelEducativo.id == Grado.nivel_educativo_id)
        .where(Grado.is_active == True)
    )
    if plantel_id:
        q = q.where(Grado.plantel_id == plantel_id)
    if nivel:
        q = q.where(NivelEducativo.nombre_nivel == nivel.upper())
    if nivel_id:
        q = q.where(Grado.nivel_educativo_id == nivel_id)
    q = q.order_by(NivelEducativo.nombre_nivel, Grado.numero_grado)
    rows = await db.execute(q)
    return rows.scalars().all()


# ── Roles ──────────────────────────────────────────────────────────────────────

class RolOut(AdesSchema):
    id: uuid.UUID
    nombre_rol: str
    descripcion: str | None
    nivel_acceso: int


@router.get("/roles", response_model=list[RolOut])
async def listar_roles(
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    rows = await db.execute(select(Rol).order_by(Rol.nivel_acceso, Rol.nombre_rol))
    return rows.scalars().all()


# ── Estatus ────────────────────────────────────────────────────────────────────

class EstatusOut(AdesSchema):
    id: uuid.UUID
    entidad: str
    nombre_estatus: str
    descripcion: str | None


@router.get("/estatus", response_model=list[EstatusOut])
async def listar_estatus(
    entidad: str | None = Query(None, description="ej. ESTUDIANTE, GRUPO, PROFESOR"),
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    q = select(Estatus).where(Estatus.is_active == True)
    if entidad:
        q = q.where(Estatus.entidad == entidad.upper())
    q = q.order_by(Estatus.entidad, Estatus.nombre_estatus)
    rows = await db.execute(q)
    return rows.scalars().all()
