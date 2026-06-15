"""
Verificación de tokens JWT emitidos por Authentik.
Descarga JWKS por la red interna Docker (más rápido, sin hairpin NAT).
Valida firma RS256 + claims de audiencia.
"""
from __future__ import annotations
import asyncio
import logging
import time
import uuid
import httpx
from dataclasses import dataclass, field
from jose import jwt, JWTError
from fastapi import Depends, HTTPException, Request, status
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, text
from sqlalchemy.orm import selectinload
from .config import settings
from .database import get_db

logger = logging.getLogger("ades.security")

_bearer = HTTPBearer(auto_error=True)

# URL interna de Authentik dentro de la red Docker — evita hairpin NAT y SSL overhead
_AUTHENTIK_INTERNAL = "http://ades-authentik-server:9000"

# TTL cache para JWKS — evita re-fetch en cada request
_JWKS_CACHE: dict = {}   # claves: "keys" → dict, "exp" → float (monotonic)
_JWKS_TTL   = 300         # 5 minutos
_JWKS_LOCK  = asyncio.Lock()


async def _get_jwks_uri() -> str:
    """Obtiene JWKS URI del discovery endpoint de Authentik (async)."""
    for base in (_AUTHENTIK_INTERNAL, settings.OIDC_ISSUER.split("/application/")[0]):
        try:
            slug = settings.OIDC_ISSUER.split("/application/o/")[-1].rstrip("/")
            disc_url = f"{base}/application/o/{slug}/.well-known/openid-configuration"
            async with httpx.AsyncClient(timeout=5.0) as client:
                resp = await client.get(disc_url)
            resp.raise_for_status()
            jwks_uri = resp.json()["jwks_uri"]
            # Reemplazar host externo por interno para la descarga de claves
            jwks_internal = jwks_uri.replace(
                settings.OIDC_ISSUER.split("/application/")[0],
                _AUTHENTIK_INTERNAL,
            )
            logger.info("JWKS URI: %s", jwks_internal)
            return jwks_internal
        except Exception as e:
            logger.warning("No se pudo obtener JWKS URI desde %s: %s", base, e)
    raise RuntimeError("No se pudo obtener el JWKS URI de Authentik")


async def _fetch_jwks() -> dict:
    """Descarga JWKS con TTL cache de 5 minutos (async, thread-safe con asyncio.Lock)."""
    now = time.monotonic()
    async with _JWKS_LOCK:
        if _JWKS_CACHE.get("exp", 0.0) > now:
            return _JWKS_CACHE["keys"]
        uri = await _get_jwks_uri()
        async with httpx.AsyncClient(timeout=10.0) as client:
            resp = await client.get(uri)
        resp.raise_for_status()
        keys = resp.json()
        _JWKS_CACHE["keys"] = keys
        _JWKS_CACHE["exp"]  = now + _JWKS_TTL
        logger.info("JWKS actualizado, expira en %ds", _JWKS_TTL)
        return keys


async def verify_token(token: str) -> dict:
    try:
        jwks = await _fetch_jwks()
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
    return await verify_token(creds.credentials)


# ── ADES User context (enriquecido con scope RBAC) ────────────────────────────

@dataclass
class AdesUser:
    """Contexto del usuario ADES — incluye scope de plantel/nivel para RBAC."""
    id: uuid.UUID
    nombre_usuario: str
    email: str
    rol: str
    nivel_acceso: int
    plantel_id: uuid.UUID | None = field(default=None)
    nivel_educativo_id: uuid.UUID | None = field(default=None)
    persona_id: uuid.UUID | None = field(default=None)
    oidc_sub: str = field(default="")
    rol_id: uuid.UUID | None = field(default=None)
    privilegios: list[str] = field(default_factory=list)
    todos_roles: list[uuid.UUID] = field(default_factory=list)

    def tiene_privilegio(self, codigo: str) -> bool:
        """Verifica si el usuario tiene un privilegio específico."""
        return codigo in self.privilegios

    @property
    def es_admin_global(self) -> bool:
        return self.plantel_id is None

    @property
    def es_admin_plantel(self) -> bool:
        return self.plantel_id is not None and self.nivel_educativo_id is None

    @property
    def tiene_scope_nivel(self) -> bool:
        return self.plantel_id is not None and self.nivel_educativo_id is not None

    @property
    def usuario_id(self) -> uuid.UUID:
        """Alias de 'id' para compatibilidad retroactiva."""
        return self.id

    def apply_plantel_filter(self, query, plantel_col):
        """Aplica filtro de plantel si el usuario no es ADMIN_GLOBAL."""
        if self.plantel_id is not None:
            return query.where(plantel_col == self.plantel_id)
        return query


async def get_ades_user(
    request: Request,
    token_payload: dict = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> "AdesUser":
    # Import lazy para evitar circular
    from app.models.personas import Usuario

    sub   = token_payload.get("sub", "")
    email = token_payload.get("email", "")

    q = (
        select(Usuario)
        .options(selectinload(Usuario.rol))
        .where(
            (Usuario.oidc_sub == sub) | (Usuario.email_institucional == email)
        )
    )
    usuario = (await db.execute(q)).scalar_one_or_none()
    if not usuario:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Usuario no registrado en ADES. Contacta al administrador.",
        )

    auth_groups = token_payload.get("groups", []) or token_payload.get("roles", [])
    todos_roles_db = []
    privilegios_lista = []

    if auth_groups:
        # 1. Obtener los IDs de los roles correspondientes en Authentik
        roles_query = text("SELECT id, nombre_rol, nivel_acceso FROM ades_roles WHERE nombre_rol = ANY(:groups)")
        roles_result = await db.execute(roles_query, {"groups": auth_groups})
        roles_db = roles_result.fetchall()
        roles_ids = [r.id for r in roles_db]

        # 2. Verificar discrepancias en ades_usuario_roles
        current_roles_query = text("SELECT rol_id FROM ades_usuario_roles WHERE usuario_id = :uid")
        current_roles_result = await db.execute(current_roles_query, {"uid": usuario.id})
        current_roles = [r.rol_id for r in current_roles_result]

        if set(roles_ids) != set(current_roles):
            # Mismatch detectado -> Sync JIT
            await db.execute(text("DELETE FROM ades_usuario_roles WHERE usuario_id = :uid"), {"uid": usuario.id})
            if roles_ids:
                insert_query = text("INSERT INTO ades_usuario_roles (usuario_id, rol_id, peso) VALUES (:uid, :rid, 100)")
                for rid in roles_ids:
                    await db.execute(insert_query, {"uid": usuario.id, "rid": rid})
                
                # Actualizar el rol principal en ades_usuarios (retrocompatibilidad)
                mejor_rol = min(roles_db, key=lambda x: x.nivel_acceso)
                if usuario.rol_id != mejor_rol.id:
                    await db.execute(text("UPDATE ades_usuarios SET rol_id = :rid WHERE id = :uid"), 
                                   {"rid": mejor_rol.id, "uid": usuario.id})
            await db.commit()
            todos_roles_db = roles_ids
        else:
            todos_roles_db = current_roles

    # 3. Fetch Privilegios
    priv_query = text("""
        SELECT DISTINCT p.codigo 
        FROM ades_privilegios p
        JOIN ades_rol_privilegios rp ON rp.privilegio_id = p.id
        JOIN ades_usuario_roles ur ON ur.rol_id = rp.rol_id
        WHERE ur.usuario_id = :uid AND p.is_active = TRUE
    """)
    priv_result = await db.execute(priv_query, {"uid": usuario.id})
    privilegios_lista = [row.codigo for row in priv_result]

    ades_user = AdesUser(
        id=usuario.id,
        nombre_usuario=usuario.nombre_usuario,
        email=usuario.email_institucional,
        rol=usuario.rol.nombre_rol if usuario.rol else "",
        nivel_acceso=usuario.rol.nivel_acceso if usuario.rol else 99,
        plantel_id=usuario.plantel_id,
        nivel_educativo_id=usuario.nivel_educativo_id,
        persona_id=usuario.persona_id,
        oidc_sub=usuario.oidc_sub or "",
        rol_id=usuario.rol_id,
        privilegios=privilegios_lista,
        todos_roles=todos_roles_db,
    )
    # Registrar actividad en Valkey para telemetría de sesiones concurrentes (TTL 15 minutos)
    try:
        import redis.asyncio as aioredis
        r = await aioredis.from_url(settings.VALKEY_URL)
        await r.setex(f"ades:session:{usuario.id}", 900, usuario.nombre_usuario)
        await r.aclose()
    except Exception as e:
        logger.warning("No se pudo registrar sesion en Valkey: %s", e)

    # Propagar al request.state para que AuditMiddleware lo use sin re-decodificar JWT
    request.state.ades_user_id = str(usuario.id)
    request.state.ades_user_nombre = usuario.nombre_usuario
    return ades_user


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
