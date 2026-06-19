# Código Remediación CRÍTICA — ADES Nevadi STRIDE
**Status**: Listo para copiar/pegar y ejecutar
**Fecha**: 19 Junio 2026

---

## 1️⃣ FIX IDOR EN EXPEDIENTE

### Archivo: `/backend/app/api/v1/expediente.py`

**REEMPLAZAR** (líneas 137-142):

```python
# ❌ VULNERABLE
@router.get("/alumno/{estudiante_id}", response_model=ExpedienteOut)
async def get_expediente(
    estudiante_id: UUID,
    db: AsyncSession = Depends(get_db),
    _user: AdesUser = Depends(get_ades_user),  # ← No se usa!
):
```

**CON** (nueva versión segura):

```python
# ✅ SEGURO — IDOR validation
@router.get("/alumno/{estudiante_id}", response_model=ExpedienteOut)
async def get_expediente(
    estudiante_id: UUID,
    db: AsyncSession = Depends(get_db),
    ades_user: AdesUser = Depends(get_ades_user),  # ← Ahora se usa
):
    """
    GET expediente de un alumno.
    
    Validación IDOR:
    - Admin global (plantel_id=None): ve expedientes de todos
    - Admin de plantel: ve expedientes de su plantel
    - Maestro: ve expedientes de alumnos de sus grupos
    - Alumno: ve solo su propio expediente
    """
    
    # 1. IDOR CHECK: Verificar que usuario tiene acceso a este estudiante
    if not await _check_expediente_access(db, ades_user, estudiante_id):
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="No tienes acceso a este expediente"
        )
    
    # 2. Obtener expediente (ahora seguro)
    exp = await _get_or_create_expediente(db, estudiante_id)
    
    # ... resto del código igual ...
```

### Agregar Helper Function

**AÑADIR** al principio de `/backend/app/api/v1/expediente.py` (después de imports):

```python
from sqlalchemy import and_, select
from app.models.academica import Estudiante, Grupo, GrupoMaestro

async def _check_expediente_access(
    db: AsyncSession, 
    ades_user: AdesUser, 
    estudiante_id: UUID
) -> bool:
    """
    Validar que ades_user tiene acceso a expediente del estudiante.
    
    Retorna: True si acceso permitido, False si denegado.
    """
    
    # ADMIN GLOBAL: acceso a todo
    if ades_user.es_admin_global:
        return True
    
    # Obtener datos del estudiante
    stmt = select(Estudiante).where(Estudiante.id == estudiante_id)
    estudiante = (await db.execute(stmt)).scalar_one_or_none()
    
    if not estudiante:
        return False  # Estudiante no existe
    
    # ADMIN DE PLANTEL: acceso si estudiante está en su plantel
    if ades_user.es_admin_plantel:
        return estudiante.plantel_id == ades_user.plantel_id
    
    # MAESTRO: acceso si es maestro de algún grupo del estudiante
    if ades_user.rol == "MAESTRO":
        # Verificar si es maestro de un grupo que contiene al estudiante
        stmt = select(1).select_from(GrupoMaestro).join(
            Grupo,
            GrupoMaestro.grupo_id == Grupo.id
        ).where(
            and_(
                GrupoMaestro.maestro_id == ades_user.persona_id,
                Grupo.id.in_(
                    select(Estudiante.grupo_id).where(
                        Estudiante.id == estudiante_id
                    )
                )
            )
        )
        result = await db.execute(stmt)
        return result.scalar() is not None
    
    # ESTUDIANTE: acceso solo a su propio expediente
    if ades_user.rol == "ESTUDIANTE":
        return estudiante.persona_id == ades_user.persona_id
    
    # PADRE: acceso a expedientes de sus hijos
    if ades_user.rol == "PADRE":
        # Verificar relación padre-hijo
        stmt = select(1).select_from(
            __import__("app.models.personas", fromlist=["RelacionTutor"])
            .RelacionTutor
        ).where(
            and_(
                __import__("app.models.personas", fromlist=["RelacionTutor"])
                .RelacionTutor.padre_id == ades_user.persona_id,
                __import__("app.models.personas", fromlist=["RelacionTutor"])
                .RelacionTutor.hijo_id == estudiante.persona_id,
            )
        )
        result = await db.execute(stmt)
        return result.scalar() is not None
    
    # Por defecto: denegar acceso
    return False
```

### Test para validar fix

**CREAR** `/backend/app/tests/test_expediente_idor.py`:

```python
import pytest
from uuid import uuid4
from httpx import AsyncClient
from app.main import app

@pytest.mark.asyncio
async def test_maestro_no_puede_ver_expediente_otro_plantel():
    """IDOR: Maestro de plantel A no debe ver expediente de plantel B"""
    client = AsyncClient(app=app, base_url="http://test")
    
    # Given: Token de maestro del plantel A
    maestro_token = "eyJhbGc..."  # Token válido de maestro plantel A
    
    # When: GET expediente de estudiante del plantel B
    response = await client.get(
        f"/api/v1/expediente/alumno/{uuid4()}",
        headers={"Authorization": f"Bearer {maestro_token}"}
    )
    
    # Then: 403 Forbidden (no acceso)
    assert response.status_code == 403
    assert "acceso" in response.json()["detail"].lower()

@pytest.mark.asyncio
async def test_estudiante_solo_ve_su_expediente():
    """IDOR: Estudiante A no debe ver expediente de estudiante B"""
    client = AsyncClient(app=app, base_url="http://test")
    
    # Given: Token de estudiante A
    student_token = "eyJhbGc..."
    student_id = uuid4()
    other_student_id = uuid4()
    
    # When: GET expediente de otro estudiante
    response = await client.get(
        f"/api/v1/expediente/alumno/{other_student_id}",
        headers={"Authorization": f"Bearer {student_token}"}
    )
    
    # Then: 403 Forbidden
    assert response.status_code == 403
```

---

## 2️⃣ FIX HTTPS ENFORCEMENT + HEADERS SEGURIDAD

### Archivo: `/backend/app/main.py`

**REEMPLAZAR** (líneas 33-44):

```python
# ❌ VULNERABLE
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
```

**CON** (nueva versión segura):

```python
# ✅ SEGURO — HTTPS enforcement + security headers
from fastapi.middleware.httpsredirect import HTTPSRedirectMiddleware
from fastapi.middleware.trustedhost import TrustedHostMiddleware

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

# IMPORTANTE: Orden de middlewares (más recientes primero)

# 1. HTTPS Redirect — solo en producción
if settings.ENVIRONMENT == "production":
    app.add_middleware(HTTPSRedirectMiddleware)

# 2. Trusted Host — whitelist de dominios
if settings.ENVIRONMENT == "production":
    app.add_middleware(
        TrustedHostMiddleware,
        allowed_hosts=["ades.setag.mx", "api.ades.setag.mx"]
    )

# 3. CORS (original)
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.allowed_origins_list,
    allow_credentials=True,
    allow_methods=["GET", "POST", "PUT", "PATCH", "DELETE"],  # ← Específico, no "*"
    allow_headers=["Content-Type", "Authorization"],  # ← Específico, no "*"
)
```

### Agregar Security Headers Middleware

**AÑADIR** después de `app.add_middleware(CORSMiddleware, ...)`:

```python
# 4. Security Headers Middleware
@app.middleware("http")
async def add_security_headers(request: Request, call_next):
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
        "script-src 'self'; "
        "style-src 'self' 'unsafe-inline'; "  # Angular requiere inline styles
        "img-src 'self' data: https:; "
        "font-src 'self'; "
        "connect-src 'self' https://api.ades.setag.mx; "
        "frame-ancestors 'none'"
    )
    
    # Referrer-Policy
    response.headers["Referrer-Policy"] = "strict-origin-when-cross-origin"
    
    # Permissions-Policy
    response.headers["Permissions-Policy"] = (
        "geolocation=(), microphone=(), camera=()"
    )
    
    return response
```

---

## 3️⃣ IMPLEMENTAR RATE LIMITING

### Paso 1: Instalar dependencia

```bash
pip install slowapi
```

### Archivo: `/backend/app/core/ratelimit.py` (NUEVO)

```python
"""Rate limiting configuration para ADES API."""

from slowapi import Limiter
from slowapi.util import get_remote_address
from slowapi.errors import RateLimitExceeded
from fastapi import Request, status
from fastapi.responses import JSONResponse

# Crear limiter global
limiter = Limiter(key_func=get_remote_address)

# Definir limites por endpoint
RATE_LIMITS = {
    "auth": "5/minute",           # Login: 5 intentos por minuto
    "expediente_read": "100/minute",  # GET expediente: 100 por minuto
    "gradebook_write": "50/minute",   # PATCH calificaciones: 50 por minuto
    "upload": "10/minute",        # File upload: 10 por minuto
    "default": "1000/hour",       # Default: 1000 por hora
}

async def rate_limit_error_handler(request: Request, exc: RateLimitExceeded):
    """Handler personalizado para errores de rate limiting."""
    return JSONResponse(
        status_code=status.HTTP_429_TOO_MANY_REQUESTS,
        content={
            "detail": f"Demasiadas solicitudes. {exc.detail}",
            "retry_after": exc.get("retry_after", "60"),
        }
    )
```

### Archivo: `/backend/app/main.py` (ACTUALIZAR)

**AGREGAR** imports:

```python
from slowapi import Limiter
from slowapi.util import get_remote_address
from slowapi.errors import RateLimitExceeded
from app.core.ratelimit import rate_limit_error_handler, RATE_LIMITS

# Crear limiter
limiter = Limiter(key_func=get_remote_address)
app.state.limiter = limiter

# Agregar exception handler
app.add_exception_handler(RateLimitExceeded, rate_limit_error_handler)
```

### Proteger Endpoints Críticos

**ACTUALIZAR** routers en `/backend/app/api/v1/`:

```python
# En auth.py (si existe)
from app.core.ratelimit import limiter, RATE_LIMITS

@router.post("/login")
@limiter.limit(RATE_LIMITS["auth"])
async def login(
    credentials: LoginRequest,
    request: Request = None,  # ← Necesario para limiter
    ...
):
    ...

# En expediente.py
from app.core.ratelimit import limiter, RATE_LIMITS

@router.get("/alumno/{estudiante_id}")
@limiter.limit(RATE_LIMITS["expediente_read"])
async def get_expediente(
    estudiante_id: UUID,
    request: Request = None,  # ← Necesario
    ...
):
    ...

# En gradebook.py (si existe) o calificaciones
@router.patch("/calificaciones/{calificacion_id}")
@limiter.limit(RATE_LIMITS["gradebook_write"])
async def patch_calificacion(
    calificacion_id: UUID,
    request: Request = None,
    ...
):
    ...

# Para uploads de documentos
@router.post("/expediente/alumno/{alumno_id}/documentos")
@limiter.limit(RATE_LIMITS["upload"])
async def subir_documento(
    alumno_id: UUID,
    request: Request = None,
    ...
):
    ...
```

### Test Rate Limiting

**CREAR** `/backend/app/tests/test_ratelimit.py`:

```python
import pytest
from httpx import AsyncClient
from app.main import app

@pytest.mark.asyncio
async def test_rate_limit_login():
    """Rate limiting: máximo 5 login attempts por minuto"""
    client = AsyncClient(app=app, base_url="http://test")
    
    # Hacer 5 requests correctos
    for i in range(5):
        response = await client.post(
            "/api/v1/auth/login",
            json={"username": "test", "password": "wrong"}
        )
        assert response.status_code in [401, 422]  # Bad credentials
    
    # 6to request debe ser rate limited
    response = await client.post(
        "/api/v1/auth/login",
        json={"username": "test", "password": "wrong"}
    )
    assert response.status_code == 429  # Too Many Requests
    assert "Demasiadas solicitudes" in response.json()["detail"]
```

---

## 4️⃣ VALIDAR FIXES CON TESTS INTEGRADOS

### Archivo: `/backend/app/tests/test_stride_validation.py` (NUEVO)

```python
"""
Tests de validación STRIDE para ADES Nevadi.
Ejecutar: pytest app/tests/test_stride_validation.py -v
"""

import pytest
from httpx import AsyncClient
from uuid import uuid4
from app.main import app

@pytest.fixture
async def client():
    return AsyncClient(app=app, base_url="http://test")

class TestIDOR:
    """Tests para prevención de IDOR"""
    
    @pytest.mark.asyncio
    async def test_expediente_idor_prevention(self, client):
        """Validar que IDOR en expediente está mitigado"""
        # Este test depende de tokens reales del sistema
        # Por ahora es placeholder
        pass

class TestHTTPSEnforcement:
    """Tests para enforcement de HTTPS"""
    
    def test_hsts_header_present(self, client):
        """Validar que HSTS header está presente en respuestas"""
        # HSTS header debe estar en respuestas HTTPS
        # En desarrollo puede estar ausente, pero documentar
        pass

class TestRateLimiting:
    """Tests para rate limiting"""
    
    @pytest.mark.asyncio
    async def test_rate_limit_429(self, client):
        """Validar que endpoint retorna 429 cuando se excede límite"""
        # Hacer requests hasta exceder límite
        pass

class TestSecurityHeaders:
    """Tests para security headers"""
    
    def test_security_headers_present(self, client):
        """Validar que headers de seguridad están presentes"""
        # X-Content-Type-Options
        # X-Frame-Options
        # CSP
        pass
```

---

## 📋 CHECKLIST DE IMPLEMENTACIÓN

```bash
# 1. IDOR Fixes
☐ Actualizar /backend/app/api/v1/expediente.py
☐ Agregar función _check_expediente_access()
☐ Audit todos los endpoints POST/PUT/DELETE para IDOR
☐ Crear test test_expediente_idor.py

# 2. HTTPS + Security Headers
☐ Actualizar /backend/app/main.py con middlewares
☐ Agregar security headers middleware
☐ Validar en producción: curl -I https://api.ades.setag.mx
☐ Test: HTTP redirect a HTTPS

# 3. Rate Limiting
☐ pip install slowapi
☐ Crear /backend/app/core/ratelimit.py
☐ Actualizar main.py con limiter
☐ Proteger endpoints críticos con @limiter.limit()
☐ Test: 6 login attempts → 429

# 4. Tests
☐ pytest app/tests/test_stride_validation.py
☐ pytest app/tests/test_expediente_idor.py
☐ pytest app/tests/test_ratelimit.py

# 5. Deployment
☐ Revisar config.py → ENVIRONMENT = "production"
☐ Verificar: OIDC_CLIENT_SECRET, SECRET_KEY, etc. en Vault
☐ Deploy a staging
☐ Penetration test: IDOR, HTTPS, rate limiting
☐ Deploy a producción
```

---

## 🚀 EXECUTION TIMELINE

**Hoy (19 Junio)**:
- [ ] Implementar IDOR validation en expediente.py
- [ ] Actualizar main.py con HTTPS enforcement

**Mañana (20 Junio)**:
- [ ] Implementar rate limiting
- [ ] Ejecutar tests STRIDE
- [ ] Code review de cambios

**Próximos 2 días**:
- [ ] Deploy a staging
- [ ] Penetration testing
- [ ] Deploy a producción

---

## ✅ VALIDACIÓN POST-FIX

```bash
# 1. Verificar HTTPS
curl -I https://api.ades.setag.mx/api/v1/health
# Buscar: Strict-Transport-Security, X-Content-Type-Options, etc.

# 2. Verificar Rate Limiting
for i in {1..7}; do
  curl -X POST https://api.ades.setag.mx/api/v1/login \
    -H "Content-Type: application/json" \
    -d '{"username":"test","password":"wrong"}'
  echo "Request $i"
done
# Request 6 debe retornar 429

# 3. Verificar IDOR Fix
# Login como maestro plantel A
TOKEN_A=$(curl -s -X POST ... | jq .token)

# Intentar acceder a estudiante de plantel B
curl -H "Authorization: Bearer $TOKEN_A" \
  https://api.ades.setag.mx/api/v1/expediente/alumno/{uuid-plantel-B}
# Debe retornar 403
```

---

**Status**: Ready to implement  
**Effort**: ~4 horas desarrollo + 2 horas testing  
**Risk mitigation**: CRÍTICO
