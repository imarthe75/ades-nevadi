-- =============================================================================
-- Migración 044: Planes de estudio y temarios completos de Primaria NEM
--
--   Este script es idempotente y seguro para ejecutarse múltiples veces.
--   Reemplaza los temas de placeholder genéricos de Primaria con contenidos
--   específicos alineados a los programas sintéticos de la SEP para la NEM.
-- =============================================================================

BEGIN;

-- 1. Desactivar temas previos tipo placeholder para Primaria
UPDATE public.ades_temas
   SET is_active = FALSE
 WHERE materia_id IN (
   SELECT m.id 
     FROM public.ades_materias m
     JOIN public.ades_niveles_educativos n ON m.nivel_educativo_id = n.id
    WHERE n.nombre_nivel = 'PRIMARIA'
 );

-- 2. Registrar nuevos temas específicos para cada grado de Primaria
DO $$
DECLARE
  r_grado RECORD;
  v_materia_id UUID;
BEGIN
  -- Iteramos por todos los grados escolares de Primaria en todos los planteles activos
  FOR r_grado IN (
    SELECT id, numero_grado 
      FROM public.ades_grados 
     WHERE nivel_educativo_id = (SELECT id FROM public.ades_niveles_educativos WHERE nombre_nivel = 'PRIMARIA')
       AND is_active = TRUE
  ) LOOP
    
    -- =========================================================================
    -- LENGUAJES (PRI-LEN)
    -- =========================================================================
    SELECT id INTO v_materia_id FROM public.ades_materias WHERE clave_materia = 'PRI-LEN' AND is_active = TRUE;
    IF v_materia_id IS NOT NULL THEN
      IF r_grado.numero_grado = 1 THEN
        INSERT INTO public.ades_temas (materia_id, grado_id, nombre_tema, descripcion, orden, periodo_sugerido) VALUES
          (v_materia_id, r_grado.id, 'Escritura de nombres y palabras familiares', 'Reconocimiento y trazo del propio nombre y palabras cotidianas en lengua materna', 1, 1),
          (v_materia_id, r_grado.id, 'Descripción de objetos, personas y seres vivos', 'Uso de adjetivos, lenguaje oral y escrito para describir el entorno cercano', 2, 1),
          (v_materia_id, r_grado.id, 'Lectura y escritura de textos sencillos', 'Identificación de grafías y fonemas en palabras y oraciones cortas', 3, 2),
          (v_materia_id, r_grado.id, 'Expresión de emociones a través del arte', 'Uso del color, formas y sonidos para representar estados de ánimo y vivencias', 4, 2),
          (v_materia_id, r_grado.id, 'Apreciación de la diversidad lingüística', 'Reconocimiento de palabras de origen indígena o extranjero de uso común', 5, 3),
          (v_materia_id, r_grado.id, 'Creación de historias y cuentos colectivos', 'Desarrollo de la imaginación mediante narración estructurada en grupo', 6, 3);
      ELSIF r_grado.numero_grado = 2 THEN
        INSERT INTO public.ades_temas (materia_id, grado_id, nombre_tema, descripcion, orden, periodo_sugerido) VALUES
          (v_materia_id, r_grado.id, 'Descripción detallada de lugares y procesos', 'Redacción de textos descriptivos sobre el entorno natural y social', 1, 1),
          (v_materia_id, r_grado.id, 'Uso de convenciones de la escritura', 'Ortografía, puntuación y uso correcto de mayúsculas en enunciados sencillos', 2, 1),
          (v_materia_id, r_grado.id, 'Lectura crítica de carteles y anuncios', 'Comprensión de mensajes visuales e información en espacios escolares', 3, 2),
          (v_materia_id, r_grado.id, 'Representación teatral y títeres', 'Uso de gestos, voz y corporalidad para narrar historias cortas de la comunidad', 4, 2),
          (v_materia_id, r_grado.id, 'Registro de información con esquemas', 'Organización de datos mediante tablas, diagramas y dibujos sencillos', 5, 3),
          (v_materia_id, r_grado.id, 'Poesía y rimas infantiles', 'Sensibilidad estética mediante la lectura y creación de juegos de palabras', 6, 3);
      ELSIF r_grado.numero_grado = 3 THEN
        INSERT INTO public.ades_temas (materia_id, grado_id, nombre_tema, descripcion, orden, periodo_sugerido) VALUES
          (v_materia_id, r_grado.id, 'Narración de sucesos del pasado y presente', 'Estructura temporal y uso de verbos en narraciones escritas sobre la comunidad', 1, 1),
          (v_materia_id, r_grado.id, 'Comprensión y producción de instructivos', 'Redacción de pasos detallados para juegos, experimentos y recetas', 2, 1),
          (v_materia_id, r_grado.id, 'Exposición oral de temas comunitarios', 'Estructura de la exposición, tono de voz y diseño de apoyos visuales', 3, 2),
          (v_materia_id, r_grado.id, 'Búsqueda y organización de información', 'Técnicas de síntesis y resúmenes a partir de diversas fuentes impresas', 4, 2),
          (v_materia_id, r_grado.id, 'Comprensión de textos discontinuos', 'Lectura e interpretación de mapas, diagramas y tablas de datos sencillas', 5, 3),
          (v_materia_id, r_grado.id, 'Apreciación de lenguas indígenas mexicanas', 'Identificación de lenguas y variantes presentes en la comunidad nacional', 6, 3);
      ELSIF r_grado.numero_grado = 4 THEN
        INSERT INTO public.ades_temas (materia_id, grado_id, nombre_tema, descripcion, orden, periodo_sugerido) VALUES
          (v_materia_id, r_grado.id, 'Análisis de textos periodísticos y noticias', 'Identificación de hechos, opiniones y estructura de la nota informativa', 1, 1),
          (v_materia_id, r_grado.id, 'Elaboración de textos explicativos', 'Uso de nexos causales para explicar fenómenos naturales y procesos de interés', 2, 1),
          (v_materia_id, r_grado.id, 'Entrevistas a personas de la comunidad', 'Formulación de preguntas abiertas y reporte en formato de diálogo formal', 3, 2),
          (v_materia_id, r_grado.id, 'Lectura y creación dramática', 'Lectura de guiones teatrales y adaptación de leyendas populares para representación', 4, 2),
          (v_materia_id, r_grado.id, 'Análisis crítico de la publicidad escolar y local', 'Reconocimiento de la persuasión visual y diseño de campañas informativas escolares', 5, 3),
          (v_materia_id, r_grado.id, 'Juegos de palabras y caligramas artísticos', 'Exploración artística del espacio gráfico con la palabra escrita', 6, 3);
      ELSIF r_grado.numero_grado = 5 THEN
        INSERT INTO public.ades_temas (materia_id, grado_id, nombre_tema, descripcion, orden, periodo_sugerido) VALUES
          (v_materia_id, r_grado.id, 'Narración de sucesos autobiográficos y biografías', 'Uso de la primera y tercera persona en relatos cronológicos estructurados', 1, 1),
          (v_materia_id, r_grado.id, 'Comprensión y producción de textos argumentativos', 'Desarrollo de argumentos sólidos y posturas críticas en ensayos breves', 2, 1),
          (v_materia_id, r_grado.id, 'Elaboración de trípticos informativos', 'Diseño de folletos para la prevención de problemáticas escolares y comunitarias', 3, 2),
          (v_materia_id, r_grado.id, 'Debate escolar y argumentación oral formal', 'Roles de participación, moderación y formulación de réplicas en discusiones', 4, 2),
          (v_materia_id, r_grado.id, 'Lectura y análisis de poemas y canciones', 'Identificación de metáforas, ritmo, métrica y figuras retóricas en la lírica', 5, 3),
          (v_materia_id, r_grado.id, 'Uso de la lengua extranjera en contextos reales', 'Diálogos de presentación, intercambio comercial simulado y descripción en inglés', 6, 3);
      ELSIF r_grado.numero_grado = 6 THEN
        INSERT INTO public.ades_temas (materia_id, grado_id, nombre_tema, descripcion, orden, periodo_sugerido) VALUES
          (v_materia_id, r_grado.id, 'Producción de textos explicativos y monográficos', 'Búsqueda sistemática de información, organización por subtemas y citación', 1, 1),
          (v_materia_id, r_grado.id, 'Análisis y creación de textos de opinión', 'Lectura de editoriales periodísticas y expresión formal de puntos de vista', 2, 1),
          (v_materia_id, r_grado.id, 'Adaptación de textos literarios a guion teatral', 'Conversión de narrativa en diálogo dramático para puesta en escena escolar', 3, 2),
          (v_materia_id, r_grado.id, 'Elaboración de informes de investigación escolar', 'Estructura formal con hipótesis, desarrollo de datos, tablas y conclusiones', 4, 2),
          (v_materia_id, r_grado.id, 'Lectura crítica de medios masivos de comunicación', 'Deconstrucción de estereotipos y sesgos informativos en la prensa escrita', 5, 3),
          (v_materia_id, r_grado.id, 'Proyecto integrador de lenguajes y publicación', 'Compilación escolar de textos creativos y periodísticos en gaceta o blog escolar', 6, 3);
      END IF;
    END IF;

    -- =========================================================================
    -- SABERES Y PENSAMIENTO CIENTÍFICO (PRI-SPC)
    -- =========================================================================
    SELECT id INTO v_materia_id FROM public.ades_materias WHERE clave_materia = 'PRI-SPC' AND is_active = TRUE;
    IF v_materia_id IS NOT NULL THEN
      IF r_grado.numero_grado = 1 THEN
        INSERT INTO public.ades_temas (materia_id, grado_id, nombre_tema, descripcion, orden, periodo_sugerido) VALUES
          (v_materia_id, r_grado.id, 'Estudio de los números hasta el 100', 'Conteo, ordenamiento, lectura y representation física de agrupaciones', 1, 1),
          (v_materia_id, r_grado.id, 'El cuerpo humano y sus cuidados básicos', 'Estructura externa del cuerpo, higiene y hábitos de alimentación saludable', 2, 1),
          (v_materia_id, r_grado.id, 'Nociones de suma y resta', 'Resolución de problemas de juntar, agregar, quitar y comparar objetos cotidianos', 3, 2),
          (v_materia_id, r_grado.id, 'Características del entorno natural', 'Observación de plantas, animales, el clima y los estados físicos cotidianos', 4, 2),
          (v_materia_id, r_grado.id, 'Cuerpos y figuras geométricas básicas', 'Identificación de círculos, cuadrados y triángulos en objetos comunes', 5, 3),
          (v_materia_id, r_grado.id, 'Medición del tiempo y magnitudes físicas', 'Uso de días de la semana, meses y medición no convencional de longitud', 6, 3);
      ELSIF r_grado.numero_grado = 2 THEN
        INSERT INTO public.ades_temas (materia_id, grado_id, nombre_tema, descripcion, orden, periodo_sugerido) VALUES
          (v_materia_id, r_grado.id, 'Números de tres cifras y valor posicional', 'Lectura y escritura de números hasta 1000, centenas, decenas y unidades', 1, 1),
          (v_materia_id, r_grado.id, 'Suma y resta con algoritmos convencionales', 'Cálculo mental y resolución de problemas aditivos de dos dígitos', 2, 1),
          (v_materia_id, r_grado.id, 'Funcionamiento de los órganos de los sentidos', 'Mecanismos de percepción, estimulación y prevención de riesgos', 3, 2),
          (v_materia_id, r_grado.id, 'Clasificación de plantas y animales locales', 'Tipos de alimentación, respiración y hábitats del entorno natural', 4, 2),
          (v_materia_id, r_grado.id, 'Figuras de tres o más lados y simetría', 'Identificación de aristas, vértices e introducción a ejes de simetría', 5, 3),
          (v_materia_id, r_grado.id, 'Medición de longitud, peso y capacidad', 'Uso del metro, balanzas sencillas y recipientes con medidas estándar', 6, 3);
      ELSIF r_grado.numero_grado = 3 THEN
        INSERT INTO public.ades_temas (materia_id, grado_id, nombre_tema, descripcion, orden, periodo_sugerido) VALUES
          (v_materia_id, r_grado.id, 'Estudio de los números hasta cuatro cifras', 'Valor posicional e introducción a números fraccionarios (medios, cuartos, octavos)', 1, 1),
          (v_materia_id, r_grado.id, 'Estructura y funcionamiento del sistema locomotor y respiratorio', 'Cuidado de huesos, músculos, pulmones y prevención de accidentes', 2, 1),
          (v_materia_id, r_grado.id, 'Multiplicación y su relación con la suma repetida', 'Tablas de multiplicar, problemas de reparto y proporciones simples', 3, 2),
          (v_materia_id, r_grado.id, 'Estados físicos de la materia y la temperatura', 'Sólidos, líquidos, gases y uso seguro del termómetro clínico', 4, 2),
          (v_materia_id, r_grado.id, 'El sistema solar y los movimientos terrestres', 'Rotación, traslación, fases lunares y relación con las estaciones del año', 5, 3),
          (v_materia_id, r_grado.id, 'Organización y representación de datos', 'Elaboración de tablas de frecuencia y gráficas sencillas de barras', 6, 3);
      ELSIF r_grado.numero_grado = 4 THEN
        INSERT INTO public.ades_temas (materia_id, grado_id, nombre_tema, descripcion, orden, periodo_sugerido) VALUES
          (v_materia_id, r_grado.id, 'Números decimales y fracciones equivalentes', 'Representación en la recta numérica y suma/resta con decimales', 1, 1),
          (v_materia_id, r_grado.id, 'Funcionamiento del sistema circulatorio e inmunitario', 'La sangre, el corazón, prevención de infecciones y vacunas esenciales', 2, 1),
          (v_materia_id, r_grado.id, 'División y algoritmos de reparto complejo', 'Operaciones inversas a la multiplicación y cálculo mental avanzado', 3, 2),
          (v_materia_id, r_grado.id, 'Ecosistemas, cadenas alimentarias e impacto humano', 'Relación productor-consumidor y preservación del equilibrio ecológico', 4, 2),
          (v_materia_id, r_grado.id, 'Cuerpos geométricos planos, prismas y pirámides', 'Identificación de caras, aristas, vértices y desarrollo plano de prismas', 5, 3),
          (v_materia_id, r_grado.id, 'Medición de superficies y volumen básico', 'Concepto de área mediante cuadrículas e introducción al centímetro cúbico', 6, 3);
      ELSIF r_grado.numero_grado = 5 THEN
        INSERT INTO public.ades_temas (materia_id, grado_id, nombre_tema, descripcion, orden, periodo_sugerido) VALUES
          (v_materia_id, r_grado.id, 'Fracciones mixtas, porcentajes y regla de tres', 'Conversión entre fracciones y decimales en problemas comerciales', 1, 1),
          (v_materia_id, r_grado.id, 'Sistema digestivo y nutrición equilibrada', 'Proceso de digestión, plato del bien comer e índice de masa corporal', 2, 1),
          (v_materia_id, r_grado.id, 'Propiedades de la materia: densidad y solubilidad', 'Experimentos sencillos con mezclas homogéneas y heterogéneas', 3, 2),
          (v_materia_id, r_grado.id, 'Biodiversidad global e importancia de su conservación', 'Especies endémicas de México y factores que amenazan los hábitats', 4, 2),
          (v_materia_id, r_grado.id, 'Cálculo de perímetros y áreas de polígonos regulares', 'Fórmulas para triángulos, cuadriláteros, pentágonos y el círculo', 5, 3),
          (v_materia_id, r_grado.id, 'Nociones de probabilidad y estadística descriptiva', 'Cálculo de la media, moda, mediana y diagramas circulares', 6, 3);
      ELSIF r_grado.numero_grado = 6 THEN
        INSERT INTO public.ades_temas (materia_id, grado_id, nombre_tema, descripcion, orden, periodo_sugerido) VALUES
          (v_materia_id, r_grado.id, 'Operaciones combinadas con números reales y potencia', 'Jerarquía de operaciones y potenciación al cuadrado y cubo', 1, 1),
          (v_materia_id, r_grado.id, 'Sistema reproductor y sexualidad responsable', 'Cambios en la pubertad, anatomía, fecundación y prevención de ITS', 2, 1),
          (v_materia_id, r_grado.id, 'Energía: fuentes renovables y sustentabilidad', 'Energía solar, eólica, hidráulica y uso eficiente de la electricidad', 3, 2),
          (v_materia_id, r_grado.id, 'Fenómenos naturales extremos y cambio climático', 'Efecto invernadero, huracanes, sismos y protocolos de protección civil', 4, 2),
          (v_materia_id, r_grado.id, 'Coordenadas cartesianas y volumen de cuerpos', 'Uso del plano cartesiano, cuadrantes y cálculo de volumen de prismas', 5, 3),
          (v_materia_id, r_grado.id, 'Proyecto integrador de pensamiento matemático y ciencia', 'Diseño de un prototipo tecnológico sustentable y análisis estadístico', 6, 3);
      END IF;
    END IF;

    -- =========================================================================
    -- ÉTICA, NATURALEZA Y SOCIEDADES (PRI-ENS)
    -- =========================================================================
    SELECT id INTO v_materia_id FROM public.ades_materias WHERE clave_materia = 'PRI-ENS' AND is_active = TRUE;
    IF v_materia_id IS NOT NULL THEN
      IF r_grado.numero_grado = 1 THEN
        INSERT INTO public.ades_temas (materia_id, grado_id, nombre_tema, descripcion, orden, periodo_sugerido) VALUES
          (v_materia_id, r_grado.id, 'Mi historia personal, familiar y escolar', 'Construcción del sentido de pertenencia y árbol genealógico básico', 1, 1),
          (v_materia_id, r_grado.id, 'El cuidado de la naturaleza como responsabilidad común', 'Acciones escolares para el ahorro de agua y manejo de basura', 2, 1),
          (v_materia_id, r_grado.id, 'Normas de convivencia en el salón de clases', 'Elaboración colectiva del reglamento de aula y consecuencias del diálogo', 3, 2),
          (v_materia_id, r_grado.id, 'Celebraciones y costumbres de mi comunidad', 'Reconocimiento y respeto a las festividades tradicionales locales', 4, 2),
          (v_materia_id, r_grado.id, 'Los derechos de las niñas y los niños', 'Identificación de necesidades básicas y protección contra el maltrato', 5, 3),
          (v_materia_id, r_grado.id, 'Símbolos patrios y efemérides nacionales', 'Respeto al himno, escudo y bandera, y conmemoraciones cívicas', 6, 3);
      ELSIF r_grado.numero_grado = 2 THEN
        INSERT INTO public.ades_temas (materia_id, grado_id, nombre_tema, descripcion, orden, periodo_sugerido) VALUES
          (v_materia_id, r_grado.id, 'El relieve, agua y vegetación de mi localidad', 'Identificación de ríos, cerros y plantas nativas del municipio', 1, 1),
          (v_materia_id, r_grado.id, 'Cambios en la comunidad a través del tiempo', 'Comparación del entorno actual con el pasado a través de fotografías', 2, 1),
          (v_materia_id, r_grado.id, 'Resolución de conflictos mediante el diálogo', 'Estrategias de mediación escolar y empatía ante desacuerdos cotidianos', 3, 2),
          (v_materia_id, r_grado.id, 'Diversidad cultural y lenguas de México', 'Valoración de la riqueza multicultural del país y respeto al otro', 4, 2),
          (v_materia_id, r_grado.id, 'Derecho a una vida digna y libre de violencia', 'Instituciones de protección infantil y redes de apoyo familiar', 5, 3),
          (v_materia_id, r_grado.id, 'Funciones del gobierno escolar y comunitario', 'Roles de los jefes de grupo, directores y autoridades municipales', 6, 3);
      ELSIF r_grado.numero_grado = 3 THEN
        INSERT INTO public.ades_temas (materia_id, grado_id, nombre_tema, descripcion, orden, periodo_sugerido) VALUES
          (v_materia_id, r_grado.id, 'La representación cartográfica del estado y municipio', 'Lectura de mapas, uso de simbología y rosa de los vientos', 1, 1),
          (v_materia_id, r_grado.id, 'El poblamiento de América y culturas prehispánicas del estado', 'Estudio de sitios arqueológicos cercanos y formas de vida originarias', 2, 1),
          (v_materia_id, r_grado.id, 'Impacto de la actividad productiva en los recursos naturales', 'Contaminación de ríos de la entidad y alternativas sustentables', 3, 2),
          (v_materia_id, r_grado.id, 'La época colonial en nuestro estado', 'Fundación de ciudades históricas, minería, agricultura y mestizaje', 4, 2),
          (v_materia_id, r_grado.id, 'Derechos fundamentales e igualdad de género', 'La Constitución de 1917 y no discriminación en el hogar y escuela', 5, 3),
          (v_materia_id, r_grado.id, 'La democracia participativa y el bien común', 'Consulta infantil y toma de decisiones democráticas en el aula', 6, 3);
      ELSIF r_grado.numero_grado = 4 THEN
        INSERT INTO public.ades_temas (materia_id, grado_id, nombre_tema, descripcion, orden, periodo_sugerido) VALUES
          (v_materia_id, r_grado.id, 'División política e hidrografía de México', 'Ubicación de los 32 estados, capitales y principales cuencas del país', 1, 1),
          (v_materia_id, r_grado.id, 'La Independencia de México: causas y desarrollo', 'Antecedentes, conspiración de Querétaro y etapas de la lucha armada', 2, 1),
          (v_materia_id, r_grado.id, 'Biodiversidad de la República Mexicana', 'Áreas Naturales Protegidas y especies animales en peligro de extinción', 3, 2),
          (v_materia_id, r_grado.id, 'La vida cotidiana durante el Virreinato de la Nueva España', 'Castas, comercio, influencia eclesiástica y gastronomía novohispana', 4, 2),
          (v_materia_id, r_grado.id, 'Declaración de los Derechos Humanos y leyes infantiles', 'Análisis de la Convención sobre los Derechos del Niño en el país', 5, 3),
          (v_materia_id, r_grado.id, 'El poder legislativo, ejecutivo y judicial en el país', 'Estructura de la república representativa, democrática y federal', 6, 3);
      ELSIF r_grado.numero_grado = 5 THEN
        INSERT INTO public.ades_temas (materia_id, grado_id, nombre_tema, descripcion, orden, periodo_sugerido) VALUES
          (v_materia_id, r_grado.id, 'Geografía mundial: continentes, climas y regiones', 'Ubicación geográfica, zonas térmicas e importancia del agua dulce', 1, 1),
          (v_materia_id, r_grado.id, 'La Reforma y el Imperio en México', 'Leyes de Reforma, Benito Juárez, Maximiliano de Habsburgo y soberanía', 2, 1),
          (v_materia_id, r_grado.id, 'El Porfiriato y los albores de la Revolución Mexicana', 'Modernización ferroviaria, huelgas de Cananea y Río Blanco, descontento', 3, 2),
          (v_materia_id, r_grado.id, 'Problemas ambientales mundiales y sustentabilidad', 'Deforestación, huella de carbono, acidificación de océanos', 4, 2),
          (v_materia_id, r_grado.id, 'Derecho a la educación y no discriminación étnica', 'Historia del derecho a la educación laica e inclusiva en México', 5, 3),
          (v_materia_id, r_grado.id, 'Instituciones y tratados internacionales de paz', 'La ONU, UNICEF y derechos de los pueblos indígenas a nivel global', 6, 3);
      ELSIF r_grado.numero_grado = 6 THEN
        INSERT INTO public.ades_temas (materia_id, grado_id, nombre_tema, descripcion, orden, periodo_sugerido) VALUES
          (v_materia_id, r_grado.id, 'La globalización, migración y sus implicaciones sociales', 'Causas de la migración nacional e internacional y derechos humanos', 1, 1),
          (v_materia_id, r_grado.id, 'La Revolución Mexicana y la Constitución de 1917', 'Maderismo, Zapatismo, Carrancismo y los artículos 3º, 27º y 123º', 2, 1),
          (v_materia_id, r_grado.id, 'México contemporáneo y transiciones democráticas', 'El desarrollo estabilizador, movimiento estudiantil de 1968, elecciones del 2000', 3, 2),
          (v_materia_id, r_grado.id, 'Cultura de paz y erradicación del acoso y violencia social', 'Estrategias de convivencia no violenta y defensa activa de DDHH', 4, 2),
          (v_materia_id, r_grado.id, 'Acuerdos internacionales de sustentabilidad y cambio global', 'El Acuerdo de París y la Agenda 2030 desde la participación ciudadana', 5, 3),
          (v_materia_id, r_grado.id, 'Proyecto integrador de ética cívica y participación', 'Diseño de iniciativa comunitaria escolar para mejorar el entorno cívico', 6, 3);
      END IF;
    END IF;

    -- =========================================================================
    -- DE LO HUMANO Y LO COMUNITARIO (PRI-DHC)
    -- =========================================================================
    SELECT id INTO v_materia_id FROM public.ades_materias WHERE clave_materia = 'PRI-DHC' AND is_active = TRUE;
    IF v_materia_id IS NOT NULL THEN
      IF r_grado.numero_grado = 1 THEN
        INSERT INTO public.ades_temas (materia_id, grado_id, nombre_tema, descripcion, orden, periodo_sugerido) VALUES
          (v_materia_id, r_grado.id, 'Autoconocimiento e identidad personal', 'Reconocimiento de rasgos físicos, gustos, habilidades y pertenencia', 1, 1),
          (v_materia_id, r_grado.id, 'Habilidades y capacidades motrices básicas', 'Ejercicios de coordinación, equilibrio, marcha, salto y lateralidad', 2, 1),
          (v_materia_id, r_grado.id, 'Hábitos saludables de alimentación e hidratación', 'Consumo de agua potable, frutas y verduras frente a productos procesados', 3, 2),
          (v_materia_id, r_grado.id, 'Expresión y regulación de las emociones', 'Identificación de la alegría, tristeza, miedo y enojo, y cómo expresarlas', 4, 2),
          (v_materia_id, r_grado.id, 'Convivencia armónica en la escuela y familia', 'Importancia del respeto, ayuda mutua y juego limpio en el patio escolar', 5, 3),
          (v_materia_id, r_grado.id, 'Prevención de riesgos en el entorno inmediato', 'Señales de seguridad escolar, salidas de emergencia y no hablar con extraños', 6, 3);
      ELSIF r_grado.numero_grado = 2 THEN
        INSERT INTO public.ades_temas (materia_id, grado_id, nombre_tema, descripcion, orden, periodo_sugerido) VALUES
          (v_materia_id, r_grado.id, 'Mis gustos, fortalezas y metas escolares sencillas', 'Fomento del autoconcepto positivo y planteamiento de logros personales', 1, 1),
          (v_materia_id, r_grado.id, 'Coordinación dinámica general y juegos cooperativos', 'Ejercicios lúdicos en equipo con reglas sencillas y fomento del compañerismo', 2, 1),
          (v_materia_id, r_grado.id, 'Importancia de la actividad física diaria', 'Ejercicios de estiramiento, resistencia básica y combate al sedentarismo', 3, 2),
          (v_materia_id, r_grado.id, 'Empatía y escucha activa con los compañeros', 'Comprensión de los puntos de vista de otros y apoyo escolar', 4, 2),
          (v_materia_id, r_grado.id, 'Higiene personal y del entorno escolar', 'Taller de lavado de manos, cepillado de dientes y orden en la escuela', 5, 3),
          (v_materia_id, r_grado.id, 'Plan familiar de protección civil ante sismos', 'Simulacros de repliegue, zonas de seguridad y mochila de emergencia', 6, 3);
      ELSIF r_grado.numero_grado = 3 THEN
        INSERT INTO public.ades_temas (materia_id, grado_id, nombre_tema, descripcion, orden, periodo_sugerido) VALUES
          (v_materia_id, r_grado.id, 'El sentido de comunidad y proyectos colaborativos', 'Diagnóstico de necesidades lúdicas del plantel y propuesta de juegos', 1, 1),
          (v_materia_id, r_grado.id, 'Habilidades motrices complejas y juegos predeportivos', 'Introducción al manejo de balones, implementos y pases coordinados', 2, 1),
          (v_materia_id, r_grado.id, 'Alimentación balanceada y plato del bien comer', 'Clasificación de alimentos en carbohidratos, proteínas y vitaminas', 3, 2),
          (v_materia_id, r_grado.id, 'Gestión de la frustración y la tolerancia', 'Técnicas de respiración y replanteamiento de metas ante dificultades', 4, 2),
          (v_materia_id, r_grado.id, 'Prevención de adicciones y sustancias tóxicas', 'Uso seguro de medicamentos, no ingerir productos químicos peligrosos', 5, 3),
          (v_materia_id, r_grado.id, 'Cuidado de la salud colectiva e higiene respiratoria', 'Estornudo de etiqueta, ventilación de aulas y vacunación estacional', 6, 3);
      ELSIF r_grado.numero_grado = 4 THEN
        INSERT INTO public.ades_temas (materia_id, grado_id, nombre_tema, descripcion, orden, periodo_sugerido) VALUES
          (v_materia_id, r_grado.id, 'Construcción del proyecto de vida a corto plazo', 'Organización del tiempo de estudio, descanso, juego y responsabilidades', 1, 1),
          (v_materia_id, r_grado.id, 'Expresión corporal, danza y ritmos motores', 'Uso del cuerpo como lenguaje artístico e interpretación rítmica', 2, 1),
          (v_materia_id, r_grado.id, 'Vida saludable y prevención de la obesidad infantil', 'El jarra del buen beber, etiquetado frontal de alimentos y cálculo de agua requerida', 3, 2),
          (v_materia_id, r_grado.id, 'Comunicación asertiva y prevención del bullying', 'Identificación de agresiones verbales, físicas o cibernéticas y denuncias', 4, 2),
          (v_materia_id, r_grado.id, 'Primeros auxilios básicos en la escuela', 'Atención inmediata ante raspaduras, torceduras y hemorragia nasal', 5, 3),
          (v_materia_id, r_grado.id, 'La toma de decisiones en el juego y la vida cotidiana', 'Valoración de consecuencias individuales y colectivas antes de actuar', 6, 3);
      ELSIF r_grado.numero_grado = 5 THEN
        INSERT INTO public.ades_temas (materia_id, grado_id, nombre_tema, descripcion, orden, periodo_sugerido) VALUES
          (v_materia_id, r_grado.id, 'Autoconcepto, autoestima y cambios en la pubertad', 'Aceptación de la imagen corporal y madurez afectivo-emocional', 1, 1),
          (v_materia_id, r_grado.id, 'Iniciación deportiva escolar y reglas de atletismo', 'Carreras de velocidad, relevos, saltos y fomento del juego limpio', 2, 1),
          (v_materia_id, r_grado.id, 'Prevención de adicciones: tabaco, alcohol y vapeadores', 'Efectos en la salud pulmonar, circulatoria y neurológica en menores', 3, 2),
          (v_materia_id, r_grado.id, 'Manejo del estrés y la ansiedad académica', 'Técnicas de relajación muscular progresiva y planificación eficiente', 4, 2),
          (v_materia_id, r_grado.id, 'Salud reproductiva y autocuidado del cuerpo', 'Ciclo menstrual, higiene íntima, límites del contacto físico', 5, 3),
          (v_materia_id, r_grado.id, 'Proyectos de impacto comunitario sustentable', 'Campaña de reforestación escolar o creación de huerto orgánico', 6, 3);
      ELSIF r_grado.numero_grado = 6 THEN
        INSERT INTO public.ades_temas (materia_id, grado_id, nombre_tema, descripcion, orden, periodo_sugerido) VALUES
          (v_materia_id, r_grado.id, 'Mi proyecto de vida: transición a la secundaria', 'Planteamiento de metas académicas, temores, expectativas y estrategias', 1, 1),
          (v_materia_id, r_grado.id, 'Deportes colectivos y tácticas básicas de juego', 'Fútbol, básquetbol, voleibol: reglas formales, arbitraje escolar', 2, 1),
          (v_materia_id, r_grado.id, 'Análisis de factores sociales en el consumo de comida chatarra', 'Deconstrucción de campañas publicitarias e influencia social en la dieta', 3, 2),
          (v_materia_id, r_grado.id, 'Educación socioemocional: resiliencia ante pérdidas', 'Taller de duelo, empatía grupal y fortalecimiento afectivo', 4, 2),
          (v_materia_id, r_grado.id, 'Prevención de ciberriesgos: sexting, grooming e identidad digital', 'Uso seguro de redes, privacidad y denuncias ante la policía cibernética', 5, 3),
          (v_materia_id, r_grado.id, 'Proyecto integrador humano y comunitario escolar', 'Organización de feria escolar de vida saludable y salud mental', 6, 3);
      END IF;
    END IF;

    -- =========================================================================
    -- INGLÉS (PRI-ING / NVI-PRI-ING)
    -- =========================================================================
    SELECT id INTO v_materia_id FROM public.ades_materias WHERE clave_materia = 'PRI-ING' AND is_active = TRUE;
    IF v_materia_id IS NOT NULL THEN
      IF r_grado.numero_grado = 1 THEN
        INSERT INTO public.ades_temas (materia_id, grado_id, nombre_tema, descripcion, orden, periodo_sugerido) VALUES
          (v_materia_id, r_grado.id, 'Greetings and introductions', 'Say hello, goodbye, ask for names and simple greetings', 1, 1),
          (v_materia_id, r_grado.id, 'Numbers, colors and shapes', 'Count 1 to 20, basic color vocabulary, and geometric shapes', 2, 1),
          (v_materia_id, r_grado.id, 'My family and home', 'Basic relationships: mother, father, brother, sister, house vocabulary', 3, 2),
          (v_materia_id, r_grado.id, 'School objects', 'Pencil, book, eraser, notebook, classroom vocabulary', 4, 3);
      ELSIF r_grado.numero_grado = 2 THEN
        INSERT INTO public.ades_temas (materia_id, grado_id, nombre_tema, descripcion, orden, periodo_sugerido) VALUES
          (v_materia_id, r_grado.id, 'Daily routines', 'Simple actions: wake up, wash hands, eat breakfast, play, sleep', 1, 1),
          (v_materia_id, r_grado.id, 'Body parts and clothes', 'Vocabulary for head, arms, legs, shirt, pants, shoes', 2, 1),
          (v_materia_id, r_grado.id, 'Animals and habitats', 'Domestic and wild animals, simple descriptors: big, small, fast', 3, 2),
          (v_materia_id, r_grado.id, 'Food and preferences', 'Fruits, vegetables, expressing likes/dislikes: I like, I don''t like', 4, 3);
      ELSIF r_grado.numero_grado = 3 THEN
        INSERT INTO public.ades_temas (materia_id, grado_id, nombre_tema, descripcion, orden, periodo_sugerido) VALUES
          (v_materia_id, r_grado.id, 'Telling the time and schedule', 'Ask for the time, read simple hours, and express school days', 1, 1),
          (v_materia_id, r_grado.id, 'Community helpers and professions', 'Doctor, teacher, firefighter, police officer, workspace vocabulary', 2, 1),
          (v_materia_id, r_grado.id, 'Weather and seasons', 'Sunny, rainy, cold, hot, spring, summer, fall, winter', 3, 2),
          (v_materia_id, r_grado.id, 'Simple descriptive sentences', 'Use of "this is", "these are", adjectives and colors together', 4, 3);
      ELSIF r_grado.numero_grado = 4 THEN
        INSERT INTO public.ades_temas (materia_id, grado_id, nombre_tema, descripcion, orden, periodo_sugerido) VALUES
          (v_materia_id, r_grado.id, 'Comparing objects and animals', 'Basic comparative structures: faster, taller, bigger than', 1, 1),
          (v_materia_id, r_grado.id, 'Past events: My yesterday', 'Introduction to simple past verbs: played, studied, went, ate', 2, 1),
          (v_materia_id, r_grado.id, 'Giving directions', 'Go straight, turn left, turn right, map vocabulary and signs', 3, 2),
          (v_materia_id, r_grado.id, 'My future plans', 'Use of "going to" for weekend or vacation activities', 4, 3);
      ELSIF r_grado.numero_grado = 5 THEN
        INSERT INTO public.ades_temas (materia_id, grado_id, nombre_tema, descripcion, orden, periodo_sugerido) VALUES
          (v_materia_id, r_grado.id, 'Healthy habits and recipes', 'Cooking verbs, ingredients list, and expressing quantities', 1, 1),
          (v_materia_id, r_grado.id, 'Experiences and travels', 'Use of present perfect basic forms: I have visited, I have been', 2, 1),
          (v_materia_id, r_grado.id, 'Environmental actions', 'Recycling vocabulary, instructions: save water, plant a tree', 3, 2),
          (v_materia_id, r_grado.id, 'Reading short stories', 'Identifying main characters, setting, and summarizing the plot', 4, 3);
      ELSIF r_grado.numero_grado = 6 THEN
        INSERT INTO public.ades_temas (materia_id, grado_id, nombre_tema, descripcion, orden, periodo_sugerido) VALUES
          (v_materia_id, r_grado.id, 'Biographies and famous people', 'Chronological writing in simple past, descriptive adjectives', 1, 1),
          (v_materia_id, r_grado.id, 'Technology and inventions', 'Vocabulary for computers, internet, expressing hypothetical scenarios', 2, 1),
          (v_materia_id, r_grado.id, 'My future school: secondary expectations', 'Expressing hopes, plans, and transition questions', 3, 2),
          (v_materia_id, r_grado.id, 'Final English project and presentation', 'Create, write and present a community poster in English', 4, 3);
      END IF;
    END IF;

    -- Taller de Inglés Intensivo (NVI-PRI-ING) - Mismos temas para complementar
    SELECT id INTO v_materia_id FROM public.ades_materias WHERE clave_materia = 'NVI-PRI-ING' AND is_active = TRUE;
    IF v_materia_id IS NOT NULL THEN
      IF r_grado.numero_grado = 1 THEN
        INSERT INTO public.ades_temas (materia_id, grado_id, nombre_tema, descripcion, orden, periodo_sugerido) VALUES
          (v_materia_id, r_grado.id, 'Intensive oral practice 1', 'Interactive speaking games, songs and phonics basics', 1, 1),
          (v_materia_id, r_grado.id, 'Interactive storytelling 1', 'Role playing simple stories in english', 2, 2);
      ELSIF r_grado.numero_grado = 2 THEN
        INSERT INTO public.ades_temas (materia_id, grado_id, nombre_tema, descripcion, orden, periodo_sugerido) VALUES
          (v_materia_id, r_grado.id, 'Intensive oral practice 2', 'Speaking activities with daily routine actions', 1, 1),
          (v_materia_id, r_grado.id, 'Interactive storytelling 2', 'Simple plays and puppets in english', 2, 2);
      ELSIF r_grado.numero_grado = 3 THEN
        INSERT INTO public.ades_temas (materia_id, grado_id, nombre_tema, descripcion, orden, periodo_sugerido) VALUES
          (v_materia_id, r_grado.id, 'Basic conversations in community', 'Roleplaying shopping and helper actions', 1, 1),
          (v_materia_id, r_grado.id, 'Reading comprehension games', 'Word search, simple matching and puzzles in english', 2, 2);
      ELSIF r_grado.numero_grado = 4 THEN
        INSERT INTO public.ades_temas (materia_id, grado_id, nombre_tema, descripcion, orden, periodo_sugerido) VALUES
          (v_materia_id, r_grado.id, 'Speaking about the past', 'Describing weekend activities using past tense', 1, 1),
          (v_materia_id, r_grado.id, 'Creative writing workshops', 'Creating simple english comic strips and captions', 2, 2);
      ELSIF r_grado.numero_grado = 5 THEN
        INSERT INTO public.ades_temas (materia_id, grado_id, nombre_tema, descripcion, orden, periodo_sugerido) VALUES
          (v_materia_id, r_grado.id, 'Presentation of recipes and food', 'Describing step-by-step preparation of healthy snacks', 1, 1),
          (v_materia_id, r_grado.id, 'Reading of short tales', 'Analyzing plots and moral lessons in english', 2, 2);
      ELSIF r_grado.numero_grado = 6 THEN
        INSERT INTO public.ades_temas (materia_id, grado_id, nombre_tema, descripcion, orden, periodo_sugerido) VALUES
          (v_materia_id, r_grado.id, 'Debating simple school topics', 'Giving opinions, agreeing and disagreeing in english', 1, 1),
          (v_materia_id, r_grado.id, 'Final theater play in English', 'Writing and performing a short script in front of classmates', 2, 2);
      END IF;
    END IF;

    -- =========================================================================
    -- INFORMÁTICA Y PENSAMIENTO COMPUTACIONAL (NVI-PRI-INF)
    -- =========================================================================
    SELECT id INTO v_materia_id FROM public.ades_materias WHERE clave_materia = 'NVI-PRI-INF' AND is_active = TRUE;
    IF v_materia_id IS NOT NULL THEN
      IF r_grado.numero_grado = 1 THEN
        INSERT INTO public.ades_temas (materia_id, grado_id, nombre_tema, descripcion, orden, periodo_sugerido) VALUES
          (v_materia_id, r_grado.id, 'Partes de la computadora y encendido seguro', 'Identificación de monitor, teclado, mouse y CPU. Reglas de uso', 1, 1),
          (v_materia_id, r_grado.id, 'Manejo básico del mouse y puntero', 'Ejercicios de arrastrar, hacer doble clic y coordinación visomotriz', 2, 2);
      ELSIF r_grado.numero_grado = 2 THEN
        INSERT INTO public.ades_temas (materia_id, grado_id, nombre_tema, descripcion, orden, periodo_sugerido) VALUES
          (v_materia_id, r_grado.id, 'Uso del teclado alfanumérico', 'Escritura de palabras y números sencillos en bloc de notas', 1, 1),
          (v_materia_id, r_grado.id, 'Programas de dibujo básico (Paint)', 'Creación de figuras geométricas, coloreado digital y guardado', 2, 2);
      ELSIF r_grado.numero_grado = 3 THEN
        INSERT INTO public.ades_temas (materia_id, grado_id, nombre_tema, descripcion, orden, periodo_sugerido) VALUES
          (v_materia_id, r_grado.id, 'Procesador de textos básico', 'Dar formato a textos (fuente, color, tamaño) y alineación', 1, 1),
          (v_materia_id, r_grado.id, 'Búsqueda segura de imágenes en Internet', 'Uso de buscadores infantiles y copiado/pegado de archivos', 2, 2);
      ELSIF r_grado.numero_grado = 4 THEN
        INSERT INTO public.ades_temas (materia_id, grado_id, nombre_tema, descripcion, orden, periodo_sugerido) VALUES
          (v_materia_id, r_grado.id, 'Presentaciones digitales interactivas', 'Diseño de diapositivas con textos, imágenes y transiciones básicas', 1, 1),
          (v_materia_id, r_grado.id, 'Introducción al correo electrónico escolar', 'Estructura de un correo, destinatario, asunto y adjuntar archivos', 2, 2);
      ELSIF r_grado.numero_grado = 5 THEN
        INSERT INTO public.ades_temas (materia_id, grado_id, nombre_tema, descripcion, orden, periodo_sugerido) VALUES
          (v_materia_id, r_grado.id, 'Hojas de cálculo: tablas y sumas básicas', 'Uso de celdas, filas, columnas y fórmulas de suma y promedio', 1, 1),
          (v_materia_id, r_grado.id, 'Pensamiento computacional con Scratch', 'Conceptos de secuencia, bucles y control de movimiento de personajes', 2, 2);
      ELSIF r_grado.numero_grado = 6 THEN
        INSERT INTO public.ades_temas (materia_id, grado_id, nombre_tema, descripcion, orden, periodo_sugerido) VALUES
          (v_materia_id, r_grado.id, 'Ciudadanía digital y redes sociales seguras', 'Prevención de riesgos en línea, cyberbullying y cuidado de contraseñas', 1, 1),
          (v_materia_id, r_grado.id, 'Proyecto de programación Scratch avanzado', 'Diseño de un juego o historia animada interactiva con variables y eventos', 2, 2);
      END IF;
    END IF;

    -- =========================================================================
    -- ARTES (PRI-ART)
    -- =========================================================================
    SELECT id INTO v_materia_id FROM public.ades_materias WHERE clave_materia = 'PRI-ART' AND is_active = TRUE;
    IF v_materia_id IS NOT NULL THEN
      IF r_grado.numero_grado = 1 OR r_grado.numero_grado = 2 THEN
        INSERT INTO public.ades_temas (materia_id, grado_id, nombre_tema, descripcion, orden, periodo_sugerido) VALUES
          (v_materia_id, r_grado.id, 'Formas, colores y sonidos del entorno', 'Exploración sensorial del espacio y dibujo libre', 1, 1),
          (v_materia_id, r_grado.id, 'Expresión corporal y juegos rítmicos', 'Uso del cuerpo para seguir melodías y cantar rondas infantiles', 2, 2);
      ELSIF r_grado.numero_grado = 3 OR r_grado.numero_grado = 4 THEN
        INSERT INTO public.ades_temas (materia_id, grado_id, nombre_tema, descripcion, orden, periodo_sugerido) VALUES
          (v_materia_id, r_grado.id, 'Teoría del color y texturas táctiles', 'Uso de colores primarios/secundarios y modelado en plastilina', 1, 1),
          (v_materia_id, r_grado.id, 'Danza folclórica y expresión escénica', 'Apreciación de bailes tradicionales de México y coordinación rítmica', 2, 2);
      ELSIF r_grado.numero_grado = 5 OR r_grado.numero_grado = 6 THEN
        INSERT INTO public.ades_temas (materia_id, grado_id, nombre_tema, descripcion, orden, periodo_sugerido) VALUES
          (v_materia_id, r_grado.id, 'El muralismo y las vanguardias artísticas', 'Reconocimiento del arte nacional (Diego Rivera, Frida Kahlo) y su valor histórico', 1, 1),
          (v_materia_id, r_grado.id, 'Proyecto de teatro escolar y escenografía', 'Creación y montaje de obra escolar con reciclaje de utilería', 2, 2);
      END IF;
    END IF;

    -- =========================================================================
    -- EDUCACIÓN FÍSICA (PRI-EDF)
    -- =========================================================================
    SELECT id INTO v_materia_id FROM public.ades_materias WHERE clave_materia = 'PRI-EDF' AND is_active = TRUE;
    IF v_materia_id IS NOT NULL THEN
      IF r_grado.numero_grado = 1 OR r_grado.numero_grado = 2 THEN
        INSERT INTO public.ades_temas (materia_id, grado_id, nombre_tema, descripcion, orden, periodo_sugerido) VALUES
          (v_materia_id, r_grado.id, 'Esquema corporal y lateralidad', 'Reconocimiento de derecha/izquierda y partes del cuerpo en movimiento', 1, 1),
          (v_materia_id, r_grado.id, 'Equilibrio y locomoción básica', 'Saltar, correr, gatear y mantener posturas estables', 2, 2);
      ELSIF r_grado.numero_grado = 3 OR r_grado.numero_grado = 4 THEN
        INSERT INTO public.ades_temas (materia_id, grado_id, nombre_tema, descripcion, orden, periodo_sugerido) VALUES
          (v_materia_id, r_grado.id, 'Habilidades motrices combinadas y manipulación', 'Lanzar y atrapar pelotas, botar con ritmo y saltar obstáculos', 1, 1),
          (v_materia_id, r_grado.id, 'Juegos predeportivos y trabajo en equipo', 'Reglas básicas de juego cooperativo y coordinación en grupo', 2, 2);
      ELSIF r_grado.numero_grado = 5 OR r_grado.numero_grado = 6 THEN
        INSERT INTO public.ades_temas (materia_id, grado_id, nombre_tema, descripcion, orden, periodo_sugerido) VALUES
          (v_materia_id, r_grado.id, 'Capacidades físicas: velocidad, fuerza y flexibilidad', 'Circuitos de ejercicios deportivos controlados y toma de frecuencia cardíaca', 1, 1),
          (v_materia_id, r_grado.id, 'Iniciación deportiva y tácticas colectivas', 'Voleibol, fútbol y básquetbol escolar: arbitraje, jugadas estructuradas', 2, 2);
      END IF;
    END IF;

  END LOOP;
END $$;

COMMIT;
