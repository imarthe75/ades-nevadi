"""
Materias y Planes de Estudio — FASE 19.

  GET    /materias                        — lista materias con filtros
  GET    /materias/{id}                   — detalle + temas + estadísticas
  POST   /materias                        — crear materia (admin)
  PATCH  /materias/{id}                   — actualizar materia (admin)
  DELETE /materias/{id}                   — soft-delete (admin)

  GET    /planes-estudio                  — plan vigente con filtros
  POST   /planes-estudio                  — asignar materia a grado+ciclo
  PATCH  /planes-estudio/{id}             — actualizar horas / obligatoria
  DELETE /planes-estudio/{id}             — quitar asignación (soft-delete)

  GET    /planes-estudio/{id}/temas       — temas del plan
  POST   /planes-estudio/{id}/temas       — crear tema
  PUT    /planes-estudio/{id}/temas/{tid} — editar tema
  DELETE /planes-estudio/{id}/temas/{tid} — eliminar tema

  GET    /materias/{id}/estadisticas      — uso: tareas, calificaciones, rúbricas
  GET    /materias/{id}/rubricas          — rúbricas que usan esta materia
"""

from __future__ import annotations

import uuid
from decimal import Decimal

from fastapi import APIRouter, Depends, HTTPException, Query, status
from pydantic import BaseModel, Field
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, func, delete, or_
from sqlalchemy.orm import selectinload

from app.core.database import get_db
from app.core.security import get_current_user, AdesUser, get_ades_user
from app.models.materias import Materia, MateriaPlan, Tema
from app.models.academica import NivelEducativo, Grado, CicloEscolar
from app.schemas.materias import (
    MateriaOut, MateriaCreate, MateriaUpdate,
    MateriaPlanOut, MateriaPlanCreate, MateriaPlanUpdate,
    TemaOut,
)
from app.schemas.base import AdesSchema

router = APIRouter(tags=["materias"])


# ── Modelos inline ─────────────────────────────────────────────────────────────

class TemaCreate(AdesSchema):
    nombre_tema: str = Field(min_length=2, max_length=255)
    descripcion: str | None = None
    orden: int = Field(1, ge=1)
    periodo_sugerido: int | None = Field(None, ge=1, le=6)


class TemaUpdate(AdesSchema):
    nombre_tema: str | None = Field(None, min_length=2, max_length=255)
    descripcion: str | None = None
    orden: int | None = Field(None, ge=1)
    periodo_sugerido: int | None = Field(None, ge=1, le=6)


class MateriaEstadisticas(BaseModel):
    materia_id: uuid.UUID
    nombre_materia: str
    grados_asignados: int
    total_tareas: int
    total_calificaciones: int
    total_rubricas: int
    promedio_calificaciones: float | None


# ── GET /materias ──────────────────────────────────────────────────────────────

@router.get("/materias", response_model=list[MateriaOut])
async def listar_materias(
    nivel: str | None = Query(None, description="PRIMARIA | SECUNDARIA | PREPARATORIA"),
    buscar: str | None = None,
    nivel_id: uuid.UUID | None = None,
    incluir_inactivas: bool = False,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    q = select(Materia).join(NivelEducativo, NivelEducativo.id == Materia.nivel_educativo_id)
    if nivel:
        q = q.where(NivelEducativo.nombre_nivel == nivel.upper())
    if nivel_id:
        q = q.where(Materia.nivel_educativo_id == nivel_id)
    if buscar:
        q = q.where(Materia.nombre_materia.ilike(f"%{buscar}%"))
    if not incluir_inactivas:
        q = q.where(Materia.is_active == True)
    q = q.order_by(NivelEducativo.nombre_nivel, Materia.nombre_materia)
    return (await db.execute(q)).scalars().all()


@router.get("/materias/{materia_id}", response_model=MateriaOut)
async def obtener_materia(
    materia_id: uuid.UUID,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    row = await db.get(Materia, materia_id)
    if not row:
        raise HTTPException(404, "Materia no encontrada")
    return row


# ── POST /materias ─────────────────────────────────────────────────────────────

@router.post("/materias", response_model=MateriaOut, status_code=201)
async def crear_materia(
    body: MateriaCreate,
    db: AsyncSession = Depends(get_db),
    ades_user: AdesUser = Depends(get_ades_user),
):
    if ades_user.nivel_acceso > 2:
        raise HTTPException(403, "Solo administradores y coordinadores pueden crear materias")

    nivel = await db.get(NivelEducativo, body.nivel_educativo_id)
    if not nivel:
        raise HTTPException(404, "Nivel educativo no encontrado")

    obj = Materia(**body.model_dump())
    db.add(obj)
    await db.commit()
    await db.refresh(obj)
    return obj


# ── PATCH /materias/{id} ───────────────────────────────────────────────────────

@router.patch("/materias/{materia_id}", response_model=MateriaOut)
async def actualizar_materia(
    materia_id: uuid.UUID,
    body: MateriaUpdate,
    db: AsyncSession = Depends(get_db),
    ades_user: AdesUser = Depends(get_ades_user),
):
    if ades_user.nivel_acceso > 2:
        raise HTTPException(403, "Solo administradores y coordinadores pueden editar materias")

    obj = await db.get(Materia, materia_id)
    if not obj:
        raise HTTPException(404, "Materia no encontrada")

    for k, v in body.model_dump(exclude_unset=True).items():
        setattr(obj, k, v)
    await db.commit()
    await db.refresh(obj)
    return obj


# ── DELETE /materias/{id} ──────────────────────────────────────────────────────

@router.delete("/materias/{materia_id}", status_code=204)
async def eliminar_materia(
    materia_id: uuid.UUID,
    db: AsyncSession = Depends(get_db),
    ades_user: AdesUser = Depends(get_ades_user),
):
    if ades_user.nivel_acceso > 1:
        raise HTTPException(403, "Solo ADMIN_GLOBAL o ADMIN_PLANTEL")

    obj = await db.get(Materia, materia_id)
    if not obj:
        raise HTTPException(404, "Materia no encontrada")

    obj.is_active = False
    await db.commit()


# ── GET /planes-estudio ────────────────────────────────────────────────────────

@router.get("/planes-estudio", response_model=list[MateriaPlanOut])
async def listar_plan_estudios(
    grado_id: uuid.UUID | None = None,
    ciclo_id: uuid.UUID | None = None,
    plantel_id: uuid.UUID | None = None,
    nivel: str | None = None,
    nivel_id: uuid.UUID | None = None,
    incluir_inactivas: bool = False,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    q = (
        select(MateriaPlan)
        .options(selectinload(MateriaPlan.materia))
        .join(Grado, Grado.id == MateriaPlan.grado_id)
        .join(NivelEducativo, NivelEducativo.id == Grado.nivel_educativo_id)
    )
    if grado_id:
        q = q.where(MateriaPlan.grado_id == grado_id)
    if ciclo_id:
        q = q.where(MateriaPlan.ciclo_escolar_id == ciclo_id)
    if plantel_id:
        q = q.where(Grado.plantel_id == plantel_id)
    if nivel:
        q = q.where(NivelEducativo.nombre_nivel == nivel.upper())
    if nivel_id:
        q = q.where(NivelEducativo.id == nivel_id)
    if not incluir_inactivas:
        q = q.where(MateriaPlan.is_active == True)
    q = q.order_by(Grado.numero_grado, MateriaPlan.orden, MateriaPlan.materia_id)
    return (await db.execute(q)).scalars().all()


# ── POST /planes-estudio ───────────────────────────────────────────────────────

@router.post("/planes-estudio", response_model=MateriaPlanOut, status_code=201)
async def asignar_materia_plan(
    body: MateriaPlanCreate,
    db: AsyncSession = Depends(get_db),
    ades_user: AdesUser = Depends(get_ades_user),
):
    if ades_user.nivel_acceso > 2:
        raise HTTPException(403, "Solo coordinadores o superiores")

    # Verificar que no exista ya
    existing = await db.execute(
        select(MateriaPlan).where(
            MateriaPlan.materia_id == body.materia_id,
            MateriaPlan.grado_id == body.grado_id,
            MateriaPlan.ciclo_escolar_id == body.ciclo_escolar_id,
        )
    )
    existing_obj = existing.scalar_one_or_none()
    if existing_obj:
        if not existing_obj.is_active:
            existing_obj.is_active = True
            if body.orden is not None:
                existing_obj.orden = body.orden
            if body.horas_semanales is not None:
                existing_obj.horas_semanales = body.horas_semanales
            if body.es_obligatoria is not None:
                existing_obj.es_obligatoria = body.es_obligatoria
            await db.commit()
            await db.refresh(existing_obj, ["materia"])
            return existing_obj
        raise HTTPException(409, "Esta materia ya está asignada a ese grado y ciclo")

    obj = MateriaPlan(**body.model_dump())
    db.add(obj)
    await db.commit()
    await db.refresh(obj, ["materia"])
    return obj


# ── PATCH /planes-estudio/{id} ─────────────────────────────────────────────────

@router.patch("/planes-estudio/{plan_id}", response_model=MateriaPlanOut)
async def actualizar_plan(
    plan_id: uuid.UUID,
    body: MateriaPlanUpdate,
    db: AsyncSession = Depends(get_db),
    ades_user: AdesUser = Depends(get_ades_user),
):
    if ades_user.nivel_acceso > 2:
        raise HTTPException(403, "Solo coordinadores o superiores")

    obj = await db.get(MateriaPlan, plan_id, options=[selectinload(MateriaPlan.materia)])
    if not obj:
        raise HTTPException(404, "Asignación no encontrada")

    for k, v in body.model_dump(exclude_unset=True).items():
        setattr(obj, k, v)
    await db.commit()
    await db.refresh(obj)
    return obj


# ── DELETE /planes-estudio/{id} ────────────────────────────────────────────────

@router.delete("/planes-estudio/{plan_id}", status_code=204)
async def quitar_asignacion(
    plan_id: uuid.UUID,
    db: AsyncSession = Depends(get_db),
    ades_user: AdesUser = Depends(get_ades_user),
):
    if ades_user.nivel_acceso > 1:
        raise HTTPException(403, "Solo administradores")

    obj = await db.get(MateriaPlan, plan_id)
    if not obj:
        raise HTTPException(404, "Asignación no encontrada")

    obj.is_active = False
    await db.commit()


# ── Temas ──────────────────────────────────────────────────────────────────────

@router.get("/planes-estudio/{plan_id}/temas", response_model=list[TemaOut])
async def temas_del_plan(
    plan_id: uuid.UUID,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    plan = await db.get(MateriaPlan, plan_id)
    if not plan:
        raise HTTPException(404, "Plan no encontrado")
    q = (
        select(Tema)
        .where(
            Tema.materia_id == plan.materia_id,
            or_(Tema.grado_id == plan.grado_id, Tema.grado_id == None),
            Tema.is_active == True,
        )
        .order_by(Tema.orden, Tema.nombre_tema)
    )
    return (await db.execute(q)).scalars().all()


@router.post("/planes-estudio/{plan_id}/temas", response_model=TemaOut, status_code=201)
async def crear_tema(
    plan_id: uuid.UUID,
    body: TemaCreate,
    db: AsyncSession = Depends(get_db),
    ades_user: AdesUser = Depends(get_ades_user),
):
    if ades_user.nivel_acceso > 3:
        raise HTTPException(403, "Solo coordinadores, directivos o docentes")

    plan = await db.get(MateriaPlan, plan_id)
    if not plan:
        raise HTTPException(404, "Plan no encontrado")

    obj = Tema(
        materia_id=plan.materia_id,
        grado_id=plan.grado_id,
        ciclo_escolar_id=plan.ciclo_escolar_id,
        **body.model_dump(),
    )
    db.add(obj)
    await db.commit()
    await db.refresh(obj)
    return obj


@router.put("/planes-estudio/{plan_id}/temas/{tema_id}", response_model=TemaOut)
async def actualizar_tema(
    plan_id: uuid.UUID,
    tema_id: uuid.UUID,
    body: TemaUpdate,
    db: AsyncSession = Depends(get_db),
    ades_user: AdesUser = Depends(get_ades_user),
):
    if ades_user.nivel_acceso > 3:
        raise HTTPException(403)

    plan = await db.get(MateriaPlan, plan_id)
    if not plan:
        raise HTTPException(404, "Plan no encontrado")

    obj = await db.get(Tema, tema_id)
    if not obj or obj.materia_id != plan.materia_id:
        raise HTTPException(404, "Tema no encontrado")

    for k, v in body.model_dump(exclude_unset=True).items():
        setattr(obj, k, v)
    await db.commit()
    await db.refresh(obj)
    return obj


@router.delete("/planes-estudio/{plan_id}/temas/{tema_id}", status_code=204)
async def eliminar_tema(
    plan_id: uuid.UUID,
    tema_id: uuid.UUID,
    db: AsyncSession = Depends(get_db),
    ades_user: AdesUser = Depends(get_ades_user),
):
    if ades_user.nivel_acceso > 3:
        raise HTTPException(403)

    plan = await db.get(MateriaPlan, plan_id)
    if not plan:
        raise HTTPException(404, "Plan no encontrado")

    obj = await db.get(Tema, tema_id)
    if not obj or obj.materia_id != plan.materia_id:
        raise HTTPException(404)

    obj.is_active = False
    await db.commit()


# ── Estadísticas de materia ────────────────────────────────────────────────────

@router.get("/materias/{materia_id}/estadisticas", response_model=MateriaEstadisticas)
async def estadisticas_materia(
    materia_id: uuid.UUID,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    obj = await db.get(Materia, materia_id)
    if not obj:
        raise HTTPException(404, "Materia no encontrada")

    # Grados asignados
    grados_q = await db.execute(
        select(func.count()).select_from(MateriaPlan)
        .where(MateriaPlan.materia_id == materia_id, MateriaPlan.is_active == True)
    )
    grados_asignados = grados_q.scalar_one()

    # Tareas (via asignaciones)
    try:
        from app.models.operacion import Tarea
        tareas_q = await db.execute(
            select(func.count()).select_from(Tarea).where(Tarea.materia_id == materia_id)
        )
        total_tareas = tareas_q.scalar_one()
    except Exception:
        total_tareas = 0

    # Calificaciones y promedio
    try:
        from app.models.operacion import CalificacionPeriodo
        cals_q = await db.execute(
            select(
                func.count(CalificacionPeriodo.id),
                func.avg(CalificacionPeriodo.calificacion_final),
            )
            .where(CalificacionPeriodo.materia_id == materia_id)
        )
        total_cals, promedio = cals_q.one()
    except Exception:
        total_cals, promedio = 0, None

    # Rúbricas
    try:
        from app.models.operacion import Rubrica
        rubs_q = await db.execute(
            select(func.count()).select_from(Rubrica).where(Rubrica.materia_id == materia_id)
        )
        total_rubricas = rubs_q.scalar_one()
    except Exception:
        total_rubricas = 0

    return MateriaEstadisticas(
        materia_id=materia_id,
        nombre_materia=obj.nombre_materia,
        grados_asignados=grados_asignados,
        total_tareas=total_tareas,
        total_calificaciones=total_cals,
        total_rubricas=total_rubricas,
        promedio_calificaciones=round(float(promedio), 2) if promedio else None,
    )
