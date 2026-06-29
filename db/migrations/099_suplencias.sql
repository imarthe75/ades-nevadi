-- ============================================================================
-- V1_099__suplencias.sql
-- ============================================================================

CREATE TABLE IF NOT EXISTS ades_suplencias (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    profesor_ausente_id UUID NOT NULL,
    fecha DATE NOT NULL,
    timeslot_id UUID,
    horario_id UUID,
    motivo TEXT,
    profesor_cobertura_id UUID,
    estado VARCHAR(50) DEFAULT 'PENDIENTE',

    -- Auditoría
    is_active BOOLEAN DEFAULT TRUE NOT NULL,
    creado_por VARCHAR(100) NOT NULL,
    creado_el TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    actualizado_por VARCHAR(100),
    actualizado_el TIMESTAMP WITH TIME ZONE
);

DROP TRIGGER IF EXISTS ades_suplencias_audit_biu ON ades_suplencias;
CREATE TRIGGER ades_suplencias_audit_biu
    BEFORE INSERT OR UPDATE ON ades_suplencias
    FOR EACH ROW EXECUTE FUNCTION auditoria.fn_auditoria_biu();
