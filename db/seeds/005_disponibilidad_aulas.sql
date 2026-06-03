-- =============================================================================
-- ADES — Seed 005: Disponibilidad Docente (aSc TimeTables) + Direcciones
-- =============================================================================
BEGIN;

-- =============================================================================
-- A. MUNICIPIOS Y LOCALIDADES para las direcciones de planteles
-- Estado de México — municipios donde están los planteles
-- =============================================================================
INSERT INTO ades_municipios (clave_municipio, nombre_municipio, estado_id)
SELECT m.clave, m.nombre, e.id
FROM ades_estados e,
(VALUES
  ('106', 'Metepec'),
  ('099', 'Tenancingo'),
  ('057', 'Ixtapan de la Sal')
) AS m(clave, nombre)
WHERE e.clave_estado = '15'
ON CONFLICT (clave_municipio, estado_id) DO NOTHING;

-- Tipos de asentamiento
INSERT INTO ades_tipos_asentamiento (clave_tipo, nombre_tipo) VALUES
  ('09', 'Colonia'),
  ('21', 'Pueblo'),
  ('17', 'Fraccionamiento'),
  ('28', 'Unidad habitacional')
ON CONFLICT (clave_tipo) DO NOTHING;

-- Localidades
INSERT INTO ades_localidades (nombre_localidad, municipio_id, tipo_asentamiento_id)
SELECT l.nombre, m.id, ta.id
FROM ades_municipios m,
ades_tipos_asentamiento ta,
(VALUES
  ('Metepec',       'Metepec',          '21'),
  ('Tenancingo',    'Tenancingo',       '21'),
  ('Ixtapan de la Sal', 'Ixtapan de la Sal', '21')
) AS l(nombre, municipio_nombre, tipo)
WHERE m.nombre_municipio = l.municipio_nombre
  AND ta.clave_tipo = l.tipo
ON CONFLICT DO NOTHING;

-- Códigos postales aproximados
INSERT INTO ades_codigos_postales
  (codigo_postal, localidad_id, municipio_id, estado_id)
SELECT cp.codigo, loc.id, mun.id, est.id
FROM ades_estados est,
(VALUES
  ('52140', 'Metepec',          'Metepec'),
  ('52400', 'Tenancingo',       'Tenancingo'),
  ('51900', 'Ixtapan de la Sal','Ixtapan de la Sal')
) AS cp(codigo, localidad_nombre, municipio_nombre)
JOIN ades_municipios mun ON mun.nombre_municipio = cp.municipio_nombre
JOIN ades_localidades loc ON loc.nombre_localidad = cp.localidad_nombre
  AND loc.municipio_id = mun.id
WHERE est.clave_estado = '15'
ON CONFLICT DO NOTHING;

-- Direcciones de planteles
INSERT INTO ades_direcciones
  (calle, numero_exterior, entidad_tipo, entidad_id,
   localidad_id, codigo_postal_id)
SELECT
  d.calle, d.num_ext, 'PLANTEL', pl.id, loc.id, cp.id
FROM ades_planteles pl,
(VALUES
  ('Metepec',          'Prolongación Heriberto Enríquez', '1001', 'Metepec'),
  ('Tenancingo',       'Carretera Tenancingo-Tenería',    'S/N',  'Tenancingo'),
  ('Ixtapan de la Sal','Independencia Pte.',              '5',    'Ixtapan de la Sal')
) AS d(plantel_nombre, calle, num_ext, localidad_nombre)
JOIN ades_localidades loc ON loc.nombre_localidad = d.localidad_nombre
JOIN ades_codigos_postales cp ON cp.localidad_id = loc.id
WHERE pl.nombre_plantel = d.plantel_nombre
ON CONFLICT DO NOTHING;

-- =============================================================================
-- B. DISPONIBILIDAD DOCENTE PARA aSc TimeTables
-- Horario escolar estándar: Lunes a Viernes 07:00 - 15:00
-- Todos los profesores disponibles en horario completo por defecto
-- Los CTE (viernes 1 por mes) se manejan en el calendario, no aquí
-- =============================================================================

-- Disponibilidad general: Lun-Vie 07:00-15:00 para todos los profesores
INSERT INTO ades_disponibilidad_docente
  (profesor_id, dia_semana, hora_inicio, hora_fin, disponible, ciclo_escolar_id)
SELECT
  prof.id,
  dia.num,
  '07:00'::TIME,
  '15:00'::TIME,
  TRUE,
  ce.id
FROM ades_profesores prof
CROSS JOIN (VALUES (1),(2),(3),(4),(5)) AS dia(num)
CROSS JOIN ades_ciclos_escolares ce
JOIN ades_grados gr ON gr.plantel_id = prof.plantel_id
JOIN ades_niveles_educativos ne ON ne.id = gr.nivel_educativo_id
WHERE ce.nivel_educativo_id = ne.id
  AND ce.es_vigente = TRUE
  -- Evitar duplicados por múltiples grados del mismo plantel
GROUP BY prof.id, dia.num, ce.id
ON CONFLICT DO NOTHING;

-- =============================================================================
-- C. AULAS ADICIONALES (laboratorios específicos por plantel)
-- Las aulas básicas ya se insertaron en el seed 001
-- =============================================================================
INSERT INTO ades_aulas (nombre_aula, plantel_id, tipo_aula, capacidad)
SELECT a.nombre, pl.id, a.tipo, a.capacidad
FROM ades_planteles pl,
(VALUES
  ('Laboratorio Ciencias',  'LABORATORIO', 28),
  ('Biblioteca',            'AULA',        40),
  ('Sala de Maestros',      'AULA',        20)
) AS a(nombre, tipo, capacidad)
ON CONFLICT (nombre_aula, plantel_id) DO NOTHING;

-- =============================================================================
-- D. INFORMACIÓN ADICIONAL DE ESCUELA
-- =============================================================================
-- Actualizar logo/slogan con FK correcta
UPDATE ades_escuelas e
SET
  identidad_institucional_logo_id = ii_logo.id,
  identidad_institucional_slogan_id = ii_slogan.id
FROM ades_identidad_institucional ii_logo,
     ades_identidad_institucional ii_slogan
WHERE ii_logo.tipo_elemento   = 'LOGO'
  AND ii_slogan.tipo_elemento = 'SLOGAN'
  AND e.nombre_escuela = 'Instituto Nevadi';

COMMIT;

DO $$
DECLARE v_dir INT; v_disp INT; v_aulas INT;
BEGIN
  SELECT COUNT(*) INTO v_dir  FROM ades_direcciones;
  SELECT COUNT(*) INTO v_disp FROM ades_disponibilidad_docente;
  SELECT COUNT(*) INTO v_aulas FROM ades_aulas;
  RAISE NOTICE '=== SEED 005 ===';
  RAISE NOTICE 'Direcciones:    %', v_dir;
  RAISE NOTICE 'Disponibilidad: %', v_disp;
  RAISE NOTICE 'Aulas total:    %', v_aulas;
END $$;
