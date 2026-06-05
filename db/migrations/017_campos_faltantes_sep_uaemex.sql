/*
 * Migración 017: Campos faltantes según estándares SEP/UAEMEX
 * Agregar campos críticos que el sistema requiere pero no existen en BD
 *
 * Cambios:
 *   1. ades_usuarios: agregar nivel_acceso (cache del nivel del rol para evitar joins)
 *   2. ades_estudiantes: agregar folio_sep, tipo_alumno
 *   3. ades_contactos_familiares: agregar toma_decision_conjunta, grado_responsabilidad
 *
 * Fecha: 2026-06-05
 * Motor: PostgreSQL 18
 */

-- =====================================================================
-- 1. NIVEL_ACCESO en ades_usuarios (cache del rol para optimización)
-- =====================================================================
-- Refleja el nivel_acceso del rol asignado para evitar JOIN constante
-- Sincronizar cuando cambia el rol del usuario
ALTER TABLE ades_usuarios
ADD COLUMN IF NOT EXISTS nivel_acceso INTEGER DEFAULT 5;
COMMENT ON COLUMN ades_usuarios.nivel_acceso IS
    'Nivel de acceso cacheado del rol (sincronizar si cambia rol)';
CREATE INDEX IF NOT EXISTS idx_ades_usuarios_nivel_acceso ON ades_usuarios(nivel_acceso);

-- =====================================================================
-- 2. CAMPOS ADICIONALES en ades_estudiantes (estándares SEP)
-- =====================================================================
ALTER TABLE ades_estudiantes
ADD COLUMN IF NOT EXISTS folio_sep VARCHAR(50) UNIQUE,
ADD COLUMN IF NOT EXISTS tipo_alumno VARCHAR(30) DEFAULT 'NUEVO'; -- NUEVO, REGULAR, REINGRESO
COMMENT ON COLUMN ades_estudiantes.folio_sep IS 'Folio de identificación SEP del alumno';
COMMENT ON COLUMN ades_estudiantes.tipo_alumno IS 'Tipo de alumno: NUEVO, REGULAR, REINGRESO';
CREATE INDEX IF NOT EXISTS idx_ades_estudiantes_folio_sep ON ades_estudiantes(folio_sep);
CREATE INDEX IF NOT EXISTS idx_ades_estudiantes_tipo ON ades_estudiantes(tipo_alumno);

-- =====================================================================
-- 3. CAMPOS ADICIONALES en ades_contactos_familiares
-- =====================================================================
ALTER TABLE ades_contactos_familiares
ADD COLUMN IF NOT EXISTS toma_decision_conjunta BOOLEAN DEFAULT FALSE,
ADD COLUMN IF NOT EXISTS grado_responsabilidad VARCHAR(20) DEFAULT 'PRINCIPAL'; -- PRINCIPAL, SECUNDARIO, CONSULTA
COMMENT ON COLUMN ades_contactos_familiares.toma_decision_conjunta IS
    'TRUE si la decisión sobre el alumno requiere aprobación de ambos padres (custodia compartida)';
COMMENT ON COLUMN ades_contactos_familiares.grado_responsabilidad IS
    'Grado: PRINCIPAL (todas decisiones), SECUNDARIO (coaprobación), CONSULTA (solo información)';
CREATE INDEX IF NOT EXISTS idx_ades_contactos_fam_decision ON ades_contactos_familiares(toma_decision_conjunta);

-- =====================================================================
-- 4. BACKFILL nivel_acceso en ades_usuarios desde ades_roles
-- =====================================================================
UPDATE ades_usuarios u
SET nivel_acceso = r.nivel_acceso
FROM ades_roles r
WHERE u.rol_id = r.id
  AND u.nivel_acceso = 5;  -- solo los con valor por defecto

-- =====================================================================
-- VERIFICACIÓN
-- =====================================================================
SELECT 'Migración 017 completada correctamente';
