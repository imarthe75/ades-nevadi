BEGIN;

-- ─────────────────────────────────────────────────────────────────────────────
-- PRIVILEGIOS GRANULARES
-- Permisos específicos más allá del nivel_acceso numérico (0-5).
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS ades_privilegios (
    id          UUID PRIMARY KEY DEFAULT uuidv7(),
    codigo      VARCHAR(80) NOT NULL UNIQUE,   -- 'BOLETAS_GENERAR', 'KAARDEX_APROBAR'
    nombre      VARCHAR(150),
    descripcion VARCHAR(500),
    modulo      VARCHAR(50),                   -- 'CALIFICACIONES', 'REPORTES', 'ADMIN'
    -- AuditMixin
    ref                  UUID        DEFAULT gen_random_uuid() UNIQUE,
    is_active            BOOLEAN     NOT NULL DEFAULT TRUE,
    fecha_creacion           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fecha_modificacion       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion     VARCHAR(150) NOT NULL DEFAULT current_user,
    usuario_modificacion VARCHAR(150) NOT NULL DEFAULT current_user,
    row_version          INTEGER     NOT NULL DEFAULT 1
);

CREATE TABLE IF NOT EXISTS ades_rol_privilegios (
    rol_id        UUID REFERENCES ades_roles(id) ON DELETE CASCADE,
    privilegio_id UUID REFERENCES ades_privilegios(id) ON DELETE CASCADE,
    PRIMARY KEY (rol_id, privilegio_id),
    -- AuditMixin simplificado
    ref           UUID DEFAULT gen_random_uuid() UNIQUE,
    row_version   INTEGER NOT NULL DEFAULT 1,
    fecha_creacion    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion VARCHAR(150) NOT NULL DEFAULT current_user
);

-- ─────────────────────────────────────────────────────────────────────────────
-- MULTI-ROL: Un usuario puede tener N roles con peso (mayor peso = rol activo)
-- Retrocompatible: ades_usuarios.rol_id se mantiene como "rol principal"
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS ades_usuario_roles (
    usuario_id UUID REFERENCES ades_usuarios(id) ON DELETE CASCADE,
    rol_id     UUID REFERENCES ades_roles(id) ON DELETE CASCADE,
    peso       INTEGER NOT NULL DEFAULT 100,  -- Rol con mayor peso = activo por defecto
    PRIMARY KEY (usuario_id, rol_id),
    -- AuditMixin simplificado
    ref        UUID DEFAULT gen_random_uuid() UNIQUE,
    row_version INTEGER NOT NULL DEFAULT 1,
    fecha_creacion TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion VARCHAR(150) NOT NULL DEFAULT current_user
);

COMMENT ON TABLE ades_usuario_roles IS 'Permite multi-rol. El campo peso determina el rol activo principal (mayor peso = prioridad). Sincronizado JIT con Authentik.';

-- Poblar ades_usuario_roles con los roles actuales (retrocompatibilidad)
INSERT INTO ades_usuario_roles (usuario_id, rol_id, peso)
SELECT id, rol_id, 100
FROM ades_usuarios
WHERE rol_id IS NOT NULL AND is_active = TRUE
ON CONFLICT (usuario_id, rol_id) DO NOTHING;

-- ─────────────────────────────────────────────────────────────────────────────
-- TRAZABILIDAD ENRIQUECIDA
-- Ampliar ades_audit_log con campos del starter (risk level, category, metadata)
-- ─────────────────────────────────────────────────────────────────────────────

-- Agregar columnas nuevas si no existen
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'ades_audit_log') THEN
        ALTER TABLE ades_audit_log
            ADD COLUMN IF NOT EXISTS event_category   VARCHAR(50),
            ADD COLUMN IF NOT EXISTS event_risk_level VARCHAR(20),
            ADD COLUMN IF NOT EXISTS security_outcome VARCHAR(20),
            ADD COLUMN IF NOT EXISTS metadata         JSONB;

        CREATE INDEX IF NOT EXISTS idx_audit_log_category ON ades_audit_log(event_category);
        CREATE INDEX IF NOT EXISTS idx_audit_log_risk ON ades_audit_log(event_risk_level);
        CREATE INDEX IF NOT EXISTS idx_audit_log_metadata ON ades_audit_log USING GIN (metadata);

        COMMENT ON COLUMN ades_audit_log.event_category IS 'Categoría del evento: AUTHENTICATION, AUTHORIZATION, IDENTITY_MANAGEMENT, ACADEMIC, SYSTEM';
        COMMENT ON COLUMN ades_audit_log.event_risk_level IS 'Nivel de riesgo del evento: LOW, MEDIUM, HIGH, CRITICAL';
        COMMENT ON COLUMN ades_audit_log.metadata IS 'Datos adicionales del evento en JSONB (actores, recursos afectados, contexto)';
    END IF;
END $$;

-- ─────────────────────────────────────────────────────────────────────────────
-- SEED: Privilegios base de ADES
-- ─────────────────────────────────────────────────────────────────────────────

INSERT INTO ades_privilegios (codigo, nombre, descripcion, modulo) VALUES
    ('BOLETAS_GENERAR',          'Generar Boletas',             'Permite generar boletas en lote',                            'REPORTES'),
    ('KARDEX_APROBAR',           'Aprobar Kárdex',              'Permite aprobar el Kárdex oficial de preparatoria',          'REPORTES'),
    ('EXTRAORDINARIO_PROGRAMAR', 'Programar Extraordinarios',   'Permite inscribir alumnos a exámenes extraordinarios',       'EVALUACIONES'),
    ('CALIFICACIONES_EDITAR',    'Editar Calificaciones',       'Permite modificar calificaciones ya cerradas',               'CALIFICACIONES'),
    ('USUARIOS_ADMIN',           'Administrar Usuarios',        'Permite crear/editar/desactivar usuarios del sistema',       'ADMIN'),
    ('VARIABLES_EDITAR',         'Editar Variables del Sistema','Permite modificar variables de configuración',               'ADMIN'),
    ('IMPORTAR_DATOS',           'Importar Datos Masivos',      'Permite importar alumnos/profesores por CSV',                'ADMIN'),
    ('REPORTES_BI_VER',          'Ver BI / Analytics',         'Permite acceder a los dashboards de Superset',               'BI'),
    ('GRUPOS_ADMIN',             'Administrar Grupos',          'Permite crear/editar grupos y sobrecupos',                   'ACADEMICO'),
    ('CONDUCTA_GESTIONAR',       'Gestionar Conducta',          'Permite crear reportes disciplinarios y planes de mejora',   'OPERACION')
ON CONFLICT (codigo) DO NOTHING;

COMMIT;
