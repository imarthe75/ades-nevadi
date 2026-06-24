-- =============================================================================
-- Migración: 084_biblioteca.sql
-- Descripción: Crea el módulo de biblioteca escolar: catálogo de libros por título
--              (con conteo de ejemplares total/disponibles) y tabla de circulación
--              de préstamos. El decremento de ejemplares_disponibles es atómico.
--              Scoping por plantel_id para acceso multi-plantel.
-- Tablas afectadas: ades_biblioteca_libros, ades_biblioteca_prestamos
-- Dependencias: ades_planteles, ades_personas, auditoria.asignar_biu()
-- Autor: ADES
-- Fecha: 2026-06
-- =============================================================================

-- =============================================================================
-- MIGRACIÓN 084 — Biblioteca: acervo (catálogo) + circulación (préstamos)
-- -----------------------------------------------------------------------------
-- Modelo pragmático (escala escolar, mantenibilidad > granularidad):
--   * ades_biblioteca_libros: un registro por título, con conteo de ejemplares
--     (total / disponibles) en vez de un registro por copia física.
--   * ades_biblioteca_prestamos: circulación; al prestar se decrementa
--     ejemplares_disponibles, al devolver se incrementa.
-- Scoping por plantel_id (no-admins solo ven su plantel). Auditoría v2 (biu).
-- =============================================================================

-- Catálogo / acervo -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS ades_biblioteca_libros (
    id                     UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    titulo                 TEXT    NOT NULL,
    autor                  TEXT,
    isbn                   VARCHAR(20),
    editorial              TEXT,
    anio_publicacion       INTEGER CHECK (anio_publicacion IS NULL OR anio_publicacion BETWEEN 1400 AND 2200),
    categoria              TEXT    NOT NULL DEFAULT 'OTRO'
                             CHECK (categoria IN ('LITERATURA','CIENCIA','HISTORIA','MATEMATICAS',
                                                  'ARTE','TECNOLOGIA','INFANTIL','CONSULTA','TEXTO','OTRO')),
    ubicacion              TEXT,                                   -- estante / sección
    plantel_id             UUID    REFERENCES ades_planteles(id),
    ejemplares_total       INTEGER NOT NULL DEFAULT 1 CHECK (ejemplares_total >= 0),
    ejemplares_disponibles INTEGER NOT NULL DEFAULT 1 CHECK (ejemplares_disponibles >= 0),
    is_active              BOOLEAN NOT NULL DEFAULT TRUE,
    ref                    UUID,
    row_version            INTEGER,
    fecha_creacion         TIMESTAMPTZ,
    fecha_modificacion     TIMESTAMPTZ,
    usuario_creacion       TEXT,
    usuario_modificacion   TEXT,
    CONSTRAINT ck_biblio_disponibles_le_total CHECK (ejemplares_disponibles <= ejemplares_total)
);

-- Circulación / préstamos -----------------------------------------------------
CREATE TABLE IF NOT EXISTS ades_biblioteca_prestamos (
    id                        UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    libro_id                  UUID NOT NULL REFERENCES ades_biblioteca_libros(id),
    persona_id                UUID NOT NULL REFERENCES ades_personas(id),  -- alumno o personal
    plantel_id                UUID REFERENCES ades_planteles(id),
    fecha_prestamo            DATE NOT NULL DEFAULT CURRENT_DATE,
    fecha_devolucion_esperada DATE NOT NULL,
    fecha_devolucion_real     DATE,
    estatus                   TEXT NOT NULL DEFAULT 'PRESTADO'
                                CHECK (estatus IN ('PRESTADO','DEVUELTO','VENCIDO','EXTRAVIADO')),
    observaciones             TEXT,
    is_active                 BOOLEAN NOT NULL DEFAULT TRUE,
    ref                       UUID,
    row_version               INTEGER,
    fecha_creacion            TIMESTAMPTZ,
    fecha_modificacion        TIMESTAMPTZ,
    usuario_creacion          TEXT,
    usuario_modificacion      TEXT,
    CONSTRAINT ck_biblio_fechas CHECK (fecha_devolucion_esperada >= fecha_prestamo)
);

-- Índices ---------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_biblio_libros_plantel   ON ades_biblioteca_libros(plantel_id);
CREATE INDEX IF NOT EXISTS idx_biblio_libros_categoria ON ades_biblioteca_libros(categoria);
CREATE INDEX IF NOT EXISTS idx_biblio_libros_titulo    ON ades_biblioteca_libros(lower(titulo));
CREATE INDEX IF NOT EXISTS idx_biblio_prest_libro      ON ades_biblioteca_prestamos(libro_id);
CREATE INDEX IF NOT EXISTS idx_biblio_prest_persona    ON ades_biblioteca_prestamos(persona_id);
CREATE INDEX IF NOT EXISTS idx_biblio_prest_estatus    ON ades_biblioteca_prestamos(estatus);
CREATE INDEX IF NOT EXISTS idx_biblio_prest_plantel    ON ades_biblioteca_prestamos(plantel_id);

-- Auditoría v2 (DEV: solo biu) ------------------------------------------------
SELECT auditoria.asignar_biu('public.ades_biblioteca_libros');
SELECT auditoria.asignar_biu('public.ades_biblioteca_prestamos');

COMMENT ON TABLE ades_biblioteca_libros    IS 'Acervo bibliográfico: un registro por título con conteo de ejemplares.';
COMMENT ON TABLE ades_biblioteca_prestamos IS 'Circulación: préstamos y devoluciones; afecta ejemplares_disponibles del libro.';
COMMENT ON COLUMN ades_biblioteca_libros.ejemplares_disponibles IS 'Copias libres; se decrementa al prestar y se incrementa al devolver.';
