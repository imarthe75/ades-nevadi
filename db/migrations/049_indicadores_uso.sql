-- =============================================================================
-- MIGRACIÓN 049 — Cierre de Ciclo Escolar e Indicadores de Uso
-- =============================================================================
-- Instituto Nevadi / ADES
-- Fecha: 2026-06-12
--
-- Cambios:
--   1. Agrega columna 'estado' a ades_ciclos_escolares para control de cierre.
--   2. Agrega vista v_indicadores_cierre_ciclo para consolidar métricas de cierre.
-- =============================================================================

BEGIN;

-- 1. Columna de estado para control de ciclo
ALTER TABLE ades_ciclos_escolares
    ADD COLUMN IF NOT EXISTS estado VARCHAR(20) DEFAULT 'ACTIVO' CHECK (estado IN ('ACTIVO', 'CERRADO'));

-- 2. Vista de indicadores de cierre de ciclo escolar
CREATE OR REPLACE VIEW v_indicadores_cierre_ciclo AS
SELECT 
    c.id AS ciclo_escolar_id,
    c.nombre_ciclo,
    c.nivel_educativo_id,
    n.nombre_nivel,
    c.fecha_inicio,
    c.fecha_fin,
    c.estado,
    -- Conteo de alumnos inscritos
    (SELECT COUNT(DISTINCT i.estudiante_id) 
     FROM ades_inscripciones i 
     WHERE i.ciclo_escolar_id = c.id AND i.is_active = TRUE) AS matricula_total,
    -- Conteo de docentes asignados
    (SELECT COUNT(DISTINCT ad.profesor_id) 
     FROM ades_asignaciones_docentes ad 
     JOIN ades_grupos g ON ad.grupo_id = g.id
     WHERE g.ciclo_escolar_id = c.id AND ad.is_active = TRUE) AS total_docentes,
    -- Promedio general del ciclo
    COALESCE((
        SELECT ROUND(AVG(cp.calificacion_final), 2)
        FROM ades_calificaciones_periodo cp
        JOIN ades_periodos_evaluacion pe ON cp.periodo_evaluacion_id = pe.id
        WHERE pe.ciclo_escolar_id = c.id AND cp.is_active = TRUE
    ), 0.0) AS promedio_general,
    -- Tasa de aprobación
    COALESCE((
        SELECT ROUND((COUNT(CASE WHEN cp.es_acreditado = TRUE THEN 1 END) * 100.0) / NULLIF(COUNT(cp.id), 0), 2)
        FROM ades_calificaciones_periodo cp
        JOIN ades_periodos_evaluacion pe ON cp.periodo_evaluacion_id = pe.id
        WHERE pe.ciclo_escolar_id = c.id AND cp.is_active = TRUE
    ), 100.00) AS tasa_aprobacion,
    -- Bajas registradas
    (SELECT COUNT(*) 
     FROM ades_bajas b 
     JOIN ades_inscripciones i ON b.estudiante_id = i.estudiante_id
     WHERE i.ciclo_escolar_id = c.id AND b.is_active = TRUE) AS total_bajas,
    -- Alumnos activos
    (SELECT COUNT(*) 
     FROM ades_inscripciones i 
     JOIN ades_estudiantes e ON i.estudiante_id = e.id
     WHERE i.ciclo_escolar_id = c.id AND i.is_active = TRUE AND e.is_active = TRUE) AS total_alumnos_activos
FROM ades_ciclos_escolares c
JOIN ades_niveles_educativos n ON c.nivel_educativo_id = n.id;

COMMENT ON VIEW v_indicadores_cierre_ciclo IS 'Consolidado estadístico para las actas oficiales de inicio y cierre de ciclo escolar.';

COMMIT;
