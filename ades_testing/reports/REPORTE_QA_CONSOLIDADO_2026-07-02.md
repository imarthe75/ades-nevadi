# ADES Nevadi — Reporte QA Consolidado

**Fecha:** 2026-07-02
**Alcance:** Testing exploratorio (Playwright + IA cognitiva), suite de interacción topbar/sidebar, y análisis estático de código (3 stacks: Angular, Spring Boot, FastAPI).
**Metodología:** ver desglose de confianza por sección — se distingue entre hallazgos **confirmados con evidencia determinista** (HTTP status, stack trace, código fuente) y **hipótesis de un analizador cognitivo (LLM)** que requieren verificación manual.

---

## 0. Hallazgo prioritario — IDOR confirmado en Portal de Familias (CONFIRMADO por código)

**Severidad: Crítico | Confianza: Confirmado (lectura directa de código, no inferencia de IA)**

`PortalFamiliasController` (`backend-spring/src/main/java/mx/ades/modules/portal_familias/`) expone:

- `GET /api/v1/portal/tutores/{alumno_id}` (línea 69-74)
- `GET /api/v1/portal/resumen/{alumno_id}` (línea 171-176)

Ambos llaman `userService.resolveUser(jwt)` (válida que el JWT sea de un usuario autenticado) pero **nunca verifican que `alumno_id` pertenezca al padre/tutor autenticado**. `PortalFamiliasQueryService.resumenAcademico()` y `.listarTutores()` consultan directamente por el `alumnoId` recibido, sin cláusula de scoping por usuario.

En contraste, `GET /mis-alumnos` (línea 163-169) sí hace lo correcto: resuelve el email del usuario y filtra por él.

**Impacto:** cualquier padre/tutor autenticado puede leer el resumen académico y los tutores de **cualquier alumno del sistema** cambiando el UUID en la URL — típico BOLA/IDOR (OWASP API1:2023, CWE-639). Dado que ADES maneja datos académicos y de salud de menores, este es el hallazgo de mayor riesgo de todo el ciclo de pruebas.

**Sugerencia:** en ambos endpoints, tras `resolveUser(jwt)`, verificar que `alumnoId` esté en el conjunto de alumnos vinculados al tutor (mismo patrón que ya usa `misAlumnos(email)`), y devolver 403 si no.

---

## 1. Resumen ejecutivo

| Fuente | Hallazgos | Confianza |
|---|---|---|
| IDOR Portal Familias (arriba) | 1 crítico | Confirmado (código) |
| Exploración cognitiva NIM (50) + hallazgos deterministas añadidos (8) | 58 totales — 15 críticas, 24 altas, 15 medias, 4 bajas | Mixta (ver §2) |
| Suite Playwright `18-topbar-sidebar.spec.ts` | 4 errores 5xx/503 confirmados en sidenav completo | Confirmado |
| ESLint (Angular) | 1,492 issues (0 warnings, todo error) | Confirmado |
| Checkstyle (Spring) | 17,962 violaciones — mayormente estilo/Javadoc | Confirmado |
| PMD (Spring) | 1,029 violaciones — 24 de prioridad 1 | Confirmado |
| SpotBugs (Spring) | 302 bugs — 3 de prioridad alta, 1 CORRECTNESS real | Confirmado |
| Ruff (FastAPI) | 1,011 hallazgos — 5 funciones con complejidad ciclomática >10 | Confirmado |
| Bandit (FastAPI) | 53 — **3 HIGH, 9 MEDIUM (posible SQLi)** | Confirmado |
| Mypy (FastAPI) | 27 errores de tipos | Confirmado |
| jscpd (Angular) | 76 clones, 749 líneas duplicadas (1.7%) | Confirmado |

---

## 2. Exploración cognitiva (NIM) — nota de confianza importante

El analizador `02_claude_qa_analyzer.py` usa un LLM (`meta/llama-3.1-70b-instruct`) que **solo ve texto visible y screenshots**, no ejecuta acciones de formulario ni lee el backend. Verifiqué manualmente 2 de sus 13 hallazgos "Validation Missing" contra el código real:

- ❌ **Falso positivo confirmado:** *"[admision] No se valida el formato de la CURP"* — `ProcesosEscolaresController.java:194` ya llama `ValidationUtils.validarCURP(body.getCurp())` en la creación de solicitud.
- ⚠️ **Dudoso:** *"[expediente_laboral] RFC inválido aceptado"* — el código valida RFC en 2 de los métodos de mutación (líneas 109, 149) pero solo si el campo no es `null`; no confirmé si existe una ruta de mutación que lo omita.
- ⚠️ **Probable falso positivo:** *"[medico] No se cifran los datos médicos en tránsito"* — todo el tráfico pasa por nginx con TLS (confirmado en arquitectura del proyecto); es poco probable que este módulo sea la excepción.

**Recomendación:** tratar las categorías *Validation Missing* (13) y *SEP/Nevadi Ambiguity* (4) como **leads para QA manual**, no como bugs confirmados. Los tipos *API Error* y *Error Hidden* que yo agregué manualmente (ver §3) sí tienen evidencia determinista (status HTTP real, stack trace real) y tienen alta confianza.

Reportes completos: `analysis/inconsistencies_report.json`, `reports/inconsistencies_report.html`, `reports/jira_issues.csv`, `reports/traceability_matrix.csv`.

---

## 3. Hallazgos deterministas confirmados (red + consola, sin LLM)

Extraídos directamente de `captures/captures_summary.json` (52 módulos, Fase 2) y de la suite Playwright 18:

| Módulo | Hallazgo | Severidad |
|---|---|---|
| `estadistica_911` | `GET /api/v1/reportes/911` → **HTTP 500** | Crítico (ya documentado en STATE.md) |
| `planes_estudio` | `GET /api/v1/planes-estudio?ciclo_id=...` → **HTTP 500** | Alto |
| `monitor_sistema` / `admin` | `GET /api/v1/superset/dashboards` → **HTTP 500** + `TypeError: Failed to fetch` en consola | Alto (Superset OIDC pendiente, ya conocido) |
| `videoconferencias` | `GET /api/v1/bbb/info` → **HTTP 503** | Medio |
| `eval_docente` | `TypeError: this.profesores(...).map is not a function` — se repite 7 veces en una sola carga (computed signal roto) | **Crítico** |
| `reportes` | `TypeError: Cannot read properties of null (reading 'writeValue')` | Medio |
| Global (los 52 módulos) | `GET /api/v1/push/suscripcion` falla en el 100% de las páginas — llamada global del shell (`PushNotificationService.init()`), degradación silenciosa ya manejada en código | Medio |

La suite `frontend/e2e/tests/18-topbar-sidebar.spec.ts` (nueva, agregada al framework existente de 17 suites) confirma estos 4 errores navegando el sidenav completo (52 links, uno por uno) y además valida: cascada Plantel→Nivel→Ciclo→Grado→Grupo, popovers de notificaciones/usuario, persistencia de contexto entre módulos, y breadcrumbs — sin regresiones adicionales.

**Nota de proceso:** el script exploratorio Python (`01_ades_explorer_v4_complete.py`) tenía un bug que invalidaba silenciosamente TODA sesión: Authentik ahora presenta una pantalla de consentimiento OAuth2 explícito ("Continue") tras el login que el flujo de 3 pasos no manejaba — cada navegación caía de vuelta a `/login` y el primer intento de Fase 2 capturó 52 páginas de login vacías, no la app real. Ya corregido (paso 3.5 agregado) y verificado con una corrida limpia.

---

## 4. Análisis estático — hallazgos más accionables

### FastAPI (backend/) — el más relevante para seguridad
- **HIGH — `app/api/v1/bbb.py:62`**: `httpx.AsyncClient(verify=False)` deshabilita validación TLS al llamar a BigBlueButton — riesgo MITM real.
- **HIGH — `app/api/v1/certificados.py:133`**: Jinja2 `Environment()` sin `autoescape=True` al renderizar `certificado.html` — riesgo XSS en documentos oficiales (certificados/constancias).
- **HIGH — `app/api/v1/bbb.py:47`**: SHA-1 en checksum BBB — ya documentado como requerido por la API de BBB (`# noqa` existente), no requiere acción.
- **MEDIUM (9x) — B608** "possible SQL injection": `ai_assistant.py:253`, `bbb.py:148`, `certificados.py:192`, `h5p.py:121,320`, `ia_avanzada.py:172`, `webhooks.py:111`, `notification_triggers.py:187`. Bandit detecta construcción de queries por f-string/concat; **requiere revisión manual** para confirmar si usan parámetros bindeados internamente (bandit no distingue) — dado que CLAUDE.md exige "sin SQLi" explícitamente, vale la pena una pasada dedicada.
- Complejidad ciclomática >10 en `sync_sepomex_weekly` (21) y `_generar_pdf_alumno` (19).

### Spring Boot (backend-spring/)
- 1 bug SpotBugs de categoría CORRECTNESS real: `RC_REF_COMPARISON` en `HorarioConstraintProvider` (comparación de objetos con `==` en vez de `.equals()`).
- PMD prioridad 1 (24): mayormente `AvoidThrowingRawExceptionTypes` / `AvoidThrowingNullPointerException` / `ReturnEmptyCollectionRatherThanNull` — deuda de manejo de excepciones, no vulnerabilidades.
- El resto (Checkstyle 17,962, PMD P3 853, SpotBugs `EI_EXPOSE_REP2` 212) es deuda de estilo/Javadoc — bajo impacto funcional, no priorizar antes que lo de arriba.

### Angular (frontend/)
- 671 usos de `any` — reduce seguridad de tipos en DTOs de API.
- 560 `label-has-associated-control` — accesibilidad, patrón repetido en formularios PrimeNG.
- Componente `admin.component.ts` concentra 122 issues, `alumno-perfil.component.ts` 100 — candidatos para refactor si se prioriza deuda técnica.
- Duplicación: `alumno-perfil.component.ts` / `profesor-perfil.component.ts` comparten un clon de 71 líneas — candidato claro a extraer un componente base `persona-perfil`. También parece existir una duplicación completa entre `apex-component-library/interactive-grid` y `app/shared/components/interactive-grid`.

Todos los reportes crudos están en `ades_testing/static_analysis/{frontend,spring,fastapi}/`.

---

## 5. Recomendaciones priorizadas

1. **Ahora:** corregir el IDOR de Portal de Familias (§0) — dato de menores expuesto cross-tutor.
2. **Esta semana:** revisar los 9 hallazgos B608 (posible SQLi) del backend FastAPI uno por uno — confirmar si usan parámetros bindeados; corregir `verify=False` en `bbb.py` y `autoescape=False` en `certificados.py`.
3. **Esta semana:** arreglar el computed signal roto en `eval_docente` (bloquea selector de profesores) y los 3 endpoints 500 nuevos (`planes-estudio`, `reportes/911` ya conocido, `superset/dashboards` ya conocido por STATE.md).
4. **Backlog QA manual:** validar en vivo (no solo lectura de código) los 13 hallazgos "Validation Missing" y 4 "SEP/Nevadi Ambiguity" de la exploración cognitiva — son leads, no bugs confirmados.
5. **Backlog técnico:** extraer componente base `persona-perfil` (alumno/profesor), consolidar `interactive-grid` duplicado, reducir uso de `any` en DTOs nuevos.
6. **Mantenimiento del framework de testing:** el fix de consentimiento OAuth2 en `01_ades_explorer_v4_complete.py` y la nueva suite `18-topbar-sidebar.spec.ts` quedan disponibles para las próximas sesiones de regresión.

---

## 6. Artefactos generados en esta sesión

- `frontend/e2e/tests/18-topbar-sidebar.spec.ts` — nueva suite (11 tests, integrada al framework Playwright TS existente)
- `ades_testing/01_ades_explorer_v4_complete.py` — fix de consentimiento OAuth2 + soporte de CLI args (`phase`, `limit`)
- `ades_testing/merge_suite18_findings.py` — fusiona hallazgos deterministas dentro del pipeline de reportes existente
- `ades_testing/static_analysis/{frontend,spring,fastapi}/` — reportes crudos + resúmenes de los 3 linters
- `ades_testing/analysis/inconsistencies_report.json` (58 hallazgos) y `ades_testing/reports/` (HTML, CSV Jira, matriz de trazabilidad)
- Este documento: `ades_testing/reports/REPORTE_QA_CONSOLIDADO_2026-07-02.md`

**Nota:** los plugins de calidad agregados a `backend-spring/pom.xml` (Checkstyle/PMD/SpotBugs) quedaron en el árbol de trabajo — decide si conservarlos como parte permanente del build o descartarlos, ya que no se hizo commit de ningún cambio.
