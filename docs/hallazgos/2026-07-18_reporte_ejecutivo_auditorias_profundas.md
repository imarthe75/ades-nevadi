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

Lo tercero más importante: el límite de peticiones por minuto que se agregó ayer (como parte
de cerrar otro hallazgo de seguridad) es probablemente **demasiado estricto para uso real**:
navegar unas pocas pantallas seguidas, algo que cualquier persona del staff haría en cinco
minutos normales de trabajo, ya alcanza el límite y empieza a recibir errores. Esto no es una
sospecha — se reprodujo de forma controlada y repetible. No se tocó el número porque cambiarlo
es una decisión de negocio (qué tanto abuso se tolera a cambio de no molestar a usuarios
legítimos), no algo que deba decidir unilateralmente quien corrige bugs. Alguien del equipo
necesita revisar esto con datos reales de tráfico antes de liberar el sistema.

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

- **El límite de peticiones por minuto necesita revisión** — ver arriba.
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
de 6. La revisión de usabilidad que dos veces se había quedado a medias, ya se completó. En
el camino aparecieron 5 bugs reales de backend — 4 ya corregidos y verificados (incluyendo la
caída total de boletas y los 1,612 alumnos con datos duplicados), 1 confirmado pero sin
resolver por una limitación técnica del entorno de esta sesión, no del sistema. Y sigue en
pie el hallazgo más importante para decidir antes de liberar el sistema: el límite de
peticiones que se endureció esta misma sesión por motivos de seguridad puede estar, sin
querer, poniendo un techo demasiado bajo al uso normal del sistema.

Ningún hallazgo de este reporte se da por corregido sin haberlo probado contra el sistema
real corriendo — incluyendo el descubrimiento, dos veces en el mismo día, de que bloques
enteros de pruebas de "seguridad" llevaban tiempo sin probar nada de verdad, y el hallazgo
de los 1,612 alumnos duplicados, que ninguna prueba automatizada ni revisión de código
tenía forma de atrapar porque nadie había mirado la pantalla real después de la
reinscripción de ayer. El patrón se repite: revisar el sistema real, no solo el código,
sigue siendo lo que encuentra los problemas que de verdad importan.
