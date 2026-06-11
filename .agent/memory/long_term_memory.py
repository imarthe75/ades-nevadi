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
            if 'cur' in locals() and cur:
                cur.close()
            if 'conn' in locals() and conn:
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
            if 'cur' in locals() and cur:
                cur.close()
            if 'conn' in locals() and conn:
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
            if 'cur' in locals() and cur:
                cur.close()
            if 'conn' in locals() and conn:
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
