-- ============================================================
-- MIGRACIÓN 062 — Seed convocatorias de flyers institucionales
-- Origen: flyers publicados por Nevadi Instituto, junio 2026
-- 2 OFERTA_EDUCATIVA  +  8 RECURSOS_HUMANOS  = 10 convocatorias
-- ============================================================
-- Ejecutar:
--   docker compose exec -T postgres psql -U ades_admin -d ades < db/migrations/062_seed_convocatorias_flyers_jun2026.sql

BEGIN;

-- ─────────────────────────────────────────────────────────────
-- UUIDs de referencia
-- ─────────────────────────────────────────────────────────────
-- Planteles
-- Tenancingo    : 019e8f74-d143-7368-a0b6-06cc2fbc7156
-- Ixtapan de la Sal: 019e8f74-d143-740c-aa16-63a83c575d92
-- Niveles
-- PRIMARIA      : 019e8f74-d13f-7052-9890-b128df7ea199
-- SECUNDARIA    : 019e8f74-d13f-77e5-aeb8-e859b106072c
-- PREPARATORIA  : 019e8f74-d13f-788e-8ed6-99c4825b22c8

-- ════════════════════════════════════════════════════════════
-- BLOQUE A — OFERTA EDUCATIVA
-- ════════════════════════════════════════════════════════════

-- ─────────────────────────────────────────────────────────────
-- A-1: Admisiones Abiertas 2026-2027 (todos los planteles)
-- ─────────────────────────────────────────────────────────────
DO $$
DECLARE v_id UUID;
BEGIN
  INSERT INTO portal.convocatorias (
    categoria, tipo, titulo, descripcion,
    plantel_id, nivel_educativo_id,
    fecha_inicio_postulacion, fecha_cierre_postulacion,
    cupo_maximo, is_published, usuario_creacion
  ) VALUES (
    'OFERTA_EDUCATIVA', 'INSCRIPCION',
    'Admisiones Abiertas — Ciclo Escolar 2026-2027 | Instituto Nevadi',
    'Instituto Nevadi abre sus puertas para el ciclo 2026-2027 en los niveles Primaria, Secundaria y Preparatoria (incorporada a la UAEMéx). Becas disponibles, servicio de comedor y horario de tiempo completo.',
    NULL, NULL,
    '2026-06-01 08:00:00-06',
    '2026-07-31 20:00:00-06',
    NULL, TRUE, 'admin@nevadi.edu.mx'
  ) RETURNING id INTO v_id;

  INSERT INTO portal.secciones_convocatoria
    (convocatoria_id, tipo_seccion, titulo, contenido, orden, usuario_creacion)
  VALUES (v_id, 'INTRO',
    'En Nevadi, creemos que la educación transforma vidas.',
    'Ofrecemos educación de calidad con oportunidades de crecimiento académico y personal en tres niveles educativos: Primaria SEP, Secundaria SEP y Preparatoria incorporada a la UAEMéx.',
    0, 'admin@nevadi.edu.mx');

  INSERT INTO portal.secciones_convocatoria
    (convocatoria_id, tipo_seccion, titulo, datos, orden, usuario_creacion)
  VALUES (v_id, 'LISTA', '¿Por qué elegir Instituto Nevadi?',
    '[
      "Educación con valores",
      "Acompañamiento humano y emocional",
      "Docentes capacitados",
      "Inglés y computación desde primaria",
      "Actividades deportivas, artísticas, científicas y financieras",
      "Instalaciones seguras y servicio de comedor saludable",
      "Horario de tiempo completo",
      "Becas académicas disponibles"
    ]'::JSONB,
    1, 'admin@nevadi.edu.mx');

  INSERT INTO portal.secciones_convocatoria
    (convocatoria_id, tipo_seccion, titulo, datos, orden, usuario_creacion)
  VALUES (v_id, 'PROCESO', 'Proceso de Inscripción — Primer Paso',
    '[
      {"num":1,"titulo":"Reúne tu documentación","desc":"Acta de nacimiento, CURP del alumno y padres, identificación INE, fotografía infantil, comprobante de domicilio y de ingresos, carta de antecedentes no penales, carta de exposición de motivos y boletas anteriores. Consulta la lista completa según el nivel."},
      {"num":2,"titulo":"Acude con un tutor","desc":"Para iniciar el trámite debe acudir una sola persona (papá, mamá o tutor). No se recibe documentación incompleta."},
      {"num":3,"titulo":"Horario de atención","desc":"Lunes a jueves de 8:00 a.m. a 1:00 p.m. Plantel Tenancingo: Km 1.15 Carretera Tenancingo-Tenería, San José El Cuartel."},
      {"num":4,"titulo":"Confirmación de inscripción","desc":"Una vez revisada tu documentación recibirás confirmación y la asignación de grupo. Los lugares son limitados y se asignan por orden de entrega."}
    ]'::JSONB,
    2, 'admin@nevadi.edu.mx');

  INSERT INTO portal.secciones_convocatoria
    (convocatoria_id, tipo_seccion, titulo, datos, orden, usuario_creacion)
  VALUES (v_id, 'FAQ', 'Documentos por nivel',
    '[
      {"pregunta":"¿Qué documentos necesito para Primaria?","respuesta":"Acta de nacimiento, CURP del alumno y padres, INE de tutores, fotografía, comprobante de domicilio y de ingresos, carta de antecedentes no penales, carta de motivos, 3 contactos de confianza, boletas de preescolar en adelante y RFC de ambos padres."},
      {"pregunta":"¿Qué documentos necesito para Secundaria?","respuesta":"Los mismos que Primaria, más boletas de 1° y 2° de preescolar. RFC de ambos padres."},
      {"pregunta":"¿Qué documentos necesito para Preparatoria?","respuesta":"Los mismos requisitos base más: certificado de preescolar, boletas de primaria, boletas de secundaria 1° y 2°, y comprobante de ingresos de ambos padres."},
      {"pregunta":"¿Ofrecen becas?","respuesta":"Sí. Contamos con un proceso de selección para otorgar becas académicas a familias comprometidas con la educación de sus hijos. Consulta la convocatoria de becas para más información."},
      {"pregunta":"¿Cuáles son los teléfonos de contacto?","respuesta":"Primaria: 714 688 7684 | Secundaria y Preparatoria: 714 142 6815"}
    ]'::JSONB,
    3, 'admin@nevadi.edu.mx');

  INSERT INTO portal.secciones_convocatoria
    (convocatoria_id, tipo_seccion, titulo, contenido, datos, orden, usuario_creacion)
  VALUES (v_id, 'AVISO', 'Documentación completa obligatoria',
    'No se recibirá documentación incompleta, sin excepción.',
    '{"nivel":"warn","icono":"pi-exclamation-triangle"}'::JSONB,
    4, 'admin@nevadi.edu.mx');

  INSERT INTO portal.secciones_convocatoria
    (convocatoria_id, tipo_seccion, titulo, contenido, datos, orden, usuario_creacion)
  VALUES (v_id, 'CTA', '¡Solicita informes hoy!',
    'Conoce todo lo que Instituto Nevadi tiene para tu hijo. Tu compromiso hoy, su futuro mañana.',
    '{"texto":"Iniciar mi postulación","url":"#postular"}'::JSONB,
    5, 'admin@nevadi.edu.mx');
END $$;

-- ─────────────────────────────────────────────────────────────
-- A-2: Becas Académicas — Proceso de Selección 2026-2027 (Tenancingo)
-- ─────────────────────────────────────────────────────────────
DO $$
DECLARE v_id UUID;
BEGIN
  INSERT INTO portal.convocatorias (
    categoria, tipo, titulo, descripcion,
    plantel_id, nivel_educativo_id,
    fecha_inicio_postulacion, fecha_cierre_postulacion,
    cupo_maximo, is_published, usuario_creacion
  ) VALUES (
    'OFERTA_EDUCATIVA', 'BECA',
    'Becas Académicas — Proceso de Selección 2026-2027 | Nevadi Tenancingo',
    'Continuamos el proceso de selección para otorgar becas académicas a familias comprometidas con la educación de sus hijos. Disponibles para Primaria (todos los grados), Secundaria (2° y 3°) y Preparatoria incorporada a la UAEMéx.',
    '019e8f74-d143-7368-a0b6-06cc2fbc7156', NULL,
    '2026-06-01 08:00:00-06',
    '2026-07-31 20:00:00-06',
    NULL, TRUE, 'admin@nevadi.edu.mx'
  ) RETURNING id INTO v_id;

  INSERT INTO portal.secciones_convocatoria
    (convocatoria_id, tipo_seccion, titulo, contenido, orden, usuario_creacion)
  VALUES (v_id, 'INTRO', '¡Continuamos el proceso de selección!',
    'Instituto Nevadi Tenancingo ofrece becas académicas para familias comprometidas con la educación de sus hijos. Formamos mentes e inspiramos futuros con educación con valores, excelencia y propósito.',
    0, 'admin@nevadi.edu.mx');

  INSERT INTO portal.secciones_convocatoria
    (convocatoria_id, tipo_seccion, titulo, datos, orden, usuario_creacion)
  VALUES (v_id, 'LISTA', 'Niveles y grados con beca disponible',
    '[
      "Primaria — todos los grados",
      "Secundaria — 2° y 3° de secundaria",
      "Preparatoria — incorporada a la UAEMéx"
    ]'::JSONB,
    1, 'admin@nevadi.edu.mx');

  INSERT INTO portal.secciones_convocatoria
    (convocatoria_id, tipo_seccion, titulo, datos, orden, usuario_creacion)
  VALUES (v_id, 'LISTA', 'Lo que nos distingue',
    '[
      "Excelencia académica",
      "Formación en valores",
      "Aula Maker e innovación",
      "Bienestar integral",
      "Laboratorio equipado",
      "Inglés y tecnología",
      "Desarrollo de habilidades para la vida"
    ]'::JSONB,
    2, 'admin@nevadi.edu.mx');

  INSERT INTO portal.secciones_convocatoria
    (convocatoria_id, tipo_seccion, titulo, datos, orden, usuario_creacion)
  VALUES (v_id, 'FAQ', 'Preguntas frecuentes',
    '[
      {"pregunta":"¿Cómo inicio el proceso de beca?","respuesta":"Llama al 714 688 7684 (Primaria) o 714 142 6815 (Secundaria y Preparatoria) para agendar una cita de informes."},
      {"pregunta":"¿Cuáles son los criterios de selección?","respuesta":"El proceso considera el desempeño académico previo y el compromiso de la familia con el proyecto educativo de Nevadi."},
      {"pregunta":"¿Dónde están ubicados?","respuesta":"Km 1.15 Carretera Tenancingo-Tenería, San José El Cuartel, Tenancingo, Estado de México."}
    ]'::JSONB,
    3, 'admin@nevadi.edu.mx');

  INSERT INTO portal.secciones_convocatoria
    (convocatoria_id, tipo_seccion, titulo, contenido, datos, orden, usuario_creacion)
  VALUES (v_id, 'CTA', '¡Solicita informes ahora!',
    'Conoce nuestras becas académicas y da el primer paso hacia una educación de excelencia.',
    '{"texto":"Solicitar información","url":"#postular"}'::JSONB,
    4, 'admin@nevadi.edu.mx');
END $$;


-- ════════════════════════════════════════════════════════════
-- BLOQUE B — RECURSOS HUMANOS
-- ════════════════════════════════════════════════════════════

-- ─────────────────────────────────────────────────────────────
-- B-1: Docente de Inglés — Secundaria Ixtapan de la Sal
-- ─────────────────────────────────────────────────────────────
DO $$
DECLARE v_id UUID;
BEGIN
  INSERT INTO portal.convocatorias (
    categoria, tipo, titulo, descripcion,
    plantel_id, nivel_educativo_id,
    fecha_inicio_postulacion, fecha_cierre_postulacion,
    cupo_maximo, is_published, usuario_creacion
  ) VALUES (
    'RECURSOS_HUMANOS', 'VACANTE_DOCENTE',
    'Docente de Inglés — Secundaria Ixtapan de la Sal',
    'Instituto Nevadi plantel Ixtapan de la Sal busca Docente de Inglés para nivel secundaria. Contratación por horas. Incorporación ciclo escolar Agosto 2026-2027.',
    '019e8f74-d143-740c-aa16-63a83c575d92',
    '019e8f74-d13f-77e5-aeb8-e859b106072c',
    '2026-06-15 08:00:00-06',
    '2026-07-15 20:00:00-06',
    1, TRUE, 'admin@nevadi.edu.mx'
  ) RETURNING id INTO v_id;

  INSERT INTO portal.secciones_convocatoria
    (convocatoria_id, tipo_seccion, titulo, contenido, orden, usuario_creacion)
  VALUES (v_id, 'INTRO', 'Estamos buscando Docente de Inglés',
    'Instituto Nevadi Ixtapan de la Sal busca un profesional comprometido con la educación para impartir la asignatura de Inglés en nivel secundaria. Formarás parte de un equipo de docentes capacitados en un excelente ambiente laboral.',
    0, 'admin@nevadi.edu.mx');

  INSERT INTO portal.secciones_convocatoria
    (convocatoria_id, tipo_seccion, titulo, datos, orden, usuario_creacion)
  VALUES (v_id, 'LISTA', 'Requisitos',
    '[
      "Licenciatura en Educación Secundaria con Especialidad en Lengua Extranjera",
      "Indispensable: Título y cédula profesional",
      "Experiencia docente impartiendo clases en secundaria y dominio del idioma",
      "Deseable: experiencia impartiendo club de lectura o Español"
    ]'::JSONB,
    1, 'admin@nevadi.edu.mx');

  INSERT INTO portal.secciones_convocatoria
    (convocatoria_id, tipo_seccion, titulo, datos, orden, usuario_creacion)
  VALUES (v_id, 'LISTA', 'Ofrecemos',
    '[
      "Sueldo competitivo",
      "Prestaciones de Ley",
      "Servicio de comedor",
      "Capacitación continua",
      "Excelente ambiente laboral",
      "Contratación por horas"
    ]'::JSONB,
    2, 'admin@nevadi.edu.mx');

  INSERT INTO portal.secciones_convocatoria
    (convocatoria_id, tipo_seccion, titulo, datos, orden, usuario_creacion)
  VALUES (v_id, 'PROCESO', 'Cómo postular',
    '[
      {"num":1,"titulo":"Fecha de ingreso","desc":"Ciclo escolar Agosto 2026-2027"},
      {"num":2,"titulo":"Envía tu CV","desc":"Manda tu curriculum vitae a: talentohumano.ixtapan@institutonevadi.edu.mx"},
      {"num":3,"titulo":"Entrevista","desc":"El equipo de Talento Humano se pondrá en contacto para agendar entrevista."}
    ]'::JSONB,
    3, 'admin@nevadi.edu.mx');

  INSERT INTO portal.secciones_convocatoria
    (convocatoria_id, tipo_seccion, titulo, contenido, datos, orden, usuario_creacion)
  VALUES (v_id, 'CTA', '¡Aplica ahora!',
    'Envía tu CV a talentohumano.ixtapan@institutonevadi.edu.mx',
    '{"texto":"Enviar mi CV","url":"mailto:talentohumano.ixtapan@institutonevadi.edu.mx"}'::JSONB,
    4, 'admin@nevadi.edu.mx');
END $$;

-- ─────────────────────────────────────────────────────────────
-- B-2: Auxiliar de Mantenimiento — Ixtapan de la Sal
-- ─────────────────────────────────────────────────────────────
DO $$
DECLARE v_id UUID;
BEGIN
  INSERT INTO portal.convocatorias (
    categoria, tipo, titulo, descripcion,
    plantel_id, nivel_educativo_id,
    fecha_inicio_postulacion, fecha_cierre_postulacion,
    cupo_maximo, is_published, usuario_creacion
  ) VALUES (
    'RECURSOS_HUMANOS', 'VACANTE_ADMINISTRATIVA',
    'Auxiliar de Mantenimiento — Plantel Ixtapan de la Sal',
    'Instituto Nevadi plantel Ixtapan de la Sal busca Auxiliar de Mantenimiento. Incorporación inmediata.',
    '019e8f74-d143-740c-aa16-63a83c575d92', NULL,
    '2026-06-15 08:00:00-06',
    '2026-07-31 20:00:00-06',
    1, TRUE, 'admin@nevadi.edu.mx'
  ) RETURNING id INTO v_id;

  INSERT INTO portal.secciones_convocatoria
    (convocatoria_id, tipo_seccion, titulo, contenido, orden, usuario_creacion)
  VALUES (v_id, 'INTRO', 'Estamos buscando Auxiliar de Mantenimiento',
    'Instituto Nevadi Ixtapan de la Sal requiere un Auxiliar de Mantenimiento para ejecutar actividades del plan de mantenimiento preventivo y correctivo en todas las áreas del plantel.',
    0, 'admin@nevadi.edu.mx');

  INSERT INTO portal.secciones_convocatoria
    (convocatoria_id, tipo_seccion, titulo, datos, orden, usuario_creacion)
  VALUES (v_id, 'LISTA', 'Requisitos',
    '[
      "Escolaridad: Preparatoria trunca o Carrera técnica en mantenimiento",
      "Mínimo 2 años de experiencia en mantenimiento general (pintura, jardinería, electricidad, etc.)",
      "Fecha de ingreso: Inmediato"
    ]'::JSONB,
    1, 'admin@nevadi.edu.mx');

  INSERT INTO portal.secciones_convocatoria
    (convocatoria_id, tipo_seccion, titulo, datos, orden, usuario_creacion)
  VALUES (v_id, 'LISTA', 'Funciones',
    '[
      "Eléctrico",
      "Herrería",
      "Carpintería",
      "Plomería",
      "Mobiliario",
      "Canchas deportivas",
      "Instalaciones especiales y Obra Civil"
    ]'::JSONB,
    2, 'admin@nevadi.edu.mx');

  INSERT INTO portal.secciones_convocatoria
    (convocatoria_id, tipo_seccion, titulo, datos, orden, usuario_creacion)
  VALUES (v_id, 'LISTA', 'Ofrecemos',
    '[
      "Sueldo competitivo",
      "Prestaciones de Ley",
      "Buen ambiente laboral",
      "Horario: Lunes a Viernes 7:00 am a 4:30 pm, sábados 8:00 am a 12:00 pm"
    ]'::JSONB,
    3, 'admin@nevadi.edu.mx');

  INSERT INTO portal.secciones_convocatoria
    (convocatoria_id, tipo_seccion, titulo, contenido, datos, orden, usuario_creacion)
  VALUES (v_id, 'CTA', '¡Aplica ahora!',
    'Envía tu CV a talentohumano.ixtapan@institutonevadi.edu.mx',
    '{"texto":"Enviar mi CV","url":"mailto:talentohumano.ixtapan@institutonevadi.edu.mx"}'::JSONB,
    4, 'admin@nevadi.edu.mx');
END $$;

-- ─────────────────────────────────────────────────────────────
-- B-3: Docente de Matemáticas — Secundaria Ixtapan de la Sal
-- ─────────────────────────────────────────────────────────────
DO $$
DECLARE v_id UUID;
BEGIN
  INSERT INTO portal.convocatorias (
    categoria, tipo, titulo, descripcion,
    plantel_id, nivel_educativo_id,
    fecha_inicio_postulacion, fecha_cierre_postulacion,
    cupo_maximo, is_published, usuario_creacion
  ) VALUES (
    'RECURSOS_HUMANOS', 'VACANTE_DOCENTE',
    'Docente de Matemáticas — Secundaria Ixtapan de la Sal',
    'Instituto Nevadi plantel Ixtapan de la Sal busca Docente de Matemáticas para nivel secundaria. 40 horas clase quincenales. Incorporación inmediata.',
    '019e8f74-d143-740c-aa16-63a83c575d92',
    '019e8f74-d13f-77e5-aeb8-e859b106072c',
    '2026-06-15 08:00:00-06',
    '2026-07-31 20:00:00-06',
    1, TRUE, 'admin@nevadi.edu.mx'
  ) RETURNING id INTO v_id;

  INSERT INTO portal.secciones_convocatoria
    (convocatoria_id, tipo_seccion, titulo, contenido, orden, usuario_creacion)
  VALUES (v_id, 'INTRO', 'Estamos buscando Docente de Matemáticas',
    'Instituto Nevadi Ixtapan de la Sal busca un profesional con experiencia docente en nivel secundaria para impartir la asignatura de Matemáticas.',
    0, 'admin@nevadi.edu.mx');

  INSERT INTO portal.secciones_convocatoria
    (convocatoria_id, tipo_seccion, titulo, datos, orden, usuario_creacion)
  VALUES (v_id, 'LISTA', 'Requisitos',
    '[
      "Licenciatura en Matemáticas, Ingenierías o afín",
      "Indispensable: Título y cédula profesional",
      "Experiencia docente impartiendo clases en nivel secundaria",
      "Contratación: 40 horas clase quincenales",
      "Fecha de ingreso: Inmediato"
    ]'::JSONB,
    1, 'admin@nevadi.edu.mx');

  INSERT INTO portal.secciones_convocatoria
    (convocatoria_id, tipo_seccion, titulo, datos, orden, usuario_creacion)
  VALUES (v_id, 'LISTA', 'Ofrecemos',
    '[
      "Sueldo competitivo",
      "Prestaciones de Ley",
      "Capacitación continua",
      "Excelente ambiente laboral"
    ]'::JSONB,
    2, 'admin@nevadi.edu.mx');

  INSERT INTO portal.secciones_convocatoria
    (convocatoria_id, tipo_seccion, titulo, contenido, datos, orden, usuario_creacion)
  VALUES (v_id, 'CTA', '¡Aplica ahora!',
    'Envía tu CV a talentohumano.ixtapan@institutonevadi.edu.mx',
    '{"texto":"Enviar mi CV","url":"mailto:talentohumano.ixtapan@institutonevadi.edu.mx"}'::JSONB,
    3, 'admin@nevadi.edu.mx');
END $$;

-- ─────────────────────────────────────────────────────────────
-- B-4: Docente de Ciencias Sociales I y II — Preparatoria Tenancingo
-- ─────────────────────────────────────────────────────────────
DO $$
DECLARE v_id UUID;
BEGIN
  INSERT INTO portal.convocatorias (
    categoria, tipo, titulo, descripcion,
    plantel_id, nivel_educativo_id,
    fecha_inicio_postulacion, fecha_cierre_postulacion,
    cupo_maximo, is_published, usuario_creacion
  ) VALUES (
    'RECURSOS_HUMANOS', 'VACANTE_DOCENTE',
    'Docente de Ciencias Sociales I y II — Preparatoria Tenancingo',
    'Grupo Educativo Nevadi busca Docente para impartir Ciencias Sociales I y II en Preparatoria Tenancingo. Contratación por horas. Ciclo escolar julio 2026-2027.',
    '019e8f74-d143-7368-a0b6-06cc2fbc7156',
    '019e8f74-d13f-788e-8ed6-99c4825b22c8',
    '2026-06-15 08:00:00-06',
    '2026-07-10 20:00:00-06',
    1, TRUE, 'admin@nevadi.edu.mx'
  ) RETURNING id INTO v_id;

  INSERT INTO portal.secciones_convocatoria
    (convocatoria_id, tipo_seccion, titulo, contenido, orden, usuario_creacion)
  VALUES (v_id, 'INTRO', 'Trabaja con nosotros — Docente de Ciencias Sociales',
    'Grupo Educativo Nevadi busca profesionales comprometidos con la educación y la formación integral de nuestros estudiantes para impartir Ciencias Sociales I y II en nivel preparatoria, plantel Tenancingo.',
    0, 'admin@nevadi.edu.mx');

  INSERT INTO portal.secciones_convocatoria
    (convocatoria_id, tipo_seccion, titulo, datos, orden, usuario_creacion)
  VALUES (v_id, 'LISTA', 'Requisitos',
    '[
      "Licenciatura en Antropología, Historia, Sociología o afín",
      "Indispensable: Título y Cédula Profesional",
      "Experiencia en el ámbito educativo nivel preparatoria",
      "Contratación por horas clase — Lunes a viernes 6:45 am a 16:00 hrs",
      "Fecha de ingreso: Ciclo escolar julio 2026-2027"
    ]'::JSONB,
    1, 'admin@nevadi.edu.mx');

  INSERT INTO portal.secciones_convocatoria
    (convocatoria_id, tipo_seccion, titulo, datos, orden, usuario_creacion)
  VALUES (v_id, 'LISTA', 'Ofrecemos',
    '[
      "Sueldo competitivo",
      "Prestaciones de Ley",
      "Capacitación continua",
      "Excelente ambiente laboral"
    ]'::JSONB,
    2, 'admin@nevadi.edu.mx');

  INSERT INTO portal.secciones_convocatoria
    (convocatoria_id, tipo_seccion, titulo, contenido, datos, orden, usuario_creacion)
  VALUES (v_id, 'CTA', '¡Aplica ahora!',
    'Envía tu CV a l.medina@institutonevadi.edu.mx',
    '{"texto":"Enviar mi CV","url":"mailto:l.medina@institutonevadi.edu.mx"}'::JSONB,
    3, 'admin@nevadi.edu.mx');
END $$;

-- ─────────────────────────────────────────────────────────────
-- B-5: Docente de Humanidades — Preparatoria Tenancingo
-- ─────────────────────────────────────────────────────────────
DO $$
DECLARE v_id UUID;
BEGIN
  INSERT INTO portal.convocatorias (
    categoria, tipo, titulo, descripcion,
    plantel_id, nivel_educativo_id,
    fecha_inicio_postulacion, fecha_cierre_postulacion,
    cupo_maximo, is_published, usuario_creacion
  ) VALUES (
    'RECURSOS_HUMANOS', 'VACANTE_DOCENTE',
    'Docente de Humanidades — Preparatoria Nevadi Tenancingo',
    'Instituto Nevadi busca Docente de Humanidades para nivel preparatoria, plantel Tenancingo. Contratación por horas. Fecha de ingreso: 29 de julio 2026, ciclo escolar 2026-2027.',
    '019e8f74-d143-7368-a0b6-06cc2fbc7156',
    '019e8f74-d13f-788e-8ed6-99c4825b22c8',
    '2026-06-15 08:00:00-06',
    '2026-07-20 20:00:00-06',
    1, TRUE, 'admin@nevadi.edu.mx'
  ) RETURNING id INTO v_id;

  INSERT INTO portal.secciones_convocatoria
    (convocatoria_id, tipo_seccion, titulo, contenido, orden, usuario_creacion)
  VALUES (v_id, 'INTRO', 'Estamos contratando — Docente de Humanidades',
    'Buscamos profesionales comprometidos con la educación y la formación integral de nuestros estudiantes para impartir la asignatura de Humanidades en nivel preparatoria, plantel Tenancingo.',
    0, 'admin@nevadi.edu.mx');

  INSERT INTO portal.secciones_convocatoria
    (convocatoria_id, tipo_seccion, titulo, datos, orden, usuario_creacion)
  VALUES (v_id, 'LISTA', 'Perfiles aceptados',
    '[
      "Licenciatura en Filosofía",
      "Licenciatura en Humanidades",
      "Licenciatura en Derecho con Diplomado o Posgrado en Filosofía o Humanidades",
      "Maestría en Enseñanza de la Filosofía en Nivel Medio Superior"
    ]'::JSONB,
    1, 'admin@nevadi.edu.mx');

  INSERT INTO portal.secciones_convocatoria
    (convocatoria_id, tipo_seccion, titulo, datos, orden, usuario_creacion)
  VALUES (v_id, 'LISTA', 'Requisitos',
    '[
      "Experiencia docente impartiendo clases en nivel preparatoria",
      "Dominio de los contenidos de las áreas de humanidades",
      "Habilidades para el análisis crítico, la argumentación y la comunicación",
      "Compromiso con la formación integral de los estudiantes"
    ]'::JSONB,
    2, 'admin@nevadi.edu.mx');

  INSERT INTO portal.secciones_convocatoria
    (convocatoria_id, tipo_seccion, titulo, datos, orden, usuario_creacion)
  VALUES (v_id, 'LISTA', 'Ofrecemos',
    '[
      "Sueldo competitivo",
      "Prestaciones de Ley",
      "Capacitación continua",
      "Excelente ambiente laboral",
      "Contratación por horas — Fecha de ingreso: 29 de julio 2026"
    ]'::JSONB,
    3, 'admin@nevadi.edu.mx');

  INSERT INTO portal.secciones_convocatoria
    (convocatoria_id, tipo_seccion, titulo, contenido, datos, orden, usuario_creacion)
  VALUES (v_id, 'CTA', '¡Aplica ahora!',
    'Envía tu CV a talentohumano.tenancingo@institutonevadi.edu.mx',
    '{"texto":"Enviar mi CV","url":"mailto:talentohumano.tenancingo@institutonevadi.edu.mx"}'::JSONB,
    4, 'admin@nevadi.edu.mx');
END $$;

-- ─────────────────────────────────────────────────────────────
-- B-6: Docente de Educación Ambiental — Preparatoria Tenancingo
-- ─────────────────────────────────────────────────────────────
DO $$
DECLARE v_id UUID;
BEGIN
  INSERT INTO portal.convocatorias (
    categoria, tipo, titulo, descripcion,
    plantel_id, nivel_educativo_id,
    fecha_inicio_postulacion, fecha_cierre_postulacion,
    cupo_maximo, is_published, usuario_creacion
  ) VALUES (
    'RECURSOS_HUMANOS', 'VACANTE_DOCENTE',
    'Docente de Educación Ambiental — Preparatoria Nevadi Tenancingo',
    'Instituto Nevadi busca Docente de Educación Ambiental para nivel preparatoria, plantel Tenancingo. Contratación por horas. Fecha de ingreso: 29 de julio 2026.',
    '019e8f74-d143-7368-a0b6-06cc2fbc7156',
    '019e8f74-d13f-788e-8ed6-99c4825b22c8',
    '2026-06-15 08:00:00-06',
    '2026-07-20 20:00:00-06',
    1, TRUE, 'admin@nevadi.edu.mx'
  ) RETURNING id INTO v_id;

  INSERT INTO portal.secciones_convocatoria
    (convocatoria_id, tipo_seccion, titulo, contenido, orden, usuario_creacion)
  VALUES (v_id, 'INTRO', 'Únete a nuestro equipo — Docente de Educación Ambiental',
    'Buscamos profesionales comprometidos con la educación y la formación integral de nuestros estudiantes para impartir la asignatura de Educación Ambiental en nivel preparatoria.',
    0, 'admin@nevadi.edu.mx');

  INSERT INTO portal.secciones_convocatoria
    (convocatoria_id, tipo_seccion, titulo, datos, orden, usuario_creacion)
  VALUES (v_id, 'LISTA', 'Perfiles aceptados',
    '[
      "Licenciatura en Educación Primaria",
      "Pedagogía",
      "Sociología",
      "Historia",
      "Ingeniería en Agronomía",
      "Planeación y Desarrollo Territorial",
      "Psicología Social",
      "Ciencias Sociales",
      "Ciencias Ambientales",
      "Licenciatura en Geografía"
    ]'::JSONB,
    1, 'admin@nevadi.edu.mx');

  INSERT INTO portal.secciones_convocatoria
    (convocatoria_id, tipo_seccion, titulo, datos, orden, usuario_creacion)
  VALUES (v_id, 'LISTA', 'Requisitos',
    '[
      "Rama común de experiencia: Geografía o haber impartido clases de Geografía",
      "Indispensable: experiencia impartiendo clase en secundaria o preparatoria",
      "Contratación por horas — Fecha de ingreso: 29 de julio 2026, ciclo escolar 2026-2027"
    ]'::JSONB,
    2, 'admin@nevadi.edu.mx');

  INSERT INTO portal.secciones_convocatoria
    (convocatoria_id, tipo_seccion, titulo, datos, orden, usuario_creacion)
  VALUES (v_id, 'LISTA', 'Responsabilidades',
    '[
      "Impartir la asignatura de Educación Ambiental en nivel preparatoria",
      "Fomentar la conciencia ambiental, el pensamiento crítico y la participación activa",
      "Diseñar y aplicar estrategias didácticas que promuevan el aprendizaje significativo y la sostenibilidad",
      "Vincular los contenidos con problemáticas ambientales locales y globales",
      "Evaluar el aprendizaje y dar seguimiento al desempeño académico de los alumnos"
    ]'::JSONB,
    3, 'admin@nevadi.edu.mx');

  INSERT INTO portal.secciones_convocatoria
    (convocatoria_id, tipo_seccion, titulo, datos, orden, usuario_creacion)
  VALUES (v_id, 'LISTA', 'Ofrecemos',
    '[
      "Sueldo competitivo",
      "Prestaciones de Ley",
      "Capacitación continua",
      "Excelente ambiente laboral"
    ]'::JSONB,
    4, 'admin@nevadi.edu.mx');

  INSERT INTO portal.secciones_convocatoria
    (convocatoria_id, tipo_seccion, titulo, contenido, datos, orden, usuario_creacion)
  VALUES (v_id, 'CTA', '¡Aplica ahora!',
    'Envía tu CV a talentohumano.tenancingo@institutonevadi.edu.mx',
    '{"texto":"Enviar mi CV","url":"mailto:talentohumano.tenancingo@institutonevadi.edu.mx"}'::JSONB,
    5, 'admin@nevadi.edu.mx');
END $$;

-- ─────────────────────────────────────────────────────────────
-- B-7: Docente de Inglés — Preparatoria Nevadi Tenancingo (apertura)
-- ─────────────────────────────────────────────────────────────
DO $$
DECLARE v_id UUID;
BEGIN
  INSERT INTO portal.convocatorias (
    categoria, tipo, titulo, descripcion,
    plantel_id, nivel_educativo_id,
    fecha_inicio_postulacion, fecha_cierre_postulacion,
    cupo_maximo, is_published, usuario_creacion
  ) VALUES (
    'RECURSOS_HUMANOS', 'VACANTE_DOCENTE',
    'Docente de Inglés — Preparatoria Nevadi Tenancingo (Apertura)',
    'Convocatoria especial: apertura de Preparatoria. Instituto Nevadi Tenancingo busca Docente de Inglés nivel B2 para nuevo proyecto educativo. Incorporación julio 2026. Contratación por horas.',
    '019e8f74-d143-7368-a0b6-06cc2fbc7156',
    '019e8f74-d13f-788e-8ed6-99c4825b22c8',
    '2026-06-15 08:00:00-06',
    '2026-07-15 20:00:00-06',
    1, TRUE, 'admin@nevadi.edu.mx'
  ) RETURNING id INTO v_id;

  INSERT INTO portal.secciones_convocatoria
    (convocatoria_id, tipo_seccion, titulo, contenido, orden, usuario_creacion)
  VALUES (v_id, 'INTRO', 'Convocatoria — Apertura de Preparatoria',
    'Únete a nuestro equipo y forma parte de la apertura de nuestra nueva preparatoria. Buscamos un Docente de Inglés comprometido con la excelencia educativa para nivel medio superior.',
    0, 'admin@nevadi.edu.mx');

  INSERT INTO portal.secciones_convocatoria
    (convocatoria_id, tipo_seccion, titulo, datos, orden, usuario_creacion)
  VALUES (v_id, 'LISTA', 'Licenciaturas indispensables',
    '[
      "Licenciado(a) en Lengua Inglesa",
      "Licenciado(a) en Lenguas",
      "Licenciado(a) en Enseñanza del Inglés"
    ]'::JSONB,
    1, 'admin@nevadi.edu.mx');

  INSERT INTO portal.secciones_convocatoria
    (convocatoria_id, tipo_seccion, titulo, datos, orden, usuario_creacion)
  VALUES (v_id, 'LISTA', 'Requisitos',
    '[
      "Dominio del idioma: Nivel B2 mínimo",
      "Experiencia docente impartiendo clases en nivel preparatoria",
      "Contratación por horas — Incorporación julio 2026, ciclo escolar 2026-2027"
    ]'::JSONB,
    2, 'admin@nevadi.edu.mx');

  INSERT INTO portal.secciones_convocatoria
    (convocatoria_id, tipo_seccion, titulo, contenido, datos, orden, usuario_creacion)
  VALUES (v_id, 'AVISO', 'Nota importante',
    'La Licenciatura en Educación Secundaria con Especialidad en Lengua Extranjera NO será considerada para nivel medio superior.',
    '{"nivel":"warn","icono":"pi-exclamation-circle"}'::JSONB,
    3, 'admin@nevadi.edu.mx');

  INSERT INTO portal.secciones_convocatoria
    (convocatoria_id, tipo_seccion, titulo, datos, orden, usuario_creacion)
  VALUES (v_id, 'LISTA', 'Ofrecemos',
    '[
      "Sueldo competitivo",
      "Prestaciones de Ley",
      "Capacitación continua",
      "Excelente ambiente laboral"
    ]'::JSONB,
    4, 'admin@nevadi.edu.mx');

  INSERT INTO portal.secciones_convocatoria
    (convocatoria_id, tipo_seccion, titulo, contenido, datos, orden, usuario_creacion)
  VALUES (v_id, 'CTA', '¡Aplica ahora!',
    'Envía tu CV a talentohumano.tenancingo@institutonevadi.edu.mx',
    '{"texto":"Enviar mi CV","url":"mailto:talentohumano.tenancingo@institutonevadi.edu.mx"}'::JSONB,
    5, 'admin@nevadi.edu.mx');
END $$;

-- ─────────────────────────────────────────────────────────────
-- B-8: Docente de Física — Preparatoria Tenancingo
-- ─────────────────────────────────────────────────────────────
DO $$
DECLARE v_id UUID;
BEGIN
  INSERT INTO portal.convocatorias (
    categoria, tipo, titulo, descripcion,
    plantel_id, nivel_educativo_id,
    fecha_inicio_postulacion, fecha_cierre_postulacion,
    cupo_maximo, is_published, usuario_creacion
  ) VALUES (
    'RECURSOS_HUMANOS', 'VACANTE_DOCENTE',
    'Docente de Física — Preparatoria Nevadi Tenancingo',
    'Instituto Nevadi busca Docente de Física para nivel preparatoria, plantel Tenancingo. Contratación por horas. Ciclo escolar 2026-2027.',
    '019e8f74-d143-7368-a0b6-06cc2fbc7156',
    '019e8f74-d13f-788e-8ed6-99c4825b22c8',
    '2026-06-15 08:00:00-06',
    '2026-07-20 20:00:00-06',
    1, TRUE, 'admin@nevadi.edu.mx'
  ) RETURNING id INTO v_id;

  INSERT INTO portal.secciones_convocatoria
    (convocatoria_id, tipo_seccion, titulo, contenido, orden, usuario_creacion)
  VALUES (v_id, 'INTRO', 'Estamos contratando — Docente de Física',
    'Buscamos profesionales comprometidos con la educación y la formación integral de nuestros estudiantes para impartir la asignatura de Física en nivel preparatoria.',
    0, 'admin@nevadi.edu.mx');

  INSERT INTO portal.secciones_convocatoria
    (convocatoria_id, tipo_seccion, titulo, datos, orden, usuario_creacion)
  VALUES (v_id, 'LISTA', 'Perfiles aceptados',
    '[
      "Ingeniería de cualquier especialidad",
      "Ciencias Naturales",
      "Educación Media en Física",
      "Educación Media en Física y Química"
    ]'::JSONB,
    1, 'admin@nevadi.edu.mx');

  INSERT INTO portal.secciones_convocatoria
    (convocatoria_id, tipo_seccion, titulo, datos, orden, usuario_creacion)
  VALUES (v_id, 'LISTA', 'Responsabilidades',
    '[
      "Impartir la asignatura de Física en nivel preparatoria",
      "Diseñar y desarrollar planeaciones didácticas alineadas al programa académico",
      "Fomentar el pensamiento científico, el análisis y la resolución de problemas",
      "Evaluar el aprendizaje y dar seguimiento al desempeño académico de los alumnos"
    ]'::JSONB,
    2, 'admin@nevadi.edu.mx');

  INSERT INTO portal.secciones_convocatoria
    (convocatoria_id, tipo_seccion, titulo, datos, orden, usuario_creacion)
  VALUES (v_id, 'LISTA', 'Ofrecemos',
    '[
      "Sueldo competitivo",
      "Prestaciones de Ley",
      "Capacitación continua",
      "Excelente ambiente laboral",
      "Contratación por horas — ciclo escolar 2026-2027"
    ]'::JSONB,
    3, 'admin@nevadi.edu.mx');

  INSERT INTO portal.secciones_convocatoria
    (convocatoria_id, tipo_seccion, titulo, contenido, datos, orden, usuario_creacion)
  VALUES (v_id, 'CTA', '¡Aplica ahora!',
    'Envía tu CV a talentohumano.tenancingo@institutonevadi.edu.mx',
    '{"texto":"Enviar mi CV","url":"mailto:talentohumano.tenancingo@institutonevadi.edu.mx"}'::JSONB,
    4, 'admin@nevadi.edu.mx');
END $$;

-- ─────────────────────────────────────────────────────────────
-- Verificación final
-- ─────────────────────────────────────────────────────────────
DO $$
DECLARE v_total INT;
BEGIN
  SELECT COUNT(*) INTO v_total FROM portal.convocatorias;
  RAISE NOTICE 'Total convocatorias en portal: % (esperado >= 16)', v_total;
END $$;

COMMIT;
