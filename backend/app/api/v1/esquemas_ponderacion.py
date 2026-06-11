from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import text
from app.core.database import get_db
from app.core.security import get_current_user
from typing import Optional
from pydantic import BaseModel, field_validator
from decimal import Decimal

router = APIRouter(prefix="/esquemas-ponderacion", tags=["Gradebook – Ponderaciones"])


class ItemIn(BaseModel):
    tipo_item: str
    nombre_personalizado: Optional[str] = None
    peso_porcentaje: Decimal
    orden_display: int = 1


class EsquemaIn(BaseModel):
    nombre: str
    nivel_educativo_id: str
    materia_id: Optional[str] = None
    vigente_desde: str
    vigente_hasta: Optional[str] = None
    items: list[ItemIn]

    @field_validator("items")
    @classmethod
    def validar_suma_100(cls, items):
        total = sum(float(i.peso_porcentaje) for i in items)
        if abs(total - 100.0) > 0.01:
            raise ValueError(f"Los pesos deben sumar 100% (suma actual: {total}%)")
        return items


# ── GET /esquemas-ponderacion ─────────────────────────────────
@router.get("")
async def listar_esquemas(
    nivel_educativo_id: Optional[str] = None,
    materia_id: Optional[str] = None,
    db: AsyncSession = Depends(get_db),
    current_user=Depends(get_current_user),
):
    filters = "WHERE ep.is_active = TRUE"
    params: dict = {}
    if nivel_educativo_id:
        filters += " AND ep.nivel_educativo_id = :nid"
        params["nid"] = nivel_educativo_id
    if materia_id:
        filters += " AND (ep.materia_id = :mid OR ep.materia_id IS NULL)"
        params["mid"] = materia_id

    rows = await db.execute(
        text(f"""
            SELECT ep.id, ep.nombre, ep.vigente_desde, ep.vigente_hasta,
                   ep.activo, ep.materia_id,
                   ne.nombre_nivel,
                   m.nombre_materia,
                   (SELECT json_agg(json_build_object(
                               'id', ip.id,
                               'tipo_item', ip.tipo_item,
                               'nombre_personalizado', ip.nombre_personalizado,
                               'peso_porcentaje', ip.peso_porcentaje,
                               'orden_display', ip.orden_display)
                           ORDER BY ip.orden_display)
                    FROM ades_items_ponderacion ip
                   WHERE ip.esquema_id = ep.id AND ip.is_active = TRUE
                   ) AS items
              FROM ades_esquemas_ponderacion ep
              JOIN ades_niveles_educativos ne ON ne.id = ep.nivel_educativo_id
              LEFT JOIN ades_materias m ON m.id = ep.materia_id
              {filters}
             ORDER BY ne.nombre_nivel, ep.vigente_desde DESC
        """),
        params,
    )
    return [dict(r._mapping) for r in rows.fetchall()]


# ── GET /esquemas-ponderacion/efectivo/{materia_id} ───────────
@router.get("/efectivo/{materia_id}")
async def esquema_efectivo(
    materia_id: str,
    db: AsyncSession = Depends(get_db),
    current_user=Depends(get_current_user),
):
    """Devuelve el esquema vigente para una materia (específico > nivel)."""
    row = await db.execute(
        text("""
            SELECT ep.id, ep.nombre, ep.vigente_desde, ep.vigente_hasta,
                   ep.materia_id, ne.nombre_nivel, ne.escala_maxima,
                   ne.minimo_aprobatorio,
                   (SELECT json_agg(json_build_object(
                               'id', ip.id,
                               'tipo_item', ip.tipo_item,
                               'nombre_personalizado', ip.nombre_personalizado,
                               'peso_porcentaje', ip.peso_porcentaje,
                               'orden_display', ip.orden_display)
                           ORDER BY ip.orden_display)
                    FROM ades_items_ponderacion ip
                   WHERE ip.esquema_id = ep.id AND ip.is_active = TRUE
                   ) AS items
              FROM ades_esquemas_ponderacion ep
              JOIN ades_niveles_educativos ne ON ne.id = ep.nivel_educativo_id
              JOIN ades_materias m ON m.nivel_educativo_id = ne.id
             WHERE m.id = :mid
               AND ep.activo = TRUE
               AND (ep.vigente_hasta IS NULL OR ep.vigente_hasta >= CURRENT_DATE)
               AND ep.vigente_desde <= CURRENT_DATE
             ORDER BY (ep.materia_id = :mid) DESC NULLS LAST,
                      ep.vigente_desde DESC
             LIMIT 1
        """),
        {"mid": materia_id},
    )
    r = row.fetchone()
    if not r:
        raise HTTPException(404, "No hay esquema de ponderación para esta materia")
    return dict(r._mapping)


# ── POST /esquemas-ponderacion ────────────────────────────────
@router.post("", status_code=201)
async def crear_esquema(
    body: EsquemaIn,
    db: AsyncSession = Depends(get_db),
    current_user=Depends(get_current_user),
):
    sub = current_user.get("sub")
    user_row = await db.execute(
        text("SELECT id FROM ades_usuarios WHERE oidc_sub = :s"), {"s": sub}
    )
    user_id = user_row.scalar()

    row = await db.execute(
        text("""
            INSERT INTO ades_esquemas_ponderacion
                   (nombre, nivel_educativo_id, materia_id,
                    vigente_desde, vigente_hasta, creado_por, activo)
            VALUES (:nombre, :nid, :mid,
                    :vd, :vh, :uid, TRUE)
            RETURNING id
        """),
        {
            "nombre": body.nombre,
            "nid": body.nivel_educativo_id,
            "mid": body.materia_id,
            "vd": body.vigente_desde,
            "vh": body.vigente_hasta,
            "uid": user_id,
        },
    )
    esquema_id = row.scalar()

    for item in body.items:
        await db.execute(
            text("""
                INSERT INTO ades_items_ponderacion
                       (esquema_id, tipo_item, nombre_personalizado,
                        peso_porcentaje, orden_display)
                VALUES (:eid, :tipo, :nombre, :peso, :orden)
            """),
            {
                "eid": esquema_id,
                "tipo": item.tipo_item,
                "nombre": item.nombre_personalizado,
                "peso": float(item.peso_porcentaje),
                "orden": item.orden_display,
            },
        )

    await db.commit()
    return {"id": str(esquema_id), "message": "Esquema creado"}


# ── PUT /esquemas-ponderacion/{id} ────────────────────────────
@router.put("/{esquema_id}")
async def actualizar_esquema(
    esquema_id: str,
    body: EsquemaIn,
    db: AsyncSession = Depends(get_db),
    current_user=Depends(get_current_user),
):
    # Verificar que existe
    exists = await db.execute(
        text("SELECT id FROM ades_esquemas_ponderacion WHERE id = :id AND is_active = TRUE"),
        {"id": esquema_id},
    )
    if not exists.scalar():
        raise HTTPException(404, "Esquema no encontrado")

    await db.execute(
        text("""
            UPDATE ades_esquemas_ponderacion
               SET nombre = :nombre, nivel_educativo_id = :nid, materia_id = :mid,
                   vigente_desde = :vd, vigente_hasta = :vh,
                   fecha_modificacion = now(), row_version = row_version + 1
             WHERE id = :id
        """),
        {
            "nombre": body.nombre,
            "nid": body.nivel_educativo_id,
            "mid": body.materia_id,
            "vd": body.vigente_desde,
            "vh": body.vigente_hasta,
            "id": esquema_id,
        },
    )

    # Reemplazar ítems (soft delete + re-insert)
    await db.execute(
        text("UPDATE ades_items_ponderacion SET is_active = FALSE WHERE esquema_id = :eid"),
        {"eid": esquema_id},
    )
    for item in body.items:
        await db.execute(
            text("""
                INSERT INTO ades_items_ponderacion
                       (esquema_id, tipo_item, nombre_personalizado,
                        peso_porcentaje, orden_display)
                VALUES (:eid, :tipo, :nombre, :peso, :orden)
            """),
            {
                "eid": esquema_id,
                "tipo": item.tipo_item,
                "nombre": item.nombre_personalizado,
                "peso": float(item.peso_porcentaje),
                "orden": item.orden_display,
            },
        )

    await db.commit()
    return {"message": "Esquema actualizado"}


# ── DELETE /esquemas-ponderacion/{id} (soft) ─────────────────
@router.delete("/{esquema_id}")
async def desactivar_esquema(
    esquema_id: str,
    db: AsyncSession = Depends(get_db),
    current_user=Depends(get_current_user),
):
    await db.execute(
        text("""
            UPDATE ades_esquemas_ponderacion
               SET activo = FALSE, is_active = FALSE, fecha_modificacion = now()
             WHERE id = :id
        """),
        {"id": esquema_id},
    )
    await db.commit()
    return {"message": "Esquema desactivado"}
