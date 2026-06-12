-- =============================================================================
-- Migración 034: Sanciones Disciplinarias + Plan de Mejora (SB-012, SB-013, SB-014)
-- =============================================================================

BEGIN;

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. Sanciones Disciplinarias (SB-012)
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS ades_sanciones_disciplinarias (
    id                  UUID        PRIMARY KEY DEFAULT uuidv7(),
    reporte_conducta_id UUID        NOT NULL REFERENCES ades_reportes_conducta(id),
    estudiante_id       UUID        NOT NULL REFERENCES ades_estudiantes(id),

    -- Tipo y severidad
    tipo_sancion        VARCHAR(40) NOT NULL
        CHECK (tipo_sancion IN (
            'AMONESTACION_VERBAL',
            'AMONESTACION_ESCRITA',
            'CITATORIO_PADRES',
            'SUSPENSION_1_DIA',
            'SUSPENSION_3_DIAS',
            'SUSPENSION_5_DIAS',
            'CONDICIONAL',
            'EXPULSION'
        )),
    justificacion       TEXT        NOT NULL,

    -- Quién la aplica (debe ser Director o superior)
    autorizado_por_id   UUID        NOT NULL REFERENCES ades_usuarios(id),
    fecha_sancion       DATE        NOT NULL DEFAULT CURRENT_DATE,
    fecha_fin_sancion   DATE,       -- para suspensiones con duración

    -- Estado del proceso
    estado              VARCHAR(20) NOT NULL DEFAULT 'APLICADA'
        CHECK (estado IN ('APLICADA', 'EN_PROCESO', 'CUMPLIDA', 'APELADA', 'REVOCADA')),

    -- Notificación a padres
    notificado_padres   BOOLEAN     NOT NULL DEFAULT FALSE,
    fecha_notificacion  DATE,
    medio_notificacion  VARCHAR(30),  -- PRESENCIAL, TELEFONO, EMAIL, WHATSAPP

    notas_adicionales   TEXT,

    row_version         INT         NOT NULL DEFAULT 1,
    is_active           BOOLEAN     NOT NULL DEFAULT TRUE,
    fecha_creacion      TIMESTAMPTZ NOT NULL DEFAULT now(),
    fecha_modificacion  TIMESTAMPTZ NOT NULL DEFAULT now(),
    usuario_creacion    VARCHAR(150) NOT NULL DEFAULT CURRENT_USER,
    usuario_modificacion VARCHAR(150) NOT NULL DEFAULT CURRENT_USER
);

CREATE INDEX IF NOT EXISTS idx_sancion_reporte
    ON ades_sanciones_disciplinarias (reporte_conducta_id);
CREATE INDEX IF NOT EXISTS idx_sancion_estudiante
    ON ades_sanciones_disciplinarias (estudiante_id, fecha_sancion DESC);

COMMENT ON TABLE ades_sanciones_disciplinarias IS
    'Sanciones formales aplicadas a un alumno. Una por reporte, aprobadas por Director.';

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. Plan de Mejora Conductual (SB-013)
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS ades_planes_mejora (
    id                  UUID        PRIMARY KEY DEFAULT uuidv7(),
    reporte_conducta_id UUID        NOT NULL REFERENCES ades_reportes_conducta(id),
    estudiante_id       UUID        NOT NULL REFERENCES ades_estudiantes(id),
    ciclo_escolar_id    UUID        REFERENCES ades_ciclos_escolares(id),

    -- Responsable del plan (orientador o director)
    elaborado_por_id    UUID        NOT NULL REFERENCES ades_usuarios(id),
    fecha_elaboracion   DATE        NOT NULL DEFAULT CURRENT_DATE,

    -- Estructura del plan
    objetivo_general    TEXT        NOT NULL,
    compromisos_alumno  JSONB       NOT NULL DEFAULT '[]',  -- [{texto, plazo_dias, cumplido}]
    compromisos_padre   JSONB       NOT NULL DEFAULT '[]',  -- [{texto, plazo_dias, cumplido}]
    compromisos_escuela JSONB       NOT NULL DEFAULT '[]',  -- [{texto, responsable}]

    -- Firmas y validación
    firmado_alumno      BOOLEAN     NOT NULL DEFAULT FALSE,
    firmado_padre       BOOLEAN     NOT NULL DEFAULT FALSE,
    firmado_director    BOOLEAN     NOT NULL DEFAULT FALSE,
    fecha_firma_alumno  DATE,
    fecha_firma_padre   DATE,

    -- Seguimiento
    fecha_primer_seguimiento DATE,
    fecha_cierre        DATE,
    estado              VARCHAR(20) NOT NULL DEFAULT 'ACTIVO'
        CHECK (estado IN ('ACTIVO', 'EN_PROCESO', 'CUMPLIDO', 'INCUMPLIDO', 'CANCELADO')),

    observaciones_cierre TEXT,

    row_version         INT         NOT NULL DEFAULT 1,
    is_active           BOOLEAN     NOT NULL DEFAULT TRUE,
    fecha_creacion      TIMESTAMPTZ NOT NULL DEFAULT now(),
    fecha_modificacion  TIMESTAMPTZ NOT NULL DEFAULT now(),
    usuario_creacion    VARCHAR(150) NOT NULL DEFAULT CURRENT_USER,
    usuario_modificacion VARCHAR(150) NOT NULL DEFAULT CURRENT_USER,

    CONSTRAINT uq_plan_reporte UNIQUE (reporte_conducta_id)
);

CREATE INDEX IF NOT EXISTS idx_plan_mejora_estudiante
    ON ades_planes_mejora (estudiante_id, fecha_elaboracion DESC);

COMMENT ON TABLE ades_planes_mejora IS
    'Plan de mejora conductual. Un plan por reporte. Incluye compromisos de alumno, padre y escuela.';

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. Seguimiento del Plan (SB-014)
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS ades_seguimiento_plan (
    id                  UUID        PRIMARY KEY DEFAULT uuidv7(),
    plan_mejora_id      UUID        NOT NULL REFERENCES ades_planes_mejora(id),
    estudiante_id       UUID        NOT NULL REFERENCES ades_estudiantes(id),

    registrado_por_id   UUID        NOT NULL REFERENCES ades_usuarios(id),
    fecha_seguimiento   DATE        NOT NULL DEFAULT CURRENT_DATE,

    -- Valoración del avance
    avance              VARCHAR(20) NOT NULL DEFAULT 'PARCIAL'
        CHECK (avance IN ('SIN_AVANCE', 'PARCIAL', 'SATISFACTORIO', 'EXCELENTE')),

    descripcion         TEXT        NOT NULL,
    compromisos_cumplidos JSONB     NOT NULL DEFAULT '[]',  -- índices de compromisos cumplidos
    acciones_adicionales TEXT,

    -- Actualización del estado del plan
    nuevo_estado_plan   VARCHAR(20)
        CHECK (nuevo_estado_plan IS NULL OR nuevo_estado_plan IN (
            'ACTIVO', 'EN_PROCESO', 'CUMPLIDO', 'INCUMPLIDO', 'CANCELADO'
        )),

    row_version         INT         NOT NULL DEFAULT 1,
    is_active           BOOLEAN     NOT NULL DEFAULT TRUE,
    fecha_creacion      TIMESTAMPTZ NOT NULL DEFAULT now(),
    fecha_modificacion  TIMESTAMPTZ NOT NULL DEFAULT now(),
    usuario_creacion    VARCHAR(150) NOT NULL DEFAULT CURRENT_USER,
    usuario_modificacion VARCHAR(150) NOT NULL DEFAULT CURRENT_USER
);

CREATE INDEX IF NOT EXISTS idx_seguimiento_plan
    ON ades_seguimiento_plan (plan_mejora_id, fecha_seguimiento DESC);

COMMENT ON TABLE ades_seguimiento_plan IS
    'Entradas de seguimiento periódico al plan de mejora. Actualiza el avance del plan.';

-- ─────────────────────────────────────────────────────────────────────────────
-- 4. Trigger: al agregar seguimiento, actualizar estado del plan
-- ─────────────────────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION trg_actualizar_estado_plan()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.nuevo_estado_plan IS NOT NULL THEN
        UPDATE ades_planes_mejora
           SET estado             = NEW.nuevo_estado_plan,
               fecha_cierre       = CASE WHEN NEW.nuevo_estado_plan IN ('CUMPLIDO','INCUMPLIDO','CANCELADO')
                                         THEN CURRENT_DATE ELSE fecha_cierre END,
               fecha_modificacion = now(),
               row_version        = row_version + 1
         WHERE id = NEW.plan_mejora_id;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_seg_plan_estado
    AFTER INSERT ON ades_seguimiento_plan
    FOR EACH ROW EXECUTE FUNCTION trg_actualizar_estado_plan();

-- ─────────────────────────────────────────────────────────────────────────────
-- 5. Triggers de auditoría
-- ─────────────────────────────────────────────────────────────────────────────
SELECT auditoria.asignar_trigger('ades_sanciones_disciplinarias');
SELECT auditoria.asignar_trigger('ades_planes_mejora');
SELECT auditoria.asignar_trigger('ades_seguimiento_plan');

COMMIT;
