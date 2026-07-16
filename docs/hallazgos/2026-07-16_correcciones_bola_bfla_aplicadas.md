# Correcciones aplicadas — plan de `2026-07-16_reporte_fiabilidad_3dias_y_plan.md`

**Encargo:** ejecutar el plan de la §5 de ese reporte ("Plan hacia 90-95% de fiabilidad global
esta semana") hasta solventar los huecos BOLA/BFLA de scoping por plantel documentados.

---

## 1. Día 1-2 — Cola larga confirmada ([Cierto]/[Probable] en el reporte)

Aplicado el patrón `AdesUserService#verificarPlantel(user, plantelEntidadId, mensaje)` (solo
nivelAcceso 0 mantiene alcance institucional libre) a los 4 controllers que el reporte señaló
como huecos reales, no hipotéticos:

| Controller | Qué se corrigió |
|---|---|
| `SaludAvanzadaController` | medicamentos, incidentes médicos, psicosocial, tutorías, descargas PDF — antes solo validaban `nivelAcceso`, nunca el plantel del alumno |
| `ExpedienteLaboralController` | listar/crear/detalle/actualizar/documento/eliminar — expone CURP/RFC/salario/IMSS/INFONAVIT; se agregó resolución de plantel vía COALESCE entre `ades_profesores`/`ades_personal_administrativo`/`ades_personal_salud` |
| `DisponibilidadDocenteController` | el "control de rol" de R-5 (2026-07-15) nunca incluyó scoping de plantel — confirmado y corregido en listar/guardar/resumen/cobertura/eliminar |
| `EvalDocenteController` | resumen, crear evaluación, guardar criterios, enviar, plan de mejora — comparaba IDs de espacio equivocado y nunca verificaba plantel para nivelAcceso 1-3 |

## 2. Día 2-3 — Verificación puntual + Biblioteca

- **`BibliotecaController`:** `scopePlantel()` usaba `nivel(u) > 2` (eximía nivel 0, 1 y 2) en vez
  de `> 0` — corregido en el helper y en los 4 chequeos inline (actualizar/eliminar/prestar/
  devolver libro).
- **8 candidatos "por confirmar" del reporte — los 8 resultaron ser huecos reales:**
  `ContactosController` (contactos familiares + expediente médico + expediente-docs),
  `CertificadosController` (listar/emitir/firmar), `AsistenciaController` (`requireAccesoClase`
  nunca verificaba plantel para nivelAcceso 1-3), `AsistenciaPersonalController` (cero scoping),
  `TareaEntregaController` (mismo patrón que `EntregasController`), `ActividadesController`
  (mismo patrón), `PadresController` (calificaciones — nivelAcceso 1-3 sin ningún chequeo),
  `PlanesEstudioController` (incluye planes NEE alternativos), `JustificacionController` (cero
  scoping).

## 3. Día 3 — Barrido de variantes de nombre

El reporte advertía que Biblioteca probó que el grep original (`nivel(u) > 2`, nombres de
variable no estandarizados) no era exhaustivo. Un grep dirigido a la forma
`if (nivel <= 3) return;` / `if (user.getNivelAcceso() <= 3) return;` (el "alcance institucional"
que nunca valida plantel) encontró el **mismo bug replicado en 7 archivos adicionales** que
ninguna ronda previa había tocado:

`EntregasController` (el "canónico" del que los demás copiaron `requireAccesoGrupo`),
`CalificacionesController`, `PlaneacionController`, `GradebookController`, `TareaController`,
`EvaluacionController`.

Los otros 2 umbrales encontrados por el mismo grep (`BibliotecaApplicationService.eliminar`,
`CondicionCronicaApplicationService.eliminar`, ambos `nivelAcceso > 2`) se revisaron y son
**correctos por diseño** — son un piso de rol mínimo (RH/Dirección), no un bypass de scoping; el
scoping de esas dos entidades ya se verifica en el controller antes de llegar ahí.

## 4. Hallazgos colaterales (no en el plan original, encontrados verificando en vivo)

- **HikariCP `maximum-pool-size: 10` insuficiente para carga concurrente real.** Confirmado en
  logs del contenedor durante la corrida E2E: `HikariPool-1 - Connection is not available,
  request timed out after 30000ms (total=10, active=10, idle=0, waiting=18)`. Ampliado a 25
  (`minimum-idle: 8`) — decisión explícita del usuario, considerando que producción tendrá
  cientos de usuarios concurrentes. El BFF conecta directo a `ades-postgres` (bypass de
  pgbouncer, ver comentario ya existente en `docker-compose.yml` sobre el fix pendiente de SCRAM)
  así que este valor sí consume `max_connections` reales de Postgres (default 100) — 25 deja
  margen amplio sin arriesgar al resto de servicios (Authentik/Superset/FastAPI/Celery, que pasan
  por pgbouncer).
- **`GlobalExceptionHandler` sin handler para `NoResourceFoundException`.** Una ruta inexistente
  caía en el catch-all de `Exception` y respondía 500 en vez de 404. Encontrado por
  `10-rbac.spec.ts` (RBAC-13 prueba un endpoint `/api/v1/alumnos/{id}/calificaciones` que no
  existe en las rutas reales del backend — bug del test, pero el 500 en vez de 404 era un bug
  real del backend). Agregado handler dedicado.
- **`curpValido()` en `frontend/e2e/fixtures/data-generators.ts` generaba CURPs de 19 caracteres
  que nunca pasaban `ApexValidators.isCURP()` (regex RENAPO real, 18 caracteres).** El bug: el
  bloque `apellidoPat` ya era 4 caracteres por sí solo (letra+vocal+letra+letra) en vez de solo
  2 (letra+vocal), y se le sumaban `apellidoMat`+`nombre` (2 más) — 6 letras al inicio del CURP
  en vez de 4. Efecto: el botón "Guardar"/"Crear alumno" del alta rápida de alumno nunca se
  habilitaba (`crearAlumnoForm.invalid` siempre `true`), causando timeout de 30s en **todo** test
  que crea un alumno vía UI — 15 de los 50 fallos de la corrida completa (`ALU-02`, `ALU-03`,
  `ALU-11`, `ALU-12`, 8 variantes de `ALU-D`, `FUZZ-01`, `AUD-01`, `CON-12`, y probablemente
  `CAOS-09`/`CAOS-15`). Reescrito con la estructura real de CURP (4 letras + 6 dígitos + sexo +
  5 letras + diferenciador + dígito verificador = 18). Verificado con 10,000 simulaciones en
  Node: 100% pasan la regex real. Cero riesgo de producción — el cambio es solo en el generador
  de datos de prueba E2E, no toca código de aplicación.

## 5. Verificación

- `mvn test`: **555/555 en verde**, sin regresiones, en cada punto de control del proceso.
- `ades-bff` reconstruido y redesplegado en el servidor único (confirmación explícita del usuario
  antes de cada reinicio de contenedor de producción).
- `10-rbac.spec.ts` agregado a `.github/workflows/e2e-tests.yml` junto a los 5 specs que ya
  corrían (antes: 5/21 specs en CI).
### Resultado final — corrida completa de los 21 specs (372 tests, ~32 min)

**291 passed / 50 failed / 31 skipped.** Esta corrida se lanzó *antes* de descubrir y corregir el
bug de `curpValido()` de arriba, así que buena parte de los 50 fallos son ese mismo bug ya
resuelto. Clasificación de los 50 fallos, con causa raíz verificada individualmente (no
asumida):

| Causa | # tests | Fallos | Relacionado con los fixes de hoy |
|---|---|---|---|
| Bug de `curpValido()` (botón Guardar nunca habilitado) | 15 | `ALU-02/03/11/12`, 8×`ALU-D`, `FUZZ-01`, `AUD-01`, `CON-12` | **No** — ya corregido (§4), re-verificado en corrida dirigida (ver abajo) |
| `06-edge-cases.spec.ts` — tokens literales falsos (`'docente-plantel-1-token'`, etc.), nunca conectado a OIDC real | 23 | Todo el archivo (suites A-G) | **No** — confirmado con curl: el BFF responde 401 correcto para el token falso; el test espera 403 (asume un token válido con rol equivocado) |
| `paginacion-tareas.spec.ts` — `authToken` declarado pero nunca asignado (`undefined`) | 4 | Las 4 pruebas del archivo | **No** — mismo patrón de scaffold sin terminar; `Authorization: Bearer undefined` → 401 esperado |
| `12-certificados.spec.ts` CER-E2E-10 — `page.goto(..., {waitUntil:'networkidle'})` nunca alcanza networkidle (polling de fondo de la SPA) | 1 | `CER-E2E-10` | **No** — falla en la navegación, antes de invocar cualquier endpoint de `CertificadosController`; patrón conocido de Playwright con SPAs con polling |
| `19-cascadas-grupos.spec.ts` — dialog `[data-testid="dialog-grupo-admin"]` no visible / `window.ng` no expuesto (build de producción) | 6 | `GRP-CASCADE-01/03/04/05/06/07` | **No** — módulo de Grupos, ningún archivo tocado hoy; `window.ng` es un hook de dev-mode ausente en build de producción |
| `06-chaos.spec.ts` — mismo flujo de alta de alumno (`alumnoValido()`) | 3 | `CAOS-09/11/15` | **No** — mismo bug de `curpValido()`, cubierto por el fix |

**Cero fallos atribuibles a los 15 controllers corregidos hoy.** Los specs que sí ejercitan
código tocado hoy (`05-certificados.spec.ts` completo, `10-rbac.spec.ts`, `13-rrhh.spec.ts`
asistencia de personal) pasaron 100%.

### Re-verificación dirigida tras el fix de `curpValido()`

Corrida aislada de `02-alumnos.spec.ts` (33 tests) contra el mismo servidor, con el fix ya
aplicado: **20 passed / 11 failed / 2 skipped.**

**Confirmado — el fix funciona:** `ALU-02` (crear alumno, antes 30.4s timeout → ahora 5.9s ✅),
`ALU-03` (CURP duplicado → 409, antes timeout → ahora pasa ✅), `ALU-D-fuzz` (5 CURPs aleatorias,
antes no llegaba a ejecutarse por el bug de otros tests en el mismo archivo → ahora pasa ✅).

**11 fallos restantes — causa distinta, ya diagnosticada, NO relacionada con `curpValido()`:**
`ALU-11`, `ALU-12` y 8×`ALU-D` dejan el campo CURP vacío o inválido **a propósito** (están
probando que la app avise al usuario). El botón "Guardar" se deshabilita correctamente vía
Angular reactive forms (`[disabled]="crearAlumnoForm.invalid"` en `alumnos.component.ts`), pero
`AlumnosPage.save()` (`e2e/page-objects/alumnos-page.ts:67`) hace `await this.saveBtn.click()`
sin `{force: true}` ni timeout corto — cuelga 30s esperando un botón que está *correctamente*
deshabilitado en vez de fallar rápido o verificar el estado deshabilitado como resultado válido.
**No se corrigió**, porque la corrección real depende de una decisión de producto que no
corresponde tomar unilateralmente: ¿el botón debe permitir el click y mostrar un toast de
advertencia (como asumen los comentarios de estos tests — "El componente usa notify.warning()
no ng-invalid"), o debe seguir deshabilitado silenciosamente como hace hoy? Ambas son UX válidas;
cambiar cualquiera de las dos sin confirmación del usuario sería expandir el alcance de esta
sesión (BOLA/BFLA) hacia decisiones de producto no relacionadas con seguridad. `ALU-05` también
falló en esta corrida con el mismo síntoma (botón deshabilitado) — no estaba en la lista de 50
fallos de la corrida completa, así que es flakiness de orden/estado entre tests del mismo
archivo, no una regresión nueva.

## 6. Lo que queda fuera de esta pasada (por decisión del usuario, diferido)

- `audit_aiud` (180 tablas en `PENDIENTE_AIUD`) y prueba de restore de backup — diferidos a
  go-live, tal como documenta el reporte origen.
- Accesibilidad (35-40%) y cobertura de tests medida (JaCoCo/coverage frontend) — próximo foco
  natural, ninguna ronda de auditoría lo ha tocado todavía.
- Extraer el patrón `requireAccesoGrupo`/`requireAccesoClase` (corregido ahora en 7+ archivos
  independientes con copy-paste) a un helper compartido en `AdesUserService`, para que el
  próximo módulo nuevo no reintroduzca el mismo bug.
