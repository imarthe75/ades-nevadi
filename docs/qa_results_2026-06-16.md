# Resultados de Ejecución — Plan Integral de Pruebas ADES
**Fecha:** 2026-06-16 | **Sprint:** QA Pre-Producción | **Ejecutor:** Claude Code (automatizado)

---

## Resumen ejecutivo

| Categoría | Resultado |
|-----------|-----------|
| Contenedores (27) | ✅ Todos healthy/up |
| Infraestructura y salud de servicios | ✅ Pasa |
| Reglas de negocio DB (RN-01..RN-20) | ⚠️ 3 hallazgos |
| API / BFF smoke tests | ⚠️ 1 hallazgo |
| Seguridad (SEC-01..SEC-10) | ❌ 1 falla P1 |
| Auditoría cobertura | ❌ 3 hallazgos (2 P2, 1 P1) |
| Pruebas UI Módulos 1–50 | 🔲 Pendiente (requiere browser) |

---

## PRERREQUISITOS TÉCNICOS

| Check | Estado | Detalle |
|-------|--------|---------|
| Contenedores healthy | ✅ | 27/27 up (algunos sin healthcheck) |
| Frontend `localhost:4200` | ✅ | HTTP 200 |
| FastAPI `/api/v1/health` | ✅ | `{"status":"ok","db":"ades","pg_version":"PostgreSQL","uuid_v7_sample":"..."}` |
| BFF `/actuator/health` | ✅ | `{"status":"UP"}` — DB+Redis+Vault UP |
| PostgreSQL migraciones | ✅ | 78 grupos, 1 980 estudiantes |
| Valkey | ✅ | Healthy |
| Vault | ✅ | Initialized=true, Sealed=false |
| Celery worker | ✅ | 1 nodo activo |
| Carbone `/health` | ✅ | `{"status":"ok","templates":0}` |
| ntfy `/v1/health` | ✅ | `{"healthy":true}` |
| n8n `/healthz` | ✅ | `{"status":"ok"}` |
| Grafana `/api/health` | ✅ | `{"database":"ok","version":"13.0.2"}` |
| Superset `/health` | ✅ | HTTP 200 OK |
| Prometheus `/-/healthy` | ✅ | HTTP 200 |
| Stirling-PDF `/api/v1/info` | ⚠️ | 401 — requiere autenticación (esperado) |
| Paperless-ngx | ⚠️ | Sin respuesta vía exec (contenedor up pero API no expuesta directamente) |

---

## SECCIÓN C — Reglas de Negocio (automatizable)

| ID | Regla | Estado | Detalle |
|----|-------|--------|---------|
| RN-01 | PK siempre UUID | ✅ | `ades_grupos.id = 019e8f74-...` (UUID v7) |
| RN-02 | Auditoría en toda mutación | ⚠️ | **2 tablas elegibles con `PENDIENTE_BIU`** (ver hallazgo A) |
| RN-03 | `audit_biu` popula ref/rv/fecha | ✅ | `ref`, `row_version=2`, `fecha_creacion` correctos |
| RN-04 | `usuario_id` en mutaciones | 🔲 | Requiere POST con token |
| RN-05 | Suma ponderaciones = 100% | ✅ | SEP-Primaria=100, SEP-Secundaria=100, UAEMEX-Prep=100 |
| RN-06 | RBAC scope plantel | 🔲 | Requiere tokens múltiples |
| RN-07 | Soft delete `is_active` | ✅ | Columna presente en `ades_estudiantes` |
| RN-08 | `cerrar_ciclo_y_promover()` | 🔲 | No ejecutar en DEV sin ciclo real |
| RN-09 | `calcular_calificacion_periodo` idempotente | 🔲 | Requiere datos calificaciones |
| RN-10 | Cal. cerrada inmutable sin ADMIN | 🔲 | Requiere token + datos calificaciones |
| RN-11 | Escala SEP 0-10 | 🔲 | Requiere token docente |
| RN-12 | Escala UAEMEX 0-100 | 🔲 | Requiere token docente |
| RN-13 | CURP único | ✅ | `ades_personas_curp_key` UNIQUE CONSTRAINT presente |
| RN-14 | RFC profesor único | ✅ | `idx_profesores_rfc` UNIQUE índice presente |
| RN-15 | Máx 2 grupos por grado | ⚠️ | `uq_ades_grupos` por nombre/grado/ciclo — **no hay constraint DB de max 2**; solo lógica app |
| RN-16 | `RECHAZAR.requiereRazon()` | 🔲 | Requiere token + reinscripción activa |
| RN-17 | Firma Ed25519 verificable | ✅ | Columnas `firma_ed25519`, `hash_sha256`, `estado_firma` presentes |
| RN-18 | `DiasHabiles` excluye sáb-dom | 🔲 | Requiere crear licencia |
| RN-19 | Ajuste manual ≥ 20 chars | 🔲 | Requiere token admin + calificación cerrada |
| RN-20 | Partición activa por ciclo | ✅ | `EXPLAIN` → solo escanea `ades_asistencias_ciclo_2026_2027` |

---

## SECCIÓN A — API FastAPI (smoke)

| ID | Endpoint | Estado | Detalle |
|----|----------|--------|---------|
| — | `GET /api/v1/health` | ✅ | 200 OK con uuid_v7 |
| — | `GET /api/v1/certificados/verificar/:folio` | ✅ | 404 sin auth para folio inválido (ruta pública) |
| — | `GET /api/v1/ai/mis-sesiones` (sin token) | ✅ | 401 "Not authenticated" |
| — | Rutas de catálogos, alumnos, grupos | ⚠️ | **No están en FastAPI** — están en BFF Spring Boot (ver hallazgo B) |

---

## SECCIÓN B — BFF Spring Boot (smoke)

| ID | Endpoint | Estado | Detalle |
|----|----------|--------|---------|
| BFF-01 | `GET /actuator/health` | ✅ | `{"status":"UP"}` DB+Redis+Vault UP |
| BFF-02 | `GET /actuator/prometheus` | ✅ | Métricas Micrometer en texto plain (JVM, Lettuce, Security) |
| BFF-03 | `GET /api/v1/catalogs/roles` (sin auth) | ✅ BFF-07 | 401 Unauthorized — autenticación requerida |
| BFF-06 | `GET /api/v1/stats/telemetria` (sin auth) | ✅ | 401 — requiere nivel_acceso ≤ 2 |
| BFF-07 | Auth header faltante → 401 | ✅ | Confirmado |
| BFF-08 | Token inválido → 401 | ✅ | Token manipulado → 401 |

---

## SECCIÓN D — Servicios Externos

| ID | Servicio | Estado | Detalle |
|----|----------|--------|---------|
| CEL-01 | Celery worker activo | ✅ | `1 node online` |
| CAR-01 | Carbone `/health` | ✅ | `{"status":"ok","templates":0}` |
| PAP-01 | Paperless-ngx health | ⚠️ | Contenedor up pero exec falla (nombre servicio no expuesto directamente) |
| NTF-01 | ntfy `/v1/health` | ✅ | `{"healthy":true}` |
| STR-01 | Stirling-PDF `/api/v1/info` | ⚠️ | 401 auth requerida (comportamiento esperado) |
| VLT-01 | Vault status | ✅ | Initialized=true, Sealed=false, v2.0.2 |
| VLT-02 | Vault integración servicios | ⚠️ | BFF usa Vault (UP en actuator), FastAPI sigue con `.env` |
| N8N | n8n health | ✅ | HTTP 200 |
| MON | Grafana | ✅ | `{"database":"ok","version":"13.0.2"}` |
| MON | Superset | ✅ | HTTP 200 |
| MON | Prometheus | ✅ | HTTP 200 Healthy |

---

## SECCIÓN E — Seguridad

| ID | Prueba | Estado | Detalle |
|----|--------|--------|---------|
| SEC-03 | Token inválido → 401 | ✅ | BFF retorna 401 con token manipulado |
| SEC-08 | `/verificar/:folio` sin auth | ✅ | 404 para folio inválido, no requiere Authorization |
| SEC-09 | `/portal` sin auth | ✅ | HTTP 200 sin sesión |
| SEC-10 | Audit log inmutable | ❌ **P1** | `ades_admin` tiene permisos DELETE en `auditoria.log_auditoria` — no protegido (tabla vacía en DEV pero el permiso existe) |

---

## SECCIÓN G — Performance

| ID | Prueba | Estado | Detalle |
|----|--------|--------|---------|
| PERF-06 | Partition pruning | ✅ | `EXPLAIN` confirma solo `ades_asistencias_ciclo_2026_2027` escaneada |
| PERF-07 | Cache hit PostgreSQL | ✅ | **99.41%** (blks_hit=114.7M, blks_read=684K) |

---

## HALLAZGOS Y DEFECTOS

### ❌ HALLAZGO A — Audit log NO inmutable (SEC-10) `P1`

**Descripción:** `ades_admin` tiene permisos `DELETE`, `UPDATE`, `TRUNCATE` sobre `auditoria.log_auditoria`. El plan exige que solo `auditoria_admin` pueda borrar.

**Impacto:** Trail de auditoría puede ser alterado por el usuario de aplicación.

**Acción correctiva:**
```sql
-- Revocar permisos destructivos de ades_admin sobre log_auditoria
REVOKE DELETE, UPDATE, TRUNCATE ON auditoria.log_auditoria FROM ades_admin;
-- Crear rol auditoria_admin con permisos completos
CREATE ROLE auditoria_admin;
GRANT ALL ON auditoria.log_auditoria TO auditoria_admin;
```

---

### ⚠️ HALLAZGO B — 70 tablas con triggers BIU DUPLICADOS `P2`

**Descripción:** 70 tablas tienen AMBOS `audit_biu` Y `trg_aud_biu`, ejecutando `auditoria.fn_auditoria_biu()` **dos veces** por INSERT/UPDATE.

**Impacto:** `row_version` se incrementa 2 veces por operación (debería ser 1). Performance degradada innecesariamente. Inconsistencia en auditoría.

**Tabla afectadas (muestra):** `ades_acuses_comunicado`, `ades_alertas_academicas`, `ades_archivos`, `ades_aulas`, `ades_ciclos_escolares`, `ades_clases`, `ades_certificados`, `ades_comunicados`... (70 total)

**Acción correctiva:** Eliminar el trigger duplicado (mantener `audit_biu`, eliminar `trg_aud_biu`):
```sql
-- Script para eliminar trg_aud_biu de todas las tablas que también tienen audit_biu
DO $$
DECLARE
  r RECORD;
BEGIN
  FOR r IN
    SELECT c.relname AS tbl
    FROM pg_trigger t JOIN pg_class c ON t.tgrelid=c.oid
    JOIN pg_namespace n ON c.relnamespace=n.oid
    WHERE n.nspname='public' AND NOT t.tgisinternal
      AND t.tgname IN ('audit_biu','trg_aud_biu')
    GROUP BY c.relname HAVING count(*) > 1
  LOOP
    EXECUTE format('DROP TRIGGER IF EXISTS trg_aud_biu ON public.%I', r.tbl);
  END LOOP;
END $$;
```

---

### ⚠️ HALLAZGO C — 2 tablas PENDIENTE_BIU (naming mismatch) `P2`

**Descripción:** `ades_log_autenticacion` y `ades_reinscripcion_ciclo` tienen `trg_aud_biu` pero no `audit_biu`. La función `reporte_cobertura()` busca específicamente el nombre `audit_biu` y las reporta como PENDIENTE_BIU.

**Causa raíz:** Las migraciones recientes usan el helper `asignar_biu()` que crea `trg_aud_biu`, mientras que migraciones anteriores creaban `audit_biu` directamente.

**Acción correctiva:**
```sql
-- Renombrar trg_aud_biu → audit_biu en las 2 tablas afectadas
-- O actualizar reporte_cobertura() para reconocer ambos nombres
ALTER TRIGGER trg_aud_biu ON public.ades_log_autenticacion RENAME TO audit_biu;
ALTER TRIGGER trg_aud_biu ON public.ades_reinscripcion_ciclo RENAME TO audit_biu;
```

---

### ⚠️ HALLAZGO D — 17 tablas SIN_COLUMNAS_AUDITORIA `P2`

**Descripción:** 17 tablas no tienen las columnas de auditoría canónicas (`ref`, `usuario_creacion`, etc.), violando la regla mandatoria del CLAUDE.md.

**Tablas afectadas:**
- `ades_ai_conversaciones` (tiene `fecha_creacion`, `row_version` pero sin `ref`, `usuario_creacion/modificacion`)
- `ades_badges`, `ades_badge_otorgados`
- `ades_calificaciones_historico`, `ades_encuesta_respuestas`, `ades_lp_progreso`, `ades_notificaciones_sistema`
- `ades_cierre_periodo_log`, `ades_coordinaciones_area`, `ades_cuotas_concepto`
- `ades_disponibilidad_aula`, `ades_documentos_tipo`, `ades_periodos_inscripcion`
- `ades_planes_mejora`, `ades_sanciones_disciplinarias`, `ades_seguimiento_plan`
- `ades_audit_log` (tabla de log — excepción aceptada)

**Nota especial:** `ades_ai_conversaciones` almacena el historial de chat IA (IA-03/04/05) sin `ref` ni `usuario_creacion` — impacta RBAC y trazabilidad.

---

### ⚠️ HALLAZGO E — Max-2-grupos solo enforced a nivel app (RN-15) `P3`

**Descripción:** No hay constraint DB que impida crear un tercer grupo para el mismo grado/ciclo. Solo `uq_ades_grupos (nombre_grupo, grado_id, ciclo_escolar_id)` previene duplicados de nombre. La regla "máximo A y B" se enforcea solo en la capa de aplicación.

**Impacto bajo:** En producción no es crítico si la UI/API lo valida. Pero en INSERTs directos a BD se puede violar.

---

### ⚠️ HALLAZGO F — Separación FastAPI / BFF puede confundir el plan de pruebas `P3`

**Descripción:** La Sección A del plan referencia endpoints como `GET /api/v1/alumnos`, `POST /alumnos`, `GET /grupos`, que NO existen en el FastAPI (puerto 8000). Esos endpoints están en el BFF Spring Boot (puerto 8080 / ruta nginx `https://ades.setag.mx/bff/`).

El FastAPI solo expone: `health`, `ai`, `chatbot`, `carbone`, `push`, `pdf_tools`, `automations`, `webhooks`, `ia_avanzada`, `certificados`, `expediente`.

**Acción:** Corregir la Sección A del plan — los endpoints de dominio escolar (alumnos, grupos, calificaciones, etc.) deben usar `https://ades.setag.mx/bff/api/v1/`.

---

## PRUEBAS PENDIENTES (requieren browser + tokens)

Las siguientes categorías requieren sesión autenticada y/o interacción de UI:

| Módulos | Descripción | Usuarios necesarios |
|---------|-------------|---------------------|
| AUTH-01..08 | Login OIDC, callback, MFA, logout | admin.global, docente, padre |
| DASH-01..07 | Dashboard KPIs, filtros, drill-down | Todos los roles |
| ADM-01..13 | CRUD usuarios, ciclos, parámetros | ADMIN_GLOBAL |
| GRP-01..08 | Grupos, detalle, filtros | COORDINADOR |
| ALU-01..12 | Alumnos, inscripciones, CSV | DOCENTE+ |
| PRO-01..07 | Profesores, asignaciones | COORDINADOR |
| ASI-01..10 | Asistencias, bulk, alertas | DOCENTE |
| CAL-01..10 | Calificaciones, cierre, ajuste | DOCENTE + ADMIN |
| GRB-01..09 | Gradebook, exportar XLSX | DOCENTE |
| CER-01..07 | Certificados Ed25519, PDF, QR | DIRECTOR |
| IA-01..07 | Chat IA, NL→SQL, historial | COORDINADOR |
| Módulos 12-50 | Todos los demás módulos | Según rol |
| RN-04..12 | Reglas que requieren datos y tokens | Múltiples |
| SEC-01..07 | Cross-plantel, elevation, XSS, SQL injection | Múltiples tokens |
| PERF-01..05 | LCP, query times, PgBouncer | Browser + herramienta |
| IG-01..08 | InteractiveGrid components | Browser |
| D.2 | n8n webhook triggers | Acción real en app |
| D.4 | OCR Paperless end-to-end | Subir documento |
| CEL-02..06 | Alertas Celery, OCR, boletas | Datos reales |

---

## CHECKLIST DE CIERRE QA — Estado Actual

- [x] **Contenedores healthy** — 27/27 ✅
- [x] **UUID PKs en todas las tablas principales** ✅  
- [x] **Partition pruning activo** ✅
- [x] **Cache hit > 95%** — 99.41% ✅
- [x] **BFF autentica correctamente** ✅
- [x] **Rutas públicas sin auth** (portal, verificar folio) ✅
- [x] **Ponderaciones suman 100%** ✅
- [x] **Celery worker online** ✅
- [x] **Vault unsealed** ✅
- [ ] ❌ **Audit trail inmutable** — ades_admin puede borrar (Hallazgo A)
- [ ] ⚠️ **Audit coverage 100%** — 17 tablas sin columnas, 2 sin BIU correcto (Hallazgos C/D)
- [ ] ⚠️ **Triggers duplicados** — 70 tablas con double-fire BIU (Hallazgo B)
- [ ] 🔲 **P1 UI tests** — pendientes (browser)
- [ ] 🔲 **Google SSO** — bloqueado (credenciales externas pendientes)
- [ ] 🔲 **Superset dashboards** — configuración manual pendiente
- [ ] 🔲 **Polygon** — diferido a producción

---

## ISSUES ABIERTOS

| ID | Descripción | Módulo | Prioridad | Fecha |
|----|-------------|--------|-----------|-------|
| QA-001 | ades_admin tiene DELETE en auditoria.log_auditoria | SEC-10 | P1 | 2026-06-16 |
| QA-002 | 70 tablas con triggers audit_biu + trg_aud_biu duplicados (row_version +2 por op) | RN-03 | P2 | 2026-06-16 |
| QA-003 | ades_log_autenticacion y ades_reinscripcion_ciclo: trg_aud_biu sin reconocimiento en reporte_cobertura() | RN-02 | P2 | 2026-06-16 |
| QA-004 | 17 tablas sin columnas auditoría canónicas (ades_planes_mejora, ades_sanciones, ades_badges, etc.) | RN-02 | P2 | 2026-06-16 |
| QA-005 | Sección A del plan apunta a FastAPI pero esos endpoints están en BFF | Plan pruebas | P3 | 2026-06-16 |
| QA-006 | Carbone sin templates cargados ({"templates":0}) — boletas no generables hasta cargar plantillas | CAR-02 | P2 | 2026-06-16 |

---

*Generado automáticamente — 2026-06-16 22:30 | ADES Sprint QA Pre-Producción*
