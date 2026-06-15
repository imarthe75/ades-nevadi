-- ==========================================
-- Migración 045: Módulo de Admisiones y Bajas
-- ==========================================

BEGIN;

-- 1. Agregar columnas para la evaluación diagnóstica a ades_solicitudes_admision
ALTER TABLE public.ades_solicitudes_admision
    ADD COLUMN IF NOT EXISTS puntuacion_diagnostico NUMERIC(5,2) NULL,
    ADD COLUMN IF NOT EXISTS observaciones_diagnostico TEXT NULL;

COMMENT ON COLUMN public.ades_solicitudes_admision.puntuacion_diagnostico IS 'Puntaje obtenido en el examen diagnóstico de admisión (PE-003)';
COMMENT ON COLUMN public.ades_solicitudes_admision.observaciones_diagnostico IS 'Observaciones y comentarios del evaluador del examen diagnóstico (PE-003)';

-- 2. Crear función y trigger para liberar cupos al registrar una baja
CREATE OR REPLACE FUNCTION public.fn_baja_estudiante()
RETURNS TRIGGER AS $$
BEGIN
    -- Si se especifica inscripcion_id, desactivar esa inscripción
    IF NEW.inscripcion_id IS NOT NULL THEN
        UPDATE public.ades_inscripciones
        SET is_active = FALSE,
            fecha_modificacion = NOW(),
            usuario_modificacion = COALESCE(NEW.usuario_creacion, 'system')
        WHERE id = NEW.inscripcion_id;
    ELSE
        -- Si no se especifica, desactivar todas las inscripciones activas del estudiante
        UPDATE public.ades_inscripciones
        SET is_active = FALSE,
            fecha_modificacion = NOW(),
            usuario_modificacion = COALESCE(NEW.usuario_creacion, 'system')
        WHERE estudiante_id = NEW.estudiante_id AND is_active = TRUE;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_baja_estudiante ON public.ades_bajas;

CREATE TRIGGER trg_baja_estudiante
AFTER INSERT ON public.ades_bajas
FOR EACH ROW
EXECUTE FUNCTION public.fn_baja_estudiante();

COMMENT ON TRIGGER trg_baja_estudiante ON public.ades_bajas IS 'Libera automáticamente el cupo en el grupo al inactivar la inscripción del alumno tras una baja.';

COMMIT;
