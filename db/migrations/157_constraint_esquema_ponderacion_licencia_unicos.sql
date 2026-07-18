-- 157_constraint_esquema_ponderacion_licencia_unicos.sql
--
-- Auditoría de llaves únicas faltantes (2026-07-18), continuación del hallazgo de
-- ades_inscripciones (mig. 155/156): mismo patrón — "solo debería haber un X activo por
-- contexto" sin restricción real en base de datos. Escaneo automatizado de todas las
-- tablas ades_* con is_active produjo ~150 falsos positivos (cualquier FK con muchas
-- filas activas relacionadas, ej. muchos ades_tareas por grupo_id — eso es normal, no un
-- bug). Se descartaron por juicio de negocio; solo 2 candidatos reales sobrevivieron la
-- revisión manual:
--
-- 1) ades_esquemas_ponderacion: nada impedía 2+ esquemas de ponderación "activos" y
--    "vigentes" simultáneos para el mismo contexto (nivel_educativo_id + materia_id +
--    plantel_id + profesor_id, siendo estos 3 últimos opcionales/NULL para esquemas
--    generales). Se encontraron 3 pares de duplicados EXACTOS reales (SEP Primaria,
--    SEP Secundaria, UAEMEX Preparatoria — Base), creados 35 minutos aparte el
--    2026-07-12, con los mismos pesos (examen=70, tarea=20, asistencia=10) — indicio
--    claro de un seed que corrió dos veces. Hoy es inofensivo porque los pesos
--    coinciden; pero cualquier código que haga
--    "WHERE nivel_educativo_id = ? AND activo = TRUE" sin desempatar puede, en el
--    futuro, elegir arbitrariamente entre dos esquemas con pesos DISTINTOS y calcular
--    calificaciones oficiales con el esquema equivocado sin ningún error visible — el
--    mismo patrón de falla que produjo los 1,612 alumnos duplicados, solo que aquí
--    afectaría calificaciones en vez de inscripciones. Los NULL en materia_id/
--    plantel_id/profesor_id impiden que un UNIQUE INDEX normal detecte el duplicado
--    (NULL <> NULL en Postgres), así que se usa COALESCE a un UUID centinela.
--
-- 2) ades_licencias_personal: nada impide que la misma persona tenga 2 licencias
--    APROBADAS con fechas traslapadas (ej. médica y personal al mismo tiempo) — doble
--    conteo de días, posible doble pago si con_goce_sueldo, o asignar el mismo
--    sustituto a dos licencias simultáneas sin que el sistema lo note. Auditoría de
--    datos reales: CERO traslapes existen hoy (verificado antes de escribir esta
--    migración), así que no hay reparación de datos que hacer — solo cerrar el hueco
--    estructural antes de que ocurra. Se usa EXCLUDE USING gist (requiere btree_gist,
--    extensión estándar de contrib de Postgres, sin dependencia externa — cumple
--    Regla Mandatoria #23 de soberanía de datos) porque la invariante es un traslape de
--    rango de fechas, no una igualdad simple que un UNIQUE INDEX pueda expresar.

BEGIN;

-- ── 1) ades_esquemas_ponderacion ────────────────────────────────────────────────

-- Repara los 3 duplicados exactos confirmados (mismos pesos en ambas filas de cada
-- par) desactivando la copia más reciente de cada uno. No se pierde información: los
-- ades_items_ponderacion de la fila desactivada permanecen en la tabla (ON DELETE
-- CASCADE no aplica porque no se borra, solo is_active = FALSE), solo dejan de ser el
-- esquema "vivo" para ese contexto.
UPDATE ades_esquemas_ponderacion
SET is_active = FALSE
WHERE id IN (
    '019f549d-69ab-71ee-a91d-d355f47c144b', -- SEP Primaria — Base (duplicado, creado 2026-07-12 04:37)
    '019f549d-69ad-7819-a86a-a93e09976c70', -- SEP Secundaria — Base (duplicado)
    '019f549d-69ae-726a-b645-7126047a31ba'  -- UAEMEX Preparatoria — Base (duplicado)
)
AND is_active = TRUE;

-- Restricción estructural: a lo más un esquema activo+vigente por contexto real. Los
-- NULL de materia_id/plantel_id/profesor_id se normalizan con COALESCE a un UUID
-- centinela (nil UUID) para que Postgres los trate como iguales entre sí a efectos de
-- unicidad — de lo contrario NULL <> NULL habría dejado pasar exactamente el mismo
-- duplicado que se acaba de reparar arriba.
CREATE UNIQUE INDEX uq_esquema_ponderacion_contexto_activo
ON ades_esquemas_ponderacion (
    nivel_educativo_id,
    COALESCE(materia_id, '00000000-0000-0000-0000-000000000000'::uuid),
    COALESCE(plantel_id, '00000000-0000-0000-0000-000000000000'::uuid),
    COALESCE(profesor_id, '00000000-0000-0000-0000-000000000000'::uuid)
)
WHERE activo = TRUE AND is_active = TRUE;

-- ── 2) ades_licencias_personal ──────────────────────────────────────────────────

CREATE EXTENSION IF NOT EXISTS btree_gist;

-- Ninguna persona puede tener 2 licencias APROBADAS con rangos de fecha traslapados.
-- daterange(fecha_inicio, fecha_fin, '[]') → intervalo cerrado en ambos extremos
-- (fecha_fin es inclusiva según el check constraint chk_licencia_fechas existente).
ALTER TABLE ades_licencias_personal
    ADD CONSTRAINT excl_licencia_personal_traslape
    EXCLUDE USING gist (
        personal_id WITH =,
        daterange(fecha_inicio, fecha_fin, '[]') WITH &&
    )
    WHERE (estado = 'APROBADA' AND is_active = TRUE);

COMMIT;
