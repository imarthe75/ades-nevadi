-- ============================================================
-- Migración 043: Temarios completos + Materias Nevadi
-- Temas para SEC NEM, CBU PREP (51 materias), y materias institucionales Nevadi
-- ============================================================

BEGIN;

-- ────────────────────────────────────────────────────────────
-- BLOQUE 1: TEMAS SEC NEM (5 campos formativos, 12 temas c/u)
-- ────────────────────────────────────────────────────────────

INSERT INTO ades_temas (id, materia_id, nombre_tema, descripcion, orden, periodo_sugerido)
SELECT gen_random_uuid(), m.id, v.nombre, v.descr, v.ord::int, v.per::int
FROM ades_materias m CROSS JOIN (VALUES
  ('Comprensión lectora: textos literarios','Narrativa, poesía, teatro — análisis e interpretación','1','1'),
  ('Comprensión lectora: textos informativos','Noticias, reportajes, ensayos — estrategias de lectura','2','1'),
  ('Producción escrita: texto narrativo','Cuento, crónica — planificación, escritura y revisión','3','1'),
  ('Producción escrita: texto argumentativo','Ensayo, carta — estructura y conectores','4','2'),
  ('Oralidad: presentaciones y debates','Técnicas de expresión oral, argumentación y escucha activa','5','2'),
  ('Multimodalidad y textos digitales','Infografías, blogs, redes — lectura crítica de medios','6','2'),
  ('Análisis lingüístico básico','Morfosintaxis, ortografía y puntuación funcional','7','3'),
  ('Literatura y contexto cultural','Obras representativas de México y el mundo','8','3'),
  ('Producción de proyectos comunicativos','Diseño y realización de productos textuales colectivos','9','3'),
  ('Diversidad lingüística de México','Lenguas indígenas, variedades del español','10','3'),
  ('Escritura creativa y expresión personal','Taller de escritura — voz y estilo propio','11','4'),
  ('Evaluación integral del campo Lenguajes','Repaso, portafolio y presentación final','12','4')
) v(nombre, descr, ord, per)
WHERE m.clave_materia='SEC-LEN' AND m.is_active=TRUE
  AND NOT EXISTS (SELECT 1 FROM ades_temas WHERE materia_id=m.id AND is_active=TRUE);

INSERT INTO ades_temas (id, materia_id, nombre_tema, descripcion, orden, periodo_sugerido)
SELECT gen_random_uuid(), m.id, v.nombre, v.descr, v.ord::int, v.per::int
FROM ades_materias m CROSS JOIN (VALUES
  ('Método científico y pensamiento crítico','Observación, hipótesis, experimentación','1','1'),
  ('Biología: célula y organización de la vida','Célula eucariota y procariota, organelos','2','1'),
  ('Biología: ecosistemas y biodiversidad','Cadenas tróficas, biodiversidad de México','3','1'),
  ('Física: mecánica y movimiento','Cinemática, dinámica, leyes de Newton','4','2'),
  ('Física: energía y transformaciones','Trabajo, energía cinética, potencial y calor','5','2'),
  ('Química: materia y sus propiedades','Tabla periódica, enlaces, estados de la materia','6','2'),
  ('Química: reacciones y estequiometría básica','Tipos de reacción, balanceo, mol','7','3'),
  ('Tecnología e innovación','Diseño tecnológico, STEM, impacto social','8','3'),
  ('Proyecto de investigación científica','Diseño, recolección y análisis de datos','9','3'),
  ('Salud, cuerpo y bienestar','Sistema inmune, nutrición, salud integral','10','4'),
  ('Ciencia, tecnología y sociedad','Ética científica, cambio climático, sustentabilidad','11','4'),
  ('Evaluación integral del campo SPC','Repaso, exposición de proyectos, reflexión','12','4')
) v(nombre, descr, ord, per)
WHERE m.clave_materia='SEC-SPC' AND m.is_active=TRUE
  AND NOT EXISTS (SELECT 1 FROM ades_temas WHERE materia_id=m.id AND is_active=TRUE);

INSERT INTO ades_temas (id, materia_id, nombre_tema, descripcion, orden, periodo_sugerido)
SELECT gen_random_uuid(), m.id, v.nombre, v.descr, v.ord::int, v.per::int
FROM ades_materias m CROSS JOIN (VALUES
  ('Identidad personal y colectiva','Autoconocimiento, pertenencia, diversidad identitaria','1','1'),
  ('Derechos humanos y ciudadanía','Declaración Universal, derechos de NNA en México','2','1'),
  ('Historia de México: períodos clave','Prehispánico, Colonia, Independencia, Revolución','3','1'),
  ('Historia universal: civilizaciones','Antiguas civilizaciones, Edad Media, Modernidad','4','2'),
  ('Geografía social y cultural','Regiones de México, diversidad territorial','5','2'),
  ('Arte, estética y expresión cultural','Historia del arte, expresión plástica y musical','6','2'),
  ('Trabajo, economía y vida cotidiana','Sistema económico, trabajo digno, consumo responsable','7','3'),
  ('Diversidad cultural y perspectiva global','Interculturalidad, globalización, migraciones','8','3'),
  ('Participación democrática y ciudadanía','Instituciones, participación comunitaria, sufragio','9','3'),
  ('Memoria histórica y patrimonio','Patrimonio cultural tangible e intangible','10','4'),
  ('Proyecto de acción comunitaria','Diagnóstico, diseño e implementación colectiva','11','4'),
  ('Evaluación integral del campo DHC','Reflexión, portafolio y presentación final','12','4')
) v(nombre, descr, ord, per)
WHERE m.clave_materia='SEC-DHC' AND m.is_active=TRUE
  AND NOT EXISTS (SELECT 1 FROM ades_temas WHERE materia_id=m.id AND is_active=TRUE);

INSERT INTO ades_temas (id, materia_id, nombre_tema, descripcion, orden, periodo_sugerido)
SELECT gen_random_uuid(), m.id, v.nombre, v.descr, v.ord::int, v.per::int
FROM ades_materias m CROSS JOIN (VALUES
  ('Ética y formación de valores','Virtudes, dilemas morales, toma de decisiones éticas','1','1'),
  ('Convivencia pacífica y resolución de conflictos','Mediación, diálogo, cultura de paz','2','1'),
  ('Sostenibilidad y cuidado del ambiente','Recursos naturales, huella ecológica, acción ambiental','3','1'),
  ('Cambio climático y respuesta global','Causas, efectos, Acuerdos de París, acción local','4','2'),
  ('Sociedad y bien común','Solidaridad, justicia social, equidad','5','2'),
  ('Formación ciudadana activa','Democracia participativa, instituciones, derechos políticos','6','2'),
  ('Derechos, deberes y responsabilidades','Constitución mexicana, derechos colectivos','7','3'),
  ('Bienestar emocional y socioemocional','Gestión emocional, empatía, resiliencia','8','3'),
  ('Comunidades sustentables','Proyectos de sustentabilidad local, ecodiseño','9','3'),
  ('Ética tecnológica y digital','Privacidad, datos, inteligencia artificial ética','10','4'),
  ('Proyecto de reflexión ética','Análisis de caso comunitario con postura argumentada','11','4'),
  ('Evaluación integral del campo ENS','Repaso, debates, bitácora de reflexión','12','4')
) v(nombre, descr, ord, per)
WHERE m.clave_materia='SEC-ENS' AND m.is_active=TRUE
  AND NOT EXISTS (SELECT 1 FROM ades_temas WHERE materia_id=m.id AND is_active=TRUE);

INSERT INTO ades_temas (id, materia_id, nombre_tema, descripcion, orden, periodo_sugerido)
SELECT gen_random_uuid(), m.id, v.nombre, v.descr, v.ord::int, v.per::int
FROM ades_materias m CROSS JOIN (VALUES
  ('Identificación de problemáticas comunitarias','Diagnóstico participativo del entorno escolar y social','1','1'),
  ('Investigación y marco teórico','Búsqueda y organización de información','2','1'),
  ('Planeación del proyecto','Objetivos, metas, cronograma y responsables','3','1'),
  ('Diseño de propuesta de solución','Prototipado, viabilidad y recursos necesarios','4','2'),
  ('Gestión de recursos y colaboración','Trabajo en equipo, roles, comunicación efectiva','5','2'),
  ('Implementación: primera etapa','Ejecución de acciones planificadas','6','2'),
  ('Implementación: segunda etapa','Ajustes, monitoreo, registro de avances','7','3'),
  ('Documentación y sistematización','Bitácora, evidencias, informe de proceso','8','3'),
  ('Evaluación del impacto del proyecto','Indicadores, resultados obtenidos','9','3'),
  ('Comunicación de resultados','Presentación oral, visual y/o escrita ante la comunidad','10','4'),
  ('Reflexión y retroalimentación','Lecciones aprendidas, áreas de mejora','11','4'),
  ('Presentación final y evaluación integral','Exposición del proyecto completo','12','4')
) v(nombre, descr, ord, per)
WHERE m.clave_materia='SEC-PRY' AND m.is_active=TRUE
  AND NOT EXISTS (SELECT 1 FROM ades_temas WHERE materia_id=m.id AND is_active=TRUE);

-- ────────────────────────────────────────────────────────────
-- BLOQUE 2: TEMAS CBU PREPARATORIA (51 materias, 8 temas c/u)
-- ────────────────────────────────────────────────────────────

-- Pensamiento Matemático 1
INSERT INTO ades_temas (id, materia_id, nombre_tema, descripcion, orden, periodo_sugerido)
SELECT gen_random_uuid(), m.id, v.nombre, v.descr, v.ord::int, v.per::int
FROM ades_materias m CROSS JOIN (VALUES
  ('Números reales y operaciones','Enteros, racionales, irracionales, operaciones básicas','1','1'),
  ('Expresiones algebraicas','Monomios, polinomios, productos notables','2','1'),
  ('Ecuaciones lineales y sistemas','Solución analítica y gráfica de sistemas 2×2','3','2'),
  ('Ecuaciones cuadráticas','Factorización, fórmula general, discriminante','4','2'),
  ('Funciones y sus propiedades','Concepto de función, dominio, rango, gráficas','5','3'),
  ('Función lineal y cuadrática','Pendiente, ordenada al origen, parábola','6','3'),
  ('Razones y proporciones','Porcentajes, variación proporcional directa/inversa','7','4'),
  ('Estadística descriptiva básica','Media, mediana, moda, dispersión','8','4')
) v(nombre, descr, ord, per)
WHERE m.clave_materia='CBU-PM1' AND m.is_active=TRUE
  AND NOT EXISTS (SELECT 1 FROM ades_temas WHERE materia_id=m.id AND is_active=TRUE);

-- Pensamiento Matemático 2
INSERT INTO ades_temas (id, materia_id, nombre_tema, descripcion, orden, periodo_sugerido)
SELECT gen_random_uuid(), m.id, v.nombre, v.descr, v.ord::int, v.per::int
FROM ades_materias m CROSS JOIN (VALUES
  ('Trigonometría: razones y funciones','Sen, cos, tan — triángulo rectángulo y círculo unitario','1','1'),
  ('Identidades y ecuaciones trigonométricas','Identidades pitagóricas, suma y diferencia de ángulos','2','1'),
  ('Geometría analítica: recta','Ecuación de la recta, distancias, intersecciones','3','2'),
  ('Cónicas: circunferencia y parábola','Ecuación canónica y estándar','4','2'),
  ('Cónicas: elipse e hipérbola','Elementos, ecuaciones y aplicaciones','5','3'),
  ('Sucesiones y series','Progresiones aritméticas y geométricas','6','3'),
  ('Combinatoria y probabilidad','Permutaciones, combinaciones, regla de Laplace','7','4'),
  ('Estadística inferencial básica','Distribuciones, intervalos de confianza','8','4')
) v(nombre, descr, ord, per)
WHERE m.clave_materia='CBU-PM2' AND m.is_active=TRUE
  AND NOT EXISTS (SELECT 1 FROM ades_temas WHERE materia_id=m.id AND is_active=TRUE);

-- Pensamiento Matemático 3
INSERT INTO ades_temas (id, materia_id, nombre_tema, descripcion, orden, periodo_sugerido)
SELECT gen_random_uuid(), m.id, v.nombre, v.descr, v.ord::int, v.per::int
FROM ades_materias m CROSS JOIN (VALUES
  ('Límites y continuidad','Definición, propiedades, límites laterales','1','1'),
  ('Derivada: concepto y reglas básicas','Definición, potencia, suma, producto, cociente','2','1'),
  ('Regla de la cadena y derivadas de funciones especiales','Trigonométricas, exponenciales, logarítmicas','3','2'),
  ('Aplicaciones de la derivada','Máximos, mínimos, problemas de optimización','4','2'),
  ('Integral indefinida y antiderivadas','Reglas de integración, integración por sustitución','5','3'),
  ('Integral definida y Teorema Fundamental','Área bajo la curva, Riemann','6','3'),
  ('Métodos de integración','Por partes, fracciones parciales','7','4'),
  ('Aplicaciones de la integral','Áreas, volúmenes, trabajo y centro de masa','8','4')
) v(nombre, descr, ord, per)
WHERE m.clave_materia='CBU-PM3' AND m.is_active=TRUE
  AND NOT EXISTS (SELECT 1 FROM ades_temas WHERE materia_id=m.id AND is_active=TRUE);

-- Temas Selectos Matemáticas 1
INSERT INTO ades_temas (id, materia_id, nombre_tema, descripcion, orden, periodo_sugerido)
SELECT gen_random_uuid(), m.id, v.nombre, v.descr, v.ord::int, v.per::int
FROM ades_materias m CROSS JOIN (VALUES
  ('Lógica proposicional','Conectivos, tablas de verdad, tautologías','1','1'),
  ('Conjuntos y relaciones','Operaciones con conjuntos, relaciones de equivalencia','2','1'),
  ('Funciones avanzadas','Inyectiva, sobreyectiva, biyectiva, composición','3','2'),
  ('Álgebra de matrices','Operaciones, determinantes, sistemas lineales','4','2'),
  ('Vectores en el plano y el espacio','Magnitud, dirección, producto punto y cruz','5','3'),
  ('Inducción matemática','Principio de inducción, demostraciones','6','3'),
  ('Combinatoria avanzada','Principio de inclusión-exclusión, particiones','7','4'),
  ('Introducción a la teoría de grafos','Grafos, árboles, rutas, aplicaciones','8','4')
) v(nombre, descr, ord, per)
WHERE m.clave_materia='CBU-TSM1' AND m.is_active=TRUE
  AND NOT EXISTS (SELECT 1 FROM ades_temas WHERE materia_id=m.id AND is_active=TRUE);

-- Temas Selectos Matemáticas 2
INSERT INTO ades_temas (id, materia_id, nombre_tema, descripcion, orden, periodo_sugerido)
SELECT gen_random_uuid(), m.id, v.nombre, v.descr, v.ord::int, v.per::int
FROM ades_materias m CROSS JOIN (VALUES
  ('Espacios vectoriales','Bases, dimensión, subespacios','1','1'),
  ('Transformaciones lineales','Núcleo, imagen, representación matricial','2','1'),
  ('Valores y vectores propios','Diagonalización, aplicaciones','3','2'),
  ('Funciones de variable compleja básicas','Números complejos, plano complejo, módulo','4','2'),
  ('Series de Taylor y Maclaurin','Representación de funciones como series de potencias','5','3'),
  ('Ecuaciones diferenciales de primer orden','Separables, lineales, aplicaciones','6','3'),
  ('Probabilidad avanzada','Variable aleatoria, distribuciones discreta y continua','7','4'),
  ('Procesos estocásticos introductorios','Cadenas de Markov y aplicaciones','8','4')
) v(nombre, descr, ord, per)
WHERE m.clave_materia='CBU-TSM2' AND m.is_active=TRUE
  AND NOT EXISTS (SELECT 1 FROM ades_temas WHERE materia_id=m.id AND is_active=TRUE);

-- Temas Selectos Matemáticas 3
INSERT INTO ades_temas (id, materia_id, nombre_tema, descripcion, orden, periodo_sugerido)
SELECT gen_random_uuid(), m.id, v.nombre, v.descr, v.ord::int, v.per::int
FROM ades_materias m CROSS JOIN (VALUES
  ('Análisis matemático: topología básica','Abiertos, cerrados, límites en R^n','1','1'),
  ('Cálculo multivariable','Derivadas parciales, gradiente, jacobianos','2','1'),
  ('Integrales múltiples','Dobles, triples, cambio de variable','3','2'),
  ('Teoremas de Green, Stokes y Gauss','Integrales de línea y de superficie','4','2'),
  ('Álgebra abstracta: grupos','Definición, subgrupos, homomorfismos','5','3'),
  ('Anillos y cuerpos','Polinomios, divisibilidad, extensiones','6','3'),
  ('Criptografía matemática','RSA, logaritmos discretos, curvas elípticas','7','4'),
  ('Proyecto integrador de matemáticas avanzadas','Investigación y presentación de tema selecto','8','4')
) v(nombre, descr, ord, per)
WHERE m.clave_materia='CBU-TSM3' AND m.is_active=TRUE
  AND NOT EXISTS (SELECT 1 FROM ades_temas WHERE materia_id=m.id AND is_active=TRUE);

-- Física 1
INSERT INTO ades_temas (id, materia_id, nombre_tema, descripcion, orden, periodo_sugerido)
SELECT gen_random_uuid(), m.id, v.nombre, v.descr, v.ord::int, v.per::int
FROM ades_materias m CROSS JOIN (VALUES
  ('Magnitudes físicas y sistema SI','Medición, error, cifras significativas','1','1'),
  ('Cinemática: movimiento rectilíneo','MRU, MRUA, caída libre — gráficas y ecuaciones','2','1'),
  ('Cinemática: movimiento en 2D','Proyectil, composición de movimientos','3','2'),
  ('Leyes de Newton','Inercia, fuerza neta, acción-reacción, aplicaciones','4','2'),
  ('Trabajo y energía mecánica','Trabajo, energía cinética, potencial, conservación','5','3'),
  ('Cantidad de movimiento e impulso','Impulso, colisiones elásticas e inelásticas','6','3'),
  ('Rotación y momento de inercia','Cinemática angular, torque, equilibrio','7','4'),
  ('Gravitación universal','Ley de gravitación, satélites, órbitas','8','4')
) v(nombre, descr, ord, per)
WHERE m.clave_materia='CBU-FIS1' AND m.is_active=TRUE
  AND NOT EXISTS (SELECT 1 FROM ades_temas WHERE materia_id=m.id AND is_active=TRUE);

-- Física 2
INSERT INTO ades_temas (id, materia_id, nombre_tema, descripcion, orden, periodo_sugerido)
SELECT gen_random_uuid(), m.id, v.nombre, v.descr, v.ord::int, v.per::int
FROM ades_materias m CROSS JOIN (VALUES
  ('Termodinámica: temperatura y calor','Escalas, calor específico, dilatación','1','1'),
  ('Leyes de la termodinámica','1ª y 2ª ley, entropía, ciclos de Carnot','2','1'),
  ('Electrostática','Carga eléctrica, Ley de Coulomb, campo eléctrico','3','2'),
  ('Potencial eléctrico y capacitores','Diferencia de potencial, capacitancia, energía','4','2'),
  ('Corriente y circuitos eléctricos','Ohm, Kirchhoff, circuitos en serie y paralelo','5','3'),
  ('Magnetismo y electromagnetismo','Campo magnético, ley de Faraday, inducción','6','3'),
  ('Óptica geométrica','Reflexión, refracción, espejos y lentes','7','4'),
  ('Física moderna: cuántica y relatividad','Efecto fotoeléctrico, dualidad, relatividad especial','8','4')
) v(nombre, descr, ord, per)
WHERE m.clave_materia='CBU-FIS2' AND m.is_active=TRUE
  AND NOT EXISTS (SELECT 1 FROM ades_temas WHERE materia_id=m.id AND is_active=TRUE);

-- Química 1
INSERT INTO ades_temas (id, materia_id, nombre_tema, descripcion, orden, periodo_sugerido)
SELECT gen_random_uuid(), m.id, v.nombre, v.descr, v.ord::int, v.per::int
FROM ades_materias m CROSS JOIN (VALUES
  ('Materia y sus propiedades','Estados de la materia, mezclas, sustancias puras','1','1'),
  ('Modelos atómicos y estructura','Dalton, Thomson, Rutherford, Bohr, mecánica cuántica','2','1'),
  ('Tabla periódica y propiedades periódicas','Períodos, grupos, radio atómico, electronegatividad','3','2'),
  ('Enlace químico','Iónico, covalente, metálico — propiedades físicas','4','2'),
  ('Formulación y nomenclatura inorgánica','Óxidos, hidróxidos, ácidos, sales','5','3'),
  ('Reacciones químicas y estequiometría','Balanceo, mol, masa molar, cálculos estequiométricos','6','3'),
  ('Soluciones y concentración','Molaridad, porcentaje, solubilidad, pH','7','4'),
  ('Cinética y equilibrio químico básico','Velocidad de reacción, Le Chatelier','8','4')
) v(nombre, descr, ord, per)
WHERE m.clave_materia='CBU-QUI1' AND m.is_active=TRUE
  AND NOT EXISTS (SELECT 1 FROM ades_temas WHERE materia_id=m.id AND is_active=TRUE);

-- Química 2
INSERT INTO ades_temas (id, materia_id, nombre_tema, descripcion, orden, periodo_sugerido)
SELECT gen_random_uuid(), m.id, v.nombre, v.descr, v.ord::int, v.per::int
FROM ades_materias m CROSS JOIN (VALUES
  ('Electroquímica','Celdas galvánicas, electrólisis, potencial de reducción','1','1'),
  ('Termodinámica química','ΔH, ΔS, ΔG, ley de Hess, espontaneidad','2','1'),
  ('Química orgánica: hidrocarburos','Alcanos, alquenos, alquinos, arenos — IUPAC','3','2'),
  ('Grupos funcionales orgánicos','Alcoholes, éteres, cetonas, ácidos carboxílicos','4','2'),
  ('Reacciones orgánicas básicas','Sustitución, adición, eliminación, condensación','5','3'),
  ('Biomoléculas','Carbohidratos, lípidos, proteínas, ácidos nucleicos','6','3'),
  ('Polímeros y materiales','Plásticos, cauchos, fibras — síntesis y reciclaje','7','4'),
  ('Química ambiental y sustentabilidad','Contaminación, remediación, química verde','8','4')
) v(nombre, descr, ord, per)
WHERE m.clave_materia='CBU-QUI2' AND m.is_active=TRUE
  AND NOT EXISTS (SELECT 1 FROM ades_temas WHERE materia_id=m.id AND is_active=TRUE);

-- Biología 1
INSERT INTO ades_temas (id, materia_id, nombre_tema, descripcion, orden, periodo_sugerido)
SELECT gen_random_uuid(), m.id, v.nombre, v.descr, v.ord::int, v.per::int
FROM ades_materias m CROSS JOIN (VALUES
  ('Características de los seres vivos','Organización, metabolismo, reproducción, homeostasis','1','1'),
  ('Bioquímica celular','Biomoléculas: carbohidratos, proteínas, lípidos, ADN','2','1'),
  ('Célula: estructura y función','Procariota vs. eucariota, organelos, membrana','3','2'),
  ('Metabolismo celular','Fotosíntesis, respiración aeróbica, fermentación','4','2'),
  ('Ciclo celular y reproducción','Mitosis, meiosis, ciclo de vida de la célula','5','3'),
  ('Genética mendeliana','Leyes de Mendel, dominancia, probabilidad genética','6','3'),
  ('Genética molecular','ADN, ARN, síntesis de proteínas, código genético','7','4'),
  ('Biotecnología y bioética','PCR, ingeniería genética, CRISPR, aplicaciones','8','4')
) v(nombre, descr, ord, per)
WHERE m.clave_materia='CBU-BIO1' AND m.is_active=TRUE
  AND NOT EXISTS (SELECT 1 FROM ades_temas WHERE materia_id=m.id AND is_active=TRUE);

-- Biología 2
INSERT INTO ades_temas (id, materia_id, nombre_tema, descripcion, orden, periodo_sugerido)
SELECT gen_random_uuid(), m.id, v.nombre, v.descr, v.ord::int, v.per::int
FROM ades_materias m CROSS JOIN (VALUES
  ('Evolución biológica','Darwin, selección natural, evidencias, especiación','1','1'),
  ('Taxonomía y clasificación de la vida','Dominos, reinos, filos — árbol de la vida','2','1'),
  ('Animales: diversidad y fisiología','Vertebrados e invertebrados, sistemas corporales','3','2'),
  ('Plantas: estructura y reproducción','Anatomía vegetal, fotosíntesis, ciclos reproductivos','4','2'),
  ('Ecología: poblaciones y comunidades','Nicho ecológico, relaciones bióticas, dinámica','5','3'),
  ('Ecosistemas y ciclos biogeoquímicos','Cadenas tróficas, ciclos del carbono y nitrógeno','6','3'),
  ('Biodiversidad de México','Regiones biogeográficas, endemismos, áreas naturales','7','4'),
  ('Problemáticas ambientales globales','Pérdida de biodiversidad, cambio climático, soluciones','8','4')
) v(nombre, descr, ord, per)
WHERE m.clave_materia='CBU-BIO2' AND m.is_active=TRUE
  AND NOT EXISTS (SELECT 1 FROM ades_temas WHERE materia_id=m.id AND is_active=TRUE);

-- Lengua y Comunicación 1
INSERT INTO ades_temas (id, materia_id, nombre_tema, descripcion, orden, periodo_sugerido)
SELECT gen_random_uuid(), m.id, v.nombre, v.descr, v.ord::int, v.per::int
FROM ades_materias m CROSS JOIN (VALUES
  ('La comunicación: elementos y funciones','Emisor, receptor, canal, código, contexto','1','1'),
  ('Comprensión lectora: estrategias globales','Skimming, scanning, inferencia, síntesis','2','1'),
  ('El texto y sus tipos','Descriptivo, narrativo, expositivo, argumentativo','3','2'),
  ('Cohesión y coherencia textual','Conectores, pronombres, repetición, isotopía','4','2'),
  ('El resumen y la síntesis','Técnicas para condensar información con fidelidad','5','3'),
  ('La exposición oral','Planeación, estructura, recursos visuales, evaluación','6','3'),
  ('Ortografía y redacción funcional','Tildes, puntuación, construcción oracional','7','4'),
  ('El ensayo académico básico','Tesis, argumentos, conclusión, citas','8','4')
) v(nombre, descr, ord, per)
WHERE m.clave_materia='CBU-LC1' AND m.is_active=TRUE
  AND NOT EXISTS (SELECT 1 FROM ades_temas WHERE materia_id=m.id AND is_active=TRUE);

-- Lengua y Comunicación 2
INSERT INTO ades_temas (id, materia_id, nombre_tema, descripcion, orden, periodo_sugerido)
SELECT gen_random_uuid(), m.id, v.nombre, v.descr, v.ord::int, v.per::int
FROM ades_materias m CROSS JOIN (VALUES
  ('Géneros discursivos','Ámbitos social, periodístico, académico, literario','1','1'),
  ('El texto argumentativo avanzado','Tipos de argumento, falacias, contraargumentación','2','1'),
  ('El reportaje y la crónica','Periodismo, investigación, redacción narrativa','3','2'),
  ('Intertextualidad y paráfrasis','Citar, parafrasear, alusión, homenaje literario','4','2'),
  ('Lectura crítica de medios','Análisis de noticias, fake news, fuentes confiables','5','3'),
  ('Comunicación digital','E-mail, redes, netiqueta, privacidad digital','6','3'),
  ('El debate y la controversia','Técnicas, roles, evaluación de argumentos','7','4'),
  ('Proyecto de comunicación multimodal','Diseño de producto comunicativo real','8','4')
) v(nombre, descr, ord, per)
WHERE m.clave_materia='CBU-LC2' AND m.is_active=TRUE
  AND NOT EXISTS (SELECT 1 FROM ades_temas WHERE materia_id=m.id AND is_active=TRUE);

-- Lengua y Comunicación 3
INSERT INTO ades_temas (id, materia_id, nombre_tema, descripcion, orden, periodo_sugerido)
SELECT gen_random_uuid(), m.id, v.nombre, v.descr, v.ord::int, v.per::int
FROM ades_materias m CROSS JOIN (VALUES
  ('Lingüística: lenguaje y lengua','Saussure, signo, sincronía, diacronía','1','1'),
  ('Morfología y sintaxis funcional','Análisis oracional, frases, subordinación','2','1'),
  ('Semántica y pragmática','Denotación, connotación, actos de habla','3','2'),
  ('Historia del español en México','Latín, árabe, náhuatl, anglicismos','4','2'),
  ('Lingüística textual','Superestructura, macroestructura, coherencia global','5','3'),
  ('Literatura comparada','Paralelos temáticos entre textos de culturas distintas','6','3'),
  ('Escritura académica avanzada','Tesina, monografía, protocolo de investigación','7','4'),
  ('Proyecto de investigación lingüística','Análisis de corpus o fenómeno de habla local','8','4')
) v(nombre, descr, ord, per)
WHERE m.clave_materia='CBU-LC3' AND m.is_active=TRUE
  AND NOT EXISTS (SELECT 1 FROM ades_temas WHERE materia_id=m.id AND is_active=TRUE);

-- Humanidades 1
INSERT INTO ades_temas (id, materia_id, nombre_tema, descripcion, orden, periodo_sugerido)
SELECT gen_random_uuid(), m.id, v.nombre, v.descr, v.ord::int, v.per::int
FROM ades_materias m CROSS JOIN (VALUES
  ('¿Qué son las humanidades?','Filosofía, historia, arte, literatura como saberes','1','1'),
  ('Filosofía antigua: Sócrates, Platón, Aristóteles','Método socrático, ideas, ética y política','2','1'),
  ('El pensamiento medieval','Escolástica, fe y razón, filosofía árabe','3','2'),
  ('Renacimiento y humanismo','Hombre como centro, arte, ciencia, Reforma','4','2'),
  ('Modernidad filosófica','Descartes, Locke, Hume, Kant','5','3'),
  ('Ilustración y revolución','Enciclopedistas, Rousseau, independencias','6','3'),
  ('El siglo XIX: Hegel, Marx, Nietzsche','Dialéctica, materialismo, nihilismo','7','4'),
  ('Humanidades y mundo contemporáneo','Existencialismo, feminismo filosófico, bioética','8','4')
) v(nombre, descr, ord, per)
WHERE m.clave_materia='CBU-HUM1' AND m.is_active=TRUE
  AND NOT EXISTS (SELECT 1 FROM ades_temas WHERE materia_id=m.id AND is_active=TRUE);

-- Humanidades 2
INSERT INTO ades_temas (id, materia_id, nombre_tema, descripcion, orden, periodo_sugerido)
SELECT gen_random_uuid(), m.id, v.nombre, v.descr, v.ord::int, v.per::int
FROM ades_materias m CROSS JOIN (VALUES
  ('Estética y teoría del arte','Belleza, sublime, arte y experiencia estética','1','1'),
  ('Historia del arte: antigüedad y Edad Media','Arte griego, romano, románico, gótico','2','1'),
  ('Arte renacentista y barroco','Leonardo, Miguel Ángel, Velázquez, Caravaggio','3','2'),
  ('Neoclasicismo, Romanticismo, Impresionismo','Revolución industrial y arte del siglo XIX','4','2'),
  ('Arte moderno y vanguardias','Cubismo, surrealismo, expresionismo abstracto','5','3'),
  ('Arte latinoamericano y mexicano','Muralismo, Rivera, Kahlo, arte contemporáneo','6','3'),
  ('Música y performance como arte','Historia de la música occidental y mexicana','7','4'),
  ('Arte digital y medios contemporáneos','Cine, fotografía, arte interactivo','8','4')
) v(nombre, descr, ord, per)
WHERE m.clave_materia='CBU-HUM2' AND m.is_active=TRUE
  AND NOT EXISTS (SELECT 1 FROM ades_temas WHERE materia_id=m.id AND is_active=TRUE);

-- Humanidades 3
INSERT INTO ades_temas (id, materia_id, nombre_tema, descripcion, orden, periodo_sugerido)
SELECT gen_random_uuid(), m.id, v.nombre, v.descr, v.ord::int, v.per::int
FROM ades_materias m CROSS JOIN (VALUES
  ('Ética: fundamentos y corrientes','Utilitarismo, deontología, virtud, cuidado','1','1'),
  ('Ética aplicada: casos contemporáneos','Bioética, ética ambiental, ética en IA','2','1'),
  ('Filosofía del lenguaje','Wittgenstein, Austin, Searle — significado y uso','3','2'),
  ('Epistemología','Conocimiento, verdad, ciencia, pseudociencia','4','2'),
  ('Filosofía política','Estado, poder, justicia — Rawls, Nozick, Habermas','5','3'),
  ('Derechos humanos y dignidad','Fundamentos filosóficos, instrumentos internacionales','6','3'),
  ('Diálogo intercultural','Multiculturalismo, identidades, reconocimiento','7','4'),
  ('Proyecto humanístico integrador','Ensayo filosófico sobre tema contemporáneo','8','4')
) v(nombre, descr, ord, per)
WHERE m.clave_materia='CBU-HUM3' AND m.is_active=TRUE
  AND NOT EXISTS (SELECT 1 FROM ades_temas WHERE materia_id=m.id AND is_active=TRUE);

-- Conciencia Histórica 1
INSERT INTO ades_temas (id, materia_id, nombre_tema, descripcion, orden, periodo_sugerido)
SELECT gen_random_uuid(), m.id, v.nombre, v.descr, v.ord::int, v.per::int
FROM ades_materias m CROSS JOIN (VALUES
  ('La historia como disciplina','Fuentes, métodos, historiografía','1','1'),
  ('Prehistoria y primeras civilizaciones','Paleolítico, Neolítico, Mesopotamia, Egipto','2','1'),
  ('Civilizaciones clásicas','Grecia y Roma: política, cultura, herencia','3','2'),
  ('Mesoamérica prehispánica','Olmecas, Mayas, Teotihuacan, Mexicas','4','2'),
  ('La Edad Media en Europa y el mundo islámico','Feudalismo, islam, cruzadas, comercio','5','3'),
  ('Los pueblos originarios de América','Aztecas, Incas, otros imperios americanos','6','3'),
  ('El Renacimiento y la Reforma','Humanismo, Reforma protestante, guerras de religión','7','4'),
  ('Conquista y Colonia en México','Proceso de conquista, Virreinato, mestizaje','8','4')
) v(nombre, descr, ord, per)
WHERE m.clave_materia='CBU-CH1' AND m.is_active=TRUE
  AND NOT EXISTS (SELECT 1 FROM ades_temas WHERE materia_id=m.id AND is_active=TRUE);

-- Conciencia Histórica 2
INSERT INTO ades_temas (id, materia_id, nombre_tema, descripcion, orden, periodo_sugerido)
SELECT gen_random_uuid(), m.id, v.nombre, v.descr, v.ord::int, v.per::int
FROM ades_materias m CROSS JOIN (VALUES
  ('Revoluciones atlánticas','Revolución francesa, independencias americanas','1','1'),
  ('Independencia de México','Causas, personajes, etapas, consumación','2','1'),
  ('Liberalismo y conservadurismo en México','Reforma, Benito Juárez, intervención francesa','3','2'),
  ('Imperialismo y colonialismo','Reparto de África, Asia, América Latina','4','2'),
  ('La era del Porfiriato','Modernización, desigualdad, causas del estallido','5','3'),
  ('Revolución Mexicana','Fases, caudillos, Constitución de 1917','6','3'),
  ('Primera Guerra Mundial','Causas, desarrollo, consecuencias, Tratado de Versalles','7','4'),
  ('Entreguerras: crisis y auge del fascismo','Depresión, nazismo, fascismo italiano','8','4')
) v(nombre, descr, ord, per)
WHERE m.clave_materia='CBU-CH2' AND m.is_active=TRUE
  AND NOT EXISTS (SELECT 1 FROM ades_temas WHERE materia_id=m.id AND is_active=TRUE);

-- Conciencia Histórica 3
INSERT INTO ades_temas (id, materia_id, nombre_tema, descripcion, orden, periodo_sugerido)
SELECT gen_random_uuid(), m.id, v.nombre, v.descr, v.ord::int, v.per::int
FROM ades_materias m CROSS JOIN (VALUES
  ('Segunda Guerra Mundial','Causas, frentes, Holocausto, bomba atómica','1','1'),
  ('Guerra Fría y mundo bipolar','URSS vs EUA, Korea, Cuba, Vietnam, carrera espacial','2','1'),
  ('Descolonización y Tercer Mundo','Africa, Asia — nuevas naciones','3','2'),
  ('México posrevolucionario','Cardenismo, PRI, milagro mexicano, 1968','4','2'),
  ('Globalización y neoliberalismo','Fin de la Guerra Fría, TLCAN, mundialización','5','3'),
  ('México contemporáneo','Transición democrática, crisis económicas, NAFTA-T-MEC','6','3'),
  ('Conflictos y retos del siglo XXI','Terrorismo, pandemias, migraciones, cambio climático','7','4'),
  ('Historia y conciencia crítica','Memoria colectiva, derechos humanos, futuro común','8','4')
) v(nombre, descr, ord, per)
WHERE m.clave_materia='CBU-CH3' AND m.is_active=TRUE
  AND NOT EXISTS (SELECT 1 FROM ades_temas WHERE materia_id=m.id AND is_active=TRUE);

-- Ciencias Sociales 1
INSERT INTO ades_temas (id, materia_id, nombre_tema, descripcion, orden, periodo_sugerido)
SELECT gen_random_uuid(), m.id, v.nombre, v.descr, v.ord::int, v.per::int
FROM ades_materias m CROSS JOIN (VALUES
  ('Las ciencias sociales y su método','Sociología, antropología, economía, politología','1','1'),
  ('La sociedad: estructura y estratificación','Clases sociales, movilidad, capital social','2','1'),
  ('Cultura, identidad y diversidad','Multiculturalismo, etnicidad, género','3','2'),
  ('Socialización y educación','Agentes de socialización, escuela, familia, medios','4','2'),
  ('Economía básica: oferta y demanda','Mercados, precios, producción, consumo','5','3'),
  ('El Estado y la democracia','Teorías del Estado, democracia representativa y directa','6','3'),
  ('Desigualdad y exclusión social','Pobreza, marginalidad, políticas sociales','7','4'),
  ('Globalización y sociedad red','TIC, redes sociales, identidades globales','8','4')
) v(nombre, descr, ord, per)
WHERE m.clave_materia='CBU-CS1' AND m.is_active=TRUE
  AND NOT EXISTS (SELECT 1 FROM ades_temas WHERE materia_id=m.id AND is_active=TRUE);

-- Ciencias Sociales 2
INSERT INTO ades_temas (id, materia_id, nombre_tema, descripcion, orden, periodo_sugerido)
SELECT gen_random_uuid(), m.id, v.nombre, v.descr, v.ord::int, v.per::int
FROM ades_materias m CROSS JOIN (VALUES
  ('Geografía humana y urbana','Ciudad, territorio, urbanización, migración','1','1'),
  ('Geopolítica contemporánea','Fronteras, poder, organismos internacionales','2','1'),
  ('Sistema económico mundial','Capitalismo global, corporaciones, trabajo','3','2'),
  ('Medios de comunicación y poder','Opinión pública, propaganda, periodismo crítico','4','2'),
  ('Movimientos sociales y ciudadanía','Feminismo, ambientalismo, LGBTQ+, derechos','5','3'),
  ('Violencia, seguridad y justicia','Crimen organizado, estado de derecho, derechos humanos','6','3'),
  ('Desarrollo sustentable y agenda 2030','ODS, economía circular, cooperación internacional','7','4'),
  ('Proyecto de investigación social','Diagnóstico y propuesta sobre problema local','8','4')
) v(nombre, descr, ord, per)
WHERE m.clave_materia='CBU-CS2' AND m.is_active=TRUE
  AND NOT EXISTS (SELECT 1 FROM ades_temas WHERE materia_id=m.id AND is_active=TRUE);

-- Literatura
INSERT INTO ades_temas (id, materia_id, nombre_tema, descripcion, orden, periodo_sugerido)
SELECT gen_random_uuid(), m.id, v.nombre, v.descr, v.ord::int, v.per::int
FROM ades_materias m CROSS JOIN (VALUES
  ('Literatura: concepto y géneros','Lírica, narrativa, dramática — función estética','1','1'),
  ('Literatura grecolatina','Ilíada, Odisea, Eneida, tragedia griega','2','1'),
  ('Literatura medieval y renacentista','Dante, Cervantes, Quevedo, Sor Juana','3','2'),
  ('Literatura del Siglo de Oro y Barroco','Gongorismo, conceptismo, teatro español','4','2'),
  ('Romanticismo y Realismo','Goethe, Dostoievski, Tolstoi, Dickens','5','3'),
  ('Modernismo y literatura latinoamericana','Rubén Darío, José Martí, Amado Nervo','6','3'),
  ('Boom latinoamericano','García Márquez, Fuentes, Rulfo, Cortázar','7','4'),
  ('Literatura mexicana contemporánea','Elena Poniatowska, Rosario Castellanos, Valeria Luiselli','8','4')
) v(nombre, descr, ord, per)
WHERE m.clave_materia='CBU-LIT' AND m.is_active=TRUE
  AND NOT EXISTS (SELECT 1 FROM ades_temas WHERE materia_id=m.id AND is_active=TRUE);

-- Inglés 1
INSERT INTO ades_temas (id, materia_id, nombre_tema, descripcion, orden, periodo_sugerido)
SELECT gen_random_uuid(), m.id, v.nombre, v.descr, v.ord::int, v.per::int
FROM ades_materias m CROSS JOIN (VALUES
  ('Greetings, introductions and personal information','Simple present tense: be and have','1','1'),
  ('Daily routines and schedules','Simple present tense: affirmative, negative, questions','2','1'),
  ('Describing people and places','Adjectives, comparatives, prepositions of place','3','2'),
  ('Free time activities and hobbies','Present continuous, frequency adverbs','4','2'),
  ('Food, health and lifestyle','Countable/uncountable nouns, quantity expressions','5','3'),
  ('Past events and experiences','Simple past tense: regular and irregular verbs','6','3'),
  ('Future plans and predictions','Will and going to, making decisions','7','4'),
  ('Reading and writing: short texts','Comprehension, e-mail, informal messages','8','4')
) v(nombre, descr, ord, per)
WHERE m.clave_materia='CBU-ING1' AND m.is_active=TRUE
  AND NOT EXISTS (SELECT 1 FROM ades_temas WHERE materia_id=m.id AND is_active=TRUE);

-- Inglés 2
INSERT INTO ades_temas (id, materia_id, nombre_tema, descripcion, orden, periodo_sugerido)
SELECT gen_random_uuid(), m.id, v.nombre, v.descr, v.ord::int, v.per::int
FROM ades_materias m CROSS JOIN (VALUES
  ('Experiences and achievements','Present perfect: have + past participle, ever/never','1','1'),
  ('Comparing and contrasting','Comparatives, superlatives, as … as structures','2','1'),
  ('Abilities and obligations','Modal verbs: can, could, must, should, have to','3','2'),
  ('Expressing opinions and preferences','I think, I believe, in my opinion…','4','2'),
  ('Describing processes','Passive voice in simple present and past','5','3'),
  ('Narrating stories','Past continuous, time connectors, sequence words','6','3'),
  ('Hypothetical situations','Conditional sentences: zero, first and second','7','4'),
  ('Oral and written production: intermediate','Short essay, presentations, dialogues','8','4')
) v(nombre, descr, ord, per)
WHERE m.clave_materia='CBU-ING2' AND m.is_active=TRUE
  AND NOT EXISTS (SELECT 1 FROM ades_temas WHERE materia_id=m.id AND is_active=TRUE);

-- Inglés 3
INSERT INTO ades_temas (id, materia_id, nombre_tema, descripcion, orden, periodo_sugerido)
SELECT gen_random_uuid(), m.id, v.nombre, v.descr, v.ord::int, v.per::int
FROM ades_materias m CROSS JOIN (VALUES
  ('Reporting speech and news','Reported speech: say and tell, tense backshift','1','1'),
  ('Science and technology vocabulary','Collocations, academic word list, definitions','2','1'),
  ('Social issues and debate','Expressing agreement/disagreement, hedging language','3','2'),
  ('Advanced passive constructions','Passive with modals, by + agent, get passive','4','2'),
  ('Reading academic texts','Skimming, scanning, inference, topic sentences','5','3'),
  ('Writing: structured paragraphs','Topic, supporting ideas, concluding sentence','6','3'),
  ('Listening: authentic materials','Podcasts, news clips, lectures — note-taking','7','4'),
  ('Integrated skills project','Research, write and present on a chosen topic','8','4')
) v(nombre, descr, ord, per)
WHERE m.clave_materia='CBU-ING3' AND m.is_active=TRUE
  AND NOT EXISTS (SELECT 1 FROM ades_temas WHERE materia_id=m.id AND is_active=TRUE);

-- Inglés 4
INSERT INTO ades_temas (id, materia_id, nombre_tema, descripcion, orden, periodo_sugerido)
SELECT gen_random_uuid(), m.id, v.nombre, v.descr, v.ord::int, v.per::int
FROM ades_materias m CROSS JOIN (VALUES
  ('Advanced grammar review','Perfect tenses, mixed conditionals, inversion','1','1'),
  ('Academic writing: essay structure','Introduction, body paragraphs, conclusions, transitions','2','1'),
  ('Critical reading and annotation','Text analysis, rhetorical devices, bias detection','3','2'),
  ('Oral communication: discussions','Turn-taking, clarifying, elaborating, summarising','4','2'),
  ('Vocabulary in context: idioms and collocations','Fixed expressions, phrasal verbs for academic use','5','3'),
  ('Research project: design and planning','Research question, sources, citation (APA basics)','6','3'),
  ('Research project: writing and revision','Draft, peer review, revision strategies','7','4'),
  ('Final presentation and portfolio','Oral defense of research, reflective writing','8','4')
) v(nombre, descr, ord, per)
WHERE m.clave_materia='CBU-ING4' AND m.is_active=TRUE
  AND NOT EXISTS (SELECT 1 FROM ades_temas WHERE materia_id=m.id AND is_active=TRUE);

-- Recursos y Ámbitos Socioemocionales 1
INSERT INTO ades_temas (id, materia_id, nombre_tema, descripcion, orden, periodo_sugerido)
SELECT gen_random_uuid(), m.id, v.nombre, v.descr, v.ord::int, v.per::int
FROM ades_materias m CROSS JOIN (VALUES
  ('Autoconocimiento e identidad','Quién soy, mis valores, fortalezas y áreas de mejora','1','1'),
  ('Gestión de emociones básicas','Reconocer, nombrar y regular emociones','2','1'),
  ('Comunicación asertiva','Escucha activa, expresión de necesidades, límites','3','2'),
  ('Relaciones interpersonales','Tipos de relaciones, amistad, conflicto y resolución','4','2'),
  ('Empatía y perspectiva social','Ponerse en lugar del otro, diversidad humana','5','3'),
  ('Bienestar y autocuidado','Sueño, nutrición, ejercicio, gestión del estrés','6','3'),
  ('Toma de decisiones responsable','Proceso de decisión, consecuencias, ética personal','7','4'),
  ('Proyecto de desarrollo personal','Metas SMART, plan de acción, seguimiento','8','4')
) v(nombre, descr, ord, per)
WHERE m.clave_materia='CBU-RAS1' AND m.is_active=TRUE
  AND NOT EXISTS (SELECT 1 FROM ades_temas WHERE materia_id=m.id AND is_active=TRUE);

-- Recursos y Ámbitos Socioemocionales 2
INSERT INTO ades_temas (id, materia_id, nombre_tema, descripcion, orden, periodo_sugerido)
SELECT gen_random_uuid(), m.id, v.nombre, v.descr, v.ord::int, v.per::int
FROM ades_materias m CROSS JOIN (VALUES
  ('Inteligencia emocional avanzada','Modelo Salovey-Mayer, aplicaciones cotidianas','1','1'),
  ('Manejo del estrés y la ansiedad','Técnicas de relajación, mindfulness básico','2','1'),
  ('Habilidades de negociación','Negociación colaborativa, mediación, ganar-ganar','3','2'),
  ('Liderazgo y trabajo en equipo','Estilos de liderazgo, roles de equipo, sinergia','4','2'),
  ('Resiliencia y afrontamiento','Factores protectores, superación de adversidades','5','3'),
  ('Ciudadanía digital y bienestar online','Huella digital, redes sociales y salud mental','6','3'),
  ('Proyecto social comunitario','Diseño e implementación de acción solidaria','7','4'),
  ('Reflexión y crecimiento personal','Evaluación del semestre, nuevas metas','8','4')
) v(nombre, descr, ord, per)
WHERE m.clave_materia='CBU-RAS2' AND m.is_active=TRUE
  AND NOT EXISTS (SELECT 1 FROM ades_temas WHERE materia_id=m.id AND is_active=TRUE);

-- Recursos y Ámbitos Socioemocionales 3
INSERT INTO ades_temas (id, materia_id, nombre_tema, descripcion, orden, periodo_sugerido)
SELECT gen_random_uuid(), m.id, v.nombre, v.descr, v.ord::int, v.per::int
FROM ades_materias m CROSS JOIN (VALUES
  ('Proyecto de vida: visión y misión personal','Valores, propósito, vocación','1','1'),
  ('Planificación de metas de largo plazo','Objetivos educativos, profesionales y personales','2','1'),
  ('Gestión del tiempo y productividad','Matriz Eisenhower, hábitos, técnica Pomodoro','3','2'),
  ('Finanzas personales básicas','Presupuesto, ahorro, inversión, deudas','4','2'),
  ('Empleabilidad y mercado laboral','CV, entrevista, networking, emprendimiento','5','3'),
  ('Diversidad, inclusión y equidad','Sesgos inconscientes, accesibilidad, interseccionalidad','6','3'),
  ('Bienestar comunitario y servicio','Voluntariado, servicio social, impacto positivo','7','4'),
  ('Síntesis y proyecto de vida final','Presentación del proyecto de vida integrado','8','4')
) v(nombre, descr, ord, per)
WHERE m.clave_materia='CBU-RAS3' AND m.is_active=TRUE
  AND NOT EXISTS (SELECT 1 FROM ades_temas WHERE materia_id=m.id AND is_active=TRUE);

-- Desarrollo Personal y Emocional
INSERT INTO ades_temas (id, materia_id, nombre_tema, descripcion, orden, periodo_sugerido)
SELECT gen_random_uuid(), m.id, v.nombre, v.descr, v.ord::int, v.per::int
FROM ades_materias m CROSS JOIN (VALUES
  ('Psicología del desarrollo adolescente','Cambios físicos, cognitivos y emocionales en la adolescencia','1','1'),
  ('Autoestima y autoconcepto','Construcción de la autoestima, crítica interna, crecimiento','2','1'),
  ('Motivación y metas','Teorías de la motivación, motivación intrínseca/extrínseca','3','2'),
  ('Comunicación y relaciones afectivas','Amistad, amor, relaciones sanas vs. tóxicas','4','2'),
  ('Sexualidad, género e identidad','Diversidad sexual, prevención, afectividad responsable','5','3'),
  ('Salud mental: conceptos básicos','Depresión, ansiedad, buscar ayuda, desestigmatización','6','3'),
  ('Creatividad y resolución de problemas','Design thinking, creatividad aplicada, innovación','7','4'),
  ('Mi proyecto de vida: síntesis','Integración de aprendizajes y elaboración de plan personal','8','4')
) v(nombre, descr, ord, per)
WHERE m.clave_materia='CBU-DPE' AND m.is_active=TRUE
  AND NOT EXISTS (SELECT 1 FROM ades_temas WHERE materia_id=m.id AND is_active=TRUE);

-- Orientación Vocacional
INSERT INTO ades_temas (id, materia_id, nombre_tema, descripcion, orden, periodo_sugerido)
SELECT gen_random_uuid(), m.id, v.nombre, v.descr, v.ord::int, v.per::int
FROM ades_materias m CROSS JOIN (VALUES
  ('Autoconocimiento vocacional','Intereses, aptitudes, valores y estilos de aprendizaje','1','1'),
  ('Exploración de carreras','Clasificación de profesiones, entrevistas a profesionistas','2','1'),
  ('Educación superior en México','UNAM, UAEMEX, IPN, universidades privadas — opciones','3','2'),
  ('Proceso de admisión UAEMEX y COMIPEMS','Convocatorias, puntajes, estrategias de estudio','4','2'),
  ('Mundo laboral y competencias del siglo XXI','Habilidades blandas, tecnología, emprendimiento','5','3'),
  ('Finanzas para estudiar','Becas, créditos, administración de recursos','6','3'),
  ('Decisión vocacional informada','Análisis de opciones, plan de acción','7','4'),
  ('Presentación del proyecto vocacional','Exposición de elección de carrera fundamentada','8','4')
) v(nombre, descr, ord, per)
WHERE m.clave_materia='CBU-OV' AND m.is_active=TRUE
  AND NOT EXISTS (SELECT 1 FROM ades_temas WHERE materia_id=m.id AND is_active=TRUE);

-- Psicología
INSERT INTO ades_temas (id, materia_id, nombre_tema, descripcion, orden, periodo_sugerido)
SELECT gen_random_uuid(), m.id, v.nombre, v.descr, v.ord::int, v.per::int
FROM ades_materias m CROSS JOIN (VALUES
  ('Historia y corrientes de la psicología','Conductismo, psicoanálisis, humanismo, cognitivo','1','1'),
  ('Bases biológicas del comportamiento','Neurociencia, sistema nervioso, cerebro y conducta','2','1'),
  ('Sensación y percepción','Órganos sensoriales, percepción, ilusiones','3','2'),
  ('Aprendizaje y condicionamiento','Pavlov, Skinner, Bandura, aprendizaje social','4','2'),
  ('Memoria y cognición','Tipos de memoria, procesamiento de información','5','3'),
  ('Emoción y motivación','Teorías emocionales, necesidades, jerarquía de Maslow','6','3'),
  ('Personalidad y diferencias individuales','Teorías de la personalidad, rasgos, Big Five','7','4'),
  ('Psicología social y psicopatología','Conformismo, prejuicio, clasificación de trastornos','8','4')
) v(nombre, descr, ord, per)
WHERE m.clave_materia='CBU-PSI' AND m.is_active=TRUE
  AND NOT EXISTS (SELECT 1 FROM ades_temas WHERE materia_id=m.id AND is_active=TRUE);

-- Desarrollo Emprendedor
INSERT INTO ades_temas (id, materia_id, nombre_tema, descripcion, orden, periodo_sugerido)
SELECT gen_random_uuid(), m.id, v.nombre, v.descr, v.ord::int, v.per::int
FROM ades_materias m CROSS JOIN (VALUES
  ('El emprendedor y su mentalidad','Actitudes emprendedoras, tolerancia al riesgo, perseverancia','1','1'),
  ('Identificación de oportunidades','Observación del entorno, necesidades, problemas a resolver','2','1'),
  ('Design Thinking y creatividad','Empatizar, definir, idear, prototipar, testear','3','2'),
  ('Modelo de negocio Canvas','Propuesta de valor, segmentos, canales, recursos','4','2'),
  ('Estudio de mercado básico','Clientes, competencia, encuestas, análisis FODA','5','3'),
  ('Finanzas para emprendedores','Costos, punto de equilibrio, flujo de caja','6','3'),
  ('Prototipo y pitch de negocio','Desarrollo de MVP, presentación ante inversores','7','4'),
  ('Proyecto emprendedor final','Presentación de plan de negocio completo','8','4')
) v(nombre, descr, ord, per)
WHERE m.clave_materia='CBU-DE' AND m.is_active=TRUE
  AND NOT EXISTS (SELECT 1 FROM ades_temas WHERE materia_id=m.id AND is_active=TRUE);

-- Desarrollo Social
INSERT INTO ades_temas (id, materia_id, nombre_tema, descripcion, orden, periodo_sugerido)
SELECT gen_random_uuid(), m.id, v.nombre, v.descr, v.ord::int, v.per::int
FROM ades_materias m CROSS JOIN (VALUES
  ('El ser humano como ser social','Individuo, sociedad, contrato social','1','1'),
  ('Instituciones sociales','Familia, escuela, iglesia, Estado — funciones y cambios','2','1'),
  ('Estructura social y movilidad','Clases sociales, capital social, ascenso social','3','2'),
  ('Problemas sociales de México','Pobreza, desigualdad, violencia, impunidad','4','2'),
  ('Participación ciudadana','Mecanismos de participación, sociedad civil, ONG','5','3'),
  ('Voluntariado y servicio social','Experiencia de campo en comunidad','6','3'),
  ('Desarrollo social sustentable','ODS, bienestar integral, economía social','7','4'),
  ('Proyecto de intervención social','Diseño e implementación de acción comunitaria','8','4')
) v(nombre, descr, ord, per)
WHERE m.clave_materia='CBU-DS' AND m.is_active=TRUE
  AND NOT EXISTS (SELECT 1 FROM ades_temas WHERE materia_id=m.id AND is_active=TRUE);

-- Cultura de Paz
INSERT INTO ades_temas (id, materia_id, nombre_tema, descripcion, orden, periodo_sugerido)
SELECT gen_random_uuid(), m.id, v.nombre, v.descr, v.ord::int, v.per::int
FROM ades_materias m CROSS JOIN (VALUES
  ('Paz positiva y paz negativa','Definición de Johan Galtung, violencia directa/estructural','1','1'),
  ('Conflicto: tipos y análisis','Conflicto interpersonal, social, armado — análisis','2','1'),
  ('Comunicación no violenta','Observación, sentimiento, necesidad, petición (CNV)','3','2'),
  ('Mediación y resolución de conflictos','Proceso de mediación, rol del mediador','4','2'),
  ('Derechos humanos y paz','Instrumentos internacionales, DIH, mecanismos de protección','5','3'),
  ('Tolerancia, diversidad e inclusión','Discriminación, xenofobia, combate al odio','6','3'),
  ('Movimientos pacifistas históricos','Gandhi, Luther King, Mandela, EZLN','7','4'),
  ('Proyecto de cultura de paz','Campaña o taller de paz en la escuela','8','4')
) v(nombre, descr, ord, per)
WHERE m.clave_materia='CBU-CP' AND m.is_active=TRUE
  AND NOT EXISTS (SELECT 1 FROM ades_temas WHERE materia_id=m.id AND is_active=TRUE);

-- Cultura Digital
INSERT INTO ades_temas (id, materia_id, nombre_tema, descripcion, orden, periodo_sugerido)
SELECT gen_random_uuid(), m.id, v.nombre, v.descr, v.ord::int, v.per::int
FROM ades_materias m CROSS JOIN (VALUES
  ('Sociedad de la información y del conocimiento','Brecha digital, economía digital, ciudadanía digital','1','1'),
  ('Pensamiento computacional','Algoritmos, descomposición, patrones, abstracción','2','1'),
  ('Programación básica','Scratch o Python: variables, condicionales, ciclos','3','2'),
  ('Bases de datos y manejo de información','Hojas de cálculo, bases relacionales, consultas básicas','4','2'),
  ('Seguridad informática y privacidad','Contraseñas, phishing, malware, protección de datos','5','3'),
  ('Inteligencia artificial básica','Machine learning, IA generativa, usos y riesgos','6','3'),
  ('Producción de contenidos digitales','Video, podcast, infografía — derechos de autor','7','4'),
  ('Ciudadanía digital y ética tecnológica','Derechos digitales, fake news, responsabilidad online','8','4')
) v(nombre, descr, ord, per)
WHERE m.clave_materia='CBU-CD' AND m.is_active=TRUE
  AND NOT EXISTS (SELECT 1 FROM ades_temas WHERE materia_id=m.id AND is_active=TRUE);

-- Cultura Ambiental y Desarrollo Sustentable
INSERT INTO ades_temas (id, materia_id, nombre_tema, descripcion, orden, periodo_sugerido)
SELECT gen_random_uuid(), m.id, v.nombre, v.descr, v.ord::int, v.per::int
FROM ades_materias m CROSS JOIN (VALUES
  ('Desarrollo sustentable: concepto y dimensiones','Brundtland, triple bottom line, sostenibilidad','1','1'),
  ('Cambio climático: causas y evidencia','Efecto invernadero, gases GEI, datos del IPCC','2','1'),
  ('Impactos del cambio climático en México','Sequías, huracanes, pérdida de biodiversidad','3','2'),
  ('Energías renovables','Solar, eólica, hidráulica, geotérmica — potencial en México','4','2'),
  ('Economía circular y cero residuos','Ciclo de vida del producto, reciclaje, upcycling','5','3'),
  ('Agua: gestión y conservación','Ciclo hidrológico, escasez, agua virtual, huella hídrica','6','3'),
  ('Biodiversidad y áreas naturales protegidas','Servicios ecosistémicos, áreas protegidas de México','7','4'),
  ('Proyecto de acción ambiental escolar','Diagnóstico y propuesta de mejora ambiental','8','4')
) v(nombre, descr, ord, per)
WHERE m.clave_materia='CBU-CADS' AND m.is_active=TRUE
  AND NOT EXISTS (SELECT 1 FROM ades_temas WHERE materia_id=m.id AND is_active=TRUE);

-- Metodología de Investigación y Taller 1
INSERT INTO ades_temas (id, materia_id, nombre_tema, descripcion, orden, periodo_sugerido)
SELECT gen_random_uuid(), m.id, v.nombre, v.descr, v.ord::int, v.per::int
FROM ades_materias m CROSS JOIN (VALUES
  ('El conocimiento científico','Ciencia, pseudociencia, tipos de conocimiento','1','1'),
  ('El método científico','Observación, hipótesis, experimentación, conclusión','2','1'),
  ('Tipos de investigación','Cuantitativa, cualitativa, mixta, básica, aplicada','3','2'),
  ('Planteamiento del problema','Problema, objetivo general y específicos, justificación','4','2'),
  ('Marco teórico y revisión de literatura','Búsqueda en bases académicas, fichas bibliográficas','5','3'),
  ('Metodología: instrumentos de recolección','Encuesta, entrevista, observación, experimento','6','3'),
  ('Análisis e interpretación de datos','Estadística descriptiva aplicada, tablas y gráficas','7','4'),
  ('Reporte de investigación básico','Estructura IMRAD, APA 7ª, presentación oral','8','4')
) v(nombre, descr, ord, per)
WHERE m.clave_materia='CBU-MIT1' AND m.is_active=TRUE
  AND NOT EXISTS (SELECT 1 FROM ades_temas WHERE materia_id=m.id AND is_active=TRUE);

-- Metodología de Investigación y Taller 2
INSERT INTO ades_temas (id, materia_id, nombre_tema, descripcion, orden, periodo_sugerido)
SELECT gen_random_uuid(), m.id, v.nombre, v.descr, v.ord::int, v.per::int
FROM ades_materias m CROSS JOIN (VALUES
  ('Investigación cualitativa en profundidad','Etnografía, fenomenología, investigación-acción','1','1'),
  ('Diseño de instrumentos cualitativos','Guía de entrevista, observación participante','2','1'),
  ('Análisis de contenido y discurso','Categorías, codificación, saturación teórica','3','2'),
  ('Estadística inferencial básica aplicada','Prueba t, chi cuadrada, correlación','4','2'),
  ('Validez y confiabilidad en la investigación','Triangulación, prueba piloto, fiabilidad','5','3'),
  ('Ética en la investigación','Consentimiento informado, confidencialidad, plagio','6','3'),
  ('Redacción científica avanzada','Artículo, ponencia, póster de investigación','7','4'),
  ('Proyecto de investigación completo','Defensa ante comité evaluador','8','4')
) v(nombre, descr, ord, per)
WHERE m.clave_materia='CBU-MIT2' AND m.is_active=TRUE
  AND NOT EXISTS (SELECT 1 FROM ades_temas WHERE materia_id=m.id AND is_active=TRUE);

-- Apreciación y Expresión Artística 1
INSERT INTO ades_temas (id, materia_id, nombre_tema, descripcion, orden, periodo_sugerido)
SELECT gen_random_uuid(), m.id, v.nombre, v.descr, v.ord::int, v.per::int
FROM ades_materias m CROSS JOIN (VALUES
  ('El arte: definición y funciones','Arte como forma de conocimiento y comunicación','1','1'),
  ('Elementos del lenguaje visual','Punto, línea, forma, color, textura, espacio','2','1'),
  ('Historia del arte: antigüedad a Renacimiento','Arte prehistórico, egipcio, griego, medieval, renacentista','3','2'),
  ('Arte moderno: siglos XIX y XX','Impresionismo, cubismo, expresionismo, surrealismo','4','2'),
  ('Música: elementos y géneros','Ritmo, melodía, armonía — géneros clásicos y populares','5','3'),
  ('Teatro y danza','Expresión corporal, teatro del oprimido, danza mexicana','6','3'),
  ('Arte latinoamericano y mexicano','Muralismo, arte popular, artesanía, identidad','7','4'),
  ('Taller de creación artística','Producción de obra personal con técnica libre','8','4')
) v(nombre, descr, ord, per)
WHERE m.clave_materia='CBU-AEA1' AND m.is_active=TRUE
  AND NOT EXISTS (SELECT 1 FROM ades_temas WHERE materia_id=m.id AND is_active=TRUE);

-- Apreciación y Expresión Artística 2
INSERT INTO ades_temas (id, materia_id, nombre_tema, descripcion, orden, periodo_sugerido)
SELECT gen_random_uuid(), m.id, v.nombre, v.descr, v.ord::int, v.per::int
FROM ades_materias m CROSS JOIN (VALUES
  ('Arte contemporáneo y posmoderno','Arte conceptual, performance, instalación','1','1'),
  ('Arte digital y nuevos medios','Fotografía artística, video arte, net art','2','1'),
  ('Análisis crítico de obras','Método iconológico, contextualización histórica','3','2'),
  ('Diseño gráfico y comunicación visual','Tipografía, composición, branding, publicidad','4','2'),
  ('Cine como arte y lenguaje','Historia del cine, análisis de películas, géneros','5','3'),
  ('Música de México y el mundo','Géneros actuales, etnomusicología, producción musical','6','3'),
  ('Arte y compromiso social','Artivismo, muralismo comunitario, arte callejero','7','4'),
  ('Proyecto artístico integrador','Creación y exposición de obra colectiva','8','4')
) v(nombre, descr, ord, per)
WHERE m.clave_materia='CBU-AEA2' AND m.is_active=TRUE
  AND NOT EXISTS (SELECT 1 FROM ades_temas WHERE materia_id=m.id AND is_active=TRUE);

-- Actividades Físicas y Deportivas 1
INSERT INTO ades_temas (id, materia_id, nombre_tema, descripcion, orden, periodo_sugerido)
SELECT gen_random_uuid(), m.id, v.nombre, v.descr, v.ord::int, v.per::int
FROM ades_materias m CROSS JOIN (VALUES
  ('Cultura física y salud','Beneficios del ejercicio, sedentarismo, hábitos saludables','1','1'),
  ('Calentamiento y vuelta a la calma','Movilidad articular, estiramientos, RPE','2','1'),
  ('Capacidades físicas básicas','Resistencia, fuerza, flexibilidad, velocidad','3','2'),
  ('Atletismo básico','Carrera, salto, lanzamiento — técnica y reglamento','4','2'),
  ('Juegos y deportes colectivos I','Voleibol o basquetbol — fundamentos y reglas','5','3'),
  ('Habilidades motrices y coordinación','Equilibrio, coordinación ojo-mano, lateralidad','6','3'),
  ('Evaluación física y plan de mejora','Test de condición física, metas de mejora','7','4'),
  ('Actividad física y bienestar integral','Relación deporte-salud mental, ocio activo','8','4')
) v(nombre, descr, ord, per)
WHERE m.clave_materia='CBU-AFD1' AND m.is_active=TRUE
  AND NOT EXISTS (SELECT 1 FROM ades_temas WHERE materia_id=m.id AND is_active=TRUE);

-- Actividades Físicas y Deportivas 2
INSERT INTO ades_temas (id, materia_id, nombre_tema, descripcion, orden, periodo_sugerido)
SELECT gen_random_uuid(), m.id, v.nombre, v.descr, v.ord::int, v.per::int
FROM ades_materias m CROSS JOIN (VALUES
  ('Entrenamiento de resistencia','Métodos aeróbicos, zonas de frecuencia cardíaca','1','1'),
  ('Entrenamiento de fuerza y potencia','Pesas, TRX, fundamentos de musculación segura','2','1'),
  ('Deportes colectivos II','Fútbol soccer — técnica, táctica y reglamento','3','2'),
  ('Deportes de raqueta','Tenis de mesa o bádminton — técnica y partido','4','2'),
  ('Natación o actividad acuática','Técnica de brazada, patada, respiración','5','3'),
  ('Primeros auxilios básicos','RCP, fracturas, golpe de calor, activación SAMU','6','3'),
  ('Nutrición y rendimiento deportivo','Macronutrientes, hidratación, suplementación básica','7','4'),
  ('Torneo y evaluación de competencia','Aplicación de habilidades en formato competitivo','8','4')
) v(nombre, descr, ord, per)
WHERE m.clave_materia='CBU-AFD2' AND m.is_active=TRUE
  AND NOT EXISTS (SELECT 1 FROM ades_temas WHERE materia_id=m.id AND is_active=TRUE);

-- Actividades Físicas y Deportivas 3
INSERT INTO ades_temas (id, materia_id, nombre_tema, descripcion, orden, periodo_sugerido)
SELECT gen_random_uuid(), m.id, v.nombre, v.descr, v.ord::int, v.per::int
FROM ades_materias m CROSS JOIN (VALUES
  ('Deporte alternativo y aventura','Senderismo, escalada, orientación — valores','1','1'),
  ('Actividades rítmicas y expresión','Baile deportivo, zumba, danza folclórica','2','1'),
  ('Deportes de combate','Defensa personal, judo o kárate — filosofía y técnica','3','2'),
  ('Planificación del entrenamiento','Periodización, ciclos, sobrecarga progresiva','4','2'),
  ('Deporte y sociedad','Historia olímpica, dopaje, fair play, inclusión','5','3'),
  ('Psicología del deporte','Concentración, visualización, presión competitiva','6','3'),
  ('Actividad física adaptada','Deporte paraolímpico, inclusión, diseño universal','7','4'),
  ('Proyecto deportivo escolar','Organización de evento, arbitraje y liderazgo','8','4')
) v(nombre, descr, ord, per)
WHERE m.clave_materia='CBU-AFD3' AND m.is_active=TRUE
  AND NOT EXISTS (SELECT 1 FROM ades_temas WHERE materia_id=m.id AND is_active=TRUE);

-- Actividades Físicas y Deportivas 4
INSERT INTO ades_temas (id, materia_id, nombre_tema, descripcion, orden, periodo_sugerido)
SELECT gen_random_uuid(), m.id, v.nombre, v.descr, v.ord::int, v.per::int
FROM ades_materias m CROSS JOIN (VALUES
  ('Entrenamiento funcional avanzado','HIIT, circuitos, movimientos funcionales','1','1'),
  ('Deporte individual de elección libre','Plan personal de práctica de deporte elegido','2','1'),
  ('Análisis biomecánico del movimiento','Planos, ejes, palancas, análisis de gestos','3','2'),
  ('Lesiones deportivas y prevención','Calentamiento específico, vendaje, RICE','4','2'),
  ('Entrenador y liderazgo deportivo','Roles, comunicación, diseño de sesión de entrenamiento','5','3'),
  ('Deporte y comunidad','Voluntariado deportivo, ligas comunitarias','6','3'),
  ('Evaluación final de condición física','Baterías: Eurofit, FitnessGram, interpretación','7','4'),
  ('Proyecto integrador de cultura física','Informe de progreso y plan de actividad física futura','8','4')
) v(nombre, descr, ord, per)
WHERE m.clave_materia='CBU-AFD4' AND m.is_active=TRUE
  AND NOT EXISTS (SELECT 1 FROM ades_temas WHERE materia_id=m.id AND is_active=TRUE);

-- Optativas 1–4 (contenido genérico enriquecido)
INSERT INTO ades_temas (id, materia_id, nombre_tema, descripcion, orden, periodo_sugerido)
SELECT gen_random_uuid(), m.id, v.nombre, v.descr, v.ord::int, v.per::int
FROM ades_materias m CROSS JOIN (VALUES
  ('Diagnóstico de necesidades e intereses','Identificación del tema de la optativa con el grupo','1','1'),
  ('Marco teórico y conceptual','Conceptos clave, antecedentes, contexto del tema','2','1'),
  ('Exploración práctica — Unidad I','Actividades de aplicación y experimentación','3','2'),
  ('Exploración práctica — Unidad II','Profundización y variantes del tema','4','2'),
  ('Proyecto intermedio','Entrega parcial con retroalimentación docente','5','3'),
  ('Exploración práctica — Unidad III','Integración de aprendizajes previos','6','3'),
  ('Proyecto final: diseño','Planeación del producto integrador final','7','4'),
  ('Presentación y evaluación final','Exposición, demostración o entrega de producto','8','4')
) v(nombre, descr, ord, per)
WHERE m.clave_materia IN ('CBU-OPT1','CBU-OPT2','CBU-OPT3','CBU-OPT4')
  AND m.is_active=TRUE
  AND NOT EXISTS (SELECT 1 FROM ades_temas WHERE materia_id=m.id AND is_active=TRUE);

-- ────────────────────────────────────────────────────────────
-- BLOQUE 3: MATERIAS NEVADI (institucionales)
-- tipo_materia: NEVADI_FORMATIVA, NEVADI_ENRIQUECIMIENTO, NEVADI_ESPECIALIZADA
-- ────────────────────────────────────────────────────────────

INSERT INTO ades_materias (
  id, nombre_materia, clave_materia, nivel_educativo_id,
  horas_semana, tipo_materia, reporta_a_sep_uaemex, incluir_en_boleta,
  ponderacion_default, is_active
)
SELECT
  gen_random_uuid(),
  v.nombre, v.clave,
  (SELECT id FROM ades_niveles_educativos WHERE nombre_nivel ILIKE '%' || v.nivel || '%' LIMIT 1),
  v.hrs::int,
  v.tipo::character varying,
  FALSE, TRUE,
  v.pond::numeric,
  TRUE
FROM (VALUES
  -- Primaria: Nevadi formativas/enriquecimiento
  ('Informática y Pensamiento Computacional', 'NVI-PRI-INF', 'PRIMARIA',  '2', 'NEVADI_FORMATIVA',      '0.1000'),
  ('Taller de Inglés Intensivo',              'NVI-PRI-ING', 'PRIMARIA',  '2', 'NEVADI_ENRIQUECIMIENTO','0.1000'),
  -- Secundaria
  ('Informática y Tecnología Digital',        'NVI-SEC-INF', 'SECUNDARIA','2', 'NEVADI_FORMATIVA',      '0.1000'),
  ('Taller de Inglés Intensivo SEC',          'NVI-SEC-ING', 'SECUNDARIA','2', 'NEVADI_ENRIQUECIMIENTO','0.1000'),
  ('Orientación y Proyecto de Vida',          'NVI-SEC-ORI', 'SECUNDARIA','2', 'NEVADI_FORMATIVA',      '0.1000'),
  -- Preparatoria
  ('Habilidades Digitales Avanzadas',         'NVI-PREP-HAD','PREPARATORIA','2','NEVADI_ESPECIALIZADA', '0.1000'),
  ('Emprendimiento e Innovación Nevadi',      'NVI-PREP-EMP','PREPARATORIA','2','NEVADI_ESPECIALIZADA', '0.1000')
) v(nombre, clave, nivel, hrs, tipo, pond)
WHERE NOT EXISTS (
  SELECT 1 FROM ades_materias WHERE clave_materia = v.clave
);

-- ────────────────────────────────────────────────────────────
-- BLOQUE 4: TEMAS NEVADI
-- ────────────────────────────────────────────────────────────

-- NVI-PRI-INF: Informática y Pensamiento Computacional (Primaria)
INSERT INTO ades_temas (id, materia_id, nombre_tema, descripcion, orden, periodo_sugerido)
SELECT gen_random_uuid(), m.id, v.nombre, v.descr, v.ord::int, v.per::int
FROM ades_materias m CROSS JOIN (VALUES
  ('Introducción a las computadoras','Hardware, software, partes y cuidado del equipo','1','1'),
  ('Manejo del teclado y ratón','Técnica mecanográfica, accesos directos básicos','2','1'),
  ('Procesador de texto básico','Word/Writer — formato, guardado, impresión','3','2'),
  ('Presentaciones digitales','PowerPoint/Impress — diapositivas y diseño','4','2'),
  ('Internet seguro','Búsqueda, navegación segura, privacidad en niños','5','3'),
  ('Pensamiento computacional básico','Algoritmos con Scratch — secuencias y bucles','6','3'),
  ('Hoja de cálculo básica','Suma, promedio, gráficas simples en Excel/Calc','7','4'),
  ('Proyecto digital integrador','Producción multimedia: presentación o video corto','8','4')
) v(nombre, descr, ord, per)
WHERE m.clave_materia='NVI-PRI-INF' AND m.is_active=TRUE
  AND NOT EXISTS (SELECT 1 FROM ades_temas WHERE materia_id=m.id AND is_active=TRUE);

-- NVI-PRI-ING: Taller de Inglés Intensivo (Primaria)
INSERT INTO ades_temas (id, materia_id, nombre_tema, descripcion, orden, periodo_sugerido)
SELECT gen_random_uuid(), m.id, v.nombre, v.descr, v.ord::int, v.per::int
FROM ades_materias m CROSS JOIN (VALUES
  ('Greetings and classroom language','Saludos, despedidas, normas del salón en inglés','1','1'),
  ('Numbers, colors and shapes','Vocabulario básico con juegos y canciones','2','1'),
  ('Family and friends','My family, describing people, possessives','3','2'),
  ('My school and my day','School objects, schedule, simple present','4','2'),
  ('Animals and nature','Wild, domestic, habitats — describing animals','5','3'),
  ('Food and celebrations','Food, birthdays, Mexican holidays in English','6','3'),
  ('Simple stories and songs','Reading picture books, singing in English','7','4'),
  ('Mini English fair','Presentación oral de tema libre en inglés','8','4')
) v(nombre, descr, ord, per)
WHERE m.clave_materia='NVI-PRI-ING' AND m.is_active=TRUE
  AND NOT EXISTS (SELECT 1 FROM ades_temas WHERE materia_id=m.id AND is_active=TRUE);

-- NVI-SEC-INF: Informática y Tecnología Digital (Secundaria)
INSERT INTO ades_temas (id, materia_id, nombre_tema, descripcion, orden, periodo_sugerido)
SELECT gen_random_uuid(), m.id, v.nombre, v.descr, v.ord::int, v.per::int
FROM ades_materias m CROSS JOIN (VALUES
  ('Sistemas operativos y productividad','Windows/Linux básico, gestión de archivos, nube','1','1'),
  ('Procesamiento avanzado de texto','Estilos, tablas, referencias, combinación de correspondencia','2','1'),
  ('Hojas de cálculo intermedias','Fórmulas, funciones SI, BUSCARV, gráficas dinámicas','3','2'),
  ('Bases de datos básicas','Access/LibreBase — tablas, consultas, formularios','4','2'),
  ('Programación con Python I','Variables, tipos, entrada/salida, condicionales','5','3'),
  ('Programación con Python II','Ciclos, listas, funciones, mini proyectos','6','3'),
  ('Diseño web básico','HTML, CSS, estructura de página, publicación','7','4'),
  ('Proyecto tecnológico integrador','Aplicación web, app o automatización con Python','8','4')
) v(nombre, descr, ord, per)
WHERE m.clave_materia='NVI-SEC-INF' AND m.is_active=TRUE
  AND NOT EXISTS (SELECT 1 FROM ades_temas WHERE materia_id=m.id AND is_active=TRUE);

-- NVI-SEC-ING: Taller de Inglés Intensivo (Secundaria)
INSERT INTO ades_temas (id, materia_id, nombre_tema, descripcion, orden, periodo_sugerido)
SELECT gen_random_uuid(), m.id, v.nombre, v.descr, v.ord::int, v.per::int
FROM ades_materias m CROSS JOIN (VALUES
  ('Conversation starters and social English','Small talk, likes/dislikes, opinion expressions','1','1'),
  ('Grammar boost: present and past tenses','Revision, common errors, practice drills','2','1'),
  ('Reading for pleasure: graded readers','Extensive reading, vocabulary in context','3','2'),
  ('Listening skills: songs, podcasts, news','Prediction, gist, specific information','4','2'),
  ('Speaking: storytelling and role-play','Fluency activities, pronunciation focus','5','3'),
  ('Writing: emails and messages','Formal vs informal register, structure','6','3'),
  ('Cultural connections: English-speaking world','Countries, traditions, food, music','7','4'),
  ('English Talent Show','Final performance: song, poem, skit or speech','8','4')
) v(nombre, descr, ord, per)
WHERE m.clave_materia='NVI-SEC-ING' AND m.is_active=TRUE
  AND NOT EXISTS (SELECT 1 FROM ades_temas WHERE materia_id=m.id AND is_active=TRUE);

-- NVI-SEC-ORI: Orientación y Proyecto de Vida (Secundaria)
INSERT INTO ades_temas (id, materia_id, nombre_tema, descripcion, orden, periodo_sugerido)
SELECT gen_random_uuid(), m.id, v.nombre, v.descr, v.ord::int, v.per::int
FROM ades_materias m CROSS JOIN (VALUES
  ('Autoconocimiento: quién soy','Valores, fortalezas, áreas de oportunidad','1','1'),
  ('Comunicación efectiva y asertividad','Estilos de comunicación, manejo de conflictos','2','1'),
  ('Educación socioemocional','Inteligencia emocional, regulación, empatía','3','2'),
  ('Prevención de adicciones','Factores de riesgo y protección, toma de decisiones','4','2'),
  ('Sexualidad y salud reproductiva','Pubertad, anticoncepción, ITS, relaciones sanas','5','3'),
  ('Exploración vocacional','Intereses, aptitudes, carreras, COMIPEMS','6','3'),
  ('Proyecto de vida: diseño','Metas a corto y largo plazo, plan de acción','7','4'),
  ('Presentación del proyecto de vida','Exposición ante grupo, retroalimentación','8','4')
) v(nombre, descr, ord, per)
WHERE m.clave_materia='NVI-SEC-ORI' AND m.is_active=TRUE
  AND NOT EXISTS (SELECT 1 FROM ades_temas WHERE materia_id=m.id AND is_active=TRUE);

-- NVI-PREP-HAD: Habilidades Digitales Avanzadas (Preparatoria)
INSERT INTO ades_temas (id, materia_id, nombre_tema, descripcion, orden, periodo_sugerido)
SELECT gen_random_uuid(), m.id, v.nombre, v.descr, v.ord::int, v.per::int
FROM ades_materias m CROSS JOIN (VALUES
  ('Ofimática avanzada y automatización','Macros en Excel/Sheets, combinación de correspondencia masiva','1','1'),
  ('Python para datos y automatización','Pandas, NumPy básico, automatización de tareas','2','1'),
  ('Análisis y visualización de datos','Power BI / Tableau Public — dashboards interactivos','3','2'),
  ('Inteligencia Artificial aplicada','Uso de APIs de IA, prompt engineering, ChatGPT ético','4','2'),
  ('Ciberseguridad práctica','Pentesting básico, OWASP, gestión de contraseñas, VPN','5','3'),
  ('Desarrollo web fullstack introductorio','HTML/CSS/JS + backend con Python/Flask','6','3'),
  ('Gestión de proyectos digitales','Scrum, Trello/Jira, control de versiones con Git','7','4'),
  ('Proyecto digital integrador avanzado','Presentación de producto digital funcional','8','4')
) v(nombre, descr, ord, per)
WHERE m.clave_materia='NVI-PREP-HAD' AND m.is_active=TRUE
  AND NOT EXISTS (SELECT 1 FROM ades_temas WHERE materia_id=m.id AND is_active=TRUE);

-- NVI-PREP-EMP: Emprendimiento e Innovación Nevadi (Preparatoria)
INSERT INTO ades_temas (id, materia_id, nombre_tema, descripcion, orden, periodo_sugerido)
SELECT gen_random_uuid(), m.id, v.nombre, v.descr, v.ord::int, v.per::int
FROM ades_materias m CROSS JOIN (VALUES
  ('Ecosistema emprendedor en México','Startups, PYMES, incubadoras, CONACYT/INADEM','1','1'),
  ('Creatividad e innovación aplicada','Design Thinking, TRIZ, Six Hats, SCAMPER','2','1'),
  ('Identificación y validación de problema','Entrevistas a usuarios, Problem-Solution Fit','3','2'),
  ('Modelo de negocio avanzado','Business Model Canvas, lean startup, MVP','4','2'),
  ('Marketing digital para emprendedores','Branding, redes sociales, SEO básico, email marketing','5','3'),
  ('Finanzas para startups','Valuación, inversión, break-even, pitch financiero','6','3'),
  ('Pitch y presentación ante inversores','Elevator pitch, deck de 10 slides, storytelling','7','4'),
  ('Demo Day Nevadi','Presentación pública de empresa/proyecto ante jurado','8','4')
) v(nombre, descr, ord, per)
WHERE m.clave_materia='NVI-PREP-EMP' AND m.is_active=TRUE
  AND NOT EXISTS (SELECT 1 FROM ades_temas WHERE materia_id=m.id AND is_active=TRUE);

-- ────────────────────────────────────────────────────────────
-- BLOQUE 5: NEVADI materias → ades_materias_plan
-- Insertar para todos los grados del nivel y todos los ciclos activos
-- La clave única (materia_id, grado_id, ciclo_escolar_id) previene duplicados
-- ────────────────────────────────────────────────────────────

INSERT INTO ades_materias_plan (id, materia_id, grado_id, ciclo_escolar_id, horas_semana, es_obligatoria)
SELECT gen_random_uuid(), m.id, gr.id, ce.id, m.horas_semana, TRUE
FROM ades_materias m
JOIN ades_niveles_educativos n  ON n.id = m.nivel_educativo_id
JOIN ades_grados gr              ON gr.nivel_educativo_id = n.id
CROSS JOIN ades_ciclos_escolares ce
WHERE m.clave_materia IN (
  'NVI-PRI-INF','NVI-PRI-ING',
  'NVI-SEC-INF','NVI-SEC-ING','NVI-SEC-ORI',
  'NVI-PREP-HAD','NVI-PREP-EMP'
)
  AND m.is_active = TRUE
  AND ce.is_active = TRUE
ON CONFLICT (materia_id, grado_id, ciclo_escolar_id) DO NOTHING;

-- ────────────────────────────────────────────────────────────
-- RESUMEN
-- ────────────────────────────────────────────────────────────
DO $$
DECLARE
  v_sec_temas   INT;
  v_cbu_temas   INT;
  v_nvi_mat     INT;
  v_nvi_temas   INT;
  v_nvi_plan    INT;
BEGIN
  SELECT COUNT(*) INTO v_sec_temas
  FROM ades_temas t JOIN ades_materias m ON m.id=t.materia_id
  WHERE m.tipo_materia='OFICIAL_SEP_SECUNDARIA' AND t.is_active=TRUE;

  SELECT COUNT(*) INTO v_cbu_temas
  FROM ades_temas t JOIN ades_materias m ON m.id=t.materia_id
  WHERE m.tipo_materia='OFICIAL_UAEMEX_PREP' AND t.is_active=TRUE;

  SELECT COUNT(*) INTO v_nvi_mat
  FROM ades_materias WHERE tipo_materia IN ('NEVADI_FORMATIVA','NEVADI_ENRIQUECIMIENTO','NEVADI_ESPECIALIZADA') AND is_active=TRUE;

  SELECT COUNT(*) INTO v_nvi_temas
  FROM ades_temas t JOIN ades_materias m ON m.id=t.materia_id
  WHERE m.tipo_materia IN ('NEVADI_FORMATIVA','NEVADI_ENRIQUECIMIENTO','NEVADI_ESPECIALIZADA') AND t.is_active=TRUE;

  SELECT COUNT(*) INTO v_nvi_plan
  FROM ades_materias_plan mp JOIN ades_materias m ON m.id=mp.materia_id
  WHERE m.tipo_materia IN ('NEVADI_FORMATIVA','NEVADI_ENRIQUECIMIENTO','NEVADI_ESPECIALIZADA') AND mp.is_active=TRUE;

  RAISE NOTICE 'SEC NEM temas: %  |  CBU PREP temas: %  |  NEVADI materias: %  |  NEVADI temas: %  |  NEVADI plan rows: %',
    v_sec_temas, v_cbu_temas, v_nvi_mat, v_nvi_temas, v_nvi_plan;
END $$;

COMMIT;
