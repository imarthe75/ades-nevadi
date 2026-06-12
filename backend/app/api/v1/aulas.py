"""
/aulas — Gestión de aulas y espacios físicos (AC-006).

  GET    /aulas                         — lista aulas con filtros
  POST   /aulas                         — crear aula
  GET    /aulas/{id}                    — detalle + franjas
  PATCH  /aulas/{id}                    — actualizar aula
  DELETE /aulas/{id}                    — soft delete
  GET    /aulas/vista/ocupacion         — vista resumen pct_ocupacion por plantel
  POST   /aulas/{id}/disponibilidad     — asignar franja horaria
  DELETE /aulas/disponibilidad/{franja_id} — liberar franja
  POST   /aulas/{id}/verificar-conflicto   — verificar solapamiento antes de asignar
"""
from __future__ import annotations

import uuid
from typing import Optional

from fastapi import APIRouter, Depends, HTTPException, Query, status
from pydantic import BaseModel, Field
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.core.security import get_current_user, get_ades_user, AdesUser

router = APIRouter(prefix="/aulas", tags=["aulas"])

_NIVEL_ADMIN = 3   # COORDINADOR o superior puede gestionar aulas


# ── Schemas ───────────────────────────────────────────────────────────────────

class AulaCreate(BaseModel):
    plantel_id: uuid.UUID
    nombre_aula: str = Field(min_length=2, max_length=80)
    clave_aula: Optional[str] = Field(None, max_length=20)
    piso: int = 1
    edificio: Optional[str] = Field(None, max_length=40)
    capacidad_alumnos: int = Field(30, ge=1, le=500)
    capacidad_maxima: Optional[int] = None
    tipo_aula: str = "SALON"
    tiene_proyector: bool = False
    tiene_pizarra_digital: bool = False
    tiene_pizarron: bool = True
    tiene_aire_acondicionado: bool = False
    tiene_ventiladores: bool = False
    tiene_internet: bool = False
    num_computadoras: int = 0
    estado_aula: str = "ACTIVA"
    observaciones: Optional[str] = None


class AulaUpdate(BaseModel):
    nombre_aula: Optional[str] = Field(None, min_length=2, max_length=80)
    clave_aula: Optional[str] = Field(None, max_length=20)
    piso: Optional[int] = None
    edificio: Optional[str] = None
    capacidad_alumnos: Optional[int] = Field(None, ge=1, le=500)
    capacidad_maxima: Optional[int] = None
    tipo_aula: Optional[str] = None
    tiene_proyector: Optional[bool] = None
    tiene_pizarra_digital: Optional[bool] = None
    tiene_pizarron: Optional[bool] = None
    tiene_aire_acondicionado: Optional[bool] = None
    tiene_ventiladores: Optional[bool] = None
    tiene_internet: Optional[bool] = None
    num_computadoras: Optional[int] = None
    estado_aula: Optional[str] = None
    observaciones: Optional[str] = None
    is_active: Optional[bool] = None


class FranjaCreate(BaseModel):
    grupo_id: Optional[uuid.UUID] = None
    ciclo_escolar_id: Optional[uuid.UUID] = None
    dia_semana: int = Field(ge=1, le=7)
    hora_inicio: str = Field(pattern=r"^\d{2}:\d{2}$")
    hora_fin: str = Field(pattern=r"^\d{2}:\d{2}$")
    motivo_bloqueo: Optional[str] = Field(None, max_length=80)


class ConflictoCheck(BaseModel):
    dia_semana: int = Field(ge=1, le=7)
    hora_inicio: str
    hora_fin: str
    excluir_id: Optional[uuid.UUID] = None


# ── Endpoints ─────────────────────────────────────────────────────────────────

@router.get("")
async def listar_aulas(
    plantel_id: Optional[uuid.UUID] = None,
    tipo_aula:  Optional[str]       = None,
    estado_aula: Optional[str]      = None,
    con_proyector: Optional[bool]   = None,
    con_internet: Optional[bool]    = None,
    capacidad_min: Optional[int]    = None,
    pagina: int   = Query(1, ge=1),
    por_pagina: int = Query(50, ge=1, le=200),
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    conditions = ["a.is_active = TRUE"]
    params: dict = {}

    if plantel_id:
        conditions.append("a.plantel_id = :plantel_id::uuid")
        params["plantel_id"] = str(plantel_id)
    if tipo_aula:
        conditions.append("a.tipo_aula = :tipo_aula")
        params["tipo_aula"] = tipo_aula.upper()
    if estado_aula:
        conditions.append("a.estado_aula = :estado_aula")
        params["estado_aula"] = estado_aula.upper()
    if con_proyector is not None:
        conditions.append("a.tiene_proyector = :con_proyector")
        params["con_proyector"] = con_proyector
    if con_internet is not None:
        conditions.append("a.tiene_internet = :con_internet")
        params["con_internet"] = con_internet
    if capacidad_min is not None:
        conditions.append("a.capacidad_alumnos >= :cap_min")
        params["cap_min"] = capacidad_min

    where = " AND ".join(conditions)
    params["offset"] = (pagina - 1) * por_pagina
    params["limit"]  = por_pagina

    rows = await db.execute(text(f"""
        SELECT
            a.*,
            pl.nombre_plantel,
            COUNT(da.id) FILTER (WHERE da.is_active = TRUE) AS franjas_ocupadas
        FROM ades_aulas a
        JOIN ades_planteles pl ON pl.id = a.plantel_id
        LEFT JOIN ades_disponibilidad_aula da ON da.aula_id = a.id
        WHERE {where}
        GROUP BY a.id, pl.nombre_plantel
        ORDER BY pl.nombre_plantel, a.piso, a.nombre_aula
        LIMIT :limit OFFSET :offset
    """), params)
    return [dict(r) for r in rows.mappings().all()]


@router.get("/vista/ocupacion")
async def vista_ocupacion(
    plantel_id: Optional[uuid.UUID] = None,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    """Vista resumen de % ocupación de aulas por plantel."""
    cond = "WHERE 1=1"
    params: dict = {}
    if plantel_id:
        cond += " AND plantel_id = :plantel_id::uuid"
        params["plantel_id"] = str(plantel_id)
    rows = await db.execute(text(f"SELECT * FROM ades_v_ocupacion_aulas {cond} ORDER BY nombre_plantel, nombre_aula"), params)
    return [dict(r) for r in rows.mappings().all()]


@router.get("/{aula_id}")
async def obtener_aula(
    aula_id: uuid.UUID,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    row = await db.execute(text("""
        SELECT a.*, pl.nombre_plantel
        FROM ades_aulas a
        JOIN ades_planteles pl ON pl.id = a.plantel_id
        WHERE a.id = :id::uuid AND a.is_active = TRUE
    """), {"id": str(aula_id)})
    aula = row.mappings().first()
    if not aula:
        raise HTTPException(404, "Aula no encontrada")

    # Franjas de disponibilidad
    franjas_rows = await db.execute(text("""
        SELECT da.*,
               g.nombre_grupo,
               g.grado_id
          FROM ades_disponibilidad_aula da
          LEFT JOIN ades_grupos g ON g.id = da.grupo_id
         WHERE da.aula_id = :id::uuid AND da.is_active = TRUE
         ORDER BY da.dia_semana, da.hora_inicio
    """), {"id": str(aula_id)})

    return {
        **dict(aula),
        "franjas": [dict(f) for f in franjas_rows.mappings().all()],
    }


@router.post("", status_code=status.HTTP_201_CREATED)
async def crear_aula(
    body: AulaCreate,
    db: AsyncSession = Depends(get_db),
    ades_user: AdesUser = Depends(get_ades_user),
):
    if ades_user.nivel_acceso > _NIVEL_ADMIN:
        raise HTTPException(403, "Solo COORDINADOR/DIRECTOR/ADMIN puede crear aulas")

    row = await db.execute(text("""
        INSERT INTO ades_aulas
            (plantel_id, nombre_aula, clave_aula, piso, edificio,
             capacidad_alumnos, capacidad_maxima, tipo_aula,
             tiene_proyector, tiene_pizarra_digital, tiene_pizarron,
             tiene_aire_acondicionado, tiene_ventiladores, tiene_internet,
             num_computadoras, estado_aula, observaciones)
        VALUES
            (:plantel_id::uuid, :nombre, :clave, :piso, :edificio,
             :cap, :cap_max, :tipo,
             :proyector, :pizarra_digital, :pizarron,
             :ac, :venti, :internet,
             :num_pc, :estado, :obs)
        RETURNING id, nombre_aula, clave_aula, tipo_aula, capacidad_alumnos, estado_aula
    """), {
        "plantel_id":       str(body.plantel_id),
        "nombre":           body.nombre_aula,
        "clave":            body.clave_aula,
        "piso":             body.piso,
        "edificio":         body.edificio,
        "cap":              body.capacidad_alumnos,
        "cap_max":          body.capacidad_maxima,
        "tipo":             body.tipo_aula.upper(),
        "proyector":        body.tiene_proyector,
        "pizarra_digital":  body.tiene_pizarra_digital,
        "pizarron":         body.tiene_pizarron,
        "ac":               body.tiene_aire_acondicionado,
        "venti":            body.tiene_ventiladores,
        "internet":         body.tiene_internet,
        "num_pc":           body.num_computadoras,
        "estado":           body.estado_aula.upper(),
        "obs":              body.observaciones,
    })
    await db.commit()
    return dict(row.mappings().first())


@router.patch("/{aula_id}")
async def actualizar_aula(
    aula_id: uuid.UUID,
    body: AulaUpdate,
    db: AsyncSession = Depends(get_db),
    ades_user: AdesUser = Depends(get_ades_user),
):
    if ades_user.nivel_acceso > _NIVEL_ADMIN:
        raise HTTPException(403, "Sin permiso para modificar aulas")

    campos = body.model_dump(exclude_none=True)
    if not campos:
        raise HTTPException(422, "Ningún campo para actualizar")

    sets, params = [], {"id": str(aula_id)}
    BOOL_CAMPOS = {
        "tiene_proyector", "tiene_pizarra_digital", "tiene_pizarron",
        "tiene_aire_acondicionado", "tiene_ventiladores", "tiene_internet", "is_active",
    }
    STR_UPPER = {"tipo_aula", "estado_aula"}

    for campo, val in campos.items():
        if campo in STR_UPPER and val is not None:
            val = val.upper()
        sets.append(f"{campo} = :{campo}")
        params[campo] = val

    sets += ["fecha_modificacion = now()", "row_version = row_version + 1"]
    result = await db.execute(
        text(f"UPDATE ades_aulas SET {', '.join(sets)} WHERE id = :id::uuid AND is_active = TRUE RETURNING id"),
        params,
    )
    if not result.fetchone():
        raise HTTPException(404, "Aula no encontrada")
    await db.commit()
    return {"ok": True}


@router.delete("/{aula_id}", status_code=status.HTTP_204_NO_CONTENT)
async def eliminar_aula(
    aula_id: uuid.UUID,
    db: AsyncSession = Depends(get_db),
    ades_user: AdesUser = Depends(get_ades_user),
):
    if ades_user.nivel_acceso > _NIVEL_ADMIN:
        raise HTTPException(403, "Sin permiso para eliminar aulas")
    result = await db.execute(
        text("UPDATE ades_aulas SET is_active = FALSE, fecha_modificacion = now() WHERE id = :id::uuid AND is_active = TRUE"),
        {"id": str(aula_id)},
    )
    if result.rowcount == 0:
        raise HTTPException(404, "Aula no encontrada")
    await db.commit()


# ── Disponibilidad ────────────────────────────────────────────────────────────

@router.post("/{aula_id}/verificar-conflicto")
async def verificar_conflicto(
    aula_id: uuid.UUID,
    body: ConflictoCheck,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    """Llama a detectar_conflicto_aula() antes de asignar una franja."""
    row = await db.execute(text("""
        SELECT * FROM detectar_conflicto_aula(
            :aula_id::uuid, :dia, :h_ini::TIME, :h_fin::TIME,
            :excluir::uuid
        )
    """), {
        "aula_id": str(aula_id),
        "dia":     body.dia_semana,
        "h_ini":   body.hora_inicio,
        "h_fin":   body.hora_fin,
        "excluir": str(body.excluir_id) if body.excluir_id else None,
    })
    return dict(row.mappings().first())


@router.post("/{aula_id}/disponibilidad", status_code=201)
async def asignar_franja(
    aula_id: uuid.UUID,
    body: FranjaCreate,
    db: AsyncSession = Depends(get_db),
    ades_user: AdesUser = Depends(get_ades_user),
):
    if ades_user.nivel_acceso > _NIVEL_ADMIN:
        raise HTTPException(403, "Sin permiso para asignar franjas")

    # Verificar conflicto antes de insertar
    conflicto = await db.execute(text("""
        SELECT conflicto, num_conflictos FROM detectar_conflicto_aula(
            :aula_id::uuid, :dia, :h_ini::TIME, :h_fin::TIME, NULL
        )
    """), {
        "aula_id": str(aula_id),
        "dia":     body.dia_semana,
        "h_ini":   body.hora_inicio,
        "h_fin":   body.hora_fin,
    })
    c = conflicto.mappings().first()
    if c and c["conflicto"]:
        raise HTTPException(409, f"Conflicto: el aula ya tiene {c['num_conflictos']} asignación(es) en esa franja")

    row = await db.execute(text("""
        INSERT INTO ades_disponibilidad_aula
            (aula_id, grupo_id, ciclo_escolar_id, dia_semana, hora_inicio, hora_fin, motivo_bloqueo)
        VALUES
            (:aula_id::uuid, :grupo_id::uuid, :ciclo_id::uuid, :dia, :h_ini::TIME, :h_fin::TIME, :motivo)
        RETURNING id, dia_semana, hora_inicio, hora_fin
    """), {
        "aula_id":  str(aula_id),
        "grupo_id": str(body.grupo_id) if body.grupo_id else None,
        "ciclo_id": str(body.ciclo_escolar_id) if body.ciclo_escolar_id else None,
        "dia":      body.dia_semana,
        "h_ini":    body.hora_inicio,
        "h_fin":    body.hora_fin,
        "motivo":   body.motivo_bloqueo,
    })
    await db.commit()
    return dict(row.mappings().first())


@router.delete("/disponibilidad/{franja_id}", status_code=204)
async def liberar_franja(
    franja_id: uuid.UUID,
    db: AsyncSession = Depends(get_db),
    ades_user: AdesUser = Depends(get_ades_user),
):
    if ades_user.nivel_acceso > _NIVEL_ADMIN:
        raise HTTPException(403, "Sin permiso")
    result = await db.execute(
        text("UPDATE ades_disponibilidad_aula SET is_active = FALSE WHERE id = :id::uuid AND is_active = TRUE"),
        {"id": str(franja_id)},
    )
    if result.rowcount == 0:
        raise HTTPException(404, "Franja no encontrada")
    await db.commit()
