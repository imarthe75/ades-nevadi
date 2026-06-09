"""
/calificaciones — Libreta de calificaciones y boletas con optimistic locking.

Endpoints:
  POST   /calificaciones                         — registrar calificación (row_version en payload)
  PUT    /calificaciones/{id}                    — actualizar (row_version en payload)
  GET    /calificaciones/grupo/{grupo_id}/libreta — libreta completa del grupo
  GET    /calificaciones/alumno/{id}/boleta       — boleta del alumno (ciclo)
  GET    /calificaciones/periodos                 — periodos del ciclo

Spec: spec/standards/api-design.md § Optimistic Locking
"""
from __future__ import annotations
import asyncio
import uuid
from decimal import Decimal
from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, func
from sqlalchemy.orm import selectinload
from app.core.database import get_db
from app.core.security import get_current_user
from app.core.optimistic_locking import check_row_version
from app.models.operacion import CalificacionPeriodo, PeriodoEvaluacion
from app.models.academica import Grupo, CicloEscolar, NivelEducativo
from app.models.personas import Estudiante, Persona, Inscripcion
from app.models.materias import Materia, MateriaPlan
from app.schemas.operacion import (
    CalificacionCreate, CalificacionUpdate, CalificacionOut,
    LibretaGrupo, RegistroLibreta, BolentaAlumno, BolentaMateria, PeriodoOut,
    PeriodoSimple,
)

router = APIRouter(prefix="/calificaciones", tags=["calificaciones"])


# ── Periodos ──────────────────────────────────────────────────────────────────

@router.get("/periodos", response_model=list[PeriodoOut])
async def listar_periodos(
    ciclo_id: uuid.UUID | None = None,
    tipo: str | None = Query(None, description="ORDINARIO | FINAL | EXTRAORDINARIO"),
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    q = select(PeriodoEvaluacion).where(PeriodoEvaluacion.is_active == True)
    if ciclo_id:
        q = q.where(PeriodoEvaluacion.ciclo_escolar_id == ciclo_id)
    if tipo:
        q = q.where(PeriodoEvaluacion.tipo_periodo == tipo.upper())
    q = q.order_by(PeriodoEvaluacion.ciclo_escolar_id, PeriodoEvaluacion.numero_periodo)
    rows = await db.execute(q)
    return rows.scalars().all()


# ── CRUD calificaciones ───────────────────────────────────────────────────────

@router.post("", response_model=CalificacionOut, status_code=status.HTTP_201_CREATED)
async def registrar_calificacion(
    data: CalificacionCreate,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    existing = (await db.execute(
        select(CalificacionPeriodo).where(
            CalificacionPeriodo.estudiante_id == data.estudiante_id,
            CalificacionPeriodo.materia_id == data.materia_id,
            CalificacionPeriodo.periodo_evaluacion_id == data.periodo_evaluacion_id,
        )
    )).scalar_one_or_none()

    if existing:
        existing.calificacion_final = data.calificacion_final
        existing.observaciones = data.observaciones
        await db.commit()
        await db.refresh(existing)
        cal = existing
    else:
        cal = CalificacionPeriodo(**data.model_dump())
        db.add(cal)
        await db.commit()
        await db.refresh(cal)

    # FASE 20 — Push al padre si calificación reprobatoria (<6)
    # Usamos nueva sesión en el background task para evitar usar db ya cerrada
    if data.calificacion_final is not None and data.calificacion_final < 6.0:
        from app.models.materias import Materia
        mat = await db.get(Materia, data.materia_id)
        _mat_nombre = mat.nombre_materia if mat else "materia"
        _est_id = data.estudiante_id
        _cal_val = float(data.calificacion_final)
        _periodo = getattr(data, "numero_periodo", 0) or 0
        async def _push_cal():
            from app.services.notification_triggers import on_calificacion_reprobatoria
            from app.core.database import AsyncSessionLocal
            async with AsyncSessionLocal() as _db:
                await on_calificacion_reprobatoria(_db, _est_id, _mat_nombre, _periodo, _cal_val)
        asyncio.create_task(_push_cal())

    return cal


@router.put("/{cal_id}", response_model=CalificacionOut)
async def actualizar_calificacion(
    cal_id: uuid.UUID,
    data: CalificacionUpdate,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    """
    Actualizar calificación con optimistic locking.

    Spec: spec/standards/api-design.md § Optimistic Locking
    Returns 409 Conflict si row_version no coincide con la BD.
    """
    cal = await db.get(CalificacionPeriodo, cal_id)
    if not cal:
        raise HTTPException(status_code=404, detail="Calificación no encontrada")

    # Verificar row_version para optimistic locking (Spec: API Design § Optimistic Locking)
    if hasattr(data, 'row_version') and data.row_version is not None:
        check_row_version(cal, data.row_version)

    cal.calificacion_final = data.calificacion_final
    if data.observaciones is not None:
        cal.observaciones = data.observaciones
    await db.commit()
    await db.refresh(cal)

    # FASE 20 — Push si nueva calificación es reprobatoria
    if data.calificacion_final is not None and data.calificacion_final < 6.0:
        _est_id2 = cal.estudiante_id
        _cal_val2 = float(data.calificacion_final)
        async def _push_cal2():
            from app.services.notification_triggers import on_calificacion_reprobatoria
            from app.core.database import AsyncSessionLocal
            async with AsyncSessionLocal() as _db2:
                await on_calificacion_reprobatoria(_db2, _est_id2, "materia", 0, _cal_val2)
        asyncio.create_task(_push_cal2())

    return cal


# ── Libreta del grupo ─────────────────────────────────────────────────────────

@router.get("/grupo/{grupo_id}/libreta", response_model=LibretaGrupo)
async def libreta_grupo(
    grupo_id: uuid.UUID,
    materia_id: uuid.UUID | None = None,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    """
    Libreta de calificaciones de un grupo para una materia.
    Si no se especifica materia_id, devuelve la primera materia del grupo.
    """
    grupo = await db.get(Grupo, grupo_id)
    if not grupo:
        raise HTTPException(status_code=404, detail="Grupo no encontrado")

    # Obtener periodos del ciclo del grupo
    periodos_q = (
        select(PeriodoEvaluacion)
        .where(
            PeriodoEvaluacion.ciclo_escolar_id == grupo.ciclo_escolar_id,
            PeriodoEvaluacion.is_active == True,
        )
        .order_by(PeriodoEvaluacion.numero_periodo)
    )
    periodos = (await db.execute(periodos_q)).scalars().all()
    periodo_nombres = [p.nombre_periodo for p in periodos]
    periodo_ids = {p.id: p.nombre_periodo for p in periodos}
    periodos_detalle = [PeriodoSimple(id=p.id, nombre_periodo=p.nombre_periodo) for p in periodos]

    # Alumnos inscritos activos
    inscr_q = (
        select(Inscripcion.estudiante_id, Estudiante.matricula, Persona.nombre, Persona.apellido_paterno)
        .join(Estudiante, Estudiante.id == Inscripcion.estudiante_id)
        .join(Persona, Persona.id == Estudiante.persona_id)
        .where(Inscripcion.grupo_id == grupo_id, Inscripcion.is_active == True)
        .order_by(Persona.apellido_paterno, Persona.nombre)
    )
    alumnos = (await db.execute(inscr_q)).all()

    if not materia_id:
        return LibretaGrupo(
            grupo_id=grupo_id, materia_id=uuid.uuid4(),
            periodos=periodo_nombres, periodos_detalle=periodos_detalle, registros=[],
        )

    # Calificaciones del grupo para la materia
    cals_q = (
        select(CalificacionPeriodo)
        .where(
            CalificacionPeriodo.grupo_id == grupo_id,
            CalificacionPeriodo.materia_id == materia_id,
        )
    )
    cals = (await db.execute(cals_q)).scalars().all()
    # Indexar: {(estudiante_id, periodo_id) → calificacion_final}
    cal_map: dict[tuple, Decimal] = {
        (str(c.estudiante_id), str(c.periodo_evaluacion_id)): c.calificacion_final
        for c in cals
    }

    registros = []
    for alumno in alumnos:
        cals_alumno: dict[str, Decimal | None] = {}
        suma = Decimal("0")
        conteo = 0
        for periodo_id, periodo_nombre in periodo_ids.items():
            val = cal_map.get((str(alumno.estudiante_id), str(periodo_id)))
            cals_alumno[periodo_nombre] = val
            if val is not None:
                suma += val
                conteo += 1

        promedio = round(suma / conteo, 2) if conteo > 0 else None
        registros.append(
            RegistroLibreta(
                estudiante_id=alumno.estudiante_id,
                matricula=alumno.matricula,
                nombre_completo=f"{alumno.apellido_paterno} {alumno.nombre}",
                calificaciones=cals_alumno,
                promedio=promedio,
            )
        )

    return LibretaGrupo(
        grupo_id=grupo_id,
        materia_id=materia_id,
        periodos=periodo_nombres,
        periodos_detalle=periodos_detalle,
        registros=registros,
    )


# ── Boleta del alumno ─────────────────────────────────────────────────────────

@router.get("/alumno/{estudiante_id}/boleta", response_model=BolentaAlumno)
async def boleta_alumno(
    estudiante_id: uuid.UUID,
    ciclo_id: uuid.UUID | None = None,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    """
    Boleta de calificaciones de un alumno.
    Si no se especifica ciclo_id, usa el ciclo vigente.
    """
    estudiante = (await db.execute(
        select(Estudiante).options(selectinload(Estudiante.persona))
        .where(Estudiante.id == estudiante_id)
    )).scalar_one_or_none()
    if not estudiante:
        raise HTTPException(status_code=404, detail="Alumno no encontrado")

    # Inscripción activa en el ciclo solicitado (o vigente)
    inscr_q = (
        select(Inscripcion)
        .join(CicloEscolar, CicloEscolar.id == Inscripcion.ciclo_escolar_id)
        .where(
            Inscripcion.estudiante_id == estudiante_id,
            Inscripcion.is_active == True,
        )
    )
    if ciclo_id:
        inscr_q = inscr_q.where(Inscripcion.ciclo_escolar_id == ciclo_id)
    else:
        inscr_q = inscr_q.where(CicloEscolar.es_vigente == True)

    inscripcion = (await db.execute(inscr_q)).scalar_one_or_none()
    if not inscripcion:
        raise HTTPException(status_code=404, detail="No hay inscripción activa para ese ciclo")

    ciclo = await db.get(CicloEscolar, inscripcion.ciclo_escolar_id)

    # Periodos del ciclo
    periodos_q = (
        select(PeriodoEvaluacion)
        .where(
            PeriodoEvaluacion.ciclo_escolar_id == inscripcion.ciclo_escolar_id,
            PeriodoEvaluacion.is_active == True,
        )
        .order_by(PeriodoEvaluacion.numero_periodo)
    )
    periodos = (await db.execute(periodos_q)).scalars().all()
    periodo_ids = {p.id: p.nombre_periodo for p in periodos}

    # Materias del plan para el grado del grupo del alumno
    grupo = await db.get(Grupo, inscripcion.grupo_id)
    planes_q = (
        select(MateriaPlan)
        .options(selectinload(MateriaPlan.materia))
        .where(
            MateriaPlan.grado_id == grupo.grado_id,
            MateriaPlan.ciclo_escolar_id == inscripcion.ciclo_escolar_id,
            MateriaPlan.is_active == True,
        )
        .order_by(MateriaPlan.orden)
    )
    planes = (await db.execute(planes_q)).scalars().all()

    # Calificaciones del alumno
    cals_q = select(CalificacionPeriodo).where(
        CalificacionPeriodo.estudiante_id == estudiante_id,
        CalificacionPeriodo.grupo_id == inscripcion.grupo_id,
    )
    cals = (await db.execute(cals_q)).scalars().all()
    cal_map: dict[tuple, Decimal] = {
        (str(c.materia_id), str(c.periodo_evaluacion_id)): c.calificacion_final
        for c in cals
    }

    materias_boleta = []
    promedios_materia: list[Decimal] = []

    for plan in planes:
        cals_mat: dict[str, Decimal | None] = {}
        suma = Decimal("0")
        conteo = 0
        for periodo_id, periodo_nombre in periodo_ids.items():
            val = cal_map.get((str(plan.materia_id), str(periodo_id)))
            cals_mat[periodo_nombre] = val
            if val is not None:
                suma += val
                conteo += 1
        promedio = round(suma / conteo, 2) if conteo > 0 else None
        if promedio is not None:
            promedios_materia.append(promedio)

        materias_boleta.append(BolentaMateria(
            materia_nombre=plan.materia.nombre_materia,
            calificaciones=cals_mat,
            promedio=promedio,
            acreditado=(promedio >= Decimal("6.0")) if promedio is not None else False,
        ))

    promedio_general = (
        round(sum(promedios_materia) / len(promedios_materia), 2)
        if promedios_materia else None
    )

    return BolentaAlumno(
        estudiante_id=estudiante_id,
        matricula=estudiante.matricula,
        nombre_completo=estudiante.persona.nombre_completo if estudiante.persona else "",
        ciclo_nombre=ciclo.nombre_ciclo if ciclo else "",
        materias=materias_boleta,
        promedio_general=promedio_general,
    )
