-- =============================================================================
-- Migración: 103_plantel_nivel_clave.sql
-- Descripción: Corrige la representación de la Clave de Centro de Trabajo (CCT) —
--              hasta ahora `ades_planteles.clave_ct` guardaba valores placeholder
--              (MET-NVD-001, etc., no CCT oficiales) y solo un valor por plantel
--              físico. En México el CCT se asigna por nivel educativo + turno, no
--              por plantel — un mismo plantel con Primaria y Secundaria tiene dos
--              CCT distintos. Preparatoria (UAEMEX, no SEP) usa un código de
--              incorporación/RVOE en vez de CCT.
--              Se agrega una tabla plantel+nivel para representar esto
--              correctamente, sin tocar `ades_planteles.clave_ct` (queda
--              deprecado pero no se elimina, por si algún reporte lo referencia).
-- Tablas afectadas: ades_plantel_nivel_clave (nueva)
-- Dependencias: ades_planteles, ades_niveles_educativos, auditoria.asignar_biu()
-- Autor: ADES
-- Fecha: 2026-07-03
-- =============================================================================

CREATE TABLE IF NOT EXISTS ades_plantel_nivel_clave (
    id                   UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    plantel_id           UUID NOT NULL REFERENCES ades_planteles(id),
    nivel_educativo_id   UUID NOT NULL REFERENCES ades_niveles_educativos(id),
    tipo_clave           TEXT NOT NULL DEFAULT 'CCT_SEP'
                           CHECK (tipo_clave IN ('CCT_SEP', 'INCORPORACION_UAEMEX')),
    clave                VARCHAR(20),
    vigente_desde        DATE,
    observaciones        TEXT,
    is_active            BOOLEAN NOT NULL DEFAULT TRUE,
    ref                  UUID,
    row_version          INTEGER,
    fecha_creacion       TIMESTAMPTZ,
    fecha_modificacion   TIMESTAMPTZ,
    usuario_creacion     TEXT,
    usuario_modificacion TEXT,
    CONSTRAINT uq_plantel_nivel_tipo_clave UNIQUE (plantel_id, nivel_educativo_id, tipo_clave)
);

COMMENT ON TABLE ades_plantel_nivel_clave IS
    'Clave oficial (CCT SEP o incorporación UAEMEX) por combinación plantel+nivel educativo. Reemplaza el uso de ades_planteles.clave_ct (deprecado, un solo valor por plantel físico, insuficiente cuando un plantel tiene varios niveles con CCT distintos).';

SELECT auditoria.asignar_biu('public.ades_plantel_nivel_clave');

COMMENT ON COLUMN ades_planteles.clave_ct IS
    'DEPRECADO — contenía valores placeholder, no CCT oficiales. Usar ades_plantel_nivel_clave (una fila por nivel educativo). Se conserva sin modificar por compatibilidad con reportes existentes.';

-- =============================================================================
-- Seed: CCT SEP reales de Instituto Nevadi, verificados 2026-07-03 mediante
-- múltiples directorios educativos públicos independientes (escuelasmex.com,
-- escuelas-mexico.com, edunautica.mx, pequenautica.com — todos coinciden).
-- Preparatoria (UAEMEX) queda con clave NULL — el Instituto debe proporcionar
-- el código de incorporación/RVOE oficial (no indexado en directorios públicos).
-- =============================================================================

INSERT INTO ades_plantel_nivel_clave (plantel_id, nivel_educativo_id, tipo_clave, clave, observaciones)
SELECT p.id, ne.id, 'CCT_SEP', v.clave, NULL
FROM (VALUES
    ('Metepec',           'PRIMARIA',     '15PPR7068F'),
    ('Metepec',           'SECUNDARIA',   '15PES0124F'),
    ('Tenancingo',        'PRIMARIA',     '15PPR7106S'),
    ('Tenancingo',        'SECUNDARIA',   '15PES0143U'),
    ('Ixtapan de la Sal', 'PRIMARIA',     '15PPR0088Y'),
    ('Ixtapan de la Sal', 'SECUNDARIA',   '15PES0169B')
) AS v(nombre_plantel, nombre_nivel, clave)
JOIN ades_planteles p ON p.nombre_plantel = v.nombre_plantel
JOIN ades_niveles_educativos ne ON ne.nombre_nivel = v.nombre_nivel
ON CONFLICT (plantel_id, nivel_educativo_id, tipo_clave) DO NOTHING;

INSERT INTO ades_plantel_nivel_clave (plantel_id, nivel_educativo_id, tipo_clave, clave, observaciones)
SELECT p.id, ne.id, 'INCORPORACION_UAEMEX', NULL,
       'Pendiente — solicitar oficio de incorporación UAEMEX al Instituto (no se encontró en registros públicos)'
FROM ades_planteles p
JOIN ades_grados gr ON gr.plantel_id = p.id
JOIN ades_niveles_educativos ne ON ne.id = gr.nivel_educativo_id AND ne.nombre_nivel = 'PREPARATORIA'
GROUP BY p.id, ne.id
ON CONFLICT (plantel_id, nivel_educativo_id, tipo_clave) DO NOTHING;
