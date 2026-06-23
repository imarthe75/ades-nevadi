-- =============================================================================
-- 090_menus_permisos_rol.sql
-- Extiende tablas existentes ades_menus y ades_menu_roles con columnas de:
--   • sección, clave única, nivel_maximo/minimo (visibilidad por nivel_acceso)
--   • puede_ver, puede_editar, puede_crear, puede_eliminar (permisos por rol)
-- Además carga el catálogo completo de menús de la app.
-- =============================================================================

-- ─── 1. Extender ades_menus ───────────────────────────────────────────────────
ALTER TABLE ades_menus
    ADD COLUMN IF NOT EXISTS clave         TEXT UNIQUE,
    ADD COLUMN IF NOT EXISTS seccion       TEXT NOT NULL DEFAULT 'General',
    ADD COLUMN IF NOT EXISTS nivel_maximo  INT  NOT NULL DEFAULT 99,
    ADD COLUMN IF NOT EXISTS nivel_minimo  INT  NOT NULL DEFAULT 0;

-- ─── 2. Extender ades_menu_roles con permisos CRUD ───────────────────────────
ALTER TABLE ades_menu_roles
    ADD COLUMN IF NOT EXISTS puede_ver      BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS puede_editar   BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS puede_crear    BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS puede_eliminar BOOLEAN NOT NULL DEFAULT FALSE;

-- Restricción única para evitar duplicados rol+menu
ALTER TABLE ades_menu_roles
    DROP CONSTRAINT IF EXISTS uq_menu_roles_rol_menu;
ALTER TABLE ades_menu_roles
    ADD CONSTRAINT uq_menu_roles_rol_menu UNIQUE (rol_id, menu_id);

-- ─── 3. Índice de búsqueda por clave ─────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_menus_clave   ON ades_menus (clave) WHERE clave IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_menus_seccion ON ades_menus (seccion, peso);

-- ─── 4. Repoblar catálogo completo (TRUNCATE + INSERT) ───────────────────────
-- Primero limpiamos dependencias y los datos previos
TRUNCATE ades_menu_roles;
TRUNCATE ades_menus CASCADE;

INSERT INTO ades_menus (clave, seccion, label, icon, route, nivel_maximo, nivel_minimo, peso) VALUES
-- Principal
('dashboard',            'Principal',        'Dashboard',                'pi-home',                '/dashboard',             99, 0,  10),
-- Académico
('alumnos',              'Académico',        'Alumnos',                  'pi-users',               '/alumnos',               4,  0,  20),
('reinscripcion',        'Académico',        'Reinscripción',            'pi-refresh',             '/reinscripcion',         3,  0,  21),
('cierre-ciclo',         'Académico',        'Cierre de Ciclo',          'pi-lock',                '/cierre-ciclo',          2,  0,  22),
('padres-admin',         'Académico',        'Gestión de Padres',        'pi-users',               '/padres-admin',          1,  0,  23),
('profesores',           'Académico',        'Profesores',               'pi-id-card',             '/profesores',            4,  0,  24),
('grupos',               'Académico',        'Grupos',                   'pi-building',            '/grupos',                4,  0,  25),
('aulas',                'Académico',        'Aulas',                    'pi-map',                 '/aulas',                 3,  0,  26),
('planes-estudio',       'Académico',        'Planes de Estudio',        'pi-list',                '/planes-estudio',        3,  0,  27),
('calificaciones',       'Académico',        'Calificaciones',           'pi-star',                '/calificaciones',        4,  0,  28),
('evaluaciones',         'Académico',        'Evaluaciones',             'pi-file-check',          '/evaluaciones',          4,  0,  29),
('asistencias',          'Académico',        'Asistencias',              'pi-check-square',        '/asistencias',           4,  0,  30),
('tareas',               'Académico',        'Tareas',                   'pi-file-edit',           '/tareas',                4,  0,  31),
('planeacion',           'Académico',        'Planeación',               'pi-list-check',          '/planeacion',            3,  0,  32),
-- Operaciones
('horarios',             'Operaciones',      'Horarios',                 'pi-calendar',            '/horarios',              3,  0,  40),
('calendario',           'Operaciones',      'Calendario Escolar',       'pi-calendar-clock',      '/calendario',            3,  0,  41),
('conducta',             'Operaciones',      'Conducta',                 'pi-exclamation-circle',  '/conducta',              3,  0,  42),
('medico',               'Operaciones',      'Expediente Médico',        'pi-heart',               '/medico',                3,  0,  43),
('condiciones-cronicas', 'Operaciones',      'Condiciones Crónicas',     'pi-exclamation-triangle','/condiciones-cronicas',  3,  0,  44),
('justificaciones',      'Operaciones',      'Justificaciones Faltas',   'pi-check-circle',        '/justificaciones',       3,  0,  45),
('movilidad',            'Operaciones',      'Movilidad Estudiantil',    'pi-arrows-h',            '/movilidad',             3,  0,  46),
('biblioteca',           'Operaciones',      'Biblioteca',               'pi-book',                '/biblioteca',            4,  0,  47),
('estadistica-911',      'Operaciones',      'Formato 911 SEP',          'pi-chart-bar',           '/estadistica-911',       3,  0,  48),
('kardex',               'Operaciones',      'Kardex UAEMEX',            'pi-id-card',             '/kardex',                3,  0,  49),
('acta-evaluacion',      'Operaciones',      'Acta Evaluación UAEMEX',   'pi-file-edit',           '/acta-evaluacion',       3,  0,  50),
('optativas',            'Operaciones',      'Optativas',                'pi-star',                '/optativas',             3,  0,  51),
('admision',             'Operaciones',      'Admisión',                 'pi-user-plus',           '/admision',              3,  0,  52),
-- Recursos Humanos
('personal-admin',       'Recursos Humanos', 'Personal No-Docente',      'pi-building',            '/personal-admin',        2,  0,  60),
('licencias',            'Recursos Humanos', 'Licencias y Permisos',     'pi-calendar-times',      '/licencias',             2,  0,  61),
('capacitaciones',       'Recursos Humanos', 'Capacitaciones',           'pi-graduation-cap',      '/capacitaciones',        2,  0,  62),
('expediente-laboral',   'Recursos Humanos', 'Expediente Laboral',       'pi-id-card',             '/expediente-laboral',    2,  0,  63),
('disponibilidad',       'Recursos Humanos', 'Disponibilidad Docente',   'pi-calendar',            '/disponibilidad',        2,  0,  64),
('asistencia-personal',  'Recursos Humanos', 'Asistencia Personal',      'pi-clock',               '/asistencia-personal',   2,  0,  65),
-- Comunicación
('comunicados',          'Comunicación',     'Comunicados',              'pi-envelope',            '/comunicados',           4,  0,  70),
('foros',                'Comunicación',     'Foros y Anuncios',         'pi-comments',            '/foros',                 4,  0,  71),
('encuestas',            'Comunicación',     'Encuestas',                'pi-chart-pie',           '/encuestas',             3,  0,  72),
('videoconferencias',    'Comunicación',     'Videoconferencias',        'pi-video',               '/videoconferencias',     4,  0,  73),
-- Gradebook
('gradebook',            'Gradebook',        'Gradebook',                'pi-book',                '/gradebook',             4,  0,  80),
('mi-progreso',          'Gradebook',        'Mi Progreso',              'pi-chart-line',          '/mi-progreso',           4,  0,  81),
('ponderacion-config',   'Gradebook',        'Ponderaciones',            'pi-sliders-h',           '/ponderacion-config',    3,  0,  82),
-- Recursos
('rubricas',             'Recursos',         'Rúbricas',                 'pi-table',               '/rubricas',              4,  0,  90),
('badges',               'Recursos',         'Insignias',                'pi-star-fill',           '/badges',                4,  0,  91),
('portal',               'Recursos',         'Portal Alumno',            'pi-id-card',             '/portal',                4,  0,  92),
('h5p',                  'Recursos',         'Contenido H5P',            'pi-th-large',            '/h5p',                   4,  0,  93),
-- Convocatorias
('portal-admin',         'Convocatorias',    'Gestión Convocatorias',    'pi-megaphone',           '/portal-admin',          2,  0, 100),
-- Mi Familia (nivel_acceso ≥ 5 = padres/alumnos)
('padres',               'Mi Familia',       'Portal de Padres',         'pi-users',               '/padres',                99, 5, 110),
('mi-progreso-familia',  'Mi Familia',       'Mi Progreso',              'pi-chart-line',          '/mi-progreso',           99, 5, 111),
('comunicados-familia',  'Mi Familia',       'Comunicados',              'pi-envelope',            '/comunicados',           99, 5, 112),
-- Inteligencia
('bi',                   'Inteligencia',     'Dashboards BI',            'pi-chart-pie',           '/bi',                    3,  0, 120),
('grade-analytics',      'Inteligencia',     'Grade Analytics',          'pi-chart-bar',           '/grade-analytics',       3,  0, 121),
('ia',                   'Inteligencia',     'Asistente IA + Datos',     'pi-sparkles',            '/ia',                    3,  0, 122),
('eval-docente',         'Inteligencia',     'Eval. Docente 360°',       'pi-star',                '/eval-docente',          3,  0, 123),
('learning-paths',       'Inteligencia',     'Learning Paths',           'pi-graduation-cap',      '/learning-paths',        3,  0, 124),
-- Reportes
('reportes',             'Reportes',         'Generador de Reportes',    'pi-file-pdf',            '/reportes',              3,  0, 130),
('certificados',         'Reportes',         'Certificados Digitales',   'pi-verified',            '/certificados',          3,  0, 131),
('expediente-doc',       'Reportes',         'Expediente Digital',       'pi-folder-open',         '/expediente-doc',        3,  0, 132),
-- Sistema
('monitor',              'Sistema',          'Monitor del Sistema',       'pi-heart-fill',          '/monitor',               1,  0, 140),
('admin',                'Sistema',          'Administración',            'pi-cog',                 '/admin',                 1,  0, 141),
-- Ayuda
('ayuda',                'Ayuda',            'Manual de Usuario',         'pi-question-circle',     '/ayuda',                 99, 0, 150);

-- ─── 5. Permisos base por rol ─────────────────────────────────────────────────

-- ALUMNO: solo puede VER (no editar/crear) en sus módulos
INSERT INTO ades_menu_roles (rol_id, menu_id, puede_ver, puede_editar, puede_crear, puede_eliminar)
SELECT r.id, m.id, TRUE, FALSE, FALSE, FALSE
FROM ades_roles r, ades_menus m
WHERE r.nombre_rol = 'ALUMNO'
  AND m.clave IN ('calificaciones','asistencias','gradebook','mi-progreso','evaluaciones',
                  'comunicados','foros','biblioteca','mi-progreso-familia','comunicados-familia','portal','tareas')
ON CONFLICT (rol_id, menu_id) DO NOTHING;

-- PADRE_FAMILIA: solo puede VER portal propio
INSERT INTO ades_menu_roles (rol_id, menu_id, puede_ver, puede_editar, puede_crear, puede_eliminar)
SELECT r.id, m.id, TRUE, FALSE, FALSE, FALSE
FROM ades_roles r, ades_menus m
WHERE r.nombre_rol = 'PADRE_FAMILIA'
  AND m.clave IN ('padres','mi-progreso-familia','comunicados-familia','calificaciones',
                  'asistencias','evaluaciones','biblioteca','tareas')
ON CONFLICT (rol_id, menu_id) DO NOTHING;

-- DOCENTE: puede editar lo académico de sus grupos, solo VER estructura
INSERT INTO ades_menu_roles (rol_id, menu_id, puede_ver, puede_editar, puede_crear, puede_eliminar)
SELECT r.id, m.id, TRUE, FALSE, FALSE, FALSE
FROM ades_roles r, ades_menus m
WHERE r.nombre_rol = 'DOCENTE'
  AND m.clave IN ('alumnos','grupos','planes-estudio','aulas','horarios')
ON CONFLICT (rol_id, menu_id) DO NOTHING;

INSERT INTO ades_menu_roles (rol_id, menu_id, puede_ver, puede_editar, puede_crear, puede_eliminar)
SELECT r.id, m.id, TRUE, TRUE, TRUE, FALSE
FROM ades_roles r, ades_menus m
WHERE r.nombre_rol = 'DOCENTE'
  AND m.clave IN ('calificaciones','asistencias','tareas','evaluaciones','gradebook',
                  'planeacion','comunicados','foros','videoconferencias','conducta')
ON CONFLICT (rol_id, menu_id) DO NOTHING;
