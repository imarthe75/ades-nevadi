from __future__ import annotations
import structlog
import httpx
from fastapi import APIRouter, Form, HTTPException

logger = structlog.get_logger()

router = APIRouter(tags=["auth"])

_AUTHENTIK_INTERNAL = "http://ades-authentik-server:9000"
_TOKEN_URL = f"{_AUTHENTIK_INTERNAL}/application/o/token/"
_CLIENT_ID = "ades-frontend"
_REDIRECT_URI = "https://ades.setag.mx/callback"


@router.post("/auth/callback")
async def auth_callback(
    code: str = Form(...),
    code_verifier: str = Form(...),
):
    logger.info("auth_callback.start", code_prefix=code[:8], verifier_len=len(code_verifier))

    try:
        async with httpx.AsyncClient(timeout=15) as client:
            resp = await client.post(
                _TOKEN_URL,
                data={
                    "grant_type":    "authorization_code",
                    "code":          code,
                    "redirect_uri":  _REDIRECT_URI,
                    "client_id":     _CLIENT_ID,
                    "code_verifier": code_verifier,
                },
                headers={"Content-Type": "application/x-www-form-urlencoded"},
            )
    except httpx.RequestError as exc:
        logger.error("auth_callback.unreachable", error=str(exc))
        raise HTTPException(status_code=503, detail={"error": "authentik_unreachable", "message": str(exc)})

    logger.info("auth_callback.response", status=resp.status_code, body=resp.text[:300])

    if resp.status_code == 200:
        tokens = resp.json()
        return {
            "access_token": tokens.get("access_token"),
            "id_token":     tokens.get("id_token"),
            "token_type":   tokens.get("token_type", "Bearer"),
            "expires_in":   tokens.get("expires_in"),
        }

    try:
        detail = resp.json()
    except Exception:
        detail = {"error": "token_exchange_failed", "status": resp.status_code, "body": resp.text[:200]}

    logger.error("auth_callback.failed", authentik_status=resp.status_code, detail=detail)
    raise HTTPException(
        status_code=resp.status_code if resp.status_code in (400, 401, 403) else 502,
        detail=detail,
    )
