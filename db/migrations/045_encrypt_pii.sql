-- MIGRACIÓN 045: Agregar soporte para encriptación de PII
-- Fecha: 2026-06-19
-- Propósito: Preparar BD para encriptación de datos sensibles

BEGIN TRANSACTION;

-- 1. Crear tabla de backup (en caso de rollback)
CREATE TABLE IF NOT EXISTS ades_pii_encryption_backup_20260619 (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_table VARCHAR(100),
    field_name VARCHAR(100),
    original_id UUID,
    original_value TEXT,
    backup_date TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- 2. Agregar columnas para versiones encriptadas en ades_usuarios
ALTER TABLE ades_usuarios
ADD COLUMN IF NOT EXISTS email_institucional_encrypted VARCHAR,
ADD COLUMN IF NOT EXISTS email_institucional_hash VARCHAR,
ADD COLUMN IF NOT EXISTS telefono_encrypted VARCHAR,
ADD COLUMN IF NOT EXISTS pii_encryption_status VARCHAR DEFAULT 'pending';

-- 3. Agregar columnas para versiones encriptadas en ades_personas
ALTER TABLE ades_personas
ADD COLUMN IF NOT EXISTS email_personal_encrypted VARCHAR,
ADD COLUMN IF NOT EXISTS email_personal_hash VARCHAR,
ADD COLUMN IF NOT EXISTS telefono_encrypted VARCHAR,
ADD COLUMN IF NOT EXISTS telefono_hash VARCHAR,
ADD COLUMN IF NOT EXISTS curp_encrypted VARCHAR,
ADD COLUMN IF NOT EXISTS curp_hash VARCHAR,
ADD COLUMN IF NOT EXISTS rfc_encrypted VARCHAR,
ADD COLUMN IF NOT EXISTS rfc_hash VARCHAR,
ADD COLUMN IF NOT EXISTS domicilio_encrypted VARCHAR,
ADD COLUMN IF NOT EXISTS domicilio_hash VARCHAR,
ADD COLUMN IF NOT EXISTS pii_encryption_status VARCHAR DEFAULT 'pending';

-- 4. Crear índices en campos hash (para búsquedas sin desencriptar)
CREATE INDEX IF NOT EXISTS idx_usuarios_email_hash ON ades_usuarios(email_institucional_hash)
WHERE email_institucional_hash IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_personas_email_hash ON ades_personas(email_personal_hash)
WHERE email_personal_hash IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_personas_curp_hash ON ades_personas(curp_hash)
WHERE curp_hash IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_personas_rfc_hash ON ades_personas(rfc_hash)
WHERE rfc_hash IS NOT NULL;

-- 5. Crear tabla de audit de encriptación
CREATE TABLE IF NOT EXISTS ades_encryption_audit (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tabla_nombre VARCHAR(100) NOT NULL,
    cantidad_registros INTEGER,
    fecha_inicio TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    fecha_fin TIMESTAMPTZ,
    estado VARCHAR(20) DEFAULT 'en_progreso',  -- en_progreso, completado, error
    errores TEXT,
    usuario_id UUID
);

COMMIT;
