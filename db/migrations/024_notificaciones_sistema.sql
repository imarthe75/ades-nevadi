BEGIN;

CREATE TABLE IF NOT EXISTS ades_notificaciones_sistema (
    id           UUID PRIMARY KEY DEFAULT uuidv7(),
    usuario_id   UUID NOT NULL REFERENCES ades_usuarios(id) ON DELETE CASCADE,
    titulo       VARCHAR(200) NOT NULL,
    mensaje      TEXT,
    tipo         VARCHAR(20)  NOT NULL DEFAULT 'INFO',  -- INFO | WARN | ERROR | SUCCESS
    leido        BOOLEAN      NOT NULL DEFAULT FALSE,
    fecha_creacion TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    -- No necesita AuditMixin completo, son registros del sistema
    CONSTRAINT ck_tipo_notif CHECK (tipo IN ('INFO', 'WARN', 'ERROR', 'SUCCESS'))
);

CREATE INDEX IF NOT EXISTS idx_notif_sistema_usuario ON ades_notificaciones_sistema(usuario_id, leido);
CREATE INDEX IF NOT EXISTS idx_notif_sistema_fecha ON ades_notificaciones_sistema(fecha_creacion DESC);

COMMENT ON TABLE ades_notificaciones_sistema IS
    'Notificaciones in-app generadas automáticamente por el sistema: asignación de roles, cambios de grupo, alertas académicas, etc. Complementa el push via ntfy.';

COMMIT;
