"""
FASE 16 — Superset embedded dashboard guest tokens.

Flujo:
  1. Angular solicita GET /superset/dashboard/{key}
  2. FastAPI se autentica en Superset con cuenta de servicio
  3. Obtiene guest token con RLS según el rol del usuario ADES
  4. Devuelve token + URL del iframe al frontend

Keys válidas: instituto | plantel | docente | alumno
"""

from __future__ import annotations

import logging
from functools import lru_cache
from typing import Literal

import httpx
from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel

from app.core.config import settings
from app.core.security import AdesUser, get_current_user, get_ades_user

log = logging.getLogger(__name__)
router = APIRouter(prefix="/superset", tags=["superset"])

DashboardKey = Literal["instituto", "plantel", "docente", "alumno"]

_DASHBOARD_MAP: dict[str, str] = {
    "instituto": "SUPERSET_DASHBOARD_INSTITUTO",
    "plantel":   "SUPERSET_DASHBOARD_PLANTEL",
    "docente":   "SUPERSET_DASHBOARD_DOCENTE",
    "alumno":    "SUPERSET_DASHBOARD_ALUMNO",
}

_NIVEL_A_KEY: dict[int, str] = {
    0: "instituto",
    1: "plantel",
    2: "plantel",
    3: "plantel",
    4: "docente",
    5: "alumno",
}


class GuestTokenResponse(BaseModel):
    token: str
    dashboard_id: str
    embed_url: str


async def _superset_login() -> str:
    """Obtiene un JWT de Superset usando la cuenta de servicio."""
    async with httpx.AsyncClient(timeout=10) as client:
        resp = await client.post(
            f"{settings.SUPERSET_URL}/api/v1/security/login",
            json={
                "username": settings.SUPERSET_ADMIN_USER,
                "password": settings.SUPERSET_ADMIN_PASSWORD,
                "provider": "db",
                "refresh": False,
            },
        )
        if resp.status_code != 200:
            log.error("Superset login failed: %s", resp.text)
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="No se pudo conectar con el servicio de dashboards",
            )
        return resp.json()["access_token"]


def _build_rls(user: AdesUser) -> list[dict]:
    """Construye las cláusulas RLS según el rol del usuario."""
    nivel = user.nivel_acceso
    rls: list[dict] = []

    if nivel == 0:
        # ADMIN_GLOBAL — sin restricción
        return rls

    if nivel <= 2 and user.plantel_id:
        # ADMIN_PLANTEL, DIRECTOR, SUBDIRECTOR — filtrar por plantel
        rls.append({
            "clause": f"plantel_id = '{user.plantel_id}'",
            "dataset": None,  # aplica a todos los datasets
        })
    elif nivel == 4 and user.persona_id:
        # DOCENTE — filtrar por sus grupos (aproximación: por persona_id del profesor)
        rls.append({
            "clause": f"profesor_id = '{user.persona_id}'",
            "dataset": None,
        })
    elif nivel == 5 and user.persona_id:
        # ALUMNO / PADRE_FAMILIA
        rls.append({
            "clause": f"estudiante_id = '{user.persona_id}'",
            "dataset": None,
        })

    return rls


@router.get("/dashboard/{key}", response_model=GuestTokenResponse)
async def get_guest_token(
    key: DashboardKey,
    user: AdesUser = Depends(get_ades_user),
) -> GuestTokenResponse:
    """
    Devuelve un guest token de Superset con RLS según el rol del usuario.
    El Angular lo usa para embeber el dashboard vía iframe.
    """
    # Determinar el dashboard correcto para este rol
    effective_key = key
    rol_key = _NIVEL_A_KEY.get(user.nivel_acceso, "alumno")
    if key == "instituto" and user.nivel_acceso > 0:
        effective_key = rol_key

    dashboard_id = getattr(settings, _DASHBOARD_MAP.get(effective_key, "SUPERSET_DASHBOARD_ALUMNO"), "")
    if not dashboard_id:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Dashboard '{effective_key}' no configurado. Crear en Superset y configurar SUPERSET_DASHBOARD_{effective_key.upper()} en .env",
        )

    access_token = await _superset_login()

    rls = _build_rls(user)
    guest_payload = {
        "resources": [{"type": "dashboard", "id": dashboard_id}],
        "rls": rls,
        "user": {
            "username": user.username,
            "first_name": user.nombre or "",
            "last_name": "",
        },
    }

    async with httpx.AsyncClient(timeout=10) as client:
        resp = await client.post(
            f"{settings.SUPERSET_URL}/api/v1/security/guest_token/",
            json=guest_payload,
            headers={"Authorization": f"Bearer {access_token}"},
        )
        if resp.status_code != 200:
            log.error("Superset guest token error: %s", resp.text)
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="Error al obtener token del dashboard",
            )

    token = resp.json()["token"]
    embed_url = f"{settings.SUPERSET_URL}/superset/embedded/{dashboard_id}"

    return GuestTokenResponse(
        token=token,
        dashboard_id=dashboard_id,
        embed_url=embed_url,
    )


@router.get("/dashboards")
async def list_available_dashboards(
    user: AdesUser = Depends(get_ades_user),
) -> dict:
    """Devuelve qué dashboards están disponibles para el rol del usuario."""
    rol_key = _NIVEL_A_KEY.get(user.nivel_acceso, "alumno")
    available = {}
    for key, env_var in _DASHBOARD_MAP.items():
        dashboard_id = getattr(settings, env_var, "")
        if dashboard_id:
            available[key] = {"id": dashboard_id, "configured": True}
        else:
            available[key] = {"id": None, "configured": False}
    return {"rol_key": rol_key, "dashboards": available}
