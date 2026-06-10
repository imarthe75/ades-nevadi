-- Migración: 025_estandarizacion_nombres.sql
-- Propósito: Renombrar las columnas heredadas fccreacion y fcmodificacion
-- por sus nombres estándar en snake_case (fecha_creacion, fecha_modificacion) en TODAS las tablas existentes.
-- Fecha: 2026-06-10

DO $$ 
DECLARE
    r RECORD;
BEGIN
    -- Renombrar fccreacion a fecha_creacion
    FOR r IN
        SELECT table_schema, table_name, column_name
        FROM information_schema.columns
        WHERE table_schema IN ('public', 'ades_bi') AND column_name = 'fccreacion'
    LOOP
        EXECUTE format('ALTER TABLE %I.%I RENAME COLUMN fccreacion TO fecha_creacion;', r.table_schema, r.table_name);
        RAISE NOTICE 'Renombrada fccreacion a fecha_creacion en %.%', r.table_schema, r.table_name;
    END LOOP;

    -- Renombrar fcmodificacion a fecha_modificacion
    FOR r IN
        SELECT table_schema, table_name, column_name
        FROM information_schema.columns
        WHERE table_schema IN ('public', 'ades_bi') AND column_name = 'fcmodificacion'
    LOOP
        EXECUTE format('ALTER TABLE %I.%I RENAME COLUMN fcmodificacion TO fecha_modificacion;', r.table_schema, r.table_name);
        RAISE NOTICE 'Renombrada fcmodificacion a fecha_modificacion en %.%', r.table_schema, r.table_name;
    END LOOP;
END $$;
