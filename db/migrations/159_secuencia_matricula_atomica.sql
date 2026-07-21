-- =============================================================================
-- 159_secuencia_matricula_atomica.sql
-- Hallazgo H-5 de auditoría externa (2026-07-20), verificado y ampliado: existían
-- 3 generadores de matrícula independientes y racy en Java —
--   1) AlumnoPersistenceAdapter.generarSiguienteMatricula(): SELECT MAX(...)+1
--      (lectura-luego-escritura, no atómico).
--   2) ImportsController: contador local en memoria (seq++/seq--) por request,
--      no compartido entre imports concurrentes.
--   3) ProcesosPersistenceAdapter: Random().nextInt(900000), sin verificación.
-- La UNIQUE constraint en ades_estudiantes.matricula evita que se persista un
-- duplicado silencioso, pero bajo concurrencia real (alta de alumnos en horario
-- pico de inscripción, 3 planteles) uno de los dos inserts colisionantes fallaría
-- con una excepción de constraint violation cruda en vez de generar un id único
-- de entrada. Esta secuencia de Postgres reemplaza los 3 esquemas por una sola
-- fuente atómica (nextval() nunca repite valor, incluso con cientos de llamadas
-- concurrentes) — ver AlumnoPersistenceAdapter/ImportsController/
-- ProcesosPersistenceAdapter tras esta migración.
-- No se modifica el formato "MAT-NNNNNN" ya usado por esos 3 puntos (decisión de
-- formato de matrícula, fuera de alcance de esta corrección puramente técnica).
-- =============================================================================

BEGIN;

CREATE SEQUENCE IF NOT EXISTS ades_estudiantes_matricula_seq START WITH 1;

COMMIT;
