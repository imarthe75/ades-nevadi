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
