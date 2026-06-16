-- ============================================================
-- Migración 063 — Requisitos de documentos para convocatorias
-- Agrega requisitos faltantes:
--   • OFERTA_EDUCATIVA sin requisitos → documentos de admisión
--   • RECURSOS_HUMANOS sin requisitos → solo CV
-- ============================================================

BEGIN;

-- ── 1. ADMISIONES ABIERTAS GENERAL (INSCRIPCION, todos los planteles) ────────
-- ID: 33847079-1ebf-4bf9-b91e-9489e79b7b01

INSERT INTO portal.requisitos_documentos
    (id, convocatoria_id, nombre, descripcion, es_obligatorio,
     tipos_mime_permitidos, tamano_maximo_mb, orden)
VALUES
(gen_random_uuid(), '33847079-1ebf-4bf9-b91e-9489e79b7b01',
 'Acta de nacimiento',
 'Acta de nacimiento original o copia certificada del alumno',
 true, ARRAY['application/pdf','image/jpeg','image/png'], 5, 1),

(gen_random_uuid(), '33847079-1ebf-4bf9-b91e-9489e79b7b01',
 'CURP del alumno',
 'Clave Única de Registro de Población del solicitante',
 true, ARRAY['application/pdf','image/jpeg','image/png'], 2, 2),

(gen_random_uuid(), '33847079-1ebf-4bf9-b91e-9489e79b7b01',
 'INE del padre o tutor',
 'Identificación oficial vigente del padre, madre o tutor (ambas caras)',
 true, ARRAY['application/pdf','image/jpeg','image/png'], 5, 3),

(gen_random_uuid(), '33847079-1ebf-4bf9-b91e-9489e79b7b01',
 'Fotografía tamaño infantil',
 'Foto reciente a color, fondo blanco, sin lentes (JPG o PNG)',
 true, ARRAY['image/jpeg','image/png'], 3, 4),

(gen_random_uuid(), '33847079-1ebf-4bf9-b91e-9489e79b7b01',
 'Comprobante de domicilio',
 'Recibo de luz, agua o teléfono de los últimos 3 meses',
 true, ARRAY['application/pdf','image/jpeg','image/png'], 5, 5),

(gen_random_uuid(), '33847079-1ebf-4bf9-b91e-9489e79b7b01',
 'Comprobante de ingresos',
 'Recibo de nómina, estado de cuenta o constancia de ingresos del padre o tutor',
 true, ARRAY['application/pdf','image/jpeg','image/png'], 5, 6),

(gen_random_uuid(), '33847079-1ebf-4bf9-b91e-9489e79b7b01',
 'Carta de no antecedentes penales',
 'Del padre, madre o tutor. Expedida con no más de 6 meses de antigüedad',
 true, ARRAY['application/pdf','image/jpeg','image/png'], 5, 7),

(gen_random_uuid(), '33847079-1ebf-4bf9-b91e-9489e79b7b01',
 'Carta de motivos',
 'Texto escrito por el solicitante o padre/tutor explicando el interés en Instituto Nevadi',
 true, ARRAY['application/pdf'], 2, 8),

(gen_random_uuid(), '33847079-1ebf-4bf9-b91e-9489e79b7b01',
 '3 contactos de confianza',
 'Nombres, teléfonos y relación con el alumno de tres personas de confianza (PDF o imagen)',
 true, ARRAY['application/pdf','image/jpeg','image/png'], 3, 9),

(gen_random_uuid(), '33847079-1ebf-4bf9-b91e-9489e79b7b01',
 'Boletas de calificaciones',
 'Boletas del ciclo escolar anterior (todos los periodos)',
 true, ARRAY['application/pdf','image/jpeg','image/png'], 10, 10),

(gen_random_uuid(), '33847079-1ebf-4bf9-b91e-9489e79b7b01',
 'RFC del padre o tutor',
 'Constancia de situación fiscal o cédula de RFC',
 false, ARRAY['application/pdf','image/jpeg','image/png'], 3, 11);


-- ── 2. BECAS ACADÉMICAS TENANCINGO (BECA) ─────────────────────────────────────
-- ID: 74d2a5c5-064a-49a8-906d-b8e860c262af

INSERT INTO portal.requisitos_documentos
    (id, convocatoria_id, nombre, descripcion, es_obligatorio,
     tipos_mime_permitidos, tamano_maximo_mb, orden)
VALUES
(gen_random_uuid(), '74d2a5c5-064a-49a8-906d-b8e860c262af',
 'Acta de nacimiento',
 'Acta de nacimiento original o copia certificada del alumno',
 true, ARRAY['application/pdf','image/jpeg','image/png'], 5, 1),

(gen_random_uuid(), '74d2a5c5-064a-49a8-906d-b8e860c262af',
 'CURP del alumno',
 'Clave Única de Registro de Población del solicitante',
 true, ARRAY['application/pdf','image/jpeg','image/png'], 2, 2),

(gen_random_uuid(), '74d2a5c5-064a-49a8-906d-b8e860c262af',
 'INE del padre o tutor',
 'Identificación oficial vigente del padre, madre o tutor',
 true, ARRAY['application/pdf','image/jpeg','image/png'], 5, 3),

(gen_random_uuid(), '74d2a5c5-064a-49a8-906d-b8e860c262af',
 'Fotografía tamaño infantil',
 'Foto reciente a color, fondo blanco, sin lentes',
 true, ARRAY['image/jpeg','image/png'], 3, 4),

(gen_random_uuid(), '74d2a5c5-064a-49a8-906d-b8e860c262af',
 'Comprobante de domicilio',
 'Recibo de luz, agua o teléfono de los últimos 3 meses',
 true, ARRAY['application/pdf','image/jpeg','image/png'], 5, 5),

(gen_random_uuid(), '74d2a5c5-064a-49a8-906d-b8e860c262af',
 'Boletas de calificaciones',
 'Boletas del ciclo anterior con promedio mínimo de 8.5 para ser elegible a beca',
 true, ARRAY['application/pdf','image/jpeg','image/png'], 10, 6),

(gen_random_uuid(), '74d2a5c5-064a-49a8-906d-b8e860c262af',
 'Carta de motivos',
 'Por qué mereces la beca y qué compromiso tienes con tu formación académica',
 true, ARRAY['application/pdf'], 2, 7),

(gen_random_uuid(), '74d2a5c5-064a-49a8-906d-b8e860c262af',
 'Comprobante de ingresos familiares',
 'Recibo de nómina o constancia de ingresos del tutor económico',
 false, ARRAY['application/pdf','image/jpeg','image/png'], 5, 8);


-- ── 3. RRHH SIN REQUISITOS → SOLO CV ─────────────────────────────────────────
-- (8 convocatorias de tipo VACANTE_DOCENTE / VACANTE_ADMINISTRATIVA)

INSERT INTO portal.requisitos_documentos
    (id, convocatoria_id, nombre, descripcion, es_obligatorio,
     tipos_mime_permitidos, tamano_maximo_mb, orden)
SELECT
    gen_random_uuid(),
    c.id,
    'Currículum Vitae',
    'CV actualizado con fotografía, experiencia docente y referencias profesionales (PDF, máx. 5 MB)',
    true,
    ARRAY['application/pdf'],
    5,
    1
FROM portal.convocatorias c
WHERE c.categoria = 'RECURSOS_HUMANOS'
  AND NOT EXISTS (
      SELECT 1 FROM portal.requisitos_documentos r WHERE r.convocatoria_id = c.id
  );

COMMIT;
