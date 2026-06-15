-- ─────────────────────────────────────────────────────────────────────────────
-- 044_materias_completas_pri_sec.sql
-- 1. Agrega columna `orden` faltante en ades_materias_plan (causa del 500)
-- 2. Agrega materias tradicionales PRI (Español, Matemáticas, etc.)
-- 3. Reactiva 11 materias SEC inactivas (ESP, MAT, ART, EDF, FCE, GEO, HIS, ING, CNT, TEC, TUT)
-- 4. Agrega materias de ciencias SEC: Biología (1°), Física (2°), Química (3°)
-- 5. Crea entradas de plan para todas las materias nuevas/reactivadas
-- 6. Agrega temas para las materias nuevas
-- ─────────────────────────────────────────────────────────────────────────────

BEGIN;

-- ═══════════════════════════════════════════════════════════════════════
-- BLOQUE 1: Columna `orden` en ades_materias_plan (faltaba en la BD)
-- ═══════════════════════════════════════════════════════════════════════
ALTER TABLE ades_materias_plan ADD COLUMN IF NOT EXISTS orden INTEGER;


-- ═══════════════════════════════════════════════════════════════════════
-- BLOQUE 2: Materias tradicionales PRIMARIA
-- ═══════════════════════════════════════════════════════════════════════
INSERT INTO ades_materias (nombre_materia, clave_materia, nivel_educativo_id, horas_semana, tipo_materia,
                            reporta_a_sep_uaemex, incluir_en_boleta, ponderacion_default)
SELECT v.nombre, v.clave, n.id, v.hrs, 'OFICIAL_SEP_PRIMARIA'::text, TRUE, TRUE, 0.1250
FROM (VALUES
  ('Español',                     'PRI-ESP', 5.0),
  ('Matemáticas',                 'PRI-MAT', 5.0),
  ('Ciencias Naturales',          'PRI-CNT', 3.0),
  ('Historia',                    'PRI-HIS', 2.0),
  ('Geografía',                   'PRI-GEO', 2.0),
  ('Formación Cívica y Ética',    'PRI-FCE', 1.0),
  ('Tecnología',                  'PRI-TEC', 2.0),
  ('Tutoría',                     'PRI-TUT', 1.0)
) AS v(nombre, clave, hrs)
JOIN ades_niveles_educativos n ON n.nombre_nivel = 'PRIMARIA'
ON CONFLICT (nombre_materia, nivel_educativo_id) DO NOTHING;


-- ═══════════════════════════════════════════════════════════════════════
-- BLOQUE 3: Reactivar materias SEC inactivas
-- ═══════════════════════════════════════════════════════════════════════
UPDATE ades_materias
SET is_active = TRUE
WHERE clave_materia IN ('SEC-ESP','SEC-MAT','SEC-ART','SEC-EDF','SEC-FCE',
                        'SEC-GEO','SEC-HIS','SEC-ING','SEC-CNT','SEC-TEC','SEC-TUT')
  AND is_active = FALSE;


-- ═══════════════════════════════════════════════════════════════════════
-- BLOQUE 4: Materias de ciencias por grado (SEC)
-- ═══════════════════════════════════════════════════════════════════════
INSERT INTO ades_materias (nombre_materia, clave_materia, nivel_educativo_id, horas_semana, tipo_materia,
                            reporta_a_sep_uaemex, incluir_en_boleta, ponderacion_default)
SELECT v.nombre, v.clave, n.id, v.hrs, 'OFICIAL_SEP_SECUNDARIA'::text, TRUE, TRUE, 0.1250
FROM (VALUES
  ('Biología', 'SEC-BIO', 3.0),
  ('Física',   'SEC-FIS', 3.0),
  ('Química',  'SEC-QUI', 3.0)
) AS v(nombre, clave, hrs)
JOIN ades_niveles_educativos n ON n.nombre_nivel = 'SECUNDARIA'
ON CONFLICT (nombre_materia, nivel_educativo_id) DO NOTHING;


-- ═══════════════════════════════════════════════════════════════════════
-- BLOQUE 5: Plan entries PRI — todas las materias activas → todos los grados PRI
-- Ciclo PRI 2026-2027: 019e8f74-d148-7c7c-94de-f1500e73faed
-- ═══════════════════════════════════════════════════════════════════════
INSERT INTO ades_materias_plan (materia_id, grado_id, ciclo_escolar_id, horas_semana, es_obligatoria)
SELECT m.id, gr.id, '019e8f74-d148-7c7c-94de-f1500e73faed'::uuid, m.horas_semana, TRUE
FROM ades_materias m
JOIN ades_niveles_educativos n ON n.id = m.nivel_educativo_id AND n.nombre_nivel = 'PRIMARIA'
JOIN ades_grados gr ON gr.nivel_educativo_id = n.id AND gr.is_active = TRUE
WHERE m.is_active = TRUE
ON CONFLICT (materia_id, grado_id, ciclo_escolar_id) DO NOTHING;


-- ═══════════════════════════════════════════════════════════════════════
-- BLOQUE 6: Plan entries SEC
-- Ciclo SEC 2026-2027: 019e8f74-d149-735b-823a-0f253f33474c
-- ═══════════════════════════════════════════════════════════════════════
-- Materias generales SEC → todos los grados (excepto ciencias graduales)
INSERT INTO ades_materias_plan (materia_id, grado_id, ciclo_escolar_id, horas_semana, es_obligatoria)
SELECT m.id, gr.id, '019e8f74-d149-735b-823a-0f253f33474c'::uuid, m.horas_semana, TRUE
FROM ades_materias m
JOIN ades_niveles_educativos n ON n.id = m.nivel_educativo_id AND n.nombre_nivel = 'SECUNDARIA'
JOIN ades_grados gr ON gr.nivel_educativo_id = n.id AND gr.is_active = TRUE
WHERE m.is_active = TRUE
  AND m.clave_materia NOT IN ('SEC-BIO','SEC-FIS','SEC-QUI')
ON CONFLICT (materia_id, grado_id, ciclo_escolar_id) DO NOTHING;

-- Biología → solo 1° SEC
INSERT INTO ades_materias_plan (materia_id, grado_id, ciclo_escolar_id, horas_semana, es_obligatoria)
SELECT m.id, gr.id, '019e8f74-d149-735b-823a-0f253f33474c'::uuid, m.horas_semana, TRUE
FROM ades_materias m
JOIN ades_niveles_educativos n ON n.id = m.nivel_educativo_id AND n.nombre_nivel = 'SECUNDARIA'
JOIN ades_grados gr ON gr.nivel_educativo_id = n.id AND gr.is_active = TRUE AND gr.numero_grado = 1
WHERE m.clave_materia = 'SEC-BIO'
ON CONFLICT (materia_id, grado_id, ciclo_escolar_id) DO NOTHING;

-- Física → solo 2° SEC
INSERT INTO ades_materias_plan (materia_id, grado_id, ciclo_escolar_id, horas_semana, es_obligatoria)
SELECT m.id, gr.id, '019e8f74-d149-735b-823a-0f253f33474c'::uuid, m.horas_semana, TRUE
FROM ades_materias m
JOIN ades_niveles_educativos n ON n.id = m.nivel_educativo_id AND n.nombre_nivel = 'SECUNDARIA'
JOIN ades_grados gr ON gr.nivel_educativo_id = n.id AND gr.is_active = TRUE AND gr.numero_grado = 2
WHERE m.clave_materia = 'SEC-FIS'
ON CONFLICT (materia_id, grado_id, ciclo_escolar_id) DO NOTHING;

-- Química → solo 3° SEC
INSERT INTO ades_materias_plan (materia_id, grado_id, ciclo_escolar_id, horas_semana, es_obligatoria)
SELECT m.id, gr.id, '019e8f74-d149-735b-823a-0f253f33474c'::uuid, m.horas_semana, TRUE
FROM ades_materias m
JOIN ades_niveles_educativos n ON n.id = m.nivel_educativo_id AND n.nombre_nivel = 'SECUNDARIA'
JOIN ades_grados gr ON gr.nivel_educativo_id = n.id AND gr.is_active = TRUE AND gr.numero_grado = 3
WHERE m.clave_materia = 'SEC-QUI'
ON CONFLICT (materia_id, grado_id, ciclo_escolar_id) DO NOTHING;


-- ═══════════════════════════════════════════════════════════════════════
-- BLOQUE 7: Temas para materias PRI nuevas
-- ═══════════════════════════════════════════════════════════════════════

INSERT INTO ades_temas (materia_id, nombre_tema, descripcion, orden, periodo_sugerido)
SELECT m.id, v.nombre, v.descr, v.ord, v.per
FROM (VALUES
  ('Comunicación oral: escucha activa y expresión',        'Comprensión auditiva y expresión verbal en contextos cotidianos.',              1, 1),
  ('Lectura comprensiva de textos narrativos',             'Estrategias de lectura: antes, durante y después del texto.',                   2, 1),
  ('Producción de textos descriptivos',                    'Estructura y redacción de descripciones de objetos, personas y lugares.',       3, 2),
  ('Ortografía: mayúsculas y signos de puntuación',        'Normas básicas de escritura correcta.',                                         4, 2),
  ('Textos informativos: artículo de divulgación',         'Lectura y producción de textos que informen sobre temas de interés.',           5, 2),
  ('Narración: cuentos y fábulas',                         'Estructura narrativa: inicio, desarrollo y desenlace.',                         6, 3),
  ('Correspondencia formal e informal',                    'Cartas y comunicación escrita con propósito social.',                           7, 3),
  ('Proyecto integrador: texto multimodal propio',         'Producción de un texto que integre aprendizajes del año.',                      8, 3)
) AS v(nombre, descr, ord, per)
JOIN ades_materias m ON m.clave_materia = 'PRI-ESP'
WHERE NOT EXISTS (SELECT 1 FROM ades_temas WHERE materia_id = m.id AND is_active = TRUE);

INSERT INTO ades_temas (materia_id, nombre_tema, descripcion, orden, periodo_sugerido)
SELECT m.id, v.nombre, v.descr, v.ord, v.per
FROM (VALUES
  ('Números naturales y valor posicional',                 'Lectura, escritura y comparación de números hasta millones.',                   1, 1),
  ('Suma y resta con reagrupación',                        'Algoritmos de la adición y sustracción con números grandes.',                   2, 1),
  ('Multiplicación: tablas y procedimientos',              'Dominio de la tabla del 1 al 10 y multiplicaciones de 2 cifras.',              3, 1),
  ('División exacta y con residuo',                        'Concepto de división y procedimiento algorítmico.',                             4, 2),
  ('Fracciones: concepto y equivalencias',                 'Representación, comparación y equivalencia de fracciones.',                    5, 2),
  ('Medida: longitud, capacidad y masa',                   'Uso de instrumentos de medición y conversión de unidades.',                   6, 2),
  ('Figuras geométricas y cuerpos',                        'Clasificación, propiedades y construcción de figuras geométricas.',            7, 3),
  ('Tratamiento de la información: tablas y gráficas',     'Lectura e interpretación de datos estadísticos básicos.',                     8, 3)
) AS v(nombre, descr, ord, per)
JOIN ades_materias m ON m.clave_materia = 'PRI-MAT'
WHERE NOT EXISTS (SELECT 1 FROM ades_temas WHERE materia_id = m.id AND is_active = TRUE);

INSERT INTO ades_temas (materia_id, nombre_tema, descripcion, orden, periodo_sugerido)
SELECT m.id, v.nombre, v.descr, v.ord, v.per
FROM (VALUES
  ('El ser humano y su cuerpo',                            'Sistemas: digestivo, respiratorio y circulatorio.',                             1, 1),
  ('Los seres vivos y su clasificación',                   'Reino animal, vegetal, fungi y microorganismos.',                               2, 1),
  ('Los ecosistemas y el ambiente',                        'Cadenas alimenticias, biomas y cuidado del medio ambiente.',                   3, 2),
  ('La materia y sus cambios',                             'Estados de la materia, mezclas y separación.',                                 4, 2),
  ('Energía: formas y transformaciones',                   'Calor, luz, sonido y electricidad en la vida cotidiana.',                      5, 3),
  ('Salud y prevención de enfermedades',                   'Higiene personal, alimentación saludable y vacunación.',                       6, 3)
) AS v(nombre, descr, ord, per)
JOIN ades_materias m ON m.clave_materia = 'PRI-CNT'
WHERE NOT EXISTS (SELECT 1 FROM ades_temas WHERE materia_id = m.id AND is_active = TRUE);

INSERT INTO ades_temas (materia_id, nombre_tema, descripcion, orden, periodo_sugerido)
SELECT m.id, v.nombre, v.descr, v.ord, v.per
FROM (VALUES
  ('El tiempo y la historia',                              'Nociones de tiempo: pasado, presente y futuro. Línea del tiempo.',             1, 1),
  ('Mesoamérica: civilizaciones antiguas',                 'Olmecas, mayas, aztecas y otras culturas prehispánicas.',                     2, 1),
  ('La Colonia en México',                                 'Conquista española, mestizaje y vida colonial.',                               3, 2),
  ('Independencia de México',                              'Causas, personajes y consecuencias del movimiento de Independencia.',          4, 2),
  ('México moderno: siglos XIX-XXI',                       'Reforma, Revolución y consolidación del Estado mexicano.',                    5, 3)
) AS v(nombre, descr, ord, per)
JOIN ades_materias m ON m.clave_materia = 'PRI-HIS'
WHERE NOT EXISTS (SELECT 1 FROM ades_temas WHERE materia_id = m.id AND is_active = TRUE);

INSERT INTO ades_temas (materia_id, nombre_tema, descripcion, orden, periodo_sugerido)
SELECT m.id, v.nombre, v.descr, v.ord, v.per
FROM (VALUES
  ('El espacio geográfico y los mapas',                    'Lectura de mapas: escala, simbología y orientación.',                          1, 1),
  ('México: relieve y climas',                             'Montañas, llanuras, costas y regiones climáticas.',                           2, 1),
  ('Regiones naturales del mundo',                         'Biomas: selvas, desiertos, tundras y bosques templados.',                     3, 2),
  ('Población y diversidad cultural',                      'Distribución de la población, grupos étnicos y lenguas de México.',           4, 2),
  ('Recursos naturales y sustentabilidad',                 'Uso responsable del agua, suelo, bosques y energías renovables.',             5, 3)
) AS v(nombre, descr, ord, per)
JOIN ades_materias m ON m.clave_materia = 'PRI-GEO'
WHERE NOT EXISTS (SELECT 1 FROM ades_temas WHERE materia_id = m.id AND is_active = TRUE);

INSERT INTO ades_temas (materia_id, nombre_tema, descripcion, orden, periodo_sugerido)
SELECT m.id, v.nombre, v.descr, v.ord, v.per
FROM (VALUES
  ('Identidad personal y autoestima',                      'Quién soy, mis emociones, fortalezas y retos personales.',                    1, 1),
  ('Convivencia y respeto',                                'Normas de convivencia, resolución de conflictos y empatía.',                  2, 2),
  ('Derechos y responsabilidades',                         'Derechos de los niños, responsabilidades ciudadanas.',                        3, 2),
  ('La democracia en la escuela',                          'Participación, elecciones escolares y bien común.',                           4, 3)
) AS v(nombre, descr, ord, per)
JOIN ades_materias m ON m.clave_materia = 'PRI-FCE'
WHERE NOT EXISTS (SELECT 1 FROM ades_temas WHERE materia_id = m.id AND is_active = TRUE);

INSERT INTO ades_temas (materia_id, nombre_tema, descripcion, orden, periodo_sugerido)
SELECT m.id, v.nombre, v.descr, v.ord, v.per
FROM (VALUES
  ('Tecnología en la vida cotidiana',                      'Herramientas, máquinas y aparatos de uso frecuente.',                         1, 1),
  ('Proceso tecnológico: diseño y construcción',           'Fases del diseño: identificar, planear, construir y evaluar.',               2, 2),
  ('Uso responsable de las TIC',                           'Internet seguro, búsqueda de información y ciudadanía digital.',              3, 2),
  ('Proyecto tecnológico integrador',                      'Creación de un artefacto que solucione un problema real.',                    4, 3)
) AS v(nombre, descr, ord, per)
JOIN ades_materias m ON m.clave_materia = 'PRI-TEC'
WHERE NOT EXISTS (SELECT 1 FROM ades_temas WHERE materia_id = m.id AND is_active = TRUE);

INSERT INTO ades_temas (materia_id, nombre_tema, descripcion, orden, periodo_sugerido)
SELECT m.id, v.nombre, v.descr, v.ord, v.per
FROM (VALUES
  ('Autoconocimiento y bienestar',                         'Exploración de emociones, valores y metas personales.',                       1, 1),
  ('Habilidades socioemocionales',                         'Cooperación, comunicación asertiva y manejo del estrés.',                    2, 2),
  ('Orientación académica',                                'Hábitos de estudio, organización del tiempo y proyecto de vida.',            3, 3)
) AS v(nombre, descr, ord, per)
JOIN ades_materias m ON m.clave_materia = 'PRI-TUT'
WHERE NOT EXISTS (SELECT 1 FROM ades_temas WHERE materia_id = m.id AND is_active = TRUE);


-- ═══════════════════════════════════════════════════════════════════════
-- BLOQUE 8: Temas para materias SEC reactivadas y ciencias nuevas
-- ═══════════════════════════════════════════════════════════════════════

INSERT INTO ades_temas (materia_id, nombre_tema, descripcion, orden, periodo_sugerido)
SELECT m.id, v.nombre, v.descr, v.ord, v.per
FROM (VALUES
  ('Análisis de textos narrativos',                        'Elementos del relato: narrador, personajes, tiempo y espacio.',               1, 1),
  ('Argumentación escrita',                                'Estructura: tesis, argumentos y conclusión.',                                 2, 1),
  ('Literatura: poesía y drama',                           'Figuras retóricas, métrica y estructura dramática.',                          3, 2),
  ('Textos de divulgación científica',                     'Características y producción de artículos de divulgación.',                   4, 2),
  ('Lengua y comunicación digital',                        'Géneros digitales: blog, podcast, presentación multimedia.',                  5, 2),
  ('Sintaxis: oración y sus elementos',                    'Sujeto, predicado, complementos y tipos de oración.',                        6, 3),
  ('Investigación documental',                             'Búsqueda, selección y citación de fuentes bibliográficas.',                   7, 3),
  ('Proyecto: antología literaria',                        'Selección, edición y presentación de textos literarios.',                    8, 3)
) AS v(nombre, descr, ord, per)
JOIN ades_materias m ON m.clave_materia = 'SEC-ESP'
WHERE NOT EXISTS (SELECT 1 FROM ades_temas WHERE materia_id = m.id AND is_active = TRUE);

INSERT INTO ades_temas (materia_id, nombre_tema, descripcion, orden, periodo_sugerido)
SELECT m.id, v.nombre, v.descr, v.ord, v.per
FROM (VALUES
  ('Números enteros y operaciones',                        'Suma, resta, multiplicación y división con enteros y negativos.',             1, 1),
  ('Fracciones y decimales avanzados',                     'Operaciones con fracciones mixtas y decimales periódicos.',                   2, 1),
  ('Álgebra: expresiones y ecuaciones',                    'Variables, términos semejantes y ecuaciones de primer grado.',               3, 2),
  ('Proporcionalidad y porcentajes',                       'Razón, proporción, regla de tres y porcentajes.',                            4, 2),
  ('Geometría: figuras y cuerpos',                         'Perímetro, área, volumen y cuerpos geométricos.',                            5, 2),
  ('Estadística: datos y probabilidad',                    'Medidas de tendencia central y probabilidad.',                               6, 3),
  ('Funciones y gráficas',                                 'Función lineal, cuadrática e interpretación de gráficas.',                   7, 3),
  ('Proyecto: matemáticas en contexto',                    'Aplicación de matemáticas en situaciones reales.',                          8, 3)
) AS v(nombre, descr, ord, per)
JOIN ades_materias m ON m.clave_materia = 'SEC-MAT'
WHERE NOT EXISTS (SELECT 1 FROM ades_temas WHERE materia_id = m.id AND is_active = TRUE);

INSERT INTO ades_temas (materia_id, nombre_tema, descripcion, orden, periodo_sugerido)
SELECT m.id, v.nombre, v.descr, v.ord, v.per
FROM (VALUES
  ('Civilizaciones antiguas del mundo',                    'Mesopotamia, Egipto, Grecia y Roma.',                                         1, 1),
  ('Edad Media y Renacimiento',                            'Feudalismo, Cruzadas y Reforma protestante.',                                 2, 1),
  ('Expansión europea y colonialismos',                    'Conquista de América y colonialismo.',                                        3, 2),
  ('Revoluciones modernas',                                'Revolución Francesa, Revolución Industrial.',                                 4, 2),
  ('Siglo XX: guerras y cambios',                          'Primera y Segunda Guerras Mundiales, Guerra Fría.',                          5, 3),
  ('Mundo contemporáneo',                                  'Globalización, derechos humanos y México en el siglo XXI.',                  6, 3)
) AS v(nombre, descr, ord, per)
JOIN ades_materias m ON m.clave_materia = 'SEC-HIS'
WHERE NOT EXISTS (SELECT 1 FROM ades_temas WHERE materia_id = m.id AND is_active = TRUE);

INSERT INTO ades_temas (materia_id, nombre_tema, descripcion, orden, periodo_sugerido)
SELECT m.id, v.nombre, v.descr, v.ord, v.per
FROM (VALUES
  ('Geografía física: tierra y relieve',                   'Litosfera, hidrosfera, atmósfera y movimientos de la tierra.',                1, 1),
  ('Climas y biomas del mundo',                            'Factores climáticos, zonas climáticas y ecosistemas.',                        2, 1),
  ('Geografía humana y económica',                         'Población mundial, migraciones y actividades económicas.',                   3, 2),
  ('México: regiones geoeconómicas',                       'División regional, recursos y actividades productivas.',                     4, 2),
  ('Problemas ambientales globales',                       'Cambio climático, contaminación y pérdida de biodiversidad.',                5, 3)
) AS v(nombre, descr, ord, per)
JOIN ades_materias m ON m.clave_materia = 'SEC-GEO'
WHERE NOT EXISTS (SELECT 1 FROM ades_temas WHERE materia_id = m.id AND is_active = TRUE);

INSERT INTO ades_temas (materia_id, nombre_tema, descripcion, orden, periodo_sugerido)
SELECT m.id, v.nombre, v.descr, v.ord, v.per
FROM (VALUES
  ('Vocabulary and basic communication',                   'Greetings, introductions and everyday expressions.',                         1, 1),
  ('Grammar: present and past tenses',                     'Simple present, present continuous and simple past.',                        2, 1),
  ('Reading comprehension strategies',                     'Skimming, scanning and inferring meaning from context.',                     3, 2),
  ('Writing: paragraphs and short texts',                  'Topic sentence, details and conclusion.',                                    4, 2),
  ('Oral production: conversations',                       'Role plays and dialogues on familiar topics.',                               5, 3),
  ('Culture and English-speaking world',                   'Customs and traditions of English-speaking countries.',                     6, 3)
) AS v(nombre, descr, ord, per)
JOIN ades_materias m ON m.clave_materia = 'SEC-ING'
WHERE NOT EXISTS (SELECT 1 FROM ades_temas WHERE materia_id = m.id AND is_active = TRUE);

INSERT INTO ades_temas (materia_id, nombre_tema, descripcion, orden, periodo_sugerido)
SELECT m.id, v.nombre, v.descr, v.ord, v.per
FROM (VALUES
  ('Introducción a las artes visuales',                    'Elementos del lenguaje visual: línea, color, forma y textura.',              1, 1),
  ('Música: ritmo y melodía',                              'Notación musical básica, ritmo, tempo y géneros.',                           2, 1),
  ('Expresión corporal y danza',                           'El cuerpo como instrumento expresivo y danza folclórica.',                  3, 2),
  ('Teatro y expresión dramática',                         'Texto dramático, personaje y puesta en escena.',                            4, 2),
  ('Proyecto artístico integrador',                        'Creación colaborativa que integre dos disciplinas artísticas.',              5, 3)
) AS v(nombre, descr, ord, per)
JOIN ades_materias m ON m.clave_materia = 'SEC-ART'
WHERE NOT EXISTS (SELECT 1 FROM ades_temas WHERE materia_id = m.id AND is_active = TRUE);

INSERT INTO ades_temas (materia_id, nombre_tema, descripcion, orden, periodo_sugerido)
SELECT m.id, v.nombre, v.descr, v.ord, v.per
FROM (VALUES
  ('Capacidades físicas básicas',                          'Resistencia, fuerza, velocidad y flexibilidad.',                             1, 1),
  ('Deportes colectivos',                                  'Reglas de fútbol, basquetbol y voleibol.',                                   2, 2),
  ('Actividad física y salud',                             'Beneficios del ejercicio, alimentación saludable y prevención.',            3, 2),
  ('Juegos tradicionales y recreativos',                   'Juegos populares mexicanos y cooperativos.',                                 4, 3)
) AS v(nombre, descr, ord, per)
JOIN ades_materias m ON m.clave_materia = 'SEC-EDF'
WHERE NOT EXISTS (SELECT 1 FROM ades_temas WHERE materia_id = m.id AND is_active = TRUE);

INSERT INTO ades_temas (materia_id, nombre_tema, descripcion, orden, periodo_sugerido)
SELECT m.id, v.nombre, v.descr, v.ord, v.per
FROM (VALUES
  ('Identidad, dignidad y derechos',                       'Dignidad humana e identidad como base de los derechos fundamentales.',       1, 1),
  ('Ciudadanía y democracia',                              'Valores democráticos, participación ciudadana y bien común.',               2, 1),
  ('Interculturalidad y diversidad',                       'Respeto a las diferencias culturales, lingüísticas y religiosas.',          3, 2),
  ('Ética personal y social',                              'Dilemas éticos, toma de decisiones y responsabilidad social.',              4, 2),
  ('Derechos humanos y legalidad',                         'Sistema jurídico y resolución pacífica de conflictos.',                     5, 3)
) AS v(nombre, descr, ord, per)
JOIN ades_materias m ON m.clave_materia = 'SEC-FCE'
WHERE NOT EXISTS (SELECT 1 FROM ades_temas WHERE materia_id = m.id AND is_active = TRUE);

INSERT INTO ades_temas (materia_id, nombre_tema, descripcion, orden, periodo_sugerido)
SELECT m.id, v.nombre, v.descr, v.ord, v.per
FROM (VALUES
  ('Tecnología e innovación',                              'Historia de la tecnología, inventos y su impacto en la sociedad.',           1, 1),
  ('Diseño tecnológico y prototipado',                     'Proceso de diseño: problema, propuesta, construcción y evaluación.',        2, 1),
  ('Programación y pensamiento computacional',             'Algoritmos, diagramas de flujo y programación visual.',                     3, 2),
  ('Robótica educativa',                                   'Fundamentos de robótica y automatización con kits educativos.',             4, 2),
  ('Proyecto tecnológico final',                           'Diseño y construcción de un prototipo que solucione un problema real.',     5, 3)
) AS v(nombre, descr, ord, per)
JOIN ades_materias m ON m.clave_materia = 'SEC-TEC'
WHERE NOT EXISTS (SELECT 1 FROM ades_temas WHERE materia_id = m.id AND is_active = TRUE);

INSERT INTO ades_temas (materia_id, nombre_tema, descripcion, orden, periodo_sugerido)
SELECT m.id, v.nombre, v.descr, v.ord, v.per
FROM (VALUES
  ('Autoconocimiento y proyecto de vida',                  'Intereses, habilidades y metas personales a corto y largo plazo.',          1, 1),
  ('Habilidades socioemocionales',                         'Gestión de emociones, resiliencia y comunicación asertiva.',                2, 2),
  ('Prevención: adicciones y riesgos',                     'Información sobre sustancias y comportamientos de riesgo.',                 3, 2),
  ('Orientación vocacional',                               'Exploración de profesiones y opciones educativas.',                         4, 3)
) AS v(nombre, descr, ord, per)
JOIN ades_materias m ON m.clave_materia = 'SEC-TUT'
WHERE NOT EXISTS (SELECT 1 FROM ades_temas WHERE materia_id = m.id AND is_active = TRUE);

INSERT INTO ades_temas (materia_id, nombre_tema, descripcion, orden, periodo_sugerido)
SELECT m.id, v.nombre, v.descr, v.ord, v.per
FROM (VALUES
  ('Método científico y experimentación',                  'Observación, hipótesis, experimento y conclusiones.',                        1, 1),
  ('La materia: propiedades y cambios',                    'Átomo, molécula, estados de la materia y cambios físico-químicos.',          2, 1),
  ('Los seres vivos: célula y organización',               'Célula procariota y eucariota, tejidos, órganos y sistemas.',               3, 2),
  ('Ecosistemas y biodiversidad',                          'Relaciones entre organismos, cadenas alimenticias y biomas.',               4, 2),
  ('Tecnología y sociedad',                                'Impacto de la tecnología en el ambiente y la salud humana.',                5, 3)
) AS v(nombre, descr, ord, per)
JOIN ades_materias m ON m.clave_materia = 'SEC-CNT'
WHERE NOT EXISTS (SELECT 1 FROM ades_temas WHERE materia_id = m.id AND is_active = TRUE);

-- Biología SEC (7 temas)
INSERT INTO ades_temas (materia_id, nombre_tema, descripcion, orden, periodo_sugerido)
SELECT m.id, v.nombre, v.descr, v.ord, v.per
FROM (VALUES
  ('La célula: unidad básica de vida',                     'Tipos de células, organelos y funciones celulares.',                         1, 1),
  ('Reproducción celular',                                 'Mitosis, meiosis y su importancia en el crecimiento.',                      2, 1),
  ('Biodiversidad y clasificación',                        'Los cinco reinos y criterios de clasificación biológica.',                  3, 1),
  ('Anatomía y fisiología humana',                         'Sistemas: digestivo, circulatorio, respiratorio, excretor y nervioso.',     4, 2),
  ('Ecología y ecosistemas',                               'Cadenas tróficas, flujo de energía y ciclos biogeoquímicos.',               5, 2),
  ('Herencia y genética',                                  'ADN, cromosomas, genes y leyes de Mendel.',                                 6, 3),
  ('Evolución y origen de la vida',                        'Selección natural, Darwin y evidencias de la evolución.',                   7, 3)
) AS v(nombre, descr, ord, per)
JOIN ades_materias m ON m.clave_materia = 'SEC-BIO'
WHERE NOT EXISTS (SELECT 1 FROM ades_temas WHERE materia_id = m.id AND is_active = TRUE);

-- Física SEC (7 temas)
INSERT INTO ades_temas (materia_id, nombre_tema, descripcion, orden, periodo_sugerido)
SELECT m.id, v.nombre, v.descr, v.ord, v.per
FROM (VALUES
  ('Magnitudes y medición',                                'Sistema Internacional, instrumentos de medición.',                           1, 1),
  ('Movimiento: cinemática',                               'Velocidad, aceleración y gráficas de movimiento uniforme.',                  2, 1),
  ('Fuerzas: dinámica',                                    'Leyes de Newton, peso, fricción y aplicaciones.',                           3, 2),
  ('Trabajo, energía y potencia',                          'Trabajo mecánico, energía cinética y potencial.',                           4, 2),
  ('Calor y temperatura',                                  'Dilatación, transmisión de calor y cambios de estado.',                     5, 2),
  ('Electricidad y magnetismo',                            'Circuitos eléctricos, ley de Ohm y electroimanes.',                        6, 3),
  ('Ondas, sonido y luz',                                  'Naturaleza ondulatoria de la luz y el sonido.',                            7, 3)
) AS v(nombre, descr, ord, per)
JOIN ades_materias m ON m.clave_materia = 'SEC-FIS'
WHERE NOT EXISTS (SELECT 1 FROM ades_temas WHERE materia_id = m.id AND is_active = TRUE);

-- Química SEC (7 temas)
INSERT INTO ades_temas (materia_id, nombre_tema, descripcion, orden, periodo_sugerido)
SELECT m.id, v.nombre, v.descr, v.ord, v.per
FROM (VALUES
  ('La materia y sus propiedades',                         'Propiedades físicas y químicas, clasificación de la materia.',              1, 1),
  ('Modelo atómico y tabla periódica',                     'Evolución del modelo atómico y organización de los elementos.',             2, 1),
  ('El enlace químico',                                    'Enlace iónico, covalente y metálico.',                                      3, 2),
  ('Reacciones químicas',                                  'Tipos de reacciones, balanceo y estequiometría básica.',                    4, 2),
  ('Soluciones y concentración',                           'Soluto, solvente, tipos y cálculo de concentración.',                      5, 2),
  ('Ácidos, bases y pH',                                   'Escala de pH, neutralización y aplicaciones cotidianas.',                   6, 3),
  ('Química orgánica básica',                              'Hidrocarburos, grupos funcionales y polímeros.',                           7, 3)
) AS v(nombre, descr, ord, per)
JOIN ades_materias m ON m.clave_materia = 'SEC-QUI'
WHERE NOT EXISTS (SELECT 1 FROM ades_temas WHERE materia_id = m.id AND is_active = TRUE);

COMMIT;
