-- =============================================================================
-- Migración 036: Trazabilidad Blockchain (Polygon PoS)
-- =============================================================================

BEGIN;

ALTER TABLE ades_certificados
    ADD COLUMN IF NOT EXISTS blockchain_tx        VARCHAR(66),
    ADD COLUMN IF NOT EXISTS blockchain_status    VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    ADD COLUMN IF NOT EXISTS fecha_anclaje         TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS blockchain_network    VARCHAR(30);

COMMENT ON COLUMN ades_certificados.blockchain_tx IS 'Hash de la transacción en la blockchain de Polygon (0x...)';
COMMENT ON COLUMN ades_certificados.blockchain_status IS 'Estado del anclaje: PENDIENTE | ANCLADO | FALLIDO';
COMMENT ON COLUMN ades_certificados.fecha_anclaje IS 'Fecha/hora en que se confirmó el bloque de la transacción';
COMMENT ON COLUMN ades_certificados.blockchain_network IS 'Identificador de la red: POLYGON_AMOY | POLYGON_MAINNET | MOCK';

-- Añadir constraint chk_blockchain_status si no existe
DO $$
BEGIN
    ALTER TABLE ades_certificados
        ADD CONSTRAINT chk_blockchain_status CHECK (blockchain_status IN ('PENDIENTE', 'ANCLADO', 'FALLIDO'));
EXCEPTION
    WHEN duplicate_object THEN NULL;
END $$;

-- Índice para búsquedas rápidas por estado
CREATE INDEX IF NOT EXISTS idx_certificados_blockchain
    ON ades_certificados (blockchain_status) WHERE is_active = TRUE;

COMMIT;
