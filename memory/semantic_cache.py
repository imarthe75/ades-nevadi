import json
import redis
import numpy as np

class SemanticCache:
    """
    Caché semántica local utilizando Valkey/Redis para almacenar respuestas de LLMs.
    Compara las consultas nuevas contra las previas utilizando similitud de coseno sobre embeddings.
    """
    def __init__(self, host="localhost", port=6379, threshold=0.90):
        self.client = redis.Redis(host=host, port=port, decode_responses=True)
        self.threshold = threshold

    def _cosine_similarity(self, a, b):
        return np.dot(a, b) / (np.linalg.norm(a) * np.linalg.norm(b))

    def set(self, query_text: str, query_embedding: list, response_text: str):
        """
        Almacena una consulta, su embedding y la respuesta generada.
        """
        cache_key = f"cache:query:{hash(query_text)}"
        payload = {
            "query": query_text,
            "embedding": json.dumps(query_embedding),
            "response": response_text
        }
        self.client.hset(cache_key, mapping=payload)
        # Añadir al índice de claves
        self.client.sadd("cache:keys", cache_key)

    def get(self, query_embedding: list) -> str:
        """
        Busca si existe alguna consulta previa en la caché con similitud semántica
        mayor o igual al threshold establecido.
        """
        keys = self.client.smembers("cache:keys")
        best_similarity = -1
        best_response = None

        for key in keys:
            cache_data = self.client.hgetall(key)
            if not cache_data:
                continue
            
            cached_embedding = json.loads(cache_data["embedding"])
            similarity = self._cosine_similarity(query_embedding, cached_embedding)

            if similarity > best_similarity:
                best_similarity = similarity
                best_response = cache_data["response"]

        if best_similarity >= self.threshold:
            print(f"[Semantic Cache] HIT semántico! Similitud: {best_similarity:.4f}")
            return best_response
        
        print(f"[Semantic Cache] MISS semántico. Mejor similitud: {best_similarity:.4f}")
        return None
