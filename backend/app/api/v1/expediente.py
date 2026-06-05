"""
Expediente académico del alumno: bajas, extraordinarias, constancias.

  GET/POST          /bajas?estudiante_id=<uuid>
  POST/PATCH        /bajas/{id}
  GET/POST          /extraordinarias?estudiante_id=<uuid>
  GET/POST/PATCH    /constancias
"""
from __future__ import annotations
import uuid
from datetime import date
from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from app.core.database import get_db
from app.core.security import get_ades_user, AdesUser
from app.models.personas import Baja, Extraordinaria, Constancia, Estudiante
from app.models.materias import Materia
from app.schemas.base import AdesSchema, AdesResponse

router = APIRouter(tags=["expediente académico"])


# ══════════════════════════════════════════════════════════════════════════════
# BAJAS
# ══════════════════════════════════════════════════════════════════════════════

class BajaCreate(AdesSchema):
    tipo_baja: str                              # TEMPORAL DEFINITIVA TRASLADO DESERCION
    motivo: str | None = None
    fecha_efectiva: date
    fecha_reingreso: date | None = None
    plantel_destino: str | None = None
    clave_ct_destino: str | None = None
    observaciones: str | None = None


class BajaOut(AdesResponse):
    estudiante_id: uuid.UUID
    tipo_baja: str
    motivo: str | None
    fecha_efectiva: date
    fecha_reingreso: date | None
    plantel_destino: str | None
    clave_ct_destino: str | None
    observaciones: str | None
    autorizado_por_id: uuid.UUID | None


@router.get("/bajas", response_model=list[BajaOut])
async def listar_bajas(
    estudiante_id: uuid.UUID = Query(...),
    db: AsyncSession = Depends(get_db),
    _user: AdesUser = Depends(get_ades_user),
):
    q = select(Baja).where(Baja.estudiante_id == estudiante_id, Baja.is_active == True).order_by(Baja.fecha_efectiva.desc())
    return (await db.execute(q)).scalars().all()


@router.post("/bajas", response_model=BajaOut, status_code=201)
async def registrar_baja(
    estudiante_id: uuid.UUID = Query(...),
    data: BajaCreate = ...,
    db: AsyncSession = Depends(get_db),
    ades_user: AdesUser = Depends(get_ades_user),
):
    baja = Baja(
        estudiante_id=estudiante_id,
        autorizado_por_id=ades_user.id,
        **data.model_dump(),
    )
    db.add(baja)

    # Actualizar estatus del alumno si es baja definitiva
    if data.tipo_baja in ("DEFINITIVA", "DESERCION"):
        est = await db.get(Estudiante, estudiante_id)
        if est:
            est.is_active = False

    await db.commit()
    await db.refresh(baja)
    return baja


# ══════════════════════════════════════════════════════════════════════════════
# EXTRAORDINARIOS
# ══════════════════════════════════════════════════════════════════════════════

class ExtraordinarioCreate(AdesSchema):
    materia_id: uuid.UUID
    ciclo_escolar_id: uuid.UUID
    grupo_id: uuid.UUID | None = None
    tipo_examen: str = "EXTRAORDINARIO"
    calificacion_previa: float | None = None
    fecha_examen: date | None = None
    calificacion: float | None = None
    acredita: bool | None = None
    observaciones: str | None = None


class ExtraordinarioOut(AdesResponse):
    estudiante_id: uuid.UUID
    materia_id: uuid.UUID
    ciclo_escolar_id: uuid.UUID
    tipo_examen: str
    calificacion_previa: float | None
    fecha_examen: date | None
    calificacion: float | None
    acredita: bool | None
    observaciones: str | None


@router.get("/extraordinarias", response_model=list[ExtraordinarioOut])
async def listar_extraordinarias(
    estudiante_id: uuid.UUID = Query(...),
    ciclo_id: uuid.UUID | None = Query(None),
    db: AsyncSession = Depends(get_db),
    _user: AdesUser = Depends(get_ades_user),
):
    q = select(Extraordinaria).where(Extraordinaria.estudiante_id == estudiante_id, Extraordinaria.is_active == True)
    if ciclo_id:
        q = q.where(Extraordinaria.ciclo_escolar_id == ciclo_id)
    return (await db.execute(q)).scalars().all()


@router.post("/extraordinarias", response_model=ExtraordinarioOut, status_code=201)
async def registrar_extraordinario(
    estudiante_id: uuid.UUID = Query(...),
    data: ExtraordinarioCreate = ...,
    db: AsyncSession = Depends(get_db),
    ades_user: AdesUser = Depends(get_ades_user),
):
    extra = Extraordinaria(
        estudiante_id=estudiante_id,
        aplicado_por_id=ades_user.id,
        **data.model_dump(),
    )
    db.add(extra)
    await db.commit()
    await db.refresh(extra)
    return extra


@router.patch("/extraordinarias/{extra_id}", response_model=ExtraordinarioOut)
async def actualizar_extraordinario(
    extra_id: uuid.UUID,
    calificacion: float = Query(..., ge=0, le=10),
    acredita: bool = Query(...),
    fecha_examen: date | None = Query(None),
    db: AsyncSession = Depends(get_db),
    _user: AdesUser = Depends(get_ades_user),
):
    extra = await db.get(Extraordinaria, extra_id)
    if not extra:
        raise HTTPException(status_code=404, detail="Extraordinario no encontrado")
    extra.calificacion = calificacion  # type: ignore[assignment]
    extra.acredita = acredita
    if fecha_examen:
        extra.fecha_examen = fecha_examen
    await db.commit()
    await db.refresh(extra)
    return extra


# ══════════════════════════════════════════════════════════════════════════════
# CONSTANCIAS
# ══════════════════════════════════════════════════════════════════════════════

class ConstanciaCreate(AdesSchema):
    tipo_constancia: str
    ciclo_escolar_id: uuid.UUID | None = None
    solicitada_por: str | None = None
    proposito: str | None = None
    fecha_vencimiento: date | None = None
    observaciones: str | None = None


class ConstanciaOut(AdesResponse):
    estudiante_id: uuid.UUID
    tipo_constancia: str
    folio: str | None
    ciclo_escolar_id: uuid.UUID | None
    fecha_emision: date
    fecha_vencimiento: date | None
    solicitada_por: str | None
    proposito: str | None
    emitida_por_id: uuid.UUID | None
    entregada: bool
    fecha_entrega: date | None


@router.get("/constancias", response_model=list[ConstanciaOut])
async def listar_constancias(
    estudiante_id: uuid.UUID = Query(...),
    db: AsyncSession = Depends(get_db),
    _user: AdesUser = Depends(get_ades_user),
):
    q = select(Constancia).where(Constancia.estudiante_id == estudiante_id, Constancia.is_active == True).order_by(Constancia.fecha_emision.desc())
    return (await db.execute(q)).scalars().all()


@router.post("/constancias", response_model=ConstanciaOut, status_code=201)
async def emitir_constancia(
    estudiante_id: uuid.UUID = Query(...),
    data: ConstanciaCreate = ...,
    db: AsyncSession = Depends(get_db),
    ades_user: AdesUser = Depends(get_ades_user),
):
    from sqlalchemy import func as sqlfunc
    # Generar folio: TIPO-YYYY-NNNN
    anio = date.today().year
    count_q = await db.execute(
        select(sqlfunc.count()).where(
            Constancia.tipo_constancia == data.tipo_constancia,
            sqlfunc.extract("year", Constancia.fecha_emision) == anio,
        )
    )
    seq = (count_q.scalar() or 0) + 1
    folio = f"{data.tipo_constancia[:3].upper()}-{anio}-{seq:04d}"

    constancia = Constancia(
        estudiante_id=estudiante_id,
        folio=folio,
        emitida_por_id=ades_user.id,
        **data.model_dump(),
    )
    db.add(constancia)
    await db.commit()
    await db.refresh(constancia)
    return constancia


@router.patch("/constancias/{constancia_id}/entregar", response_model=ConstanciaOut)
async def marcar_entregada(
    constancia_id: uuid.UUID,
    db: AsyncSession = Depends(get_db),
    _user: AdesUser = Depends(get_ades_user),
):
    c = await db.get(Constancia, constancia_id)
    if not c:
        raise HTTPException(status_code=404, detail="Constancia no encontrada")
    c.entregada = True
    c.fecha_entrega = date.today()
    await db.commit()
    await db.refresh(c)
    return c
