from contextlib import asynccontextmanager
import structlog
from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import ORJSONResponse

from app.core.config import settings
from app.core.audit import AuditMiddleware
from app.core.metrics import setup_metrics
import app.models  # noqa: F401 — registra todos los modelos en SQLAlchemy metadata
from app.api.v1.router import api_router

log = structlog.get_logger()


@asynccontextmanager
async def lifespan(app: FastAPI):
    log.info("ades_api.startup", env=settings.ENVIRONMENT)
    yield
    log.info("ades_api.shutdown")


app = FastAPI(
    title="ADES API — Instituto Nevadi",
    description="Sistema integral de administración escolar. FASE 1.",
    version="1.0.0",
    docs_url="/api/v1/docs",
    redoc_url="/api/v1/redoc",
    openapi_url="/api/v1/openapi.json",
    default_response_class=ORJSONResponse,
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.allowed_origins_list,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# FASE 15 — Auditoría: registra POST/PUT/PATCH/DELETE en ades_audit_log
app.add_middleware(AuditMiddleware)

app.include_router(api_router, prefix=settings.API_V1_PREFIX)

# FASE 22 — Prometheus metrics en /metrics
setup_metrics(app)


@app.middleware("http")
async def log_requests(request: Request, call_next):
    response = await call_next(request)
    log.info(
        "http_request",
        method=request.method,
        path=request.url.path,
        status=response.status_code,
    )
    return response
