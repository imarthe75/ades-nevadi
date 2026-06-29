/*
 * by Im@rthe
 * Fecha: 2026-06-29
 * Archivo: 20260629_0002_ades_nevadi.sql
 *
 * Descripcion:
 * Creacion de tabla ades_horario_indisponibilidad para la matriz
 * de 3 estados (DISPONIBLE, CONDICIONAL, NO_DISPONIBLE) de aSc.
 *
 * Solicitado y aprobado por Israel Martínez Hernández
 *
 * Motor de Base de Datos: PostgreSQL
 * Version minima requerida: 18
 * Ambiente: desarrollo
 */

-- ===========================
-- 1. Creacion de tabla y columnas
-- ===========================
CREATE TABLE IF NOT EXISTS public.ades_horario_indisponibilidad ();

ALTER TABLE public.ades_horario_indisponibilidad
    ADD COLUMN IF NOT EXISTS id uuid NOT NULL DEFAULT gen_random_uuid(),
    ADD COLUMN IF NOT EXISTS profesor_id uuid NOT NULL,
    ADD COLUMN IF NOT EXISTS ciclo_escolar_id uuid NOT NULL,
    ADD COLUMN IF NOT EXISTS franja_id uuid NOT NULL,
    ADD COLUMN IF NOT EXISTS tipo character varying(20) NOT NULL DEFAULT 'NO_DISPONIBLE',
    ADD COLUMN IF NOT EXISTS is_active boolean NOT NULL DEFAULT true,
    ADD COLUMN IF NOT EXISTS row_version integer DEFAULT 0,
    ADD COLUMN IF NOT EXISTS fecha_creacion timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS usuario_creacion character varying(100),
    ADD COLUMN IF NOT EXISTS fecha_modificacion timestamp with time zone,
    ADD COLUMN IF NOT EXISTS usuario_modificacion character varying(100),
    ADD COLUMN IF NOT EXISTS ref uuid DEFAULT gen_random_uuid();

-- ===========================
-- 2. Llave primaria
-- ===========================
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'public.ades_horario_indisponibilidad'::regclass
          AND contype = 'p'
    ) THEN
        ALTER TABLE public.ades_horario_indisponibilidad
            ADD CONSTRAINT pk_ades_horario_indisponibilidad PRIMARY KEY (id);
    END IF;
END $$;

-- ===========================
-- 3. Llaves foraneas
-- ===========================
DO $$
BEGIN
    -- FK Profesores
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'public.ades_horario_indisponibilidad'::regclass
          AND contype = 'f' AND conname = 'fk_indisponibilidad_profesor'
    ) THEN
        ALTER TABLE public.ades_horario_indisponibilidad
            ADD CONSTRAINT fk_indisponibilidad_profesor
            FOREIGN KEY (profesor_id) REFERENCES public.ades_profesores(id);
    END IF;

    -- FK Ciclo
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'public.ades_horario_indisponibilidad'::regclass
          AND contype = 'f' AND conname = 'fk_indisponibilidad_ciclo'
    ) THEN
        ALTER TABLE public.ades_horario_indisponibilidad
            ADD CONSTRAINT fk_indisponibilidad_ciclo
            FOREIGN KEY (ciclo_escolar_id) REFERENCES public.ades_ciclos_escolares(id);
    END IF;

    -- FK Franja
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'public.ades_horario_indisponibilidad'::regclass
          AND contype = 'f' AND conname = 'fk_indisponibilidad_franja'
    ) THEN
        ALTER TABLE public.ades_horario_indisponibilidad
            ADD CONSTRAINT fk_indisponibilidad_franja
            FOREIGN KEY (franja_id) REFERENCES public.ades_horario_franjas(id);
    END IF;
END $$;

-- Unicidad para evitar duplicados en la misma franja para un profesor y ciclo
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'public.ades_horario_indisponibilidad'::regclass
          AND contype = 'u' AND conname = 'uq_indisponibilidad_profesor_franja'
    ) THEN
        ALTER TABLE public.ades_horario_indisponibilidad
            ADD CONSTRAINT uq_indisponibilidad_profesor_franja UNIQUE (profesor_id, ciclo_escolar_id, franja_id);
    END IF;
END $$;

-- ===========================
-- 4. Comentarios
-- ===========================
COMMENT ON TABLE public.ades_horario_indisponibilidad IS 'Tabla para matriz de disponibilidad de 3 estados por profesor, conectada a franjas de horario.';
COMMENT ON COLUMN public.ades_horario_indisponibilidad.id IS 'Llave primaria UUID.';
COMMENT ON COLUMN public.ades_horario_indisponibilidad.tipo IS 'Estado: DISPONIBLE, CONDICIONAL, NO_DISPONIBLE.';

-- ===========================
-- 5. Triggers de auditoria
-- ===========================
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_trigger
        WHERE tgname = 'ades_horario_indisponibilidad_audit_biu'
          AND tgrelid = 'public.ades_horario_indisponibilidad'::regclass
    ) THEN
        CREATE TRIGGER ades_horario_indisponibilidad_audit_biu
        BEFORE INSERT OR UPDATE ON public.ades_horario_indisponibilidad
        FOR EACH ROW EXECUTE FUNCTION auditoria.fn_auditoria_biu();
    END IF;
END $$;

-- ===========================
-- 6. Comentarios tecnicos
-- ===========================
-- 1. Script idempotente para tabla de indisponibilidad.
-- Fin del script
