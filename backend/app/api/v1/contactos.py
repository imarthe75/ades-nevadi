"""
Contactos familiares / tutores + expediente médico del alumno.

  GET/POST/PATCH/DELETE  /contactos?estudiante_id=<uuid>
  GET/PUT                /expediente-medico/{estudiante_id}
"""
from __future__ import annotations
import uuid
from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from sqlalchemy.orm import selectinload
from app.core.database import get_db
from app.core.security import get_ades_user, AdesUser
from app.models.personas import ContactoFamiliar, Persona, ExpedienteDoc, DocumentoTipo
from app.models.fase3 import ExpedienteMedico
from pydantic import BaseModel, Field
from app.schemas.personas import (
    ExpedienteMedicoOut, ExpedienteMedicoUpdate,
)


class ContactoPayload(BaseModel):
    """Payload del frontend para crear/actualizar contactos."""
    estudiante_id: uuid.UUID | None = None
    nombre_completo: str = Field(min_length=2, max_length=200)
    parentesco: str | None = None
    telefono_principal: str | None = None
    email: str | None = None
    es_tutor_legal: bool = False
    es_contacto_emergencia: bool = False
    puede_recoger: bool = True
    ocupacion: str | None = None
    nivel_estudios: str | None = None
    rfc: str | None = None
    toma_decision_conjunta: bool = False
    grado_responsabilidad: str = "PRINCIPAL"

router = APIRouter(tags=["contactos & expediente"])


# ══════════════════════════════════════════════════════════════════════════════
# CONTACTOS FAMILIARES / TUTORES
# ══════════════════════════════════════════════════════════════════════════════

class ContactoFamiliarOut(BaseModel):
    """Schema de respuesta para contactos familiares — sin herencia de AdesResponse."""
    id: uuid.UUID
    persona_id: uuid.UUID
    estudiante_id: uuid.UUID
    nombre_completo: str
    parentesco: str | None = None
    telefono_principal: str | None = None
    email: str | None = None
    es_tutor_legal: bool = False
    es_contacto_emergencia: bool = False
    puede_recoger: bool = False
    ocupacion: str | None = None
    nivel_estudios: str | None = None
    rfc: str | None = None
    toma_decision_conjunta: bool = False
    grado_responsabilidad: str = "PRINCIPAL"
    is_active: bool = True
    nombre_completo_persona: str | None = None

    model_config = {"from_attributes": True}


@router.get("/contactos", response_model=list[ContactoFamiliarOut])
async def listar_contactos(
    estudiante_id: uuid.UUID = Query(...),
    db: AsyncSession = Depends(get_db),
    _user: AdesUser = Depends(get_ades_user),
):
    q = (
        select(ContactoFamiliar)
        .options(selectinload(ContactoFamiliar.persona))
        .where(ContactoFamiliar.estudiante_id == estudiante_id, ContactoFamiliar.is_active == True)
        .order_by(ContactoFamiliar.es_tutor_legal.desc(), ContactoFamiliar.prioridad)
    )
    rows = (await db.execute(q)).scalars().all()
    result = []
    for c in rows:
        nombre = c.nombre_completo
        if not nombre and c.persona:
            nombre = c.persona.nombre_completo
        result.append(ContactoFamiliarOut(
            id=c.id,
            persona_id=c.persona_id or c.estudiante_id,
            estudiante_id=c.estudiante_id,
            nombre_completo=nombre or "",
            parentesco=c.parentesco,
            telefono_principal=c.telefono_principal,
            email=c.email,
            es_tutor_legal=c.es_tutor_legal,
            es_contacto_emergencia=c.es_contacto_emergencia,
            puede_recoger=c.puede_recoger,
            ocupacion=c.ocupacion,
            nivel_estudios=c.nivel_estudios,
            rfc=c.rfc,
            toma_decision_conjunta=getattr(c, "toma_decision_conjunta", False),
            grado_responsabilidad=getattr(c, "grado_responsabilidad", "PRINCIPAL") or "PRINCIPAL",
            is_active=c.is_active,
            nombre_completo_persona=(c.persona.nombre_completo if c.persona else None),
        ))
    return result


@router.post("/contactos", response_model=ContactoFamiliarOut, status_code=201)
async def crear_contacto(
    data: ContactoPayload,
    estudiante_id: uuid.UUID | None = Query(None),
    db: AsyncSession = Depends(get_db),
    _user: AdesUser = Depends(get_ades_user),
):
    est_id = estudiante_id or data.estudiante_id
    if not est_id:
        raise HTTPException(status_code=422, detail="estudiante_id es requerido")
    contacto = ContactoFamiliar(
        estudiante_id=est_id,
        nombre_completo=data.nombre_completo,
        parentesco=data.parentesco,
        telefono_principal=data.telefono_principal,
        email=data.email,
        es_tutor_legal=data.es_tutor_legal,
        es_contacto_emergencia=data.es_contacto_emergencia,
        puede_recoger=data.puede_recoger,
        ocupacion=data.ocupacion,
        nivel_estudios=data.nivel_estudios,
        rfc=data.rfc,
    )
    db.add(contacto)
    await db.commit()
    await db.refresh(contacto)
    return ContactoFamiliarOut(
        id=contacto.id,
        persona_id=contacto.persona_id or est_id,
        estudiante_id=contacto.estudiante_id,
        nombre_completo=contacto.nombre_completo or "",
        parentesco=contacto.parentesco,
        telefono_principal=contacto.telefono_principal,
        email=contacto.email,
        es_tutor_legal=contacto.es_tutor_legal,
        es_contacto_emergencia=contacto.es_contacto_emergencia,
        puede_recoger=contacto.puede_recoger,
        ocupacion=contacto.ocupacion,
        nivel_estudios=contacto.nivel_estudios,
        rfc=contacto.rfc,
        toma_decision_conjunta=getattr(contacto, "toma_decision_conjunta", False),
        grado_responsabilidad=getattr(contacto, "grado_responsabilidad", "PRINCIPAL") or "PRINCIPAL",
        is_active=contacto.is_active,
    )


@router.patch("/contactos/{contacto_id}", response_model=ContactoFamiliarOut)
async def actualizar_contacto(
    contacto_id: uuid.UUID,
    data: ContactoPayload,
    db: AsyncSession = Depends(get_db),
    _user: AdesUser = Depends(get_ades_user),
):
    contacto = await db.get(ContactoFamiliar, contacto_id)
    if not contacto or not contacto.is_active:
        raise HTTPException(status_code=404, detail="Contacto no encontrado")

    for field in data.model_fields_set:
        if field == "estudiante_id":
            continue
        db_field = field
        if hasattr(contacto, db_field):
            setattr(contacto, db_field, getattr(data, field))

    await db.commit()
    await db.refresh(contacto)
    return ContactoFamiliarOut(
        id=contacto.id,
        persona_id=contacto.persona_id or contacto.estudiante_id,
        estudiante_id=contacto.estudiante_id,
        nombre_completo=contacto.nombre_completo or "",
        parentesco=contacto.parentesco,
        telefono_principal=contacto.telefono_principal,
        email=contacto.email,
        es_tutor_legal=contacto.es_tutor_legal,
        es_contacto_emergencia=contacto.es_contacto_emergencia,
        puede_recoger=contacto.puede_recoger,
        ocupacion=contacto.ocupacion,
        nivel_estudios=contacto.nivel_estudios,
        rfc=contacto.rfc,
        toma_decision_conjunta=getattr(contacto, "toma_decision_conjunta", False),
        grado_responsabilidad=getattr(contacto, "grado_responsabilidad", "PRINCIPAL") or "PRINCIPAL",
        is_active=contacto.is_active,
    )


@router.delete("/contactos/{contacto_id}", status_code=204)
async def eliminar_contacto(
    contacto_id: uuid.UUID,
    db: AsyncSession = Depends(get_db),
    _user: AdesUser = Depends(get_ades_user),
):
    contacto = await db.get(ContactoFamiliar, contacto_id)
    if not contacto:
        raise HTTPException(status_code=404, detail="Contacto no encontrado")
    contacto.is_active = False
    await db.commit()


# ══════════════════════════════════════════════════════════════════════════════
# EXPEDIENTE MÉDICO
# ══════════════════════════════════════════════════════════════════════════════

@router.get("/expediente-medico/{estudiante_id}", response_model=ExpedienteMedicoOut)
async def obtener_expediente_medico(
    estudiante_id: uuid.UUID,
    db: AsyncSession = Depends(get_db),
    _user: AdesUser = Depends(get_ades_user),
):
    q = select(ExpedienteMedico).where(ExpedienteMedico.estudiante_id == estudiante_id)
    exp = (await db.execute(q)).scalar_one_or_none()
    if not exp:
        # Crear expediente vacío al vuelo (lazy init)
        exp = ExpedienteMedico(estudiante_id=estudiante_id)
        db.add(exp)
        await db.commit()
        await db.refresh(exp)
    return ExpedienteMedicoOut.model_validate(exp)


@router.put("/expediente-medico/{estudiante_id}", response_model=ExpedienteMedicoOut)
async def actualizar_expediente_medico(
    estudiante_id: uuid.UUID,
    data: ExpedienteMedicoUpdate,
    db: AsyncSession = Depends(get_db),
    _user: AdesUser = Depends(get_ades_user),
):
    q = select(ExpedienteMedico).where(ExpedienteMedico.estudiante_id == estudiante_id)
    exp = (await db.execute(q)).scalar_one_or_none()
    if not exp:
        exp = ExpedienteMedico(estudiante_id=estudiante_id)
        db.add(exp)
        await db.flush()

    for field, value in data.model_dump(exclude_unset=True).items():
        setattr(exp, field, value)

    await db.commit()
    await db.refresh(exp)
    return ExpedienteMedicoOut.model_validate(exp)


# ══════════════════════════════════════════════════════════════════════════════
# EXPEDIENTE DE DOCUMENTOS
# ══════════════════════════════════════════════════════════════════════════════

@router.get("/expediente-docs/{estudiante_id}")
async def obtener_expediente_docs(
    estudiante_id: uuid.UUID,
    ciclo_id: uuid.UUID | None = Query(None),
    db: AsyncSession = Depends(get_db),
    _user: AdesUser = Depends(get_ades_user),
):
    """Devuelve todos los documentos requeridos con su estatus para el alumno."""
    # Todos los tipos activos
    tipos = (await db.execute(
        select(DocumentoTipo).where(DocumentoTipo.is_active == True).order_by(DocumentoTipo.orden)
    )).scalars().all()

    # Documentos ya entregados
    q = select(ExpedienteDoc).where(
        ExpedienteDoc.estudiante_id == estudiante_id,
        ExpedienteDoc.is_active == True,
    )
    if ciclo_id:
        q = q.where(ExpedienteDoc.ciclo_escolar_id == ciclo_id)
    docs = {d.documento_tipo_id: d for d in (await db.execute(q)).scalars().all()}

    resultado = []
    for tipo in tipos:
        doc = docs.get(tipo.id)
        resultado.append({
            "documento_tipo_id": tipo.id,
            "nombre_documento": tipo.nombre_documento,
            "descripcion": tipo.descripcion,
            "obligatorio": tipo.obligatorio,
            "estatus": doc.estatus if doc else "PENDIENTE",
            "fecha_entrega": doc.fecha_entrega if doc else None,
            "observaciones": doc.observaciones if doc else None,
            "doc_id": doc.id if doc else None,
        })
    return resultado


@router.patch("/expediente-docs/{estudiante_id}/{doc_tipo_id}")
async def actualizar_doc_estatus(
    estudiante_id: uuid.UUID,
    doc_tipo_id: uuid.UUID,
    estatus: str = Query(..., pattern="^(PENDIENTE|ENTREGADO|INCOMPLETO|RECHAZADO|EXENTO)$"),
    ciclo_id: uuid.UUID | None = Query(None),
    observaciones: str | None = Query(None),
    db: AsyncSession = Depends(get_db),
    _user: AdesUser = Depends(get_ades_user),
):
    from datetime import date
    q = select(ExpedienteDoc).where(
        ExpedienteDoc.estudiante_id == estudiante_id,
        ExpedienteDoc.documento_tipo_id == doc_tipo_id,
    )
    if ciclo_id:
        q = q.where(ExpedienteDoc.ciclo_escolar_id == ciclo_id)
    doc = (await db.execute(q)).scalar_one_or_none()

    if not doc:
        doc = ExpedienteDoc(
            estudiante_id=estudiante_id,
            documento_tipo_id=doc_tipo_id,
            ciclo_escolar_id=ciclo_id,
            verificado_por_id=_user.id,
        )
        db.add(doc)

    doc.estatus = estatus
    if estatus == "ENTREGADO":
        doc.fecha_entrega = date.today()
        doc.verificado_por_id = _user.id
    doc.observaciones = observaciones

    await db.commit()
    return {"ok": True, "estatus": estatus}
