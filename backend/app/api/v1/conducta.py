"""
/conducta — Reportes de conducta, sanciones y planes de mejora.

  GET    /conducta                                    — lista reportes
  GET    /conducta/{id}                               — detalle reporte
  POST   /conducta                                    — crear reporte (SB-010)
  PATCH  /conducta/{id}                               — actualizar reporte
  GET    /conducta/{id}/detalle-completo              — reporte + sanción + plan + seguimientos
  POST   /conducta/{id}/sancion                       — aplicar sanción formal (SB-012)
  PATCH  /conducta/{id}/sancion/{sancion_id}          — actualizar sanción
  POST   /conducta/{id}/plan-mejora                   — crear plan de mejora (SB-013)
  PATCH  /conducta/{id}/plan-mejora/{plan_id}         — actualizar plan
  POST   /conducta/{id}/plan-mejora/{plan_id}/seguimiento — agregar seguimiento (SB-014)
  GET    /conducta/alumno/{estudiante_id}/historial   — historial completo por alumno
"""
from __future__ import annotations

import datetime
import uuid
from typing import Any, Optional

from fastapi import APIRouter, Depends, HTTPException, Query, status
from pydantic import BaseModel, Field
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select

from app.core.database import get_db
from app.core.security import get_current_user, get_ades_user, AdesUser
from app.models.fase3 import ReporteConducta
from app.schemas.fase3 import ConductaCreate, ConductaUpdate, ConductaOut

router = APIRouter(prefix="/conducta", tags=["conducta"])

_NIVEL_DIRECTOR = 2   # DIRECTOR o superior puede aplicar sanciones graves
_NIVEL_COORD    = 3   # COORDINADOR puede crear planes y seguimientos


# ── Schemas ───────────────────────────────────────────────────────────────────

class SancionCreate(BaseModel):
    tipo_sancion: str
    justificacion: str = Field(min_length=20)
    autorizado_por_id: uuid.UUID
    fecha_sancion: Optional[datetime.date] = None
    fecha_fin_sancion: Optional[datetime.date] = None
    notificado_padres: bool = False
    fecha_notificacion: Optional[datetime.date] = None
    medio_notificacion: Optional[str] = None
    notas_adicionales: Optional[str] = None


class SancionUpdate(BaseModel):
    estado: Optional[str] = None
    notificado_padres: Optional[bool] = None
    fecha_notificacion: Optional[datetime.date] = None
    medio_notificacion: Optional[str] = None
    notas_adicionales: Optional[str] = None


class PlanMejoraCreate(BaseModel):
    elaborado_por_id: uuid.UUID
    objetivo_general: str = Field(min_length=20)
    compromisos_alumno: list[dict[str, Any]] = Field(default_factory=list)
    compromisos_padre: list[dict[str, Any]] = Field(default_factory=list)
    compromisos_escuela: list[dict[str, Any]] = Field(default_factory=list)
    fecha_primer_seguimiento: Optional[datetime.date] = None
    ciclo_escolar_id: Optional[uuid.UUID] = None


class PlanMejoraUpdate(BaseModel):
    objetivo_general: Optional[str] = None
    compromisos_alumno: Optional[list[dict[str, Any]]] = None
    compromisos_padre: Optional[list[dict[str, Any]]] = None
    compromisos_escuela: Optional[list[dict[str, Any]]] = None
    firmado_alumno: Optional[bool] = None
    firmado_padre: Optional[bool] = None
    firmado_director: Optional[bool] = None
    fecha_firma_alumno: Optional[datetime.date] = None
    fecha_firma_padre: Optional[datetime.date] = None
    fecha_primer_seguimiento: Optional[datetime.date] = None
    estado: Optional[str] = None
    observaciones_cierre: Optional[str] = None


class SeguimientoCreate(BaseModel):
    registrado_por_id: uuid.UUID
    fecha_seguimiento: Optional[datetime.date] = None
    avance: str = "PARCIAL"
    descripcion: str = Field(min_length=20)
    compromisos_cumplidos: list[Any] = Field(default_factory=list)
    acciones_adicionales: Optional[str] = None
    nuevo_estado_plan: Optional[str] = None


# ── Endpoints existentes (SB-010, SB-011, SB-015) ────────────────────────────

@router.get("", response_model=list[ConductaOut])
async def listar_reportes(
    estudiante_id: uuid.UUID | None = None,
    grupo_id: uuid.UUID | None = None,
    tipo_falta: str | None = Query(None, description="LEVE | GRAVE | MUY_GRAVE"),
    requiere_seguimiento: bool | None = None,
    pagina: int = Query(1, ge=1),
    por_pagina: int = Query(20, ge=1, le=100),
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    q = select(ReporteConducta).where(ReporteConducta.is_active == True)
    if estudiante_id:
        q = q.where(ReporteConducta.estudiante_id == estudiante_id)
    if grupo_id:
        q = q.where(ReporteConducta.grupo_id == grupo_id)
    if tipo_falta:
        q = q.where(ReporteConducta.tipo_falta == tipo_falta.upper())
    if requiere_seguimiento is not None:
        q = q.where(ReporteConducta.requiere_seguimiento == requiere_seguimiento)
    q = q.order_by(ReporteConducta.fecha_creacion.desc())
    q = q.offset((pagina - 1) * por_pagina).limit(por_pagina)
    return (await db.execute(q)).scalars().all()


@router.get("/alumno/{estudiante_id}/historial")
async def historial_alumno(
    estudiante_id: uuid.UUID,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    """Historial completo: reportes + sanciones + planes por alumno."""
    rows = await db.execute(text("""
        SELECT
            rc.id, rc.fecha_reporte, rc.tipo_falta, rc.descripcion,
            rc.medida_aplicada, rc.requiere_seguimiento,
            -- sanción vinculada
            sd.id                AS sancion_id,
            sd.tipo_sancion,
            sd.estado            AS estado_sancion,
            sd.fecha_sancion,
            sd.notificado_padres,
            -- plan vinculado
            pm.id                AS plan_id,
            pm.estado            AS estado_plan,
            pm.fecha_elaboracion,
            pm.objetivo_general
        FROM ades_reportes_conducta rc
        LEFT JOIN ades_sanciones_disciplinarias sd ON sd.reporte_conducta_id = rc.id AND sd.is_active = TRUE
        LEFT JOIN ades_planes_mejora pm             ON pm.reporte_conducta_id = rc.id AND pm.is_active = TRUE
        WHERE rc.estudiante_id = :est_id::uuid AND rc.is_active = TRUE
        ORDER BY rc.fecha_reporte DESC
    """), {"est_id": str(estudiante_id)})
    return [dict(r) for r in rows.mappings().all()]


@router.get("/{reporte_id}", response_model=ConductaOut)
async def obtener_reporte(
    reporte_id: uuid.UUID,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    r = await db.get(ReporteConducta, reporte_id)
    if not r or not r.is_active:
        raise HTTPException(404, "Reporte no encontrado")
    return r


@router.get("/{reporte_id}/detalle-completo")
async def detalle_completo(
    reporte_id: uuid.UUID,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    """Reporte + sanción + plan de mejora + todos los seguimientos."""
    rep_row = await db.execute(text("""
        SELECT rc.*,
               p.nombre || ' ' || p.apellido_paterno AS nombre_alumno,
               est.matricula
          FROM ades_reportes_conducta rc
          JOIN ades_estudiantes est ON est.id = rc.estudiante_id
          JOIN ades_personas    p   ON p.id   = est.persona_id
         WHERE rc.id = :id::uuid AND rc.is_active = TRUE
    """), {"id": str(reporte_id)})
    reporte = rep_row.mappings().first()
    if not reporte:
        raise HTTPException(404, "Reporte no encontrado")

    sancion_row = await db.execute(text("""
        SELECT sd.*, u.nombre_usuario AS autorizado_por_nombre
          FROM ades_sanciones_disciplinarias sd
          JOIN ades_usuarios u ON u.id = sd.autorizado_por_id
         WHERE sd.reporte_conducta_id = :id::uuid AND sd.is_active = TRUE
         LIMIT 1
    """), {"id": str(reporte_id)})
    sancion = sancion_row.mappings().first()

    plan_row = await db.execute(text("""
        SELECT pm.*, u.nombre_usuario AS elaborado_por_nombre
          FROM ades_planes_mejora pm
          JOIN ades_usuarios u ON u.id = pm.elaborado_por_id
         WHERE pm.reporte_conducta_id = :id::uuid AND pm.is_active = TRUE
         LIMIT 1
    """), {"id": str(reporte_id)})
    plan = plan_row.mappings().first()

    seguimientos = []
    if plan:
        seg_rows = await db.execute(text("""
            SELECT sp.*, u.nombre_usuario AS registrado_por_nombre
              FROM ades_seguimiento_plan sp
              JOIN ades_usuarios u ON u.id = sp.registrado_por_id
             WHERE sp.plan_mejora_id = :plan_id::uuid AND sp.is_active = TRUE
             ORDER BY sp.fecha_seguimiento DESC
        """), {"plan_id": str(plan["id"])})
        seguimientos = [dict(r) for r in seg_rows.mappings().all()]

    return {
        "reporte":      dict(reporte),
        "sancion":      dict(sancion) if sancion else None,
        "plan_mejora":  dict(plan)    if plan    else None,
        "seguimientos": seguimientos,
    }


@router.post("", response_model=ConductaOut, status_code=status.HTTP_201_CREATED)
async def crear_reporte(
    data: ConductaCreate,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    r = ReporteConducta(**data.model_dump())
    db.add(r)
    await db.commit()
    await db.refresh(r)
    return r


@router.patch("/{reporte_id}", response_model=ConductaOut)
async def actualizar_reporte(
    reporte_id: uuid.UUID,
    data: ConductaUpdate,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    r = await db.get(ReporteConducta, reporte_id)
    if not r or not r.is_active:
        raise HTTPException(404, "Reporte no encontrado")
    for field, value in data.model_dump(exclude_none=True).items():
        setattr(r, field, value)
    await db.commit()
    await db.refresh(r)
    return r


# ── SB-012: Sanción Formal ────────────────────────────────────────────────────

@router.post("/{reporte_id}/sancion", status_code=201)
async def aplicar_sancion(
    reporte_id: uuid.UUID,
    body: SancionCreate,
    db: AsyncSession = Depends(get_db),
    ades_user: AdesUser = Depends(get_ades_user),
):
    """Aplica sanción formal a un reporte. Solo DIRECTOR/ADMIN."""
    if ades_user.nivel_acceso > _NIVEL_DIRECTOR:
        raise HTTPException(403, "Solo DIRECTOR/ADMIN puede aplicar sanciones formales")

    # Verificar que el reporte existe
    rep = await db.execute(
        text("SELECT id, estudiante_id FROM ades_reportes_conducta WHERE id = :id::uuid AND is_active = TRUE"),
        {"id": str(reporte_id)},
    )
    reporte = rep.mappings().first()
    if not reporte:
        raise HTTPException(404, "Reporte no encontrado")

    # Verificar que no tenga ya una sanción activa
    exist = await db.execute(
        text("SELECT id FROM ades_sanciones_disciplinarias WHERE reporte_conducta_id = :id::uuid AND is_active = TRUE"),
        {"id": str(reporte_id)},
    )
    if exist.fetchone():
        raise HTTPException(409, "Este reporte ya tiene una sanción aplicada")

    row = await db.execute(text("""
        INSERT INTO ades_sanciones_disciplinarias
            (reporte_conducta_id, estudiante_id, tipo_sancion, justificacion,
             autorizado_por_id, fecha_sancion, fecha_fin_sancion,
             notificado_padres, fecha_notificacion, medio_notificacion, notas_adicionales)
        VALUES
            (:reporte_id::uuid, :est_id::uuid, :tipo, :justificacion,
             :autorizado::uuid, :fecha, :fecha_fin,
             :notif_padres, :fecha_notif, :medio, :notas)
        RETURNING id, tipo_sancion, estado, fecha_sancion
    """), {
        "reporte_id":   str(reporte_id),
        "est_id":       str(reporte["estudiante_id"]),
        "tipo":         body.tipo_sancion,
        "justificacion": body.justificacion,
        "autorizado":   str(body.autorizado_por_id),
        "fecha":        body.fecha_sancion or datetime.date.today(),
        "fecha_fin":    body.fecha_fin_sancion,
        "notif_padres": body.notificado_padres,
        "fecha_notif":  body.fecha_notificacion,
        "medio":        body.medio_notificacion,
        "notas":        body.notas_adicionales,
    })
    result = dict(row.mappings().first())

    # Marcar reporte como con seguimiento requerido
    await db.execute(
        text("UPDATE ades_reportes_conducta SET requiere_seguimiento = TRUE WHERE id = :id::uuid"),
        {"id": str(reporte_id)},
    )
    await db.commit()
    return result


@router.patch("/{reporte_id}/sancion/{sancion_id}")
async def actualizar_sancion(
    reporte_id: uuid.UUID,
    sancion_id: uuid.UUID,
    body: SancionUpdate,
    db: AsyncSession = Depends(get_db),
    ades_user: AdesUser = Depends(get_ades_user),
):
    if ades_user.nivel_acceso > _NIVEL_COORD:
        raise HTTPException(403, "Sin permiso para actualizar sanciones")

    sets, params = [], {"id": str(sancion_id), "reporte_id": str(reporte_id)}
    if body.estado is not None:
        sets.append("estado = :estado"); params["estado"] = body.estado
    if body.notificado_padres is not None:
        sets.append("notificado_padres = :notif"); params["notif"] = body.notificado_padres
    if body.fecha_notificacion is not None:
        sets.append("fecha_notificacion = :f_notif"); params["f_notif"] = body.fecha_notificacion
    if body.medio_notificacion is not None:
        sets.append("medio_notificacion = :medio"); params["medio"] = body.medio_notificacion
    if body.notas_adicionales is not None:
        sets.append("notas_adicionales = :notas"); params["notas"] = body.notas_adicionales
    if not sets:
        raise HTTPException(422, "Ningún campo para actualizar")

    sets += ["fecha_modificacion = now()", "row_version = row_version + 1"]
    result = await db.execute(
        text(f"UPDATE ades_sanciones_disciplinarias SET {', '.join(sets)} "
             "WHERE id = :id::uuid AND reporte_conducta_id = :reporte_id::uuid AND is_active = TRUE "
             "RETURNING id, estado"),
        params,
    )
    if not result.fetchone():
        raise HTTPException(404, "Sanción no encontrada")
    await db.commit()
    return {"ok": True}


# ── SB-013: Plan de Mejora ────────────────────────────────────────────────────

@router.post("/{reporte_id}/plan-mejora", status_code=201)
async def crear_plan_mejora(
    reporte_id: uuid.UUID,
    body: PlanMejoraCreate,
    db: AsyncSession = Depends(get_db),
    ades_user: AdesUser = Depends(get_ades_user),
):
    """Crea un plan de mejora conductual. COORDINADOR o superior."""
    if ades_user.nivel_acceso > _NIVEL_COORD:
        raise HTTPException(403, "Solo COORDINADOR/DIRECTOR/ADMIN puede crear planes de mejora")

    rep = await db.execute(
        text("SELECT id, estudiante_id FROM ades_reportes_conducta WHERE id = :id::uuid AND is_active = TRUE"),
        {"id": str(reporte_id)},
    )
    reporte = rep.mappings().first()
    if not reporte:
        raise HTTPException(404, "Reporte no encontrado")

    # UNIQUE(reporte_conducta_id) — solo un plan por reporte
    exist = await db.execute(
        text("SELECT id FROM ades_planes_mejora WHERE reporte_conducta_id = :id::uuid AND is_active = TRUE"),
        {"id": str(reporte_id)},
    )
    if exist.fetchone():
        raise HTTPException(409, "Este reporte ya tiene un plan de mejora activo")

    row = await db.execute(text("""
        INSERT INTO ades_planes_mejora
            (reporte_conducta_id, estudiante_id, ciclo_escolar_id,
             elaborado_por_id, objetivo_general,
             compromisos_alumno, compromisos_padre, compromisos_escuela,
             fecha_primer_seguimiento)
        VALUES
            (:reporte_id::uuid, :est_id::uuid, :ciclo_id::uuid,
             :elab::uuid, :objetivo,
             :comp_alumno::jsonb, :comp_padre::jsonb, :comp_escuela::jsonb,
             :f_seg)
        RETURNING id, estado, fecha_elaboracion
    """), {
        "reporte_id":   str(reporte_id),
        "est_id":       str(reporte["estudiante_id"]),
        "ciclo_id":     str(body.ciclo_escolar_id) if body.ciclo_escolar_id else None,
        "elab":         str(body.elaborado_por_id),
        "objetivo":     body.objetivo_general,
        "comp_alumno":  __import__("json").dumps(body.compromisos_alumno),
        "comp_padre":   __import__("json").dumps(body.compromisos_padre),
        "comp_escuela": __import__("json").dumps(body.compromisos_escuela),
        "f_seg":        body.fecha_primer_seguimiento,
    })
    await db.commit()
    return dict(row.mappings().first())


@router.patch("/{reporte_id}/plan-mejora/{plan_id}")
async def actualizar_plan_mejora(
    reporte_id: uuid.UUID,
    plan_id: uuid.UUID,
    body: PlanMejoraUpdate,
    db: AsyncSession = Depends(get_db),
    ades_user: AdesUser = Depends(get_ades_user),
):
    if ades_user.nivel_acceso > _NIVEL_COORD:
        raise HTTPException(403, "Sin permiso para actualizar planes de mejora")

    import json
    sets, params = [], {"id": str(plan_id), "reporte_id": str(reporte_id)}

    for campo, col in [
        ("objetivo_general", "objetivo_general"),
        ("firmado_alumno", "firmado_alumno"),
        ("firmado_padre", "firmado_padre"),
        ("firmado_director", "firmado_director"),
        ("fecha_firma_alumno", "fecha_firma_alumno"),
        ("fecha_firma_padre", "fecha_firma_padre"),
        ("fecha_primer_seguimiento", "fecha_primer_seguimiento"),
        ("estado", "estado"),
        ("observaciones_cierre", "observaciones_cierre"),
    ]:
        val = getattr(body, campo)
        if val is not None:
            sets.append(f"{col} = :{campo}"); params[campo] = val

    for campo_json, col in [
        ("compromisos_alumno", "compromisos_alumno"),
        ("compromisos_padre",  "compromisos_padre"),
        ("compromisos_escuela", "compromisos_escuela"),
    ]:
        val = getattr(body, campo_json)
        if val is not None:
            sets.append(f"{col} = :{campo_json}::jsonb"); params[campo_json] = json.dumps(val)

    if not sets:
        raise HTTPException(422, "Ningún campo para actualizar")

    sets += ["fecha_modificacion = now()", "row_version = row_version + 1"]
    result = await db.execute(
        text(f"UPDATE ades_planes_mejora SET {', '.join(sets)} "
             "WHERE id = :id::uuid AND reporte_conducta_id = :reporte_id::uuid AND is_active = TRUE "
             "RETURNING id, estado"),
        params,
    )
    if not result.fetchone():
        raise HTTPException(404, "Plan no encontrado")
    await db.commit()
    return {"ok": True}


# ── SB-014: Seguimiento del Plan ─────────────────────────────────────────────

@router.post("/{reporte_id}/plan-mejora/{plan_id}/seguimiento", status_code=201)
async def agregar_seguimiento(
    reporte_id: uuid.UUID,
    plan_id: uuid.UUID,
    body: SeguimientoCreate,
    db: AsyncSession = Depends(get_db),
    ades_user: AdesUser = Depends(get_ades_user),
):
    """Agrega una entrada de seguimiento al plan. El trigger actualiza el estado del plan."""
    if ades_user.nivel_acceso > _NIVEL_COORD:
        raise HTTPException(403, "Sin permiso para registrar seguimientos")

    # Verificar que el plan pertenece al reporte
    plan_check = await db.execute(
        text("SELECT id, estudiante_id FROM ades_planes_mejora WHERE id = :pid::uuid AND reporte_conducta_id = :rid::uuid AND is_active = TRUE"),
        {"pid": str(plan_id), "rid": str(reporte_id)},
    )
    plan = plan_check.mappings().first()
    if not plan:
        raise HTTPException(404, "Plan no encontrado")

    import json
    row = await db.execute(text("""
        INSERT INTO ades_seguimiento_plan
            (plan_mejora_id, estudiante_id, registrado_por_id,
             fecha_seguimiento, avance, descripcion,
             compromisos_cumplidos, acciones_adicionales, nuevo_estado_plan)
        VALUES
            (:plan_id::uuid, :est_id::uuid, :reg_por::uuid,
             :fecha, :avance, :descripcion,
             :comp_cumpl::jsonb, :acciones, :nuevo_estado)
        RETURNING id, avance, fecha_seguimiento
    """), {
        "plan_id":      str(plan_id),
        "est_id":       str(plan["estudiante_id"]),
        "reg_por":      str(body.registrado_por_id),
        "fecha":        body.fecha_seguimiento or datetime.date.today(),
        "avance":       body.avance,
        "descripcion":  body.descripcion,
        "comp_cumpl":   json.dumps(body.compromisos_cumplidos),
        "acciones":     body.acciones_adicionales,
        "nuevo_estado": body.nuevo_estado_plan,
    })
    await db.commit()
    return dict(row.mappings().first())
