-- =============================================================================
-- MIGRACION 118d: Auditoría y Verificación FASE 1
-- =============================================================================
-- Objetivo: Aplicar triggers de auditoría a nuevas columnas
--           y verificar integridad referencial.
-- Fase: 1 (Foundation - Planeaciones Semanales)
-- Fecha: 2026-07-08
-- =============================================================================

BEGIN;

-- =============================================================================
-- 1. VERIFICAR INTEGRIDAD REFERENCIAL
-- =============================================================================

-- Verificar que todas las FK hacia ades_planeacion_clases son válidas
DO $$
DECLARE
    v_huerfanos INT;
BEGIN
    SELECT COUNT(*) INTO v_huerfanos
    FROM ades_tareas
    WHERE planeacion_clase_id IS NOT NULL
      AND planeacion_clase_id NOT IN (SELECT ref FROM ades_planeacion_clases);

    IF v_huerfanos > 0 THEN
        RAISE WARNING 'ADVERTENCIA: % tareas con planeacion_clase_id huérfanos', v_huerfanos;
    ELSE
        RAISE NOTICE 'OK: Todas las tareas tienen planeacion_clase_id válidos o NULL';
    END IF;

    -- Igual para evaluaciones
    SELECT COUNT(*) INTO v_huerfanos
    FROM ades_calificaciones_evaluaciones
    WHERE planeacion_clase_id IS NOT NULL
      AND planeacion_clase_id NOT IN (SELECT ref FROM ades_planeacion_clases);

    IF v_huerfanos > 0 THEN
        RAISE WARNING 'ADVERTENCIA: % evaluaciones con planeacion_clase_id huérfanos', v_huerfanos;
    ELSE
        RAISE NOTICE 'OK: Todas las evaluaciones tienen planeacion_clase_id válidos o NULL';
    END IF;
END $$;

-- =============================================================================
-- 2. APLICAR AUDITORÍA A NUEVAS COLUMNAS
-- =============================================================================
-- Las tablas ya tienen audit_biu, pero aseguramos que cubre las nuevas columnas

-- Verificar que audit_biu está en ades_tareas
SELECT CASE
    WHEN EXISTS (
        SELECT 1 FROM information_schema.triggers
        WHERE trigger_name = 'audit_biu'
          AND event_object_table = 'ades_tareas'
    ) THEN 'OK: audit_biu existe en ades_tareas'
    ELSE 'ADVERTENCIA: audit_biu NO existe en ades_tareas'
END as audit_tareas_status;

-- Verificar que audit_biu está en ades_calificaciones_evaluaciones
SELECT CASE
    WHEN EXISTS (
        SELECT 1 FROM information_schema.triggers
        WHERE trigger_name = 'audit_biu'
          AND event_object_table = 'ades_calificaciones_evaluaciones'
    ) THEN 'OK: audit_biu existe en ades_calificaciones_evaluaciones'
    ELSE 'ADVERTENCIA: audit_biu NO existe en ades_calificaciones_evaluaciones'
END as audit_evals_status;

-- =============================================================================
-- 3. CREAR VISTA: vw_planeacion_con_aprendizajes_evaluados
-- =============================================================================
-- Vista para dashboard: muestra cuales aprendizajes de una planeación
-- tienen tareas/evaluaciones ya creadas.

CREATE OR REPLACE VIEW vw_planeacion_con_aprendizajes_evaluados AS
SELECT
    pc.ref as planeacion_id,
    pc.numero_trimestre,
    pc.numero_semana,
    g.nombre_grupo,
    m.nombre_materia,
    pae.aprendizaje_esperado_id,
    ae.codigo as aprendizaje_codigo,
    ae.descripcion as aprendizaje_descripcion,
    COUNT(DISTINCT t.ref) as cantidad_tareas,
    COUNT(DISTINCT ce.ref) as cantidad_evaluaciones,
    COUNT(DISTINCT CASE WHEN t.ref IS NOT NULL THEN t.ref END) +
    COUNT(DISTINCT CASE WHEN ce.ref IS NOT NULL THEN ce.ref END) as total_instrumentos
FROM ades_planeacion_clases pc
LEFT JOIN ades_planeacion_aprendizajes pae ON pc.ref = pae.planeacion_clase_id
LEFT JOIN ades_aprendizajes_esperados ae ON pae.aprendizaje_esperado_id = ae.ref
JOIN ades_grupos g ON pc.grupo_id = g.ref
JOIN ades_temas t_tema ON pc.tema_id = t_tema.ref
JOIN ades_materias m ON t_tema.materia_id = m.ref
LEFT JOIN ades_tareas t ON pc.ref = t.planeacion_clase_id
    AND (t.aprendizajes_esperados IS NULL OR pae.aprendizaje_esperado_id = ANY(t.aprendizajes_esperados))
LEFT JOIN ades_calificaciones_evaluaciones ce ON pc.ref = ce.planeacion_clase_id
    AND (ce.aprendizajes_esperados IS NULL OR pae.aprendizaje_esperado_id = ANY(ce.aprendizajes_esperados))
WHERE pc.is_active = TRUE
GROUP BY
    pc.ref, pc.numero_trimestre, pc.numero_semana,
    g.nombre_grupo, m.nombre_materia,
    pae.aprendizaje_esperado_id,
    ae.codigo, ae.descripcion;

-- =============================================================================
-- 4. CREAR VISTA: vw_aprendizajes_por_nivel_materia
-- =============================================================================
-- Vista de referencia: cuantos aprendizajes tenemos por nivel/materia

CREATE OR REPLACE VIEW vw_aprendizajes_por_nivel_materia AS
SELECT
    n.nombre_nivel,
    m.nombre_materia,
    COUNT(DISTINCT ae.ref) as total_aprendizajes,
    COUNT(DISTINCT ae.competencia_id) as competencias_cubiertas,
    STRING_AGG(DISTINCT g.numero_grado::text, ', ' ORDER BY g.numero_grado::text) as grados
FROM ades_aprendizajes_esperados ae
JOIN ades_grados g ON ae.grado_id = g.ref
JOIN ades_niveles_educativos n ON g.nivel_educativo_id = n.ref
JOIN ades_materias m ON ae.materia_id = m.ref
WHERE ae.activo = TRUE
GROUP BY n.nombre_nivel, m.nombre_materia
ORDER BY n.nombre_nivel, m.nombre_materia;

-- =============================================================================
-- 5. REPORTE FINAL: Cobertura FASE 1
-- =============================================================================

SELECT '═══════════════════════════════════════════════════════════════' as "REPORTE FASE 1";

SELECT
    'COMPETENCIAS Y APRENDIZAJES' as "COMPONENTE",
    COUNT(*) as "CANTIDAD"
FROM ades_competencias WHERE activo = TRUE
UNION ALL
SELECT 'APRENDIZAJES ESPERADOS' as "COMPONENTE",
    COUNT(*) FROM ades_aprendizajes_esperados WHERE activo = TRUE
UNION ALL
SELECT 'PLANEACIONES DE CLASE' as "COMPONENTE",
    COUNT(*) FROM ades_planeacion_clases WHERE is_active = TRUE
UNION ALL
SELECT 'PLANEACIÓN-APRENDIZAJE (VÍNCULOS)' as "COMPONENTE",
    COUNT(*) FROM ades_planeacion_aprendizajes
UNION ALL
SELECT 'TAREAS CON PLANEACIÓN' as "COMPONENTE",
    COUNT(*) FROM ades_tareas WHERE planeacion_clase_id IS NOT NULL AND is_active = TRUE
UNION ALL
SELECT 'EVALUACIONES CON PLANEACIÓN' as "COMPONENTE",
    COUNT(*) FROM ades_calificaciones_evaluaciones WHERE planeacion_clase_id IS NOT NULL AND is_active = TRUE;

SELECT '═══════════════════════════════════════════════════════════════' as "---";

COMMIT;

-- =============================================================================
-- NOTAS DE EJECUCIÓN
-- =============================================================================
-- 1. Ejecutar: psql -U ades_admin -d ades < 118d_auditoria_fase1_planeacion.sql
-- 2. Revisar warnings/notices en output
-- 3. Consultar vistas:
--    - SELECT * FROM vw_planeacion_con_aprendizajes_evaluados;
--    - SELECT * FROM vw_aprendizajes_por_nivel_materia;
-- 4. Si todo OK: FASE 1 DATABASE COMPLETADA ✅
-- 5. Próxima: Backend Spring Boot (EntidadesJPA, Services, Controllers)
-- =============================================================================
