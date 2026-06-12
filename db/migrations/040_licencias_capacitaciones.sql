/*
 * by Im@rthe
 * Fecha: 2026-06-11
 * Archivo: 040_licencias_capacitaciones.sql
 *
 * Descripcion:
 * FASE 29 — Seguridad Avanzada y Recursos Humanos
 * Implementa la gestion de licencias/permisos de personal (DP-006) y
 * el registro de capacitaciones y certificaciones docentes (DP-007).
 *
 * Tablas:
 *   - ades_licencias_personal     : solicitudes de licencia/permiso de personal
 *   - ades_capacitaciones_docente : capacitaciones y certificaciones de docentes
 *
 * Solicitado y aprobado por Israel Martinez Hernandez
 */

-- ===========================
-- 1. ades_licencias_personal (DP-006)
-- ===========================
CREATE TABLE IF NOT EXISTS public.ades_licencias_personal (
    id                  UUID        NOT NULL DEFAULT gen_random_uuid(),
    personal_id         UUID        NOT NULL,   -- FK a ades_personas
    tipo_licencia       VARCHAR(40) NOT NULL,   -- MEDICA MATERNIDAD PATERNIDAD DUELO PERSONAL COMISION CAPACITACION OTRO
    fecha_inicio        DATE        NOT NULL,
    fecha_fin           DATE        NOT NULL,
    dias_habiles        INTEGER     NOT NULL DEFAULT 1,
    estado              VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',  -- PENDIENTE APROBADA RECHAZADA CANCELADA
    motivo              TEXT        NULL,
    observaciones_rh    TEXT        NULL,
    sustituto_id        UUID        NULL,       -- FK a ades_personas (quien cubre)
    aprobado_por        UUID        NULL,       -- FK a ades_usuarios
    fecha_aprobacion    TIMESTAMPTZ NULL,
    con_goce_sueldo     BOOLEAN     NOT NULL DEFAULT TRUE,
    -- Auditoría canónica
    ref                 UUID        NULL,
    row_version         INTEGER     NOT NULL DEFAULT 1,
    fecha_creacion      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fecha_modificacion  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion    TEXT        NULL,
    usuario_modificacion TEXT       NULL,
    is_active           BOOLEAN     NOT NULL DEFAULT TRUE
);

-- PK
DO $$ BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'public.ades_licencias_personal'::regclass AND contype = 'p'
    ) THEN
        ALTER TABLE public.ades_licencias_personal
            ADD CONSTRAINT pk_ades_licencias_personal PRIMARY KEY (id);
    END IF;
END $$;

-- CHECK tipo_licencia
DO $$ BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'public.ades_licencias_personal'::regclass
          AND conname = 'chk_licencia_tipo'
    ) THEN
        ALTER TABLE public.ades_licencias_personal
            ADD CONSTRAINT chk_licencia_tipo
            CHECK (tipo_licencia IN (
                'MEDICA','MATERNIDAD','PATERNIDAD','DUELO',
                'PERSONAL','COMISION','CAPACITACION','OTRO'
            ));
    END IF;
END $$;

-- CHECK estado
DO $$ BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'public.ades_licencias_personal'::regclass
          AND conname = 'chk_licencia_estado'
    ) THEN
        ALTER TABLE public.ades_licencias_personal
            ADD CONSTRAINT chk_licencia_estado
            CHECK (estado IN ('PENDIENTE','APROBADA','RECHAZADA','CANCELADA'));
    END IF;
END $$;

-- CHECK fechas
DO $$ BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'public.ades_licencias_personal'::regclass
          AND conname = 'chk_licencia_fechas'
    ) THEN
        ALTER TABLE public.ades_licencias_personal
            ADD CONSTRAINT chk_licencia_fechas
            CHECK (fecha_fin >= fecha_inicio);
    END IF;
END $$;

-- Índices
CREATE INDEX IF NOT EXISTS idx_licencias_personal_id
    ON public.ades_licencias_personal (personal_id);
CREATE INDEX IF NOT EXISTS idx_licencias_estado
    ON public.ades_licencias_personal (estado);
CREATE INDEX IF NOT EXISTS idx_licencias_fechas
    ON public.ades_licencias_personal (fecha_inicio, fecha_fin);
CREATE INDEX IF NOT EXISTS idx_licencias_active
    ON public.ades_licencias_personal (is_active) WHERE is_active = TRUE;

-- Comentarios
COMMENT ON TABLE public.ades_licencias_personal IS
    'Solicitudes de licencia y permisos de personal docente/administrativo. Flujo: PENDIENTE → APROBADA/RECHAZADA. Incluye sustituto y goce de sueldo.';
COMMENT ON COLUMN public.ades_licencias_personal.tipo_licencia IS
    'Tipo de permiso: MEDICA, MATERNIDAD, PATERNIDAD, DUELO, PERSONAL, COMISION, CAPACITACION, OTRO.';
COMMENT ON COLUMN public.ades_licencias_personal.dias_habiles IS
    'Días hábiles que comprende la licencia (calculado por el sistema).';
COMMENT ON COLUMN public.ades_licencias_personal.con_goce_sueldo IS
    'TRUE = con goce de sueldo. FALSE = sin goce de sueldo.';

-- Trigger audit_biu
SELECT auditoria.asignar_biu('public.ades_licencias_personal');

-- ===========================
-- 2. ades_capacitaciones_docente (DP-007)
-- ===========================
CREATE TABLE IF NOT EXISTS public.ades_capacitaciones_docente (
    id                  UUID        NOT NULL DEFAULT gen_random_uuid(),
    docente_id          UUID        NOT NULL,   -- FK a ades_profesores
    nombre              TEXT        NOT NULL,
    descripcion         TEXT        NULL,
    institucion         TEXT        NOT NULL,   -- Institución que otorga la capacitación
    tipo_certificacion  VARCHAR(30) NOT NULL DEFAULT 'CURSO',  -- CURSO TALLER DIPLOMADO POSGRADO CERTIFICACION CONGRESO
    modalidad           VARCHAR(20) NOT NULL DEFAULT 'PRESENCIAL',  -- PRESENCIAL EN_LINEA HIBRIDA
    fecha_inicio        DATE        NOT NULL,
    fecha_fin           DATE        NOT NULL,
    duracion_hrs        NUMERIC(6,1) NOT NULL DEFAULT 0,
    area_formacion      VARCHAR(40) NULL,   -- PEDAGOGIA TIC DISCIPLINAR IDIOMAS LIDERAZGO OTRO
    certificado_url     TEXT        NULL,   -- MinIO URL del certificado PDF
    folio_certificado   TEXT        NULL,
    validado_rh         BOOLEAN     NOT NULL DEFAULT FALSE,
    fecha_validacion    TIMESTAMPTZ NULL,
    -- Auditoría canónica
    ref                 UUID        NULL,
    row_version         INTEGER     NOT NULL DEFAULT 1,
    fecha_creacion      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fecha_modificacion  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion    TEXT        NULL,
    usuario_modificacion TEXT       NULL,
    is_active           BOOLEAN     NOT NULL DEFAULT TRUE
);

-- PK
DO $$ BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'public.ades_capacitaciones_docente'::regclass AND contype = 'p'
    ) THEN
        ALTER TABLE public.ades_capacitaciones_docente
            ADD CONSTRAINT pk_ades_capacitaciones_docente PRIMARY KEY (id);
    END IF;
END $$;

-- CHECK tipo_certificacion
DO $$ BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'public.ades_capacitaciones_docente'::regclass
          AND conname = 'chk_capacitacion_tipo'
    ) THEN
        ALTER TABLE public.ades_capacitaciones_docente
            ADD CONSTRAINT chk_capacitacion_tipo
            CHECK (tipo_certificacion IN (
                'CURSO','TALLER','DIPLOMADO','POSGRADO',
                'CERTIFICACION','CONGRESO','OTRO'
            ));
    END IF;
END $$;

-- CHECK modalidad
DO $$ BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'public.ades_capacitaciones_docente'::regclass
          AND conname = 'chk_capacitacion_modalidad'
    ) THEN
        ALTER TABLE public.ades_capacitaciones_docente
            ADD CONSTRAINT chk_capacitacion_modalidad
            CHECK (modalidad IN ('PRESENCIAL','EN_LINEA','HIBRIDA'));
    END IF;
END $$;

-- Índices
CREATE INDEX IF NOT EXISTS idx_capacitaciones_docente_id
    ON public.ades_capacitaciones_docente (docente_id);
CREATE INDEX IF NOT EXISTS idx_capacitaciones_fecha
    ON public.ades_capacitaciones_docente (fecha_inicio DESC);
CREATE INDEX IF NOT EXISTS idx_capacitaciones_tipo
    ON public.ades_capacitaciones_docente (tipo_certificacion);
CREATE INDEX IF NOT EXISTS idx_capacitaciones_validado
    ON public.ades_capacitaciones_docente (validado_rh) WHERE validado_rh = TRUE;

-- Comentarios
COMMENT ON TABLE public.ades_capacitaciones_docente IS
    'Registro de capacitaciones, cursos, talleres y certificaciones del personal docente. Base para reportes de formación continua y cumplimiento SEP/UAEMEX.';
COMMENT ON COLUMN public.ades_capacitaciones_docente.certificado_url IS
    'URL de MinIO con el PDF del certificado de participación o acreditación.';
COMMENT ON COLUMN public.ades_capacitaciones_docente.validado_rh IS
    'TRUE = RH validó y registró la capacitación como oficial en el expediente docente.';

-- Trigger audit_biu
SELECT auditoria.asignar_biu('public.ades_capacitaciones_docente');

-- ===========================
-- 3. Resumen
-- ===========================
DO $$ BEGIN
    RAISE NOTICE '=== Migración 040 aplicada: ades_licencias_personal + ades_capacitaciones_docente ===';
    RAISE NOTICE 'DP-006 (Licencias) y DP-007 (Capacitaciones) implementados — FASE 29';
END $$;
