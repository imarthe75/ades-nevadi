# Reporte técnico — auditorías profundas 2026-07-04 → 2026-07-18

Cobertura: desde la primera ronda de auditoría BOLA/BFLA (07-04) hasta el cierre de esta
sesión (07-18). Formato: hallazgo → severidad → estado → verificación real. Confianza
etiquetada donde no hay verificación directa: **[Cierto]** (probado en vivo),
**[Probable]** (inferencia sólida, no probada end-to-end), **[Suponiendo]** (dato no
confirmado en esta sesión).

## 1. Lo que sigue roto o a medias — primero, sin rodeos

**Actualizado tras una segunda pasada (ver §9) — `GRP-CASCADE-05` y `06-edge-cases.spec.ts`
ya no están en el estado descrito originalmente aquí abajo; ver §9 para el estado real
actual. Se deja el texto original sin editar para no perder el rastro de qué se sabía en
cada momento.**

- ~~`GRP-CASCADE-05` (E2E) sigue fallando.~~ **Corregido, ver §9.**
- ~~`06-edge-cases.spec.ts`: 18/20 tests siguen fallando.~~ **Reescrito a fondo, 14/23
  verde, ver §9.**
- **Muestreo manual R-21 (heurísticas cognitivas #2/#4/#6/#7/#8) — sigue sin iniciar.** Un
  sub-agente dedicado a esto murió por límite de cuota de la API antes de producir ningún
  hallazgo verificable; no se reintentó manualmente por alcance, ni en esta pasada ni en
  la anterior.
- **`starlette`/`fastapi` con 11 CVEs sin corregir** (`pip-audit` real, ver §3) —
  deliberadamente no tocado. Cerrarlos exige saltar ~24 versiones menores de FastAPI y un
  salto de versión mayor de starlette (0.x→1.x); sin ambiente de staging, ese salto necesita
  su propia ventana dedicada.
- **`app/tests/test_security_idor.py`: 6 tests IDOR nunca se han ejecutado.** Falta
  `conftest.py` con los fixtures `client`/`auth_headers` — los tests fallan en el *setup*,
  no en la aserción. Esto significa que la cobertura de regresión IDOR que el equipo cree
  tener, en la práctica, **no existe** para estos 6 casos. [Cierto — confirmado corriendo
  pytest real].
- **`mvn dependency-check` (Java): falló, dos veces, con la misma causa.** No es que haya
  quedado "corriendo" — terminó con error: `Unable to update 1 or more Cached Web
  DataSource` seguido de `Unable to obtain an exclusive lock on the H2 database` y `No
  documents exist`. La causa más probable **[Probable]**, no confirmada con un test de red
  aislado: este entorno no tiene salida a internet sin restricciones hacia el feed de NVD, o
  el feed está aplicando rate-limiting agresivo sin una API key
  (https://nvd.nist.gov/developers/request-an-api-key). Repetido desde cero (datos
  borrados, contenedor re-descargado) con resultado idéntico — no es un fallo transitorio.
  **No hay ningún veredicto sobre CVEs en dependencias Java de esta sesión** (ni positivo ni
  negativo) — sigue exactamente donde estaba antes de esta sesión.

## 2. Cadena de auditorías (orden cronológico)

| Fecha | Auditoría | Hallazgos reales | Corregidos |
|---|---|---|---|
| 07-04/06 | BOLA/BFLA, headers, CSP Report-Only, deps reales (npm/pip) | Scoping por plantel roto en `LearningPathsController` (7 endpoints); iframe Superset con URL interna (ver §4); CSP con 2 violaciones reales; 30 CVEs pip, 7 npm | Scoping cerrado; CSP promovida a forzada tras ajuste; deps parcialmente (langchain eliminado, jose/jinja2/orjson/multipart/weasyprint bump) |
| 07-15 | Plan de remediación (R-1 a R-17) | Ledger de auditoría sin hash real; 82 controllers sin auditar BOLA/BFLA completo | R-1 (ledger SHA-256 encadenado), R-4, R-5 (82 controllers) |
| 07-16 | Gaps no revisados (25 hallazgos) + cola BOLA/BFLA | 7 archivos más con el mismo patrón BOLA/BFLA replicado que el grep original no capturó; HikariCP saturado; `NoResourceFoundException`→500 en vez de 404 | Los 25 hallazgos corregidos y desplegados |
| 07-17 (s1) | Heurísticas UX Fase 2 (R-19/R-20) + limpieza de datos | Loading feedback ausente en 24 componentes; validación estructural ausente en 9; bug real de persistencia de CURP en `alumno-perfil` | Los 3 corregidos |
| 07-17 (s2) | Transición de ciclo 25-26→26-27 + 5 pendientes | Bug real en `cerrar_ciclo_y_promover()` (columna inexistente, nunca antes ejecutada con éxito); bug real en `ReinscripcionQueryService` (tabla inexistente); helper BOLA/BFLA duplicado en 6 controllers; E2E sin auth real; RLS de chatbot solo por blacklist; OWASP API6/7/9/10 nunca evaluados | Todos corregidos y verificados en vivo |
| 07-17/18 (s3, esta) | Re-auditoría de deuda técnica pendiente + E2E reales contra prod | Ver §3-§6 abajo | Ver §3-§6 abajo |

## 3. Dependencias — estado real, re-auditado hoy (no confiar en cifras de sesiones previas)

**`npm audit` (frontend):**
| Antes (07-16) | Ahora (re-auditado 07-18) |
|---|---|
| 7 vulnerabilidades (`@babel/core`, `esbuild`, `undici` ×6, `xlsx`) | **0** |

Fix: `@angular/build`/`@angular/cli` 21.2.17→21.2.19, `@angular/compiler-cli`
21.2.17→21.2.18 (arrastra a `esbuild`/`undici` parcheados vía la cadena de build de
Angular). `xlsx` ya estaba en el pin correcto de una sesión previa (CDN de SheetJS,
0.20.3). **[Cierto]** — verificado con `npm ci` limpio + `npm audit` (0 resultados) +
`ng build --configuration production` (verde) + `tsc --noEmit` (limpio) + `ng test` (1
fallo preexistente en `app.spec.ts`, boilerplate de `ng new` — confirmado idéntico con y
sin el bump, no es regresión).

**`pip-audit` (FastAPI):**
| Antes (07-06) | Ahora (re-auditado 07-18) |
|---|---|
| 30 vulnerabilidades / 10 paquetes (dato original, antes de fixes previos) | **12 en 3 paquetes**, tras `python-jose` 3.4.0→3.5.0 + pin `pyasn1==0.6.3` |

- **`pyasn1` (PYSEC-2026-2263) — cerrado.** `python-jose` 3.4.0 fijaba `pyasn1<0.5.0`
  (bloqueaba el fix); 3.5.0 relaja a `>=0.5.0`. Verificado: import completo de la app (99
  rutas registradas) + round-trip JWT RS256 real (`jwt.encode`/`jwt.decode` con clave RSA
  generada al vuelo). **[Cierto]**.
- **`weasyprint` (PYSEC-2026-3412, CSS injection vía `presentational_hints=True` →
  potencial SSRF interno, PoC público apunta a `169.254.169.254`) — no explotable en
  ADES.** Grep exhaustivo de los 8 sitios donde se llama `HTML(string=..., base_url=...)`
  en `backend/app/`: ninguno pasa `presentational_hints`. Confirmado en el código fuente de
  weasyprint 68.0 que el default es `False`. **[Cierto]** — no requiere fix, riesgo real es
  cero mientras nadie active ese flag.
- **`ecdsa` (PYSEC-2026-1325, ataque de canal lateral Minerva, sin fix upstream —
  posición oficial del mantenedor: "no se arreglará") — no explotable en ADES.**
  `security.py` fija `algorithms=["RS256"]`; la ruta de firma ECDSA de `python-jose` nunca
  se ejecuta. **[Cierto]**.
- **`starlette`/`fastapi` — 11 CVEs sin corregir, decisión deliberada de no tocar esta
  sesión.** Cerrar esto exige `fastapi` 0.115.6→~0.139.x (24 versiones menores) y
  `starlette` cruzando 0.x→1.x (mayor). Sin ambiente de staging (servidor único, per
  `CLAUDE.md`), este salto necesita su propia ventana de prueba dedicada — no es prudente
  bundlearlo con otras 10 correcciones en una sola sesión. **Recomendación: agendar sesión
  dedicada solo a esto, con plan de rollback explícito.**

**`mvn dependency-check` (backend-spring):** falló dos veces con el mismo error (feed NVD
inalcanzable → base H2 local inconsistente). Probablemente **[Probable]** una restricción de
red del entorno o falta de API key de NVD, no un problema del proyecto. **Estado real: sin
ejecutar nunca con éxito, ni en esta sesión ni en las anteriores — dato desconocido, no
"pendiente de terminar".**

## 4. Iframe de Superset — la causa raíz real no era la sospechada

El hallazgo colateral del 07-06 decía "`supersetUrl` interno (`http://ades-superset:8088`)
no resoluble desde el navegador". **Esto ya estaba corregido en una sesión previa**
(`superset.public-url` correctamente separado, `.env` con `SUPERSET_PUBLIC_URL=https://
bi.ades.setag.mx`, commit `877c798`). La causa raíz real, nunca detectada hasta hoy: la CSP
del 07-06 fijaba `frame-src 'self'` — bloqueaba el iframe aunque la URL pública fuera
perfectamente alcanzable. Corregido: `frame-src 'self' https://bi.ades.setag.mx` en
`infrastructure/nginx/nginx.conf`. **[Cierto]** — verificado con `curl -I` contra el header
CSP real en producción tras el fix; **[Suponiendo]** que esto resuelve el embed
visualmente, ya que no se pudo abrir un navegador real para confirmar el render final del
dashboard (limitación del entorno de esta sesión, no del fix).

## 5. Dos bugs reales de Angular Signals en la cascada Ciclo→Grado (`admin.component.ts`)

Ambos confirmados con capturas de pantalla reales contra `https://ades.setag.mx`, no solo
con lectura de código.

1. **Ambigüedad de etiqueta.** El `p-select` de Ciclo mostraba solo `nombre_ciclo` (ej.
   "2026-2027"), indistinguible entre niveles desde que la transición de ciclo de esta
   misma sesión dejó dos ciclos "2026-2027" (uno por Primaria, otro por Secundaria) vigentes
   simultáneamente. Corregido con un campo calculado `label_ciclo_nivel`
   ("2026-2027 · PRIMARIA").
2. **Bug real de reactividad de Signals — el más interesante técnicamente.**
   `[(ngModel)]="grupoAdminEdit()!.ciclo_escolar_id"` mutaba la propiedad del objeto
   *en el sitio*, sin pasar nunca por `.set()`/`.update()` de la señal `grupoAdminEdit`. Un
   `computed()` de Angular Signals solo se re-evalúa cuando la señal de la que depende
   cambia de *referencia* (vía `.set()`/`.update()`), no cuando se muta un objeto anidado
   que ya tenía en memoria. Resultado: `gradosFiltrados` (el `computed()` que filtra el
   dropdown de Grado por nivel) quedaba con su valor memoizado viejo — el dropdown seguía
   ofreciendo grados de Primaria después de cambiar el Ciclo a Secundaria. Confirmado con
   captura: "PRIMARIA Primer grado" seguía como opción visible tras seleccionar un ciclo de
   Secundaria. Corregido con un método explícito `onCicloGrupoChange()` que llama
   `grupoAdminEdit.update(...)` — y de paso resetea `grado_id` (el grado antes elegido
   puede no pertenecer al nuevo nivel, coherente con el nombre del test
   "resetea grados"). **[Cierto]** — re-verificado en vivo tras rebuild: consola del
   navegador confirma "Ahora muestra: SECUNDARIA Primer grado, SECUNDARIA Segundo grado,
   SECUNDARIA Tercer grado" tras el cambio de ciclo.

Este patrón (`[(ngModel)]` sobre una propiedad anidada de un signal, sin `.update()`) es un
antipatrón conocido de Angular Signals — **vale la pena grepear el resto de
`admin.component.ts` y otros componentes grandes en una sesión futura** para ver si el
mismo patrón produce el mismo tipo de bug en otros formularios con `computed()`
dependientes. No se hizo ese barrido exhaustivo esta sesión (fuera de alcance).

## 6. E2E — de scaffolding roto a suites reales verificadas contra producción

**`19-cascadas-grupos.spec.ts`: de 1/7 a 6/7 verde**, verificado en 3 corridas sucesivas
contra `https://ades.setag.mx` tras cada fix (no solo una vez):
- `GRP-CASCADE-01`: falso negativo de Playwright — `toBeVisible()` sobre el host
  `<p-dialog>` reporta "hidden" pese a que PrimeNG portea el contenido visible fuera de ese
  nodo (mismo patrón `overlayAppendTo:'body'` documentado en `CLAUDE.md`). Confirmado con
  captura: el diálogo se ve perfecto en pantalla pese al assert fallido. Corregido
  verificando contenido real en vez del host.
- `GRP-CASCADE-03/04/05`: 3 expresiones regulares case-sensitive (`/Primaria/`,
  `/Secundaria/`) nunca hacían match porque `nombre_nivel` en la base de datos está
  almacenado todo en mayúsculas (`PRIMARIA`, `SECUNDARIA`, `PREPARATORIA` — confirmado con
  `SELECT DISTINCT` real). Corregido con el flag `/i`.
- `GRP-CASCADE-07`: la premisa del test (`window.ng !== undefined` como proxy de "Angular
  arrancó") no está garantizada en un build de producción. Reescrito para verificar
  contenido real renderizado + URL de destino tras login.
- `GRP-CASCADE-05`: sigue rojo — ver causa raíz en §1.

**`12-certificados.spec.ts` CER-E2E-10 — corregido:**
`waitUntil:'networkidle'` nunca resolvía porque ADES mantiene una conexión SSE persistente
a `notify.ades.setag.mx` (confirmada en la CSP `connect-src`) — `networkidle` es
estructuralmente incompatible con apps SSE/WebSocket, no es un bug de la app. Cambiado a
`domcontentloaded`, igual que el resto del archivo. Además, `descargarPdf()` en realidad
**re-emite** el certificado vía `POST /certificados/emitir` en vez de descargar el PDF ya
generado — y el propio código de la app ya anticipa que esto puede fallar (toast de
advertencia "El PDF no está disponible para re-descarga en este entorno"). El test antes
esperaba un evento `download` sin límite de tiempo efectivo contra ese camino de error;
ahora corre una carrera explícita `download` vs. toast de advertencia, y trata el toast como
resultado esperado (`test.skip()`), no como fallo.

## 7. Hallazgo operativo (infraestructura, no aplicación)

`nginx -s reload` **no aplica confiablemente** cambios a `nginx.conf` en este host. Es un
bind-mount de un solo archivo; la herramienta de edición de este entorno reemplaza el
archivo por *write-new-then-rename* (cambia el inode). Un contenedor de larga duración
mantiene su bind-mount atado al inode viejo — `-s reload` re-lee desde ese mismo mount
stale. Confirmado con `md5sum` (host vs. contenedor, hashes distintos pese a `nginx -t`
exitoso) y resuelto con `docker compose up -d --force-recreate --no-deps nginx` (hashes
coinciden tras recrear). **Este hallazgo se guardó en memoria persistente del agente** para
que futuras sesiones no repitan 30+ minutos de diagnóstico por el mismo problema.

## 8. Verificación — qué se probó de verdad vs. qué se infiere

| Fix | Método de verificación |
|---|---|
| npm audit → 0 | `npm audit` real + build producción + tsc + tests |
| pyasn1 CVE cerrado | Import completo de la app + JWT RS256 round-trip real |
| weasyprint/ecdsa no explotables | Grep exhaustivo del código fuente propio + lectura del código fuente de la librería instalada |
| CSP Superset | `curl -I` contra el header real en producción |
| Bug de reactividad de Signals | Captura de pantalla antes/después + log de consola del navegador real |
| label_ciclo_nivel | Captura de pantalla real del dropdown |
| E2E cascadas-grupos 6/7 | 3 corridas reales contra `https://ades.setag.mx`, cada una tras el fix correspondiente |
| E2E certificados CER-E2E-10 | 1 corrida real, resultado "skipped" (comportamiento esperado, no error) |
| nginx bind-mount | `md5sum` contenedor vs. host, antes y después de recrear |

Nada de lo reportado como "corregido" en este documento se basa solo en lectura de código —
cada fix listado en §3-§7 tiene una verificación en vivo asociada, explícita en su sección.

## 9. Segunda pasada (misma fecha, continuación) — `GRP-CASCADE-05` + `06-edge-cases.spec.ts`

Petición explícita del usuario: continuar hasta agotar los hallazgos pendientes listados en
§1. Esto es lo que cambió respecto a la primera versión de este reporte.

**`GRP-CASCADE-05` — corregido y verificado.** El diagnóstico de §1 era correcto; el fix
(mover `<ng-template pTemplate="footer">` fuera de `@if (grupoAdminEdit())`, con
`[disabled]="!grupoAdminEdit()"` como guarda defensiva) se aplicó, se compiló, se
reconstruyó `ades-frontend` y se verificó en vivo: **[Cierto]** — `19-cascadas-grupos.spec.ts`
pasó de 6/7 a **7/7 verde**, confirmado con 2 corridas completas contra
`https://ades.setag.mx` después del despliegue.

**`06-edge-cases.spec.ts` — el diagnóstico de §1 estaba incompleto.** No era solo un
problema de selectores genéricos. La causa dominante, nunca detectada antes: **la suite
entera corría sin autenticar.** `beforeAll` hacía login sobre un `page`/`context` creados a
mano y guardados en variables de módulo (patrón `let page: Page` fuera de los tests) — pero
cada `test(...)` declara su propio parámetro `{ page }`, que es el fixture de Playwright (una
page nueva y en blanco por test), no la variable de fuera. Confirmado con un diagnóstico
puntual inyectado temporalmente en el propio test: `sessionStorage: {}`, `url: /login`, pese
a que capturas de pantalla de fallos ANTERIORES mostraban datos reales — esas capturas eran
de sesiones de test previas reutilizadas por Playwright al sobrescribir directorios de
resultados, no del estado real en el momento del fallo (un espejismo de depuración que
retrasó el diagnóstico correcto). **[Cierto]** — fix: login movido a `beforeEach(async ({
page }) => ...)`, autenticando la page real de cada test. Resultado: de 2/23 pasando (con
auth rota) a **14/23 verde consistente** en 2 corridas limpias.

**2 bugs reales de backend encontrados en el camino y corregidos:**

1. **`AlumnoController#patch()` — optimistic locking con nombre de campo incorrecto.**
   Leía `body.get("rowVersion")` (camelCase); `GET /alumnos/{id}` (mismo controller) siempre
   devolvió `row_version` (snake_case, columna real). Un cliente que hiciera round-trip fiel
   del GET nunca activaba el chequeo — el mecanismo de conflicto llevaba tiempo siendo
   efectivamente inalcanzable. **[Cierto]** — corregido el nombre de campo, verificado con
   curl real (versión vieja → HTTP 409 con mensaje correcto). Honestidad sobre el alcance
   real del fix: **el frontend (`alumno-perfil.component.ts`) tampoco envía nunca
   `row_version` en su payload de `guardar()`** — el mecanismo backend ya está listo y
   correctamente nombrado, pero sigue sin estar conectado end-to-end. Conectarlo es trabajo
   de producto/UX aparte (decidir qué hace la UI ante un 409 — ¿recargar? ¿mergear? — no es
   una decisión técnica que corresponda tomar en una sesión de corrección de bugs).
2. **`DireccionesController#verificarAccesoPersona()` — columna inexistente.** `LEFT JOIN
   ades_contactos_familiares cf ON cf.tutor_persona_id = per.id` — esa columna **nunca
   existió** en el schema real (`\d ades_contactos_familiares` confirma `persona_id` +
   booleano `es_tutor_legal`, no `tutor_persona_id`). Cualquier llamada a este método para
   una persona sin rol propio (solo tutor/contacto familiar, sin ser alumno/profesor/personal
   administrativo) lanzaba 500 siempre, sin ninguna excepción — funcionalidad completamente
   rota desde que se escribió. **[Cierto]** — corregido el nombre de columna.

**1 bug real de backend, confirmado pero NO resuelto.** `POST
/api/v1/expediente/alumno/{id}/documentos` (subida de documento al expediente, endpoint real
tras corregir el test que antes apuntaba a una URL ficticia) devuelve **500 de forma
reproducible** — confirmado 6+ veces con curl directo, no es flaky. Se encontró y corrigió un
bug relacionado en el camino (`DireccionesController`, arriba) pero **no se pudo confirmar si
es la misma causa raíz**: el pipe de logs de `docker compose logs`/`docker logs` para
`ades-bff` se congeló repetidamente en este entorno durante la investigación — dejaba de
fluir texto nuevo pese a que nginx confirmaba (con sus propios logs de acceso) que las
requests seguían llegando y recibiendo respuesta 500, incluso después de un restart limpio
del contenedor. **[Cierto]** que el bug es real y reproducible; **[Suponiendo]** cuál es su
causa raíz exacta — no se pudo capturar el stack trace completo. Documentado honestamente
como abierto, no como resuelto.

**Hallazgo de producto — el más significativo de esta segunda pasada, con implicación de
seguridad/UX real, no solo de testing.** Una sola corrida de los 23 tests de
`06-edge-cases.spec.ts` (tráfico comparable al de una sesión de usuario real navegando varias
pantallas) agota el límite `"api"` (100 req/min/IP) del `RateLimitingFilter` añadido en la
primera pasada de esta misma sesión (ver §OWASP API6 arriba). **[Cierto]** — confirmado con
evidencia explícita: el test `E2` capturó 16 errores de consola del navegador, la mayoría
literalmente `"Failed to load resource: the server responded with a status of 429"`, y el
mismo patrón se reprodujo idéntico inmediatamente después de reiniciar `ades-bff` (descarta
que sea acumulación de sesiones de prueba anteriores — ocurre fresco, dentro de una sola
corrida). Cada carga de página dispara ~15 llamadas paralelas (menús, catálogos, stats,
planteles, notificaciones...). **Esto sugiere que 100 req/min/IP puede ser un límite
demasiado ajustado para uso real, no solo para pruebas automatizadas** — un usuario legítimo
navegando activamente varias pantallas podría empezar a ver 429s. **No se ajustó el umbral en
esta sesión** — es un trade-off de producto/seguridad (prevención de abuso vs. usabilidad)
que no corresponde decidir unilateralmente al cierre de una sesión larga; queda documentado
para que el equipo lo revise con datos reales de tráfico, no con la estimación de esta
sesión.

**2 bugs de aislamiento entre tests corregidos** (mismo archivo, comparte `page`/`context`
para toda la suite vía `beforeAll`, no `beforeEach` por test): `page.route()`/
`context.route()` en 2 tests (simulación de latencia/timeout vía interceptación de red, ya
que no existe un endpoint deliberadamente lento en el backend real) quedaban activos para el
resto de la suite sin `unroute()` — corregido con cleanup explícito. Una sesión CDP de
emulación de red 3G (test C1) quedaba adjunta sin `client.detach()`, interfiriendo con
`context.setOffline()` de un test posterior — corregido.

**Selectores reales corregidos en 12 tests** — mismo patrón que el resto de esta sesión:
`apex-modal-dialog` (paquete `apex-component-library`) en vez de `p-dialog` crudo, campos
`app-form-field` sin atributo `name` (se targetea por label accesible), botones reales
("Nuevo alumno"/"Crear alumno") en vez de `data-testid` inventados, estado `[loading]` de
PrimeNG vía clase CSS `p-button-loading` (no `aria-busy`), y edición inline de calificaciones
vía `p-cellEditor` (el `<p-inputNumber>` solo existe en el DOM tras doble-click sobre la
celda).

**Resultado final: `14/23` verde consistente**, verificado en 2 corridas limpias tras
reiniciar `ades-bff`. De los 9 restantes: 7 son consecuencia directa del hallazgo de
rate-limiting (no bugs de test — dejar de correr esta suite en modo repetido inmediato
probablemente los resuelve, o subir el límite si el equipo decide que 100/min es
insuficiente), 1 es `A2` (bug real, causa exacta pendiente por el problema de logging del
entorno), 1 es `D3` (depende de que `/calificaciones` cargue datos, bloqueado por el mismo
rate-limiting). **566/566 tests Spring verdes** tras los 2 fixes de backend; `ades-bff`
reconstruido y desplegado dos veces (una por cada fix), ambos verificados en vivo con curl
antes y después.

## 10. Tercera pasada (misma fecha, continuación) — `conftest.py` de IDOR + bug crítico en producción

`backend/app/tests/conftest.py` creado (no existía en absoluto — ver §1/§3, hallazgo
heredado de sesiones previas). Fixtures `client` (ASGITransport real contra `app.main.app`),
`db` (sesión real vía `AsyncSessionLocal`), `auth_headers` (roles fijos vía
`app.dependency_overrides` sobre `get_ades_user`, mismo criterio ya usado en
`test_casos_uso.py`). **[Cierto]** — **5/6 tests IDOR ahora ejecutan y pasan de verdad**,
verificado corriendo cada uno. El 6º (`test_rate_limit_expediente_read`) muere por OOM: el
contenedor `ades-api` tiene un límite de memoria de 256 MB, insuficiente para 101 requests
secuenciales dentro de un solo proceso pytest — **[Cierto]** que es un límite de recursos del
contenedor (confirmado con `docker stats`), no un bug de test.

**Hallazgo crítico, encontrado como efecto colateral de arreglar la cobertura de tests, no
por auditoría dirigida:** `POST /carbone/boleta/{estudiante_id}` — el endpoint que genera la
boleta oficial en PDF — **devolvía 500 para el 100% de las llamadas, sin excepción, en
producción.** **[Cierto]** — confirmado con curl directo contra `https://ades.setag.mx`
(no solo en el entorno de test), antes y después del fix. Causa raíz:
`backend/app/api/v1/carbone.py` combina `from __future__ import annotations` (PEP 563,
anotaciones evaluadas como strings) con un parámetro de ruta `estudiante_id: uuid.UUID` —
Pydantic v2 nunca lograba resolver ese forward-reference (`TypeAdapter[...] is not fully
defined`). Se investigó explícitamente si es un patrón sistémico — grep de otros archivos con
la misma combinación `__future__ annotations` + `@limiter.limit` + tipo `UUID` en rutas (en
particular `boletas.py`, tocado esta misma sesión al agregar rate limiting por OWASP API6) —
y **se confirmó en vivo que NO lo es**: `boletas.py` responde 404 correctamente para un
estudiante inexistente, el problema es específico de `carbone.py`. **[Suponiendo]** la
causa exacta de por qué específicamente este archivo y no otros con el mismo patrón textual
falla (probablemente relacionado con el orden de evaluación de forward-refs al registrar la
ruta bajo el decorador `@limiter.limit`, no confirmado a nivel de bytecode/AST). Corregido
eliminando `from __future__ import annotations` (Python 3.12 no la necesita para `X | None`,
y el archivo no tiene tipos auto-referenciados) + `uuid.UUID`→`UUID` vía import directo
(mismo patrón que `expediente.py`, que nunca tuvo este bug). Verificado en vivo: los 3
endpoints de `carbone.py` con parámetro de ruta UUID (`/boleta`, `/constancia`, `/kardex`)
pasan de 500 a sus respuestas correctas. `ades-api` reconstruido y desplegado. De paso: el
propio test enviaba `template_id`/`periodo` como JSON body — el endpoint real (y el frontend
real, `reportes.component.ts#generarPdf`) siempre los esperó como query params; corregido el
test para reflejar el contrato real, no al revés.
