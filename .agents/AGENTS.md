# Reglas de Comportamiento del Agente (ADES)

- **Gestión de Espacio y Docker**: Debido a los límites de espacio actuales en el sistema, cada vez que se construyan, regeneren o reinicien contenedores (por ejemplo, al ejecutar `docker compose build` o levantar servicios), se DEBE ejecutar siempre el comando de limpieza profunda para eliminar contenedores, volúmenes, redes y objetos huérfanos que ya no se utilicen:
  `docker system prune -a --volumes -f`
  (O su equivalente seguro para liberar espacio).

- **Auditoría de Seguridad y Funcionalidad**: Siempre se deben aplicar las auditorías necesarias en la base de datos y en la lógica de negocio para garantizar el cumplimiento de una seguridad estricta y de la funcionalidad solicitada de forma impecable.
- **Autoexploración y Autoescaneo de Componentes**: Cada vez que se desarrolle o modifique un componente, este debe ser autoexplorado y autoescaneado para asegurar que sea 100% seguro con respecto a los estándares definidos y cumpla al 100% con la funcionalidad esperada.
- **Análisis Exploratorios**: Realizar análisis exploratorios profundos antes y durante el diseño o la implementación de cualquier cambio o componente nuevo para identificar posibles fallas, vulnerabilidades o inconsistencias lógicas de manera proactiva.
- **Documentación Completa del Código**: Todo el código desarrollado o modificado debe estar plenamente documentado (incluyendo comentarios en funciones críticas, clases, modelos, parámetros y cabeceras si aplica), asegurando su legibilidad y mantenibilidad a futuro.
- **Estandarización de Mensajes de Error**: Todo componente o servicio desarrollado/modificado debe asegurar que las excepciones del sistema, base de datos o de validación no se muestren de forma cruda ni técnica al usuario (evitando stacktraces de lenguajes o frameworks). Deben capturarse centralizadamente y transformarse en mensajes comprensibles, amigables y localizados.
- **Heurísticas Cognitivas de UX/UI (Nielsen)**: En todos los componentes Angular desarrollados o modificados, es obligatorio aplicar y validar el cumplimiento de las heurísticas cognitivas de Nielsen:
  - *Feedback visual inmediato*: Toda mutación de datos (`POST`, `PUT`, `PATCH`, `DELETE`) debe reflejar un estado de carga claro (`[loading]`, spinner o botón deshabilitado temporalmente).
  - *Confirmación en acciones destructivas*: Toda acción de eliminación o baja de registros debe requerir confirmación explícita con `ConfirmationService` de PrimeNG.
  - *Validación de datos sensibles*: Todo formulario debe validar la estructura de campos como CURP, RFC, NSS, teléfonos y coherencia de rangos numéricos/fechas antes de procesar el guardado.
- **Estándar Maestro de Auditoría Universal (`docs/security/ESTANDAR_MAESTRO_AUDITORIA_UNIVERSAL.md`)**:
  - *Cumplimiento Obligatorio*: En todo desarrollo, refactorización o auditoría, se DEBE consultar y aplicar el catálogo de seguridad, calidad de código y errores probables definido en [docs/security/ESTANDAR_MAESTRO_AUDITORIA_UNIVERSAL.md](file:///opt/ades/docs/security/ESTANDAR_MAESTRO_AUDITORIA_UNIVERSAL.md).
  - *Verificación de Autorización (BOLA/BFLA/OWASP/STRIDE)*: Todo endpoint o servicio expuesto debe validar la identidad criptográficamente, forzar el aislamiento por tenant (plantel) y denegar el acceso a recursos ajenos sin importar el nivel de rol.
  - *Evidencia Empírica de Verificación*: Ninguna tarea se considerará finalizada o "100% correcta" sin contar con evidencia empírica runtime ([Cierto]), habiendo ejecutado los comandos reales de compilación y pruebas (`tsc --noEmit`, `ng build`, `mvn test` o `docker compose build`).

