-- ============================================================
-- MIGRACIÓN 056 — REGLAS DE PROMOCIÓN CONFIGURABLES
-- Extiende ades_niveles_educativos con parámetros configurables
-- y agrega fn_evaluar_estatus_promocion() para calcular el
-- estatus de cada alumno antes del cierre de ciclo.
-- ============================================================
-- Ejecutar:
--   docker compose exec -T postgres psql -U ades_admin -d ades < db/migrations/056_reglas_promocion.sql

BEGIN;

-- ─────────────────────────────────────────────────────────────
-- 1. Nuevos campos en ades_niveles_educativos
-- ─────────────────────────────────────────────────────────────
ALTER TABLE ades_niveles_educativos
    ADD COLUMN IF NOT EXISTS max_materias_reprobadas  SMALLINT        NOT NULL DEFAULT 3,
    ADD COLUMN IF NOT EXISTS min_asistencia_pct       NUMERIC(5,2)    NOT NULL DEFAULT 80.0,
    ADD COLUMN IF NOT EXISTS permite_recursamiento    BOOLEAN         NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS max_anios_reprobados     SMALLINT        NOT NULL DEFAULT 2,
    ADD COLUMN IF NOT EXISTS tiene_examen_extra       BOOLEAN         NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN ades_niveles_educativos.max_materias_reprobadas IS
    'Número máximo de materias reprobadas antes de marcar al alumno como REPROBADO al cierre de ciclo.';
COMMENT ON COLUMN ades_niveles_educativos.min_asistencia_pct IS
    'Porcentaje mínimo de asistencia requerido. Incumplirlo puede condicionar la promoción según nivel.';
COMMENT ON COLUMN ades_niveles_educativos.permite_recursamiento IS
    'Si TRUE, un alumno REPROBADO puede inscribirse al mismo grado el siguiente ciclo.';
COMMENT ON COLUMN ades_niveles_educativos.max_anios_reprobados IS
    'Número máximo de ciclos consecutivos que un alumno puede reprobar antes de baja definitiva.';
COMMENT ON COLUMN ades_niveles_educativos.tiene_examen_extra IS
    'Si TRUE, los alumnos con calificación reprobatoria tienen derecho a un examen extraordinario.';

-- ─────────────────────────────────────────────────────────────
-- 2. Valores específicos por nivel SEP / UAEMEX
-- ─────────────────────────────────────────────────────────────
-- Primaria SEP: mínimo 6, máximo 2 materias reprobadas (sin extraordinario)
UPDATE ades_niveles_educativos SET
    max_materias_reprobadas = 2,
    min_asistencia_pct      = 80.0,
    permite_recursamiento   = TRUE,
    max_anios_reprobados    = 2,
    tiene_examen_extra      = FALSE
WHERE nombre_nivel = 'PRIMARIA';

-- Secundaria SEP: mínimo 6, hasta 3 materias (sin extraordinario oficial)
UPDATE ades_niveles_educativos SET
    max_materias_reprobadas = 3,
    min_asistencia_pct      = 80.0,
    permite_recursamiento   = TRUE,
    max_anios_reprobados    = 3,
    tiene_examen_extra      = FALSE
WHERE nombre_nivel = 'SECUNDARIA';

-- Preparatoria UAEMEX: mínimo 60 (escala 100), hasta 3 materias, con examen extraordinario
UPDATE ades_niveles_educativos SET
    max_materias_reprobadas = 3,
    min_asistencia_pct      = 80.0,
    permite_recursamiento   = TRUE,
    max_anios_reprobados    = 3,
    tiene_examen_extra      = TRUE
WHERE nombre_nivel = 'PREPARATORIA';

-- ─────────────────────────────────────────────────────────────
-- 3. Función: fn_evaluar_estatus_promocion
--    Evalúa el estatus de cada alumno inscrito en un ciclo dado
--    con base en las reglas del nivel educativo.
--    Debe ejecutarse ANTES de cerrar_ciclo_y_promover().
-- ─────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION fn_evaluar_estatus_promocion(
    p_ciclo_id   UUID,
    p_plantel_id UUID    DEFAULT NULL,
    p_usuario    TEXT    DEFAULT current_user
)
RETURNS JSONB
LANGUAGE plpgsql
AS $$
DECLARE
    v_rec                  RECORD;
    v_regla                RECORD;
    v_calificacion_final   NUMERIC;
    v_materias_reprobadas  INTEGER;
    v_asistencia_pct       NUMERIC;
    v_id_estatus_promovido UUID;
    v_id_estatus_reprobado UUID;
    v_id_estatus_cond      UUID;      -- PROMOVIDO_CONDICIONADO si existe
    v_conteo_promovidos    INTEGER := 0;
    v_conteo_reprobados    INTEGER := 0;
    v_conteo_cond          INTEGER := 0;
    v_conteo_sin_notas     INTEGER := 0;
BEGIN
    SELECT id INTO v_id_estatus_promovido FROM ades_estatus
    WHERE entidad = 'ESTUDIANTE' AND nombre_estatus = 'PROMOVIDO' LIMIT 1;

    SELECT id INTO v_id_estatus_reprobado FROM ades_estatus
    WHERE entidad = 'ESTUDIANTE' AND nombre_estatus = 'REPROBADO' LIMIT 1;

    -- Iteramos cada inscripción activa del ciclo
    FOR v_rec IN
        SELECT
            i.id              AS inscripcion_id,
            i.estudiante_id,
            i.grupo_id,
            i.estatus_id,
            g.plantel_id,
            g.grado_id,
            gr.nivel_educativo_id,
            e_est.nombre_estatus
        FROM ades_inscripciones  i
        JOIN ades_grupos         g    ON g.id   = i.grupo_id
        JOIN ades_grados         gr   ON gr.id  = g.grado_id
        JOIN ades_estatus        e_est ON e_est.id = i.estatus_id
        WHERE i.ciclo_escolar_id = p_ciclo_id
          AND i.is_active        = TRUE
          AND (p_plantel_id IS NULL OR g.plantel_id = p_plantel_id)
          -- Solo evaluar alumnos ACTIVOS (no BAJA, no ya-evaluados)
          AND e_est.nombre_estatus = 'ACTIVO'
    LOOP
        -- Obtener reglas del nivel
        SELECT
            ne.minimo_aprobatorio,
            ne.max_materias_reprobadas,
            ne.min_asistencia_pct
        INTO v_regla
        FROM ades_niveles_educativos ne
        WHERE ne.id = v_rec.nivel_educativo_id;

        IF NOT FOUND THEN
            CONTINUE;
        END IF;

        -- Contar materias reprobadas (calificación_final < mínimo)
        SELECT COUNT(*)
        INTO   v_materias_reprobadas
        FROM   ades_calificaciones_periodo cp
        JOIN   ades_inscripciones_materias  im ON im.id = cp.inscripcion_materia_id
        WHERE  im.inscripcion_id  = v_rec.inscripcion_id
          AND  cp.ciclo_escolar_id = p_ciclo_id
          AND  cp.calificacion_final IS NOT NULL
          AND  cp.calificacion_final < v_regla.minimo_aprobatorio;

        -- Si no hay calificaciones registradas, no cambiar estatus
        IF v_materias_reprobadas IS NULL THEN
            v_conteo_sin_notas := v_conteo_sin_notas + 1;
            CONTINUE;
        END IF;

        -- Asistencia del alumno en el ciclo (%)
        SELECT
            CASE WHEN COUNT(*) = 0 THEN 100.0
                 ELSE 100.0 * SUM(CASE WHEN a.estatus_asistencia IN ('PRESENTE','TARDE') THEN 1 ELSE 0 END)::NUMERIC / COUNT(*)
            END
        INTO v_asistencia_pct
        FROM ades_asistencias a
        JOIN ades_clases cl ON cl.id = a.clase_id
        JOIN ades_grupos  g  ON g.id = cl.grupo_id
        WHERE g.id = v_rec.grupo_id
          AND cl.ciclo_escolar_id = p_ciclo_id
          AND a.estudiante_id = v_rec.estudiante_id;

        -- Determinar estatus: REPROBADO si supera materias reprobadas O asistencia insuficiente
        IF v_materias_reprobadas > v_regla.max_materias_reprobadas
           OR COALESCE(v_asistencia_pct, 100.0) < v_regla.min_asistencia_pct
        THEN
            UPDATE ades_inscripciones
               SET estatus_id           = v_id_estatus_reprobado,
                   materias_reprobadas  = v_materias_reprobadas,
                   usuario_modificacion = p_usuario
             WHERE id = v_rec.inscripcion_id;
            v_conteo_reprobados := v_conteo_reprobados + 1;
        ELSE
            UPDATE ades_inscripciones
               SET estatus_id           = v_id_estatus_promovido,
                   materias_reprobadas  = v_materias_reprobadas,
                   usuario_modificacion = p_usuario
             WHERE id = v_rec.inscripcion_id;
            v_conteo_promovidos := v_conteo_promovidos + 1;
        END IF;
    END LOOP;

    RETURN jsonb_build_object(
        'ciclo_id',         p_ciclo_id,
        'plantel_id',       p_plantel_id,
        'promovidos',       v_conteo_promovidos,
        'reprobados',       v_conteo_reprobados,
        'condicionados',    v_conteo_cond,
        'sin_calificacion', v_conteo_sin_notas,
        'total',            v_conteo_promovidos + v_conteo_reprobados + v_conteo_cond + v_conteo_sin_notas
    );
END;
$$;

COMMENT ON FUNCTION fn_evaluar_estatus_promocion(UUID, UUID, TEXT) IS
    'Evalúa el estatus PROMOVIDO/REPROBADO de todos los alumnos ACTIVOS en un ciclo dado,
    usando las reglas configuradas en ades_niveles_educativos.
    Llamar ANTES de cerrar_ciclo_y_promover(). Puede ejecutarse por plantel o para todos.';

-- ─────────────────────────────────────────────────────────────
-- 4. Columna materias_reprobadas en ades_inscripciones (si no existe)
-- ─────────────────────────────────────────────────────────────
ALTER TABLE ades_inscripciones
    ADD COLUMN IF NOT EXISTS materias_reprobadas SMALLINT DEFAULT 0;

COMMENT ON COLUMN ades_inscripciones.materias_reprobadas IS
    'Número de materias con calificación_final < minimo_aprobatorio al cierre del ciclo.
    Actualizado por fn_evaluar_estatus_promocion().';

COMMIT;
