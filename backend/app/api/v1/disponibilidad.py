"""
FASE 30 — Disponibilidad Docente (DP-003)

Trabaja sobre la tabla slot-based ades_disponibilidad_docente existente
(profesor_id, dia_semana 0-6, hora_inicio, hora_fin, disponible).
Añade endpoints para:

  GET    /disponibilidad                                — listar slots (filtro: profesor_id, ciclo_id)
  PUT    /disponibilidad/docente/{profesor_id}          — guardar disponibilidad completa (upsert bulk)
  GET    /disponibilidad/docente/{profesor_id}/resumen  — resumen horas + días disponibles
  GET    /disponibilidad/cobertura/{ciclo_id}           — docentes sin horario asignado completo
  DELETE /disponibilidad/{id}                           — soft delete slot
"""
from __future__ import annotations

import uuid
from datetime import time
from typing import Optional

from fastapi import APIRouter, Depends, HTTPException, Query
from pydantic import BaseModel
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.core.security import AdesUser, get_ades_user

router = APIRouter(prefix="/disponibilidad", tags=["disponibilidad docente"])

_DIAS = {0: "Lunes", 1: "Martes", 2: "Miércoles", 3: "Jueves", 4: "Viernes", 5: "Sábado", 6: "Domingo"}


# ── Schemas ───────────────────────────────────────────────────────────────────

class SlotIn(BaseModel):
    dia_semana:  int   # 0=Lunes … 6=Domingo
    hora_inicio: time
    hora_fin:    time
    disponible:  bool = True
    motivo_no_disponible: Optional[str] = None

    class Config:
        json_encoders = {time: str}


class BulkDisponibilidadIn(BaseModel):
    slots:          list[SlotIn]
    ciclo_escolar_id: Optional[uuid.UUID] = None
    horas_semana_max:    Optional[float] = None
    horas_frente_grupo:  Optional[float] = None


class SlotOut(BaseModel):
    id:            uuid.UUID
    profesor_id:   uuid.UUID
    dia_semana:    int
    dia_nombre:    str
    hora_inicio:   str
    hora_fin:      str
    disponible:    bool
    motivo_no_disponible: Optional[str]
    ciclo_escolar_id: Optional[uuid.UUID]

    class Config:
        from_attributes = True


class ResumenDisponibilidad(BaseModel):
    profesor_id:        uuid.UUID
    dias_disponibles:   list[str]
    total_slots:        int
    slots_disponibles:  int
    horas_semana:       float
    horas_semana_max:   float
    horas_frente_grupo: float


# ── Endpoints ─────────────────────────────────────────────────────────────────

@router.get("", response_model=list[SlotOut])
async def listar_slots(
    profesor_id:     Optional[uuid.UUID] = Query(None),
    ciclo_escolar_id: Optional[uuid.UUID] = Query(None),
    db:              AsyncSession         = Depends(get_db),
    _user:           AdesUser             = Depends(get_ades_user),
):
    filters = ["is_active = TRUE"]
    params: dict = {}
    if profesor_id:
        filters.append("profesor_id = :pid"); params["pid"] = str(profesor_id)
    if ciclo_escolar_id:
        filters.append("ciclo_escolar_id = :cid"); params["cid"] = str(ciclo_escolar_id)

    rows = await db.execute(
        text(f"SELECT * FROM public.ades_disponibilidad_docente WHERE {' AND '.join(filters)} ORDER BY dia_semana, hora_inicio"),
        params,
    )
    result = []
    for r in rows.mappings().all():
        d = dict(r)
        d["dia_nombre"] = _DIAS.get(d["dia_semana"], "?")
        d["hora_inicio"] = str(d["hora_inicio"])
        d["hora_fin"]    = str(d["hora_fin"])
        result.append(d)
    return result


@router.put("/docente/{profesor_id}", status_code=200)
async def guardar_disponibilidad(
    profesor_id: uuid.UUID,
    data:        BulkDisponibilidadIn,
    db:          AsyncSession = Depends(get_db),
    user:        AdesUser     = Depends(get_ades_user),
):
    ciclo_str = str(data.ciclo_escolar_id) if data.ciclo_escolar_id else None

    # Soft-delete slots actuales del profesor (mismo ciclo o general)
    if ciclo_str:
        await db.execute(
            text("UPDATE public.ades_disponibilidad_docente SET is_active=FALSE WHERE profesor_id=:pid AND ciclo_escolar_id=:cid"),
            {"pid": str(profesor_id), "cid": ciclo_str},
        )
    else:
        await db.execute(
            text("UPDATE public.ades_disponibilidad_docente SET is_active=FALSE WHERE profesor_id=:pid AND ciclo_escolar_id IS NULL"),
            {"pid": str(profesor_id)},
        )

    # Insertar nuevos slots
    for slot in data.slots:
        await db.execute(
            text("""
                INSERT INTO public.ades_disponibilidad_docente
                    (profesor_id, dia_semana, hora_inicio, hora_fin, disponible,
                     motivo_no_disponible, ciclo_escolar_id, usuario_creacion)
                VALUES (:pid, :dia, :hi, :hf, :disp, :mot, :cid, :usr)
            """),
            {
                "pid": str(profesor_id), "dia": slot.dia_semana,
                "hi": str(slot.hora_inicio), "hf": str(slot.hora_fin),
                "disp": slot.disponible, "mot": slot.motivo_no_disponible,
                "cid": ciclo_str, "usr": user.username,
            },
        )

    # Actualizar horas en ades_profesores si se proporcionan
    if data.horas_semana_max is not None or data.horas_frente_grupo is not None:
        sets = ["usuario_modificacion = :usr", "row_version = row_version + 1"]
        params: dict = {"pid": str(profesor_id), "usr": user.username}
        if data.horas_semana_max is not None:
            sets.append("horas_semana_max = :hsm"); params["hsm"] = data.horas_semana_max
        if data.horas_frente_grupo is not None:
            sets.append("horas_frente_grupo = :hfg"); params["hfg"] = data.horas_frente_grupo
        await db.execute(
            text(f"UPDATE public.ades_profesores SET {', '.join(sets)} WHERE id = :pid"),
            params,
        )

    await db.commit()
    return {"detail": f"{len(data.slots)} slots guardados para docente {profesor_id}"}


@router.get("/docente/{profesor_id}/resumen", response_model=ResumenDisponibilidad)
async def resumen_disponibilidad(
    profesor_id: uuid.UUID,
    ciclo_escolar_id: Optional[uuid.UUID] = Query(None),
    db:          AsyncSession = Depends(get_db),
    _user:       AdesUser     = Depends(get_ades_user),
):
    params: dict = {"pid": str(profesor_id)}
    ciclo_filter = "AND ciclo_escolar_id = :cid" if ciclo_escolar_id else "AND ciclo_escolar_id IS NULL"
    if ciclo_escolar_id:
        params["cid"] = str(ciclo_escolar_id)

    rows = await db.execute(
        text(f"""
            SELECT dia_semana, hora_inicio, hora_fin, disponible
            FROM public.ades_disponibilidad_docente
            WHERE profesor_id = :pid AND is_active = TRUE {ciclo_filter}
            ORDER BY dia_semana, hora_inicio
        """),
        params,
    )
    slots = rows.mappings().all()

    # Calcular horas disponibles
    horas = 0.0
    dias_set: set[int] = set()
    for s in slots:
        if s["disponible"]:
            hi = s["hora_inicio"]
            hf = s["hora_fin"]
            mins = (hf.hour * 60 + hf.minute) - (hi.hour * 60 + hi.minute)
            horas += max(mins, 0) / 60
            dias_set.add(s["dia_semana"])

    prof = await db.execute(
        text("SELECT horas_semana_max, horas_frente_grupo FROM public.ades_profesores WHERE id = :pid"),
        {"pid": str(profesor_id)},
    )
    prof_row = prof.mappings().first() or {}

    return ResumenDisponibilidad(
        profesor_id=profesor_id,
        dias_disponibles=[_DIAS[d] for d in sorted(dias_set)],
        total_slots=len(slots),
        slots_disponibles=sum(1 for s in slots if s["disponible"]),
        horas_semana=round(horas, 1),
        horas_semana_max=float(prof_row.get("horas_semana_max", 20)),
        horas_frente_grupo=float(prof_row.get("horas_frente_grupo", 16)),
    )


@router.get("/cobertura/{ciclo_id}")
async def cobertura_disponibilidad(
    ciclo_id: uuid.UUID,
    db:       AsyncSession = Depends(get_db),
    _user:    AdesUser     = Depends(get_ades_user),
):
    """Devuelve docentes activos sin disponibilidad registrada en el ciclo dado."""
    rows = await db.execute(
        text("""
            SELECT p.id, per.nombre, per.apellido_paterno,
                   COUNT(dd.id) AS slots_registrados
            FROM public.ades_profesores p
            JOIN public.ades_personas per ON per.id = p.persona_id
            LEFT JOIN public.ades_disponibilidad_docente dd
                   ON dd.profesor_id = p.id
                  AND dd.ciclo_escolar_id = :cid
                  AND dd.is_active = TRUE
            WHERE p.is_active = TRUE
            GROUP BY p.id, per.nombre, per.apellido_paterno
            ORDER BY per.apellido_paterno, per.nombre
        """),
        {"cid": str(ciclo_id)},
    )
    result = []
    for r in rows.mappings().all():
        result.append({
            "profesor_id": str(r["id"]),
            "nombre_completo": f"{r['apellido_paterno']} {r['nombre']}",
            "slots_registrados": r["slots_registrados"],
            "tiene_disponibilidad": r["slots_registrados"] > 0,
        })
    return result


@router.delete("/{slot_id}", status_code=204)
async def eliminar_slot(
    slot_id: uuid.UUID,
    db:     AsyncSession = Depends(get_db),
    user:   AdesUser     = Depends(get_ades_user),
):
    await db.execute(
        text("UPDATE public.ades_disponibilidad_docente SET is_active=FALSE WHERE id=:id"),
        {"id": str(slot_id)},
    )
    await db.commit()
