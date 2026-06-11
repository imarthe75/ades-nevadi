-- =============================================================================
-- MIGRACIÓN 026 — FASE 27: Certificación Digital con firma Ed25519
-- Extiende ades_certificados con campos de firma y crea inventario de llaves.
-- Autor: Agente Residente v2.0 | Fecha: 2026-06-10
-- =============================================================================

-- ── 1. Extender ades_certificados con campos de firma ────────────────────────
ALTER TABLE ades_certificados
  ADD COLUMN IF NOT EXISTS hash_sha256       TEXT,
  ADD COLUMN IF NOT EXISTS firma_ed25519     TEXT,
  ADD COLUMN IF NOT EXISTS clave_publica_ref UUID,
  ADD COLUMN IF NOT EXISTS verificable_url   TEXT,
  ADD COLUMN IF NOT EXISTS estado_firma      VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
  ADD COLUMN IF NOT EXISTS fecha_firma       TIMESTAMPTZ;

COMMENT ON COLUMN ades_certificados.hash_sha256       IS 'SHA-256 del payload canónico JSON del certificado';
COMMENT ON COLUMN ades_certificados.firma_ed25519     IS 'Firma Ed25519 del hash_sha256 (base64url)';
COMMENT ON COLUMN ades_certificados.clave_publica_ref IS 'FK a ades_llaves_firma.id que firmó el documento';
COMMENT ON COLUMN ades_certificados.verificable_url   IS 'URL pública de verificación: https://ades.setag.mx/verificar/{folio}';
COMMENT ON COLUMN ades_certificados.estado_firma      IS 'PENDIENTE | FIRMADO | REVOCADO';
COMMENT ON COLUMN ades_certificados.fecha_firma       IS 'Timestamp UTC de cuando se firmó';

-- Índice para consultas por estado de firma
CREATE INDEX IF NOT EXISTS idx_ades_cert_estado_firma
  ON ades_certificados(estado_firma)
  WHERE estado_firma != 'FIRMADO';

-- ── 2. Tabla de llaves de firma del Instituto ────────────────────────────────
CREATE TABLE IF NOT EXISTS ades_llaves_firma (
  id               UUID         NOT NULL DEFAULT uuidv7() PRIMARY KEY,
  nombre           TEXT         NOT NULL,
  descripcion      TEXT,
  algoritmo        VARCHAR(20)  NOT NULL DEFAULT 'Ed25519',
  clave_publica_b64 TEXT        NOT NULL,
  activa           BOOLEAN      NOT NULL DEFAULT TRUE,
  fecha_activacion TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  fecha_expiracion TIMESTAMPTZ,
  creada_por_id    UUID REFERENCES ades_usuarios(id),
  ref              UUID         NOT NULL UNIQUE DEFAULT uuidv7(),
  is_active        BOOLEAN      NOT NULL DEFAULT TRUE,
  fecha_creacion   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  fecha_modificacion TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  usuario_creacion VARCHAR(150) NOT NULL DEFAULT CURRENT_USER,
  usuario_modificacion VARCHAR(150) NOT NULL DEFAULT CURRENT_USER,
  row_version      INTEGER      NOT NULL DEFAULT 1
);

COMMENT ON TABLE  ades_llaves_firma IS 'Inventario de llaves públicas de firma del Instituto Nevadi';
COMMENT ON COLUMN ades_llaves_firma.clave_publica_b64 IS 'Llave pública Ed25519 en base64 estándar';
COMMENT ON COLUMN ades_llaves_firma.activa            IS 'Solo la llave activa se usa para firmar nuevos docs';

-- Trigger de auditoría
DO $$ BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_trigger WHERE tgname = 'trg_aud_biu'
      AND tgrelid = 'ades_llaves_firma'::regclass
  ) THEN
    CREATE TRIGGER trg_aud_biu
      BEFORE INSERT OR UPDATE ON ades_llaves_firma
      FOR EACH ROW EXECUTE FUNCTION auditoria.fn_auditoria_biu();
  END IF;
END $$;

-- FK de ades_certificados → ades_llaves_firma (diferida para no romper rows existentes)
DO $$ BEGIN
  BEGIN
    ALTER TABLE ades_certificados
      ADD CONSTRAINT fk_cert_llave_firma
      FOREIGN KEY (clave_publica_ref) REFERENCES ades_llaves_firma(id)
      DEFERRABLE INITIALLY DEFERRED;
  EXCEPTION WHEN duplicate_object THEN NULL;
  END;
END $$;

-- ── 3. Actualizar CHECK para estado_firma ───────────────────────────────────
DO $$ BEGIN
  BEGIN
    ALTER TABLE ades_certificados
      ADD CONSTRAINT chk_estado_firma
      CHECK (estado_firma IN ('PENDIENTE', 'FIRMADO', 'REVOCADO'));
  EXCEPTION WHEN duplicate_object THEN NULL;
  END;
END $$;

-- ── 4. Vista de certificados con estado de verificación ─────────────────────
CREATE OR REPLACE VIEW ades_v_certificados_verificacion AS
SELECT
  c.folio,
  c.tipo_certificado,
  c.nivel_educativo,
  c.grado_completado,
  c.promedio_final,
  c.fecha_emision,
  c.vigente,
  c.estado_firma,
  c.fecha_firma,
  c.verificable_url,
  c.hash_sha256,
  lf.nombre            AS llave_nombre,
  lf.clave_publica_b64 AS llave_publica,
  CONCAT(per.nombre, ' ', per.apellido_paterno,
         CASE WHEN per.apellido_materno IS NOT NULL THEN ' ' || per.apellido_materno ELSE '' END
  )                    AS alumno_nombre,
  per.curp             AS alumno_curp,
  p.nombre_plantel     AS plantel_nombre,
  ce.nombre_ciclo      AS ciclo_nombre
FROM ades_certificados c
  LEFT JOIN ades_llaves_firma lf ON lf.id  = c.clave_publica_ref
  JOIN ades_estudiantes e        ON e.id   = c.estudiante_id
  JOIN ades_personas per         ON per.id = e.persona_id
  JOIN ades_planteles p          ON p.id   = e.plantel_id
  JOIN ades_ciclos_escolares ce  ON ce.id  = c.ciclo_escolar_id
WHERE c.is_active = TRUE;

COMMENT ON VIEW ades_v_certificados_verificacion
  IS 'Vista pública de certificados con datos de verificación (usada por endpoint GET /verificar/{folio})';

-- ── 5. Función PL/pgSQL para revocar certificado ────────────────────────────
CREATE OR REPLACE FUNCTION revocar_certificado(p_folio TEXT, p_motivo TEXT DEFAULT NULL)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
  UPDATE ades_certificados
    SET estado_firma        = 'REVOCADO',
        datos_adicionales   = COALESCE(datos_adicionales, '{}'::jsonb)
                              || jsonb_build_object('motivo_revocacion', p_motivo,
                                                    'fecha_revocacion', NOW()),
        fecha_modificacion  = NOW(),
        row_version         = row_version + 1
  WHERE folio = p_folio AND is_active = TRUE;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'Certificado con folio % no encontrado', p_folio;
  END IF;
END;
$$;

COMMENT ON FUNCTION revocar_certificado
  IS 'Revoca un certificado. Solo ADMIN_GLOBAL puede invocarla.';
