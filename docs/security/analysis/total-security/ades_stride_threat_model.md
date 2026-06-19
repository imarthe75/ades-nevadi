# STRIDE Threat Model: ADES Nevadi
**Fecha**: Junio 2026 | **Versión**: 1.0 | **Estado**: Revisión activa

---

## 1. SISTEMA: MAPEO DE FRONTERAS

### Componentes Principales
```
┌─────────────────────────────────────────────────────────────┐
│                   CLIENTE (Angular 19+)                      │
│         ├─ PrimeNG 21+ Components                            │
│         ├─ Gradebook Inline Editing                          │
│         └─ OIDC Token Storage (localStorage/sessionStorage)  │
└──────────────────────┬──────────────────────────────────────┘
                       │ HTTPS/TLS 1.3+
┌──────────────────────▼──────────────────────────────────────┐
│        BACKEND (FastAPI + Python 3.12)                       │
│  ├─ OAuth2/OIDC Integration (Authentik)                     │
│  ├─ SQLAlchemy 2.x Async ORM                                │
│  ├─ REST/GraphQL Endpoints (?)                              │
│  ├─ WebSocket handlers (?)                                  │
│  └─ Audit Logging System                                    │
└──────────────────────┬──────────────────────────────────────┘
                       │ TCP/SSH
┌──────────────────────▼──────────────────────────────────────┐
│        DATOS (PostgreSQL 18)                                 │
│  ├─ Tablas con prefix ades_*                                │
│  ├─ Row-level security (RLS) [?]                            │
│  ├─ Encrypted fields [?]                                    │
│  └─ Audit tables (ades_audit_log, etc)                       │
└─────────────────────────────────────────────────────────────┘
        │
        ├─ Apache Superset (Reporting, lectura)
        └─ Authentik (External OIDC Identity Provider)
```

### Entry Points (Superficies de ataque)
- **Públicas**: Endpoint de login OIDC, cualquier endpoint sin requiere_auth
- **Autenticadas**: CRUD de estudiantes, calificaciones, catálogos, privilegios
- **Administrativas**: Gestión de usuarios, auditoría, variables de sistema
- **Internas**: Background jobs, scripts de migración, webhooks Authentik
- **Terceros**: Apache Superset (acceso a lectura de datos)

---

## 2. ANÁLISIS STRIDE

### **S: SPOOFING (Suplantación de Identidad)**

#### Amenazas Identificadas

| ID | Amenaza | Severidad | Estado |
|----|---------|-----------|--------|
| S-001 | OIDC Token Hijacking vía XSS | 🔴 CRÍTICA | ⚠️ Vulnerable |
| S-002 | Falsificación de JWT si secret débil | 🔴 CRÍTICA | ⚠️ Vulnerable |
| S-003 | Session Fixation en login flow | 🟠 ALTA | ⚠️ Por evaluar |
| S-004 | CSRF en endpoints que modifican estado | 🟠 ALTA | ⚠️ Por evaluar |
| S-005 | Account enumeration en login | 🟡 MEDIA | ⚠️ Probable |
| S-006 | API key hardcoding en credenciales Authentik | 🔴 CRÍTICA | ⚠️ Probable |

#### Evaluación Detallada

**S-001: OIDC Token Hijacking (XSS)**
```
CONTEXTO:
- Angular guarda tokens OIDC en localStorage/sessionStorage
- Token contiene claims con role/privilege info
- Vulnerable a XSS attacks si Angular binding no sanitiza

RIESGO:
- Attacker inyecta JS malicioso → localStorage → exfiltrar token
- Token usado para suplantar usuario en API
- Acceso a datos/funciones del usuario suplantado
```

**Recomendaciones (Implementar INMEDIATO)**:
- [ ] Usar **httpOnly cookies** para almacenar tokens OIDC (no localStorage)
- [ ] Implementar **SameSite=Strict** en cookies
- [ ] DomPurify sanitización en Angular templates
- [ ] Content Security Policy (CSP) headers
- [ ] Token rotation: refresh token cada 15min, re-issue en cada request

**S-002: JWT Secret Débil**
```
CONTEXTO:
- FastAPI genera/valida JWTs para sesiones
- Si secret es débil (< 256 bits), compromiso total

RIESGO:
- Attacker puede generar JWTs válidos para cualquier usuario
- Escalación de privilegios sencilla
```

**Recomendaciones**:
- [ ] Verificar `SECRET_KEY` en FastAPI: mínimo 64 bytes (512 bits)
- [ ] Usar `secrets.token_urlsafe(64)` para generación
- [ ] NO hardcodear en código → usar env variables/vault

**S-003: CSRF en endpoints POST/PUT/DELETE**
```
CONTEXTO:
- Angular formularios pueden ser targetados por CSRF
- Si SameSite=Lax, attacker puede forjar request desde otro sitio

RIESGO:
- Cross-site request forgery: admin crea estudiante sin autorización
- Cambio de calificaciones no consentido
```

**Recomendaciones**:
- [ ] Implementar **CSRF tokens** en cada formulario
- [ ] FastAPI: usar `starlette.middleware.csrf.CSRFMiddleware`
- [ ] Validar `X-CSRF-Token` header en requests POST/PUT/DELETE
- [ ] Angular: `HttpClientXsrfModule` automáticamente inyecta token

---

### **T: TAMPERING (Manipulación de Datos)**

#### Amenazas Identificadas

| ID | Amenaza | Severidad | Estado |
|----|---------|-----------|--------|
| T-001 | SQL Injection vía ORM (SQLAlchemy) | 🟢 BAJA | ✅ Mitigado (ORM) |
| T-002 | NoSQL Injection en query filters | 🟢 N/A | N/A |
| T-003 | Race condition en canje de descuentos/privilegios | 🔴 CRÍTICA | ⚠️ Probable |
| T-004 | Modification de calificaciones sin auditoria | 🔴 CRÍTICA | ⚠️ Por evaluar |
| T-005 | Bypass de row-level security (RLS) | 🔴 CRÍTICA | ⚠️ Por evaluar |
| T-006 | Data tampering en tránsito (HTTPS no obligatorio) | 🟠 ALTA | ⚠️ Por evaluar |
| T-007 | Bulk edit sin validación de permisos por row | 🔴 CRÍTICA | ⚠️ Probable |

#### Evaluación Detallada

**T-003: Race Condition en Gradebook**
```
CONTEXTO (basado en arquitectura gradebook):
- Inline editable grid: estudiante + calificación actualiza vía PATCH
- Multiple requests simultáneos podrían generar inconsistencies
- PostgreSQL no tiene optimistic locking automático sin configuración

EJEMPLO VULNERABLE:
Thread-A: READ calificación estudiante (75)
Thread-B: READ calificación estudiante (75)
Thread-A: UPDATE calificación a 80
Thread-B: UPDATE calificación a 70  ← SOBRESCRIBE cambio de A

RIESGO:
- Calificaciones inconsistentes
- Auditoría imprecisa de cambios
- Violación de integridad de datos críticos
```

**Recomendaciones**:
```python
# Implementar versioning en tabla ades_gradebook_entry

# OPCIÓN 1: Optimistic Locking (SQLAlchemy)
class AdesGradebookEntry(Base):
    __tablename__ = "ades_gradebook_entry"
    id = Column(UUID(as_uuid=True), primary_key=True)
    version = Column(Integer, default=1)  # ← Version counter
    calification = Column(Float)
    
    @staticmethod
    async def update_with_lock(session, entry_id, new_score, expected_version):
        stmt = (
            update(AdesGradebookEntry)
            .where(AdesGradebookEntry.id == entry_id)
            .where(AdesGradebookEntry.version == expected_version)  # ← Guard clause
            .values(calification=new_score, version=expected_version + 1)
        )
        result = await session.execute(stmt)
        if result.rowcount == 0:
            raise StaleDataError("Entry was modified by another user")

# OPCIÓN 2: Pessimistic Locking (PostgreSQL FOR UPDATE)
async def update_gradebook_entry(session, entry_id, new_score):
    entry = await session.execute(
        select(AdesGradebookEntry)
        .where(AdesGradebookEntry.id == entry_id)
        .with_for_update()  # ← Row-level lock
    )
    entry.calification = new_score
    await session.commit()
```

- [ ] Implementar versioning en **todas las tablas críticas** (estudiantes, calificaciones, catálogos)
- [ ] API response incluya `version` en JSON → cliente envía en siguiente UPDATE
- [ ] FastAPI valida `expected_version` antes de update

**T-004: Auditoría Insuficiente de Cambios Críticos**
```
CONTEXTO:
- Calificaciones son datos críticos en sistema educativo
- Must track: quién cambió, cuándo, antes/después

RIESGO:
- Maestro A modifica nota de estudiante B
- Sin auditoría, no se sabe quién lo hizo
- Violación de compliance educativo (SEP/UAEMEX)
```

**Recomendaciones**:
```python
# Tabla de auditoría para cambios críticos
class AdesAuditLog(Base):
    __tablename__ = "ades_audit_log"
    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid7)
    entity_type = Column(String)  # "gradebook_entry", "estudiante", etc
    entity_id = Column(UUID(as_uuid=True))
    action = Column(String)  # "create", "update", "delete"
    old_values = Column(JSON)  # ← Before snapshot
    new_values = Column(JSON)  # ← After snapshot
    changed_by = Column(UUID(as_uuid=True))  # User who made change
    timestamp = Column(DateTime, default=utc_now)
    ip_address = Column(String)
    user_agent = Column(String)

# En endpoint de actualización:
async def patch_gradebook_entry(
    entry_id: UUID,
    update_data: GradebookUpdate,
    current_user: User = Depends(get_current_user),
    request: Request = None
):
    old_entry = await session.get(AdesGradebookEntry, entry_id)
    new_entry = old_entry.copy(update={**update_data.dict()})
    
    # Log antes de commit
    audit_log = AdesAuditLog(
        entity_type="gradebook_entry",
        entity_id=entry_id,
        action="update",
        old_values=old_entry.dict(),
        new_values=new_entry.dict(),
        changed_by=current_user.id,
        ip_address=request.client.host,
        user_agent=request.headers.get("user-agent")
    )
    session.add(audit_log)
    session.commit()
```

- [ ] Crear tabla `ades_audit_log` con snapshots before/after
- [ ] Inmutable append-only: nunca delete/update de auditoría
- [ ] Incluir contexto: IP, user-agent, timestamp UTC
- [ ] Crear índices en `entity_type`, `entity_id`, `timestamp` para queries
- [ ] Superset dashboard para auditoría: "Cambios de Calificaciones por Usuario/Fecha"

**T-005: Row-Level Security (RLS) en PostgreSQL**
```
CONTEXTO:
- Maestro de primaria NO debe ver calificaciones de secundaria
- Estudiante solo ve sus propias calificaciones
- Sin RLS en DB, rely 100% en aplicación

RIESGO:
- Bug en FastAPI filter → maestro ve datos ajenos
- SQL injection (raro con ORM, pero posible con raw queries)
- Datos leakage en backups sin cifrado
```

**Recomendaciones**:
```sql
-- Habilitar RLS en PostgreSQL
ALTER TABLE ades_gradebook_entry ENABLE ROW LEVEL SECURITY;

-- Política: Maestro solo ve estudiantes de su grupo
CREATE POLICY gradebook_teacher_policy ON ades_gradebook_entry
USING (
  EXISTS (
    SELECT 1 FROM ades_group_teacher gt
    JOIN ades_gradebook_entry gbe ON gbe.group_id = gt.group_id
    WHERE gt.user_id = current_setting('app.current_user_id')::uuid
      AND gbe.id = ades_gradebook_entry.id
  )
);

-- Política: Estudiante solo ve sus propias calificaciones
CREATE POLICY gradebook_student_policy ON ades_gradebook_entry
USING (
  student_id = current_setting('app.current_user_id')::uuid
);

-- Política: Admin ve todo (sin filtro)
CREATE POLICY gradebook_admin_policy ON ades_gradebook_entry
USING (
  (SELECT role FROM ades_user WHERE id = current_setting('app.current_user_id')::uuid) = 'ADMIN'
);
```

- [ ] Implementar RLS en tablas sensibles: `ades_gradebook_entry`, `ades_student`, `ades_student_enrollment`
- [ ] FastAPI: setear `app.current_user_id` en PostgreSQL session context en cada request
- [ ] Pruebas de RLS: verificar que roles ven solo datos permitidos

**T-006: HTTPS No Obligatorio**
```
RIESGO:
- Si API acepte HTTP plano, attacker puede MITM y modificar requests
- Especialmente crítico con inline editable grids (muchos PATCH requests)
```

**Recomendaciones**:
- [ ] Forzar HTTPS en reverse proxy (nginx/CloudFlare)
- [ ] FastAPI: Agregar middleware que rechaza HTTP plano
```python
from fastapi.middleware.httpsredirect import HTTPSRedirectMiddleware
app.add_middleware(HTTPSRedirectMiddleware)
```
- [ ] HSTS header: `Strict-Transport-Security: max-age=31536000; includeSubDomains`

---

### **R: REPUDIATION (Repudio - Negación)**

#### Amenazas Identificadas

| ID | Amenaza | Severidad | Estado |
|----|---------|-----------|--------|
| R-001 | Admin niega cambios que realizó | 🔴 CRÍTICA | ⚠️ Sin mitigación |
| R-002 | Logs no firmados, pueden ser alterados | 🟠 ALTA | ⚠️ Sin mitigación |
| R-003 | Auditoría borrrable por admin | 🔴 CRÍTICA | ⚠️ Sin mitigación |
| R-004 | No hay non-repudiation de calificaciones | 🟠 ALTA | ⚠️ Sin mitigación |

#### Evaluación Detallada

**R-001/R-003: Auditoría Inmutable**
```
CONTEXTO:
- Instituto Nevadi es responsable ante SEP/UAEMEX por calificaciones
- Si auditoría se puede borrar/modificar, no hay accountability

RIESGO:
- Admin: "Nunca cambié esa calificación" (pero logs fueron eliminados)
- Imposible de auditar externos (SEP)
```

**Recomendaciones**:
```sql
-- Tabla immutable de auditoría (append-only)
CREATE TABLE ades_audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- ... campos ...
    hash_previous BYTEA,  -- SHA256 hash de record anterior
    hash_current BYTEA    -- SHA256 hash de este record
);

-- Trigger que previene UPDATE/DELETE en auditoría
CREATE OR REPLACE FUNCTION prevent_audit_tampering()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Auditoría es inmutable: no se permite DELETE/UPDATE';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_immutability_trigger
BEFORE UPDATE OR DELETE ON ades_audit_log
FOR EACH ROW EXECUTE FUNCTION prevent_audit_tampering();
```

- [ ] Implementar tabla `ades_audit_log` **append-only**
- [ ] No permitir UPDATE/DELETE en logs (trigger en PostgreSQL)
- [ ] Backup diario a **almacenamiento inmutable** (S3 con Object Lock, o similar)
- [ ] Cadena de hashes: cada log referencia hash del anterior
- [ ] Dashboard Superset de auditoría sin acceso de admin a modificar

**R-002: Firma de Logs**
```
CONTEXTO:
- Logs en Superset pueden ser modificados si DB se compromete

RIESGO:
- Attacker borra evidencia de intrusión
```

**Recomendaciones**:
```python
# Hashlib para chain-of-hashes
import hashlib
from datetime import datetime

def create_audit_entry(entity_type, entity_id, action, old_val, new_val, user_id):
    # 1. Obtener hash del log anterior
    last_log = session.query(AdesAuditLog).order_by(
        AdesAuditLog.created_at.desc()
    ).first()
    previous_hash = last_log.current_hash if last_log else "genesis"
    
    # 2. Crear entrada
    entry = AdesAuditLog(
        entity_type=entity_type,
        entity_id=entity_id,
        action=action,
        old_values=old_val,
        new_values=new_val,
        changed_by=user_id,
        created_at=datetime.utcnow(),
        previous_hash=previous_hash  # Enlazar al anterior
    )
    
    # 3. Calcular hash de esta entrada (immutable)
    data_to_hash = f"{entry.created_at}{entry.entity_type}{entry.action}{entry.changed_by}"
    entry.current_hash = hashlib.sha256(data_to_hash.encode()).hexdigest()
    
    session.add(entry)
    session.commit()
```

- [ ] Implementar SHA256 chain-of-hashes en auditoría
- [ ] Verificar integridad de logs en alertas (si chain se quiebra)

---

### **I: INFORMATION DISCLOSURE (Divulgación de Información)**

#### Amenazas Identificadas

| ID | Amenaza | Severidad | Estado |
|----|---------|-----------|--------|
| I-001 | Stack traces expuestos en errores | 🟠 ALTA | ⚠️ Probable |
| I-002 | PII en logs sin redacción | 🟠 ALTA | ⚠️ Probable |
| I-003 | Datos en tránsito sin cifrado (field-level) | 🟠 ALTA | ⚠️ Por evaluar |
| I-004 | Superset expone datos sensibles sin RBAC | 🔴 CRÍTICA | ⚠️ Probable |
| I-005 | Información de usuario enumerable (email, ID) | 🟡 MEDIA | ⚠️ Probable |
| I-006 | Database backups sin cifrado | 🟠 ALTA | ⚠️ Probable |
| I-007 | Credenciales en environment variables visibles | 🔴 CRÍTICA | ⚠️ Probable |

#### Evaluación Detallada

**I-001: Error Messages y Stack Traces**
```
CONTEXTO:
- FastAPI por defecto expone stack traces en dev mode
- Puede revelar rutas de código, variables internas, DB schema

RIESGO:
- Attacker ve: "SELECT * FROM ades_user WHERE email = ?" → conoce schema
```

**Recomendaciones**:
```python
# En FastAPI main.py
import logging
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

# Logging seguro
logging.basicConfig(
    level=logging.INFO,  # NO DEBUG en producción
    format='%(asctime)s %(name)s %(levelname)s: %(message)s'
)

@app.exception_handler(Exception)
async def general_exception_handler(request, exc):
    # En DEV: loguear detalles
    if settings.ENVIRONMENT == "development":
        logger.exception(f"Error en {request.url}")
        return JSONResponse(
            status_code=500,
            content={"detail": str(exc)}  # ← Incluir detalle
        )
    
    # En PROD: respuesta genérica
    logger.exception(f"Error interno en {request.url}")  # ← Log interno
    return JSONResponse(
        status_code=500,
        content={"detail": "Error interno del servidor"}  # ← Genérico
    )

@app.exception_handler(RequestValidationError)
async def validation_exception_handler(request, exc):
    # Loguear pero NO exponer detalles específicos
    logger.warning(f"Validación fallida en {request.url}: {exc}")
    return JSONResponse(
        status_code=422,
        content={"detail": "Datos inválidos"}  # ← Genérico
    )
```

- [ ] Configurar FastAPI para NO exponer stack traces en producción
- [ ] `settings.ENVIRONMENT` control: "development" vs "production"
- [ ] Loguear detalles internamente, pero respuestas genéricas al cliente

**I-004: Superset RBAC**
```
CONTEXTO:
- Superset tiene acceso de lectura a PostgreSQL
- Sin RBAC fino, todos los usuarios ven todos los datos

RIESGO:
- Maestro de primaria ve salarios de maestros
- Estudiante ve calificaciones de otros
- Administrativo ve PHI (datos personales)
```

**Recomendaciones**:
```sql
-- Database user con permisos limitados para Superset
CREATE ROLE superset_readonly;
GRANT CONNECT ON DATABASE nevadi_db TO superset_readonly;

-- Superset solo accede a tablas específicas (no PII)
GRANT USAGE ON SCHEMA public TO superset_readonly;
GRANT SELECT ON TABLE ades_gradebook_entry TO superset_readonly;
GRANT SELECT ON TABLE ades_group TO superset_readonly;
GRANT SELECT ON TABLE ades_academic_level TO superset_readonly;

-- REVOKE acceso a tablas sensibles
REVOKE SELECT ON TABLE ades_user TO superset_readonly;  -- Usuarios (PHI)
REVOKE SELECT ON TABLE ades_audit_log TO superset_readonly;  -- Auditoría

-- Crear vista segura para reportes de Superset
CREATE VIEW v_gradebook_report_safe AS
SELECT
    ge.id,
    ge.calification,
    ge.subject_id,
    ge.created_at,
    -- NO incluir: user_id, email, phone del maestro
    -- Usar hash si necesita relación
    '***' AS teacher_name_redacted
FROM ades_gradebook_entry ge;

GRANT SELECT ON TABLE v_gradebook_report_safe TO superset_readonly;
```

- [ ] Crear **readonly database user** para Superset (no admin)
- [ ] REVOKE acceso a tablas con PII: usuarios, auditoría, credenciales
- [ ] Usar vistas SQL que redactan datos sensibles
- [ ] Superset RBAC: cada rol solo ve dashboards asignados
- [ ] Ejemplo: "Maestro" role → solo dashboards de su grupo

**I-007: Credenciales en Environment Variables**
```
CONTEXTO:
- DATABASE_URL, OIDC_CLIENT_SECRET pueden estar en .env
- Si .env se commitea a git → exposición permanente

RIESGO:
- Attacker clona repo → acceso a DB, credenciales Authentik
```

**Recomendaciones**:
```bash
# .gitignore (verificar que existe)
echo ".env*" >> .gitignore
echo "secrets/" >> .gitignore
echo ".agents/secrets.json" >> .gitignore

# Usar .env.example sin secretos
cat > .env.example << 'EOF'
DATABASE_URL=postgresql://user:password@localhost/nevadi_db
AUTHENTIK_CLIENT_ID=your-client-id-here
AUTHENTIK_CLIENT_SECRET=your-secret-here
SECRET_KEY=your-jwt-secret-here
EOF

# En CI/CD (GitHub Actions, etc), inyectar secrets
# NO commitear .env
```

- [ ] `.gitignore` contiene `.env*`, `secrets/`, archivos de credenciales
- [ ] Verificar histórico de git: `git log --all --full-history -S DATABASE_URL`
- [ ] Usar secret management: HashiCorp Vault, AWS Secrets Manager, o Google Secret Manager
- [ ] En CI/CD: inyectar secrets desde variables de entorno, NO from .env files

**I-003: Datos Sensibles Sin Cifrado a Nivel de Campo**
```
CONTEXTO:
- Emails, teléfonos, SSN de estudiantes en PostgreSQL en plaintext

RIESGO:
- Database dump comprometido → todos los datos expuestos
```

**Recomendaciones**:
```python
from cryptography.fernet import Fernet
import os

# Generar clave (una sola vez, guardar en vault)
# cipher_key = Fernet.generate_key()
cipher_key = os.getenv("ENCRYPTION_KEY").encode()  # 32 bytes base64
cipher = Fernet(cipher_key)

class AdesStudent(Base):
    __tablename__ = "ades_student"
    id = Column(UUID, primary_key=True)
    
    # Campos sensibles cifrados
    _email_encrypted = Column("email", String, nullable=False)
    _phone_encrypted = Column("phone", String, nullable=False)
    
    @property
    def email(self):
        return cipher.decrypt(self._email_encrypted.encode()).decode()
    
    @email.setter
    def email(self, value: str):
        self._email_encrypted = cipher.encrypt(value.encode()).decode()

# Migration: cifrar datos existentes
async def encrypt_existing_pii():
    students = await session.execute(select(AdesStudent))
    for student in students.scalars():
        student.email = student.email  # Trigger setter
    await session.commit()
```

- [ ] Cifrar campos PII con `cryptography.fernet` (AES)
- [ ] Almacenar clave en secret manager, no en code
- [ ] Migración: cifrar datos existentes en background
- [ ] Búsquedas por PII: usar hashes + índices (no plaintext) para performance

---

### **D: DENIAL OF SERVICE (Denegación de Servicio)**

#### Amenazas Identificadas

| ID | Amenaza | Severidad | Estado |
|----|---------|-----------|--------|
| D-001 | Rate limiting ausente en API | 🟠 ALTA | ⚠️ Probable |
| D-002 | Query expensive sin timeouts | 🟠 ALTA | ⚠️ Probable |
| D-003 | Bulk upload sin límites de tamaño | 🟠 ALTA | ⚠️ Por evaluar |
| D-004 | WebSocket connections ilimitadas | 🟡 MEDIA | ⚠️ Por evaluar |
| D-005 | N+1 queries en gradebook list | 🟠 ALTA | ⚠️ Probable |

#### Evaluación Detallada

**D-001: Rate Limiting**
```
CONTEXTO:
- Sin rate limiting, attacker puede hacer:
  - 1000 login attempts/seg (brute force)
  - 10000 student list queries (enumeration)
  - DDOS de aplicación

RIESGO:
- Disponibilidad: API sin respuesta para usuarios legítimos
```

**Recomendaciones**:
```python
from slowapi import Limiter
from slowapi.util import get_remote_address
from slowapi.errors import RateLimitExceeded

limiter = Limiter(key_func=get_remote_address)
app.state.limiter = limiter

# Limites por endpoint
@app.post("/auth/login")
@limiter.limit("5/minute")  # ← 5 intentos por minuto
async def login(creds: LoginRequest):
    ...

@app.get("/students")
@limiter.limit("100/minute")  # ← 100 requests por minuto
async def list_students(skip: int = 0, limit: int = 100):
    # También limitar resultado
    assert limit <= 1000, "Máximo 1000 registros por request"
    ...

# Rate limits globales por usuario autenticado
@app.get("/me")
@limiter.limit("1000/hour")  # ← Límite generoso pero seguro
async def get_current_user(current_user: User = Depends(...)):
    ...
```

- [ ] Implementar `slowapi` rate limiting
- [ ] Limites por endpoint: login (strict), GET (moderate), POST (strict)
- [ ] Limites globales: 10,000 requests/hora por usuario
- [ ] Return 429 (Too Many Requests) cuando se excede
- [ ] Redis para shared rate limits (si multiple app instances)

**D-002: Query Timeouts**
```
CONTEXTO:
- Superset query expensive sin límite → hangs forever
- Attacker: crafted query que consume CPU/memoria

RIESGO:
- Database unresponsive
- Otros usuarios afectados
```

**Recomendaciones**:
```python
# FastAPI: statement timeout
from sqlalchemy import text

async def list_gradebook(group_id: UUID, session: AsyncSession):
    # PostgreSQL statement timeout: 5 segundos
    await session.execute(text("SET statement_timeout = 5000"))
    
    result = await session.execute(
        select(AdesGradebookEntry)
        .where(AdesGradebookEntry.group_id == group_id)
        .limit(1000)
    )
    return result.scalars().all()

# Superset: configurar en connection string
DATABASE_URL = "postgresql://user:pwd@host/db?statement_timeout=5000"
```

- [ ] PostgreSQL: `SET statement_timeout = 5000` (5 segundos)
- [ ] FastAPI: async timeouts en endpoints (asyncio.timeout)
- [ ] Superset: timeout en source configuration
- [ ] Monitoreo: alertar si queries toman >2 segundos

**D-005: N+1 Queries en Gradebook**
```
CONTEXTO:
- Obtener 500 estudiantes + calificaciones = 501 queries
- Sin eager loading → O(n) queries

RIESGO:
- Latencia, cargar DB
- Susceptible a DOS
```

**Recomendaciones**:
```python
from sqlalchemy.orm import selectinload, joinedload

# MALO: N+1 queries
students = await session.execute(select(AdesStudent))
for student in students.scalars():
    print(student.gradebook_entries)  # ← Query por cada estudiante

# BUENO: Eager loading
students = await session.execute(
    select(AdesStudent)
    .options(selectinload(AdesStudent.gradebook_entries))
)
for student in students.scalars():
    print(student.gradebook_entries)  # ← Ya cargado, sin query

# O usar join si necesita filtro
gradebook = await session.execute(
    select(AdesGradebookEntry)
    .join(AdesStudent)
    .where(AdesGroup.id == group_id)
    .options(joinedload(AdesGradebookEntry.student))
)
```

- [ ] Usar `selectinload()` o `joinedload()` para relaciones
- [ ] Query profiling: log queries lentas (> 100ms)
- [ ] Test de performance: 500+ estudiantes sin "N+1 queries"

---

### **E: ELEVATION OF PRIVILEGE (Escalación de Privilegios)**

#### Amenazas Identificadas

| ID | Amenaza | Severidad | Estado |
|----|---------|-----------|--------|
| E-001 | IDOR: acceso directo a recursos por ID | 🔴 CRÍTICA | ⚠️ Probable |
| E-002 | Bypass de granular privileges | 🔴 CRÍTICA | ⚠️ Probable |
| E-003 | Privilege escalation vía parámetros | 🔴 CRÍTICA | ⚠️ Probable |
| E-004 | Insecure direct object reference en gradebook | 🔴 CRÍTICA | ⚠️ Muy Probable |
| E-005 | Multi-role bypass: admin claims es maestro | 🟠 ALTA | ⚠️ Probable |

#### Evaluación Detallada

**E-001/E-004: IDOR en Gradebook**
```
CONTEXTO (CRÍTICO en ADES):
- Endpoint GET /gradebook/{gradebook_entry_id}
- Sin validar que current_user es maestro del grupo

ESCENARIO:
GET /api/gradebook/550e8400-e29b-41d4-a716-446655440000
→ Retorna calificación de estudiante X
→ Attacker cambia UUID a otro
→ Accede a calificación de estudiante Y (SIN permiso)

RIESGO CRÍTICO:
- Maestro de primaria lee/modifica secundaria
- Estudiante ve propias calificaciones + otros
- Violación de privacidad estudiantil
```

**Recomendaciones**:
```python
# IMPLEMENTACIÓN CORRECTA
@router.get("/gradebook/{entry_id}")
async def get_gradebook_entry(
    entry_id: UUID,
    current_user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_session)
):
    # 1. Obtener la entrada
    entry = await session.get(AdesGradebookEntry, entry_id)
    if not entry:
        raise HTTPException(status_code=404)
    
    # 2. VERIFICAR PERMISOS (CRÍTICO)
    if current_user.role == "STUDENT":
        # Estudiante solo ve sus propias calificaciones
        if entry.student_id != current_user.id:
            raise HTTPException(status_code=403, detail="Forbidden")
    
    elif current_user.role == "TEACHER":
        # Maestro solo ve sus grupos
        is_teacher_of_group = await session.execute(
            select(1).from_(AdesGroupTeacher)
            .where(
                (AdesGroupTeacher.user_id == current_user.id) &
                (AdesGroupTeacher.group_id == entry.group_id)
            )
        )
        if not is_teacher_of_group.scalar():
            raise HTTPException(status_code=403, detail="Forbidden")
    
    elif current_user.role == "ADMIN":
        # Admin puede ver todo (sin filtro)
        pass
    
    # 3. Retornar
    return entry

# MEJOR AÚNA: Usar Row-Level Security (RLS) de PostgreSQL
# Así el acceso está enforced en BD, no solo en FastAPI
```

**IMPLEMENTAR INMEDIATAMENTE**:
- [ ] **AUDITORÍA COMPLETA** de endpoints GET/PUT/DELETE
- [ ] Cada endpoint debe validar que `current_user` tiene acceso a recurso
- [ ] Tests explícitos: user A intenta acceder a recurso de user B → 403
- [ ] Usar RLS en PostgreSQL como segunda línea de defensa

**E-002: Granular Privileges Bypass**
```
CONTEXTO (ADES tiene "granular privileges"):
- Tabla: ades_privilege (MANAGE_GRADEBOOK, EXPORT_STUDENTS, etc)
- Sin validar en endpoint → escalación de privilegios

ESCENARIO:
- Maestro NO tiene MANAGE_GRADEBOOK
- Token JWT contiene: ["TEACH_GROUP_1"]
- Maestro POST /gradebook (debería rechazarse)
- Si app no valida privilege → escalación
```

**Recomendaciones**:
```python
# Dependency para validar privileges
async def require_privilege(
    privilege: str,
    current_user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_session)
):
    has_privilege = await session.execute(
        select(1).from_(AdesPrivilege)
        .where(
            (AdesPrivilege.user_id == current_user.id) &
            (AdesPrivilege.code == privilege)
        )
    )
    if not has_privilege.scalar():
        raise HTTPException(status_code=403, detail=f"Privilege '{privilege}' required")
    return True

# En endpoint
@router.post("/gradebook")
async def create_gradebook_entry(
    entry: GradebookCreate,
    has_privilege: bool = Depends(require_privilege("MANAGE_GRADEBOOK")),
    current_user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_session)
):
    # Crear entrada solo si privilege validado
    ...

# Tests
def test_teacher_without_manage_gradebook_cannot_create():
    # Given: teacher sin MANAGE_GRADEBOOK
    # When: POST /gradebook
    # Then: 403 Forbidden
    assert response.status_code == 403
```

- [ ] Crear dependency `require_privilege("PRIVILEGE_NAME")`
- [ ] CADA endpoint de modificación valida privilege
- [ ] Tests: user sin privilege → 403
- [ ] Documento: matriz de roles vs privileges (`ROLES_MATRIX.md`)

**E-005: Multi-Role Bypass**
```
CONTEXTO:
- Usuario puede tener múltiples roles (TEACHER + ADMIN)
- Si lógica no es AND (all roles required), escalación

ESCENARIO:
- User es TEACHER
- User reclama ADMIN en JWT (token hijacking o malicia)
- App verifica "role == ADMIN OR role == TEACHER" → permite
```

**Recomendaciones**:
```python
# Validar roles correctamente
@require_role("ADMIN")  # ← Require ONE specific role
async def delete_student(student_id: UUID):
    ...

# NO hacer esto:
if current_user.role in ["ADMIN", "TEACHER"]:  # ← Ambiguo

# SÍ hacer esto:
if current_user.role == "ADMIN":  # ← Exacto
    # admin-only logic
elif current_user.role == "TEACHER":
    # teacher-only logic
    # validar privilege MANAGE_STUDENTS
else:
    raise HTTPException(status_code=403)

# Definir explícitamente qué rol puede qué acción
ROLE_PERMISSIONS = {
    "ADMIN": ["READ_ALL", "WRITE_ALL", "DELETE_ALL"],
    "TEACHER": ["READ_OWN_GROUP", "WRITE_GRADEBOOK"],
    "STUDENT": ["READ_OWN_DATA"],
    "PARENT": ["READ_CHILD_DATA"],
}

def authorize(required_permission: str):
    async def _authorize(current_user: User = Depends(...)):
        permissions = ROLE_PERMISSIONS.get(current_user.role, [])
        if required_permission not in permissions:
            raise HTTPException(status_code=403)
    return _authorize
```

- [ ] Roles mutuamente excluyentes o explicit nesting
- [ ] Tests: user con rol incorrecto → rechazado
- [ ] Matriz RBAC en documentación: ROLES_MATRIX.md

---

## 3. MATRIZ DE RIESGOS RESUMIDA

| Pilar | Críticas | Altas | Medias | Estado General |
|-------|----------|-------|--------|--------|
| **Spoofing** | 3 | 2 | 1 | 🔴 **VULNERABLE** |
| **Tampering** | 3 | 2 | 0 | 🔴 **VULNERABLE** |
| **Repudiation** | 2 | 1 | 0 | 🔴 **SIN MITIGACIÓN** |
| **Info Disclosure** | 2 | 3 | 1 | 🟠 **RIESGOSA** |
| **Denial of Service** | 0 | 3 | 2 | 🟠 **PARCIAL** |
| **Elevation of Privilege** | 3 | 2 | 0 | 🔴 **VULNERABLE** |

**Total de riesgo: 🔴 CRÍTICO**
- **13 amenazas críticas/altas**
- **Enfoque inmediato**: IDOR, SQL Race Conditions, Auditoría, OIDC Token Security

---

## 4. PLAN DE REMEDIACIÓN (ROADMAP)

### **FASE 1: CRÍTICA (Implementar en próximas 2 semanas)**
- [ ] Validación IDOR en TODOS los endpoints de modificación (gradebook, estudiantes, etc)
- [ ] CSRF tokens en formularios
- [ ] Auditoría append-only en calificaciones
- [ ] httpOnly + SameSite cookies para tokens OIDC
- [ ] Verificación de SECRET_KEY (mín 64 bytes)
- [ ] Rate limiting en login (5/min)

### **FASE 2: ALTA PRIORIDAD (Próximas 4 semanas)**
- [ ] Optimistic locking (versioning) en tablas críticas
- [ ] RLS en PostgreSQL para estudiantes/maestros
- [ ] Encriptación de campos PII
- [ ] Error messages genéricos en producción
- [ ] HTTPS enforced, HSTS headers
- [ ] Superset RBAC y vistas seguras

### **FASE 3: MEJORA CONTINUA (Próximos 2-3 meses)**
- [ ] Firma de logs (SHA256 chain)
- [ ] Backup inmutable de auditoría
- [ ] Query profiling y N+1 elimination
- [ ] Secret management (Vault o equivalente)
- [ ] Penetration testing por experto externo
- [ ] Cumplimiento SEP/UAEMEX (compliance audit)

---

## 5. CHECKLIST DE VERIFICACIÓN

Usar este checklist en daily standups:

- [ ] **Token Security**: httpOnly cookies, SameSite=Strict, rotation cada 15min
- [ ] **IDOR Validation**: Cada GET/PUT/DELETE verifica permisos
- [ ] **Privilege Checks**: `@require_privilege()` en endpoints críticos
- [ ] **Audit Logging**: Calificación/usuario/timestamp en ades_audit_log
- [ ] **Rate Limiting**: API protegida contra brute force
- [ ] **Error Handling**: Stack traces NO expuestos en producción
- [ ] **Database**: HTTPS to PostgreSQL, RLS habilitado
- [ ] **Secrets**: NO hardcoded, usando env variables
- [ ] **Tests**: IDOR, privilege escalation, race conditions
- [ ] **Superset**: Read-only user, vistas redactadas

---

## 6. REFERENCIAS Y ESTÁNDARES

- **STRIDE Framework**: https://owasp.org/www-community/Threat_Modeling_Process#stride-threat--mitigation-techniques
- **OWASP Top 10**: https://owasp.org/Top10/
  - A01:2021 – Broken Access Control (IDOR, privilege escalation)
  - A02:2021 – Cryptographic Failures
  - A05:2021 – Broken Access Control (again!)
  - A07:2021 – Identification and Authentication Failures
- **SQLAlchemy Security**: https://docs.sqlalchemy.org/en/20/
- **FastAPI Security**: https://fastapi.tiangolo.com/advanced/security/
- **PostgreSQL RLS**: https://www.postgresql.org/docs/current/ddl-rowsecurity.html

---

**Fecha de Próxima Revisión**: 30 días (post-implementación FASE 1)
**Dueño**: Equipo de Seguridad ADES
**Status**: 🔴 REQUIRES IMMEDIATE ACTION
