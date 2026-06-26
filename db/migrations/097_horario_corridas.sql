-- =============================================================================
-- 97. CORRIDAS DEL MOTOR DE HORARIOS
-- =============================================================================
CREATE TABLE ades_horario_corrida (
    id                   UUID         NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    plantel_id           UUID         NOT NULL REFERENCES ades_planteles(id),
    ciclo_escolar_id     UUID         NOT NULL REFERENCES ades_ciclos_escolares(id),
    estado               VARCHAR(30)  NOT NULL DEFAULT 'PENDIENTE',
    score_text           VARCHAR(40),
    score_analysis_json  JSONB,
    tiempo_solving_ms    BIGINT,
    version              INTEGER      NOT NULL DEFAULT 1,
    generado_por         TEXT         NOT NULL,
    resultado_excel_url  TEXT,
    ref                  UUID         NOT NULL DEFAULT uuidv7() UNIQUE,
    is_active            BOOLEAN      NOT NULL DEFAULT TRUE,
    fecha_creacion       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    fecha_modificacion   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    usuario_creacion     VARCHAR(150) NOT NULL DEFAULT current_user,
    usuario_modificacion VARCHAR(150) NOT NULL DEFAULT current_user,
    row_version          INTEGER      NOT NULL DEFAULT 1,
    CONSTRAINT uq_ades_horario_corrida_version UNIQUE (plantel_id, ciclo_escolar_id, version)
);

COMMENT ON TABLE ades_horario_corrida IS 'Cabecera de cada corrida del motor de horarios Timefold.';
COMMENT ON COLUMN ades_horario_corrida.id IS 'Llave primaria UUID generada por uuidv7().';
SELECT auditoria.asignar_trigger('ades_horario_corrida');

ALTER TABLE ades_horarios
    ADD COLUMN IF NOT EXISTS corrida_id UUID REFERENCES ades_horario_corrida(id),
    ADD COLUMN IF NOT EXISTS fijado BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN ades_horarios.corrida_id IS 'Corrida de Timefold que generó la lección; NULL para edición manual o legado.';
COMMENT ON COLUMN ades_horarios.fijado IS 'TRUE cuando la lección queda bloqueada para regeneración parcial.';

CREATE INDEX IF NOT EXISTS idx_ades_horarios_corrida ON ades_horarios(corrida_id);
CREATE INDEX IF NOT EXISTS idx_ades_horarios_corrida_grupo ON ades_horarios(corrida_id, grupo_id, dia_semana);
CREATE INDEX IF NOT EXISTS idx_ades_horarios_corrida_profesor ON ades_horarios(corrida_id, profesor_id, dia_semana);