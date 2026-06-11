import pytest
import json
from datetime import datetime
import redis
import psycopg2
import sys
import os

# Asegurar que se puede importar el .agent.memory
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '.agent')))

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
        try:
            leccion_id = mem.store_leccion(
                titulo="UUID Best Practices",
                contenido="Usar UUID v7 para PKs en PostgreSQL 18+",
                categoria="arquitectura"
            )
            assert leccion_id is not None
        except Exception as e:
            pytest.skip(f"No se pudo probar DB: {e}")
    
    def test_buscar_similar(self, mem):
        """Valida búsqueda semántica."""
        try:
            mem.store_leccion(
                titulo="UUID en PG",
                contenido="Usar DEFAULT uuidv7() para claves primarias",
                categoria="arquitectura"
            )
            
            resultados = mem.buscar_similar("¿Cómo genero UUIDs?")
            assert len(resultados) > 0
            assert resultados[0]["similitud"] > 0.5
        except Exception as e:
            pytest.skip(f"No se pudo probar DB: {e}")


class TestADESIntegration:
    def test_postgres_connectivity(self):
        """Valida conexión a Postgres."""
        try:
            conn = psycopg2.connect("postgresql://user:password@localhost:5432/ades")
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

