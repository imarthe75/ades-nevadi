"""
/bbb — FASE 26: Videoconferencias BigBlueButton

  GET    /bbb/info                              — estado del servidor BBB (admin)
  GET    /bbb/reuniones                         — listar reuniones del plantel
  POST   /bbb/reuniones                         — crear/programar reunión
  GET    /bbb/reuniones/{id}                    — detalle de reunión
  DELETE /bbb/reuniones/{id}                    — cancelar reunión
  GET    /bbb/reuniones/{id}/join               — obtener URL de acceso (moderador/asistente)
  POST   /bbb/reuniones/{id}/terminar           — terminar reunión activa
  GET    /bbb/reuniones/{id}/grabaciones        — grabaciones de la reunión
  POST   /bbb/webhook                           — webhook BBB (eventos meeting-ended, etc.)

Integración API-only: ADES conecta con servidor BBB externo via API REST con checksum SHA-1.
BBB_SERVER_URL y BBB_SHARED_SECRET configurados en .env / Vault.
"""
from __future__ import annotations

import hashlib
import logging
import secrets
import uuid as uuid_lib
from datetime import datetime, timezone
from typing import Optional
from uuid import UUID

import httpx
import xmltodict
from fastapi import APIRouter, BackgroundTasks, Depends, HTTPException, Request
from pydantic import BaseModel
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import settings
from app.core.database import get_db
from app.core.security import AdesUser, get_ades_user

log = logging.getLogger(__name__)
router = APIRouter(prefix="/bbb", tags=["bbb"])

# ──────────────────────────────────────────────────────────────────────────────
# BBB API helper
# ──────────────────────────────────────────────────────────────────────────────

def _bbb_checksum(action: str, params: str, secret: str) -> str:
    raw = f"{action}{params}{secret}"
    return hashlib.sha1(raw.encode()).hexdigest()  # noqa: S324 — BBB requiere SHA-1


async def _bbb_call(action: str, params: dict | None = None) -> dict:
    """Llama a la API BBB y devuelve el resultado como dict (XML→JSON)."""
    bbb_url = getattr(settings, "BBB_SERVER_URL", "")
    bbb_secret = getattr(settings, "BBB_SHARED_SECRET", "")

    if not bbb_url or not bbb_secret:
        raise HTTPException(503, "Servidor BBB no configurado. Establezca BBB_SERVER_URL y BBB_SHARED_SECRET en .env")

    qs = "&".join(f"{k}={v}" for k, v in (params or {}).items())
    checksum = _bbb_checksum(action, qs, bbb_secret)
    url = f"{bbb_url.rstrip('/')}/api/{action}?{qs}&checksum={checksum}"

    async with httpx.AsyncClient(timeout=15.0, verify=settings.BBB_SSL_VERIFY) as client:
        r = await client.get(url)
        r.raise_for_status()

    data = xmltodict.parse(r.text)
    resp = data.get("response", {})
    if resp.get("returncode") != "SUCCESS":
        msg = resp.get("message", "BBB retornó error")
        raise HTTPException(502, f"BBB API: {msg}")
    return resp


def _bbb_join_url(meeting_id: str, full_name: str, password: str, user_id: str) -> str:
    bbb_url = getattr(settings, "BBB_SERVER_URL", "")
    bbb_secret = getattr(settings, "BBB_SHARED_SECRET", "")
    params = f"fullName={full_name}&meetingID={meeting_id}&password={password}&userID={user_id}"
    checksum = _bbb_checksum("join", params, bbb_secret)
    return f"{bbb_url.rstrip('/')}/api/join?{params}&checksum={checksum}"


# ──────────────────────────────────────────────────────────────────────────────
# Schemas
# ──────────────────────────────────────────────────────────────────────────────

class ReunionCreate(BaseModel):
    nombre: str
    descripcion: Optional[str] = None
    tipo: str = "CLASE"
    grupo_id: Optional[UUID] = None
    plantel_id: Optional[UUID] = None
    fecha_programada: str           # ISO 8601
    duracion_max_min: int = 60
    grabar: bool = False
    bienvenida_msg: Optional[str] = None
    participantes_max: int = 50


# ──────────────────────────────────────────────────────────────────────────────
# Endpoints
# ──────────────────────────────────────────────────────────────────────────────

@router.get("/info")
async def info_servidor(user: AdesUser = Depends(get_ades_user)):
    if user.nivel_acceso > 2:
        raise HTTPException(403, "Solo directores y admins pueden ver la info del servidor BBB")
    try:
        data = await _bbb_call("getMeetings")
        meetings = data.get("meetings") or {}
        meeting_list = meetings.get("meeting", [])
        if isinstance(meeting_list, dict):
            meeting_list = [meeting_list]
        return {
            "servidor": getattr(settings, "BBB_SERVER_URL", "no_configurado"),
            "reuniones_activas": len(meeting_list),
            "meetings": meeting_list,
        }
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(502, f"No se pudo contactar el servidor BBB: {e}")


@router.get("/reuniones")
async def listar_reuniones(
    plantel_id: Optional[UUID] = None,
    grupo_id: Optional[UUID] = None,
    estado: Optional[str] = None,
    db: AsyncSession = Depends(get_db),
    user: AdesUser = Depends(get_ades_user),
):
    where = ["TRUE"]
    params: dict = {}
    if plantel_id:
        where.append("r.plantel_id = :plantel_id")
        params["plantel_id"] = str(plantel_id)
    elif user.nivel_acceso > 1 and user.plantel_id:
        where.append("r.plantel_id = :plantel_id")
        params["plantel_id"] = str(user.plantel_id)
    if grupo_id:
        where.append("r.grupo_id = :grupo_id")
        params["grupo_id"] = str(grupo_id)
    if estado:
        where.append("r.estado = :estado")
        params["estado"] = estado

    rows = await db.execute(
        text(f"""
            SELECT r.id, r.meeting_id, r.nombre, r.descripcion, r.tipo,
                   r.grupo_id, g.nombre_grupo, r.plantel_id, pl.nombre_plantel AS plantel_nombre,
                   r.fecha_programada, r.duracion_max_min, r.grabar,
                   r.estado, r.participantes_max, r.bienvenida_msg, r.fecha_creacion,
                   p.nombre || ' ' || p.apellido_paterno AS organizador
            FROM ades_bbb_reuniones r
            LEFT JOIN ades_grupos g ON r.grupo_id = g.id
            LEFT JOIN ades_planteles pl ON r.plantel_id = pl.id
            LEFT JOIN ades_personas p ON r.organiza_persona_id = p.id
            WHERE {' AND '.join(where)}
            ORDER BY r.fecha_programada DESC
            LIMIT 100
        """),
        params,
    )
    return [dict(r._mapping) for r in rows.fetchall()]


@router.post("/reuniones", status_code=201)
async def crear_reunion(
    body: ReunionCreate,
    db: AsyncSession = Depends(get_db),
    user: AdesUser = Depends(get_ades_user),
):
    if user.nivel_acceso > 4:
        raise HTTPException(403, "Solo docentes y superiores pueden crear reuniones BBB")

    meeting_id = str(uuid_lib.uuid4())
    pwd_mod = secrets.token_urlsafe(12)
    pwd_att = secrets.token_urlsafe(12)

    # Crear reunión en BBB (la API crea la sala; los usuarios se unen cuando llegan)
    bbb_response = {}
    try:
        bbb_response = await _bbb_call("create", {
            "meetingID": meeting_id,
            "name": body.nombre[:128],
            "attendeePW": pwd_att,
            "moderatorPW": pwd_mod,
            "record": "true" if body.grabar else "false",
            "duration": str(body.duracion_max_min),
            "maxParticipants": str(body.participantes_max),
            "welcomeMessage": (body.bienvenida_msg or f"Bienvenido a {body.nombre}"),
            "meta_bbb-origin": "ADES-Nevadi",
        })
    except HTTPException as e:
        log.warning("BBB no disponible al crear reunión: %s — se guarda como PROGRAMADA", e.detail)

    result = await db.execute(
        text("""
            INSERT INTO ades_bbb_reuniones
                (meeting_id, nombre, descripcion, tipo, grupo_id, plantel_id,
                 organiza_persona_id, fecha_programada, duracion_max_min,
                 password_moderador, password_asistente, grabar, bienvenida_msg,
                 participantes_max, bbb_create_response)
            VALUES
                (:meeting_id, :nombre, :descripcion, :tipo, :grupo_id, :plantel_id,
                 :organiza_persona_id, :fecha_programada::timestamptz, :duracion_max_min,
                 :pwd_mod, :pwd_att, :grabar, :bienvenida_msg,
                 :participantes_max, :bbb_resp::jsonb)
            RETURNING id
        """),
        {
            "meeting_id": meeting_id,
            "nombre": body.nombre,
            "descripcion": body.descripcion,
            "tipo": body.tipo,
            "grupo_id": str(body.grupo_id) if body.grupo_id else None,
            "plantel_id": str(body.plantel_id) if body.plantel_id else None,
            "organiza_persona_id": str(user.persona_id),
            "fecha_programada": body.fecha_programada,
            "duracion_max_min": body.duracion_max_min,
            "pwd_mod": pwd_mod,
            "pwd_att": pwd_att,
            "grabar": body.grabar,
            "bienvenida_msg": body.bienvenida_msg,
            "participantes_max": body.participantes_max,
            "bbb_resp": __import__("json").dumps(bbb_response),
        },
    )
    await db.commit()
    return {"id": str(result.fetchone()[0]), "meeting_id": meeting_id}


@router.get("/reuniones/{reunion_id}")
async def detalle_reunion(
    reunion_id: UUID,
    db: AsyncSession = Depends(get_db),
    user: AdesUser = Depends(get_ades_user),
):
    row = await db.execute(
        text("""
            SELECT r.*, g.nombre_grupo, pl.nombre_plantel AS plantel_nombre,
                   p.nombre || ' ' || p.apellido_paterno AS organizador
            FROM ades_bbb_reuniones r
            LEFT JOIN ades_grupos g ON r.grupo_id = g.id
            LEFT JOIN ades_planteles pl ON r.plantel_id = pl.id
            LEFT JOIN ades_personas p ON r.organiza_persona_id = p.id
            WHERE r.id = :id
        """),
        {"id": str(reunion_id)},
    )
    r = row.fetchone()
    if not r:
        raise HTTPException(404, "Reunión no encontrada")
    data = dict(r._mapping)
    data.pop("password_moderador", None)  # ocultar passwords
    data.pop("password_asistente", None)
    return data


@router.get("/reuniones/{reunion_id}/join")
async def join_url(
    reunion_id: UUID,
    rol: str = "asistente",
    db: AsyncSession = Depends(get_db),
    user: AdesUser = Depends(get_ades_user),
):
    row = await db.execute(
        text("SELECT meeting_id, password_moderador, password_asistente, estado, nombre FROM ades_bbb_reuniones WHERE id = :id"),
        {"id": str(reunion_id)},
    )
    r = row.fetchone()
    if not r:
        raise HTTPException(404, "Reunión no encontrada")
    if r.estado == "CANCELADA":
        raise HTTPException(400, "La reunión fue cancelada")

    # Moderador solo si nivel_acceso <= 4 (docente o superior)
    es_moderador = (rol == "moderador" and user.nivel_acceso <= 4)
    password = r.password_moderador if es_moderador else r.password_asistente
    nombre_completo = user.nombre or "Usuario ADES"

    bbb_url = getattr(settings, "BBB_SERVER_URL", "")
    if not bbb_url:
        raise HTTPException(503, "Servidor BBB no configurado")

    join = _bbb_join_url(r.meeting_id, nombre_completo, password, str(user.persona_id))

    # Registrar asistencia (upsert)
    await db.execute(
        text("""
            INSERT INTO ades_bbb_asistencia (reunion_id, persona_id, rol_bbb, joined_at)
            VALUES (:rid, :pid, :rol, NOW())
            ON CONFLICT (reunion_id, persona_id) DO UPDATE SET
                joined_at = NOW(), rol_bbb = EXCLUDED.rol_bbb
        """),
        {"rid": str(reunion_id), "pid": str(user.persona_id), "rol": "MODERADOR" if es_moderador else "ASISTENTE"},
    )
    # Actualizar estado si era PROGRAMADA
    if r.estado == "PROGRAMADA":
        await db.execute(
            text("UPDATE ades_bbb_reuniones SET estado = 'EN_CURSO' WHERE id = :id"),
            {"id": str(reunion_id)},
        )
    await db.commit()

    return {"join_url": join, "rol": "MODERADOR" if es_moderador else "ASISTENTE", "nombre_reunion": r.nombre}


@router.post("/reuniones/{reunion_id}/terminar")
async def terminar_reunion(
    reunion_id: UUID,
    db: AsyncSession = Depends(get_db),
    user: AdesUser = Depends(get_ades_user),
):
    if user.nivel_acceso > 4:
        raise HTTPException(403, "Solo docentes y superiores pueden terminar reuniones")

    row = await db.execute(
        text("SELECT meeting_id, password_moderador FROM ades_bbb_reuniones WHERE id = :id"),
        {"id": str(reunion_id)},
    )
    r = row.fetchone()
    if not r:
        raise HTTPException(404, "Reunión no encontrada")

    try:
        await _bbb_call("end", {"meetingID": r.meeting_id, "password": r.password_moderador})
    except Exception:
        pass  # si ya terminó, continuar

    await db.execute(
        text("UPDATE ades_bbb_reuniones SET estado = 'FINALIZADA' WHERE id = :id"),
        {"id": str(reunion_id)},
    )
    # Registrar left_at para todos
    await db.execute(
        text("UPDATE ades_bbb_asistencia SET left_at = NOW() WHERE reunion_id = :rid AND left_at IS NULL"),
        {"rid": str(reunion_id)},
    )
    await db.commit()
    return {"terminada": True}


@router.delete("/reuniones/{reunion_id}", status_code=204)
async def cancelar_reunion(
    reunion_id: UUID,
    db: AsyncSession = Depends(get_db),
    user: AdesUser = Depends(get_ades_user),
):
    if user.nivel_acceso > 3:
        raise HTTPException(403, "Solo coordinadores y superiores pueden cancelar reuniones")
    await db.execute(
        text("UPDATE ades_bbb_reuniones SET estado = 'CANCELADA' WHERE id = :id"),
        {"id": str(reunion_id)},
    )
    await db.commit()


@router.get("/reuniones/{reunion_id}/grabaciones")
async def grabaciones(
    reunion_id: UUID,
    db: AsyncSession = Depends(get_db),
    user: AdesUser = Depends(get_ades_user),
):
    row = await db.execute(
        text("SELECT meeting_id FROM ades_bbb_reuniones WHERE id = :id"),
        {"id": str(reunion_id)},
    )
    r = row.fetchone()
    if not r:
        raise HTTPException(404, "Reunión no encontrada")

    # Consultar al servidor BBB y sincronizar
    try:
        data = await _bbb_call("getRecordings", {"meetingID": r.meeting_id})
        recordings = (data.get("recordings") or {}).get("recording", [])
        if isinstance(recordings, dict):
            recordings = [recordings]
        for rec in recordings:
            await db.execute(
                text("""
                    INSERT INTO ades_bbb_grabaciones
                        (reunion_id, record_id, nombre, url_playback, duracion_segundos, publicada, fecha_grabacion, formatos)
                    VALUES
                        (:rid, :record_id, :nombre, :url, :dur, :pub, :fecha, :formatos::jsonb)
                    ON CONFLICT (record_id) DO UPDATE SET
                        url_playback = EXCLUDED.url_playback,
                        publicada = EXCLUDED.publicada
                """),
                {
                    "rid": str(reunion_id),
                    "record_id": rec.get("recordID", ""),
                    "nombre": rec.get("name", "Grabación"),
                    "url": rec.get("playback", {}).get("format", {}).get("url", ""),
                    "dur": int(rec.get("playback", {}).get("format", {}).get("length", 0)) if rec.get("playback") else None,
                    "pub": rec.get("published", "false") == "true",
                    "fecha": rec.get("startTime"),
                    "formatos": __import__("json").dumps([rec.get("playback", {})]),
                },
            )
        await db.commit()
    except Exception:
        pass  # Si BBB no responde, devolver lo que hay en BD

    rows = await db.execute(
        text("SELECT * FROM ades_bbb_grabaciones WHERE reunion_id = :rid ORDER BY fecha_grabacion DESC"),
        {"rid": str(reunion_id)},
    )
    return [dict(r._mapping) for r in rows.fetchall()]


@router.post("/webhook")
async def webhook_bbb(request: Request, bg: BackgroundTasks, db: AsyncSession = Depends(get_db)):
    """Recibe webhooks de BBB (meeting-ended, user-left, recording-ready)."""
    body = await request.json()
    event = body.get("event", {})
    etype = event.get("header", {}).get("name", "")
    meeting_id = event.get("data", {}).get("id", "")
    log.info("BBB webhook: %s meeting=%s", etype, meeting_id)

    if etype == "meeting-ended" and meeting_id:
        await db.execute(
            text("UPDATE ades_bbb_reuniones SET estado = 'FINALIZADA' WHERE meeting_id = :mid"),
            {"mid": meeting_id},
        )
        await db.execute(
            text("""
                UPDATE ades_bbb_asistencia a
                SET left_at = NOW(),
                    duracion_segundos = EXTRACT(EPOCH FROM (NOW() - a.joined_at))::int
                FROM ades_bbb_reuniones r
                WHERE a.reunion_id = r.id AND r.meeting_id = :mid AND a.left_at IS NULL
            """),
            {"mid": meeting_id},
        )
        await db.commit()

    return {"procesado": True}
