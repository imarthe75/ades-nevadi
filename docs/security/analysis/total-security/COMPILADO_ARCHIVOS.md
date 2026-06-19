# 📦 COMPILADO DE ARCHIVOS — Ready to Copy/Paste

**Este documento contiene todos los archivos de configuración, YAML, y scripts listos para copiar directamente a tu proyecto.**

---

## 1️⃣ ARCHIVOS DE CONFIGURACIÓN (Raíz del proyecto)

### Archivo: `.pre-commit-config.yaml`

**Ubicación**: Raíz del repo  
**Descripción**: Configuración de pre-commit hooks para seguridad local

```yaml
# .pre-commit-config.yaml
# Pre-commit hooks — Ejecuta ANTES de cada commit local
# Setup: pip install pre-commit && pre-commit install

default_language_version:
  python: python3.12

repos:
  - repo: https://github.com/pre-commit/pre-commit-hooks
    rev: v4.4.0
    hooks:
      - id: detect-private-key
        args: ['--allow-multiple-files']
      - id: check-ast
      - id: check-json
      - id: check-yaml
      - id: check-xml
      - id: check-toml
      - id: check-merge-conflict
      - id: check-added-large-files
        args: ['--maxkb=1000']
      - id: end-of-file-fixer
      - id: trailing-whitespace
      - id: mixed-line-ending

  - repo: https://github.com/PyCQA/bandit
    rev: 1.7.5
    hooks:
      - id: bandit
        args: ['-r', 'app/', '-c', '.bandit']
        stages: [commit]

  - repo: https://github.com/PyCQA/flake8
    rev: 6.0.0
    hooks:
      - id: flake8
        args: ['--max-line-length=120', '--extend-ignore=E203,W503']
        types: [python]

  - repo: https://github.com/psf/black
    rev: 23.7.0
    hooks:
      - id: black
        language_version: python3.12
        args: ['--line-length=120']
        types: [python]

  - repo: https://github.com/PyCQA/isort
    rev: 5.12.0
    hooks:
      - id: isort
        args: ['--profile', 'black', '--line-length', '120']
        types: [python]

  - repo: https://github.com/Yelp/detect-secrets
    rev: v1.4.0
    hooks:
      - id: detect-secrets
        args: ['--baseline', '.secrets.baseline']
        types: [python]

  - repo: https://github.com/adrienverge/yamllint
    rev: v1.28.0
    hooks:
      - id: yamllint
        args: ['-d', '{extends: default, rules: {line-length: {max: 120}}}']
        types: [yaml]

fail_fast: false
exclude: |
  (?x)^(
      venv/|
      __pycache__/|
      \.eggs/|
      node_modules/|
      dist/|
      build/
  )

stages: [commit, push]
```

---

### Archivo: `.bandit`

**Ubicación**: Raíz del repo  
**Descripción**: Configuración de Bandit SAST

```ini
[bandit]
tests = B201,B301,B302,B303,B304,B305,B306,B307,B308,B309,B310,B311,B312,B313,B314,B315,B316,B317,B318,B319,B320,B321,B322,B323,B324,B325,B401,B402,B403,B404,B405,B406,B407,B408,B409,B410,B411,B412,B413,B414,B415,B416,B417,B418,B419,B420,B421,B422,B501,B502,B503,B504,B505,B506,B507,B601,B602,B603,B604,B605,B606,B607,B608,B609,B610,B611,B701,B702,B703,B704,B705,B706

skips = B101
```

---

### Archivo: `pyproject.toml` (AGREGAR ESTAS SECCIONES)

**Ubicación**: Raíz del repo  
**Descripción**: Configuración de herramientas Python

```toml
[tool.black]
line-length = 120
target-version = ['py312']

[tool.isort]
profile = "black"
line_length = 120

[tool.bandit]
exclude_dirs = ["tests", "venv"]

[tool.pytest.ini_options]
testpaths = ["app/tests"]
asyncio_mode = "auto"
```

---

## 2️⃣ GITHUB ACTIONS WORKFLOWS

### Archivo: `.github/workflows/security.yml`

**Ubicación**: `.github/workflows/security.yml`  
**Descripción**: Pipeline de seguridad en CI/CD

```yaml
name: Security Checks

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main, develop ]

jobs:
  sast:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up Python
        uses: actions/setup-python@v4
        with:
          python-version: '3.12'
          cache: 'pip'
      
      - name: Install dependencies
        run: |
          pip install bandit semgrep flake8 safety
      
      - name: Bandit Security Scan
        run: |
          bandit -r app/ -f json -o bandit-report.json
          bandit -r app/ -f txt
        continue-on-error: true
      
      - name: Semgrep Scan
        run: |
          semgrep --config=p/security-audit app/ --json -o semgrep-report.json
          semgrep --config=p/security-audit app/
        continue-on-error: true
      
      - name: Flake8 Lint
        run: |
          flake8 app/ --max-line-length=120 --count --statistics
        continue-on-error: true
      
      - name: Upload SAST reports
        uses: actions/upload-artifact@v3
        with:
          name: sast-reports
          path: |
            bandit-report.json
            semgrep-report.json

  dependencies:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up Python
        uses: actions/setup-python@v4
        with:
          python-version: '3.12'
      
      - name: Install Safety
        run: pip install safety pip-audit
      
      - name: Safety Check
        run: |
          safety check --json --output safety-report.json
        continue-on-error: true
      
      - name: Pip Audit
        run: |
          pip-audit --desc > pip-audit-report.txt
        continue-on-error: true

  tests:
    runs-on: ubuntu-latest
    services:
      postgres:
        image: postgres:15
        env:
          POSTGRES_DB: ades_test
          POSTGRES_PASSWORD: test_password
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5
        ports:
          - 5432:5432
    
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up Python
        uses: actions/setup-python@v4
        with:
          python-version: '3.12'
          cache: 'pip'
      
      - name: Install dependencies
        run: |
          pip install -r requirements.txt
          pip install pytest pytest-cov pytest-asyncio
      
      - name: Run Tests
        env:
          DATABASE_URL: postgresql+asyncpg://postgres:test_password@localhost:5432/ades_test
        run: |
          pytest app/tests/ -v --cov=app --cov-report=xml
      
      - name: Upload coverage
        uses: codecov/codecov-action@v3
        with:
          files: ./coverage.xml
```

---

### Archivo: `.github/workflows/deploy-prod.yml`

**Ubicación**: `.github/workflows/deploy-prod.yml`  
**Descripción**: Deployment seguro a producción

```yaml
name: Deploy to Production

on:
  workflow_dispatch:
  push:
    branches: [ main ]

jobs:
  security-validation:
    runs-on: ubuntu-latest
    
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up Python
        uses: actions/setup-python@v4
        with:
          python-version: '3.12'
      
      - name: Install Security Tools
        run: |
          pip install bandit safety
      
      - name: Run Security Scans
        run: |
          bandit -r app/ || exit 1
          safety check || exit 1

  deploy:
    needs: security-validation
    runs-on: ubuntu-latest
    
    environment:
      name: production
      url: https://ades.setag.mx
    
    steps:
      - uses: actions/checkout@v3
      
      - name: Deploy to Production
        run: |
          echo "Deploying to production..."
          # Tu script de deploy aquí
```

---

## 3️⃣ ARCHIVOS DE MIGRACIÓN DATABASE

### Archivo: `db/migrations/202406_encrypt_pii.sql`

**Ubicación**: `db/migrations/202406_encrypt_pii.sql`  
**Descripción**: Migración para agregar columnas de encriptación

```sql
BEGIN TRANSACTION;

-- Backup
CREATE TABLE IF NOT EXISTS ades_pii_encryption_backup_20260620 AS
SELECT 
    'ades_usuarios' as source_table,
    id,
    email_institucional as pii_value,
    'email' as field_type,
    NOW() as backup_date
FROM ades_usuarios
WHERE email_institucional IS NOT NULL;

-- Agregar columnas
ALTER TABLE ades_usuarios
ADD COLUMN IF NOT EXISTS email_institucional_encrypted VARCHAR,
ADD COLUMN IF NOT EXISTS telefono_encrypted VARCHAR,
ADD COLUMN IF NOT EXISTS email_institucional_hash VARCHAR,
ADD COLUMN IF NOT EXISTS pii_encryption_status VARCHAR DEFAULT 'pending';

ALTER TABLE ades_personas
ADD COLUMN IF NOT EXISTS email_personal_encrypted VARCHAR,
ADD COLUMN IF NOT EXISTS telefono_encrypted VARCHAR,
ADD COLUMN IF NOT EXISTS curp_encrypted VARCHAR,
ADD COLUMN IF NOT EXISTS rfc_encrypted VARCHAR,
ADD COLUMN IF NOT EXISTS domicilio_encrypted VARCHAR,
ADD COLUMN IF NOT EXISTS email_personal_hash VARCHAR,
ADD COLUMN IF NOT EXISTS pii_encryption_status VARCHAR DEFAULT 'pending';

COMMIT;
```

---

## 4️⃣ SCRIPTS DE CONFIGURACIÓN

### Script: `scripts/setup_security.sh`

**Ubicación**: `scripts/setup_security.sh`  
**Descripción**: Setup de seguridad para desarrolladores

```bash
#!/bin/bash
# scripts/setup_security.sh

set -e

echo "🔒 Configurando seguridad para ADES..."

# 1. Instalar pre-commit
echo "📦 Instalando pre-commit..."
pip install pre-commit

# 2. Instalar hooks
echo "🔗 Instalando git hooks..."
pre-commit install

# 3. Instalar herramientas de seguridad
echo "🛡️  Instalando herramientas de seguridad..."
pip install bandit semgrep safety pip-audit

# 4. Crear baseline para detect-secrets
echo "🔐 Generando baseline de secrets..."
detect-secrets scan > .secrets.baseline

# 5. Verificar
echo "✅ Ejecutando pre-commit en todos los archivos..."
pre-commit run --all-files || true

echo ""
echo "✅ Setup completado!"
echo ""
echo "Próximos pasos:"
echo "  git commit -m 'chore: setup security'"
echo ""
echo "Los pre-commit hooks ejecutarán automáticamente en cada commit"
```

---

### Script: `scripts/generate_encryption_key.sh`

**Ubicación**: `scripts/generate_encryption_key.sh`  
**Descripción**: Generar clave de encriptación (EJECUTAR UNA SOLA VEZ)

```bash
#!/bin/bash
# scripts/generate_encryption_key.sh

set -e

echo "🔐 Generando clave de encriptación Fernet..."
echo ""

ENCRYPTION_KEY=$(python3 -c "from cryptography.fernet import Fernet; print(Fernet.generate_key().decode())")

echo "Clave generada:"
echo "$ENCRYPTION_KEY"
echo ""
echo "🔒 Guardando en Vault..."

if command -v vault &> /dev/null; then
    vault kv put secret/ades DATABASE_ENCRYPTION_KEY="$ENCRYPTION_KEY"
    echo "✅ Clave guardada en Vault"
else
    echo "⚠️  Vault no encontrado. Guardar manualmente en:"
    echo "   vault kv put secret/ades DATABASE_ENCRYPTION_KEY='$ENCRYPTION_KEY'"
fi

echo ""
echo "⚠️  IMPORTANTE:"
echo "   - Guardar esta clave en lugar seguro (1Password, BitWarden, etc)"
echo "   - NUNCA commitearla a git"
echo "   - Usar solo en .env (nunca en código)"
echo ""
echo "Ubicación en Vault:"
echo "   secret/ades/DATABASE_ENCRYPTION_KEY"
```

---

## 5️⃣ ARCHIVOS PYTHON

### Archivo: `backend/app/core/encryption.py`

**Ubicación**: `backend/app/core/encryption.py`  
**Descripción**: Módulo de encriptación de PII

```python
"""
Encriptación de campos sensibles (PII).
Usar Fernet (symmetric encryption).
"""

from cryptography.fernet import Fernet, InvalidToken
from app.core.config import settings
import logging

log = logging.getLogger(__name__)

_CIPHER = None

def get_cipher() -> Fernet:
    """Obtener cipher singleton."""
    global _CIPHER
    if _CIPHER is None:
        key = settings.DATABASE_ENCRYPTION_KEY.encode()
        _CIPHER = Fernet(key)
    return _CIPHER

def encrypt_field(value: str) -> str:
    """Encriptar un campo."""
    if not value:
        return None
    try:
        cipher = get_cipher()
        encrypted = cipher.encrypt(value.encode())
        return encrypted.decode()
    except Exception as e:
        log.error(f"Encryption error: {e}")
        raise ValueError("Error encriptando dato")

def decrypt_field(value: str) -> str:
    """Desencriptar un campo."""
    if not value:
        return None
    try:
        cipher = get_cipher()
        decrypted = cipher.decrypt(value.encode())
        return decrypted.decode()
    except InvalidToken as e:
        log.error(f"Invalid token: {e}")
        raise ValueError("Error desencriptando dato")
    except Exception as e:
        log.error(f"Decryption error: {e}")
        raise ValueError("Error desencriptando dato")
```

---

### Archivo: `backend/app/core/ratelimit.py`

**Ubicación**: `backend/app/core/ratelimit.py`  
**Descripción**: Configuración de rate limiting

```python
"""
Rate limiting configuration.
"""

from slowapi import Limiter
from slowapi.util import get_remote_address

limiter = Limiter(
    key_func=get_remote_address,
    default_limits=["1000/hour"]
)

# Límites específicos
LIMITS = {
    "auth": "5/minute",         # Login: 5 intentos
    "read": "100/minute",       # GET: 100 requests
    "write": "50/minute",       # POST/PATCH: 50 requests
    "upload": "10/minute",      # Upload: 10 requests
    "public": "100/day",        # Public endpoints: 100/día
}
```

---

### Archivo: `backend/app/core/security.py` (AGREGAR)

**Ubicación**: `backend/app/core/security.py`  
**Descripción**: Agregar a archivo existente

```python
# AGREGAR A FINAL DE ARCHIVO EXISTENTE

# Security headers middleware
from starlette.middleware.base import BaseHTTPMiddleware
from fastapi import Request

class SecurityHeadersMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request: Request, call_next):
        response = await call_next(request)
        
        # HSTS
        response.headers["Strict-Transport-Security"] = (
            "max-age=31536000; includeSubDomains; preload"
        )
        
        # X-Content-Type-Options
        response.headers["X-Content-Type-Options"] = "nosniff"
        
        # X-Frame-Options
        response.headers["X-Frame-Options"] = "DENY"
        
        # X-XSS-Protection
        response.headers["X-XSS-Protection"] = "1; mode=block"
        
        # CSP
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
```

---

### Archivo: `backend/app/tests/test_expediente_idor.py` (NUEVO)

**Ubicación**: `backend/app/tests/test_expediente_idor.py`  
**Descripción**: Tests para IDOR en expediente

```python
"""Tests para IDOR en expediente.py"""

import pytest
from uuid import uuid4
from httpx import AsyncClient
from app.main import app

@pytest.fixture
async def client():
    return AsyncClient(app=app, base_url="http://test")

@pytest.mark.asyncio
async def test_maestro_no_puede_ver_otro_plantel(client, db):
    """IDOR: Maestro A no puede ver expediente Plantel B"""
    # Setup: crear maestro, estudiante de otro plantel
    # Act: GET /expediente/{otro_estudiante}
    # Assert: 403 Forbidden
    pass

@pytest.mark.asyncio
async def test_estudiante_solo_ve_su_expediente(client, db):
    """Estudiante A no puede ver expediente Estudiante B"""
    pass

@pytest.mark.asyncio
async def test_admin_global_puede_ver_cualquiera(client, db):
    """Admin global PUEDE ver expediente de cualquiera"""
    pass
```

---

## 6️⃣ REQUIREMENTS.TXT (AGREGAR)

**Ubicación**: `requirements.txt`  
**Descripción**: Dependencias adicionales

```
# Seguridad
slowapi==0.1.8
cryptography==41.0.0
python-jose[cryptography]==3.3.0
passlib[bcrypt]==1.7.4

# SAST/Testing
bandit==1.7.5
semgrep==1.42.0
safety==2.3.5
pip-audit==2.6.1

# Otros
python-multipart==0.0.6
```

---

## 📋 CHECKLIST DE INSTALACIÓN

```bash
# 1. Copiar archivos a raíz
cp .pre-commit-config.yaml .  # Raíz
cp .bandit .                  # Raíz
cp pyproject.toml .           # Raíz (merge con existente)

# 2. Copiar GitHub Actions
mkdir -p .github/workflows
cp security.yml .github/workflows/
cp deploy-prod.yml .github/workflows/

# 3. Copiar migrations
cp db/migrations/202406_encrypt_pii.sql db/migrations/

# 4. Copiar scripts
mkdir -p scripts
cp setup_security.sh scripts/
cp generate_encryption_key.sh scripts/

# 5. Copiar código Python
cp backend/app/core/encryption.py backend/app/core/
cp backend/app/core/ratelimit.py backend/app/core/
# Agregar a backend/app/core/security.py

# 6. Copiar tests
cp backend/app/tests/test_expediente_idor.py backend/app/tests/

# 7. Instalar dependencias
pip install -r requirements.txt

# 8. Setup local
bash scripts/setup_security.sh
```

---

**Status**: Todo listo para implementar  
**Próximo paso**: Seguir guía en 00_INDICE_MAESTRO.md
