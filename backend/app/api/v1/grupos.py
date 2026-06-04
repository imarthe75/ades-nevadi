from __future__ import annotations
import uuid
from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from app.core.database import get_db
from app.core.security import get_current_user
from app.models.academica import Grupo, Grado, NivelEducativo, CicloEscolar
from app.schemas.academica import GrupoOut, GrupoCreate, GrupoDetalle

router = APIRouter(prefix="/grupos", tags=["grupos"])


@router.get("", response_model=list[GrupoOut])
async def listar_grupos(
    plantel_id: uuid.UUID | None = None,
    nivel: str | None = Query(None, description="PRIMARIA | SECUNDARIA | PREPARATORIA"),
    grado_num: int | None = None,
    solo_activos: bool = True,
    ciclo_vigente: bool = False,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    q = (
        select(Grupo)
        .join(Grado, Grado.id == Grupo.grado_id)
        .join(NivelEducativo, NivelEducativo.id == Grado.nivel_educativo_id)
        .join(CicloEscolar, CicloEscolar.id == Grupo.ciclo_escolar_id)
    )
    if solo_activos:
        q = q.where(Grupo.is_active == True)
    if plantel_id:
        q = q.where(Grado.plantel_id == plantel_id)
    if nivel:
        q = q.where(NivelEducativo.nombre_nivel == nivel.upper())
    if grado_num:
        q = q.where(Grado.numero_grado == grado_num)
    if ciclo_vigente:
        q = q.where(CicloEscolar.es_vigente == True)
    q = q.order_by(NivelEducativo.nombre_nivel, Grado.numero_grado, Grupo.nombre_grupo)
    rows = await db.execute(q)
    return rows.scalars().all()


@router.get("/{grupo_id}", response_model=GrupoDetalle)
async def obtener_grupo(
    grupo_id: uuid.UUID,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    row = await db.get(Grupo, grupo_id)
    if not row:
        raise HTTPException(status_code=404, detail="Grupo no encontrado")
    return row
