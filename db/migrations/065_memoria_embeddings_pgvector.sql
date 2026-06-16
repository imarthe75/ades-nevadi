-- ============================================================
-- Migración 065: Schema memoria + tabla embeddings pgvector
-- ============================================================
-- Activa LongTermMemory del agente residente ADES.
-- Dimensión 384 → all-MiniLM-L6-v2 (SentenceTransformer).
-- ============================================================

CREATE EXTENSION IF NOT EXISTS vector;

CREATE SCHEMA IF NOT EXISTS memoria;

-- Sesiones de trabajo del agente residente
CREATE TABLE IF NOT EXISTS memoria.sesiones (
    id            UUID PRIMARY KEY DEFAULT uuidv7(),
    agente_id     VARCHAR(255) NOT NULL,
    iniciada      TIMESTAMPTZ DEFAULT now(),
    cerrada       TIMESTAMPTZ,
    hash_contenido VARCHAR(64),
    metadata      JSONB,
    created_at    TIMESTAMPTZ DEFAULT now(),
    updated_at    TIMESTAMPTZ DEFAULT now()
);

-- Lecciones aprendidas y patrones como vectores semánticos
CREATE TABLE IF NOT EXISTS memoria.embeddings (
    id               UUID PRIMARY KEY DEFAULT uuidv7(),
    sesion_id        UUID REFERENCES memoria.sesiones(id) ON DELETE SET NULL,
    tipo             VARCHAR(50)  NOT NULL,   -- 'leccion' | 'decision' | 'patron'
    contenido        TEXT         NOT NULL,
    vector           vector(384),             -- all-MiniLM-L6-v2 (384 dims)
    relevancia_score FLOAT        DEFAULT 0.5,
    metadata         JSONB,
    created_at       TIMESTAMPTZ  DEFAULT now()
);

-- Decisiones arquitectónicas del agente
CREATE TABLE IF NOT EXISTS memoria.decisiones (
    id                  UUID PRIMARY KEY DEFAULT uuidv7(),
    sesion_id           UUID REFERENCES memoria.sesiones(id) ON DELETE SET NULL,
    titulo              VARCHAR(255) NOT NULL,
    descripcion         TEXT,
    heuristica_aplicada VARCHAR(255),
    contexto            JSONB,
    status              VARCHAR(50)  DEFAULT 'pendiente',
    created_at          TIMESTAMPTZ  DEFAULT now()
);

-- Índices básicos
CREATE INDEX IF NOT EXISTS idx_memoria_sesiones_agente   ON memoria.sesiones(agente_id);
CREATE INDEX IF NOT EXISTS idx_memoria_embeddings_tipo   ON memoria.embeddings(tipo);
CREATE INDEX IF NOT EXISTS idx_memoria_decisiones_sesion ON memoria.decisiones(sesion_id);

-- HNSW coseno para búsqueda ANN (mejor que IVFFlat en tablas pequeñas)
CREATE INDEX IF NOT EXISTS idx_memoria_embeddings_hnsw
    ON memoria.embeddings USING hnsw (vector vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

-- Trigger updated_at en sesiones
CREATE OR REPLACE FUNCTION memoria.fn_sesiones_updated_at()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_sesiones_updated_at ON memoria.sesiones;
CREATE TRIGGER trg_sesiones_updated_at
    BEFORE UPDATE ON memoria.sesiones
    FOR EACH ROW EXECUTE FUNCTION memoria.fn_sesiones_updated_at();

-- Comentarios
COMMENT ON SCHEMA memoria              IS 'Memoria a largo plazo del agente residente ADES (pgvector)';
COMMENT ON TABLE  memoria.embeddings   IS 'Lecciones, decisiones y patrones codificados como vectores 384-dim';
COMMENT ON TABLE  memoria.decisiones   IS 'Decisiones arquitectónicas del agente con heurística aplicada';
COMMENT ON TABLE  memoria.sesiones     IS 'Sesiones de trabajo del agente residente';
COMMENT ON COLUMN memoria.embeddings.vector IS 'Embedding 384-dim — SentenceTransformer all-MiniLM-L6-v2';
