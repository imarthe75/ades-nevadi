"""Configuración centralizada de la aplicación ADES via variables de entorno.

Carga secretos desde HashiCorp Vault (si está disponible) antes de inicializar
``pydantic_settings.BaseSettings``, de modo que las variables Docker siempre
tienen precedencia sobre los valores de Vault (``os.environ.setdefault``).

El objeto ``settings`` es un singleton importado por todos los módulos; modificar
valores en tiempo de ejecución fuera de tests no está soportado.
"""
import os

# Inject Vault secrets into environment variables before initializing BaseSettings.
# Use setdefault so Docker container env vars (VAULT_ADDR, DATABASE_URL, etc.)
# always take precedence over Vault values.
try:
    from app.core.vault import get_vault_client
    client = get_vault_client()
    if client:
        try:
            read_response = client.secrets.kv.v2.read_secret_version(
                path="ades",
                mount_point="secret"
            )
            secrets_data = read_response.get("data", {}).get("data", {})
            for k, v in secrets_data.items():
                if v is not None:
                    os.environ.setdefault(k, str(v))
        except Exception:
            pass
except Exception:
    pass

from pydantic_settings import BaseSettings, SettingsConfigDict
from pydantic import AnyHttpUrl, field_validator, model_validator
from typing import Literal


class Settings(BaseSettings):
    """Configuración de la aplicación ADES cargada desde variables de entorno / .env.

    Todas las variables con valor por defecto son opcionales; las que no tienen
    valor por defecto (``DATABASE_URL``, ``SECRET_KEY``, ``VALKEY_URL``) son
    obligatorias y la app fallará al arrancar si no están definidas.

    En ``ENVIRONMENT=production`` se validan adicionalmente los secretos críticos
    (``ADES_INTERNAL_API_KEY``, ``OIDC_CLIENT_SECRET``, ``MINIO_SECRET_KEY``,
    ``NTFY_ADMIN_TOKEN``).
    """
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    # Aplicación
    APP_NAME: str = "ADES API"
    ENVIRONMENT: Literal["development", "production"] = "development"
    LOG_LEVEL: str = "info"
    API_V1_PREFIX: str = "/api/v1"
    BASE_URL: str = "https://ades.setag.mx"

    # Base de datos
    DATABASE_URL: str
    DATABASE_URL_SYNC: str = ""

    # Valkey / Celery
    VALKEY_URL: str
    CELERY_BROKER_URL: str = ""
    CELERY_RESULT_URL: str = ""

    # MinIO
    MINIO_ENDPOINT: str = "ades-minio:9000"
    MINIO_ACCESS_KEY: str = ""
    MINIO_SECRET_KEY: str = ""
    MINIO_SECURE: bool = False
    MINIO_BUCKET: str = "ades-archivos"

    # OIDC — Authentik
    OIDC_ISSUER: str = "https://auth.ades.setag.mx/application/o/ades-frontend/"
    OIDC_CLIENT_ID: str = "ades-frontend"
    OIDC_CLIENT_SECRET: str = ""

    # JWT
    SECRET_KEY: str
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 60
    DATABASE_ENCRYPTION_KEY: str = ""

    # CORS
    ALLOWED_ORIGINS: str = "http://localhost:4200,https://ades.setag.mx"

    # Carbone — generador de reportes PDF (FASE 18)
    CARBONE_URL: str = "http://ades-carbone:3000"

    # ntfy — push notifications (FASE 20)
    NTFY_URL: str = "http://ades-ntfy:80"
    NTFY_ADMIN_TOKEN: str = ""

    # Stirling-PDF — procesamiento PDF (FASE 21)
    STIRLING_PDF_URL: str = "http://ades-stirling-pdf:8080"

    # n8n — automatización de flujos (FASE 23)
    N8N_URL: str = "http://ades-n8n:5678"
    N8N_WEBHOOK_URL: str = "http://ades-n8n:5678"
    ADES_INTERNAL_API_KEY: str = ""

    # Flowise — AI chatbot flows (FASE 17)
    FLOWISE_URL: str = "http://ades-flowise:3000"
    FLOWISE_API_KEY: str = ""
    FLOWISE_CHATFLOW_ID: str = ""

    # Polygon PoS Blockchain (FASE 5 Etapa B)
    POLYGON_RPC_URL: str = "MOCK"
    POLYGON_PRIVATE_KEY: str = ""
    POLYGON_CONTRACT_ADDRESS: str = ""

    # OpenAI / NVIDIA NIM (FASE 4 / FASE 27)
    OPENAI_BASE_URL: str = "https://integrate.api.nvidia.com/v1"
    OPENAI_API_KEY: str = ""
    OPENAI_MODEL: str = "meta/llama-3.1-70b-instruct"
    ANTHROPIC_API_KEY: str = ""

    # BigBlueButton — Videoconferencias Institucionales (FASE 26)
    BBB_SERVER_URL: str = ""          # e.g. https://bbb.institutonevadi.edu.mx/bigbluebutton
    BBB_SHARED_SECRET: str = ""       # Shared secret del servidor BBB

    # Paperless-ngx — Gestión Documental OCR (FASE 28)
    PAPERLESS_URL: str = "http://ades-paperless:8000"
    PAPERLESS_API_TOKEN: str = ""       # Se inyecta desde Vault (path: secret/ades -> PAPERLESS_API_TOKEN)
    PAPERLESS_DEFAULT_OWNER: int = 1    # ID del usuario admin de Paperless

    # Apache Superset — integración embebida (FASE 16)
    SUPERSET_URL: str = "http://ades-superset:8088"
    SUPERSET_ADMIN_USER: str = "admin"
    SUPERSET_ADMIN_PASSWORD: str = ""
    # UUIDs de dashboards (se llenan tras crearlos en la UI de Superset)
    SUPERSET_DASHBOARD_INSTITUTO: str = ""
    SUPERSET_DASHBOARD_PLANTEL: str = ""
    SUPERSET_DASHBOARD_DOCENTE: str = ""
    SUPERSET_DASHBOARD_ALUMNO: str = ""

    @field_validator("DATABASE_URL_SYNC", mode="before")
    @classmethod
    def set_sync_url(cls, v: str, info) -> str:
        """Deriva DATABASE_URL_SYNC de DATABASE_URL si no está definida explícitamente.

        Reemplaza el driver ``+asyncpg`` por el driver síncrono psycopg2 para
        uso en tareas Celery y scripts de migración que no soportan async.
        """
        if not v:
            return info.data.get("DATABASE_URL", "").replace("+asyncpg", "")
        return v

    @field_validator("CELERY_BROKER_URL", "CELERY_RESULT_URL", mode="before")
    @classmethod
    def set_celery_urls(cls, v: str, info) -> str:
        """Usa VALKEY_URL como broker/backend de Celery si no están configurados."""
        return v or info.data.get("VALKEY_URL", "")

    @model_validator(mode="after")
    def check_production_secrets(self) -> "Settings":
        """Valida que los secretos críticos estén presentes en entorno de producción.

        Raises:
            ValueError: Si alguno de los secretos requeridos está vacío en producción.
        """
        if self.ENVIRONMENT == "production":
            missing = [
                name for name, val in [
                    ("ADES_INTERNAL_API_KEY", self.ADES_INTERNAL_API_KEY),
                    ("OIDC_CLIENT_SECRET",    self.OIDC_CLIENT_SECRET),
                    ("MINIO_SECRET_KEY",      self.MINIO_SECRET_KEY),
                    ("NTFY_ADMIN_TOKEN",      self.NTFY_ADMIN_TOKEN),
                ]
                if not val
            ]
            if missing:
                raise ValueError(
                    f"Secretos requeridos en producción no configurados: {', '.join(missing)}"
                )
        return self

    @property
    def allowed_origins_list(self) -> list[str]:
        """Devuelve ALLOWED_ORIGINS como lista de strings sin espacios."""
        return [o.strip() for o in self.ALLOWED_ORIGINS.split(",") if o.strip()]


settings = Settings()
