# Estándar Maestro de Auditoría — Seguridad, Estándares de Código y Errores Probables

**Alcance: universal / multi-lenguaje.** Este documento es portable — no depende de
Angular, Java, Spring, OpenAPI ni de ninguna decisión de arquitectura específica de un
proyecto. Está pensado para copiarse tal cual a cualquier repositorio (como
`docs/`, anexo de `CLAUDE.md`/`AGENTS.md`, o checklist de code review) y usarse para
auditar seguridad, calidad de código y errores probables en cualquier stack.

Es la versión generalizada de las secciones "ESTÁNDARES DE SEGURIDAD OBLIGATORIOS" y
"OPTIMIZACIÓN AL 100%" de un proyecto real (sistema escolar multi-tenant, backend
Java/Python + frontend Angular), destilada para que las lecciones — muchas aprendidas de
bugs reales en producción, no de teoría — sirvan en cualquier lenguaje o framework. El
skill compañero, `.agent/skills/universal-code-audit/SKILL.md`, cubre el *método* de
auditoría (cómo encontrar estos problemas de forma reproducible); este documento cubre el
*catálogo* de qué buscar.

---

## 0. Cómo usar este documento

- **Convención de confianza** (úsala en cualquier reporte de auditoría, no solo aquí):
  marca cada hallazgo como **[Cierto]** (reproducido con evidencia real: logs, prueba
  ejecutada, query contra datos reales), **[Probable]** (inferido de código/patrones
  consistentes pero no ejecutado) o **[Hipótesis]** (sospecha razonable, sin verificar
  aún). Nunca declares algo "100% corregido" o "COMPLETADO" sin la evidencia que
  corresponde a [Cierto] — ver §8.
- Este documento **no reemplaza** el estándar de seguridad específico de tu proyecto (ese
  debe seguir viviendo en tu `CLAUDE.md`/`AGENTS.md` con los detalles concretos de tu
  stack); lo complementa con la capa universal que aplica sin importar el lenguaje.
- Secciones 1-4: seguridad. Sección 5: catálogo de errores probables (bugs reales, no
  solo vulnerabilidades). Secciones 6-7: confiabilidad de CI/pruebas y UX. Sección 8:
  disciplina de verificación. Sección 9: mapa rápido por stack. Sección 10: checklist
  condensado para pre-merge.

---

## 1. Modelo de amenazas y línea base de seguridad

### 1.1 STRIDE (aplicar a cada endpoint/función expuesta nueva, sin importar el lenguaje)

| Amenaza | Pregunta a responder |
|---|---|
| **S**poofing | ¿Puede alguien hacerse pasar por otro usuario/servicio? ¿La identidad se verifica criptográficamente (JWT firmado, mTLS, API key con HMAC), no solo por un header que el cliente controla? |
| **T**ampering | ¿Puede alguien modificar datos en tránsito o en reposo sin detección? ¿Hay integridad (firma, checksum, TLS) en cada capa? |
| **R**epudiation | ¿Queda registro de quién hizo qué y cuándo, de forma que no se pueda negar después? (ver §5.12, auditoría) |
| **I**nformation disclosure | ¿Puede alguien leer datos que no le corresponden? (scoping — ver §4) |
| **D**enial of service | ¿Puede un solo actor agotar recursos compartidos (CPU, conexiones DB, rate limit) y tumbar el servicio para todos? |
| **E**levation of privilege | ¿Puede un usuario de bajo privilegio ejecutar una acción reservada a uno de mayor privilegio? |

### 1.2 OWASP Top 10 (2021) — generalizado, sin asumir stack

1. **Broken Access Control** — la causa #1 de incidentes reales en la mayoría de sistemas
   multi-tenant. Ver §4 (BOLA/BFLA).
2. **Cryptographic Failures** — secretos en texto plano, TLS mal configurado, hashes
   débiles (MD5/SHA1 para contraseñas), claves hardcodeadas en el repo.
3. **Injection** — no es solo SQL. Ver §3 para el catálogo completo por tipo.
4. **Insecure Design** — falta de threat modeling antes de construir (por eso STRIDE va
   primero en este documento, no al final).
5. **Security Misconfiguration** — puertos expuestos innecesarios, headers de seguridad
   faltantes (HSTS, X-Frame-Options, X-Content-Type-Options, CSP), stack traces
   expuestos al cliente en producción, cuentas/servicios con credenciales por defecto.
6. **Vulnerable and Outdated Components** — ver §9 (comando de audit por ecosistema).
7. **Identification and Authentication Failures** — sesiones que no expiran, rate
   limiting ausente en login/reset de contraseña, enumeración de usuarios vía mensajes
   de error distintos ("usuario no existe" vs "contraseña incorrecta").
8. **Software and Data Integrity Failures** — pipelines CI/CD sin verificación de
   integridad de dependencias, deserialización insegura de datos no confiables.
9. **Security Logging and Monitoring Failures** — sin esto, un incidente se detecta
   meses después o nunca. Ver §5.12.
10. **Server-Side Request Forgery (SSRF)** — cualquier función que acepte una URL/host
    de usuario y haga una petición saliente desde el servidor.

### 1.3 OWASP API Security Top 10 (2023) — aplica a REST, GraphQL, gRPC, RPC interno

| # | Riesgo | Pregunta clave |
|---|---|---|
| API1 | Broken Object Level Authorization (BOLA) | ¿El endpoint verifica que el objeto pedido por ID pertenece al tenant/usuario que lo pide, o confía en que el ID "parece" correcto? |
| API2 | Broken Authentication | ¿Los tokens se validan (firma, expiración, audiencia) en cada request, o solo en el login? |
| API3 | Broken Object Property Level Authorization | ¿Un usuario puede leer/escribir campos del objeto que no debería (mass assignment)? |
| API4 | Unrestricted Resource Consumption | ¿Hay límites de paginación, tamaño de payload, rate limiting, timeouts? |
| API5 | Broken Function Level Authorization (BFLA) | ¿La función (no el objeto) requiere el rol correcto — un usuario normal no puede llamar un endpoint de admin solo porque conoce la URL? |
| API6 | Unrestricted Access to Sensitive Business Flows | ¿Un flujo de negocio sensible (compra, aprobación, promoción de ciclo escolar, transferencia) se puede automatizar/repetir sin control (ej. bot, doble clic, replay)? |
| API7 | Server Side Request Forgery | Ver 1.2 #10. |
| API8 | Security Misconfiguration | Ver 1.2 #5. |
| API9 | Improper Inventory Management | ¿Existen versiones viejas de la API o endpoints de debug/staging accesibles en producción? ¿Hay un inventario real de qué expone el sistema? |
| API10 | Unsafe Consumption of APIs | ¿Se confía ciegamente en la respuesta de una API de terceros (incluyendo otro microservicio interno) sin validarla? |

### 1.4 Controles NIST rápidos (mapeo mínimo, sin necesidad de certificación formal)

- **AC-3** (control de acceso) → §4.
- **AU-3 / AU-12** (contenido y generación de auditoría) → §5.12.
- **SI-10** (validación de input) → §2, §3.
- **SC-8** (protección en tránsito) → TLS en todo enlace que cruce una frontera de
  confianza, incluyendo tráfico interno entre microservicios si no están en la misma red
  de confianza.
- **SC-28** (protección de datos en reposo) → cifrado de PII/datos sensibles en la base
  de datos, no solo a nivel de disco.

### 1.5 Privacidad (GDPR / leyes locales equivalentes — LGPD, LFPDPPP, CCPA, etc.)

- Minimización de datos: no captures/retengas más de lo que el flujo de negocio
  requiere.
- Acceso por necesidad: un rol de "lectura general" no debería poder ver campos
  sensibles (salud, salario, datos de menores) sin una razón de negocio explícita.
- Derecho al olvido / portabilidad: si la ley aplicable lo exige, verifica que exista un
  camino real para borrar/exportar datos de una persona, no solo teórico.
- Cifrado en tránsito **y** en reposo para cualquier campo clasificado como sensible.

---

## 2. Checklist universal de seguridad por endpoint/función expuesta

Aplica esto a cualquier punto de entrada (HTTP endpoint, RPC, GraphQL resolver, mensaje
de cola consumido, comando de CLI con privilegios, cron job) sin importar el lenguaje:

- [ ] La identidad del llamante se resuelve criptográficamente (nunca "confío en el
      header/campo que el cliente envía").
- [ ] El nivel de acceso/rol se verifica contra la operación solicitada — 403/permission
      denied si es insuficiente, no un 200 que silenciosamente no hace nada.
- [ ] Si el sistema es multi-tenant (multi-plantel, multi-organización, multi-cuenta):
      el scoping por tenant se aplica **siempre**, no solo para roles no-admin — un
      "superadmin" con alcance real limitado a su propio tenant necesita el mismo
      chequeo que cualquiera (lección real: confundir "rol más alto" con "sin límite de
      tenant" es un BOLA disfrazado).
- [ ] Los parámetros de ruta/query se usan realmente en la lógica de autorización (no
      solo en el WHERE de la query de datos — un ID de ruta ignorado en el chequeo de
      permisos es una fuga clásica).
- [ ] Cada campo del body se valida: tipo, rango, enum permitido, longitud máxima. No
      confiar en la validación del cliente/frontend — se puede saltar con `curl`.
- [ ] La función, si muta estado, queda registrada en el mecanismo de auditoría del
      proyecto (ver §5.12) — con quién, qué, cuándo, y de ser posible, el valor anterior.
- [ ] Una actualización/borrado que afecta 0 filas devuelve 404 (recurso no encontrado o
      no pertenece a este tenant), nunca 200 — un 200 con 0 filas afectadas es
      indistinguible, desde el cliente, de "sí se guardó" (esta es exactamente la clase
      de bug que produce guardados silenciosamente rotos — ver §5.4).
- [ ] Mass assignment: el endpoint solo escribe los campos que el DTO/schema de entrada
      declara explícitamente — nunca hace bind directo de un objeto/diccionario completo
      del body a la entidad de persistencia.

---

## 3. Clases de inyección más allá de SQL (agnóstico de lenguaje)

| Clase | Dónde aparece | Mitigación genérica |
|---|---|---|
| SQL Injection | Cualquier query construida por concatenación de strings | Parametrización/prepared statements siempre; ORM no es inmunidad automática (los `raw()`/`exec()` del ORM reintroducen el riesgo) |
| Command Injection | `exec`/`system`/`subprocess` con input de usuario en el comando | Nunca construir el comando por concatenación; usar arrays de argumentos, no shell=True/string interpolation |
| Path Traversal | Rutas de archivo construidas con input de usuario (`../../etc/passwd`) | Normalizar y validar contra una lista blanca de directorio base; nunca confiar en el nombre de archivo del cliente |
| SSRF | Cualquier función que haga fetch/request a una URL provista por el usuario | Lista blanca de hosts/esquemas permitidos; bloquear rangos de IP internos (169.254.x.x, 10.x, 172.16-31.x, 192.168.x, `localhost`) |
| XXE (XML External Entity) | Parsers XML con resolución de entidades externas habilitada por defecto | Deshabilitar DTD externo/entidades en el parser (`disallow-doctype-decl`, `resolve_entities=False`, etc. según el lenguaje) |
| Deserialización insegura | `pickle`/`unserialize`/`ObjectInputStream`/`unmarshal` sobre datos no confiables | No deserializar tipos nativos peligrosos desde input externo; usar formatos de datos (JSON validado por schema), no objetos serializados nativos |
| Template Injection (SSTI) | Motor de plantillas que evalúa expresiones del usuario (`{{ user_input }}` sin escapar en Jinja2/Twig/Freemarker/etc.) | Nunca pasar input de usuario como plantilla en sí misma, solo como variable a interpolar (auto-escapada) |
| NoSQL Injection | Operadores de MongoDB/similar (`$where`, `$ne`) inyectados vía JSON del body | Validar tipos estrictamente (un campo que espera string no debe aceptar un objeto `{$ne: null}`) |
| LDAP Injection | Filtros LDAP construidos por concatenación | Escapar caracteres especiales de filtro LDAP antes de construir la query |
| Log Injection / Log Forging | Input de usuario escrito directo a logs sin sanear (permite falsificar entradas de log o inyectar ANSI/control chars) | Sanear saltos de línea y caracteres de control antes de loggear input de usuario |
| Prototype Pollution (JS/Node específico) | Merge/asignación recursiva de objetos con claves controladas por el usuario (`__proto__`, `constructor.prototype`) | Usar `Object.create(null)` o librerías de merge que bloqueen explícitamente esas claves |

---

## 4. Autorización multi-tenant: BOLA/BFLA generalizado

Esta es, empíricamente, la categoría de vulnerabilidad más común y más costosa de
encontrar tarde en sistemas multi-tenant reales — y la más fácil de dar por "cerrada"
prematuramente. Patrón recurrente observado:

- **Anti-patrón real:** un umbral de rol usado de forma inconsistente para lo mismo en
  distintos archivos (`nivelAcceso <= 1`, `== 3`, `< 2`, `> 2` todos coexistiendo para
  expresar el mismo concepto de "es admin de este tenant"). Esto no es solo desprolijo:
  cada variante es una superficie de bug distinta. **Solución:** un único helper/función
  centralizada (`requireTenantScope(user, resourceTenantId)` o equivalente) que todo
  endpoint debe llamar — nunca reimplementar el umbral inline.
- **Anti-patrón real #2:** confundir "rol de mayor privilegio dentro de un tenant" con
  "sin límite de tenant". Un rol como "administrador de plantel/sucursal/organización"
  frecuentemente NO debería ver otros tenants — pero si el código solo chequea
  `rol == ADMIN` sin verificar el tenant, cualquier admin de cualquier tenant ve todos.
- **Auditoría reproducible:** para cada función/endpoint que resuelve un objeto por ID,
  clasifícalo en una de 3 categorías:
  1. Tiene un mecanismo de scoping verificable (nombre de función/patrón consistente,
     grep-eable).
  2. No lo tiene pero legítimamente no lo necesita (catálogos globales, tablas de
     referencia sin dueño).
  3. No lo tiene y **debería** — candidato real. Prioriza los que tocan datos sensibles
     (salud, financiero, PII de menores/empleados) primero.
- **No confíes en un audit previo que declaró "completado".** Si un reporte de auditoría
  anterior dice "82 controladores auditados, 100% corregido", vuelve a correr el grep/
  script de detección tú mismo antes de asumirlo — es común que una ronda de auditoría
  cierre el patrón dominante y deje una "cola larga" de casos que ningún grep anterior
  cubría (nombres de función distintos, helpers con casing diferente, etc.). Ver §8.

---

## 5. Catálogo de errores probables (bug classes independientes del lenguaje)

Estas no son necesariamente vulnerabilidades de seguridad — son clases de bugs reales,
observados en producción, que aparecen sin importar el lenguaje o framework.

### 5.1 — N+1 queries / fan-out en agregaciones (cualquier ORM)

Aplica a Hibernate/JPA, SQLAlchemy, Django ORM, ActiveRecord, Prisma, GORM, Sequelize,
Eloquent, etc. por igual:

- **N+1 clásico:** cargar una lista de N objetos y luego, por cada uno, hacer una query
  separada para su relación (`for item in items: item.related_thing` sin eager loading).
  Detección: contar queries emitidas para una request que devuelve una lista — debería
  ser O(1) o O(pocas), no O(N).
- **Fan-out en agregación (más sutil y más peligroso):** un `JOIN` uno-a-muchos dentro de
  una query que además hace `SUM`/`COUNT`/`AVG` infla el resultado silenciosamente
  (multiplica filas antes de agregar). Ejemplo real: un JOIN a una tabla de calificaciones
  por periodo sin correlacionar correctamente por estudiante infló un promedio general
  porque cada fila de estudiante se multiplicó por el número de periodos. **Regla:**
  cualquier query con agregado sobre un JOIN uno-a-muchos debe revisarse explícitamente
  por este patrón — no basta con que la query "corra sin error", el número puede estar
  mal y nadie lo nota hasta que alguien audita el dato en detalle.

### 5.2 — Transacciones y atomicidad (silenciosamente no persistido)

Bug real observado: un ORM/driver configurado con `autocommit=false` a nivel de
conexión exige que **cada** operación de escritura esté envuelta explícitamente en una
transacción/commit — si una ruta de código nueva olvida el `@Transactional`/`BEGIN...
COMMIT`/`with transaction.atomic():` equivalente, el UPDATE/INSERT se ejecuta contra la
conexión, nunca se confirma, y el rollback implícito al final del request lo descarta —
**sin ningún error, con HTTP 200 igual.** Esto es especialmente peligroso porque:
- Pasa desapercibido en pruebas superficiales (el código "no truena").
- Puede persistir por meses si nadie compara el dato que "se guardó" contra lo que
  realmente hay en la base.
- El mismo patrón puede estar replicado en decenas de archivos si se copió una plantilla
  de servicio sin el decorador/wrapper de transacción.

**Detección:** para cualquier función que hace una escritura, confirma con una prueba
real (no solo lectura de código) que el dato persiste tras el request — leyendo de una
conexión nueva, no de caché/sesión.

### 5.3 — Fugas de memoria/recursos, por paradigma

| Paradigma/lenguaje | Patrón de fuga típico | Detección |
|---|---|---|
| Frontend reactivo (Angular/React/Vue) | Suscripción a un observable/event listener no cancelada al destruir el componente | Verificar que todo componente con suscripciones implemente el hook de limpieza (`ngOnDestroy`, `useEffect` cleanup, `onUnmounted`) — grep de "implements/extends X" con patrón laxo, no solo textual exacto |
| JavaScript/Node en general | Closures que retienen referencias grandes; listeners de `EventEmitter` acumulados sin `removeListener` | Revisar cualquier `.on(...)` sin `.off(...)`/`.once(...)` correspondiente |
| Python | Referencias circulares con `__del__`; caches globales (`lru_cache`, diccionarios module-level) que crecen sin límite | Usar `weakref` donde aplique; poner límite/TTL a cualquier cache in-memory |
| Java/JVM | Listeners registrados y nunca removidos; `ThreadLocal` no limpiado en pools de threads reusados | Revisar registro/desregistro simétrico; `ThreadLocal.remove()` explícito |
| Go | Goroutines que nunca terminan (bloqueadas en un channel que nadie más lee/escribe) | `go vet`, revisar que todo goroutine tenga una condición de salida clara (context cancellation) |
| Rust | Ciclos de referencia con `Rc<RefCell<T>>` sin `Weak` | Usar `Weak` para referencias "hacia atrás" en estructuras cíclicas |
| Cualquier lenguaje | Conexiones DB/archivos/sockets abiertos sin cerrar en la ruta de error (solo se cierran en el happy path) | Usar `try/finally`, `with`/context manager, `defer`, `using` — cualquier constructo que garantice cierre incluso si hay excepción |

### 5.4 — Fallas silenciosas / manejo de errores que oculta el problema

- `catch (e) {}` vacío, o `.catch(() => [])`/`.catch(() => null)` que convierte un error
  real en un valor por defecto sin loggear ni notificar — el síntoma desaparece pero la
  causa sigue ahí. Bug real: un buscador que llamaba a un endpoint inexistente (404)
  quedó enmascarado por un catch que devolvía lista vacía — parecía "sin resultados",
  era en realidad "endpoint roto", y nadie lo notó porque el catch nunca dejó ver el
  error.
- Un 200 OK que en realidad significa "no se hizo nada" (ver §2, actualización de 0
  filas). Cualquier operación que pueda "no encontrar nada que actualizar" debe
  distinguir ese caso del éxito real.
- Mensajes de error genéricos ("Ocurrió un error") que ocultan si el problema es del
  usuario (input inválido, corregible) o del sistema (bug, debe reportarse) — dificulta
  tanto la UX como el diagnóstico.

### 5.5 — Deriva de contrato entre capas/servicios

Aplica a cualquier frontera entre dos sistemas que deben acordar un formato: frontend↔
backend, microservicio↔microservicio, backend↔cola de mensajes, backend↔proveedor
externo.

- **Convención de nombres inconsistente:** un lado usa `camelCase`, el otro
  `snake_case` (o `kebab-case`, o `PascalCase`) y no hay una capa de traducción
  explícita y probada — el mismatch aparece campo por campo, silenciosamente, cada vez
  que alguien agrega un campo nuevo sin verificar el nombre real que el otro lado espera.
- **Generadores automáticos de tipos/schema pueden mentir:** una herramienta que genera
  tipos TypeScript/clases desde OpenAPI/protobuf/GraphQL SDL es tan buena como la
  configuración de serialización real del backend — si el generador no conoce la
  estrategia de naming real (ej. Jackson con `SNAKE_CASE` configurado globalmente), el
  contrato "generado automáticamente" queda **engañoso** y peor que no tener tipos, porque
  da falsa confianza. **Lección:** después de generar tipos automáticamente, verifica
  con una petición real (`curl`/Postman) que el JSON real coincide con el tipo generado,
  al menos para los casos con nombre explícito de request body (las respuestas GET suelen
  ser más confiables que los tipos de "petición con nombre" en generadores basados en
  reflexión de clases).
- **Un método que devuelve un mapa/diccionario dinámico** (`Map<String,Object>`,
  `dict`, objeto JS sin tipo) en vez de un tipo/DTO fuerte es una fuente doble de deriva:
  nadie documenta las claves reales, y el consumidor adivina el nombre de la propiedad.
  Documenta las claves explícitamente (comentario, schema, o mejor: usa un tipo real) y
  revisa ambos lados (productor y consumidor) en el mismo cambio.
- **Modelos "aspiracionales":** declarar un campo en el tipo del lado consumidor porque
  "debería" venir del backend, sin haberlo confirmado contra la respuesta real. Un campo
  que nunca llega hace que la UI se quede silenciosamente vacía (sin excepción, sin error
  de compilación) — el tipo compila, el dato nunca aparece.

### 5.6 — "Solo una activa a la vez" sin restricción real (condición de carrera / regla de unicidad)

Patrón real y recurrente: una regla de negocio de la forma "solo debe existir un X
activo a la vez para esta entidad" (una inscripción activa por alumno, una licencia sin
traslape de fechas, una configuración/esquema "base" por categoría) implementada
**solo** en lógica de aplicación (crear el nuevo, luego cerrar/desactivar el viejo, en
dos pasos separados) es vulnerable a:
- Un bug que crea el nuevo pero olvida cerrar el viejo (la causa real más común — no
  hace falta concurrencia para que pase, solo un camino de código incompleto).
- Concurrencia real: dos requests simultáneos que ambos pasan la verificación "no hay
  otro activo" antes de que cualquiera de los dos escriba.
- Reintentos de un job/script (un seed o script de carga que corre dos veces sin que
  nadie lo note, dejando duplicados con el mismo momento de creación).

**Mitigación real, no solo revisión de código:** agrega la restricción **en la base de
datos** (constraint único, índice único parcial/filtrado por condición "activo", o
constraint de exclusión con rango de fechas para el caso de traslapes) — así ningún
camino de código, presente o futuro, puede violar la regla, sin importar de dónde venga
el intento. La lógica de aplicación sigue siendo necesaria para dar un mensaje de error
amigable, pero la garantía real vive en el constraint.

**Cómo encontrar candidatos sin generar cientos de falsos positivos:** un query genérico
que busque "entidades con más de una fila relacionada activa" produce ruido masivo (la
mayoría de relaciones uno-a-muchos son normales — muchas tareas por grupo, muchos
horarios por profesor). El criterio real es semántico: ¿existe un campo/enum de estado
tipo "activo/vigente" y la regla de negocio dice explícitamente que solo uno debería
tener ese estado a la vez para la misma llave de negocio (mismo alumno, misma persona,
misma categoría)? Revisa candidatos a mano contra esa pregunta, no contra un conteo bruto.

### 5.7 — Validación estructural real vs. superficial

Una máscara de input (que solo permite ciertos caracteres mientras se escribe) **no es**
validación estructural completa. Un campo con formato oficial (identificador nacional,
código postal, número de cuenta, IBAN, etc.) necesita:
1. Sanear el juego de caracteres mientras se captura (UX, opcional).
2. **Validar la forma estructural completa antes de enviar** (longitud exacta, checksum
   si aplica, patrón completo) — esta es la que previene datos corruptos, y con
   frecuencia falta aunque la máscara sí exista.
3. Confirmar que el campo **realmente se envía** en el payload de guardado — un campo
   editable en el formulario pero omitido del payload de submit se descarta en silencio
   (bug real: un campo con contador de caracteres visible, validado en pantalla, pero
   nunca incluido en el objeto que se envía al guardar — el usuario ve "Guardado" y el
   cambio nunca ocurrió).

### 5.8 — Feedback de estado en operaciones mutantes/asíncronas

Independiente de framework: cualquier acción que dispare una operación de red/mutación
debe comunicar 3 estados al usuario — en progreso, éxito, error — y **deshabilitar
doble-envío** mientras está en progreso. La señal de "existe un flag de loading en la
clase" no basta; hay que verificar que ese flag está realmente conectado al control que
dispara la acción (ver método en el skill compañero, §2.2).

### 5.9 — Rate limiting / throttling: errores de diseño, no solo de número

Bug real: un rate limiter con "todo se repone de golpe cada N segundos exactos" en vez
de reposición continua/rolling produce ráfagas de error concentradas justo antes de cada
reset, aun cuando el promedio de tráfico esté bien por debajo del límite — el problema no
es el número, es el algoritmo de reposición (token bucket con refill continuo, o sliding
window, en vez de fixed window). Antes de "simplemente subir el límite", verifica si el
algoritmo de reposición es el problema real.

### 5.10 — Confirmación de operaciones destructivas

Independiente de superficie (web, CLI, móvil, bot): toda operación que borra, cancela,
revoca o de otra forma no es reversible fácilmente debe pedir confirmación explícita
**con el mecanismo estándar de la plataforma/framework** (modal de confirmación de la
librería de UI, prompt interactivo de CLI con `--force` explícito para saltarlo, doble
confirmación en bots) — un `confirm()`/`alert()` nativo del navegador, o un `print` +
`input()` ad-hoc en un script, funciona pero es visual/funcionalmente inconsistente con
el resto del sistema y señal de que el desarrollador tomó un atajo en vez de usar el
patrón establecido.

### 5.11 — Migraciones/DDL: constraints como defensa en profundidad

No confíes solo en la aplicación para invariantes de datos que "nunca deberían" violarse:
`NOT NULL`, `UNIQUE`, `CHECK`, `FOREIGN KEY`, constraints de exclusión (rangos de fecha
sin traslape) a nivel de base de datos son más baratos de mantener que re-verificar la
misma regla en cada punto de entrada de código, y son la única defensa real contra un
bug futuro que nadie anticipó (ver §5.6).

### 5.12 — Auditoría / logging

- Registra como mínimo: quién (identidad real, no un rol genérico ni el usuario técnico
  de la conexión a base de datos), qué operación, cuándo, y de ser posible el estado
  anterior y nuevo del recurso afectado.
- Un log de auditoría centralizado agnóstico de esquema (guardar el payload como
  JSON/JSONB) es más mantenible que una columna de auditoría por tabla de negocio.
- Si necesitas resistencia a manipulación, un ledger con hash encadenado
  (`hash_actual = hash(hash_anterior || datos)`) detecta alteración del propio log; pero
  eso **no** detecta si alguien modificó la tabla de negocio directamente sin pasar por
  la aplicación — para eso necesitas una reconciliación periódica que compare el estado
  vivo de la tabla contra la última entrada del log.
- Cuidado con el "usuario técnico de conexión" pisando al usuario real: si tu
  aplicación usa un pool de conexiones con un solo rol de base de datos, cualquier
  columna `created_by`/`updated_by` con `DEFAULT CURRENT_USER` a nivel de columna
  siempre va a grabar ese rol técnico, nunca al usuario real de la aplicación — la
  identidad real debe propagarse explícitamente (variable de sesión/contexto) y el
  trigger/hook de auditoría debe priorizar ese valor sobre el default de columna.

### 5.13 — Backups: la prueba de restauración es la que cuenta, no el log de "backup exitoso"

Un backup que nunca se restauró en un entorno aislado es una hipótesis, no una garantía.
Verifica periódicamente: restaurar en una instancia efímera separada, contar filas
clave, y si tienes un mecanismo de integridad (§5.12), verificarlo también post-restore.
Si tu política de retención hace `sync --delete` o equivalente antes de confirmar que la
subida nueva tuvo éxito, un fallo a medias puede borrar los backups previos junto con el
que falló — sube primero, confirma éxito, **solo entonces** borra lo viejo.

---

## 6. CI/CD y confiabilidad de pruebas — trampas comunes

- **`continue-on-error: true` (o equivalente) en un paso de pruebas convierte el gate en
  decorativo** — el pipeline se ve verde sin importar si algo falló. Antes de confiar en
  "las pruebas pasan en CI", confirma que un fallo real efectivamente rompe el build.
- **Entornos efímeros no replican dependencias con estado del entorno real** (sistemas
  de identidad tipo IdP con blueprints/configuración custom, servicios con licencias,
  bases de datos con extensiones específicas). Un pipeline que "funciona en local" puede
  fallar en 40 segundos en CI antes de ejecutar una sola prueba — verifica el pipeline
  completo de punta a punta al menos una vez, no asumas que un YAML sintácticamente
  válido implica un pipeline funcional.
- **Pruebas E2E "falso verde":** una prueba que espera estar autenticada pero en
  realidad recibe un redirect a login y lo interpreta como éxito no prueba nada real —
  solo confirma que un usuario sin sesión es enviado a la pantalla de login. Verifica
  que las aserciones realmente dependen de estar autenticado (ej. contenido específico
  del usuario logueado), no solo de la URL final.
- **Runner self-hosted con acceso a infraestructura real** es una decisión de seguridad
  explícita, no gratuita: cualquiera que pueda hacer push (o merge de un PR externo, si
  el repo es público) puede ejecutar código en esa máquina. Documenta el trade-off y
  quién lo autorizó; considera aislarlo (contenedor, usuario sin privilegios, red
  restringida) en vez de correrlo directo en el host de producción cuando sea posible.
- **Disciplina de "arreglar hasta que pase" sin subir timeouts a ciegas:** cuando una
  prueba falla intermitentemente, investiga la causa raíz de cada fallo antes de tocar
  el test. Categorías reales observadas: (a) bug real de la aplicación — corrígelo; (b)
  aserción/selector incorrecto en el test contra un comportamiento correcto de la app —
  corrige el test; (c) higiene de datos de prueba (valores fijos que solo funcionan la
  primera corrida, limpieza insuficiente entre pasos, condiciones de carrera del propio
  test) — corrige el test con datos generados dinámicamente y esperas explícitas de
  estado, no con un timeout más grande. Nunca marques `.skip()` un caso para que
  "desaparezca" sin documentar por qué.
- **Corre la suite completa varias veces seguidas antes de declarar estable**, no solo
  una vez — fallos intermitentes reales (condiciones de carrera, rate limiting activado
  por la propia suite, datos que colisionan entre corridas) solo aparecen con repetición.

---

## 7. Heurísticas cognitivas / UX (resumen — ver skill compañero para método completo)

Las 10 heurísticas de Nielsen aplican sin importar el framework de UI (web, móvil, CLI,
voz): visibilidad del estado del sistema, coincidencia entre el sistema y el mundo real
(terminología del dominio, no jerga técnica), control y libertad del usuario (deshacer/
cancelar), consistencia y estándares, prevención de errores, reconocimiento en vez de
recuerdo, flexibilidad y eficiencia de uso, diseño estético y minimalista, ayuda a
reconocer/diagnosticar/recuperarse de errores, y ayuda y documentación. El método
detallado para auditar esto de forma reproducible (qué se puede detectar con
grep/script objetivo vs. qué requiere muestreo manual con automatización de navegador/
UI) vive en `.agent/skills/universal-code-audit/SKILL.md` §4.

---

## 8. Disciplina de verificación — anti "declarado completo sin evidencia"

**La lección más cara de cualquier auditoría real:** un reporte que declara "N
controladores auditados, 100% corregido" o "COMPLETADO" es una afirmación sobre el
criterio que se probó *en ese momento* — no una garantía de ausencia de huecos. Se ha
observado, en más de una ronda de auditoría del mismo sistema, que una vulnerabilidad
declarada "cerrada" seguía presente porque la verificación posterior usó un criterio más
estricto o más amplio que el original (un endpoint con "algo de protección" no es lo
mismo que "protección con el scoping correcto"; un grep que no cubre todas las variantes
de nombre dice "cero hallazgos" sin que sea cierto).

**Reglas prácticas:**
- Antes de aceptar un "ya está corregido" de un reporte anterior (propio o ajeno),
  vuelve a correr la verificación tú mismo si el hallazgo es de alto impacto.
- Prefiere evidencia ejecutada (prueba real corrida, query contra datos reales, curl
  contra el endpoint real) sobre lectura de código como criterio para el nivel de
  confianza [Cierto].
- Cuando una auditoría "cierra" un patrón, documenta explícitamente el criterio/grep
  exacto usado — así la siguiente ronda puede ampliarlo en vez de repetirlo idéntico
  (y volver a fallar en encontrar la cola larga que el criterio original no cubría).
- Un cheque de tipo estático (type checker) que pasa **no** garantiza que un binding de
  UI/plantilla, un mock de prueba, o un contrato de red estén realmente correctos —
  varias clases de bug (bindings de evento con nombre incorrecto, contratos de
  serialización) son invisibles para el compilador/type-checker y solo aparecen en
  ejecución real o en el build/compilador específico de la capa de plantillas/UI.

---

## 9. Mapa rápido por stack — auditoría de dependencias y análisis estático

| Ecosistema | Audit de dependencias vulnerables | Análisis estático / lint | Nota de footgun común |
|---|---|---|---|
| Node.js / npm | `npm audit` (o `pnpm audit`, `yarn audit`) | ESLint + reglas de seguridad (`eslint-plugin-security`) | Prototype pollution; dependencias transitivas desactualizadas por años en proyectos viejos |
| Python | `pip-audit` o `safety check` | `bandit` (seguridad), `mypy`/`pyright` (tipos) | `pickle`/`eval`/`exec` sobre input externo; `subprocess` con `shell=True` |
| Java (Maven/Gradle) | OWASP `dependency-check` plugin, o `mvn versions:display-dependency-updates` | SpotBugs, PMD, SonarQube/SonarLint | Deserialización Java nativa; XXE en parsers XML por defecto sin hardening |
| Go | `govulncheck` | `go vet`, `staticcheck` | Goroutines sin cancelación de contexto; manejo de errores ignorado (`_ = err`) |
| Ruby | `bundler-audit` | RuboCop, `brakeman` (seguridad específico de Rails) | Mass assignment sin `strong_parameters`; `eval`/`send` dinámico con input de usuario |
| PHP | `composer audit` | PHPStan/Psalm | Inclusión de archivos dinámica (`include $_GET[...]`); deserialización insegura |
| .NET / C# | `dotnet list package --vulnerable` | Roslyn analyzers, SonarQube | Deserialización insegura de `BinaryFormatter`; XXE en `XmlDocument` por defecto en versiones viejas |
| Rust | `cargo audit` | `clippy` | `unsafe` sin justificación documentada; ciclos de `Rc` (ver §5.3) |

---

## 10. Checklist maestro pre-merge (condensado, genérico)

- [ ] Toda función expuesta nueva pasó el checklist de §2.
- [ ] Sin inyección de las clases de §3 en el código nuevo.
- [ ] Scoping multi-tenant verificado si el sistema es multi-tenant (§4).
- [ ] Sin N+1/fan-out nuevo en queries con agregación (§5.1).
- [ ] Escrituras nuevas envueltas correctamente en transacción/atomicidad (§5.2),
      verificado con una prueba real de persistencia, no solo lectura de código.
- [ ] Sin recursos (conexiones, listeners, subscripciones, archivos) que se abran sin un
      cierre garantizado incluso en la ruta de error (§5.3).
- [ ] Ningún `catch`/manejo de error nuevo oculta la causa real (§5.4).
- [ ] Cualquier contrato nuevo entre frontend/backend o entre servicios se verificó
      contra una respuesta/petición real, no solo contra el tipo generado (§5.5).
- [ ] Reglas "solo uno activo a la vez" nuevas tienen constraint real en base de datos,
      no solo lógica de aplicación (§5.6).
- [ ] Campos con formato oficial validados estructuralmente (no solo con máscara) y
      confirmado que se envían en el payload real (§5.7).
- [ ] Botones/acciones mutantes nuevas tienen feedback de estado conectado de verdad
      (§5.8), y las destructivas piden confirmación con el patrón estándar del proyecto
      (§5.10).
- [ ] Dependencias nuevas auditadas con la herramienta de §9 para el ecosistema
      correspondiente.
- [ ] `git status`/equivalente limpio de artefactos generados (reportes de cobertura,
      capturas de pruebas, directorios de build) antes de dar por cerrada la tarea.
- [ ] Ningún hallazgo de esta sesión se declara "corregido" sin el nivel de evidencia
      que corresponde a [Cierto] (§8).

---

## Procedencia

Este catálogo se destiló de incidentes reales (no hipotéticos) encontrados durante
auditorías profundas de un sistema de producción multi-tenant con datos sensibles de
menores de edad — cada patrón de bug listado en §5 ocurrió al menos una vez en ese
sistema real antes de generalizarse aquí. El objetivo de generalizarlo es que la próxima
vez que aparezca (en cualquier proyecto, en cualquier lenguaje) se reconozca por patrón
en vez de descubrirse otra vez desde cero.
