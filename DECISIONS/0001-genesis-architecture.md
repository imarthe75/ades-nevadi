# ADR 0001: Arquitectura de Memoria Soberana para el Agente Residente

## Estado
Aprobado ✅

## Contexto
Los agentes de IA modernos sufren de amnesia entre ejecuciones si no se cuenta con un gestor de estados persistente. Los servicios en la nube de VectorDBs o embeddings introducen latencia, costo y, lo más crítico, violan la soberanía de los datos del host local. 

Para resolver esto, integramos conceptos de:
- **ECC (Embedded Cognitive Control):** Para la autorregulación interna del agente.
- **Short-Term Memory (Valkey):** Para la velocidad en retención temporal del contexto y caché semántica.
- **Long-Term Memory (Postgres + pgvector):** Para la retención semántica y aprendizaje histórico local.

## Decisión
1. **Dockerización Local:** Todos los servicios de persistencia de datos (Valkey y Postgres) se instalan de manera local y con almacenamiento mapeado en el directorio `./data` del host.
2. **Caché Semántica (Valkey):** Antes de consumir tokens en llamadas a APIs de LLMs, el agente buscará en Valkey si existe una consulta conceptualmente similar (similitud semántica).
3. **Persistencia Semántica (pgvector):** Las lecciones del día y resúmenes de interacciones se traducirán a embeddings y se guardarán en PostgreSQL de forma local.

## Consecuencias
- **Positivas:** 100% de privacidad de datos, cero costos de almacenamiento en la nube, persistencia absoluta tras reinicios de contenedores (`docker compose down -v` conserva los datos locales).
- **Negativas:** El host debe proveer los recursos de RAM mínimos asignados (aproximadamente 1.5 GB dedicados a la base de datos y cache).
