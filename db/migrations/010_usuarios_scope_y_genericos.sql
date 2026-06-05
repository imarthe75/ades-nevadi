-- =============================================================================
-- Migración 010 — Scope de usuarios + usuarios genéricos por escuela
-- =============================================================================

-- ── 1. Columna nivel_educativo_id en ades_usuarios ───────────────────────────
ALTER TABLE ades_usuarios
  ADD COLUMN IF NOT EXISTS nivel_educativo_id UUID
    REFERENCES ades_niveles_educativos(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_usuarios_plantel
  ON ades_usuarios(plantel_id) WHERE plantel_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_usuarios_nivel
  ON ades_usuarios(nivel_educativo_id) WHERE nivel_educativo_id IS NOT NULL;

-- ── 2. Usuarios genéricos de prueba ──────────────────────────────────────────
-- CURP sintético de 18 chars: TGEN + seq(2) + XXXXXXXXXX (para pasar la restricción)
-- oidc_sub = username (sub_mode = user_username en Authentik)
DO $$
DECLARE
  v_estatus UUID := '019e8f74-d13d-73a0-bca3-76c8bf09c681';
  v_rol_ap  UUID := '019e8f74-d13e-785f-9d44-5ba90c43af2a';
  v_rol_dir UUID := '019e8f74-d13e-78fd-bdfb-4893c3151cb7';
  v_ten     UUID := '019e8f74-d143-7368-a0b6-06cc2fbc7156';
  v_met     UUID := '019e8f74-d142-7c91-8b82-c84464113dad';
  v_ixt     UUID := '019e8f74-d143-740c-aa16-63a83c575d92';
  v_pri     UUID := '019e8f74-d13f-7052-9890-b128df7ea199';
  v_sec     UUID := '019e8f74-d13f-77e5-aeb8-e859b106072c';
  v_prep    UUID := '019e8f74-d13f-788e-8ed6-99c4825b22c8';
  v_pid     UUID;
  r         RECORD;
BEGIN
  CREATE TEMP TABLE _ug (
    seq     INT,
    uname   TEXT,
    nombre  TEXT,
    ap_pat  TEXT,
    email   TEXT,
    rol     UUID,
    plantel UUID,
    nivel   UUID
  ) ON COMMIT DROP;

  INSERT INTO _ug VALUES
  (1,  'admin.tenancingo',     'Admin','Tenancingo',      'admin.tenancingo@institutonevadi.edu.mx',    v_rol_ap,  v_ten, NULL),
  (2,  'admin.metepec',        'Admin','Metepec',         'admin.metepec@institutonevadi.edu.mx',       v_rol_ap,  v_met, NULL),
  (3,  'admin.ixtapan',        'Admin','Ixtapan',         'admin.ixtapan@institutonevadi.edu.mx',       v_rol_ap,  v_ixt, NULL),
  (4,  'dir.ten.primaria',     'Dir','Ten-Primaria',      'dir.ten.pri@institutonevadi.edu.mx',         v_rol_dir, v_ten, v_pri),
  (5,  'dir.ten.secundaria',   'Dir','Ten-Secundaria',    'dir.ten.sec@institutonevadi.edu.mx',         v_rol_dir, v_ten, v_sec),
  (6,  'dir.ten.preparatoria', 'Dir','Ten-Preparatoria',  'dir.ten.prep@institutonevadi.edu.mx',        v_rol_dir, v_ten, v_prep),
  (7,  'dir.met.primaria',     'Dir','Met-Primaria',      'dir.met.pri@institutonevadi.edu.mx',         v_rol_dir, v_met, v_pri),
  (8,  'dir.met.secundaria',   'Dir','Met-Secundaria',    'dir.met.sec@institutonevadi.edu.mx',         v_rol_dir, v_met, v_sec),
  (9,  'dir.met.preparatoria', 'Dir','Met-Preparatoria',  'dir.met.prep@institutonevadi.edu.mx',        v_rol_dir, v_met, v_prep),
  (10, 'dir.ixt.primaria',     'Dir','Ixt-Primaria',      'dir.ixt.pri@institutonevadi.edu.mx',         v_rol_dir, v_ixt, v_pri),
  (11, 'dir.ixt.secundaria',   'Dir','Ixt-Secundaria',    'dir.ixt.sec@institutonevadi.edu.mx',         v_rol_dir, v_ixt, v_sec),
  (12, 'dir.ixt.preparatoria', 'Dir','Ixt-Preparatoria',  'dir.ixt.prep@institutonevadi.edu.mx',        v_rol_dir, v_ixt, v_prep);

  FOR r IN SELECT * FROM _ug ORDER BY seq LOOP
    -- CURP sintético único de 18 chars: TGEN + seq 2 digs + 12 ceros
    INSERT INTO ades_personas (nombre, apellido_paterno, curp)
    VALUES (r.nombre, r.ap_pat, 'TGEN' || lpad(r.seq::text, 2, '0') || 'XXXXXXXXXXXX')
    ON CONFLICT (curp) DO NOTHING
    RETURNING id INTO v_pid;

    IF v_pid IS NULL THEN
      SELECT id INTO v_pid FROM ades_personas
      WHERE curp = 'TGEN' || lpad(r.seq::text, 2, '0') || 'XXXXXXXXXXXX';
    END IF;

    INSERT INTO ades_usuarios (
      nombre_usuario, email_institucional, oidc_sub,
      persona_id, rol_id, plantel_id, nivel_educativo_id, estatus_id
    ) VALUES (
      r.uname, r.email, r.uname,
      v_pid, r.rol, r.plantel, r.nivel, v_estatus
    ) ON CONFLICT (nombre_usuario) DO UPDATE
      SET plantel_id         = EXCLUDED.plantel_id,
          nivel_educativo_id = EXCLUDED.nivel_educativo_id,
          oidc_sub           = EXCLUDED.oidc_sub;

    RAISE NOTICE 'OK: %', r.uname;
  END LOOP;
END $$;
