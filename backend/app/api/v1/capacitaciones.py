"""
FASE 29 — Registro de Capacitaciones y Certificaciones Docentes (DP-007)

  GET    /capacitaciones                         — listar (filtros: docente_id, tipo, validado)
  POST   /capacitaciones                         — registrar capacitación
  GET    /capacitaciones/{id}                    — detalle
  PATCH  /capacitaciones/{id}                    — actualizar (docente o RH)
  POST   /capacitaciones/{id}/validar            — RH valida como oficial
  DELETE /capacitaciones/{id}                    — soft delete
  GET    /capacitaciones/resumen/{docente_id}    — total hrs, conteo por tipo
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

router = APIRouter(prefix="/capacitaciones", tags=["capacitaciones docentes"])

_TIPOS = ["CURSO","TALLER","DIPLOMADO","POSGRADO","CERTIFICACION","CONGRESO","OTRO"]
_MODALIDADES = ["PRESENCIAL","EN_LINEA","HIBRIDA"]
_AREAS = ["PEDAGOGIA","TIC","DISCIPLINAR","IDIOMAS","LIDERAZGO","OTRO"]


# ── Schemas ───────────────────────────────────────────────────────────────────

class CapacitacionCreate(BaseModel):
    docente_id:        uuid.UUID
    nombre:            str
    descripcion:       Optional[str] = None
    institucion:       str
    tipo_certificacion: str = "CURSO"
    modalidad:         str = "PRESENCIAL"
    fecha_inicio:      date
    fecha_fin:         date
    duracion_hrs:      float
    area_formacion:    Optional[str] = None
    folio_certificado: Optional[str] = None
    certificado_url:   Optional[str] = None

    @model_validator(mode="after")
    def validar(self):
        if self.fecha_fin < self.fecha_inicio:
            raise ValueError("fecha_fin debe ser >= fecha_inicio")
        if self.tipo_certificacion not in _TIPOS:
            raise ValueError(f"tipo_certificacion inválido. Opciones: {_TIPOS}")
        if self.modalidad not in _MODALIDADES:
            raise ValueError(f"modalidad inválida. Opciones: {_MODALIDADES}")
        if self.area_formacion and self.area_formacion not in _AREAS:
            raise ValueError(f"area_formacion inválida. Opciones: {_AREAS}")
        if self.duracion_hrs <= 0:
            raise ValueError("duracion_hrs debe ser positiva")
        return self


class CapacitacionPatch(BaseModel):
    nombre:            Optional[str] = None
    descripcion:       Optional[str] = None
    institucion:       Optional[str] = None
    duracion_hrs:      Optional[float] = None
    folio_certificado: Optional[str] = None
    certificado_url:   Optional[str] = None
    area_formacion:    Optional[str] = None


class CapacitacionOut(BaseModel):
    id:                uuid.UUID
    docente_id:        uuid.UUID
    nombre:            str
    descripcion:       Optional[str]
    institucion:       str
    tipo_certificacion: str
    modalidad:         str
    fecha_inicio:      date
    fecha_fin:         date
    duracion_hrs:      float
    area_formacion:    Optional[str]
    certificado_url:   Optional[str]
    folio_certificado: Optional[str]
    validado_rh:       bool
    fecha_validacion:  Optional[str]
    row_version:       int
    fecha_creacion:    str

    class Config:
        from_attributes = True


class ResumenCapacitaciones(BaseModel):
    docente_id:    uuid.UUID
    total_hrs:     float
    total_eventos: int
    por_tipo:      dict
    por_modalidad: dict
    validadas:     int


# ── Helpers ───────────────────────────────────────────────────────────────────

async def _get_or_404(db: AsyncSession, cap_id: uuid.UUID) -> dict:
    row = await db.execute(
        text("SELECT * FROM public.ades_capacitaciones_docente WHERE id = :id AND is_active = TRUE"),
        {"id": str(cap_id)},
    )
    rec = row.mappings().first()
    if not rec:
        raise HTTPException(status_code=404, detail="Capacitación no encontrada")
    return dict(rec)


# ── Endpoints ─────────────────────────────────────────────────────────────────

@router.get("", response_model=list[CapacitacionOut])
async def listar_capacitaciones(
    docente_id: Optional[uuid.UUID] = Query(None),
    tipo:       Optional[str]       = Query(None),
    modalidad:  Optional[str]       = Query(None),
    validado:   Optional[bool]      = Query(None),
    db:         AsyncSession        = Depends(get_db),
    _user:      AdesUser            = Depends(get_ades_user),
):
    filters = ["is_active = TRUE"]
    params: dict = {}
    if docente_id:
        filters.append("docente_id = :did"); params["did"] = str(docente_id)
    if tipo:
        filters.append("tipo_certificacion = :tipo"); params["tipo"] = tipo
    if modalidad:
        filters.append("modalidad = :mod"); params["mod"] = modalidad
    if validado is not None:
        filters.append("validado_rh = :val"); params["val"] = validado

    q = f"SELECT * FROM public.ades_capacitaciones_docente WHERE {' AND '.join(filters)} ORDER BY fecha_inicio DESC"
    rows = await db.execute(text(q), params)
    return [dict(r) for r in rows.mappings().all()]


@router.post("", response_model=CapacitacionOut, status_code=201)
async def registrar_capacitacion(
    data: CapacitacionCreate,
    db:   AsyncSession = Depends(get_db),
    user: AdesUser     = Depends(get_ades_user),
):
    row = await db.execute(
        text("""
            INSERT INTO public.ades_capacitaciones_docente
                (docente_id, nombre, descripcion, institucion, tipo_certificacion,
                 modalidad, fecha_inicio, fecha_fin, duracion_hrs, area_formacion,
                 folio_certificado, certificado_url, usuario_creacion)
            VALUES
                (:did, :nom, :desc, :inst, :tipo,
                 :mod, :fi, :ff, :hrs, :area,
                 :folio, :cert_url, :usr)
            RETURNING *
        """),
        {
            "did":      str(data.docente_id),
            "nom":      data.nombre,
            "desc":     data.descripcion,
            "inst":     data.institucion,
            "tipo":     data.tipo_certificacion,
            "mod":      data.modalidad,
            "fi":       data.fecha_inicio,
            "ff":       data.fecha_fin,
            "hrs":      data.duracion_hrs,
            "area":     data.area_formacion,
            "folio":    data.folio_certificado,
            "cert_url": data.certificado_url,
            "usr":      user.username,
        },
    )
    await db.commit()
    return dict(row.mappings().first())


@router.get("/resumen/{docente_id}", response_model=ResumenCapacitaciones)
async def resumen_docente(
    docente_id: uuid.UUID,
    db:    AsyncSession = Depends(get_db),
    _user: AdesUser     = Depends(get_ades_user),
):
    rows = await db.execute(
        text("""
            SELECT tipo_certificacion, modalidad, duracion_hrs, validado_rh
            FROM public.ades_capacitaciones_docente
            WHERE docente_id = :did AND is_active = TRUE
        """),
        {"did": str(docente_id)},
    )
    records = rows.mappings().all()
    total_hrs = sum(float(r["duracion_hrs"]) for r in records)
    por_tipo: dict[str, float] = {}
    por_mod:  dict[str, float] = {}
    validadas = 0
    for r in records:
        por_tipo[r["tipo_certificacion"]] = por_tipo.get(r["tipo_certificacion"], 0) + float(r["duracion_hrs"])
        por_mod[r["modalidad"]] = por_mod.get(r["modalidad"], 0) + float(r["duracion_hrs"])
        if r["validado_rh"]:
            validadas += 1
    return ResumenCapacitaciones(
        docente_id=docente_id,
        total_hrs=round(total_hrs, 1),
        total_eventos=len(records),
        por_tipo=por_tipo,
        por_modalidad=por_mod,
        validadas=validadas,
    )


@router.get("/{cap_id}", response_model=CapacitacionOut)
async def detalle_capacitacion(
    cap_id: uuid.UUID,
    db:    AsyncSession = Depends(get_db),
    _user: AdesUser     = Depends(get_ades_user),
):
    return await _get_or_404(db, cap_id)


@router.patch("/{cap_id}", response_model=CapacitacionOut)
async def actualizar_capacitacion(
    cap_id: uuid.UUID,
    data:  CapacitacionPatch,
    db:    AsyncSession = Depends(get_db),
    user:  AdesUser     = Depends(get_ades_user),
):
    await _get_or_404(db, cap_id)
    sets = ["usuario_modificacion = :usr", "row_version = row_version + 1"]
    params: dict = {"id": str(cap_id), "usr": user.username}

    for field, col in [("nombre","nombre"),("descripcion","descripcion"),
                       ("institucion","institucion"),("duracion_hrs","duracion_hrs"),
                       ("folio_certificado","folio_certificado"),
                       ("certificado_url","certificado_url"),("area_formacion","area_formacion")]:
        val = getattr(data, field)
        if val is not None:
            sets.append(f"{col} = :{field}"); params[field] = val

    row = await db.execute(
        text(f"UPDATE public.ades_capacitaciones_docente SET {', '.join(sets)} WHERE id = :id RETURNING *"),
        params,
    )
    await db.commit()
    return dict(row.mappings().first())


@router.post("/{cap_id}/validar", response_model=CapacitacionOut)
async def validar_capacitacion(
    cap_id: uuid.UUID,
    db:    AsyncSession = Depends(get_db),
    user:  AdesUser     = Depends(get_ades_user),
):
    if user.nivel_acceso > 2:
        raise HTTPException(status_code=403, detail="Solo RH o Dirección puede validar capacitaciones")
    await _get_or_404(db, cap_id)
    row = await db.execute(
        text("""
            UPDATE public.ades_capacitaciones_docente
            SET validado_rh = TRUE, fecha_validacion = NOW(),
                usuario_modificacion = :usr, row_version = row_version + 1
            WHERE id = :id
            RETURNING *
        """),
        {"id": str(cap_id), "usr": user.username},
    )
    await db.commit()
    return dict(row.mappings().first())


@router.delete("/{cap_id}", status_code=204)
async def eliminar_capacitacion(
    cap_id: uuid.UUID,
    db:    AsyncSession = Depends(get_db),
    user:  AdesUser     = Depends(get_ades_user),
):
    await _get_or_404(db, cap_id)
    await db.execute(
        text("""
            UPDATE public.ades_capacitaciones_docente
            SET is_active = FALSE, usuario_modificacion = :usr, row_version = row_version + 1
            WHERE id = :id
        """),
        {"id": str(cap_id), "usr": user.username},
    )
    await db.commit()
