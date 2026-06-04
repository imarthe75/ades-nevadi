"""
/conducta — Reportes de conducta / incidentes disciplinarios.
"""
from __future__ import annotations
import uuid
from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select

from app.core.database import get_db
from app.core.security import get_current_user
from app.models.fase3 import ReporteConducta
from app.schemas.fase3 import ConductaCreate, ConductaUpdate, ConductaOut

router = APIRouter(prefix="/conducta", tags=["conducta"])


@router.get("", response_model=list[ConductaOut])
async def listar_reportes(
    estudiante_id: uuid.UUID | None = None,
    grupo_id: uuid.UUID | None = None,
    tipo_falta: str | None = Query(None, description="LEVE | GRAVE | MUY_GRAVE"),
    requiere_seguimiento: bool | None = None,
    pagina: int = Query(1, ge=1),
    por_pagina: int = Query(20, ge=1, le=100),
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    q = select(ReporteConducta).where(ReporteConducta.is_active == True)
    if estudiante_id:
        q = q.where(ReporteConducta.estudiante_id == estudiante_id)
    if grupo_id:
        q = q.where(ReporteConducta.grupo_id == grupo_id)
    if tipo_falta:
        q = q.where(ReporteConducta.tipo_falta == tipo_falta.upper())
    if requiere_seguimiento is not None:
        q = q.where(ReporteConducta.requiere_seguimiento == requiere_seguimiento)
    q = q.order_by(ReporteConducta.fccreacion.desc())
    q = q.offset((pagina - 1) * por_pagina).limit(por_pagina)
    rows = (await db.execute(q)).scalars().all()
    return rows


@router.get("/{reporte_id}", response_model=ConductaOut)
async def obtener_reporte(
    reporte_id: uuid.UUID,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    r = await db.get(ReporteConducta, reporte_id)
    if not r or not r.is_active:
        raise HTTPException(status_code=404, detail="Reporte no encontrado")
    return r


@router.post("", response_model=ConductaOut, status_code=status.HTTP_201_CREATED)
async def crear_reporte(
    data: ConductaCreate,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    r = ReporteConducta(**data.model_dump())
    db.add(r)
    await db.commit()
    await db.refresh(r)
    return r


@router.patch("/{reporte_id}", response_model=ConductaOut)
async def actualizar_reporte(
    reporte_id: uuid.UUID,
    data: ConductaUpdate,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    r = await db.get(ReporteConducta, reporte_id)
    if not r or not r.is_active:
        raise HTTPException(status_code=404, detail="Reporte no encontrado")
    for field, value in data.model_dump(exclude_none=True).items():
        setattr(r, field, value)
    await db.commit()
    await db.refresh(r)
    return r
