"""
FASE 15 — Middleware de auditoría ADES.

Registra en ades_audit_log todas las mutaciones (POST/PUT/PATCH/DELETE)
con el usuario, endpoint, código HTTP y duración.

Diseño:
  - No bloquea la respuesta (escribe en background vía asyncio.create_task)
  - Si falla la escritura del log, el error se suprime (audit no es crítico)
  - Solo audita rutas /api/v1/* para excluir métricas y health
  - Extrae entidad e ID desde el path del endpoint
"""

from __future__ import annotations

import asyncio
import re
import time
import logging
import uuid
from typing import Callable

from jose import jwt, JWTError
from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request
from starlette.responses import Response
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import settings
from app.core.database import AsyncSessionLocal as async_session_factory

log = logging.getLogger(__name__)

_AUDIT_METHODS = {"POST", "PUT", "PATCH", "DELETE"}
_PATH_PREFIX = "/api/v1/"

# Extraer entidad e ID del path: /api/v1/alumnos/uuid → (alumnos, uuid)
_PATH_RE = re.compile(r"/api/v1/([^/]+)(?:/([^/?]+))?")

# Mapa método HTTP → acción de auditoría
_METHOD_ACCION = {
    "POST":   "INSERT",
    "PUT":    "UPDATE",
    "PATCH":  "UPDATE",
    "DELETE": "DELETE",
}


def _extract_user(request: Request) -> tuple[uuid.UUID | None, str | None]:
    """Devuelve (usuario_id, nombre_usuario) del JWT de la request, o (None, None)."""
    auth = request.headers.get("Authorization", "")
    if not auth.startswith("Bearer "):
        return None, None
    token = auth[7:]
    try:
        payload = jwt.decode(token, settings.SECRET_KEY, algorithms=["HS256"])
        sub = payload.get("sub")
        nombre = payload.get("nombre") or payload.get("preferred_username") or payload.get("username")
        uid = uuid.UUID(str(sub)) if sub else None
        return uid, nombre
    except (JWTError, ValueError):
        return None, None


def _extract_entidad(path: str) -> tuple[str, uuid.UUID | None]:
    """Extrae entidad e ID del path del endpoint."""
    m = _PATH_RE.match(path)
    if not m:
        return path, None
    entidad = m.group(1)
    raw_id = m.group(2)
    entidad_id = None
    if raw_id:
        try:
            entidad_id = uuid.UUID(raw_id)
        except ValueError:
            pass
    return entidad, entidad_id


async def _write_audit(
    usuario_id: uuid.UUID | None,
    nombre_usuario: str | None,
    ip_origen: str | None,
    accion: str,
    entidad: str,
    entidad_id: uuid.UUID | None,
    endpoint: str,
    metodo_http: str,
    codigo_respuesta: int,
    duracion_ms: int,
) -> None:
    """Escribe un registro en ades_audit_log. Fallo silencioso."""
    try:
        async with async_session_factory() as session:
            async with session.begin():
                await session.execute(
                    __import__("sqlalchemy", fromlist=["text"]).text(
                        """
                        INSERT INTO ades_audit_log
                            (usuario_id, nombre_usuario, ip_origen, accion,
                             entidad, entidad_id, endpoint, metodo_http,
                             codigo_respuesta, duracion_ms)
                        VALUES
                            (:usuario_id, :nombre_usuario, :ip_origen, :accion,
                             :entidad, :entidad_id, :endpoint, :metodo_http,
                             :codigo_respuesta, :duracion_ms)
                        """
                    ),
                    {
                        "usuario_id":      str(usuario_id) if usuario_id else None,
                        "nombre_usuario":  nombre_usuario,
                        "ip_origen":       ip_origen,
                        "accion":          accion,
                        "entidad":         entidad,
                        "entidad_id":      str(entidad_id) if entidad_id else None,
                        "endpoint":        endpoint[:200],
                        "metodo_http":     metodo_http,
                        "codigo_respuesta": codigo_respuesta,
                        "duracion_ms":     duracion_ms,
                    },
                )
    except Exception as exc:  # noqa: BLE001
        log.debug("Audit write failed (non-critical): %s", exc)


class AuditMiddleware(BaseHTTPMiddleware):
    """Registra mutaciones POST/PUT/PATCH/DELETE en ades_audit_log."""

    async def dispatch(self, request: Request, call_next: Callable) -> Response:
        method = request.method.upper()
        path = request.url.path

        # Solo auditar mutaciones en rutas de la API
        if method not in _AUDIT_METHODS or not path.startswith(_PATH_PREFIX):
            return await call_next(request)

        # Ignorar paths de sistema que no son entidades de dominio
        skip_paths = ("/api/v1/health", "/api/v1/auth", "/api/v1/notificaciones/")
        if any(path.startswith(p) for p in skip_paths):
            return await call_next(request)

        t0 = time.monotonic()
        response = await call_next(request)
        duracion_ms = int((time.monotonic() - t0) * 1000)

        usuario_id, nombre_usuario = _extract_user(request)
        entidad, entidad_id = _extract_entidad(path)
        accion = _METHOD_ACCION[method]
        ip = request.headers.get("X-Real-IP") or (
            request.client.host if request.client else None
        )

        # Escritura asíncrona, sin bloquear la respuesta
        asyncio.create_task(
            _write_audit(
                usuario_id=usuario_id,
                nombre_usuario=nombre_usuario,
                ip_origen=ip,
                accion=accion,
                entidad=entidad,
                entidad_id=entidad_id,
                endpoint=path,
                metodo_http=method,
                codigo_respuesta=response.status_code,
                duracion_ms=duracion_ms,
            )
        )

        return response
