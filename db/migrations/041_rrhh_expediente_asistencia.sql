/*
 * by Im@rthe
 * Fecha: 2026-06-11
 * Archivo: 041_rrhh_expediente_asistencia.sql
 *
 * Descripcion:
 * FASE 30 — RRHH Avanzado
 *   - ades_disponibilidad_docente  (DP-003): días/turno disponibles por docente
 *   - ades_expediente_laboral      (DP-004): contrato, IMSS, cédula, documentos
 *   - ades_asistencia_personal     (DP-005): registro entrada/salida del personal
 *
 * Solicitado y aprobado por Israel Martinez Hernandez
 */

-- ===========================
-- 1. ades_disponibilidad_docente (DP-003)
-- ===========================
CREATE TABLE IF NOT EXISTS public.ades_disponibilidad_docente (
    id                  UUID        NOT NULL DEFAULT gen_random_uuid(),
    docente_id          UUID        NOT NULL,   -- FK a ades_profesores
    ciclo_escolar_id    UUID        NULL,        -- NULL = disponibilidad general
    dias_disponibles    JSONB       NOT NULL DEFAULT '["LUNES","MARTES","MIERCOLES","JUEVES","VIERNES"]',
    turno               VARCHAR(20) NOT NULL DEFAULT 'MATUTINO',  -- MATUTINO VESPERTINO AMBOS
    horas_semana_max    NUMERIC(4,1) NOT NULL DEFAULT 20,
    horas_frente_grupo  NUMERIC(4,1) NOT NULL DEFAULT 16,
    materias_preferidas JSONB       NULL,  -- ["MAT","ESP","BIOLOGIA"] códigos
    notas               TEXT        NULL,
    -- Auditoría canónica
    ref                 UUID        NULL,
    row_version         INTEGER     NOT NULL DEFAULT 1,
    fecha_creacion      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fecha_modificacion  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion    TEXT        NULL,
    usuario_modificacion TEXT       NULL,
    is_active           BOOLEAN     NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_disponibilidad_docente_ciclo UNIQUE (docente_id, ciclo_escolar_id)
);

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.ades_disponibilidad_docente'::regclass AND contype='p') THEN
        ALTER TABLE public.ades_disponibilidad_docente ADD CONSTRAINT pk_ades_disponibilidad_docente PRIMARY KEY (id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.ades_disponibilidad_docente'::regclass AND conname='chk_dispon_turno') THEN
        ALTER TABLE public.ades_disponibilidad_docente
            ADD CONSTRAINT chk_dispon_turno CHECK (turno IN ('MATUTINO','VESPERTINO','AMBOS'));
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_dispon_docente_id  ON public.ades_disponibilidad_docente (docente_id);
CREATE INDEX IF NOT EXISTS idx_dispon_ciclo        ON public.ades_disponibilidad_docente (ciclo_escolar_id);

COMMENT ON TABLE  public.ades_disponibilidad_docente IS 'Disponibilidad de docente por ciclo escolar: días, turno y horas máximas. Base para generación de horarios aSc.';
COMMENT ON COLUMN public.ades_disponibilidad_docente.dias_disponibles IS 'Array JSON de días: ["LUNES","MARTES","MIERCOLES","JUEVES","VIERNES"].';
COMMENT ON COLUMN public.ades_disponibilidad_docente.horas_semana_max IS 'Total de horas por semana incluyendo guardia y administrativas.';
COMMENT ON COLUMN public.ades_disponibilidad_docente.horas_frente_grupo IS 'Horas de clase efectiva frente a grupo (≤ horas_semana_max).';

SELECT auditoria.asignar_biu('public.ades_disponibilidad_docente');

-- ===========================
-- 2. ades_expediente_laboral (DP-004)
-- ===========================
CREATE TABLE IF NOT EXISTS public.ades_expediente_laboral (
    id                  UUID        NOT NULL DEFAULT gen_random_uuid(),
    persona_id          UUID        NOT NULL UNIQUE,  -- FK a ades_personas
    tipo_contrato       VARCHAR(30) NOT NULL DEFAULT 'INDEFINIDO',  -- INDEFINIDO DETERMINADO HONORARIOS COMISION
    fecha_contratacion  DATE        NOT NULL,
    fecha_fin_contrato  DATE        NULL,   -- NULL para contratos indefinidos
    salario_mensual     NUMERIC(10,2) NOT NULL DEFAULT 0,
    imss_numero         TEXT        NULL,
    infonavit_numero    TEXT        NULL,
    curp                TEXT        NULL,
    rfc                 TEXT        NULL,
    cedula_profesional  TEXT        NULL,
    nivel_estudios      VARCHAR(30) NULL,   -- LICENCIATURA MAESTRIA DOCTORADO NORMAL_BASICA BACHILLERATO OTRO
    especialidad        TEXT        NULL,
    institucion_formacion TEXT      NULL,
    clave_ct            TEXT        NULL,   -- clave de centro de trabajo SEP
    clave_issste        TEXT        NULL,
    documentos_urls     JSONB       NOT NULL DEFAULT '{}',  -- {"contrato": "https://...", "titulo": "https://..."}
    -- Auditoría canónica
    ref                 UUID        NULL,
    row_version         INTEGER     NOT NULL DEFAULT 1,
    fecha_creacion      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fecha_modificacion  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion    TEXT        NULL,
    usuario_modificacion TEXT       NULL,
    is_active           BOOLEAN     NOT NULL DEFAULT TRUE
);

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.ades_expediente_laboral'::regclass AND contype='p') THEN
        ALTER TABLE public.ades_expediente_laboral ADD CONSTRAINT pk_ades_expediente_laboral PRIMARY KEY (id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.ades_expediente_laboral'::regclass AND conname='chk_exp_lab_contrato') THEN
        ALTER TABLE public.ades_expediente_laboral
            ADD CONSTRAINT chk_exp_lab_contrato CHECK (tipo_contrato IN ('INDEFINIDO','DETERMINADO','HONORARIOS','COMISION'));
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.ades_expediente_laboral'::regclass AND conname='chk_exp_lab_fecha_fin') THEN
        ALTER TABLE public.ades_expediente_laboral
            ADD CONSTRAINT chk_exp_lab_fecha_fin CHECK (fecha_fin_contrato IS NULL OR fecha_fin_contrato >= fecha_contratacion);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_exp_laboral_persona ON public.ades_expediente_laboral (persona_id);

COMMENT ON TABLE  public.ades_expediente_laboral IS 'Expediente laboral digital del personal. Contiene datos contractuales, IMSS, INFONAVIT, cédula profesional y URLs a documentos en MinIO.';
COMMENT ON COLUMN public.ades_expediente_laboral.documentos_urls IS 'JSON de documentos: {"contrato": "url", "titulo": "url", "cedula": "url", "nss": "url", "identificacion": "url"}.';
COMMENT ON COLUMN public.ades_expediente_laboral.clave_ct IS 'Clave de Centro de Trabajo asignada por SEP. Requerida para trámites ante ISSSTE.';

SELECT auditoria.asignar_biu('public.ades_expediente_laboral');

-- ===========================
-- 3. ades_asistencia_personal (DP-005)
-- ===========================
CREATE TABLE IF NOT EXISTS public.ades_asistencia_personal (
    id                  UUID        NOT NULL DEFAULT gen_random_uuid(),
    persona_id          UUID        NOT NULL,   -- FK a ades_personas
    fecha               DATE        NOT NULL,
    hora_entrada        TIME        NULL,
    hora_salida         TIME        NULL,
    tipo_jornada        VARCHAR(20) NOT NULL DEFAULT 'COMPLETA',  -- COMPLETA MEDIA NINGUNA INCAPACIDAD VACACIONES PERMISO
    es_retardo          BOOLEAN     NOT NULL DEFAULT FALSE,
    minutos_retardo     INTEGER     NOT NULL DEFAULT 0,
    justificado         BOOLEAN     NOT NULL DEFAULT FALSE,
    justificacion       TEXT        NULL,
    justificado_por     UUID        NULL,    -- FK a ades_usuarios
    observaciones       TEXT        NULL,
    -- Auditoría canónica
    ref                 UUID        NULL,
    row_version         INTEGER     NOT NULL DEFAULT 1,
    fecha_creacion      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fecha_modificacion  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion    TEXT        NULL,
    usuario_modificacion TEXT       NULL,
    is_active           BOOLEAN     NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_asistencia_personal_fecha UNIQUE (persona_id, fecha)
);

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.ades_asistencia_personal'::regclass AND contype='p') THEN
        ALTER TABLE public.ades_asistencia_personal ADD CONSTRAINT pk_ades_asistencia_personal PRIMARY KEY (id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.ades_asistencia_personal'::regclass AND conname='chk_asist_pers_jornada') THEN
        ALTER TABLE public.ades_asistencia_personal
            ADD CONSTRAINT chk_asist_pers_jornada
            CHECK (tipo_jornada IN ('COMPLETA','MEDIA','NINGUNA','INCAPACIDAD','VACACIONES','PERMISO'));
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_asist_pers_persona ON public.ades_asistencia_personal (persona_id);
CREATE INDEX IF NOT EXISTS idx_asist_pers_fecha   ON public.ades_asistencia_personal (fecha DESC);
CREATE INDEX IF NOT EXISTS idx_asist_pers_activa  ON public.ades_asistencia_personal (is_active) WHERE is_active = TRUE;

COMMENT ON TABLE  public.ades_asistencia_personal IS 'Registro diario de asistencia del personal docente y administrativo. Captura hora de entrada/salida, retardos y justificaciones.';
COMMENT ON COLUMN public.ades_asistencia_personal.tipo_jornada IS 'COMPLETA=asistió jornada completa, MEDIA=media jornada, NINGUNA=falta, INCAPACIDAD/VACACIONES/PERMISO=ausencia justificada.';
COMMENT ON COLUMN public.ades_asistencia_personal.minutos_retardo IS 'Minutos de retardo calculados desde hora de entrada esperada. 0 si es_retardo=FALSE.';

SELECT auditoria.asignar_biu('public.ades_asistencia_personal');

-- ===========================
-- 4. Resumen
-- ===========================
DO $$ BEGIN
    RAISE NOTICE '=== Migración 041 aplicada: FASE 30 — RRHH Avanzado ===';
    RAISE NOTICE 'DP-003: ades_disponibilidad_docente';
    RAISE NOTICE 'DP-004: ades_expediente_laboral';
    RAISE NOTICE 'DP-005: ades_asistencia_personal';
END $$;
