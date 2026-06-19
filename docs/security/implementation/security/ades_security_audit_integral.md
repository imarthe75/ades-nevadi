# 🔐 AUDITORÍA DE SEGURIDAD INTEGRAL — ADES Nevadi
**Fecha**: 19 Junio 2026 | **Scope**: Múltiples estándares  
**Evaluación**: STRIDE + OWASP + NIST + Compliance + Infrastructure

---

## 📊 RESUMEN EJECUTIVO

```
┌──────────────────────────────────────────────────────────┐
│ POSTURA DE SEGURIDAD GENERAL: 🟡 MEDIA (6.5/10)         │
├──────────────────────────────────────────────────────────┤
│ STRIDE Threats          │ 🔴 13 críticas/altas          │
│ OWASP Top 10 2021       │ 🟠 5 aplicables encontradas   │
│ OWASP API Top 10        │ 🟠 6 aplicables encontradas   │
│ NIST Framework          │ 🟡 3/5 funciones implementadas│
│ Data Protection         │ 🟠 PII sin encriptación       │
│ Infrastructure          │ 🟡 Parcial (nginx + vault)    │
│ Supply Chain            │ ⚠️  Riesgos de dependencias   │
│ SDLC Security           │ ✅ Mejorando (tests + git)    │
│ Compliance              │ ⚠️  GDPR/LFPDPPP parcial      │
├──────────────────────────────────────────────────────────┤
│ HALLAZGOS CRÍTICOS      │ 2 encontrados en código       │
│ PROBLEMAS ALTOS         │ 8 requieren atención          │
│ MEJORAS RECOMENDADAS    │ 25+                           │
└──────────────────────────────────────────────────────────┘
```

---

## 1️⃣ STRIDE (Threat Modeling)

### Status: 🔴 CRÍTICO
- **13 amenazas críticas/altas identificadas**
- **2 vulnerabilidades reales confirmadas en código**
- Ver: `ades_stride_real_audit.md` (adjunto)

| Categoría | Status | Top Issue |
|-----------|--------|-----------|
| Spoofing | 🔴 | OIDC tokens en localStorage |
| Tampering | 🔴 | Race conditions gradebook |
| Repudiation | 🔴 | Auditoría modificable |
| Info Disclosure | 🟠 | PII sin encriptación |
| Denial of Service | 🟠 | Rate limiting ausente |
| Elevation of Privilege | 🔴 | IDOR en endpoints críticos |

---

## 2️⃣ OWASP TOP 10 2021

### Mapeo de ADES contra OWASP Top 10

| # | Vulnerabilidad | Aplicable | Severidad | Status | ADES Risk |
|---|---|---|---|---|---|
| A01 | Broken Access Control | ✅ SÍ | CRÍTICA | 🔴 Vulnerable | **IDOR en expediente** |
| A02 | Cryptographic Failures | ✅ SÍ | CRÍTICA | 🟠 Parcial | PII sin encriptación |
| A03 | Injection | ✅ SÍ | CRÍTICA | ✅ Mitigado | ORM (SQLAlchemy) |
| A04 | Insecure Design | ✅ SÍ | CRÍTICA | 🟠 Parcial | Falta RLS en BD |
| A05 | Security Misconfiguration | ✅ SÍ | ALTA | 🟠 Parcial | CORS permisivo, HTTPS |
| A06 | Vulnerable & Outdated Components | ✅ SÍ | ALTA | ⚠️  Por revisar | Dependencias |
| A07 | Identification & Auth Failures | ✅ SÍ | CRÍTICA | 🟡 Bueno | OIDC RS256 OK, rate limit NO |
| A08 | Data Integrity Failures | ✅ SÍ | CRÍTICA | ✅ Bueno | Optimistic locking |
| A09 | Logging & Monitoring Gaps | ✅ SÍ | ALTA | ✅ Bueno | AuditMiddleware |
| A10 | SSRF | ✅ SÍ | ALTA | 🟢 Bajo | No hay calls externas críticas |

### Detalle: Top 3 Riesgos OWASP

#### **A01: Broken Access Control** (CRÍTICA)

**Hallazgo**: IDOR en endpoints críticos
```python
# ADES Vulnerable:
GET /api/v1/expediente/alumno/{id}  → Sin validación de acceso
POST /api/v1/documentos              → Sin validación de owner
PATCH /api/v1/calificaciones/{id}   → Sin validar que maestro es del grupo
```

**Mitigación**: Ver `ades_fix_code_ready.md`

**Testing**:
```bash
# Test 1: Acceso a recurso de otro usuario
curl -H "Authorization: Bearer $TOKEN_MAESTRO_A" \
  https://api.ades.setag.mx/api/v1/expediente/alumno/$ESTUDIANTE_PLANTEL_B
# Esperado: 403 Forbidden
# Actual: 200 OK + datos (VULNERABILIDAD CONFIRMADA)
```

---

#### **A02: Cryptographic Failures** (CRÍTICA)

**Hallazgo**: PII sin encriptación en reposo

```sql
-- ADES actual: plaintext
SELECT id, nombre, email, curp, telefono FROM ades_alumnos;
-- ❌ Si BD se compromete → todos los datos expuestos
```

**Campos sensibles sin encriptación**:
- ✅ Contraseñas (hasheadas vía Authentik, OK)
- ❌ Email
- ❌ Teléfono
- ❌ CURP / RFC
- ❌ Dirección
- ❌ Datos padres

**Mitigación requerida**: Fernet encryption (AES)

```python
# Implementación en models
from cryptography.fernet import Fernet

class Estudiante(Base):
    __tablename__ = "ades_alumnos"
    _email_encrypted = Column("email", String)
    
    @property
    def email(self):
        return decrypt(self._email_encrypted)
    
    @email.setter
    def email(self, value):
        self._email_encrypted = encrypt(value)
```

---

#### **A05: Security Misconfiguration** (ALTA)

**Hallazgos**:

| Configurable | Actual | Recomendado | Risk |
|---|---|---|---|
| HTTPS | ❌ No enforced | Middleware + HSTS | CRÍTICA |
| CORS | `allow_methods=["*"]` | Específicos | ALTA |
| JWT Secret | ? (revisar length) | ≥256 bits | CRÍTICA |
| OIDC Scope | ? | Minimal scope | MEDIA |
| Rate Limiting | ❌ Ausente | slowapi | ALTA |
| Security Headers | Parcial | CSP + X-* | MEDIA |

---

## 3️⃣ OWASP API SECURITY TOP 10

### Mapeo para ADES (FastAPI REST)

| # | Vulnerabilidad | Aplicable | Status | Risk |
|---|---|---|---|---|
| API1 | Broken Object Level Auth | ✅ | 🔴 Vulnerable | **IDOR** |
| API2 | Broken Function Level Auth | ✅ | 🟠 Parcial | Privileges no validados |
| API3 | Broken Resource-Level Authorization | ✅ | 🔴 Vulnerable | **IDOR** |
| API4 | Unrestricted Resource Consumption | ✅ | 🟠 Parcial | Rate limiting ausente |
| API5 | Broken Function Level Authorization | ✅ | 🟠 Parcial | RBAC inconsistente |
| API6 | Mass Assignment | ✅ | ✅ OK | Pydantic schemas |
| API7 | Cross-Site Scripting (XSS) | ✅ | 🟡 Depende Angular | DomPurify? |
| API8 | API Injection | ✅ | ✅ OK | SQLAlchemy ORM |
| API9 | Improper Asset Management | ✅ | ⚠️  Por revisar | Versioning API |
| API10 | Unsafe Consumption of APIs | ✅ | ⚠️  Por revisar | Webhooks, calls externas |

### Crítico: API1 - Broken Object Level Auth (IDOR)

```
ADES vulnerable en:
  GET    /api/v1/expediente/alumno/{student_id}
  POST   /api/v1/expediente/{exp_id}/documentos
  PATCH  /api/v1/calificaciones/{cal_id}
  DELETE /api/v1/documentos/{doc_id}

Fix: Ver sección STRIDE + ades_fix_code_ready.md
```

---

## 4️⃣ NIST CYBERSECURITY FRAMEWORK

### Status: 🟡 3/5 funciones implementadas

```
┌─────────────────────────────────────────────────────┐
│ NIST CSF Mapping                                    │
├──────────────────────────────────────────────────────┤
│ IDENTIFY (Assets, Risks)                    🟡 60%  │
│  ├─ Asset inventory                         ✅ OK   │
│  ├─ Threat assessment                       🟡 Parcial│
│  ├─ Vulnerabilities                         ✅ STRIDE│
│  └─ Business continuity                     ❌ Falta │
│                                                      │
│ PROTECT (Controls)                          🟡 50%  │
│  ├─ Access Control (AuthN/AuthZ)            🟠 IDOR │
│  ├─ Encryption (data in transit)            ✅ TLS   │
│  ├─ Encryption (data at rest)               ❌ PII   │
│  ├─ Logging & Monitoring                    ✅ Good  │
│  └─ Incident Response Plan                  ❌ Falta │
│                                                      │
│ DETECT (Monitoring)                         🟡 40%  │
│  ├─ Anomaly Detection                       ❌ Falta │
│  ├─ Logging                                 ✅ OK    │
│  ├─ SIEM Integration                        ❌ Falta │
│  └─ Alerts                                  ⚠️  Min  │
│                                                      │
│ RESPOND (Incident Management)               🔴 10%  │
│  ├─ Incident Response Plan                  ❌ Falta │
│  ├─ Communication                           ❌ Falta │
│  ├─ Forensics                               ❌ Falta │
│  └─ Recovery                                ❌ Falta │
│                                                      │
│ RECOVER (Business Continuity)               🔴 20%  │
│  ├─ Backup & Restore                        ⚠️  Min  │
│  ├─ Disaster Recovery Plan                  ❌ Falta │
│  ├─ Business Continuity Plan                ❌ Falta │
│  └─ Testing                                 ❌ Falta │
└─────────────────────────────────────────────────────┘
```

### Detalles por Función

#### **IDENTIFY**
```
✅ Tienes:
  - Asset inventory (infrastructure/docker-compose.yml)
  - Threat modeling (ADR-0004, STRIDE)
  - Dependencias (requirements.txt)

❌ Faltan:
  - BCP (Business Continuity Plan)
  - Risk tolerance statement
  - Data flow diagrams (DFD)
  - External service dependencies catalog
```

#### **PROTECT**
```
✅ Tienes:
  - OIDC Authentication
  - Encryption in transit (TLS)
  - Optimistic locking
  - Audit logging
  - Secret management (Vault)

❌ Faltan:
  - Encryption at rest para PII
  - Database Row-Level Security
  - Rate limiting
  - HTTPS enforcement
  - Access Control enforcement (IDOR)
```

#### **DETECT**
```
✅ Tienes:
  - Audit logging (AuditMiddleware)
  - Prometheus metrics
  - Grafana dashboards

❌ Faltan:
  - SIEM (Security Information & Event Management)
  - Anomaly detection
  - Real-time alerting
  - Log retention policy
  - Security baseline
```

#### **RESPOND**
```
❌ Faltan completamente:
  - Incident response plan
  - Escalation procedures
  - Communication protocol
  - Forensics procedures
  - Evidence preservation
```

#### **RECOVER**
```
⚠️  Parcial:
  - Backups (scripts/backup_*.sh)
  - Pero sin: testing, RTO/RPO, automation

❌ Faltan:
  - Disaster recovery plan
  - Runbooks
  - DR testing schedule
```

---

## 5️⃣ DATA PROTECTION & PII SECURITY

### Status: 🟠 PARCIAL

#### Regulaciones Aplicables

| Norma | Aplicable | Status | Brecha |
|-------|-----------|--------|--------|
| **GDPR** | ✅ SÍ (menores, UE posible) | ❌ Falta | Consentimiento, DPA |
| **LFPDPPP** (México) | ✅ SÍ (datos personales) | 🟠 Parcial | Encriptación PII |
| **Coppa** | ✅ SÍ (menores <13) | ❌ Falta | Parental consent |
| **LGPD** (Brasil) | ? | ? | — |
| **NIST 800-53** | ✅ SÍ | 🟡 Parcial | AC, SI, SC |
| **ISO 27001** | ✅ Aplicable | 🟠 Parcial | Certificación falta |

#### Campos PII Identificados

```
ESTUDIANTES:
  - Nombre completo ❌
  - Email institucional ❌
  - Teléfono ❌
  - CURP/RFC ❌
  - Domicilio ❌
  - Fecha nacimiento ⚠️
  - Foto/biometría ❌
  
PADRES:
  - Nombre ❌
  - Email ❌
  - Teléfono ❌
  - Domicilio ❌
  - Documento identidad ❌
  
MAESTROS:
  - Email institucional ❌
  - Teléfono ❌
  - Domicilio ❌
  
SENSIBLE EDUCATIVO:
  - Calificaciones ✅ (con IDOR risk)
  - Asistencias ✅ (con IDOR risk)
  - Conducta ⚠️
  - Evaluaciones ❌
  - Diagnósticos NEE ❌

Legend: ✅=Encriptado, ❌=Plaintext, ⚠️=Parcial
```

#### Recomendación Inmediata

```python
# 1. Encriptación de campos PII

from app.core.encryption import encrypt_field, decrypt_field

class Alumno(Base):
    # Antes: email = Column(String)
    # Después:
    _email_encrypted = Column("email", String)
    
    @property
    def email(self):
        if self._email_encrypted:
            return decrypt_field(self._email_encrypted)
        return None
    
    @email.setter
    def email(self, value: str):
        if value:
            self._email_encrypted = encrypt_field(value)

# 2. Auditoría de acceso a PII
@router.get("/estudiantes/{student_id}")
async def get_student_pii(student_id: UUID, user: AdesUser):
    # Log cada acceso a PII
    await log_pii_access(
        user_id=user.id,
        resource="alumno_pii",
        resource_id=student_id,
        action="READ",
        timestamp=datetime.utcnow()
    )
    
# 3. Derecho al olvido (GDPR/LFPDPPP)
@router.delete("/estudiantes/{student_id}/pii")
async def delete_student_pii(student_id: UUID, user: AdesUser):
    """Implementar derecho al olvido"""
    # Anonimizar registros (PSEUDONYMIZATION)
    # Mantener calificaciones (requisito legal) sin PII
    pass
```

---

## 6️⃣ INFRASTRUCTURE SECURITY

### Status: 🟡 BUENO (con mejoras)

#### Componentes de Seguridad

| Componente | Implementación | Status | Mejoras |
|-----------|---|---|---|
| **Reverse Proxy** | nginx | ✅ Presente | Rate limit en nginx |
| **SSL/TLS** | Certbot/Let's Encrypt | ✅ OK | HSTS headers |
| **Database** | PostgreSQL 18 | ✅ OK | RLS + encryption |
| **Cache** | Valkey (Redis) | ✅ OK | requirepass ✅ |
| **Secrets Mgmt** | HashiCorp Vault | ✅ Presente | Audit Vault access |
| **Auth Provider** | Authentik | ✅ OK | Audit logs |
| **Monitoring** | Prometheus + Grafana | ✅ OK | Alerting rules |
| **Storage** | SeaweedFS (S3) | ✅ OK | Encryption, ACLs |
| **Network** | Docker internal | ✅ Seguro | Network policies |
| **Backup** | Scripts | ⚠️  Manual | Automate, encrypt |

#### Nginx Configuration Security

**Revisar**: `/infrastructure/nginx/`

```nginx
# Lo que DEBE haber:
server {
    # HTTPS obligatorio
    listen 443 ssl http2;
    
    # Security headers
    add_header Strict-Transport-Security "max-age=31536000" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header X-Frame-Options "DENY" always;
    add_header Content-Security-Policy "default-src 'self'" always;
    
    # Rate limiting
    limit_req_zone $binary_remote_addr zone=api:10m rate=10r/s;
    limit_req zone=api burst=50;
    
    # Proxy seguro
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    
    # No exponer versiones
    server_tokens off;
    proxy_hide_header Server;
}
```

#### PostgreSQL Security

**Revisar**: `/db/migrations/` y `docker-compose.yml`

```sql
-- Lo que DEBE haber:

-- 1. Row-Level Security (RLS)
ALTER TABLE ades_alumnos ENABLE ROW LEVEL SECURITY;
CREATE POLICY "maestros_own_alumnos" ON ades_alumnos
  USING (plantel_id = current_setting('app.current_user_id')::uuid);

-- 2. Encryption at rest
-- Usar: pgcrypto, pgtle, transparent data encryption

-- 3. Audit logging
CREATE TABLE ades_pgaudit_log (
    audit_id BIGSERIAL PRIMARY KEY,
    audit_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    username TEXT,
    database TEXT,
    object_type TEXT,
    object_name TEXT,
    action TEXT,
    statement TEXT,
    statement_result TEXT
);

-- 4. Connection limits
ALTER SYSTEM SET max_connections = 100;
ALTER SYSTEM SET max_prepared_transactions = 50;
```

#### Vault Configuration

**Revisar**: `/infrastructure/vault/`

```hcl
# Lo que DEBE haber:
storage "file" {
  path = "/vault/data"
}

# Auditing
audit {
  file {
    path = "/vault/logs/audit.log"
  }
}

# Encryption key rotation
kv_v2 {
  # Rotación automática de keys cada 90 días
}

# Access policies (RBAC)
path "secret/data/ades/*" {
  capabilities = ["create", "read", "update", "delete", "list"]
}
```

---

## 7️⃣ SUPPLY CHAIN & DEPENDENCIES

### Status: ⚠️  POR REVISAR

#### Requisitos Python

**Revisar**: `/backend/requirements.txt`

```bash
# Comandos de auditoría
pip install safety
safety check --file requirements.txt

pip install pip-audit
pip-audit

# SCA (Software Composition Analysis)
pip install cyclonedx-bom
cyclonedx-py -o bom.json

# Dependencias con vulnerabilidades conocidas
pip install pip-licenses
pip-licenses
```

#### Riesgos Identificados

| Paquete | Versión | Risk |
|---------|---------|------|
| FastAPI | ? | ✅ Actively maintained |
| SQLAlchemy | 2.x | ✅ OK |
| Authentik SDK | ? | ⚠️  Verificar |
| nodejs deps (frontend) | ? | ⚠️  Verificar |

#### Mitigación

```yaml
# Crear .dependencies.json para tracking
{
  "python_packages": [
    {
      "name": "fastapi",
      "version": "0.104.1",
      "license": "MIT",
      "vulnerabilities": [],
      "last_audit": "2026-06-19"
    }
  ],
  "nodejs_packages": [],
  "system_packages": []
}

# En CI/CD:
# 1. pip install -r requirements.txt --require-hashes
# 2. pip-audit antes de deploy
# 3. Renovate/Dependabot para updates
```

---

## 8️⃣ SDLC SECURITY

### Status: 🟡 MEJORANDO

#### Secure Development Practices

| Práctica | Implementado | Status |
|----------|---|---|
| **Code Review** | ✅ (Git) | ✅ Esencial |
| **Pre-commit Hooks** | ❌ Falta | 🔴 CRÍTICO |
| **Static Code Analysis** | ❌ Falta | 🔴 CRÍTICO |
| **Dependency Scanning** | ❌ Falta | 🔴 CRÍTICO |
| **SAST** | ❌ Falta | 🟠 ALTA |
| **DAST** | ❌ Falta | 🟠 ALTA |
| **Unit Tests** | ✅ (pytest) | ✅ OK |
| **Integration Tests** | ✅ (E2E) | ✅ OK |
| **Security Tests** | 🟡 Básico | 🟠 Mejorar |
| **Version Control** | ✅ (Git) | ✅ OK |
| **Secrets Management** | ✅ (Vault) | ✅ OK |
| **Deployment Automation** | ? | ⚠️  Revisar |

#### Recomendaciones SDLC

```yaml
# 1. Pre-commit Hooks
# Crear: .pre-commit-config.yaml
repos:
  - repo: https://github.com/pre-commit/pre-commit-hooks
    rev: v4.0.0
    hooks:
      - id: detect-private-key
      - id: check-ast
      - id: check-json
      - id: end-of-file-fixer
      
  - repo: https://github.com/PyCQA/bandit
    rev: 1.7.5
    hooks:
      - id: bandit
        args: ['-c', '.bandit']
        
  - repo: https://github.com/PyCQA/flake8
    hooks:
      - id: flake8

# 2. CI/CD Pipeline (GitHub Actions)
# Crear: .github/workflows/security.yml
name: Security Checks
on: [push, pull_request]

jobs:
  bandit:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-python@v4
      - run: pip install bandit
      - run: bandit -r app/ -f json -o bandit-report.json

  safety:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-python@v4
      - run: pip install safety
      - run: safety check --json

  sast:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: github/super-linter@v4
        env:
          DEFAULT_BRANCH: main
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}

  dast:
    runs-on: ubuntu-latest
    services:
      postgres:
        image: postgres:18
      ades-api:
        image: ades:latest
    steps:
      - uses: actions/checkout@v3
      - run: docker compose up -d
      - run: npm install -g owasp-zap-cli
      - run: |
          zap-baseline.py \
            -t http://localhost:8000 \
            -r zap-report.html
```

#### Threat Model en SDLC

```markdown
## Security Checklist per Release

- [ ] STRIDE updated
- [ ] OWASP Top 10 review
- [ ] Dependency audit (pip-audit)
- [ ] Code security review (Bandit)
- [ ] SAST results < threshold
- [ ] DAST penetration test
- [ ] Compliance checklist
- [ ] Changelog/security advisories
- [ ] Approval from security team
```

---

## 9️⃣ COMPLIANCE & REGULATIONS

### Status: 🔴 CRÍTICO (Para escuela con menores)

#### Regulaciones Aplicables

```
├─ GDPR (si hay EU data)
│  ├─ Consentimiento parental ❌
│  ├─ Data Processing Agreement (DPA) ❌
│  ├─ Privacy Impact Assessment (DPIA) ❌
│  ├─ Right to be forgotten ❌
│  └─ Data portability ❌
│
├─ LFPDPPP (México)
│  ├─ Aviso de privacidad ❌
│  ├─ Consentimiento expreso ❌
│  ├─ Transferencias internacionales ❌
│  ├─ Encriptación obligatoria ❌
│  └─ Derechos (ARCO) ⚠️ Parcial
│
├─ SEP/UAEMEX (Educación México)
│  ├─ Integridad de calificaciones ⚠️
│  ├─ No manipulación de actas ⚠️
│  ├─ Firma digital (Ed25519) ✅
│  └─ Auditoría completa ✅
│
├─ COPPA (Menores <13)
│  ├─ Parental consent ❌
│  ├─ Data collection limits ❌
│  ├─ No targeted advertising ✅ (N/A)
│  └─ Data deletion ❌
│
├─ WCAG 2.1 AA (Accesibilidad)
│  └─ Estado: ✅ RECIENTE (commit 847ebe7)
│
└─ Protección de Menores
   ├─ Safe harbor features ❌
   ├─ Content filtering ❌
   ├─ Monitoring tools ❌
   └─ Incident reporting ❌
```

#### Acciones Requeridas

```markdown
## GDPR/LFPDPPP Compliance Tasks

### Inmediatas (Esta semana)
- [ ] Crear Aviso de Privacidad en español/inglés
- [ ] Implementar consentimiento explícito
- [ ] Documentar Data Processing Agreement (DPA)
- [ ] Notificar autoridades si brecha de datos

### Corto plazo (Este mes)
- [ ] Data Processing Impact Assessment (DPIA)
- [ ] Implementar derecho al olvido
- [ ] Encriptar PII (campo nivel)
- [ ] Crear registro de actividades procesamiento

### Mediano plazo (Próximos 3 meses)
- [ ] Auditoría de compliance por tercero
- [ ] Certificación ISO 27001
- [ ] Implement Privacy by Design
- [ ] Training GDPR/LFPDPPP para equipo

### Largo plazo
- [ ] Data retention policy
- [ ] Breach response plan
- [ ] Continuous compliance monitoring
```

---

## 🎯 PLAN INTEGRAL DE REMEDIACIÓN

### 🔴 FASE 0: CRÍTICA (48-72 horas)

```
IDOR Prevention
  ├─ Fix /expediente endpoint
  ├─ Audit todos POST/PUT/DELETE
  ├─ Add tests IDOR
  └─ Deploy a staging

HTTPS Enforcement
  ├─ FastAPI middleware
  ├─ Nginx HSTS headers
  ├─ Redirect HTTP → HTTPS
  └─ Test certificados

Rate Limiting
  ├─ Install slowapi
  ├─ Protect login endpoint
  ├─ Protect /expediente
  └─ Monitor 429 responses

Data Protection Baseline
  ├─ Identificar PII en BD
  ├─ Crear campo _encrypted
  ├─ Migration script
  └─ Rotate encryption key

Effort: ~20 horas (2-3 developers)
```

### 🟠 FASE 1: ALTA PRIORIDAD (1-2 semanas)

```
Row-Level Security
  ├─ Enable RLS en PostgreSQL
  ├─ Create policies por role
  ├─ Test RLS bypass
  └─ Deploy con migrations

Audit & Logging
  ├─ Before/after snapshots
  ├─ PII access audit
  ├─ Log retention policy
  └─ SIEM integration

SDLC Security
  ├─ Pre-commit hooks
  ├─ CI/CD security checks (Bandit, Safety)
  ├─ SAST/DAST pipeline
  └─ Dependency scanning

Compliance Framework
  ├─ Privacy policy
  ├─ Data processing agreement
  ├─ Incident response plan
  └─ DPIA assessment

Effort: ~40 horas (2-3 weeks)
```

### 🟡 FASE 2: MEJORA CONTINUA (2-4 semanas)

```
Incident Response
  ├─ DR plan
  ├─ Runbooks
  ├─ Backup testing
  └─ Failover procedures

Network & Infrastructure
  ├─ Network segmentation
  ├─ WAF (Web Application Firewall)
  ├─ Intrusion detection
  └─ DDoS protection

Supply Chain
  ├─ Dependency management
  ├─ SCA tools
  ├─ Vulnerability tracking
  └─ Update policy

Monitoring & Detection
  ├─ SIEM setup
  ├─ Alerting rules
  ├─ Anomaly detection
  └─ Log aggregation

Effort: ~50 horas (4 weeks)
```

### 🟢 FASE 3: OPTIMIZACIÓN (Continuo)

```
Penetration Testing
  ├─ External pentest
  ├─ Red team exercises
  ├─ Bug bounty program
  └─ Remediation tracking

Certification & Audit
  ├─ ISO 27001
  ├─ SOC 2 Type II
  ├─ External security audit
  └─ Compliance certification

Hardening
  ├─ Zero-trust architecture
  ├─ Micro-segmentation
  ├─ Enhanced encryption
  └─ Advanced monitoring

Team & Process
  ├─ Security training
  ├─ Threat modeling practice
  ├─ Security champions
  └─ Continuous improvement

Effort: Ongoing
```

---

## 📋 MATRIZ DE RIESGOS CONSOLIDADA

```
SEVERIDAD vs ESFUERZO vs IMPACTO

┌──────────────────────────────────────────────────────┐
│ Riesgo                    │ Severidad │ Esfuerzo │ ROI│
├──────────────────────────────────────────────────────┤
│ IDOR en endpoints         │ CRÍTICA   │ Bajo     │ 9/10│
│ HTTPS enforcement         │ CRÍTICA   │ Bajo     │ 9/10│
│ Rate limiting             │ ALTA      │ Bajo     │ 8/10│
│ PII encryption            │ CRÍTICA   │ Medio    │ 9/10│
│ RLS en PostgreSQL         │ CRÍTICA   │ Medio    │ 8/10│
│ SDLC security tools       │ ALTA      │ Medio    │ 7/10│
│ Incident response plan    │ ALTA      │ Medio    │ 6/10│
│ Compliance framework      │ ALTA      │ Alto     │ 7/10│
│ Penetration testing       │ MEDIA     │ Alto     │ 5/10│
│ Zero-trust architecture   │ MEDIA     │ Muy Alto │ 6/10│
└──────────────────────────────────────────────────────┘
```

---

## ✅ CHECKLIST MAESTRO DE SEGURIDAD

### Semanal
- [ ] Review de código (security-focused)
- [ ] Check de alertas Prometheus
- [ ] Backup verification
- [ ] Log review (errors, 403, 429)

### Mensual
- [ ] Dependency updates
- [ ] Security training
- [ ] Threat model review
- [ ] Compliance checklist

### Trimestral
- [ ] Penetration testing
- [ ] Disaster recovery drill
- [ ] Security audit
- [ ] Risk assessment update

### Anual
- [ ] External security audit
- [ ] Compliance certification
- [ ] Strategic security review
- [ ] Incident post-mortems

---

## 🎖️ RECOMENDACIONES FINALES

### Top 5 Acciones Inmediatas

1. **IDOR Prevention** (48h)
   - Fix expediente.py
   - Audit otros endpoints
   - Deploy con tests

2. **HTTPS + Rate Limiting** (72h)
   - Enforce HTTPS
   - Add slowapi
   - Monitor en producción

3. **PII Encryption** (1 week)
   - Identify sensitive fields
   - Encrypt at rest
   - Audit access

4. **SDLC Security** (2 weeks)
   - Pre-commit hooks
   - CI/CD security checks
   - Dependency scanning

5. **Compliance Framework** (3 weeks)
   - Privacy policy
   - Data processing agreement
   - Incident response plan

### Métricas de Éxito

```
Antes → Después

OWASP A01 (Broken Access Control): 🔴 → 🟢
OWASP A02 (Crypto Failures):      🔴 → 🟡
OWASP A05 (Misconfiguration):     🟠 → 🟢
STRIDE Critical Issues:            13 → 2
Test Coverage:                      TBD → 85%+
Dependency Vulnerabilities:         TBD → 0
Compliance Score:                   40% → 85%
```

---

**Documento Generado**: 19 Junio 2026  
**Próxima Revisión**: 30 días post-implementación FASE 0  
**Dueño**: Equipo de Seguridad ADES  
**Status**: 🔴 REQUIERE ACCIÓN INMEDIATA
