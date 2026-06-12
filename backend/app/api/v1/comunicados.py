"""
/comunicados — Comunicados y circulares institucionales.

  GET    /comunicados                   — lista paginada (filtros plantel/nivel/tipo)
  POST   /comunicados                   — crear comunicado (roles admin/director)
  GET    /comunicados/{id}              — detalle + total acuses
  PUT    /comunicados/{id}/acusar       — registrar acuse de recibo del usuario actual
  DELETE /comunicados/{id}              — baja lógica (admin)
"""
from __future__ import annotations
import asyncio

import datetime
from typing import Optional
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.core.security import get_current_user

router = APIRouter(prefix="/comunicados", tags=["comunicados"])


# ── helpers ───────────────────────────────────────────────────────────────────

async def _resolve_usuario_id(db: AsyncSession, jwt_sub: str) -> str | None:
    row = await db.execute(
        text("SELECT id FROM ades_usuarios WHERE oidc_sub = :sub AND is_active = TRUE"),
        {"sub": jwt_sub},
    )
    r = row.fetchone()
    return str(r[0]) if r else None


# ── schemas ───────────────────────────────────────────────────────────────────

_PERIODICIDADES = ["DIARIA", "SEMANAL", "QUINCENAL", "MENSUAL", "TRIMESTRAL"]


class ComunicadoCreate(BaseModel):
    titulo: str
    contenido: str
    tipo_comunicado: str = "GENERAL"
    plantel_id: Optional[UUID] = None
    nivel_educativo_id: Optional[UUID] = None
    grupo_id: Optional[UUID] = None
    requiere_acuse: bool = False
    fecha_vencimiento: Optional[datetime.datetime] = None
    es_recurrente: bool = False
    periodicidad: Optional[str] = None   # CO-007: SEMANAL MENSUAL etc.


# ── endpoints ─────────────────────────────────────────────────────────────────

@router.get("")
async def listar_comunicados(
    plantel_id: Optional[UUID] = None,
    nivel_educativo_id: Optional[UUID] = None,
    tipo: Optional[str] = None,
    solo_vigentes: bool = True,
    limit: int = 50,
    db: AsyncSession = Depends(get_db),
    current_user: dict = Depends(get_current_user),
):
    jwt_sub = current_user.get("sub", "")
    uid = await _resolve_usuario_id(db, jwt_sub) or "00000000-0000-0000-0000-000000000000"

    filters = ["c.is_active = TRUE"]
    params: dict = {"uid": uid}

    if plantel_id:
        filters.append("(c.plantel_id IS NULL OR c.plantel_id = :plantel_id::uuid)")
        params["plantel_id"] = str(plantel_id)
    if nivel_educativo_id:
        filters.append("(c.nivel_educativo_id IS NULL OR c.nivel_educativo_id = :nivel_id::uuid)")
        params["nivel_id"] = str(nivel_educativo_id)
    if tipo:
        filters.append("c.tipo_comunicado = :tipo")
        params["tipo"] = tipo
    if solo_vigentes:
        filters.append("(c.fecha_vencimiento IS NULL OR c.fecha_vencimiento > NOW())")

    where = " AND ".join(filters)
    params["limit"] = limit

    rows = await db.execute(text(f"""
        SELECT
            c.id, c.titulo, c.contenido, c.tipo_comunicado,
            c.plantel_id, c.nivel_educativo_id, c.grupo_id,
            c.requiere_acuse, c.fecha_publicacion, c.fecha_vencimiento,
            COUNT(a.id) FILTER (WHERE a.id IS NOT NULL)       AS total_acuses,
            BOOL_OR(a.usuario_id = :uid::uuid)                AS acusado_por_mi,
            u.nombre_usuario                                  AS creado_por_nombre
        FROM ades_comunicados c
        LEFT JOIN ades_acuses_comunicado a ON a.comunicado_id = c.id
        LEFT JOIN ades_usuarios u          ON u.id = c.creado_por_id
        WHERE {where}
        GROUP BY c.id, u.nombre_usuario
        ORDER BY c.fecha_publicacion DESC
        LIMIT :limit
    """), params)

    return rows.mappings().all()


@router.post("", status_code=201)
async def crear_comunicado(
    body: ComunicadoCreate,
    db: AsyncSession = Depends(get_db),
    current_user: dict = Depends(get_current_user),
):
    jwt_sub = current_user.get("sub", "")
    uid = await _resolve_usuario_id(db, jwt_sub)

    row = await db.execute(text("""
        INSERT INTO ades_comunicados
            (titulo, contenido, tipo_comunicado, plantel_id, nivel_educativo_id, grupo_id,
             requiere_acuse, fecha_vencimiento, es_recurrente, periodicidad, creado_por_id)
        VALUES
            (:titulo, :contenido, :tipo, :plantel_id, :nivel_id, :grupo_id,
             :requiere_acuse, :fecha_vencimiento, :es_recurrente, :periodicidad, :creado_por)
        RETURNING id, titulo, tipo_comunicado, fecha_publicacion
    """), {
        "titulo": body.titulo,
        "contenido": body.contenido,
        "tipo": body.tipo_comunicado,
        "plantel_id": str(body.plantel_id) if body.plantel_id else None,
        "nivel_id": str(body.nivel_educativo_id) if body.nivel_educativo_id else None,
        "grupo_id": str(body.grupo_id) if body.grupo_id else None,
        "requiere_acuse": body.requiere_acuse,
        "fecha_vencimiento": body.fecha_vencimiento,
        "es_recurrente": body.es_recurrente,
        "periodicidad": body.periodicidad if body.es_recurrente else None,
        "creado_por": uid,
    })
    await db.commit()
    result = row.mappings().first()

    # FASE 20 — Push batch a destinatarios del comunicado
    if result:
        from app.services.notification_triggers import on_comunicado_publicado
        asyncio.create_task(
            on_comunicado_publicado(
                db,
                comunicado_id=result["id"],
                titulo=body.titulo,
                tipo=body.tipo_comunicado,
                plantel_id=body.plantel_id,
                nivel_id=body.nivel_educativo_id,
                grupo_id=body.grupo_id,
            )
        )

    return result


@router.get("/{comunicado_id}")
async def detalle_comunicado(
    comunicado_id: UUID,
    db: AsyncSession = Depends(get_db),
    current_user: dict = Depends(get_current_user),
):
    jwt_sub = current_user.get("sub", "")
    uid = await _resolve_usuario_id(db, jwt_sub) or "00000000-0000-0000-0000-000000000000"

    row = await db.execute(text("""
        SELECT c.*,
               COUNT(a.id)                        AS total_acuses,
               BOOL_OR(a.usuario_id = :uid::uuid) AS acusado_por_mi
        FROM ades_comunicados c
        LEFT JOIN ades_acuses_comunicado a ON a.comunicado_id = c.id
        WHERE c.id = :id::uuid AND c.is_active = TRUE
        GROUP BY c.id
    """), {"id": str(comunicado_id), "uid": uid})
    result = row.mappings().first()
    if not result:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Comunicado no encontrado")
    return result


@router.put("/{comunicado_id}/acusar")
async def acusar_recibo(
    comunicado_id: UUID,
    db: AsyncSession = Depends(get_db),
    current_user: dict = Depends(get_current_user),
):
    jwt_sub = current_user.get("sub", "")
    uid = await _resolve_usuario_id(db, jwt_sub)
    if not uid:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Usuario no encontrado en ADES")

    await db.execute(text("""
        INSERT INTO ades_acuses_comunicado (comunicado_id, usuario_id)
        VALUES (:comunicado_id::uuid, :usuario_id::uuid)
        ON CONFLICT (comunicado_id, usuario_id) DO NOTHING
    """), {"comunicado_id": str(comunicado_id), "usuario_id": uid})
    await db.commit()
    return {"ok": True, "comunicado_id": str(comunicado_id)}


@router.delete("/{comunicado_id}", status_code=204)
async def eliminar_comunicado(
    comunicado_id: UUID,
    db: AsyncSession = Depends(get_db),
    current_user: dict = Depends(get_current_user),
):
    await db.execute(text("""
        UPDATE ades_comunicados SET is_active = FALSE
        WHERE id = :id::uuid
    """), {"id": str(comunicado_id)})
    await db.commit()


# ── CO-007: Comunicados recurrentes ──────────────────────────────────────────
@router.get("/recurrentes/pendientes")
async def comunicados_recurrentes_pendientes(
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    """CO-007: Lista comunicados recurrentes con próximo envío vencido o próximo."""
    rows = await db.execute(text("""
        SELECT id, titulo, tipo_comunicado, periodicidad,
               proximo_envio, fecha_publicacion, plantel_id, nivel_educativo_id
        FROM ades_comunicados
        WHERE es_recurrente = TRUE AND is_active = TRUE
        ORDER BY proximo_envio ASC NULLS LAST
    """))
    return [dict(r) for r in rows.mappings().all()]


@router.post("/{comunicado_id}/programar-siguiente")
async def programar_siguiente_envio(
    comunicado_id: UUID,
    db: AsyncSession = Depends(get_db),
    current_user: dict = Depends(get_current_user),
):
    """CO-007: Calcula y actualiza proximo_envio según periodicidad."""
    row = await db.execute(
        text("SELECT periodicidad, proximo_envio FROM ades_comunicados WHERE id=:id::uuid AND is_active=TRUE"),
        {"id": str(comunicado_id)},
    )
    rec = row.mappings().first()
    if not rec:
        raise HTTPException(status_code=404, detail="Comunicado no encontrado")
    if not rec["periodicidad"]:
        raise HTTPException(status_code=400, detail="El comunicado no tiene periodicidad configurada")

    import datetime as dt
    delta_map = {
        "DIARIA":     dt.timedelta(days=1),
        "SEMANAL":    dt.timedelta(weeks=1),
        "QUINCENAL":  dt.timedelta(days=15),
        "MENSUAL":    dt.timedelta(days=30),
        "TRIMESTRAL": dt.timedelta(days=90),
    }
    delta = delta_map.get(rec["periodicidad"], dt.timedelta(days=30))
    base  = rec["proximo_envio"] or dt.datetime.now(dt.timezone.utc)
    siguiente = base + delta

    await db.execute(
        text("UPDATE ades_comunicados SET proximo_envio=:next WHERE id=:id::uuid"),
        {"next": siguiente, "id": str(comunicado_id)},
    )
    await db.commit()
    return {"proximo_envio": siguiente.isoformat(), "periodicidad": rec["periodicidad"]}


# ── CO-005: Reporte de lectura ──────────────────────────────────────────────
@router.get("/{comunicado_id}/reporte-lectura")
async def reporte_lectura(
    comunicado_id: UUID,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    com = (await db.execute(
        text("SELECT id, titulo, total_destinatarios FROM ades_comunicados WHERE id = :id::uuid AND is_active = TRUE"),
        {"id": str(comunicado_id)},
    )).mappings().first()
    if not com:
        raise HTTPException(404, "Comunicado no encontrado")

    acuses = (await db.execute(
        text("""
            SELECT COUNT(*) AS leidos,
                   COUNT(DISTINCT ac.usuario_id) AS usuarios_distintos
            FROM ades_acuses_comunicado ac
            WHERE ac.comunicado_id = :cid::uuid AND ac.is_active = TRUE
        """),
        {"cid": str(comunicado_id)},
    )).mappings().first()

    leidos = acuses["leidos"] or 0
    total  = com.get("total_destinatarios") or 0
    pct    = round(leidos / total * 100, 1) if total else 0.0

    detalle = (await db.execute(
        text("""
            SELECT u.nombre_usuario, ac.fecha_acuse, ac.ip_origen
            FROM ades_acuses_comunicado ac
            JOIN ades_usuarios u ON u.id = ac.usuario_id
            WHERE ac.comunicado_id = :cid::uuid AND ac.is_active = TRUE
            ORDER BY ac.fecha_acuse DESC
            LIMIT 200
        """),
        {"cid": str(comunicado_id)},
    )).mappings().all()

    return {
        "comunicado_id": str(comunicado_id),
        "titulo": com["titulo"],
        "total_destinatarios": total,
        "total_leidos": leidos,
        "pct_lectura": pct,
        "detalle": [dict(r) for r in detalle],
    }
