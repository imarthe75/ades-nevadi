# 📖 Manual de Usuario Detallado — ADES Instituto Nevadi

Bienvenido al Manual de Usuario de **ADES (Administración Escolar Instituto Nevadi)**. Este manual está diseñado para servir como guía de referencia completa para todos los perfiles de usuario que interactúan con el sistema.

---

## 📋 Tabla de Contenido
1. [Introducción y Acceso](#1-introducción-y-acceso)
2. [Guía de Roles y Permisos](#2-guía-de-roles-y-permisos)
3. [Módulos Core Académicos](#3-módulos-core-académicos)
4. [Módulo de Calificaciones y Gradebook](#4-módulo-de-calificaciones-y-gradebook)
5. [Módulo de Expediente Digital (Fase 28)](#5-módulo-de-expediente-digital-fase-28)
6. [Módulo de Certificados Digitales (Fase 27)](#6-módulo-de-certificados-digitales-fase-27)
7. [Recursos Humanos y Licencias (Fases 29 y 30)](#7-recursos-humanos-y-licencias-fases-29-y-30)
8. [Operatividad Avanzada y Justificaciones (Fase 31)](#8-operatividad-avanzada-y-justificaciones-fase-31)
9. [Asistente IA y Consultas en Lenguaje Natural (Fase 4)](#9-asistente-ia-y-consultas-en-lenguaje-natural-fase-4)
10. [Monitoreo e Infraestructura (Solo Administradores)](#10-monitoreo-e-infraestructura-solo-administradores)

---

## 1. Introducción y Acceso

### 1.1 ¿Qué es ADES?
ADES es la plataforma digital interna de administración escolar del **Instituto Nevadi**, que integra control académico, expedientes del alumnado, gestión del personal docente, alertas automáticas, firmas criptográficas y analítica institucional.

### 1.2 ¿Cómo ingresar al sistema?
1. Accede a través del navegador web a la dirección provista por la institución: `https://ades.setag.mx/`.
2. Introduce tu correo institucional de Google Workspace (`@institutonevadi.edu.mx`) o tu cuenta local según corresponda.
3. Si eres administrador, se te solicitará validar el segundo factor de autenticación (MFA TOTP) configurado en Authentik.

---

## 2. Guía de Roles y Permisos

El sistema se rige por un estricto control de acceso basado en roles (RBAC) con un alcance territorial delimitado por el plantel asignado:

*   **ADMIN_GLOBAL (Nivel 0):** Acceso irrestricto a todos los planteles, auditoría inmutable, parámetros de sistema, marcas e infraestructura.
*   **ADMIN_PLANTEL (Nivel 1):** Gestión de usuarios, alumnos y configuraciones exclusivas de su plantel asignado.
*   **DIRECTOR / SUBDIRECTOR (Nivel 2):** Supervisión académica, aprobación de justificaciones médicas, imposición de sanciones disciplinarias y emisión de certificados.
*   **COORDINADOR ACADÉMICO (Nivel 3):** Configuración de ponderaciones curriculares, control del mapa de materias, revisión de planeaciones de clase y administración de planes de mejora.
*   **DOCENTE (Nivel 4):** Registro de asistencias, planeaciones, asignación y evaluación de tareas, y vaciado de calificaciones de sus grupos asignados.
*   **PADRE DE FAMILIA / ALUMNO (Nivel 5):** Consulta de progreso académico, asistencia, descarga de boletas y entrega digital de actividades.

---

## 3. Módulos Core Académicos

### 3.1 Estructura Curricular e Inscripciones
*   **Mapa Curricular:** Visualiza la malla curricular del ciclo activo estructurada por planteles y planes de estudio vigentes (NEM 2022 para Primaria/Secundaria y CBU 2024 para Preparatoria).
*   **Inscripción Masiva:** Permite promover en lote alumnos de un grado a otro al inicio del ciclo escolar, validando previamente que el estudiante no cuente con adeudos de materias.

### 3.2 Asistencias
*   Los docentes toman asistencia diaria para cada clase asignada.
*   Estados de asistencia: **Presente**, **Ausente**, **Tarde**, o **Justificado**.
*   **Regla de Alerta:** Si el porcentaje de asistencia acumulado de un estudiante disminuye por debajo del **85%**, el sistema envía automáticamente una notificación push al padre de familia a través de *ntfy*.

---

## 4. Módulo de Calificaciones y Gradebook

### 4.1 Captura Tradicional de Calificaciones
*   Organizada según los periodos de evaluación definidos por la autoridad regulatoria (3 periodos para Primaria, 6 para Secundaria y 2 parciales + examen final para Preparatoria).
*   Cualquier calificación reprobatoria ($< 6.0$ en SEP o $< 60$ en UAEMEX) gatilla una alerta inmediata a la cuenta vinculada del padre de familia.

### 4.2 Libreta Digital Completa (Gradebook)
*   Panel interactivo tipo hoja de cálculo (*spreadsheet*) para la visualización detallada de todas las actividades evaluadas (tareas, exámenes, participación).
*   Permite a los coordinadores académicos realizar ajustes manuales justificados a las calificaciones definitivas.

---

## 5. Módulo de Expediente Digital (Fase 28)

Este módulo centraliza los expedientes documentales de los alumnos utilizando **Paperless-ngx** y almacenamiento local compatible con **MinIO S3**:

*   **Completitud del Expediente:** Muestra el listado de documentos obligatorios (CURP, Acta de Nacimiento, Certificado anterior, Comprobante de Domicilio) con su estado actual y barra de progreso.
*   **Ingesta y OCR:** Los archivos PDF o imágenes cargados pasan por un proceso de reconocimiento óptico de caracteres (OCR) en español. El visor integrado permite inspeccionar los documentos directamente en pantalla.
*   **Validación con IA:** Al presionar **Analizar con IA**, el asistente procesa el texto OCR mediante NVIDIA NIM local para detectar inconsistencias de datos (ej. discrepancia de nombres, vigencia de documentos) e identificar papeles pendientes de entrega.

---

## 6. Módulo de Certificados Digitales (Fase 27)

Permite la emisión y validación criptográfica de documentos oficiales emitidos por el Instituto Nevadi:

*   **Emisión de Certificados:** Genera un documento PDF normalizado que contiene las asignaturas cursadas, promedio acumulado y una firma digital con criptografía de curva elíptica **Ed25519**.
*   **Código QR Institucional:** Embebe un QR dinámico que apunta a la URL de validación del sistema.
*   **Validación Pública:** A través de la ruta `/verificar/:folio` (que no requiere iniciar sesión), terceros pueden verificar en tiempo real la autenticidad e integridad del documento. Cualquier alteración al PDF invalida la firma de inmediato.

---

## 7. Recursos Humanos y Licencias (Fases 29 y 30)

Permite automatizar y resguardar los flujos operacionales de los colaboradores del Instituto:

*   **Solicitud de Licencias:** El personal docente y administrativo puede solicitar permisos indicando el periodo, tipo de licencia (médica, con/sin goce de sueldo) y adjuntar la documentación de respaldo. Cuenta con un flujo de aprobación por parte del Coordinador de RH.
*   **Capacitaciones Docentes:** Registro de certificaciones y cursos tomados para computar las horas acumuladas de formación continua en el expediente laboral.
*   **Expediente Laboral Digital:** Resguarda contratos, identificaciones y credenciales del personal bajo un esquema estricto de auditoría.

---

## 8. Salud Escolar Completa (Fase 32)

*   **Dispensación de Medicamentos (SB-003):** Registro centralizado y control de los medicamentos recetados a los alumnos que deben ser administrados en el plantel, con sus respectivas dosis y horarios.
*   **Actas Médicas e Incidentes (SB-005):** Emisión en PDF de actas detalladas en caso de accidentes o incidentes dentro del plantel escolar para notificación y soporte legal.
*   **Certificado de Aptitud Física (SB-009):** Generación automática en formato PDF de certificados de salud para actividades deportivas escolares, validados por el médico de la institución.
*   **Condiciones Médicas Crónicas:** Registro detallado de alergias o patologías crónicas de los alumnos para alertar inmediatamente al personal médico o directivo del plantel en caso de emergencia.
*   **Justificaciones Médicas y Familiares:** Una vez aprobado por el Director del plantel, el sistema recalcula de forma automática la asistencia acumulada para evitar penalizaciones al alumno.

---

## 9. Asistente IA, Analítica y Rutas de Aprendizaje (Fase 4 & 32)

*   **Predicción de Abandono (IA-005):** Panel predictivo que calcula el índice de riesgo de deserción del alumno utilizando su historial académico, conducta y récord de asistencia.
*   **Ajuste Dinámico de Ruta (IA-009 / IA-014):** Asignación y recomendación automática de recursos adicionales (learning paths) adaptados al nivel del estudiante y basados en su rendimiento.
*   **Análisis Preventivo de Bullying (SB-016 / SB-020):** Escaneo semántico de encuestas escolares para detectar patrones de conducta y palabras clave relacionadas con acoso, alertando a los orientadores en tiempo real.
*   **Chatbot Pedagógico:** Un asistente disponible en el panel superior que responde consultas de directrices escolares, genera sugerencias de rúbricas para evaluación y provee herramientas de apoyo educativo al docente.
*   **NL to SQL (Consulta de datos):** Permite escribir preguntas en lenguaje cotidiano en la barra de consulta (ej. *"¿Qué alumnos tienen más de 3 inasistencias en este ciclo?"*). El sistema genera la consulta SQL de manera segura, la ejecuta y presenta el resultado en una tabla.

---

## 10. Colaboración y Personalización de Dashboard (Fase 32)

*   **Foros Interactivos y de Materias (CO-021 / CO-022 / CO-023):** Espacios de discusión segmentados por asignatura escolar (docente-alumnos) y foros privados exclusivos para padres y tutores. Incluye un sistema de moderación de contenido para coordinadores académicos y directores.
*   **Dashboard Personalizable (CO-020):** Menú de control en el panel superior (icono de engranaje) que permite activar o desactivar widgets del inicio (Cabecera, KPIs, Panel de Mi Plantel, Gráficos de Distribución y Accesos Rápidos). Las preferencias se almacenan localmente por navegador.
*   **Filtros de Alumnos y Plantel:** Caja de búsqueda de planteles y filtro dinámico que oculta KPIs cuyos valores estén por debajo del límite de estudiantes definido.

---

## 11. Admisiones y Trámites Escolares (Fase 32)

*   **Evaluaciones Diagnósticas (PE-003):** Registro y control automatizado de los resultados de exámenes de admisión para aspirantes.
*   **Cartas de Aceptación/Rechazo (PE-005 / PE-006):** Generación automática de cartas personalizadas en formato PDF y línea de tiempo interactiva que muestra el avance en las etapas de admisión del aspirante.
*   **Matrícula Cívica y Credenciales (PE-011 / PE-014):** Asignación sistematizada de matrícula cívica y emisión digital de credenciales de estudiante con código de verificación QR.
*   **Workflow de Bajas y Reactivaciones (PE-020 / PE-021 / PE-022):** Flujos automatizados para tramitar bajas temporales y definitivas de alumnos, así como reactivaciones con validación de cupos disponibles en el grupo correspondiente.

---

## 12. Monitoreo e Infraestructura (Solo Administradores)

*   **Monitor de Servicios:** Dashboard integrado que muestra el estado de salud de los contenedores Docker locales (PostgreSQL, Valkey, MinIO, ntfy, n8n, Stirling-PDF, Carbone).
*   **Grafana + Prometheus:** Dashboards interactivos para visualizar tasas de peticiones HTTP por segundo, tiempos de respuesta P95 del backend y errores de red.
*   **n8n Workflows:** Automatizaciones que programan la generación y envío en lote de boletas escolares al cierre de periodo y envío periódico de notificaciones pendientes.
