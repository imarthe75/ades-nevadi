import json
import hashlib
import datetime
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
