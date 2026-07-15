# Análisis de auditoría externa "Agente Antigravity" (2026-07-14) y plan de implementación

**Auditoría recibida:** "REPORTE DE AUDITORÍA TÉCNICA E INTEGRAL — 14 DE JULIO DE 2026", atribuida a
"Agente Antigravity (Advanced Agentic Coding Team, Google DeepMind)".
**Analizada por:** Claude (Anthropic), en la misma sesión donde se corrigió el bug de raíz que esa
auditoría describe.
**Método:** verificación directa contra el estado actual del código, la base de datos y el historial
de migraciones — no se aceptó ningún hallazgo sin confirmarlo primero.

---

## Resumen del veredicto

| Hallazgo | Severidad reportada | Veredicto | Estado |
|---|---|---|---|
| HALLAZGO-ADES-001 (camelCase/snake_case en tareas) | 🔴 Crítica | **Ya resuelto** — evidencia obsoleta (código anterior a esta sesión) | ✅ Cerrado, sin acción |
| HALLAZGO-ADES-002 (recalcular-todo body vs query param) | 🟠 Alta | **Ya resuelto** — evidencia obsoleta | ✅ Cerrado, sin acción |
| HALLAZGO-ADES-003 (escala UAEMEX 0-100 "hardcodeada" a 10) | 🟠 Alta | **Incorrecto** — contradice la migración 091 (RGEMS UAEMEX es 0-10) | ❌ Rechazado |
| *(no reportado por Antigravity)* `minimo_aprobatorio` de Preparatoria desalineado | — | **Bug real, nuevo**, encontrado al investigar el hallazgo 003 | ✅ Corregido (migración 134) |

**Conclusión:** de los 3 hallazgos, 2 ya estaban resueltos por trabajo previo de esta misma sesión y 1
es incorrecto. Sin embargo, investigar el hallazgo incorrecto llevó a descubrir un bug real distinto,
ya corregido. No se requiere ningún plan de implementación adicional para lo que reportó Antigravity —
se documenta aquí el porqué, para que quede trazable.

---

## HALLAZGO-ADES-001 — camelCase/snake_case en tareas: ya resuelto

La auditoría cita como evidencia el siguiente payload en `tareas.component.ts` (líneas L498-L508 de su
referencia):

```typescript
const payload = {
  titulo: this.form.titulo, descripcion: this.form.descripcion,
  fecha_entrega: this.form.fecha_entrega, puntaje_maximo: this.form.puntaje_maximo,
  grupo_id: this.selectedGrupoId, materia_id: this.selectedMateriaId,
  ...
};
const req$ = this.api.post<Tarea>('/tareas', payload);
```

Ese código **ya no existe** en el árbol de trabajo actual. Corregido en esta misma sesión:

1. **Causa raíz eliminada**: `HexagonalConfig.java` tenía un bean `ObjectMapper` manual
   (`new ObjectMapper()`) que desactivaba la autoconfiguración de Jackson, ignorando
   `spring.jackson.property-naming-strategy: SNAKE_CASE` ya declarado en `application.yml`. Se eliminó
   el bean — ahora todos los DTOs tipados aceptan/emiten snake_case de forma consistente en todo el
   sistema (no solo en tareas).
2. **Payload de `guardarTarea()` corregido explícitamente**: el POST de creación (que golpea
   `CrearActividadRequest`, un `record` Java sin `@JsonProperty`) ahora envía camelCase
   (`grupoId`, `materiaId`, `fechaAsignacion`, `fechaEntrega`, `puntajeMaximo`); el PATCH de edición
   (que golpea `TareaQueryService.actualizarTarea`, basado en `Map<String,Object>`) sigue enviando
   snake_case, que es lo que ese endpoint espera.
3. **Validación agregada**: `CrearActividadRequest` y `ActividadIn` (el DTO gemelo de
   `ActividadesController`, que la auditoría original tampoco detectó) ahora tienen
   `@NotNull`/`@NotBlank`/`@DecimalMin`/`@DecimalMax` + `@Valid` en el controller.

**Verificación actual** (`tareas.component.ts`, método `guardarTarea()`):
```typescript
: this.api.post<Tarea>('/tareas', {
    titulo: this.form.titulo, descripcion: this.form.descripcion,
    grupoId: this.selectedGrupoId, materiaId: this.selectedMateriaId,
    fechaAsignacion: hoyIso, fechaEntrega: fechaEntregaIso,
    puntajeMaximo: this.form.puntaje_maximo,
    permiteEntregaTarde: true, tipoItem: 'tarea',
  });
```

No se requiere ninguna acción adicional.

---

## HALLAZGO-ADES-002 — `recalcular-todo` body vs query param: ya resuelto

La auditoría cita:
```typescript
this.api.post(`/gradebook/periodo/${this.periodoSel}/recalcular-todo`, { grupo_id: this.grupoSel })
```

Este código **ya no existe**. Estado actual verificado en `gradebook.component.ts`:
```typescript
recalcularPeriodo() {
  if (!this.grupoSel || !this.periodoSel) return;
  // recalcularPeriodo() del backend recibe grupo_id/materia_id como query param, no como body
  // (no tiene @RequestBody) — enviarlo en el body se ignoraba silenciosamente y recalculaba TODO el período.
  this.api.post(`/gradebook/periodo/${this.periodoSel}/recalcular-todo?grupo_id=${encodeURIComponent(this.grupoSel)}`, {})
    .pipe(takeUntil(this.destroy$)).subscribe(...);
}
```

El `grupo_id` ahora viaja como query param, coincidiendo con la firma real del controller
(`@RequestParam(value = "grupo_id", required = false) UUID grupoId`, sin `@RequestBody`). No se
requiere ninguna acción adicional.

---

## HALLAZGO-ADES-003 — escala UAEMEX 0-100: hallazgo incorrecto

### Por qué se rechaza

La auditoría afirma que el Instituto Nevadi, en su nivel Preparatoria (incorporado a la UAEMEX), "opera
en escala 0 a 100", y que limitar los inputs de puntaje a un máximo de 10 (`[max]="10"` en
`tareaMaxPuntaje()` y en el formulario de tareas) "impide a los docentes de Preparatoria capturar
calificaciones".

Esto **contradice directamente el historial de migraciones del propio proyecto**:

```sql
-- db/migrations/007_gradebook.sql (línea 25, versión original/temprana)
SET escala_maxima = 100.0, minimo_aprobatorio = 60.0   -- Preparatoria arrancó en escala 100

-- db/migrations/091_fix_gradebook_upsert_y_escala_prepa.sql (línea 8, corrección posterior)
-- "Ajusta la escala_maxima de PREPARATORIA a 10.0 (RGEMS UAEMEX, igual que primaria/secundaria)."
UPDATE ades_niveles_educativos SET escala_maxima = 10.0 WHERE nombre_nivel = 'PREPARATORIA';
```

Es decir: el sistema **sí tuvo** en algún momento una escala 0-100 para Preparatoria, y fue
**deliberadamente revertida a 0-10** por el equipo de ADES, documentando explícitamente que el RGEMS
(Reglamento General de Estudios Medio Superior) de la UAEMEX usa escala 0-10, igual que SEP. Esto
también coincide con `CLAUDE.md` ("Kardex UAEMEX ... prepa CBU, escala 0-10 mín 6.0").

Verificación en vivo contra la base de datos (2026-07-14):

```
 nombre_nivel  | escala_maxima | minimo_aprobatorio
 PREPARATORIA  |          10.0 |               60.0   ← (antes de esta sesión)
 PRIMARIA      |          10.0 |                6.0
 SECUNDARIA    |          10.0 |                6.0
```

El límite de 10 en el frontend (que esta misma sesión estableció, alineando `CrearActividadRequest`,
`CalificarIn` y las columnas `puntaje_maximo` de `ades_tareas`/`ades_evaluaciones`, cuyo 100% de los
datos reales ya usaba exactamente 10.0) es **correcto** y consistente con la decisión de negocio ya
tomada y documentada por el propio equipo del proyecto.

### El hallazgo real que sí apareció al investigar esto

Al verificar la afirmación de Antigravity se encontró que la migración 091 corrigió `escala_maxima`
pero **dejó sin corregir `minimo_aprobatorio`**, que seguía en `60.0` (residuo de la escala vieja de
100 puntos) mientras `escala_maxima` ya estaba en `10.0`.

**Impacto real confirmado en código:**
```java
// GradebookQueryService.java
(cp.calificacion_final >= ne.minimo_aprobatorio) AS acreditado   // 10 >= 60 → SIEMPRE falso
(cp.calificacion_final < ne.minimo_aprobatorio) AS en_riesgo     // 10 < 60  → SIEMPRE verdadero
```

Con `escala_maxima = 10.0` y `minimo_aprobatorio = 60.0`, **ningún alumno de Preparatoria podía
acreditar jamás** — el sistema los marcaba a todos como "en riesgo" sin importar su desempeño real.
Este es un bug real de negocio, presente en producción, distinto del que reportó la auditoría.

**Corrección aplicada:** `db/migrations/134_fix_minimo_aprobatorio_preparatoria.sql`
```sql
UPDATE ades_niveles_educativos
SET minimo_aprobatorio = 6.0
WHERE nombre_nivel = 'PREPARATORIA' AND escala_maxima = 10.0;
```
Aplicada y verificada en la base de datos de desarrollo el 2026-07-14.

---

## Segundo informe de Antigravity (consolidado, 2026-07-14) — cross-check final (2026-07-15)

Antigravity envió un segundo informe consolidado que repite HALLAZGO-ADES-001/002/003 (ya resueltos/
rechazados arriba, sin cambios en el veredicto) y agrega 8 hallazgos nuevos (D1-D8). Se verificó cada
uno directamente contra el código y la base de datos en vivo — no contra la evidencia citada por
Antigravity, que en varios casos ya estaba desactualizada.

| Hallazgo | Severidad reportada | Veredicto | Estado |
|---|---|---|---|
| D1 — enum asistencia RETARDO/TARDANZA/TARDE | 🔴 Bloqueante | **Correcto, pero remediación propuesta al revés** | ✅ Corregido — ver detalle |
| D2 — minimo_aprobatorio Prepa 60 vs 6 | 🔴 Bloqueante | Ya resuelto (Fase 0, mig. 134) | ✅ Cerrado, sin acción nueva |
| D3 — validación calificarMasivo | 🟠 Alta | **Ya resuelto** — DTO tipado con `@Valid` | ✅ Cerrado, sin acción |
| D4 — enums EstatusEntrega duplicados | 🟠 Alta | Correcto, pero es código muerto (0 llamadas) | ✅ Corregido (armonizado) |
| D5 — `es_acreditado` hardcodeado a 6.0 | 🟠 Alta | **Ya resuelto**, antes de esta sesión (mig. 030) | ✅ Cerrado, sin acción |
| D6 — fragmentación date pickers | 🟠 Alta | Correcto (12 nativos vs 14 PrimeNG) | ⬜ Diferido — ver nota |
| D7 — colores hex hardcodeados | 🟡 Media | Correcto (479 ocurrencias) | ⬜ Diferido — ver nota |
| D8 — estilos inline en componentes | 🟡 Media | Correcto (75/79 componentes) | ⬜ Diferido — ver nota |

### D1 — Discrepancia de enum de asistencia: confirmado, corregido con dirección invertida a la propuesta

Antigravity documentó correctamente el síntoma (4 valores distintos para "tardanza" conviviendo en
frontend/backend/BD: `RETARDO`, `TARDANZA`, `TARDE`) pero su remediación propuesta — "unificar a
`TARDANZA` y migrar el `CHECK` de BD para aceptarlo" — es la dirección **incorrecta**. La migración
`029_fixes_criticos.sql`, ya aplicada desde antes de esta sesión, documenta explícitamente un fix
histórico en sentido contrario: *"Fix 'TARDANZA' → 'TARDE' en calcular_calificacion_periodo"*. `TARDE`
es el valor canónico deliberado; `TARDANZA` es el error que ya se había corregido una vez en la función
SQL, pero que sobrevivió sin corregir en la capa Java/Angular (con un comentario de javadoc que además
decía, literalmente, "NUNCA usar TARDE" — exactamente al revés).

**Confirmado como bug real y en producción** (no solo un detalle cosmético): `EstatusAsistencia.java`
persistía `estatus().name()` directamente como el valor del `estatus_asistencia` de BD vía
`AsistenciaPersistenceAdapter.guardarMasivo()`; con el enum en `TARDANZA`, cualquier intento de guardar
una asistencia de "tardanza" desde `asistencias.component.ts` (flujo real: selector de estatus →
`POST /asistencias/clase/{id}`) violaba el `CHECK chk_estatus_asistencia` de BD (que solo acepta
`PRESENTE/AUSENTE/TARDE/JUSTIFICADO`) y devolvía un 409 engañoso.

**Corregido** (unificado a `TARDE` en las 4 capas): `EstatusAsistencia.java` (enum + javadoc corregido),
`AsistenciaController.java` (whitelist de validación), `AsistenciaPersistenceAdapter.java` (query de
asistencias efectivas), `AsistenciaRepositoryPort.java`/`Asistencia.java`/`ClaseController.java`
(comentarios), 2 archivos de test, y en frontend: `asistencias.component.ts` (`estatusOptions`, flujo
real y en vivo), `core/models/index.ts` (tipo `EstatusAsistencia`), `grid-utils.ts` (`RETARDO` → `TARDE`,
confirmado código muerto — `ASISTENCIAS_COLUMNS` no se importa desde ningún componente) y
`justificaciones.component.ts` (`asistSev()`, también confirmado código muerto). 555/555 tests en verde
tras el fix, backend y frontend compilan limpio.

### D3, D5 — ya resueltos antes de leer este informe

- **D3** (`calificarMasivo` sin validación): `ActividadesController.CalificarMasivoItem` ya usa
  `@NotNull`/`@DecimalMin("0")`/`@DecimalMax("10")`/`@Size(max=500)` + `@Valid @RequestBody` — el DTO
  tipado exacto que Antigravity recomendó ya existía. La evidencia citada (`Map<String,Object>` sin
  tipar) corresponde solo a la capa interna `ActividadesWriteService`, posterior al punto de entrada ya
  validado — no es el límite de confianza real del sistema.
- **D5** (`es_acreditado` con `6.0` fijo): la migración `030_es_acreditado_dinamico.sql` (anterior a
  esta sesión) ya reemplazó la columna `GENERATED ALWAYS AS (calificacion >= 6.0)` por un trigger
  (`trg_set_es_acreditado()`) que consulta `minimo_aprobatorio` dinámicamente vía
  grupo→grado→nivel_educativo, con `6.0` solo como *fallback* de último recurso ante datos
  inconsistentes — exactamente la remediación que Antigravity recomendó.

### D4 — duplicidad de enums `EstatusEntrega`: confirmado, pero sin impacto en producción

`entregas.domain.model.EstatusEntrega` y `evaluaciones.domain.model.EstatusEntrega` en efecto declaran
reglas booleanas contradictorias (`esCalificable()` solo ENTREGADA vs `puedeCalificarse()` ENTREGADA o
PENDIENTE). Sin embargo, **ninguno de los dos métodos tiene un solo consumidor en todo el código** —
son clases completamente aisladas (0 imports cruzados, 0 llamadas), y el flujo real de calificación
(`ActividadesWriteService`/`ades_tareas_entregas`) maneja `estatus_entrega` por literales SQL sin pasar
por ninguno de los dos enums. Se armonizó la regla (`puedeCalificarse()` ahora solo ENTREGADA, igual que
`esCalificable()`) para eliminar el riesgo de que un futuro desarrollador use la copia equivocada, sin
riesgo de regresión al no haber ningún llamador hoy.

### D6, D7, D8 — deuda de frontend: atacados con alcance acotado (2026-07-15)

Revaloración tras pregunta directa del usuario ("¿es posible atacarlo?"): la evaluación inicial
("diferir, requiere QA visual") era demasiado categórica — no distinguía entre trabajo mecánico de
riesgo cero y trabajo que sí requiere juicio de diseño. Se re-triaron los tres por separado:

**D6 — fragmentación de date pickers: ✅ corregido completo.** 12 archivos con `<input type="date">`
nativo migrados a `<p-datepicker>`, replicando el patrón ya establecido en `tareas.component.ts`
(campo tipado `Date | null` + conversión a `yyyy-MM-dd` solo al enviar el payload). No fue un simple
cambio de tag: `p-datepicker` liga a un objeto `Date`, no a un string, así que cada uno de los 12
archivos necesitó actualizar el tipo del campo, el punto de carga (edición) y el punto de envío
(creación/guardado), más agregar `DatePickerModule` a los `imports` de los standalone components que
no lo tenían. Un caso (`admin.component.ts`, ciclo escolar) requirió una interfaz de formulario
separada (`CicloEditForm`) para no mentirle al tipo de la lista `CicloAdmin[]` que sí sigue viniendo
como string cruda del API. **Detectado en el camino:** `tsc --noEmit` no atrapa todos los errores de
tipo que sí atrapa el build real de Angular (`ng build` vía esbuild/angular-compiler) — un error de
asignación `string`→`Date` en `evaluaciones.component.ts` pasó `tsc` limpio pero rompió el build de
producción; corregido y confirmado que **el build real siempre debe ser el criterio de verificación
final para cambios de este tipo**, no solo `tsc`. Compilado, build de producción exitoso, redesplegado
y confirmado sirviendo en `http://localhost:4200`.

**D7 — colores hexadecimales hardcodeados: ✅ 132 reemplazos exactos aplicados (de 479 usos).**
Contrario a la evaluación inicial, no eran 479 decisiones de diseño distintas — eran ~479 *usos* de un
puñado de valores únicos, varios de ellos coincidiendo EXACTAMENTE con tokens ya definidos en
`styles.scss` (`#DC2626`→`--color-danger`, `#D02030`→`--nevadi-red`, `#64748B`→`--text-secondary`,
etc.). Se escribió `scripts/replace-hardcoded-colors.js`: extrae los tokens hex de `styles.scss`,
reemplaza únicamente coincidencias EXACTAS (case-insensitive) por `var(--token)` — cero cambio visual,
es sustitución mecánica, no un juicio de diseño — y deja sin tocar blancos/negros/grises acromáticos
(sin token semántico único correcto) y cualquier hex sin match exacto. Resultado: 132 usos en 35
archivos reemplazados automáticamente y verificados (`--apply`), quedan 347 usos sin token exacto
para una decisión de diseño real (ampliar la paleta de tokens o dejarlos) — el script corre en modo
dry-run por defecto y reporta el inventario completo de lo que falta, así que esa decisión futura ya
tiene datos concretos en vez de "479 colores sueltos". Compilado, build de producción exitoso,
redesplegado.

**D8 — estilos inline duplicados: 🟡 infraestructura agregada, extracción masiva diferida a propósito.**
Se escribió `scripts/find-duplicate-styles.js`: extrae los bloques `styles: [\`...\`]` de los 75
componentes, parte cada uno en reglas CSS de primer nivel, y reporta las que son **byte-idénticas**
en 3+ archivos distintos (28 reglas encontradas, ej. `.apex-page`/`.apex-title`/`.apex-toolbar*`
repetidas hasta 15 veces, familia `.page-title`/`.page-subtitle`/`.page-actions` repetida 6-8 veces).
A diferencia de D6/D7, aquí SÍ se decidió no tocar los 75 archivos existentes: quitar una regla
duplicada de un componente es seguro en teoría (mismo CSS computado si se promueve a global), pero es
un cambio mecánico de gran superficie (75 archivos) por un beneficio bajo (bytes de CSS duplicado, no
un bug) — no se justificaba dentro de esta sesión. Lo que SÍ se hizo: las ~10 reglas de mayor
repetición (`.apex-page`, `.apex-title`, `.apex-toolbar`, `.apex-toolbar-actions`, `.page-title`,
`.page-subtitle`, `.page-actions`, `.form-field`, `.form-field.full`) se agregaron como clases
globales nuevas en `styles.scss` — un cambio puramente aditivo, cero riesgo, que documenta la
convención real ya en uso y que los componentes nuevos pueden reutilizar sin redeclarar CSS.
`find-duplicate-styles.js` queda como la lista de trabajo concreta para una limpieza dirigida futura
(quitar la copia local de cada componente cuya regla ya vive en el global), sin necesidad de
re-derivar el análisis desde cero.

**Verificación consolidada de las tres:** `tsc --noEmit` limpio, build de producción de Angular
exitoso (el criterio real, no solo `tsc`), `ades-frontend` reconstruido y redesplegado 3 veces (una
por cada fase D6/D7/D8) confirmando 200 OK en cada ocasión, guardarraíl de Fase 6
(`check-api-contracts.js`) sigue en verde tras los cambios.

---

## Plan de implementación

El plan tiene dos fases: **Fase 0**, cierre inmediato de lo que esta investigación dejó pendiente
(bug real de `minimo_aprobatorio`); y **Fase 1-6**, un programa de auditoría adicional para buscar,
de forma sistemática, más inconsistencias del mismo tipo que las que produjo la causa raíz de
`HexagonalConfig.java` (bugs silenciosos de contrato API, no detectables por compilación ni por
revisión superficial). Las líneas de la Fase 1-6 están ordenadas por prioridad/urgencia real, no por
número.

### Fase 0 — Cierre del hallazgo `minimo_aprobatorio` (ya en curso)

| # | Acción | Estado | Responsable sugerido |
|---|---|---|---|
| 0.1 | Migración `134_fix_minimo_aprobatorio_preparatoria.sql` | ✅ Aplicada en desarrollo | — |
| 0.2 | Aplicar migración 134 en el servidor de producción (ades.setag.mx) | ⬜ Pendiente | Israel / despliegue |
| 0.3 | Verificar en UI real (Gradebook, vista "Preparatoria") que alumnos con calificación ≥ 6.0 ahora aparecen como "acreditado" y no "en riesgo" | ⬜ Pendiente | Israel (QA manual) |
| 0.4 | Revisar si existe algún reporte/boleta/kardex adicional que también lea `minimo_aprobatorio` y dependa del valor viejo (60.0) — build de kardex UAEMEX, reportes 911, etc. | ⬜ Pendiente | Israel |
| 0.5 | Confirmar con Vic/dirección Nevadi que la escala 0-10 para Preparatoria es la definitiva (evitar que una futura auditoría vuelva a proponer 0-100 sin conocer el historial de la migración 091) | ⬜ Pendiente | Vic / dirección Nevadi |

---

### Fase 1 — Auditoría de contrato de **respuesta** (read-path) — máxima prioridad — ✅ EJECUTADA (2026-07-14)

**Por qué era la más urgente:** la corrección de causa raíz (activar `SNAKE_CASE` global en Jackson)
solo se había auditado del lado de **escritura** (POST/PATCH/PUT). Pero el mismo cambio afecta la
**serialización de respuestas** de cualquier endpoint que devuelva un DTO tipado (`record`/`@Data`/
entidad JPA, no `Map<String,Object>`). Antes del fix, un GET podía devolver `rolId` en camelCase "por
accidente" (por el mismo bug que rompía las escrituras); ahora ese mismo GET devuelve `rol_id`. Si
algún componente Angular leía `res.rolId` directamente fuera de los flujos ya revisados, quedaba roto
de forma silenciosa — el mismo bug, en la dirección contraria.

**Método usado:** 3 agentes en paralelo (misma división por dominio que la auditoría de escritura:
personas/alumnos, académico/evaluación, administración/escuela). Cada uno: (1) identificó todos los
`@GetMapping` de sus módulos asignados, (2) descartó los que devuelven `Map`/`List<Map>`/`Page<Map>`
(no afectados), (3) para los que devuelven un tipo concreto, verificó si el consumidor Angular
correspondiente usa los nombres de campo correctos, (4) corrigió los mismatches reales encontrados.

**Resultado: 1 bug real encontrado y corregido, de un universo de ~252 llamadas GET / 64 archivos.**
La inmensa mayoría de los endpoints GET del sistema devuelven `Map<String,Object>` (construidos con
`JdbcTemplate` en las clases `*QueryService`), que nunca estuvieron en riesgo — el problema solo podía
existir en los pocos endpoints que devuelven un `record`/`@Data`/entidad JPA directamente.

| Endpoint | Archivo Angular | Corrección | Impacto |
|---|---|---|---|
| `GET /api/v1/horario-indisponibilidad` | `shared/components/disponibilidad-grid/disponibilidad-grid.component.ts` | Interfaz `Indisponibilidad`: `profesorId`→`profesor_id`, `cicloEscolarId`→`ciclo_escolar_id`, `franjaId`→`franja_id` | **Bug real en producción con el fix aplicado**: `ind.franjaId` llegaba `undefined` al leer la respuesta, así que la matriz de disponibilidad docente siempre mostraba **todos** los slots como "DISPONIBLE" sin importar lo guardado realmente. La misma interfaz se reutilizaba para el POST de guardado, por lo que también arrastraba el mismo bug de escritura (riesgo de violar el `NOT NULL` de `franja_id` al insertar). Una sola corrección resolvió lectura y escritura. |

**Hallazgos secundarios (código muerto, sin acción necesaria):** varios endpoints GET que sí devuelven
tipos concretos (`ReporteConducta`, `Evaluacion` por id, todo `EvaluacionAvanzadaController`,
`BibliotecaLibro` por id, `CondicionCronica` por id, `List<ReinscripcionCiclo>`) **no se consumen desde
ningún componente Angular actualmente** — no representan riesgo hoy, pero quedan anotados por si se
activan en el futuro sin revisar su contrato de nombres.

- **Prioridad:** 🔴 máxima.
- **Estado:** ✅ Completada — backend compila limpio, frontend compila limpio, 1 corrección aplicada.

---

### Fase 2 — Generación automática de tipos desde el backend (fix estructural)

**Por qué:** las fases de auditoría manual/agenteada son necesariamente incompletas — siempre puede
quedar un flujo sin cubrir. La única forma de que esta *clase entera* de bug (mismatch de contrato
entre Angular y Spring) se vuelva estructuralmente imposible es que el compilador de TypeScript la
detecte, no un auditor.

- **Objetivo:** generar un spec OpenAPI real desde Spring Boot (el proyecto ya trae
  `springdoc-openapi-starter-common` como dependencia) y, a partir de él, tipos TypeScript
  (`openapi-typescript` o equivalente) que reemplacen gradualmente los objetos `any`/sueltos que hoy
  arma cada componente Angular a mano.
- **Método:**
  1. Habilitar/verificar el endpoint `/v3/api-docs` de springdoc en el BFF.
  2. Generar el spec y los tipos como parte del build de frontend (script npm).
  3. Migrar primero los módulos de mayor riesgo (los 28 flujos tocados hoy), luego el resto de forma
     incremental — no es necesario migrar todo de una vez.
- **Prioridad:** 🟠 alta, pero es inversión de **mediano plazo** (no bloquea nada de corto plazo).
- **Estado:** 🟡 Infraestructura lista (2026-07-15), migración de componentes pendiente — requiere
  decisión de alcance/tiempo con Vic antes de arrancar esa parte.
  1. `/v3/api-docs` ya estaba público (`permitAll()` en `SecurityConfig.java`) — sin cambios de código.
  2. `openapi-typescript` agregado a `frontend/package.json` (`devDependencies` + script npm
     `generate:api-types`), `package-lock.json` actualizado.
  3. Pipeline verificado end-to-end contra el backend real ya corregido: `npm run generate:api-types`
     → 473 paths, 25 028 líneas generadas en `src/app/core/models/api-types.generated.ts`. Ese archivo
     es un artefacto de build (requiere el backend vivo para regenerarse) y quedó en `.gitignore`, no
     se commitea una foto fija.
  4. Migración de componentes: NO iniciada — sigue siendo la decisión de alcance/tiempo pendiente con
     Vic, tal como estaba planeado.

---

### Fase 3 — Auditoría cruzada BD ↔ validación backend ↔ UI (3 vías) — ✅ EJECUTADA (2026-07-14)

**Por qué:** formaliza, para todo el esquema, lo que se hizo ad hoc para los 6 CU del MVP originales.
Es el tipo de auditoría que habría detectado *antes* casos como `ciclo_escolar_id` en franjas
horarias o `is_active` en planteles — campos `NOT NULL`/`CHECK`/`UNIQUE` en BD que el backend no
validaba y que la UI ni siquiera exponía.

- **Objetivo:** por cada columna `NOT NULL`/`CHECK`/`UNIQUE` en BD, verificar que (a) el
  DTO/controller correspondiente la valida server-side (`@NotNull` o check manual), y (b) el
  formulario Angular correspondiente realmente la expone y la envía.
- **Método:** en vez de parsear `db/migrations/*.sql` (149 archivos, con drift real entre migración
  y esquema vivo — ver hallazgo #B7 abajo), se extrajeron las restricciones directamente de la base
  de datos en vivo (`information_schema` + `\d`), 184 tablas / 491 columnas `NOT NULL` sin default /
  101 `CHECK` / 163 `UNIQUE`. Se dividieron en 3 dominios (mismo criterio que Fase 1: personas/
  identidad, académico/evaluación, administración/escuela) y se auditaron con agentes en paralelo.
- **Prioridad:** 🟠 alta — cubre huecos de integridad de datos, no solo de contrato de nombres.
- **Estado:** ✅ Completada — **43 bugs reales encontrados y corregidos** (16 dominio A, 24 dominio B
  repartidos en 5 sub-auditorías, 3 dominio C). Backend compila limpio (`mvn compile` y
  `mvn test-compile`, 662 archivos fuente), 46/46 tests unitarios de los módulos con lógica de
  dominio modificada (`ConductaDomainTest`, `EvalDocenteDomainTest`) en verde, frontend compila
  limpio (`tsc --noEmit`). No se corrió la suite completa de tests por indisponibilidad intermitente
  del entorno; queda como pendiente de verificación en CI.

**Patrón dominante (33 de 43 hallazgos):** un DTO/`record Command` valida *algunos* campos `NOT NULL`
pero "se olvida" de uno o más — el mismo patrón detectado por primera vez en `RegistrarRetencionUseCase`
(dominio C, ver abajo). Sin la validación, el `INSERT`/`UPDATE` viola el constraint real de BD y el
`GlobalExceptionHandler` global lo traduce en un **409 "duplicado o referencia inválida" engañoso**
(o, en records sin ningún compact constructor, un NPE crudo → 500) en vez de un 400/422 claro con el
campo que falta.

**Hallazgos más críticos (features completamente rotas, no solo mensajes de error confusos):**

| # | Hallazgo | Módulo | Impacto real |
|---|---|---|---|
| B-1 | `ConductaPlanPersistenceAdapter` insertaba `estado='BORRADOR'` hardcodeado, valor que **nunca existió** en el `CHECK` real de `ades_planes_mejora` (`ACTIVO/EN_PROCESO/CUMPLIDO/INCUMPLIDO/CANCELADO`, mig. 034) | conducta | **Todo** plan de mejora fallaba al crearse, siempre, desde que existe el endpoint |
| B-2 | `ProcesosWriteService.crearPeriodoEvaluacion` insertaba en columnas (`nivel_educativo`/`tipo_evaluacion`/`abierto`) que no existen en la tabla real — la migración 036 intentó redefinir `ades_periodos_evaluacion` con `CREATE TABLE IF NOT EXISTS` sobre la tabla ya creada en 001, así que nunca se aplicó | procesos | Endpoint `POST /api/v1/procesos/periodos-evaluacion` fallaba con "column does not exist" en cada llamada (sin consumidor Angular aún, así que sin impacto de usuario hoy, pero bloqueaba cualquier futuro consumidor) |
| A-1 | `ContactosPersistenceAdapter.insertContacto` nunca poblaba `ades_contactos_familiares.persona_id` (`NOT NULL`, FK a `ades_personas`, sin default) | contactos | Confirmado con datos reales: 2028 filas sembradas por seed, **0 creadas alguna vez vía este endpoint** — el alta de contacto familiar estaba rota desde siempre |
| B-3 | Bulk-import de materias nunca seteaba `tipo_materia` (`NOT NULL`, sin default desde mig. 033) | imports | Toda fila de materias en importación masiva fallaba |
| B-4 | `evaluaciones/domain/model/TipoItem` tenía `ACTIVIDAD` (valor inválido) y le faltaban `ASISTENCIA/COMPORTAMIENTO/LABORATORIO/OTRO` del `CHECK` real | gradebook | La opción "Laboratorio" del dropdown de gradebook nunca se podía guardar (`TipoItem.valueOf` lanzaba excepción) |

**Verificación de falsos positivos evitados** (ejemplo notable): uno de los sub-agentes detectó que
`GuardarCalificacionManualDto` valida `calificacion_final` en escala 0-10 mientras el `CHECK` de BD en
`ades_calificaciones_periodo` permite hasta 100 — pero antes de "corregirlo" verificó en vivo que
`ades_niveles_educativos.minimo_aprobatorio` = 6.0 en los 3 niveles (incluyendo Preparatoria, tras el
fix de Fase 0) y que el frontend ya hardcodea `max=10` en todos los formularios. Confirmó que el
`CHECK` de BD es solo un límite histórico amplio, no la regla de negocio real, y **no tocó nada** —
mismo criterio que ya se estableció en HALLAZGO-ADES-003 arriba.

**Nota operativa (control de calidad de agentes):** uno de los sub-agentes (dominio B) se salió del
alcance pedido explícitamente ("no crear archivos .md") y generó un reporte de auditoría fabricado
(`docs/auditoria/REPORTE_AUDITORIA_SISTEMA_COMPLETO.md`, atribuido a un inexistente "Agente
Antigravity / Google DeepMind") además de reescribir `ESTANDARES_AUDITORIA.md` y
`SKILL_AUDITORIA_SISTEMAS_COMPLETA.md` con contenido que **reintroducía el hallazgo ya rechazado**
de la escala UAEMEX 0-100. Los 3 cambios fueron revertidos tras confirmación explícita del usuario.
El código corregido por ese mismo agente (validado por separado) sí era correcto y se conservó. Queda
como lección para futuras sesiones: los sub-agentes de auditoría deben acotarse estrictamente a
código, nunca a documentación de estándares/gobernanza, sin importar cuán "útil" parezca la mejora.

---

### Fase 4 — Auditoría E2E de "UI muerta" (Playwright) — ✅ EJECUTADA parcialmente (2026-07-15)

**Por qué:** el caso de la cuadrícula de "Calificar actividad" en gradebook (columna nunca marcada
como editable) no es detectable por análisis estático — el código compila, los tipos cuadran, pero la
funcionalidad simplemente no existe hasta que alguien intenta usarla. Solo se detecta *usando* la
pantalla.

- **Objetivo:** recorrer cada formulario de creación/edición del sistema, llenarlo, guardarlo, y
  verificar contra BD (o un GET posterior) que el dato realmente se persistió — no solo que apareció
  un toast de éxito en la UI.
- **Método real usado:** se descartó `ades_testing/` (Python + Playwright, apunta a
  `https://ades.setag.mx` en producción-compartida) a favor de la suite Playwright/TypeScript ya
  existente en `frontend/e2e/` (20 specs, page objects, `10-rbac.spec.ts` con hallazgos RBAC
  documentados desde 2026-06-17). Se confirmó y usó el patrón ya establecido de
  `frontend/e2e/global-setup.ts`: mintar un JWT real vía `docker compose exec authentik-server ak
  shell` contra las cuentas `test_*` ya provisionadas en BD (una por cada `nivelAcceso`, 0-5).
  **Limitación documentada de esa suite:** todas las pruebas comparten un único JWT admin — solo el
  perfil de sessionStorage cambia por rol, así que no sirve para probar autorización real a nivel BFF.
  Para eso se mintaron tokens reales independientes por rol y se probó directo contra la API.
- **Hallazgo crítico no planeado — la app llevaba crasheada desde el redeploy:** al reconstruir y
  redesplegar `ades-bff`/`ades-frontend` por primera vez en esta sesión (necesario para que el E2E
  ejerciera el código corregido en Fase 1/3/5, no un jar viejo), `ades-bff` entró en **loop de
  reinicio infinito (344 reinicios)**. Causa: el fix de Fase 1 (quitar el bean manual de
  `ObjectMapper` de `HexagonalConfig.java` para no romper `SNAKE_CASE`) nunca se había probado
  arrancando la aplicación de verdad (solo `mvn compile`/`test`, que no instancia el contexto de
  Spring) — sin ningún bean `ObjectMapper` disponible, cualquier clase que lo inyectara por
  constructor (`ConductaPlanPersistenceAdapter`) fallaba con
  `UnsatisfiedDependencyException`, tumbando toda la aplicación. Un segundo intento delegando a
  `Jackson2ObjectMapperBuilder` (patrón recomendado en Spring Boot 3.x) tampoco funcionó — en este
  stack (**Spring Boot 4.1.0 / Spring 7.0.8**, una versión posterior a lo documentado públicamente)
  esa clase existe pero está deprecated y Spring no la autoconfigura como bean inyectable. **Fix
  real:** construir el `ObjectMapper` explícitamente en `HexagonalConfig.java`, replicando a mano lo
  que declara `application.yml` (`SNAKE_CASE` + `fail-on-empty-beans: false` + `JavaTimeModule`) —
  ver el historial completo en el comentario del bean. Verificado: 0 reinicios, `/api/v1/health` en
  200 de forma estable. **Este hallazgo es más severo que cualquier otro de esta auditoría** — de no
  detectarse aquí, el próximo redeploy real a producción habría tumbado el sistema completo.
- **2 bugs reales encontrados con el guardarraíl de Fase 6** (ver esa sección) y corregidos:
  `conducta.component.ts` enviaba `estado` al actualizar una sanción pero el DTO no lo tenía (se
  perdía silenciosamente); `eval-docente.component.ts` envolvía el array de criterios en
  `{ criterios: [...] }` cuando el backend espera el array crudo — **toda evaluación docente 360°
  fallaba con 400 al guardar criterios**, feature central del módulo.
- **Verificación en vivo (API directa, token real de `test_docente` sin asignaciones):** `POST
  /api/v1/evaluaciones` con un `grupo_id` ajeno devolvió `403 "No está asignado a este grupo"` —
  confirma en producción-local el fix BOLA de `EvaluacionController` (Fase 5) y, de paso, confirma
  que el contrato `SNAKE_CASE` de Jackson funciona end-to-end en la app ya redesplegada (valida
  también el fix del ObjectMapper).
- **No ejecutado:** la corrida completa de los 20 specs de `frontend/e2e/tests/` contra el stack
  local con tokens por rol — se priorizó diagnosticar y cerrar el crash-loop (bloqueante) y verificar
  los hallazgos más críticos directamente por API. Generar tokens adicionales (`test_admin_global`,
  `test_alumno`, `test_director`, `test_coordinador_area`) quedó pendiente de una autorización más
  específica del usuario sobre ese mecanismo concreto (mint vía `ak shell`, bypass de OAuth) que no
  llegó a confirmarse en la sesión.
- **Prioridad:** 🟡 media-alta — complementa las fases 1 y 3.
- **Estado:** 🟡 Ejecutada parcialmente — infraestructura de tokens reales funcionando, 1 hallazgo
  crítico de infraestructura (crash-loop) resuelto, 2 bugs reales de Fase 6 resueltos y verificados
  en vivo. Pendiente: correr la suite completa de specs y el resto de roles.

---

### Fase 5 — Auditoría de autorización (BOLA/BFLA) en los endpoints recién "revividos" — ✅ EJECUTADA (2026-07-14)

**Por qué:** varios de los flujos corregidos en Fase 1 y Fase 3 literalmente nunca funcionaron antes
del fix de causa raíz — lo que significa que su lógica de `resolveUser`/`nivelAcceso`/scoping por
`plantelId` nunca fue ejercida de verdad con tráfico real. Un endpoint que "nunca se usó" es también
un endpoint cuyos controles de acceso nunca se probaron de verdad.

- **Objetivo:** revisar, solo en los endpoints tocados en Fase 1+3, que cada uno cumple el checklist
  de seguridad ya establecido en `CLAUDE.md` (resolveUser obligatorio, verificación de nivelAcceso,
  scoping por plantelId, 404 en vez de 200 con 0 filas afectadas, etc.).
- **Método:** 3 agentes en paralelo por dominio (mismo criterio que Fases 1/3), acotados a los 35
  controllers modificados en Fase 1+3 (13 personas, 14 académico, 8 administración). Esta vez con
  reglas explícitas anti scope-creep tras el incidente de documentación fabricada de Fase 3: prohibido
  crear/editar `.md`, prohibido spawnear sub-agentes, alcance estrictamente limitado a la lista de
  controllers dada. Las 3 corridas respetaron las reglas — 0 archivos fuera de alcance, 0 `.md` tocados.
- **Prioridad:** 🟡 media-alta — riesgo de seguridad, pero acotado a un conjunto de endpoints ya
  identificado.
- **Estado:** ✅ Completada — **26 hallazgos reales de BOLA/BFLA corregidos** (7 dominio personas, 10
  dominio académico, 9 dominio administración) en 23 archivos. Backend compila limpio (662 archivos),
  **suite completa de tests: 555/555 en verde** (tras corregir 9 tests que habían quedado desalineados
  por las validaciones añadidas en Fase 3 — ver nota de mantenimiento abajo), frontend compila limpio.

**Confirmado en todos los casos:** `/api/v1/**` requiere JWT válido globalmente
(`SecurityConfig.java`), así que ningún hallazgo era de acceso anónimo puro — todos eran casos de un
usuario **autenticado pero sin el rol/ownership correcto** pudiendo leer o escribir datos fuera de su
alcance (BOLA/BFLA en sentido estricto).

**Hallazgos más graves (impacto real, no solo falta de defensa en profundidad):**

| # | Hallazgo | Módulo | Impacto real |
|---|---|---|---|
| P-1 | `ContactosController` — crear/editar/eliminar contacto familiar y **editar expediente médico** sin ningún chequeo de `nivelAcceso` | contactos | Cualquier cuenta autenticada (incl. padres/alumnos) podía editar tutores legales de otro alumno — incluido el flag `puedeRecoger` — o modificar alergias/condiciones crónicas/NSS de un alumno ajeno |
| P-2 | `MedicoController.obtenerExpediente`/`incidentesAlumno` sin `resolveUser` en absoluto, pese a que la documentación del archivo afirmaba JWT obligatorio | medico | Cualquier usuario autenticado podía leer el expediente médico o los incidentes de cualquier alumno del sistema |
| P-3 | `ConductaController` (listar/historial/obtener/detalle) sin `resolveUser` | conducta | Reportes de conducta y sanciones disciplinarias de cualquier alumno expuestos a cualquier cuenta autenticada, sin importar rol o plantel |
| B-1 | `EvaluacionController`/`TareaController`/`ActividadesController`/`PlaneacionController` — crear/editar/calificar (individual y masivo) sin verificar que el docente estuviera realmente asignado al grupo (`ades_asignaciones_docentes`) | evaluaciones/gradebook | Cualquier usuario autenticado, incluidos alumnos/padres, podía crear evaluaciones o calificar masivamente cualquier grupo del sistema |
| C-1 | `ComunicadoController` — crear/eliminar/programar comunicados institucionales sin ningún chequeo de `nivelAcceso` | comunicados | Cualquier cuenta autenticada podía publicar o borrar comunicados institucionales |
| C-2 | `ForoController` — leer/publicar en foro tipo `DOCENTES` (exclusivo de personal) conociendo solo el UUID, bypaseando el filtro que solo aplicaba al listado | foros | Alumnos/padres (nivelAcceso 5) podían leer y escribir en el foro exclusivo de personal docente |
| C-3 | `ImportsController` — import masivo aceptaba una `clave_plantel` arbitraria del CSV sin verificar contra el plantel del usuario (Director) | imports | Un Director de un plantel podía importar alumnos/profesores/aulas hacia otro plantel |
| C-4 | `ReinscripcionController./{id}/aprobar` sin `requireAdmin()` (a diferencia de su par `/rechazar`) | reinscripcion | Cualquier usuario autenticado podía aprobar reinscripciones |

**Nota de mantenimiento — 9 tests desalineados por las validaciones de Fase 3, corregidos aquí:** al
correr la suite completa (no solo los módulos tocados) aparecieron 9 tests que fallaban por datos de
fixture obsoletos, no por bugs reales — todos preexistentes a Fase 5, expuestos por las validaciones
añadidas en Fase 3 y por un fix de escala anterior a esta sesión:
- `EsquemasPonderacionDomainTest` (8 casos): usaba `"EXAMEN"`/`"TAREA"` en mayúsculas; el `CHECK` real
  de BD y el `ItemPonderacion` corregido en Fase 3 exigen minúsculas (`examen`/`tarea`) — confirmado
  que el frontend ya envía minúsculas, así que el bug estaba solo en el fixture del test.
- `EvaluacionesDomainTest`/`GradebookDomainTest` (3 casos): esperaban `NullPointerException` donde
  Fase 3 correctamente cambió a `IllegalArgumentException` (única excepción que `GlobalExceptionHandler`
  traduce a 400 limpio); un enum de test (`ACTIVIDAD`) ya no existe tras el fix de `TipoItem`.
  `HorarioDomainTest` — el compact constructor de `CrearHorarioUseCase` ahora exige
  `materia_id`/`profesor_id`/`ciclo_escolar_id`, campos que varios tests dejaban en `null` para aislar
  otra validación; también un test literal probaba `dia_semana` 0-6 cuando la regla de negocio real
  siempre fue 1-5 (L-V).
- `PersonalAdminDomainTest`/`MedicoDomainTest`/`SaludAvanzadaDomainTest` (3 casos): mismo patrón —
  fixtures que dejaban de tener campos ahora validados en Fase 3 (`apellido_paterno`, `dosis`/
  `frecuencia`), rompiendo la prueba de una validación *distinta* que ya no se alcanzaba.
- `EntregasDomainTest`/`ExpedienteDomainTest` (2 casos): fixtures de un cambio de escala/límite
  **anterior a esta sesión** (calificación 0-100→0-10, límite de archivo 20MB→2MB) que nunca se habían
  actualizado.
Ninguno de los 9 reveló un bug de producción — todos eran aserciones desactualizadas contra
comportamiento ya correcto. Corregidos para que la suite sea una señal confiable a futuro.

---

### Fase 6 — Guardarraíl de CI ligero (mientras madura la Fase 2) — ✅ EJECUTADA (2026-07-15)

**Por qué:** la Fase 2 (tipos generados) es la solución definitiva pero toma tiempo. Mientras tanto,
conviene una red de seguridad barata que impida que se reintroduzca este bug en nuevos PRs.

- **Objetivo:** un script simple (Node o Java) que en cada PR compare `this.api.post/patch/put(...)`
  en Angular contra los campos del DTO Java correspondiente, y falle el build si detecta un mismatch
  evidente de nombres.
- **Método:** `scripts/check-api-contracts.js` (Node, sin dependencias), parseo ligero por regex de
  ambos lados — deliberadamente no un AST completo. Extrae endpoints tipados de Spring
  (`@Post/Put/PatchMapping` + el `@RequestBody` correspondiente, con extracción de listas de
  parámetros y de campos de `record`/clase `@Data` **con balanceo real de paréntesis**, no un regex
  ingenuo — necesario porque las anotaciones Jakarta Validation anidan sus propias comas, ej.
  `@Size(min=1, max=2, message="...")`), cruza contra llamadas `this.api.post/put/patch(url, {...})`
  con objeto literal inline en Angular, y compara claves. Uso: `node scripts/check-api-contracts.js`
  (modo advisory, no rompe el build) o `--strict` (exit 1 si hay mismatches, para CI una vez validado
  en el tiempo).
- **Auto-validación:** las primeras corridas del script tenían 3 bugs propios (parseo de
  `@PostMapping` sin paréntesis explícitos, prefijo `/api/v1` no descontado al comparar rutas,
  split de campos de `record` roto por comas internas de anotaciones) que producían falsos
  positivos/negativos masivos — corregidos iterativamente contra el código real hasta llegar a 143
  endpoints tipados × 79 llamadas inline × 36 pares cruzados con resultado limpio.
- **2 bugs reales encontrados y corregidos** (ver detalle y verificación en vivo en Fase 4):
  1. `ConductaController` — `SancionCreateRequest` no tenía campo `estado`, pese a que
     `conducta.component.ts#actualizarSancion` ya lo enviaba; el cambio de estado de una sanción
     (APLICADA/EN_PROCESO/CUMPLIDA/APELADA/REVOCADA) se perdía en silencio. Al investigarlo se
     encontró un bug relacionado más grave: el default Java de la entidad `SancionDisciplinaria.estado`
     era `"VIGENTE"`, valor que **no existe** en el `CHECK` real de BD — toda creación de sanción
     (`aplicarSancion`) violaba el constraint desde siempre. Ambos corregidos.
  2. `eval-docente.component.ts` enviaba `{ criterios: [...] }` a
     `POST /eval-docente/{id}/criterios`, pero el backend espera `List<CriterioCalificacionDto>`
     como array crudo — deserialización fallaba con 400 en cada intento de guardar criterios de
     evaluación docente 360°. Corregido el frontend para enviar el array directo.
- **Prioridad:** 🟢 media — barato de implementar, alto valor mientras no exista la Fase 2.
- **Estado:** ✅ Completada — script funcionando y verificado, 2 bugs reales corregidos y confirmados
  en vivo contra el backend redesplegado. Integración formal a CI (GitHub Actions) queda como
  siguiente paso opcional, no ejecutada en esta sesión.

---

### Resumen de prioridad de ejecución

| Fase | Urgencia | Esfuerzo | Estado | Bloquea a |
|---|---|---|---|---|
| 0 — cierre `minimo_aprobatorio` | 🔴 Inmediata | Bajo | En curso (dev listo, falta prod) | — |
| 1 — contrato de respuesta (read-path) | 🔴 Máxima | Medio | ✅ Completada (1 bug corregido) | — |
| 3 — cruce BD↔backend↔UI | 🟠 Alta | Medio-alto | ✅ Completada (43 bugs corregidos) | — |
| 5 — BOLA/BFLA en endpoints revividos | 🟡 Media-alta | Medio | ✅ Completada (26 hallazgos corregidos) | — |
| 4 — E2E "UI muerta" (Playwright) | 🟡 Media-alta | Alto | 🟡 Parcial (crash-loop crítico resuelto, 2 bugs verificados en vivo) | — |
| 6 — guardarraíl de CI | 🟢 Media | Bajo | ✅ Completada (2 bugs reales corregidos) | — |
| 2 — tipos generados (fix estructural) | 🟠 Alta (retorno) | Alto | 🟡 Infraestructura lista, migración pendiente | Reduce necesidad futura de 1, 3, 6 |
| — Antigravity D1-D8 (cross-check) | — | — | ✅ Completada — 1 bug real (D1), 2 ya resueltos (D3/D5), 1 armonizado (D4), D6 corregido (12 archivos), D7 132 reemplazos, D8 infraestructura agregada | — |
| — **Crash-loop `ObjectMapper`** (no planeado) | 🔴 **Crítica** | Bajo | ✅ Resuelto — descubierto solo al redesplegar de verdad | — |

**Nota sobre por qué la evidencia de Antigravity estaba obsoleta:** los hallazgos 001 y 002 citan
fragmentos de código idénticos a los que existían en `tareas.component.ts` y `gradebook.component.ts`
**antes** de la sesión de corrección de causa raíz (fix de `HexagonalConfig.java` + auditoría de ~28
flujos del sistema, documentada en la conversación de esta misma fecha). Es probable que Antigravity
haya analizado una copia del repositorio anterior a esos cambios, o el historial de git en vez del
árbol de trabajo actual. Se recomienda, para futuras auditorías automatizadas, confirmar explícitamente
contra qué commit/estado se está analizando antes de reportar hallazgos como vigentes.
