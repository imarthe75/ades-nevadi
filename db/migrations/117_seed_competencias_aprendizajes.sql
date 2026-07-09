-- MIGRACION 117: Seed Data - Competencias y Aprendizajes Esperados
BEGIN;

-- Competencias Primaria
INSERT INTO ades_competencias (codigo, nombre, descripcion, nivel_educativo_id, campo_formativo, orden, activo)
SELECT 'PRI.LENGUAJES.1', 'Comunicarse en español', 'Lectura, escritura y expresión oral', ref, 'Lenguajes', 1, TRUE FROM ades_niveles_educativos WHERE nombre_nivel ILIKE '%Primaria%' LIMIT 1;

INSERT INTO ades_competencias (codigo, nombre, descripcion, nivel_educativo_id, campo_formativo, orden, activo)
SELECT 'PRI.SPC.2', 'Pensamiento matemático', 'Resolver problemas matemáticos', ref, 'Saberes y Pensamiento Crítico', 2, TRUE FROM ades_niveles_educativos WHERE nombre_nivel ILIKE '%Primaria%' LIMIT 1;

INSERT INTO ades_competencias (codigo, nombre, descripcion, nivel_educativo_id, campo_formativo, orden, activo)
SELECT 'SEC.LENGUAJES.1', 'Comunicación avanzada', 'Textos complejos en español', ref, 'Lenguajes', 1, TRUE FROM ades_niveles_educativos WHERE nombre_nivel ILIKE '%Secundaria%' LIMIT 1;

INSERT INTO ades_competencias (codigo, nombre, descripcion, nivel_educativo_id, campo_formativo, orden, activo)
SELECT 'SEC.SPC.2', 'Pensamiento matemático avanzado', 'Modelamiento y resolución de problemas', ref, 'Saberes y Pensamiento Crítico', 2, TRUE FROM ades_niveles_educativos WHERE nombre_nivel ILIKE '%Secundaria%' LIMIT 1;

-- Aprendizajes Esperados - Primaria
INSERT INTO ades_aprendizajes_esperados (codigo, grado_id, materia_id, competencia_id, descripcion, orden, activo)
SELECT 'ES.1.1.1', g.ref, m.ref, c.ref, 'Lee palabras simples', 1, TRUE
FROM ades_grados g, ades_materias m, ades_competencias c
WHERE g.numero_grado = 1 AND m.nombre_materia ILIKE '%Español%' AND c.codigo = 'PRI.LENGUAJES.1'
LIMIT 1;

INSERT INTO ades_aprendizajes_esperados (codigo, grado_id, materia_id, competencia_id, descripcion, orden, activo)
SELECT 'MAT.1.1.1', g.ref, m.ref, c.ref, 'Cuenta colecciones hasta 20 objetos', 1, TRUE
FROM ades_grados g, ades_materias m, ades_competencias c
WHERE g.numero_grado = 1 AND m.nombre_materia ILIKE '%Matemática%' AND c.codigo = 'PRI.SPC.2'
LIMIT 1;

-- Aprendizajes Esperados - Secundaria
INSERT INTO ades_aprendizajes_esperados (codigo, grado_id, materia_id, competencia_id, descripcion, orden, activo)
SELECT 'ES.SEC.1.1', g.ref, m.ref, c.ref, 'Interpreta textos narrativos', 1, TRUE
FROM ades_grados g, ades_materias m, ades_competencias c
WHERE g.numero_grado = 7 AND m.nombre_materia ILIKE '%Español%' AND c.codigo = 'SEC.LENGUAJES.1'
LIMIT 1;

INSERT INTO ades_aprendizajes_esperados (codigo, grado_id, materia_id, competencia_id, descripcion, orden, activo)
SELECT 'MAT.SEC.1.1', g.ref, m.ref, c.ref, 'Resuelve problemas algebraicos', 1, TRUE
FROM ades_grados g, ades_materias m, ades_competencias c
WHERE g.numero_grado = 7 AND m.nombre_materia ILIKE '%Matemática%' AND c.codigo = 'SEC.SPC.2'
LIMIT 1;

-- Verificación
SELECT COUNT(*) as competencias FROM ades_competencias;
SELECT COUNT(*) as aprendizajes FROM ades_aprendizajes_esperados;

COMMIT;
