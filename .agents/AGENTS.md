# Reglas de Comportamiento del Agente (ADES)

- **Gestión de Espacio y Docker**: Debido a los límites de espacio actuales en el sistema, cada vez que se construyan, regeneren o reinicien contenedores (por ejemplo, al ejecutar `docker compose build` o levantar servicios), se DEBE ejecutar siempre el comando de limpieza profunda para eliminar contenedores, volúmenes, redes y objetos huérfanos que ya no se utilicen:
  `docker system prune -a --volumes -f`
  (O su equivalente seguro para liberar espacio).
