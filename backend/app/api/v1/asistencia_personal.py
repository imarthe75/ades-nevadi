"""
FASE 30 — Control de Asistencia de Personal (DP-005)

  GET    /asistencia-personal                   — listar (filtros: persona_id, fecha_inicio, fecha_fin)
  POST   /asistencia-personal                   — registrar asistencia (upsert por persona+fecha)
  GET    /asistencia-personal/reporte           — reporte mensual (persona_id + mes + año)
  GET    /asistencia-personal/{id}              — detalle
  PATCH  /asistencia-personal/{id}              — actualizar (justificar, corregir hora)
  DELETE /asistencia-personal/{id}              — soft delete
"""
from __future__ import annotations

import uuid
from datetime import date, time
from typing import Optional

from fastapi import APIRouter, Depends, HTTPException, Query
from pydantic import BaseModel
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.core.security import AdesUser, get_ades_user

router = APIRouter(prefix="/asistencia-personal", tags=["asistencia personal"])

_JORNADAS = ["COMPLETA", "MEDIA", "NINGUNA", "INCAPACIDAD", "VACACIONES", "PERMISO"]


# ── Schemas ───────────────────────────────────────────────────────────────────

class AsistenciaCreate(BaseModel):
    persona_id:     uuid.UUID
    fecha:          date
    hora_entrada:   Optional[time] = None
    hora_salida:    Optional[time] = None
    tipo_jornada:   str = "COMPLETA"
    es_retardo:     bool = False
    minutos_retardo: int = 0
    observaciones:  Optional[str] = None

    class Config:
        json_encoders = {time: str}


class AsistenciaPatch(BaseModel):
    hora_entrada:   Optional[time] = None
    hora_salida:    Optional[time] = None
    tipo_jornada:   Optional[str] = None
    es_retardo:     Optional[bool] = None
    minutos_retardo: Optional[int] = None
    justificado:    Optional[bool] = None
    justificacion:  Optional[str] = None
    observaciones:  Optional[str] = None

    class Config:
        json_encoders = {time: str}


class AsistenciaOut(BaseModel):
    id:              uuid.UUID
    persona_id:      uuid.UUID
    fecha:           date
    hora_entrada:    Optional[str]
    hora_salida:     Optional[str]
    tipo_jornada:    str
    es_retardo:      bool
    minutos_retardo: int
    justificado:     bool
    justificacion:   Optional[str]
    observaciones:   Optional[str]
    row_version:     int
    fecha_creacion:  str

    class Config:
        from_attributes = True


class ReportePersonal(BaseModel):
    persona_id:     uuid.UUID
    mes:            int
    anio:           int
    total_dias:     int
    dias_asistio:   int
    dias_falta:     int
    dias_incapacidad: int
    dias_vacaciones: int
    dias_permiso:   int
    total_retardos: int
    porcentaje_asistencia: float


# ── Helpers ───────────────────────────────────────────────────────────────────

async def _get_or_404(db: AsyncSession, rec_id: uuid.UUID) -> dict:
    row = await db.execute(
        text("SELECT * FROM public.ades_asistencia_personal WHERE id = :id AND is_active = TRUE"),
        {"id": str(rec_id)},
    )
    rec = row.mappings().first()
    if not rec:
        raise HTTPException(status_code=404, detail="Registro de asistencia no encontrado")
    return dict(rec)


# ── Endpoints ─────────────────────────────────────────────────────────────────

@router.get("", response_model=list[AsistenciaOut])
async def listar_asistencias(
    persona_id:   Optional[uuid.UUID] = Query(None),
    fecha_inicio: Optional[date]      = Query(None),
    fecha_fin:    Optional[date]      = Query(None),
    tipo_jornada: Optional[str]       = Query(None),
    db:           AsyncSession        = Depends(get_db),
    _user:        AdesUser            = Depends(get_ades_user),
):
    filters = ["is_active = TRUE"]
    params: dict = {}
    if persona_id:
        filters.append("persona_id = :pid"); params["pid"] = str(persona_id)
    if fecha_inicio:
        filters.append("fecha >= :fi"); params["fi"] = fecha_inicio
    if fecha_fin:
        filters.append("fecha <= :ff"); params["ff"] = fecha_fin
    if tipo_jornada:
        filters.append("tipo_jornada = :tj"); params["tj"] = tipo_jornada

    rows = await db.execute(
        text(f"SELECT * FROM public.ades_asistencia_personal WHERE {' AND '.join(filters)} ORDER BY fecha DESC, persona_id"),
        params,
    )
    return [dict(r) for r in rows.mappings().all()]


@router.post("", response_model=AsistenciaOut, status_code=201)
async def registrar_asistencia(
    data: AsistenciaCreate,
    db:   AsyncSession = Depends(get_db),
    user: AdesUser     = Depends(get_ades_user),
):
    if data.tipo_jornada not in _JORNADAS:
        raise HTTPException(status_code=422, detail=f"tipo_jornada inválido. Opciones: {_JORNADAS}")
    row = await db.execute(
        text("""
            INSERT INTO public.ades_asistencia_personal
                (persona_id, fecha, hora_entrada, hora_salida, tipo_jornada,
                 es_retardo, minutos_retardo, observaciones, usuario_creacion)
            VALUES
                (:pid, :fecha, :entrada, :salida, :jornada,
                 :retardo, :mins, :obs, :usr)
            ON CONFLICT (persona_id, fecha) DO UPDATE
            SET hora_entrada = EXCLUDED.hora_entrada,
                hora_salida  = EXCLUDED.hora_salida,
                tipo_jornada = EXCLUDED.tipo_jornada,
                es_retardo   = EXCLUDED.es_retardo,
                minutos_retardo = EXCLUDED.minutos_retardo,
                observaciones = EXCLUDED.observaciones,
                usuario_modificacion = :usr,
                row_version = ades_asistencia_personal.row_version + 1
            RETURNING *
        """),
        {
            "pid":    str(data.persona_id), "fecha": data.fecha,
            "entrada": str(data.hora_entrada) if data.hora_entrada else None,
            "salida":  str(data.hora_salida) if data.hora_salida else None,
            "jornada": data.tipo_jornada, "retardo": data.es_retardo,
            "mins":    data.minutos_retardo, "obs": data.observaciones,
            "usr":     user.username,
        },
    )
    await db.commit()
    return dict(row.mappings().first())


@router.get("/reporte", response_model=ReportePersonal)
async def reporte_mensual(
    persona_id: uuid.UUID = Query(...),
    mes:        int       = Query(..., ge=1, le=12),
    anio:       int       = Query(..., ge=2020),
    db:         AsyncSession = Depends(get_db),
    _user:      AdesUser     = Depends(get_ades_user),
):
    rows = await db.execute(
        text("""
            SELECT tipo_jornada, es_retardo
            FROM public.ades_asistencia_personal
            WHERE persona_id = :pid
              AND EXTRACT(MONTH FROM fecha) = :mes
              AND EXTRACT(YEAR  FROM fecha) = :anio
              AND is_active = TRUE
        """),
        {"pid": str(persona_id), "mes": mes, "anio": anio},
    )
    records = rows.mappings().all()
    total_dias   = len(records)
    dias_falta   = sum(1 for r in records if r["tipo_jornada"] == "NINGUNA")
    dias_inc     = sum(1 for r in records if r["tipo_jornada"] == "INCAPACIDAD")
    dias_vac     = sum(1 for r in records if r["tipo_jornada"] == "VACACIONES")
    dias_perm    = sum(1 for r in records if r["tipo_jornada"] == "PERMISO")
    dias_asistio = sum(1 for r in records if r["tipo_jornada"] in ("COMPLETA", "MEDIA"))
    retardos     = sum(1 for r in records if r["es_retardo"])
    pct          = round(dias_asistio / total_dias * 100, 1) if total_dias > 0 else 0.0

    return ReportePersonal(
        persona_id=persona_id, mes=mes, anio=anio,
        total_dias=total_dias, dias_asistio=dias_asistio,
        dias_falta=dias_falta, dias_incapacidad=dias_inc,
        dias_vacaciones=dias_vac, dias_permiso=dias_perm,
        total_retardos=retardos, porcentaje_asistencia=pct,
    )


@router.get("/{rec_id}", response_model=AsistenciaOut)
async def detalle_asistencia(
    rec_id: uuid.UUID,
    db:    AsyncSession = Depends(get_db),
    _user: AdesUser     = Depends(get_ades_user),
):
    return await _get_or_404(db, rec_id)


@router.patch("/{rec_id}", response_model=AsistenciaOut)
async def actualizar_asistencia(
    rec_id: uuid.UUID,
    data:   AsistenciaPatch,
    db:     AsyncSession = Depends(get_db),
    user:   AdesUser     = Depends(get_ades_user),
):
    await _get_or_404(db, rec_id)
    if data.tipo_jornada and data.tipo_jornada not in _JORNADAS:
        raise HTTPException(status_code=422, detail=f"tipo_jornada inválido. Opciones: {_JORNADAS}")

    sets = ["usuario_modificacion = :usr", "row_version = row_version + 1"]
    params: dict = {"id": str(rec_id), "usr": user.username}

    for field, col in [
        ("tipo_jornada","tipo_jornada"), ("es_retardo","es_retardo"),
        ("minutos_retardo","minutos_retardo"), ("justificado","justificado"),
        ("justificacion","justificacion"), ("observaciones","observaciones"),
    ]:
        val = getattr(data, field)
        if val is not None:
            sets.append(f"{col} = :{field}"); params[field] = val

    if data.hora_entrada is not None:
        sets.append("hora_entrada = :entrada"); params["entrada"] = str(data.hora_entrada)
    if data.hora_salida is not None:
        sets.append("hora_salida = :salida"); params["salida"] = str(data.hora_salida)
    if data.justificado and user.nivel_acceso > 3:
        raise HTTPException(status_code=403, detail="Solo Coordinador o superior puede justificar asistencias")
    if data.justificado:
        sets.append("justificado_por = :jby"); params["jby"] = str(user.id)

    row = await db.execute(
        text(f"UPDATE public.ades_asistencia_personal SET {', '.join(sets)} WHERE id = :id RETURNING *"),
        params,
    )
    await db.commit()
    return dict(row.mappings().first())


@router.delete("/{rec_id}", status_code=204)
async def eliminar_asistencia(
    rec_id: uuid.UUID,
    db:    AsyncSession = Depends(get_db),
    user:  AdesUser     = Depends(get_ades_user),
):
    await _get_or_404(db, rec_id)
    await db.execute(
        text("UPDATE public.ades_asistencia_personal SET is_active=FALSE, usuario_modificacion=:usr, row_version=row_version+1 WHERE id=:id"),
        {"id": str(rec_id), "usr": user.username},
    )
    await db.commit()
