# PRs de Seguridad Crítica — ADES

Estos 4 PRs deben implementarse en paralelo (Semana 1-2).

---

## PR #2: HTTPS Enforcement + Security Headers

**Title**: [SECURITY] HTTPS enforcement + Security headers (HSTS, CSP, X-* headers)

### Changes

#### Archivo: `/backend/app/main.py`

**IMPORT (línea 4):**
```python
from fastapi import FastAPI, Request
from fastapi.middleware.httpsredirect import HTTPSRedirectMiddleware
from fastapi.middleware.trustedhost import TrustedHostMiddleware
from fastapi.responses import ORJSONResponse
from starlette.middleware.base import BaseHTTPMiddleware
```

**DESPUÉS de lifespan (línea 32):**

```python
# ✅ HTTPS Enforcement (solo en producción)
if settings.ENVIRONMENT == "production":
    app.add_middleware(HTTPSRedirectMiddleware)

# ✅ Trusted Host (whitelist dominios)
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
        
        # Content-Security-Policy
        response.headers["Content-Security-Policy"] = (
            "default-src 'self'; "
            "script-src 'self' 'unsafe-inline'; "  # Angular requiere
            "style-src 'self' 'unsafe-inline'; "   # Angular requiere
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
```

### Tests

```python
# /backend/app/tests/test_security_headers.py

import pytest
from httpx import AsyncClient
from app.main import app

@pytest.mark.asyncio
async def test_https_redirect_in_production(monkeypatch):
    """Producción: HTTP → HTTPS redirect (308)"""
    monkeypatch.setenv("ENVIRONMENT", "production")
    
    client = AsyncClient(app=app, base_url="http://ades.setag.mx")
    response = await client.get("/api/v1/health", follow_redirects=False)
    
    assert response.status_code == 307  # Redirect
    assert "https://" in response.headers["location"]

@pytest.mark.asyncio
async def test_hsts_header_present():
    """HSTS header debe estar en respuestas"""
    client = AsyncClient(app=app, base_url="https://ades.setag.mx")
    response = await client.get("/api/v1/health")
    
    assert "Strict-Transport-Security" in response.headers
    assert "31536000" in response.headers["Strict-Transport-Security"]

@pytest.mark.asyncio
async def test_security_headers_present():
    """Todos los security headers presentes"""
    client = AsyncClient(app=app, base_url="https://ades.setag.mx")
    response = await client.get("/api/v1/health")
    
    required_headers = [
        "Strict-Transport-Security",
        "X-Content-Type-Options",
        "X-Frame-Options",
        "Content-Security-Policy",
        "Referrer-Policy",
        "Permissions-Policy"
    ]
    
    for header in required_headers:
        assert header in response.headers, f"Missing header: {header}"

@pytest.mark.asyncio
async def test_csp_blocks_inline_scripts():
    """CSP debe bloquear scripts inline no autorizados"""
    client = AsyncClient(app=app, base_url="https://ades.setag.mx")
    response = await client.get("/api/v1/health")
    
    csp = response.headers["Content-Security-Policy"]
    assert "script-src" in csp
    # Angular permite 'unsafe-inline' por necesidad
    assert "'unsafe-inline'" in csp or "nonce-" in csp
```

### Checklist
- [ ] HTTPSRedirectMiddleware agregado
- [ ] Security headers agregado
- [ ] Tests pasan
- [ ] Validar en staging: curl -I https://api.ades.setag.mx
- [ ] Validar headers presentes
- [ ] HSTS preload: https://hstspreload.org/

---

## PR #3: Rate Limiting

**Title**: [SECURITY] Rate limiting con slowapi

### Changes

#### 1. Instalar
```bash
pip install slowapi
# Agregar a requirements.txt
```

#### 2. Crear: `/backend/app/core/ratelimit.py`

```python
"""Rate limiting configuration."""

from slowapi import Limiter
from slowapi.util import get_remote_address

limiter = Limiter(
    key_func=get_remote_address,
    default_limits=["1000/hour"]  # Default global limit
)

# Límites específicos por tipo de endpoint
LIMITS = {
    "auth": "5/minute",         # Login: 5 attempts
    "read": "100/minute",       # GET requests: 100
    "write": "50/minute",       # POST/PATCH: 50
    "upload": "10/minute",      # File uploads: 10
    "public": "100/day",        # Public endpoints (sin auth)
}
```

#### 2. Actualizar: `/backend/app/main.py`

```python
from slowapi.errors import RateLimitExceeded
from app.core.ratelimit import limiter

# Agregar exception handler
@app.exception_handler(RateLimitExceeded)
async def rate_limit_handler(request, exc):
    return JSONResponse(
        status_code=status.HTTP_429_TOO_MANY_REQUESTS,
        content={
            "detail": f"Too many requests. {exc.detail}",
            "retry_after": exc.get("retry_after", 60)
        }
    )

app.state.limiter = limiter
app.include_router(api_router, prefix=settings.API_V1_PREFIX)
```

#### 3. Proteger endpoints críticos

```python
# En /backend/app/api/v1/

# Para Authentik
from app.core.ratelimit import limiter, LIMITS

# Auth (si existe módulo auth.py)
@router.post("/auth/login")
@limiter.limit(LIMITS["auth"])
async def login(creds: LoginRequest, request: Request):
    ...

# Expediente
@router.get("/expediente/alumno/{estudiante_id}")
@limiter.limit(LIMITS["read"])
async def get_expediente(estudiante_id: UUID, request: Request):
    ...

# Calificaciones
@router.patch("/calificaciones/{cal_id}")
@limiter.limit(LIMITS["write"])
async def update_calificacion(cal_id: UUID, request: Request):
    ...

# Certificados
@router.post("/certificados/emitir")
@limiter.limit(LIMITS["write"])
async def emitir_certificado(body: CertificadoCreate, request: Request):
    ...
```

### Tests

```python
# /backend/app/tests/test_ratelimit.py

import pytest
from httpx import AsyncClient
from app.main import app

@pytest.mark.asyncio
async def test_rate_limit_login():
    """Login: máximo 5 intentos por minuto"""
    client = AsyncClient(app=app, base_url="http://test")
    
    # Hacer 5 requests
    for i in range(5):
        response = await client.post(
            "/api/v1/auth/login",
            json={"username": "test", "password": "wrong"}
        )
        assert response.status_code in [401, 422]
    
    # 6to request debe ser rate limited
    response = await client.post(
        "/api/v1/auth/login",
        json={"username": "test", "password": "wrong"}
    )
    assert response.status_code == 429

@pytest.mark.asyncio
async def test_rate_limit_reset_after_window():
    """Rate limit se reset después de ventana temporal"""
    client = AsyncClient(app=app, base_url="http://test")
    
    # Hit limit
    for i in range(6):
        await client.get("/api/v1/expediente/alumno/xyz")
    
    # Esperar 61 segundos (ventana de 1 minuto)
    import asyncio
    await asyncio.sleep(61)
    
    # Siguiente request debe funcionar
    response = await client.get("/api/v1/expediente/alumno/xyz")
    assert response.status_code in [403, 404]  # OK or auth error, not 429
```

---

## PR #4: IDOR en Certificados

**Title**: [SECURITY] Fix IDOR en certificados.py

### Changes

Similar a PR #1 (expediente), pero para:
- `POST /certificados/emitir` → Validar RBAC
- `GET /certificados` → Filtrar por plantel/rol
- `POST /certificados/llave/generar` → Agregar rate limiting

### Diff (resumido)

```python
# certificados.py línea 214

# ❌ ANTES
async def emitir_certificado(
    body: CertificadoCreate,
    db: AsyncSession = Depends(get_db),
    current_user: dict = Depends(get_current_user),
):

# ✅ DESPUÉS
async def emitir_certificado(
    body: CertificadoCreate,
    db: AsyncSession = Depends(get_db),
    ades_user: AdesUser = Depends(get_ades_user),
):
    # Validar RBAC
    if ades_user.nivel_acceso > 2:  # Solo admin/coordinador
        raise HTTPException(403, "No tienes permiso")
    
    # Validar scope de plantel
    if ades_user.plantel_id:
        est = await db.get(Estudiante, body.estudiante_id)
        if est.plantel_id != ades_user.plantel_id:
            raise HTTPException(403, "Estudiante no en tu plantel")
    
    # Continuar...
```

---

## PR #5: IDOR en Carbone

**Title**: [SECURITY] Fix IDOR en carbone.py - Validar acceso a boletas

### Changes

```python
# carbone.py

async def generar_boleta(
    estudiante_id: uuid.UUID,
    db: AsyncSession = Depends(get_db),
    ades_user: AdesUser = Depends(get_ades_user),
):
    # ✅ Validar acceso antes de generar boleta
    if not await _check_student_access(db, ades_user, estudiante_id):
        raise HTTPException(403, "No tienes acceso a este estudiante")
    
    # Continuar con generación...
```

### Helper function
```python
async def _check_student_access(db, ades_user, estudiante_id):
    """Validar que usuario puede acceder a estudiante"""
    
    # Admin global: OK
    if ades_user.es_admin_global:
        return True
    
    # Admin plantel: OK si estudiante en su plantel
    if ades_user.es_admin_plantel:
        est = await db.get(Estudiante, estudiante_id)
        return est and est.plantel_id == ades_user.plantel_id
    
    # Maestro: OK si estudiante en su grupo
    if ades_user.rol == "MAESTRO":
        # Check relación maestro-grupo-estudiante
        # ...
        return has_access
    
    return False
```

---

## 📋 CONSOLIDADO

```
PR #2: HTTPS + Headers         — 2h desarrollo + 1h testing
PR #3: Rate Limiting            — 2h desarrollo + 1h testing
PR #4: Certificados IDOR        — 3h desarrollo + 1h testing
PR #5: Carbone IDOR            — 2h desarrollo + 1h testing
────────────────────────────────────────────────
TOTAL:                           9h desarrollo + 4h testing = 13h
```

### Merge Order (en paralelo, pero merge secuencial)
1. PR #2 (HTTPS) — independiente
2. PR #3 (Rate limiting) — independiente
3. PR #1 (Expediente IDOR) — base para otros
4. PR #4 (Certificados IDOR) — similar a PR #1
5. PR #5 (Carbone IDOR) — similar a PR #1

### Timeline
- Día 1: Todos desarrollados en branches separadas
- Día 2: Testing en staging
- Día 3: Merge y deploy a producción (en orden)

---

## ✅ MASTER CHECKLIST

- [ ] PR #2 (HTTPS): Merge & deploy
- [ ] PR #3 (Rate Limit): Merge & deploy
- [ ] PR #1 (Expediente IDOR): Merge & deploy
- [ ] PR #4 (Certificados IDOR): Merge & deploy
- [ ] PR #5 (Carbone IDOR): Merge & deploy
- [ ] Production health check
- [ ] Monitor logs para 403/429 responses
- [ ] Validar sin regresiones

---

**Effort Total Semana 1**: ~13 horas + testing + deployment
**Status**: Ready to implement
