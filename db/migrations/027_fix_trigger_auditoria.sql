-- =============================================================================
-- MIGRACIÓN 027 — Fix fn_auditoria_biu tras renombramiento de columnas (mig 025)
-- La mig 025 renombró fccreacion→fecha_creacion / fcmodificacion→fecha_modificacion
-- pero no actualizó el cuerpo de la función del trigger.
-- Fecha: 2026-06-10
-- =============================================================================

CREATE OR REPLACE FUNCTION auditoria.fn_auditoria_biu()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        NEW.fecha_creacion       := NOW();
        NEW.fecha_modificacion   := NOW();
        NEW.usuario_creacion     := CURRENT_USER;
        NEW.usuario_modificacion := CURRENT_USER;
        NEW.ref                  := COALESCE(NEW.ref, uuidv7());
        NEW.row_version          := 1;
        NEW.is_active            := COALESCE(NEW.is_active, TRUE);
    ELSIF TG_OP = 'UPDATE' THEN
        NEW.fecha_modificacion   := NOW();
        NEW.usuario_modificacion := CURRENT_USER;
        NEW.row_version          := COALESCE(OLD.row_version, 0) + 1;
        -- Inmutable en UPDATE
        NEW.fecha_creacion       := OLD.fecha_creacion;
        NEW.usuario_creacion     := OLD.usuario_creacion;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION auditoria.fn_auditoria_biu()
  IS 'Trigger BIU de auditoría: rellena fecha_creacion/modificacion, usuario, ref, row_version, is_active.';

SELECT 'Migration 027: fn_auditoria_biu corregida' AS status;
