# Reglas de Comportamiento del Agente (ADES)

- **Gestión de Espacio y Docker**: Debido a los límites de espacio actuales en el sistema, cada vez que se construyan, regeneren o reinicien contenedores (por ejemplo, al ejecutar `docker compose build` o levantar servicios), se DEBE ejecutar siempre el comando de limpieza profunda para eliminar contenedores, volúmenes, redes y objetos huérfanos que ya no se utilicen:
  `docker system prune -a --volumes -f`
  (O su equivalente seguro para liberar espacio).

- **Auditoría de Seguridad y Funcionalidad**: Siempre se deben aplicar las auditorías necesarias en la base de datos y en la lógica de negocio para garantizar el cumplimiento de una seguridad estricta y de la funcionalidad solicitada de forma impecable.
- **Autoexploración y Autoescaneo de Componentes**: Cada vez que se desarrolle o modifique un componente, este debe ser autoexplorado y autoescaneado para asegurar que sea 100% seguro con respecto a los estándares definidos y cumpla al 100% con la funcionalidad esperada.
- **Análisis Exploratorios**: Realizar análisis exploratorios profundos antes y durante el diseño o la implementación de cualquier cambio o componente nuevo para identificar posibles fallas, vulnerabilidades o inconsistencias lógicas de manera proactiva.
- **Documentación Completa del Código**: Todo el código desarrollado o modificado debe estar plenamente documentado (incluyendo comentarios en funciones críticas, clases, modelos, parámetros y cabeceras si aplica), asegurando su legibilidad y mantenibilidad a futuro.
