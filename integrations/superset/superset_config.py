"""
Apache Superset — Configuración personalizada para ADES Instituto Nevadi.

Este archivo se monta en /app/pythonpath/superset_config.py dentro del contenedor.
Superset lo carga automáticamente al arrancar.

Datasource principal: PostgreSQL ADES (esquema ades_bi con vistas materializadas)
Auth: local (Authentik OIDC se configura manualmente en la UI tras el primer arranque)
"""
import os

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
    "CACHE_DEFAULT_TIMEOUT": 600,   # 10 min — los dashboards de BI no requieren tiempo real
    "CACHE_KEY_PREFIX": "superset_data_",
    "CACHE_REDIS_URL": REDIS_URL,
}

CELERY_CONFIG = {
    "broker_url": REDIS_URL,
    "result_backend": REDIS_URL,
    "worker_prefetch_multiplier": 1,
    "task_acks_late": False,
    "task_annotations": {
        "sql_lab.get_sql_results": {"rate_limit": "100/s"},
        "email_reports.send": {"rate_limit": "1/5s", "time_limit": 120, "soft_time_limit": 150},
    },
}

# ── Idioma y zona horaria ─────────────────────────────────────────────────────
BABEL_DEFAULT_LOCALE = "es"
BABEL_DEFAULT_TIMEZONE = "America/Mexico_City"

# ── Feature flags ─────────────────────────────────────────────────────────────
FEATURE_FLAGS = {
    "ENABLE_TEMPLATE_PROCESSING": True,     # Permite usar Jinja2 en SQL
    "DASHBOARD_NATIVE_FILTERS": True,
    "DASHBOARD_CROSS_FILTERS": True,
    "DRILL_TO_DETAIL": True,
    "DRILL_BY": True,
    "HORIZONTAL_FILTER_BAR": True,
    "CHART_PLUGINS_EXPERIMENTAL": False,
}

# ── Seguridad ─────────────────────────────────────────────────────────────────
# Permitir iframe embebido (para el componente Angular de BI)
HTTP_HEADERS = {"X-Frame-Options": "SAMEORIGIN"}

WTF_CSRF_ENABLED = True
WTF_CSRF_EXEMPT_LIST = []
WTF_CSRF_TIME_LIMIT = 60 * 60 * 24 * 365

# Dominios de confianza (ajustar con el dominio real)
CORS_OPTIONS = {
    "supports_credentials": True,
    "allow_headers": ["*"],
    "resources": ["/api/*"],
    "origins": ["https://ades.setag.mx", "http://localhost:4200"],
}

# ── Datasources adicionales ───────────────────────────────────────────────────
# La conexión a PostgreSQL ADES se agrega desde la UI:
#   Database → + → PostgreSQL
#   Host: ades-postgres  Port: 5432  DB: ades  User: superset_ro
#   Exponer los schemas: ades_bi, public
#
# Esquema recomendado para exploración: ades_bi
# Vistas disponibles:
#   mv_asistencia_diaria       — asistencia por grupo y día
#   mv_calificaciones_grupo    — promedios por materia, grupo y periodo
#   mv_riesgo_academico        — riesgo alto/medio/bajo por alumno
#   mv_resumen_plantel         — KPIs ejecutivos por plantel y nivel
#   mv_cobertura_curricular    — % cobertura del plan de estudios

# ── Límites de consulta ───────────────────────────────────────────────────────
ROW_LIMIT = 50_000
SQL_MAX_ROW = 100_000
VIZ_ROW_LIMIT = 10_000

# ── Inicio limpio ─────────────────────────────────────────────────────────────
# Crear usuario admin en primer arranque:
#   docker compose exec superset superset fab create-admin \
#       --username admin --firstname Admin --lastname ADES \
#       --email admin@institutonevadi.edu.mx --password <PASSWORD>
#   docker compose exec superset superset db upgrade
#   docker compose exec superset superset init
