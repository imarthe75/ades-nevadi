# Auditoría de huecos no revisados — 2026-07-16 (post cierre de "cola larga" BOLA/BFLA)

**Encargo:** tras cerrar la cola larga de 15 controllers BOLA/BFLA (sesión 07-16, banner de
CLAUDE.md), el usuario pidió una pasada adicional buscando específicamente lo que quedó **fuera
de alcance** de las auditorías previas — no repetir lo ya cubierto — para seguir avanzando hacia
>90% de fiabilidad global (hoy estimada ~70-75%, ver
`2026-07-15_analisis_honesto_entregabilidad.md`).

**Método:** 5 investigaciones en paralelo, cada una con verificación en vivo (lectura completa de
código, consultas SQL reales contra la BD de producción, `npm audit` real, inspección de
`nginx.conf`/`docker-compose.yml`), cubriendo áreas que ningún documento previo había tocado:
(A) los 41 de 83 controllers Spring sin el patrón `verificarPlantel`, (B) censo completo de
triggers de auditoría en las 184 tablas `ades_*`, (C) seguridad del backend FastAPI (nunca
auditado), (D) infraestructura/CI (CSP, pinning de imágenes, supply chain, servicios no
documentados), (E) fallas E2E no investigadas + medición real de cobertura de tests.

**Resultado más importante:** la "cola larga" que el banner de CLAUDE.md da por cerrada **no lo
está**. El barrido de los 41 controllers restantes encontró **14 controllers Spring adicionales**
con huecos BOLA/BFLA reales — un volumen comparable a la ronda que se acaba de cerrar — más un
backend FastAPI completo (IA, boletas, certificados, conducta) que nunca había recibido ninguna
auditoría de seguridad y tiene fallos críticos, incluyendo uno explotable por cualquier usuario
autenticado sin necesidad de rol elevado.

---

## 0. Resumen ejecutivo — hallazgos por severidad

| # | Hallazgo | Severidad | Área |
|---|---|---|---|
| 1 | `/chatbot/sql` (FastAPI): aislamiento por plantel implementado solo como texto sugerido al LLM, bypasseable con SQL disfrazado; expuesto directo por nginx sin pasar por el BFF | **CRÍTICO** | FastAPI |
| 2 | `/ai/alertas` (FastAPI): filtro `plantel_id` es dead code — cualquier autenticado ve alertas de riesgo/abandono de los 3 planteles | **CRÍTICO** | FastAPI |
| 3 | Grafana en `monitor.ades.setag.mx` accesible **sin ninguna autenticación** (anónimo Viewer + sin `auth_basic` en nginx, pese a que el propio comentario del archivo dice "solo admin, acceso VPN/IP") | **CRÍTICO** | Infra |
| 4 | `ConductaController` (Spring): 13 endpoints sin ningún chequeo de plantel — docente/coordinador de un plantel lee y **escribe sanciones disciplinarias** de alumnos de otro plantel. El fix de 07-04/06 resolvió BFLA (rol) pero nunca BOLA (plantel) | **CRÍTICO** | Spring |
| 5 | `/conducta/{id}/acta-pdf`: BOLA sin mitigar en FastAPI **y** en Spring a la vez (doble falla en la misma cadena) | ALTO | FastAPI+Spring |
| 6 | `GET /certificados` (FastAPI): sin RBAC ni scoping, expone folio/promedio/estado de cualquier alumno de cualquier plantel a cualquier autenticado | ALTO | FastAPI |
| 7 | `GradeAnalyticsController` (Spring): 6 endpoints sin chequeo de nivelAcceso NI plantel — hasta un alumno/padre puede consultar analítica de riesgo académico de otro plantel | ALTO | Spring |
| 8 | `DireccionesController` (Spring): domicilio + GPS + teléfono/email de cualquier persona, cross-plantel, sin scoping | ALTO | Spring |
| 9 | `HorarioIndisponibilidadController` (Spring): docente de un plantel puede **sobrescribir completamente** (borra+reinserta) la disponibilidad de un profesor de otro plantel | ALTO | Spring |
| 10 | `SuplenciaController.listarSuplencias`: el fix previo solo agregó `requireStaff`, la query sigue sin filtro de plantel — expone el calendario de ausencias institucional completo | ALTO | Spring |
| 11 | `ReinscripcionController.aprobarMasivo`: coordinador de un plantel aprueba/rechaza en bloque reinscripciones de **todo el instituto** | ALTO | Spring |
| 12 | `xlsx` (SheetJS) en frontend: 2 CVE HIGH (prototype pollution + ReDoS), **sin fix disponible** en npm | ALTO | Supply chain |
| 13 | JWT del BFF Spring no valida `aud` — riesgo de confused-deputy con otras apps OIDC del mismo Authentik | ALTO | Spring |
| 14 | 7 controllers Spring adicionales con huecos de severidad media (Encuesta, Foro, CierreCiclo, PortalAdmin, ProcesosEscolares, Grupo, `AdminController.crearUsuario`) | MEDIO | Spring |
| 15 | Cobertura E2E de seguridad "fantasma": solo 6/21 specs corren en CI; los specs que deberían probar los 403 cross-plantel (`06-edge-cases.spec.ts`, `paginacion-tareas.spec.ts`, 27 tests) usan tokens falsos/`undefined` — los fixes BOLA/BFLA de 07-16 no tienen ninguna regresión E2E real que los proteja | MEDIO | Testing |
| 16 | Cobertura de tests no solo "nunca medida" sino **no medible hoy**: backend sin plugin JaCoCo en `pom.xml`; frontend sin `@vitest/coverage-v8` instalado | MEDIO | Testing |
| 17 | Supply-chain gates de CI son cosméticos: el paso "OWASP Dependency Check" tiene `|| true` + `continue-on-error: true` en toda la cadena — nunca puede fallar el build; `npm audit` no corre en ningún workflow | MEDIO | CI |
| 18 | `ades_log_autenticacion` sigue sin `audit_biu` ni columnas de auditoría (hueco ya conocido desde 07-15, aún sin corregir) | MEDIO | BD |
| 19 | `nginx:alpine` (único punto de entrada TLS del sistema) y `ades-h5p:latest` sin pinear por digest | BAJO | Infra |
| 20 | FastAPI: solo 4/18 routers tienen rate limiting explícito; `chatbot.py` (coste LLM+DB) y `ai_assistant.py` solo tienen el límite global genérico | BAJO | FastAPI |

**Lo que SÍ se confirmó en buen estado (no repetir esfuerzo ahí):** CSP en 7/7 vhosts con
contenido real, 11 imágenes Docker pineadas por digest (incluye los servicios nuevos no
documentados), `check-api-contracts.js` corriendo en modo estricto y bloqueante en CI, JWT con
`RS256` fijo + `aud` sí validado **en FastAPI** (el hueco de `aud` es solo del lado Spring),
`AuditMiddleware` de FastAPI registrado globalmente, cadena de hash del ledger de auditoría
íntegra (`fn_verificar_cadena()` → 0 filas alteradas), 180/184 tablas `ades_*` con columnas y
trigger `audit_biu` correctos, `celery-flower` sí protegido con `auth_basic`.

---

## 1. Spring — 14 controllers adicionales con huecos BOLA/BFLA reales

Auditoría completa (lectura de archivo, no grep) de los 41 de 83 controllers que no usan
`verificarPlantel`. 20 clasificados como (a) genuinamente sin dimensión plantel, 14 como (b) ya
scopeados por un mecanismo equivalente (`getEffectivePlantelId`, `scopePlantel()`,
`resolverScopeEscritura()` — patrones correctos, usarlos de referencia), y **14 con hueco real
confirmado**:

| Controller | Endpoint(s) | Escenario de ataque |
|---|---|---|
| `ConductaController` | 13 endpoints (obtener, sancionar, plan de mejora, acta PDF, riesgo conductual) | Docente Plantel A lee/escribe sanciones y actas disciplinarias de alumnos de Plantel B |
| `GradeAnalyticsController` | Los 6 endpoints del módulo | Sin nivelAcceso NI plantel check — hasta rol ALUMNO/PADRE consulta riesgo académico de otro plantel |
| `DireccionesController` | listar/crear/actualizar/eliminar domicilio | `requireStaff` sin plantel — PII de domicilio+GPS cross-plantel |
| `HorarioIndisponibilidadController` | listar + sobrescribir (borra+reinserta) | Docente edita disponibilidad de profesor de otro plantel |
| `HorarioFranjaController` | create/update/delete | Coordinador crea/edita/borra franjas horarias de otro plantel (mass-assignment de `plantelId` en el body) |
| `SuplenciaController` | `listarSuplencias`, `crearSuplencia` | Calendario institucional completo de ausencias/suplencias visible cross-plantel |
| `CierreCicloController` | `obtenerIndicadores`, `validarCompletitud` | Director ve matrícula/promedio institucional completo si omite `plantel_id` |
| `ReinscripcionController` | `aprobarMasivo`, `accionIndividual` | Aprobación/rechazo masivo institucional de reinscripciones |
| `ProcesosEscolaresController` | `resolverAdmision`, `registrarBaja`, `reactivarEstudiante` | Asimetría: los `listar*` sí filtran por plantel, las mutaciones no |
| `PortalAdminController` | convocatorias, postulaciones, exportación ARCO | Director administra y exporta datos de admisión de otro plantel |
| `EncuestaController` | resultados, respuestas crudas | Respuestas de texto libre de detección de bullying, cross-plantel |
| `ForoController` | leer/publicar en foro tipo PLANTEL | Docente publica/lee en foro de otro plantel |
| `GrupoController` | los 3 endpoints del módulo | Enumeración cross-plantel de estructura de grupos (severidad baja, mismo patrón) |
| `AdminController.crearUsuario()` | POST creación de usuario | `puedeEditarOtrosPlantelUsuarios()` se usa en `actualizarUsuario` (PATCH) pero no en `crearUsuario` (POST) — ADMIN_PLANTEL crea cuentas, incluso otro ADMIN_PLANTEL, en plantel ajeno |

**Nota sobre falsos positivos descartados:** `ExpedienteLaboralController` de la lista original
resultó ser un stub vacío migrado (`modules/expediente/`); el controller real y activo
(`modules/expediente_laboral/`) sí usa `verificarPlantel` correctamente en sus 5 endpoints —
confusión por duplicidad de nombre de clase, no un hueco.

**Patrón repetido:** en 4 de los 14 casos (Suplencia, ProcesosEscolares, Reinscripcion,
CierreCiclo) el endpoint de **listado** sí está bien scopeado pero el de **mutación/agregación**
al lado no lo está — el mismo "fix asimétrico" (arreglar la mitad visible del patrón y dejar la
otra mitad) que ya se documentó como causa raíz en sesiones anteriores.

**Mass assignment:** no se encontró el patrón clásico de un usuario auto-asignándose
`nivelAcceso`. La única instancia real es el `AdminController.crearUsuario()` de arriba, que es
más BOLA de plantel-destino que mass-assignment puro.

---

## 2. FastAPI — nunca auditado, huecos críticos confirmados

El backend Python (`backend/app/`, IA + render de documentos) nunca había recibido una auditoría
de seguridad dedicada — todas las rondas previas (07-04, 07-14, 07-15, 07-16) fueron
exclusivamente sobre el BFF Spring.

- **`/chatbot/sql` (`chatbot.py:107-262`) — CRÍTICO.** El aislamiento por plantel/rol se
  implementa **solo como texto inyectado en el prompt** del LLM ("Agregar siempre: WHERE
  plantel_id = '...'"). El SQL generado se ejecuta directo con una única validación
  (`sql_upper.startswith("SELECT")`), bypasseable con una CTE (`WITH x AS (DELETE ... RETURNING
  *) SELECT * FROM x`) o con inyección de prompt para que el LLM ignore el hint. Cualquier
  autenticado (incluido ALUMNO/PADRE) llega aquí, y la ruta está expuesta **directo por nginx**,
  sin pasar por el BFF Spring.
- **`/ai/alertas` y `/ai/alertas/scan/{grupo_id}` (`ai_assistant.py:225-290`) — CRÍTICO.** El
  parámetro `plantel_id` se acepta pero nunca se usa en el `WHERE`; `grupo_id` tampoco se valida
  contra el plantel del usuario. Solo requiere JWT válido, sin chequeo de rol.
- **`/conducta/{id}/acta-pdf` (`conducta.py`) — ALTO.** Sin chequeo en FastAPI; y del lado Spring,
  a diferencia de `SaludAvanzadaController` (corregido el mismo día), `ConductaController` nunca
  recibió `verificarPlantel` — falla en cadena en ambos lados.
- **`GET /certificados` (`certificados.py:176-214`) — ALTO.** Depende de `get_current_user` (no
  `get_ades_user`), sin RBAC ni scoping — lista los últimos 50 certificados de cualquier alumno de
  cualquier plantel a cualquier cuenta autenticada.
- **`boletas.py`/`salud_avanzada.py` — MEDIO, mitigado hoy.** Sin chequeo propio en FastAPI, pero
  esas rutas están ausentes del passthrough directo de nginx y el Spring side sí valida antes de
  proxear. Es defensa en profundidad rota: si cambia la regex de nginx o se expone
  `ades-api:8000`, el hueco queda explotable de inmediato.
- **Lo que sí está bien:** validación JWT (`core/security.py`) sólida — `RS256` fijo, `audience`
  verificado, JWKS con cache; `AuditMiddleware` registrado globalmente; `app/routers/agente.py` es
  código muerto no alcanzable, sin riesgo real.
- **Rate limiting real:** solo 4/18 routers tienen límites explícitos; `chatbot.py` (coste LLM+BD)
  y `ai_assistant.py` no tienen límite específico más allá del default global (1000/hora).

**Causa raíz común:** cada módulo de FastAPI asume que "alguien más" (el BFF Spring o el propio
LLM) hace el control de acceso, en vez de validarlo él mismo — el mismo patrón "fix asimétrico"
que ya se identificó del lado Spring, replicado en la capa Python.

---

## 3. Infraestructura — un hallazgo crítico nuevo, resto confirmado en buen estado

- **CRÍTICO — Grafana sin autenticación.** `monitor.ades.setag.mx` sirve Grafana con
  `GF_AUTH_ANONYMOUS_ENABLED=true` + rol Viewer, y la ruta `/` de ese vhost en `nginx.conf` **no**
  tiene `auth_basic` (a diferencia de `/flower/` en el mismo vhost, que sí lo tiene, con
  `.htpasswd`). El propio comentario del archivo ("solo admin, acceso VPN/IP") no se refleja en
  ninguna directiva `allow`/`deny` real — cualquiera que resuelva el subdominio ve dashboards
  operativos de infraestructura sin credenciales.
- **CONFIRMADO — CSP en 7/7 vhosts** con contenido real (el 8vo es solo el redirect HTTP→HTTPS).
- **CONFIRMADO — pinning de imágenes**, 11/17 por digest SHA-256, incluidos los servicios nuevos
  no documentados en CLAUDE.md (grafana, paperless, pgbouncer, node-exporter). Pendiente:
  `nginx:alpine` (el punto de entrada TLS de todo el sistema) y `ades-h5p:latest` siguen en tag
  flotante.
- **CONFIRMADO — `check-api-contracts.js`** corre en modo `--strict` y bloqueante en
  `e2e-tests.yml`.
- **REFUTADO — supply chain scanning.** El paso "OWASP Dependency Check" en
  `security-audit.yml` tiene `|| true` en toda la cadena de comandos más `continue-on-error:
  true` en el step — nunca puede bloquear un merge, es puramente decorativo. `npm audit` no corre
  en ningún workflow. Corrida manual real (`npm audit --omit=dev`): **`xlsx` HIGH** (prototype
  pollution + ReDoS, sin fix disponible en el registro npm — requiere migrar a la distribución
  oficial de SheetJS o reemplazar la librería) y `quill` LOW (XSS, fix disponible pero es bump
  mayor). `pom.xml` de Spring no tiene ningún plugin de dependency-check.
- **NUEVO — JWT sin validar `aud` en Spring.** `SecurityConfig.java` construye el
  `NimbusJwtDecoder` sin `.jwtValidator()` adicional — valida firma+`exp` pero no `iss`/`aud`.
  Riesgo de confused-deputy con otra app OIDC del mismo Authentik (ej. Superset) si comparte
  clave de firma. (FastAPI sí valida `aud` correctamente — el hueco es solo del lado Spring.)
- **Servicios no documentados en CLAUDE.md — sin otros hallazgos de severidad además de
  Grafana:** paperless, carbone, pgbouncer(-exporter), node-exporter sin exposición directa;
  h5p expuesto solo como player de assets (no editor); ntfy con lectura anónima por diseño
  (mitigado por topics por usuario); flower correctamente protegido.

---

## 4. Testing — cobertura "fantasma" y medición imposible hoy

- **Solo 6/21 specs E2E corren en CI** (28.6%) — confirma que el número subió de 5 a 6 tras
  agregar `10-rbac.spec.ts` en la sesión 07-16, pero los 15 restantes, incluidos los que deberían
  cubrir exactamente los escenarios BOLA/BFLA recién cerrados, siguen fuera.
- **Hallazgo de mayor impacto:** `06-edge-cases.spec.ts` y `paginacion-tareas.spec.ts` (27 tests
  combinados) usan tokens de auth falsos/`undefined` — sus aserciones de seguridad (B1: acceso
  cross-plantel a alumno debe dar 403; B2: COORDINADOR creando USER debe dar 403; B3: roster
  cross-plantel debe dar 403) **nunca ejercitan el camino real de autorización**, probablemente
  fallando en 401 antes de llegar a la lógica de negocio. Es decir: **los 14+ controllers
  corregidos en las últimas 2 sesiones (y los 14 nuevos huecos de este documento, una vez
  corregidos) no tienen ninguna prueba E2E automatizada real que los proteja de regresión.**
- **`19-cascadas-grupos.spec.ts`:** 6 de 7 fallos sin causa raíz confirmable por análisis estático
  (posible timing de animación PrimeNG, no se pudo reproducir en este entorno sin runtime de
  Playwright disponible). El 7mo (`GRP-CASCADE-07`) SÍ se confirmó como bug del propio test: hace
  `assert window.ng !== undefined`, pero Angular elimina ese hook de debug en builds de
  producción (`ngDevMode` guard) — confirmado inspeccionando el bundle real servido por
  `ades-frontend` (0 matches de `ngDevMode`/`publishDefaultGlobalUtils`). No es un bug de
  producto.
- **`12-certificados.spec.ts` CER-E2E-10:** el único otro spec activo que usa
  `waitUntil:'networkidle'` es `06-edge-cases.spec.ts` (ya sabemos que es scaffold), así que el
  riesgo de propagación es bajo hoy, pero es un patrón fràgil a evitar en specs futuros contra
  `/certificados` (polling no identificado que impide alcanzar networkidle).
- **Cobertura de tests — más grave que "nunca medida": no es medible con las dependencias
  actuales.** Backend: cero rastro de `jacoco-maven-plugin` en `pom.xml` — no hay ni siquiera un
  goal que correr. Frontend: `ng test --coverage` existe como flag pero falla de inmediato porque
  falta la dependencia `@vitest/coverage-v8`. Antes de poder reportar un % real de cobertura hay
  que instalar/configurar tooling en ambos stacks.

---

## 5. Base de datos — censo completo de las 184 tablas `ades_*`

Confirmado con `auditoria.reporte_cobertura()` + verificación cruzada por
`information_schema.columns`: **180/184 tablas correctas** (columnas de auditoría + `audit_biu`
activo; `PENDIENTE_AIUD` es el estado esperado, diferido a go-live por diseño). Solo **4 con
hueco real**, de las cuales 3 son excepciones legítimas (`ades_audit_log`,
`ades_encryption_audit`, `ades_mv_refresh_log` — bitácoras/infraestructura interna del propio
sistema de auditoría, no entidades de negocio con PII) y **1 gap real que persiste**:
`ades_log_autenticacion` sigue sin `audit_biu` ni la mayoría de las columnas de auditoría (ya
identificado el 07-15, sin corregir a la fecha). `auditoria.fn_verificar_cadena()` corrió limpio
(0 filas alteradas) — el ledger de auditoría en sí está íntegro.

Nota de higiene documental menor: el comentario de `ades_audit_log` declara un esquema de "hash
MD5 encadenado" que no existe como columna en la tabla real — descripción desactualizada, no
riesgo de seguridad.

---

## 6. Impacto en la estimación de fiabilidad

La tabla de `2026-07-15_analisis_honesto_entregabilidad.md` estimaba "Seguridad/autorización" en
~60-65%. Este documento no mejora esa cifra — la empeora en el sentido de que revela que el
denominador real de trabajo pendiente era mayor de lo que el banner de CLAUDE.md sugería: **14
controllers Spring + un backend FastAPI completo** quedan con huecos BOLA/BFLA reales y no
mitigados hoy, más 1 exposición crítica de infraestructura (Grafana). Para llegar a >90% de
fiabilidad global, esto es prerrequisito antes de cualquier avance en usabilidad/accesibilidad
(que sigue en ~40%, sin tocar en este documento por estar ya bien diagnosticado en
`2026-07-16_plan_revision_heuristicas_cognitivas.md`).

---

## 7. Recomendación de orden de remediación

1. **Inmediato, bajo riesgo de tocar (no toca red/firewall):** agregar `auth_basic` a la ruta `/`
   de Grafana en `nginx.conf` — mismo patrón que ya existe para `/flower/` en el mismo archivo.
2. **Crítico, mismo patrón que la ronda 07-16:** aplicar `verificarPlantel`/`AdesUserService` a
   los 14 controllers Spring de la sección 1, empezando por `ConductaController` (datos
   disciplinarios de menores, mayor sensibilidad) y `GradeAnalyticsController` (sin ni siquiera
   chequeo de rol).
3. **Crítico, backend distinto:** corregir `/chatbot/sql` (reemplazar el RLS-por-prompt por un
   filtro real a nivel de query, o restringir la tabla accesible por rol/plantel antes de pasar al
   LLM) y `/ai/alertas*` (aplicar el filtro `plantel_id` que ya existe pero no se usa) en FastAPI.
4. **Alto:** conectar `06-edge-cases.spec.ts`/`paginacion-tareas.spec.ts` a autenticación OIDC
   real — sin esto, cualquier fix de los puntos 2-3 sigue sin protección de regresión automatizada.
5. **Medio:** decidir reemplazo de `xlsx` (sin fix disponible), agregar `.jwtValidator()` con
   chequeo de `aud` en `SecurityConfig.java`, instalar `jacoco-maven-plugin` y
   `@vitest/coverage-v8` para poder medir cobertura real por primera vez.
6. **Bajo:** pinear `nginx:alpine`/`ades-h5p`, quitar el `|| true` del step de OWASP
   Dependency Check en CI (o eliminarlo si no se piensa mantener), corregir `ades_log_autenticacion`.

Ningún punto de este documento requiere tocar firewall/iptables/puerto 22 ni el archivo
`docker-compose.yml` de forma destructiva — son cambios de código de aplicación, configuración de
nginx (agregar, no quitar, una directiva) y dependencias.
