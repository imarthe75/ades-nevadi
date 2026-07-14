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
- **Estado:** ⬜ Pendiente — requiere decisión de alcance/tiempo con Vic antes de arrancar.

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

### Fase 4 — Auditoría E2E de "UI muerta" (Playwright)

**Por qué:** el caso de la cuadrícula de "Calificar actividad" en gradebook (columna nunca marcada
como editable) no es detectable por análisis estático — el código compila, los tipos cuadran, pero la
funcionalidad simplemente no existe hasta que alguien intenta usarla. Solo se detecta *usando* la
pantalla.

- **Objetivo:** recorrer cada formulario de creación/edición del sistema, llenarlo, guardarlo, y
  verificar contra BD (o un GET posterior) que el dato realmente se persistió — no solo que apareció
  un toast de éxito en la UI.
- **Método:** el proyecto ya tiene infraestructura Playwright en `ades_testing/` (usada para el
  testing exploratorio de Fase 2, ver `CLAUDE.md`). Extender esos scripts para cubrir explícitamente
  los ~28 flujos corregidos hoy primero, y expandir al resto de formularios del sistema después.
- **Prioridad:** 🟡 media-alta — complementa las fases 1 y 3, pero requiere más tiempo de preparación
  (fixtures de datos, credenciales de prueba por rol).
- **Estado:** ⬜ Pendiente.

---

### Fase 5 — Auditoría de autorización (BOLA/BFLA) en los endpoints recién "revividos"

**Por qué:** varios de los ~28 flujos corregidos hoy literalmente nunca funcionaron antes del fix de
causa raíz — lo que significa que su lógica de `resolveUser`/`nivelAcceso`/scoping por `plantelId`
nunca fue ejercida de verdad con tráfico real. Un endpoint que "nunca se usó" es también un endpoint
cuyos controles de acceso nunca se probaron de verdad.

- **Objetivo:** revisar, solo en los endpoints tocados hoy, que cada uno cumple el checklist de
  seguridad ya establecido en `CLAUDE.md` (resolveUser obligatorio, verificación de nivelAcceso,
  scoping por plantelId, 404 en vez de 200 con 0 filas afectadas, etc.).
- **Método:** revisión dirigida (manual o agenteada) endpoint por endpoint, usando la lista de los 28
  flujos como checklist de alcance — no es necesario re-auditar todo el sistema, solo lo que empezó a
  recibir tráfico real hoy.
- **Prioridad:** 🟡 media-alta — riesgo de seguridad, pero acotado a un conjunto de endpoints ya
  identificado.
- **Estado:** ⬜ Pendiente.

---

### Fase 6 — Guardarraíl de CI ligero (mientras madura la Fase 2)

**Por qué:** la Fase 2 (tipos generados) es la solución definitiva pero toma tiempo. Mientras tanto,
conviene una red de seguridad barata que impida que se reintroduzca este bug en nuevos PRs.

- **Objetivo:** un script simple (Node o Java) que en cada PR compare `this.api.post/patch/put(...)`
  en Angular contra los campos del DTO Java correspondiente, y falle el build si detecta un mismatch
  evidente de nombres.
- **Método:** script standalone ejecutado en CI (no requiere el spec OpenAPI completo de la Fase 2,
  solo un parseo ligero de ambos lados). Puede reusar buena parte de la lógica que ya usaron los
  agentes de auditoría de esta sesión.
- **Prioridad:** 🟢 media — barato de implementar, alto valor mientras no exista la Fase 2.
- **Estado:** ⬜ Pendiente.

---

### Resumen de prioridad de ejecución

| Fase | Urgencia | Esfuerzo | Estado | Bloquea a |
|---|---|---|---|---|
| 0 — cierre `minimo_aprobatorio` | 🔴 Inmediata | Bajo | En curso (dev listo, falta prod) | — |
| 1 — contrato de respuesta (read-path) | 🔴 Máxima | Medio | ✅ Completada (1 bug corregido) | — |
| 3 — cruce BD↔backend↔UI | 🟠 Alta | Medio-alto | ✅ Completada (43 bugs corregidos) | — |
| 5 — BOLA/BFLA en endpoints revividos | 🟡 Media-alta | Medio | ⬜ Pendiente | — |
| 4 — E2E "UI muerta" (Playwright) | 🟡 Media-alta | Alto | ⬜ Pendiente | — |
| 6 — guardarraíl de CI | 🟢 Media | Bajo | ⬜ Pendiente | — |
| 2 — tipos generados (fix estructural) | 🟠 Alta (retorno) | Alto | ⬜ Pendiente | Reduce necesidad futura de 1, 3, 6 |

**Nota sobre por qué la evidencia de Antigravity estaba obsoleta:** los hallazgos 001 y 002 citan
fragmentos de código idénticos a los que existían en `tareas.component.ts` y `gradebook.component.ts`
**antes** de la sesión de corrección de causa raíz (fix de `HexagonalConfig.java` + auditoría de ~28
flujos del sistema, documentada en la conversación de esta misma fecha). Es probable que Antigravity
haya analizado una copia del repositorio anterior a esos cambios, o el historial de git en vez del
árbol de trabajo actual. Se recomienda, para futuras auditorías automatizadas, confirmar explícitamente
contra qué commit/estado se está analizando antes de reportar hallazgos como vigentes.
