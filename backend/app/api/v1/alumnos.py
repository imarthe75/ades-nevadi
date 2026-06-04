from __future__ import annotations
import uuid
from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, func
from app.core.database import get_db
from app.core.security import get_current_user
from app.models.personas import Estudiante, Persona, Inscripcion
from app.models.academica import Grupo, Grado, Plantel, CicloEscolar, NivelEducativo
from app.schemas.personas import EstudianteOut, EstudianteCreate, InscripcionOut, InscripcionCreate
from app.schemas.base import Paginacion

router = APIRouter(prefix="/alumnos", tags=["alumnos"])


@router.get("", response_model=list[EstudianteOut])
async def listar_alumnos(
    plantel_id: uuid.UUID | None = None,
    buscar: str | None = Query(None, description="Busca por nombre o CURP"),
    pagina: int = Query(1, ge=1),
    por_pagina: int = Query(30, ge=1, le=100),
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    q = select(Estudiante).join(Persona, Persona.id == Estudiante.persona_id)
    if plantel_id:
        q = q.where(Estudiante.plantel_id == plantel_id)
    if buscar:
        term = f"%{buscar}%"
        q = q.where(
            Persona.nombre.ilike(term)
            | Persona.apellido_paterno.ilike(term)
            | Persona.curp.ilike(term)
        )
    q = q.where(Estudiante.is_active == True)
    q = q.order_by(Persona.apellido_paterno, Persona.nombre)
    q = q.offset((pagina - 1) * por_pagina).limit(por_pagina)
    rows = await db.execute(q)
    return rows.scalars().all()


@router.get("/{estudiante_id}", response_model=EstudianteOut)
async def obtener_alumno(
    estudiante_id: uuid.UUID,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    row = await db.get(Estudiante, estudiante_id)
    if not row:
        raise HTTPException(status_code=404, detail="Alumno no encontrado")
    return row


@router.post("", response_model=EstudianteOut, status_code=status.HTTP_201_CREATED)
async def crear_alumno(
    data: EstudianteCreate,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    # Verificar CURP única
    existing = await db.execute(
        select(Persona).where(Persona.curp == data.persona.curp)
    )
    if existing.scalar_one_or_none():
        raise HTTPException(status_code=409, detail="CURP ya registrada")

    persona = Persona(**data.persona.model_dump())
    db.add(persona)
    await db.flush()

    # Generar matrícula automática
    count = await db.execute(select(func.count()).select_from(Estudiante))
    seq = (count.scalar() or 0) + 1
    matricula = f"MAT-{seq:06d}"

    from app.models.personas import Estatus
    est = await db.execute(
        select(Estatus).where(Estatus.entidad == "ESTUDIANTE", Estatus.nombre_estatus == "INSCRITO")
    )
    estatus_id = est.scalar_one_or_none()

    estudiante = Estudiante(
        matricula=matricula,
        persona_id=persona.id,
        plantel_id=data.plantel_id,
        fecha_ingreso=data.fecha_ingreso,
        estatus_id=estatus_id.id if estatus_id else None,
    )
    db.add(estudiante)
    await db.commit()
    await db.refresh(estudiante)
    return estudiante


@router.get("/{estudiante_id}/inscripciones", response_model=list[InscripcionOut])
async def inscripciones_del_alumno(
    estudiante_id: uuid.UUID,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    q = select(Inscripcion).where(Inscripcion.estudiante_id == estudiante_id)
    rows = await db.execute(q)
    return rows.scalars().all()


@router.post("/{estudiante_id}/inscripciones", response_model=InscripcionOut, status_code=201)
async def inscribir_alumno(
    estudiante_id: uuid.UUID,
    data: InscripcionCreate,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    # Verificar que el grupo tenga capacidad
    grupo = await db.get(Grupo, data.grupo_id)
    if not grupo:
        raise HTTPException(status_code=404, detail="Grupo no encontrado")
    if not grupo.is_active:
        raise HTTPException(status_code=400, detail="El grupo no está activo para inscripciones")

    count_q = await db.execute(
        select(func.count()).select_from(Inscripcion)
        .where(Inscripcion.grupo_id == data.grupo_id, Inscripcion.is_active == True)
    )
    inscritos = count_q.scalar() or 0
    if inscritos >= grupo.capacidad_maxima:
        raise HTTPException(status_code=400, detail=f"Grupo lleno ({inscritos}/{grupo.capacidad_maxima})")

    from app.models.personas import Estatus
    est = await db.execute(
        select(Estatus).where(Estatus.entidad == "INSCRIPCION", Estatus.nombre_estatus == "VIGENTE")
    )
    estatus_id = est.scalar_one_or_none()

    insc = Inscripcion(
        estudiante_id=estudiante_id,
        grupo_id=data.grupo_id,
        ciclo_escolar_id=data.ciclo_escolar_id,
        fecha_inscripcion=data.fecha_inscripcion,
        estatus_id=estatus_id.id if estatus_id else None,
    )
    db.add(insc)
    await db.commit()
    await db.refresh(insc)
    return insc
