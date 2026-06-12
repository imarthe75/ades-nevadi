"""
FASE 29 — Gestión de Licencias y Permisos de Personal (DP-006)

  GET    /licencias                           — listar (filtros: personal_id, estado, tipo)
  POST   /licencias                           — crear solicitud
  GET    /licencias/{id}                      — detalle
  PATCH  /licencias/{id}                      — actualizar (RH: estado, observaciones)
  POST   /licencias/{id}/aprobar             — aprobar (Director/RH)
  POST   /licencias/{id}/rechazar            — rechazar con motivo
  DELETE /licencias/{id}                      — cancelar (solo PENDIENTE)
"""
from __future__ import annotations

import uuid
from datetime import date
from typing import Optional

from fastapi import APIRouter, Depends, HTTPException, Query
from pydantic import BaseModel, model_validator
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.core.security import AdesUser, get_ades_user

router = APIRouter(prefix="/licencias", tags=["licencias personal"])

_TIPOS = ["MEDICA","MATERNIDAD","PATERNIDAD","DUELO","PERSONAL","COMISION","CAPACITACION","OTRO"]
_ESTADOS = ["PENDIENTE","APROBADA","RECHAZADA","CANCELADA"]


# ── Schemas ───────────────────────────────────────────────────────────────────

class LicenciaCreate(BaseModel):
    personal_id:     uuid.UUID
    tipo_licencia:   str
    fecha_inicio:    date
    fecha_fin:       date
    motivo:          Optional[str] = None
    sustituto_id:    Optional[uuid.UUID] = None
    con_goce_sueldo: bool = True

    @model_validator(mode="after")
    def validar_fechas(self):
        if self.fecha_fin < self.fecha_inicio:
            raise ValueError("fecha_fin debe ser >= fecha_inicio")
        if self.tipo_licencia not in _TIPOS:
            raise ValueError(f"tipo_licencia inválido. Opciones: {_TIPOS}")
        return self


class LicenciaPatch(BaseModel):
    motivo:            Optional[str] = None
    observaciones_rh:  Optional[str] = None
    sustituto_id:      Optional[uuid.UUID] = None
    con_goce_sueldo:   Optional[bool] = None


class LicenciaOut(BaseModel):
    id:               uuid.UUID
    personal_id:      uuid.UUID
    tipo_licencia:    str
    fecha_inicio:     date
    fecha_fin:        date
    dias_habiles:     int
    estado:           str
    motivo:           Optional[str]
    observaciones_rh: Optional[str]
    sustituto_id:     Optional[uuid.UUID]
    aprobado_por:     Optional[uuid.UUID]
    fecha_aprobacion: Optional[str]
    con_goce_sueldo:  bool
    row_version:      int
    fecha_creacion:   str

    class Config:
        from_attributes = True


# ── Helpers ───────────────────────────────────────────────────────────────────

def _calcular_dias_habiles(inicio: date, fin: date) -> int:
    from datetime import timedelta
    dias = 0
    current = inicio
    while current <= fin:
        if current.weekday() < 5:  # Lunes–Viernes
            dias += 1
        current += timedelta(days=1)
    return max(dias, 1)


async def _get_or_404(db: AsyncSession, licencia_id: uuid.UUID) -> dict:
    row = await db.execute(
        text("SELECT * FROM public.ades_licencias_personal WHERE id = :id AND is_active = TRUE"),
        {"id": str(licencia_id)},
    )
    rec = row.mappings().first()
    if not rec:
        raise HTTPException(status_code=404, detail="Licencia no encontrada")
    return dict(rec)


# ── Endpoints ─────────────────────────────────────────────────────────────────

@router.get("", response_model=list[LicenciaOut])
async def listar_licencias(
    personal_id: Optional[uuid.UUID] = Query(None),
    estado:      Optional[str]       = Query(None),
    tipo:        Optional[str]       = Query(None),
    db:          AsyncSession        = Depends(get_db),
    _user:       AdesUser            = Depends(get_ades_user),
):
    filters = ["is_active = TRUE"]
    params: dict = {}
    if personal_id:
        filters.append("personal_id = :pid")
        params["pid"] = str(personal_id)
    if estado:
        filters.append("estado = :estado")
        params["estado"] = estado
    if tipo:
        filters.append("tipo_licencia = :tipo")
        params["tipo"] = tipo

    q = f"SELECT * FROM public.ades_licencias_personal WHERE {' AND '.join(filters)} ORDER BY fecha_creacion DESC"
    rows = await db.execute(text(q), params)
    return [dict(r) for r in rows.mappings().all()]


@router.post("", response_model=LicenciaOut, status_code=201)
async def crear_licencia(
    data:  LicenciaCreate,
    db:    AsyncSession = Depends(get_db),
    user:  AdesUser     = Depends(get_ades_user),
):
    dias = _calcular_dias_habiles(data.fecha_inicio, data.fecha_fin)
    row = await db.execute(
        text("""
            INSERT INTO public.ades_licencias_personal
                (personal_id, tipo_licencia, fecha_inicio, fecha_fin, dias_habiles,
                 motivo, sustituto_id, con_goce_sueldo, usuario_creacion)
            VALUES
                (:pid, :tipo, :fi, :ff, :dias,
                 :motivo, :sus, :goce, :usr)
            RETURNING *
        """),
        {
            "pid":   str(data.personal_id),
            "tipo":  data.tipo_licencia,
            "fi":    data.fecha_inicio,
            "ff":    data.fecha_fin,
            "dias":  dias,
            "motivo": data.motivo,
            "sus":   str(data.sustituto_id) if data.sustituto_id else None,
            "goce":  data.con_goce_sueldo,
            "usr":   user.username,
        },
    )
    await db.commit()
    return dict(row.mappings().first())


@router.get("/{licencia_id}", response_model=LicenciaOut)
async def detalle_licencia(
    licencia_id: uuid.UUID,
    db:   AsyncSession = Depends(get_db),
    _user: AdesUser    = Depends(get_ades_user),
):
    return await _get_or_404(db, licencia_id)


@router.patch("/{licencia_id}", response_model=LicenciaOut)
async def actualizar_licencia(
    licencia_id: uuid.UUID,
    data: LicenciaPatch,
    db:   AsyncSession = Depends(get_db),
    user: AdesUser     = Depends(get_ades_user),
):
    rec = await _get_or_404(db, licencia_id)
    if rec["estado"] not in ("PENDIENTE",):
        raise HTTPException(status_code=400, detail="Solo se puede editar una licencia PENDIENTE")

    sets = ["usuario_modificacion = :usr", "row_version = row_version + 1"]
    params: dict = {"id": str(licencia_id), "usr": user.username}

    if data.motivo is not None:
        sets.append("motivo = :motivo"); params["motivo"] = data.motivo
    if data.observaciones_rh is not None:
        sets.append("observaciones_rh = :obs"); params["obs"] = data.observaciones_rh
    if data.sustituto_id is not None:
        sets.append("sustituto_id = :sus"); params["sus"] = str(data.sustituto_id)
    if data.con_goce_sueldo is not None:
        sets.append("con_goce_sueldo = :goce"); params["goce"] = data.con_goce_sueldo

    row = await db.execute(
        text(f"UPDATE public.ades_licencias_personal SET {', '.join(sets)} WHERE id = :id RETURNING *"),
        params,
    )
    await db.commit()
    return dict(row.mappings().first())


@router.post("/{licencia_id}/aprobar", response_model=LicenciaOut)
async def aprobar_licencia(
    licencia_id: uuid.UUID,
    observaciones: Optional[str] = Query(None),
    db:   AsyncSession = Depends(get_db),
    user: AdesUser     = Depends(get_ades_user),
):
    if user.nivel_acceso > 2:
        raise HTTPException(status_code=403, detail="Solo Director o RH puede aprobar licencias")
    rec = await _get_or_404(db, licencia_id)
    if rec["estado"] != "PENDIENTE":
        raise HTTPException(status_code=400, detail="La licencia no está en estado PENDIENTE")

    row = await db.execute(
        text("""
            UPDATE public.ades_licencias_personal
            SET estado = 'APROBADA', aprobado_por = :usr_id, fecha_aprobacion = NOW(),
                observaciones_rh = COALESCE(:obs, observaciones_rh),
                usuario_modificacion = :usr, row_version = row_version + 1
            WHERE id = :id
            RETURNING *
        """),
        {"id": str(licencia_id), "usr_id": str(user.id), "obs": observaciones, "usr": user.username},
    )
    await db.commit()
    return dict(row.mappings().first())


@router.post("/{licencia_id}/rechazar", response_model=LicenciaOut)
async def rechazar_licencia(
    licencia_id: uuid.UUID,
    motivo_rechazo: str = Query(..., min_length=5),
    db:   AsyncSession = Depends(get_db),
    user: AdesUser     = Depends(get_ades_user),
):
    if user.nivel_acceso > 2:
        raise HTTPException(status_code=403, detail="Solo Director o RH puede rechazar licencias")
    rec = await _get_or_404(db, licencia_id)
    if rec["estado"] != "PENDIENTE":
        raise HTTPException(status_code=400, detail="La licencia no está en estado PENDIENTE")

    row = await db.execute(
        text("""
            UPDATE public.ades_licencias_personal
            SET estado = 'RECHAZADA', aprobado_por = :usr_id,
                observaciones_rh = :motivo,
                usuario_modificacion = :usr, row_version = row_version + 1
            WHERE id = :id
            RETURNING *
        """),
        {"id": str(licencia_id), "usr_id": str(user.id), "motivo": motivo_rechazo, "usr": user.username},
    )
    await db.commit()
    return dict(row.mappings().first())


@router.delete("/{licencia_id}", status_code=204)
async def cancelar_licencia(
    licencia_id: uuid.UUID,
    db:   AsyncSession = Depends(get_db),
    user: AdesUser     = Depends(get_ades_user),
):
    rec = await _get_or_404(db, licencia_id)
    if rec["estado"] != "PENDIENTE":
        raise HTTPException(status_code=400, detail="Solo se puede cancelar una licencia PENDIENTE")
    await db.execute(
        text("""
            UPDATE public.ades_licencias_personal
            SET estado = 'CANCELADA', is_active = FALSE,
                usuario_modificacion = :usr, row_version = row_version + 1
            WHERE id = :id
        """),
        {"id": str(licencia_id), "usr": user.username},
    )
    await db.commit()
