-- =============================================================================
-- SPRINT 2: FASE 2 — Agregar Comentarios Faltantes en Tablas
-- =============================================================================
-- Esta migración añade COMMENT ON TABLE para las 38 tablas sin descripción
-- Se generan descripciones inteligentes basadas en:
-- 1. Nombre de la tabla (prefijo ades_ + patrón de dominio)
-- 2. Columnas existentes (análisis de naming conventions)
-- 3. Contexto académico del sistema ADES

COMMENT ON TABLE public.ades_acuses_comunicado IS 'Registro de acuses de lectura para comunicados enviados a estudiantes, profesores y tutores.';
COMMENT ON TABLE public.ades_archivos IS 'Almacenamiento de referencias a archivos del sistema: documentos, expedientes, comprobantes.';
COMMENT ON TABLE public.ades_avance_planificacion IS 'Seguimiento del avance de la planificación de clases respecto a las metas establecidas.';
COMMENT ON TABLE public.ades_calendario_escolar IS 'Calendario académico del ciclo escolar: períodos de evaluación, recesos, eventos escolares.';
COMMENT ON TABLE public.ades_calificaciones_evaluaciones IS 'Calificaciones de estudiantes por evaluación (parciales, finales, extraordinarios).';
COMMENT ON TABLE public.ades_calificaciones_tareas IS 'Calificaciones individuales de tareas y actividades asignadas en clase.';
COMMENT ON TABLE public.ades_clases IS 'Sesiones de clase: asignación de docente, materia, horario, aula en un grupo específico.';
COMMENT ON TABLE public.ades_comunicados IS 'Mensajes y comunicados institucionales enviados a estudiantes, profesores y tutores.';
COMMENT ON TABLE public.ades_correos_electronicos IS 'Log de correos electrónicos enviados por el sistema (notificaciones, alertas, reportes).';
COMMENT ON TABLE public.ades_cuotas_concepto IS 'Conceptos de cuota escolar (colegiatura, servicios, actividades) por nivel educativo.';
COMMENT ON TABLE public.ades_documentos_tipo IS 'Tipos de documentos permitidos en el sistema: INE, certificado de nacimiento, CURP, etc.';
COMMENT ON TABLE public.ades_encuesta_preguntas IS 'Preguntas de encuestas de satisfacción, evaluación docente, feedback académico.';
COMMENT ON TABLE public.ades_encuesta_respuestas IS 'Respuestas de estudiantes y usuarios a encuestas del sistema.';
COMMENT ON TABLE public.ades_esquemas_ponderacion IS 'Esquemas de ponderación para cálculo de calificaciones (% examen, tareas, participación).';
COMMENT ON TABLE public.ades_evaluaciones IS 'Evaluaciones (exámenes, pruebas) aplicadas a estudiantes con fechas y temas.';
COMMENT ON TABLE public.ades_expedientes_medicos IS 'Historial de salud, alergias, medicamentos y emergencias médicas de estudiantes.';
COMMENT ON TABLE public.ades_historico_identidad IS 'Auditoría de cambios en datos de identidad: CURP, RFC, nombre, fecha nacimiento.';
COMMENT ON TABLE public.ades_incidentes_medicos IS 'Incidentes médicos: accidentes, enfermedades, primeros auxilios en la institución.';
COMMENT ON TABLE public.ades_informacion_escuela IS 'Información general de la institución: misión, visión, datos de contacto, ubicación.';
COMMENT ON TABLE public.ades_items_ponderacion IS 'Items de evaluación incluidos en esquemas de ponderación (examen 30%, tareas 20%, etc).';
COMMENT ON TABLE public.ades_localidades IS 'Localidades (pueblos, ciudades) de México para geolocalización de direcciones.';
COMMENT ON TABLE public.ades_materias IS 'Catálogo de materias/asignaturas por nivel educativo y plan de estudios.';
COMMENT ON TABLE public.ades_municipios IS 'Municipios de México para localización geográfica de estudiantes y escuelas.';
COMMENT ON TABLE public.ades_niveles_educativos IS 'Niveles de educación soportados: Primaria, Secundaria, Preparatoria, etc.';
COMMENT ON TABLE public.ades_periodos_inscripcion IS 'Períodos habilitados para inscripción y reinscripción de estudiantes.';
COMMENT ON TABLE public.ades_planeacion_clases IS 'Planificación de clases: temas, objetivos, recursos, actividades planificadas.';
COMMENT ON TABLE public.ades_privilegios IS 'Catálogo de permisos del sistema (crear usuario, ver reportes, editar calificaciones, etc).';
COMMENT ON TABLE public.ades_reportes_academicos IS 'Reportes académicos generados: boletas, constancias, kardex, reporte de calificaciones.';
COMMENT ON TABLE public.ades_reportes_conducta IS 'Reportes de conducta y disciplina: faltas, amonestaciones, reconocimientos.';
COMMENT ON TABLE public.ades_rol_privilegios IS 'Asignación de privilegios a roles: qué puede hacer cada rol (admin, director, docente, etc).';
COMMENT ON TABLE public.ades_roles IS 'Roles del sistema: Admin, Director, Coordinador, Docente, Apoyo, Estudiante.';
COMMENT ON TABLE public.ades_rubrica_criterios IS 'Criterios de evaluación en rúbricas (escala de desempeño, descriptores).';
COMMENT ON TABLE public.ades_rubricas IS 'Rúbricas de evaluación: escalas de desempeño, niveles de logro.';
COMMENT ON TABLE public.ades_tareas IS 'Tareas y actividades asignadas a estudiantes en una clase.';
COMMENT ON TABLE public.ades_tareas_entregas IS 'Entregas de tareas por parte de estudiantes: archivos, fechas, calificaciones.';
COMMENT ON TABLE public.ades_telefonos IS 'Teléfonos de contacto de personas (estudiantes, tutores, docentes, personal).';
COMMENT ON TABLE public.ades_temas IS 'Temas de clase dentro de una materia: conceptos, lecciones, unidades didácticas.';
COMMENT ON TABLE public.ades_tipos_asentamiento IS 'Tipos de asentamiento (colonia, ejido, fraccionamiento, etc) del catálogo SEPOMEX.';

-- Verificar aplicación
SELECT COUNT(*) as comentarios_aplicados
FROM pg_class c
JOIN pg_namespace n ON n.oid = c.relnamespace
LEFT JOIN pg_description d ON d.objoid = c.oid AND d.objsubid = 0
WHERE n.nspname = 'public' AND c.relkind = 'r' AND d.objoid IS NOT NULL;

