"""
/calendario — Gestión del calendario escolar (ID-015)

  GET    /calendario                         — eventos del ciclo activo (filtrable)
  POST   /calendario                         — crear evento ad-hoc
  PATCH  /calendario/{id}                    — editar evento
  DELETE /calendario/{id}                    — baja lógica

Tipos válidos: DIA_FESTIVO, VACACIONES, INICIO_CLASES, FIN_CLASES,
               CONSEJO_TECNICO, SUSPENSION
"""
from __future__ import annotations

import datetime
from typing import Optional
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, field_validator
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.core.security import get_current_user, get_ades_user, AdesUser

router = APIRouter(prefix="/calendario", tags=["calendario"])

_TIPOS_VALIDOS = frozenset({
    "DIA_FESTIVO", "VACACIONES", "INICIO_CLASES",
    "FIN_CLASES", "CONSEJO_TECNICO", "SUSPENSION",
})


class EventoCreate(BaseModel):
    ciclo_escolar_id: UUID
    fecha_evento: datetime.date
    nombre_evento: str
    tipo_evento: str
    aplica_todos_planteles: bool = True
    plantel_id: Optional[UUID] = None

    @field_validator("tipo_evento")
    @classmethod
    def tipo_valido(cls, v: str) -> str:
        if v not in _TIPOS_VALIDOS:
            raise ValueError(f"tipo_evento debe ser uno de: {', '.join(sorted(_TIPOS_VALIDOS))}")
        return v


class EventoUpdate(BaseModel):
    fecha_evento: Optional[datetime.date] = None
    nombre_evento: Optional[str] = None
    tipo_evento: Optional[str] = None
    aplica_todos_planteles: Optional[bool] = None
    plantel_id: Optional[UUID] = None

    @field_validator("tipo_evento")
    @classmethod
    def tipo_valido(cls, v: Optional[str]) -> Optional[str]:
        if v is not None and v not in _TIPOS_VALIDOS:
            raise ValueError(f"tipo_evento debe ser uno de: {', '.join(sorted(_TIPOS_VALIDOS))}")
        return v


# ── GET /calendario ───────────────────────────────────────────────────────────
@router.get("")
async def listar_eventos(
    ciclo_escolar_id: Optional[UUID] = None,
    plantel_id: Optional[UUID] = None,
    tipo_evento: Optional[str] = None,
    fecha_desde: Optional[datetime.date] = None,
    fecha_hasta: Optional[datetime.date] = None,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    filters = ["ce.is_active = TRUE"]
    params: dict = {}

    if ciclo_escolar_id:
        filters.append("ce.ciclo_escolar_id = :ciclo_id::uuid")
        params["ciclo_id"] = str(ciclo_escolar_id)
    if tipo_evento:
        filters.append("ce.tipo_evento = :tipo")
        params["tipo"] = tipo_evento
    if fecha_desde:
        filters.append("ce.fecha_evento >= :desde")
        params["desde"] = fecha_desde
    if fecha_hasta:
        filters.append("ce.fecha_evento <= :hasta")
        params["hasta"] = fecha_hasta
    if plantel_id:
        filters.append(
            "(ce.aplica_todos_planteles = TRUE OR ce.plantel_id = :plantel_id::uuid)"
        )
        params["plantel_id"] = str(plantel_id)

    where = " AND ".join(filters)

    rows = await db.execute(text(f"""
        SELECT
            ce.id, ce.ciclo_escolar_id, c.nombre_ciclo,
            ce.fecha_evento, ce.nombre_evento, ce.tipo_evento,
            ce.aplica_todos_planteles,
            ce.plantel_id, p.nombre_plantel,
            ce.row_version
        FROM ades_calendario_escolar ce
        JOIN ades_ciclos_escolares c ON c.id = ce.ciclo_escolar_id
        LEFT JOIN ades_planteles p   ON p.id = ce.plantel_id
        WHERE {where}
        ORDER BY ce.fecha_evento ASC
    """), params)

    return rows.mappings().all()


# ── POST /calendario ──────────────────────────────────────────────────────────
@router.post("", status_code=201)
async def crear_evento(
    body: EventoCreate,
    db: AsyncSession = Depends(get_db),
    ades_user: AdesUser = Depends(get_ades_user),
):
    if ades_user.nivel_acceso > 3:
        raise HTTPException(403, "Solo DIRECTOR/COORDINADOR/ADMIN puede crear eventos")

    row = await db.execute(text("""
        INSERT INTO ades_calendario_escolar
            (ciclo_escolar_id, fecha_evento, nombre_evento, tipo_evento,
             aplica_todos_planteles, plantel_id)
        VALUES
            (:ciclo_id::uuid, :fecha, :nombre, :tipo, :todos, :plantel_id::uuid)
        RETURNING id, nombre_evento, tipo_evento, fecha_evento
    """), {
        "ciclo_id":  str(body.ciclo_escolar_id),
        "fecha":     body.fecha_evento,
        "nombre":    body.nombre_evento,
        "tipo":      body.tipo_evento,
        "todos":     body.aplica_todos_planteles,
        "plantel_id": str(body.plantel_id) if body.plantel_id else None,
    })
    await db.commit()
    return row.mappings().first()


# ── PATCH /calendario/{id} ────────────────────────────────────────────────────
@router.patch("/{evento_id}")
async def editar_evento(
    evento_id: UUID,
    body: EventoUpdate,
    db: AsyncSession = Depends(get_db),
    ades_user: AdesUser = Depends(get_ades_user),
):
    if ades_user.nivel_acceso > 3:
        raise HTTPException(403, "Sin permiso para editar eventos")

    sets, params = [], {"id": str(evento_id)}
    if body.fecha_evento is not None:
        sets.append("fecha_evento = :fecha"); params["fecha"] = body.fecha_evento
    if body.nombre_evento is not None:
        sets.append("nombre_evento = :nombre"); params["nombre"] = body.nombre_evento
    if body.tipo_evento is not None:
        sets.append("tipo_evento = :tipo"); params["tipo"] = body.tipo_evento
    if body.aplica_todos_planteles is not None:
        sets.append("aplica_todos_planteles = :todos"); params["todos"] = body.aplica_todos_planteles
    if body.plantel_id is not None:
        sets.append("plantel_id = :plantel_id::uuid"); params["plantel_id"] = str(body.plantel_id)
    if not sets:
        raise HTTPException(422, "Ningún campo para actualizar")

    sets.append("fecha_modificacion = now()")
    sets.append("row_version = row_version + 1")

    result = await db.execute(
        text(f"UPDATE ades_calendario_escolar SET {', '.join(sets)} WHERE id = :id::uuid AND is_active = TRUE RETURNING id"),
        params,
    )
    if not result.fetchone():
        raise HTTPException(404, "Evento no encontrado")
    await db.commit()
    return {"ok": True}


# ── DELETE /calendario/{id} ───────────────────────────────────────────────────
@router.delete("/{evento_id}", status_code=204)
async def eliminar_evento(
    evento_id: UUID,
    db: AsyncSession = Depends(get_db),
    ades_user: AdesUser = Depends(get_ades_user),
):
    if ades_user.nivel_acceso > 3:
        raise HTTPException(403, "Sin permiso para eliminar eventos")

    await db.execute(
        text("UPDATE ades_calendario_escolar SET is_active = FALSE WHERE id = :id::uuid"),
        {"id": str(evento_id)},
    )
    await db.commit()
