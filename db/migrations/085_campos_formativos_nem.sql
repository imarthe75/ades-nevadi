-- =============================================================================
-- Migración: 085_campos_formativos_nem.sql
-- Descripción: Agrega columna campo_formativo a ades_materias con los 4 campos
--              formativos de la Nueva Escuela Mexicana (NEM) SEP: LENGUAJES,
--              SABERES_PENSAMIENTO_CIENTIFICO, ETICA_NATURALEZA_SOCIEDADES,
--              HUMANO_COMUNITARIO. Se puebla automáticamente para materias SEP
--              mediante patrones de nombre. Prerequisito para la boleta oficial NEM.
-- Tablas afectadas: ades_materias
-- Dependencias: ades_materias_plan, ades_grados, ades_niveles_educativos
-- Autor: ADES
-- Fecha: 2026-06
-- =============================================================================

-- =============================================================================
-- MIGRACIÓN 085 — Campos Formativos NEM en materias (prerequisito boleta oficial)
-- -----------------------------------------------------------------------------
-- La boleta oficial NEM (educación básica SEP) agrupa las disciplinas en 4
-- Campos Formativos. En primaria la evaluación es directamente por campo; en
-- secundaria las disciplinas se agrupan en ellos. Se agrega `campo_formativo`
-- a ades_materias y se puebla por nombre, solo para materias usadas en SEP
-- (UAEMEX/prep no usa NEM → queda NULL).
-- =============================================================================

ALTER TABLE ades_materias
    ADD COLUMN IF NOT EXISTS campo_formativo VARCHAR(40);

ALTER TABLE ades_materias
    DROP CONSTRAINT IF EXISTS ck_materias_campo_formativo;
ALTER TABLE ades_materias
    ADD  CONSTRAINT ck_materias_campo_formativo CHECK (
        campo_formativo IS NULL OR campo_formativo IN (
            'LENGUAJES',
            'SABERES_PENSAMIENTO_CIENTIFICO',
            'ETICA_NATURALEZA_SOCIEDADES',
            'HUMANO_COMUNITARIO'
        )
    );

COMMENT ON COLUMN ades_materias.campo_formativo IS
    'Campo Formativo NEM (educación básica SEP). NULL para materias no-NEM (UAEMEX).';

-- Poblar por nombre, solo materias usadas en niveles SEP. El orden de los CASE
-- importa: "Educación Física" antes que "Física"; "Ciencias Naturales y
-- Tecnología" cae en ciencias antes que el "Tecnología" suelto de Humano.
UPDATE ades_materias m
SET campo_formativo = CASE
    WHEN lower(m.nombre_materia) ~ 'educaci[oó]n f[ií]sica'
        THEN 'HUMANO_COMUNITARIO'
    WHEN lower(m.nombre_materia) ~ 'saberes y pensamiento|matem[aá]ticas|ciencias naturales|biolog[ií]a|qu[ií]mica|(^| )f[ií]sica'
        THEN 'SABERES_PENSAMIENTO_CIENTIFICO'
    WHEN lower(m.nombre_materia) ~ 'lenguaje|espa[ñn]ol|ingl[eé]s|artes|lengua materna'
        THEN 'LENGUAJES'
    WHEN lower(m.nombre_materia) ~ '[eé]tica, naturaleza|historia|geograf[ií]a|formaci[oó]n c[ií]vica'
        THEN 'ETICA_NATURALEZA_SOCIEDADES'
    WHEN lower(m.nombre_materia) ~ 'de lo humano|^tecnolog[ií]a|tutor[ií]a|socioemocional|inform[aá]tica|orientaci[oó]n|proyect|vida saludable'
        THEN 'HUMANO_COMUNITARIO'
    ELSE m.campo_formativo
END
WHERE m.id IN (
    SELECT DISTINCT mp.materia_id
    FROM ades_materias_plan mp
    JOIN ades_grados gr            ON gr.id = mp.grado_id
    JOIN ades_niveles_educativos n ON n.id = gr.nivel_educativo_id
    WHERE n.autoridad_educativa = 'SEP'
);

CREATE INDEX IF NOT EXISTS idx_materias_campo_formativo ON ades_materias(campo_formativo);
