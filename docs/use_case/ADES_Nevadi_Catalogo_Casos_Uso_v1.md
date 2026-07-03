# ADES Nevadi — Catálogo Exhaustivo de Casos de Uso
## Instituto Nevadi | Administración Escolar SEP/UAEMEX
**Versión:** 2.0  
**Fecha:** Junio 2026  
**Documento de Referencia para Fases 27-35+**

---

## Prefacio: Alcance y Objetivo

Este catálogo consolida **todos los casos de uso operacionales, académicos, administrativos y analíticos** del sistema ADES para los tres planteles (Metepec, Tenancingo, Ixtapan) y sus tres niveles educativos (Primaria SEP, Secundaria SEP, Preparatoria UAEMEX).

Está estructurado en **10 dominios funcionales**, con identificación de casos implementados (✅) y casos pendientes (⏳), facilitando la priorización de desarrollo futuro.

> **Cobertura al 2026-07-03:** 192/230 CUs implementados (83.5%) — Grupos A-D (19 CU) + corrección de
> claves CCT/UAEMEX (Grupo E) completados. Última actualización: sesión 2026-07-03.

---

## Dominio 1: Gestión de Identidad Institucional

### 1.1 Configuración de Planteles y Entornos
| CU ID | Descripción | Actor | Estado | Fase |
|-------|------------|-------|--------|------|
| ID-001 | Alta de nuevo plantel (nombre, ubicación, contactos, zonas geográficas) | Admin Global | ✅ | 1 |
| ID-002 | Edición de datos de plantel (teléfono, email, horario, infraestructura) | Admin Plantel | ✅ | 1 |
| ID-003 | Desactivación de plantel (soft delete, archivar estudiantes/docentes) | Admin Global | ⏳ | 27 |
| ID-004 | Sincronización de catálogos geográficos SEP (estados, municipios, localidades) | Admin Global | ✅ | 26E |
| ID-005 | Importar y actualizar códigos postales SEPOMEX | Admin Global | ✅ | 26E |

### 1.2 Identidad Visual Institucional
| CU ID | Descripción | Actor | Estado | Fase |
|-------|------------|-------|--------|------|
| ID-006 | Carga de logo institucional y configuración de paleta de colores por plantel | Admin Plantel | ✅ | 1 |
| ID-007 | Administración de templates de comunicados (encabezado, pie, firma) | Admin Plantel | ✅ | 5 |
| ID-008 | Configuración de plantillas de boletas PDF (tipo de letra, espacios, firmas) | Admin Plantel | ✅ | 18 |
| ID-009 | Gestión de firma digital institucional (CEO/Director) | Admin Global | ✅ | 27 |

### 1.3 Calendario Institucional
| CU ID | Descripción | Actor | Estado | Fase |
|-------|------------|-------|--------|------|
| ID-010 | Creación de ciclos escolares SEP (Primaria/Secundaria) | Admin Global | ✅ | 1 |
| ID-011 | Creación de ciclos escolares UAEMEX (Preparatoria) | Admin Global | ✅ | 1 |
| ID-012 | Importar calendario oficial SEP (días no laborales, vacaciones, suspensiones) | Admin Global | ✅ | 1 |
| ID-013 | Importar calendario oficial UAEMEX | Admin Global | ✅ | 1 |
| ID-014 | Crear periodos de evaluación bimestral/parcial con fechas de cierre | Admin Plantel | ✅ | 1 |
| ID-015 | Marcar días festivos/suspensiones no contemplados en calendario oficial | Admin Plantel | ✅ | 27 |
| ID-016 | Generación automática de actas de inicio y cierre de ciclo | Admin Plantel | ⏳ | 29 |

---

## Dominio 2: Gestión de Estructura Académica

### 2.1 Configuración de Niveles, Grados y Grupos
| CU ID | Descripción | Actor | Estado | Fase |
|-------|------------|-------|--------|------|
| AC-001 | Configurar niveles educativos (Primaria, Secundaria, Preparatoria) | Admin Global | ✅ | 1 |
| AC-002 | Crear grados por nivel (1°-6° Primaria, 1°-3° Secundaria, 1°-6° Preparatoria) | Admin Global | ✅ | 1 |
| AC-003 | Crear grupos (A, B, C...) con capacidades máximas por grado | Admin Plantel | ✅ | 1 |
| AC-004 | Asignar grupos a planteles y niveles | Admin Plantel | ✅ | 1 |
| AC-005 | Cambiar asignación de grupo (traslado entre planteles o niveles) | Admin Plantel | ⏳ | 28 |
| AC-006 | Gestionar aulas físicas (nombre, capacidad, equipamiento: proyector, pizarra digital) | Admin Plantel | ✅ | 27 |
| AC-007 | Crear horarios de funcionamiento por plantel y nivel (entrada, salida, recesos) | Admin Plantel | ✅ | 1 |

### 2.2 Planes de Estudio y Mapas Curriculares
| CU ID | Descripción | Actor | Estado | Fase |
|-------|------------|-------|--------|------|
| AC-008 | Importar Plan de Estudios oficial SEP 2022 (NEM) para Primaria y Secundaria | Admin Global | ✅ | 1 |
| AC-009 | Importar Plan de Estudios UAEMEX CBU 2024 para Preparatoria | Admin Global | ✅ | 1 |
| AC-010 | CRUD de Materias (nombre, clave, horas lectivas, nivel requerido) | Coord Académico | ✅ | 1 |
| AC-011 | Asignar materias a plan de estudio (materia-plan-nivel-grado) | Coord Académico | ✅ | 1 |
| AC-012 | Visualizar mapa curricular (vista árbol: nivel → grado → materia → horas) | Coord Académico | ✅ | 19 |
| AC-013 | CRUD de temas/contenidos por materia (orden secuencial, horas estimadas) | Docente/Coord | ✅ | 1 |
| AC-014 | Crear planes de estudio alternativos o reducidos (ej: alumnos con NEE) | Coord Académico | ✅ | 29 |
| AC-015 | Publicar/archivar versiones de planes de estudio | Admin Plantel | ✅ | 28 |

### 2.3 Programación de Horarios
| CU ID | Descripción | Actor | Estado | Fase |
|-------|------------|-------|--------|------|
| AC-016 | Generar matriz de horarios vía aSc TimeTables (import XML, export matriz semanal) | Admin Plantel | ✅ | 3 |
| AC-017 | Visualizar horario semanal por grupo, docente o aula | Todos | ✅ | 3 |
| AC-018 | Gestionar cambios de horario en tiempo real (enfermedad docente, eventos especiales) | Coord Administrativo | ✅ | 31 |
| AC-019 | Detectar conflictos de doble asignación (docente, aula, grupo) | Sistema | ✅ | 31 |

---

## Dominio 3: Gestión de Población Escolar

### 3.1 Proceso de Admisión
| CU ID | Descripción | Actor | Estado | Fase |
|-------|------------|-------|--------|------|
| PE-001 | Crear solicitud de admisión (datos personales, nivel solicitado, documentos) | Solicitante/Admin | ✅ | 1 |
| PE-002 | Carga de documentos (CURP, acta de nacimiento, certificado previo, foto) | Solicitante | ✅ | 28 |
| PE-003 | Evaluación diagnóstica automatizada (cuestionarios por nivel) | Evaluador | ✅ | 30 |
| PE-004 | Asignación de estatus de solicitud (pendiente, evaluado, aprobado, rechazado) | Admin Plantel | ✅ | 1 |
| PE-005 | Generación de carta de aceptación/rechazo en PDF | Sistema | ⏳ | 28 |
| PE-006 | Seguimiento de expediente de admisión (timeline de estados) | Solicitante | ✅ | 28 |
| PE-007 | Importar listados de admitidos desde portal SEP (si aplica) | Admin Plantel | ⏳ | 30 |

### 3.2 Inscripción y Alta de Alumnos
| CU ID | Descripción | Actor | Estado | Fase |
|-------|------------|-------|--------|------|
| PE-008 | Crear registro de alumno con datos demográficos completos | Secretaria | ✅ | 1 |
| PE-009 | Asignación automática de grupo basada en cuota/capacidad | Sistema | ✅ | 1 |
| PE-010 | Asignación manual de grupo (excepciones, hermanos, necesidades especiales) | Admin Plantel | ✅ | 1 |
| PE-011 | Generación de matrícula única y código ADEN (si aplica SEP) | Sistema | ⏳ | 28 |
| PE-012 | Inscripción en materias específicas (secundaria/preparatoria optativas) | Sistema/Docente | ⏳ | 29 |
| PE-013 | Crear expediente académico inicial con fotocopia de CURP, acta, certificado anterior | Secretaria | ✅ | 1 |
| PE-014 | Generar y enviar credencial de alumno (digital/física) | Sistema | ✅ | 28 |

### 3.3 Reinscripción y Cambios de Estatus
| CU ID | Descripción | Actor | Estado | Fase |
|-------|------------|-------|--------|------|
| PE-015 | Reinscripción masiva de alumnos para nuevo ciclo (con validaciones: adeudos, becas) | Admin Plantel | ✅ | 27 |
| PE-016 | Verificación de no-adeudo (cobranza, cuotas, uniformes, etc.) | Coord Administrativo | ✅ | 31 |
| PE-017 | Cambio de grado (promoción automática al cierre de ciclo) | Sistema | ✅ | 10 |
| PE-018 | Cambio de grupo (por solicitud o necesidad académica) | Admin Plantel | ⏳ | 27 |
| PE-019 | Cambio de plantel (traslado entre sedes) | Admin Global | ⏳ | 27 |
| PE-020 | Baja temporal (licencia, suspensión disciplinaria) | Director | ⏳ | 28 |
| PE-021 | Baja definitiva (retiro voluntario, expulsión, abandono) | Director | ⏳ | 28 |
| PE-022 | Reactivación de alumno (retorno tras baja temporal) | Admin Plantel | ⏳ | 28 |

### 3.4 Expediente del Alumno (360°)
| CU ID | Descripción | Actor | Estado | Fase |
|-------|------------|-------|--------|------|
| PE-023 | Visualizar expediente consolidado (datos, académico, salud, conducta, familiar) | Autorizados | ✅ | 9 |
| PE-024 | Gestión de fotocopia de CURP, acta de nacimiento, certificado (Paperless-ngx+OCR) | Secretaria | ✅ | 28 |
| PE-025 | Histórico de inscripciones (ciclos anteriores, cambios de grupo) | Autorizados | ✅ | 28 |
| PE-026 | Descarga de expediente completo en ZIP | Admin Plantel | ⏳ | 29 |
| PE-027 | Auditoría de cambios en expediente (quién modificó qué, cuándo) | Admin | ✅ | 15 |

### 3.5 Gestión de Padres/Tutores
| CU ID | Descripción | Actor | Estado | Fase |
|-------|------------|-------|--------|------|
| PE-028 | Registro de padres biológicos, tutores legales y responsables de pago | Secretaria | ✅ | 1 |
| PE-029 | Gestión de múltiples tutores por alumno (custodia compartida, abuelos) | Secretaria | ⏳ | 27 |
| PE-030 | Actualización de datos de contacto (teléfono, email, domicilio) | Padre/Sistema | ✅ | 1 |
| PE-031 | Asignación de contacto de emergencia (no es padre ni tutor) | Secretaria | ✅ | 1 |
| PE-032 | Crear usuario portal para padre (vía Authentik, con permisos de visualización) | Sistema | ⏳ | 28 |
| PE-033 | Restricción de acceso (padre sin custodia, no ve información) | Sistema | ⏳ | 28 |

---

## Dominio 4: Gestión de Docentes y Personal

### 4.1 Registro y Control de Personal
| CU ID | Descripción | Actor | Estado | Fase |
|-------|------------|-------|--------|------|
| DP-001 | Registro de docentes (datos demográficos, cédula, acreditaciones, especialidad) | RH | ✅ | 1 |
| DP-002 | Registro de personal administrativo y de apoyo | RH | ✅ | 1 |
| DP-003 | Asignación de disponibilidad por docente (horas totales, jornada) | RH | ✅ | 30 |
| DP-004 | Gestión de expediente laboral digital (contrato, IMSS, infonavit) | RH | ✅ | 30 |
| DP-005 | Control de asistencia de personal (entrada/salida, puntualidad) | Prefecto | ✅ | 30 |
| DP-006 | Gestión de licencias y permisos de personal | RH | ✅ | 29 |
| DP-007 | Registro de capacitaciones y certificaciones docentes | RH | ✅ | 29 |

### 4.2 Asignación de Docentes a Grupos/Materias
| CU ID | Descripción | Actor | Estado | Fase |
|-------|------------|-------|--------|------|
| DP-008 | Asignar docente a grupo-materia (SEP: 1 titular primaria, N docentes secundaria/preparatoria) | Admin Plantel | ✅ | 1 |
| DP-009 | Validar reglas de asignación (carga horaria, especialidad, disponibilidad) | Sistema | ✅ | 1 |
| DP-010 | Reasignar docente (cambio de grupo, licencia, despido) | Admin Plantel | ✅ | 31 |
| DP-011 | Crear schedule de docente (vista semanal de clases, tiempos libres) | Sistema | ✅ | 3 |
| DP-012 | Gestionar docentes de apoyo (inglés, educación física, maestro especializado) | Admin Plantel | ✅ | 8 |

### 4.3 Evaluación de Desempeño Docente
| CU ID | Descripción | Actor | Estado | Fase |
|-------|------------|-------|--------|------|
| DP-013 | Evaluación 360° de docentes (directivos, pares, alumnos, padres) | Evaluador | ✅ | 3 |
| DP-014 | Cálculo automático de promedio por criterio y general | Sistema | ✅ | 3 |
| DP-015 | Visualización de histórico de evaluaciones por ciclo | Director/Coord | ✅ | 3 |
| DP-016 | Generación de plan de mejora basado en evaluación | Coord Académico | ✅ | 30 |
| DP-017 | Observación pedagógica en aula (con estatus semáforo: verde/amarilla/roja) | Observador | ✅ | Nuevas |
| DP-018 | Reporte de retroalimentación pedagógica (PDF con dimensiones y compromisos) | Sistema | ✅ | Nuevas |

---

## Dominio 5: Operación Académica Diaria

### 5.1 Registro de Clases y Asistencia
| CU ID | Descripción | Actor | Estado | Fase |
|-------|------------|-------|--------|------|
| OA-001 | Crear registro de clase diario (docente, grupo, materia, tema, fecha) | Docente | ✅ | 2 |
| OA-002 | Marcar asistencia (presente, falta, retardo, excusa) | Docente | ✅ | 2 |
| OA-003 | Justificación de faltas (certificado médico, evento especial) | Padre/Estudiante | ✅ | 31 |
| OA-004 | Generar reporte de asistencia (por alumno, grupo, docente, período) | Coord Académico | ✅ | 2 |
| OA-005 | Alertas de ausentismo (>20% inasistencia) | Sistema | ✅ | 4 |
| OA-006 | Visualización de clase presencial vs remota (post-COVID) | Docente | ✅ | 31 |
| OA-007 | Exportar reporte de asistencia (CSV, Excel, PDF) | Docente | ✅ | 2 |

### 5.2 Planificación Docente y Avance Curricular
| CU ID | Descripción | Actor | Estado | Fase |
|-------|------------|-------|--------|------|
| OA-008 | Crear plan de clase semanal/mensual (temas, actividades, recursos) | Docente | ✅ | 6 |
| OA-009 | Registrar temas impartidos vs planeados (estado: impartido/pendiente) | Docente | ✅ | 6 |
| OA-010 | Cálculo automático de % de cobertura curricular | Sistema | ✅ | 6 |
| OA-011 | Alertas de rezago (menos del 80% de temas cubiertos) | Sistema | ✅ | 30 |
| OA-012 | Ajuste dinámico de plan ante suspensiones o eventos | Docente | ✅ | 27 |
| OA-013 | Visualización de avance por grado/materia a nivel plantel | Director | ⏳ | 28 |

### 5.3 Tareas, Proyectos y Entregas
| CU ID | Descripción | Actor | Estado | Fase |
|-------|------------|-------|--------|------|
| OA-014 | Asignar tarea (descripción, fecha de entrega, rubrica, peso en calificación) | Docente | ✅ | 2 |
| OA-015 | Subida de archivos por alumno (MinIO: PDF, Word, imágenes, video) | Estudiante | ✅ | 2 |
| OA-016 | Validación automática de extensión/tamaño de archivo | Sistema | ✅ | 2 |
| OA-017 | Detección de plagio (Turnitin, Grammarly, análisis interno) | Sistema | ✅ | 30 |
| OA-018 | Calificación de tarea con rúbrica (criterios, niveles de logro) | Docente | ✅ | 2 |
| OA-019 | Retroalimentación escrita/vídeo en tarea | Docente | ⏳ | 29 |
| OA-020 | Reasignación de tarea (por error o necesidad) | Docente | ✅ | 27 |
| OA-021 | Reporte de tareas pendientes (alumno, grupo, docente) | Todos | ✅ | 2 |
| OA-022 | Exportar historial de tareas y calificaciones | Docente | ✅ | 2 |

---

## Dominio 6: Evaluación y Calificación

### 6.1 Gradebook e Ingreso de Calificaciones
| CU ID | Descripción | Actor | Estado | Fase |
|-------|------------|-------|--------|------|
| EV-001 | Visualización de gradebook (spreadsheet alumno × actividad) | Docente | ✅ | 2 |
| EV-002 | Ingreso de calificaciones numérica inline (1-10, 0-100) | Docente | ✅ | 2 |
| EV-003 | Cálculo automático de promedio por periodo | Sistema | ✅ | 10 |
| EV-004 | Aplicar ponderación dinámica (examen 40%, tareas 30%, proyecto 30%) | Sistema | ✅ | 10 |
| EV-005 | Ajuste manual con justificación (acta de reunión, compromiso alumno) | Docente | ✅ | 10 |
| EV-006 | Cierre de periodo (bloqueo de edición, generación de historial) | Coord Académico | ✅ | 27 |
| EV-007 | Detección de inconsistencias (alumno aprobado pero sin entregas) | Sistema | ✅ | 30 |
| EV-008 | Historial de cambios en calificación (quién, cuándo, justificación) | Sistema | ✅ | 15 |

### 6.2 Esquemas de Ponderación
| CU ID | Descripción | Actor | Estado | Fase |
|-------|------------|-------|--------|------|
| EV-009 | CRUD de esquemas de ponderación por nivel/materia | Coord Académico | ✅ | 10 |
| EV-010 | Validación: suma de pesos = 100% | Sistema | ✅ | 10 |
| EV-011 | Histórico de versiones de esquemas (cambios ciclo a ciclo) | Sistema | ✅ | 10 |
| EV-012 | Ponderación diferenciada por alumno (NEE, recuperación) | Coord Académico | ⏳ | 29 |

### 6.3 Evaluaciones Formales (Exámenes)
| CU ID | Descripción | Actor | Estado | Fase |
|-------|------------|-------|--------|------|
| EV-013 | Programación de exámenes (ordinario, final, extraordinario) | Admin Plantel | ✅ | 6 |
| EV-014 | Asignación de aula y hora por evaluación | Sistema | ⏳ | 27 |
| EV-015 | Ingreso de resultados de examen escrito/oral | Docente | ✅ | 6 |
| EV-016 | Cálculo de promedio final (libreta + examen final) | Sistema | ✅ | 10 |
| EV-017 | Generación de acta de calificaciones para envío a SEP | Sistema | ⏳ | 28 |
| EV-018 | Identificación automática de alumnos en extraordinario (<6.0) | Sistema | ✅ | 30 |

### 6.4 Boletas y Reportes de Calificación
| CU ID | Descripción | Actor | Estado | Fase |
|-------|------------|-------|--------|------|
| EV-019 | Generación de boleta individual por período | Sistema | ✅ | 3 |
| EV-020 | Generación masiva de boletas (grupo, grado, plantel) | Sistema | ✅ | 10 |
| EV-021 | Boleta con firma digital (Ed25519) y verificación de autenticidad | Sistema | ✅ | 27 |
| EV-022 | Descarga en PDF/Excel por alumno o padre | Padre/Estudiante | ✅ | 9 |
| EV-023 | Historial de boletas (ciclos anteriores) | Padre/Estudiante | ✅ | 9 |
| EV-024 | Boleta con observaciones pedagógicas (fortalezas/áreas de mejora) | Docente | ⏳ | 30 |
| EV-025 | Configuración de escalas de evaluación cualitativa (excelente/bueno/suficiente) | Admin Plantel | ⏳ | 27 |

---

## Dominio 7: Inteligencia Artificial y Analítica Predictiva

### 7.1 Detección de Riesgo Académico
| CU ID | Descripción | Actor | Estado | Fase |
|-------|------------|-------|--------|------|
| IA-001 | Cálculo de índice de riesgo (calificación < 6.0, ausentismo > 20%) | Sistema | ✅ | 4 |
| IA-002 | Alertas automáticas a docente/orientador/padre | Sistema | ✅ | 4 |
| IA-003 | Clasificación de nivel de riesgo (BAJO/MEDIO/ALTO) | Sistema | ✅ | 4 |
| IA-004 | Dashboard de alumnos en riesgo (filtrable por grupo, materia, plantel) | Director | ✅ | 4 |
| IA-005 | Predicción de abandono escolar basada en patrones históricos | Sistema | ⏳ | 32 |

### 7.2 Learning Paths (Rutas Adaptativas)
| CU ID | Descripción | Actor | Estado | Fase |
|-------|------------|-------|--------|------|
| IA-006 | Generación automática de ruta de refuerzo por alumno | IA Claude | ✅ | 4B |
| IA-007 | Recomendación de recursos (videos, ejercicios, lecturas) | IA Claude | ✅ | 4B |
| IA-008 | Seguimiento de progreso en learning path (% completado) | Estudiante | ✅ | 4B |
| IA-009 | Ajuste dinámico de ruta según desempeño | IA Claude | ✅ | 32 |
| IA-010 | Asignación manual de ruta por docente | Docente | ✅ | 4B |

### 7.3 Asistente Pedagógico (Chatbot NL→SQL)
| CU ID | Descripción | Actor | Estado | Fase |
|-------|------------|-------|--------|------|
| IA-011 | Consulta en lenguaje natural: "¿Quién aprobó matemáticas?" | Docente/Admin | ✅ | 17 |
| IA-012 | Generación automática de consultas SQL desde texto | IA Claude | ✅ | 17 |
| IA-013 | Contexto académico (plantel, ciclo, grupo activo) en respuestas | Sistema | ✅ | 17 |
| IA-014 | Recomendaciones pedagógicas basadas en datos (ej: estudiantes con similar perfil) | IA Claude | ✅ | 32 |
| IA-015 | Historial de conversaciones persistente | Sistema | ⏳ | 28 |

### 7.4 Análisis Estadístico y BI
| CU ID | Descripción | Actor | Estado | Fase |
|-------|------------|-------|--------|------|
| IA-016 | Dashboard KPI: matriculación, retención, rendimiento académico | Director | ✅ | 4 |
| IA-017 | Gráficos de distribución de calificaciones por materia/grupo | Coord Académico | ✅ | 4 |
| IA-018 | Análisis de tendencias (mejoría/empeoramiento ciclo a ciclo) | Director | ✅ | 4 |
| IA-019 | Comparativa entre planteles (KPI relativo) | Admin Global | ✅ | 16 |
| IA-020 | Exportación de reportes BI (PDF, PowerPoint, Excel) | Admin | ✅ | 28 |
| IA-021 | Dashboards personalizados por rol (docente, director, padre) | Sistema | ✅ | 16 |

---

## Dominio 8: Comunicación e Interacción Comunitaria

### 8.1 Comunicados Institucionales
| CU ID | Descripción | Actor | Estado | Fase |
|-------|------------|-------|--------|------|
| CO-001 | Crear comunicado (oficial, informativo, urgente) | Admin/Director | ✅ | 5 |
| CO-002 | Segmentación de audiencia (todos, padres, alumnos, docentes, plantel) | Admin | ✅ | 5 |
| CO-003 | Envío de comunicado por email/notificación/SMS | Sistema | ✅ | 5 |
| CO-004 | Acuse de recibo digital | Padre/Estudiante | ✅ | 5 |
| CO-005 | Reporte de entrega (quién leyó, quién aún no) | Admin | ✅ | 31 |
| CO-006 | Archivo de comunicados históricos (búsqueda por palabra clave) | Todos | ✅ | 5 |
| CO-007 | Comunicados recurrentes (matrícula, vacaciones, eventos) | Admin | ✅ | 30 |

### 8.2 Notificaciones en Tiempo Real
| CU ID | Descripción | Actor | Estado | Fase |
|-------|------------|-------|--------|------|
| CO-008 | Notificación de calificación nuevamente ingresada | Padre/Estudiante | ✅ | 5 |
| CO-009 | Notificación de tarea próxima a vencer | Estudiante | ✅ | 5 |
| CO-010 | Notificación de alerta académica (riesgo) | Padre/Estudiante | ✅ | 4 |
| CO-011 | Notificación de evento escolar (reunión, suspensión, festivo) | Todos | ✅ | 5 |
| CO-012 | Push notifications nativas (móvil, browser) | Sistema | ✅ | 20 |
| CO-013 | Badge/contador de notificaciones no leídas | Todos | ✅ | 5 |
| CO-014 | Marcar notificación como leída/todas leídas | Todos | ✅ | 5 |

### 8.3 Portales de Usuario
| CU ID | Descripción | Actor | Estado | Fase |
|-------|------------|-------|--------|------|
| CO-015 | Portal del Alumno 360° (KPIs, calificaciones, tareas, badges, LP) | Estudiante | ✅ | 9 |
| CO-016 | Portal del Padre (calificaciones/asistencia de hijos, comunicados) | Padre | ✅ | 9 |
| CO-017 | Portal del Docente (libreta, alumnos, plan, avisos) | Docente | ✅ | 9 |
| CO-018 | Portal del Directivo (dashboards, reportes, personal, usuarios) | Director | ✅ | 9 |
| CO-019 | Búsqueda global (alumno, documento, comunicado) | Todos | ✅ | 9 |
| CO-020 | Personalización de dashboard (widgets, orden, filtros) | Todos | ⏳ | 31 |

### 8.4 Foros y Espacios de Colaboración
| CU ID | Descripción | Actor | Estado | Fase |
|-------|------------|-------|--------|------|
| CO-021 | Foro por materia (docente + alumnos) | Docente/Estudiante | ⏳ | 30 |
| CO-022 | Foro de tutores (orientación, consejos) | Padre/Tutor | ⏳ | 30 |
| CO-023 | Moderación de foros (eliminar posts inapropiados) | Moderador | ⏳ | 30 |

---

## Dominio 9: Salud, Bienestar y Conducta

### 9.1 Expediente Médico Escolar
| CU ID | Descripción | Actor | Estado | Fase |
|-------|------------|-------|--------|------|
| SB-001 | Registro de datos de salud básicos (grupo sanguíneo, alergias, vacunas) | Secretaria | ✅ | 1 |
| SB-002 | Listado de medicamentos permitidos en escuela (alergia, asma, diabetes) | Médico | ✅ | 3 |
| SB-003 | Control de dispensación de medicamentos | Médico | ⏳ | 27 |
| SB-004 | Registro de visita a enfermería (síntomas, tratamiento, referencia) | Médico | ✅ | 3 |
| SB-005 | Generación de acta de incidente médico | Médico | ⏳ | 28 |
| SB-006 | Alertas de condiciones crónicas (epilepsia, diabetes, asma) | Sistema | ✅ | 31 |
| SB-007 | Contacto de emergencia automático si hay incidente grave | Sistema | ✅ | 31 |
| SB-008 | Historial médico consolidado (ciclos anteriores) | Médico/Padre | ✅ | 3 |
| SB-009 | Certificados de salud (apto para actividad física) | Médico | ⏳ | 28 |

### 9.2 Gestión de Conducta y Disciplina
| CU ID | Descripción | Actor | Estado | Fase |
|-------|------------|-------|--------|------|
| SB-010 | Reporte de incidente disciplinario (tipo, involucrados, testigos) | Prefecto | ✅ | 3 |
| SB-011 | Clasificación de falta (leve, grave, muy grave) | Prefecto | ✅ | 3 |
| SB-012 | Aplicación de sanción (reporte, suspensión, expulsión) | Director | ✅ | 27 |
| SB-013 | Plan de mejora (compromisos del alumno y padre) | Orientador | ✅ | 27 |
| SB-014 | Seguimiento de cumplimiento de plan | Tutor | ✅ | 27 |
| SB-015 | Historial conductual por alumno (ciclo actual + histórico) | Director/Padre | ✅ | 3 |
| SB-016 | Análisis de patrones de conducta (factores de riesgo) | IA Claude | ✅ | 32 |
| SB-017 | Generación de acta de evaluación de conducta | Sistema | ✅ | 28 |

### 9.3 Programa de Bienestar Integral
| CU ID | Descripción | Actor | Estado | Fase |
|-------|------------|-------|--------|------|
| SB-018 | Encuestas de clima escolar (seguridad, bullying, satisfacción) | Sistema | ✅ | 7 |
| SB-019 | Análisis de resultados (gráficos, tendencias) | Orientador | ✅ | 7 |
| SB-020 | Alertas de potencial bullying (análisis de encuestas) | Sistema | ⏳ | 32 |
| SB-021 | Seguimiento de casos de riesgo psicosocial | Psicólogo | ⏳ | 30 |
| SB-022 | Programa de tutoría académica y emocional | Tutor | ⏳ | 29 |
| SB-023 | Eventos de bienestar (día de la amabilidad, actividades lúdicas) | Coord Social | ✅ | 31 |

---

## Dominio 10: Administración, Seguridad y Cumplimiento

### 10.1 Gestión de Usuarios y Control de Acceso (RBAC)
| CU ID | Descripción | Actor | Estado | Fase |
|-------|------------|-------|--------|------|
| AD-001 | Sincronización JIT de usuarios desde Authentik | Sistema | ✅ | 26C |
| AD-002 | Asignación de roles a usuarios (18 roles disponibles) | Admin Global | ✅ | 1 |
| AD-003 | Gestión de permisos granulares (módulo + acción: VISUALIZAR/EDITAR) | Admin Global | ✅ | Nuevas |
| AD-004 | Filtrado dinámico de menú según permisos | Sistema | ✅ | 26B |
| AD-005 | Bloqueo de endpoints según rol (middleware FastAPI) | Sistema | ✅ | Nuevas |
| AD-006 | Desactivación de usuario (soft delete, mantener historial) | RH | ✅ | 1 |
| AD-007 | Auditoría de intentos fallidos de login | Sistema | ✅ | 28 |
| AD-008 | Reset de contraseña (vía Authentik o email) | Usuario/Admin | ✅ | 1 |

### 10.2 Auditoría y Cumplimiento
| CU ID | Descripción | Actor | Estado | Fase |
|-------|------------|-------|--------|------|
| AD-009 | Bitácora de todos los cambios (INSERT/UPDATE/DELETE) en tablas críticas | Sistema | ✅ | 15 |
| AD-010 | Registro de quién, qué, cuándo y justificación en cambios | Sistema | ✅ | 15 |
| AD-011 | Imposibilidad de eliminar registros (soft delete + auditoría) | Sistema | ✅ | 15 |
| AD-012 | Reporte de auditoría (acceso a expedientes, cambios de calificación) | Admin | ✅ | 15 |
| AD-013 | Conformidad LRFD (Ley de Reglamentación Federal de Datos) | Sistema | ✅ | 33 |
| AD-014 | Cumplimiento de normas de SEP/UAEMEX (archivos, informes) | Admin | ✅ | 28 |

### 10.3 Backup y Recuperación
| CU ID | Descripción | Actor | Estado | Fase |
|-------|------------|-------|--------|------|
| AD-015 | Backup automático diario de BD PostgreSQL | Sistema | ✅ | 27 |
| AD-016 | Backup de archivos en MinIO (tareas, documentos, expedientes) | Sistema | ✅ | 27 |
| AD-017 | Restauración de backup en ambiente de recuperación ante desastre | Admin | ⏳ | 33 |
| AD-018 | Retención de backups según política (30 días full, 7 días incremental) | Sistema | ⏳ | 33 |

### 10.4 Encripción y Seguridad de Datos
| CU ID | Descripción | Actor | Estado | Fase |
|-------|------------|-------|--------|------|
| AD-019 | Encripción de CURP/datos sensibles en reposo (pgcrypto) | Sistema | ✅ | 26A |
| AD-020 | HTTPS obligatorio en todos los endpoints | Sistema | ✅ | 1 |
| AD-021 | Hash de contraseñas con Argon2id | Sistema | ✅ | 1 |
| AD-022 | Firma digital de documentos oficiales (Ed25519) | Sistema | ✅ | 27 |
| AD-023 | Autenticación multifactor (MFA) en Authentik | Sistema | ✅ | 29 |
| AD-024 | Gestión centralizada de secretos (HashiCorp Vault) | Sistema | ✅ | 29 |

### 10.5 Mantenimiento y Configuración del Sistema
| CU ID | Descripción | Actor | Estado | Fase |
|-------|------------|-------|--------|------|
| AD-025 | Panel de administración (usuarios, roles, variables del sistema) | Admin | ✅ | 11 |
| AD-026 | Gestión de variables del sistema (sin SSH, vía UI) | Admin Plantel | ✅ | 26A |
| AD-027 | Configuración de emails (SMTP, Jinja2 templates: reinscripción, alerta, calificación) | Admin Global | ✅ | 27 |
| AD-028 | Logs de sistema (accesos, errores, eventos críticos) | Admin | ✅ | 1 |
| AD-029 | Health check de servicios (BD, Valkey, MinIO, Authentik) | Admin | ✅ | 27 |
| AD-030 | Estadísticas de uso (usuarios activos, espacios en disco) | Admin | ⏳ | 28 |

---

## Análisis de Brecha: Casos de Uso Pendientes por Prioridad

### 🔴 **Críticos** (Impacto Alto, Esfuerzo Medio-Alto)
Estos afectan operación diaria y cumplimiento normativo:

| # | CU | Descripción | Estimado | Fase | Estado |
|----|----|----|----------|-------|--------|
| 1 | PE-015 | Reinscripción masiva con validaciones | 12 h | 27 | ✅ 2026-06-11 |
| 2 | EV-006 | Cierre de período (bloqueo + backup) | 8 h | 27 | ✅ 2026-06-11 |
| 3 | SB-012 | Aplicación de sanciones disciplinarias | 8 h | 27 | ✅ 2026-06-11 |
| 4 | AC-006 | Gestionar aulas físicas | 10 h | 27 | ✅ 2026-06-11 |
| 5 | AD-015 | Backup automático diario BD | 6 h | 27 | ✅ 2026-06-11 |
| 6 | DP-006 | Gestión de licencias/permisos personal | 10 h | 29 | ✅ 2026-06-11 |
| 7 | PE-016 | Verificación de no-adeudo en reinscripción | 8 h | 31 | ⏳ |
| 8 | AD-013 | Conformidad LRFD (datos personales) | 16 h | 32 | ✅ 2026-07-03 |

### 🟡 **Altos** (Impacto Medio-Alto, Esfuerzo Medio)
Mejoran la experiencia y eficiencia:

| # | CU | Descripción | Estimado | Fase |
|----|----|----|----------|-------|
| 6 | PE-024 | Gestión de documentos escaneados (MinIO OCR) | 14 h | 24 |
| 7 | OA-017 | Detección de plagio (Turnitin/interno) | 12 h | 30 |
| 8 | IA-005 | Predicción de abandono escolar | 16 h | 32 |
| 9 | CO-015 | Portal padre con múltiples hijos | 6 h | 28 |
| 10 | SB-016 | Análisis de patrones conductuales (IA) | 10 h | 32 |

### 🟢 **Medios** (Impacto Medio, Esfuerzo Bajo-Medio)
Complementos útiles:

| # | CU | Descripción | Estimado | Fase |
|----|----|----|----------|-------|
| 11 | ID-015 | Marcar días festivos ad-hoc | 3 h | 27 |
| 12 | OA-018 | Retroalimentación vídeo en tareas | 8 h | 29 |
| 13 | CO-021 | Foros por materia | 12 h | 30 |
| 14 | SB-023 | Programa de bienestar (eventos) | 10 h | 31 |

---

## Recomendaciones Estratégicas

### Hoja de Ruta (2026-2027)

**Q3 2026 (Jul-Sep):**
- ✅ Consolidar Fases 1-10 (APEX Library Integration + Certs Digitales)
- 🔧 Fase 27: Backup/Recuperación, Reinscripción masiva, Cierre de período

**Q4 2026 (Oct-Dic):**
- 🔧 Fase 28: Gestión de documentos (MinIO + OCR), Expediente digital, Firma digital centralizada
- 🔧 Fase 29: Encripción de datos, MFA, Licencias de personal, Retroalimentación vídeo

**Q1 2027 (Ene-Mar):**
- 🔧 Fase 30: Predicción de abandono, Evaluación diagnóstica, Análisis de conducta (IA)
- 🔧 Fase 31: Foros, Customización de dashboards, Programa de bienestar

**Q2 2027 (Abr-Jun):**
- 🔧 Fase 32: Cumplimiento LRFD, Disaster Recovery, Conformidad normativa

### Métricas de Éxito

| Métrica | Objetivo 2027 |
|---------|---|
| **Cobertura de CU** | 100% (de 195 casos de uso actuales) |
| **Automatización** | 85% de procesos sin intervención manual |
| **Adopción de IA** | 60% de docentes usando Learning Paths |
| **Satisfacción de Usuario** | 4.5/5.0 (encuestas trimestrales) |
| **Uptime del Sistema** | 99.5% |

---

## Conclusión

ADES Nevadi ha alcanzado un nivel de **madurez operacional** (Fases 1-10). Este catálogo define claramente el **roadmap hasta la excelencia** (Fases 27-35), incorporando:

✅ **Seguridad y cumplimiento normativo**  
✅ **Automatización inteligente (IA/ML)**  
✅ **Experiencia de usuario empresarial (APEX-style)**  
✅ **Escalabilidad multi-plantel y multi-nivel**  

La priorización recomendada asegura que las **funcionalidades críticas** se implementen primero, manteniendo la estabilidad del sistema mientras se agregan nuevas capacidades.

---

---

## Historial de Cobertura

| Fecha | Fases Completadas | CUs ✅ | CUs ⏳ | Total | % |
|-------|-------------------|--------|--------|-------|---|
| 2026-06-09 (inicial) | 1–10 | 56 | 139 | 195 | 29% |
| 2026-06-11 (sesión 1) | 1–27 (parcial) | 146 | 84 | 230 | 63.5% |
| 2026-06-11 (sesión 2) | 1–28 + FASE 29 parcial | **158** | **72** | **230** | **68.7%** |
| 2026-06-11 (sesión 3) | 1–30 completas | **165** | **65** | **230** | **71.7%** |
| 2026-06-11 (sesión 4) | 1–31 completas | **173** | **57** | **230** | **75.2%** |
| 2026-07-03 (sesión 5) | 1–39 + Grupos A-E (auditoría de brecha) | **192** | **38** | **230** | **83.5%** |

> Nota: El total pasó de 195 a 230 porque el catálogo fue expandido con CUs adicionales en los dominios PE, DP, SB y AD durante el análisis de brecha completo.
> Nota (sesión 5): el salto de 173→192 incluye tanto los 19 CU implementados directamente en esta
> sesión (Grupos A-D del plan de auditoría 2026-07-03) como CUs de las Fases 32-39 (sesión
> 2026-06-12, ver `.agent/STATE.md`) que ya estaban completos en código pero nunca se reflejaron en
> este catálogo — la tabla no se actualizaba en cada sesión intermedia.

**Nuevos CUs completados en sesión 2 (2026-06-11):**  
AC-006 (aulas), PE-002 (carga docs), PE-024 (expediente), PE-025 (histórico), AD-015/016 (backups), AD-027 (emails), AD-023 (MFA), AD-024 (Vault), DP-006 (licencias), DP-007 (capacitaciones) + Auditoría v2 (038+039, 90 tablas).

**Nuevos CUs completados en sesión 3 (2026-06-11) — FASE 30:**  
DP-003 (disponibilidad docente), DP-004 (expediente laboral digital), DP-005 (asistencia personal), EV-007 (detección inconsistencias), EV-018 (candidatos extraordinario), OA-011 (alertas rezago), CO-007 (comunicados recurrentes).  
Migración: `041_rrhh_expediente_asistencia.sql`. Backend: 3 routers nuevos + 4 endpoints detección. Frontend: 3 componentes RRHH.

**Nuevos CUs completados en sesión 4 (2026-06-11) — FASE 31:**  
SB-006 (condiciones crónicas), SB-007 (alerta emergencia), OA-003 (justificaciones faltas), PE-016 (verificación no-adeudo), CO-005 (reporte lectura comunicados), AC-018 (cambios horario), AC-019 (conflictos doble asignación), DP-010 (reasignación docente).  
Migración: `042_operatividad_avanzada.sql`. Backend: 2 routers nuevos + 5 endpoints extendidos. Frontend: 2 componentes nuevos. Fix CRUD admin.component.ts: implementados 6 stubs (ciclos/planteles/grupos). Fix TS: primeng/datepicker, apex-component-library.

**Documento actualizado:** 2026-06-11 (sesión 4)  
**Responsable:** Equipo de Arquitectura ADES  
**Próxima revisión:** Al completar FASE 31
