# Reporte ejecutivo — auditorías profundas 2026-07-04 → 2026-07-18

## Lo que hay que saber primero

El límite de peticiones por minuto que se agregó hoy mismo (como parte de cerrar otro
hallazgo de seguridad) es probablemente **demasiado estricto para uso real**: navegar unas
pocas pantallas seguidas, algo que cualquier persona del staff haría en cinco minutos
normales de trabajo, ya alcanza el límite y empieza a recibir errores. Esto no es una
sospecha — se reprodujo de forma controlada y repetible. No se tocó el número hoy porque
cambiarlo es una decisión de negocio (qué tanto abuso se tolera a cambio de no molestar a
usuarios legítimos), no algo que deba decidir unilateralmente quien corrige bugs. Alguien del
equipo necesita revisar esto con datos reales de tráfico antes de que el sistema se libere a
usuarios finales.

El resto de lo que se arregló hoy es, en comparación, buenas noticias.

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

- **El límite de peticiones por minuto necesita revisión — ver arriba, es lo más
  importante de este reporte.**
- **Seis pruebas de seguridad específicas (control de acceso a expedientes y certificados)
  siguen sin poder ejecutarse** — llevan tiempo con un error de configuración que hace que
  ni siquiera arranquen. Sin cambios desde el reporte anterior.
- **Una actualización de seguridad grande sigue pendiente a propósito** — requiere su
  propia sesión dedicada con plan de reversión, no debe mezclarse con otras correcciones.
- **Una revisión de usabilidad (qué tan fácil es usar el sistema en el día a día) sigue sin
  poder iniciarse** — el intento de delegarla a un proceso automático se interrumpió antes
  de producir resultados, dos veces.
- **Un análisis de seguridad de las librerías Java del backend principal sigue sin poder
  ejecutarse**, por una limitación del entorno (no del código de ADES) para descargar la
  base de datos de vulnerabilidades conocidas.

## Balance

Desde el primer reporte de hoy: el botón "Guardar" pasó de "diagnosticado, sin corregir" a
"corregido y confirmado con 7 pruebas reales". El bloque grande de pruebas automáticas pasó
de 2 de 23 pasando (con autenticación rota, sin que nadie lo supiera) a 14 de 23 pasando de
verdad. En el camino aparecieron 3 bugs reales de backend — 2 ya corregidos y verificados, 1
confirmado pero sin resolver por una limitación técnica del entorno de esta sesión, no del
sistema. Y apareció el hallazgo más importante de todo el día: el límite de peticiones que se
endureció esta misma sesión por motivos de seguridad puede estar, sin querer, poniendo un
techo demasiado bajo al uso normal del sistema.

Ningún hallazgo de este reporte se da por corregido sin haberlo probado contra el sistema
real corriendo — incluyendo el descubrimiento incómodo de que 23 pruebas de "seguridad"
llevaban tiempo sin probar nada de verdad. Vale la pena preguntarse qué más podría estar en
esa misma situación en otras partes del proyecto.
