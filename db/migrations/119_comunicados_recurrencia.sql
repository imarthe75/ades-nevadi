-- =============================================================================
-- MIGRACION 119: Comunicados recurrentes — columnas faltantes en ades_comunicados
-- =============================================================================
-- Objetivo: la entidad JPA mx.ades.modules.comunicados.Comunicado (introducida en
--           el refactor a Spring Boot, commit a77f9af) define es_recurrente,
--           periodicidad, proximo_envio y total_destinatarios, pero nunca se
--           creó la migración correspondiente — Hibernate schema validation
--           fallaba en boot por columnas ausentes.
-- Fecha: 2026-07-10
-- =============================================================================

BEGIN;

ALTER TABLE ades_comunicados
    ADD COLUMN IF NOT EXISTS es_recurrente       BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS periodicidad        VARCHAR(20),
    ADD COLUMN IF NOT EXISTS proximo_envio       TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS total_destinatarios INTEGER NOT NULL DEFAULT 0;

COMMENT ON COLUMN ades_comunicados.es_recurrente IS 'Indica si el comunicado se reenvía periódicamente';
COMMENT ON COLUMN ades_comunicados.periodicidad IS 'Periodicidad de reenvío: DIARIA, SEMANAL, MENSUAL, etc.';
COMMENT ON COLUMN ades_comunicados.proximo_envio IS 'Fecha/hora del próximo envío programado (comunicados recurrentes)';
COMMENT ON COLUMN ades_comunicados.total_destinatarios IS 'Conteo de destinatarios del comunicado al momento de publicación';

COMMIT;
