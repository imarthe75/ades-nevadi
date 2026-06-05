from __future__ import annotations
import uuid
from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, func
from sqlalchemy.orm import selectinload
from app.core.database import get_db
from app.core.security import get_current_user, get_ades_user, AdesUser
from app.models.personas import Persona as PersonaModel
from app.models.personas import Profesor, Persona, Estatus
from app.models.materias import AsignacionDocente
from app.models.academica import Grupo, Grado, Plantel, NivelEducativo, CicloEscolar
from app.models.materias import Materia
from app.schemas.personas import ProfesorOut, ProfesorCreate, ProfesorUpdate
from app.schemas.materias import AsignacionDocenteOut
from app.schemas.base import PagedResponse

router = APIRouter(prefix="/profesores", tags=["profesores"])


@router.get("", response_model=PagedResponse[ProfesorOut])
async def listar_profesores(
    plantel_id: uuid.UUID | None = None,
    buscar: str | None = Query(None, description="Nombre, apellido o número de empleado"),
    pagina: int = Query(1, ge=1),
    por_pagina: int = Query(30, ge=1, le=200),
    db: AsyncSession = Depends(get_db),
    ades_user: AdesUser = Depends(get_ades_user),
):
    base_q = (
        select(Profesor)
        .options(selectinload(Profesor.persona))
        .join(Persona, Persona.id == Profesor.persona_id)
        .where(Profesor.is_active == True)
    )
    if ades_user.plantel_id:
        base_q = base_q.where(Profesor.plantel_id == ades_user.plantel_id)
    elif plantel_id:
        base_q = base_q.where(Profesor.plantel_id == plantel_id)
    if buscar:
        term = f"%{buscar}%"
        base_q = base_q.where(
            Persona.nombre.ilike(term)
            | Persona.apellido_paterno.ilike(term)
            | Profesor.numero_empleado.ilike(term)
        )

    total = (await db.execute(select(func.count()).select_from(base_q.subquery()))).scalar_one()
    data_q = base_q.order_by(Persona.apellido_paterno, Persona.nombre)
    data_q = data_q.offset((pagina - 1) * por_pagina).limit(por_pagina)
    rows = (await db.execute(data_q)).scalars().all()

    return PagedResponse.build(data=rows, total=total, pagina=pagina, por_pagina=por_pagina)


@router.get("/{profesor_id}", response_model=ProfesorOut)
async def obtener_profesor(
    profesor_id: uuid.UUID,
    db: AsyncSession = Depends(get_db),
    _user: AdesUser = Depends(get_ades_user),
):
    q = (
        select(Profesor)
        .options(selectinload(Profesor.persona))
        .where(Profesor.id == profesor_id)
    )
    row = (await db.execute(q)).scalar_one_or_none()
    if not row:
        raise HTTPException(status_code=404, detail="Profesor no encontrado")
    return row


@router.patch("/{profesor_id}", response_model=ProfesorOut)
async def actualizar_profesor(
    profesor_id: uuid.UUID,
    data: ProfesorUpdate,
    db: AsyncSession = Depends(get_db),
    _user: AdesUser = Depends(get_ades_user),
):
    q = select(Profesor).options(selectinload(Profesor.persona)).where(Profesor.id == profesor_id)
    prof = (await db.execute(q)).scalar_one_or_none()
    if not prof:
        raise HTTPException(status_code=404, detail="Profesor no encontrado")

    if data.laborales:
        for field, value in data.laborales.model_dump(exclude_unset=True).items():
            setattr(prof, field, value)

    if data.persona and prof.persona:
        for field, value in data.persona.model_dump(exclude_unset=True).items():
            setattr(prof.persona, field, value)

    await db.commit()
    await db.refresh(prof)
    return prof


@router.post("", response_model=ProfesorOut, status_code=status.HTTP_201_CREATED)
async def crear_profesor(
    data: ProfesorCreate,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    existing = (await db.execute(
        select(Persona).where(Persona.curp == data.persona.curp)
    )).scalar_one_or_none()
    if existing:
        raise HTTPException(status_code=409, detail="CURP ya registrada")

    existing_emp = (await db.execute(
        select(Profesor).where(Profesor.numero_empleado == data.numero_empleado)
    )).scalar_one_or_none()
    if existing_emp:
        raise HTTPException(status_code=409, detail="Número de empleado ya registrado")

    persona = Persona(**data.persona.model_dump())
    db.add(persona)
    await db.flush()

    est = (await db.execute(
        select(Estatus).where(Estatus.entidad == "PROFESOR", Estatus.nombre_estatus == "ACTIVO")
    )).scalar_one_or_none()

    profesor = Profesor(
        numero_empleado=data.numero_empleado,
        persona_id=persona.id,
        plantel_id=data.plantel_id,
        tipo_contrato=data.tipo_contrato,
        estatus_id=est.id if est else None,
    )
    db.add(profesor)
    await db.commit()
    await db.refresh(profesor)
    return profesor


@router.get("/{profesor_id}/asignaciones", response_model=list[AsignacionDocenteOut])
async def asignaciones_del_profesor(
    profesor_id: uuid.UUID,
    ciclo_id: uuid.UUID | None = None,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    q = select(AsignacionDocente).where(
        AsignacionDocente.profesor_id == profesor_id,
        AsignacionDocente.is_active == True,
    )
    if ciclo_id:
        q = q.where(AsignacionDocente.ciclo_escolar_id == ciclo_id)
    rows = await db.execute(q)
    return rows.scalars().all()


@router.get("/{profesor_id}/grupos", response_model=list)
async def grupos_del_profesor(
    profesor_id: uuid.UUID,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    """Grupos únicos donde el profesor tiene al menos una asignación activa."""
    q = (
        select(Grupo)
        .join(AsignacionDocente, AsignacionDocente.grupo_id == Grupo.id)
        .where(
            AsignacionDocente.profesor_id == profesor_id,
            AsignacionDocente.is_active == True,
            Grupo.is_active == True,
        )
        .distinct()
    )
    rows = await db.execute(q)
    return rows.scalars().all()
