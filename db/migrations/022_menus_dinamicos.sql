BEGIN;

-- ─────────────────────────────────────────────────────────────────────────────
-- MIGRACIÓN 022: Menús Administrables (FASE 26-B)
-- Árbol de navegación administrable desde la base de datos.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS ades_menus (
    id          SERIAL PRIMARY KEY,
    label       VARCHAR(100) NOT NULL,         -- Texto visible en el menú
    route       VARCHAR(200),                  -- Ruta Angular: '/alumnos', 'external:url'
    icon        VARCHAR(80),                   -- Clase PrimeNG: 'pi pi-users'
    parent_id   INTEGER REFERENCES ades_menus(id),
    permission_id VARCHAR(80),                 -- 'alumnos:view', 'admin:manage' (futuro)
    peso        INTEGER NOT NULL DEFAULT 100,  -- Orden de aparición (menor = primero)
    -- AuditMixin
    ref                  UUID        DEFAULT gen_random_uuid() UNIQUE,
    is_active            BOOLEAN     NOT NULL DEFAULT TRUE,
    fecha_creacion           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fecha_modificacion       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion     VARCHAR(150) NOT NULL DEFAULT current_user,
    usuario_modificacion VARCHAR(150) NOT NULL DEFAULT current_user,
    row_version          INTEGER     NOT NULL DEFAULT 1
);

COMMENT ON TABLE ades_menus IS 'Árbol de navegación administrable desde el módulo Admin, sin necesidad de redeploy del frontend.';
COMMENT ON COLUMN ades_menus.route IS 'Ruta Angular. Prefijo "external:" indica enlace externo.';

CREATE TABLE IF NOT EXISTS ades_menu_roles (
    menu_id   INTEGER REFERENCES ades_menus(id) ON DELETE CASCADE,
    rol_id    UUID NOT NULL, -- references ades_roles(id) we will add constraint if ades_roles exists
    PRIMARY KEY (menu_id, rol_id),
    -- AuditMixin simplificado
    ref                  UUID        DEFAULT gen_random_uuid() UNIQUE,
    row_version          INTEGER     NOT NULL DEFAULT 1,
    fecha_creacion           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fecha_modificacion       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion     VARCHAR(150) NOT NULL DEFAULT current_user,
    usuario_modificacion VARCHAR(150) NOT NULL DEFAULT current_user
);

-- Agregando FK solo si la tabla ades_roles existe (lo cual debería)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'ades_roles') THEN
        ALTER TABLE ades_menu_roles ADD CONSTRAINT fk_menu_roles_rol FOREIGN KEY (rol_id) REFERENCES ades_roles(id) ON DELETE CASCADE;
    END IF;
END $$;

COMMENT ON TABLE ades_menu_roles IS 'Asignación de menús a roles. Un menú visible solo si el usuario tiene el rol asignado.';

-- ─────────────────────────────────────────────────────────────────────────────
-- SEED: estructura del menú actual de ADES
-- ─────────────────────────────────────────────────────────────────────────────

-- Menús de primer nivel (sin parent)
INSERT INTO ades_menus (label, route, icon, parent_id, peso) VALUES
    ('Dashboard',           '/dashboard',        'pi pi-home',          NULL, 10),
    ('Alumnos',             '/alumnos',          'pi pi-users',         NULL, 20),
    ('Profesores',          '/profesores',       'pi pi-id-card',       NULL, 30),
    ('Grupos',              '/grupos',           'pi pi-building',      NULL, 40),
    ('Calificaciones',      '/calificaciones',   'pi pi-star',          NULL, 50),
    ('Asistencias',         '/asistencias',      'pi pi-check-square',  NULL, 60),
    ('Evaluaciones',        '/evaluaciones',     'pi pi-file-edit',     NULL, 70),
    ('Tareas',              '/tareas',           'pi pi-book',          NULL, 80),
    ('Comunicados',         '/comunicados',      'pi pi-envelope',      NULL, 90),
    ('Conducta',            '/conducta',         'pi pi-flag',          NULL, 100),
    ('Expediente Médico',   '/medico',           'pi pi-heart',         NULL, 110),
    ('Gradebook',           '/gradebook',        'pi pi-table',         NULL, 120),
    ('Padres / Contactos',  '/padres',           'pi pi-heart-fill',    NULL, 130),
    ('BI / Analytics',      '/bi',               'pi pi-chart-bar',     NULL, 140),
    ('IA Académica',        '/ia',               'pi pi-sparkles',      NULL, 150),
    ('Geográficos',         '/geograficos',      'pi pi-map-marker',    NULL, 160),
    ('Administración',      NULL,                'pi pi-cog',           NULL, 999)
ON CONFLICT DO NOTHING;

-- Submenús de Administración
DO $$
DECLARE
    v_admin_id INTEGER;
BEGIN
    SELECT id INTO v_admin_id FROM ades_menus WHERE label = 'Administración';
    
    IF v_admin_id IS NOT NULL THEN
        INSERT INTO ades_menus (label, route, icon, parent_id, peso) VALUES
        ('Usuarios',           '/admin#usuarios',   'pi pi-users',    v_admin_id, 10),
        ('Roles',              '/admin#roles',      'pi pi-shield',   v_admin_id, 20),
        ('Privilegios',        '/admin#privilegios','pi pi-lock',     v_admin_id, 30),
        ('Menús',              '/admin#menus',      'pi pi-sitemap',  v_admin_id, 40),
        ('Variables del Sistema','/admin#variables','pi pi-sliders',  v_admin_id, 50),
        ('Catálogos',          '/admin#catalogos',  'pi pi-box',      v_admin_id, 60),
        ('Ciclos Escolares',   '/admin#ciclos',     'pi pi-calendar', v_admin_id, 70),
        ('Planteles',          '/admin#planteles',  'pi pi-building', v_admin_id, 80),
        ('Auditoría',          '/admin#auditoria',  'pi pi-history',  v_admin_id, 90)
        ON CONFLICT DO NOTHING;
    END IF;
END $$;

COMMIT;
