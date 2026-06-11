"""
/rubricas — Rúbricas de evaluación con criterios y niveles de logro.

  GET    /rubricas                         — lista por materia/nivel
  POST   /rubricas                         — crear rúbrica
  GET    /rubricas/{id}                    — detalle con criterios y niveles de logro
  DELETE /rubricas/{id}                    — baja lógica
  POST   /rubricas/{id}/criterios          — agregar criterio
  PUT    /rubricas/{id}/criterios/{cid}    — actualizar criterio / niveles de logro
  DELETE /rubricas/{id}/criterios/{cid}    — quitar criterio
"""
from __future__ import annotations

from typing import Optional, Any
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.core.security import get_current_user

router = APIRouter(prefix="/rubricas", tags=["rubricas"])


# ── schemas ───────────────────────────────────────────────────────────────────

class RubricaCreate(BaseModel):
    nombre_rubrica: str
    descripcion: Optional[str] = None
    materia_id: Optional[UUID] = None
    nivel_educativo_id: Optional[UUID] = None


class CriterioCreate(BaseModel):
    nombre_criterio: str
    descripcion: Optional[str] = None
    ponderacion: float = 0.0
    orden: int = 1
    niveles_logro: Optional[list[dict[str, Any]]] = None
    # Formato esperado:
    # [{"nivel": 1, "etiqueta": "Inicial", "descripcion": "..."},
    #  {"nivel": 2, "etiqueta": "En desarrollo", "descripcion": "..."},
    #  {"nivel": 3, "etiqueta": "Logrado", "descripcion": "..."},
    #  {"nivel": 4, "etiqueta": "Destacado", "descripcion": "..."}]


# ── endpoints ─────────────────────────────────────────────────────────────────

@router.get("")
async def listar_rubricas(
    materia_id: Optional[UUID] = None,
    nivel_educativo_id: Optional[UUID] = None,
    limit: int = 50,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    filters = ["r.is_active = TRUE"]
    params: dict = {"limit": limit}

    if materia_id:
        filters.append("r.materia_id = :materia_id::uuid")
        params["materia_id"] = str(materia_id)
    if nivel_educativo_id:
        filters.append("r.nivel_educativo_id = :nivel_id::uuid")
        params["nivel_id"] = str(nivel_educativo_id)

    where = " AND ".join(filters)
    rows = await db.execute(text(f"""
        SELECT
            r.id, r.nombre_rubrica, r.descripcion,
            r.materia_id, m.nombre_materia,
            r.nivel_educativo_id, ne.nombre_nivel,
            COUNT(rc.id) AS total_criterios,
            ROUND(SUM(rc.ponderacion), 1) AS ponderacion_total
        FROM ades_rubricas r
        LEFT JOIN ades_materias         m  ON m.id  = r.materia_id
        LEFT JOIN ades_niveles_educativos ne ON ne.id = r.nivel_educativo_id
        LEFT JOIN ades_rubrica_criterios rc ON rc.rubrica_id = r.id AND rc.is_active = TRUE
        WHERE {where}
        GROUP BY r.id, m.nombre_materia, ne.nombre_nivel
        ORDER BY r.fecha_creacion DESC
        LIMIT :limit
    """), params)
    return rows.mappings().all()


@router.post("", status_code=201)
async def crear_rubrica(
    body: RubricaCreate,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    row = await db.execute(text("""
        INSERT INTO ades_rubricas (nombre_rubrica, descripcion, materia_id, nivel_educativo_id)
        VALUES (:nombre, :desc, :materia_id, :nivel_id)
        RETURNING id, nombre_rubrica
    """), {
        "nombre":    body.nombre_rubrica,
        "desc":      body.descripcion,
        "materia_id": str(body.materia_id) if body.materia_id else None,
        "nivel_id":   str(body.nivel_educativo_id) if body.nivel_educativo_id else None,
    })
    await db.commit()
    return row.mappings().first()


@router.get("/{rubrica_id}")
async def detalle_rubrica(
    rubrica_id: UUID,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    r_row = await db.execute(text("""
        SELECT r.*, m.nombre_materia, ne.nombre_nivel
        FROM ades_rubricas r
        LEFT JOIN ades_materias m ON m.id = r.materia_id
        LEFT JOIN ades_niveles_educativos ne ON ne.id = r.nivel_educativo_id
        WHERE r.id = :id::uuid AND r.is_active = TRUE
    """), {"id": str(rubrica_id)})
    rubrica = r_row.mappings().first()
    if not rubrica:
        raise HTTPException(status_code=404, detail="Rúbrica no encontrada")

    c_rows = await db.execute(text("""
        SELECT id, nombre_criterio, descripcion, ponderacion, orden, niveles_logro
        FROM ades_rubrica_criterios
        WHERE rubrica_id = :id::uuid AND is_active = TRUE
        ORDER BY orden
    """), {"id": str(rubrica_id)})

    return {**dict(rubrica), "criterios": c_rows.mappings().all()}


@router.post("/{rubrica_id}/criterios", status_code=201)
async def agregar_criterio(
    rubrica_id: UUID,
    body: CriterioCreate,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    import json
    row = await db.execute(text("""
        INSERT INTO ades_rubrica_criterios
            (rubrica_id, nombre_criterio, descripcion, ponderacion, orden, niveles_logro)
        VALUES
            (:rubrica_id::uuid, :nombre, :desc, :ponderacion, :orden,
             :niveles_logro::jsonb)
        RETURNING id, nombre_criterio, ponderacion, orden
    """), {
        "rubrica_id":   str(rubrica_id),
        "nombre":       body.nombre_criterio,
        "desc":         body.descripcion,
        "ponderacion":  body.ponderacion,
        "orden":        body.orden,
        "niveles_logro": json.dumps(body.niveles_logro) if body.niveles_logro else None,
    })
    await db.commit()
    return row.mappings().first()


@router.put("/{rubrica_id}/criterios/{criterio_id}")
async def actualizar_criterio(
    rubrica_id: UUID,
    criterio_id: UUID,
    body: CriterioCreate,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    import json
    await db.execute(text("""
        UPDATE ades_rubrica_criterios
        SET nombre_criterio = :nombre,
            descripcion     = :desc,
            ponderacion     = :ponderacion,
            orden           = :orden,
            niveles_logro   = :niveles_logro::jsonb
        WHERE id = :criterio_id::uuid AND rubrica_id = :rubrica_id::uuid
    """), {
        "criterio_id": str(criterio_id),
        "rubrica_id":  str(rubrica_id),
        "nombre":      body.nombre_criterio,
        "desc":        body.descripcion,
        "ponderacion": body.ponderacion,
        "orden":       body.orden,
        "niveles_logro": json.dumps(body.niveles_logro) if body.niveles_logro else None,
    })
    await db.commit()
    return {"ok": True}


@router.delete("/{rubrica_id}/criterios/{criterio_id}", status_code=204)
async def eliminar_criterio(
    rubrica_id: UUID,
    criterio_id: UUID,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    await db.execute(text("""
        UPDATE ades_rubrica_criterios SET is_active = FALSE
        WHERE id = :criterio_id::uuid AND rubrica_id = :rubrica_id::uuid
    """), {"criterio_id": str(criterio_id), "rubrica_id": str(rubrica_id)})
    await db.commit()


@router.delete("/{rubrica_id}", status_code=204)
async def eliminar_rubrica(
    rubrica_id: UUID,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    await db.execute(text("""
        UPDATE ades_rubricas SET is_active = FALSE WHERE id = :id::uuid
    """), {"id": str(rubrica_id)})
    await db.commit()
