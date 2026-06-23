# Manual de Usuario — ADES Instituto Nevadi
**Versión:** 2.0 | **Fecha:** 2026-06-23 | **Sistema:** ADES — Administración Escolar Instituto Nevadi

---

## Tabla de Contenido

1. [Introducción y Acceso al Sistema](#1-introducción-y-acceso-al-sistema)
2. [Roles y Niveles de Acceso](#2-roles-y-niveles-de-acceso)
3. [Barra Superior y Selector de Contexto](#3-barra-superior-y-selector-de-contexto)
4. [Dashboard — Panel Principal](#4-dashboard--panel-principal)
5. [Alumnos](#5-alumnos)
6. [Admisión](#6-admisión)
7. [Grupos](#7-grupos)
8. [Profesores](#8-profesores)
9. [Calificaciones](#9-calificaciones)
10. [Gradebook — Libreta Digital Avanzada](#10-gradebook--libreta-digital-avanzada)
11. [Asistencias](#11-asistencias)
12. [Evaluaciones](#12-evaluaciones)
13. [Evaluación Docente 360°](#13-evaluación-docente-360)
14. [Conducta](#14-conducta)
15. [Kardex UAEMEX](#15-kardex-uaemex)
16. [Boleta NEM (Primaria y Secundaria)](#16-boleta-nem-primaria-y-secundaria)
17. [Boleta UAEMEX (Preparatoria)](#17-boleta-uaemex-preparatoria)
18. [Biblioteca](#18-biblioteca)
19. [Estadística 911 SEP](#19-estadística-911-sep)
20. [Horarios](#20-horarios)
21. [Aulas](#21-aulas)
22. [Médico y Condiciones Crónicas](#22-médico-y-condiciones-crónicas)
23. [Personal Administrativo](#23-personal-administrativo)
24. [Licencias y Capacitaciones](#24-licencias-y-capacitaciones)
25. [Padres y Tutores](#25-padres-y-tutores)
26. [Comunicados](#26-comunicados)
27. [Planeación Curricular](#27-planeación-curricular)
28. [Reinscripción](#28-reinscripción)
29. [Acta de Evaluación UAEMEX](#29-acta-de-evaluación-uaemex)
30. [Asistente de Inteligencia Artificial](#30-asistente-de-inteligencia-artificial)
31. [Administración del Sistema](#31-administración-del-sistema)
32. [Seguridad del Sistema](#32-seguridad-del-sistema)
33. [Exportación de Datos](#33-exportación-de-datos)
34. [Preguntas Frecuentes](#34-preguntas-frecuentes)

---

## 1. Introducción y Acceso al Sistema

### 1.1 ¿Qué es ADES?

ADES es el sistema integral de administración escolar del **Instituto Nevadi**, diseñado para los tres planteles de la institución:

| Plantel | Dirección | Teléfono |
|---|---|---|
| Metepec | Prolongación Heriberto Enríquez 1001 | 722 297 1441 / 722 325 3683 |
| Tenancingo | Carretera Tenancingo-Tenería S/N | 714 142 4323 |
| Ixtapan de la Sal | Independencia Pte. 5 | 721 143 3015 |

El sistema cubre tres niveles educativos:
- **Primaria** — Plan de estudios NEM 2022 (SEP), ciclo 2026-2027
- **Secundaria** — Plan de estudios NEM 2022 (SEP), ciclo 2026-2027
- **Preparatoria** — Plan CBU 2024 (UAEMEX), ciclo semestral 26B / 27A

ADES **no** incluye módulos de colegiaturas, pagos ni facturación CFDI. Su enfoque es académico, formativo, de salud, conductual y el cumplimiento de reportes oficiales SEP/UAEMEX.

### 1.2 Acceso al Sistema

1. Abra un navegador web (Google Chrome o Microsoft Edge recomendados) y acceda a la dirección:
   ```
   https://ades.setag.mx/
   ```
2. Se mostrará la pantalla de inicio de sesión. Ingrese sus credenciales:
   - **Personal docente y administrativo (niveles 0-4):** use su cuenta de Google Workspace institucional (`@institutonevadi.edu.mx`). El sistema lo redirigirá a Google para autenticar.
   - **Padres de familia y alumnos (nivel 5):** use la cuenta local asignada por la secretaría académica. Ingrese usuario y contraseña directamente en la pantalla de acceso.
3. Si tiene configurado el segundo factor de autenticación (MFA), se le pedirá ingresar el código de 6 dígitos de su aplicación TOTP (Google Authenticator, Authy, etc.). Esta medida adicional aplica principalmente a cuentas administrativas.
4. Al ingresar correctamente, ADES cargará el Dashboard principal.

### 1.3 Cerrar Sesión

Para cerrar sesión de forma segura:
1. Haga clic en el ícono de su avatar (esquina superior derecha de la barra).
2. Seleccione **Cerrar sesión** en el menú desplegable.
3. Esto cierra la sesión tanto en ADES como en el proveedor de identidad (Authentik/Google).

> **Recomendación de seguridad:** Cierre siempre su sesión al terminar de usar el sistema, especialmente en computadoras compartidas.

---

## 2. Roles y Niveles de Acceso

ADES implementa un control de acceso basado en roles (RBAC) con alcance territorial por plantel. Cada usuario tiene asignado un nivel de acceso que determina qué puede ver y modificar.

| Nivel | Rol | Descripción |
|---|---|---|
| 0 | Admin Global | Acceso irrestricto a todos los planteles, configuración del sistema, auditoría completa. |
| 1 | Admin Plantel | Gestión de usuarios, alumnos y configuración de su plantel asignado. |
| 2 | Director / Subdirector / Coordinador RH | Supervisión académica, aprobación de sanciones, emisión de certificados, reportes de conducta. |
| 2 | Coordinador de Área | Coordinación por área académica específica (Matemáticas, Español, Inglés, etc.), acceso global por área. |
| 3 | Coordinador Académico / Tutor / Orientador / Secretaria Académica | Configuración curricular, revisión de planeaciones, gestión de planes de mejora conductual. |
| 4 | Docente / Médico Escolar / Prefecto / Apoyo Académico | Registro de asistencias, calificaciones, planeaciones y evaluaciones de sus grupos asignados. |
| 5 | Alumno / Padre de Familia | Consulta de progreso académico, descarga de boletas, entrega de actividades. |

### 2.1 Scoping por Plantel

Los usuarios con nivel de acceso 1 a 4 solo pueden ver y modificar información del plantel al que están asignados. El Admin Global (nivel 0) puede ver y operar en todos los planteles desde una sola cuenta.

### 2.2 Módulos Visibles por Rol

La barra de navegación lateral muestra únicamente los módulos a los que tiene acceso su rol. Un docente no verá, por ejemplo, el módulo de Administración ni el de Licencias de personal.

---

## 3. Barra Superior y Selector de Contexto

La barra superior (topbar) de ADES contiene los selectores de contexto que **afectan la información mostrada en todos los módulos**. Comprender su funcionamiento es fundamental para operar el sistema correctamente.

### 3.1 Elementos de la Barra Superior

La barra superior contiene, de izquierda a derecha:
- **Logotipo Instituto Nevadi** — botón para regresar al Dashboard.
- **Selector de Plantel** — permite cambiar entre los tres planteles (visible para admins globales y usuarios con acceso multi-plantel).
- **Selector de Nivel Educativo** — filtra entre Primaria, Secundaria o Preparatoria.
- **Selector de Ciclo Escolar** — selecciona el ciclo/semestre activo.
- **Ícono de notificaciones** — acceso al buzón de notificaciones del sistema.
- **Avatar de usuario** — nombre del usuario activo y acceso al menú de perfil/cierre de sesión.

### 3.2 Cascada de Selección: Plantel → Nivel → Grado → Grupo

Varios módulos (Calificaciones, Gradebook, Evaluaciones, Asistencias, Kardex) implementan una cascada de selección que sigue esta jerarquía obligatoria:

```
Plantel  →  Nivel Educativo  →  Grado  →  Grupo
```

Esta cascada garantiza que solo vea los datos del grupo específico que desea consultar o modificar. Debe seleccionar cada nivel antes de que el siguiente se habilite:

1. Seleccione el **Plantel** (Metepec, Tenancingo, Ixtapan de la Sal).
2. Seleccione el **Nivel Educativo** (Primaria, Secundaria, Preparatoria).
3. Seleccione el **Grado** (los grados disponibles dependen del nivel y plantel).
4. Seleccione el **Grupo** (grupos A y B disponibles por grado).

> Los selects deshabilitados en color gris claro indican que primero debe elegir el nivel superior de la cascada.

### 3.3 Ciclos Escolares

| Sistema | Ciclo activo | Formato |
|---|---|---|
| SEP (Primaria y Secundaria) | 2026-2027 | Anual |
| UAEMEX (Preparatoria) | 26B / 27A | Semestral |

Al cambiar el ciclo en el selector, todos los módulos se actualizan automáticamente para mostrar información del ciclo seleccionado.

---

## 4. Dashboard — Panel Principal

### 4.1 ¿Qué muestra el Dashboard?

El Dashboard es la pantalla de inicio de ADES. Presenta indicadores clave de desempeño (KPIs) del instituto en tiempo real, filtrados según el contexto (plantel, ciclo) seleccionado.

### 4.2 Tarjetas KPI

Las tarjetas KPI de la parte superior muestran:

| KPI | Descripción |
|---|---|
| Alumnos | Total de alumnos inscritos (con enlace directo al módulo de Alumnos) |
| Profesores | Total de docentes activos en el plantel |
| Grupos | Número de grupos activos en el ciclo actual |
| Cobertura curricular | Porcentaje de temas impartidos vs. planificados |

Cada tarjeta tiene un ícono de color diferenciado por nivel (verde para Primaria, azul para Secundaria, morado para Preparatoria) y actúa como acceso directo al módulo correspondiente.

### 4.3 Widget "Mi Plantel"

Para usuarios de nivel 1-4 (sin acceso global), el widget "Mi Plantel" muestra un resumen ejecutivo del plantel asignado: distribución de alumnos por nivel, grupos activos y estadísticas de asistencia recientes.

### 4.4 Widget "Vista Global de Planteles"

Para el Admin Global (nivel 0), aparece una sección con las estadísticas consolidadas de los tres planteles, permitiendo comparar métricas entre ellos.

### 4.5 Widget de Distribución por Nivel

Un gráfico de barras muestra la distribución de alumnos entre los tres niveles educativos del plantel seleccionado. El gráfico se actualiza al cambiar el contexto en la barra superior.

### 4.6 Personalización del Dashboard

El ícono de engranaje (parte superior derecha del Dashboard) abre el panel de personalización. Puede activar o desactivar cada widget según sus preferencias:

- Bienvenida contextual
- Tarjetas KPI
- Widget Mi Plantel
- Distribución por planteles
- Gráfico de distribución
- Accesos rápidos

La configuración se guarda en su sesión y persiste entre visitas.

---

## 5. Alumnos

**Acceso:** Niveles 1-5 (lectura para nivel 5; escritura para niveles 1-3; nivel 4 solo su grupo)

### 5.1 Lista de Alumnos

El módulo de Alumnos muestra una tabla interactiva estilo Oracle APEX con todos los estudiantes del plantel/ciclo activo. La tabla permite:

- **Búsqueda rápida** por nombre, apellido o matrícula mediante la barra de búsqueda.
- **Filtros por columna** haciendo clic en el encabezado de cada columna (nombre, grado, grupo, estatus).
- **Ordenamiento** ascendente/descendente en cualquier columna haciendo clic en el encabezado.
- **Exportación** a CSV o Excel (con encabezado rojo institucional) mediante los botones correspondientes.
- **Importación masiva** mediante archivo CSV o Excel usando el botón "Importar" (visible para niveles 1-3).

Las columnas principales de la tabla son:
- Matrícula
- Nombre completo
- CURP
- Grado y Grupo
- Plantel
- Estatus (Activo, Baja temporal, Baja definitiva, Egresado)

### 5.2 Alta de Nuevo Alumno

1. Haga clic en el botón **"Nuevo alumno"** (esquina superior derecha).
2. Se abrirá un diálogo con los campos obligatorios:
   - Nombre(s), Apellido paterno, Apellido materno
   - CURP (validado automáticamente)
   - Fecha de nacimiento
   - Sexo (M/F)
   - Plantel de inscripción
3. Haga clic en **Guardar** para crear el registro.
4. El sistema asignará automáticamente una matrícula única.

### 5.3 Perfil Completo del Alumno

Al hacer clic en cualquier fila de la tabla, se despliega un panel lateral (drawer) con el perfil completo del alumno organizado en secciones:

**Sección Datos Personales:**
- Información de identidad (nombre, CURP, fecha de nacimiento)
- Datos de contacto y teléfonos
- Fotografía del expediente

**Sección Domicilio y Contactos:**
- Dirección completa con selector geográfico SEPOMEX (Estado, Municipio, Colonia, Código Postal)
- Contactos familiares vinculados (padres, tutores, contactos de emergencia)

**Sección Documentos (Expediente):**
- Lista de documentos requeridos: CURP, Acta de Nacimiento, Certificado de estudios previos, Comprobante de domicilio
- Estado de cada documento (Entregado, Pendiente)
- Subida de nuevos documentos en formato PDF o imagen
- Estado del procesamiento OCR (Pendiente, Procesando, Completado)
- Búsqueda por contenido dentro de los documentos digitalizados

**Sección Académica:**
- Historial de inscripciones por ciclo
- Calificaciones y asistencias en resumen

**Sección Médica:**
- Información del expediente médico
- Condiciones crónicas activas
- Alergias y medicamentos

### 5.4 Edición de Perfil

Para editar los datos de un alumno:
1. Abra el perfil haciendo clic en la fila de la tabla.
2. Modifique los campos deseados en el panel lateral.
3. Haga clic en **Guardar** para confirmar los cambios.

El sistema registra automáticamente quién realizó el cambio y a qué hora (auditoría automática).

### 5.5 Estatus del Alumno

| Estatus | Descripción |
|---|---|
| Activo | Inscrito en el ciclo actual |
| Baja temporal | Ausencia temporal justificada; se puede reinscribir |
| Baja definitiva | Salida permanente; no se reinscribe |
| Egresado | Completó el nivel educativo |
| Reprobado | Año escolar no acreditado; permanece en el mismo grado |

---

## 6. Admisión

**Acceso:** Niveles 1-3 (gestión completa); nivel 4 (solo consulta)

### 6.1 ¿Qué es el Módulo de Admisión?

El módulo de Admisión gestiona el proceso de ingreso de nuevos estudiantes al Instituto Nevadi: desde la solicitud inicial hasta la inscripción formal. Permite registrar aspirantes, hacer seguimiento de su proceso y generar el PDF de la solicitud oficial.

### 6.2 Lista de Solicitudes

La pantalla principal muestra todas las solicitudes de admisión con filtros por:
- **Estado:** Pendiente, Diagnóstico, Aceptado, Lista de Espera, Rechazado, Inscrito
- **Nivel solicitado:** Primaria, Secundaria, Preparatoria

La tabla muestra: nombre del aspirante, CURP, nivel solicitado, grado, nombre del tutor, correo del tutor, fecha de solicitud, promedio de procedencia y escuela de origen.

### 6.3 Registro de Nueva Solicitud

1. Haga clic en **"Nueva solicitud"**.
2. Complete los datos del aspirante:
   - Nombre completo y CURP
   - Nivel y grado al que aspira ingresar
   - Nombre y correo del tutor responsable
   - Escuela de procedencia y promedio
3. Haga clic en **Guardar**. La solicitud queda en estado **Pendiente**.

### 6.4 Flujo de Admisión

El proceso sigue estos estados:

```
PENDIENTE → DIAGNÓSTICO → ACEPTADO → INSCRITO
                       → LISTA DE ESPERA
                       → RECHAZADO
```

Para avanzar el estado de una solicitud:
1. Haga clic en la fila del aspirante.
2. En el diálogo de detalle, modifique el estado.
3. Para el estado DIAGNÓSTICO, puede registrar la puntuación obtenida y observaciones.
4. Al cambiar a ACEPTADO, el sistema prepara la inscripción formal.

### 6.5 Generación de PDF de Solicitud

1. Seleccione una solicitud en la tabla.
2. Haga clic en el botón **"Generar PDF"**.
3. El sistema genera automáticamente la solicitud oficial en formato PDF, lista para imprimir y entregar al tutor.

---

## 7. Grupos

**Acceso:** Niveles 1-4 (nivel 4 solo consulta de sus grupos asignados)

### 7.1 Lista de Grupos

La tabla de grupos muestra todos los grupos académicos activos del plantel filtrado por el contexto actual. Las columnas son:
- Nombre del grupo (ej. "1°A Primaria")
- Nivel educativo
- Grado
- Turno (Matutino / Vespertino)
- Capacidad máxima
- Alumnos inscritos
- Estatus (Activo / Inactivo)

La barra de búsqueda permite filtrar grupos por nombre o nivel. Los botones CSV y Excel exportan la lista completa.

### 7.2 Creación de Nuevo Grupo

Solo disponible para niveles 1-2:
1. Haga clic en **"Nuevo grupo"**.
2. Seleccione el nivel educativo, grado y ciclo escolar.
3. Ingrese el nombre del grupo, turno y capacidad máxima.
4. Haga clic en **Guardar**.

> Los grupos siguen la convención A y B por grado (máximo 2 por grado). Grupos futuros (semestres de preparatoria no activos) se crean con estatus inactivo y se activan cuando comienza el ciclo correspondiente.

### 7.3 Detalle del Grupo

Al hacer clic en un grupo, se muestra un panel lateral con:
- Lista de alumnos inscritos en el grupo
- Profesor titular asignado (para Primaria)
- Asignaciones de materias y docentes (para Secundaria y Preparatoria)
- Estadísticas de asistencia del grupo

---

## 8. Profesores

**Acceso:** Niveles 1-4 (nivel 4 solo consulta de sus propios datos)

### 8.1 Lista de Profesores

La tabla muestra todos los docentes activos del plantel. Incluye:
- Nombre completo
- CURP
- Correo institucional
- Número de empleado
- Materias asignadas
- Grupos activos
- Estatus

Los botones CSV, Excel e Importar están disponibles para niveles 1-3. El botón "Nuevo profesor" está restringido a niveles 1-2.

### 8.2 Alta de Nuevo Profesor

1. Haga clic en **"Nuevo profesor"**.
2. Complete los datos básicos: nombre, apellidos, CURP, RFC, correo institucional.
3. Seleccione el plantel y nivel educativo al que pertenece.
4. Haga clic en **Guardar**.

### 8.3 Perfil del Profesor

Al hacer clic en un profesor, se abre el perfil completo organizado en secciones:

**Datos Personales:** información de identidad y contacto.

**Disponibilidad Horaria:** la cuadrícula semanal (Lunes a Viernes, franjas de media hora) indica los bloques en que el docente está disponible para impartir clases. Esta información se usa para la generación de horarios automática (exportación a aSc TimeTables).

**Expediente Laboral:** antigüedad, tipo de contrato, documentos de la relación laboral (título, cédula, cartas de recomendación).

**Asignaciones Actuales:** lista de grupos y materias que el docente imparte en el ciclo activo.

### 8.4 Asignación de Materias

Para asignar o reasignar una materia a un docente:
1. Abra el perfil del profesor.
2. En la sección de asignaciones, haga clic en **"Agregar asignación"**.
3. Seleccione el grupo y la materia.
4. Confirme la asignación.

> En Primaria, el profesor titular cubre todas las materias de su grupo. En Secundaria y Preparatoria, un profesor puede estar asignado a una o más materias en uno o varios grupos.

---

## 9. Calificaciones

**Acceso:** Nivel 4 (sus grupos asignados); nivel 3 (todos los grupos del plantel); niveles 1-2 (acceso completo de lectura y ajuste)

### 9.1 Selección del Contexto

Antes de ver o capturar calificaciones, debe seleccionar el contexto completo mediante la cascada:

1. **Plantel** (en la barra superior)
2. **Nivel Educativo** (en la barra superior)
3. **Grado** (selector dentro del módulo)
4. **Grupo** (selector dentro del módulo)
5. **Materia** (selector dentro del módulo)

Una vez seleccionados todos los campos, la tabla de calificaciones carga automáticamente los registros del período activo.

### 9.2 Períodos de Evaluación

Los períodos disponibles dependen del nivel educativo:

| Nivel | Período | Escala |
|---|---|---|
| Primaria SEP | Bimestre 1, 2 y 3 | 1-10 (mínimo aprobatorio: 6) |
| Secundaria SEP | Bimestre 1, 2, 3, 4, 5 y 6 | 1-10 (mínimo aprobatorio: 6) |
| Preparatoria UAEMEX | Parcial 1, Parcial 2, Final, Extraordinario | 0-100 (mínimo aprobatorio: 60) |

### 9.3 Captura de Calificaciones (Edición Inline)

La tabla de calificaciones funciona como una hoja de cálculo editable:

1. Haga clic en la celda de calificación del alumno que desea modificar.
2. Ingrese el valor numérico dentro del rango de escala permitido.
3. Presione Tab para avanzar al siguiente alumno, o Enter para confirmar.
4. La barra superior muestra el contador de cambios pendientes.
5. Cuando termine, haga clic en **"Guardar cambios"** para enviar todos los cambios al servidor en una sola operación.

> **Importante:** Los cambios no se guardan automáticamente. Siempre presione "Guardar cambios" antes de salir de la página o cambiar de materia.

### 9.4 Indicadores de la Tabla

Cada fila de alumno muestra:
- **Promedio** calculado automáticamente según los períodos registrados.
- **Acredita** (Si/No) con colores: verde para aprobado, rojo para reprobado.
- **Calificación cerrada** — un candado indica que la calificación fue cerrada por el coordinador y no puede modificarse sin autorización.

### 9.5 Calificaciones Reprobatorias

Cuando se registra una calificación por debajo del mínimo aprobatorio, el sistema:
- Marca la celda en rojo.
- Genera una alerta académica automática.
- Notifica al padre de familia registrado en el expediente del alumno.

### 9.6 Cierre de Calificaciones

Los coordinadores (nivel 3) pueden cerrar las calificaciones de un período una vez concluido. Al cierre:
- Las calificaciones quedan inmutables para el docente.
- Solo un administrador (nivel 0-1) puede reabrir y justificar el cambio (requiere justificación de al menos 20 caracteres).

### 9.7 Evaluación Cualitativa (1° y 2° Primaria)

Para los grupos de 1° y 2° de Primaria bajo el plan NEM, la tabla de calificaciones cambia automáticamente a escala cualitativa. En lugar de números, se usan descriptores con niveles A, B, C y D (donde A es el nivel más alto). El sistema muestra un panel de leyenda de colores para identificar rápidamente el nivel de cada alumno.

---

## 10. Gradebook — Libreta Digital Avanzada

**Acceso:** Nivel 4 (sus grupos); nivel 3 (todos los grupos del plantel)

### 10.1 ¿Qué es el Gradebook?

El Gradebook es el panel avanzado de calificaciones que va más allá del registro por período. Permite gestionar el ciclo completo de evaluación: desde la creación de actividades individuales (tareas, exámenes, participación) hasta el cálculo automático de la calificación de período ponderada.

### 10.2 Navegación por Pestañas

El Gradebook está organizado en cuatro pestañas:

**Pestaña 1 — Actividades:**
Lista todas las actividades evaluadas del grupo/materia/período seleccionado. Cada actividad muestra:
- Título y tipo (Tarea, Examen, Participación, Proyecto, etc.)
- Fecha de entrega o de examen
- Puntaje máximo
- Total de alumnos / entregas recibidas / actividades calificadas

Desde aquí puede:
- Crear nueva actividad con el botón **"+ Nueva actividad"**
- Ver y calificar las entregas de una actividad
- Consultar si los alumnos entregaron o no

**Pestaña 2 — Entregas:**
Al seleccionar una actividad, esta pestaña muestra la lista de alumnos con su estatus de entrega:
- **Sin entrega** — el alumno no ha subido ningún archivo
- **Entregada** — archivo recibido, pendiente de calificación
- **Calificada** — se registró la calificación obtenida
- **Excusa** — entrega justificada (no cuenta como falta)

Para calificar una entrega:
1. Haga clic en el campo de calificación del alumno.
2. Ingrese la calificación y opcionalmente un comentario.
3. Guarde. El sistema recalcula automáticamente el promedio del período.

**Pestaña 3 — Concentrado:**
Vista consolidada de todas las calificaciones de cada alumno en todos los ítems del período. Funciona como una hoja de cálculo donde las columnas son los ítems (actividades, exámenes) y las filas son los alumnos. La última columna muestra la calificación de período calculada automáticamente. Los alumnos en riesgo de reprobar se marcan con un indicador visual rojo.

**Pestaña 4 — Insights:**
Panel de análisis curricular que muestra:
- Porcentaje de cobertura de temas (temas impartidos vs. planificados)
- Tabla de cobertura por materia
- Estadísticas de tareas vinculadas a temas
- Promedio grupal por materia y número de alumnos en riesgo

### 10.3 Cálculo Automático de Calificaciones

El sistema calcula automáticamente la calificación de período usando la fórmula de ponderación configurada para cada materia/nivel. La ponderación define el peso porcentual de cada tipo de ítem (ej. Tareas 30%, Exámenes 50%, Participación 20%). La suma siempre debe ser 100%.

Cada vez que se registra o modifica una entrega o calificación de evaluación, el sistema recalcula instantáneamente la calificación del período para ese alumno.

### 10.4 Cierre de Período

El botón **"Cerrar período"** (visible para nivel 3) consolida las calificaciones calculadas como definitivas. Una vez cerrado, las calificaciones individuales son inmutables.

### 10.5 Exportación

El botón **"Excel"** exporta el concentrado completo del gradebook en formato .xlsx con el encabezado institucional rojo del Instituto Nevadi. El botón **"Recalcular"** fuerza el recálculo de todas las calificaciones del período seleccionado.

---

## 11. Asistencias

**Acceso:** Nivel 4 (sus clases); niveles 1-3 (consulta de cualquier clase del plantel)

### 11.1 Pase de Lista

El módulo de Asistencias permite registrar la asistencia de los alumnos por clase:

1. Asegúrese de que el **Grupo** está seleccionado en la barra superior.
2. En el selector **"Seleccionar clase"**, elija la clase del día que desea registrar (la lista muestra fecha y materia).
3. Aparecerá la lista de alumnos del grupo.
4. Use el campo de búsqueda para filtrar alumnos por nombre.

### 11.2 Estatus de Asistencia

Para cada alumno puede asignar uno de los siguientes estatus haciendo clic en el botón correspondiente:

| Estatus | Color | Significado |
|---|---|---|
| PRESENTE | Verde | El alumno asistió |
| AUSENTE | Rojo | Falta no justificada |
| TARDE | Amarillo | Llegó después del tiempo de entrada |
| JUSTIFICADO | Azul | Falta con justificación aceptada |

### 11.3 Guardado del Pase de Lista

1. Una vez marcados todos los alumnos, haga clic en **"Guardar asistencias"**.
2. El sistema guarda el registro con fecha y hora, vinculado a la clase seleccionada.
3. Si hay alumnos con asistencia acumulada por debajo del 85%, el sistema genera automáticamente una notificación al padre de familia.

### 11.4 Justificaciones de Faltas

Los coordinadores (nivel 3) pueden justificar faltas desde el módulo de Justificaciones (sección del menú lateral). Para justificar una falta:
1. Acceda al módulo **Justificaciones**.
2. Busque la falta del alumno por nombre o fecha.
3. Ingrese el motivo de la justificación.
4. Haga clic en **Aprobar**. El estatus cambiará a JUSTIFICADO en el registro de asistencia.

---

## 12. Evaluaciones

**Acceso:** Nivel 4 (crear y calificar); nivel 3 (ver y gestionar evaluaciones del plantel)

### 12.1 Tipos de Evaluación

El módulo de Evaluaciones gestiona evaluaciones formales (distintas de las actividades diarias del Gradebook):

- **Diagnóstica** — al inicio del ciclo para identificar el nivel del grupo
- **Formativa** — durante el ciclo para monitorear el avance
- **Sumativa** — al final de un período para evaluación formal

### 12.2 Creación de Evaluación

1. Haga clic en **"Nueva evaluación"**.
2. En el diálogo, seleccione mediante la cascada: Nivel → Grado → Grupo.
3. Ingrese el título, tipo, materia y fecha de aplicación.
4. Haga clic en **Guardar**.

### 12.3 Registro de Calificaciones de Evaluación

1. Seleccione la evaluación en la tabla.
2. Aparece la lista de alumnos del grupo.
3. Ingrese la calificación obtenida por cada alumno.
4. Guarde los registros.

Las calificaciones de evaluaciones se integran automáticamente al cálculo de la calificación de período en el Gradebook, según la ponderación configurada.

---

## 13. Evaluación Docente 360°

**Acceso:** Nivel 2 (Director — puede evaluar a cualquier docente); nivel 3 (Coordinador — puede evaluar como par o coordinador); nivel 4 (solo autoevaluación)

### 13.1 ¿Qué es la Evaluación 360°?

El sistema implementa una evaluación multidimensional del desempeño docente con cuatro perspectivas distintas:

| Tipo de Evaluador | Quién evalúa | Código |
|---|---|---|
| Director | El director del plantel | DIRECTOR |
| Coordinador | El coordinador académico | COORDINADOR |
| Par docente | Otro docente del mismo nivel o área | PAR |
| Autoevaluación | El propio docente | AUTO |

### 13.2 Pestaña Resumen

La pestaña de Resumen muestra las tarjetas KPI con el promedio global obtenido por tipo de evaluador para el docente y ciclo seleccionados. Permite comparar rápidamente cómo perciben el desempeño docente las diferentes perspectivas.

La tabla inferior lista todas las evaluaciones existentes con: tipo de evaluador, fecha, calificación global y estado (Borrador, Enviada, Aprobada).

### 13.3 Pestaña Nueva Evaluación

Para crear una evaluación docente:
1. Seleccione el **docente** a evaluar desde el selector.
2. Seleccione el **tipo de evaluador** que corresponde a su rol.
3. Aparecerá la lista de **criterios** agrupados por categoría (Dominio didáctico, Planeación, Clima de aula, Comunicación, etc.).
4. Para cada criterio, mueva el control deslizante (slider) para asignar una calificación del **1 al 5** (1=Insuficiente, 5=Excelente).
5. Opcionalmente agregue observaciones en el campo de texto.
6. El sistema calcula automáticamente la calificación global ponderada.
7. Haga clic en **Enviar evaluación**.

### 13.4 Exportación

El botón Excel exporta el histórico de evaluaciones de un docente en formato .xlsx para análisis externo o archivo.

---

## 14. Conducta

**Acceso:** Nivel 4 (Prefecto — registra reportes); nivel 3 (Coordinador — gestiona planes de mejora); nivel 2 (Director — aplica sanciones formales)

### 14.1 Lista de Reportes de Conducta

La pantalla principal muestra todos los reportes de conducta del plantel (filtrados por grupo si el contexto lo indica). Los reportes se clasifican por gravedad:

| Gravedad | Color | Descripción |
|---|---|---|
| LEVE | Azul | Incidente menor (retraso repetido, falta de material) |
| GRAVE | Amarillo/Naranja | Incidente que afecta el ambiente de aprendizaje |
| MUY_GRAVE | Rojo | Incidente que requiere intervención inmediata |

### 14.2 Nuevo Reporte de Conducta

1. Haga clic en **"Nuevo reporte"** (botón con data-testid "btn-nueva-sancion").
2. Seleccione el alumno (búsqueda por nombre o matrícula).
3. Ingrese:
   - Descripción del incidente
   - Gravedad (Leve, Grave, Muy Grave)
   - Fecha del incidente
4. Guarde el reporte.

### 14.3 Detalle Completo del Reporte

Al hacer clic en un reporte, se abre un diálogo con cuatro pestañas:

**Pestaña Reporte:** información básica del incidente y su historial.

**Pestaña Sanción** (solo para Director, nivel 2):
Permite aplicar una sanción formal al alumno:
- Tipo de sanción: Amonestación verbal, Amonestación escrita, Citatorio a padres, Suspensión (1, 3 o 5 días), Condicional, Expulsión
- Fecha de aplicación
- Notificación a padres (toggle con fecha y medio: Presencial, Teléfono, Email, WhatsApp)
- Autorizado por (nombre del director)

**Pestaña Plan de Mejora** (nivel 3 en adelante):
El coordinador puede crear un plan de mejora conductual que incluye:
- Compromisos del alumno (lista editable: descripción, plazo en días, estado cumplido/pendiente)
- Compromisos del padre de familia
- Compromisos de la escuela
- Firma del alumno, padre y coordinador
- Estado general del plan: Activo, En proceso, Cumplido, Incumplido, Cancelado

**Pestaña Seguimientos:**
Historial cronológico de los avances del plan de mejora. Para agregar un seguimiento:
1. Haga clic en **"Agregar seguimiento"**.
2. Seleccione el nivel de avance: Sin avance, Parcial, Satisfactorio, Excelente.
3. Ingrese las observaciones del seguimiento.
4. Guarde. El sistema actualiza automáticamente el estado general del plan.

---

## 15. Kardex UAEMEX

**Acceso:** Niveles 1-3 (acceso completo); nivel 4 (solo consulta); nivel 5 alumno (solo su propio kardex)

### 15.1 ¿Qué es el Kardex?

El Kardex es el historial académico oficial de los alumnos de Preparatoria UAEMEX. Muestra las calificaciones de cada materia con las tres instancias de evaluación previstas por el Reglamento General de Evaluación y Movilidad de la UAEMEX (RGEMS):

| Instancia | Descripción | Escala |
|---|---|---|
| Ordinario | Evaluación durante el semestre | 0-100 |
| Extraordinario | Evaluación de segunda oportunidad | 0-100 |
| Definitiva | Calificación final registrada | 0-100 |

**Mínimo aprobatorio:** 60 puntos. Una materia no acreditada con 60 en ordinario puede sustentarse en extraordinario.

### 15.2 Consulta del Kardex

1. Seleccione el **Plantel** (Metepec o Tenancingo — solo planteles con preparatoria activa).
2. Seleccione el **Semestre** (semestres activos del plantel seleccionado).
3. Seleccione el **Grupo**.
4. Seleccione el **Alumno** de la lista desplegable.
5. El kardex se carga automáticamente mostrando:
   - Ficha del alumno (nombre, matrícula, CURP, semestre, plantel, ciclo)
   - Tabla de materias con calificaciones ordinario/extraordinario/definitiva
   - Indicador "Acreditada" (Si/No) por materia
   - Inasistencias por materia
   - Promedio general del semestre
   - Materias acreditadas y reprobadas en resumen

### 15.3 Descarga de Constancia PDF

El botón **"Constancia PDF"** genera la constancia oficial de calificaciones del semestre en formato PDF para el alumno seleccionado. El documento incluye la firma digital del plantel.

---

## 16. Boleta NEM (Primaria y Secundaria)

**Acceso:** Niveles 1-3 (emisión y descarga); nivel 4 (descarga solo de sus alumnos); nivel 5 (descarga de su propio historial)

### 16.1 ¿Qué es la Boleta NEM?

La Boleta NEM es el documento oficial de evaluación para Primaria y Secundaria bajo el Plan de Estudios 2022 (Nueva Escuela Mexicana, DGAIR/SIGED). Refleja el desempeño del alumno organizado por **Campos Formativos** (agrupaciones curriculares NEM):

- Lenguajes
- Saberes y Pensamiento Científico
- Ética, Naturaleza y Sociedades
- De lo Humano y lo Comunitario

### 16.2 Generación de Boleta Individual

1. Busque al alumno en el módulo de Alumnos.
2. Abra el perfil del alumno.
3. Haga clic en el botón **"Descargar Boleta NEM"**.
4. El sistema genera el PDF con:
   - Membrete oficial del Instituto Nevadi
   - Datos del alumno (nombre, CURP, matrícula, grupo, ciclo)
   - Calificaciones agrupadas por campo formativo (escala 6-10)
   - Asistencias del período
   - Indicador "Acredita el grado" (Sí/No)
   - Firma del profesor titular

### 16.3 Escala de Calificación NEM

Las boletas de Primaria y Secundaria usan la escala SEP de 6 a 10 puntos:
- **10 — Excelente**
- **9 — Muy bueno**
- **8 — Bueno**
- **7 — Suficiente**
- **6 — Suficiente mínimo**
- **Menos de 6 — No acreditado (NE)**

---

## 17. Boleta UAEMEX (Preparatoria)

**Acceso:** Niveles 1-3 (emisión); nivel 5 (descarga de su propia boleta)

### 17.1 Acceso a la Boleta UAEMEX

La constancia de calificaciones de preparatoria se genera desde el módulo **Kardex**:
1. Seleccione plantel, semestre, grupo y alumno.
2. Haga clic en **"Constancia PDF"**.
3. El sistema genera el documento oficial con:
   - Encabezado con datos del plantel y ciclo UAEMEX
   - Ficha del alumno
   - Tabla con columnas: Materia, Clave, Ordinario, Extraordinario, Definitiva
   - Resumen de materias acreditadas/reprobadas
   - Promedio general del semestre
   - Sección de firmas (docentes y director)

El documento cumple el formato estándar RGEMS de la UAEMEX para constancias de calificaciones.

---

## 18. Biblioteca

**Acceso:** Niveles 1-4 (gestión completa); nivel 5 (solo consulta de catálogo)

### 18.1 Catálogo de Libros

La pestaña **Libros** muestra el catálogo completo de la biblioteca del plantel. La tabla incluye:
- Título, autor, ISBN
- Editorial y año de publicación
- Categoría (Literatura, Ciencia, Historia, Matemáticas, Arte, Tecnología, Infantil, Consulta, Texto, Otro)
- Ubicación física (número de estante o sala)
- Ejemplares totales / disponibles

La barra de búsqueda permite buscar por título, autor o ISBN. Se puede filtrar por categoría mediante el selector correspondiente.

### 18.2 Alta de Libro

1. Haga clic en **"Nuevo libro"**.
2. Complete los campos:
   - Título (obligatorio)
   - Autor, ISBN, Editorial, Año de publicación
   - Categoría
   - Ubicación en biblioteca
   - Número de ejemplares totales disponibles
3. Haga clic en **Guardar**. Los ejemplares disponibles se inician igual al total.

### 18.3 Gestión de Préstamos

La pestaña **Préstamos** muestra todos los préstamos activos y el historial. Las columnas incluyen:
- Libro prestado
- Persona (alumno o profesor)
- Número de control
- Fecha de préstamo
- Fecha esperada de devolución
- Fecha real de devolución
- Estado (Activo, Devuelto, Vencido)
- Indicador de préstamo vencido (rojo)

### 18.4 Registro de Préstamo

1. Haga clic en **"Nuevo préstamo"**.
2. Busque y seleccione el libro deseado.
3. Busque y seleccione al alumno o profesor que lo lleva.
4. Ingrese la fecha esperada de devolución.
5. Haga clic en **Guardar**.

El sistema decrementa automáticamente el contador de ejemplares disponibles. Si no hay ejemplares disponibles, el sistema impide registrar el préstamo.

### 18.5 Registro de Devolución

1. Localice el préstamo activo en la tabla.
2. Haga clic en el botón **"Devolver"** de la fila correspondiente.
3. Confirme la operación. El sistema registra la fecha real de devolución y actualiza el contador de ejemplares disponibles.

### 18.6 Control de Vencimientos

Los préstamos vencidos (fecha de devolución esperada superada sin devolución real) se marcan automáticamente con el estatus **VENCIDO** y se resaltan en rojo en la tabla. Esto facilita la identificación rápida de materiales atrasados.

---

## 19. Estadística 911 SEP

**Acceso:** Niveles 1-3; solo para planteles con Primaria y/o Secundaria SEP

### 19.1 ¿Qué es el Formato 911?

El Formato 911 es el reporte estadístico oficial que la SEP requiere a las instituciones educativas al inicio del ciclo escolar. ADES **pre-calcula** las cifras necesarias para que la secretaría académica las transcriba a la plataforma oficial f911 de la SEP. El sistema **no envía directamente** a la SEP.

### 19.2 Generación del Reporte

1. Acceda al módulo **Estadística 911** desde el menú lateral.
2. Opcionalmente ingrese el UUID del ciclo escolar (si lo deja vacío, usa el ciclo vigente).
3. Haga clic en **Generar**.
4. El sistema calcula las matrices estadísticas en tiempo real.

### 19.3 Contenido del Reporte

El reporte muestra las matrices estadísticas requeridas por el formato 911, organizadas por nivel educativo (Primaria, Secundaria):

**Sección IV.1 — Alumnado por grado, sexo, ingreso y edad:**
Matriz con:
- Filas: edades (cubetas oficiales SEP por nivel)
- Columnas: grados (1° a 6° para Primaria, 1° a 3° para Secundaria)
- Para cada celda de grado: Hombres Nuevo Ingreso, Hombres Repetidor, Mujeres Nuevo Ingreso, Mujeres Repetidor

Las cubetas de edad utilizadas son las oficiales SEP:
- Primaria: "Menos de 6", 6-14 años, "15 y más"
- Secundaria: "Menos de 11", 11-17 años, "18 y más"

**Sección de Grupos:**
Total de grupos activos por grado.

**Sección IX — Discapacidad:**
Alumnos con condiciones registradas como discapacidad, clasificados por tipo de discapacidad, grado y sexo. Los datos provienen de los expedientes médicos registrados en el módulo de Condiciones Crónicas.

### 19.4 Exportación

El botón **"Exportar Excel"** (disponible por nivel) genera el archivo .xlsx con los datos listos para captura en la plataforma oficial f911 de la SEP.

---

## 20. Horarios

**Acceso:** Niveles 1-3 (crear y modificar); nivel 4 (solo consulta de sus horarios)

### 20.1 Vista Semanal de Horarios

El módulo de Horarios muestra una cuadrícula semanal (Lunes a Viernes) con las franjas horarias del día. Puede ver el horario de:
- **Un grupo** — todas las clases asignadas al grupo seleccionado
- **Un docente** — todos los grupos donde imparte clases el profesor seleccionado

Use los botones de selección para cambiar entre vista por Grupo y vista por Docente.

### 20.2 Entradas de Horario

Cada celda de la cuadrícula muestra:
- Nombre de la materia (con color de fondo por materia)
- Nombre del docente
- Nombre del aula asignada

Los colores por materia facilitan la lectura visual del horario.

### 20.3 Creación de Entrada de Horario

1. Haga clic en **"Nueva entrada"** o directamente en una celda vacía del horario.
2. En el diálogo, seleccione:
   - Día de la semana (Lunes a Viernes)
   - Hora de inicio
   - Duración (30 min, 45 min, 50 min, 1 hora, 1.5 horas, 2 horas)
   - Grupo
   - Materia
   - Docente
   - Aula (opcional)
3. Haga clic en **Guardar**.

### 20.4 Edición y Eliminación

Para editar una entrada:
1. Haga clic sobre la entrada en la cuadrícula.
2. Se abre el diálogo de edición con los datos actuales.
3. Modifique los campos necesarios y guarde.

Para eliminar una entrada:
1. Abra el diálogo de la entrada.
2. Haga clic en el botón **"Eliminar"** (rojo).
3. Confirme la eliminación.

### 20.5 Exportación a aSc TimeTables

El botón **"Exportar XML aSc"** genera el archivo XML compatible con el software de generación automática de horarios aSc TimeTables. Este archivo incluye:
- Datos de grupos, docentes y aulas
- Disponibilidad horaria de cada docente
- Restricciones configuradas

Para reimportar un horario generado por aSc:
1. Haga clic en **"Importar XML aSc"**.
2. Seleccione el archivo XML exportado por aSc TimeTables.
3. El sistema carga las asignaciones y actualiza el horario.

---

## 21. Aulas

**Acceso:** Niveles 1-3 (gestión completa); nivel 4 (solo consulta)

### 21.1 Catálogo de Aulas

La tabla de aulas muestra todos los espacios físicos del plantel:
- Nombre o código del aula
- Capacidad máxima de alumnos
- Tipo (Aula regular, Laboratorio, Sala de cómputo, Sala de artes, Patio, etc.)
- Equipamiento disponible (proyector, computadoras, pizarrón inteligente, etc.)
- Estado (Operativa, En mantenimiento, Fuera de servicio)
- Observaciones adicionales

### 21.2 Alta y Edición de Aula

1. Haga clic en **"Nueva aula"** o seleccione un aula existente para editarla.
2. Complete los campos del formulario.
3. Guarde los cambios.

### 21.3 Disponibilidad de Aula

Puede registrar períodos de no disponibilidad (mantenimiento, eventos especiales) para un aula:
1. Seleccione el aula en la tabla.
2. Haga clic en **"Registrar no disponibilidad"**.
3. Ingrese el período (fecha/hora de inicio y fin) y el motivo.
4. El sistema usará esta información al verificar conflictos de horario.

### 21.4 Verificación de Conflictos

El botón **"Verificar conflicto"** permite comprobar si un aula está disponible en un horario específico antes de asignarla a una clase.

---

## 22. Médico y Condiciones Crónicas

**Acceso:** Nivel 4 Médico Escolar (acceso completo); niveles 1-3 (lectura y gestión); nivel 4 docente (solo visualizar alertas de emergencia)

### 22.1 Expediente Médico

El módulo Médico permite al médico escolar registrar y consultar información de salud de los alumnos:
- Tipo de sangre
- Alergias conocidas
- Medicamentos actuales
- Enfermedades previas relevantes
- Vacunas al día
- Contacto de emergencia médica

### 22.2 Condiciones Crónicas

El módulo de Condiciones Crónicas registra padecimientos que requieren atención especial durante la jornada escolar:

**Registro de Condición:**
1. Busque al alumno.
2. Haga clic en **"Nueva condición"**.
3. Complete:
   - Tipo de condición (ej. DIABETES, EPILEPSIA, DISCAPACIDAD_VISUAL, ALERGIAS, etc.)
   - Descripción detallada
   - Indicaciones para emergencia
   - Medicamentos de emergencia si aplica
4. Guarde la condición.

**Alerta de Emergencia:**
Cada condición puede marcarse como **alerta de emergencia activa**. Las condiciones marcadas como emergencia son visibles para todos los docentes del grupo del alumno mediante un ícono de alerta en la lista de asistencias.

### 22.3 Reporte de Discapacidades

Las condiciones registradas con prefijo "DISCAPACIDAD_" alimentan automáticamente la Sección IX del reporte estadístico 911 SEP.

---

## 23. Personal Administrativo

**Acceso:** Niveles 1-2 (gestión completa); nivel 3 Coordinador RH (gestión de su área)

### 23.1 Lista de Personal

El módulo de Personal Administrativo gestiona los empleados no docentes del plantel (secretarias, intendencia, prefectos, personal de apoyo). La tabla muestra:
- Nombre completo
- Puesto/cargo
- Área o departamento
- Turno
- Fecha de ingreso
- Estatus (Activo/Inactivo)

### 23.2 Expediente del Personal

Al hacer clic en un empleado se accede a su expediente completo:
- Datos personales y contacto (sin teléfono/email en la vista principal; se gestiona en contactos vinculados)
- Documentos laborales (título, comprobante de domicilio, IMSS, etc.)
- Historial de puestos dentro de la institución

### 23.3 Asistencia del Personal

El submódulo de Asistencia Personal registra la puntualidad y asistencia del personal administrativo por jornada. Accesible desde el menú lateral bajo "RRHH".

---

## 24. Licencias y Capacitaciones

**Acceso:** Niveles 1-2 (gestión completa); nivel 3 Coordinador RH (gestión de su área); nivel 4 (solicitar y ver sus propias licencias/capacitaciones)

### 24.1 Licencias

El módulo de Licencias registra las ausencias formales del personal docente y administrativo:
- **Por incapacidad médica** — con folio IMSS
- **Permiso con goce de sueldo** — vacaciones, permisos especiales
- **Permiso sin goce de sueldo** — ausencias no remuneradas
- **Comisión** — representación en eventos externos

Para registrar una licencia:
1. Seleccione al empleado.
2. Ingrese el tipo, fecha de inicio, fecha de fin y motivo.
3. Adjunte el documento justificativo si aplica.
4. Guarde. La licencia queda en estado PENDIENTE hasta ser aprobada por el director.

### 24.2 Capacitaciones

El módulo de Capacitaciones registra los cursos y actualizaciones del personal:
- Nombre del curso / taller / diplomado
- Institución que lo imparte
- Fecha de inicio y fin
- Horas totales
- Calificación obtenida (si aplica)
- Constancia digital (adjunto PDF)

---

## 25. Padres y Tutores

**Acceso:** Niveles 1-3 (gestión de contactos familiares); nivel 5 Padre de Familia (solo su propio perfil e hijos)

### 25.1 Contactos Familiares

El sistema vincula a los padres/tutores con los alumnos mediante la tabla de contactos familiares. Cada vínculo incluye:
- Nombre del contacto
- Parentesco (Padre, Madre, Tutor legal, Abuelo, Tío, etc.)
- Teléfonos y correo electrónico
- Flags: Es tutor legal, Puede recoger al alumno, Es contacto de emergencia

Un padre puede tener múltiples hijos registrados en el sistema. El portal de padres les permite consultar las calificaciones, asistencias y comunicados de sus hijos.

### 25.2 Portal del Padre de Familia

Los padres de familia acceden con cuentas locales asignadas por la secretaría académica. Al iniciar sesión, ven:
- Resumen del desempeño de cada hijo
- Calificaciones por período y materia
- Registro de asistencias con porcentaje acumulado
- Comunicados recibidos
- Descarga de boletas disponibles

### 25.3 Gestión de Contactos (Personal Administrativo)

Para agregar o modificar un contacto familiar:
1. Abra el perfil del alumno.
2. En la sección "Domicilio y Contactos", haga clic en **"Agregar contacto"**.
3. Complete los datos del familiar y los indicadores booleanos.
4. Guarde. El sistema vincula automáticamente al contacto con el alumno.

---

## 26. Comunicados

**Acceso:** Niveles 1-3 (crear y enviar); nivel 4 (recibir y leer); nivel 5 (recibir y leer)

### 26.1 Lista de Comunicados

El módulo de Comunicados gestiona la mensajería interna del instituto. La tabla muestra:
- Título del comunicado
- Autor / área remitente
- Destinatarios (todos los docentes, un grupo específico, etc.)
- Fecha de publicación
- Estado de lectura (Leído / No leído)

### 26.2 Nuevo Comunicado

1. Haga clic en **"Nuevo comunicado"**.
2. Complete los campos obligatorios:
   - **Título** (requerido, mínimo 5 caracteres)
   - **Contenido** del comunicado
   - **Destinatarios** — seleccione el grupo o nivel al que va dirigido
3. Haga clic en **"Publicar"**.

El sistema registra automáticamente el acuse digital de lectura cuando cada destinatario abre el comunicado.

### 26.3 Acuse de Lectura

Los comunicados importantes pueden marcarse para requerir acuse de lectura. El remitente puede ver el reporte de quién ha leído el comunicado y quién no, para dar seguimiento.

---

## 27. Planeación Curricular

**Acceso:** Nivel 4 (sus propias planeaciones); nivel 3 (revisar y aprobar planeaciones del plantel)

### 27.1 Planes de Clase

El módulo de Planeación permite a los docentes registrar sus planes de clase vinculados al mapa curricular:
- Tema del plan de estudios (tomado del catálogo de temas por materia/grado)
- Objetivo de la sesión
- Estrategias didácticas
- Recursos y materiales
- Evaluación de la sesión
- Fecha de impartición

### 27.2 Mapa Curricular

La pestaña de Mapa Curricular muestra la secuencia completa de temas por materia y grado:
- Para Primaria: temas por bimestre
- Para Secundaria: temas por bimestre
- Para Preparatoria: temas por parcial

La selección se hace mediante cascada Nivel → Grado → Materia. Cada tema muestra si ya fue impartido (vinculado a una planeación de clase).

### 27.3 Planes de Estudio

El apartado de Planes de Estudio (accesible para coordinadores) permite consultar y administrar los planes vigentes:
- NEM 2022 Primaria y Secundaria (materias y temas por grado y bimestre)
- CBU 2024 UAEMEX Preparatoria (materias y temas por semestre y parcial)
- Materias institucionales del Instituto Nevadi

---

## 28. Reinscripción

**Acceso:** Niveles 1-3

### 28.1 ¿Qué es el Módulo de Reinscripción?

Permite gestionar el proceso de reinscripción al ciclo escolar siguiente. El sistema aplica automáticamente las reglas de promoción vigentes:

| Situación | Acción automática |
|---|---|
| Alumno con todas las materias acreditadas | Se reinscribe al grado siguiente |
| Alumno REPROBADO | Permanece en el mismo grado |
| Alumno BAJA DEFINITIVA | No se reinscribe |
| Alumno EGRESADO | No se reinscribe (completó el nivel) |
| Sin grupo destino disponible | Va a lista de promociones pendientes |

### 28.2 Proceso de Reinscripción

1. El coordinador revisa la lista de alumnos pendientes de reinscripción.
2. Para cada alumno, puede confirmar o rechazar la reinscripción propuesta.
3. Para rechazar, debe indicar el motivo (ej. documentación incompleta, adeudos de materias).
4. Una vez procesados, los alumnos confirmados quedan inscritos en el ciclo siguiente.

### 28.3 Cierre de Ciclo y Promoción Automática

El administrador puede ejecutar la función de **Cierre de Ciclo** que:
1. Calcula el estatus de promoción de todos los alumnos del plantel.
2. Reinscribe automáticamente a los alumnos que cumplen los requisitos.
3. Los casos excepcionales (sin grupo destino, alumnos en seguimiento especial) van a la lista de "Promociones pendientes" para resolución manual.

---

## 29. Acta de Evaluación UAEMEX

**Acceso:** Niveles 1-3; solo para grupos de Preparatoria

### 29.1 ¿Qué es el Acta de Evaluación?

El Acta de Evaluación es el documento oficial UAEMEX que reporta las calificaciones definitivas de un grupo-materia por semestre. Se usa para el control escolar interno y eventualmente para reporte a UAEMEX.

### 29.2 Generación del Acta

La pantalla implementa la cascada completa para Preparatoria:

1. Seleccione **Plantel**.
2. Seleccione **Semestre** (los semestres activos del plantel).
3. Seleccione **Grupo**.
4. Seleccione **Materia**.

El acta se genera automáticamente mostrando:
- **Cabecera:** grupo, semestre, plantel, ciclo, materia, clave y docente asignado
- **Lista de alumnos:** número de orden, nombre, matrícula, CURP
- **Columnas de calificación:** Ordinario, Extraordinario, Definitiva, Acreditada (Si/No), Inasistencias
- **Pie del acta:** total de alumnos, acreditados, reprobados, sin calificación, promedio grupal

### 29.3 Exportación del Acta

El botón **"Exportar Excel"** genera el acta en formato .xlsx. El botón **"Imprimir"** abre el diálogo de impresión del navegador para obtener copia en papel con firma.

---

## 30. Asistente de Inteligencia Artificial

**Acceso:** Todos los niveles (con algunas funciones restringidas a niveles 1-3)

### 30.1 Pestaña Chat con el Asistente

ADES integra un asistente pedagógico con IA que puede responder preguntas sobre el sistema, los datos académicos y dar recomendaciones educativas. El asistente se accede desde el menú lateral en la sección "Inteligencia Artificial".

Para usar el chat:
1. Escriba su pregunta en el campo de texto inferior.
2. Presione Enter o el botón de enviar.
3. El asistente responde en segundos con información contextualizada para su institución.

Ejemplos de preguntas útiles:
- "¿Cuántos alumnos están en riesgo de reprobar en el grupo 3°A Secundaria?"
- "¿Qué estrategias didácticas puedo usar para reforzar fracciones en 4° primaria?"
- "Muéstrame el resumen de asistencias del plantel Metepec este bimestre."

### 30.2 Historial de Conversaciones

El panel lateral (colapsible) muestra las últimas 8 sesiones de chat guardadas. Puede:
- **Cargar** una sesión anterior para continuar la conversación.
- **Eliminar** sesiones que ya no necesita.

### 30.3 Pestaña Alertas Académicas

La segunda pestaña muestra las alertas académicas generadas automáticamente por el sistema:

| Nivel de Riesgo | Color | Descripción |
|---|---|---|
| BAJO | Azul | Tendencia de baja menor a monitorear |
| MEDIO | Amarillo | Riesgo moderado, requiere seguimiento |
| ALTO | Naranja | Riesgo significativo, requiere intervención |
| CRITICO | Rojo | Situación urgente, requiere acción inmediata |

El botón **"Escanear grupo"** analiza el grupo seleccionado y genera o actualiza las alertas para todos sus alumnos. El botón **"Marcar atendida"** en cada alerta registra que el docente/coordinador tomó acción.

### 30.4 Rutas de Aprendizaje (Learning Paths)

Accesible desde el menú **Rutas de Aprendizaje**, este módulo ofrece secuencias de recursos de refuerzo adaptativas para alumnos con alertas académicas activas. Al hacer clic en el botón ✨ junto a un alumno en la lista de alertas, el sistema genera mediante IA una recomendación personalizada que incluye:
- Análisis del historial académico
- Fortalezas detectadas
- Áreas de oportunidad
- Estrategias de refuerzo sugeridas
- Recursos priorizados
- Frase motivacional para el alumno

---

## 31. Administración del Sistema

**Acceso:** Nivel 0 (Admin Global) y nivel 1 (Admin Plantel) únicamente

### 31.1 Pestañas del Módulo de Administración

El módulo de Administración está organizado en las siguientes pestañas:

**Pestaña Usuarios:**
Lista todos los usuarios del sistema con sus datos básicos:
- Nombre de usuario, correo institucional, nombre completo
- Rol asignado y nivel de acceso
- Plantel asignado (para usuarios con alcance de plantel)
- Nivel educativo asignado
- Estatus (Activo / Inactivo)

Desde aquí puede crear nuevos usuarios, modificar su rol o plantel, y desactivar cuentas.

**Pestaña Ciclos:**
Gestiona los ciclos escolares registrados:
- Nombre del ciclo, nivel educativo asociado
- Fechas de inicio y fin
- Tipo de ciclo (ANUAL para SEP, SEMESTRAL para UAEMEX)
- Si es el ciclo vigente

**Pestaña Planteles:**
Permite editar los datos de los planteles existentes (nombre, clave CT, estatus activo/inactivo). No es posible crear planteles nuevos desde la UI (requiere migración de base de datos).

**Pestaña Grupos:**
Visualización y edición de grupos: nombre, nivel, grado, ciclo, capacidad máxima, turno y estatus activo.

**Pestaña Variables del Sistema:**
Los 18 parámetros de configuración global del sistema, organizados en grupos:
- **GENERAL:** Nombre del sistema, nombre de la institución, eslogan
- **CONTACTO:** Teléfono principal, correo de contacto, sitio web
- **APARIENCIA:** URL del logotipo, color primario (#C41724), color secundario, favicon
- **SEP:** Claves CCT de cada plantel por nivel (Primaria, Secundaria, Preparatoria)
- **FUNCIONALIDAD:** Activación del portal de padres, encuestas, módulo IA, API key de IA

Las variables marcadas como públicas son accesibles sin autenticación (ej. nombre de la institución para la pantalla de login).

**Pestaña Catálogos:**
Gestión de los catálogos geográficos SEPOMEX (Estados, Municipios, Localidades, Colonias, Códigos Postales). Estos catálogos alimentan el selector geográfico en las fichas de alumnos y profesores.

**Pestaña Marca:**
Personalización visual del sistema: logotipo institucional, colores principales y favicon. Los cambios se aplican en tiempo real a toda la interfaz.

**Pestaña Auditoría:**
Registro inmutable de todas las acciones realizadas en el sistema. Cada entrada incluye:
- Fecha y hora exacta (TIMESTAMPTZ)
- Usuario que realizó la acción
- Tipo de acción (INSERT, UPDATE, DELETE)
- Tabla afectada
- Valores anteriores y nuevos
- Hash de integridad encadenado (MD5)

El registro de auditoría es de solo lectura. Ni siquiera el Admin Global puede modificar o eliminar entradas de auditoría.

---

## 32. Seguridad del Sistema

### 32.1 Autenticación

ADES utiliza el protocolo OIDC (OpenID Connect) con Authentik como proveedor de identidad:
- Personal docente y administrativo: Google Workspace SSO (`@institutonevadi.edu.mx`)
- Padres y alumnos: cuentas locales con contraseña gestionadas por la secretaría

### 32.2 Segundo Factor de Autenticación (MFA)

Las cuentas administrativas (niveles 0-2) deben configurar MFA obligatorio mediante una aplicación TOTP (Time-based One-Time Password). Las aplicaciones recomendadas son:
- Google Authenticator
- Authy
- Microsoft Authenticator

Para configurar MFA: acceda a su perfil en Authentik (`https://ades.setag.mx/auth/`) y siga las instrucciones del apartado de Seguridad.

### 32.3 Control de Acceso por Plantel

Todos los usuarios con nivel 1-4 solo pueden ver y modificar datos del plantel al que están asignados. Los intentos de acceder a datos de otros planteles son rechazados automáticamente con un error 403 (Acceso denegado), independientemente de cómo se construya la solicitud.

### 32.4 Comunicación Cifrada

Toda la comunicación entre el navegador y el servidor está protegida con TLS (HTTPS). El acceso por HTTP es redirigido automáticamente a HTTPS. El certificado SSL es emitido por Let's Encrypt y se renueva automáticamente.

### 32.5 Rate Limiting

El sistema implementa límites de velocidad en las API para prevenir ataques de fuerza bruta y abuso:
- Máximo 60 solicitudes por minuto por IP en endpoints públicos
- Máximo 30 solicitudes por minuto en endpoints de autenticación

Si supera estos límites, recibirá temporalmente un error 429 (Demasiadas solicitudes). Esto es normal y se resuelve esperando unos segundos.

### 32.6 Certificados Digitales con Firma Ed25519

Los certificados académicos emitidos por ADES incluyen firma criptográfica Ed25519 y código QR para verificación pública. Para verificar la autenticidad de un certificado:
1. Escanee el código QR con su teléfono, o
2. Acceda a `https://ades.setag.mx/verificar/[FOLIO]`.
3. El sistema muestra si la firma es válida y si el certificado no ha sido alterado.

Esta funcionalidad es accesible públicamente sin necesidad de cuenta en el sistema.

---

## 33. Exportación de Datos

### 33.1 Formatos Disponibles

Prácticamente todos los módulos del sistema ofrecen exportación en dos formatos:

| Formato | Botón | Descripción |
|---|---|---|
| CSV | "CSV" | Archivo de texto separado por comas, compatible con cualquier hoja de cálculo |
| Excel (.xlsx) | "Excel" | Archivo Excel con encabezado rojo institucional del Instituto Nevadi |

### 33.2 Exportación de Reportes Oficiales

Los reportes oficiales tienen botones de exportación específicos:

| Reporte | Formato | Módulo |
|---|---|---|
| Boleta NEM | PDF | Módulo Alumnos / Perfil del alumno |
| Boleta UAEMEX | PDF | Módulo Kardex |
| Acta de Evaluación | Excel | Módulo Acta de Evaluación |
| Estadística 911 | Excel (por nivel) | Módulo Estadística 911 |
| Horarios aSc | XML | Módulo Horarios |
| Certificado digital | PDF con QR | Módulo Certificados |

### 33.3 Importación Masiva

Los módulos de Alumnos, Grupos y Profesores permiten importar datos desde archivos CSV o Excel. Para importar:
1. Haga clic en el botón **"Importar"** del módulo correspondiente.
2. Descargue la plantilla de ejemplo haciendo clic en "Descargar plantilla".
3. Complete la plantilla con sus datos respetando el formato de las columnas.
4. Suba el archivo completado.
5. El sistema valida los datos antes de importar y reporta cualquier error por fila.

---

## 34. Preguntas Frecuentes

### 34.1 No puedo iniciar sesión con mi cuenta de Google

**Causa probable:** Su cuenta de Google (`@institutonevadi.edu.mx`) no está registrada como usuario en ADES.
**Solución:** Contacte al administrador del sistema para que registre su usuario y le asigne el rol correspondiente.

---

### 34.2 No veo los alumnos de mi grupo en el módulo de Calificaciones

**Causa probable:** No ha seleccionado el contexto completo (Plantel → Nivel → Grado → Grupo) en la cascada de selección.
**Solución:** Verifique que todos los selectores de la cascada estén activos (no en gris). Si un selector está deshabilitado, primero seleccione el nivel superior de la jerarquía.

---

### 34.3 Guardé calificaciones pero no aparecen después de recargar la página

**Causa probable:** Hizo cambios en la tabla pero no presionó el botón **"Guardar cambios"** antes de salir.
**Solución:** Las calificaciones se acumulan localmente y requieren guardar explícitamente. Siempre verifique que el contador de "cambios sin guardar" llegue a 0 antes de salir.

---

### 34.4 La boleta NEM no muestra calificaciones de todos los períodos

**Causa probable:** Solo se imprimirán los períodos que tengan calificaciones registradas y cerradas.
**Solución:** Verifique que todas las calificaciones del período estén capturadas y que el coordinador haya cerrado los períodos correspondientes.

---

### 34.5 El sistema me muestra error 403 al intentar acceder a un módulo

**Causa probable:** Su rol no tiene permiso para acceder a ese módulo o al plantel que tiene seleccionado en el contexto.
**Solución:** Verifique que el plantel seleccionado en la barra superior corresponda al plantel asignado a su cuenta. Si el problema persiste, contacte al administrador.

---

### 34.6 ¿Cómo agrego a un alumno de nuevo ingreso que ya solicitó admisión?

1. Vaya al módulo de **Admisión** y cambie el estado de la solicitud a **ACEPTADO**.
2. Una vez en estado ACEPTADO, cree al alumno desde el módulo de **Alumnos** con el botón "Nuevo alumno" usando los datos de la solicitud.
3. El sistema asignará la matrícula automáticamente.

---

### 34.7 ¿Cómo genero el reporte 911 para la SEP?

1. Acceda al módulo **Estadística 911** desde el menú lateral (sección Reportes Oficiales).
2. Verifique que tenga seleccionado el plantel y ciclo correctos en la barra superior.
3. Haga clic en **Generar**. Las matrices se calculan automáticamente.
4. Exporte cada nivel (Primaria y Secundaria) con el botón **"Exportar Excel"**.
5. Transcriba o importe los datos al sistema f911 oficial de la SEP.

> Recuerde: ADES no envía directamente a la SEP. Solo pre-calcula los datos.

---

### 34.8 ¿Qué hago si un alumno tiene un cambio de calificación ya cerrada?

Solo el administrador del sistema (niveles 0-1) puede reabrir una calificación cerrada. Para hacerlo:
1. El administrador accede al módulo de Calificaciones.
2. Localiza la calificación cerrada (marcada con candado).
3. Hace clic en **"Reabrir"** e ingresa una justificación de al menos 20 caracteres explicando el motivo del cambio.
4. La calificación queda disponible para edición por el docente durante un período limitado.

---

### 34.9 ¿Cómo verifico la autenticidad de un certificado digital?

Existen dos formas de verificar un certificado:
1. **Código QR:** Escanee el código QR impreso en el documento físico con cualquier aplicación de escaneo QR. Lo llevará directamente a la página de verificación.
2. **URL directa:** Vaya a `https://ades.setag.mx/verificar/[FOLIO]` sustituyendo `[FOLIO]` por el número de folio impreso en el certificado.

Si el certificado es auténtico, verá el mensaje "VERIFICADO — Firma válida". Si fue alterado, el sistema lo indicará.

---

### 34.10 ¿Puedo acceder al sistema desde un teléfono móvil o tablet?

Sí. ADES está diseñado con enfoque tablet-first y funciona en dispositivos móviles modernos. Se recomienda usar el navegador en orientación horizontal (paisaje) para una mejor experiencia en la visualización de tablas y gráficos. La resolución mínima recomendada es 768px de ancho.

---

## Contacto y Soporte

Para reportar problemas técnicos o solicitar capacitación sobre el sistema, comuníquese con el área de administración del Instituto Nevadi en el plantel correspondiente.

Para consultas sobre funcionalidades del sistema, también puede usar el **Asistente IA** integrado en el módulo de Inteligencia Artificial.

---

*Manual generado el 2026-06-23. ADES Instituto Nevadi — Sistema de Administración Escolar.*
*"EL ÚNICO CAMINO PARA SALIR ADELANTE ES LA EDUCACIÓN."*
