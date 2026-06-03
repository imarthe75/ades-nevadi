# 🌌 Resident Agent Framework: Master Blueprint (MIT License)

Este repositorio contiene la arquitectura de referencia, la infraestructura base y la lógica de control cognitivo para inicializar un **Agente Residente** con soberanía de datos local, memoria de corto plazo (**Valkey**) y memoria de largo plazo (PostgreSQL + pgvector).

> [!NOTE]
> **Atribución y Licenciamiento:** Este proyecto se inspira fuertemente e incorpora las mejores prácticas, estructuras de subagentes, hooks y patrones de arnés del proyecto de código abierto [affaan-m/ECC](https://github.com/affaan-m/ECC), el cual se distribuye bajo la Licencia MIT. Agradecemos a los contribuidores de ECC por sentar las bases del desarrollo orientado a arneses nativos de IA.

---

## 🛠️ Requisitos del Host
- Docker & Docker Compose
- Python 3.10+
- `redis` (librería cliente para interactuar con Valkey), `psycopg2-binary`, `numpy`

---

## 🚀 Guía de Inicialización (Paso a Paso)

Para incorporar este agente a cualquier proyecto nuevo del ecosistema, sigue estos pasos:

### 1. Clonar e Inicializar la Estructura Cognitiva y Heurística (ECC Harness)
La heurística y la inteligencia artificial son partes fundamentales del ciclo de vida de todo proyecto generado a partir de este framework. Para inicializar un nuevo proyecto, ejecuta el comando de arnés o copia la estructura `.agent/` de forma mandatoria:
- `.agent/AGENT.md` (Las Leyes de tu Agente)
- `.agent/CONTEXT.md` (El propósito de tu proyecto)
- `.agent/MAP.md` (El mapa de puertos y directorios)
- `.agent/STATE.md` (La bitácora de sesión)
- `.agent/RULES.md` (Las reglas de flujo y hooks de sistema de ECC)
- `.agent/HEURISTICS.md` (La heurística cognitivo-aplicada y guías de decisión de la IA)
- `resident_agent_genesis.md` (Manual Maestro de Operaciones en la raíz del proyecto)

> [!IMPORTANT]
> **Heurística Mandatoria (Guía + Código):** 
> Todo proyecto derivado debe:
> 1. Configurar al arnés del agente para leer e incorporar `HEURISTICS.md` antes de tomar cualquier decisión de diseño o modificación.
> 2. **Implementar heurística dentro de la lógica del desarrollo o aplicación** (ej. algoritmos heurísticos para procesamiento rápido de datos, sistemas de fallback locales y priorización autorregulada de tareas sin depender exclusivamente de llamadas de IA externas).

### 2. Levantar la Infraestructura Soberana de Memoria (Valkey + Postgres)
Copia el archivo `docker-compose.yml` en la raíz de tu proyecto y levanta las bases de datos locales:
```bash
docker compose up -d
```
> [!IMPORTANT]
> Esto creará las carpetas `./data/valkey` y `./data/postgres` en tu host. Los datos persistirán de forma segura incluso si eliminas los contenedores. Valkey reemplaza por completo a Redis para toda la gestión de caché semántica y estados de sesión de corto plazo.

### 3. Ejecutar el Rito de Inicio (Bootstrapping)
Al iniciar tu sesión de desarrollo, tu agente debe leer `.agent/STATE.md` y verificar las reglas en `.agent/RULES.md` para sincronizarse con los pendientes de la sesión anterior.

### 4. Integrar la Memoria Corta (Caché Semántica en Valkey)
Utiliza la clase `SemanticCache` para evitar llamadas redundantes a las APIs de modelos de lenguaje, comparando la similitud semántica de la consulta:
```python
from memory.semantic_cache import SemanticCache

# Conexión directa a Valkey
cache = SemanticCache(host="localhost", port=6379)
# Busca si la consulta conceptualmente ya fue respondida en Valkey
response = cache.get(query_embedding)
if not response:
    # Generar respuesta de LLM y guardarla
    cache.set(query_text, query_embedding, response_text)
```

### 5. Integrar la Memoria de Largo Plazo (Embeddings en pgvector)
Almacena resúmenes y lecciones de forma persistente en PostgreSQL:
```python
from memory.long_term_memory import LongTermMemory

db_memory = LongTermMemory()
db_memory.store_lesson("Lección aprendida: Usar UUID v7 para IDs", embedding_vector)
```

### 6. Ejecutar el Rito de Cierre e Higiene Cognitiva (ECC)
Al terminar tu sesión, ejecuta el proceso de control cognitivo:
- **Summarization**: Condensa la actividad y lecciones.
- **Bitácora**: Actualiza `.agent/STATE.md` documentando los cambios y los siguientes pasos.
- **Persistencia**: Almacena las lecciones de la sesión en la base de datos local mediante embeddings.

---

## 🗄️ 7. Gobernanza de Bases de Datos & Estilo SQL

El framework incorpora un sistema estricto de gobernanza de base de datos detallado en las [Database Style Guidelines](file:///home/ia/ecosistema-casmarts/resident-agent-framework/rules/common/database-style.md).

### 🛠️ Estructura de Auditoría (auditoria.sql)
Para habilitar el rastreo transaccional de DML (INSERT/UPDATE/DELETE), se provee el script modular [auditoria.sql](file:///home/ia/ecosistema-casmarts/resident-agent-framework/auditoria.sql), el cual:
- Crea el esquema central `auditoria` y su correspondiente `log_auditoria` con almacenamiento en formato JSONB.
- Registra el trigger `auditoria_biu` para automatizar fechas (`fccreacion`, `fcmodificacion`), control de concurrencia optimista (`row_version`), y claves estables (`ref` usando UUIDv7 con fallback a `gen_random_uuid()`).
- Registra el trigger `auditoria_aiud` para loguear deltas con hashes de verificación de integridad (md5).

### 🕵️ Automatización con el Skill de Auditoría
El framework incluye el skill [postgres-audit-setup](file:///home/ia/ecosistema-casmarts/resident-agent-framework/skills/postgres-audit/SKILL.md) para guiar al agente de forma interactiva en la creación de las tablas, validación de esquemas y asignación idempotente de triggers en cualquier proyecto derivado.

