"""
Verificación de tokens JWT emitidos por Authentik.
Descarga las JWKS públicas del endpoint de Authentik y valida firma + claims.
"""
from __future__ import annotations
import httpx
from functools import lru_cache
from jose import jwt, JWTError
from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from .config import settings

_bearer = HTTPBearer(auto_error=True)


@lru_cache(maxsize=1)
def _jwks_uri() -> str:
    # Authentik expone el OIDC discovery en /.well-known/openid-configuration
    disc_url = settings.OIDC_ISSUER.rstrip("/") + "/.well-known/openid-configuration"
    resp = httpx.get(disc_url, timeout=10)
    resp.raise_for_status()
    return resp.json()["jwks_uri"]


def _fetch_jwks() -> dict:
    resp = httpx.get(_jwks_uri(), timeout=10)
    resp.raise_for_status()
    return resp.json()


def verify_token(token: str) -> dict:
    try:
        jwks = _fetch_jwks()
        payload = jwt.decode(
            token,
            jwks,
            algorithms=["RS256"],
            audience=settings.OIDC_CLIENT_ID,
            options={"verify_exp": True},
        )
        return payload
    except JWTError as e:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail=f"Token inválido: {e}",
            headers={"WWW-Authenticate": "Bearer"},
        )


async def get_current_user(
    creds: HTTPAuthorizationCredentials = Depends(_bearer),
) -> dict:
    return verify_token(creds.credentials)


async def require_role(*roles: str):
    """Dependencia de FastAPI que verifica que el usuario tenga uno de los roles dados."""
    async def _check(user: dict = Depends(get_current_user)) -> dict:
        user_roles: list[str] = user.get("groups", []) or user.get("roles", [])
        if not any(r in user_roles for r in roles):
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail=f"Se requiere uno de los roles: {', '.join(roles)}",
            )
        return user
    return _check
