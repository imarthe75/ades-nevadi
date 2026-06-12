"""
/clases — Gestión de sesiones de clase.
Una clase es la unidad de registro de asistencia:
  docente crea la clase → marca asistencia → cambia estatus a IMPARTIDA.
"""
from __future__ import annotations
import uuid
from datetime import date
from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, func
from sqlalchemy.orm import selectinload
from app.core.database import get_db
from app.core.security import get_current_user
from app.models.operacion import Clase, Asistencia
from app.models.academica import Grupo, Grado
from app.models.personas import Estudiante, Inscripcion, Persona
from app.models.materias import Materia
from app.schemas.operacion import ClaseCreate, ClaseUpdate, ClaseOut

router = APIRouter(prefix="/clases", tags=["clases"])


@router.get("", response_model=list[ClaseOut])
async def listar_clases(
    grupo_id: uuid.UUID | None = None,
    materia_id: uuid.UUID | None = None,
    profesor_id: uuid.UUID | None = None,
    fecha_desde: date | None = None,
    fecha_hasta: date | None = None,
    estatus: str | None = Query(None, description="PROGRAMADA | IMPARTIDA | CANCELADA | SUSPENDIDA"),
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    q = (
        select(
            Clase,
            Grupo.nombre_grupo.label("grupo_nombre"),
            Materia.nombre_materia.label("materia_nombre"),
        )
        .outerjoin(Grupo, Grupo.id == Clase.grupo_id)
        .outerjoin(Materia, Materia.id == Clase.materia_id)
        .where(Clase.is_active == True)
    )
    if grupo_id:
        q = q.where(Clase.grupo_id == grupo_id)
    if materia_id:
        q = q.where(Clase.materia_id == materia_id)
    if profesor_id:
        q = q.where(Clase.profesor_id == profesor_id)
    if fecha_desde:
        q = q.where(Clase.fecha_clase >= fecha_desde)
    if fecha_hasta:
        q = q.where(Clase.fecha_clase <= fecha_hasta)
    if estatus:
        q = q.where(Clase.estatus_clase == estatus.upper())
    q = q.order_by(Clase.fecha_clase.desc(), Clase.hora_inicio)
    rows = await db.execute(q)
    
    results = []
    for row in rows:
        clase_obj = row.Clase
        clase_obj.grupo_nombre = row.grupo_nombre
        clase_obj.materia_nombre = row.materia_nombre
        results.append(clase_obj)
    return results


@router.get("/{clase_id}", response_model=ClaseOut)
async def obtener_clase(
    clase_id: uuid.UUID,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    q = (
        select(
            Clase,
            Grupo.nombre_grupo.label("grupo_nombre"),
            Materia.nombre_materia.label("materia_nombre"),
        )
        .outerjoin(Grupo, Grupo.id == Clase.grupo_id)
        .outerjoin(Materia, Materia.id == Clase.materia_id)
        .where(Clase.id == clase_id)
    )
    res = (await db.execute(q)).one_or_none()
    if not res:
        raise HTTPException(status_code=404, detail="Clase no encontrada")
    clase_obj = res.Clase
    clase_obj.grupo_nombre = res.grupo_nombre
    clase_obj.materia_nombre = res.materia_nombre
    return clase_obj


@router.post("", response_model=ClaseOut, status_code=status.HTTP_201_CREATED)
async def registrar_clase(
    data: ClaseCreate,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    clase = Clase(**data.model_dump())
    db.add(clase)
    await db.commit()
    await db.refresh(clase)
    return clase


@router.patch("/{clase_id}", response_model=ClaseOut)
async def actualizar_clase(
    clase_id: uuid.UUID,
    data: ClaseUpdate,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    clase = await db.get(Clase, clase_id)
    if not clase:
        raise HTTPException(status_code=404, detail="Clase no encontrada")
    for field, value in data.model_dump(exclude_none=True).items():
        setattr(clase, field, value)
    await db.commit()
    await db.refresh(clase)
    return clase


@router.get("/{clase_id}/alumnos-esperados")
async def alumnos_esperados(
    clase_id: uuid.UUID,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    """Devuelve los alumnos inscritos en el grupo de la clase (para pre-poblar el pase de lista)."""
    clase = await db.get(Clase, clase_id)
    if not clase:
        raise HTTPException(status_code=404, detail="Clase no encontrada")

    # Obtener alumnos inscritos activos en el grupo con sus nombres
    q = (
        select(
            Inscripcion.estudiante_id,
            Estudiante.matricula,
            Persona.nombre,
            Persona.apellido_paterno,
            Persona.apellido_materno,
        )
        .join(Estudiante, Estudiante.id == Inscripcion.estudiante_id)
        .join(Persona, Persona.id == Estudiante.persona_id)
        .where(
            Inscripcion.grupo_id == clase.grupo_id,
            Inscripcion.is_active == True,
            Estudiante.is_active == True,
        )
        .order_by(Persona.apellido_paterno, Persona.apellido_materno, Persona.nombre)
    )
    rows = (await db.execute(q)).all()

    # Verificar cuáles ya tienen asistencia registrada
    asist_q = select(Asistencia.estudiante_id, Asistencia.estatus_asistencia).where(
        Asistencia.clase_id == clase_id
    )
    asist_rows = (await db.execute(asist_q)).all()
    asist_map = {str(r.estudiante_id): r.estatus_asistencia for r in asist_rows}

    return [
        {
            "estudiante_id": str(r.estudiante_id),
            "matricula": r.matricula,
            "nombre": f"{r.apellido_paterno} {r.apellido_materno or ''} {r.nombre}".strip(),
            "asistencia_registrada": str(r.estudiante_id) in asist_map,
            "estatus": asist_map.get(str(r.estudiante_id)),
        }
        for r in rows
    ]
