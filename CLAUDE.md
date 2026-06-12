# ADES — Claude Code Guidelines
# Versión: 2.0 | Actualizado: 2026-06-09

## MISIÓN Y CONTEXTO

ADES es el sistema integral de administración escolar del Instituto Nevadi (México).
3 planteles, 3 niveles educativos (Primaria SEP, Secundaria SEP, Preparatoria UAEMEX).
Repositorio: https://github.com/imarthe75/ades-nevadi
Servidor desarrollo: ades.setag.mx (129.213.35.140) — ARM OCI 4 cores 24 GB RAM
Contexto completo: .agent/CONTEXT.md

---

## ESTADO ACTUAL DEL PROYECTO

| Componente | Estado |
|---|---|
| PostgreSQL 18 + pgvector | Corriendo healthy en localhost:5432 |
| Valkey 9.1.0 | Corriendo healthy en localhost:6379 |
| Authentik 2026.5.2 | Corriendo healthy en localhost:9010 |
| MinIO | Corriendo healthy en localhost:9000-9001 |
| nginx | Corriendo — pendiente configurar upstreams faltantes |
| Backend FastAPI | Pendiente Dockerfile |
| Frontend Angular 22 | Pendiente Dockerfile |
| Superset 6.1.0 | Pendiente levantar |
| Fases 1-3 | Completas |
| Fase 4 IA | En progreso |
| Fase 5 Blockchain | Pendiente |
| Migraciones | 001-018 aplicadas. Próxima: 019 (ades_certificados_digitales) |

---

## COMANDOS DE DESARROLLO

### Docker Compose (desde /opt/ades)
```bash
docker compose up -d                          # levantar todo
docker compose up -d --no-deps nginx          # solo nginx sin dependencias
docker compose down                           # bajar todo
docker compose ps                             # estado de contenedores
docker compose logs -f ades-api               # logs en tiempo real
docker compose exec -T postgres psql -U ades_admin -d ades < archivo.sql
```

### Base de datos
```bash
# Aplicar migración
docker compose exec -T postgres psql -U ades_admin -d ades < db/migrations/018_xxx.sql

# Consulta rápida
docker compose exec postgres psql -U ades_admin -d ades -c "SELECT COUNT(*) FROM ades_grupos;"

# Backup
docker compose exec postgres pg_dump -U ades_admin ades > backup_$(date +%Y%m%d).sql
```

### Frontend Angular (dentro del contenedor o localmente)
```bash
cd /opt/ades/frontend
npm install
npm run start    # dev server puerto 4200
npm run build    # producción
npm run test     # Vitest
```

### Backend FastAPI
```bash
cd /opt/ades/backend
uvicorn app.main:app --reload --port 8000
celery -A app.worker.celery_app worker --loglevel=info
```

---

## REGLAS MANDATORIAS

1. PKs: siempre UUID con `uuidv7()` o `gen_random_uuid()`. NUNCA SERIAL, BIGINT, INTEGER como PK.
2. FKs: siempre referencian UUID.
3. **Toda tabla nueva `ades_*` DEBE tener columnas de auditoría:** `ref UUID`, `row_version INTEGER`, `fecha_creacion TIMESTAMPTZ`, `fecha_modificacion TIMESTAMPTZ`, `usuario_creacion TEXT`, `usuario_modificacion TEXT`.
4. **Triggers de auditoría obligatorios:** al final de cada migración con tablas nuevas llamar `SELECT auditoria.asignar_biu('public.ades_<tabla>');` — aplica `audit_biu` automáticamente. El `audit_aiud` se activa solo en producción.
5. Cada INSERT/UPDATE: el trigger `audit_biu` gestiona automáticamente `ref` (uuidv7), `row_version`, timestamps y usuarios. No asignar manualmente.
6. Para producción: `SELECT auditoria.asignar_triggers('public.ades_<tabla>');` activa también `audit_aiud` (log completo en `auditoria.log_auditoria`).
7. Verificar cobertura: `SELECT * FROM auditoria.reporte_cobertura();`
8. Volúmenes Docker: mapear a `./data/postgres/`, `./data/valkey/`, `./data/minio/`.
9. UI: estilo Oracle APEX — interactive grids, master-detail, LOV, edición inline.
10. `docker compose down -v` no debe perder datos con volúmenes correctamente configurados.
11. `.gitignore` incluye: `data/`, `.env`, `.agent/brain/`, `node_modules/`.
12. Migraciones: numeradas con 3 dígitos en `db/migrations/` (ej: `039_xxx.sql`).
13. Endpoints mutantes (POST/PUT/PATCH/DELETE): siempre pasan por `AuditMiddleware`.

### Esquema de auditoría ADES (implementado en 038_auditoria_v2.sql)

| Elemento | Descripción |
|---|---|
| `auditoria.log_auditoria` | Tabla de log con PK UUID, hash MD5 encadenado, TIMESTAMPTZ |
| `auditoria.fn_auditoria_biu()` | BEFORE INSERT/UPDATE — gestiona ref/row_version/timestamps/usuario |
| `auditoria.fn_auditoria_aiud()` | AFTER INSERT/UPDATE/DELETE — graba en log_auditoria (solo producción) |
| `auditoria.asignar_biu(tabla)` | Aplica solo `audit_biu` — usar en migraciones DEV |
| `auditoria.asignar_triggers(tabla)` | Aplica `audit_biu` + `audit_aiud` — usar al pasar a producción |
| `auditoria.reporte_cobertura()` | Reporte de cobertura de triggers por tabla |

---

## PRÁCTICAS RECOMENDADAS

- Paralelizar búsquedas: `grep_search` + `semantic_search` simultáneos.
- Leer rangos grandes en una sola operación vs. múltiples lecturas pequeñas.
- Filtrar outputs > 60KB con `head`, `tail`, `grep`.
- Revisar migraciones antes de aplicar: `alembic revision --autogenerate` + revisión manual.
- Documentar decisiones arquitectónicas en `DECISIONS/` como ADRs.
- Max 3 intentos de auto-corrección por tarea — al 4° halt + análisis en `memory.lessons`.

---

## OPTIMIZACIÓN DE TOKENS

### Semantic Caching con Valkey
```python
from memory.semantic_cache import SemanticCache
cache = SemanticCache(host="localhost", port=6379)
response = cache.get(query_embedding)
if not response:
    response = await llm.generate(query)
    cache.set(query_text, query_embedding, response)
```

### Estructura mínima de prompts
```
[CONTEXT] 2-3 líneas: qué es ADES
[STATE] 1 línea: tarea actual
[CONSTRAINTS] 2-3 líneas: reglas clave
[TASK] 1-2 líneas: qué hacer
[OUTPUT] 1 línea: formato esperado
```

### Fallback heurístico
Prioridad 1: Valkey (0 latencia) → Prioridad 2: PostgreSQL (20-50ms) → Prioridad 3: API externa

---

## CÓDIGO — ESTILO Y CALIDAD

### Backend Python/FastAPI
- Python 3.12+ con type annotations completas
- Pydantic v2 para schemas
- SQLAlchemy 2.x async patterns
- PKs: UUID v7 siempre
- snake_case para todo (parámetros, métodos, columnas)
- AuditMiddleware en endpoints mutantes

### Frontend Angular
- Standalone components
- Signals para state management
- PrimeNG con paletas HSL personalizadas
- Estilo APEX: interactive grids, master-detail, LOV, edición inline
- ContextService para plantel + nivel + ciclo activo
- Jost (headings) + Inter (body) desde Google Fonts

### Base de datos PostgreSQL
- Migraciones en `db/migrations/` con prefijo 3 dígitos
- Constraints, FKs e índices bien definidos
- Comentarios en tablas y columnas

---

## MEMORIA RESIDENTE

### Leer lecciones aprendidas
```python
from memory.long_term_memory import LongTermMemory
db_mem = LongTermMemory()
lessons = db_mem.search_lessons(query_embedding, limit=3)
```

### Rito de cierre de sesión
1. Leer `.agent/STATE.md` y resumir cambios
2. Generar embedding del resumen → guardar en `memory.lessons`
3. Actualizar `.agent/STATE.md` con timestamp y próximos pasos
4. Crear ADR en `DECISIONS/` si hubo cambios arquitectónicos

---

## CHECKLIST DE BOOTSTRAPPING

- [ ] PostgreSQL: `docker compose exec postgres psql -U ades_admin -d ades -c "SELECT 1;"`
- [ ] Valkey: `docker compose exec valkey valkey-cli -a $VALKEY_PASSWORD ping`
- [ ] `.agent/STATE.md` existe y actualizado
- [ ] `memory.lessons` creada y pgvector activa
- [ ] Revisar `DECISIONS/` para ADRs pendientes
- [ ] Migraciones al día
- [ ] Revisar fase actual en CONTEXT.md

---

## REFERENCIAS RÁPIDAS

| Archivo | Propósito |
|---|---|
| `.agent/CONTEXT.md` | Especificación completa del sistema |
| `.agent/STATE.md` | Estado actual de desarrollo |
| `.agent/MAP.md` | Mapa de módulos y rutas API |
| `DECISIONS/` | ADRs arquitectónicas |
| `memory/long_term_memory.py` | API persistencia PostgreSQL |
| `memory/semantic_cache.py` | API caché Valkey |
| `docker-compose.yml` | Stack dockerizado |
| `db/migrations/` | DDL versionado (001-018+) |
| `db/seeds/` | Datos iniciales 2026-2027 |

---

## VARIABLES DE ENTORNO (referencia — ver .env real)

```bash
POSTGRES_DB=ades
POSTGRES_USER=ades_admin
POSTGRES_PASSWORD=<en .env>

VALKEY_PASSWORD=<en .env>

MINIO_ROOT_USER=ades_minio
MINIO_ROOT_PASSWORD=<en .env>

AUTHENTIK_SECRET__KEY=<en .env>
OIDC_ISSUER=https://ades.setag.mx/auth/application/o/ades/
OIDC_CLIENT_ID=ades-frontend
OIDC_CLIENT_SECRET=<pendiente — crear app en Authentik>

SUPERSET_SECRET_KEY=<en .env>
SUPERSET_OIDC_CLIENT_SECRET=<pendiente — crear app en Authentik>

ANTHROPIC_API_KEY=<en .env>
ENVIRONMENT=development
```

---

## HEURÍSTICAS COGNITIVAS

| # | Principio | Implementación |
|---|---|---|
| 1 | Visibilidad de estado | STATE.md, CONTEXT.md, MAP.md actualizados |
| 2 | Terminología real | Términos escolares SEP/UAEMEX específicos |
| 3 | Control y libertad | Flujos reversibles, cancelar/deshacer |
| 4 | Consistencia | Estilos uniformes frontend/backend |
| 5 | Prevención de errores | Validación Pydantic v2, UI deshabilitada |
| 6 | Reconocimiento vs recuerdo | Metadatos claros, previews contextuales |
| 7 | Flexibilidad | Shortcuts, autocompletado experto |
| 8 | Diseño minimalista | Dashboards limpios, pocos widgets |
| 9 | Recuperación de errores | Mensajes amigables, fallback local |
| 10 | Ayuda integrada | Tooltips, docs, mensajes de error explicativos |
