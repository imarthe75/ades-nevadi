"""
Apache Superset — Configuración personalizada para ADES Instituto Nevadi.

Este archivo se monta en /app/pythonpath/superset_config.py dentro del contenedor.
Superset lo carga automáticamente al arrancar.

Datasource principal: PostgreSQL ADES (esquema ades_bi con vistas materializadas)
Auth: OIDC con Authentik (mismo IdP que el resto del sistema)
"""
import os
import logging

from flask_appbuilder.security.manager import AUTH_OAUTH

log = logging.getLogger(__name__)

# ── Secreto ───────────────────────────────────────────────────────────────────
SECRET_KEY = os.environ.get("SUPERSET_SECRET_KEY", "CHANGE_ME_IN_PRODUCTION")

# ── Base de datos de metadatos de Superset ────────────────────────────────────
SQLALCHEMY_DATABASE_URI = os.environ.get(
    "SUPERSET_DATABASE_URI",
    "postgresql+psycopg2://ades_admin:changeme@ades-postgres:5432/superset",
)

# ── Caché (Valkey/Redis) ──────────────────────────────────────────────────────
REDIS_URL = os.environ.get("REDIS_URL", "redis://ades-valkey:6379/2")

CACHE_CONFIG = {
    "CACHE_TYPE": "RedisCache",
    "CACHE_DEFAULT_TIMEOUT": 300,
    "CACHE_KEY_PREFIX": "superset_",
    "CACHE_REDIS_URL": REDIS_URL,
}

DATA_CACHE_CONFIG = {
    "CACHE_TYPE": "RedisCache",
    "CACHE_DEFAULT_TIMEOUT": 600,
    "CACHE_KEY_PREFIX": "superset_data_",
    "CACHE_REDIS_URL": REDIS_URL,
}

FILTER_STATE_CACHE_CONFIG = {
    "CACHE_TYPE": "RedisCache",
    "CACHE_DEFAULT_TIMEOUT": 600,
    "CACHE_KEY_PREFIX": "superset_filter_",
    "CACHE_REDIS_URL": REDIS_URL,
}

EXPLORE_FORM_DATA_CACHE_CONFIG = {
    "CACHE_TYPE": "RedisCache",
    "CACHE_DEFAULT_TIMEOUT": 300,
    "CACHE_KEY_PREFIX": "superset_explore_",
    "CACHE_REDIS_URL": REDIS_URL,
}

CELERY_CONFIG = {
    "broker_url": REDIS_URL,
    "result_backend": REDIS_URL,
    "worker_prefetch_multiplier": 1,
    "task_acks_late": False,
    "task_annotations": {
        "sql_lab.get_sql_results": {"rate_limit": "100/s"},
    },
}

# ── Idioma y zona horaria ─────────────────────────────────────────────────────
BABEL_DEFAULT_LOCALE = "es"
BABEL_DEFAULT_TIMEZONE = "America/Mexico_City"

# ── Autenticación OIDC con Authentik ─────────────────────────────────────────
_OIDC_ISSUER = os.environ.get(
    "OIDC_ISSUER",
    "https://auth.ades.setag.mx/application/o/superset",
).rstrip("/")

AUTH_TYPE = AUTH_OAUTH
AUTH_USER_REGISTRATION = True
AUTH_USER_REGISTRATION_ROLE = "Gamma"

OAUTH_PROVIDERS = [
    {
        "name": "oidc",
        "icon": "fa-openid",
        "token_key": "access_token",
        "remote_app": {
            "client_id": os.environ.get("OIDC_CLIENT_ID", "superset"),
            "client_secret": os.environ.get("OIDC_CLIENT_SECRET", ""),
            "server_metadata_url": f"{_OIDC_ISSUER}/.well-known/openid-configuration",
            "client_kwargs": {
                "scope": "openid profile email",
                "token_endpoint_auth_method": "client_secret_post",
            },
            "api_base_url": f"{_OIDC_ISSUER}/",
            "access_token_url": f"{_OIDC_ISSUER}/token/",
            "authorize_url": f"{_OIDC_ISSUER}/authorize/",
            "jwks_uri": f"{_OIDC_ISSUER}/jwks/",
        },
    }
]

# Custom Security Manager con mapeo de roles ADES → Superset
from custom_sso_security_manager import AdesSecurityManager  # noqa: E402
CUSTOM_SECURITY_MANAGER = AdesSecurityManager

# ── Feature flags ─────────────────────────────────────────────────────────────
FEATURE_FLAGS = {
    "ENABLE_TEMPLATE_PROCESSING": True,
    "DASHBOARD_NATIVE_FILTERS": True,
    "DASHBOARD_CROSS_FILTERS": True,
    "DRILL_TO_DETAIL": True,
    "DRILL_BY": True,
    "HORIZONTAL_FILTER_BAR": True,
    "EMBEDDED_SUPERSET": True,            # habilita el SDK embebido (guest tokens)
    "CHART_PLUGINS_EXPERIMENTAL": False,
}

# ── Embedded Superset — dominios permitidos ───────────────────────────────────
# El frontend Angular embebe dashboards vía guest token. Listar los dominios.
GUEST_ROLE_NAME = "Public"
GUEST_TOKEN_JWT_SECRET = os.environ.get("SUPERSET_SECRET_KEY", "CHANGE_ME_IN_PRODUCTION")
GUEST_TOKEN_JWT_ALGO = "HS256"
GUEST_TOKEN_HEADER_NAME = "X-GuestToken"
GUEST_TOKEN_JWT_EXP_SECONDS = 300  # 5 minutos

TALISMAN_ENABLED = False  # Desactivado para poder embeber en iframes desde ades.setag.mx
ENABLE_PROXY_FIX = True  # Habilitado para leer cabeceras X-Forwarded-Proto y generar HTTPS URLs

# Permitir iframe desde el dominio del frontend
HTTP_HEADERS = {"X-Frame-Options": "ALLOWALL"}  # se restringe por CORS_OPTIONS

# ── Seguridad / CORS ──────────────────────────────────────────────────────────
CORS_OPTIONS = {
    "supports_credentials": True,
    "allow_headers": ["*"],
    "resources": ["/api/*", "/superset/embedded/*"],
    "origins": [
        "https://ades.setag.mx",
        "http://localhost:4200",
    ],
}

WTF_CSRF_ENABLED = True
WTF_CSRF_EXEMPT_LIST = ["superset.views.core.log"]
WTF_CSRF_TIME_LIMIT = 60 * 60 * 24 * 365

# ── Límites de consulta ───────────────────────────────────────────────────────
ROW_LIMIT = 50_000
SQL_MAX_ROW = 100_000
VIZ_ROW_LIMIT = 10_000

# ── Datasource — conexión de solo lectura al schema ades_bi ──────────────────
# La conexión se agrega desde la UI (o vía script init):
#   Database → + → PostgreSQL
#   Host: ades-postgres  Port: 5432  DB: ades  User: superset_ro
#   Schemas expuestos: ades_bi, public
#
# Vistas disponibles en ades_bi:
#   mv_asistencia_diaria       — asistencia por grupo y día
#   mv_calificaciones_grupo    — promedios por materia, grupo y periodo
#   mv_riesgo_academico        — riesgo alto/medio/bajo por alumno
#   mv_resumen_plantel         — KPIs ejecutivos por plantel y nivel
#   mv_cobertura_curricular    — % cobertura del plan de estudios

# ── Inicio — comandos de primer arranque: ─────────────────────────────────────
#   docker compose exec superset superset db upgrade
#   docker compose exec superset superset init
#   docker compose exec superset superset fab create-admin \
#       --username admin --firstname Admin --lastname ADES \
#       --email admin@institutonevadi.edu.mx --password <PASSWORD>
#   Ver: infrastructure/superset/init.sh
