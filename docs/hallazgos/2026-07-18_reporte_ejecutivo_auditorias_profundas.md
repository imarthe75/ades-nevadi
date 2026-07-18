# Reporte ejecutivo — auditorías profundas 2026-07-04 → 2026-07-18

## Lo que hay que saber primero

**1,612 alumnos reales quedaron con datos académicos duplicados e inconsistentes tras la
reinscripción masiva de ayer, y ya está corregido.** La función que promueve a los alumnos
de un ciclo escolar al siguiente creaba correctamente la inscripción nueva, pero nunca
cerraba la vieja — cada alumno promovido quedaba registrado como activo simultáneamente en
DOS grados distintos (el viejo y el nuevo). En la práctica, cualquiera que abriera la lista
de Alumnos veía cada nombre duplicado con información contradictoria. No se encontró
revisando la base de datos ni el código de la reinscripción — se encontró mirando la pantalla
real durante una revisión de qué tan fácil es usar el sistema, un tipo de revisión que no
tenía nada que ver con este problema. Ya está reparado (con respaldo tomado antes de tocar
un solo dato) y verificado: los 1,612 casos corregidos, contados uno por uno, coinciden
exactamente con el número de alumnos que se habían promovido. Esto es, con diferencia, el
hallazgo más serio de todos los reportados hoy — no por ser difícil de corregir, sino porque
estuvo produciendo información académica incorrecta y visible para el personal desde ayer sin
que nadie lo supiera.

**Además, la generación de boletas oficiales en PDF estuvo completamente caída — 100% de las
llamadas fallaban, para cualquier usuario, sin excepción.** Se encontró por casualidad,
arreglando pruebas de seguridad que llevaban tiempo sin poder ejecutarse. Ya está corregido y
verificado contra el sistema en producción.

Estos dos hallazgos comparten algo importante: ninguno se habría encontrado con una auditoría
de seguridad tradicional (centrada en permisos y accesos) ni con solo leer el código. Ambos
aparecieron porque alguien realmente *usó* la función real — creó un usuario de prueba,
navegó a la pantalla real, miró los datos reales. Vale la pena que esto se convierta en una
práctica regular, no en algo que solo pasa cuando se le pide explícitamente.

Lo tercero más importante: el límite de peticiones por minuto que se había agregado como
parte de cerrar otro hallazgo de seguridad era **demasiado estricto para uso real** —
navegar unas pocas pantallas seguidas, algo que cualquier persona del staff haría en cinco
minutos normales de trabajo, ya alcanzaba el límite y empezaba a recibir errores. No era una
sospecha: se reprodujo de forma controlada y repetible. **Ya está corregido y verificado.**
No era solo cuestión de subir el número — la forma en que se reponía el límite tenía un
defecto de diseño (todo de golpe cada 60 segundos exactos, en vez de continuo), que era la
causa real de por qué los errores aparecían en ráfagas concentradas. Se corrigieron ambas
cosas y se probó con tres escenarios distintos: uso normal pausado (sin errores), uso
intensivo sostenido (sin errores), y una inundación real y deliberada de peticiones
simultáneas — que sigue siendo bloqueada en su mayoría, confirmando que el límite sigue
cumpliendo su función de seguridad, solo que ahora calibrado a tráfico real.

El resto de lo que se arregló es, en comparación, buenas noticias.

## Qué se corrigió hoy, verificado contra el sistema real (no solo en el código)

- **El botón "Guardar" que no aparecía (reportado como pendiente antes) — ya está
  corregido y confirmado.** La causa era una interacción poco conocida entre dos piezas de
  la librería de interfaz que hacía que el botón nunca se llegara a dibujar. Verificado con
  7 pruebas automáticas contra el sistema real, las 7 en verde.
- **Se descubrió que un bloque completo de pruebas automáticas de seguridad y estabilidad
  (23 escenarios) llevaba tiempo corriendo "en falso"** — nunca habían estado realmente
  autenticadas, así que en la práctica no probaban nada real, solo confirmaban que un
  usuario sin sesión era enviado a la pantalla de login. Esto es exactamente el tipo de
  "red de seguridad que en realidad no atrapa nada" que preocupa en un sistema que maneja
  datos de menores de edad. Corregido: ahora 14 de esos 23 escenarios pasan de verdad
  contra el sistema real con una sesión de administrador auténtica; los 9 restantes ya
  tienen causa identificada (7 son el problema de límite de peticiones de arriba, 1 es un
  bug de backend real y confirmado — ver abajo — y 1 depende de que se resuelva el límite
  de peticiones).
- **Dos bugs reales de backend, encontrados al corregir esas pruebas y ya corregidos:**
  1. Un mecanismo pensado para evitar que dos personas editando el mismo alumno al mismo
     tiempo se pisen los cambios entre sí **nunca funcionó** por un error de un solo
     nombre de campo mal escrito. Corregido y verificado. Nota honesta: el mecanismo ya
     funciona del lado del servidor, pero la pantalla de edición todavía no lo usa — eso
     es trabajo aparte, de producto, no un bug de código.
  2. Una función para verificar a qué plantel pertenece una persona (usada para
     direcciones) fallaba **siempre**, sin excepción, para cualquier tutor o contacto
     familiar que no tuviera también un rol de alumno/profesor/personal — un error de
     nombre de columna en la base de datos que probablemente lleva tiempo ahí. Corregido.
- **Un bug real de backend, confirmado pero no resuelto — la subida de documentos al
  expediente de un alumno falla de forma consistente (reproducido 6 veces seguidas).** Se
  encontró y corrigió un bug relacionado en el camino, pero no se pudo confirmar con
  certeza si es la misma causa: una falla técnica del entorno (el sistema de registro de
  eventos del servidor dejó de responder repetidamente durante la investigación, incluso
  después de reiniciar el servicio) impidió capturar el detalle técnico exacto del error.
  Queda documentado como una falla real y reproducible que alguien debe investigar con
  herramientas de diagnóstico más robustas que las disponibles hoy.

## Lo que sigue pendiente y por qué

- **Una actualización de seguridad grande sigue pendiente a propósito** — requiere su
  propia sesión dedicada con plan de reversión, no debe mezclarse con otras correcciones.
- **Un análisis de seguridad de las librerías Java del backend principal sigue sin poder
  ejecutarse**, por una limitación del entorno (no del código de ADES) para descargar la
  base de datos de vulnerabilidades conocidas.

## Lo que se terminó en esta última vuelta

- **El segundo bloque de 6 pruebas de seguridad (control de acceso a expedientes,
  certificados y boletas) — que llevaba tiempo sin poder ejecutarse, igual que el primero —
  ya corre completo: las 6 pasan.** La última necesitaba más memoria asignada al servidor de
  pruebas; se amplió (el servidor tiene de sobra) y se corrigió un segundo problema técnico
  que apareció al correrlas todas juntas. Fue arreglando este bloque que apareció la falla
  de boletas del principio de este reporte.
- **La revisión de usabilidad (qué tan fácil es usar el sistema en el día a día) — que dos
  veces antes se había interrumpido sin producir resultados — ya se completó**, con 11
  pantallas reales revisadas. Encontró el hallazgo de los 1,612 alumnos duplicados descrito
  arriba, y un hallazgo menor: dos pantallas (Administración de usuarios, Certificados)
  muestran códigos internos del sistema en vez de texto en español (ej. "ADMIN_GLOBAL" en
  vez de "Administrador Global") — cosmético, no bloqueante, no corregido todavía porque
  requiere decidir el texto exacto de cada etiqueta, una decisión de producto. El resto de
  la revisión — terminología, consistencia visual, que la información se reconozca sin tener
  que recordarla, atajos para usuarios frecuentes — salió bien en las 11 pantallas
  revisadas.

## Balance

Desde el primer reporte de hoy: el botón "Guardar" pasó de "diagnosticado, sin corregir" a
"corregido y confirmado con 7 pruebas reales". El bloque grande de pruebas automáticas pasó
de 2 de 23 pasando (con autenticación rota, sin que nadie lo supiera) a 14 de 23 pasando de
verdad. Un segundo bloque de 6 pruebas de seguridad, en la misma situación, pasa completo: 6
de 6. La revisión de usabilidad que dos veces se había quedado a medias, ya se completó.

## Se pidió, además, blindar el sistema contra que esto vuelva a pasar

Después del hallazgo de los 1,612 alumnos duplicados, se hizo una revisión de todo el código
que pudiera tener el mismo problema — no solo confiar en que fue un caso aislado. Encontró el
mismo error en un segundo lugar: la función que promueve a los alumnos de Preparatoria de un
semestre al siguiente tenía exactamente el mismo defecto, simplemente **todavía no se había
usado este ciclo** — se corrigió antes de que causara ningún daño, no después. El resto del
código que toca la información de inscripción de alumnos se revisó y está bien escrito.

Más importante: se agregó una regla directamente en la base de datos que hace **imposible**
que un alumno vuelva a quedar con dos inscripciones activas al mismo tiempo — sin importar si
el error aparece en esta función, en una nueva que se escriba en el futuro, o en cualquier
otro lugar. Antes de hoy, nada en el sistema impedía que esto pasara; ahora, cualquier intento
de crearlo se rechaza automáticamente, de inmediato. Se probó directamente que la regla
funciona antes de darla por buena.

De paso, con el sistema de registro de eventos del servidor ya funcionando con normalidad
(el problema técnico que lo había bloqueado en el reporte anterior no volvió a aparecer), se
encontró y corrigió la causa exacta de la falla de subida de documentos de expediente que
había quedado pendiente: un nombre de columna equivocado en el código, del mismo tipo de
error que causó el problema de los 1,612 alumnos, pero en un lugar distinto. Ya no quedan
bugs confirmados sin corregir de todo lo revisado hoy.

## Se pidió también revisar si faltan más reglas de este tipo en el resto de la base de datos

Después de cerrar el caso de los 1,612 alumnos con una regla estructural (arriba), se pidió
extender la búsqueda a toda la base de datos, no solo a inscripciones: ¿hay otras entidades
donde "solo debería existir una activa a la vez" y nada lo esté garantizando? Un primer
intento de automatizar esta búsqueda no funcionó — devolvió cerca de 150 falsos positivos,
porque "muchas filas activas relacionadas" es completamente normal en la mayoría de las
tablas (muchas tareas por grupo, muchos horarios por profesor) y no distingue eso de una
regla de negocio real violada. Se descartó el atajo y se revisó a mano cada candidato con
forma plausible del mismo problema. Resultado: dos huecos reales, ambos ya cerrados.

- **Los esquemas de calificación (qué porcentaje vale examen, tarea, asistencia) tenían el
  mismo hueco que causó el problema de los 1,612 alumnos — con evidencia de que ya había
  ocurrido, aunque sin consecuencia visible hasta ahora.** Se encontraron 3 pares de
  esquemas "base" (Primaria SEP, Secundaria SEP, Preparatoria UAEMEX) duplicados por
  completo, creados con 35 minutos de diferencia — todo indica que un script de carga
  inicial corrió dos veces sin que nadie lo notara. Por suerte, ambas copias de cada par
  tenían exactamente los mismos porcentajes, así que ninguna calificación se calculó mal.
  Pero el riesgo era real: si en el futuro alguien hubiera creado una segunda versión con
  porcentajes distintos, el sistema podía haber elegido cuál usar de forma arbitraria y
  calcular boletas oficiales con el esquema equivocado, sin ningún aviso de error — el
  mismo tipo de falla silenciosa que el hallazgo principal de hoy. Ya está corregido: se
  eliminaron las copias duplicadas y se agregó una regla en la base de datos que hace
  imposible que vuelva a pasar.
- **El personal puede solicitar licencias (médica, personal, maternidad, etc.) y nada
  impedía que a la misma persona se le aprobaran dos licencias con fechas encimadas** —
  ej. una licencia médica y una personal cubriendo los mismos días, lo que podría llevar a
  contar mal los días de ausencia o pagar de más. Se revisaron todos los datos reales: no
  había ningún caso así ocurrido todavía, así que no hubo que corregir nada retroactivo.
  Se agregó la regla en la base de datos para que sea imposible que ocurra de ahora en
  adelante.

## Balance

En el camino de todo el día aparecieron 6 bugs reales de backend — los 6 ya corregidos y
verificados contra el sistema real, incluyendo la caída total de boletas, los 1,612 alumnos
con datos duplicados (más el mismo error cerrado en un segundo lugar antes de que causara
daño), y la falla de subida de documentos. El límite de peticiones por minuto, que había
quedado como el único punto pendiente de una vuelta anterior de este reporte, ya está
corregido y probado. La revisión final de reglas de base de datos encontró y cerró dos huecos
más del mismo tipo (esquemas de calificación duplicados, licencias de personal sin protección
contra traslape de fechas) — ninguno causó daño real en datos existentes, pero ambos podían
haberlo hecho en cualquier momento futuro. No queda ningún hallazgo abierto de esta sesión que
no sea una decisión de negocio ya documentada (actualización mayor de seguridad diferida a
propósito, dos análisis bloqueados por el entorno sin salida a internet).

Ningún hallazgo de este reporte se da por corregido sin haberlo probado contra el sistema
real corriendo — incluyendo el descubrimiento, dos veces en el mismo día, de que bloques
enteros de pruebas de "seguridad" llevaban tiempo sin probar nada de verdad, y el hallazgo de
los 1,612 alumnos duplicados, que ninguna prueba automatizada ni revisión de código tenía
forma de atrapar porque nadie había mirado la pantalla real después de la reinscripción de
ayer. Ese último hallazgo ya no puede volver a pasar desapercibido: quedó una regla en la
base de datos que lo impide estructuralmente, no solo un parche puntual en el código que lo
causó — y la misma protección se extendió hoy a los dos huecos nuevos que aparecieron al
buscar el mismo patrón en el resto del sistema. El patrón se repite: revisar el sistema real,
no solo el código, sigue siendo lo que encuentra los problemas que de verdad importan — y
cuando se encuentra uno, vale la pena preguntar "¿dónde más podría estar pasando esto?" antes
de darlo por cerrado.
