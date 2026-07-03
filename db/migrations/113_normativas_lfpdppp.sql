-- =============================================================================
-- Migración: 113_normativas_lfpdppp.sql
-- Descripción: AD-013 — normativas específicas LFPDPPP (Ley Federal de
--              Protección de Datos Personales en Posesión de Particulares),
--              distintas del "Aviso de Privacidad" genérico ya registrado.
--              Reusa ades_normatividad/ades_alertas_cumplimiento existentes
--              (RegistrarNormativaUseCase) — solo datos, sin tabla ni código nuevo.
-- Tablas afectadas: ades_normatividad, ades_alertas_cumplimiento (INSERT)
-- Autor: ADES
-- Fecha: 2026-07-03
-- =============================================================================

INSERT INTO ades_normatividad (nombre, tipo, descripcion, fecha_vigencia_inicio,
                                aplica_primaria, aplica_secundaria, aplica_preparatoria)
SELECT * FROM (VALUES
    ('LFPDPPP — Minimización de Datos', 'LFPDPPP',
     'Principio de minimización: ADES solo captura los datos personales estrictamente necesarios para la operación escolar/de salud/conductual. Revisar periódicamente formularios y reportes para evitar sobre-recolección.',
     '2026-01-01'::date, true, true, true),
    ('LFPDPPP — Consentimiento y Derechos ARCO', 'LFPDPPP',
     'Los padres/tutores deben poder ejercer sus derechos ARCO (Acceso, Rectificación, Cancelación, Oposición) sobre los datos de sus hijos. El Aviso de Privacidad debe estar visible y actualizado en el Portal de Familias.',
     '2026-01-01'::date, true, true, true),
    ('LFPDPPP — Plazos de Retención y Cifrado en Tránsito', 'LFPDPPP',
     'Datos académicos y de salud deben cifrarse en tránsito (SC-8, TLS en producción) y no conservarse más allá del plazo legal/institucional requerido tras el egreso del alumno.',
     '2026-01-01'::date, true, true, true)
) AS v(nombre, tipo, descripcion, fecha_vigencia_inicio, aplica_primaria, aplica_secundaria, aplica_preparatoria)
WHERE NOT EXISTS (
    SELECT 1 FROM ades_normatividad n WHERE n.nombre = v.nombre
);

INSERT INTO ades_alertas_cumplimiento (tipo_alerta, descripcion, severidad, requiere_accion, estado)
SELECT * FROM (VALUES
    ('LFPDPPP_REVISION_ANUAL', 'Revisión anual pendiente: confirmar que el Aviso de Privacidad y los formularios de captura de datos siguen cumpliendo el principio de minimización LFPDPPP.', 'MEDIA', true, 'PENDIENTE'),
    ('LFPDPPP_ARCO_PORTAL', 'Verificar que el Portal de Familias exponga claramente el mecanismo para ejercer derechos ARCO sobre los datos del alumno.', 'MEDIA', true, 'PENDIENTE')
) AS v(tipo_alerta, descripcion, severidad, requiere_accion, estado)
WHERE NOT EXISTS (
    SELECT 1 FROM ades_alertas_cumplimiento a WHERE a.tipo_alerta = v.tipo_alerta
);
