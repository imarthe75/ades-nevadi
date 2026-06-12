# Especificación: Catálogo Completo de Casos de Uso - ADES

**Version:** 1.1.0  
**Status:** Active  
**Last Updated:** 2026-06-12  
**Scope:** Todas las fases implementadas (1-10, 15-23, 29-31)  
**Audience:** Arquitectos, Desarrolladores, Product Managers  

---

## 🎯 Introducción

Este documento define de forma exhaustiva los **22 casos de uso críticos** del sistema ADES, mapeados a:
- Actores del sistema (roles de Authentik / nivel_acceso numérico)
- Flujos de negocio paso a paso
- Reglas de negocio específicas por nivel educativo (SEP Primaria/Secundaria, UAEMEX Preparatoria)
- Impacto técnico en PostgreSQL, n8n, ntfy y componentes Angular
- UI Interactive Grid con edición inline

## 🔐 Tabla de Actores — Mapeo nivel_acceso

| Nombre actor (CU) | nivel_acceso | Descripción | roleGuard Angular |
|---|---|---|---|
| `ADMIN_GLOBAL` | 1 | Acceso total al sistema + configuración | roleGuard(1) |
| `ADMIN_PLANTEL` | 2 | Gestión de un plantel específico | roleGuard(2) |
| `DIRECTOR` / `COORDINADOR_*` / `SECRETARIA_ACADEMICA` / `MEDICO_ESCOLAR` / `ORIENTADOR` / `PREFECTO` (supervisor) | 3 | Personal directivo y administrativo | roleGuard(3) |
| `DOCENTE` / `TUTOR` / `PREFECTO` (levantador) | 4 | Personal operativo de aula | roleGuard(4) |
| `PADRE_FAMILIA` / `ALUMNO` | 5 | Usuarios externos del portal | minNivel: 5 |

> **Nota:** `COORDINADOR_AREA` (CU-02) se mapea a nivel_acceso=3. La gestión de áreas académicas se implementa en `planes-estudio.component.ts` (tab "Catálogo"), no como componente separado. El RLS por coordinador es una mejora planificada.

> **Nota:** La importación masiva CSV/Excel está disponible en: Alumnos, Profesores, Grupos, Aulas y Materias — a través del componente `ImportButtonComponent` en la barra de herramientas de cada módulo.

---

## 📋 Índice de Casos de Uso

### Módulo 1: Identidad e Infraestructura Multinivel
- **CU-01:** Apertura y Configuración de Ciclo Escolar Diferenciado
- **CU-02:** Gestión Transversal de Áreas Académicas por Coordinación Global

### Módulo 2: Control Escolar, Inscripciones y Aula
- **CU-03:** Inscripción y Asignación de Alumnos a Grupos
- **CU-04:** Pase de Lista Diario Unificado (Primaria - NEM)
- **CU-05:** Pase de Lista por Asignatura/Hora (Secundaria y Preparatoria)

### Módulo 3: Evaluación, Calificaciones e IA
- **CU-06:** Gestión del Gradebook Curricular y Evaluación Formativa (Primaria - NEM)
- **CU-07:** Evaluación de Períodos y Exámenes Extraordinarios (Preparatoria - UAEMEX)
- **CU-08:** Detección Predictiva de Alumnos en Riesgo Académico mediante IA

### Módulo 4: Operación Especializada, Salud y Conducta
- **CU-09:** Control de Expediente Médico y Alertas de Incidentes
- **CU-10:** Reportes Disciplinarios y Compromisos de Conducta

### Módulo 5: Comunicación, Reportes y BI
- **CU-11:** Emisión Masiva de Boletas y Certificados Digitales Firmados
- **CU-12:** Consumo de Dashboards Ejecutivos y KPIs por Plantel

---

## 📘 Formato de Cada Caso de Uso

```
### CU-##: Nombre del Caso de Uso

**Nivel de Riesgo:** [Bajo | Medio | Alto]  
**Frecuencia:** [Diaria | Semanal | Mensual | Por período | Puntual]  
**Complejidad:** [Baja | Media | Alta]

#### Actores Involucrados
- `ROL_1` — Descripción breve
- `ROL_2` — Descripción breve

#### Precondiciones
1. Condición A
2. Condición B

#### Flujo Principal
1. Actor realiza acción X
2. Sistema valida Y
3. Sistema persiste en BD
4. Sistema dispara notificación/webhook

#### Reglas de Negocio Críticas
- **RN-1:** Descripción de restricción
- **RN-2:** Descripción de restricción

#### Impacto Técnico
- **BD:** Tablas afectadas, triggers, índices
- **API:** Endpoints REST requeridos
- **Frontend:** Componentes Angular, grids Interactive
- **Automatización:** Webhooks n8n, tareas Celery
- **Notificaciones:** Canales push, email, SMS

#### Escenarios Alternativos
- **A1:** Si ocurre X, entonces hacer Y
- **A2:** Si ocurre Z, entonces hacer W

#### Casos de Error
- **E1:** Validación falla → Mostrar error específico
```

---

## 🔧 Matriz de Mapeo Técnico

Referencia rápida que vincula cada CU a sus componentes técnicos:

| CU | Tablas PostgreSQL | Endpoints FastAPI | Componentes Angular | Triggers/Jobs | Notificaciones |
|----|------------------|-------------------|------------------|--------------|----------------|
| CU-01 | `ades_ciclos_escolares`, `ades_periodos_evaluacion`, `ades_plantel_niveles` | POST/PATCH `/admin/ciclos` | `admin.component.ts` tab "Ciclos Escolares" (Interactive Grid) | Creación de periodos automática | — |
| CU-02 | `ades_areas_academicas`, `ades_coordinaciones_area` | GET/PATCH `/materias` `/planes-estudio` | `planes-estudio.component.ts` tab "Catálogo" (Editor inline) | — | — |
| CU-03 | `ades_estudiantes`, `ades_grupos`, `ades_reinscripciones` | POST `/alumnos` PATCH `/reinscripcion/{id}` | `alumnos.component.ts` (alta inicial) + `reinscripcion.component.ts` (asignación a grupo) | Webhook → Authentik API | Email confirmación |
| CU-04 | `ades_asistencias`, `ades_clases` | POST/PATCH `/asistencias` | `asistencias.component.ts` (Grid filtrable por grupo) | Alert ausentismo en n8n | Push ntfy si ausencia |
| CU-05 | `ades_asistencias`, `ades_clases`, `ades_horarios` | POST/PATCH `/asistencias` | `asistencias.component.ts` (Grid por materia) | Cálculo de % ausentismo Celery | Push si riesgo |
| CU-06 | `ades_calificaciones_periodo`, `ades_materias_plan`, `ades_rubricas` | PATCH `/calificaciones` | `gradebook.component.ts` (Spreadsheet editable) | `calcular_calificacion_periodo()` trigger PG | — |
| CU-07 | `ades_calificaciones_evaluaciones`, `ades_evaluaciones`, `ades_periodos_evaluacion` | PUT `/calificaciones/{id}` | `evaluaciones.component.ts` (Grid Agenda + Libreta) | Recalculador de Kárdex | SMS si reprobado |
| CU-08 | `ades_reportes_academicos`, `ades_calificaciones_periodo`, `ades_asistencias` | GET `/reportes-academicos` | `reportes.component.ts` (Dashboard BI) | Agente IA (LangChain + Claude) Celery semanal | Email director si riesgo alto |
| CU-09 | `ades_expedientes_medicos`, `ades_incidentes_medicos` | PATCH `/expedientes-medicos` | `expediente-medico.component.ts` (Drawer formulario) | Webhook crítico n8n | Push ntfy urgente a padre |
| CU-10 | `ades_reportes_conducta`, `ades_personas` | POST/PATCH `/reportes-conducta` | `conducta.component.ts` (Grid + Drawer acuse) | Email padre obligatorio | Push notificación acuse |
| CU-11 | `ades_calificaciones_periodo` (lectura), `MinIO` (almacenamiento), `ades_certificados_digitales` | POST `/reportes/boletas-lote` POST `/certificados` | `certificados.component.ts` (firma Ed25519 + folio QR) + `reportes.component.ts` (generador boletas) | Celery → Carbone (3001) → Stirling-PDF (8081) → MinIO | Email descarga a padre |
| CU-12 | Vistas materializadas `ades_bi.*` | GET `/superset/guest-token` | `bi.component.ts` (iframe embed) | — | — |

---

## 🌐 Módulo 1: Identidad e Infraestructura Multinivel

### CU-01: Apertura y Configuración de Ciclo Escolar Diferenciado

**Nivel de Riesgo:** Alto  
**Frecuencia:** 1-2 veces por año (inicio de ciclo)  
**Complejidad:** Media  

#### Actores Involucrados
- `ADMIN_GLOBAL` — Gestión de ciclos a nivel institución
- `SECRETARIA_ACADEMICA` — Validación y confirmación de fechas

#### Precondiciones
1. El plantel debe estar activo en `ades_planteles` con `is_active=TRUE`
2. El nivel educativo debe existir en `ades_niveles_educativos`
3. La relación `ades_plantel_niveles` debe indicar que el plantel ofrece ese nivel

#### Flujo Principal

1. El `ADMIN_GLOBAL` accede al módulo **Administración → Ciclos Escolares**
2. Da clic en **"Nuevo Ciclo"** → Se abre un Dialog de Angular
3. Selecciona:
   - **Plantel:** Metepec, Tenancingo, o Ixtapan
   - **Nivel:** Primaria, Secundaria, o Preparatoria
   - **Fechas:** Inicio y fin del ciclo
4. El sistema valida:
   - **Si Preparatoria + Ixtapan:** Bloquea. Mensaje: "Ixtapan no cuenta con nivel preparatoria. Seleccione otro plantel."
   - **Si Primaria/Secundaria:** Calcula automáticamente 3 periodos de evaluación (Bimestral SEP)
   - **Si Preparatoria (Metepec/Tenancingo):** Crea los periodos semestrales UAEMEX (26B / 27A)
5. FastAPI inserta el registro en `ades_ciclos_escolares` con UUID v7
6. El trigger automático genera los registros en `ades_periodos_evaluacion`
7. Confirmación visual: "Ciclo creado exitosamente. 3 períodos generados."

#### Reglas de Negocio Críticas

- **RN-1:** Bloqueo estricto de Preparatoria en Ixtapan de la Sal (validación relacional en BD)
- **RN-2:** Generación automática de periodos según lineamiento (3 para SEP, variables para UAEMEX)
- **RN-3:** El ciclo recién creado no se marca como vigente automáticamente; debe ser activado explícitamente por el `ADMIN_GLOBAL`

#### Impacto Técnico

**BD:**
- Insert en `ades_ciclos_escolares` (PK UUID v7, triggers auditoría)
- Insert masivo en `ades_periodos_evaluacion` (generados por stored procedure o trigger AFTER INSERT)
- Validación relacional en BD mediante FK y CHECK constraints

**API:**
- POST `/api/v1/ciclos-escolares` (requireRole: ADMIN_GLOBAL)
- PATCH `/api/v1/ciclos-escolares/{ciclo_id}` (activación posterior)

**Frontend:**
- `admin.component.ts` → Tab **Ciclos Escolares**
- Dialog de creación con **Select** de plantel, nivel, **DatePicker** de fechas
- Grid interactivo listando ciclos (columnas: nombre, fechas, estado, acciones)

**Notificaciones:**
- Email a `SECRETARIA_ACADEMICA` informando nuevo ciclo activo

---

### CU-02: Gestión Transversal de Áreas Académicas por Coordinación Global

**Nivel de Riesgo:** Medio  
**Frecuencia:** Ocasional (diseño curricular)  
**Complejidad:** Baja  

#### Actores Involucrados
- `COORDINADOR_AREA` — Supervisión de área (ej. Matemáticas) a través de los 3 planteles
- `ADMIN_GLOBAL` — Aprobación de cambios estructurales

#### Precondiciones
1. El usuario debe tener el rol `COORDINADOR_AREA` en Authentik
2. El área (ej. "Matemáticas") debe existir en `ades_areas_academicas`

#### Flujo Principal

1. El `COORDINADOR_AREA` ingresa a **Académico → Áreas Académicas**
2. El sistema filtra automáticamente a las áreas asignadas al coordinador (via RLS)
3. Visualiza un Interactive Grid con:
   - Nombre del área
   - Planteles cubiertos (Metepec, Tenancingo, Ixtapan)
   - Materias adscritas
   - Docentes por materia
4. El coordinador puede:
   - Editar inline la descripción del área
   - Añadir nuevas materias al catálogo
   - Ver el temario acumulado (drawer de detalle)
5. Al guardar cambios, FastAPI registra la auditoría y notifica al `ADMIN_GLOBAL`

#### Reglas de Negocio Críticas

- **RN-1:** El alcance es transversal (afecta a los 3 planteles simultáneamente)
- **RN-2:** Un cambio a una materia base del área se propaga a todos los grupos que la cursan
- **RN-3:** Requiere aprobación explícita si modifica créditos o horas de clase

#### Impacto Técnico

**BD:**
- Select/Update en `ades_areas_academicas`
- Join con `ades_coordinaciones_area` para RLS del coordinador
- Índices en `(coordinador_id, area_id)` para búsquedas rápidas

**API:**
- GET `/api/v1/areas-academicas` (RLS automático)
- PATCH `/api/v1/areas-academicas/{area_id}` (validación de coordinador)

**Frontend:**
- `areas.component.ts` (Grid Interactive con edición inline)
- Drawer de detalle mostrando temario y asignaciones docentes

---

## 🏫 Módulo 2: Control Escolar, Inscripciones y Aula

### CU-03: Inscripción y Asignación de Alumnos a Grupos

**Nivel de Riesgo:** Alto  
**Frecuencia:** Diaria durante período de inscripción (6-8 semanas al año)  
**Complejidad:** Media  

#### Actores Involucrados
- `SECRETARIA_ACADEMICA` — Captura y validación de inscripciones
- `COORDINADOR_ADMINISTRATIVO` — Supervisión de ocupación por grupo
- `ADMIN_PLANTEL` — Autorización de sobrecupos

#### Precondiciones
1. El ciclo escolar debe estar activo (`ades_ciclos_escolares.es_vigente=TRUE`)
2. El grupo debe estar activo (`ades_grupos.is_active=TRUE`)
3. El alumno debe existir o ser creado en `ades_estudiantes`
4. Para Preparatoria: el semestre debe estar marcado como activo en `ades_grupos.is_active=TRUE`

#### Flujo Principal

1. La secretaria abre el módulo **Control Escolar → Inscripciones**
2. Busca al alumno en el Grid (búsqueda por nombre, matrícula, CURP)
3. Si el alumno es nuevo:
   - Abre un Dialog de creación rápida
   - Captura nombre, apellidos, CURP, NSS (vía PrimeNG inputs)
   - Inserta en `ades_personas` y `ades_estudiantes` automáticamente
4. Selecciona el grupo destino (filtrado por plantel y nivel actual)
   - El sistema muestra: "Ocupación actual: 32/35" (capacidad máxima)
5. Si la ocupación >= capacidad_maxima:
   - Sistema bloquea la inscripción a menos que `ADMIN_PLANTEL` autorice sobrecupo
   - Muestra Dialog de confirmación: "¿Autorizar sobrecupo?"
6. Para Preparatoria (si aplica):
   - Valida que el semestre seleccionado tenga `is_active=TRUE`
   - Si intenta inscribir a semestre futuro (ej. Semestre 5 en Tenancingo), bloquea
7. Confirma la inscripción:
   - Inserta en `ades_inscripciones` (FK hacia grupo y estudiante)
   - Trigger `auditoria.trg_auditoria_biu` registra timestamp, IP, usuario
8. Webhook disparado → FastAPI llama a Authentik API:
   - Crea usuario local en Authentik con nombre de usuario estandarizado
   - Envía email de bienvenida al padre con credenciales temporales
9. Confirmación visual: "Alumno inscrito en Grupo 3A. Email enviado a padre."

#### Reglas de Negocio Críticas

- **RN-1:** Máximo 2 grupos por grado (A y B), salvo autorización explícita de sobrecupo
- **RN-2:** Validación de semestres activos para Preparatoria (Tenancingo: 1-2; Metepec: 1-4)
- **RN-3:** Un alumno solo puede estar inscrito en **1 grupo por ciclo escolar**
- **RN-4:** La cancelación de una inscripción no borra el registro histórico (soft delete via `is_active`)

#### Impacto Técnico

**BD:**
- Insert en `ades_inscripciones`
- Validación de FK contra `ades_estudiantes`, `ades_grupos`
- CHECK constraint: `(SELECT COUNT(*) FROM ades_inscripciones WHERE grupo_id = ? AND is_active) <= capacidad_maxima`
- Trigger auditoría: registra usuario, IP, timestamp en `auditoria.bitacora`

**API:**
- GET `/api/v1/estudiantes?buscar=...` (búsqueda)
- POST `/api/v1/inscripciones` (crear)
- GET `/api/v1/grupos?ciclo_id=...&plantel_id=...` (listar grupos disponibles)
- PATCH `/api/v1/inscripciones/{inscripcion_id}` (editar asignación de grupo)

**Frontend:**
- `inscripciones.component.ts` (Grid de búsqueda + Dialog de creación)
- Interactive Grid mostrando inscripciones por grupo (columnas: alumno, grupo, estado, fecha inscripción)
- Validación en cliente: impedir selección de grupo lleno sin autorización

**Automatización (n8n):**
- Webhook: POST event "inscripcion_creada" → Authentik API para crear usuario
- Email trigger: Plantilla "Bienvenida Alumno" → Padres

---

### CU-04: Pase de Lista Diario Unificado (Primaria - NEM)

**Nivel de Riesgo:** Medio  
**Frecuencia:** Diaria (5 días/semana, 36 semanas/ciclo = 180 eventos)  
**Complejidad:** Baja  

#### Actores Involucrados
- `DOCENTE` (Titular de Primaria) — Registro diario de asistencia

#### Precondiciones
1. El docente debe estar asignado como **titular** del grupo (tabla `ades_asignaciones_docentes.es_titular=TRUE`)
2. El grupo debe estar activo
3. El ciclo escolar debe estar vigente

#### Flujo Principal

1. El docente abre la SPA de Angular a las **08:00 AM** (inicio de jornada)
2. Accede a **Operación → Asistencias → Mi Grupo**
3. El sistema renderiza un **Interactive Grid** con:
   - Foto del alumno (pequeña)
   - Nombre completo
   - Estatus: ✓ (Presente), ✗ (Ausente), T (Retardo), J (Justificado)
4. El docente marca ausencias/retardos/justificantes deslizando o haciendo clic
5. Por defecto, todos los alumnos están marcados como "Presentes"
6. Al dar clic en **"Guardar Asistencia"**:
   - FastAPI valida que el docente sea titular del grupo (RLS)
   - Inserta los registros en `ades_asistencias` (1 registro por alumno)
7. Sistema calcula automáticamente:
   - Inasistencias acumuladas del mes
   - Si un alumno alcanza >= 3 ausencias injustificadas → Bandera "Alerta Ausentismo"
8. Si hay alertas:
   - Webhook enviado a `ades-n8n`
   - n8n procesa la regla y dispara notificación push a **la app del padre** via `ades-ntfy` (SSE)
   - Notificación: "Tu hijo(a) X ha faltado 3 días este mes. Favor contactar con la dirección."
9. Confirmación visual: "Asistencia guardada. 28 presentes, 2 ausentes, 1 retardo."

#### Reglas de Negocio Críticas

- **RN-1:** En Primaria se toma **una sola asistencia al día**, válida para toda la jornada
- **RN-2:** El docente titular es el **único** que puede modificar la asistencia de su grupo (RLS en BD)
- **RN-3:** Las justificaciones (ej. cita médica) requieren documento de soporte en `ades_archivos` (pendiente integración Paperless-ngx)
- **RN-4:** Ausentismo >= 20% acumulado invalida el derecho a exención de examen (regla SEP)

#### Impacto Técnico

**BD:**
- Insert masivo en `ades_asistencias` (1 por alumno × 180 días/año = 356k registros/plantel)
- Índices compuestos: `(grupo_id, fecha_asistencia)` para consultas rápidas
- Vista materializada: `ades_bi.v_ausentismo_mensual` para reporting

**API:**
- GET `/api/v1/asistencias/grupo/{grupo_id}?fecha=YYYY-MM-DD` (obtener estado)
- POST `/api/v1/asistencias/lote` (insert masivo con batch insert optimizado)
- PATCH `/api/v1/asistencias/{asistencia_id}` (editar puntual)

**Frontend:**
- `asistencias.component.ts` (Grid Interactive editable)
- Selector de fecha (DatePicker)
- Botón **"Guardar Asistencia"** con validación cliente-lado

**Automatización (n8n):**
- Webhook trigger: "asistencia_registrada"
- Lógica: IF ausencias_mes >= 3 THEN enviar notificación push padre
- Job: Ejecutar cada fin de mes para alertas acumulativas

---

### CU-05: Pase de Lista por Asignatura/Hora (Secundaria y Preparatoria)

**Nivel de Riesgo:** Medio  
**Frecuencia:** Diaria (5 días/semana × 180 días/año × 7 horas/día aprox.)  
**Complejidad:** Media  

#### Actores Involucrados
- `DOCENTE` (por Materia) — Registro de asistencia por sesión

#### Precondiciones
1. El docente debe estar asignado a la materia en el grupo (`ades_asignaciones_docentes`)
2. El grupo debe tener un horario definido en `ades_horarios`
3. La clase debe estar registrada en `ades_clases` (generada automáticamente o manual)

#### Flujo Principal

1. El docente ingresa a **Operación → Asistencias → Mis Materias**
2. Selecciona la materia y el grupo (ej. "Matemáticas - Grupo 3A")
3. El sistema obtiene la hora actual y pre-carga la **clase correspondiente** desde `ades_horarios`
4. Renderiza un **Interactive Grid** con alumnos inscritos en esa materia+grupo
5. Marca ausencias/retardos por clase
6. Guarda: FastAPI inserta en `ades_asistencias` asociado a `id_clase`
7. Cálculo en Background:
   - Job Celery ejecuta consulta: "¿Alumno X falta > 20% de clases de esta materia?"
   - Si SÍ: actualiza flag `ades_inscripciones.riesgo_ausentismo = TRUE`
   - Webhook a n8n para alertar al TUTOR del grupo

#### Reglas de Negocio Críticas

- **RN-1:** La asistencia se evalúa **por hora/materia**, no globalmente
- **RN-2:** El total acumulado de ausencias en una materia ponderará el derecho a exención de examen en UAEMEX (criterio oficial)
- **RN-3:** Más de 3 faltas consecutivas sin justificación genera alerta de "Riesgo de Deserción"

#### Impacto Técnico

**BD:**
- Insert en `ades_asistencias` con FK a `ades_clases`
- Vista: `ades_bi.v_ausentismo_por_materia` para dashboard docente

**API:**
- GET `/api/v1/clases/docente/{docente_id}?fecha=...` (clases del día)
- POST `/api/v1/asistencias/lote-clase` (batch insert por clase)

**Frontend:**
- `asistencias.component.ts` (cambiar contexto a Vista por Materia)
- Selector de materia y grupo

**Automatización (n8n):**
- Celery job: Análisis de ausentismo semanal → actualizar flags de riesgo

---

## 🎓 Módulo 3: Evaluación, Calificaciones e Inteligencia Artificial

### CU-06: Gestión del Gradebook Curricular y Evaluación Formativa (Primaria - NEM)

**Nivel de Riesgo:** Alto  
**Frecuencia:** Continua (diaria durante clase, concentración al final de período)  
**Complejidad:** Alta  

#### Actores Involucrados
- `DOCENTE` (Titular Primaria) — Evaluación de alumnos
- `DIRECTOR` (Nivel Primaria) — Supervisión de cobertura curricular

#### Precondiciones
1. El docente debe ser titular del grupo
2. El período de evaluación debe estar abierto
3. Las materias deben estar cargadas en `ades_materias_plan` según NEM

#### Flujo Principal

1. El docente abre **Académico → Gradebook** desde la SPA
2. Sistema renderiza un **Spreadsheet-style Interactive Grid** (tipo Excel embebido):
   - Filas: Alumnos del grupo
   - Columnas dinámicas: Los 4 Campos Formativos de la NEM
   - Sub-columnas: Evaluación numérica (5-10) + Descripción cualitativa (JSONB)
3. El docente ingresa:
   - **Celda numérica:** Calificación (5-10)
   - **Celda texto (drawer):** Justificación ("Domina completamente el campo...")
4. Al hacer TAB o Enter:
   - Validación inmediata en cliente (5 <= valor <= 10)
   - Se abre un **pequeño drawer** inline solicitando justificación
5. El docente completa la justificación
6. Clic en **"Guardar"**:
   - FastAPI valida el payload (row_version para optimistic locking)
   - Inserta en `ades_calificaciones_periodo`
   - **Trigger PG `calcular_calificacion_periodo()`:**
     - Ejecuta función de cálculo según ponderaciones de NEM
     - Actualiza `ades_calificaciones_periodo.promedio_final` de forma idempotente
7. Confirmación: "Calificaciones guardadas. Promedio calculado automáticamente."
8. El `DIRECTOR` accede a un **Dashboard de Cobertura Curricular:**
   - % de alumnos evaluados por campo
   - Alertas si algún campo tiene < 80% de cobertura
   - Reporte exportable a PDF

#### Reglas de Negocio Críticas

- **RN-1:** Un único profesor titular evalúa todas las materias base del grupo
- **RN-2:** Escala restricta: 5 (No Logra) a 10 (Logra Completamente), según NEM
- **RN-3:** Toda calificación manual requiere **justificación escrita obligatoria**
- **RN-4:** El promedio se calcula con ponderación dinámica según la NEM: Lenguajes (25%), Saberes (25%), Ética (25%), Humano (25%)

#### Impacto Técnico

**BD:**
- Insert/Update en `ades_calificaciones_periodo`
- Trigger: `fn_calcular_calificacion_periodo()` — ejecución idempotente
- Índices: `(grupo_id, periodo_id, alumno_id)`
- Campo JSONB: `justificacion` (almacena descripción cualitativa)

**API:**
- GET `/api/v1/gradebook/{grupo_id}/{periodo_id}` (obtener grid)
- PATCH `/api/v1/calificaciones/{calificacion_id}` (actualizar con row_version)

**Frontend:**
- `gradebook.component.ts` (Spreadsheet editable con PrimeNG Table)
- Inline drawer para justificación
- Validación de rango 5-10

**Optimistic Locking:**
- Client envía `row_version` en payload
- Backend chequea en `check_row_version()` antes de commit
- Si falla: respuesta 409 Conflict con datos actuales

---

### CU-07: Evaluación de Períodos y Exámenes Extraordinarios / Títulos (Preparatoria - UAEMEX)

**Nivel de Riesgo:** Alto  
**Frecuencia:** Hasta 6 veces/año (períodos semestrales) + eventos extraordinarios  
**Complejidad:** Alta  

#### Actores Involucrados
- `DOCENTE` (por Materia) — Captura de calificaciones
- `SECRETARIA_ACADEMICA` — Gestión de extraordinarios y títulos
- `DIRECTOR` (Preparatoria) — Aprobación de actas

#### Precondiciones
1. El período debe estar abierto en `ades_periodos_evaluacion`
2. La materia debe estar asignada al docente en el grupo
3. Para extraordinarios: el alumno debe tener calificación < 6.0 en ordinario

#### Flujo Principal

**Fase 1: Evaluación Ordinaria**

1. El docente abre **Académico → Evaluaciones → Agenda** (Interactive Grid)
2. Selecciona el período ordinario y visualiza las materias asignadas
3. Clic en una evaluación → Se abre la **Libreta de Calificaciones**:
   - Grid con alumnos inscritos en esa materia+grupo
   - Columnas: Actividades (ponderadas), Examen, Proyecto, Total
4. Edición inline de calificaciones (escala 0-10, aprobación >= 6.0)
5. Al guardar:
   - Inserta en `ades_calificaciones_evaluaciones`
   - Trigger calcula promedio del periodo
   - Sistema marca automáticamente a reprobados (< 6.0)

**Fase 2: Gestión de Extraordinarios**

6. La secretaria abre **Control Escolar → Extraordinarios**
7. Sistema filtra automáticamente alumnos con promedio < 6.0
8. Clic en **"Programar Extraordinario"** → Dialog de creación:
   - Selecciona materia y alumno
   - Asigna fecha de examen
9. Inserta en `ades_evaluaciones` con `TIPO = 'EXTRAORDINARIO'`
10. Email automático al alumno y padre con fecha/hora
11. El docente aplica el examen y registra la calificación
12. Sistema actualiza `ades_calificaciones_evaluaciones` con la nueva nota
13. Si calificación >= 6.0 → Estatus cambia a "Acreditado"
14. Si < 6.0 → Opción de "Título de Suficiencia" (evaluación final)

**Fase 3: Generación de Kárdex**

15. La secretaria corre **Reportes → Generar Kárdex** (batch job)
16. Celery ejecuta:
    - Consulta: obtiene calificaciones finales de `ades_calificaciones_evaluaciones`
    - Llama a Carbone (microservicio) con plantilla Kárdex
    - Carbone inyecta datos en plantilla DOCX y convierte a PDF
17. Stirling-PDF recibe PDF:
    - Incrusta marca de agua "Instituto Nevadi"
    - Comprime el archivo
    - Genera folio único verificable (UUID v7 + timestamp)
18. MinIO almacena el PDF final
19. Email a padre con link descarga segura (S3 presigned URL)
20. Director aprueba el acta digital

#### Reglas de Negocio Críticas

- **RN-1:** Escala de 0 a 10 (aprobación mínima 6.0) — Criterio UAEMEX oficial
- **RN-2:** Calificación final = MAX(ordinario, extraordinario) — No promedia
- **RN-3:** Título de Suficiencia solo para alumnos que reprobaron extraordinario
- **RN-4:** Acta digital es **inmutable** (folio único verificable con UUID v7 + firma criptográfica pendiente FASE 28)

#### Impacto Técnico

**BD:**
- Insert en `ades_evaluaciones` (tipo: ORDINARIO, EXTRAORDINARIO, TITULO)
- Update en `ades_calificaciones_evaluaciones`
- Vista: `ades_bi.v_karde_estudiante` (consolidado por alumno)
- Índices: `(periodo_id, alumno_id)`, `(evaluacion_id, alumno_id)`

**API:**
- PUT `/api/v1/calificaciones/{id}` (update ordinario con row_version)
- POST `/api/v1/evaluaciones` (crear extraordinario)
- POST `/api/v1/reportes/kaardex-lote` (batch generation)

**Frontend:**
- `evaluaciones.component.ts`:
  - Tab **Agenda**: Grid de evaluaciones (read-only, click para abrir libreta)
  - Tab **Libreta**: Spreadsheet editable con columnas de actividades + examen + total

**Automatización (n8n):**
- Webhook: "extraordinario_creado" → Email alumno/padre
- Job semanal: Consolidar calificaciones y actualizar estatus
- Job mensual: Generar Kárdex en lote

**Reporting:**
- Carbone: Plantilla DOCX para Kárdex
- Stirling-PDF: Marca de agua + folio único
- MinIO: Almacenamiento seguro

---

### CU-08: Detección Predictiva de Alumnos en Riesgo Académico mediante IA

**Nivel de Riesgo:** Bajo  
**Frecuencia:** Semanal (Job Celery)  
**Complejidad:** Alta  

#### Actores Involucrados
- `DIRECTOR` (de nivel/plantel) — Recibe alertas
- `TUTOR` (de grupo) — Seguimiento personalizado
- **Sistema IA (LangChain + Claude Haiku)** — Análisis automático

#### Precondiciones
1. El alumno debe tener mínimo 4 semanas de datos académicos registrados
2. Las tablas `ades_calificaciones_periodo`, `ades_asistencias`, `ades_reportes_conducta` deben estar pobladas
3. La API de Anthropic debe estar disponible (ANTHROPIC_API_KEY en .env)

#### Flujo Principal

1. **Trigger Automático (Domingo 23:00 UTC):**
   - Job Celery `app.worker.tareas.analizar_riesgo_academico_semanal()` inicia
2. **Recolección de Datos:**
   - Query PostgreSQL obtiene para cada alumno:
     - Promedio de calificaciones del período actual
     - % de asistencia (últimas 4 semanas)
     - Contador de reportes conductuales recientes
     - Histórico de calificaciones (últimos 3 períodos)
3. **Invocación del Agente IA:**
   - FastAPI prepara contexto normalizado (sin PII sensible)
   - Envía al Agente: datos académicos + reglas de negocio + historial
   - Prompt: *"Evalúa si este alumno está en riesgo de deserción o reprobación. Considera: promedio < 6.0, ausentismo > 20%, reportes conductuales. Proporciona: diagnóstico, nivel de riesgo (BAJO/MEDIO/ALTO), plan de acción recomendado."*
4. **Procesamiento de IA:**
   - Claude Haiku analiza y devuelve structured JSON:
     ```json
     {
       "alumno_id": "uuid",
       "nivel_riesgo": "ALTO",
       "factores": ["promedio_bajo", "ausentismo"],
       "plan_accion": "Reunión padres + refuerzo extra-clase",
       "timestamp": "2026-06-09T23:15:00Z"
     }
     ```
5. **Inserción en BD:**
   - Inserta en `ades_reportes_academicos` (tabla de auditoría IA)
6. **Generación de Learning Path:**
   - Si riesgo = ALTO, la IA sugiere automáticamente un Learning Path en `ades_learning_paths`:
     - Módulos de refuerzo por materia
     - Videos de Khan Academy + ejercicios autocalificables
7. **Notificación a Stakeholders:**
   - Si nivel_riesgo = ALTO:
     - Email al DIRECTOR del nivel con resumen ejecutivo
     - Email al TUTOR del grupo con acción recomendada
     - App notificación al padre (push ntfy)
   - Mensaje: *"Tu hijo está en riesgo académico. Te recomendamos reunión con el tutor. [Link al Learning Path]"*
8. **Dashboard Actualizado:**
   - El director visualiza en `/bi` (Superset) un widget **"Alumnos en Riesgo"** en tiempo real

#### Reglas de Negocio Críticas

- **RN-1:** El análisis es **completamente automático** y no requiere intervención manual
- **RN-2:** Toda recomendación de la IA es **sugerencia, no mandato** — docentes pueden rechazar y justificar
- **RN-3:** El histórico de análisis está disponible para auditoría forense en `ades_reportes_academicos`
- **RN-4:** RLS estricta: Un director solo ve riesgos de su nivel/plantel

#### Impacto Técnico

**BD:**
- Insert en `ades_reportes_academicos` (tabla de auditoría IA)
- Select masivo desde `ades_calificaciones_periodo`, `ades_asistencias` (consultas optimizadas con índices)
- Vista: `ades_bi.v_riesgos_academicos` (para Superset)

**API:**
- GET `/api/v1/reportes-academicos?nivel=...&plantel=...` (filtrado RLS)

**Automatización (Celery + LangChain):**
- Job semanal: `app.worker.tareas.analizar_riesgo_academico_semanal()`
- Invocación async de Claude API (sin bloqueo)
- Fallback: si Claude falla, registra en log y reintenta siguiente semana

**Frontend:**
- `reportes.component.ts` (widget "Alumnos en Riesgo")
- Drawer con detalles de análisis y plan de acción sugerido

**Notificaciones:**
- Email: Template "Alerta Riesgo Académico Alumno"
- Push ntfy: Prioridad alta

---

## 🏥 Módulo 4: Operación Especializada, Salud y Conducta

### CU-09: Control de Expediente Médico y Alertas de Incidentes en Plantel

**Nivel de Riesgo:** Alto  
**Frecuencia:** Ocasional (registro anual + incidentes puntuales)  
**Complejidad:** Media  

#### Actores Involucrados
- `MEDICO_ESCOLAR` — Gestor del expediente
- `DOCENTE` — Reportador de incidentes
- `PADRE_FAMILIA` — Receptor de alertas críticas

#### Precondiciones
1. El alumno debe estar inscrito en el ciclo actual
2. El expediente médico debe existir en `ades_expedientes_medicos`

#### Flujo Principal

1. El **Médico Escolar** abre **Salud → Expediente Médico**
2. Busca al alumno → Clic para abrir Drawer con formulario:
   - Tipo de sangre
   - Alergias (JSONB: lista de medicamentos/alimentos)
   - Medicamentos controlados (ej. Ritalina, Inhalador)
   - Contacto de emergencia (FK a `ades_contactos_familiares`)
3. Edita inline y guarda
4. **Si ocurre un accidente en el plantel:**
   - Prefecto/Docente abre **Salud → Reportar Incidente**
   - Dialog rápido:
     - Fecha/Hora
     - Lugar exacto (patio, cancha, aula, etc.)
     - Descripción del evento (caída, golpe, etc.)
     - Nivel de criticidad (LEVE, MODERADO, GRAVE)
     - Tratamiento aplicado
   - Campos obligatorios con validación
5. Guarda → Inserta en `ades_incidentes_medicos`
6. **Webhook crítico disparado:**
   - FastAPI construye alerta tipo URGENTE
   - Envía a `ades-n8n` con prioridad máxima
   - n8n ejecuta simultaneamente:
     - **SMS** al celular del padre (via Twilio pendiente FASE 25)
     - **Email** con descripción al padre
     - **Push ntfy** a la app móvil (notificación nativa)
7. El padre recibe la alerta y puede:
   - Confirmar que asume el tratamiento
   - Solicitar seguimiento adicional
8. El Médico Escolar monitorea el expediente del alumno
   - Dashboard con historial de incidentes por alumno
   - Alertas si hay patrones (ej. caídas recurrentes)

#### Reglas de Negocio Críticas

- **RN-1:** Los datos de salud están **encriptados a nivel de BD** (pgcrypto extension pendiente FASE 25)
- **RN-2:** RLS estricta: El médico solo ve expedientes de su plantel; docentes ven solo "Tarjeta de Alerta" (ej. "Alérgico a Penicilina")
- **RN-3:** Incidentes GRAVE disparan notificación también a Dirección
- **RN-4:** Histórico de incidentes se archiva en `Paperless-ngx` (FASE 24)

#### Impacto Técnico

**BD:**
- Insert/Update en `ades_expedientes_medicos` (con campos JSONB para alergias/medicamentos)
- Insert en `ades_incidentes_medicos`
- FK: `ades_incidentes_medicos.contacto_emergencia_id` → `ades_contactos_familiares`

**API:**
- PATCH `/api/v1/expedientes-medicos/{estudiante_id}` (update con validación de rol)
- POST `/api/v1/incidentes-medicos` (crear reporte urgente)
- GET `/api/v1/expedientes-medicos/{estudiante_id}/alertas` (tarjeta pública para docentes)

**Frontend:**
- `expediente-medico.component.ts` (Drawer formulario con inputs JSONB para alergias)
- Botón **"Reportar Incidente"** en cada alumno (acceso rápido)

**Automatización (n8n):**
- Webhook: "incidente_medico_creado" (prioridad CRÍTICA)
- Acciones:
  - IF criticidad = GRAVE THEN notificar también a DIRECTOR
  - Enviar SMS + Email + Push simultáneamente

---

### CU-10: Reportes Disciplinarios y Compromisos de Conducta

**Nivel de Riesgo:** Medio  
**Frecuencia:** Variable (0-3 por alumno por período)  
**Complejidad:** Media  

#### Actores Involucrados
- `PREFECTO` — Levantador de reportes
- `ORIENTADOR` — Generador de planes de mejora
- `TUTOR` — Supervisor del seguimiento
- `PADRE_FAMILIA` — Firmante de acuse digital

#### Precondiciones
1. El alumno debe estar activo en el grupo
2. Para secundaria/preparatoria, el orientador debe estar asignado al nivel

#### Flujo Principal

1. El **Prefecto** observa una falta disciplinaria (ej. bullying, violencia, inasistencia injustificada)
2. Abre **Disciplina → Reportes de Conducta**
3. Clic en **"Nuevo Reporte"** → Dialog:
   - Alumno involucrado (búsqueda)
   - Fecha y hora del incidente
   - Lugar (aula, patio, etc.)
   - Tipo de falta (Bajo nivel, Nivel medio, Grave)
   - Descripción detallada
   - Testigos (opcional)
4. Validación: Campo "Flag de Notificación Familiar" es **obligatorio** (checkbox que debe estar marcado)
5. Guarda → Inserta en `ades_reportes_conducta`
6. **Automáticamente:**
   - Sistema envía email al **ORIENTADOR** notificando nuevo reporte
   - Cita automática generada para que orientador se reúna con alumno (next available slot)
7. El **ORIENTADOR** abre el reporte y crea un **Plan de Mejora Conductual:**
   - Análisis de causas raíz
   - Compromisos del alumno
   - Compromisos del padre/tutor
   - Seguimiento recomendado (ej. sesiones semanales de tutoría)
   - Campo JSONB: `plan_mejora` (JSON con estructura)
8. Al guardar el plan:
   - Genera un **formato digital imprimible** (PDF vía Carbone)
   - Email al padre con formato PDF como adjunto
   - **Flag de obligación:** El padre debe ingresar a su portal ADES y firmar **digitalmente** el acuse de enterado
9. Si padre NO accede en 7 días:
   - Sistema envía **recordatorio email** automático (Job Celery)
   - Notificación al ORIENTADOR y DIRECTOR
10. Padre accede a su portal → Ve el reporte → Clic en **"Firmar Acuse"**:
    - Sistema captura timestamp exacto + IP
    - Inserta en `ades_acuses_comunicado` (mismo módulo que acuses de comunicados)
    - Email de confirmación al padre: "Acuse registrado exitosamente el 2026-06-09 10:15:32"
11. ORIENTADOR monitorea el cumplimiento de los compromisos:
    - Drawer de seguimiento con checkboxes de compromisos
    - Fecha de revisión próxima

#### Reglas de Negocio Críticas

- **RN-1:** Todo reporte **DEBE** llevar flag de "Notificado a Familiar" (validación en BD)
- **RN-2:** El plan de mejora es **vinculante** — incumplimiento puede escalar a sanciones disciplinarias
- **RN-3:** Histórico de reportes es visible en el expediente del alumno de por vida
- **RN-4:** Reclasificación de faltas (ej. cambiar de "Medio" a "Grave") requiere aprobación del DIRECTOR

#### Impacto Técnico

**BD:**
- Insert en `ades_reportes_conducta` (FK alumno, orientador, prefecto)
- JSONB: `plan_mejora` (estructura flexible de compromisos)
- Insert en `ades_acuses_comunicado` (acuse digital con timestamp + IP)
- Índices: `(alumno_id, fecha)` para histórico

**API:**
- POST `/api/v1/reportes-conducta` (crear reporte con validación de flag)
- PATCH `/api/v1/reportes-conducta/{reporte_id}` (actualizar plan)
- POST `/api/v1/acuses/{reporte_id}/firmar` (acuse digital)

**Frontend:**
- `conducta.component.ts` (Grid + Dialog reporte + Drawer acuse)
- Modal de firma digital (captura timestamp + IP en cliente)

**Automatización (n8n):**
- Webhook: "reporte_conducta_creado"
  - Enviar email orientador
  - Generar cita automática
  - Email padre con PDF Plan
- Job semanal: Verificar acuses pendientes > 7 días → recordatorio

---

## 📊 Módulo 5: Comunicación, Reportes y BI Institucional

### CU-11: Emisión Masiva de Boletas y Certificados Digitales Firmados

**Nivel de Riesgo:** Alto  
**Frecuencia:** Cada periodo de evaluación (6+ veces/año)  
**Complejidad:** Alta  

#### Actores Involucrados
- `SECRETARIA_ACADEMICA` — Iniciador del batch
- `ADMIN_GLOBAL` — Aprobador
- Sistema automático (Celery + Carbone + Stirling-PDF)

#### Precondiciones
1. El período de evaluación debe estar cerrado (calificaciones finales registradas)
2. Todas las calificaciones deben estar validadas

#### Flujo Principal

1. **Secretaria** abre **Reportes → Generar Boletas**
2. Selector con opciones:
   - Plantel: (Metepec, Tenancingo, Ixtapan)
   - Nivel: (Primaria, Secundaria, Preparatoria)
   - Período: (Selector de ciclo y período)
3. Clic en **"Generar Boletas en Lote"**
   - Dialog de confirmación: "Se generarán boletas para 156 alumnos. ¿Continuar?"
4. FastAPI **acepta el request** y retorna un `job_id` (UUID)
   - Usuario recibe: "Generación iniciada. [Seguimiento]" (link a status)
5. **Background Job (Celery):**
   - Query: obtiene todas las inscripciones + calificaciones finales del período
   - Para cada alumno:
     a. Prepara payload JSON (datos del alumno, materias, calificaciones)
     b. HTTP POST a `Carbone` (microservicio puerto 3001)
     c. Carbone inyecta en template `.docx` oficial del Instituto
     d. Carbone retorna PDF base
     e. HTTP POST a `Stirling-PDF` (microservicio puerto 8081) con PDF
     f. Stirling agrega marca de agua "Instituto Nevadi" + folio único
     g. Stirling retorna PDF final comprimido
     h. S3 Upload a MinIO (bucket: `reportes/{ciclo}/{periodo}/boletas/`)
     i. Genera presigned URL válida 30 días
6. **Consolidación:**
   - Al completar todos los PDFs:
     - Inserta referencias en `ades_archivos` (tabla de auditoría)
     - Crea registro en `ades_reportes_academicos` (tipo: BOLETA_LOTE)
7. **Notificación:**
   - Email a Secretaria: "Generación completada. 156 boletas generadas."
   - Webhook a n8n:
     - Para cada padre: enviar email con link descarga segura
     - Subject: "Boleta de tu hijo(a) disponible [Instituto Nevadi]"
8. **Usuario verifica:**
   - Abre **Reportes → Histórico de Generaciones**
   - Ve status: ✓ 156/156 generadas
   - Puede descargar ZIP con todas las boletas (admin) o links individuales

#### Reglas de Negocio Críticas

- **RN-1:** Las boletas generadas son **inmutables** (almacenadas con UUID v7 para referencia forense)
- **RN-2:** Cada boleta lleva **folio único verificable** (para consulta ante SEP/UAEMEX en el futuro)
- **RN-3:** Si una calificación cambia DESPUÉS de generar boletas, NO se regeneran automáticamente (previene confusiones). Manual retry: ADMIN puede regenerar específicamente.
- **RN-4:** Los links de descarga caducan en 30 días; pasado ese tiempo, solo ADMIN puede acceder

#### Impacto Técnico

**BD:**
- Select masivo desde `ades_calificaciones_periodo`, `ades_inscripciones`, `ades_estudiantes`
- Insert en `ades_archivos` (auditoría de reportes)
- Insert en `ades_reportes_academicos` (tracking de generaciones)

**API:**
- POST `/api/v1/reportes/boletas-lote` (inicia job)
- GET `/api/v1/reportes/boletas-lote/{job_id}/status` (polling status)
- GET `/api/v1/reportes/boletas-lote/{job_id}/descargar` (descarga ZIP)

**Microservicios (Docker):**
- **Carbone** (Node.js, puerto 3001): Inyección de datos en plantilla DOCX → PDF
- **Stirling-PDF** (Java, puerto 8081): Marca de agua + folio único + compresión

**Storage:**
- MinIO bucket: `reportes/{ciclo}/{periodo}/boletas/`
- Presigned URLs válidas 30 días (S3-compatible)

**Automatización (n8n):**
- Webhook: "boletas_lote_completadas"
- Acción: Para cada padre, enviar email con link descarga

---

### CU-12: Consumo de Dashboards Ejecutivos y KPIs por Plantel

**Nivel de Riesgo:** Bajo  
**Frecuencia:** Diaria (acceso bajo demanda)  
**Complejidad:** Media  

#### Actores Involucrados
- `DIRECTOR` (de Plantel/Nivel) — Principal consumidor
- `ADMIN_PLANTEL` — Configurador de vistas
- Apache Superset — Motor de BI
- PostgreSQL — Data source

#### Precondiciones
1. El usuario debe tener rol de lectura en Superset (vía Authentik RLS)
2. Las vistas materializadas `ades_bi.*` deben estar actualizadas

#### Flujo Principal

1. El **DIRECTOR** ingresa a ADES → Pestaña **"Analítica"** o módulo `/bi`
2. Angular solicita un **guest token** seguro:
   - FastAPI endpoint: POST `/api/v1/superset/guest-token`
   - Backend valida permisos del usuario (vía Authentik)
   - Backend construye parámetro RLS: `{ plantel_id: user.plantel_id, nivel_educativo_id: user.nivel_educativo_id }`
   - Backend invoca **Superset API** con `custom_sso_security_manager`:
     ```python
     token = superset_api.create_guest_token(
         dashboard_id="dashboard-ades-director",
         user_id=None,
         rls_rules=[
             { "clause": "plantel_id", "operator": "==", "value": user.plantel_id },
             { "clause": "nivel_educativo_id", "operator": "==", "value": user.nivel_educativo_id }
         ]
     )
     ```
3. FastAPI retorna `{ guest_token: "...", embed_url: "https://bi.ades.setag.mx/embedded/..." }`
4. Angular renderiza un **iframe embebido** apuntando a `embed_url` con el `guest_token` en query
5. Superset renderiza el dashboard con RLS aplicado:
   - Director Primaria Tenancingo **solo ve** KPIs de Primaria Tenancingo
   - Datos filtrados a nivel de SQL (vistas `ades_bi.*` con WHERE plantel_id = ?)
6. **Widgets disponibles:**
   - 📊 **Promedio General:** Gráfico de línea (promedio por periodo)
   - 📈 **Ausentismo:** Tabla de alumnos con % ausentismo, ordenable, filtrable
   - 🎯 **Riesgos Académicos:** Contador de ALTO/MEDIO/BAJO + lista detallada
   - 📉 **Cobertura Curricular:** % de cobertura por materia/campo formativo
   - 📊 **Indicadores Docentes:** Distribución de calificaciones por docente (análisis de tendencias)
7. Director puede:
   - Filtrar por grupo (select dinámico)
   - Exportar datos a CSV
   - Crear alertas personalizadas (ej. "Notificarme si promedio baja < 7.0")
8. **Auto-refresh:** Dashboard se actualiza cada 1 hora (configurable)

#### Reglas de Negocio Críticas

- **RN-1:** RLS estricta: Un director **nunca puede ver** datos de otros planteles o niveles
- **RN-2:** Los guest tokens expiran en 1 hora (por seguridad)
- **RN-3:** Las vistas `ades_bi.*` se materializan cada 4 horas (actualización automática via scheduled job PostgreSQL)
- **RN-4:** Acceso de auditoría: cada consulta al dashboard queda registrada en logs de Superset

#### Impacto Técnico

**BD:**
- Vistas materializadas en schema `ades_bi`:
  - `v_promedio_general` (por periodo, grupo, nivel)
  - `v_ausentismo_mensual` (% por alumno)
  - `v_riesgos_academicos` (resultado del CU-08)
  - `v_cobertura_curricular` (% cobertura por materia)
  - `v_indicadores_docentes` (promedio, dispersión por docente)
- Índices para JOIN rápido: `(plantel_id, nivel_educativo_id)` en tablas base

**API:**
- POST `/api/v1/superset/guest-token` (genera token con RLS)
- GET `/api/v1/superset/alertas` (gestión de alertas personalizadas)

**Frontend:**
- `bi.component.ts` (iframe embebido + gestión de tokens)
- Componente de selector de filtros (grupo, periodo, etc.)

**Superset:**
- Custom security manager: `custom_sso_security_manager.py` (RLS logic)
- Dashboard: "ADES - Director Planteles" (multi-source)
- Datasource: PostgreSQL (connection string desde `.env`)

---

## 🔗 Matriz de Trazabilidad: CU → BD → API → Frontend

[Tabla grande de referencia — Ver matriz_trazabilidad.md]

---

## 📋 Checklist de Implementación

Para cada caso de uso, verificar:

- [ ] **BD:** Tablas, triggers, índices creados y testeados
- [ ] **API:** Endpoints CRUD + validaciones + RLS
- [ ] **Frontend:** Componentes Angular, Interactive Grid donde aplique
- [ ] **Automatización:** Webhooks n8n, Celery jobs configurados
- [ ] **Notificaciones:** Canales (email, push, SMS) integrados
- [ ] **Testing:** Unit + E2E + load testing (>10k registros)
- [ ] **Auditoría:** Logs en `auditoria.bitacora` configurados
- [ ] **Documentación:** Especificación actualizada, ejemplos de payload

---

## 📚 Referencias Cruzadas

- **spec/standards/api-design.md** — Patrones REST, códigos de estado, RLS
- **spec/modules/fase-24-interactive-grid/** — UI Interactive Grid patterns
- **CLAUDE.md** — Reglas mandatorias del proyecto
- **db/migrations/001_initial_schema.sql** — Schema PostgreSQL
- **.agent/CONTEXT.md** — Arquitectura completa del sistema

---

**Documento generado:** 2026-06-09  
**Versión:** 1.0.0  
**Estado:** Active (Implementado en Fases 1-10, 15-23)
