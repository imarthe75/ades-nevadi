                        ?column?                         
---------------------------------------------------------
 # 📚 Data Dictionary — ADES Database                   +
                                                        +
 **Generado:** 2026-06-16                               +
 **Sistema:** ADES (Sistema de Administración Escolar)  +
 **Tablas Documentadas:** 145                           +
 **Columnas Documentadas:** 2459                        +
                                                        +
 ## Resumen por Tabla                                   +
                                                        +
 | Tabla | Comentario | Columnas |                      +
 |-------|-----------|----------|
(1 row)

                                                                                                                                                     ?column?                                                                                                                                                      
-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
 | `ades_actas_incidente_medico` | Actas formales de incidentes médicos (SB-005) | 15 |
 | `ades_acuerdos_convivencia` | Registro de firma de acuerdo de convivencia (PE-026) | 12 |
 | `ades_acuses_comunicado` | Registro de acuses de lectura para comunicados enviados a estudiantes, profesores y tutores. | 12 |
 | `ades_ai_conversaciones` | Historial de mensajes del asistente pedagógico IA. Cada fila es un mensaje (role: user/assistant). Contexto de la conversación se recupera por sesion_id. | 11 |
 | `ades_alertas_academicas` | Alertas de riesgo académico generadas automáticamente. tipo_alerta: REPROBACION (promedio<6.0), AUSENTISMO (<80% asistencia), RIESGO_ALTO (IA). El campo ia_analisis contiene el análisis generado por Claude. | 20 |
 | `ades_alertas_cumplimiento` | Alertas de incumplimiento normativo (AD-030) | 14 |
 | `ades_anuncios` | Tablón de anuncios oficial (CO-023) | 15 |
 | `ades_archivos` | Almacenamiento de referencias a archivos del sistema: documentos, expedientes, comprobantes. | 15 |
 | `ades_areas_academicas` | Áreas académicas globales supervisadas por COORDINADOR_AREA (transversal a planteles) | 11 |
 | `ades_asignaciones_aula` | Asignación puntual de aula a clase/evento (EV-025) | 14 |
 | `ades_asignaciones_docentes` | Asignación profesor↔materia↔grupo. Aplica a todos los niveles. En primaria el titular cubre todas las materias excepto inglés. | 12 |
 | `ades_asistencia_personal` | Registro diario de asistencia del personal docente y administrativo. Captura hora de entrada/salida, retardos y justificaciones. | 19 |
 | `ades_asistencias` | Registro de asistencia alumno-clase. estatus_asistencia: PRESENTE, FALTA, TARDE, JUSTIFICADA. El trigger trg_recalcular_desde_asistencia recalcula el componente asistencia en ades_calificaciones_periodo al insertar/actualizar. | 13 |
 | `ades_audit_log` | Log inmutable de mutaciones del sistema. Cada fila tiene hash MD5 encadenado para detectar alteración directa. NO hacer UPDATE/DELETE. Retención mínima: 5 años (SEP). | 19 |
 | `ades_aulas` | Aulas y espacios físicos por plantel. Incluye capacidad, equipamiento y estado operativo. | 25 |
 | `ades_avance_planificacion` | Seguimiento del avance de la planificación de clases respecto a las metas establecidas. | 13 |
 | `ades_badge_otorgados` | Registro de insignias otorgadas a alumnos por ciclo escolar | 8 |
 | `ades_badges` | Catálogo de insignias/gamificación del sistema | 13 |
 | `ades_bajas` | Registro de bajas, traslados y deserciones de alumnos. tipo_baja: TEMPORAL (reactivable), DEFINITIVA (permanente), TRASLADO (a otro plantel), DESERCION. Solo DIRECTOR puede autorizar. | 18 |
 | `ades_calendario_escolar` | Calendario académico del ciclo escolar: períodos de evaluación, recesos, eventos escolares. | 14 |
 | `ades_calendarios_academicos` | Calendario académico SEP/UAEMEX por ciclo y nivel (AC-005) | 17 |
 | `ades_calificaciones_evaluaciones` | Calificaciones de estudiantes por evaluación (parciales, finales, extraordinarios). | 13 |
 | `ades_calificaciones_historico` | Snapshot inmutable de calificaciones al momento del cierre formal de cada período. | 12 |
 | `ades_calificaciones_periodo` | Calificación acumulada del alumno por materia y período de evaluación. La columna calificacion_calculada la llena la función calcular_calificacion_periodo(). El ajuste manual (ajuste_manual) lo aplica el director. La columna es_acreditado se recalcula por trigger. | 25 |
 | `ades_calificaciones_tareas` | Calificaciones individuales de tareas y actividades asignadas en clase. | 13 |
 | `ades_cambios_grupo` | Historial de cambios de grupo (movilidad interna). Registra el grupo origen, destino, inscripción modificada y autorización. Solo COORDINADOR+ puede autorizar. | 13 |
 | `ades_capacitaciones_docente` | Registro de capacitaciones, cursos, talleres y certificaciones del personal docente. Base para reportes de formación continua y cumplimiento SEP/UAEMEX. | 22 |
 | `ades_catalogo_items` | Items/valores de los catálogos dinámicos. | 12 |
 | `ades_catalogos` | Cabecera de catálogos dinámicos administrables desde el módulo Admin. | 11 |
 | `ades_certificados` | Certificados digitales firmados con Ed25519. Cada certificado tiene folio único verificable en /verificar/:folio. estado_firma: PENDIENTE→FIRMADO. La llave privada NO se almacena en BD — vive en .env (FIRMA_CLAVE_PRIVADA_HEX). | 30 |
 | `ades_ciclos_escolares` | Ciclo escolar por nivel educativo y plantel. es_vigente=TRUE identifica el ciclo activo que usan los filtros globales. Solo debe existir un ciclo vigente por nivel+plantel. | 15 |
 | `ades_cierre_periodo_log` | Registro formal de cada cierre de período por grupo. Inmutable una vez cerrado. | 14 |
 | `ades_clases` | Sesiones de clase: asignación de docente, materia, horario, aula en un grupo específico. | 20 |
 | `ades_codigos_postales` | Catálogo SEPOMEX: 158,088 asentamientos (colonias, ejidos, fraccionamientos) de México. Alimenta la cascada CP→Colonia→Municipio→Estado en formularios de domicilio. | 13 |
 | `ades_comunicados` | Mensajes y comunicados institucionales enviados a estudiantes, profesores y tutores. | 23 |
 | `ades_condiciones_cronicas` | Condiciones de salud crónicas de alumnos (SB-006/007) | 18 |
 | `ades_constancias` | Constancias y documentos oficiales emitidos por la escuela: de estudio, de calificaciones, traslado, etc. | 21 |
 | `ades_contactos_familiares` | Familiares, tutores y contactos de emergencia de un alumno. es_tutor_legal=TRUE identifica a quien tiene custodia legal. telefono_principal y telefono_trabajo validados por constraints. Ordenados por columna prioridad. | 25 |
 | `ades_coordinaciones_area` | Asignación de coordinadores de área a áreas académicas globales | 12 |
 | `ades_correos_electronicos` | Log de correos electrónicos enviados por el sistema (notificaciones, alertas, reportes). | 12 |
 | `ades_criterios_eval_docente` | Catálogo de criterios para evaluación docente. Configurable por nivel educativo. | 15 |
 | `ades_cuotas_concepto` | Conceptos de cuota escolar (colegiatura, servicios, actividades) por nivel educativo. | 13 |
 | `ades_cuotas_pagos` | Registro de cobros y pagos escolares por alumno. No es sistema de facturación — solo control interno. | 23 |
 | `ades_direcciones` | Domicilios de personas (alumnos, profesores, tutores). Integra catálogo SEPOMEX via codigo_postal_id y localidad_id. Incluye geocodificación opcional (latitud/longitud) via Nominatim. | 24 |
 | `ades_disponibilidad_aula` | Asignación de grupos a aulas por franja horaria semanal. Permite detectar conflictos de doble uso. | 14 |
 | `ades_disponibilidad_docente` | Disponibilidad de docente por ciclo escolar: días, turno y horas máximas. Base para generación de horarios aSc. | 15 |
 | `ades_documentos_admision` | Documentos del proceso de admisión (PE-006) | 13 |
 | `ades_documentos_tipo` | Tipos de documentos permitidos en el sistema: INE, certificado de nacimiento, CURP, etc. | 12 |
 | `ades_encuesta_preguntas` | Preguntas de encuestas de satisfacción, evaluación docente, feedback académico. | 14 |
 | `ades_encuesta_respuestas` | Respuestas de estudiantes y usuarios a encuestas del sistema. | 9 |
 | `ades_encuestas` | Encuestas y sondeos escolares (satisfacción, diagnóstico, clima) | 20 |
 | `ades_escalas_evaluacion` | Escalas de evaluación cualitativa SEP/UAEMEX | 12 |
 | `ades_escuelas` | Entidad raíz: Instituto Nevadi. | 13 |
 | `ades_esquemas_ponderacion` | Esquemas de ponderación para cálculo de calificaciones (% examen, tareas, participación). | 15 |
 | `ades_estados` | Catálogo de estados/provincias. | 11 |
 | `ades_estatus` | Catálogo de estatus por entidad del sistema. | 11 |
 | `ades_estudiantes` | Datos académicos y escolares específicos del alumno. Hereda identidad de ades_personas via persona_id. Incluye matrícula única por plantel, nivel socioeconómico, datos de beca y procedencia escolar. | 27 |
 | `ades_eval_docente_criterios` | Calificación por criterio dentro de una evaluación docente. | 11 |
 | `ades_evaluacion_docente` | Evaluación 360° de un docente por ciclo. Un evaluador puede emitir una por ciclo. | 16 |
 | `ades_evaluaciones` | Evaluaciones (exámenes, pruebas) aplicadas a estudiantes con fechas y temas. | 16 |
 | `ades_evaluaciones_riesgo` | Evaluaciones de riesgo de abandono escolar (IA-005) | 10 |
 | `ades_expediente_docs` | Estado de cada documento requerido del expediente por alumno y ciclo. | 17 |
 | `ades_expediente_documentos` | Documentos individuales que conforman el expediente digital del alumno. Cada registro referencia un documento en Paperless-ngx via paperless_doc_id. | 17 |
 | `ades_expediente_laboral` | Expediente laboral digital del personal. Contiene datos contractuales, IMSS, INFONAVIT, cédula profesional y URLs a documentos en MinIO. | 24 |
 | `ades_expedientes_alumno` | Expediente digital del alumno por ciclo escolar. Encabezado que agrupa todos los documentos requeridos para la inscripcion y seguimiento escolar. Integrado con Paperless-ngx como motor OCR. | 14 |
 | `ades_expedientes_medicos` | Historial de salud, alergias, medicamentos y emergencias médicas de estudiantes. | 21 |
 | `ades_extraordinarias` | Exámenes extraordinarios y de regularización. SEP limita a 3 por ciclo en secundaria/preparatoria. | 19 |
 | `ades_foros` | Foros de discusión por grupo/plantel (CO-020) | 16 |
 | `ades_grados` | Grados por nivel y plantel. Ixtapan secundaria solo tiene grados 1 y 2. | 13 |
 | `ades_grupos` | Grupo escolar por nivel/grado/ciclo. capacidad_maxima controla el cupo para cambios de grupo. nombre_grupo = grado + letra (ej: 1A, 2B). | 16 |
 | `ades_historico_identidad` | Auditoría de cambios en datos de identidad: CURP, RFC, nombre, fecha nacimiento. | 12 |
 | `ades_horarios` | Horario de clases generado por aSc TimeTables (XML import) o capturado manualmente. | 21 |
 | `ades_identidad_institucional` | Branding y configuración visual del sistema. Un registro por elemento de identidad.                                                                                                                                                                                           +
    Soporte de scope: plantel_id NULL = aplica a toda la institución.                                                                                                                                                                                                                                             +
    Tipos: NOMBRE_INSTITUCION, COLOR_PRIMARIO, COLOR_SECUNDARIO, LOGO_URL, FAVICON_URL, SLOGAN, FOOTER_TEXTO. | 17 |
 | `ades_incidentes_medicos` | Incidentes médicos: accidentes, enfermedades, primeros auxilios en la institución. | 16 |
 | `ades_informacion_escuela` | Información general de la institución: misión, visión, datos de contacto, ubicación. | 13 |
 | `ades_inscripciones` | Registro de la inscripción de un alumno a un grupo en un ciclo escolar. Una inscripción activa (is_active=TRUE) es el estado actual del alumno. El historial de cambios está en ades_cambios_grupo. | 14 |
 | `ades_inscripciones_optativas` | Inscripción de alumnos a materias optativas/electivas (PE-014) | 12 |
 | `ades_items_ponderacion` | Items de evaluación incluidos en esquemas de ponderación (examen 30%, tareas 20%, etc). | 13 |
 | `ades_justificaciones_falta` | Justificaciones de faltas de alumnos con workflow aprobación (OA-003) | 16 |
 | `ades_learning_paths` | Rutas de refuerzo académico adaptativas — inspiradas en Moodle Learning Plans | 12 |
 | `ades_lenguas_indigenas` | Catálogo INALI — 68 agrupaciones lingüísticas indígenas nacionales | 11 |
 | `ades_licencias_personal` | Solicitudes de licencia y permisos de personal docente/administrativo. Flujo: PENDIENTE → APROBADA/RECHAZADA. Incluye sustituto y goce de sueldo. | 20 |
 | `ades_llaves_firma` | Inventario de llaves públicas de firma del Instituto Nevadi | 16 |
 | `ades_localidades` | Localidades (pueblos, ciudades) de México para geolocalización de direcciones. | 11 |
 | `ades_log_autenticacion` | Log de autenticaciones Authentik (AD-007) | 8 |
 | `ades_lp_asignaciones` | Asignaciones de Learning Paths a alumnos. ia_recomendacion JSONB contiene el análisis de Claude (fortalezas, áreas mejora, estrategias, recursos priorizados). | 17 |
 | `ades_lp_progreso` | Progreso por recurso individual dentro de una asignación | 8 |
 | `ades_lp_recursos` | Recursos didácticos que conforman un Learning Path | 16 |
 | `ades_materias` | Catálogo de materias/asignaturas por nivel educativo y plan de estudios. | 18 |
 | `ades_materias_plan` | Plan de estudios: materias por grado y ciclo escolar. Al insertar aquí un proceso Celery genera las tareas del ciclo. | 14 |
 | `ades_medicamentos_alumno` | Medicamentos en uso de alumnos (SB-003) | 18 |
 | `ades_mensajes_foro` | Mensajes en foros (CO-021) | 15 |
 | `ades_menu_roles` | Asignación de menús a roles. Un menú visible solo si el usuario tiene el rol asignado. | 8 |
 | `ades_menus` | Árbol de navegación administrable desde el módulo Admin, sin necesidad de redeploy del frontend. | 14 |
 | `ades_municipios` | Municipios de México para localización geográfica de estudiantes y escuelas. | 11 |
 | `ades_nee` | Necesidades Educativas Especiales (EV-024) | 14 |
 | `ades_niveles_educativos` | Niveles de educación soportados: Primaria, Secundaria, Preparatoria, etc. | 20 |
 | `ades_niveles_ingles` | Marco Común Europeo de Referencia para las Lenguas (CEFR) | 15 |
 | `ades_normatividad` | Catálogo de normatividad SEP/UAEMEX/Interna (AD-014) | 17 |
 | `ades_notificaciones` | Bandeja de notificaciones internas por usuario | 17 |
 | `ades_notificaciones_sistema` | Notificaciones in-app generadas automáticamente por el sistema: asignación de roles, cambios de grupo, alertas académicas, etc. Complementa el push via ntfy. | 7 |
 | `ades_observaciones_pedagogicas` | Observaciones pedagógicas por alumno (EV-017) | 12 |
 | `ades_paises` | Catálogo de países. | 11 |
 | `ades_parametros_sistema` | Parámetros configurables del sistema. Modificables por ADMIN_GLOBAL desde la UI. | 16 |
 | `ades_periodos_evaluacion` | Períodos de evaluación con apertura/cierre (AC-014) | 15 |
 | `ades_periodos_inscripcion` | Períodos habilitados para inscripción y reinscripción de estudiantes. | 13 |
 | `ades_persona_contactos` | Medios de contacto estructurados por persona (normalización de ades_personas.telefono y email_personal). medio: CELULAR, FIJO, WHATSAPP, EMAIL, TELEGRAM, FAX, OTRO. Validación de formato por constraints chk_pc_valor_email y chk_pc_valor_telefono. | 17 |
 | `ades_personal_administrativo` | Personal no-docente y no-sanitario: directivos, coordinadores, secretarías, prefectos, apoyo. | 24 |
 | `ades_personal_salud` | Personal médico/sanitario del plantel (médico escolar, enfermería). | 23 |
 | `ades_personas` | Tabla maestra de personas físicas del sistema. Una persona puede ser alumno, profesor, tutor o personal administrativo. Contiene datos de identidad, nacimiento y contacto. Columnas sensibles: curp, rfc, email_personal. | 28 |
 | `ades_planeacion_clases` | Planificación de clases: temas, objetivos, recursos, actividades planificadas. | 13 |
 | `ades_planes_mejora` | Plan de mejora conductual. Un plan por reporte. Incluye compromisos de alumno, padre y escuela. | 25 |
 | `ades_plantel_niveles` | Niveles activos por plantel. Tenancingo no tiene PREPARATORIA. Ixtapan solo tiene 1° y 2° de secundaria. | 12 |
 | `ades_planteles` | Planteles físicos del Instituto Nevadi: Metepec (Primaria+Secundaria+Preparatoria), Tenancingo (Primaria+Secundaria+Preparatoria), Ixtapan de la Sal (Primaria+Secundaria, Preparatoria proyectada). | 12 |
 | `ades_privilegios` | Catálogo de permisos del sistema (crear usuario, ver reportes, editar calificaciones, etc). | 12 |
 | `ades_profesores` | Datos laborales y académicos del docente. Hereda identidad de ades_personas via persona_id. Incluye RFC, cédula profesional, materias que puede impartir y carga horaria. | 24 |
 | `ades_promociones_pendientes` | Alumnos cuya reinscripción automática no pudo completarse por falta de grupo destino. Requieren asignación manual. | 18 |
 | `ades_reinscripcion_ciclo` | Estado de validación de reinscripción de cada alumno para el siguiente ciclo. | 21 |
 | `ades_reportes_academicos` | Reportes académicos generados: boletas, constancias, kardex, reporte de calificaciones. | 15 |
 | `ades_reportes_conducta` | Reportes de conducta y disciplina: faltas, amonestaciones, reconocimientos. | 22 |
 | `ades_respuestas_foro` | Respuestas encadenadas en foros (CO-022) | 12 |
 | `ades_restricciones_tutor` | Restricciones de acceso al portal por tutor (PE-033) | 14 |
 | `ades_retenciones` | Retenciones académicas/administrativas de alumnos (AD-017) | 15 |
 | `ades_rol_privilegios` | Asignación de privilegios a roles: qué puede hacer cada rol (admin, director, docente, etc). | 6 |
 | `ades_roles` | Roles del sistema: Admin, Director, Coordinador, Docente, Apoyo, Estudiante. | 11 |
 | `ades_rubrica_criterios` | Criterios de evaluación en rúbricas (escala de desempeño, descriptores). | 14 |
 | `ades_rubricas` | Rúbricas de evaluación: escalas de desempeño, niveles de logro. | 12 |
 | `ades_sanciones_disciplinarias` | Sanciones formales aplicadas a un alumno. Una por reporte, aprobadas por Director. | 19 |
 | `ades_seguimiento_plan` | Entradas de seguimiento periódico al plan de mejora. Actualiza el avance del plan. | 16 |
 | `ades_seguimiento_psicosocial` | Seguimiento psicosocial por alumno (SB-021) | 15 |
 | `ades_solicitudes_admision` | Solicitudes de admisión de nuevos alumnos (PE-003) | 29 |
 | `ades_solicitudes_tramites` | Flujo de trámites administrativos: constancias, certificados, bajas, cambios de grupo.                                                                                                                                                                                           +
    Cada trámite tiene ciclo de vida: RECIBIDA→EN_PROCESO→APROBADA→ENTREGADA. | 21 |
 | `ades_tareas` | Tareas y actividades asignadas a estudiantes en una clase. | 24 |
 | `ades_tareas_entregas` | Entregas de tareas por parte de estudiantes: archivos, fechas, calificaciones. | 19 |
 | `ades_tareas_sistema` | Cola de tareas async del sistema (notificaciones, Celery tasks) | 12 |
 | `ades_telefonos` | Teléfonos de contacto de personas (estudiantes, tutores, docentes, personal). | 12 |
 | `ades_temas` | Temas de clase dentro de una materia: conceptos, lecciones, unidades didácticas. | 15 |
 | `ades_tipos_asentamiento` | Tipos de asentamiento (colonia, ejido, fraccionamiento, etc) del catálogo SEPOMEX. | 10 |
 | `ades_tutores_alumnos` | Vínculos tutor-alumno con permisos de portal (PE-029) | 16 |
 | `ades_tutorias` | Sesiones de tutoría individual/grupal (SB-022) | 15 |
 | `ades_usuario_roles` | Permite multi-rol. El campo peso determina el rol activo principal (mayor peso = prioridad). Sincronizado JIT con Authentik. | 7 |
 | `ades_usuarios` | Cuentas de acceso al sistema ADES. Se vinculan con Authentik via oidc_sub. nivel_acceso: 1=Admin Global, 2=Director, 3=Coordinador, 4=Docente, 5=Apoyo/Prefecto. Un usuario puede estar asociado a un plantel_id específico (null=acceso global). | 19 |
 | `ades_variables_sistema` | Variables de configuración del sistema, administrables desde el módulo Admin sin necesidad de SSH. | 15 |
 | `ades_webhook_logs` | Historico e intentos de envio de eventos via webhooks. | 15 |
 | `ades_webhooks` | Registro de webhooks activos e integraciones salientes (FASE 34). | 11 |
(145 rows)

 ?column? 
----------
 
(1 row)

 ?column? 
----------
 ---
(1 row)

 ?column? 
----------
 
(1 row)

       ?column?       
----------------------
 ## Tablas Detalladas+
 
(1 row)

                                                                                                                                         ?column?                                                                                                                                          
-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
                                                                                                                                                                                                                                                                                          +
 ### ades_codigos_postales                                                                                                                                                                                                                                                                +
                                                                                                                                                                                                                                                                                          +
 **Descripción:** Catálogo SEPOMEX: 158,088 asentamientos (colonias, ejidos, fraccionamientos) de México. Alimenta la cascada CP→Colonia→Municipio→Estado en formularios de domicilio.                                                                                                    +
                                                                                                                                                                                                                                                                                          +
 **Tamaño:** 197 MB                                                                                                                                                                                                                                                                       +
                                                                                                                                                                                                                                                                                          +
 | Columna | Tipo | Nullable | Comentario |                                                                                                                                                                                                                                               +
 |---------|------|----------|-----------|
                                                                                                                                                                                                                                                                                          +
 ### ades_asistencias                                                                                                                                                                                                                                                                     +
                                                                                                                                                                                                                                                                                          +
 **Descripción:** Registro de asistencia alumno-clase. estatus_asistencia: PRESENTE, FALTA, TARDE, JUSTIFICADA. El trigger trg_recalcular_desde_asistencia recalcula el componente asistencia en ades_calificaciones_periodo al insertar/actualizar.                                      +
                                                                                                                                                                                                                                                                                          +
 **Tamaño:** 141 MB                                                                                                                                                                                                                                                                       +
                                                                                                                                                                                                                                                                                          +
 | Columna | Tipo | Nullable | Comentario |                                                                                                                                                                                                                                               +
 |---------|------|----------|-----------|
                                                                                                                                                                                                                                                                                          +
 ### ades_calificaciones_periodo                                                                                                                                                                                                                                                          +
                                                                                                                                                                                                                                                                                          +
 **Descripción:** Calificación acumulada del alumno por materia y período de evaluación. La columna calificacion_calculada la llena la función calcular_calificacion_periodo(). El ajuste manual (ajuste_manual) lo aplica el director. La columna es_acreditado se recalcula por trigger.+
                                                                                                                                                                                                                                                                                          +
 **Tamaño:** 84 MB                                                                                                                                                                                                                                                                        +
                                                                                                                                                                                                                                                                                          +
 | Columna | Tipo | Nullable | Comentario |                                                                                                                                                                                                                                               +
 |---------|------|----------|-----------|
                                                                                                                                                                                                                                                                                          +
 ### ades_tareas_entregas                                                                                                                                                                                                                                                                 +
                                                                                                                                                                                                                                                                                          +
 **Descripción:** Entregas de tareas por parte de estudiantes: archivos, fechas, calificaciones.                                                                                                                                                                                          +
                                                                                                                                                                                                                                                                                          +
 **Tamaño:** 65 MB                                                                                                                                                                                                                                                                        +
                                                                                                                                                                                                                                                                                          +
 | Columna | Tipo | Nullable | Comentario |                                                                                                                                                                                                                                               +
 |---------|------|----------|-----------|
                                                                                                                                                                                                                                                                                          +
 ### ades_localidades                                                                                                                                                                                                                                                                     +
                                                                                                                                                                                                                                                                                          +
 **Descripción:** Localidades (pueblos, ciudades) de México para geolocalización de direcciones.                                                                                                                                                                                          +
                                                                                                                                                                                                                                                                                          +
 **Tamaño:** 46 MB                                                                                                                                                                                                                                                                        +
                                                                                                                                                                                                                                                                                          +
 | Columna | Tipo | Nullable | Comentario |                                                                                                                                                                                                                                               +
 |---------|------|----------|-----------|
                                                                                                                                                                                                                                                                                          +
 ### ades_personas                                                                                                                                                                                                                                                                        +
                                                                                                                                                                                                                                                                                          +
 **Descripción:** Tabla maestra de personas físicas del sistema. Una persona puede ser alumno, profesor, tutor o personal administrativo. Contiene datos de identidad, nacimiento y contacto. Columnas sensibles: curp, rfc, email_personal.                                              +
                                                                                                                                                                                                                                                                                          +
 **Tamaño:** 5072 kB                                                                                                                                                                                                                                                                      +
                                                                                                                                                                                                                                                                                          +
 | Columna | Tipo | Nullable | Comentario |                                                                                                                                                                                                                                               +
 |---------|------|----------|-----------|
                                                                                                                                                                                                                                                                                          +
 ### ades_clases                                                                                                                                                                                                                                                                          +
                                                                                                                                                                                                                                                                                          +
 **Descripción:** Sesiones de clase: asignación de docente, materia, horario, aula en un grupo específico.                                                                                                                                                                                +
                                                                                                                                                                                                                                                                                          +
 **Tamaño:** 4296 kB                                                                                                                                                                                                                                                                      +
                                                                                                                                                                                                                                                                                          +
 | Columna | Tipo | Nullable | Comentario |                                                                                                                                                                                                                                               +
 |---------|------|----------|-----------|
                                                                                                                                                                                                                                                                                          +
 ### ades_tareas                                                                                                                                                                                                                                                                          +
                                                                                                                                                                                                                                                                                          +
 **Descripción:** Tareas y actividades asignadas a estudiantes en una clase.                                                                                                                                                                                                              +
                                                                                                                                                                                                                                                                                          +
 **Tamaño:** 3640 kB                                                                                                                                                                                                                                                                      +
                                                                                                                                                                                                                                                                                          +
 | Columna | Tipo | Nullable | Comentario |                                                                                                                                                                                                                                               +
 |---------|------|----------|-----------|
                                                                                                                                                                                                                                                                                          +
 ### ades_usuarios                                                                                                                                                                                                                                                                        +
                                                                                                                                                                                                                                                                                          +
 **Descripción:** Cuentas de acceso al sistema ADES. Se vinculan con Authentik via oidc_sub. nivel_acceso: 1=Admin Global, 2=Director, 3=Coordinador, 4=Docente, 5=Apoyo/Prefecto. Un usuario puede estar asociado a un plantel_id específico (null=acceso global).                       +
                                                                                                                                                                                                                                                                                          +
 **Tamaño:** 3360 kB                                                                                                                                                                                                                                                                      +
                                                                                                                                                                                                                                                                                          +
 | Columna | Tipo | Nullable | Comentario |                                                                                                                                                                                                                                               +
 |---------|------|----------|-----------|
                                                                                                                                                                                                                                                                                          +
 ### ades_inscripciones                                                                                                                                                                                                                                                                   +
                                                                                                                                                                                                                                                                                          +
 **Descripción:** Registro de la inscripción de un alumno a un grupo en un ciclo escolar. Una inscripción activa (is_active=TRUE) es el estado actual del alumno. El historial de cambios está en ades_cambios_grupo.                                                                     +
                                                                                                                                                                                                                                                                                          +
 **Tamaño:** 1064 kB                                                                                                                                                                                                                                                                      +
                                                                                                                                                                                                                                                                                          +
 | Columna | Tipo | Nullable | Comentario |                                                                                                                                                                                                                                               +
 |---------|------|----------|-----------|
                                                                                                                                                                                                                                                                                          +
 ### ades_expedientes_medicos                                                                                                                                                                                                                                                             +
                                                                                                                                                                                                                                                                                          +
 **Descripción:** Historial de salud, alergias, medicamentos y emergencias médicas de estudiantes.                                                                                                                                                                                        +
                                                                                                                                                                                                                                                                                          +
 **Tamaño:** 1016 kB                                                                                                                                                                                                                                                                      +
                                                                                                                                                                                                                                                                                          +
 | Columna | Tipo | Nullable | Comentario |                                                                                                                                                                                                                                               +
 |---------|------|----------|-----------|
                                                                                                                                                                                                                                                                                          +
 ### ades_disponibilidad_docente                                                                                                                                                                                                                                                          +
                                                                                                                                                                                                                                                                                          +
 **Descripción:** Disponibilidad de docente por ciclo escolar: días, turno y horas máximas. Base para generación de horarios aSc.                                                                                                                                                         +
                                                                                                                                                                                                                                                                                          +
 **Tamaño:** 912 kB                                                                                                                                                                                                                                                                       +
                                                                                                                                                                                                                                                                                          +
 | Columna | Tipo | Nullable | Comentario |                                                                                                                                                                                                                                               +
 |---------|------|----------|-----------|
                                                                                                                                                                                                                                                                                          +
 ### ades_estudiantes                                                                                                                                                                                                                                                                     +
                                                                                                                                                                                                                                                                                          +
 **Descripción:** Datos académicos y escolares específicos del alumno. Hereda identidad de ades_personas via persona_id. Incluye matrícula única por plantel, nivel socioeconómico, datos de beca y procedencia escolar.                                                                  +
                                                                                                                                                                                                                                                                                          +
 **Tamaño:** 872 kB                                                                                                                                                                                                                                                                       +
                                                                                                                                                                                                                                                                                          +
 | Columna | Tipo | Nullable | Comentario |                                                                                                                                                                                                                                               +
 |---------|------|----------|-----------|
                                                                                                                                                                                                                                                                                          +
 ### ades_usuario_roles                                                                                                                                                                                                                                                                   +
                                                                                                                                                                                                                                                                                          +
 **Descripción:** Permite multi-rol. El campo peso determina el rol activo principal (mayor peso = prioridad). Sincronizado JIT con Authentik.                                                                                                                                            +
                                                                                                                                                                                                                                                                                          +
 **Tamaño:** 832 kB                                                                                                                                                                                                                                                                       +
                                                                                                                                                                                                                                                                                          +
 | Columna | Tipo | Nullable | Comentario |                                                                                                                                                                                                                                               +
 |---------|------|----------|-----------|
                                                                                                                                                                                                                                                                                          +
 ### ades_temas                                                                                                                                                                                                                                                                           +
                                                                                                                                                                                                                                                                                          +
 **Descripción:** Temas de clase dentro de una materia: conceptos, lecciones, unidades didácticas.                                                                                                                                                                                        +
                                                                                                                                                                                                                                                                                          +
 **Tamaño:** 792 kB                                                                                                                                                                                                                                                                       +
                                                                                                                                                                                                                                                                                          +
 | Columna | Tipo | Nullable | Comentario |                                                                                                                                                                                                                                               +
 |---------|------|----------|-----------|
                                                                                                                                                                                                                                                                                          +
 ### ades_alertas_academicas                                                                                                                                                                                                                                                              +
                                                                                                                                                                                                                                                                                          +
 **Descripción:** Alertas de riesgo académico generadas automáticamente. tipo_alerta: REPROBACION (promedio<6.0), AUSENTISMO (<80% asistencia), RIESGO_ALTO (IA). El campo ia_analisis contiene el análisis generado por Claude.                                                          +
                                                                                                                                                                                                                                                                                          +
 **Tamaño:** 792 kB                                                                                                                                                                                                                                                                       +
                                                                                                                                                                                                                                                                                          +
 | Columna | Tipo | Nullable | Comentario |                                                                                                                                                                                                                                               +
 |---------|------|----------|-----------|
                                                                                                                                                                                                                                                                                          +
 ### ades_municipios                                                                                                                                                                                                                                                                      +
                                                                                                                                                                                                                                                                                          +
 **Descripción:** Municipios de México para localización geográfica de estudiantes y escuelas.                                                                                                                                                                                            +
                                                                                                                                                                                                                                                                                          +
 **Tamaño:** 728 kB                                                                                                                                                                                                                                                                       +
                                                                                                                                                                                                                                                                                          +
 | Columna | Tipo | Nullable | Comentario |                                                                                                                                                                                                                                               +
 |---------|------|----------|-----------|
                                                                                                                                                                                                                                                                                          +
 ### ades_contactos_familiares                                                                                                                                                                                                                                                            +
                                                                                                                                                                                                                                                                                          +
 **Descripción:** Familiares, tutores y contactos de emergencia de un alumno. es_tutor_legal=TRUE identifica a quien tiene custodia legal. telefono_principal y telefono_trabajo validados por constraints. Ordenados por columna prioridad.                                              +
                                                                                                                                                                                                                                                                                          +
 **Tamaño:** 704 kB                                                                                                                                                                                                                                                                       +
                                                                                                                                                                                                                                                                                          +
 | Columna | Tipo | Nullable | Comentario |                                                                                                                                                                                                                                               +
 |---------|------|----------|-----------|
                                                                                                                                                                                                                                                                                          +
 ### ades_horarios                                                                                                                                                                                                                                                                        +
                                                                                                                                                                                                                                                                                          +
 **Descripción:** Horario de clases generado por aSc TimeTables (XML import) o capturado manualmente.                                                                                                                                                                                     +
                                                                                                                                                                                                                                                                                          +
 **Tamaño:** 504 kB                                                                                                                                                                                                                                                                       +
                                                                                                                                                                                                                                                                                          +
 | Columna | Tipo | Nullable | Comentario |                                                                                                                                                                                                                                               +
 |---------|------|----------|-----------|
                                                                                                                                                                                                                                                                                          +
 ### ades_materias_plan                                                                                                                                                                                                                                                                   +
                                                                                                                                                                                                                                                                                          +
 **Descripción:** Plan de estudios: materias por grado y ciclo escolar. Al insertar aquí un proceso Celery genera las tareas del ciclo.                                                                                                                                                   +
                                                                                                                                                                                                                                                                                          +
 **Tamaño:** 408 kB                                                                                                                                                                                                                                                                       +
                                                                                                                                                                                                                                                                                          +
 | Columna | Tipo | Nullable | Comentario |                                                                                                                                                                                                                                               +
 |---------|------|----------|-----------|
(20 rows)

