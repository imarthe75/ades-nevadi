"""
/horarios — Gestión de horarios escolares.
  GET  /horarios/grupo/{grupo_id}    — vista semanal del grupo
  GET  /horarios/profesor/{id}       — vista semanal del docente
  POST /horarios                     — crear entrada manual
  DELETE /horarios/{id}              — eliminar

/aulas — Espacios físicos por plantel.
  GET  /aulas                        — listar por plantel
  POST /aulas                        — crear

/disponibilidad — Restricciones horarias docente para aSc.
  GET  /disponibilidad/profesor/{id} — restricciones del docente
  POST /disponibilidad               — registrar restricción

/horarios/exportar-asc/{ciclo_id}   — exportar XML para aSc TimeTables
"""
from __future__ import annotations
import uuid
import xml.etree.ElementTree as ET
from io import BytesIO
from fastapi import APIRouter, Depends, HTTPException, Query, Response, status
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from sqlalchemy.orm import selectinload

from app.core.database import get_db
from app.core.security import get_current_user
from app.models.fase3 import Aula, Horario, DisponibilidadDocente
from app.models.academica import Grupo, CicloEscolar
from app.models.materias import Materia
from app.models.personas import Profesor, Persona
from app.schemas.fase3 import (
    AulaCreate, AulaOut,
    HorarioCreate, HorarioOut, HorarioSemanalGrupo, HorarioSemanalProfesor,
    DisponibilidadCreate, DisponibilidadOut,
)

router = APIRouter(tags=["horarios"])

DIAS = {1: "Lunes", 2: "Martes", 3: "Miércoles", 4: "Jueves", 5: "Viernes"}


# ── AULAS ─────────────────────────────────────────────────────────────────────

@router.get("/aulas", response_model=list[AulaOut])
async def listar_aulas(
    plantel_id: uuid.UUID | None = None,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    q = select(Aula).where(Aula.is_active == True)
    if plantel_id:
        q = q.where(Aula.plantel_id == plantel_id)
    q = q.order_by(Aula.nombre_aula)
    rows = await db.execute(q)
    return rows.scalars().all()


@router.post("/aulas", response_model=AulaOut, status_code=status.HTTP_201_CREATED)
async def crear_aula(
    data: AulaCreate,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    aula = Aula(**data.model_dump())
    db.add(aula)
    await db.commit()
    await db.refresh(aula)
    return aula


# ── HORARIOS ──────────────────────────────────────────────────────────────────

async def _enriquecer_horario(h: Horario, db: AsyncSession) -> HorarioOut:
    """Añade nombres legibles a un objeto Horario."""
    materia = await db.get(Materia, h.materia_id)
    grupo = await db.get(Grupo, h.grupo_id)
    profesor = await db.get(Profesor, h.profesor_id)
    persona = await db.get(Persona, profesor.persona_id) if profesor else None
    aula = await db.get(Aula, h.aula_id) if h.aula_id else None

    nombre_prof = f"{persona.apellido_paterno} {persona.nombre}" if persona else None

    return HorarioOut(
        id=h.id,
        grupo_id=h.grupo_id,
        materia_id=h.materia_id,
        profesor_id=h.profesor_id,
        aula_id=h.aula_id,
        ciclo_escolar_id=h.ciclo_escolar_id,
        dia_semana=h.dia_semana,
        hora_inicio=h.hora_inicio,
        hora_fin=h.hora_fin,
        origen=h.origen,
        nombre_materia=materia.nombre_materia if materia else None,
        nombre_grupo=grupo.nombre_grupo if grupo else None,
        nombre_profesor=nombre_prof,
        nombre_aula=aula.nombre_aula if aula else None,
    )


@router.get("/horarios/grupo/{grupo_id}", response_model=HorarioSemanalGrupo)
async def horario_grupo(
    grupo_id: uuid.UUID,
    ciclo_id: uuid.UUID | None = None,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    grupo = await db.get(Grupo, grupo_id)
    if not grupo:
        raise HTTPException(status_code=404, detail="Grupo no encontrado")

    ciclo_escolar_id = ciclo_id or grupo.ciclo_escolar_id
    q = (
        select(Horario)
        .where(
            Horario.grupo_id == grupo_id,
            Horario.ciclo_escolar_id == ciclo_escolar_id,
            Horario.is_active == True,
        )
        .order_by(Horario.dia_semana, Horario.hora_inicio)
    )
    horarios = (await db.execute(q)).scalars().all()
    entradas = [await _enriquecer_horario(h, db) for h in horarios]

    return HorarioSemanalGrupo(
        grupo_id=grupo_id,
        nombre_grupo=grupo.nombre_grupo,
        ciclo_escolar_id=ciclo_escolar_id,
        entradas=entradas,
    )


@router.get("/horarios/profesor/{profesor_id}", response_model=HorarioSemanalProfesor)
async def horario_profesor(
    profesor_id: uuid.UUID,
    ciclo_id: uuid.UUID | None = None,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    q_ciclo = select(CicloEscolar.id).where(CicloEscolar.es_vigente == True).limit(1)
    ciclo_escolar_id = ciclo_id or (await db.execute(q_ciclo)).scalar_one_or_none()

    q = (
        select(Horario)
        .where(
            Horario.profesor_id == profesor_id,
            Horario.ciclo_escolar_id == ciclo_escolar_id,
            Horario.is_active == True,
        )
        .order_by(Horario.dia_semana, Horario.hora_inicio)
    )
    horarios = (await db.execute(q)).scalars().all()
    entradas = [await _enriquecer_horario(h, db) for h in horarios]

    return HorarioSemanalProfesor(
        profesor_id=profesor_id,
        ciclo_escolar_id=ciclo_escolar_id,
        entradas=entradas,
    )


@router.post("/horarios", response_model=HorarioOut, status_code=status.HTTP_201_CREATED)
async def crear_horario(
    data: HorarioCreate,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    h = Horario(**data.model_dump())
    db.add(h)
    await db.commit()
    await db.refresh(h)
    return await _enriquecer_horario(h, db)


@router.delete("/horarios/{horario_id}", status_code=status.HTTP_204_NO_CONTENT)
async def eliminar_horario(
    horario_id: uuid.UUID,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    h = await db.get(Horario, horario_id)
    if not h:
        raise HTTPException(status_code=404, detail="Horario no encontrado")
    h.is_active = False
    await db.commit()


# ── DISPONIBILIDAD DOCENTE ────────────────────────────────────────────────────

@router.get("/disponibilidad/profesor/{profesor_id}", response_model=list[DisponibilidadOut])
async def disponibilidad_profesor(
    profesor_id: uuid.UUID,
    ciclo_id: uuid.UUID | None = None,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    q = (
        select(DisponibilidadDocente)
        .where(DisponibilidadDocente.profesor_id == profesor_id, DisponibilidadDocente.is_active == True)
        .order_by(DisponibilidadDocente.dia_semana, DisponibilidadDocente.hora_inicio)
    )
    if ciclo_id:
        q = q.where(DisponibilidadDocente.ciclo_escolar_id == ciclo_id)
    rows = (await db.execute(q)).scalars().all()
    return rows


@router.post("/disponibilidad", response_model=DisponibilidadOut, status_code=status.HTTP_201_CREATED)
async def registrar_disponibilidad(
    data: DisponibilidadCreate,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    d = DisponibilidadDocente(**data.model_dump())
    db.add(d)
    await db.commit()
    await db.refresh(d)
    return d


# ── EXPORTAR XML PARA aSc TimeTables ─────────────────────────────────────────

@router.get("/horarios/exportar-asc/{ciclo_id}")
async def exportar_asc_xml(
    ciclo_id: uuid.UUID,
    plantel_id: uuid.UUID | None = None,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    """
    Genera XML compatible con aSc TimeTables para importar como datos base.
    Incluye: profesores, aulas, grupos, materias y restricciones de disponibilidad.
    """
    root = ET.Element("timetable", attrib={"software": "aSc TimeTables", "version": "2024"})

    # Aulas
    aulas_el = ET.SubElement(root, "classrooms")
    q_aulas = select(Aula).where(Aula.is_active == True)
    if plantel_id:
        q_aulas = q_aulas.where(Aula.plantel_id == plantel_id)
    aulas = (await db.execute(q_aulas)).scalars().all()
    for a in aulas:
        ET.SubElement(aulas_el, "classroom", attrib={
            "id": str(a.id), "name": a.nombre_aula, "capacity": str(a.capacidad or 35),
        })

    # Profesores
    profs_el = ET.SubElement(root, "teachers")
    q_profs = select(Profesor, Persona).join(Persona, Persona.id == Profesor.persona_id)
    if plantel_id:
        q_profs = q_profs.where(Profesor.plantel_id == plantel_id)
    profs = (await db.execute(q_profs)).all()

    # Disponibilidades por profesor
    disp_q = select(DisponibilidadDocente).where(
        DisponibilidadDocente.ciclo_escolar_id == ciclo_id,
        DisponibilidadDocente.is_active == True,
    )
    disps = (await db.execute(disp_q)).scalars().all()
    disp_map: dict[str, list[DisponibilidadDocente]] = {}
    for d in disps:
        disp_map.setdefault(str(d.profesor_id), []).append(d)

    for prof, persona in profs:
        prof_el = ET.SubElement(profs_el, "teacher", attrib={
            "id": str(prof.id),
            "firstname": persona.nombre,
            "lastname": persona.apellido_paterno,
            "short": prof.numero_empleado,
        })
        # Restricciones de disponibilidad
        for d in disp_map.get(str(prof.id), []):
            if not d.disponible:
                ET.SubElement(prof_el, "notavailable", attrib={
                    "day": str(d.dia_semana),
                    "starttime": d.hora_inicio.strftime("%H:%M"),
                    "endtime": d.hora_fin.strftime("%H:%M"),
                })

    # Grupos
    grupos_el = ET.SubElement(root, "classes")
    q_grupos = (
        select(Grupo)
        .join(CicloEscolar, CicloEscolar.id == Grupo.ciclo_escolar_id)
        .where(Grupo.ciclo_escolar_id == ciclo_id, Grupo.is_active == True)
    )
    grupos = (await db.execute(q_grupos)).scalars().all()
    for g in grupos:
        ET.SubElement(grupos_el, "class", attrib={"id": str(g.id), "name": g.nombre_grupo})

    xml_bytes = ET.tostring(root, encoding="unicode", xml_declaration=False)
    return Response(
        content=f'<?xml version="1.0" encoding="UTF-8"?>\n{xml_bytes}',
        media_type="application/xml",
        headers={"Content-Disposition": f"attachment; filename=asc_horarios_{ciclo_id}.xml"},
    )
