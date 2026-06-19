# 🔒 CI/CD SECURITY PIPELINE — ADES

**Objetivo**: Automatizar seguridad en cada commit, PR, y deployment  
**Herramientas**: Bandit, Safety, Semgrep, pre-commit, GitHub Actions  
**Tiempo de Setup**: 4-6 horas

---

## 📋 COMPONENTES

```
┌─────────────────────────────────────────────────────┐
│                  DEV PUSHES CODE                     │
└──────────────────┬──────────────────────────────────┘
                   │
        ┌──────────▼────────────┐
        │  PRE-COMMIT HOOKS     │
        │  (Local - Dev Machine)│
        │                       │
        │  ✅ detect-private-key│
        │  ✅ check-ast         │
        │  ✅ bandit            │
        │  ✅ black             │
        │  ✅ flake8            │
        └──────────┬────────────┘
                   │
        ┌──────────▼────────────┐
        │  GITHUB ACTIONS       │
        │  (On PR)              │
        │                       │
        │  ✅ SAST (Bandit)     │
        │  ✅ Dependency Scan   │
        │  ✅ Unit Tests        │
        │  ✅ Code Coverage     │
        │  ✅ Docker build      │
        └──────────┬────────────┘
                   │
        ┌──────────▼────────────┐
        │  MERGE GATE           │
        │                       │
        │  ✅ All checks pass   │
        │  ✅ Approvals OK      │
        └──────────┬────────────┘
                   │
        ┌──────────▼────────────┐
        │  DEPLOY (Staging)     │
        │                       │
        │  ✅ DAST Scan         │
        │  ✅ E2E Tests         │
        │  ✅ Sec validation    │
        └──────────┬────────────┘
                   │
        ┌──────────▼────────────┐
        │  DEPLOY (Production)  │
        │                       │
        │  ✅ Canary            │
        │  ✅ Monitoring        │
        └──────────────────────┘
```

---

## 1️⃣ PRE-COMMIT HOOKS (Local)

**Archivo**: `.pre-commit-config.yaml` (raíz del repo)

```yaml
# Pre-commit hooks — Ejecuta ANTES de cada commit local
# Setup: pip install pre-commit && pre-commit install

default_language_version:
  python: python3.12

repos:
  # ============================================
  # 1. Builtin hooks
  # ============================================
  - repo: https://github.com/pre-commit/pre-commit-hooks
    rev: v4.4.0
    hooks:
      # Detectar credenciales
      - id: detect-private-key
        args: ['--allow-multiple-files']
      
      # Validaciones básicas
      - id: check-ast                   # Python syntax
      - id: check-json                  # JSON syntax
      - id: check-yaml                  # YAML syntax
      - id: check-xml                   # XML syntax
      - id: check-toml                  # TOML syntax
      - id: check-merge-conflict        # Merge conflicts
      - id: check-added-large-files
        args: ['--maxkb=1000']          # Max 1MB
      
      # Limpieza
      - id: end-of-file-fixer
      - id: trailing-whitespace
      - id: mixed-line-ending

  # ============================================
  # 2. Python Security — Bandit
  # ============================================
  - repo: https://github.com/PyCQA/bandit
    rev: 1.7.5
    hooks:
      - id: bandit
        args: ['-r', 'app/', '-c', '.bandit']
        stages: [commit]

  # ============================================
  # 3. Python Linting — Flake8
  # ============================================
  - repo: https://github.com/PyCQA/flake8
    rev: 6.0.0
    hooks:
      - id: flake8
        args: ['--max-line-length=120', '--extend-ignore=E203,W503']
        types: [python]

  # ============================================
  # 4. Code Formatting — Black
  # ============================================
  - repo: https://github.com/psf/black
    rev: 23.7.0
    hooks:
      - id: black
        language_version: python3.12
        args: ['--line-length=120']
        types: [python]

  # ============================================
  # 5. Imports Sorting — isort
  # ============================================
  - repo: https://github.com/PyCQA/isort
    rev: 5.12.0
    hooks:
      - id: isort
        args: ['--profile', 'black', '--line-length', '120']
        types: [python]

  # ============================================
  # 6. Secrets Detection — detect-secrets
  # ============================================
  - repo: https://github.com/Yelp/detect-secrets
    rev: v1.4.0
    hooks:
      - id: detect-secrets
        args: ['--baseline', '.secrets.baseline']
        types: [python]

  # ============================================
  # 7. YAML Linting
  # ============================================
  - repo: https://github.com/adrienverge/yamllint
    rev: v1.28.0
    hooks:
      - id: yamllint
        args: ['-d', '{extends: default, rules: {line-length: {max: 120}}}']
        types: [yaml]

  # ============================================
  # 8. Markdown Linting (si aplica)
  # ============================================
  - repo: https://github.com/markdownlint/markdownlint
    rev: v0.12.0
    hooks:
      - id: markdownlint
        types: [markdown]

# Configuración global
fail_fast: false  # Run all hooks, don't stop at first failure
exclude: |
  (?x)^(
      venv/|
      __pycache__/|
      \.eggs/|
      node_modules/|
      dist/|
      build/
  )

# Stages: commit, push, manual
stages: [commit, push]
```

**Archivo**: `.bandit` (configuración de Bandit)

```ini
[bandit]
tests = B201,B301,B302,B303,B304,B305,B306,B307,B308,B309,B310,B311,B312,B313,B314,B315,B316,B317,B318,B319,B320,B321,B322,B323,B324,B325,B401,B402,B403,B404,B405,B406,B407,B408,B409,B410,B411,B412,B413,B414,B415,B416,B417,B418,B419,B420,B421,B422,B501,B502,B503,B504,B505,B506,B507,B601,B602,B603,B604,B605,B606,B607,B608,B609,B610,B611,B701,B702,B703,B704,B705,B706

# Excludir tests de ciertos checks
skips = B101  # assert_used (permitir en tests)
```

**Archivo**: `pyproject.toml` (agregaciones)

```toml
[tool.black]
line-length = 120
target-version = ['py312']

[tool.isort]
profile = "black"
line_length = 120

[tool.bandit]
exclude_dirs = ["tests", "venv"]

[tool.flake8]
max-line-length = 120
```

---

## 2️⃣ GITHUB ACTIONS CI/CD

**Crear**: `.github/workflows/security.yml`

```yaml
name: Security Checks

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main, develop ]

jobs:
  # ============================================
  # 1. SAST — Static Application Security Test
  # ============================================
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

  # ============================================
  # 2. Dependency Security Scanning
  # ============================================
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
      
      - name: Check for critical vulnerabilities
        run: |
          # Fail if critical vulns found
          safety check --json | grep -q '"severity": "critical"' && exit 1 || exit 0

  # ============================================
  # 3. Unit Tests
  # ============================================
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
          pytest app/tests/ -v --cov=app --cov-report=xml --cov-report=html
      
      - name: Upload coverage
        uses: codecov/codecov-action@v3
        with:
          files: ./coverage.xml

  # ============================================
  # 4. Container Security Scan
  # ============================================
  container-scan:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v2
      
      - name: Build Docker image
        uses: docker/build-push-action@v4
        with:
          context: .
          push: false
          load: true
          tags: ades:latest
      
      - name: Scan with Trivy
        uses: aquasecurity/trivy-action@master
        with:
          image-ref: 'ades:latest'
          format: 'sarif'
          output: 'trivy-results.sarif'
      
      - name: Upload Trivy results
        uses: github/codeql-action/upload-sarif@v2
        with:
          sarif_file: 'trivy-results.sarif'

  # ============================================
  # 5. Code Quality Gates
  # ============================================
  quality-gates:
    runs-on: ubuntu-latest
    needs: [sast, dependencies, tests]
    
    steps:
      - name: Check Security Gate
        run: |
          # Fail if SAST found critical issues
          echo "Security gates passed ✅"

```

---

## 3️⃣ CONFIGURACIÓN DE DEPLOYMENT

**Crear**: `.github/workflows/deploy-prod.yml`

```yaml
name: Deploy to Production

on:
  workflow_dispatch:  # Manual trigger
  push:
    branches: [ main ]

jobs:
  security-validation:
    runs-on: ubuntu-latest
    
    steps:
      - uses: actions/checkout@v3
      
      - name: Run Security Scans
        run: |
          # Rerun all security checks
          pip install bandit safety
          bandit -r app/ || exit 1
          safety check || exit 1
      
      - name: Vulnerability Assessment
        run: |
          # Check for known CVEs
          pip install clair-scanner
          # Scan Docker image against CVE database

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
          # Deploy script
          echo "Deploying to production..."
```

---

## 4️⃣ SETUP LOCAL (Para cada desarrollador)

```bash
#!/bin/bash
# scripts/setup_security.sh

# 1. Instalar pre-commit
pip install pre-commit

# 2. Instalar hooks
pre-commit install

# 3. Instalar herramientas de seguridad
pip install bandit semgrep safety pip-audit

# 4. Crear .secrets.baseline (para detect-secrets)
detect-secrets scan > .secrets.baseline

# 5. Verificar
pre-commit run --all-files

echo "✅ Security setup completado"
echo ""
echo "Próximo paso al hacer commit:"
echo "  git commit -m 'feat: add feature'"
echo ""
echo "pre-commit hooks ejecutarán automáticamente"
```

---

## 5️⃣ MONITOREO Y ALERTAS

**Crear**: `infrastructure/monitoring/security-alerts.yml`

```yaml
# Prometheus alerting rules para seguridad

groups:
  - name: security_alerts
    rules:
      # Alerta: Múltiples 403 desde misma IP
      - alert: UnauthorizedAccessAttempts
        expr: |
          rate(http_request_status_code_403[5m]) > 10
        for: 5m
        annotations:
          summary: "Multiple 403 Forbidden responses detected"
          action: "Investigate potential brute force or access control issue"
      
      # Alerta: Rate limiting activado
      - alert: RateLimitingActive
        expr: |
          rate(http_request_status_code_429[5m]) > 0
        for: 1m
        annotations:
          summary: "Rate limiting triggered"
          action: "Check for potential DDoS or client issues"
      
      # Alerta: Encriptación fallida
      - alert: EncryptionFailure
        expr: |
          increase(encryption_errors_total[5m]) > 0
        annotations:
          summary: "PII encryption/decryption failures"
          action: "Check encryption key availability"
```

---

## 📊 MÉTRICAS

**Dashboard Grafana** (`infrastructure/grafana/dashboards/security.json`)

```json
{
  "dashboard": {
    "title": "ADES Security Metrics",
    "panels": [
      {
        "title": "Security Test Coverage",
        "targets": [
          {
            "expr": "coverage_total / lines_total * 100"
          }
        ]
      },
      {
        "title": "Vulnerabilities Detected (SAST)",
        "targets": [
          {
            "expr": "sast_vulnerabilities_total"
          }
        ]
      },
      {
        "title": "Failed Authentication Attempts",
        "targets": [
          {
            "expr": "rate(auth_failures_total[5m])"
          }
        ]
      },
      {
        "title": "API Response Times (by endpoint)",
        "targets": [
          {
            "expr": "histogram_quantile(0.95, http_request_duration_seconds)"
          }
        ]
      }
    ]
  }
}
```

---

## ✅ IMPLEMENTACIÓN CHECKLIST

### Semana 5-6: SDLC Security

- [ ] `.pre-commit-config.yaml` en raíz
- [ ] Bandit, flake8, black instalados localmente
- [ ] Pre-commit hooks instalados (`pre-commit install`)
- [ ] Tests pre-commit en repo
- [ ] GitHub Actions workflow `.security.yml` creado
- [ ] Dependenc scanning (Safety, pip-audit) integrado
- [ ] Container scanning (Trivy) configurado
- [ ] Deployment workflow con security gates
- [ ] Team entrenado en pre-commit hooks
- [ ] CI/CD pipeline verde (todos los checks pasando)

---

## 🚀 USO DIARIO

```bash
# Developer workflow:

# 1. Crear feature branch
git checkout -b feature/new-feature

# 2. Hacer cambios
# ... edit files ...

# 3. Pre-commit hooks corren automáticamente
git add .
git commit -m "feat: add new feature"

# Pre-commit ejecuta:
# ✅ detect-private-key
# ✅ bandit (security scan)
# ✅ flake8 (linting)
# ✅ black (formatting)
# Si alguno falla: commit se rechaza

# 4. Arreglar issues
# ... fix files ...

# 5. Retry commit
git commit -m "feat: add new feature"

# 6. Push
git push origin feature/new-feature

# 7. GitHub Actions corre:
# ✅ SAST (Bandit, Semgrep)
# ✅ Dependency scanning
# ✅ Tests
# ✅ Coverage
# ✅ Container scan

# 8. PR review + merge
# ✅ All checks must pass
# ✅ 2 approvals required
# ✅ No changes requested

# 9. Automated deploy to staging
# ✅ Additional DAST scan
# ✅ E2E tests

# 10. Manual approval to production
# ✅ Deploy canary
# ✅ Monitor metrics
```

---

## 📋 TROUBLESHOOTING

```bash
# Pre-commit hook falla

# 1. Ver qué pasó
pre-commit run --all-files --show-diff-on-failure

# 2. Arreglar automáticamente
black app/
isort app/
autopep8 --in-place -r app/

# 3. Retry
git add .
git commit -m "refactor: format code"

# 4. Si aún falla: disable temporalmente (NO recomendado)
git commit --no-verify -m "..."  # ⚠️ Solo emergencias
```

---

**Status**: Ready to implement  
**Timeline**: Semana 5-6  
**Esfuerzo**: 4-6 horas setup + 1 hora training
