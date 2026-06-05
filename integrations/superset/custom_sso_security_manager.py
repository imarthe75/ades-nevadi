"""
Custom Security Manager para Apache Superset — ADES Instituto Nevadi.

Mapea los grupos/roles de Authentik (OIDC claims) a roles de Superset:
  ADMIN_GLOBAL                              → Admin
  ADMIN_PLANTEL · DIRECTOR · SUBDIRECTOR    → Alpha
  COORDINADOR_* · DOCENTE · SECRETARIA_*    → Gamma
  PADRE_FAMILIA · ALUMNO · (invitados)      → Public

Montaje en docker-compose:
  volumes:
    - ./integrations/superset/custom_sso_security_manager.py:/app/pythonpath/custom_sso_security_manager.py:ro
"""

import logging
from superset.security import SupersetSecurityManager

log = logging.getLogger(__name__)

# Mapeo: valor del claim "ades_rol" (o grupo Authentik) → rol Superset
_ROLE_MAP: dict[str, str] = {
    "ADMIN_GLOBAL": "Admin",
    "ADMIN_PLANTEL": "Alpha",
    "DIRECTOR": "Alpha",
    "SUBDIRECTOR": "Alpha",
    "COORDINADOR_ADMINISTRATIVO": "Alpha",
    "COORDINADOR_RH": "Alpha",
    "COORDINADOR_AREA": "Alpha",
    "COORDINADOR_ACADEMICO": "Gamma",
    "TUTOR": "Gamma",
    "ORIENTADOR": "Gamma",
    "SECRETARIA_ACADEMICA": "Gamma",
    "DOCENTE": "Gamma",
    "MEDICO_ESCOLAR": "Gamma",
    "PREFECTO": "Gamma",
    "APOYO_ACADEMICO": "Gamma",
    "APOYO_ADMINISTRATIVO": "Gamma",
    "ALUMNO": "Public",
    "PADRE_FAMILIA": "Public",
}
_DEFAULT_SUPERSET_ROLE = "Gamma"


class AdesSecurityManager(SupersetSecurityManager):
    """Extiende SupersetSecurityManager para mapear roles ADES → Superset."""

    def oauth_user_info(self, provider: str, response=None) -> dict:
        info = super().oauth_user_info(provider, response)
        if provider == "oidc" and response:
            me = self.appbuilder.sm.oauth_remoteapp.userinfo()
            info.update(
                {
                    "name": me.get("name", ""),
                    "email": me.get("email", ""),
                    "first_name": me.get("given_name", ""),
                    "last_name": me.get("family_name", ""),
                    "username": me.get("preferred_username", me.get("email", "")),
                    "ades_rol": me.get("ades_rol", ""),
                    "groups": me.get("groups", []),
                }
            )
        return info

    def auth_user_oauth(self, userinfo: dict):
        user = super().auth_user_oauth(userinfo)
        if user is None:
            return None

        # Determinar rol Superset desde el claim ades_rol (mapeado en Authentik)
        ades_rol = userinfo.get("ades_rol", "")
        superset_role_name = _ROLE_MAP.get(ades_rol, _DEFAULT_SUPERSET_ROLE)

        # Si el rol no coincide con el actual del usuario, actualizar
        desired_role = self.find_role(superset_role_name)
        if desired_role and desired_role not in user.roles:
            # Mantener solo el rol ADES; quitar roles anteriores de la lista ADES
            ades_roles = set(_ROLE_MAP.values())
            user.roles = [r for r in user.roles if r.name not in ades_roles]
            user.roles.append(desired_role)
            self.update_user(user)
            log.info("Usuario %s asignado al rol Superset: %s", user.username, superset_role_name)

        return user
