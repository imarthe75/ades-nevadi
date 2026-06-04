"""
/expedientes-medicos — Expediente médico del estudiante.
/incidentes-medicos  — Registro de incidentes médicos en plantel.
"""
from __future__ import annotations
import uuid
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select

from app.core.database import get_db
from app.core.security import get_current_user
from app.models.fase3 import ExpedienteMedico, IncidenteMedico
from app.schemas.fase3 import (
    ExpedienteCreate, ExpedienteUpdate, ExpedienteOut,
    IncidenteCreate, IncidenteOut,
)

router = APIRouter(tags=["medico"])


# ── Expediente Médico ─────────────────────────────────────────────────────────

@router.get("/expedientes-medicos/alumno/{estudiante_id}", response_model=ExpedienteOut)
async def obtener_expediente(
    estudiante_id: uuid.UUID,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    exp = (await db.execute(
        select(ExpedienteMedico).where(ExpedienteMedico.estudiante_id == estudiante_id)
    )).scalar_one_or_none()
    if not exp:
        raise HTTPException(status_code=404, detail="Expediente médico no encontrado")
    return exp


@router.post("/expedientes-medicos", response_model=ExpedienteOut, status_code=status.HTTP_201_CREATED)
async def crear_expediente(
    data: ExpedienteCreate,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    existing = (await db.execute(
        select(ExpedienteMedico).where(ExpedienteMedico.estudiante_id == data.estudiante_id)
    )).scalar_one_or_none()
    if existing:
        raise HTTPException(status_code=409, detail="El alumno ya tiene expediente médico")
    exp = ExpedienteMedico(**data.model_dump())
    db.add(exp)
    await db.commit()
    await db.refresh(exp)
    return exp


@router.put("/expedientes-medicos/{exp_id}", response_model=ExpedienteOut)
async def actualizar_expediente(
    exp_id: uuid.UUID,
    data: ExpedienteUpdate,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    exp = await db.get(ExpedienteMedico, exp_id)
    if not exp:
        raise HTTPException(status_code=404, detail="Expediente no encontrado")
    for field, value in data.model_dump(exclude_none=True).items():
        setattr(exp, field, value)
    await db.commit()
    await db.refresh(exp)
    return exp


# ── Incidentes Médicos ────────────────────────────────────────────────────────

@router.get("/incidentes-medicos/alumno/{estudiante_id}", response_model=list[IncidenteOut])
async def incidentes_alumno(
    estudiante_id: uuid.UUID,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    rows = (await db.execute(
        select(IncidenteMedico)
        .where(IncidenteMedico.estudiante_id == estudiante_id, IncidenteMedico.is_active == True)
        .order_by(IncidenteMedico.fecha_incidente.desc())
    )).scalars().all()
    return rows


@router.post("/incidentes-medicos", response_model=IncidenteOut, status_code=status.HTTP_201_CREATED)
async def registrar_incidente(
    data: IncidenteCreate,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    inc = IncidenteMedico(**data.model_dump())
    db.add(inc)
    await db.commit()
    await db.refresh(inc)
    return inc
