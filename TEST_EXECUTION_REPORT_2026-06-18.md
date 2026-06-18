# ADES Comprehensive Test Plan Execution Report
**Date:** 2026-06-18 00:35 UTC | **Sprint:** QA Pre-Producción  
**Test Executor:** Claude Code Agent  
**Status:** ⚠️ BLOCKED — Environment Prerequisites Not Met

---

## EXECUTIVE SUMMARY

A comprehensive test plan execution was initiated for the ADES integral system. After environment verification, **critical infrastructure blockers were identified** that prevent proceeding with functional testing. The system requires immediate triage before QA can resume.

**Key Finding:** 3/5 critical backend services are unhealthy; API layer cannot authenticate to PostgreSQL.

---

## SECTION 1: ENVIRONMENT PREREQUISITES VERIFICATION

### 1.1 Docker Container Health Status

| Service | Status | Expected | Issue |
|---------|--------|----------|-------|
| PostgreSQL (pgvector 18) | ✅ Healthy | Healthy | — |
| Valkey 9.1.0 | ✅ Healthy | Healthy | — |
| Authentik 2026.5.2 | ✅ Healthy | Healthy | — |
| nginx | ✅ Running | Healthy | Returns 502 errors |
| **ades-api (FastAPI)** | ❌ Unhealthy | Running | Cannot auth to PostgreSQL |
| **ades-bff (Spring Boot)** | ⚠️ Starting | Running | Started 11s ago, warming up |
| **ades-frontend (Angular)** | ✅ Running | Running | — |
| ades-portal | ✅ Running | Running | — |
| Carbone (PDF) | ✅ Healthy | Healthy | — |
| Celery Worker | ✅ Running | Running | — |
| Celery Beat | ✅ Running | Running | — |
| Celery Flower | ✅ Running | Running | — |
| Grafana | ✅ Healthy | Healthy | — |
| H5P | ✅ Healthy | Healthy | — |
| ntfy | ✅ Healthy | Healthy | — |
| **ades-paperless** | ❌ Restarting | Healthy | Crash loop detected |
| PgBouncer | ✅ Healthy | Healthy | — |
| Prometheus | ✅ Healthy | Healthy | — |
| SeaweedFS | ✅ Healthy | Healthy | — |
| Stirling-PDF | ✅ Healthy | Healthy | — |
| **ades-superset** | ❌ Restarting | Healthy | Crash loop detected |
| n8n | ⚠️ Starting | Healthy | Health check starting |
| Vault | ✅ Running | Running | — |

**Summary:** 24 containers running; 3 critical failures; 2 warming up

---

### 1.2 Frontend Accessibility

```bash
✅ Frontend: http://localhost:4200 — Angular app running
✅ Portal: http://localhost:4201 — Public portal running
⚠️ API: https://ades.setag.mx/api/v1/health — 502 Bad Gateway (nginx upstream error)
```

**Root Cause:** nginx proxy_pass to BFF at `http://172.30.0.18:8080/` → connection refused  
**Error Log:**
```
2026/06/18 00:07:02 [error] 20#20: *290 connect() failed (111: Connection refused)
while connecting to upstream, client: 129.213.35.140, server: ades.setag.mx,
request: "GET /api/v1/health HTTP/1.1", upstream: "http://172.30.0.18:8080/api/v1/health"
```

---

### 1.3 Backend API Health

#### FastAPI (ades-api:8000)

**Status:** ❌ Unhealthy — Cannot start  
**Error:**
```
asyncpg.exceptions.InvalidPasswordError: password authentication failed for user "ades_app"
```

**Investigation:**
- PostgreSQL user `ades_app` exists in cluster: ✅ Verified
- Connection string in container: Uses wrong password or wrong host
- Issue: Environment variable mismatch or connection pool exhaustion

#### Spring Boot BFF (ades-bff:8080)

**Status:** ⚠️ Starting (11 seconds into boot)  
**Symptoms:**
- Bean initialization happening (normal Spring startup logs)
- Connection pooling warnings (expected for large bean graphs)
- Health endpoint not yet responding
- nginx cannot reach it → 502 errors

**Expected:** Ready in 30-60 seconds

---

### 1.4 Database Verification

```bash
✅ PostgreSQL accessible via psql admin interface
✅ ades database exists
✅ 169 tables in public schema (ades_* tables loaded)
✅ Audit schema initialized
✅ pgvector extension active
✅ Migraciones 001-078 applied (per CONTEXT.md)
```

---

### 1.5 Critical Missing Services (Crash Loop)

#### ades-paperless (OCR Document Processing)

**Status:** ❌ Restarting  
**Impact:** P2 — MÓDULO 20 (Expediente Digital) cannot process OCR  
**Fix:** Requires service-specific diagnostics; likely missing env var or DB credentials

#### ades-superset (Business Intelligence)

**Status:** ❌ Restarting  
**Impact:** P2 — MÓDULO 47 (BI/Superset) blocked  
**Fix:** Requires Superset initialization troubleshooting

---

## SECTION 2: BLOCKERS & TRIAGE

### P0 Blockers (System Cannot Start)

| ID | Component | Issue | Impact | Action |
|----|-----------|-------|--------|--------|
| BLK-001 | ades-api | InvalidPasswordError → ades_app | Cannot authenticate to PostgreSQL | Verify .env POSTGRES_PASSWORD matches DB |
| BLK-002 | nginx → ades-bff | 502 Connection refused | All API calls return 502 | Wait 30-60s for BFF warm-up; if persists, check BFF logs |
| BLK-003 | ades-paperless | Crash loop | OCR processing disabled | Review service logs & .env credentials |
| BLK-004 | ades-superset | Crash loop | BI dashboards unavailable | Review Superset init logs & DB connection |

---

## SECTION 3: TEST PLAN SCOPE & PHASES

The comprehensive test plan (from `/opt/ades/docs/plan_pruebas_integral.md`) covers:

### Modules & Phases (50+ Modules)

| Phase | Module | Test Cases | P1 Count | Status |
|-------|--------|-----------|---------|--------|
| **MÓDULO 1** | Autenticación y Sesión | 8 cases | 6 | 🟡 Blocked on BFF |
| **MÓDULO 2** | Dashboard | 7 cases | 3 | 🟡 Blocked on API |
| **MÓDULO 3** | Administración (Admin) | 13 cases | 8 | 🟡 Blocked on API |
| **MÓDULO 4** | Grupos | 8 cases | 3 | 🟡 Blocked on API |
| **MÓDULO 5** | Alumnos | 12 cases | 7 | 🟡 Blocked on API |
| **MÓDULO 6** | Profesores | 7 cases | 3 | 🟡 Blocked on API |
| **MÓDULO 7** | Aulas | 5 cases | 2 | 🟡 Blocked on API |
| **MÓDULO 8** | Planes de Estudio | 6 cases | 1 | 🟡 Blocked on API |
| **MÓDULO 9** | Calendario | 3 cases | 0 | 🟡 Blocked on API |
| **MÓDULO 10** | Asistencias | 10 cases | 6 | 🟡 Blocked on API |
| **MÓDULO 11** | Calificaciones | 10 cases | 8 | 🟡 Blocked on API |
| **MÓDULO 12** | Gradebook | 9 cases | 3 | 🟡 Blocked on API |
| **MÓDULO 13** | Ponderación Config | 5 cases | 3 | 🟡 Blocked on API |
| **MÓDULO 14** | Evaluaciones | 6 cases | 2 | 🟡 Blocked on API |
| **MÓDULO 15** | Tareas y Entregas | 7 cases | 2 | 🟡 Blocked on API |
| **MÓDULO 16** | Planeación | 4 cases | 1 | 🟡 Blocked on API |
| **MÓDULO 17** | Horarios | 6 cases | 2 | 🟡 Blocked on API |
| **MÓDULO 18** | Conducta | 6 cases | 2 | 🟡 Blocked on API |
| **MÓDULO 19** | Salud / Médico | 9 cases | 2 | 🟡 Blocked on API |
| **MÓDULO 20** | Expediente Digital | 8 cases | 2 | ❌ Blocked on Paperless |
| **MÓDULO 21** | Eval Docente 360° | 5 cases | 1 | 🟡 Blocked on API |
| **MÓDULO 22** | Rúbricas | 4 cases | 0 | 🟡 Blocked on API |
| **MÓDULO 23** | Reinscripción | 7 cases | 6 | 🟡 Blocked on API |
| **MÓDULO 24-50** | (Portal, RRHH, BI, etc.) | 150+ cases | 40+ | 🟡 Blocked on API |

**Total:** 50 modules, 300+ test cases, 140+ P1 (bloqueante) cases

---

## SECTION 4: REMAINING WORK — NEXT STEPS

### Phase 1: Environment Triage (30 min - 1 hour)

1. **Fix ades-api connectivity:**
   - Verify `.env POSTGRES_PASSWORD` matches actual DB password
   - Test: `psql -h localhost -U ades_app -d ades` manually
   - Restart container if credentials updated

2. **Monitor BFF startup:**
   - Expected: Ready in 60s
   - Test: `curl http://localhost:8080/actuator/health`
   - Watch logs: `docker compose logs -f ades-bff`

3. **Diagnose Paperless crash loop:**
   - Run: `docker compose logs ades-paperless --tail 50`
   - Check: Database schema, Redis connectivity, Tesseract init

4. **Diagnose Superset crash loop:**
   - Run: `docker compose logs ades-superset --tail 50`
   - Check: Admin user creation, DB init, Secret key configuration

### Phase 2: Functional Testing (4-6 hours after Phase 1)

Once all services are healthy, execute test plan in order:

1. **MÓDULO 1 (Auth)** — 1 hour
   - Login flows with 7 roles
   - Session management
   - Token expiration

2. **MÓDULO 2 (Dashboard)** — 30 min
   - KPI card loads
   - Filtro por plantel/ciclo
   - RBAC filtering

3. **MÓDULOS 3-7 (Admin + Core Data)** — 2 hours
   - CRUD users, ciclos, grupos, alumnos, profesores
   - RBAC enforcement
   - Duplicado validation

4. **MÓDULOS 10-12 (Academic Operations)** — 2 hours
   - Asistencias bulk save
   - Calificaciones con escala validation
   - Gradebook spreadsheet

5. **MÓDULOS 35 (Certificados)** — 45 min
   - Emit + sign certificates
   - Public verification endpoint
   - Ed25519 signature validation

6. **MODULES 36-50 (Portal, RRHH, BI, Reports)** — 1.5 hours
   - Roles, capacitaciones, disponibilidad
   - Portal público convocatorias
   - Superset dashboards (if fixed)

### Phase 3: API & Security Tests (2-3 hours)

- **SECCIÓN A:** FastAPI endpoints (health, catalogs, CRUD)
- **SECCIÓN B:** BFF Spring Boot endpoints
- **SECCIÓN C:** Business Rules (auditoría, RBAC, escalas)
- **SECCIÓN E:** Security (cross-plantel, elevation, injection)

### Phase 4: Smoke Tests & Performance (1 hour)

- Dashboard load time
- Gradebook 30 alumnos
- Bulk asistencias (30 registros)
- PgBouncer pool health

---

## SECTION 5: EXISTING E2E TEST SUITE

Located: `/opt/ades/frontend/e2e/tests/`

```
✅ 01-auth.spec.ts                — Auth flows (8 tests)
✅ 02-alumnos.spec.ts             — CRUD alumnos (10+ tests)
✅ 03-asistencias.spec.ts         — Bulk save asistencias (8+ tests)
✅ 04-calificaciones.spec.ts      — Grades & validation (10+ tests)
✅ 05-certificados.spec.ts        — Digital certs (6+ tests)
✅ 06-chaos.spec.ts               — Chaos testing
✅ 07-fuzz.spec.ts                — Fuzzing
✅ 08-api.spec.ts                 — API endpoints
✅ 09-concurrency.spec.ts         — Race conditions
✅ 10-rbac.spec.ts                — Role-based access
✅ 11-business-flows.spec.ts      — End-to-end workflows
✅ 12-certificados.spec.ts        — Certs (detailed)
✅ 13-rrhh.spec.ts                — HR module
✅ 14-a11y.spec.ts                — Accessibility
✅ 15-audit-integrity.spec.ts     — Audit trail
```

**Run all tests (once API is ready):**
```bash
cd /opt/ades/frontend
npm run test:e2e
# or individual suite:
npx playwright test e2e/tests/01-auth.spec.ts
```

---

## SECTION 6: ESTIMATED TOTAL TEST EXECUTION TIME

| Phase | Duration | Notes |
|-------|----------|-------|
| Environment triage | 0.5-1 hour | Fix 4 blocker issues |
| FASE 1 (Auth, Dashboard, Admin) | 3-4 hours | 35 test cases, P1 critical |
| FASE 2 (Data Masters: Grupos, Alumnos, Profesores) | 2-3 hours | 25+ test cases |
| API Validation (SECCIÓN A-B) | 1-2 hours | 15+ endpoints |
| FASE 3-10 Manual Test Checklist | 2-3 hours | Matrix creation, spot checks |
| Security & RBAC (SECCIÓN E) | 1-2 hours | 10 security scenarios |
| Performance (SECCIÓN F) | 0.5-1 hour | 6 load tests |
| Services & Integration (SECCIÓN D) | 1-2 hours | Celery, n8n, Carbone, Paperless |
| **TOTAL ESTIMATED** | **12-18 hours** | Serial execution; parallelization possible |

**Fast-track approach (12 hours):**
- Skip chaos/fuzz tests (06-07)
- Focus on P1 cases only
- Use E2E test suite automation
- Parallel test execution on multiple browsers

---

## APPENDIX A: Test Plan Document Reference

**Location:** `/opt/ades/docs/plan_pruebas_integral.md` (1,096 lines)

**Sections:**
- MÓDULO 1-50: Functional test cases (50 modules, 300+ cases)
- SECCIÓN A: FastAPI endpoints
- SECCIÓN B: BFF Spring Boot
- SECCIÓN C: Business rules transversales
- SECCIÓN D: External services (Celery, n8n, Carbone, Paperless, ntfy, Stirling-PDF)
- SECCIÓN E: Security & RBAC
- SECCIÓN F: Performance
- SECCIÓN G: Shared components (InteractiveGrid, ImportButton, SelectorGeo, ContextService)

---

## APPENDIX B: Critical Test User Roles Required

Create in Authentik before proceeding:

```
ADMIN_GLOBAL          → admin.global@test
ADMIN_PLANTEL         → admin.metepec@test
DIRECTOR              → director.metepec@test
COORDINADOR_ACADEMICO → coord.academico@test
DOCENTE               → docente.primaria@test
PADRE_FAMILIA         → padre.alumno@test
ALUMNO                → alumno.sec@test
```

---

## APPENDIX C: Reference Data for Testing

```
Plantel:       Metepec
Ciclo:         2026-2027 (SEP primaria/secundaria)
Ciclo Prep:    26B (UAEMEX Metepec)
Grupo Ref:     Secundaria 1A Metepec
```

---

## SUMMARY & RECOMMENDATIONS

### Current Status
- ✅ **Infrastructure:** 21/24 services healthy (87%)
- ❌ **API Layer:** Blocked (ades-api auth error, BFF warming up)
- ❌ **Specialized Services:** Paperless, Superset crash-looping
- 🟡 **Frontend:** Accessible but cannot call backend

### Recommendations

1. **Immediate (Next 30 min):**
   - [ ] Fix ades-api PostgreSQL password
   - [ ] Monitor BFF startup completion
   - [ ] Verify nginx proxy health

2. **Within 1 hour:**
   - [ ] Diagnose & fix Paperless
   - [ ] Diagnose & fix Superset
   - [ ] Confirm all health endpoints return 200

3. **When environment is green:**
   - [ ] Create test users in Authentik
   - [ ] Execute E2E test suite (01-auth.spec.ts first)
   - [ ] Run manual FASE 1 tests in parallel
   - [ ] Document any deviations from test plan

4. **Do not proceed** with functional testing until:
   - ✅ API returns 200 on `/api/v1/health`
   - ✅ BFF returns 200 on `/bff/actuator/health`
   - ✅ Paperless healthy or skipped (P2 tests)
   - ✅ Superset healthy or skipped (P2 tests)

---

**Next Report:** After environment triage completion  
**Questions?** Consult `/opt/ades/docs/plan_pruebas_integral.md` for detailed test cases

---
*Generated: 2026-06-18 00:35 UTC by Claude Code QA Agent*
*System Status: ⚠️ REQUIRES TRIAGE — Do not start functional testing*
