BEGIN;

ALTER TABLE ades_horario_regla ADD COLUMN IF NOT EXISTS nivel_educativo_id UUID;

CREATE TABLE IF NOT EXISTS ades_horario_franjas (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plantel_id UUID,
    ciclo_escolar_id UUID NOT NULL,
    nivel_educativo_id UUID,
    dia_semana SMALLINT NOT NULL,
    hora_inicio TIME NOT NULL,
    hora_fin TIME NOT NULL,
    turno VARCHAR(20) DEFAULT 'MATUTINO',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    row_version INTEGER DEFAULT 0,
    fecha_creacion TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    usuario_creacion VARCHAR(100),
    fecha_modificacion TIMESTAMP WITH TIME ZONE,
    usuario_modificacion VARCHAR(100),
    ref UUID DEFAULT gen_random_uuid()
);

CREATE INDEX IF NOT EXISTS idx_ades_horario_franjas_idx 
ON ades_horario_franjas(plantel_id, ciclo_escolar_id, nivel_educativo_id);

DROP TRIGGER IF EXISTS ades_horario_franjas_audit_biu ON ades_horario_franjas;
CREATE TRIGGER ades_horario_franjas_audit_biu
    BEFORE INSERT OR UPDATE ON ades_horario_franjas
    FOR EACH ROW EXECUTE FUNCTION auditoria.fn_auditoria_biu();

COMMIT;
