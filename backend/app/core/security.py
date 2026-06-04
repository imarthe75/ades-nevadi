"""
Verificación de tokens JWT emitidos por Authentik.
Descarga JWKS por la red interna Docker (más rápido, sin hairpin NAT).
Valida firma RS256 + claims de audiencia.
"""
from __future__ import annotations
import logging
import httpx
from functools import lru_cache
from jose import jwt, JWTError
from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from .config import settings

logger = logging.getLogger("ades.security")

_bearer = HTTPBearer(auto_error=True)

# URL interna de Authentik dentro de la red Docker — evita hairpin NAT y SSL overhead
_AUTHENTIK_INTERNAL = "http://ades-authentik-server:9000"


@lru_cache(maxsize=1)
def _jwks_uri() -> str:
    # Intentar primero por red interna Docker; si falla, usar URL externa
    for base in (_AUTHENTIK_INTERNAL, settings.OIDC_ISSUER.split("/application/")[0]):
        try:
            slug = settings.OIDC_ISSUER.split("/application/o/")[-1].rstrip("/")
            disc_url = f"{base}/application/o/{slug}/.well-known/openid-configuration"
            resp = httpx.get(disc_url, timeout=5)
            resp.raise_for_status()
            jwks = resp.json()["jwks_uri"]
            # Reemplazar host externo por interno para la descarga de claves
            jwks_internal = jwks.replace(
                settings.OIDC_ISSUER.split("/application/")[0],
                _AUTHENTIK_INTERNAL,
            )
            logger.info("JWKS URI: %s", jwks_internal)
            return jwks_internal
        except Exception as e:
            logger.warning("No se pudo obtener JWKS URI desde %s: %s", base, e)
    raise RuntimeError("No se pudo obtener el JWKS URI de Authentik")


def _fetch_jwks() -> dict:
    uri = _jwks_uri()
    resp = httpx.get(uri, timeout=10)
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
        logger.debug("Token válido: sub=%s email=%s", payload.get("sub"), payload.get("email"))
        return payload
    except JWTError as e:
        logger.warning("Token inválido: %s", e)
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail=f"Token inválido: {e}",
            headers={"WWW-Authenticate": "Bearer"},
        )
    except Exception as e:
        logger.error("Error al verificar token: %s", e)
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="No se puede contactar el servidor de autenticación",
        )


async def get_current_user(
    creds: HTTPAuthorizationCredentials = Depends(_bearer),
) -> dict:
    return verify_token(creds.credentials)


async def require_role(*roles: str):
    async def _check(user: dict = Depends(get_current_user)) -> dict:
        user_roles: list[str] = user.get("groups", []) or user.get("roles", [])
        if not any(r in user_roles for r in roles):
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail=f"Se requiere uno de los roles: {', '.join(roles)}",
            )
        return user
    return _check
