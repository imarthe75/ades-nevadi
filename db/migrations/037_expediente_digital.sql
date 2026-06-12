/*
 * by Im@rthe
 * Fecha: 2026-06-11
 * Archivo: 037_expediente_digital.sql
 *
 * Descripcion:
 * FASE 28 - Gestion Documental y Expediente Digital
 * Crea el modelo de datos para el expediente digital del alumno integrado
 * con Paperless-ngx como motor OCR/gestion documental.
 *
 * Tablas afectadas:
 *   - ades_expedientes_alumno    : encabezado del expediente por alumno/ciclo
 *   - ades_expediente_documentos : documentos individuales con referencia a Paperless
 *
 * Funciones y triggers:
 *   - fn_calcular_completitud_expediente : recalcula el % de completitud
 *   - trg_expediente_completitud         : invoca la funcion tras cambios en documentos
 *
 * Solicitado y aprobado por Israel Martinez Hernandez
 *
 * Motor de Base de Datos: PostgreSQL
 * Version minima requerida: 18
 * Ambiente: desarrollo
 */

-- ===========================
-- 1. Tabla: ades_expedientes_alumno
-- ===========================
CREATE TABLE IF NOT EXISTS public.ades_expedientes_alumno ();

ALTER TABLE public.ades_expedientes_alumno
    ADD COLUMN IF NOT EXISTS id                 UUID        NOT NULL DEFAULT gen_random_uuid(),
    ADD COLUMN IF NOT EXISTS estudiante_id      UUID        NOT NULL,
    ADD COLUMN IF NOT EXISTS ciclo_escolar_id   UUID        NOT NULL,
    ADD COLUMN IF NOT EXISTS estado             VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    ADD COLUMN IF NOT EXISTS completitud_pct    NUMERIC(5,2) NOT NULL DEFAULT 0.00,
    ADD COLUMN IF NOT EXISTS revisado_por       UUID        NULL,
    ADD COLUMN IF NOT EXISTS fecha_revision     TIMESTAMPTZ NULL,
    ADD COLUMN IF NOT EXISTS observaciones      TEXT        NULL,
    -- Columnas de auditoria
    ADD COLUMN IF NOT EXISTS ref                UUID        NULL DEFAULT gen_random_uuid(),
    ADD COLUMN IF NOT EXISTS row_version        INTEGER     NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS fecha_creacion     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS fecha_modificacion TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS usuario_creacion   TEXT        NULL,
    ADD COLUMN IF NOT EXISTS usuario_modificacion TEXT      NULL;

-- ===========================
-- 2. Llave primaria: ades_expedientes_alumno
-- ===========================
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'public.ades_expedientes_alumno'::regclass
          AND contype = 'p'
    ) THEN
        ALTER TABLE public.ades_expedientes_alumno
            ADD CONSTRAINT pk_ades_expedientes_alumno PRIMARY KEY (id);
    END IF;
END $$;

-- Unicidad: un solo expediente por alumno y ciclo
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'public.ades_expedientes_alumno'::regclass
          AND conname = 'uq_expediente_alumno_ciclo'
    ) THEN
        ALTER TABLE public.ades_expedientes_alumno
            ADD CONSTRAINT uq_expediente_alumno_ciclo
            UNIQUE (estudiante_id, ciclo_escolar_id);
    END IF;
END $$;

-- CHECK de estado
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'public.ades_expedientes_alumno'::regclass
          AND conname = 'chk_expediente_estado'
    ) THEN
        ALTER TABLE public.ades_expedientes_alumno
            ADD CONSTRAINT chk_expediente_estado
            CHECK (estado IN ('PENDIENTE', 'INCOMPLETO', 'COMPLETO', 'VERIFICADO'));
    END IF;
END $$;

-- CHECK de completitud
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'public.ades_expedientes_alumno'::regclass
          AND conname = 'chk_expediente_completitud_pct'
    ) THEN
        ALTER TABLE public.ades_expedientes_alumno
            ADD CONSTRAINT chk_expediente_completitud_pct
            CHECK (completitud_pct BETWEEN 0.00 AND 100.00);
    END IF;
END $$;

-- ===========================
-- 3. Llaves foraneas: ades_expedientes_alumno
-- ===========================
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'public.ades_expedientes_alumno'::regclass
          AND conname = 'fk_expediente_estudiante'
    ) THEN
        ALTER TABLE public.ades_expedientes_alumno
            ADD CONSTRAINT fk_expediente_estudiante
            FOREIGN KEY (estudiante_id)
            REFERENCES public.ades_estudiantes(id)
            ON DELETE RESTRICT;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'public.ades_expedientes_alumno'::regclass
          AND conname = 'fk_expediente_ciclo'
    ) THEN
        ALTER TABLE public.ades_expedientes_alumno
            ADD CONSTRAINT fk_expediente_ciclo
            FOREIGN KEY (ciclo_escolar_id)
            REFERENCES public.ades_ciclos_escolares(id)
            ON DELETE RESTRICT;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'public.ades_expedientes_alumno'::regclass
          AND conname = 'fk_expediente_revisor'
    ) THEN
        ALTER TABLE public.ades_expedientes_alumno
            ADD CONSTRAINT fk_expediente_revisor
            FOREIGN KEY (revisado_por)
            REFERENCES public.ades_usuarios(id)
            ON DELETE SET NULL;
    END IF;
END $$;

-- ===========================
-- 1b. Tabla: ades_expediente_documentos
-- ===========================
CREATE TABLE IF NOT EXISTS public.ades_expediente_documentos ();

ALTER TABLE public.ades_expediente_documentos
    ADD COLUMN IF NOT EXISTS id               UUID        NOT NULL DEFAULT gen_random_uuid(),
    ADD COLUMN IF NOT EXISTS expediente_id    UUID        NOT NULL,
    ADD COLUMN IF NOT EXISTS paperless_doc_id INTEGER     NULL,
    ADD COLUMN IF NOT EXISTS tipo_documento   VARCHAR(30) NOT NULL DEFAULT 'OTRO',
    ADD COLUMN IF NOT EXISTS nombre_archivo   TEXT        NULL,
    ADD COLUMN IF NOT EXISTS url_preview      TEXT        NULL,
    ADD COLUMN IF NOT EXISTS estado_ocr       VARCHAR(15) NOT NULL DEFAULT 'PENDIENTE',
    ADD COLUMN IF NOT EXISTS ocr_texto        TEXT        NULL,
    ADD COLUMN IF NOT EXISTS metadatos_ia     JSONB       NULL,
    ADD COLUMN IF NOT EXISTS fecha_carga      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS cargado_por      UUID        NULL,
    -- Columnas de auditoria
    ADD COLUMN IF NOT EXISTS ref                UUID        NULL DEFAULT gen_random_uuid(),
    ADD COLUMN IF NOT EXISTS row_version        INTEGER     NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS fecha_creacion     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS fecha_modificacion TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS usuario_creacion   TEXT        NULL,
    ADD COLUMN IF NOT EXISTS usuario_modificacion TEXT      NULL;

-- ===========================
-- 2b. Llave primaria: ades_expediente_documentos
-- ===========================
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'public.ades_expediente_documentos'::regclass
          AND contype = 'p'
    ) THEN
        ALTER TABLE public.ades_expediente_documentos
            ADD CONSTRAINT pk_ades_expediente_documentos PRIMARY KEY (id);
    END IF;
END $$;

-- CHECK de tipo de documento
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'public.ades_expediente_documentos'::regclass
          AND conname = 'chk_doc_tipo'
    ) THEN
        ALTER TABLE public.ades_expediente_documentos
            ADD CONSTRAINT chk_doc_tipo
            CHECK (tipo_documento IN (
                'CURP',
                'ACTA_NACIMIENTO',
                'CERTIFICADO_PREV',
                'COMPROBANTE_DOMICILIO',
                'FOTOGRAFIA',
                'NSS',
                'CREDENCIAL_ESCOLAR',
                'CONSTANCIA_INSCRIPCION',
                'OTRO'
            ));
    END IF;
END $$;

-- CHECK de estado OCR
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'public.ades_expediente_documentos'::regclass
          AND conname = 'chk_doc_estado_ocr'
    ) THEN
        ALTER TABLE public.ades_expediente_documentos
            ADD CONSTRAINT chk_doc_estado_ocr
            CHECK (estado_ocr IN ('PENDIENTE', 'PROCESADO', 'ERROR'));
    END IF;
END $$;

-- ===========================
-- 3b. Llaves foraneas: ades_expediente_documentos
-- ===========================
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'public.ades_expediente_documentos'::regclass
          AND conname = 'fk_expdoc_expediente'
    ) THEN
        ALTER TABLE public.ades_expediente_documentos
            ADD CONSTRAINT fk_expdoc_expediente
            FOREIGN KEY (expediente_id)
            REFERENCES public.ades_expedientes_alumno(id)
            ON DELETE CASCADE;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'public.ades_expediente_documentos'::regclass
          AND conname = 'fk_expdoc_cargado_por'
    ) THEN
        ALTER TABLE public.ades_expediente_documentos
            ADD CONSTRAINT fk_expdoc_cargado_por
            FOREIGN KEY (cargado_por)
            REFERENCES public.ades_usuarios(id)
            ON DELETE SET NULL;
    END IF;
END $$;

-- ===========================
-- 4. Comentarios
-- ===========================
COMMENT ON TABLE public.ades_expedientes_alumno IS
    'Expediente digital del alumno por ciclo escolar. Encabezado que agrupa todos los documentos requeridos para la inscripcion y seguimiento escolar. Integrado con Paperless-ngx como motor OCR.';

COMMENT ON COLUMN public.ades_expedientes_alumno.id IS
    'Llave primaria UUID generada por gen_random_uuid().';
COMMENT ON COLUMN public.ades_expedientes_alumno.estudiante_id IS
    'Referencia al alumno propietario del expediente (FK a ades_estudiantes).';
COMMENT ON COLUMN public.ades_expedientes_alumno.ciclo_escolar_id IS
    'Ciclo escolar al que pertenece el expediente (FK a ades_ciclos_escolares).';
COMMENT ON COLUMN public.ades_expedientes_alumno.estado IS
    'Estado global del expediente: PENDIENTE (sin documentos), INCOMPLETO (faltan docs), COMPLETO (todos cargados), VERIFICADO (revisado por autoridad).';
COMMENT ON COLUMN public.ades_expedientes_alumno.completitud_pct IS
    'Porcentaje de documentos requeridos que han sido cargados. Recalculado automaticamente por trigger.';
COMMENT ON COLUMN public.ades_expedientes_alumno.revisado_por IS
    'Usuario que verifico el expediente (Director o Coordinador).';
COMMENT ON COLUMN public.ades_expedientes_alumno.fecha_revision IS
    'Fecha y hora de la verificacion oficial del expediente.';

COMMENT ON TABLE public.ades_expediente_documentos IS
    'Documentos individuales que conforman el expediente digital del alumno. Cada registro referencia un documento en Paperless-ngx via paperless_doc_id.';

COMMENT ON COLUMN public.ades_expediente_documentos.id IS
    'Llave primaria UUID generada por gen_random_uuid().';
COMMENT ON COLUMN public.ades_expediente_documentos.expediente_id IS
    'Expediente al que pertenece este documento (FK a ades_expedientes_alumno).';
COMMENT ON COLUMN public.ades_expediente_documentos.paperless_doc_id IS
    'ID del documento en la API REST de Paperless-ngx. NULL si el documento fue subido directamente sin pasar por Paperless.';
COMMENT ON COLUMN public.ades_expediente_documentos.tipo_documento IS
    'Clasificacion del tipo de documento segun catalogo interno de ADES.';
COMMENT ON COLUMN public.ades_expediente_documentos.estado_ocr IS
    'Estado del proceso OCR en Paperless-ngx: PENDIENTE (en cola), PROCESADO (texto extraido), ERROR (fallo OCR).';
COMMENT ON COLUMN public.ades_expediente_documentos.ocr_texto IS
    'Texto extraido por OCR (tesseract-spa). Usado para busqueda full-text.';
COMMENT ON COLUMN public.ades_expediente_documentos.metadatos_ia IS
    'Analisis del documento por IA (NVIDIA NIM). Incluye: entidades extraidas, validacion de coherencia, alertas.';

-- ===========================
-- 5. Indices
-- ===========================
CREATE INDEX IF NOT EXISTS idx_expedientes_estudiante_id
    ON public.ades_expedientes_alumno (estudiante_id);

CREATE INDEX IF NOT EXISTS idx_expedientes_ciclo_id
    ON public.ades_expedientes_alumno (ciclo_escolar_id);

CREATE INDEX IF NOT EXISTS idx_expedientes_estado
    ON public.ades_expedientes_alumno (estado);

CREATE INDEX IF NOT EXISTS idx_expdoc_expediente_id
    ON public.ades_expediente_documentos (expediente_id);

CREATE INDEX IF NOT EXISTS idx_expdoc_paperless_doc_id
    ON public.ades_expediente_documentos (paperless_doc_id)
    WHERE paperless_doc_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_expdoc_tipo
    ON public.ades_expediente_documentos (tipo_documento);

-- Indice GIN para busqueda full-text en texto OCR
CREATE INDEX IF NOT EXISTS idx_expdoc_ocr_texto_gin
    ON public.ades_expediente_documentos
    USING gin(to_tsvector('spanish', COALESCE(ocr_texto, '')));

-- ===========================
-- 5b. Funcion: fn_calcular_completitud_expediente
-- ===========================
CREATE OR REPLACE FUNCTION public.fn_calcular_completitud_expediente(p_expediente_id UUID)
RETURNS VOID
LANGUAGE plpgsql
AS $$
DECLARE
    v_total_requeridos  INTEGER;
    v_total_cargados    INTEGER;
    v_pct               NUMERIC(5,2);
    v_estado            VARCHAR(20);
    -- Tipos requeridos para cualquier nivel educativo
    v_tipos_requeridos  TEXT[] := ARRAY[
        'CURP',
        'ACTA_NACIMIENTO',
        'CERTIFICADO_PREV',
        'COMPROBANTE_DOMICILIO',
        'FOTOGRAFIA'
    ];
BEGIN
    v_total_requeridos := array_length(v_tipos_requeridos, 1);

    SELECT COUNT(DISTINCT tipo_documento)
    INTO v_total_cargados
    FROM public.ades_expediente_documentos
    WHERE expediente_id = p_expediente_id
      AND tipo_documento = ANY(v_tipos_requeridos);

    IF v_total_requeridos > 0 THEN
        v_pct := ROUND((v_total_cargados::NUMERIC / v_total_requeridos::NUMERIC) * 100, 2);
    ELSE
        v_pct := 0.00;
    END IF;

    -- Determinar estado segun completitud
    IF v_pct = 0 THEN
        v_estado := 'PENDIENTE';
    ELSIF v_pct < 100 THEN
        v_estado := 'INCOMPLETO';
    ELSE
        v_estado := 'COMPLETO';
    END IF;

    -- No degradar estado VERIFICADO a COMPLETO automaticamente
    UPDATE public.ades_expedientes_alumno
    SET completitud_pct    = v_pct,
        estado             = CASE
                               WHEN estado = 'VERIFICADO' THEN 'VERIFICADO'
                               ELSE v_estado
                             END,
        fecha_modificacion = NOW()
    WHERE id = p_expediente_id;

    RAISE NOTICE 'Expediente %: completitud=% %%, estado=%', p_expediente_id, v_pct, v_estado;
END;
$$;

COMMENT ON FUNCTION public.fn_calcular_completitud_expediente(UUID) IS
    'Recalcula el porcentaje de completitud y el estado del expediente digital segun los tipos de documento cargados. Invocada por trigger trg_expediente_completitud.';

-- ===========================
-- 6. Trigger: trg_expediente_completitud
-- ===========================
CREATE OR REPLACE FUNCTION public.trg_fn_expediente_completitud()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        PERFORM public.fn_calcular_completitud_expediente(OLD.expediente_id);
    ELSE
        PERFORM public.fn_calcular_completitud_expediente(NEW.expediente_id);
    END IF;
    RETURN NULL;
END;
$$;

COMMENT ON FUNCTION public.trg_fn_expediente_completitud() IS
    'Funcion trigger que llama a fn_calcular_completitud_expediente tras cualquier cambio en ades_expediente_documentos.';

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_trigger
        WHERE tgname = 'trg_expediente_completitud'
          AND tgrelid = 'public.ades_expediente_documentos'::regclass
    ) THEN
        CREATE TRIGGER trg_expediente_completitud
        AFTER INSERT OR UPDATE OR DELETE ON public.ades_expediente_documentos
        FOR EACH ROW EXECUTE FUNCTION public.trg_fn_expediente_completitud();
    END IF;
END $$;

-- ===========================
-- 6b. Triggers de auditoria: ades_expedientes_alumno
-- ===========================
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_trigger
        WHERE tgname = 'audit_biu'
          AND tgrelid = 'public.ades_expedientes_alumno'::regclass
    ) THEN
        CREATE TRIGGER audit_biu
        BEFORE INSERT OR UPDATE ON public.ades_expedientes_alumno
        FOR EACH ROW EXECUTE FUNCTION auditoria.auditoria_biu();
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_trigger
        WHERE tgname = 'audit_aiud'
          AND tgrelid = 'public.ades_expedientes_alumno'::regclass
    ) THEN
        CREATE TRIGGER audit_aiud
        AFTER INSERT OR UPDATE OR DELETE ON public.ades_expedientes_alumno
        FOR EACH ROW EXECUTE FUNCTION auditoria.auditoria_aiud();

        ALTER TABLE public.ades_expedientes_alumno DISABLE TRIGGER audit_aiud;
    END IF;
END $$;

-- ===========================
-- 6c. Triggers de auditoria: ades_expediente_documentos
-- ===========================
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_trigger
        WHERE tgname = 'audit_biu'
          AND tgrelid = 'public.ades_expediente_documentos'::regclass
    ) THEN
        CREATE TRIGGER audit_biu
        BEFORE INSERT OR UPDATE ON public.ades_expediente_documentos
        FOR EACH ROW EXECUTE FUNCTION auditoria.auditoria_biu();
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_trigger
        WHERE tgname = 'audit_aiud'
          AND tgrelid = 'public.ades_expediente_documentos'::regclass
    ) THEN
        CREATE TRIGGER audit_aiud
        AFTER INSERT OR UPDATE OR DELETE ON public.ades_expediente_documentos
        FOR EACH ROW EXECUTE FUNCTION auditoria.auditoria_aiud();

        ALTER TABLE public.ades_expediente_documentos DISABLE TRIGGER audit_aiud;
    END IF;
END $$;

-- ===========================
-- 7. Notas tecnicas
-- ===========================
-- 1. Script idempotente: usa CREATE TABLE IF NOT EXISTS + ALTER TABLE ADD COLUMN IF NOT EXISTS.
-- 2. PKs son UUID (gen_random_uuid()); FKs referencian columnas id UUID de tabla padre.
-- 3. La completitud se recalcula automaticamente via trigger AFTER en ades_expediente_documentos.
-- 4. paperless_doc_id es INTEGER (ID nativo de Paperless-ngx, no UUID).
-- 5. El estado VERIFICADO no se degrada automaticamente (requiere intervencion humana).
-- 6. audit_biu activo en ambas tablas; audit_aiud creado pero deshabilitado por defecto.
-- 7. Indice GIN en ocr_texto para busqueda full-text en espanol (tsvector 'spanish').

-- Fin del script
