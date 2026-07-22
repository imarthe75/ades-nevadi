-- 164_ajuste_matricula_prepa_realista.sql
-- Ajusta la matrícula DEMO de Preparatoria del ciclo vigente 26B a la realidad
-- declarada por el instituto (2026-07-22):
--   • Metepec  → cohorte único en 3er semestre (ya cursó 1º y 2º en ciclos previos).
--   • Tenancingo → plantel nuevo, arranca con 1er semestre únicamente.
-- El generador de simulación había poblado los 6 semestres × 52 alumnos en ambos
-- planteles, lo que no refleja que son incorporaciones recientes aún en crecimiento.
--
-- Alcance: SOLO el ciclo vigente 26B. La historia (25B) se deja intacta (decisión de
-- producto: la correspondencia ciclo↔semestre histórica es ambigua —falta 26A— y se
-- confirmará aparte). Datos de prueba: autorizado eliminar (usuario, 2026-07-22).
--
-- Radio verificado: los grupos de prepa 26B no tienen horarios/clases/calificaciones/
-- asignaciones docentes (0 en todas). El único hijo con datos es ades_inscripciones.
-- Se ejecuta en transacción: si apareciera un hijo inesperado (FK NO ACTION), aborta
-- sin pérdida.

BEGIN;

-- Grupos de prepa del ciclo vigente 26B, con su plantel y semestre.
CREATE TEMP TABLE _pg26b ON COMMIT DROP AS
SELECT gr.id AS grupo_id, gr.nombre_grupo, p.nombre_plantel, g.numero_grado AS sem
FROM ades_grupos gr
JOIN ades_grados g ON g.id = gr.grado_id
JOIN ades_planteles p ON p.id = g.plantel_id
JOIN ades_ciclos_escolares c ON c.id = gr.ciclo_escolar_id
JOIN ades_niveles_educativos ne ON ne.id = c.nivel_educativo_id
WHERE ne.nombre_nivel = 'PREPARATORIA' AND c.nombre_ciclo = '26B';

-- 1) TENANCINGO: reubica a los alumnos del 2º semestre hacia el 1º (parea A→A, B→B),
--    para poblar el 1er semestre (que estaba vacío) con una cohorte real en vez de
--    crear alumnos nuevos. El resto de sus semestres (3º-6º) se elimina más abajo.
UPDATE ades_inscripciones i
SET grupo_id = d.grupo_id
FROM _pg26b s
JOIN _pg26b d
  ON d.nombre_plantel = s.nombre_plantel
 AND d.sem = 1
 AND d.nombre_grupo = s.nombre_grupo
WHERE s.nombre_plantel = 'Tenancingo'
  AND s.sem = 2
  AND i.grupo_id = s.grupo_id;

-- 2) Borra las inscripciones de los semestres que NO deben existir en 26B:
--    Metepec: todo lo que no sea 3º ; Tenancingo: todo lo que no sea 1º
--    (las de Tenancingo 2º ya se movieron a 1º, así que no caen aquí).
DELETE FROM ades_inscripciones i
USING _pg26b x
WHERE i.grupo_id = x.grupo_id
  AND ( (x.nombre_plantel = 'Metepec'    AND x.sem <> 3)
     OR (x.nombre_plantel = 'Tenancingo' AND x.sem <> 1) );

-- 3) Borra los grupos ya vacíos de esos semestres.
DELETE FROM ades_grupos gr
USING _pg26b x
WHERE gr.id = x.grupo_id
  AND ( (x.nombre_plantel = 'Metepec'    AND x.sem <> 3)
     OR (x.nombre_plantel = 'Tenancingo' AND x.sem <> 1) );

COMMIT;
