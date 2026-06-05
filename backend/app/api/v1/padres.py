"""
Portal para PADRE_FAMILIA.

  GET /padres/mis-alumnos         — alumnos vinculados al tutor autenticado
  GET /padres/calificaciones/{id} — calificaciones del alumno (resumen por materia/periodo)
"""
from __future__ import annotations
import uuid
from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, func
from sqlalchemy.orm import selectinload
from app.core.database import get_db
from app.core.security import get_ades_user, AdesUser
from app.models.personas import (
    Estudiante, Persona, ContactoFamiliar, Inscripcion
)
from app.models.academica import (
    Grupo, Grado, NivelEducativo, CicloEscolar, Plantel
)
from app.models.materias import Materia
from app.models.operacion import PeriodoEvaluacion, CalificacionPeriodo
from app.schemas.base import AdesSchema

router = APIRouter(prefix="/padres", tags=["portal padres"])


class AlumnoVinculadoOut(AdesSchema):
    estudiante_id: uuid.UUID
    nombre_completo: str
    matricula: str
    nivel: str
    grado: str
    grupo: str
    plantel: str
    parentesco: str | None
    es_tutor_legal: bool


class CalificacionPadreOut(AdesSchema):
    materia: str
    periodo: str
    calificacion: float | None
    es_acreditado: bool


@router.get("/mis-alumnos", response_model=list[AlumnoVinculadoOut])
async def mis_alumnos(
    db: AsyncSession = Depends(get_db),
    ades_user: AdesUser = Depends(get_ades_user),
):
    """Devuelve todos los alumnos vinculados al tutor autenticado via contactos_familiares."""
    if not ades_user.persona_id:
        return []

    # Buscar contactos_familiares donde el tutor es esta persona
    q = (
        select(ContactoFamiliar)
        .where(
            ContactoFamiliar.persona_id == ades_user.persona_id,
            ContactoFamiliar.is_active == True,
        )
    )
    contactos = (await db.execute(q)).scalars().all()

    if not contactos:
        return []

    result = []
    for cf in contactos:
        est_q = (
            select(Estudiante)
            .options(selectinload(Estudiante.persona))
            .where(Estudiante.id == cf.estudiante_id, Estudiante.is_active == True)
        )
        est = (await db.execute(est_q)).scalar_one_or_none()
        if not est:
            continue

        # Inscripción vigente
        insc_q = (
            select(Inscripcion)
            .options(
                selectinload(Inscripcion.grupo).selectinload(Grupo.grado).selectinload(Grado.nivel),
                selectinload(Inscripcion.grupo).selectinload(Grupo.ciclo),
            )
            .join(Grupo, Grupo.id == Inscripcion.grupo_id)
            .join(CicloEscolar, CicloEscolar.id == Grupo.ciclo_escolar_id)
            .where(
                Inscripcion.estudiante_id == est.id,
                Inscripcion.is_active == True,
                CicloEscolar.es_vigente == True,
            )
            .limit(1)
        )
        insc = (await db.execute(insc_q)).scalar_one_or_none()

        nivel  = insc.grupo.grado.nivel.nombre_nivel if insc else '—'
        grado  = insc.grupo.grado.nombre_grado       if insc else '—'
        grupo  = insc.grupo.nombre_grupo             if insc else '—'

        plantel_id = est.plantel_id
        p = await db.get(Plantel, plantel_id)

        result.append(AlumnoVinculadoOut(
            estudiante_id=est.id,
            nombre_completo=est.persona.nombre_completo if est.persona else '—',
            matricula=est.matricula,
            nivel=nivel,
            grado=grado,
            grupo=grupo,
            plantel=p.nombre_plantel if p else '—',
            parentesco=cf.parentesco,
            es_tutor_legal=cf.es_tutor_legal,
        ))

    return result


@router.get("/calificaciones/{estudiante_id}", response_model=list[CalificacionPadreOut])
async def calificaciones_alumno(
    estudiante_id: uuid.UUID,
    db: AsyncSession = Depends(get_db),
    ades_user: AdesUser = Depends(get_ades_user),
):
    """Calificaciones por materia y período del alumno. Solo accesible por su tutor."""
    # Verificar que el tutor está vinculado a este alumno
    if ades_user.persona_id:
        cf_q = select(ContactoFamiliar).where(
            ContactoFamiliar.persona_id == ades_user.persona_id,
            ContactoFamiliar.estudiante_id == estudiante_id,
            ContactoFamiliar.is_active == True,
        )
        cf = (await db.execute(cf_q)).scalar_one_or_none()
        if not cf and ades_user.nivel_acceso > 3:
            raise HTTPException(status_code=403, detail="Sin acceso a este alumno")

    q = (
        select(
            CalificacionPeriodo,
            Materia.nombre_materia,
            PeriodoEvaluacion.nombre_periodo,
        )
        .join(Materia, Materia.id == CalificacionPeriodo.materia_id)
        .join(PeriodoEvaluacion, PeriodoEvaluacion.id == CalificacionPeriodo.periodo_evaluacion_id)
        .where(
            CalificacionPeriodo.estudiante_id == estudiante_id,
            CalificacionPeriodo.is_active == True,
        )
        .order_by(PeriodoEvaluacion.fecha_inicio, Materia.nombre_materia)
    )
    rows = (await db.execute(q)).all()

    return [CalificacionPadreOut(
        materia=row[1],
        periodo=row[2],
        calificacion=float(row[0].calificacion_final) if row[0].calificacion_final is not None else None,
        es_acreditado=row[0].es_acreditado or False,
    ) for row in rows]
