---
name: frontend-heuristicas-audit
description: Metodología reproducible para auditar las 10 heurísticas cognitivas de Nielsen (CLAUDE.md §HEURÍSTICAS COGNITIVAS) sobre los componentes Angular de ADES — qué medir con grep/script objetivo, qué requiere muestreo manual, catálogo de falsos positivos ya descartados, y el patrón de corrección para cada hallazgo real.
---

# Skill: Auditoría de Heurísticas Cognitivas (Frontend ADES)

Consolida el método usado en 3 rondas reales de auditoría (R-18 confirmación de
acciones destructivas, R-19 feedback visual en mutaciones, R-20 validación
estructural en datos sensibles — ver `docs/hallazgos/2026-07-16_plan_revision_heuristicas_cognitivas.md`
y la bitácora de sesión 2026-07-17 en `.agent/STATE.md`). El objetivo de este
skill es que la siguiente ronda de auditoría no repita desde cero el trabajo de
diseño de método, y no vuelva a "descubrir" los mismos falsos positivos.

## 0. Principio rector

**Grep de presencia ("¿existe `[loading]` en el archivo?") sobrestima cobertura.**
Un signal (`isLoading`, `saving`, `ConfirmationService`) puede existir en la
clase sin estar realmente wireado al botón que dispara la mutación — quedó de
un refactor anterior, o se usa para otro botón del mismo archivo. La señal
confiable es **mapear cada método que ejecuta la mutación contra el
`(onClick)`/`(click)` del template que lo invoca**, y verificar el atributo
justo ahí. Este skill documenta cómo hacer ese mapeo con un script en vez de
a ojo.

---

## 1. Cuándo aplicar este skill

- El usuario pide "auditar heurísticas", "revisar UX", "feedback visual en
  mutaciones", "validación de formularios", o continúa explícitamente un ítem
  `R-NN` del plan de remediación (`docs/hallazgos/2026-07-16_plan_revision_heuristicas_cognitivas.md`).
- Antes de dar por "cerrado" cualquier módulo nuevo que agregue botones de
  guardar/eliminar/aprobar — pasar el checklist de §5 antes de considerar el
  módulo terminado.

---

## 2. Las 3 auditorías ya operacionalizadas (con script, no solo criterio)

### 2.1 — Confirmación en acciones destructivas (heurística #3, "R-18")

**Señal objetiva:** todo método que llama `this.api.delete(...)` (o un
`POST`/`PATCH` que semánticamente da de baja/cancela/revoca) debe estar
envuelto en `this.confirm.confirm({ ..., accept: () => { ...llamada real... } })`
(`ConfirmationService` de PrimeNG + `<p-confirmDialog />` en el template) —
**no** `window.confirm()` nativo (inconsistente visualmente con el resto de
la app, aunque funcionalmente "confirma").

```bash
grep -rlE "this\.api\.delete\(" --include="*.component.ts" . \
  | xargs -I{} sh -c 'grep -q "ConfirmationService" "{}" || echo "SIN ConfirmationService: {}"'
```

Luego leer cada archivo marcado — el grep solo dice "el servicio no está
importado en el archivo", no que el delete específico carezca de diálogo.

**Falsos positivos reales ya descartados** (no volver a marcarlos sin releer
el código — la lógica puede haber cambiado):
- `ayuda.component.ts` — mención de texto, no hay acción.
- `conducta.component.ts::eliminarCompromiso()` — filtra un array en memoria
  (formulario aún no enviado), sin `.delete()` a backend.
- `disponibilidad.component.ts::removeSlot()` — mismo patrón, filtra
  `nuevosSlots` en memoria antes de someter.
- `eval-docente.component.ts` — sin funcionalidad de eliminar en el archivo.
- `planeacion/crear-planeacion-semanal.component.ts` — solo `Set.delete()`
  de una selección en memoria.
- `capacitaciones.component.ts::validar()` — existe en la clase pero
  **no se invoca desde ningún template** (código muerto, hallazgo 2026-07-17
  durante R-19 — no es un hueco de confirmación, es limpieza pendiente).

**Hallazgo residual sin cerrar (2026-07-17):** `bbb.component.ts::terminarReunion()`
sigue usando `window.confirm()` nativo — se le pasó por alto a la ronda R-18
porque su grep original no cubría el patrón "terminar" (solo "eliminar"/"delete").
Antes de dar R-18 por 100% cerrado, ampliar el grep a cualquier verbo de baja:
`eliminar|borrar|cancelar|revocar|terminar|desactivar|archivar|dar.*baja`.

### 2.2 — Feedback visual en mutaciones (heurística #1, "R-19")

**Señal objetiva real** (script, no grep de conteo):

```python
import re

# Para cada *.component.ts:
# 1. Recorrer línea a línea; llevar "método actual" con una regex de firma de método.
# 2. Si una línea dentro de ese método hace this.(api|apiService|http).(post|put|patch|delete)(,
#    agregar el nombre del método a mut_methods.
# 3. Recorrer las líneas del template buscando (onClick)="metodo(" o (click)="metodo(".
# 4. Si metodo ∈ mut_methods, mirar las 4 líneas previas + la propia por "[loading]".
#    Si no aparece → hueco real.
```//: mantener este script en /tmp o el scratchpad de turno; no está
checkeado en el repo como archivo aparte — es lo bastante corto para
regenerarlo cada vez (ver plantilla completa al final de este documento, §6).

**Patrón de corrección** (el mismo en los ~24 archivos corregidos 2026-07-17):
```typescript
// en la clase:
guardando = signal(false);              // o eliminandoXId = signal<string | null>(null) si es por fila

// en el método:
accion(): void {
  this.guardando.set(true);
  this.api.post(...).pipe(takeUntil(this.destroy$)).subscribe({
    next:  () => { this.guardando.set(false); /* resto de la lógica */ },
    error: (e) => { this.guardando.set(false); this.notify.error('Error', e?.error?.detail ?? '...'); },
  });
}
```
```html
<p-button [loading]="guardando()" (onClick)="accion()" />
```

Para acciones por fila (eliminar una fila específica de una lista), usar un
signal `eliminandoXId = signal<string | null>(null)` con `.set(row.id)` /
`.set(null)`, y en el template `[loading]="eliminandoXId() === row.id"` — así
solo el botón de esa fila muestra el spinner, no toda la lista.

Para `<button pButton>` (directiva, no componente `<p-button>`), `[loading]`
también funciona — es un `@Input` de la directiva `pButton`, confirmado en
`mi-progreso.component.ts`. Para botones HTML planos sin PrimeNG (ej. celdas
de grid con glifos, `planes-estudio.component.ts`), usar `[disabled]` +
interpolación de glifo (`{{ busy() ? '⏳' : '×' }}`) como equivalente.

**Falsos positivos reales ya descartados:**
- `shell.component.ts::marcarLeida()/leerTodas()` — marcar notificación como
  leída es idempotente (reintentar no daña nada) y son `<button>`/`<div>`
  de UI de campana, no un botón de mutación de datos de negocio. No requiere
  `[loading]`.
- Grids que usan `app-interactive-grid` con `(rowDeleted)` — el componente
  reusable solo expone un `[loading]` global de tabla (`@Input() loading`),
  no por-fila. El patrón correcto ahí es reusar el signal `cargando` que ya
  controla el fetch y setearlo `true` también durante el delete (no crear un
  signal nuevo) — ver `ponderacion-config.component.ts::desactivarEsquema()`
  o `condiciones-cronicas.component.ts::eliminar()` como referencia.

### 2.3 — Validación estructural en datos sensibles (heurística #5, "R-20")

**Contexto clave:** `frontend/src/app/shared/validators/ades-validators.ts`
(`AdesValidators`) ya tiene validadores reales para CURP/RFC/NSS/teléfono/CP,
pero **antes de 2026-07-17 solo se usaba en 1 de 79 componentes**
(`personal-admin.component.ts`, el único con reactive forms + `Validators.*`).
El resto del proyecto usa formularios template-driven (`[(ngModel)]`), donde
un `ValidatorFn` de `AbstractControl` no aplica directamente — por eso
`AdesValidators` ahora también expone variantes booleanas imperativas
(`curpValido`, `rfcValido`, `nssValido`, `telefonoValido`, `cpValido`) para
llamarlas dentro del método `guardar()`, con el mismo patrón ya establecido en
el codebase de "check + `notify.warning(...)` + `return`" (ver
`gradebook.component.ts` — chequeo de longitud de justificación como
precedente pre-existente).

**No confundir con `AdesFormatDirective` (`adesFormat="curp|rfc|..."`):** esa
directiva solo sanea el juego de caracteres *mientras se escribe* (máscara de
input) — no valida la forma estructural completa al guardar. Un CURP a medio
escribir, o sin la letra de género H/M en la posición correcta, pasa la
máscara pero es inválido. Las dos capas son complementarias:
`adesFormat` en el input + `AdesValidators.xValido()` en el `guardar()`.

**Señal objetiva para encontrar candidatos:**
```bash
grep -rniE '\[\(ngModel\)\]="[a-zA-Z0-9_.]*\.(curp|rfc|nss)"' --include="*.component.ts" .
grep -rlE "AdesValidators" --include="*.component.ts" .   # cobertura actual
```
Cruzar ambos: cualquier archivo en el primer grep que no aparezca en el
segundo es un hueco real (salvo que el campo sea `readonly`, como el CURP en
`profesor-perfil.component.ts` — no editable tras alta, no necesita
validación de guardado).

**Patrón de corrección:**
```typescript
import { AdesValidators } from '.../shared/validators/ades-validators';
// ...
guardar(): void {
  if (this.form.curp && !AdesValidators.curpValido(this.form.curp)) {
    this.notify.warning('CURP inválido', 'Formato esperado: AAAA000000HAAAAA00');
    return;
  }
  // ...
}
```

**Otros campos sensibles a revisar además de CURP/RFC/NSS** (mismo criterio,
sin catálogo grep dedicado aún — inspección manual por dominio):
- Rango de calificaciones (`0..puntaje_maximo`) — la mayoría ya usa
  `p-inputNumber [min] [max]`, pero eso **no bloquea el submit** si el campo
  es un `<input type="number">` nativo (el usuario puede escribir un valor
  fuera de rango directamente, min/max solo limitan las flechitas del
  spinner). Verificar el método `guardar()` explícitamente, no solo el
  template. Hallazgo real: `evaluaciones.component.ts::guardarCalificaciones()`
  no validaba rango pese a tener `[min]`/`[max]` en el `<input type="number">`.
- Coherencia de rango de fechas (`fecha_fin >= fecha_inicio`) en licencias,
  contratos, incapacidades — buscar pares `fecha_inicio`/`fecha_fin` en
  formularios y confirmar que `guardar()` los compara.
- **Bug de persistencia silenciosa** (adyacente a validación, mismo barrido):
  al revisar un campo para agregar validación, confirmar que el campo
  realmente se envía en el payload de `guardar()`. Hallazgo real 2026-07-17:
  `alumno-perfil.component.ts` dejaba editar el CURP en el formulario
  (con contador "18/18" incluido) pero el payload de `guardar()` nunca lo
  incluía — el cambio se descartaba en silencio y el usuario recibía
  "Guardado" igual. Validar un campo que no se persiste es trabajo
  desperdiciado; siempre confirmar el payload primero.

### 2.4 — Muestreo real con navegador (R-21, "las que no se pueden gregar")

Ejecutado por primera vez 2026-07-17 — método reproducible:

1. **Reusar la infraestructura E2E existente, no inventar una nueva.**
   `frontend/e2e/global-setup.ts` ya sabe generar un JWT real de Authentik
   (`docker compose exec authentik-server ak shell`) y cachearlo en
   `e2e/.auth/token.txt` + `user.json`. Un script standalone puede inyectar
   ese mismo token en `sessionStorage` (`ades_token`/`ades_usuario`) sin
   pasar por el flujo OIDC completo — mismo patrón que
   `e2e/page-objects/login-page.ts::login()`.
2. **Contenedor Playwright pineado a la versión exacta del proyecto**
   (`mcr.microsoft.com/playwright:v1.X.Y-noble`, leer la versión de
   `@playwright/test` en `package.json` — no asumir `latest`), con
   `--network host` para llegar a `localhost:4200` (el mismo build que sirve
   `ades.setag.mx`, sin necesidad de tocar producción externa).
3. **`waitUntil: 'networkidle'` falla en páginas con polling/SSE activo**
   (notificaciones, iframes de BBB/Grafana, etc.) — usar `'domcontentloaded'`
   + `waitForTimeout` fijo (~3s) en su lugar para las páginas que dan timeout.
4. **Screenshots + extracción de texto (headings, botones, `aria-label`,
   `placeholder`, conteo de `kbd`), no solo captura visual** — permite
   analizar #2/#7 por texto además de #4/#6/#8 por percepción visual.
5. **⚠️ Las capturas de pantalla de datos reales contienen PII** (nombres,
   CURPs, salarios — este es un sistema con datos reales cargados, no hay
   entorno de prueba separado). Revisar y **eliminar las capturas
   inmediatamente después del análisis** — no dejarlas en disco, no
   publicarlas como artifact. Extraer del análisis solo lo necesario para el
   reporte (nunca copiar el PII visto al reporte final).

**Lección de método (corrige §1 de este documento):** el grep de
"breadcrumb" contaba **2/79 (2.5%)** porque busca la palabra en cada
`*.component.ts` — pero el breadcrumb de ADES vive en el **shell compartido**
(`layout/shell.component.ts`, construido dinámicamente desde el árbol de
rutas), no en cada componente de feature. Evidencia real de navegador:
**12/12 páginas muestreadas tienen breadcrumb funcional.** Regla general:
antes de reportar una heurística como "sin cobertura" por ausencia de grep,
confirmar si la funcionalidad podría vivir en un componente de shell/layout
compartido en vez de por-feature — un grep por-archivo no lo verá nunca.

**Hallazgo de arquitectura encontrado en el camino (no es heurística, es un
bug de implementación):** `ColumnConfig` (`shared/components/interactive-grid/interactive-grid.component.ts`)
declara los campos `type: 'date' | 'boolean' | 'select' | ...` y `template`
en su interfaz, pero el `<td>` del grid (antes de la corrección R-24)
**nunca los leía** — renderizaba `{{ rowData[col.field] }}` a secas sin
importar qué tipo declarara la columna. Resultado: cualquier componente que
confiara en `type: 'date'` (2 casos reales: `alumnos`, `usuarios-list`) o en
`template` (`portal-admin.component.ts`, columna "Acciones" completa con
botones custom) mostraba datos crudos/rotos silenciosamente — sin error de
compilación, sin excepción en runtime, solo mal renderizado. Lección: cuando
una interfaz compartida declara un campo opcional, **verificar que el
componente que la consume realmente lo lea**, no asumir que "está en el
tipo" significa "está implementado". **Actualización 2026-07-17 (R-26):**
`type: 'date'` y `template` ya están implementados (`type` con el pipe
`date` de Angular; `template` vía `[innerHTML]`, que pasa por el
`DomSanitizer` — script/atributos `on*`/enlaces peligrosos se eliminan
automáticamente, seguro por defecto siempre que el `template` no interpole
texto libre de usuario sin escapar). El fix de `template` reveló un segundo
bug independiente en el único consumidor real (`portal-admin.component.ts`):
`(rowSelect)="seleccionar($event)"` — el `@Output()` real se llama
`rowSelected` (typo aislado, 27/28 consumidores correctos), que dejaba el
módulo de convocatorias sin reaccionar a ningún clic. Ambos corregidos y
verificados con `ng build --configuration production` real (ver nota de
§5 sobre por qué `tsc --noEmit` no basta para bindings de plantilla).

---

## 3. Heurísticas sin operacionalizar por grep (requieren muestreo manual)

De la tabla completa en `docs/hallazgos/2026-07-16_plan_revision_heuristicas_cognitivas.md`
§1 — no las reproduzcas aquí sin releer ese documento primero, pero en resumen:
heurísticas **2** (terminología SEP/UAEMEX consistente), **4** (consistencia
visual: spacing/orden de botones/paleta), **6** (reconocimiento sin
memorizar rutas), **7** (atajos de teclado — sin convención hoy) y **8**
(densidad de dashboards) no tienen señal grep confiable. Esas quedan bajo
`R-21` del plan — sesión con Playwright real, no solo lectura de código.

---

## 4. Checklist rápido para un módulo nuevo (o antes de cerrar cualquier PR de UI)

- [ ] Todo `this.api.delete(...)` (o baja/cancelación/revocación) pasa por
      `ConfirmationService`, no `window.confirm()`.
- [ ] Todo botón que dispara `post/put/patch/delete` tiene un signal de
      loading dedicado, `.set(true)` antes de la llamada, `.set(false)` en
      next **y** en error, wireado a `[loading]` en el botón real (verificar
      con el mapeo método↔botón de §2.2, no con grep de conteo).
- [ ] Todo campo CURP/RFC/NSS/teléfono/CP editable usa `AdesFormatDirective`
      (máscara) **y** `AdesValidators.xValido()` en `guardar()` (forma
      estructural) — las dos capas, no solo una.
- [ ] Todo par `fecha_inicio`/`fecha_fin` se valida coherente en `guardar()`.
- [ ] Todo campo numérico con rango de negocio (calificación, ponderación,
      edad) se valida en `guardar()`, no solo con `[min]`/`[max]` de UI.
- [ ] `tsc --noEmit` limpio tras los cambios — usar el contenedor desechable
      si el host no tiene `node`:
      ```bash
      docker run --rm -v /opt/ades/frontend:/app -w /app node:22-alpine npx tsc --noEmit -p tsconfig.app.json
      ```

---

## 5. Verificación independiente (no solo el reporte del propio agente)

Mismo estándar que el resto del proyecto (ver `2026-07-16_reporte_fiabilidad_3dias_y_plan.md`
§0 — el proyecto ya se quemó con "COMPLETADO" no verificado):
- `tsc --noEmit` en contenedor limpio, 0 errores, sobre el árbol completo
  (no solo los archivos tocados).
- Re-correr el script de §2.2 después de los fixes — debe bajar a 0 gaps (o
  solo quedar los ya catalogados como falsos positivos, explícitamente
  nombrados).
- `git status --short` — verificar que no quedaron artefactos generados
  (`test-results/`, `playwright-report/`, etc. — Regla Mandatoria #22 de
  `CLAUDE.md`).

**⚠️ `tsc --noEmit` NO detecta errores de binding de template Angular**
(nombres de `@Output()`/`@Input()` mal escritos, propiedades inexistentes en
`(evento)="..."`, etc.) — solo type-checka el cuerpo `.ts` de la clase, no el
`template:` inline ni sus bindings, porque no invoca el compilador de
Angular (`ngtsc`) con chequeo de plantillas. Hallazgo real 2026-07-17: al
implementar soporte para `ColumnConfig.template` en
`interactive-grid.component.ts`, un `git grep` de auditoría encontró
`portal-admin.component.ts` con `(rowSelect)="seleccionar($event)"` — el
`@Output()` real se llama `rowSelected` (confirmado: 27/28 consumidores del
grid usan el nombre correcto, solo este archivo tenía el typo). El binding
al nombre equivocado no falla en tsc ni en runtime — Angular simplemente
intenta escuchar un evento DOM nativo inexistente llamado "rowSelect" y
nunca dispara, dejando el módulo completo (editar/publicar/archivar
convocatorias) sin reaccionar a clics, en silencio, sin ningún error visible
en consola. **Verificar bindings de `@Output()`/`@Input()` con
`ng build --configuration production` (compilador real de Angular, mismo
contenedor `node:22-alpine` que ya se usa para `tsc`) después de tocar
cualquier componente compartido (`shared/components/`) — no basta con
`tsc --noEmit` cuando el cambio afecta un contrato de plantilla usado por
múltiples consumidores.** Grep complementario para encontrar typos de este
tipo en otros outputs compartidos: `grep -rn "(rowSelect)="` (nombre
incorrecto) vs `grep -rln "(rowSelected)="` (nombre real) — comparar conteos.

---

## 6. Plantilla completa del script de mapeo método↔botón (§2.2)

Regenerar en el scratchpad de turno, ajustando la lista `files` (o quitarla
para correr sobre todo `frontend/src/app`):

```python
import re, subprocess

files = subprocess.run(['find', '.', '-name', '*.component.ts'],
                        capture_output=True, text=True).stdout.split()
files = [f[2:] for f in files]

for f in files:
    try:
        text = open(f, encoding='utf-8').read()
    except FileNotFoundError:
        continue
    lines = text.split('\n')
    mut_methods, method_name = set(), None
    for line in lines:
        m = re.match(r'\s*(?:async\s+)?(\w+)\s*\([^)]*\)\s*(?::\s*[\w<>\[\],\s|]+)?\s*\{', line)
        if m and not line.strip().startswith(('if', 'for')):
            method_name = m.group(1)
        if re.search(r'this\.(api|apiService|http)\.(post|put|patch|delete)\(', line) and method_name:
            mut_methods.add(method_name)
    handler_re = re.compile(r'\((?:onClick|click)\)="([a-zA-Z_]\w*)\(')
    gaps = []
    for i, line in enumerate(lines):
        for m in handler_re.finditer(line):
            fn = m.group(1)
            if fn in mut_methods:
                window = '\n'.join(lines[max(0, i - 4):i + 2])
                if '[loading]' not in window:
                    gaps.append((i + 1, fn))
    if gaps:
        print(f"=== {f} ===")
        for ln, fn in gaps:
            print(f"  line {ln}: {fn}() called without nearby [loading]")
```

Nota de precisión: el script solo reconoce `[loading]`. Los botones HTML
planos que usan el patrón `[disabled]` + glifo (§2.2) seguirán apareciendo
como "gap" — son falsos positivos del script, no del código; confirmar
manualmente antes de tocarlos de nuevo (ver `planes-estudio.component.ts`
como caso ya resuelto con ese patrón).
