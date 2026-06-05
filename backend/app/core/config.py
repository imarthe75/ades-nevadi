from pydantic_settings import BaseSettings, SettingsConfigDict
from pydantic import AnyHttpUrl, field_validator
from typing import Literal


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    # Aplicación
    APP_NAME: str = "ADES API"
    ENVIRONMENT: Literal["development", "production"] = "development"
    LOG_LEVEL: str = "info"
    API_V1_PREFIX: str = "/api/v1"

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
        if not v:
            return info.data.get("DATABASE_URL", "").replace("+asyncpg", "")
        return v

    @field_validator("CELERY_BROKER_URL", "CELERY_RESULT_URL", mode="before")
    @classmethod
    def set_celery_urls(cls, v: str, info) -> str:
        return v or info.data.get("VALKEY_URL", "")

    @property
    def allowed_origins_list(self) -> list[str]:
        return [o.strip() for o in self.ALLOWED_ORIGINS.split(",") if o.strip()]


settings = Settings()
