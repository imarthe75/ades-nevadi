import psycopg2
from psycopg2.extras import execute_values

class LongTermMemory:
    """
    Motor de memoria a largo plazo basado en PostgreSQL con soporte pgvector local.
    Almacena reflexiones, decisiones y aprendizajes conceptuales del agente residente.
    """
    def __init__(self, dsn="dbname=db_resident_knowledge user=admin password=resident_secure_pwd_2026 host=localhost port=5432"):
        self.conn = psycopg2.connect(dsn)
        self.conn.autocommit = True
        self._initialize_db()

    def _initialize_db(self):
        with self.conn.cursor() as cur:
            # Habilitar extensión de pgvector en la DB local
            cur.execute("CREATE EXTENSION IF NOT EXISTS vector;")
            # Tabla de lecciones
            cur.execute("""
                CREATE TABLE IF NOT EXISTS memory.lessons (
                    id BIGSERIAL PRIMARY KEY,
                    content TEXT NOT NULL,
                    embedding vector(1536), -- Soporte estándar para embeddings de OpenAI/Aura
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                );
            """)

    def store_lesson(self, content: str, embedding: list):
        """
        Guarda una lección aprendida en el rito de cierre.
        """
        with self.conn.cursor() as cur:
            cur.execute(
                "INSERT INTO memory.lessons (content, embedding) VALUES (%s, %s);",
                (content, embedding)
            )
            print("✅ Lección almacenada en memoria de largo plazo local.")

    def search_lessons(self, query_embedding: list, limit: int = 3) -> list:
        """
        Busca las lecciones más similares a la lección o consulta provista usando distancia de coseno.
        """
        with self.conn.cursor() as cur:
            # Operador <=> denota distancia de coseno en pgvector
            cur.execute("""
                SELECT content, 1 - (embedding <=> %s::vector) AS similarity
                FROM memory.lessons
                ORDER BY embedding <=> %s::vector
                LIMIT %s;
            """, (query_embedding, query_embedding, limit))
            return cur.fetchall()
Base_Memory = LongTermMemory
