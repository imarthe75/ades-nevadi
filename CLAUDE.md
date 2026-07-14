# ADES — Claude Code Guidelines
# Versión: 2.5 | Actualizado: 2026-07-08
# **NOTA:** Auditoría 16 Puntos de Optimización completada. Ver sección "OPTIMIZACIÓN AL 100%" abajo.

## MISIÓN Y CONTEXTO

ADES es el sistema integral de administración escolar del Instituto Nevadi (México).
3 planteles, 3 niveles educativos (Primaria SEP, Secundaria SEP, Preparatoria UAEMEX).
Repositorio: https://github.com/imarthe75/ades-nevadi
Servidor desarrollo: ades.setag.mx (129.213.35.140) — ARM OCI 4 cores 24 GB RAM
Contexto completo: .agent/CONTEXT.md

**Naturaleza del proyecto:** software **donado** al Instituto Nevadi, institución
privada **sin fines de lucro**. No se comercializa. El instituto cobra solo una
**cuota inicial**; el resto del ciclo se cubre con **becas del 100%** financiadas por
una asociación civil. Por ello el sistema **no requiere pasarela de pago ni
facturación CFDI ni nómina**: el alcance es **educativo, de salud y conductual**, más
el cumplimiento oficial **SEP / control escolar (UAEMEX)**. Prioridad de diseño:
sostenibilidad y mantenibilidad por equipo pequeño > cantidad de features.

**Arquitectura (hexagonal / SOLID):** BFF en **Spring Boot 3 (Java 21)** + frontend
**Angular 22**. **FastAPI queda para IA + render de documentos** (agente, embeddings,
insights; y generación PDF de boletas/constancias/actas/certificados vía
weasyprint/Jinja/Carbone — stacks de documentos maduros en Python). El BFF Spring
proxea esos endpoints (patrón `*FastApiAdapter`, límite hexagonal limpio). Toda lógica
de negocio nueva vive en `backend-spring/` salvo IA y render de documentos.
Ver ADR-0011 para el plan de boleta NEM.

---

## ESTADO ACTUAL DEL PROYECTO

| Componente | Estado |
|---|---|
| PostgreSQL 18 + pgvector | Corriendo healthy en localhost:5432 |
| Valkey 9.1.0 | Corriendo healthy en localhost:6379 |
| Authentik 2026.5.2 | Corriendo healthy en localhost:9010 |
| SeaweedFS | Corriendo healthy en localhost:9000 (S3) y 8888 (Filer) |
| nginx | Corriendo — proxy reverso TLS y static files |
| **BFF Spring Boot 3 (Java 21)** | Backend principal — 63 módulos hexagonales |
| **FastAPI (IA + Render Docs)** | Capa de agente/embeddings/insights + generación PDF boletas |
| Frontend Angular 22 | Standalone components + signals + PrimeNG (estilo APEX) |
| Superset 6.1.0 | Levantado — credenciales admin correctamente wireadas en docker-compose (fix 2026-07-03) |
| Migraciones | 3 dígitos hasta **114** + date-based. Próxima 3-díg: 115 |
| Regla ciclo escolar | 1 año vigente por sistema (SEP/UAEMEX). Mig 083: `sistema_educativo` + trigger `fn_ciclo_sistema_vigente` |
| Biblioteca | Mig 084: `ades_biblioteca_libros` + `ades_biblioteca_prestamos`; módulo hexagonal `/api/v1/biblioteca`; 60 libros + 74 préstamos seeded |
| Reporte 911 SEP | `/api/v1/reportes/911` (Spring hexagonal); matriz edad×grado×sexo×ingreso + grupos + Sección IX discapacidad. Fix HTTP 500 aplicado 2026-07-02 (columnas `cc.alumno_id`/`cc.activa`) |
| Boleta NEM | Mig 085: `ades_materias.campo_formativo` (4 campos NEM). FastAPI (`tasks/boletas.py` + Jinja `boleta.html`). Soporte numérico (6-10) y cualitativo |
| Boleta UAEMEX | FastAPI `/boletas/uaemex/{id}` → BFF proxy; template `boleta_uaemex.html` weasyprint; ordinario/extra/definitiva RGEMS |
| Kardex UAEMEX | `/api/v1/reportes/kardex/{id}` (Spring hexagonal); prepa CBU, escala 0-10 mín 6.0, ordinario→extraordinario→definitiva |
| Evaluación Docente 360° | `/api/v1/eval-docente` (Spring hexagonal); 4 tipos (DIRECTOR/COORDINADOR/PAR/AUTO); 7 criterios ponderados |
| NEM Fase 3 (Cualitativa) | Evaluación cualitativa A/B/C/D para 1°-2° de primaria. Mig 089. Configuración y carga en frontend + visualización en Boleta PDF. |
| Seguridad (IDOR, HTTPS, Limiting) | 5 vulnerabilidades corregidas, HTTPSRedirectMiddleware, rate limiting slowapi, IDOR fixes |
| Filtros en Cascada y Búsqueda | Filtro global en cascada Toolbar y inputs de búsqueda rápida integrados en todos los módulos clave |
| LOV Global Fix | `overlayAppendTo: 'body'` en `providePrimeNG()` (app.config.ts) — todos los p-select dentro de p-dialog/p-drawer muestran correctamente |
| **Franjas Horarias** | Mig 068: `ades_horario_franjas` seeded — PRIM/SEC L-J 10 franjas 07:00-16:00, V 8 franjas 07:00-14:00; PREP L-V 7 franjas 07:00-14:30. MATUTINO. Endpoints `/api/v1/horario-franjas` + `/api/v1/horario-indisponibilidad` (DISPONIBLE/CONDICIONAL/NO_DISPONIBLE) |
| **Testing Exploratorio IA** | `ades_testing/` — Playwright + NVIDIA NIM; Fase 2 (2026-07-02): 52 módulos capturados, 58 inconsistencias (50 cognitivas + 8 deterministas); ejecutar: `python 01_ades_explorer_v4_complete.py && python 02_claude_qa_analyzer.py && python 03_report_generator.py` |
| **Autocomplete Alumnos** | `p-autocomplete` (búsqueda dinámica) reemplaza `p-select` en movilidad, optativas, padres-admin, padres, conducta, certificados, expediente-doc, learning-paths |
| **Compresión Imágenes** | Compresión automática + límite 2MB global en todas las cargas de archivos/imágenes del frontend |
| **Claves CCT/UAEMEX por nivel** | Mig 103: `ades_plantel_nivel_clave` — 6 CCT SEP reales verificados (Metepec/Tenancingo/Ixtapan × Primaria/Secundaria); incorporación UAEMEX Preparatoria pendiente de oficio institucional |
| **19 CU auditoría 2026-07-03** | Mig 104-113: planes NEE, publicar/archivar plan, credencial alumno, modalidad clase, reprogramar planeación, reabrir entrega, plagio real (Jaccard), plan de mejora docente, timeline admisión, ajuste dinámico learning path, narrativa IA, exportación CSV BI, riesgo conductual, acta conducta PDF, eventos bienestar, auditoría login fallido (Authentik Events API), compliance LFPDPPP. Catálogo: 192/230 (83.5%) |
| **Auditoría seguridad 2026-07-04/06** | BOLA/BFLA corregidos (conducta, planes-estudio, plantel-claves, plan-mejora, learning-paths), mig 114 fix auditoría, headers nginx (HSTS/X-Frame/X-Content-Type/Referrer), 2 bugs reales en Gradebook (p-tablist faltante + contrato insights roto), ADR-0012 claves CCT |

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

### Backend principal — BFF Spring Boot (Java 21)
```bash
cd /opt/ades/backend-spring
./mvnw spring-boot:run           # arrancar BFF
./mvnw -q test                   # tests
./mvnw -q package                # build jar
```

### Backend IA — FastAPI (solo agente/embeddings/insights)
```bash
cd /opt/ades/backend
uvicorn app.main:app --reload --port 8000
celery -A app.worker.celery_app worker --loglevel=info
```

---

## ESTÁNDARES DE SEGURIDAD OBLIGATORIOS (PERMANENTES)

> **TODO lo que se desarrolle en ADES debe cumplir SIEMPRE con estos estándares — sin excepción.**

> **⚠️ REGLA CRÍTICA DE ACCESO SSH — NUNCA CERRAR EL PUERTO 22.**
> El acceso al servidor es únicamente por certificado (sin contraseña por SSH,
> `PasswordAuthentication no`). Un cierre accidental del puerto 22 (firewall,
> iptables, security list/NSG) deja el servidor inaccesible de forma
> irrecuperable — ya ocurrió una vez durante una auditoría de seguridad y
> obligó a eliminar y recrear el servidor completo. Antes de tocar reglas de
> firewall/iptables/NSG, verificar SIEMPRE que el puerto 22 permanezca
> `ACCEPT`. Usuario de respaldo: `ades` (grupo sudo, contraseña solo para
> `sudo` local — no válida por SSH), con la misma authorized_keys que
> `ubuntu` para login passwordless por certificado.

| Estándar | Aplicación concreta |
|---|---|
| **STRIDE** | Modelar amenazas en cada endpoint y migración nueva |
| **OWASP Top 10 2021** | Sin SQLi, XSS, IDOR, broken auth, misconfiguration |
| **OWASP API Security Top 10** | BOLA, auth de función, consumo de recursos, mass assignment |
| **NIST Framework** | AC-3 (control acceso), AU-3/AU-12 (auditoría), SI-10 (validación input), SC-8 (tránsito cifrado) |
| **GDPR / LFPDPPP** | Minimización PII, acceso por necesidad, datos académicos cifrados en tránsito |
| **Infrastructure Security** | Secrets en .env, no exponer puertos innecesarios, TLS en producción |
| **Supply Chain** | Pinear versiones de dependencias, revisar cambios de deps |
| **SDLC Security** | Code review antes de merge, no `--no-verify`, audit trail en mutaciones |
| **Compliance** | SEP (México), UAEMEX, LFPDPPP, ISO 27001 aspiracional |

### Checklist de seguridad para cada endpoint nuevo

- [ ] `resolveUser(jwt)` llamado — nunca anonimato
- [ ] Verificación de `nivelAcceso` vs operación (403 si insuficiente)
- [ ] Scoping por `plantelId` para no-admins (evita lectura cross-plantel)
- [ ] Path params usados realmente en la lógica (no ignorados)
- [ ] Inputs de body validados: tipo, rango, enum, longitud máxima
- [ ] Endpoint mutante pasa por `AuditHttpFilter` (Spring) o `AuditMiddleware` (FastAPI)
- [ ] `actualizarX()` con 0 filas → lanzar 404, no HTTP 200

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
14. **Auditorías Obligatorias**: Siempre aplicar las auditorías necesarias en base de datos y lógica de negocio para garantizar la seguridad estricta y funcionalidad impecable.
15. **Autoexploración y Autoescaneo de Componentes**: Cada componente desarrollado o modificado debe ser autoexplorado y autoescaneado para garantizar que sea 100% seguro y cumpla con toda la funcionalidad esperada.
16. **Análisis Exploratorios**: Realizar análisis exploratorios profundos antes y durante cualquier diseño o implementación de cambios o nuevos componentes para detectar fallas e inconsistencias proactivamente.
17. **Documentación Completa del Código**: Documentar plenamente todo el código desarrollado o modificado (comentarios en funciones críticas, clases, modelos, parámetros y cabeceras), asegurando su legibilidad y mantenibilidad.
18. **Organización de Documentación**: TODO archivo `.md` generado (reportes, auditorías, análisis, guías) DEBE ir en `docs/` — NUNCA en raíz. Solo `CLAUDE.md` y `README.md` quedan en raíz. `.gitignore` ignora .md en raíz (excepto los 2 permitidos).

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

## OPTIMIZACIÓN AL 100% — 16 PUNTOS CRÍTICOS (NUEVA — 2026-07-08)

**Estado actual (auditoría 2026-07-08):**
```
@EntityGraph implementados:    0/20 (META) ❌
OnDestroy implementados:        0/70 (META) ❌
SQL concatenation vulnerabilidades: DESCONOCIDO ⚠️
```

**Objetivo:** 100% de los 16 puntos implementados en 3 fases.

### FASE 1 (CRÍTICA — Semana 1-2) — BLOQUEA MERGE SI FALTAN ESTOS 3

**Punto 1: @EntityGraph (N+1 Prevention)**
```java
// ✅ CORRECTO: findBy* methods con @EntityGraph
@Repository
public interface AlumnoRepository extends JpaRepository<Alumno, UUID> {
    @EntityGraph(attributePaths = {"grado", "grupo", "expediente"})
    List<Alumno> findByGrupoId(UUID grupoId);
}

// ✅ CORRECTO: @Query con JOIN FETCH
@Query("SELECT DISTINCT a FROM Alumno a JOIN FETCH a.calificaciones c")
List<Alumno> findAllWithCalificaciones();
```
**Verificación:** `grep -r "@EntityGraph" backend-spring/src | wc -l` debe ser ≥ 20  
**Impacto:** Sin esto → 100x queries, CPU BD 100% ❌

**Punto 6: ngOnDestroy (Memory Leaks)**
```typescript
// ✅ CORRECTO: Implementar OnDestroy + cleanup
export class CalificacionesComponent implements OnInit, OnDestroy {
  private destroy$ = new Subject<void>();
  
  constructor(private api: ApiService) {}
  
  ngOnInit() {
    this.api.getCalificaciones()
      .pipe(takeUntil(this.destroy$))
      .subscribe(data => this.data.set(data));
  }
  
  ngOnDestroy() {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
```
**Verificación:** `grep -r "implements OnDestroy" frontend/src | wc -l` debe ser ≥ 70  
**Impacto:** Sin esto → memory leak 1MB × 1000 usuarios = 1GB crash ❌

**Punto 13: Prepared Statements (SQL Injection)**
```java
// ❌ MALO: SQL concatenation
String sql = "SELECT * FROM usuarios WHERE email = '" + email + "'";

// ✅ CORRECTO: Parameterized query
@Query("SELECT a FROM Alumno a WHERE a.email = ?1")
Alumno findByEmail(String email);

// ✅ CORRECTO: Named parameters
@Query("SELECT a FROM Alumno a WHERE a.email = :email AND a.activo = :activo")
List<Alumno> findActive(@Param("email") String email, @Param("activo") Boolean activo);
```
**Verificación:** `grep -r "'+'" backend-spring/src` debe retornar 0 resultados  
**Impacto:** Sin esto → SQL Injection vulnerable ❌

### FASE 2 (PERFORMANCE — Semana 3-4)

**Punto 5: Change Detection OnPush** | **Punto 9: Caching** | **Punto 10: Batch Ops**

### FASE 3 (INFRAESTRUCTURA — Semana 5+)

**Punto 2-4, 7-8, 11-12, 14-16:** Índices, Lazy Loading, Paginación, Memory Devtools, Images, Compression, Pooling, Headers, Isolation

### Checklist Pre-Commit (16 Items — Ejecutar SIEMPRE)

**Backend (9 items):**
```bash
echo "=== FASE 1 ===" && \
grep -r "@EntityGraph" backend-spring/src | wc -l && \
grep -r "implements OnDestroy" frontend/src | wc -l && \
grep -r "'+'" backend-spring/src | wc -l && \
echo "=== FASE 2 ===" && \
grep -r "ChangeDetectionStrategy.OnPush" frontend/src | wc -l && \
grep -r "@Cacheable" backend-spring/src | wc -l && \
grep -r "saveAll" backend-spring/src | wc -l
```

**Frontend (7 items):** Verificar con DevTools Memory Profiler

### Bloqueo de Merge

```
❌ SI FALTA CUALQUIERA DE LOS 16 PUNTOS:
   - Code reviewer rechaza PR
   - CI/CD pipeline falla
   - NO se permite merge

✅ SOLO SE MERGEA SI 16/16 PASAN
```

### Referencias Auditoría 2026-07-08

| Documento | Propósito |
|---|---|
| `/AUDITORIA_ADES_2026/INDICE_MAESTRO.md` | Guía general auditoría |
| `/AUDITORIA_ADES_2026/02_ANALISIS_16_PUNTOS/16_PUNTOS_OPTIMIZACION_COMPLETO.md` | Análisis técnico completo |
| `/AUDITORIA_ADES_2026/03_PLAN_REMEDIACION/PLAN_REMEDIACION_COMPLETO_ADES.md` | Plan 3 fases |
| `/AUDITORIA_ADES_2026/04_CHECKLISTS/CHECKLIST_PRECOMMIT_16_PUNTOS.md` | Verificación detallada |

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

### Backend principal — Spring Boot 3 / Java 21 (hexagonal, SOLID)
- Arquitectura hexagonal: `controller` (adaptador entrada) → `*ApplicationService`
  (caso de uso) → `query`/`domain` (puertos). Un `@Service` por interfaz; si una clase
  implementa 2+ casos de uso, ver patrón `@Service` multi-usecase.
- `resolveUser(jwt)` obligatorio en cada endpoint; verificación de `nivelAcceso` y
  scoping por `plantelId` para no-admins.
- PKs UUID v7; mutaciones pasan por `AuditHttpFilter` (audit trail con hash encadenado).
- Optimistic locking con `row_version` en PATCH/PUT.

### Backend IA — Python 3.12 / FastAPI (solo agente/embeddings/insights)
- Pydantic v2, type annotations completas, snake_case.
- `fastembed` (ONNX) para embeddings en ARM64 — NO sentence-transformers/torch.
- `get_ades_user` obligatorio; AuditMiddleware en endpoints mutantes.

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
| `db/migrations/` | DDL versionado (3 díg. hasta 082 + date-based) |
| `backend-spring/` | BFF principal Spring Boot 3 / Java 21 (hexagonal) |
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

OPENAI_API_KEY=<en .env>  # usado por el backend para NVIDIA NIM / integrate.api.nvidia.com
OPENAI_BASE_URL=https://integrate.api.nvidia.com/v1
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
