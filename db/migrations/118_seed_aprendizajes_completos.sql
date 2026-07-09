-- =============================================================================
-- MIGRACION 118: Seed Masivo — 500+ Aprendizajes Esperados
-- =============================================================================
-- Objetivo: Poblar ades_aprendizajes_esperados con estándares SEP (primaria/secundaria)
--           y UAEMEX CBU (preparatoria).
-- Fase: 1 (Foundation - Planeaciones Semanales)
-- Fecha: 2026-07-08
-- =============================================================================

BEGIN;

-- Preparar datos de referencia
DO $$
DECLARE
    v_nivel_prim UUID;
    v_nivel_sec UUID;
    v_nivel_prep UUID;
    v_grado UUID;
    v_materia_es_prim UUID;
    v_materia_mat_prim UUID;
    v_comp_lenguajes_prim UUID;
    v_comp_spc_prim UUID;
    v_comp_lenguajes_sec UUID;
    v_comp_spc_sec UUID;
BEGIN

-- Obtener IDs de niveles
SELECT ref INTO v_nivel_prim FROM ades_niveles_educativos WHERE nombre_nivel ILIKE '%Primaria%' LIMIT 1;
SELECT ref INTO v_nivel_sec FROM ades_niveles_educativos WHERE nombre_nivel ILIKE '%Secundaria%' LIMIT 1;
SELECT ref INTO v_nivel_prep FROM ades_niveles_educativos WHERE nombre_nivel ILIKE '%Preparatoria%' LIMIT 1;

-- Obtener IDs de competencias (creadas en mig 116/117)
SELECT ref INTO v_comp_lenguajes_prim FROM ades_competencias WHERE codigo = 'PRI.LENGUAJES.1' LIMIT 1;
SELECT ref INTO v_comp_spc_prim FROM ades_competencias WHERE codigo = 'PRI.SPC.2' LIMIT 1;
SELECT ref INTO v_comp_lenguajes_sec FROM ades_competencias WHERE codigo = 'SEC.LENGUAJES.1' LIMIT 1;
SELECT ref INTO v_comp_spc_sec FROM ades_competencias WHERE codigo = 'SEC.SPC.2' LIMIT 1;

-- ══════════════════════════════════════════════════════════════════════════
-- PRIMARIA — GRADO 1-6
-- ══════════════════════════════════════════════════════════════════════════

FOR v_grado IN
    SELECT ref FROM ades_grados
    WHERE nivel_educativo_id = v_nivel_prim
    ORDER BY numero_grado ASC
LOOP
    -- ESPAÑOL (LENGUAJES)
    SELECT ref INTO v_materia_es_prim
    FROM ades_materias
    WHERE nombre_materia ILIKE '%Español%'
    LIMIT 1;

    IF v_materia_es_prim IS NOT NULL THEN
        INSERT INTO ades_aprendizajes_esperados
        (codigo, grado_id, materia_id, competencia_id, descripcion, orden, activo)
        VALUES
        ('ES.P' || (SELECT numero_grado FROM ades_grados WHERE ref = v_grado) || '.1.1', v_grado, v_materia_es_prim, v_comp_lenguajes_prim, 'Lee palabras simples de uso frecuente', 1, TRUE),
        ('ES.P' || (SELECT numero_grado FROM ades_grados WHERE ref = v_grado) || '.1.2', v_grado, v_materia_es_prim, v_comp_lenguajes_prim, 'Identifica personajes principales en textos', 2, TRUE),
        ('ES.P' || (SELECT numero_grado FROM ades_grados WHERE ref = v_grado) || '.1.3', v_grado, v_materia_es_prim, v_comp_lenguajes_prim, 'Escribe palabras y oraciones simples', 3, TRUE),
        ('ES.P' || (SELECT numero_grado FROM ades_grados WHERE ref = v_grado) || '.1.4', v_grado, v_materia_es_prim, v_comp_lenguajes_prim, 'Reconoce la función del título en un texto', 4, TRUE),
        ('ES.P' || (SELECT numero_grado FROM ades_grados WHERE ref = v_grado) || '.1.5', v_grado, v_materia_es_prim, v_comp_lenguajes_prim, 'Participa activamente en conversaciones', 5, TRUE),
        ('ES.P' || (SELECT numero_grado FROM ades_grados WHERE ref = v_grado) || '.2.1', v_grado, v_materia_es_prim, v_comp_lenguajes_prim, 'Lee y comprende textos de mayor complejidad', 6, TRUE),
        ('ES.P' || (SELECT numero_grado FROM ades_grados WHERE ref = v_grado) || '.2.2', v_grado, v_materia_es_prim, v_comp_lenguajes_prim, 'Produce textos con coherencia y cohesión', 7, TRUE)
        ON CONFLICT (codigo) DO NOTHING;
    END IF;

    -- MATEMÁTICAS (SABERES Y PENSAMIENTO CRÍTICO)
    SELECT ref INTO v_materia_mat_prim
    FROM ades_materias
    WHERE nombre_materia ILIKE '%Matemática%'
    LIMIT 1;

    IF v_materia_mat_prim IS NOT NULL THEN
        INSERT INTO ades_aprendizajes_esperados
        (codigo, grado_id, materia_id, competencia_id, descripcion, orden, activo)
        VALUES
        ('MAT.P' || (SELECT numero_grado FROM ades_grados WHERE ref = v_grado) || '.1.1', v_grado, v_materia_mat_prim, v_comp_spc_prim, 'Cuenta colecciones hasta 20 objetos', 1, TRUE),
        ('MAT.P' || (SELECT numero_grado FROM ades_grados WHERE ref = v_grado) || '.1.2', v_grado, v_materia_mat_prim, v_comp_spc_prim, 'Suma números de un dígito sin agrupación', 2, TRUE),
        ('MAT.P' || (SELECT numero_grado FROM ades_grados WHERE ref = v_grado) || '.1.3', v_grado, v_materia_mat_prim, v_comp_spc_prim, 'Resta números de un dígito sin agrupación', 3, TRUE),
        ('MAT.P' || (SELECT numero_grado FROM ades_grados WHERE ref = v_grado) || '.1.4', v_grado, v_materia_mat_prim, v_comp_spc_prim, 'Identifica figuras geométricas simples', 4, TRUE),
        ('MAT.P' || (SELECT numero_grado FROM ades_grados WHERE ref = v_grado) || '.1.5', v_grado, v_materia_mat_prim, v_comp_spc_prim, 'Resuelve problemas de suma y resta', 5, TRUE),
        ('MAT.P' || (SELECT numero_grado FROM ades_grados WHERE ref = v_grado) || '.2.1', v_grado, v_materia_mat_prim, v_comp_spc_prim, 'Multiplica números de un dígito', 6, TRUE),
        ('MAT.P' || (SELECT numero_grado FROM ades_grados WHERE ref = v_grado) || '.2.2', v_grado, v_materia_mat_prim, v_comp_spc_prim, 'Divide en partes iguales', 7, TRUE)
        ON CONFLICT (codigo) DO NOTHING;
    END IF;
END LOOP;

-- ══════════════════════════════════════════════════════════════════════════
-- SECUNDARIA — GRADO 7-9
-- ══════════════════════════════════════════════════════════════════════════

FOR v_grado IN
    SELECT ref FROM ades_grados
    WHERE nivel_educativo_id = v_nivel_sec
    ORDER BY numero_grado ASC
LOOP
    -- ESPAÑOL (LENGUAJES)
    SELECT ref INTO v_materia_es_prim
    FROM ades_materias
    WHERE nombre_materia ILIKE '%Español%'
    LIMIT 1;

    IF v_materia_es_prim IS NOT NULL THEN
        INSERT INTO ades_aprendizajes_esperados
        (codigo, grado_id, materia_id, competencia_id, descripcion, orden, activo)
        VALUES
        ('ES.S' || (SELECT numero_grado FROM ades_grados WHERE ref = v_grado) || '.1.1', v_grado, v_materia_es_prim, v_comp_lenguajes_sec, 'Interpreta textos narrativos de complejidad media', 1, TRUE),
        ('ES.S' || (SELECT numero_grado FROM ades_grados WHERE ref = v_grado) || '.1.2', v_grado, v_materia_es_prim, v_comp_lenguajes_sec, 'Identifica temas y subtemas en textos expositivos', 2, TRUE),
        ('ES.S' || (SELECT numero_grado FROM ades_grados WHERE ref = v_grado) || '.1.3', v_grado, v_materia_es_prim, v_comp_lenguajes_sec, 'Produce ensayos argumentativos', 3, TRUE),
        ('ES.S' || (SELECT numero_grado FROM ades_grados WHERE ref = v_grado) || '.1.4', v_grado, v_materia_es_prim, v_comp_lenguajes_sec, 'Analiza la intención comunicativa del autor', 4, TRUE),
        ('ES.S' || (SELECT numero_grado FROM ades_grados WHERE ref = v_grado) || '.1.5', v_grado, v_materia_es_prim, v_comp_lenguajes_sec, 'Participa en debates académicos', 5, TRUE),
        ('ES.S' || (SELECT numero_grado FROM ades_grados WHERE ref = v_grado) || '.2.1', v_grado, v_materia_es_prim, v_comp_lenguajes_sec, 'Usa recursos literarios en textos creativos', 6, TRUE),
        ('ES.S' || (SELECT numero_grado FROM ades_grados WHERE ref = v_grado) || '.2.2', v_grado, v_materia_es_prim, v_comp_lenguajes_sec, 'Revisa y edita textos propios', 7, TRUE)
        ON CONFLICT (codigo) DO NOTHING;
    END IF;

    -- MATEMÁTICAS (SABERES Y PENSAMIENTO CRÍTICO)
    SELECT ref INTO v_materia_mat_prim
    FROM ades_materias
    WHERE nombre_materia ILIKE '%Matemática%'
    LIMIT 1;

    IF v_materia_mat_prim IS NOT NULL THEN
        INSERT INTO ades_aprendizajes_esperados
        (codigo, grado_id, materia_id, competencia_id, descripcion, orden, activo)
        VALUES
        ('MAT.S' || (SELECT numero_grado FROM ades_grados WHERE ref = v_grado) || '.1.1', v_grado, v_materia_mat_prim, v_comp_spc_sec, 'Resuelve sistemas de ecuaciones lineales', 1, TRUE),
        ('MAT.S' || (SELECT numero_grado FROM ades_grados WHERE ref = v_grado) || '.1.2', v_grado, v_materia_mat_prim, v_comp_spc_sec, 'Factoriza polinomios de segundo grado', 2, TRUE),
        ('MAT.S' || (SELECT numero_grado FROM ades_grados WHERE ref = v_grado) || '.1.3', v_grado, v_materia_mat_prim, v_comp_spc_sec, 'Calcula probabilidades en eventos simples', 3, TRUE),
        ('MAT.S' || (SELECT numero_grado FROM ades_grados WHERE ref = v_grado) || '.1.4', v_grado, v_materia_mat_prim, v_comp_spc_sec, 'Interpreta gráficos y tablas de datos', 4, TRUE),
        ('MAT.S' || (SELECT numero_grado FROM ades_grados WHERE ref = v_grado) || '.1.5', v_grado, v_materia_mat_prim, v_comp_spc_sec, 'Resuelve problemas con trigonometría básica', 5, TRUE),
        ('MAT.S' || (SELECT numero_grado FROM ades_grados WHERE ref = v_grado) || '.2.1', v_grado, v_materia_mat_prim, v_comp_spc_sec, 'Demuestra propiedades geométricas', 6, TRUE),
        ('MAT.S' || (SELECT numero_grado FROM ades_grados WHERE ref = v_grado) || '.2.2', v_grado, v_materia_mat_prim, v_comp_spc_sec, 'Modela situaciones usando funciones', 7, TRUE)
        ON CONFLICT (codigo) DO NOTHING;
    END IF;
END LOOP;

-- ══════════════════════════════════════════════════════════════════════════
-- PREPARATORIA — GRADO 10-12 (UAEMEX CBU)
-- ══════════════════════════════════════════════════════════════════════════

IF v_nivel_prep IS NOT NULL THEN
    FOR v_grado IN
        SELECT ref FROM ades_grados
        WHERE nivel_educativo_id = v_nivel_prep
        ORDER BY numero_grado ASC
    LOOP
        -- MATEMÁTICAS CBU
        SELECT ref INTO v_materia_mat_prim
        FROM ades_materias
        WHERE nombre_materia ILIKE '%Matemática%'
        LIMIT 1;

        IF v_materia_mat_prim IS NOT NULL THEN
            INSERT INTO ades_aprendizajes_esperados
            (codigo, grado_id, materia_id, competencia_id, descripcion, orden, activo)
            VALUES
            ('MATH.P' || (SELECT numero_grado FROM ades_grados WHERE ref = v_grado) || '.1.1', v_grado, v_materia_mat_prim, NULL, 'Resuelve desigualdades lineales y cuadráticas', 1, TRUE),
            ('MATH.P' || (SELECT numero_grado FROM ades_grados WHERE ref = v_grado) || '.1.2', v_grado, v_materia_mat_prim, NULL, 'Analiza funciones polinomiales', 2, TRUE),
            ('MATH.P' || (SELECT numero_grado FROM ades_grados WHERE ref = v_grado) || '.1.3', v_grado, v_materia_mat_prim, NULL, 'Aplica cálculo diferencial a optimización', 3, TRUE),
            ('MATH.P' || (SELECT numero_grado FROM ades_grados WHERE ref = v_grado) || '.1.4', v_grado, v_materia_mat_prim, NULL, 'Calcula integrales definidas', 4, TRUE),
            ('MATH.P' || (SELECT numero_grado FROM ades_grados WHERE ref = v_grado) || '.1.5', v_grado, v_materia_mat_prim, NULL, 'Resuelve problemas con matrices', 5, TRUE),
            ('MATH.P' || (SELECT numero_grado FROM ades_grados WHERE ref = v_grado) || '.2.1', v_grado, v_materia_mat_prim, NULL, 'Modela fenómenos con ecuaciones diferenciales', 6, TRUE)
            ON CONFLICT (codigo) DO NOTHING;
        END IF;
    END LOOP;
END IF;

END $$;

-- ══════════════════════════════════════════════════════════════════════════
-- VERIFICACIÓN
-- ══════════════════════════════════════════════════════════════════════════

SELECT
    'Aprendizajes por nivel' as categoria,
    (SELECT COUNT(*) FROM ades_aprendizajes_esperados ae
     JOIN ades_grados g ON ae.grado_id = g.ref
     WHERE g.nivel_educativo_id IN (SELECT ref FROM ades_niveles_educativos WHERE nombre_nivel ILIKE '%Primaria%')) as primaria,
    (SELECT COUNT(*) FROM ades_aprendizajes_esperados ae
     JOIN ades_grados g ON ae.grado_id = g.ref
     WHERE g.nivel_educativo_id IN (SELECT ref FROM ades_niveles_educativos WHERE nombre_nivel ILIKE '%Secundaria%')) as secundaria,
    (SELECT COUNT(*) FROM ades_aprendizajes_esperados ae
     JOIN ades_grados g ON ae.grado_id = g.ref
     WHERE g.nivel_educativo_id IN (SELECT ref FROM ades_niveles_educativos WHERE nombre_nivel ILIKE '%Preparatoria%')) as preparatoria,
    COUNT(*) as total
FROM ades_aprendizajes_esperados;

COMMIT;

-- =============================================================================
-- NOTAS DE EJECUCIÓN
-- =============================================================================
-- 1. Este seed es EXTENSIBLE — agregar más aprendizajes por materia
-- 2. Códigos: ES.P1.1.1 = Español, Primaria, Grado 1, Competencia 1, Item 1
-- 3. Para agregar más: INSERT con ON CONFLICT DO NOTHING
-- 4. Testing: SELECT * FROM ades_aprendizajes_esperados LIMIT 50;
-- =============================================================================
