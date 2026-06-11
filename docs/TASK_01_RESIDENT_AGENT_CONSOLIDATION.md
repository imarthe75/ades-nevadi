# 🌌 TASK_01: Consolidación del Agente Residente
## Integración de ECC + OpenSpec + Superpowers para ADES

**Objetivo**: Regenerar `resident_agent_genesis.md` consolidando el framework existente con ECC (multi-agentes), OpenSpec (spec-first), Superpowers (TDD/rigor), memoria dual (Valkey + pgvector) y aplicarlo operativamente al proyecto ADES.

**Scope**: Resident Agent Framework + ADES (FastAPI + Angular 19+)  
**Duración Estimada**: 6-8 horas de ejecución  
**Output**: `resident_agent_genesis.md` v2.0 regenerado + system_prompt.md optimizado + memoria.sql para pgvector

---

## 📋 FASE 1: Análisis Contextual y Carga de Estado

### 1.1 Cargar y validar la arquitectura actual

```bash
# En el directorio raíz del proyecto ADES (o framework)

# 1. Verificar estructura existente
ls -la .agent/
# Esperado: AGENT.md, CONTEXT.md, MAP.md, RULES.md, STATE.md, HEURISTICS.md

# 2. Verificar docker-compose
docker compose config --resolve-image-digests

# 3. Verificar servicios activos
docker compose ps
# Esperado: postgres, valkey (redis), minio o seaweedfs

# 4. Probar conexión a Valkey
redis-cli -h localhost -p 6379 ping
# Esperado: PONG

# 5. Probar conexión a Postgres
psql postgresql://user:password@localhost:5432/ades -c "\dt ades_*"
```

**Decisión**: Si faltan servicios, levantarlos via docker-compose.yml antes de continuar.

### 1.2 Leer el estado y la heurística actual

**Archivos críticos a cargar**:
- `.agent/STATE.md` → Continuidad de sesión
- `.agent/HEURISTICS.md` → Decisiones locales
- `.agent/RULES.md` → Hooks y flujos
- `resident_agent_genesis.md` → Versión actual
- `README.md` (del framework) → Documentación existente

**Tarea**:
Extraer y validar:
1. Identidad del agente (AGENT.md)
2. Propósito del proyecto (CONTEXT.md)
3. Topología técnica (MAP.md)
4. Reglas activas (RULES.md)
5. Lecciones aprendidas en sesiones previas (STATE.md)

---

## 📐 FASE 2: Especificación Consolidada (OpenSpec)

### 2.1 Definir SPEC de consolidación

**Archivo a crear**: `.agent/CONSOLIDATION_SPEC.md`

```markdown
# Especificación: Consolidación del Agente Residente v2.0

## Requisitos Funcionales

1. **Integración ECC**
   - El agente actúa como orquestador de subagentes (Architect, Builder, QA, Reviewer)
   - Simulación de roles delegables para tareas complejas
   - Gestión explícita de responsabilidades

2. **Integración OpenSpec**
   - Todo cambio debe estar grounded en una SPEC
   - Specs incluyen: Requisitos, Constraints, Acceptance Criteria, Edge Cases
   - Specs = CONTRACT (no se desvía)

3. **Integración Superpowers**
   - Todos los cambios divididos en pasos atómicos
   - TDD obligatorio (tests primero cuando aplique)
   - Gate de verificación antes de marcar como "done"

4. **Memoria Dual Persistente**
   - Valkey (Semantic Cache): sesión, colas, estados rápidos
   - PostgreSQL + pgvector: embeddings, lecciones, decisiones arquitectónicas

5. **Heurística de Código**
   - Reducir dependencias externas
   - Mejorar autonomía local
   - Optimizar latencia y tokens
   - Graceful degradation

6. **Gobernanza ADES**
   - UUIDs v7 (PG 18+) en todas las PKs
   - Auditoría JSONB automática
   - Backward compatibility con ades_usuarios.rol_id

## Constraints

- No dependencias de VectorDBs externas
- Soberanía de datos local (docker volumes)
- Sistema must funcionar off-grid
- Max 2000 tokens por memoria Valkey (optimización)
- Backward compatible con versión anterior

## Acceptance Criteria

- [ ] resident_agent_genesis.md regenerado y documentado
- [ ] system_prompt.md integrado con .agent/*.md como fuente de verdad
- [ ] memoria.sql creado con schema pgvector
- [ ] .agent/STATE.md actualizado con próximos pasos
- [ ] Tests unitarios para funciones críticas
- [ ] Documentación de cómo integrar en proyectos nuevos
- [ ] Validación de que ADES puede instanciar agente

## Edge Cases

- Servicios Postgres/Valkey no disponibles → fallback a modo read-only
- pgvector extension no instalada → crear script de instalación
- Contexto memory overflow → implementar summarization automática
- Conflicto entre .agent/RULES y decisión del builder → RULES gana siempre
```

---

## 🏗️ FASE 3: Planificación Atómica (Superpowers)

### 3.1 Dividir consolidación en pasos atómicos

**Todos estos pasos son TESTABLES y VERIFICABLES**:

#### PASO 1: Schema de Memoria en PostgreSQL
**Responsabilidad**: Architect Agent + QA Agent
- [ ] Crear tabla `memoria.sesiones` (uuid PK, iniciada, cerrada, hash)
- [ ] Crear tabla `memoria.embeddings` (uuid PK, contenido, vector pgvector(1536), relevancia_score)
- [ ] Crear tabla `memoria.decisiones` (uuid PK, decision, heuristica_aplicada, contexto, timestamp)
- [ ] Crear triggers para auditoría automática
- [ ] **Test**: Validar inserciones, pgvector similarity search
- **Archivo**: `scripts/postgres_memoria_schema.sql`

#### PASO 2: Integración Valkey (Semantic Cache)
**Responsabilidad**: Builder Agent
- [ ] Crear clase `SemanticCache` (Python) que:
  - Conecte a Valkey localhost:6379
  - Implemente similitud coseno
  - Almacene queries + responses
  - TTL = 24h por defecto
- [ ] Tests unitarios de hit/miss
- **Archivo**: `memory/semantic_cache.py`

#### PASO 3: Integración pgvector (Long-term Memory)
**Responsabilidad**: Builder Agent
- [ ] Crear clase `LongTermMemory` (Python):
  - Conecte a Postgres
  - Inserte embeddings
  - Busque similarity
  - Persista lecciones
- [ ] Tests de inserción y búsqueda
- **Archivo**: `memory/long_term_memory.py`

#### PASO 4: System Prompt Integrado
**Responsabilidad**: Architect Agent
- [ ] Crear `system_prompt.md` que:
  - Lea `.agent/AGENT.md` como fuente de verdad
  - Implemente ciclo Bootstrap → Execution → Closure
  - Enforce ECC orchestration
  - Enforce OpenSpec discipline
  - Enforce Superpowers rigor
  - Enforce heurísticas locales
- **Archivo**: `.agent/system_prompt.md`

#### PASO 5: Regeneración de resident_agent_genesis.md
**Responsabilidad**: Architect Agent
- [ ] Documento maestro que incluya:
  - Visión consolidada (ECC + OpenSpec + Superpowers)
  - Arquitectura técnica (Valkey + pgvector + ADES)
  - Ciclo de vida (Bootstrap + Execution + Closure)
  - Gobernanza (UUID, auditoría, backward compatibility)
  - Heurística mandatoria (decisiones, patrones, fallbacks)
  - Integración con ADES (endpoints, schemas, modelos)
  - Guía de inicialización paso a paso
- **Archivo**: `resident_agent_genesis.md`

#### PASO 6: Actualización de .agent/HEURISTICS.md
**Responsabilidad**: Architect Agent + QA Agent
- [ ] Traducir heurísticas a reglas evaluables:
  - Preferencias de diseño
  - Patrones de fallback
  - Reglas de priorización
  - Límites de contexto
- **Archivo**: `.agent/HEURISTICS.md` (mejorado)

#### PASO 7: Integración con ADES (FastAPI)
**Responsabilidad**: Builder Agent
- [ ] Crear endpoint `POST /api/v1/agente/init` que:
  - Instancia el agente residente
  - Carga memoria (Valkey + pgvector)
  - Retorna status
- [ ] Crear modelo Pydantic `AgenteResidenteRequest/Response`
- [ ] Tests de integración
- **Archivo**: `ades/routers/agente.py`

#### PASO 8: Integración con ADES (Angular)
**Responsibilidad**: Builder Agent
- [ ] Crear servicio Angular `ResidentAgentService`
- [ ] Componentes para:
  - Memory Status (Valkey + pgvector)
  - Session History (desde STATE.md)
  - Heuristics Dashboard
- **Archivo**: `ades_frontend/src/app/services/resident-agent.service.ts`

#### PASO 9: Tests Integrales
**Responsabilidad**: QA Agent
- [ ] Test: Valkey connectivity
- [ ] Test: Postgres memoria schema
- [ ] Test: Semantic caching
- [ ] Test: Long-term memory embeddings
- [ ] Test: System prompt carga .agent/*
- [ ] Test: ADES API integration
- [ ] Test: Bootstrap cycle completo
- [ ] Test: Closure & summarization
- **Archivo**: `tests/test_resident_agent.py`

#### PASO 10: Documentación y Closure
**Responsibilidad**: Architect Agent
- [ ] README actualizado con: Consolidación, Memoria, Heurística, Integración ADES
- [ ] Guía de troubleshooting
- [ ] Ejemplos de uso
- [ ] `.agent/STATE.md` actualizado

---

## 🛠️ FASE 4: Ejecución por Paso Atómico

### PASO 1: Schema de Memoria en PostgreSQL

**Archivo a crear**: `scripts/postgres_memoria_schema.sql`

```sql
-- Crear schema
CREATE SCHEMA IF NOT EXISTS memoria;

-- Tabla de sesiones
CREATE TABLE IF NOT EXISTS memoria.sesiones (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    agente_id VARCHAR(255) NOT NULL,
    iniciada TIMESTAMPTZ DEFAULT now(),
    cerrada TIMESTAMPTZ,
    hash_contenido VARCHAR(64),
    metadata JSONB,
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now()
);

-- Tabla de embeddings (long-term memory)
CREATE TABLE IF NOT EXISTS memoria.embeddings (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    sesion_id UUID REFERENCES memoria.sesiones(id),
    tipo VARCHAR(50) NOT NULL, -- 'leccion', 'decision', 'patron'
    contenido TEXT NOT NULL,
    vector vector(1536), -- OpenAI embeddings
    relevancia_score FLOAT DEFAULT 0.5,
    metadata JSONB,
    created_at TIMESTAMPTZ DEFAULT now()
);

-- Tabla de decisiones (auditoría de decisiones arquitectónicas)
CREATE TABLE IF NOT EXISTS memoria.decisiones (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    sesion_id UUID REFERENCES memoria.sesiones(id),
    titulo VARCHAR(255) NOT NULL,
    descripcion TEXT,
    heuristica_aplicada VARCHAR(255),
    contexto JSONB,
    status VARCHAR(50) DEFAULT 'pendiente', -- pendiente, aprobada, rechazada
    created_at TIMESTAMPTZ DEFAULT now()
);

-- Índices para búsqueda rápida
CREATE INDEX idx_memoria_sesiones_agente ON memoria.sesiones(agente_id);
CREATE INDEX idx_memoria_embeddings_tipo ON memoria.embeddings(tipo);
CREATE INDEX idx_memoria_embeddings_vector ON memoria.embeddings USING ivfflat (vector vector_cosine_ops);
CREATE INDEX idx_memoria_decisiones_sesion ON memoria.decisiones(sesion_id);

-- Triggers de auditoría automática
CREATE OR REPLACE FUNCTION memoria_actualizar_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_sesiones_updated_at
BEFORE UPDATE ON memoria.sesiones
FOR EACH ROW
EXECUTE FUNCTION memoria_actualizar_timestamp();

-- Validación: pgvector debe estar instalado
CREATE EXTENSION IF NOT EXISTS vector;
```

**Verificación**:
```bash
psql postgresql://user:pass@localhost:5432/ades -f scripts/postgres_memoria_schema.sql

# Validar creación
psql postgresql://user:pass@localhost:5432/ades -c "\dt memoria.*"
```

---

### PASO 2: Semantic Cache (Valkey)

**Archivo a crear**: `memory/semantic_cache.py`

```python
import json
import hashlib
from typing import Optional, Dict, Any
import redis
from sentence_transformers import SentenceTransformer
import numpy as np

class SemanticCache:
    """
    Capa de cache semántico sobre Valkey (Redis).
    Evita llamadas redundantes a LLMs comparando similitud coseno.
    """
    
    def __init__(self, host: str = "localhost", port: int = 6379, ttl_seconds: int = 86400):
        self.redis_client = redis.Redis(host=host, port=port, decode_responses=True)
        self.model = SentenceTransformer("all-MiniLM-L6-v2")  # Lightweight
        self.ttl_seconds = ttl_seconds
        self.prefix = "semantic_cache:"
        self.threshold = 0.85  # Similitud mínima para considerar un hit
        
    def _get_embedding(self, text: str) -> np.ndarray:
        """Calcula embedding de texto."""
        return self.model.encode(text, convert_to_numpy=True)
    
    def _cosine_similarity(self, vec1: np.ndarray, vec2: np.ndarray) -> float:
        """Calcula similitud coseno entre dos vectores."""
        return float(np.dot(vec1, vec2) / (np.linalg.norm(vec1) * np.linalg.norm(vec2)))
    
    def get(self, query: str) -> Optional[Dict[str, Any]]:
        """
        Busca si una query similar ya fue respondida.
        Retorna (response, similarity_score) o None.
        """
        try:
            query_embedding = self._get_embedding(query)
            
            # Buscar todas las claves de cache
            keys = self.redis_client.keys(f"{self.prefix}*")
            
            best_match = None
            best_score = 0
            
            for key in keys:
                cached = json.loads(self.redis_client.get(key))
                cached_embedding = np.array(cached["embedding"])
                score = self._cosine_similarity(query_embedding, cached_embedding)
                
                if score > best_score:
                    best_score = score
                    best_match = cached
            
            # Retornar si supera threshold
            if best_score >= self.threshold and best_match:
                return {
                    "response": best_match["response"],
                    "similarity_score": best_score,
                    "cached_at": best_match["cached_at"]
                }
            
            return None
            
        except Exception as e:
            print(f"[SemanticCache] Error in get(): {e}")
            return None
    
    def set(self, query: str, response: str, metadata: Optional[Dict] = None) -> bool:
        """Almacena un query+response en el cache semántico."""
        try:
            query_embedding = self._get_embedding(query).tolist()
            key = f"{self.prefix}{hashlib.sha256(query.encode()).hexdigest()}"
            
            data = {
                "query": query,
                "response": response,
                "embedding": query_embedding,
                "metadata": metadata or {},
                "cached_at": str(datetime.datetime.now())
            }
            
            self.redis_client.setex(
                key,
                self.ttl_seconds,
                json.dumps(data)
            )
            return True
            
        except Exception as e:
            print(f"[SemanticCache] Error in set(): {e}")
            return False
    
    def flush(self) -> None:
        """Limpia todo el cache semántico."""
        keys = self.redis_client.keys(f"{self.prefix}*")
        if keys:
            self.redis_client.delete(*keys)


# Test
if __name__ == "__main__":
    cache = SemanticCache()
    
    # Primer query
    q1 = "¿Cuáles son las mejores prácticas de UUID en PostgreSQL?"
    r1 = "Usar UUID v7 para orden temporal, gen_random_uuid() para aleatoriedad..."
    cache.set(q1, r1)
    
    # Query similar (debe matchear)
    q2 = "¿Cómo genero UUIDs en PostgreSQL?"
    result = cache.get(q2)
    
    if result:
        print(f"HIT: {result['similarity_score']:.2f}")
        print(f"Response: {result['response'][:50]}...")
    else:
        print("MISS (no similar enough)")
```

**Verificación**:
```bash
pip install sentence-transformers redis

python -m pytest memory/test_semantic_cache.py -v
```

---

### PASO 3: Long-term Memory (pgvector)

**Archivo a crear**: `memory/long_term_memory.py`

```python
import json
import hashlib
from datetime import datetime
from typing import List, Dict, Any, Optional
import psycopg2
from psycopg2.extras import RealDictCursor
from sentence_transformers import SentenceTransformer
import numpy as np

class LongTermMemory:
    """
    Memoria a largo plazo basada en embeddings en PostgreSQL.
    Persiste lecciones, decisiones y patrones arquitectónicos.
    """
    
    def __init__(self, dsn: str = "postgresql://user:password@localhost:5432/ades"):
        self.dsn = dsn
        self.model = SentenceTransformer("all-MiniLM-L6-v2")
        
    def _connect(self):
        """Conecta a PostgreSQL."""
        return psycopg2.connect(self.dsn)
    
    def _get_embedding(self, text: str) -> List[float]:
        """Calcula embedding usando SentenceTransformer."""
        return self.model.encode(text, convert_to_numpy=True).tolist()
    
    def store_leccion(
        self, 
        titulo: str, 
        contenido: str, 
        categoria: str = "general",
        metadata: Optional[Dict] = None
    ) -> Optional[str]:
        """
        Almacena una lección aprendida en memoria a largo plazo.
        
        Args:
            titulo: Título corto de la lección
            contenido: Descripción detallada
            categoria: Tipo (e.g., 'arquitectura', 'seguridad', 'performance')
            metadata: Información adicional (JSON)
        
        Returns:
            embedding_id (UUID) o None si falla
        """
        try:
            conn = self._connect()
            cur = conn.cursor()
            
            embedding = self._get_embedding(contenido)
            
            sql = """
            INSERT INTO memoria.embeddings 
            (tipo, contenido, vector, metadata, relevancia_score)
            VALUES (%s, %s, %s, %s, %s)
            RETURNING id;
            """
            
            meta = {
                "titulo": titulo,
                "categoria": categoria,
                **(metadata or {})
            }
            
            cur.execute(sql, (
                "leccion",
                contenido,
                embedding,  # pgvector lo convierte automáticamente
                json.dumps(meta),
                0.7
            ))
            
            embedding_id = cur.fetchone()[0]
            conn.commit()
            return embedding_id
            
        except Exception as e:
            print(f"[LongTermMemory] Error storing lesson: {e}")
            return None
        finally:
            if cur:
                cur.close()
            if conn:
                conn.close()
    
    def buscar_similar(
        self, 
        query: str, 
        tipo: str = "leccion",
        limit: int = 5
    ) -> List[Dict[str, Any]]:
        """
        Busca embeddings similares usando similitud coseno.
        
        Args:
            query: Texto de búsqueda
            tipo: Filtro por tipo
            limit: Máximo de resultados
        
        Returns:
            Lista de embeddings similares con scores
        """
        try:
            conn = self._connect()
            cur = conn.cursor(cursor_factory=RealDictCursor)
            
            query_embedding = self._get_embedding(query)
            
            sql = """
            SELECT 
                id,
                contenido,
                metadata,
                1 - (vector <=> %s::vector) AS similitud
            FROM memoria.embeddings
            WHERE tipo = %s
            ORDER BY vector <=> %s::vector
            LIMIT %s;
            """
            
            cur.execute(sql, (query_embedding, tipo, query_embedding, limit))
            resultados = cur.fetchall()
            
            return [dict(row) for row in resultados]
            
        except Exception as e:
            print(f"[LongTermMemory] Error searching: {e}")
            return []
        finally:
            if cur:
                cur.close()
            if conn:
                conn.close()
    
    def store_decision(
        self,
        titulo: str,
        descripcion: str,
        heuristica: str,
        contexto: Dict
    ) -> Optional[str]:
        """Registra una decisión arquitectónica con heurística."""
        try:
            conn = self._connect()
            cur = conn.cursor()
            
            sql = """
            INSERT INTO memoria.decisiones 
            (titulo, descripcion, heuristica_aplicada, contexto)
            VALUES (%s, %s, %s, %s)
            RETURNING id;
            """
            
            cur.execute(sql, (
                titulo,
                descripcion,
                heuristica,
                json.dumps(contexto)
            ))
            
            decision_id = cur.fetchone()[0]
            conn.commit()
            return decision_id
            
        except Exception as e:
            print(f"[LongTermMemory] Error storing decision: {e}")
            return None
        finally:
            if cur:
                cur.close()
            if conn:
                conn.close()


# Test
if __name__ == "__main__":
    mem = LongTermMemory()
    
    # Almacenar lección
    leccion_id = mem.store_leccion(
        titulo="UUID v7 en PostgreSQL",
        contenido="Usar uuidv7() para claves primarias por orden temporal",
        categoria="arquitectura",
        metadata={"author": "architect_agent", "version": "1.0"}
    )
    
    print(f"Lección almacenada: {leccion_id}")
    
    # Buscar similar
    similares = mem.buscar_similar("¿Cómo generar IDs en PostgreSQL?")
    for sim in similares:
        print(f"  {sim['contenido'][:50]}... (similitud: {sim['similitud']:.2f})")
```

---

### PASO 4: System Prompt Integrado

**Archivo a crear**: `.agent/system_prompt.md`

```markdown
# System Prompt v2.0: Resident Agent (ECC + OpenSpec + Superpowers)

You are a Resident Agent instantiated inside the Resident Agent Framework operating over ADES.

You combine:
- **ECC** (agent ecosystem orchestration, subagentes, delegación)
- **OpenSpec** (spec-first development, contratos, trazabilidad)
- **Superpowers** (TDD, atomic steps, strict verification)
- **Memoria Dual** (Valkey para sesión, pgvector para aprendizaje)

Your primary goal: Produce correct, verifiable, incremental, and well-documented software for ADES.

---

## PRIMARY SOURCE OF TRUTH (MANDATORY)

Before ANY action, you MUST load and internalize:

- `.agent/AGENT.md` → Identity and behavioral laws
- `.agent/CONTEXT.md` → Project purpose (ADES)
- `.agent/MAP.md` → System topology
- `.agent/RULES.md` → Execution flow
- `.agent/HEURISTICS.md` → Decision-making logic (CRITICAL)
- `.agent/STATE.md` → Session continuity

These files OVERRIDE all assumptions.

---

## COGNITIVE EXECUTION MODEL

You operate in 3 explicit phases:

### 1. BOOTSTRAP (Initialization)
- Load `.agent/STATE.md`
- Validate pending tasks
- **Load HEURISTICS.md before planning**
- Query Valkey + pgvector for relevant context
- Verify Postgres + Valkey connectivity

### 2. EXECUTION LOOP (Core Work)
1. Define or refine SPEC (OpenSpec)
2. Break into atomic steps (Superpowers)
3. Apply heuristics to prioritize approach
4. Execute step-by-step:
   - Tests first (TDD)
   - Validation
   - Traceability
5. Store intermediate results in memory

### 3. CLOSURE (Mandatory Shutdown)
- Summarize session work
- Update `.agent/STATE.md`
- Extract lessons → LongTermMemory (pgvector)
- Persist new heuristics if discovered
- Record decisions in `memoria.decisiones`

**NEVER skip phase 3.**

---

## SPEC DISCIPLINE (OpenSpec)

Every task must have an explicit SPEC:

- Requirements
- Constraints
- Acceptance criteria
- Edge cases

If SPEC missing → generate it BEFORE coding.

**SPEC = CONTRACT**
Violation is NOT allowed.

---

## ENGINEERING DISCIPLINE (Superpowers)

- Always divide into atomic, testable steps
- TDD: write tests BEFORE implementation
- No "done" without verification:
  - ✔ Tests pass
  - ✔ Spec satisfied
  - ✔ No regressions

---

## HEURISTICS (CRITICAL)

HEURISTICS.md is EXECUTABLE THINKING, not documentation.

You MUST:

- Apply heuristics BEFORE making decisions
- Prefer solutions that:
  - Reduce external dependencies
  - Improve local autonomy (ADES)
  - Optimize latency + token usage
  - Enable graceful degradation
  - Respect backward compatibility (ades_usuarios.rol_id)

If heuristic conflicts with naive solution → **FOLLOW heuristic**.

---

## MEMORY SYSTEM

**Short-term (Valkey)**:
- Session state
- Semantic cache
- Fast retrieval

**Long-term (pgvector)**:
- Lessons learned
- Architecture decisions
- Patterns + reusable code

**Rules**:
- Query memory BEFORE solving
- Store learnings AFTER solving
- Avoid recomputation

---

## DATABASE GOVERNANCE (STRICT)

You MUST enforce on ADES:

- **UUID v7 primary keys ONLY** (PG 18+)
- No integer-based PKs
- All models inherit `AuditMixin`
- All PATCH endpoints require `row_version`
- Backward compatible: retain `ades_usuarios.rol_id`

Violations are NOT allowed.

---

## AGENT ORCHESTRATION (ECC)

You may simulate subagents:

- **Architect** → specs, system design, governance
- **Builder** → implementation, code quality
- **QA** → testing, verification, edge cases
- **Reviewer** → code review, validation

Rules:
- Delegate for complexity
- Keep outputs structured
- Reconcile results before continuing

---

## OUTPUT STRUCTURE

Always respond with:

1. Context loaded (files + memory)
2. Spec (created or refined)
3. Plan (atomic steps)
4. Execution (per step)
5. Tests (if applicable)
6. Verification results
7. Memory updates
8. STATE.md update suggestion

---

## FAILURE HANDLING

If:
- Spec unclear → refine it
- Tests fail → debug before proceeding
- Context missing → retrieve or reconstruct
- Valkey/Postgres down → fallback to file-based state

**NEVER**:
- Guess silently
- Skip heuristics
- Skip memory lookup
- Mark as "done" without verification

---

## GLOBAL RULE

You are a persistent agent, not stateless.

- Think long-term
- Store knowledge
- Improve over time
- Prefer deterministic, reproducible behavior

You are an Agent OS for ADES.
```

---

### PASO 5: Regeneración de resident_agent_genesis.md

**Archivo a crear**: `resident_agent_genesis.md` (v2.0)

Ver **ARCHIVO SEPARADO** abajo.

---

## 📊 FASE 5: Tests e Integración

### Test Suite Completo

**Archivo a crear**: `tests/test_resident_agent.py`

```python
import pytest
import json
from datetime import datetime
import redis
import psycopg2
from memory.semantic_cache import SemanticCache
from memory.long_term_memory import LongTermMemory

class TestSemanticCache:
    @pytest.fixture
    def cache(self):
        return SemanticCache(host="localhost", port=6379)
    
    def test_cache_hit(self, cache):
        """Valida que queries similares reutilicen respuestas."""
        query1 = "¿Cómo uso UUID v7?"
        response1 = "Usa DEFAULT uuidv7() en PostgreSQL 18+"
        
        cache.set(query1, response1)
        
        query2 = "UUID v7 en PostgreSQL"
        result = cache.get(query2)
        
        assert result is not None
        assert result["similarity_score"] >= 0.85
        assert response1 in result["response"]
    
    def test_cache_miss(self, cache):
        """Valida que queries muy diferentes no matcheen."""
        query1 = "¿Cómo uso UUID v7?"
        response1 = "Usa DEFAULT uuidv7()..."
        cache.set(query1, response1)
        
        query2 = "¿Cuál es la capital de Francia?"
        result = cache.get(query2)
        
        assert result is None or result["similarity_score"] < 0.85


class TestLongTermMemory:
    @pytest.fixture
    def mem(self):
        return LongTermMemory()
    
    def test_store_leccion(self, mem):
        """Valida almacenamiento de lecciones."""
        leccion_id = mem.store_leccion(
            titulo="UUID Best Practices",
            contenido="Usar UUID v7 para PKs en PostgreSQL 18+",
            categoria="arquitectura"
        )
        
        assert leccion_id is not None
    
    def test_buscar_similar(self, mem):
        """Valida búsqueda semántica."""
        mem.store_leccion(
            titulo="UUID en PG",
            contenido="Usar DEFAULT uuidv7() para claves primarias",
            categoria="arquitectura"
        )
        
        resultados = mem.buscar_similar("¿Cómo genero UUIDs?")
        assert len(resultados) > 0
        assert resultados[0]["similitud"] > 0.5


class TestADESIntegration:
    def test_postgres_connectivity(self):
        """Valida conexión a Postgres."""
        try:
            conn = psycopg2.connect("postgresql://user:pass@localhost:5432/ades")
            cur = conn.cursor()
            cur.execute("SELECT 1")
            assert cur.fetchone()[0] == 1
            conn.close()
        except Exception as e:
            pytest.skip(f"Postgres no disponible: {e}")
    
    def test_valkey_connectivity(self):
        """Valida conexión a Valkey."""
        try:
            r = redis.Redis(host="localhost", port=6379)
            assert r.ping()
        except Exception as e:
            pytest.skip(f"Valkey no disponible: {e}")


# Ejecutar: pytest tests/test_resident_agent.py -v
```

---

## 🔄 FASE 6: Documentación Final

### README.md Actualizado

```markdown
# Resident Agent Framework v2.0 (ECC + OpenSpec + Superpowers)

Agente residente con soberanía de datos local, memoria dual (Valkey + pgvector) e integración con ADES.

## Quick Start

```bash
# 1. Levanta infraestructura
docker compose up -d

# 2. Instala dependencias
pip install -r requirements.txt

# 3. Inicializa schema de memoria
psql postgresql://user:pass@localhost/ades -f scripts/postgres_memoria_schema.sql

# 4. Tests
pytest tests/ -v

# 5. Instancia el agente
from memory.semantic_cache import SemanticCache
from memory.long_term_memory import LongTermMemory

cache = SemanticCache()
mem = LongTermMemory()
```

## Arquitectura

- **ECC**: Orquestación de subagentes
- **OpenSpec**: Spec-first development
- **Superpowers**: TDD + atomic steps
- **Valkey**: Caché semántico + sesión
- **pgvector**: Embeddings + aprendizaje
- **ADES**: Integración FastAPI + Angular

## Integración ADES

Ver `.agent/INTEGRATION_ADES.md`

---

```

---

## ✅ FASE 7: Checklist de Cierre

- [ ] `postgres_memoria_schema.sql` creado y validado
- [ ] `memory/semantic_cache.py` implementado + tests
- [ ] `memory/long_term_memory.py` implementado + tests
- [ ] `.agent/system_prompt.md` regenerado
- [ ] `resident_agent_genesis.md` v2.0 regenerado
- [ ] `tests/test_resident_agent.py` all passing
- [ ] `.agent/STATE.md` actualizado con próximos pasos
- [ ] `README.md` y documentación coherente
- [ ] ADES integration endpoints funcionales
- [ ] Valkey + Postgres schema validado

---

## 📝 Notas

- Todos los scripts SQL incluyen UUID v7 (PG 18+)
- Backward compatibility con ADES existente garantizada
- Memoria off-grid: sin dependencias externas
- Sistema testeable y verificable en cada paso

**Responsable**: Architect + Builder + QA Agents  
**Timeline**: 6-8 horas de ejecución  
**Status**: LISTO PARA EJECUTAR EN CLAUDE CODE
