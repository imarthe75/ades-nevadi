# ADES — Estado del Proyecto
**Actualizado:** 2026-06-23 | **Versión:** para desarrolladores y agentes

---

## Stack técnico activo

| Componente | Versión | URL/Puerto |
|---|---|---|
| PostgreSQL 18 + pgvector | 18 | localhost:5432 |
| Valkey | 9.1.0 | localhost:6379 |
| Authentik | 2026.5.2 | https://auth.ades.setag.mx |
| SeaweedFS (S3) | latest | localhost:9000 (S3), 8888 (Filer) |
| nginx | latest | ades.setag.mx (TLS) |
| ades-bff | Spring Boot 3.x | localhost:8080 → /api/v1/* |
| ades-api | FastAPI (Python) | localhost:8000 → /api/v1/ai/* |
| ades-frontend | Angular 22 | https://ades.setag.mx |
| frontend-portal | Angular 22 | https://portalnvd.setag.mx |
| Superset | 6.1.0 | https://bi.ades.setag.mx |

---

## Backend Spring Boot (ades-bff)

**Estado:** BUILD SUCCESS — 231 tests, 0 fallos (2026-06-15)

### Arquitectura: Hexagonal + SOLID (ADR-0008)

```
modules/<modulo>/
  domain/model/          → Java records/enums (sin Spring)
  domain/port/in/        → interfaces use case
  domain/port/out/       → interfaces repository port
  application/service/   → NO @Service — en HexagonalConfig
  infrastructure/outbound/persistence/ → @Component adapters
  query/                 → @Service CQRS reads
```

### Módulos hexagonalizados (21 FASES)

| FASE | Módulo | Tests |
|------|--------|-------|
| 0 | shared/domain, foundation | 9 |
| 1 | asistencias | 16 |
| 2 | calificaciones + conducta | 26 |
| 2B | evaluaciones/tareas | 25 |
| 3 | gradebook | 21 |
| 4 | alumnos + profesores | 6 |
| 5 | expediente | 23 |
| 6 | reinscripcion | 10 |
| 7 | admin | 7 |
| 8-9 | certificados + grupos/horarios | 0 |
| 10 | evaluacion_avanzada | 13 |
| 11 | evaluacion | 4 |
| 12 | conducta reads | 28 |
| 13-15 | padres + notif + stats + compliance | 0 |
| 16 | procesos | 12 |
| 17 | encuestas | 9 |
| 18 | learning_paths | 10 |
| 19 | imports + `TipoEntidadImport` | 9 |
| 20 | portal admin (imagen upload) | 0 |
| 21 | movilidad + `TipoMovilidad` | 14 |
| **Total** | | **231** |

### Endpoints restantes en FastAPI (microservicios permanentes)

Estos módulos permanecen en Python por su acoplamiento con librerías de IA/PDF:
- `/api/v1/ai/*` — asistente pedagógico (Claude / NVIDIA NIM)
- `/api/v1/ia-avanzada/*` — predicción abandono, ajuste LP
- `/api/v1/chatbot/*` — NL-to-SQL, Flowise
- `/api/v1/carbone/*` — generación plantillas Carbone
- `/api/v1/pdf/*` — Stirling-PDF
- `/api/v1/push/*`, `/api/v1/webhooks/*`, `/api/v1/automations/*`

---

## Base de datos

**Estado:** 092 migraciones aplicadas, 164 tablas, seeds 2026-2027

### Schemas
- `public` — todas las tablas `ades_*` (core del sistema)
- `auditoria` — `log_auditoria`, funciones, triggers
- `portal` — convocatorias, postulaciones, requisitos, usuarios
- `ades_bi` — vistas materializadas para Superset

### Convenciones mandatorias
1. PKs siempre `UUID` con `uuidv7()` o `gen_random_uuid()`
2. Toda tabla `ades_*` tiene columnas de auditoría: `ref`, `row_version`, `fecha_creacion`, `fecha_modificacion`, `usuario_creacion`, `usuario_modificacion`
3. Trigger `audit_biu` aplicado con `SELECT auditoria.asignar_biu('public.ades_<tabla>');` al final de cada migración

### Cobertura de auditoría
```bash
docker compose exec postgres psql -U ades_admin -d ades \
  -c "SELECT * FROM auditoria.reporte_cobertura();"
```

---

## Frontend ADES (Angular 22)

**URL:** https://ades.setag.mx  
**Stack:** standalone components, signals, PrimeNG 21, PrimeIcons

### Módulos implementados

| Módulo | Ruta | Guard |
|--------|------|-------|
| Dashboard | / | roleGuard(4) |
| Alumnos | /alumnos | roleGuard(4) |
| Profesores | /profesores | roleGuard(3) |
| Grupos | /grupos | roleGuard(3) |
| Horarios | /horarios | roleGuard(4) |
| Asistencias | /asistencias | roleGuard(4) |
| Calificaciones | /calificaciones | roleGuard(4) |
| Tareas | /tareas | roleGuard(4) |
| Gradebook | /gradebook | roleGuard(4) |
| Planeación | /planeacion | roleGuard(4) |
| Conducta | /conducta | roleGuard(4) |
| Certificados | /certificados | roleGuard(3) |
| Learning Paths | /learning-paths | roleGuard(4) |
| Encuestas | /encuestas | roleGuard(4) |
| IA pedagógica | /ia | roleGuard(3) |
| Grade Analytics | /grade-analytics | roleGuard(3) |
| Eval. Docente 360° | /eval-docente | roleGuard(3) |
| Médico | /medico | roleGuard(3) |
| Expediente laboral | /expediente-laboral | roleGuard(2) |
| Disponibilidad | /disponibilidad | roleGuard(2) |
| Asistencia personal | /asistencia-personal | roleGuard(2) |
| Licencias | /licencias | roleGuard(2) |
| Capacitaciones | /capacitaciones | roleGuard(2) |
| Condiciones crónicas | /condiciones-cronicas | roleGuard(3) |
| Justificaciones | /justificaciones | roleGuard(3) |
| Comunicados | /comunicados | roleGuard(4) |
| Foros | /foros | roleGuard(4) |
| Reportes | /reportes | roleGuard(3) |
| Admin | /admin | roleGuard(2) |
| Planes de estudio | /planes-estudio | roleGuard(3) |
| Portal Familias | /padres | roleGuard(2) |
| Gestión Conv. | /portal-admin | roleGuard(2) |
| Verificar cert. | /verificar/:folio | público |
| Ayuda | /ayuda | authGuard |

### Servicios clave

| Servicio | Propósito |
|---|---|
| `ApiService` | HTTP + auth headers + getAbs() + postForm() + postBlob() |
| `AuthService` | OIDC Authentik + token storage (`ades_token`) |
| `ContextService` | plantel + nivel + ciclo activos (signals) |
| `ApexNotificationService` | toast único global (no usar MessageService local) |
| `ExportService` | CSV + XLSX (SheetJS) |

---

## Portal externo (portalnvd.setag.mx)

- 16 convocatorias activas con imágenes
- `GET /api/portal/catalogo` — planteles, niveles, categorías, tipos
- Postulaciones con requisitos de documentos
- Registro público sin autenticación

### Gestión de convocatorias (admin)

1. Ir a `https://ades.setag.mx/portal-admin` (requiere nivel_acceso ≤ 2)
2. Crear/editar convocatoria con todos los campos
3. Subir imagen: `POST /api/v1/portal/admin/convocatorias/{id}/imagen` (jpeg/png/webp, max 5MB)
4. La imagen se escribe en `/srv/assets/convocatorias/` (nginx static) con backup en SeaweedFS
5. URL pública: `https://ades.setag.mx/assets/convocatorias/{filename}`

---

## Roles y niveles de acceso

| nivel_acceso | Rol | Descripción |
|---|---|---|
| 1 | ADMIN_GLOBAL | Acceso total (todos los planteles) |
| 2 | DIRECTOR | Gestión completa de su plantel |
| 3 | COORDINADOR | Gestión académica |
| 4 | DOCENTE | Acceso a sus grupos/materias |
| 5 | PREFECTO / APOYO | Acceso limitado |

---

## Sprints pendientes

### SPRINT DB-AUDIT (alta prioridad)

Auditoría completa de la BD para generar documentación técnica.

**Objetivo:** Documentar TODO con `COMMENT ON`, generar diagrama E-R, revisar índices/constraints, detectar N+1 y posibles bloqueos.

**Pasos:**
1. Inventario de tablas: `SELECT schemaname, tablename FROM pg_tables WHERE schemaname IN ('public','portal','auditoria','ades_bi') ORDER BY 1,2`
2. Por cada tabla: `COMMENT ON TABLE public.ades_<t> IS '...'` + `COMMENT ON COLUMN ... IS '...'`
3. Por cada función: `COMMENT ON FUNCTION ... IS '...'`
4. Revisar índices faltantes en FKs
5. Generar Mermaid E-R desde `information_schema.columns` + `pg_constraint`
6. Guardar como `db/migrations/064_comentarios_schema.sql`

**Entregables:**
- `db/docs/DATABASE.md`
- `db/docs/ER_DIAGRAM.md`
- `db/docs/INDICES_RECOMENDADOS.md`
- `db/docs/CONSTRAINTS_AUDIT.md`
- `db/migrations/064_comentarios_schema.sql`

### Hexagonal pendiente (baja prioridad)

- `TareaEntregaController` (74L) — depende SeaweedFS S3 Java client
- `BoletasController` (103L) — proxy FastAPI, evaluar si aplica hexagonal
- `CatalogosSistemaController` (354L) — mayoritariamente reads

---

## Comandos frecuentes

```bash
# Ver estado servicios
docker compose ps

# Rebuild BFF y reiniciar
docker compose build ades-bff && docker compose up -d ades-bff

# Aplicar migración
docker compose exec -T postgres psql -U ades_admin -d ades < db/migrations/064_xxx.sql

# Tests backend-spring
cd backend-spring && mvn test -q

# Logs en tiempo real
docker compose logs -f ades-bff

# Cobertura auditoría
docker compose exec postgres psql -U ades_admin -d ades -c "SELECT * FROM auditoria.reporte_cobertura();"
```

---

## ADRs registrados

| ADR | Título |
|-----|--------|
| 0001 | Genesis — arquitectura inicial |
| 0002 | UUID PKs — no SERIAL ni BIGINT |
| 0003 | APEX component library |
| 0004 | Firma digital Ed25519 |
| 0005 | JWKS async + TTL cache |
| 0006 | RBAC niveles de acceso |
| 0007 | Audit trail v2 (biu/aiud) |
| 0008 | Hexagonal/SOLID Spring Boot |

Ver detalles en `DECISIONS/`.
