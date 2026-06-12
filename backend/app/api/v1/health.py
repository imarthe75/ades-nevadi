"""
/health  — estado básico (público)
/health/services — estado detallado de todos los servicios (requiere auth)
"""
from __future__ import annotations

import asyncio
import time
from typing import Any

import httpx
from fastapi import APIRouter, Depends
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import text

from app.core.database import get_db
from app.core.security import get_current_user
from app.core.config import settings

router = APIRouter()


# ── GET /health ───────────────────────────────────────────────────────────────
@router.get("/health", tags=["sistema"])
async def health(db: AsyncSession = Depends(get_db)):
    result = await db.execute(text("SELECT uuidv7(), current_database(), version()"))
    row = result.one()
    return {
        "status": "ok",
        "db": row[1],
        "pg_version": row[2].split(" ")[0],
        "uuid_v7_sample": str(row[0]),
    }


# ── helpers ───────────────────────────────────────────────────────────────────

async def _check_postgres(db: AsyncSession) -> dict[str, Any]:
    t0 = time.monotonic()
    try:
        row = await db.execute(text("SELECT version(), pg_database_size(current_database())"))
        r = row.one()
        latency = round((time.monotonic() - t0) * 1000, 1)
        size_mb = round(r[1] / 1024 / 1024, 1)
        return {"status": "ok", "version": r[0].split(" ")[0], "size_mb": size_mb, "latency_ms": latency}
    except Exception as e:
        return {"status": "error", "error": str(e)}


async def _check_valkey() -> dict[str, Any]:
    t0 = time.monotonic()
    try:
        import redis.asyncio as aioredis
        url = settings.VALKEY_URL
        r = await aioredis.from_url(url, socket_connect_timeout=3)
        pong = await r.ping()
        info = await r.info("server")
        await r.aclose()
        latency = round((time.monotonic() - t0) * 1000, 1)
        return {
            "status": "ok" if pong else "error",
            "version": info.get("valkey_version") or info.get("redis_version", "?"),
            "latency_ms": latency,
        }
    except Exception as e:
        return {"status": "error", "error": str(e)[:120]}


async def _check_minio() -> dict[str, Any]:
    t0 = time.monotonic()
    try:
        scheme = "https" if settings.MINIO_SECURE else "http"
        url = f"{scheme}://{settings.MINIO_ENDPOINT}/minio/health/live"
        async with httpx.AsyncClient(timeout=4.0) as client:
            resp = await client.get(url)
        latency = round((time.monotonic() - t0) * 1000, 1)
        return {
            "status": "ok" if resp.status_code == 200 else "degraded",
            "http_status": resp.status_code,
            "latency_ms": latency,
        }
    except Exception as e:
        return {"status": "error", "error": str(e)[:120]}


async def _check_authentik() -> dict[str, Any]:
    t0 = time.monotonic()
    try:
        base = settings.OIDC_ISSUER.split("/application/")[0]
        url = f"{base}/-/health/ready/"
        async with httpx.AsyncClient(timeout=4.0) as client:
            resp = await client.get(url)
        latency = round((time.monotonic() - t0) * 1000, 1)
        return {
            "status": "ok" if resp.status_code == 204 else "degraded",
            "http_status": resp.status_code,
            "latency_ms": latency,
        }
    except Exception as e:
        return {"status": "error", "error": str(e)[:120]}


# ── GET /health/services ──────────────────────────────────────────────────────
@router.get("/health/services", tags=["sistema"])
async def health_services(
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    """Estado detallado de todos los servicios. Requiere autenticación."""
    pg, valkey, minio, authentik = await asyncio.gather(
        _check_postgres(db),
        _check_valkey(),
        _check_minio(),
        _check_authentik(),
        return_exceptions=False,
    )

    services = {
        "postgresql": pg,
        "valkey":     valkey,
        "minio":      minio,
        "authentik":  authentik,
    }

    overall = "ok"
    for svc in services.values():
        if isinstance(svc, dict) and svc.get("status") == "error":
            overall = "degraded"
            break

    return {"overall": overall, "services": services}
