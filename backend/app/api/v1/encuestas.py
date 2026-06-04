"""
/encuestas — Encuestas y sondeos escolares.

  GET    /encuestas                          — lista (filtros tipo/activa/plantel)
  POST   /encuestas                          — crear encuesta
  GET    /encuestas/{id}                     — detalle + preguntas
  DELETE /encuestas/{id}                     — baja lógica
  PATCH  /encuestas/{id}/toggle-activa       — activar / desactivar
  POST   /encuestas/{id}/preguntas           — agregar pregunta
  PUT    /encuestas/{id}/preguntas/{pid}     — actualizar pregunta
  DELETE /encuestas/{id}/preguntas/{pid}     — baja lógica
  POST   /encuestas/{id}/responder           — guardar sesión de respuestas
  GET    /encuestas/{id}/resultados          — resultados estadísticos por pregunta
  GET    /encuestas/{id}/respuestas-raw      — texto libre y resumen exportable
"""
from __future__ import annotations

import json
import datetime
import uuid as _uuid
from typing import Any, Optional
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.core.security import get_current_user

router = APIRouter(prefix="/encuestas", tags=["encuestas"])


# ── schemas ───────────────────────────────────────────────────────────────────

class EncuestaCreate(BaseModel):
    titulo: str
    descripcion: Optional[str] = None
    tipo: str = "SATISFACCION"
    audiencia: str = "ALUMNO"
    plantel_id: Optional[UUID] = None
    nivel_educativo_id: Optional[UUID] = None
    grupo_id: Optional[UUID] = None
    fecha_inicio: Optional[datetime.date] = None
    fecha_fin: Optional[datetime.date] = None
    anonima: bool = False


class PreguntaCreate(BaseModel):
    texto: str
    tipo_pregunta: str = "ESCALA_5"
    opciones: Optional[list[str]] = None
    orden: int = 1
    obligatoria: bool = True


class RespuestaItem(BaseModel):
    pregunta_id: UUID
    texto_respuesta: Optional[str] = None
    valor_numerico: Optional[float] = None
    opcion_seleccionada: Optional[str] = None


class SesionRespuestas(BaseModel):
    sesion_id: Optional[str] = None   # cliente puede enviarlo; si None se genera
    respuestas: list[RespuestaItem]


# ── helpers ───────────────────────────────────────────────────────────────────

async def _get_uid(db: AsyncSession, jwt_sub: str) -> Optional[str]:
    r = await db.execute(
        text("SELECT id FROM ades_usuarios WHERE oidc_sub = :s AND is_active = TRUE"),
        {"s": jwt_sub},
    )
    row = r.fetchone()
    return str(row[0]) if row else None


# ── endpoints ─────────────────────────────────────────────────────────────────

@router.get("")
async def listar_encuestas(
    tipo: Optional[str] = None,
    activa: Optional[bool] = None,
    plantel_id: Optional[UUID] = None,
    limit: int = 50,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    filters = ["e.is_active = TRUE"]
    params: dict = {"limit": limit}

    if tipo:
        filters.append("e.tipo = :tipo")
        params["tipo"] = tipo
    if activa is not None:
        filters.append("e.activa = :activa")
        params["activa"] = activa
    if plantel_id:
        filters.append("(e.plantel_id IS NULL OR e.plantel_id = :plantel_id::uuid)")
        params["plantel_id"] = str(plantel_id)

    where = " AND ".join(filters)
    rows = await db.execute(text(f"""
        SELECT
            e.id, e.titulo, e.descripcion, e.tipo, e.audiencia,
            e.plantel_id, pl.nombre_plantel,
            e.fecha_inicio, e.fecha_fin,
            e.anonima, e.activa, e.fccreacion,
            COUNT(DISTINCT ep.id) FILTER (WHERE ep.is_active = TRUE) AS total_preguntas,
            COUNT(DISTINCT er.sesion_id)                             AS total_respuestas
        FROM ades_encuestas e
        LEFT JOIN ades_planteles               pl ON pl.id = e.plantel_id
        LEFT JOIN ades_encuesta_preguntas      ep ON ep.encuesta_id = e.id
        LEFT JOIN ades_encuesta_respuestas     er ON er.encuesta_id = e.id
        WHERE {where}
        GROUP BY e.id, pl.nombre_plantel
        ORDER BY e.fccreacion DESC
        LIMIT :limit
    """), params)
    return rows.mappings().all()


@router.post("", status_code=201)
async def crear_encuesta(
    body: EncuestaCreate,
    db: AsyncSession = Depends(get_db),
    current_user: dict = Depends(get_current_user),
):
    uid = await _get_uid(db, current_user.get("sub", ""))
    row = await db.execute(text("""
        INSERT INTO ades_encuestas
            (titulo, descripcion, tipo, audiencia, plantel_id, nivel_educativo_id, grupo_id,
             fecha_inicio, fecha_fin, anonima, creado_por_id)
        VALUES
            (:titulo, :desc, :tipo, :audiencia, :plantel_id, :nivel_id, :grupo_id,
             :f_ini, :f_fin, :anonima, :uid)
        RETURNING id, titulo, tipo, activa, fccreacion
    """), {
        "titulo":    body.titulo,
        "desc":      body.descripcion,
        "tipo":      body.tipo,
        "audiencia": body.audiencia,
        "plantel_id":  str(body.plantel_id) if body.plantel_id else None,
        "nivel_id":    str(body.nivel_educativo_id) if body.nivel_educativo_id else None,
        "grupo_id":    str(body.grupo_id) if body.grupo_id else None,
        "f_ini":    body.fecha_inicio,
        "f_fin":    body.fecha_fin,
        "anonima":  body.anonima,
        "uid":      uid,
    })
    await db.commit()
    return row.mappings().first()


@router.get("/{encuesta_id}")
async def detalle_encuesta(
    encuesta_id: UUID,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    e_row = await db.execute(text("""
        SELECT e.*, pl.nombre_plantel,
               COUNT(DISTINCT er.sesion_id) AS total_respuestas
        FROM ades_encuestas e
        LEFT JOIN ades_planteles pl ON pl.id = e.plantel_id
        LEFT JOIN ades_encuesta_respuestas er ON er.encuesta_id = e.id
        WHERE e.id = :id::uuid AND e.is_active = TRUE
        GROUP BY e.id, pl.nombre_plantel
    """), {"id": str(encuesta_id)})
    enc = e_row.mappings().first()
    if not enc:
        raise HTTPException(status_code=404, detail="Encuesta no encontrada")

    p_rows = await db.execute(text("""
        SELECT id, texto, tipo_pregunta, opciones, orden, obligatoria
        FROM ades_encuesta_preguntas
        WHERE encuesta_id = :id::uuid AND is_active = TRUE
        ORDER BY orden
    """), {"id": str(encuesta_id)})

    return {**dict(enc), "preguntas": p_rows.mappings().all()}


@router.patch("/{encuesta_id}/toggle-activa")
async def toggle_activa(
    encuesta_id: UUID,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    row = await db.execute(text("""
        UPDATE ades_encuestas
        SET activa = NOT activa
        WHERE id = :id::uuid
        RETURNING id, activa
    """), {"id": str(encuesta_id)})
    await db.commit()
    return row.mappings().first()


@router.post("/{encuesta_id}/preguntas", status_code=201)
async def agregar_pregunta(
    encuesta_id: UUID,
    body: PreguntaCreate,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    row = await db.execute(text("""
        INSERT INTO ades_encuesta_preguntas
            (encuesta_id, texto, tipo_pregunta, opciones, orden, obligatoria)
        VALUES
            (:enc_id::uuid, :texto, :tipo, :opciones::jsonb, :orden, :oblig)
        RETURNING id, texto, tipo_pregunta, orden
    """), {
        "enc_id":  str(encuesta_id),
        "texto":   body.texto,
        "tipo":    body.tipo_pregunta,
        "opciones": json.dumps(body.opciones) if body.opciones else None,
        "orden":   body.orden,
        "oblig":   body.obligatoria,
    })
    await db.commit()
    return row.mappings().first()


@router.put("/{encuesta_id}/preguntas/{pregunta_id}")
async def actualizar_pregunta(
    encuesta_id: UUID,
    pregunta_id: UUID,
    body: PreguntaCreate,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    await db.execute(text("""
        UPDATE ades_encuesta_preguntas
        SET texto = :texto, tipo_pregunta = :tipo,
            opciones = :opciones::jsonb, orden = :orden, obligatoria = :oblig
        WHERE id = :pid::uuid AND encuesta_id = :enc_id::uuid
    """), {
        "enc_id":  str(encuesta_id),
        "pid":     str(pregunta_id),
        "texto":   body.texto,
        "tipo":    body.tipo_pregunta,
        "opciones": json.dumps(body.opciones) if body.opciones else None,
        "orden":   body.orden,
        "oblig":   body.obligatoria,
    })
    await db.commit()
    return {"ok": True}


@router.delete("/{encuesta_id}/preguntas/{pregunta_id}", status_code=204)
async def eliminar_pregunta(
    encuesta_id: UUID,
    pregunta_id: UUID,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    await db.execute(text("""
        UPDATE ades_encuesta_preguntas SET is_active = FALSE
        WHERE id = :pid::uuid AND encuesta_id = :enc_id::uuid
    """), {"enc_id": str(encuesta_id), "pid": str(pregunta_id)})
    await db.commit()


@router.post("/{encuesta_id}/responder", status_code=201)
async def responder_encuesta(
    encuesta_id: UUID,
    body: SesionRespuestas,
    db: AsyncSession = Depends(get_db),
    current_user: dict = Depends(get_current_user),
):
    """Guarda todas las respuestas de una sesión. ON CONFLICT DO NOTHING (idempotente)."""
    # Verificar encuesta activa
    enc_row = await db.execute(text("""
        SELECT id, activa, anonima FROM ades_encuestas
        WHERE id = :id::uuid AND is_active = TRUE
    """), {"id": str(encuesta_id)})
    enc = enc_row.fetchone()
    if not enc:
        raise HTTPException(status_code=404, detail="Encuesta no encontrada")
    if not enc[1]:
        raise HTTPException(status_code=400, detail="La encuesta no está activa")

    sesion_id = body.sesion_id or str(_uuid.uuid4())
    uid = None if enc[2] else await _get_uid(db, current_user.get("sub", ""))

    saved = 0
    for r in body.respuestas:
        result = await db.execute(text("""
            INSERT INTO ades_encuesta_respuestas
                (encuesta_id, pregunta_id, respondido_por_id, sesion_id,
                 texto_respuesta, valor_numerico, opcion_seleccionada)
            VALUES
                (:enc_id::uuid, :preg_id::uuid, :uid, :sesion,
                 :texto, :valor, :opcion)
            ON CONFLICT (pregunta_id, sesion_id) DO NOTHING
        """), {
            "enc_id":  str(encuesta_id),
            "preg_id": str(r.pregunta_id),
            "uid":     uid,
            "sesion":  sesion_id,
            "texto":   r.texto_respuesta,
            "valor":   r.valor_numerico,
            "opcion":  r.opcion_seleccionada,
        })
        saved += result.rowcount

    await db.commit()
    return {"ok": True, "sesion_id": sesion_id, "guardadas": saved}


@router.get("/{encuesta_id}/resultados")
async def resultados_encuesta(
    encuesta_id: UUID,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    """
    Resultados estadísticos consolidados por pregunta:
    - ESCALA_5: promedio, moda, distribución 1–5
    - OPCION_MULTIPLE: count + % por opción
    - BOOLEANO: count SÍ/NO con porcentajes
    - TEXTO_LIBRE: últimas 20 respuestas
    """
    total_sesiones_row = await db.execute(text("""
        SELECT COUNT(DISTINCT sesion_id) FROM ades_encuesta_respuestas
        WHERE encuesta_id = :id::uuid
    """), {"id": str(encuesta_id)})
    total_sesiones = total_sesiones_row.scalar() or 0

    preguntas_row = await db.execute(text("""
        SELECT id, texto, tipo_pregunta, opciones, orden
        FROM ades_encuesta_preguntas
        WHERE encuesta_id = :id::uuid AND is_active = TRUE
        ORDER BY orden
    """), {"id": str(encuesta_id)})
    preguntas = preguntas_row.mappings().all()

    resultados = []
    for p in preguntas:
        pid = str(p["id"])
        tipo = p["tipo_pregunta"]
        stats: dict[str, Any] = {
            "pregunta_id": pid,
            "texto":        p["texto"],
            "tipo_pregunta": tipo,
            "orden":        p["orden"],
        }

        if tipo == "ESCALA_5":
            row = await db.execute(text("""
                SELECT
                    COUNT(*)                        AS total,
                    ROUND(AVG(valor_numerico), 2)   AS promedio,
                    MODE() WITHIN GROUP (ORDER BY valor_numerico) AS moda,
                    COUNT(*) FILTER (WHERE ROUND(valor_numerico) = 1) AS n1,
                    COUNT(*) FILTER (WHERE ROUND(valor_numerico) = 2) AS n2,
                    COUNT(*) FILTER (WHERE ROUND(valor_numerico) = 3) AS n3,
                    COUNT(*) FILTER (WHERE ROUND(valor_numerico) = 4) AS n4,
                    COUNT(*) FILTER (WHERE ROUND(valor_numerico) = 5) AS n5
                FROM ades_encuesta_respuestas
                WHERE pregunta_id = :pid::uuid AND valor_numerico IS NOT NULL
            """), {"pid": pid})
            stats.update(dict(row.mappings().first() or {}))

        elif tipo == "OPCION_MULTIPLE":
            rows = await db.execute(text("""
                SELECT
                    opcion_seleccionada                         AS opcion,
                    COUNT(*)                                    AS cantidad,
                    ROUND(COUNT(*)::numeric / NULLIF(
                        SUM(COUNT(*)) OVER(), 0) * 100, 1)     AS porcentaje
                FROM ades_encuesta_respuestas
                WHERE pregunta_id = :pid::uuid AND opcion_seleccionada IS NOT NULL
                GROUP BY opcion_seleccionada
                ORDER BY cantidad DESC
            """), {"pid": pid})
            stats["distribucion"] = rows.mappings().all()
            stats["total"] = sum(r["cantidad"] for r in stats["distribucion"])

        elif tipo == "BOOLEANO":
            row = await db.execute(text("""
                SELECT
                    COUNT(*) FILTER (WHERE LOWER(opcion_seleccionada) IN ('sí','si','true','1')) AS si,
                    COUNT(*) FILTER (WHERE LOWER(opcion_seleccionada) IN ('no','false','0'))      AS no,
                    COUNT(*)                                                                       AS total
                FROM ades_encuesta_respuestas
                WHERE pregunta_id = :pid::uuid AND opcion_seleccionada IS NOT NULL
            """), {"pid": pid})
            r = dict(row.mappings().first() or {})
            total_bool = r.get("total") or 1
            r["pct_si"] = round((r.get("si", 0) or 0) / total_bool * 100, 1)
            r["pct_no"] = round((r.get("no", 0) or 0) / total_bool * 100, 1)
            stats.update(r)

        elif tipo == "TEXTO_LIBRE":
            rows = await db.execute(text("""
                SELECT texto_respuesta, fccreacion
                FROM ades_encuesta_respuestas
                WHERE pregunta_id = :pid::uuid AND texto_respuesta IS NOT NULL
                  AND LENGTH(TRIM(texto_respuesta)) > 0
                ORDER BY fccreacion DESC
                LIMIT 20
            """), {"pid": pid})
            stats["respuestas"] = [r["texto_respuesta"] for r in rows.mappings().all()]
            stats["total"] = len(stats["respuestas"])

        resultados.append(stats)

    return {
        "encuesta_id":     str(encuesta_id),
        "total_sesiones":  total_sesiones,
        "preguntas":       resultados,
    }


@router.get("/{encuesta_id}/respuestas-raw")
async def respuestas_raw(
    encuesta_id: UUID,
    limit: int = 200,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    """Respuestas crudas para exportación CSV."""
    rows = await db.execute(text("""
        SELECT
            er.sesion_id,
            ep.texto             AS pregunta,
            ep.tipo_pregunta,
            er.valor_numerico,
            er.opcion_seleccionada,
            er.texto_respuesta,
            er.fccreacion
        FROM ades_encuesta_respuestas er
        JOIN ades_encuesta_preguntas ep ON ep.id = er.pregunta_id
        WHERE er.encuesta_id = :id::uuid
        ORDER BY er.sesion_id, ep.orden
        LIMIT :limit
    """), {"id": str(encuesta_id), "limit": limit})
    return rows.mappings().all()


@router.delete("/{encuesta_id}", status_code=204)
async def eliminar_encuesta(
    encuesta_id: UUID,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    await db.execute(text("""
        UPDATE ades_encuestas SET is_active = FALSE WHERE id = :id::uuid
    """), {"id": str(encuesta_id)})
    await db.commit()
