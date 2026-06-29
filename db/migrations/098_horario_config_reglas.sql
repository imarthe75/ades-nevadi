BEGIN;

CREATE TABLE IF NOT EXISTS ades_horario_config (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plantel_id UUID NOT NULL,
    ciclo_escolar_id UUID NOT NULL,
    config_jsonb JSONB NOT NULL DEFAULT '{}'::jsonb,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    row_version INTEGER DEFAULT 0,
    fecha_creacion TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    usuario_creacion VARCHAR(100),
    fecha_modificacion TIMESTAMP WITH TIME ZONE,
    usuario_modificacion VARCHAR(100),
    ref UUID DEFAULT gen_random_uuid()
);

CREATE TABLE IF NOT EXISTS ades_horario_regla (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plantel_id UUID NOT NULL,
    ciclo_escolar_id UUID NOT NULL,
    tipo VARCHAR(50) NOT NULL,
    dura BOOLEAN NOT NULL DEFAULT TRUE,
    peso INTEGER NOT NULL DEFAULT 1,
    activa BOOLEAN NOT NULL DEFAULT TRUE,
    params JSONB NOT NULL DEFAULT '{}'::jsonb,
    descripcion TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    row_version INTEGER DEFAULT 0,
    fecha_creacion TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    usuario_creacion VARCHAR(100),
    fecha_modificacion TIMESTAMP WITH TIME ZONE,
    usuario_modificacion VARCHAR(100),
    ref UUID DEFAULT gen_random_uuid()
);

CREATE INDEX IF NOT EXISTS idx_ades_horario_regla_plantel ON ades_horario_regla(plantel_id, ciclo_escolar_id);

DROP TRIGGER IF EXISTS ades_horario_config_audit_biu ON ades_horario_config;
CREATE TRIGGER ades_horario_config_audit_biu
    BEFORE INSERT OR UPDATE ON ades_horario_config
    FOR EACH ROW EXECUTE FUNCTION auditoria.fn_auditoria_biu();

DROP TRIGGER IF EXISTS ades_horario_regla_audit_biu ON ades_horario_regla;
CREATE TRIGGER ades_horario_regla_audit_biu
    BEFORE INSERT OR UPDATE ON ades_horario_regla
    FOR EACH ROW EXECUTE FUNCTION auditoria.fn_auditoria_biu();

COMMIT;
