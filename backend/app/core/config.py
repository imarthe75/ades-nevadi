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
