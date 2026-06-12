"""
FASE 30 — Expediente Laboral Digital (DP-004)

  GET    /expediente-laboral                    — listar (filtro: persona_id, tipo_contrato)
  POST   /expediente-laboral                    — crear expediente
  GET    /expediente-laboral/{id}               — detalle
  PATCH  /expediente-laboral/{id}               — actualizar
  DELETE /expediente-laboral/{id}               — soft delete
  POST   /expediente-laboral/{id}/documento     — registrar URL de documento
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

router = APIRouter(prefix="/expediente-laboral", tags=["expediente laboral"])

_CONTRATOS = ["INDEFINIDO", "DETERMINADO", "HONORARIOS", "COMISION"]
_ESTUDIOS   = ["LICENCIATURA", "MAESTRIA", "DOCTORADO", "NORMAL_BASICA", "BACHILLERATO", "OTRO"]
_DOC_TIPOS  = ["contrato", "titulo", "cedula", "nss", "identificacion", "acta_nacimiento", "curp_doc", "imss", "otro"]


# ── Schemas ───────────────────────────────────────────────────────────────────

class ExpedienteCreate(BaseModel):
    persona_id:          uuid.UUID
    tipo_contrato:       str = "INDEFINIDO"
    fecha_contratacion:  date
    fecha_fin_contrato:  Optional[date] = None
    salario_mensual:     float = 0.0
    imss_numero:         Optional[str] = None
    infonavit_numero:    Optional[str] = None
    curp:                Optional[str] = None
    rfc:                 Optional[str] = None
    cedula_profesional:  Optional[str] = None
    nivel_estudios:      Optional[str] = None
    especialidad:        Optional[str] = None
    institucion_formacion: Optional[str] = None
    clave_ct:            Optional[str] = None
    clave_issste:        Optional[str] = None

    @model_validator(mode="after")
    def validar(self):
        if self.tipo_contrato not in _CONTRATOS:
            raise ValueError(f"tipo_contrato inválido. Opciones: {_CONTRATOS}")
        if self.nivel_estudios and self.nivel_estudios not in _ESTUDIOS:
            raise ValueError(f"nivel_estudios inválido. Opciones: {_ESTUDIOS}")
        if self.fecha_fin_contrato and self.fecha_fin_contrato < self.fecha_contratacion:
            raise ValueError("fecha_fin_contrato debe ser >= fecha_contratacion")
        return self


class ExpedientePatch(BaseModel):
    tipo_contrato:       Optional[str] = None
    fecha_fin_contrato:  Optional[date] = None
    salario_mensual:     Optional[float] = None
    imss_numero:         Optional[str] = None
    infonavit_numero:    Optional[str] = None
    curp:                Optional[str] = None
    rfc:                 Optional[str] = None
    cedula_profesional:  Optional[str] = None
    nivel_estudios:      Optional[str] = None
    especialidad:        Optional[str] = None
    institucion_formacion: Optional[str] = None
    clave_ct:            Optional[str] = None
    clave_issste:        Optional[str] = None


class DocumentoIn(BaseModel):
    tipo_documento: str
    url:            str


class ExpedienteOut(BaseModel):
    id:                   uuid.UUID
    persona_id:           uuid.UUID
    tipo_contrato:        str
    fecha_contratacion:   date
    fecha_fin_contrato:   Optional[date]
    salario_mensual:      float
    imss_numero:          Optional[str]
    infonavit_numero:     Optional[str]
    curp:                 Optional[str]
    rfc:                  Optional[str]
    cedula_profesional:   Optional[str]
    nivel_estudios:       Optional[str]
    especialidad:         Optional[str]
    institucion_formacion: Optional[str]
    clave_ct:             Optional[str]
    clave_issste:         Optional[str]
    documentos_urls:      dict
    row_version:          int
    fecha_creacion:       str

    class Config:
        from_attributes = True


# ── Helpers ───────────────────────────────────────────────────────────────────

async def _get_or_404(db: AsyncSession, exp_id: uuid.UUID) -> dict:
    row = await db.execute(
        text("SELECT * FROM public.ades_expediente_laboral WHERE id = :id AND is_active = TRUE"),
        {"id": str(exp_id)},
    )
    rec = row.mappings().first()
    if not rec:
        raise HTTPException(status_code=404, detail="Expediente laboral no encontrado")
    return dict(rec)


# ── Endpoints ─────────────────────────────────────────────────────────────────

@router.get("", response_model=list[ExpedienteOut])
async def listar_expedientes(
    persona_id:    Optional[uuid.UUID] = Query(None),
    tipo_contrato: Optional[str]       = Query(None),
    db:            AsyncSession        = Depends(get_db),
    _user:         AdesUser            = Depends(get_ades_user),
):
    filters = ["is_active = TRUE"]
    params: dict = {}
    if persona_id:
        filters.append("persona_id = :pid"); params["pid"] = str(persona_id)
    if tipo_contrato:
        filters.append("tipo_contrato = :tc"); params["tc"] = tipo_contrato

    rows = await db.execute(
        text(f"SELECT * FROM public.ades_expediente_laboral WHERE {' AND '.join(filters)} ORDER BY fecha_creacion DESC"),
        params,
    )
    return [dict(r) for r in rows.mappings().all()]


@router.post("", response_model=ExpedienteOut, status_code=201)
async def crear_expediente(
    data: ExpedienteCreate,
    db:   AsyncSession = Depends(get_db),
    user: AdesUser     = Depends(get_ades_user),
):
    if user.nivel_acceso > 2:
        raise HTTPException(status_code=403, detail="Solo RH o Dirección puede crear expedientes laborales")
    row = await db.execute(
        text("""
            INSERT INTO public.ades_expediente_laboral
                (persona_id, tipo_contrato, fecha_contratacion, fecha_fin_contrato,
                 salario_mensual, imss_numero, infonavit_numero, curp, rfc,
                 cedula_profesional, nivel_estudios, especialidad, institucion_formacion,
                 clave_ct, clave_issste, usuario_creacion)
            VALUES
                (:pid, :tc, :fi, :ff,
                 :sal, :imss, :info, :curp, :rfc,
                 :ced, :est, :esp, :inst,
                 :ct, :issste, :usr)
            RETURNING *
        """),
        {
            "pid":    str(data.persona_id), "tc":   data.tipo_contrato,
            "fi":     data.fecha_contratacion, "ff": data.fecha_fin_contrato,
            "sal":    data.salario_mensual, "imss": data.imss_numero,
            "info":   data.infonavit_numero, "curp": data.curp, "rfc": data.rfc,
            "ced":    data.cedula_profesional, "est": data.nivel_estudios,
            "esp":    data.especialidad, "inst": data.institucion_formacion,
            "ct":     data.clave_ct, "issste": data.clave_issste,
            "usr":    user.username,
        },
    )
    await db.commit()
    return dict(row.mappings().first())


@router.get("/{exp_id}", response_model=ExpedienteOut)
async def detalle_expediente(
    exp_id: uuid.UUID,
    db:    AsyncSession = Depends(get_db),
    _user: AdesUser     = Depends(get_ades_user),
):
    return await _get_or_404(db, exp_id)


@router.patch("/{exp_id}", response_model=ExpedienteOut)
async def actualizar_expediente(
    exp_id: uuid.UUID,
    data:   ExpedientePatch,
    db:     AsyncSession = Depends(get_db),
    user:   AdesUser     = Depends(get_ades_user),
):
    if user.nivel_acceso > 2:
        raise HTTPException(status_code=403, detail="Solo RH o Dirección puede editar expedientes")
    await _get_or_404(db, exp_id)

    campo_col = [
        ("tipo_contrato","tipo_contrato"), ("fecha_fin_contrato","fecha_fin_contrato"),
        ("salario_mensual","salario_mensual"), ("imss_numero","imss_numero"),
        ("infonavit_numero","infonavit_numero"), ("curp","curp"), ("rfc","rfc"),
        ("cedula_profesional","cedula_profesional"), ("nivel_estudios","nivel_estudios"),
        ("especialidad","especialidad"), ("institucion_formacion","institucion_formacion"),
        ("clave_ct","clave_ct"), ("clave_issste","clave_issste"),
    ]
    sets = ["usuario_modificacion = :usr", "row_version = row_version + 1"]
    params: dict = {"id": str(exp_id), "usr": user.username}
    for field, col in campo_col:
        val = getattr(data, field)
        if val is not None:
            sets.append(f"{col} = :{field}"); params[field] = val

    row = await db.execute(
        text(f"UPDATE public.ades_expediente_laboral SET {', '.join(sets)} WHERE id = :id RETURNING *"),
        params,
    )
    await db.commit()
    return dict(row.mappings().first())


@router.post("/{exp_id}/documento", response_model=ExpedienteOut)
async def agregar_documento(
    exp_id: uuid.UUID,
    data:   DocumentoIn,
    db:     AsyncSession = Depends(get_db),
    user:   AdesUser     = Depends(get_ades_user),
):
    if data.tipo_documento not in _DOC_TIPOS:
        raise HTTPException(status_code=422, detail=f"tipo_documento inválido. Opciones: {_DOC_TIPOS}")
    await _get_or_404(db, exp_id)
    row = await db.execute(
        text("""
            UPDATE public.ades_expediente_laboral
            SET documentos_urls = documentos_urls || jsonb_build_object(:tipo, :url),
                usuario_modificacion = :usr, row_version = row_version + 1
            WHERE id = :id
            RETURNING *
        """),
        {"id": str(exp_id), "tipo": data.tipo_documento, "url": data.url, "usr": user.username},
    )
    await db.commit()
    return dict(row.mappings().first())


@router.delete("/{exp_id}", status_code=204)
async def eliminar_expediente(
    exp_id: uuid.UUID,
    db:    AsyncSession = Depends(get_db),
    user:  AdesUser     = Depends(get_ades_user),
):
    if user.nivel_acceso > 2:
        raise HTTPException(status_code=403, detail="Solo RH o Dirección puede eliminar expedientes")
    await _get_or_404(db, exp_id)
    await db.execute(
        text("UPDATE public.ades_expediente_laboral SET is_active=FALSE, usuario_modificacion=:usr, row_version=row_version+1 WHERE id=:id"),
        {"id": str(exp_id), "usr": user.username},
    )
    await db.commit()
