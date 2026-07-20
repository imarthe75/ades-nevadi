---
name: universal-code-audit
description: Metodología reproducible, agnóstica de lenguaje y framework, para auditar seguridad, estándares de código y errores probables en cualquier proyecto — cómo encontrar los hallazgos del catálogo en docs/security/ESTANDAR_MAESTRO_AUDITORIA_UNIVERSAL.md de forma sistemática (mapeo mutación↔disparador, catálogo de falsos positivos, muestreo real con evidencia) en vez de a ojo o con un solo grep de presencia.
---

# Skill: Auditoría Universal de Código (Seguridad + Estándares + Errores Probables)

**Portable — sin dependencia de Angular, Java, Spring, OpenAPI ni de ningún stack
específico.** Es la versión generalizada de una metodología probada en 3+ rondas reales
de auditoría de un sistema de producción (backend Java/Python + frontend Angular),
destilada para aplicarse en cualquier proyecto. El catálogo de *qué* buscar vive en
`docs/security/ESTANDAR_MAESTRO_AUDITORIA_UNIVERSAL.md`; este documento cubre el
*método* — cómo encontrarlo de forma reproducible, con evidencia real, sin quemar tiempo
redescubriendo los mismos falsos positivos cada ronda.

---

## 0. Principio rector

**Un grep de presencia sobrestima cobertura.** Preguntar "¿existe la palabra
`ConfirmationService`/`try/except`/`@Transactional` en este archivo?" no responde si esa
protección está realmente conectada al punto que necesita protegerse — puede ser un
resto de un refactor anterior, o proteger una acción distinta del mismo archivo. **La
señal confiable es mapear cada punto de mutación/riesgo real contra su disparador/
consumidor real, uno por uno**, y verificar la protección justo ahí. Este skill
documenta cómo hacer ese mapeo con un script/consulta reproducible, no a ojo.

Corolario: un compilador/type-checker en verde tampoco es evidencia suficiente para
ciertas clases de bug (bindings de plantilla/UI, contratos de serialización de red,
mocks de prueba desalineados de la realidad) — ver §6.

---

## 1. Cuándo aplicar este skill

- Al auditar seguridad, estándares o "errores probables" de cualquier proyecto,
  siguiendo el catálogo de `docs/security/ESTANDAR_MAESTRO_AUDITORIA_UNIVERSAL.md`.
- Antes de dar por cerrado cualquier módulo nuevo que agregue puntos de mutación
  (crear/editar/borrar algo) — pasar el checklist de §5 antes de considerarlo terminado.
- Cuando alguien pide "auditar UX", "revisar heurísticas cognitivas", "revisar
  seguridad de endpoints", o continuar una ronda de auditoría anterior.

---

## 2. Metodología general (5 pasos, reusable en cualquier stack)

1. **Enumerar los puntos de mutación/riesgo reales.** Cualquier cosa que cambie estado
   o cruce una frontera de confianza: escritura HTTP (POST/PUT/PATCH/DELETE o su
   equivalente RPC/GraphQL mutation), escritura a base de datos, publicación a una cola
   de mensajes, escritura de archivo, ejecución de comando de sistema, llamada a un
   servicio externo.
2. **Enumerar los disparadores/consumidores reales.** Qué botón/comando/handler/cron/
   consumer invoca cada punto de mutación de (1). En UI: el `(click)`/`onClick`/
   `@click`/handler de evento equivalente del framework. En backend: el endpoint/ruta/
   resolver que expone la función.
3. **Mapear 1:1 mutación↔disparador↔protección esperada**, y verificar la protección
   ahí mismo — no en otro lugar del archivo, no "en general para el módulo". Las
   protecciones esperadas dependen del catálogo maestro: confirmación si es destructivo
   (§3.1), feedback de estado si es asíncrono (§3.2), validación estructural si el campo
   es sensible/oficial (§3.3), scoping de autorización si es multi-tenant (§3.4),
   verificación de contrato si cruza una frontera de serialización (§3.5), atomicidad si
   escribe a más de una entidad relacionada (§3.7).
4. **Catalogar falsos positivos explícitamente conforme se descartan**, en este mismo
   documento o su equivalente del proyecto — con el archivo y la razón concreta. El
   objetivo es que la siguiente ronda de auditoría no vuelva a gastar tiempo
   redescubriendo que "esto no aplica porque X" — debe poder leerlo y confiar en él (o
   refutarlo si el código cambió).
5. **Verificación independiente con evidencia reproducible.** Preferir: prueba
   automatizada corrida contra el sistema real, consulta a datos reales, petición HTTP
   real (`curl`) contra el endpoint real, sobre lectura de código. Ver §6 para el
   estándar de qué cuenta como evidencia suficiente para declarar algo "corregido".

---

## 3. Plantillas de detección por categoría

Cada plantilla da la señal conceptual + un ejemplo de regex/grep genérico. **Adáptalo al
lenguaje/framework real del proyecto** — el regex de ejemplo es solo ilustrativo del
patrón, no una solución universal literal.

### 3.1 — Confirmación en operaciones destructivas

**Señal objetiva:** todo handler que llama a una operación de borrado/cancelación/
revocación (`delete`, `remove`, `cancel`, `revoke`, `archive`, `deactivate`,
`terminate`) debe estar envuelto en el mecanismo de confirmación estándar del proyecto
(modal de la librería de UI, prompt de CLI con flag explícito para saltarlo) — no un
`confirm()`/`alert()` nativo del navegador ni un `input()` ad-hoc.

```bash
# Ejemplo (ajustar verbo y llamada real del proyecto):
grep -rlE "\.(delete|remove|cancel|revoke|archive|deactivate|terminate)\(" --include="*.<ext>" . \
  | xargs -I{} sh -c 'grep -q "<MecanismoDeConfirmacionDelProyecto>" "{}" || echo "SIN confirmación: {}"'
```

Amplía el verbo a **todos** los sinónimos de "dar de baja" en el idioma del proyecto —
un grep que solo busca "eliminar"/"delete" se pierde "terminar", "cancelar", "revocar",
"cerrar", etc. (lección real: una ronda de auditoría se saltó un caso real solo porque su
grep original no cubría el verbo "terminar").

Luego **lee cada archivo marcado** — el grep solo dice "el mecanismo no está importado en
el archivo", no que la llamada específica carezca de diálogo (puede estar en otro método
del mismo archivo).

### 3.2 — Feedback de estado en operaciones mutantes/asíncronas

**Señal objetiva (mapeo, no conteo):** para cada método que hace una llamada
mutante (HTTP/RPC), busca el handler del template/vista que lo invoca y verifica que
las líneas cercanas realmente activan/desactivan un indicador de carga — no solo que la
palabra "loading" exista en algún lugar del archivo.

Pseudocódigo de script (ver §7 para una versión completa en Python, adaptable a
cualquier lenguaje de scripting):
1. Recorrer el archivo fuente línea a línea, llevando registro del "método actual"
   (regex de firma de función/método del lenguaje real).
2. Si una línea dentro de ese método invoca la función de mutación real del proyecto,
   agregar el nombre del método a un conjunto `mut_methods`.
3. Recorrer el template/vista buscando el patrón de binding de evento del framework
   real (`(click)=`, `onClick=`, `@click=`, `ng-click=`, etc.) que invoque un método de
   `mut_methods`.
4. Si lo encuentra, mirar unas líneas alrededor por el atributo/prop real de "loading"
   del framework de UI usado. Si no aparece → hueco real.

**Patrón de corrección genérico:**
- Una bandera de estado dedicada por acción (o por fila, si es una acción en lista) que
  se activa antes de la llamada y se desactiva tanto en éxito como en error.
- Conectada de verdad al atributo de "loading"/"disabled" real del componente de botón
  usado — no solo declarada en la clase.
- Para componentes de UI plana sin soporte nativo de "loading", usar `disabled` +
  indicador visual (spinner/glifo) como equivalente funcional.

### 3.3 — Validación estructural real (no solo máscara/formato superficial)

**Señal objetiva:** localizar todo campo con formato oficial/sensible (identificador
nacional, código postal, cuenta bancaria, teléfono, etc.) ligado a la UI, y cruzarlo
contra dónde se invoca la función de validación estructural real del proyecto antes de
enviar:

```bash
grep -rniE '(campo_sensible_1|campo_sensible_2)' --include="*.<ext>" .   # candidatos
grep -rl "<FuncionDeValidacionEstructuralDelProyecto>" --include="*.<ext>" .  # cobertura actual
```

Cualquier candidato del primer grep ausente del segundo es un hueco real — salvo que el
campo sea de solo lectura (no editable tras la creación del registro).

**Antes de agregar la validación, confirma que el campo realmente se envía en el
payload de guardado** — validar un campo que nunca se persiste es esfuerzo
desperdiciado y suele señalar un segundo bug real (persistencia silenciosa, ver
catálogo maestro §5.7).

### 3.4 — Scoping de autorización multi-tenant (BOLA/BFLA)

**Señal objetiva:** para cada endpoint/función que resuelve un objeto por ID, clasifica:
1. ¿Llama al helper/patrón de scoping centralizado del proyecto? (grep-eable, nombre
   consistente).
2. ¿Legítimamente no lo necesita? (catálogo global sin dueño de tenant).
3. Ni lo uno ni lo otro → candidato real.

```bash
grep -rLE "<HelperDeScopingReal>" --include="*<extensión_de_controladores>" . \
  | xargs grep -l "<FuncionQueResuelveUsuarioAutenticado>"
# Da la lista de archivos que resuelven usuario pero no muestran el helper de scoping —
# revisar cada uno a mano, no asumir que todos son huecos reales.
```

No confíes en que una ronda de auditoría previa cerró "todos" los casos: vuelve a correr
tu propia versión del grep con criterio propio antes de heredar la cifra (ver catálogo
maestro §8, y el corolario de §0 de este documento).

### 3.5 — Deriva de contrato entre frontend/backend o entre servicios

**Señal objetiva:** para cualquier campo nuevo en una petición/respuesta que cruce una
frontera de serialización, verifica con una petición real (no solo con el tipo/schema
generado) que el nombre y forma coinciden:

```bash
curl -s -X POST <endpoint_real> -H "Authorization: Bearer <token_real>" \
  -d '{"campo_de_prueba": "valor"}' | jq .
# Compara la respuesta/aceptación real contra lo que el tipo generado declara.
```

Si el proyecto usa un generador automático de tipos desde OpenAPI/protobuf/GraphQL SDL,
confirma primero si el generador conoce la estrategia real de naming del backend
(`camelCase` vs `snake_case` vs otra) — si no la conoce, el contrato "generado" puede ser
sistemáticamente incorrecto para un subconjunto de casos (típicamente request bodies con
nombre de clase, más que respuestas GET). Documenta esa desconexión como hallazgo de
tooling, no solo corrijas los síntomas caso por caso — si no se corrige la raíz, cada
desarrollador nuevo puede repetir el mismo error con el mismo tipo "generado" engañoso.

### 3.6 — N+1 / fan-out en agregaciones con JOIN

**Señal objetiva:** para cualquier query/consulta ORM con `SUM`/`COUNT`/`AVG`/`AVG`
sobre una relación uno-a-muchos, confirma explícitamente que el `JOIN` correlaciona por
la llave real de la entidad que se agrega (no solo que "corre sin error de sintaxis") —
construye un caso de prueba con datos conocidos donde el resultado esperado se pueda
calcular a mano, y compáralo.

### 3.7 — Transacciones/atomicidad

**Señal objetiva:** para cualquier función de escritura, verifica con una prueba real
(escribir, luego leer desde una conexión/sesión nueva, no desde caché) que el dato
persiste. Si el proyecto tiene `autocommit` deshabilitado a nivel de conexión, confirma
explícitamente que la función está envuelta en el mecanismo de transacción real del
framework (no solo que "no truena") — este es exactamente el tipo de bug que un test
superficial ("el endpoint responde 200") no detecta.

### 3.8 — Manejo de errores silencioso

**Señal objetiva:** grep de patrones de catch vacío o catch-a-valor-por-defecto sin
logging:

```bash
grep -rnE "catch\s*\([^)]*\)\s*\{\s*\}" --include="*.<ext>" .          # catch vacío
grep -rnE "\.catch\(\(\)\s*=>\s*(\[\]|null|undefined)\)" --include="*.<ext>" .  # JS/TS
grep -rnE "except\s*:\s*pass" --include="*.py" .                        # Python
```

Cada resultado: ¿el error realmente no importa (idempotente, reintentable sin daño), o
está ocultando una falla real? Si es lo segundo, al menos loggear; idealmente,
propagar o notificar.

---

## 4. Muestreo real de heurísticas cognitivas (Nielsen) — agnóstico de framework de UI

Método reproducible, validado con Playwright pero aplicable con Selenium/Cypress
(web), Appium (móvil), o incluso una sesión de terminal grabada (CLI):

1. **Reusa la infraestructura de autenticación/E2E que ya exista en el proyecto** —no
   inventes un flujo de login nuevo. Si el proyecto ya sabe generar una sesión/token real
   para pruebas, reutilízalo.
2. **Fija la versión de la herramienta de automatización a la que ya usa el proyecto**
   (leer el lockfile/manifest de dependencias) — no asumas `latest`, para reproducibilidad.
3. **Una espera de "red inactiva" (`networkidle` o equivalente) falla en páginas con
   polling/streaming activo** (notificaciones en vivo, websockets, iframes de terceros)
   — usa una espera de "DOM cargado" + espera fija corta en su lugar para esas páginas.
4. **Captura + extracción de texto** (encabezados, botones, atributos de accesibilidad,
   placeholders), no solo captura visual — permite analizar terminología/consistencia
   por texto además de por percepción visual.
5. **⚠️ Si el sistema tiene datos reales de producción, las capturas contienen PII.**
   Revisa y elimina las capturas inmediatamente después del análisis — no las dejes en
   disco, no las publiques como artifact. Extrae del análisis solo lo necesario para el
   reporte, nunca el PII visto.
6. **Antes de reportar una heurística como "sin cobertura" por ausencia de una señal de
   grep**, confirma si la funcionalidad podría vivir en un componente/layout compartido
   en vez de estar duplicada por página — un grep por-archivo no ve nunca lo que vive en
   el shell/layout común (lección real: un grep de "breadcrumb" reportó 2.5% de
   cobertura porque el breadcrumb en realidad vivía en el layout compartido, construido
   dinámicamente — la evidencia real de navegador mostró 100% de cobertura funcional).

Mapeo rápido de las 10 heurísticas a qué tipo de evidencia buscar:

| # | Heurística | Evidencia a buscar en el muestreo |
|---|---|---|
| 1 | Visibilidad del estado | Feedback de carga/error visible tras cada acción (ver §3.2) |
| 2 | Coincidencia con el mundo real | Vocabulario del dominio real, no jerga técnica/nombre interno de librería expuesto al usuario final |
| 3 | Control y libertad | Deshacer/cancelar disponible en flujos multi-paso; confirmación en destructivas (§3.1) |
| 4 | Consistencia | Mismo patrón visual/interacción para la misma acción en distintas pantallas del mismo rol |
| 5 | Prevención de errores | Validación antes de enviar (§3.3), no solo después de que el backend rechace |
| 6 | Reconocimiento vs. recuerdo | Estados vacíos que explican qué falta y dónde encontrarlo, en vez de una pantalla en blanco sin contexto |
| 7 | Flexibilidad | Atajos/bulk actions para usuarios expertos, no solo el camino de un-registro-a-la-vez |
| 8 | Diseño minimalista | Densidad de información proporcional al rol/tarea, no un menú/dashboard único para todos los roles |
| 9 | Recuperación de errores | Mensaje de error específico y accionable, no genérico ("Ocurrió un error") |
| 10 | Ayuda y documentación | Tooltips/mensajes de error explicativos en los puntos de fricción reales |

---

## 5. Checklist rápido para cerrar cualquier auditoría de módulo nuevo

- [ ] Todo punto de mutación destructivo pasa por el mecanismo de confirmación estándar
      del proyecto (§3.1).
- [ ] Todo botón/acción mutante tiene feedback de estado realmente conectado (§3.2), no
      solo declarado en la clase.
- [ ] Todo campo con formato oficial/sensible tiene máscara **y** validación
      estructural real, y se confirma que se envía en el payload (§3.3).
- [ ] Todo endpoint/función multi-tenant tiene scoping verificado, no heredado de un
      reporte anterior sin re-chequear (§3.4).
- [ ] Todo contrato nuevo entre capas se verificó contra una petición/respuesta real
      (§3.5), no solo contra un tipo generado.
- [ ] Toda query con agregación sobre JOIN uno-a-muchos se revisó por fan-out (§3.6).
- [ ] Toda escritura nueva se verificó con una prueba real de persistencia, no solo
      "no truena" (§3.7).
- [ ] Sin manejo de errores que oculte causas reales en el código nuevo (§3.8).
- [ ] El compilador/type-checker del lenguaje está limpio **y**, si el framework de UI
      tiene su propio compilador de plantillas (Angular, Vue SFC, etc.), ese build
      también se corrió — un type-checker de solo-TypeScript/solo-clase no valida
      bindings de plantilla (ver §6).
- [ ] `git status`/equivalente limpio de artefactos generados antes de cerrar la tarea.

---

## 6. Verificación independiente (no solo el reporte del propio agente/auditor)

- Preferir evidencia ejecutada (prueba corrida, query real, `curl` real) sobre lectura
  de código para cualquier hallazgo que se declare "corregido".
- Volver a correr el script/grep de detección después de un fix — debe bajar a 0 gaps
  reales (o solo quedar los ya catalogados explícitamente como falsos positivos).
- **Un type-checker limpio no es evidencia suficiente para bindings de plantilla/UI.**
  Frameworks como Angular compilan la clase `.ts` sin necesariamente type-checkear el
  `template`/HTML y sus bindings de evento — un nombre de evento mal escrito
  (`(eventoReal)` vs `(eventoTypo)`) no falla ni en el type-checker ni en runtime con
  excepción visible; el handler simplemente nunca se dispara, en silencio. Verifica con
  el compilador/build real del framework de UI (el que sí valida plantillas) después de
  tocar cualquier componente compartido consumido por múltiples pantallas.
- Documenta explícitamente el criterio/comando usado para declarar algo "0 hallazgos" —
  para que la siguiente ronda pueda ampliarlo en vez de heredarlo ciegamente (ver
  catálogo maestro `docs/security/ESTANDAR_MAESTRO_AUDITORIA_UNIVERSAL.md` §8).

---

## 7. Plantilla completa de script de mapeo método↔disparador (parametrizable)

Ajustar `MUTATION_PATTERN`, `HANDLER_PATTERN` y `FEEDBACK_ATTR` al framework real del
proyecto (los valores de ejemplo son de un frontend Angular/TypeScript — para React
sería `onClick={metodo}` y un hook de estado; para un CLI sería el nombre de la función
que ejecuta el comando y una bandera de progreso, etc.):

```python
import re, subprocess

MUTATION_PATTERN = r'this\.(api|apiService|http)\.(post|put|patch|delete)\('
HANDLER_PATTERN = re.compile(r'\((?:onClick|click)\)="([a-zA-Z_]\w*)\(')
FEEDBACK_ATTR = '[loading]'
FILE_GLOB = '*.component.ts'  # ajustar extensión/patrón al lenguaje real

files = subprocess.run(['find', '.', '-name', FILE_GLOB],
                        capture_output=True, text=True).stdout.split()

for f in [x[2:] for x in files]:
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
        if re.search(MUTATION_PATTERN, line) and method_name:
            mut_methods.add(method_name)
    gaps = []
    for i, line in enumerate(lines):
        for m in HANDLER_PATTERN.finditer(line):
            fn = m.group(1)
            if fn in mut_methods:
                window = '\n'.join(lines[max(0, i - 4):i + 2])
                if FEEDBACK_ATTR not in window:
                    gaps.append((i + 1, fn))
    if gaps:
        print(f"=== {f} ===")
        for ln, fn in gaps:
            print(f"  line {ln}: {fn}() invocado sin '{FEEDBACK_ATTR}' cercano")
```

**Nota de precisión:** el script solo reconoce el atributo/patrón exacto configurado —
variantes equivalentes (ej. `[disabled]` + glifo como sustituto de `[loading]` en
botones sin soporte nativo) aparecerán como falso positivo. Confirma manualmente antes
de "corregir" un caso ya resuelto con un patrón equivalente distinto.

---

## 8. Catálogo de falsos positivos (mantener por proyecto)

Esta sección debe llenarse por proyecto conforme se descarta cada falso positivo real —
no se reproduce aquí ningún ejemplo específico de un proyecto ajeno porque perdería
vigencia (el código cambia). Formato sugerido por entrada:

```
- `archivo.ext::metodo()` — [por qué no aplica, en una línea]. Confirmado [fecha].
```

Volver a leer esta lista al inicio de cada ronda nueva de auditoría, y **releer el
código real de cada entrada** antes de confiar en que sigue siendo válida — el código
pudo haber cambiado desde que se catalogó.

---

## 9. Apéndice: notas rápidas por stack

- **Angular/React/Vue (SPA):** el punto de mutación casi siempre es una llamada a un
  cliente HTTP dentro de un método de componente/hook; el disparador es un binding de
  evento en el template/JSX. El compilador de plantillas (no solo `tsc`/Babel) es el que
  valida bindings — correr el build real, no solo el type-checker.
- **Spring/Django/Rails/Express/FastAPI (backend web):** el punto de mutación es el
  handler de la ruta; el "disparador" a auditar es la cadena de middleware/decoradores
  de autenticación y autorización que efectivamente se ejecuta antes del handler — no
  asumir que un decorador está aplicado porque el archivo lo importa.
- **CLI/scripts con privilegios:** el "disparador" es el punto de entrada del comando;
  la confirmación destructiva es un prompt interactivo o una bandera explícita
  (`--yes`/`--force`) — nunca ejecutar una operación irreversible sin alguna de las dos.
- **Sistemas basados en colas/eventos (mensajería, workers):** el punto de mutación es
  el handler del consumer; el "disparador" es el productor del mensaje — el scoping de
  autorización debe verificarse en el consumer (nunca confiar en que el productor ya
  validó permisos, especialmente si el productor y el consumer son servicios distintos).
