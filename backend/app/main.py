from contextlib import asynccontextmanager
import structlog
from fastapi import FastAPI, Request, status
from fastapi.middleware.cors import CORSMiddleware
from fastapi.middleware.httpsredirect import HTTPSRedirectMiddleware
from fastapi.middleware.trustedhost import TrustedHostMiddleware
from fastapi.responses import ORJSONResponse, JSONResponse
from starlette.middleware.base import BaseHTTPMiddleware

from app.core.config import settings
from app.core.audit import AuditMiddleware
from app.core.metrics import setup_metrics
from app.core.ratelimit import limiter
from slowapi.errors import RateLimitExceeded
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

# ✅ HTTPS Enforcement (solo en producción)
if settings.ENVIRONMENT == "production":
    app.add_middleware(HTTPSRedirectMiddleware)

# ✅ Trusted Host (whitelist de dominios)
if settings.ENVIRONMENT == "production":
    app.add_middleware(
        TrustedHostMiddleware,
        allowed_hosts=["ades.setag.mx", "api.ades.setag.mx", "*.ades.setag.mx"]
    )

# ✅ Security Headers Middleware
class SecurityHeadersMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request: Request, call_next):
        response = await call_next(request)

        # HSTS — Force HTTPS for 1 year
        response.headers["Strict-Transport-Security"] = (
            "max-age=31536000; includeSubDomains; preload"
        )

        # X-Content-Type-Options — Prevent MIME sniffing
        response.headers["X-Content-Type-Options"] = "nosniff"

        # X-Frame-Options — Prevent clickjacking
        response.headers["X-Frame-Options"] = "DENY"

        # X-XSS-Protection — Legacy XSS protection
        response.headers["X-XSS-Protection"] = "1; mode=block"

        # Content-Security-Policy — Prevent inline scripts
        response.headers["Content-Security-Policy"] = (
            "default-src 'self'; "
            "script-src 'self' 'unsafe-inline'; "
            "style-src 'self' 'unsafe-inline'; "
            "img-src 'self' data: https:; "
            "font-src 'self'; "
            "connect-src 'self' https://api.ades.setag.mx; "
            "frame-ancestors 'none'; "
            "form-action 'self'; "
            "base-uri 'self'; "
            "upgrade-insecure-requests"
        )

        # Referrer-Policy
        response.headers["Referrer-Policy"] = "strict-origin-when-cross-origin"

        # Permissions-Policy
        response.headers["Permissions-Policy"] = (
            "geolocation=(), microphone=(), camera=(), "
            "payment=(), usb=(), magnetometer=()"
        )

        return response

app.add_middleware(SecurityHeadersMiddleware)

# ✅ CORS (específico, no wildcard)
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.allowed_origins_list,
    allow_credentials=True,
    allow_methods=["GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"],
    allow_headers=["Content-Type", "Authorization"],
)

# FASE 15 — Auditoría: registra POST/PUT/PATCH/DELETE en ades_audit_log
app.add_middleware(AuditMiddleware)

# ✅ Rate Limiting exception handler
@app.exception_handler(RateLimitExceeded)
async def rate_limit_handler(request: Request, exc: RateLimitExceeded):
    return JSONResponse(
        status_code=status.HTTP_429_TOO_MANY_REQUESTS,
        content={
            "detail": f"Too many requests. {exc.detail}",
            "retry_after": 60,  # Esperar 60 segundos antes de reintentar
        }
    )

app.state.limiter = limiter
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
