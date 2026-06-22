-- ============================================================================
-- 086_periodos_trimestres_nem.sql
-- Corrige los periodos de evaluación SEP: reemplaza el modelo viejo de bimestres
-- por los 3 TRIMESTRES oficiales de la Nueva Escuela Mexicana (NEM) para primaria
-- y secundaria. Los periodos UAEMEX (parciales/extraordinario/final) NO se tocan.
--
-- Las fechas de cada trimestre se derivan genéricamente del rango del ciclo
-- (fecha_inicio..fecha_fin), partido en tres tercios. fecha_entrega_boletas se fija
-- ~5 días después del cierre de cada trimestre.
--
-- IDEMPOTENTE y NO-destructivo de currícula: solo borra periodos de ciclos SEP.
-- ADVERTENCIA: debe ejecutarse cuando NO existan filas operativas que referencien
-- esos periodos (calificaciones_periodo, evaluaciones, tareas). El pipeline
-- seed004 lo ejecuta justo después del wipe operativo.
-- ============================================================================
BEGIN;

-- 1. Eliminar periodos existentes de los ciclos SEP (bimestres viejos)
DELETE FROM ades_periodos_evaluacion pe
 USING ades_ciclos_escolares ce
 WHERE pe.ciclo_escolar_id = ce.id
   AND ce.sistema_educativo = 'SEP';

-- 2. Insertar 3 trimestres NEM por cada ciclo SEP
INSERT INTO ades_periodos_evaluacion
    (id, nombre_periodo, numero_periodo, tipo_periodo, ciclo_escolar_id,
     fecha_inicio, fecha_fin, fecha_entrega_boletas, is_active,
     usuario_creacion, usuario_modificacion)
SELECT
    uuidv7(),
    t.nombre,
    t.num,
    'ORDINARIO',
    ce.id,
    t.ini,
    t.fin,
    t.fin + INTERVAL '5 days',
    TRUE,
    'mig086', 'mig086'
FROM ades_ciclos_escolares ce
CROSS JOIN LATERAL (
    -- tercios del ciclo escolar
    SELECT ce.fecha_inicio                                                        AS ciclo_ini,
           ce.fecha_fin                                                           AS ciclo_fin,
           (ce.fecha_fin - ce.fecha_inicio)                                       AS span
) s
CROSS JOIN LATERAL (
    VALUES
      (1, '1er Trimestre',
          s.ciclo_ini,
          s.ciclo_ini + (s.span / 3)),
      (2, '2do Trimestre',
          s.ciclo_ini + (s.span / 3) + 1,
          s.ciclo_ini + (2 * s.span / 3)),
      (3, '3er Trimestre',
          s.ciclo_ini + (2 * s.span / 3) + 1,
          s.ciclo_fin)
) AS t(num, nombre, ini, fin)
WHERE ce.sistema_educativo = 'SEP';

COMMIT;
