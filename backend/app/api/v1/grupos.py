from __future__ import annotations
import uuid
from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, func
from sqlalchemy.orm import selectinload
from app.core.database import get_db
from app.core.security import get_current_user, get_ades_user, AdesUser
from app.models.academica import Grupo, Grado, NivelEducativo, CicloEscolar
from app.models.personas import Inscripcion
from app.schemas.academica import GrupoOut, GrupoCreate, GrupoDetalle

router = APIRouter(prefix="/grupos", tags=["grupos"])


@router.get("", response_model=list[GrupoDetalle])
async def listar_grupos(
    plantel_id: uuid.UUID | None = None,
    nivel: str | None = Query(None, description="PRIMARIA | SECUNDARIA | PREPARATORIA"),
    grado_num: int | None = None,
    solo_activos: bool = True,
    ciclo_vigente: bool = False,
    db: AsyncSession = Depends(get_db),
    ades_user: AdesUser = Depends(get_ades_user),
):
    q = (
        select(Grupo)
        .options(
            selectinload(Grupo.grado).selectinload(Grado.nivel)
        )
        .join(Grado, Grado.id == Grupo.grado_id)
        .join(NivelEducativo, NivelEducativo.id == Grado.nivel_educativo_id)
        .join(CicloEscolar, CicloEscolar.id == Grupo.ciclo_escolar_id)
    )
    if solo_activos:
        q = q.where(Grupo.is_active == True)

    # RBAC scope — tiene prioridad sobre params opcionales
    if ades_user.tiene_scope_nivel:
        q = q.where(Grado.plantel_id == ades_user.plantel_id)
        q = q.where(NivelEducativo.id == ades_user.nivel_educativo_id)
    elif ades_user.plantel_id:
        q = q.where(Grado.plantel_id == ades_user.plantel_id)
    else:
        # ADMIN_GLOBAL — respeta filtros manuales
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
    grupos = rows.scalars().all()

    if not grupos:
        return []

    # Contar inscritos activos por grupo en una sola query
    grupo_ids = [g.id for g in grupos]
    counts_q = (
        select(Inscripcion.grupo_id, func.count(Inscripcion.id).label("total"))
        .where(Inscripcion.grupo_id.in_(grupo_ids), Inscripcion.is_active == True)
        .group_by(Inscripcion.grupo_id)
    )
    counts_map = {row.grupo_id: row.total for row in (await db.execute(counts_q)).all()}

    result = []
    for g in grupos:
        out = GrupoDetalle.model_validate(g)
        out.inscritos = counts_map.get(g.id, 0)
        if g.grado:
            out.nombre_grado = g.grado.nombre_grado
            out.numero_grado = g.grado.numero_grado
            if g.grado.nivel:
                out.nombre_nivel = g.grado.nivel.nombre_nivel
        result.append(out)
    return result


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
