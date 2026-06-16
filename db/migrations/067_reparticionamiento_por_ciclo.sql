-- ============================================================
-- Migración 067: Re-particionamiento por ciclo escolar
-- SPRINT 5 — ADES Instituto Nevadi
-- ============================================================
-- MOTIVO: Mig 066 usó particiones por año calendario (ene-dic).
--   Los ciclos escolares Nevadi corren de agosto a julio,
--   cruzando dos años calendario. Una consulta por ciclo
--   completo requería escanear DOS particiones.
--   Esta migración alinea los rangos a "agosto a agosto".
--
-- PARTICIÓN CICLO:  FROM YYYY-08-01  TO  (YYYY+1)-08-01
-- Ejm: ciclo 2026-2027 → 2026-08-01 a 2027-08-01
--
-- DATOS ACTUALES:
--   ades_asistencias_2026         → 180 000 filas (fecha_creacion ≈ jun 2026)
--   ades_calificaciones_periodo_2026 → 76 320 filas (idem)
--   Todas las demás particiones → 0 filas.
--
-- MAPEO FECHA → CICLO:
--   fecha_creacion 2025-08-01..2026-07-31  → ciclo_2025_2026
--   fecha_creacion 2026-08-01..2027-07-31  → ciclo_2026_2027
--   fecha_creacion 2026-01-01..2026-07-31  → ciclo_2025_2026  (datos semilla)
-- ============================================================

BEGIN;

-- ============================================================
-- 1. ades_asistencias
-- ============================================================

-- 1.1 Detach particiones con datos
ALTER TABLE ades_asistencias DETACH PARTITION ades_asistencias_2026;

-- 1.2 Drop particiones vacías
DROP TABLE ades_asistencias_2025;
DROP TABLE ades_asistencias_2027;
DROP TABLE ades_asistencias_2028;
DROP TABLE ades_asistencias_default;

-- 1.3 Crear particiones por ciclo escolar (agosto-agosto)
CREATE TABLE ades_asistencias_ciclo_2024_2025
    PARTITION OF ades_asistencias
    FOR VALUES FROM ('2024-08-01 00:00:00+00') TO ('2025-08-01 00:00:00+00');

CREATE TABLE ades_asistencias_ciclo_2025_2026
    PARTITION OF ades_asistencias
    FOR VALUES FROM ('2025-08-01 00:00:00+00') TO ('2026-08-01 00:00:00+00');

CREATE TABLE ades_asistencias_ciclo_2026_2027
    PARTITION OF ades_asistencias
    FOR VALUES FROM ('2026-08-01 00:00:00+00') TO ('2027-08-01 00:00:00+00');

CREATE TABLE ades_asistencias_ciclo_2027_2028
    PARTITION OF ades_asistencias
    FOR VALUES FROM ('2027-08-01 00:00:00+00') TO ('2028-08-01 00:00:00+00');

CREATE TABLE ades_asistencias_ciclo_2028_2029
    PARTITION OF ades_asistencias
    FOR VALUES FROM ('2028-08-01 00:00:00+00') TO ('2029-08-01 00:00:00+00');

CREATE TABLE ades_asistencias_default
    PARTITION OF ades_asistencias DEFAULT;

-- 1.4 Reinsertar datos desde partición legacy al parent particionado
--     Deshabilitar triggers para evitar recalcular calificaciones durante la migración.
--     Los datos ya existen en calificaciones; solo reposicionamos particiones.
ALTER TABLE ades_asistencias DISABLE TRIGGER ALL;

INSERT INTO ades_asistencias
    SELECT * FROM ades_asistencias_2026;

ALTER TABLE ades_asistencias ENABLE TRIGGER ALL;

-- 1.5 Verificar conteo (debe coincidir con origen)
DO $$
DECLARE
    orig  BIGINT;
    dest  BIGINT;
BEGIN
    SELECT COUNT(*) INTO orig FROM ades_asistencias_2026;
    SELECT COUNT(*) INTO dest FROM ades_asistencias;
    IF orig <> dest THEN
        RAISE EXCEPTION 'Count mismatch: origin=% dest=%', orig, dest;
    END IF;
    RAISE NOTICE 'ades_asistencias: % filas migradas OK', dest;
END $$;

-- 1.6 Drop partición legacy (ya no necesaria)
DROP TABLE ades_asistencias_2026;

-- ============================================================
-- 2. ades_calificaciones_periodo
-- ============================================================

-- 2.1 Detach partición con datos
ALTER TABLE ades_calificaciones_periodo DETACH PARTITION ades_calificaciones_periodo_2026;

-- 2.2 Drop vacías
DROP TABLE ades_calificaciones_periodo_2025;
DROP TABLE ades_calificaciones_periodo_2027;
DROP TABLE ades_calificaciones_periodo_2028;
DROP TABLE ades_calificaciones_periodo_default;

-- 2.3 Crear particiones por ciclo escolar
CREATE TABLE ades_calificaciones_periodo_ciclo_2024_2025
    PARTITION OF ades_calificaciones_periodo
    FOR VALUES FROM ('2024-08-01 00:00:00+00') TO ('2025-08-01 00:00:00+00');

CREATE TABLE ades_calificaciones_periodo_ciclo_2025_2026
    PARTITION OF ades_calificaciones_periodo
    FOR VALUES FROM ('2025-08-01 00:00:00+00') TO ('2026-08-01 00:00:00+00');

CREATE TABLE ades_calificaciones_periodo_ciclo_2026_2027
    PARTITION OF ades_calificaciones_periodo
    FOR VALUES FROM ('2026-08-01 00:00:00+00') TO ('2027-08-01 00:00:00+00');

CREATE TABLE ades_calificaciones_periodo_ciclo_2027_2028
    PARTITION OF ades_calificaciones_periodo
    FOR VALUES FROM ('2027-08-01 00:00:00+00') TO ('2028-08-01 00:00:00+00');

CREATE TABLE ades_calificaciones_periodo_ciclo_2028_2029
    PARTITION OF ades_calificaciones_periodo
    FOR VALUES FROM ('2028-08-01 00:00:00+00') TO ('2029-08-01 00:00:00+00');

CREATE TABLE ades_calificaciones_periodo_default
    PARTITION OF ades_calificaciones_periodo DEFAULT;

-- 2.4 Reinsertar datos
INSERT INTO ades_calificaciones_periodo
    SELECT * FROM ades_calificaciones_periodo_2026;

-- 2.5 Verificar conteo
DO $$
DECLARE
    orig  BIGINT;
    dest  BIGINT;
BEGIN
    SELECT COUNT(*) INTO orig FROM ades_calificaciones_periodo_2026;
    SELECT COUNT(*) INTO dest FROM ades_calificaciones_periodo;
    IF orig <> dest THEN
        RAISE EXCEPTION 'Count mismatch: origin=% dest=%', orig, dest;
    END IF;
    RAISE NOTICE 'ades_calificaciones_periodo: % filas migradas OK', dest;
END $$;

-- 2.6 Drop legacy
DROP TABLE ades_calificaciones_periodo_2026;

-- ============================================================
-- 3. Comentario en tabla padre para documentar la estrategia
-- ============================================================
COMMENT ON TABLE ades_asistencias IS
    'Particionada por ciclo escolar (agosto-agosto). '
    'Particiones: ciclo_YYYY_YYYY+1. '
    'Nuevos ciclos: CREATE TABLE ades_asistencias_ciclo_AAAA_AAAA1 PARTITION OF ades_asistencias FOR VALUES FROM (''AAAA-08-01'') TO (''AAAA1-08-01'').';

COMMENT ON TABLE ades_calificaciones_periodo IS
    'Particionada por ciclo escolar (agosto-agosto). '
    'Particiones: ciclo_YYYY_YYYY+1. '
    'Nuevos ciclos: agregar partición al inicio de cada ciclo escolar.';

COMMIT;
