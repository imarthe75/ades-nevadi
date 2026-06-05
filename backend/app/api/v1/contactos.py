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
from app.schemas.personas import (
    ContactoOut, ContactoCreate, ContactoUpdate,
    ExpedienteMedicoOut, ExpedienteMedicoUpdate,
)

router = APIRouter(tags=["contactos & expediente"])


# ══════════════════════════════════════════════════════════════════════════════
# CONTACTOS FAMILIARES / TUTORES
# ══════════════════════════════════════════════════════════════════════════════

class ContactoFamiliarOut(ContactoOut):
    """Schema extendido con campos de ades_contactos_familiares."""
    estudiante_id: uuid.UUID
    es_contacto_emergencia: bool = False
    puede_recoger: bool = False
    nombre_completo_persona: str | None = None  # de la persona vinculada


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
            persona_id=c.persona_id or c.estudiante_id,  # fallback
            estudiante_id=c.estudiante_id,
            nombre_completo=nombre or "",
            parentesco=c.parentesco,
            telefono=c.telefono_principal,
            telefono_alt=c.telefono_trabajo,
            email=c.email,
            es_tutor_legal=c.es_tutor_legal,
            es_contacto_prim=c.es_contacto_emergencia,
            puede_recoger=c.puede_recoger,
            ocupacion=c.ocupacion,
            nivel_estudios=c.nivel_estudios,
            rfc=c.rfc,
            nombre_completo_persona=(c.persona.nombre_completo if c.persona else None),
        ))
    return result


@router.post("/contactos", response_model=ContactoFamiliarOut, status_code=201)
async def crear_contacto(
    data: ContactoCreate,
    estudiante_id: uuid.UUID = Query(...),
    db: AsyncSession = Depends(get_db),
    _user: AdesUser = Depends(get_ades_user),
):
    contacto = ContactoFamiliar(
        estudiante_id=estudiante_id,
        persona_id=data.persona_id if data.persona_id != uuid.UUID(int=0) else None,
        nombre_completo=data.nombre_completo,
        parentesco=data.parentesco,
        telefono_principal=data.telefono,
        telefono_trabajo=data.telefono_alt,
        email=data.email,
        es_tutor_legal=data.es_tutor_legal,
        es_contacto_emergencia=data.es_contacto_prim,
        ocupacion=data.ocupacion,
        nivel_estudios=data.nivel_estudios,
        rfc=data.rfc,
    )
    db.add(contacto)
    await db.commit()
    await db.refresh(contacto)
    return ContactoFamiliarOut(
        id=contacto.id,
        persona_id=contacto.persona_id or estudiante_id,
        estudiante_id=contacto.estudiante_id,
        nombre_completo=contacto.nombre_completo or "",
        parentesco=contacto.parentesco,
        telefono=contacto.telefono_principal,
        telefono_alt=contacto.telefono_trabajo,
        email=contacto.email,
        es_tutor_legal=contacto.es_tutor_legal,
        es_contacto_prim=contacto.es_contacto_emergencia,
        puede_recoger=contacto.puede_recoger,
        ocupacion=contacto.ocupacion,
        nivel_estudios=contacto.nivel_estudios,
        rfc=contacto.rfc,
    )


@router.patch("/contactos/{contacto_id}", response_model=ContactoFamiliarOut)
async def actualizar_contacto(
    contacto_id: uuid.UUID,
    data: ContactoUpdate,
    db: AsyncSession = Depends(get_db),
    _user: AdesUser = Depends(get_ades_user),
):
    contacto = await db.get(ContactoFamiliar, contacto_id)
    if not contacto or not contacto.is_active:
        raise HTTPException(status_code=404, detail="Contacto no encontrado")

    mapping = {
        "nombre_completo": "nombre_completo",
        "parentesco": "parentesco",
        "telefono": "telefono_principal",
        "telefono_alt": "telefono_trabajo",
        "email": "email",
        "es_tutor_legal": "es_tutor_legal",
        "es_contacto_prim": "es_contacto_emergencia",
        "ocupacion": "ocupacion",
        "nivel_estudios": "nivel_estudios",
        "rfc": "rfc",
    }
    for src, dst in mapping.items():
        if src in data.model_fields_set:
            setattr(contacto, dst, getattr(data, src))

    await db.commit()
    await db.refresh(contacto)
    return ContactoFamiliarOut(
        id=contacto.id,
        persona_id=contacto.persona_id or contacto.estudiante_id,
        estudiante_id=contacto.estudiante_id,
        nombre_completo=contacto.nombre_completo or "",
        parentesco=contacto.parentesco,
        telefono=contacto.telefono_principal,
        telefono_alt=contacto.telefono_trabajo,
        email=contacto.email,
        es_tutor_legal=contacto.es_tutor_legal,
        es_contacto_prim=contacto.es_contacto_emergencia,
        puede_recoger=contacto.puede_recoger,
        ocupacion=contacto.ocupacion,
        nivel_estudios=contacto.nivel_estudios,
        rfc=contacto.rfc,
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
