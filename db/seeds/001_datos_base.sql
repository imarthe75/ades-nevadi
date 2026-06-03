-- =============================================================================
-- ADES — Seeds de Datos Base
-- Ciclo Escolar 2026-2027
-- Instituto Nevadi — 3 Planteles
-- Fuentes: DOF/SEP Acuerdo 2026-2027, UAEMEX convocatoria NMS2026B
-- =============================================================================

-- Usar transacción única para rollback en caso de error
BEGIN;

-- =============================================================================
-- 1. PAÍS Y ESTADO BASE (México / Estado de México)
-- =============================================================================
INSERT INTO ades_paises (clave_pais, nombre_pais) VALUES ('MX', 'México')
ON CONFLICT (clave_pais) DO NOTHING;

INSERT INTO ades_estados (clave_estado, nombre_estado, pais_id)
SELECT '15', 'Estado de México', id FROM ades_paises WHERE clave_pais = 'MX'
ON CONFLICT (clave_estado, pais_id) DO NOTHING;

-- =============================================================================
-- 2. ESTATUS (por entidad)
-- =============================================================================
INSERT INTO ades_estatus (entidad, nombre_estatus, descripcion) VALUES
  ('GLOBAL',        'ACTIVO',       'Registro activo en el sistema'),
  ('GLOBAL',        'INACTIVO',     'Registro inactivo temporalmente'),
  ('GLOBAL',        'BAJA',         'Dado de baja definitivamente'),
  ('ESTUDIANTE',    'INSCRITO',     'Alumno inscrito en el ciclo actual'),
  ('ESTUDIANTE',    'EGRESADO',     'Alumno que concluyó el nivel'),
  ('ESTUDIANTE',    'BAJA',         'Alumno dado de baja'),
  ('ESTUDIANTE',    'SUSPENDIDO',   'Alumno suspendido temporalmente'),
  ('PROFESOR',      'ACTIVO',       'Profesor en activo'),
  ('PROFESOR',      'BAJA',         'Profesor dado de baja'),
  ('PROFESOR',      'LICENCIA',     'Profesor en licencia'),
  ('GRUPO',         'ACTIVO',       'Grupo en operación'),
  ('GRUPO',         'CERRADO',      'Grupo cerrado'),
  ('INSCRIPCION',   'VIGENTE',      'Inscripción vigente'),
  ('INSCRIPCION',   'CANCELADA',    'Inscripción cancelada'),
  ('ESCUELA',       'ACTIVA',       'Escuela en operación'),
  ('PLANTEL',       'ACTIVO',       'Plantel en operación'),
  ('USUARIO',       'ACTIVO',       'Usuario con acceso al sistema'),
  ('USUARIO',       'BLOQUEADO',    'Usuario bloqueado'),
  ('USUARIO',       'PENDIENTE',    'Usuario pendiente de activación')
ON CONFLICT (entidad, nombre_estatus) DO NOTHING;

-- =============================================================================
-- 3. ROLES DEL SISTEMA
-- =============================================================================
INSERT INTO ades_roles (nombre_rol, descripcion, nivel_acceso) VALUES
  ('ADMIN_GLOBAL',          'Administrador de toda la institución',          0),
  ('ADMIN_PLANTEL',         'Administrador de un plantel específico',        1),
  ('DIRECTOR',              'Director de plantel',                           2),
  ('COORDINADOR_ACADEMICO', 'Coordinador académico por nivel',               3),
  ('DOCENTE',               'Profesor — acceso a sus grupos y materias',     4),
  ('MEDICO_ESCOLAR',        'Personal de salud del plantel',                 4),
  ('ALUMNO',                'Estudiante — acceso a su propio expediente',    5),
  ('PADRE_FAMILIA',         'Padre/tutor — acceso al expediente de su hijo', 5)
ON CONFLICT (nombre_rol) DO NOTHING;

-- =============================================================================
-- 4. NIVELES EDUCATIVOS
-- =============================================================================
INSERT INTO ades_niveles_educativos
  (nombre_nivel, autoridad_educativa, tipo_ciclo, num_periodos_eval, tiene_extraordinario)
VALUES
  ('PRIMARIA',      'SEP',    'ANUAL',      3, FALSE),
  ('SECUNDARIA',    'SEP',    'ANUAL',      6, FALSE),
  ('PREPARATORIA',  'UAEMEX', 'SEMESTRAL',  2, TRUE)
ON CONFLICT (nombre_nivel) DO NOTHING;

-- =============================================================================
-- 5. IDENTIDAD INSTITUCIONAL GLOBAL
-- =============================================================================
INSERT INTO ades_identidad_institucional
  (tipo_elemento, texto_elemento, url_archivo)
VALUES
  ('SLOGAN',        'EL ÚNICO CAMINO PARA SALIR ADELANTE ES LA EDUCACIÓN.', NULL),
  ('LOGO',          NULL, 'logos/nevadi-global.png'),
  ('COLOR_PRIMARIO',NULL, NULL)
ON CONFLICT DO NOTHING;

-- =============================================================================
-- 6. ESCUELA PRINCIPAL
-- =============================================================================
INSERT INTO ades_escuelas (nombre_escuela, sitio_web, estatus_id)
SELECT
  'Instituto Nevadi',
  'https://institutonevadi.edu.mx/',
  e.id
FROM ades_estatus e
WHERE e.entidad = 'ESCUELA' AND e.nombre_estatus = 'ACTIVA'
ON CONFLICT DO NOTHING;

-- Información general de la escuela
INSERT INTO ades_informacion_escuela (escuela_id, mision, vision, valores)
SELECT
  e.id,
  'Formar personas íntegras con sólidos valores, conocimientos y habilidades que les permitan contribuir positivamente a la sociedad.',
  'Ser la institución educativa de referencia en el Estado de México, reconocida por su excelencia académica y formación humana.',
  'Respeto, Responsabilidad, Honestidad, Excelencia, Solidaridad'
FROM ades_escuelas e
WHERE e.nombre_escuela = 'Instituto Nevadi'
ON CONFLICT DO NOTHING;

-- =============================================================================
-- 7. PLANTELES
-- =============================================================================
INSERT INTO ades_planteles (nombre_plantel, escuela_id, clave_ct, estatus_id)
SELECT
  p.nombre,
  e.id,
  p.clave_ct,
  est.id
FROM (VALUES
  ('Metepec',          'MET-NVD-001'),
  ('Tenancingo',       'TEN-NVD-001'),
  ('Ixtapan de la Sal','IXT-NVD-001')
) AS p(nombre, clave_ct)
CROSS JOIN ades_escuelas e
CROSS JOIN ades_estatus est
WHERE e.nombre_escuela = 'Instituto Nevadi'
  AND est.entidad = 'PLANTEL' AND est.nombre_estatus = 'ACTIVO'
ON CONFLICT (nombre_plantel, escuela_id) DO NOTHING;

-- =============================================================================
-- 8. CONTACTOS DE PLANTELES
-- =============================================================================
-- Teléfonos Metepec
INSERT INTO ades_telefonos (numero_telefono, tipo_telefono, entidad_tipo, entidad_id)
SELECT num, tipo, 'PLANTEL', p.id
FROM ades_planteles p,
     (VALUES ('7222971441','PRINCIPAL'), ('7223253683','SECUNDARIO')) AS t(num, tipo)
WHERE p.nombre_plantel = 'Metepec'
ON CONFLICT DO NOTHING;

-- Teléfonos Tenancingo
INSERT INTO ades_telefonos (numero_telefono, tipo_telefono, entidad_tipo, entidad_id)
SELECT '7141424323', 'PRINCIPAL', 'PLANTEL', p.id
FROM ades_planteles p WHERE p.nombre_plantel = 'Tenancingo'
ON CONFLICT DO NOTHING;

-- Teléfonos Ixtapan
INSERT INTO ades_telefonos (numero_telefono, tipo_telefono, entidad_tipo, entidad_id)
SELECT '7211433015', 'PRINCIPAL', 'PLANTEL', p.id
FROM ades_planteles p WHERE p.nombre_plantel = 'Ixtapan de la Sal'
ON CONFLICT DO NOTHING;

-- Correos
INSERT INTO ades_correos_electronicos (direccion_email, tipo_correo, entidad_tipo, entidad_id)
SELECT c.email, 'PRINCIPAL', 'PLANTEL', p.id
FROM ades_planteles p
JOIN (VALUES
  ('Metepec',           'nevadimetepec@institutonevadi.edu.mx'),
  ('Tenancingo',        'nevaditenancingo@institutonevadi.edu.mx'),
  ('Ixtapan de la Sal', 'nevadiixtapan@institutonevadi.edu.mx')
) AS c(plantel, email) ON p.nombre_plantel = c.plantel
ON CONFLICT DO NOTHING;

-- Correo general escuela
INSERT INTO ades_correos_electronicos (direccion_email, tipo_correo, entidad_tipo, entidad_id)
SELECT 'contacto@institutonevadi.edu.mx', 'PRINCIPAL', 'ESCUELA', e.id
FROM ades_escuelas e WHERE e.nombre_escuela = 'Instituto Nevadi'
ON CONFLICT DO NOTHING;

-- =============================================================================
-- 9. RELACIÓN PLANTEL ↔ NIVELES
-- Metepec:      Primaria, Secundaria, Preparatoria
-- Tenancingo:   Primaria, Secundaria
-- Ixtapan:      Primaria, Secundaria (solo grados 1 y 2)
-- =============================================================================
INSERT INTO ades_plantel_niveles (plantel_id, nivel_educativo_id, estatus_id)
SELECT p.id, ne.id, est.id
FROM ades_planteles p
CROSS JOIN ades_niveles_educativos ne
CROSS JOIN ades_estatus est
WHERE est.entidad = 'GLOBAL' AND est.nombre_estatus = 'ACTIVO'
  AND (
    (p.nombre_plantel = 'Metepec')  -- los 3 niveles
    OR (p.nombre_plantel = 'Tenancingo'    AND ne.nombre_nivel IN ('PRIMARIA','SECUNDARIA'))
    OR (p.nombre_plantel = 'Ixtapan de la Sal' AND ne.nombre_nivel IN ('PRIMARIA','SECUNDARIA'))
  )
ON CONFLICT (plantel_id, nivel_educativo_id) DO NOTHING;

-- =============================================================================
-- 10. CICLOS ESCOLARES 2026-2027
-- SEP: ciclo anual "2026-2027"  → primaria y secundaria
-- UAEMEX: semestre "26B" (ago-ene) y "27A" (ene-jul) → solo preparatoria
-- =============================================================================
INSERT INTO ades_ciclos_escolares
  (nombre_ciclo, nivel_educativo_id, fecha_inicio, fecha_fin, tipo_ciclo, es_vigente)
SELECT
  c.nombre,
  ne.id,
  c.inicio::DATE,
  c.fin::DATE,
  c.tipo,
  TRUE
FROM (VALUES
  ('2026-2027', 'PRIMARIA',     '2026-08-24', '2027-07-09', 'ANUAL'),
  ('2026-2027', 'SECUNDARIA',   '2026-08-24', '2027-07-09', 'ANUAL'),
  ('26B',       'PREPARATORIA', '2026-08-04', '2027-01-30', 'SEMESTRAL'),
  ('27A',       'PREPARATORIA', '2027-02-01', '2027-07-09', 'SEMESTRAL')
) AS c(nombre, nivel, inicio, fin, tipo)
JOIN ades_niveles_educativos ne ON ne.nombre_nivel = c.nivel
ON CONFLICT (nombre_ciclo, nivel_educativo_id) DO NOTHING;

-- =============================================================================
-- 11. CALENDARIO ESCOLAR SEP 2026-2027 (Estado de México)
-- Fuente: DOF Acuerdo SEP + Secretaría de Educación Estado de México
-- =============================================================================
-- Obtener IDs de ciclos SEP para insertar eventos
WITH ciclo_primaria AS (
    SELECT ce.id FROM ades_ciclos_escolares ce
    JOIN ades_niveles_educativos ne ON ne.id = ce.nivel_educativo_id
    WHERE ce.nombre_ciclo = '2026-2027' AND ne.nombre_nivel = 'PRIMARIA'
),
ciclo_secundaria AS (
    SELECT ce.id FROM ades_ciclos_escolares ce
    JOIN ades_niveles_educativos ne ON ne.id = ce.nivel_educativo_id
    WHERE ce.nombre_ciclo = '2026-2027' AND ne.nombre_nivel = 'SECUNDARIA'
)
INSERT INTO ades_calendario_escolar
  (ciclo_escolar_id, fecha_evento, nombre_evento, tipo_evento, aplica_todos_planteles)
-- Insertar para primaria
SELECT cp.id, e.fecha::DATE, e.nombre, e.tipo, TRUE
FROM ciclo_primaria cp,
(VALUES
  -- Inicio y fin de ciclo
  ('2026-08-24', 'Inicio del ciclo escolar 2026-2027',        'INICIO_CLASES'),
  ('2027-07-09', 'Fin del ciclo escolar 2026-2027',           'FIN_CLASES'),
  -- Días festivos LFT (suspensión de clases obligatoria)
  ('2026-09-16', 'Día de la Independencia de México',         'DIA_FESTIVO'),
  ('2026-11-17', 'Día de la Revolución Mexicana',             'DIA_FESTIVO'),
  ('2026-12-25', 'Navidad',                                   'DIA_FESTIVO'),
  ('2027-01-01', 'Año Nuevo',                                 'DIA_FESTIVO'),
  ('2027-02-01', 'Aniversario de la Constitución',            'DIA_FESTIVO'),
  ('2027-03-15', 'Natalicio de Benito Juárez',                'DIA_FESTIVO'),
  ('2027-05-01', 'Día del Trabajo',                           'DIA_FESTIVO'),
  ('2027-05-15', 'Día del Maestro',                           'SUSPENSION'),
  -- Vacaciones de invierno (18 dic — 4 enero)
  ('2026-12-18', 'Inicio vacaciones de invierno',             'VACACIONES'),
  ('2026-12-19', 'Vacaciones de invierno',                    'VACACIONES'),
  ('2026-12-20', 'Vacaciones de invierno',                    'VACACIONES'),
  ('2026-12-21', 'Vacaciones de invierno',                    'VACACIONES'),
  ('2026-12-22', 'Vacaciones de invierno',                    'VACACIONES'),
  ('2026-12-23', 'Vacaciones de invierno',                    'VACACIONES'),
  ('2026-12-28', 'Vacaciones de invierno',                    'VACACIONES'),
  ('2026-12-29', 'Vacaciones de invierno',                    'VACACIONES'),
  ('2026-12-30', 'Vacaciones de invierno',                    'VACACIONES'),
  ('2026-12-31', 'Vacaciones de invierno',                    'VACACIONES'),
  ('2027-01-04', 'Fin vacaciones de invierno (regreso)',       'VACACIONES'),
  ('2027-01-11', 'Regreso a clases tras vacaciones invierno', 'INICIO_CLASES'),
  -- Semana Santa (22-26 marzo 2027)
  ('2027-03-22', 'Inicio Semana Santa',                       'VACACIONES'),
  ('2027-03-23', 'Semana Santa',                              'VACACIONES'),
  ('2027-03-24', 'Semana Santa',                              'VACACIONES'),
  ('2027-03-25', 'Semana Santa',                              'VACACIONES'),
  ('2027-03-26', 'Jueves Santo',                              'VACACIONES'),
  -- Sesiones CTE (7 viernes, uno por mes sep-jun)
  -- Basado en el patrón histórico del calendario SEP (primer o segundo viernes del mes)
  ('2026-09-11', 'Consejo Técnico Escolar — septiembre',      'CONSEJO_TECNICO'),
  ('2026-10-09', 'Consejo Técnico Escolar — octubre',         'CONSEJO_TECNICO'),
  ('2026-11-06', 'Consejo Técnico Escolar — noviembre',       'CONSEJO_TECNICO'),
  ('2027-01-15', 'Consejo Técnico Escolar — enero',           'CONSEJO_TECNICO'),
  ('2027-02-05', 'Consejo Técnico Escolar — febrero',         'CONSEJO_TECNICO'),
  ('2027-03-12', 'Consejo Técnico Escolar — marzo',           'CONSEJO_TECNICO'),
  ('2027-04-09', 'Consejo Técnico Escolar — abril',           'CONSEJO_TECNICO'),
  -- Taller intensivo de formación (inicio de ciclo, antes del primer día)
  ('2026-08-21', 'Taller Intensivo Formación Continua Docentes (TIFCD)', 'CONSEJO_TECNICO')
) AS e(fecha, nombre, tipo)
UNION ALL
-- Mismo calendario para secundaria
SELECT cs.id, e.fecha::DATE, e.nombre, e.tipo, TRUE
FROM ciclo_secundaria cs,
(VALUES
  ('2026-08-24', 'Inicio del ciclo escolar 2026-2027',        'INICIO_CLASES'),
  ('2027-07-09', 'Fin del ciclo escolar 2026-2027',           'FIN_CLASES'),
  ('2026-09-16', 'Día de la Independencia de México',         'DIA_FESTIVO'),
  ('2026-11-17', 'Día de la Revolución Mexicana',             'DIA_FESTIVO'),
  ('2026-12-25', 'Navidad',                                   'DIA_FESTIVO'),
  ('2027-01-01', 'Año Nuevo',                                 'DIA_FESTIVO'),
  ('2027-02-01', 'Aniversario de la Constitución',            'DIA_FESTIVO'),
  ('2027-03-15', 'Natalicio de Benito Juárez',                'DIA_FESTIVO'),
  ('2027-05-01', 'Día del Trabajo',                           'DIA_FESTIVO'),
  ('2027-05-15', 'Día del Maestro',                           'SUSPENSION'),
  ('2026-12-18', 'Inicio vacaciones de invierno',             'VACACIONES'),
  ('2026-12-19', 'Vacaciones de invierno',                    'VACACIONES'),
  ('2026-12-20', 'Vacaciones de invierno',                    'VACACIONES'),
  ('2026-12-21', 'Vacaciones de invierno',                    'VACACIONES'),
  ('2026-12-22', 'Vacaciones de invierno',                    'VACACIONES'),
  ('2026-12-23', 'Vacaciones de invierno',                    'VACACIONES'),
  ('2026-12-28', 'Vacaciones de invierno',                    'VACACIONES'),
  ('2026-12-29', 'Vacaciones de invierno',                    'VACACIONES'),
  ('2026-12-30', 'Vacaciones de invierno',                    'VACACIONES'),
  ('2026-12-31', 'Vacaciones de invierno',                    'VACACIONES'),
  ('2027-01-11', 'Regreso a clases tras vacaciones invierno', 'INICIO_CLASES'),
  ('2027-03-22', 'Inicio Semana Santa',                       'VACACIONES'),
  ('2027-03-23', 'Semana Santa',                              'VACACIONES'),
  ('2027-03-24', 'Semana Santa',                              'VACACIONES'),
  ('2027-03-25', 'Semana Santa',                              'VACACIONES'),
  ('2027-03-26', 'Jueves Santo',                              'VACACIONES'),
  ('2026-09-11', 'Consejo Técnico Escolar — septiembre',      'CONSEJO_TECNICO'),
  ('2026-10-09', 'Consejo Técnico Escolar — octubre',         'CONSEJO_TECNICO'),
  ('2026-11-06', 'Consejo Técnico Escolar — noviembre',       'CONSEJO_TECNICO'),
  ('2027-01-15', 'Consejo Técnico Escolar — enero',           'CONSEJO_TECNICO'),
  ('2027-02-05', 'Consejo Técnico Escolar — febrero',         'CONSEJO_TECNICO'),
  ('2027-03-12', 'Consejo Técnico Escolar — marzo',           'CONSEJO_TECNICO'),
  ('2027-04-09', 'Consejo Técnico Escolar — abril',           'CONSEJO_TECNICO'),
  ('2026-08-21', 'Taller Intensivo Formación Continua Docentes (TIFCD)', 'CONSEJO_TECNICO')
) AS e(fecha, nombre, tipo);

-- =============================================================================
-- 12. CALENDARIO UAEMEX 26B (Preparatoria Metepec — 1er semestre)
-- Fuente: Patrón histórico NMS UAEMEX, convocatoria NMS2026B
-- =============================================================================
WITH ciclo_prep AS (
    SELECT ce.id FROM ades_ciclos_escolares ce
    JOIN ades_niveles_educativos ne ON ne.id = ce.nivel_educativo_id
    WHERE ce.nombre_ciclo = '26B' AND ne.nombre_nivel = 'PREPARATORIA'
)
INSERT INTO ades_calendario_escolar
  (ciclo_escolar_id, fecha_evento, nombre_evento, tipo_evento, aplica_todos_planteles)
SELECT cp.id, e.fecha::DATE, e.nombre, e.tipo, FALSE  -- solo Metepec
FROM ciclo_prep cp,
(VALUES
  ('2026-08-04', 'Inicio semestre 26B UAEMEX',                'INICIO_CLASES'),
  ('2026-08-14', 'Fase intensiva docentes (sin alumnos)',      'CONSEJO_TECNICO'),
  -- Días no laborables UAEMEX
  ('2026-09-01', 'Suspensión de labores UAEMEX',              'SUSPENSION'),
  ('2026-09-16', 'Día de la Independencia',                   'DIA_FESTIVO'),
  ('2026-10-12', 'Día de la Raza / Suspensión UAEMEX',        'DIA_FESTIVO'),
  ('2026-11-01', 'Día de Muertos — Suspensión',               'SUSPENSION'),
  ('2026-11-02', 'Día de Muertos — Suspensión',               'SUSPENSION'),
  ('2026-11-17', 'Revolución Mexicana',                       'DIA_FESTIVO'),
  ('2026-12-12', 'Día de la Virgen — Suspensión UAEMEX',      'SUSPENSION'),
  ('2026-12-25', 'Navidad',                                   'DIA_FESTIVO'),
  -- 1er Parcial (semana 10-11 del semestre, oct)
  ('2026-10-05', 'Inicio evaluaciones 1er parcial',           'CONSEJO_TECNICO'),
  ('2026-10-06', 'Evaluaciones 1er parcial',                  'CONSEJO_TECNICO'),
  ('2026-10-07', 'Evaluaciones 1er parcial',                  'CONSEJO_TECNICO'),
  ('2026-10-08', 'Evaluaciones 1er parcial',                  'CONSEJO_TECNICO'),
  ('2026-10-09', 'Cierre 1er parcial',                        'CONSEJO_TECNICO'),
  -- 2do Parcial (semana 15-16, nov)
  ('2026-11-09', 'Inicio evaluaciones 2do parcial',           'CONSEJO_TECNICO'),
  ('2026-11-10', 'Evaluaciones 2do parcial',                  'CONSEJO_TECNICO'),
  ('2026-11-11', 'Evaluaciones 2do parcial',                  'CONSEJO_TECNICO'),
  ('2026-11-12', 'Evaluaciones 2do parcial',                  'CONSEJO_TECNICO'),
  ('2026-11-13', 'Cierre 2do parcial',                        'CONSEJO_TECNICO'),
  -- Fin de clases y ordinario (final)
  ('2026-11-27', 'Fin de clases semestre 26B',                'FIN_CLASES'),
  ('2026-12-01', 'Inicio exámenes ordinarios (finales)',       'CONSEJO_TECNICO'),
  ('2026-12-02', 'Exámenes ordinarios',                       'CONSEJO_TECNICO'),
  ('2026-12-03', 'Exámenes ordinarios',                       'CONSEJO_TECNICO'),
  ('2026-12-04', 'Exámenes ordinarios',                       'CONSEJO_TECNICO'),
  ('2026-12-05', 'Cierre exámenes ordinarios',                'CONSEJO_TECNICO'),
  ('2026-12-14', 'Publicación calificaciones ordinario',      'CONSEJO_TECNICO'),
  -- Extraordinarios (enero 2027)
  ('2027-01-04', 'Reanudación actividades UAEMEX',            'INICIO_CLASES'),
  ('2027-01-05', 'Inicio exámenes extraordinarios 26B',       'CONSEJO_TECNICO'),
  ('2027-01-06', 'Exámenes extraordinarios',                  'CONSEJO_TECNICO'),
  ('2027-01-07', 'Exámenes extraordinarios',                  'CONSEJO_TECNICO'),
  ('2027-01-08', 'Exámenes extraordinarios',                  'CONSEJO_TECNICO'),
  ('2027-01-09', 'Cierre exámenes extraordinarios 26B',       'CONSEJO_TECNICO'),
  ('2027-01-14', 'Publicación resultados extraordinarios',    'CONSEJO_TECNICO'),
  ('2027-01-20', 'Títulos de suficiencia académica',          'CONSEJO_TECNICO'),
  ('2027-01-30', 'Cierre administrativo semestre 26B',        'FIN_CLASES')
) AS e(fecha, nombre, tipo);

-- =============================================================================
-- 13. PERIODOS DE EVALUACIÓN
-- SEP Primaria: 3 bimestres
-- SEP Secundaria: 6 bimestres
-- UAEMEX: 2 parciales + 1 final + 1 extraordinario
-- =============================================================================

-- Primaria — 3 bimestres
WITH ciclo AS (
    SELECT ce.id FROM ades_ciclos_escolares ce
    JOIN ades_niveles_educativos ne ON ne.id = ce.nivel_educativo_id
    WHERE ce.nombre_ciclo = '2026-2027' AND ne.nombre_nivel = 'PRIMARIA'
)
INSERT INTO ades_periodos_evaluacion
  (nombre_periodo, numero_periodo, tipo_periodo, ciclo_escolar_id,
   fecha_inicio, fecha_fin, fecha_entrega_boletas)
SELECT e.nombre, e.num, e.tipo, c.id,
       e.inicio::DATE, e.fin::DATE, e.boletas::DATE
FROM ciclo c,
(VALUES
  ('1er Bimestre', 1, 'ORDINARIO', '2026-08-24', '2026-10-23', '2026-10-30'),
  ('2do Bimestre', 2, 'ORDINARIO', '2026-10-26', '2026-12-17', '2026-12-18'),
  ('3er Bimestre', 3, 'ORDINARIO', '2027-01-11', '2027-07-09', '2027-07-09')
) AS e(nombre, num, tipo, inicio, fin, boletas)
ON CONFLICT (numero_periodo, tipo_periodo, ciclo_escolar_id) DO NOTHING;

-- Secundaria — 6 bimestres
WITH ciclo AS (
    SELECT ce.id FROM ades_ciclos_escolares ce
    JOIN ades_niveles_educativos ne ON ne.id = ce.nivel_educativo_id
    WHERE ce.nombre_ciclo = '2026-2027' AND ne.nombre_nivel = 'SECUNDARIA'
)
INSERT INTO ades_periodos_evaluacion
  (nombre_periodo, numero_periodo, tipo_periodo, ciclo_escolar_id,
   fecha_inicio, fecha_fin, fecha_entrega_boletas)
SELECT e.nombre, e.num, e.tipo, c.id,
       e.inicio::DATE, e.fin::DATE, e.boletas::DATE
FROM ciclo c,
(VALUES
  ('1er Bimestre', 1, 'ORDINARIO', '2026-08-24', '2026-10-02', '2026-10-09'),
  ('2do Bimestre', 2, 'ORDINARIO', '2026-10-05', '2026-11-13', '2026-11-20'),
  ('3er Bimestre', 3, 'ORDINARIO', '2026-11-16', '2026-12-17', '2026-12-18'),
  ('4to Bimestre', 4, 'ORDINARIO', '2027-01-11', '2027-02-19', '2027-02-26'),
  ('5to Bimestre', 5, 'ORDINARIO', '2027-02-22', '2027-04-09', '2027-04-16'),
  ('6to Bimestre', 6, 'ORDINARIO', '2027-04-12', '2027-07-09', '2027-07-09')
) AS e(nombre, num, tipo, inicio, fin, boletas)
ON CONFLICT (numero_periodo, tipo_periodo, ciclo_escolar_id) DO NOTHING;

-- UAEMEX 26B — 2 parciales + final + extraordinario
WITH ciclo AS (
    SELECT ce.id FROM ades_ciclos_escolares ce
    JOIN ades_niveles_educativos ne ON ne.id = ce.nivel_educativo_id
    WHERE ce.nombre_ciclo = '26B' AND ne.nombre_nivel = 'PREPARATORIA'
)
INSERT INTO ades_periodos_evaluacion
  (nombre_periodo, numero_periodo, tipo_periodo, ciclo_escolar_id,
   fecha_inicio, fecha_fin, fecha_entrega_boletas)
SELECT e.nombre, e.num, e.tipo, c.id,
       e.inicio::DATE, e.fin::DATE, e.boletas::DATE
FROM ciclo c,
(VALUES
  ('1er Parcial',    1, 'ORDINARIO',      '2026-08-04', '2026-10-09', '2026-10-17'),
  ('2do Parcial',    2, 'ORDINARIO',      '2026-10-12', '2026-11-13', '2026-11-20'),
  ('Ordinario Final',3, 'FINAL',          '2026-12-01', '2026-12-05', '2026-12-14'),
  ('Extraordinario', 4, 'EXTRAORDINARIO', '2027-01-05', '2027-01-09', '2027-01-14')
) AS e(nombre, num, tipo, inicio, fin, boletas)
ON CONFLICT (numero_periodo, tipo_periodo, ciclo_escolar_id) DO NOTHING;

-- =============================================================================
-- 14. GRADOS POR PLANTEL Y NIVEL
-- Metepec:      Primaria 1-6, Secundaria 1-3, Preparatoria 1er semestre
-- Tenancingo:   Primaria 1-6, Secundaria 1-3
-- Ixtapan:      Primaria 1-6, Secundaria 1-2
-- =============================================================================
WITH
plantel_metepec  AS (SELECT id FROM ades_planteles WHERE nombre_plantel = 'Metepec'),
plantel_tena     AS (SELECT id FROM ades_planteles WHERE nombre_plantel = 'Tenancingo'),
plantel_ixt      AS (SELECT id FROM ades_planteles WHERE nombre_plantel = 'Ixtapan de la Sal'),
niv_pri   AS (SELECT id FROM ades_niveles_educativos WHERE nombre_nivel = 'PRIMARIA'),
niv_sec   AS (SELECT id FROM ades_niveles_educativos WHERE nombre_nivel = 'SECUNDARIA'),
niv_prep  AS (SELECT id FROM ades_niveles_educativos WHERE nombre_nivel = 'PREPARATORIA'),
est_act   AS (SELECT id FROM ades_estatus WHERE entidad='GLOBAL' AND nombre_estatus='ACTIVO')
INSERT INTO ades_grados (numero_grado, nombre_grado, nivel_educativo_id, plantel_id, estatus_id)
-- PRIMARIA — los 3 planteles, grados 1-6
SELECT g.num, g.nombre, niv_pri.id, p.id, est_act.id
FROM (VALUES
  (1,'Primer grado'),(2,'Segundo grado'),(3,'Tercer grado'),
  (4,'Cuarto grado'),(5,'Quinto grado'), (6,'Sexto grado')
) AS g(num, nombre),
(SELECT id FROM ades_planteles) AS p,
niv_pri, est_act
UNION ALL
-- SECUNDARIA — Metepec y Tenancingo: grados 1-3 / Ixtapan: grados 1-2
SELECT g.num, g.nombre, niv_sec.id, p.id, est_act.id
FROM (VALUES (1,'Primer grado'),(2,'Segundo grado'),(3,'Tercer grado')) AS g(num, nombre),
(
  SELECT id FROM ades_planteles WHERE nombre_plantel IN ('Metepec','Tenancingo')
  UNION ALL
  SELECT id FROM ades_planteles WHERE nombre_plantel = 'Ixtapan de la Sal'
    -- Solo grados 1 y 2 para Ixtapan
) AS p,
niv_sec, est_act
WHERE NOT (p.id = (SELECT id FROM ades_planteles WHERE nombre_plantel = 'Ixtapan de la Sal')
           AND g.num = 3)
UNION ALL
-- PREPARATORIA — solo Metepec, 1er semestre
SELECT 1, 'Primer semestre', niv_prep.id, plantel_metepec.id, est_act.id
FROM plantel_metepec, niv_prep, est_act
ON CONFLICT (numero_grado, nivel_educativo_id, plantel_id) DO NOTHING;

-- =============================================================================
-- 15. MATERIAS POR NIVEL (Plan de estudios oficial)
-- Fuente: Plan de Estudios SEP Educación Básica 2022 (Nueva Escuela Mexicana)
--         UAEMEX NMS — Materias nivel preparatoria incorporada
-- =============================================================================

-- PRIMARIA (materias comunes a todos los grados, horas aproximadas SEP NEM 2022)
INSERT INTO ades_materias (nombre_materia, clave_materia, nivel_educativo_id, horas_semana, es_inglés)
SELECT m.nombre, m.clave, ne.id, m.horas, m.ingles
FROM ades_niveles_educativos ne,
(VALUES
  ('Lenguajes',                             'PRI-LEN', 8.0, FALSE),
  ('Saberes y Pensamiento Científico',      'PRI-SPC', 6.0, FALSE),
  ('Ética, Naturaleza y Sociedades',        'PRI-ENS', 5.0, FALSE),
  ('De lo Humano y lo Comunitario',         'PRI-DHC', 4.0, FALSE),
  ('Inglés',                                'PRI-ING', 3.0, TRUE),
  ('Educación Física',                      'PRI-EDF', 2.0, FALSE),
  ('Artes',                                 'PRI-ART', 2.0, FALSE)
) AS m(nombre, clave, horas, ingles)
WHERE ne.nombre_nivel = 'PRIMARIA'
ON CONFLICT (nombre_materia, nivel_educativo_id) DO NOTHING;

-- SECUNDARIA (Nueva Escuela Mexicana SEP 2022)
INSERT INTO ades_materias (nombre_materia, clave_materia, nivel_educativo_id, horas_semana, es_inglés)
SELECT m.nombre, m.clave, ne.id, m.horas, FALSE
FROM ades_niveles_educativos ne,
(VALUES
  ('Español',                               'SEC-ESP', 5.0),
  ('Matemáticas',                           'SEC-MAT', 5.0),
  ('Ciencias Naturales y Tecnología',       'SEC-CNT', 4.0),
  ('Historia',                              'SEC-HIS', 3.0),
  ('Geografía',                             'SEC-GEO', 3.0),
  ('Formación Cívica y Ética',              'SEC-FCE', 2.0),
  ('Inglés',                                'SEC-ING', 3.0),
  ('Educación Física',                      'SEC-EDF', 2.0),
  ('Artes',                                 'SEC-ART', 2.0),
  ('Tecnología',                            'SEC-TEC', 3.0),
  ('Tutoría y Educación Socioemocional',    'SEC-TUT', 1.0)
) AS m(nombre, clave, horas)
WHERE ne.nombre_nivel = 'SECUNDARIA'
ON CONFLICT (nombre_materia, nivel_educativo_id) DO NOTHING;

-- Inglés secundaria como is_inglés = TRUE
UPDATE ades_materias SET es_inglés = TRUE
WHERE nombre_materia = 'Inglés'
  AND nivel_educativo_id = (SELECT id FROM ades_niveles_educativos WHERE nombre_nivel = 'SECUNDARIA');

-- PREPARATORIA UAEMEX — Primer semestre (materias del tronco común)
INSERT INTO ades_materias (nombre_materia, clave_materia, nivel_educativo_id, horas_semana, es_inglés)
SELECT m.nombre, m.clave, ne.id, m.horas, FALSE
FROM ades_niveles_educativos ne,
(VALUES
  ('Taller de Lectura y Redacción I',       'PREP-TLR1', 4.0),
  ('Matemáticas I',                         'PREP-MAT1', 5.0),
  ('Química I',                             'PREP-QUI1', 4.0),
  ('Historia Universal I',                  'PREP-HIS1', 4.0),
  ('Inglés I',                              'PREP-ING1', 3.0),
  ('Metodología de la Investigación I',     'PREP-MET1', 3.0),
  ('Informática I',                         'PREP-INF1', 3.0),
  ('Educación Física I',                    'PREP-EDF1', 2.0)
) AS m(nombre, clave, horas)
WHERE ne.nombre_nivel = 'PREPARATORIA'
ON CONFLICT (nombre_materia, nivel_educativo_id) DO NOTHING;

-- =============================================================================
-- 16. AULAS POR PLANTEL (requerido para aSc TimeTables)
-- =============================================================================
INSERT INTO ades_aulas (nombre_aula, plantel_id, tipo_aula, capacidad)
SELECT a.nombre, p.id, a.tipo, a.capacidad
FROM ades_planteles p,
(VALUES
  ('Aula 01',         'AULA',        35),
  ('Aula 02',         'AULA',        35),
  ('Aula 03',         'AULA',        35),
  ('Aula 04',         'AULA',        35),
  ('Aula 05',         'AULA',        35),
  ('Aula 06',         'AULA',        35),
  ('Laboratorio Cómputo', 'COMPUTO', 30),
  ('Cancha',          'CANCHA',      60)
) AS a(nombre, tipo, capacidad)
ON CONFLICT (nombre_aula, plantel_id) DO NOTHING;

COMMIT;

-- =============================================================================
-- VERIFICACIÓN POST-SEED
-- =============================================================================
DO $$
DECLARE
  v_escuelas    INT; v_planteles  INT; v_niveles INT;
  v_ciclos      INT; v_periodos   INT; v_grados  INT;
  v_materias    INT; v_aulas      INT;
BEGIN
  SELECT COUNT(*) INTO v_escuelas  FROM ades_escuelas;
  SELECT COUNT(*) INTO v_planteles FROM ades_planteles;
  SELECT COUNT(*) INTO v_niveles   FROM ades_niveles_educativos;
  SELECT COUNT(*) INTO v_ciclos    FROM ades_ciclos_escolares;
  SELECT COUNT(*) INTO v_periodos  FROM ades_periodos_evaluacion;
  SELECT COUNT(*) INTO v_grados    FROM ades_grados;
  SELECT COUNT(*) INTO v_materias  FROM ades_materias;
  SELECT COUNT(*) INTO v_aulas     FROM ades_aulas;

  RAISE NOTICE '=== VERIFICACIÓN SEED 001 ===';
  RAISE NOTICE 'Escuelas:    % (esperado: 1)',  v_escuelas;
  RAISE NOTICE 'Planteles:   % (esperado: 3)',  v_planteles;
  RAISE NOTICE 'Niveles:     % (esperado: 3)',  v_niveles;
  RAISE NOTICE 'Ciclos:      % (esperado: 4)',  v_ciclos;
  RAISE NOTICE 'Periodos:    % (esperado: 11)', v_periodos;
  RAISE NOTICE 'Grados:      % (esperado: 46)', v_grados;
  RAISE NOTICE 'Materias:    % (esperado: 26)', v_materias;
  RAISE NOTICE 'Aulas:       % (esperado: 24)', v_aulas;
  RAISE NOTICE '=============================';
END $$;
