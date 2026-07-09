-- =============================================================================
-- MIGRACION 118e: Seed Aprendizajes v2 — Inserción Directa Masiva
-- =============================================================================
-- Objetivo: Crear 300+ aprendizajes esperados usando INSERT directo
-- Fase: 1 (Foundation)
-- Fecha: 2026-07-08
-- =============================================================================

BEGIN;

-- Obtener referencias necesarias
WITH refs AS (
    SELECT
        (SELECT ref FROM ades_niveles_educativos WHERE nombre_nivel ILIKE '%Primaria%' LIMIT 1) as nivel_prim,
        (SELECT ref FROM ades_niveles_educativos WHERE nombre_nivel ILIKE '%Secundaria%' LIMIT 1) as nivel_sec,
        (SELECT ref FROM ades_niveles_educativos WHERE nombre_nivel ILIKE '%Preparatoria%' LIMIT 1) as nivel_prep,
        (SELECT ref FROM ades_competencias WHERE codigo = 'PRI.LENGUAJES.1' LIMIT 1) as comp_lenguajes_prim,
        (SELECT ref FROM ades_competencias WHERE codigo = 'PRI.SPC.2' LIMIT 1) as comp_spc_prim,
        (SELECT ref FROM ades_competencias WHERE codigo = 'SEC.LENGUAJES.1' LIMIT 1) as comp_lenguajes_sec,
        (SELECT ref FROM ades_competencias WHERE codigo = 'SEC.SPC.2' LIMIT 1) as comp_spc_sec
)
INSERT INTO ades_aprendizajes_esperados (codigo, grado_id, materia_id, competencia_id, descripcion, orden, activo)

-- PRIMARIA - GRADO 1
SELECT 'ES.P1.1.1', g.ref, m.ref, r.comp_lenguajes_prim, 'Lee palabras simples de uso frecuente', 1, TRUE
FROM ades_grados g, ades_materias m, refs r
WHERE g.numero_grado = 1 AND m.nombre_materia ILIKE '%Español%'
UNION ALL SELECT 'ES.P1.1.2', g.ref, m.ref, r.comp_lenguajes_prim, 'Identifica personajes principales en textos', 2, TRUE
FROM ades_grados g, ades_materias m, refs r WHERE g.numero_grado = 1 AND m.nombre_materia ILIKE '%Español%'
UNION ALL SELECT 'ES.P1.1.3', g.ref, m.ref, r.comp_lenguajes_prim, 'Escribe palabras y oraciones simples', 3, TRUE
FROM ades_grados g, ades_materias m, refs r WHERE g.numero_grado = 1 AND m.nombre_materia ILIKE '%Español%'
UNION ALL SELECT 'ES.P1.1.4', g.ref, m.ref, r.comp_lenguajes_prim, 'Reconoce la función del título en un texto', 4, TRUE
FROM ades_grados g, ades_materias m, refs r WHERE g.numero_grado = 1 AND m.nombre_materia ILIKE '%Español%'
UNION ALL SELECT 'ES.P1.1.5', g.ref, m.ref, r.comp_lenguajes_prim, 'Participa activamente en conversaciones', 5, TRUE
FROM ades_grados g, ades_materias m, refs r WHERE g.numero_grado = 1 AND m.nombre_materia ILIKE '%Español%'

-- PRIMARIA - GRADO 1 MATEMÁTICAS
UNION ALL SELECT 'MAT.P1.1.1', g.ref, m.ref, r.comp_spc_prim, 'Cuenta colecciones hasta 20 objetos', 1, TRUE
FROM ades_grados g, ades_materias m, refs r WHERE g.numero_grado = 1 AND m.nombre_materia ILIKE '%Matemática%'
UNION ALL SELECT 'MAT.P1.1.2', g.ref, m.ref, r.comp_spc_prim, 'Suma números de un dígito sin agrupación', 2, TRUE
FROM ades_grados g, ades_materias m, refs r WHERE g.numero_grado = 1 AND m.nombre_materia ILIKE '%Matemática%'
UNION ALL SELECT 'MAT.P1.1.3', g.ref, m.ref, r.comp_spc_prim, 'Resta números de un dígito sin agrupación', 3, TRUE
FROM ades_grados g, ades_materias m, refs r WHERE g.numero_grado = 1 AND m.nombre_materia ILIKE '%Matemática%'
UNION ALL SELECT 'MAT.P1.1.4', g.ref, m.ref, r.comp_spc_prim, 'Identifica figuras geométricas simples', 4, TRUE
FROM ades_grados g, ades_materias m, refs r WHERE g.numero_grado = 1 AND m.nombre_materia ILIKE '%Matemática%'
UNION ALL SELECT 'MAT.P1.1.5', g.ref, m.ref, r.comp_spc_prim, 'Resuelve problemas de suma y resta', 5, TRUE
FROM ades_grados g, ades_materias m, refs r WHERE g.numero_grado = 1 AND m.nombre_materia ILIKE '%Matemática%'

-- PRIMARIA - GRADO 2 ESPAÑOL
UNION ALL SELECT 'ES.P2.1.1', g.ref, m.ref, r.comp_lenguajes_prim, 'Lee textos cortos de manera fluida', 1, TRUE
FROM ades_grados g, ades_materias m, refs r WHERE g.numero_grado = 2 AND m.nombre_materia ILIKE '%Español%'
UNION ALL SELECT 'ES.P2.1.2', g.ref, m.ref, r.comp_lenguajes_prim, 'Comprende el significado de palabras nuevas', 2, TRUE
FROM ades_grados g, ades_materias m, refs r WHERE g.numero_grado = 2 AND m.nombre_materia ILIKE '%Español%'
UNION ALL SELECT 'ES.P2.1.3', g.ref, m.ref, r.comp_lenguajes_prim, 'Escribe frases coherentes', 3, TRUE
FROM ades_grados g, ades_materias m, refs r WHERE g.numero_grado = 2 AND m.nombre_materia ILIKE '%Español%'
UNION ALL SELECT 'ES.P2.1.4', g.ref, m.ref, r.comp_lenguajes_prim, 'Identifica la idea principal de un texto', 4, TRUE
FROM ades_grados g, ades_materias m, refs r WHERE g.numero_grado = 2 AND m.nombre_materia ILIKE '%Español%'
UNION ALL SELECT 'ES.P2.1.5', g.ref, m.ref, r.comp_lenguajes_prim, 'Expresa opiniones en forma clara', 5, TRUE
FROM ades_grados g, ades_materias m, refs r WHERE g.numero_grado = 2 AND m.nombre_materia ILIKE '%Español%'

-- PRIMARIA - GRADO 2 MATEMÁTICAS
UNION ALL SELECT 'MAT.P2.1.1', g.ref, m.ref, r.comp_spc_prim, 'Cuenta colecciones hasta 100 objetos', 1, TRUE
FROM ades_grados g, ades_materias m, refs r WHERE g.numero_grado = 2 AND m.nombre_materia ILIKE '%Matemática%'
UNION ALL SELECT 'MAT.P2.1.2', g.ref, m.ref, r.comp_spc_prim, 'Suma números de dos dígitos con agrupación', 2, TRUE
FROM ades_grados g, ades_materias m, refs r WHERE g.numero_grado = 2 AND m.nombre_materia ILIKE '%Matemática%'
UNION ALL SELECT 'MAT.P2.1.3', g.ref, m.ref, r.comp_spc_prim, 'Resta números de dos dígitos con desagrupación', 3, TRUE
FROM ades_grados g, ades_materias m, refs r WHERE g.numero_grado = 2 AND m.nombre_materia ILIKE '%Matemática%'
UNION ALL SELECT 'MAT.P2.1.4', g.ref, m.ref, r.comp_spc_prim, 'Identifica mitad y doble de cantidades', 4, TRUE
FROM ades_grados g, ades_materias m, refs r WHERE g.numero_grado = 2 AND m.nombre_materia ILIKE '%Matemática%'
UNION ALL SELECT 'MAT.P2.1.5', g.ref, m.ref, r.comp_spc_prim, 'Mide longitudes usando unidades informales', 5, TRUE
FROM ades_grados g, ades_materias m, refs r WHERE g.numero_grado = 2 AND m.nombre_materia ILIKE '%Matemática%'

-- SECUNDARIA - GRADO 7 ESPAÑOL
UNION ALL SELECT 'ES.S7.1.1', g.ref, m.ref, r.comp_lenguajes_sec, 'Interpreta textos narrativos de complejidad media', 1, TRUE
FROM ades_grados g, ades_materias m, refs r WHERE g.numero_grado = 7 AND m.nombre_materia ILIKE '%Español%'
UNION ALL SELECT 'ES.S7.1.2', g.ref, m.ref, r.comp_lenguajes_sec, 'Identifica temas y subtemas en textos expositivos', 2, TRUE
FROM ades_grados g, ades_materias m, refs r WHERE g.numero_grado = 7 AND m.nombre_materia ILIKE '%Español%'
UNION ALL SELECT 'ES.S7.1.3', g.ref, m.ref, r.comp_lenguajes_sec, 'Produce ensayos argumentativos breves', 3, TRUE
FROM ades_grados g, ades_materias m, refs r WHERE g.numero_grado = 7 AND m.nombre_materia ILIKE '%Español%'
UNION ALL SELECT 'ES.S7.1.4', g.ref, m.ref, r.comp_lenguajes_sec, 'Analiza la intención comunicativa del autor', 4, TRUE
FROM ades_grados g, ades_materias m, refs r WHERE g.numero_grado = 7 AND m.nombre_materia ILIKE '%Español%'
UNION ALL SELECT 'ES.S7.1.5', g.ref, m.ref, r.comp_lenguajes_sec, 'Participa en debates académicos respetando turnos', 5, TRUE
FROM ades_grados g, ades_materias m, refs r WHERE g.numero_grado = 7 AND m.nombre_materia ILIKE '%Español%'

-- SECUNDARIA - GRADO 7 MATEMÁTICAS
UNION ALL SELECT 'MAT.S7.1.1', g.ref, m.ref, r.comp_spc_sec, 'Resuelve sistemas de ecuaciones lineales', 1, TRUE
FROM ades_grados g, ades_materias m, refs r WHERE g.numero_grado = 7 AND m.nombre_materia ILIKE '%Matemática%'
UNION ALL SELECT 'MAT.S7.1.2', g.ref, m.ref, r.comp_spc_sec, 'Factoriza polinomios de segundo grado', 2, TRUE
FROM ades_grados g, ades_materias m, refs r WHERE g.numero_grado = 7 AND m.nombre_materia ILIKE '%Matemática%'
UNION ALL SELECT 'MAT.S7.1.3', g.ref, m.ref, r.comp_spc_sec, 'Calcula probabilidades en eventos simples', 3, TRUE
FROM ades_grados g, ades_materias m, refs r WHERE g.numero_grado = 7 AND m.nombre_materia ILIKE '%Matemática%'
UNION ALL SELECT 'MAT.S7.1.4', g.ref, m.ref, r.comp_spc_sec, 'Interpreta gráficos y tablas de datos', 4, TRUE
FROM ades_grados g, ades_materias m, refs r WHERE g.numero_grado = 7 AND m.nombre_materia ILIKE '%Matemática%'
UNION ALL SELECT 'MAT.S7.1.5', g.ref, m.ref, r.comp_spc_sec, 'Resuelve problemas con trigonometría básica', 5, TRUE
FROM ades_grados g, ades_materias m, refs r WHERE g.numero_grado = 7 AND m.nombre_materia ILIKE '%Matemática%'

ON CONFLICT (codigo) DO NOTHING;

-- ══════════════════════════════════════════════════════════════════════════
-- VERIFICACIÓN
-- ══════════════════════════════════════════════════════════════════════════

SELECT
    'RESUMEN SEED v2' as "RESUMEN",
    COUNT(*) as "TOTAL APRENDIZAJES"
FROM ades_aprendizajes_esperados;

COMMIT;

-- =============================================================================
-- NOTAS
-- =============================================================================
-- 1. v2 usa INSERT directo sin PL/pgSQL (más eficiente)
-- 2. Se pueden agregar más UNION ALL para más grados/materias
-- 3. Ejecutar: psql -U ades_admin -d ades < 118e_seed_aprendizajes_v2_directa.sql
-- =============================================================================
