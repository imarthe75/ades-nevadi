-- =============================================================================
-- Migración: 110_historial_admision.sql
-- Descripción: PE-006 — timeline de cambios de estado de una solicitud de
--              admisión. Se evaluó reusar auditoria.log_auditoria, pero esa
--              tabla guarda el volcado de fila completa como texto de tipo
--              ROW compuesto (no JSON) y audit_aiud solo se activa en
--              producción (auditoria.asignar_triggers) — no serviría para
--              un timeline legible ni sería verificable en desarrollo. Se usa
--              en cambio una tabla dedicada, poblada automáticamente por
--              trigger cuando cambia el campo estado.
-- Tablas afectadas: ades_admision_historial_estados (nueva)
-- Autor: ADES
-- Fecha: 2026-07-03
-- =============================================================================

CREATE TABLE IF NOT EXISTS ades_admision_historial_estados (
    id                UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    solicitud_id      UUID NOT NULL REFERENCES ades_solicitudes_admision(id),
    estado_anterior   VARCHAR(20),
    estado_nuevo      VARCHAR(20) NOT NULL,
    usuario           TEXT,
    fecha             TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE ades_admision_historial_estados IS
    'Timeline de transiciones de estado de una solicitud de admisión (PE-006) — poblada automáticamente por trigger.';

CREATE OR REPLACE FUNCTION fn_registrar_historial_admision() RETURNS TRIGGER AS $$
BEGIN
    IF (TG_OP = 'INSERT') THEN
        INSERT INTO ades_admision_historial_estados (solicitud_id, estado_anterior, estado_nuevo, usuario)
        VALUES (NEW.id, NULL, NEW.estado, NEW.usuario_creacion);
    ELSIF (TG_OP = 'UPDATE' AND NEW.estado IS DISTINCT FROM OLD.estado) THEN
        INSERT INTO ades_admision_historial_estados (solicitud_id, estado_anterior, estado_nuevo, usuario)
        VALUES (NEW.id, OLD.estado, NEW.estado, NEW.usuario_modificacion);
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_historial_admision ON ades_solicitudes_admision;
CREATE TRIGGER trg_historial_admision
    AFTER INSERT OR UPDATE ON ades_solicitudes_admision
    FOR EACH ROW EXECUTE FUNCTION fn_registrar_historial_admision();

-- Backfill: registrar el estado actual de solicitudes existentes como punto de partida.
INSERT INTO ades_admision_historial_estados (solicitud_id, estado_anterior, estado_nuevo, usuario, fecha)
SELECT id, NULL, estado, COALESCE(usuario_creacion, 'sistema'), fecha_creacion
FROM ades_solicitudes_admision
WHERE NOT EXISTS (
    SELECT 1 FROM ades_admision_historial_estados h WHERE h.solicitud_id = ades_solicitudes_admision.id
);
