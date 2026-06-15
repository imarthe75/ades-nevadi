-- ============================================================
-- 061 — Seed: Convocatorias de ejemplo Instituto Nevadi
-- Basadas en perfil institucional real (3 planteles, 3 niveles)
-- Incluye secciones LMS de ejemplo para mostrar capacidades
-- ============================================================

-- ────────────────────────────────────────────────────────────
-- 1. INSCRIPCIÓN PREPARATORIA TENANCINGO 2026-2027
-- ────────────────────────────────────────────────────────────
DO $$
DECLARE
    v_conv_id UUID;
BEGIN
    INSERT INTO portal.convocatorias (
        categoria, tipo, titulo, descripcion, plantel_id, nivel_educativo_id,
        fecha_inicio_postulacion, fecha_cierre_postulacion,
        cupo_maximo, imagen_url, is_published, usuario_creacion
    ) VALUES (
        'OFERTA_EDUCATIVA'::portal.categoria_convocatoria,
        'INSCRIPCION'::portal.tipo_convocatoria,
        'Convocatoria de Inscripción — Preparatoria Nevadi Tenancingo, Ciclo 2026-2027',
        'Te invitamos a formar parte de nuestra Preparatoria incorporada a la UAEMEX. Más de 15 años formando bachilleres con valores.',
        '019e8f74-d143-7368-a0b6-06cc2fbc7156',
        '019e8f74-d13f-788e-8ed6-99c4825b22c8',
        '2026-06-10 08:00:00-06',
        '2026-07-31 20:00:00-06',
        120,
        NULL,
        TRUE,
        'admin@nevadi.edu.mx'
    ) RETURNING id INTO v_conv_id;

    -- Secciones LMS
    INSERT INTO portal.secciones_convocatoria (convocatoria_id, tipo_seccion, titulo, contenido, orden, usuario_creacion)
    VALUES (v_conv_id, 'INTRO', '¡Inscríbete al Bachillerato Nevadi Generación 2026!',
        'Prepara tu futuro en una institución con más de 15 años de experiencia formando bachilleres con valores, conocimiento y visión. Incorporada oficialmente a la UAEMEX.',
        0, 'admin@nevadi.edu.mx');

    INSERT INTO portal.secciones_convocatoria (convocatoria_id, tipo_seccion, titulo, datos, orden, usuario_creacion)
    VALUES (v_conv_id, 'LISTA', '¿Por qué elegir Nevadi?',
        '[
          "Plan de estudios CBU 2024 UAEMEX — certificado con validez oficial",
          "Laboratorio de cómputo con internet, laboratorio de ciencias y biblioteca",
          "Actividades deportivas, culturales y talleres extracurriculares",
          "Orientación vocacional y preparación para examen de admisión universitario",
          "Ambiente seguro, incluyente y libre de violencia",
          "Docentes certificados con experiencia profesional"
        ]'::JSONB,
        1, 'admin@nevadi.edu.mx');

    INSERT INTO portal.secciones_convocatoria (convocatoria_id, tipo_seccion, titulo, datos, orden, usuario_creacion)
    VALUES (v_conv_id, 'PROCESO', 'Proceso de Inscripción',
        '[
          {"num":1,"titulo":"Solicita tu ficha","desc":"Descarga o recoge tu ficha de inscripción en la administración escolar o en este portal."},
          {"num":2,"titulo":"Entrega documentos","desc":"Reúne y entrega tu documentación completa en ventanilla de 8:00 a 14:00 hrs de lunes a viernes."},
          {"num":3,"titulo":"Realiza tu pago","desc":"Paga la inscripción en caja o mediante transferencia bancaria. Conserva tu comprobante."},
          {"num":4,"titulo":"Confirmación","desc":"Recibirás un correo de confirmación con tu número de matrícula y horario asignado."}
        ]'::JSONB,
        2, 'admin@nevadi.edu.mx');

    INSERT INTO portal.secciones_convocatoria (convocatoria_id, tipo_seccion, titulo, datos, orden, usuario_creacion)
    VALUES (v_conv_id, 'FAQ', 'Preguntas Frecuentes',
        '[
          {"pregunta":"¿Necesito examen de admisión?","respuesta":"Sí, se aplica un examen de conocimientos básicos de Matemáticas y Español. La fecha se notifica al entregar documentos."},
          {"pregunta":"¿Qué pasa si no encuentro mi acta de nacimiento?","respuesta":"Puedes presentar una constancia del Registro Civil mientras tramitas el original. Tienes 30 días hábiles para regularizarla."},
          {"pregunta":"¿Tienen servicio de transporte?","respuesta":"Contamos con convenios con rutas de transporte desde los principales municipios del sur del Estado de México."},
          {"pregunta":"¿Cuál es el costo de la inscripción?","respuesta":"El costo y la forma de pago se informan directamente en la administración escolar, ya que puede variar según el período."}
        ]'::JSONB,
        3, 'admin@nevadi.edu.mx');

    INSERT INTO portal.secciones_convocatoria (convocatoria_id, tipo_seccion, titulo, contenido, datos, orden, usuario_creacion)
    VALUES (v_conv_id, 'AVISO', 'Cupos Limitados', 'Solo se aceptan 120 alumnos. Los lugares se asignan por estricto orden de entrega de documentación completa.',
        '{"nivel":"warn","icono":"pi-exclamation-triangle"}'::JSONB,
        4, 'admin@nevadi.edu.mx');

    INSERT INTO portal.secciones_convocatoria (convocatoria_id, tipo_seccion, titulo, contenido, datos, orden, usuario_creacion)
    VALUES (v_conv_id, 'CTA', '¿Listo para postular?', 'Completa tu solicitud en línea y da el primer paso hacia tu bachillerato.',
        '{"texto":"Iniciar mi postulación","url":"#postular"}'::JSONB,
        5, 'admin@nevadi.edu.mx');

    -- Requisitos documentales
    INSERT INTO portal.requisitos_documentos (convocatoria_id, nombre, descripcion, es_obligatorio, tipos_mime_permitidos, tamano_maximo_mb, orden, usuario_creacion)
    VALUES
        (v_conv_id, 'Acta de Nacimiento', 'Original y copia, vigente o con apostilla si aplica', TRUE, '{application/pdf,image/jpeg,image/png}', 5, 1, 'admin@nevadi.edu.mx'),
        (v_conv_id, 'CURP', 'Impresión oficial del portal de la SEP', TRUE, '{application/pdf,image/jpeg,image/png}', 2, 2, 'admin@nevadi.edu.mx'),
        (v_conv_id, 'Certificado de Secundaria', 'Certificado oficial o constancia de término con promedio', TRUE, '{application/pdf}', 10, 3, 'admin@nevadi.edu.mx'),
        (v_conv_id, 'Foto tamaño infantil', '6 fotografías recientes, fondo blanco, sin lentes', TRUE, '{image/jpeg,image/png}', 3, 4, 'admin@nevadi.edu.mx'),
        (v_conv_id, 'Comprobante de domicilio', 'No mayor a 3 meses de antigüedad', FALSE, '{application/pdf,image/jpeg,image/png}', 5, 5, 'admin@nevadi.edu.mx');

END $$;

-- ────────────────────────────────────────────────────────────
-- 2. REINSCRIPCIÓN SECUNDARIA METEPEC 2026-2027
-- ────────────────────────────────────────────────────────────
DO $$
DECLARE
    v_conv_id UUID;
BEGIN
    INSERT INTO portal.convocatorias (
        categoria, tipo, titulo, descripcion, plantel_id, nivel_educativo_id,
        fecha_inicio_postulacion, fecha_cierre_postulacion,
        cupo_maximo, is_published, usuario_creacion
    ) VALUES (
        'OFERTA_EDUCATIVA'::portal.categoria_convocatoria,
        'REINSCRIPCION'::portal.tipo_convocatoria,
        'Reinscripción Secundaria Nevadi Educa — Metepec, Ciclo 2026-2027',
        'Alumnos de 1.° y 2.° de secundaria: realiza tu reinscripción en línea para el ciclo 2026-2027 sin necesidad de acudir a la escuela.',
        '019e8f74-d142-7c91-8b82-c84464113dad',
        '019e8f74-d13f-77e5-aeb8-e859b106072c',
        '2026-06-15 08:00:00-06',
        '2026-07-25 20:00:00-06',
        200,
        TRUE,
        'admin@nevadi.edu.mx'
    ) RETURNING id INTO v_conv_id;

    INSERT INTO portal.secciones_convocatoria (convocatoria_id, tipo_seccion, titulo, contenido, orden, usuario_creacion)
    VALUES (v_conv_id, 'INTRO', 'Reinscripción en línea: fácil, rápido y sin filas',
        'Este año habilitamos el proceso completo de reinscripción a través del portal institucional. Solo necesitas tu matrícula y los documentos solicitados en formato digital.',
        0, 'admin@nevadi.edu.mx');

    INSERT INTO portal.secciones_convocatoria (convocatoria_id, tipo_seccion, titulo, datos, orden, usuario_creacion)
    VALUES (v_conv_id, 'PROCESO', 'Pasos para reinscribirte',
        '[
          {"num":1,"titulo":"Ingresa con tu matrícula","desc":"Usa el correo registrado en la escuela o tu número de matrícula para iniciar sesión."},
          {"num":2,"titulo":"Verifica tus datos","desc":"Confirma que tu información personal y de contacto esté actualizada."},
          {"num":3,"titulo":"Sube documentos actualizados","desc":"Sube comprobante de domicilio vigente y foto actualizada si cambiaron desde el ciclo anterior."},
          {"num":4,"titulo":"Paga en línea o en caja","desc":"Realiza el pago de reinscripción. Recibirás confirmación inmediata por correo."}
        ]'::JSONB,
        1, 'admin@nevadi.edu.mx');

    INSERT INTO portal.secciones_convocatoria (convocatoria_id, tipo_seccion, titulo, datos, orden, usuario_creacion)
    VALUES (v_conv_id, 'FAQ', 'Dudas Frecuentes',
        '[
          {"pregunta":"¿Puedo reinscribirme aunque tenga adeudos?","respuesta":"Para completar la reinscripción es necesario estar al corriente en pagos. Comunícate con administración para revisar tu situación."},
          {"pregunta":"¿Qué pasa si reprobé materias?","respuesta":"Puedes reinscribirte aunque tengas materias pendientes. El departamento académico te asignará asesoría para regularización."},
          {"pregunta":"¿Cambia mi grupo o horario?","respuesta":"Los grupos para el siguiente ciclo se asignan en la primera semana de agosto. Te notificamos por correo."}
        ]'::JSONB,
        2, 'admin@nevadi.edu.mx');

    INSERT INTO portal.requisitos_documentos (convocatoria_id, nombre, descripcion, es_obligatorio, tipos_mime_permitidos, tamano_maximo_mb, orden, usuario_creacion)
    VALUES
        (v_conv_id, 'Comprobante de domicilio actualizado', 'Si cambió tu domicilio desde el ciclo anterior', FALSE, '{application/pdf,image/jpeg}', 5, 1, 'admin@nevadi.edu.mx'),
        (v_conv_id, 'Foto actualizada', 'Solo si cambió desde tu última foto en expediente', FALSE, '{image/jpeg,image/png}', 3, 2, 'admin@nevadi.edu.mx');
END $$;

-- ────────────────────────────────────────────────────────────
-- 3. VACANTE DOCENTE — MATEMÁTICAS/FÍSICA (PREPARATORIA)
-- ────────────────────────────────────────────────────────────
DO $$
DECLARE
    v_conv_id UUID;
BEGIN
    INSERT INTO portal.convocatorias (
        categoria, tipo, titulo, descripcion, plantel_id, nivel_educativo_id,
        fecha_inicio_postulacion, fecha_cierre_postulacion,
        cupo_maximo, is_published, usuario_creacion
    ) VALUES (
        'RECURSOS_HUMANOS'::portal.categoria_convocatoria,
        'VACANTE_DOCENTE'::portal.tipo_convocatoria,
        'Vacante Docente — Matemáticas y Física, Preparatoria Nevadi Tenancingo',
        'Buscamos docente de Matemáticas y/o Física con perfil UAEMEX para el ciclo escolar 2026-2027 en el plantel Tenancingo.',
        '019e8f74-d143-7368-a0b6-06cc2fbc7156',
        '019e8f74-d13f-788e-8ed6-99c4825b22c8',
        '2026-06-01 08:00:00-06',
        '2026-07-15 20:00:00-06',
        2,
        TRUE,
        'rrhh@nevadi.edu.mx'
    ) RETURNING id INTO v_conv_id;

    INSERT INTO portal.secciones_convocatoria (convocatoria_id, tipo_seccion, titulo, contenido, orden, usuario_creacion)
    VALUES (v_conv_id, 'INTRO', 'Únete al equipo docente Nevadi',
        'Instituto Nevadi invita a profesionales apasionados por la enseñanza a integrarse como docente de Matemáticas y/o Física en nuestra Preparatoria incorporada a la UAEMEX, plantel Tenancingo.',
        0, 'rrhh@nevadi.edu.mx');

    INSERT INTO portal.secciones_convocatoria (convocatoria_id, tipo_seccion, titulo, datos, orden, usuario_creacion)
    VALUES (v_conv_id, 'LISTA', 'Perfil solicitado',
        '[
          "Licenciatura o ingeniería en área de Matemáticas, Física, Actuaría o afín (título o pasante en proceso)",
          "Experiencia docente mínima de 1 año en bachillerato o superior (deseable)",
          "Conocimiento del plan de estudios CBU 2024 UAEMEX (deseable)",
          "Habilidades de comunicación, empatía y trabajo en equipo",
          "Disponibilidad para horario matutino (7:00 - 14:00 hrs)",
          "Residencia en Tenancingo, Coatepec Harinas, Ixtapan de la Sal o zonas aledañas"
        ]'::JSONB,
        1, 'rrhh@nevadi.edu.mx');

    INSERT INTO portal.secciones_convocatoria (convocatoria_id, tipo_seccion, titulo, datos, orden, usuario_creacion)
    VALUES (v_conv_id, 'LISTA', 'Ofrecemos',
        '[
          "Contratación directa con prestaciones de ley",
          "Pago quincenal puntual",
          "Capacitación continua y acompañamiento pedagógico",
          "Ambiente de trabajo colaborativo y respetuoso",
          "Posibilidad de carga horaria completa según perfil"
        ]'::JSONB,
        2, 'rrhh@nevadi.edu.mx');

    INSERT INTO portal.secciones_convocatoria (convocatoria_id, tipo_seccion, titulo, datos, orden, usuario_creacion)
    VALUES (v_conv_id, 'PROCESO', 'Proceso de selección',
        '[
          {"num":1,"titulo":"Postulación en línea","desc":"Completa tu solicitud y sube CV actualizado, título o constancia de estudios."},
          {"num":2,"titulo":"Revisión de documentos","desc":"El equipo de RRHH revisará tu perfil en un plazo de 5 días hábiles."},
          {"num":3,"titulo":"Entrevista inicial","desc":"Los candidatos preseleccionados serán contactados para una entrevista por videollamada."},
          {"num":4,"titulo":"Clase muestra","desc":"Finalistas presentarán una clase demostrativa de 20 minutos ante el coordinador académico."},
          {"num":5,"titulo":"Contratación","desc":"El candidato seleccionado firmará contrato e iniciará inducción institucional."}
        ]'::JSONB,
        3, 'rrhh@nevadi.edu.mx');

    INSERT INTO portal.secciones_convocatoria (convocatoria_id, tipo_seccion, titulo, contenido, datos, orden, usuario_creacion)
    VALUES (v_conv_id, 'AVISO', 'Importante', 'Solo se contactará a candidatos preseleccionados. La institución se reserva el derecho de declarar desierta la vacante.',
        '{"nivel":"info","icono":"pi-info-circle"}'::JSONB,
        4, 'rrhh@nevadi.edu.mx');

    INSERT INTO portal.requisitos_documentos (convocatoria_id, nombre, descripcion, es_obligatorio, tipos_mime_permitidos, tamano_maximo_mb, orden, usuario_creacion)
    VALUES
        (v_conv_id, 'Currículum Vitae', 'CV actualizado con fotografía', TRUE, '{application/pdf}', 5, 1, 'rrhh@nevadi.edu.mx'),
        (v_conv_id, 'Título o constancia', 'Título profesional o constancia de titulación en trámite', TRUE, '{application/pdf,image/jpeg,image/png}', 10, 2, 'rrhh@nevadi.edu.mx'),
        (v_conv_id, 'Identificación oficial', 'INE o pasaporte vigente', TRUE, '{application/pdf,image/jpeg,image/png}', 5, 3, 'rrhh@nevadi.edu.mx'),
        (v_conv_id, 'CURP', 'Impresión oficial', TRUE, '{application/pdf,image/jpeg}', 2, 4, 'rrhh@nevadi.edu.mx'),
        (v_conv_id, 'Constancia de experiencia', 'Carta de recomendación o constancia de empleos anteriores', FALSE, '{application/pdf}', 5, 5, 'rrhh@nevadi.edu.mx');
END $$;

-- ────────────────────────────────────────────────────────────
-- 4. VACANTE ADMINISTRATIVA — AUXILIAR DE CONTROL ESCOLAR
-- ────────────────────────────────────────────────────────────
DO $$
DECLARE
    v_conv_id UUID;
BEGIN
    INSERT INTO portal.convocatorias (
        categoria, tipo, titulo, descripcion, plantel_id,
        fecha_inicio_postulacion, fecha_cierre_postulacion,
        cupo_maximo, is_published, usuario_creacion
    ) VALUES (
        'RECURSOS_HUMANOS'::portal.categoria_convocatoria,
        'VACANTE_ADMINISTRATIVA'::portal.tipo_convocatoria,
        'Vacante — Auxiliar de Control Escolar, Plantel Ixtapan de la Sal',
        'Buscamos persona proactiva para apoyo en gestión de expedientes, atención a padres de familia y procesos administrativos escolares.',
        '019e8f74-d143-740c-aa16-63a83c575d92',
        '2026-06-05 08:00:00-06',
        '2026-07-10 20:00:00-06',
        1,
        TRUE,
        'rrhh@nevadi.edu.mx'
    ) RETURNING id INTO v_conv_id;

    INSERT INTO portal.secciones_convocatoria (convocatoria_id, tipo_seccion, titulo, contenido, orden, usuario_creacion)
    VALUES (v_conv_id, 'INTRO', 'Auxiliar de Control Escolar — Plantel Ixtapan de la Sal',
        'Buscamos a una persona organizada, con vocación de servicio y habilidades administrativas para integrarse al equipo de Control Escolar en nuestro plantel de Ixtapan de la Sal.',
        0, 'rrhh@nevadi.edu.mx');

    INSERT INTO portal.secciones_convocatoria (convocatoria_id, tipo_seccion, titulo, datos, orden, usuario_creacion)
    VALUES (v_conv_id, 'ENCABEZADO', 'Actividades principales', NULL, 1, 'rrhh@nevadi.edu.mx');

    INSERT INTO portal.secciones_convocatoria (convocatoria_id, tipo_seccion, titulo, datos, orden, usuario_creacion)
    VALUES (v_conv_id, 'LISTA', NULL,
        '[
          "Gestión y archivo de expedientes de alumnos (físico y digital)",
          "Atención a padres de familia y alumnos en ventanilla",
          "Apoyo en procesos de inscripción, reinscripción y bajas",
          "Elaboración de documentos oficiales (constancias, credenciales, etc.)",
          "Registro y seguimiento en sistema escolar institucional (ADES)",
          "Coordinación con dirección académica para reportes de asistencia y calificaciones"
        ]'::JSONB,
        2, 'rrhh@nevadi.edu.mx');

    INSERT INTO portal.secciones_convocatoria (convocatoria_id, tipo_seccion, titulo, datos, orden, usuario_creacion)
    VALUES (v_conv_id, 'LISTA', 'Requisitos',
        '[
          "Licenciatura o TSU en Administración, Contabilidad, Pedagogía o área afín (pasante aceptado)",
          "Manejo de paquetería Office (Word, Excel) — nivel intermedio mínimo",
          "Experiencia en atención al cliente o área administrativa (1 año deseable)",
          "Buena ortografía y redacción",
          "Discreción y manejo de información confidencial",
          "Residencia en Ixtapan de la Sal o municipios cercanos"
        ]'::JSONB,
        3, 'rrhh@nevadi.edu.mx');

    INSERT INTO portal.requisitos_documentos (convocatoria_id, nombre, descripcion, es_obligatorio, tipos_mime_permitidos, tamano_maximo_mb, orden, usuario_creacion)
    VALUES
        (v_conv_id, 'CV actualizado', 'Máximo 2 páginas, formato PDF', TRUE, '{application/pdf}', 5, 1, 'rrhh@nevadi.edu.mx'),
        (v_conv_id, 'Comprobante de estudios', 'Título, carta de pasante o historial académico', TRUE, '{application/pdf,image/jpeg}', 10, 2, 'rrhh@nevadi.edu.mx'),
        (v_conv_id, 'Identificación oficial', 'INE vigente', TRUE, '{application/pdf,image/jpeg,image/png}', 5, 3, 'rrhh@nevadi.edu.mx');
END $$;

-- ────────────────────────────────────────────────────────────
-- 5. BECA DE EXCELENCIA ACADÉMICA — TODOS LOS PLANTELES
-- ────────────────────────────────────────────────────────────
DO $$
DECLARE
    v_conv_id UUID;
BEGIN
    INSERT INTO portal.convocatorias (
        categoria, tipo, titulo, descripcion,
        fecha_inicio_postulacion, fecha_cierre_postulacion,
        cupo_maximo, is_published, aviso_privacidad_version, usuario_creacion
    ) VALUES (
        'OFERTA_EDUCATIVA'::portal.categoria_convocatoria,
        'BECA'::portal.tipo_convocatoria,
        'Beca de Excelencia Académica Nevadi — Ciclo 2026-2027',
        'Reconocemos a nuestros alumnos destacados con becas parciales y totales de colegiatura para el ciclo 2026-2027.',
        '2026-06-01 08:00:00-06',
        '2026-07-20 20:00:00-06',
        30,
        TRUE,
        '2.0',
        'admin@nevadi.edu.mx'
    ) RETURNING id INTO v_conv_id;

    INSERT INTO portal.secciones_convocatoria (convocatoria_id, tipo_seccion, titulo, contenido, orden, usuario_creacion)
    VALUES (v_conv_id, 'INTRO', 'Beca de Excelencia Nevadi 2026-2027',
        'Instituto Nevadi premia el esfuerzo y la dedicación. Si eres alumno con promedio sobresaliente o alumno nuevo con calificaciones excepcionales en secundaria, esta beca es para ti.',
        0, 'admin@nevadi.edu.mx');

    INSERT INTO portal.secciones_convocatoria (convocatoria_id, tipo_seccion, titulo, datos, orden, usuario_creacion)
    VALUES (v_conv_id, 'LISTA', 'Tipos de beca disponibles',
        '[
          "Beca Total (100%): Para promedios de 9.5 a 10 en el ciclo anterior — Aplica para alumnos actuales",
          "Beca Parcial 50%: Para promedios de 9.0 a 9.4 en el ciclo anterior",
          "Beca de Ingreso: Para alumnos nuevos con promedio de secundaria de 9.5 o superior",
          "Beca por Vulnerabilidad: Para casos de necesidad económica comprobada — independientemente del promedio"
        ]'::JSONB,
        1, 'admin@nevadi.edu.mx');

    INSERT INTO portal.secciones_convocatoria (convocatoria_id, tipo_seccion, titulo, datos, orden, usuario_creacion)
    VALUES (v_conv_id, 'PROCESO', 'Cómo solicitar tu beca',
        '[
          {"num":1,"titulo":"Postula en línea","desc":"Completa el formulario de solicitud y adjunta los documentos requeridos."},
          {"num":2,"titulo":"Evaluación","desc":"El comité de becas revisará tu expediente en un plazo de 10 días hábiles."},
          {"num":3,"titulo":"Entrevista (si aplica)","desc":"Para becas por vulnerabilidad se realizará una entrevista con el área de orientación."},
          {"num":4,"titulo":"Notificación","desc":"Recibirás por correo el resultado y, si fue aprobada, las condiciones de mantenimiento de la beca."}
        ]'::JSONB,
        2, 'admin@nevadi.edu.mx');

    INSERT INTO portal.secciones_convocatoria (convocatoria_id, tipo_seccion, titulo, datos, orden, usuario_creacion)
    VALUES (v_conv_id, 'FAQ', 'Preguntas sobre becas',
        '[
          {"pregunta":"¿La beca se renueva automáticamente?","respuesta":"No. Debes solicitar renovación al inicio de cada ciclo escolar. El promedio mínimo para mantenimiento es 8.5."},
          {"pregunta":"¿Puedo solicitar beca aunque sea alumno de nuevo ingreso?","respuesta":"Sí. La beca de ingreso está disponible para alumnos nuevos que demuestren su promedio de secundaria."},
          {"pregunta":"¿La beca cubre materiales o solo colegiatura?","respuesta":"Las becas aplican únicamente a colegiatura mensual. Los materiales y actividades extracurriculares no están incluidos."},
          {"pregunta":"¿Qué pasa si bajo mi promedio durante el ciclo?","respuesta":"Se realiza una revisión semestral. Si el promedio baja del mínimo acordado, la beca puede reducirse o cancelarse."}
        ]'::JSONB,
        3, 'admin@nevadi.edu.mx');

    INSERT INTO portal.secciones_convocatoria (convocatoria_id, tipo_seccion, titulo, contenido, datos, orden, usuario_creacion)
    VALUES (v_conv_id, 'CTA', '¿Cumples los requisitos?', 'Solicita tu beca ahora. Los cupos son limitados y se asignan por orden de solicitud y mérito.',
        '{"texto":"Solicitar mi beca","url":"#postular"}'::JSONB,
        4, 'admin@nevadi.edu.mx');

    INSERT INTO portal.requisitos_documentos (convocatoria_id, nombre, descripcion, es_obligatorio, tipos_mime_permitidos, tamano_maximo_mb, orden, usuario_creacion)
    VALUES
        (v_conv_id, 'Boleta o historial académico', 'Del ciclo anterior o de secundaria para alumnos nuevos', TRUE, '{application/pdf,image/jpeg,image/png}', 10, 1, 'admin@nevadi.edu.mx'),
        (v_conv_id, 'Carta de motivos', 'Escrito de máximo 1 página explicando por qué solicitas la beca', TRUE, '{application/pdf}', 5, 2, 'admin@nevadi.edu.mx'),
        (v_conv_id, 'Comprobante de ingresos familiar', 'Para becas por vulnerabilidad — últimos 3 meses', FALSE, '{application/pdf,image/jpeg}', 10, 3, 'admin@nevadi.edu.mx'),
        (v_conv_id, 'CURP del alumno', NULL, TRUE, '{application/pdf,image/jpeg}', 2, 4, 'admin@nevadi.edu.mx');
END $$;

-- ────────────────────────────────────────────────────────────
-- 6. VACANTE DOCENTE — ESPAÑOL/LITERATURA (SECUNDARIA METEPEC)
-- ────────────────────────────────────────────────────────────
DO $$
DECLARE
    v_conv_id UUID;
BEGIN
    INSERT INTO portal.convocatorias (
        categoria, tipo, titulo, descripcion, plantel_id, nivel_educativo_id,
        fecha_inicio_postulacion, fecha_cierre_postulacion,
        cupo_maximo, is_published, usuario_creacion
    ) VALUES (
        'RECURSOS_HUMANOS'::portal.categoria_convocatoria,
        'VACANTE_DOCENTE'::portal.tipo_convocatoria,
        'Docente de Español y Literatura — Secundaria Nevadi Educa, Metepec',
        'Se solicita docente de Español y/o Literatura para nivel secundaria. Ambiente colaborativo, horario matutino, plantel Metepec.',
        '019e8f74-d142-7c91-8b82-c84464113dad',
        '019e8f74-d13f-77e5-aeb8-e859b106072c',
        '2026-06-08 08:00:00-06',
        '2026-07-18 20:00:00-06',
        1,
        TRUE,
        'rrhh@nevadi.edu.mx'
    ) RETURNING id INTO v_conv_id;

    INSERT INTO portal.secciones_convocatoria (convocatoria_id, tipo_seccion, titulo, contenido, orden, usuario_creacion)
    VALUES (v_conv_id, 'INTRO', 'Docente de Español para Secundaria Nevadi Educa',
        'Buscamos a una persona con vocación docente, dominio del español y amor por la lectura, para impartir Español y Literatura a estudiantes de secundaria en nuestro plantel Metepec.',
        0, 'rrhh@nevadi.edu.mx');

    INSERT INTO portal.secciones_convocatoria (convocatoria_id, tipo_seccion, titulo, datos, orden, usuario_creacion)
    VALUES (v_conv_id, 'LISTA', 'Lo que buscamos',
        '[
          "Licenciatura en Letras Hispánicas, Educación, Pedagogía o área afín",
          "Experiencia frente a grupo en nivel secundaria (mínimo 1 año)",
          "Conocimiento del plan de estudios NEM (Nueva Escuela Mexicana)",
          "Capacidad para diseñar actividades dinámicas y motivadoras",
          "Manejo básico de tecnología educativa (Google Classroom, plataformas LMS)",
          "Puntualidad, responsabilidad y trabajo en equipo"
        ]'::JSONB,
        1, 'rrhh@nevadi.edu.mx');

    INSERT INTO portal.requisitos_documentos (convocatoria_id, nombre, descripcion, es_obligatorio, tipos_mime_permitidos, tamano_maximo_mb, orden, usuario_creacion)
    VALUES
        (v_conv_id, 'Currículum Vitae', 'CV con fotografía y referencias', TRUE, '{application/pdf}', 5, 1, 'rrhh@nevadi.edu.mx'),
        (v_conv_id, 'Cédula o título profesional', 'Copia de cédula o constancia de trámite', TRUE, '{application/pdf,image/jpeg}', 10, 2, 'rrhh@nevadi.edu.mx'),
        (v_conv_id, 'INE vigente', NULL, TRUE, '{image/jpeg,image/png,application/pdf}', 5, 3, 'rrhh@nevadi.edu.mx');
END $$;

-- Verificar inserciones
SELECT categoria, tipo, titulo, is_published,
       (SELECT COUNT(*) FROM portal.secciones_convocatoria s WHERE s.convocatoria_id = c.id AND s.is_active=TRUE) AS num_secciones,
       (SELECT COUNT(*) FROM portal.requisitos_documentos r WHERE r.convocatoria_id = c.id AND r.is_active=TRUE) AS num_requisitos
FROM portal.convocatorias c
WHERE c.is_active = TRUE
ORDER BY c.fecha_creacion DESC
LIMIT 10;
