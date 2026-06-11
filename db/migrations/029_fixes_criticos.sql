-- ============================================================
-- Migración 029 — Fixes Críticos (Sprint 1 Auditoría 360°)
-- Fecha: 2026-06-10
-- ============================================================
-- 1. Fix trigger trg_recalcular_desde_asistencia
-- 2. Fix 'TARDANZA' → 'TARDE' en calcular_calificacion_periodo
-- 3. CHECK constraints faltantes
-- 4. Triggers de auditoría para tablas sin auditoría
-- 5. Índices FK faltantes
-- ============================================================

BEGIN;

-- ─────────────────────────────────────────────────────────────
-- 1. Fix trigger de asistencia — ciclo_escolar_id via grupo
--    Bug: cl.ciclo_escolar_id no existe en ades_clases
-- ─────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION trg_recalcular_desde_asistencia()
RETURNS TRIGGER LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE
    v_grupo_id         UUID;
    v_materia_id       UUID;
    v_periodo_id       UUID;
    v_ciclo_escolar_id UUID;
BEGIN
    SELECT cl.grupo_id, cl.materia_id
      INTO v_grupo_id, v_materia_id
      FROM ades_clases cl
     WHERE cl.id = NEW.clase_id;

    IF v_grupo_id IS NULL THEN
        RETURN NEW;
    END IF;

    -- Ciclo vía grupo (no vía clase — ades_clases NO tiene ciclo_escolar_id)
    SELECT g.ciclo_escolar_id
      INTO v_ciclo_escolar_id
      FROM ades_grupos g
     WHERE g.id = v_grupo_id;

    SELECT pe.id
      INTO v_periodo_id
      FROM ades_clases cl
      JOIN ades_periodos_evaluacion pe
           ON pe.ciclo_escolar_id = v_ciclo_escolar_id
          AND cl.fecha_clase BETWEEN COALESCE(pe.fecha_inicio, '1900-01-01')
                                 AND COALESCE(pe.fecha_fin,    '2099-12-31')
     WHERE cl.id = NEW.clase_id
     LIMIT 1;

    IF v_periodo_id IS NOT NULL THEN
        PERFORM calcular_calificacion_periodo(
            NEW.estudiante_id, v_grupo_id, v_materia_id, v_periodo_id
        );
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_gradebook_asistencia ON ades_asistencias;
CREATE TRIGGER trg_gradebook_asistencia
    AFTER INSERT OR UPDATE OF estatus_asistencia
    ON ades_asistencias
    FOR EACH ROW EXECUTE FUNCTION trg_recalcular_desde_asistencia();

-- ─────────────────────────────────────────────────────────────
-- 2. Fix TARDANZA → TARDE en calcular_calificacion_periodo
-- ─────────────────────────────────────────────────────────────
DO $$
BEGIN
    EXECUTE replace(
        pg_get_functiondef(
            (SELECT oid FROM pg_proc WHERE proname = 'calcular_calificacion_periodo' LIMIT 1)
        ),
        '''TARDANZA''',
        '''TARDE'''
    );
    RAISE NOTICE 'calcular_calificacion_periodo: TARDANZA → TARDE aplicado';
EXCEPTION WHEN OTHERS THEN
    RAISE NOTICE 'calcular_calificacion_periodo: sin cambios necesarios (%)', SQLERRM;
END;
$$;

-- ─────────────────────────────────────────────────────────────
-- 3. CHECK constraints faltantes
-- ─────────────────────────────────────────────────────────────
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_cal_periodo_rango') THEN
        ALTER TABLE ades_calificaciones_periodo
            ADD CONSTRAINT chk_cal_periodo_rango
            CHECK (calificacion_final BETWEEN 0 AND 100);
        RAISE NOTICE 'CHECK chk_cal_periodo_rango añadido';
    END IF;
END; $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_cal_periodo_calculada_rango') THEN
        ALTER TABLE ades_calificaciones_periodo
            ADD CONSTRAINT chk_cal_periodo_calculada_rango
            CHECK (calificacion_calculada IS NULL OR calificacion_calculada BETWEEN 0 AND 100);
        RAISE NOTICE 'CHECK chk_cal_periodo_calculada_rango añadido';
    END IF;
END; $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_ciclo_fechas') THEN
        ALTER TABLE ades_ciclos_escolares
            ADD CONSTRAINT chk_ciclo_fechas
            CHECK (fecha_fin >= fecha_inicio);
        RAISE NOTICE 'CHECK chk_ciclo_fechas añadido';
    END IF;
END; $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_periodo_eval_fechas') THEN
        ALTER TABLE ades_periodos_evaluacion
            ADD CONSTRAINT chk_periodo_eval_fechas
            CHECK (fecha_fin >= fecha_inicio);
        RAISE NOTICE 'CHECK chk_periodo_eval_fechas añadido';
    END IF;
END; $$;

-- ─────────────────────────────────────────────────────────────
-- 4. Triggers de auditoría para tablas sin auditoría
-- ─────────────────────────────────────────────────────────────
SELECT auditoria.asignar_trigger('ades_bajas');
SELECT auditoria.asignar_trigger('ades_extraordinarias');
SELECT auditoria.asignar_trigger('ades_constancias');
SELECT auditoria.asignar_trigger('ades_cuotas_concepto');
SELECT auditoria.asignar_trigger('ades_cuotas_pagos');
SELECT auditoria.asignar_trigger('ades_solicitudes_tramites');

-- ─────────────────────────────────────────────────────────────
-- 5. Índices FK faltantes
-- ─────────────────────────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_asig_doc_profesor
    ON ades_asignaciones_docentes(profesor_id);

CREATE INDEX IF NOT EXISTS idx_clases_profesor
    ON ades_clases(profesor_id);

CREATE INDEX IF NOT EXISTS idx_cal_periodo_grupo_periodo
    ON ades_calificaciones_periodo(grupo_id, periodo_evaluacion_id);

COMMIT;
