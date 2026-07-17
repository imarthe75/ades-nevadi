# Plan de revisión — Heurísticas cognitivas (10 principios, CLAUDE.md §HEURÍSTICAS COGNITIVAS)

**Encargo:** el reporte `2026-07-16_reporte_fiabilidad_3dias_y_plan.md` (§2.4) señaló que las
10 heurísticas cognitivas de CLAUDE.md nunca han sido objeto de checklist ni verificación
sistemática — a diferencia de seguridad (3 rondas), es deuda 100% virgen. Este documento define
el método para auditarlas de forma reproducible (no solo lectura de código por un agente) y
reporta los primeros resultados reales, obtenidos hoy con grep verificado en vivo sobre los 79
componentes de `frontend/src/app`.

**Por qué grep y no solo criterio de agente:** el propio proyecto ya se quemó tres veces
declarando "COMPLETADO" sobre trabajo de otro agente que no sobrevivió una verificación
independiente (ver `2026-07-16_reporte_fiabilidad_3dias_y_plan.md` §0). Las heurísticas de
Nielsen son en su mayoría cualitativas, así que aplico el mismo estándar: todo lo que se pueda
convertir en una señal objetiva y reproducible (grep + conteo), se mide así primero; lo que no
se pueda, se marca explícitamente como pendiente de muestreo manual (§5) en vez de estimarlo.

---

## 1. Metodología — operacionalizar 10 principios cualitativos

| # | Heurística (CLAUDE.md) | Señal grep objetiva usada hoy | Lo que grep NO puede medir (requiere muestreo manual, §5) |
|---|---|---|---|
| 1 | Visibilidad de estado | `isLoading`/`p-progressSpinner`/`.loading =`, uso de `MessageService` | Si el spinner/toast aparece en el momento correcto y no se queda colgado |
| 2 | Terminología real | — (no operacionalizable por grep) | Si "grupo", "grado", "ciclo", "constancia" se usan con el sentido SEP/UAEMEX correcto y consistente entre pantallas |
| 3 | Control y libertad | `ConfirmationService`/`.confirm(` en componentes con acción destructiva | Si el flujo de cancelar realmente revierte estado a medio llenar (no solo cierra el modal) |
| 4 | Consistencia | `ChangeDetectionStrategy.OnPush`, patrón `template:` inline vs `templateUrl` | Consistencia visual (spacing, orden de botones, paleta) entre módulos — requiere revisión visual |
| 5 | Prevención de errores | `Validators.required` (y afines), `[disabled]` en botones de submit | Validación de rango/longitud real vs. solo `required`; mensajes de validación visibles al usuario |
| 6 | Reconocimiento vs recuerdo | `placeholder=`, presencia de `breadcrumb` | Si el usuario puede reconocer dónde está sin memorizar rutas — necesita recorrido real |
| 7 | Flexibilidad y eficiencia | `p-autocomplete` (ya documentado como migrado), atajos de teclado | Cobertura real de atajos — sin convención de atajos en el proyecto hoy, difícil de gatillar por grep |
| 8 | Diseño minimalista | — (subjetivo) | Conteo de widgets por dashboard, densidad de información — requiere revisión visual módulo por módulo |
| 9 | Recuperación de errores | `catchError`, `severity: 'error'`, mensaje amigable vs. error crudo | Si el mensaje mostrado al usuario es comprensible o es un stack/código HTTP crudo |
| 10 | Ayuda integrada | `pTooltip`, `aria-*` | Calidad/utilidad real del texto del tooltip (vs. tooltip vacío o redundante con el label) |

**Limitación reconocida:** las heurísticas 2, 4 (parcial), 6 (parcial), 7 y 8 no son
100% medibles por grep. Para esas, este documento solo reporta señales parciales +
propone un método de muestreo manual en §5 — no se reporta un % para ellas hoy porque
sería una estimación disfrazada de medición, exactamente el patrón que este proyecto ya
decidió dejar de tolerar.

---

## 2. Resultados de hoy — evidencia real (79 componentes, `frontend/src/app`, grep verificado en vivo)

| Señal | Componentes con la señal | % sobre 79 | Heurística |
|---|---|---|---|
| `ChangeDetectionStrategy.OnPush` | 79/79 | **100%** [Cierto] | #4 (parcial) |
| `placeholder=` presente | 66/79 | 84% | #6 (parcial) |
| `pTooltip` presente | 40/79 | 51% | #10 (parcial) |
| Botón/acción "Cancelar" | 45/79 | 57% | #3 (parcial) |
| `MessageService` (feedback tras acción) | 23/79 | 29% | #1 (parcial) |
| `isLoading`/spinner/`.loading` | 19/79 | 24% | #1 (parcial) |
| `Validators.required` u otro validador reactivo | 7/79 | 9% | #5 (parcial) |
| `[disabled]` condicionado en botón | 35/79 | 44% | #5 (parcial) |
| `aria-*` | 1/79 | 1.3% | #10 (ya conocido, ver reporte 07-16 §2.4) |
| `breadcrumb` | 2/79 | 2.5% | #6 (parcial) |
| `deshacer`/`undo` | 12/79 | 15% | #3 (parcial) |
| `ConfirmationService`/`.confirm(` (cualquier uso) | 4/79 | 5% | #3 |

**Nota sobre OnPush 100%:** esto es información nueva, no reportada en el análisis 07-16 (que
lo marcaba "Fase 2, sin auditar"). Vale la pena corregir esa entrada en la tabla de 16 puntos
de CLAUDE.md — está completo, no pendiente.

---

## 3. Hallazgo más severo — acciones destructivas sin confirmación

Cruce dirigido (no solo conteo aislado):

- **35/79 componentes (44%)** contienen una acción de eliminar/borrar/dar de baja (`eliminar`,
  `delete(`, `darDeBaja`, patrones equivalentes).
- **De esos 35, solo 4 (11%) usan `ConfirmationService`/`.confirm(`.**
- **31 componentes (89% de los que tienen acción destructiva) permiten eliminar/dar de baja
  sin ningún diálogo de confirmación detectado.**

Esto mapea directo a la Heurística #3 (Control y libertad — "flujos reversibles,
cancelar/deshacer") del CLAUDE.md, y es el hallazgo con mayor impacto/riesgo real de los 10
principios: una acción destructiva sin confirmación es indistinguible, para el usuario, de un
bug de pérdida de datos — el mismo tipo de daño (dato perdido sin aviso) que motivó el
endurecimiento del ledger de auditoría en la Regla Mandatoria #3-#7. **[Cierto] — verificado
por grep cruzado hoy, no es estimación.**

No identifiqué todavía cuáles de los 31 son catálogos de bajo riesgo (ej. eliminar un filtro
guardado) vs. datos de negocio reales (ej. dar de baja un alumno, eliminar una calificación) —
es el primer paso del plan de ejecución (§4, Fase 1).

---

## 4. Plan de ejecución — por fase, orden impacto/esfuerzo

### Fase 1 — ✅ COMPLETADO 2026-07-16 (mismo día, misma sesión)

Triage real de los 31 componentes candidatos de §3 (no estimado — cada uno se leyó y se
clasificó):

**21 archivos con hueco real confirmado y corregido** (patrón replicado de
`padres-admin.component.ts`/`admin.component.ts`: `ConfirmationService` + `ConfirmDialogModule`
+ `<p-confirmDialog />` + `accept: () => { <lógica original sin cambios> }`):

`aulas`, `badges` (2 métodos), `bbb`, `biblioteca`, `calendario`, `capacitaciones`,
`condiciones-cronicas`, `encuestas` (2 métodos), `expediente-doc`, `h5p`, `horarios`, `ia`,
`medico`, `optativas`, `personal-admin`, `planes-estudio` (3 métodos), `ponderacion-config`,
`reportes`, `rubricas` (2 métodos), `shared/alumno-perfil`, `shared/domicilio` (2 métodos).

De estos, varios (`badges`, `bbb`, `h5p`, `horarios`) usaban `window.confirm()` nativo — no era
un hueco de "sin confirmación" sino de inconsistencia visual (heurística #4); se estandarizaron
al mismo componente `ConfirmationService` que el resto de la app.

**5 falsos positivos del grep de §3, confirmados por lectura de código (no se tocaron):**

| Archivo | Por qué no era un hueco real |
|---|---|
| `ayuda.component.ts` | Única mención es texto descriptivo ("...no pueden eliminarse"), sin acción |
| `conducta.component.ts` | `eliminarCompromiso()` solo filtra un array en memoria sin guardar (formulario aún no enviado), sin `.delete()` a backend |
| `disponibilidad.component.ts` | `removeSlot()` mismo patrón — filtra `nuevosSlots` en memoria antes de someter, sin llamada a backend |
| `eval-docente.component.ts` | Sin ninguna funcionalidad de eliminar en el archivo |
| `planeacion/crear-planeacion-semanal.component.ts` | Solo `Set.delete()` de una selección en memoria, sin llamada a backend |

**Hallazgo adicional (no era el objetivo de R-18, pero apareció en el triage):**
`features/alumnos/alumnos.service.ts` expone un método `eliminar(id)` que llama a
`/alumnos/{id}` — pero **ningún componente del frontend lo invoca** (`grep -rn "AlumnosService"
--include="*.ts" .` fuera del propio archivo del servicio: 0 resultados). Es código muerto, no
un hueco de heurística. No se tocó — si se decide limpiar, es un hallazgo aparte, no parte de
R-18.

**Verificación independiente (no solo el reporte del agente que hizo el cambio):**
- `grep -rl "ConfirmationService" --include="*.ts" .` → **25** (4 preexistentes + 21 nuevos,
  cuadra exacto).
- `docker run --rm -v /opt/ades/frontend:/app -w /app node:22-alpine npx tsc --noEmit -p
  tsconfig.app.json` → **exit code 0, cero errores** sobre el árbol completo tras las 21
  ediciones (este sandbox no tiene `node` en el host; se usó un contenedor `node:22-alpine`
  desechable montando el código para correr el compilador real, no una revisión visual).
- Balance de llaves/backticks verificado en el archivo con el editing slip reportado por el
  propio agente (`rubricas.component.ts`, autocorregido durante la ejecución) — 18 backticks
  (par) y los 2 bloques `confirm.confirm({ ... accept: () => {...} })` presentes y bien
  formados.

**Qué falta de Fase 1:** correr los E2E (o al menos QA manual) de los flujos tocados para
confirmar que el diálogo aparece y el flujo de aceptar/cancelar funciona en el navegador real —
`tsc` limpio confirma que el código compila, no que el comportamiento en UI es correcto. No se
hizo commit — queda pendiente de decisión del usuario.

### Fase 2 — ✅ COMPLETADO 2026-07-17 (misma sesión, R-19 + R-20)

**R-19 — Visibilidad de estado (#1), feedback de loading en mutaciones:**
la medición ingenua por conteo de `[loading]` en el archivo sobreestimaba
cobertura real (el signal puede existir sin estar wireado al botón que
dispara la mutación). Método real: script que mapea cada método con
`this.api.post/put/patch/delete(...)` contra los `(onClick)`/`(click)` del
template que lo invoca, y verifica `[loading]` en las 4 líneas previas —
plantilla completa en `.agent/skills/frontend-heuristicas-audit/SKILL.md`
§2.2/§6. Resultado: **24 componentes con hueco real, todos corregidos**
(rubricas, planeacion, badges, ponderacion-config, bbb, calendario,
condiciones-cronicas, optativas, capacitaciones, comunicados, admin,
admision, certificados, encuestas, foros, h5p, ia, learning-paths,
planes-estudio, reinscripcion, alumno-perfil, domicilio, expediente-doc,
gradebook). Re-corrida del script tras los fixes: 0 gaps reales (2 residuales
son falsos positivos ya catalogados — `shell.component.ts` idempotente y
`planes-estudio.component.ts` con patrón `[disabled]`+glifo equivalente a
`[loading]` que el script no reconoce). Hallazgos colaterales: método
`validar()` muerto en `capacitaciones.component.ts` (nunca invocado desde
template) y `bbb.component.ts::terminarReunion()` con `window.confirm()`
residual que se le pasó por alto a R-18 (su grep original no cubría el verbo
"terminar"). `tsc --noEmit` limpio sobre el árbol completo.

**R-20 — Prevención de errores (#5), validación estructural en datos
sensibles:** `AdesValidators` (`frontend/src/app/shared/validators/ades-validators.ts`)
ya tenía validadores reales de CURP/RFC/NSS/teléfono/CP, pero se usaba en
**1/79 componentes** (`personal-admin.component.ts`, el único con reactive
forms). Se extendió con variantes booleanas imperativas
(`curpValido`/`rfcValido`/`nssValido`/`telefonoValido`/`cpValido`, mismo
regex que las `ValidatorFn` — sin duplicar lógica) para los formularios
template-driven que predominan en el proyecto. **9 componentes corregidos**
(medico, expediente-laboral, profesores, profesor-perfil, alumno-perfil,
admision, admin, condiciones-cronicas, licencias) más rango de calificación
en `evaluaciones.component.ts::guardarCalificaciones()` (el `<input
type="number" [min] [max]>` nativo no bloquea el submit si se escribe un
valor fuera de rango directamente — solo limita las flechitas del spinner).
**Bug de persistencia real encontrado de paso:** `alumno-perfil.component.ts`
dejaba editar el CURP en el formulario (con contador "18/18" visible) pero
el payload de `guardar()` nunca lo incluía — el cambio se descartaba en
silencio y el usuario recibía "Guardado" de todos modos; confirmado con
`PersonaUpdateHelper.java` (backend) que el campo `curp` ya estaba soportado
en el PATCH, así que el gap era 100% frontend. `tsc --noEmit` limpio.

**Qué falta de Fase 2:** igual que Fase 1, QA manual/E2E en navegador real de
los flujos tocados — la compilación limpia confirma que el código es
correcto de tipos, no que el comportamiento visual sea el esperado.

### Fase 3 (esfuerzo bajo, ya se decidió hacer accesibilidad por separado — R-14)
`aria-*` (1.3%) y `breadcrumb` (2.5%) quedan bajo R-14 del plan de remediación existente — no
duplicar aquí, solo referenciar.

### Fase 4 — muestreo manual de lo no operacionalizable (§5)

---

## 5. Método de muestreo manual — heurísticas 2, 4, 6 (parcial), 7, 8

Grep no puede evaluar terminología, consistencia visual, orientación real del usuario o
densidad de dashboard. Propuesta concreta para no dejarlo como deuda indefinida:

1. **Muestra representativa, no censo completo:** 12 componentes (uno por módulo grande:
   alumnos, calificaciones, asistencia, salud, RH/expediente laboral, biblioteca, horarios,
   admisión, conducta, portal familias, dashboard admin, dashboard docente).
2. **Rúbrica fija de 5 puntos por heurística** (0 = ausente, 4 = ejemplar), aplicada por un
   agente con acceso de navegador real (Playwright, igual que `ades_testing/` ya usa) —
   navegando el flujo real, no leyendo el `.ts` — porque #6 y #8 son sobre percepción, no sobre
   código.
3. Reportar hallazgos concretos por componente (captura + qué heurística falla y por qué), no
   un puntaje agregado sin evidencia — mismo estándar que el resto del proyecto.
4. Esto es trabajo nuevo de exploración (no un fix), separado de las Fases 1-3 que sí son
   accionables hoy con grep.

---

## 6. Actualización a la tabla maestra (`docs/hallazgos/2026-07-15_plan_remediacion.md`)

Agregar como nueva fila:

| Ítem | Descripción | Estado | Esfuerzo | Riesgo si no se hace |
|---|---|---|---|---|
| R-18 | Confirmación antes de acciones destructivas (31/35 componentes sin ella, §3 de este doc) | ✅ **COMPLETADO 2026-07-16** — 21 huecos reales corregidos, 5 falsos positivos descartados por lectura de código, `tsc --noEmit` limpio (§4) | Bajo por archivo | Pendiente: QA manual en navegador antes de dar por cerrado el comportamiento (no solo la compilación) |
| R-19 | Feedback visible en mutaciones sin indicador de carga/guardado (§4 Fase 2) | ✅ **COMPLETADO 2026-07-17** — 24 componentes corregidos, script de verificación en `.agent/skills/frontend-heuristicas-audit/SKILL.md`, `tsc --noEmit` limpio | Medio | Pendiente: QA manual en navegador antes de dar por cerrado el comportamiento |
| R-20 | Validación real (no solo `required`) en formularios de datos sensibles | ✅ **COMPLETADO 2026-07-17** — `AdesValidators` extendido + 9 componentes corregidos + rango de calificaciones + 1 bug de persistencia real (CURP en `alumno-perfil.component.ts`) | Medio | Pendiente: QA manual en navegador antes de dar por cerrado el comportamiento |
| R-21 | Muestreo manual heurísticas 2/4/6/7/8 vía Playwright (§5) | 🟢 Pendiente | Alto | Deuda de usabilidad sin cuantificar más allá de este documento |
| R-22 | `bbb.component.ts::terminarReunion()` con `window.confirm()` residual (se le pasó por alto a R-18 — su grep no cubría el verbo "terminar") + limpiar método muerto `capacitaciones.component.ts::validar()` | 🟡 Pendiente | Bajo | Inconsistencia visual menor + código muerto, sin riesgo funcional |

Corrección a la nota existente de CLAUDE.md: `ChangeDetectionStrategy.OnPush` está en
**79/79 (100%)**, no "sin auditar" — actualizar el estado de Fase 2 en la sección
"OPTIMIZACIÓN AL 100%".

---

## 7. Qué NO cubre este documento

- No se tocó ningún archivo de código todavía — esto es plan + medición, no remediación.
- No se determinó cuáles de los 31 componentes de §3 son de bajo riesgo (catálogos) vs. alto
  riesgo (datos de negocio) — ese triage es el primer paso de Fase 1, no está hecho aquí.
- El muestreo manual de §5 no se ejecutó — requiere sesión de navegador real, no solo grep.
