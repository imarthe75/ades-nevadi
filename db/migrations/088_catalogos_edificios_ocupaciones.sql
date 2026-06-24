-- =============================================================================
-- Migración: 088_catalogos_edificios_ocupaciones.sql
-- Descripción: Siembra los catálogos dinámicos de Edificios (CAT_EDIFICIOS),
--              Pisos/Plantas (CAT_PISOS) y Ocupaciones de padres de familia
--              (CAT_OCUPACIONES) en ades_catalogos y ades_catalogo_items.
--              Idempotente mediante ON CONFLICT DO NOTHING.
-- Tablas afectadas: ades_catalogos, ades_catalogo_items
-- Dependencias: ades_catalogos y ades_catalogo_items existentes (constraint
--               UNIQUE en (catalogo_id, valor))
-- Autor: ADES
-- Fecha: 2026-06
-- =============================================================================

-- =============================================================================
-- MIGRACIÓN 088: Catálogos de Edificios, Pisos y Ocupaciones de Padres
-- =============================================================================

BEGIN;

-- Insertar cabeceras de catálogos si no existen
INSERT INTO ades_catalogos (codigo, nombre, descripcion) VALUES
    ('CAT_EDIFICIOS',   'Edificios', 'Edificios y bloques del plantel educativo'),
    ('CAT_PISOS',       'Pisos / Plantas', 'Niveles de piso en los edificios'),
    ('CAT_OCUPACIONES',  'Ocupaciones', 'Ocupaciones y profesiones de los padres de familia / tutores')
ON CONFLICT (codigo) DO NOTHING;

-- Insertar items para CAT_EDIFICIOS
WITH cat AS (SELECT id FROM ades_catalogos WHERE codigo = 'CAT_EDIFICIOS')
INSERT INTO ades_catalogo_items (catalogo_id, valor, orden) VALUES
    ((SELECT id FROM cat), 'Edificio A', 10),
    ((SELECT id FROM cat), 'Edificio B', 20),
    ((SELECT id FROM cat), 'Edificio C', 30),
    ((SELECT id FROM cat), 'Laboratorios', 40),
    ((SELECT id FROM cat), 'Dirección y Administración', 50),
    ((SELECT id FROM cat), 'Biblioteca', 60),
    ((SELECT id FROM cat), 'Área Deportiva', 70),
    ((SELECT id FROM cat), 'Otro', 99)
ON CONFLICT (catalogo_id, valor) DO NOTHING;

-- Insertar items para CAT_PISOS
WITH cat AS (SELECT id FROM ades_catalogos WHERE codigo = 'CAT_PISOS')
INSERT INTO ades_catalogo_items (catalogo_id, valor, orden) VALUES
    ((SELECT id FROM cat), 'Planta Baja', 10),
    ((SELECT id FROM cat), 'Primer Piso', 20),
    ((SELECT id FROM cat), 'Segundo Piso', 30),
    ((SELECT id FROM cat), 'Tercer Piso', 40),
    ((SELECT id FROM cat), 'Otro', 99)
ON CONFLICT (catalogo_id, valor) DO NOTHING;

-- Insertar items para CAT_OCUPACIONES
WITH cat AS (SELECT id FROM ades_catalogos WHERE codigo = 'CAT_OCUPACIONES')
INSERT INTO ades_catalogo_items (catalogo_id, valor, orden) VALUES
    ((SELECT id FROM cat), 'Empleado del sector privado', 10),
    ((SELECT id FROM cat), 'Servidor público / Gobierno', 20),
    ((SELECT id FROM cat), 'Profesional independiente / Freelance', 30),
    ((SELECT id FROM cat), 'Comerciante / Empresario', 40),
    ((SELECT id FROM cat), 'Ama de casa', 50),
    ((SELECT id FROM cat), 'Trabajador del sector informal', 60),
    ((SELECT id FROM cat), 'Jubilado / Pensionado', 70),
    ((SELECT id FROM cat), 'Estudiante', 80),
    ((SELECT id FROM cat), 'Desempleado', 90),
    ((SELECT id FROM cat), 'Otro', 99)
ON CONFLICT (catalogo_id, valor) DO NOTHING;

COMMIT;
