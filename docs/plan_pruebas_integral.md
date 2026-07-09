# Plan Integral de Pruebas — ADES Instituto Nevadi
**Versión:** 1.0 | **Fecha:** 2026-06-16 | **Sprint:** QA Pre-Producción

---

## Situación actual

### Pendiente de desarrollo (no bloquea QA)
| Ítem | Estado | Acción |
|------|--------|--------|
| Google Workspace SSO | Bloqueado — esperando credenciales Google Cloud de Nevadi | Admin externo |
| LAChain FASE 5B | Diferido a producción — LACCHAIN_RPC_URL=MOCK en DEV | No aplica QA ahora |
| H5P FASE 25 | No implementado (contenido educativo interactivo) | Backlog |
| BigBlueButton FASE 26 | No implementado (videoconferencias) | Backlog |
| Superset datasource + dashboards BI | Servicio activo, config manual pendiente | Setup independiente |
| Vault integración | Servicio activo, servicios siguen usando `.env` | Backlog |

### Todo lo demás: en QA scope

---

## Notas de arquitectura UI
- **19 componentes custom** de `apex-component-library` (InteractiveGrid, LOV, FileUpload, Chart, etc.)
- **Todo lo demás**: PrimeNG puro (`p-table`, `p-dialog`, `p-dropdown`, `p-select`, `p-toast`, etc.)
- No agregar wrappers APEX donde PrimeNG ya lo cubre

---

## Entorno de prueba

### Usuarios de prueba requeridos (crear en Authentik antes de iniciar)
| Rol | Nivel | Usuario sugerido | Plantel |
|-----|-------|------------------|---------|
| ADMIN_GLOBAL | 0 | `admin.global@test` | Todos |
| ADMIN_PLANTEL | 1 | `admin.metepec@test` | Metepec |
| DIRECTOR | 2 | `director.metepec@test` | Metepec |
| COORDINADOR_ACADEMICO | 3 | `coord.academico@test` | Metepec |
| DOCENTE | 4 | `docente.primaria@test` | Metepec |
| PADRE_FAMILIA | 5 | `padre.alumno@test` | — |
| ALUMNO | 5 | `alumno.sec@test` | — |

### Datos de referencia para pruebas
- Plantel: **Metepec** (datos más completos)
- Ciclo: **2026-2027** (SEP primaria/secundaria)
- Ciclo prep: **26B** (UAEMEX Metepec sem 1-4)
- Grupo de referencia: **Secundaria 1A Metepec**
- Alumno de referencia: cualquiera de `ades_inscripciones` activas

### Prerrequisitos técnicos
- [ ] Todos los contenedores healthy: `docker compose ps`
- [ ] Frontend accesible: `https://ades.setag.mx/`
- [ ] API accesible: `https://ades.setag.mx/api/v1/health`
- [ ] BFF accesible: `https://ades.setag.mx/bff/actuator/health`
- [ ] PostgreSQL con migraciones 001–078 aplicadas
- [ ] Herramienta REST: Bruno / Postman con `Authorization: Bearer <token>`

---

## Convenciones del plan

```
P1 = Bloqueante (producción imposible sin esto)
P2 = Crítico (funcionalidad core afectada)
P3 = Importante (flujo incompleto)
P4 = Cosmético / mejora

✅ = Pasa
❌ = Falla — abrir issue
⚠️ = Parcial / workaround
```

---

---

# MÓDULO 1 — Autenticación y Sesión

**Ruta:** `/login`, `/callback`
**Rol requerido:** Cualquiera

| ID | Caso de prueba | Pasos | Resultado esperado | P |
|----|---------------|-------|--------------------|---|
| AUTH-01 | Login con cuenta local | 1. Ir a `/login` 2. Ingresar credenciales locales 3. Submit | Redirige a `/dashboard`, token en sessionStorage como `ades_token` | P1 |
| AUTH-02 | Callback OIDC | Login exitoso → URL `/callback?code=...` | Token procesado, redirige a dashboard, `ades_token` presente | P1 |
| AUTH-03 | Sesión expirada | Esperar expiración del token → navegar a ruta protegida | Redirige automáticamente a login | P1 |
| AUTH-04 | Botón logout | Click en avatar → "Cerrar sesión" | Token eliminado, sesión Authentik cerrada, redirige a login | P1 |
| AUTH-05 | Acceso sin auth | URL directa a `/alumnos` sin sesión | Redirige a login | P1 |
| AUTH-06 | roleGuard — acceso denegado | Login como DOCENTE (nivel 4) → navegar a `/admin` (nivel 1) | Redirige a dashboard, no muestra contenido | P1 |
| AUTH-07 | Context selector activo | Login → selector plantel/ciclo en topbar | Selector visible con datos del plantel asignado | P2 |
| AUTH-08 | MFA para admin | Login como ADMIN_GLOBAL | Solicita TOTP 2FA de Authentik antes de dar acceso | P2 |

---

# MÓDULO 2 — Dashboard

**Ruta:** `/dashboard`
**Rol requerido:** Todos

| ID | Caso de prueba | Pasos | Resultado esperado | P |
|----|---------------|-------|--------------------|---|
| DASH-01 | Carga inicial | Login → dashboard | KPI cards visibles: total alumnos, asistencia media, grupos, alertas activas | P1 |
| DASH-02 | Filtro por plantel | Cambiar plantel en selector | KPIs se recalculan con datos del nuevo plantel | P1 |
| DASH-03 | Filtro por ciclo | Cambiar ciclo en selector | KPIs actualizados para ese ciclo | P1 |
| DASH-04 | Widget configuración | Click engranaje → toggle widgets | Widgets se ocultan/muestran según selección | P3 |
| DASH-05 | KPI drill-down | Click en tarjeta "Alumnos con riesgo" | Navega al módulo correspondiente con filtro aplicado | P3 |
| DASH-06 | Vista por rol | Login como PADRE_FAMILIA | Dashboard simplificado solo con datos de sus hijos | P2 |
| DASH-07 | Vista ADMIN_GLOBAL | Login como admin global | Selector sin restricción de plantel, datos de todos los planteles | P2 |

---

# MÓDULO 3 — Administración (Admin)

**Ruta:** `/admin` | **Rol:** ADMIN_PLANTEL (1) o superior

### 3.1 Usuarios
| ID | Caso de prueba | Pasos | Resultado esperado | P |
|----|---------------|-------|--------------------|---|
| ADM-01 | Crear usuario | Admin → tab Usuarios → Nuevo → llenar form → Guardar | Usuario creado, aparece en lista, `is_active=TRUE` | P1 |
| ADM-02 | Duplicado por email | Crear 2 usuarios con mismo email | Error: "Email ya registrado", no se crea el segundo | P1 |
| ADM-03 | Asignar rol | Editar usuario → cambiar rol | Rol actualizado, permisos aplicados en próximo login | P1 |
| ADM-04 | Asignar plantel | Editar usuario → asignar plantel | Usuario queda ligado al plantel, RBAC scope activo | P1 |
| ADM-05 | Desactivar usuario | Toggle `is_active` → desactivar | Usuario no puede iniciar sesión, pero registro conservado | P1 |
| ADM-06 | RBAC — asignar rol mayor | Coordinador intenta dar rol ADMIN | Sistema rechaza o no muestra esa opción | P1 |

### 3.2 Ciclos Escolares
| ID | Caso de prueba | Pasos | Resultado esperado | P |
|----|---------------|-------|--------------------|---|
| ADM-07 | Crear ciclo | Nuevo ciclo → nombre, nivel, fechas → Guardar | Ciclo aparece en selector global, `is_vigente=FALSE` por defecto | P1 |
| ADM-08 | Activar ciclo | Toggle vigente en ciclo | Solo un ciclo por nivel puede estar vigente simultáneamente | P1 |
| ADM-09 | Cerrar ciclo | Ciclo vigente → Cerrar | Llama a `cerrar_ciclo_y_promover()`, alumnos promovidos | P1 |
| ADM-10 | Periodos de evaluación | Ciclo → Periodos → Agregar | Periodos guardados con fechas correctas, validación solapamiento | P2 |

### 3.3 Parámetros del Sistema
| ID | Caso de prueba | Pasos | Resultado esperado | P |
|----|---------------|-------|--------------------|---|
| ADM-11 | Editar parámetro público | COLOR_PRIMARIO → cambiar hex | Cambio reflejado en UI sin reiniciar | P3 |
| ADM-12 | Parámetro privado | OPENAI_API_KEY → solo ADMIN_GLOBAL ve el campo | Coordinadores/docentes no ven el campo | P2 |
| ADM-13 | Logo institucional | Subir nuevo logo → Guardar | Logo actualizado en topbar y PDFs generados | P3 |

---

# MÓDULO 4 — Grupos

**Ruta:** `/grupos` | **Rol:** COORDINADOR_ACADEMICO (3) o superior

| ID | Caso de prueba | Pasos | Resultado esperado | P |
|----|---------------|-------|--------------------|---|
| GRP-01 | Listar grupos | Entrar a grupos | Tabla con grupos del plantel activo, filtro por nivel/grado | P1 |
| GRP-02 | Crear grupo | Nuevo → grado, nivel, letra (A/B), ciclo → Guardar | Grupo creado, aparece en lista | P1 |
| GRP-03 | Duplicado grado+letra | Crear grupo "1A Secundaria" cuando ya existe | Error: "Ya existe un grupo 1A para este grado y ciclo" | P1 |
| GRP-04 | Máximo 2 grupos | Intentar crear grupo "1C" (letra C) | Error: máximo 2 grupos por grado (A y B) | P2 |
| GRP-05 | Editar grupo | Click editar → cambiar aula asignada → Guardar | Aula actualizada, sin afectar inscripciones | P2 |
| GRP-06 | Filtro plantel | Cambiar plantel en selector | Lista muestra solo grupos de ese plantel | P1 |
| GRP-07 | Detalle grupo — alumnos | Click en grupo → tab alumnos | Lista de alumnos inscritos con foto/nombre | P2 |
| GRP-08 | Detalle grupo — materias | Tab materias del grupo | Materias asignadas con docente(s) y horario | P2 |

---

# MÓDULO 5 — Alumnos

**Ruta:** `/alumnos` | **Rol:** DOCENTE (4) o superior

| ID | Caso de prueba | Pasos | Resultado esperado | P |
|----|---------------|-------|--------------------|---|
| ALU-01 | Listar alumnos | Entrar al módulo | Tabla con búsqueda por nombre/matrícula, filtro por grupo | P1 |
| ALU-02 | Crear alumno | Nuevo → datos personales (CURP, nombre, fecha nacimiento) → Guardar | Alumno creado, matrícula asignada automáticamente | P1 |
| ALU-03 | CURP duplicado | Crear 2 alumnos con mismo CURP | Error: "CURP ya registrado en el sistema" | P1 |
| ALU-04 | Inscribir alumno | Alumno existente → Inscribir → seleccionar grupo/ciclo | Inscripción activa, aparece en lista del grupo | P1 |
| ALU-05 | Inscripción doble | Intentar inscribir al mismo alumno en dos grupos activos | Error: "Alumno ya inscrito en este ciclo" | P1 |
| ALU-06 | Editar datos | Click editar → cambiar domicilio → Guardar | Datos actualizados con `row_version` incrementado | P2 |
| ALU-07 | Detalle alumno | Click en alumno → panel master-detail | Pestaña: datos, calificaciones, asistencias, conducta, expediente | P2 |
| ALU-08 | Importar CSV | Botón Importar → subir CSV con alumnos | Alumnos creados masivamente, reporte de errores por fila | P2 |
| ALU-09 | Foto de perfil | Editar alumno → subir foto | Foto visible en lista y perfil, almacenada en MinIO | P3 |
| ALU-10 | Filtro RBAC plantel | Login como ADMIN_PLANTEL Tenancingo | Solo ve alumnos de Tenancingo, no puede ver Metepec | P1 |
| ALU-11 | Campos obligatorios | Submit form sin CURP | Mensaje inline "CURP es requerido", form no se envía | P1 |
| ALU-12 | CURP formato | CURP inválido (formato incorrecto) | Validación formato 18 caracteres alfanuméricos | P2 |

---

# MÓDULO 6 — Profesores

**Ruta:** `/profesores` | **Rol:** COORDINADOR_ACADEMICO (3) o superior

| ID | Caso de prueba | Pasos | Resultado esperado | P |
|----|---------------|-------|--------------------|---|
| PRO-01 | Listar profesores | Entrar al módulo | Tabla con nombre, número empleado, materias asignadas | P1 |
| PRO-02 | Crear profesor | Nuevo → RFC, número empleado, nombre → Guardar | Profesor creado con `is_active=TRUE` | P1 |
| PRO-03 | RFC duplicado | Crear 2 profesores con mismo RFC | Error: "RFC ya registrado" | P1 |
| PRO-04 | Asignar materia/grupo | Profesor → Asignaciones → seleccionar materia+grupo+ciclo | Asignación guardada, aparece en horarios | P1 |
| PRO-05 | Múltiples docentes misma materia | Asignar 2 docentes a Matemáticas 1A | Permitido: regla de negocio explícita | P1 |
| PRO-06 | Titular primaria | Profesor primaria → asignación cubre todas las materias | Un solo docente puede tener todas las materias del grupo | P2 |
| PRO-07 | Editar datos | Editar → cambiar teléfono → Guardar | Actualizado sin perder asignaciones | P2 |

---

# MÓDULO 7 — Aulas

**Ruta:** `/aulas` | **Rol:** COORDINADOR_ACADEMICO (3) o superior

| ID | Caso de prueba | Pasos | Resultado esperado | P |
|----|---------------|-------|--------------------|---|
| AUL-01 | Listar aulas | Entrar al módulo | Tabla con nombre, capacidad, equipamiento, plantel | P1 |
| AUL-02 | Crear aula | Nueva → nombre "Laboratorio 1", capacidad 30 → Guardar | Aula creada, disponible para asignación a grupos | P1 |
| AUL-03 | Nombre duplicado en mismo plantel | Crear 2 aulas "Salón 101" en Metepec | Error: "Nombre ya existe en este plantel" | P1 |
| AUL-04 | Capacidad negativa | Capacidad = -5 | Validación: "La capacidad debe ser mayor a 0" | P2 |
| AUL-05 | Asignar a grupo | Editar grupo → seleccionar aula del LOV | Aula vinculada al grupo | P2 |

---

# MÓDULO 8 — Planes de Estudio

**Ruta:** `/planes-estudio` | **Rol:** COORDINADOR_ACADEMICO (3) o superior

| ID | Caso de prueba | Pasos | Resultado esperado | P |
|----|---------------|-------|--------------------|---|
| PLA-01 | Ver mapa curricular | Entrar al módulo → seleccionar nivel | Tabla grado × materia con créditos/horas | P1 |
| PLA-02 | Agregar materia al plan | Nueva materia → nombre, horas semanales, créditos → Guardar | Materia aparece en el mapa curricular | P2 |
| PLA-03 | Editar materia | Editar → cambiar nombre → Guardar | Nombre actualizado, sin romper asignaciones existentes | P2 |
| PLA-04 | Ver temario | Click en materia → tab Temario | Lista de temas con descripción y bibliografía | P2 |
| PLA-05 | Agregar tema al temario | Temario → Nuevo tema → Guardar | Tema ordenado numéricamente en la lista | P2 |
| PLA-06 | Optativas | Ir a `/optativas` → listar | Materias optativas disponibles por nivel | P3 |

---

# MÓDULO 9 — Calendario

**Ruta:** `/calendario` | **Rol:** COORDINADOR_ACADEMICO (3) o superior

| ID | Caso de prueba | Pasos | Resultado esperado | P |
|----|---------------|-------|--------------------|---|
| CAL-01 | Ver calendario | Entrar → vista mes | Eventos SEP/UAEMEX del ciclo activo | P2 |
| CAL-02 | Agregar evento | Nuevo evento → nombre, fecha, tipo → Guardar | Evento aparece en el calendario | P2 |
| CAL-03 | Filtro por nivel | Toggle Primaria / Secundaria / Preparatoria | Solo muestra eventos del nivel seleccionado | P2 |

---

# MÓDULO 10 — Asistencias

**Ruta:** `/asistencias` | **Rol:** DOCENTE (4) o superior

| ID | Caso de prueba | Pasos | Resultado esperado | P |
|----|---------------|-------|--------------------|---|
| ASI-01 | Seleccionar grupo/fecha | Entrar → elegir grupo → fecha de hoy | Lista de alumnos del grupo con estado por defecto PRESENTE | P1 |
| ASI-02 | Cambiar estado | Click en alumno → toggle PRESENTE→AUSENTE | Estado actualizado inline, sin reload | P1 |
| ASI-03 | Estado TARDE | Click → TARDE | Se registra correctamente (no TARDANZA) | P1 |
| ASI-04 | Estado JUSTIFICADO | Click → JUSTIFICADO | Requiere o deja campo de justificación | P2 |
| ASI-05 | Guardar asistencia | Click "Guardar" con todos los estados | Insert en `ades_asistencias`, `audit_biu` dispara `row_version` | P1 |
| ASI-06 | Doble registro mismo día | Intentar registrar asistencia ya guardada | Sistema muestra registro existente (modo edición) o error de duplicado | P1 |
| ASI-07 | Alerta 85% | Alumno con < 85% asistencia acumulada | Badge de alerta visible en su fila, notificación ntfy disparada | P2 |
| ASI-08 | Bulk save | Registrar 30 alumnos → un solo click Guardar | Un solo POST con array, no 30 peticiones individuales | P1 |
| ASI-09 | Fecha futura | Intentar registrar asistencia en fecha futura | Error de validación: "No se puede registrar asistencia para fechas futuras" | P2 |
| ASI-10 | RBAC scope plantel | Docente de Metepec intenta ver grupo de Tenancingo | 403 / redirige a selección de grupos válidos | P1 |

---

# MÓDULO 11 — Calificaciones

**Ruta:** `/calificaciones` | **Rol:** DOCENTE (4) o superior

| ID | Caso de prueba | Pasos | Resultado esperado | P |
|----|---------------|-------|--------------------|---|
| CAL-01 | Captura por periodo | Seleccionar grupo+materia+periodo → tabla alumnos | Tabla editable con campo calificación por alumno | P1 |
| CAL-02 | Validación escala SEP | Ingresar 11.0 en secundaria | Error: "Calificación máxima es 10.0" | P1 |
| CAL-03 | Validación escala UAEMEX | Ingresar 105 en preparatoria | Error: "Calificación máxima es 100" | P1 |
| CAL-04 | Guardar bulk | Llenar todas las calificaciones → Guardar | Un solo POST con array, respuesta con conteo guardados | P1 |
| CAL-05 | Calificación reprobatoria | Ingresar 5.0 (SEP) / 55 (UAEMEX) | Registro guardado + alerta visual en fila + ntfy a padre | P1 |
| CAL-06 | Cerrar calificación | Coordinador → Cerrar periodo | Calificaciones se marcan `cerrada=TRUE`, campo editable deshabilitado | P1 |
| CAL-07 | Edición post-cierre — docente | Docente intenta editar calificación cerrada | Campo deshabilitado o error 403 | P1 |
| CAL-08 | Ajuste manual — admin | Admin edita calificación cerrada | Requiere justificación ≥ 20 caracteres, guarda con `usuario_id` | P1 |
| CAL-09 | Ajuste sin justificación | Admin guarda ajuste con texto de 15 chars | Error: "La justificación debe tener al menos 20 caracteres" | P1 |
| CAL-10 | Calificación extraordinario | SEP: rango 0-10; UAEMEX: 0-100 | Mismo rango que ordinario, marcado como tipo EXTRAORDINARIO | P2 |

---

# MÓDULO 12 — Gradebook

**Ruta:** `/gradebook` | **Rol:** DOCENTE (4) o superior

| ID | Caso de prueba | Pasos | Resultado esperado | P |
|----|---------------|-------|--------------------|---|
| GRB-01 | Carga panel spreadsheet | Seleccionar grupo+materia | Tabla estilo spreadsheet con columnas: alumno, tareas, evaluaciones, parcial calculado | P1 |
| GRB-02 | Calificación masiva inline | Editar celda de tarea directamente | Campo inline editable, guarda al salir del campo | P1 |
| GRB-03 | Cálculo automático | Cambiar calificación de tarea → parcial recalculado | Trigger `calcular_calificacion_periodo` actualiza parcial en ≤ 2s | P1 |
| GRB-04 | Esquema ponderación activo | Parcial = 70% tareas + 30% evaluaciones | Fórmula respeta el esquema vigente para esa materia/nivel | P1 |
| GRB-05 | Cerrar calificación | Coordinador → botón "Cerrar periodo" | Fila queda en solo lectura para docentes | P1 |
| GRB-06 | Ajuste manual con justificación | Admin → editar parcial cerrado → escribir justificación | Guarda con `justificacion_ajuste` >= 20 chars | P1 |
| GRB-07 | Ajuste sin justificación suficiente | 15 chars en justificación | Validación inline: "Mínimo 20 caracteres" | P1 |
| GRB-08 | Optimistic locking | Dos usuarios editan misma celda simultáneamente | El segundo recibe 409 Conflict con mensaje claro | P2 |
| GRB-09 | Exportar XLSX | Botón exportar → descarga | XLSX con encabezado rojo Nevadi, columnas correctas | P3 |

---

# MÓDULO 13 — Ponderación Config

**Ruta:** `/ponderacion-config` | **Rol:** COORDINADOR_ACADEMICO (3) o superior

| ID | Caso de prueba | Pasos | Resultado esperado | P |
|----|---------------|-------|--------------------|---|
| PON-01 | Ver esquemas | Entrar → lista de esquemas | Esquemas por nivel con suma de porcentajes | P1 |
| PON-02 | Crear esquema | Nuevo → nombre, nivel, ítems (tareas 70% + eval 30%) → Guardar | Esquema creado, suma validada = 100% | P1 |
| PON-03 | Suma != 100% | Ingresar tareas 70% + eval 20% | Error: "La suma de porcentajes debe ser 100%" | P1 |
| PON-04 | Esquema específico por materia | Crear esquema para "Matemáticas" nivel Secundaria | Tiene prioridad sobre el genérico del nivel | P1 |
| PON-05 | Editar esquema activo | Cambiar porcentajes → Guardar | Actualizado, trigger recalcula calificaciones de ese periodo | P2 |

---

# MÓDULO 14 — Evaluaciones

**Ruta:** `/evaluaciones` | **Rol:** DOCENTE (4) o superior

| ID | Caso de prueba | Pasos | Resultado esperado | P |
|----|---------------|-------|--------------------|---|
| EVA-01 | Crear evaluación | Nueva → nombre, fecha, tipo (ORDINARIA/FINAL/EXTRAORDINARIO) → Guardar | Evaluación creada y vinculada al grupo | P1 |
| EVA-02 | Fecha obligatoria | Submit sin fecha | Error: "La fecha de evaluación es requerida" | P1 |
| EVA-03 | Asignar aula y hora | Evaluación → Asignar aula → seleccionar slot horario | `AsignarAulaHoraUseCase` valida conflictos y guarda | P2 |
| EVA-04 | Conflicto horario | Asignar aula ya ocupada en ese slot | Error: "El aula ya está ocupada en ese horario" | P2 |
| EVA-05 | Calificar masivo | Evaluación → tab Calificar → tabla de alumnos con notas | Guardado bulk en un click | P1 |
| EVA-06 | Rango calificación | Nota fuera del rango del nivel | Error de validación con escala correcta | P1 |

---

# MÓDULO 15 — Tareas y Entregas

**Ruta:** `/tareas` | **Rol:** DOCENTE (4) o superior

| ID | Caso de prueba | Pasos | Resultado esperado | P |
|----|---------------|-------|--------------------|---|
| TAR-01 | Listar tareas | Seleccionar grupo+materia | Tareas del ciclo con estado (sin entrega, entregada, calificada) | P1 |
| TAR-02 | Crear tarea | Nueva → título, descripción, fecha límite, puntaje máx | Tarea creada, aparece en Mi Progreso del alumno | P1 |
| TAR-03 | Ver entregas | Click en tarea → tab Entregas | Lista de alumnos con su entrega/estado | P1 |
| TAR-04 | Calificar entrega | Puntaje → Guardar | Calificación guardada, trigger actualiza gradebook | P1 |
| TAR-05 | Excusa | Alumno sin entrega → "Registrar excusa" | Estado pasa a EXCUSA, no penaliza promedio | P2 |
| TAR-06 | Archivo no subido — alumno | Entrega sin archivo | Estado SIN_ENTREGA, no se muestra error de archivo null | P2 |
| TAR-07 | Fecha límite pasada | Alumno sube archivo después de la fecha | Sistema lo acepta con marca TARDÍA o rechaza según regla | P2 |

---

# MÓDULO 16 — Planeación de Clases

**Ruta:** `/planeacion` | **Rol:** DOCENTE (4) o superior

| ID | Caso de prueba | Pasos | Resultado esperado | P |
|----|---------------|-------|--------------------|---|
| PLAN-01 | Ver planeaciones | Seleccionar grupo+materia → lista de planes de clase | Planes ordenados por fecha, con tema y objetivo | P1 |
| PLAN-02 | Crear plan de clase | Nuevo → semana, tema, objetivo, estrategias → Guardar | Plan creado sin validación de unicidad de semana (múltiples por semana posibles) | P1 |
| PLAN-03 | Revisar planeación | Coordinador → ver plan de docente → comentar | Comentario guardado, docente notificado | P2 |
| PLAN-04 | Tema vinculado al temario | Seleccionar tema del LOV de temario | Tema referenciado correctamente en la BD | P2 |

---

# MÓDULO 17 — Horarios

**Ruta:** `/horarios` | **Rol:** DOCENTE (4) o superior

| ID | Caso de prueba | Pasos | Resultado esperado | P |
|----|---------------|-------|--------------------|---|
| HOR-01 | Ver horario de grupo | Seleccionar grupo → vista semana | Tabla lunes-viernes × horas con materia/docente/aula | P1 |
| HOR-02 | Exportar para aSc | Admin → Exportar XML | XML válido descargado con estructura aSc TimeTables | P2 |
| HOR-03 | Importar XML aSc | Subir XML de aSc → importar | Horarios importados, conflictos reportados | P2 |
| HOR-04 | Conflicto de aula | Asignar misma aula en mismo slot a dos grupos | Error: "Aula ocupada en ese horario" | P2 |
| HOR-05 | Conflicto de docente | Mismo docente en dos grupos mismo slot | Error: "Docente no disponible en ese horario" | P2 |
| HOR-06 | Ver mi horario — docente | Login como docente | Vista personal de su horario semanal | P2 |

---

# MÓDULO 18 — Conducta

**Ruta:** `/conducta` | **Rol:** PREFECTO (4) o superior

| ID | Caso de prueba | Pasos | Resultado esperado | P |
|----|---------------|-------|--------------------|---|
| CON-01 | Aplicar sanción | Seleccionar alumno → tipo sanción → descripción → Guardar | Sanción registrada con `fecha`, `usuario_id`, `plantel_id` | P1 |
| CON-02 | Descripción obligatoria | Guardar sin descripción | Error: "La descripción es requerida" | P1 |
| CON-03 | Plan de mejora | Sanción grave → Crear plan de mejora → seguimientos | Plan con estado ACTIVO, seguimientos múltiples | P2 |
| CON-04 | Cerrar plan de mejora | Director → Cerrar plan → estado CERRADO | Estado no permite nuevo seguimiento después de cerrar | P2 |
| CON-05 | Historial por alumno | Alumno → tab Conducta | Lista de sanciones y planes ordenados cronológicamente | P2 |
| CON-06 | RBAC — leer sanciones | Docente intenta listar sanciones de otro plantel | 403 o lista vacía | P1 |

---

# MÓDULO 19 — Salud / Médico

**Ruta:** `/medico` | **Rol:** MEDICO_ESCOLAR (4) o superior

### 19.1 Expediente Médico
| ID | Caso de prueba | Pasos | Resultado esperado | P |
|----|---------------|-------|--------------------|---|
| MED-01 | Ver expediente | Seleccionar alumno | Panel con alergias, condiciones, medicamentos activos | P1 |
| MED-02 | Registrar medicamento | Nuevo → nombre, dosis, horario, prescrito por → Guardar | Medicamento activo en expediente | P1 |
| MED-03 | Suspender medicamento | Medicamento activo → Suspender → fecha fin | Estado INACTIVO, fecha fin guardada | P2 |
| MED-04 | Generar acta médica | Incidente → Generar Acta → PDF | PDF generado con Carbone, descargable | P2 |
| MED-05 | Certificado aptitud física | Alumno sano → Generar certificado → PDF | Certificado firmado por médico, descargable | P3 |

### 19.2 Condiciones Crónicas
| ID | Caso de prueba | Pasos | Resultado esperado | P |
|----|---------------|-------|--------------------|---|
| MED-06 | Ruta `/condiciones-cronicas` | Entrar al módulo | Lista de alumnos con condiciones activas | P1 |
| MED-07 | Registrar condición | Alumno → Nueva condición → tipo (ALERGIA/CRONICA) → Guardar | Condición activa, alerta en perfil del alumno | P1 |
| MED-08 | Duplicado | Registrar misma condición dos veces para un alumno | Error o permite múltiples (verificar regla de negocio) | P2 |
| MED-09 | Alerta en UI | Dashboard → alumno con condición crónica | Badge de alerta visible para docente y director | P2 |

---

# MÓDULO 20 — Expediente Digital (Documentos)

**Ruta:** `/expediente-doc` | **Rol:** COORDINADOR_ACADEMICO (3) o superior

| ID | Caso de prueba | Pasos | Resultado esperado | P |
|----|---------------|-------|--------------------|---|
| EXP-01 | Ver expediente alumno | Seleccionar alumno | Barra de progreso, lista de documentos requeridos/cargados | P1 |
| EXP-02 | Subir documento | Click "Subir" → seleccionar PDF → confirmar | Upload a MinIO/SeaweedFS, estado OCR = PENDIENTE | P1 |
| EXP-03 | Estado OCR en tiempo real | Esperar 30-60s tras subida | Estado cambia PENDIENTE→PROCESANDO→COMPLETADO (polling) | P1 |
| EXP-04 | Buscar por contenido OCR | Barra de búsqueda → "CURP" | Documentos con esa palabra en el texto OCR | P2 |
| EXP-05 | Ver documento | Click en documento completado | Visor PDF inline o descarga | P2 |
| EXP-06 | Validar con IA | Botón "Analizar con IA" | IA lista inconsistencias/documentos faltantes | P3 |
| EXP-07 | Tipo de archivo inválido | Subir .exe | Error: "Solo se permiten PDF e imágenes" | P2 |
| EXP-08 | Tamaño máximo | Subir PDF > 20MB | Error: "El archivo supera el límite de 20 MB" | P2 |

---

# MÓDULO 21 — Evaluación Docente 360°

**Ruta:** `/eval-docente` | **Rol:** COORDINADOR_ACADEMICO (3) o superior

| ID | Caso de prueba | Pasos | Resultado esperado | P |
|----|---------------|-------|--------------------|---|
| ED-01 | Crear evaluación | Nueva → docente, evaluador, tipo (PARES/ALUMNOS/DIRECTOR) → Guardar | Evaluación en estado BORRADOR | P1 |
| ED-02 | Guardar criterios | Evaluación → criterios → calificar cada uno → Guardar | Criterios guardados, promedio calculado | P1 |
| ED-03 | Enviar evaluación | Director → Enviar → estado ENVIADA | Estado cambia, docente puede ver sus resultados | P2 |
| ED-04 | Criterios sin calificar | Enviar evaluación con criterio vacío | Error: "Todos los criterios deben estar calificados" | P2 |
| ED-05 | Vista docente | Login como docente → ver resultados de sus evaluaciones | Solo ve sus propias evaluaciones, no las de otros | P2 |

---

# MÓDULO 22 — Rúbricas

**Ruta:** `/rubricas` | **Rol:** DOCENTE (4) o superior

| ID | Caso de prueba | Pasos | Resultado esperado | P |
|----|---------------|-------|--------------------|---|
| RUB-01 | Listar rúbricas | Entrar al módulo | Rúbricas disponibles con filtro por materia | P1 |
| RUB-02 | Crear rúbrica | Nueva → nombre, criterios con puntaje → Guardar | Rúbrica creada y vinculable a tareas/evaluaciones | P1 |
| RUB-03 | Criterios vacíos | Guardar rúbrica sin criterios | Error: "Agrega al menos un criterio" | P2 |
| RUB-04 | Editar criterio | Editar rúbrica existente → cambiar puntaje máx | Actualizado correctamente | P2 |

---

# MÓDULO 23 — Reinscripción

**Ruta:** `/reinscripcion` | **Rol:** COORDINADOR_ACADEMICO (3) o superior

| ID | Caso de prueba | Pasos | Resultado esperado | P |
|----|---------------|-------|--------------------|---|
| REI-01 | Ver alumnos para reinscripción | Seleccionar ciclo destino | Lista con alumnos APROBADOS listos para promover | P1 |
| REI-02 | Promover alumno | Click Promover en alumno APROBADO | Alumno inscrito en siguiente grado, ciclo destino | P1 |
| REI-03 | Rechazar reinscripción | Click Rechazar → ingresar razón | Requiere razón escrita, estado queda RECHAZADO | P1 |
| REI-04 | Rechazar sin razón | Submit rechazo sin escribir motivo | Error: "`AccionReinscripcion.RECHAZAR.requiereRazon()` = true" | P1 |
| REI-05 | BAJA no se reinscribe | Alumno con estado BAJA aparece en lista | No aparece en lista reinscripción o bloqueado | P1 |
| REI-06 | REPROBADO mismo grado | Alumno reprobado → Procesar | Queda inscrito en el mismo grado, no asciende | P1 |
| REI-07 | EGRESADO no se reinscribe | Alumno EGRESADO | No aparece en lista de reinscripción | P1 |

---

# MÓDULO 24 — Movilidad

**Ruta:** `/movilidad` | **Rol:** COORDINADOR_ACADEMICO (3) o superior

| ID | Caso de prueba | Pasos | Resultado esperado | P |
|----|---------------|-------|--------------------|---|
| MOV-01 | Cambio de grupo | Alumno → Cambio grupo → grupo destino → Guardar | `RegistrarCambioGrupoUseCase` ejecutado, alumno en nuevo grupo | P1 |
| MOV-02 | Baja temporal | Alumno → Baja temporal → motivo + fecha estimada regreso | Estado BAJA_TEMPORAL, `BajaRegistradaEvent` publicado | P1 |
| MOV-03 | Reactivar baja temporal | Alumno BAJA_TEMPORAL → Reactivar | Estado regresa a ACTIVO, `cerrarBajaTemporal` ejecutado | P1 |
| MOV-04 | Baja definitiva | Alumno → Baja definitiva → tipo (DESERCION/TRASLADO) | Alumno `is_active=FALSE`, no se reinscribe | P1 |
| MOV-05 | DESERCION desactiva alumno | `TipoBaja.DEFINITIVA.desactivaEstudiante()` = true | Verificar que el alumno queda inactivo en BD | P1 |

---

# MÓDULO 25 — Justificaciones

**Ruta:** `/justificaciones` | **Rol:** COORDINADOR_ACADEMICO (3) o superior

| ID | Caso de prueba | Pasos | Resultado esperado | P |
|----|---------------|-------|--------------------|---|
| JUS-01 | Crear justificación | Alumno → Nueva → tipo (MEDICA/FAMILIAR) → fechas → Guardar | Justificación en estado PENDIENTE | P1 |
| JUS-02 | Aprobar justificación | Director → Aprobar | Estado APROBADA, asistencia recalculada automáticamente | P1 |
| JUS-03 | Rechazar justificación | Director → Rechazar → motivo | Estado RECHAZADA, asistencia sin cambio | P1 |
| JUS-04 | Aprobar sin ser director | Coordinador intenta aprobar | 403 o botón no visible | P1 |
| JUS-05 | Recálculo asistencia | Aprobar jus. de 3 días → verificar % asistencia | Porcentaje sube correctamente, alerta de 85% puede desaparecer | P1 |
| JUS-06 | Fechas solapadas | Dos justificaciones para el mismo alumno en fechas coincidentes | Advertencia o error de solapamiento | P2 |

---

# MÓDULO 26 — Admisión

**Ruta:** `/admision` | **Rol:** COORDINADOR_ACADEMICO (3) o superior

| ID | Caso de prueba | Pasos | Resultado esperado | P |
|----|---------------|-------|--------------------|---|
| ADM-P01 | Registrar aspirante | Nuevo aspirante → datos → Guardar | Aspirante en estado PREINSCRITO | P1 |
| ADM-P02 | Evaluación diagnóstica | Aspirante → Registrar examen → puntaje → Guardar | Resultado guardado, línea de tiempo actualizada | P2 |
| ADM-P03 | Carta de aceptación | Aspirante ACEPTADO → Generar carta | PDF con nombre del aspirante, descargable | P2 |
| ADM-P04 | Carta de rechazo | Aspirante RECHAZADO → Generar carta | PDF de rechazo generado | P2 |
| ADM-P05 | Solo ACEPTADO se inscribe | `EstadoAdmision.permitePreinscripcion()` = solo ACEPTADO | Botón "Inscribir" solo activo si estado = ACEPTADO | P1 |
| ADM-P06 | Matrícula cívica | Alumno inscrito → Asignar matrícula cívica | Matrícula generada única, sin duplicados | P2 |
| ADM-P07 | Credencial QR | Alumno activo → Generar credencial | PDF con foto, datos y QR de verificación | P3 |

---

# MÓDULO 27 — Cierre de Ciclo

**Ruta:** `/cierre-ciclo` | **Rol:** DIRECTOR (2) o superior

| ID | Caso de prueba | Pasos | Resultado esperado | P |
|----|---------------|-------|--------------------|---|
| CIC-01 | Pre-validación | Entrar → panel de estado del ciclo | Muestra: grupos sin calificaciones cerradas, alumnos pendientes | P1 |
| CIC-02 | Cerrar ciclo | Confirmar cierre → ejecutar | `CerrarCicloUseCase` valida estado ABIERTO, cambia a CERRADO | P1 |
| CIC-03 | Ciclo ya cerrado | Intentar cerrar un ciclo ya CERRADO | Error: "El ciclo ya fue cerrado" | P1 |
| CIC-04 | RBAC — docente intenta cerrar | Login docente → acceder a `/cierre-ciclo` | 403 / redirigido | P1 |
| CIC-05 | Calificaciones pendientes | Ciclo con grupos sin calificaciones completas | Advertencia antes de confirmar el cierre | P2 |

---

# MÓDULO 28 — Learning Paths

**Ruta:** `/learning-paths` | **Rol:** Todos (lectura alumno, asignación coord.)

| ID | Caso de prueba | Pasos | Resultado esperado | P |
|----|---------------|-------|--------------------|---|
| LP-01 | Ver rutas asignadas | Login como alumno | Lista de recursos asignados con % avance | P1 |
| LP-02 | Asignar ruta a alumno en riesgo | Coordinador → alumno con alerta → asignar learning path | Ruta asignada, alumno la ve en su panel | P2 |
| LP-03 | Marcar progreso | Alumno → recurso → "Marcar completado" | Progreso actualizado, `EstatusAsignacion` cambia | P2 |
| LP-04 | Recomendación IA | Panel IA → sugerencias automáticas por bajo rendimiento | IA recomienda recursos según historial | P3 |

---

# MÓDULO 29 — Asistente IA

**Ruta:** `/ia` | **Rol:** COORDINADOR_ACADEMICO (3) o superior

| ID | Caso de prueba | Pasos | Resultado esperado | P |
|----|---------------|-------|--------------------|---|
| IA-01 | Chat básico | Escribir pregunta → Enter | Respuesta de Claude API en < 30s | P1 |
| IA-02 | NL→SQL | Pregunta: "¿Cuántos alumnos reprobaron este bimestre?" | Genera SQL, lo ejecuta, muestra tabla de resultados | P2 |
| IA-03 | Historial — guardar | Enviar 3 mensajes → cerrar → reabrir | Panel "Sesiones guardadas" muestra la sesión anterior | P2 |
| IA-04 | Cargar sesión anterior | Click en sesión guardada | Chat cargado con el contexto completo previo | P2 |
| IA-05 | Eliminar sesión | Click eliminar en sesión | Sesión eliminada, confirmación antes de borrar | P2 |
| IA-06 | Respeto de scope | Pregunta sobre alumnos de otro plantel | IA filtra o rechaza según scope RBAC del usuario | P2 |
| IA-07 | Conversación NVIDIA NIM | Verificar que el backend usa `OPENAI_BASE_URL=integrate.api.nvidia.com` | Respuestas correctas y dentro del límite de tokens | P2 |

---

# MÓDULO 30 — Grade Analytics

**Ruta:** `/grade-analytics` | **Rol:** COORDINADOR_ACADEMICO (3) o superior

| ID | Caso de prueba | Pasos | Resultado esperado | P |
|----|---------------|-------|--------------------|---|
| GA-01 | Carga de dashboards | Entrar al módulo | Gráficas de distribución de calificaciones cargadas | P2 |
| GA-02 | Filtro por grupo | Seleccionar grupo → actualizar gráficas | Datos filtrados por el grupo seleccionado | P2 |
| GA-03 | Tendencia bimestral | Gráfica de evolución del grupo | Línea de tendencia por bimestre | P2 |
| GA-04 | Alumnos en riesgo | Panel de alertas académicas activas | Lista con alumnos < 70% en alguna materia | P2 |

---

# MÓDULO 31 — Badges / Insignias

**Ruta:** `/badges` | **Rol:** Todos (visualización), COORDINADOR (3) para otorgar

| ID | Caso de prueba | Pasos | Resultado esperado | P |
|----|---------------|-------|--------------------|---|
| BAD-01 | Ver badges disponibles | Entrar al módulo | Catálogo de 8+ badges con criterios | P2 |
| BAD-02 | Auto-evaluar badges | Trigger automático o botón "Evaluar" | `AutoEvaluarBadgesUseCase` verifica criterios y otorga | P2 |
| BAD-03 | Badge ya otorgado | Intentar otorgar badge que ya tiene el alumno | No se duplica, se muestra como ya obtenido | P2 |
| BAD-04 | Vista alumno | Login alumno → sus badges | Muestra sus insignias con fecha de obtención | P3 |

---

# MÓDULO 32 — Encuestas

**Ruta:** `/encuestas` | **Rol:** COORDINADOR (3) para crear, todos para responder

| ID | Caso de prueba | Pasos | Resultado esperado | P |
|----|---------------|-------|--------------------|---|
| ENC-01 | Crear encuesta | Nueva → título, preguntas (OPCION_MULTIPLE, TEXTO_LIBRE) → Publicar | Encuesta visible para los destinatarios | P2 |
| ENC-02 | Responder encuesta | Alumno → responder → Enviar | Respuestas guardadas, encuesta marcada como completada | P2 |
| ENC-03 | Responder dos veces | Alumno intenta responder encuesta ya enviada | Formulario en modo solo-lectura o error | P2 |
| ENC-04 | Ver resultados | Coordinador → tab Resultados | Gráficas de distribución de respuestas | P3 |
| ENC-05 | Scan bullying | Encuesta de clima escolar → análisis semántico IA | Palabras clave detectadas, alerta al orientador | P3 |

---

# MÓDULO 33 — Foros

**Ruta:** `/foros` | **Rol:** Todos

| ID | Caso de prueba | Pasos | Resultado esperado | P |
|----|---------------|-------|--------------------|---|
| FOR-01 | Listar foros | Entrar al módulo | Foros de materias + foro privado padres | P2 |
| FOR-02 | Crear thread | Nuevo thread → título + mensaje → Publicar | Thread visible en el foro | P2 |
| FOR-03 | Responder thread | Click en thread → responder | Respuesta anidada visible | P2 |
| FOR-04 | Moderar contenido | Coordinador → ocultar respuesta inapropiada | Respuesta marcada como OCULTA, no visible para otros | P2 |
| FOR-05 | Foro privado padres | Login padre → foro padres | Solo padres y directivos pueden ver/escribir | P2 |

---

# MÓDULO 34 — Comunicados

**Ruta:** `/comunicados` | **Rol:** Todos (lectura), COORDINADOR (3) para crear

| ID | Caso de prueba | Pasos | Resultado esperado | P |
|----|---------------|-------|--------------------|---|
| COM-01 | Ver comunicados | Entrar al módulo | Lista de comunicados activos con fecha y autor | P1 |
| COM-02 | Crear comunicado | Nuevo → título, cuerpo, destinatarios, periodicidad → Enviar | Comunicado enviado, notificación ntfy disparada | P1 |
| COM-03 | Acuse de recibo | Destinatario → "Acusar recibido" | Registro de acuse con `usuario_id` + timestamp | P2 |
| COM-04 | Periodicidad SEMANAL | Comunicado con periodicidad SEMANAL | `ProgramarSiguienteUseCase` calcula fecha siguiente = +7 días | P2 |
| COM-05 | Comunicado periódico activo | No volver a programar si ya tiene uno pendiente | No duplica el siguiente programado | P2 |

---

# MÓDULO 35 — Certificados Digitales

**Ruta:** `/certificados` | **Rol:** DIRECTOR (2) o superior para emitir

| ID | Caso de prueba | Pasos | Resultado esperado | P |
|----|---------------|-------|--------------------|---|
| CER-01 | Emitir certificado | Alumno EGRESADO → Emitir → Guardar | Certificado con folio único, estado = EMITIDO | P1 |
| CER-02 | Firmar certificado | Certificado EMITIDO → Firmar | Estado = FIRMADO, `firma_ed25519` y `hash_sha256` generados | P1 |
| CER-03 | PDF con QR | Descargar PDF del certificado firmado | QR visible en el PDF, apunta a `/verificar/{folio}` | P1 |
| CER-04 | Verificación pública | Abrir `/verificar/{folio}` sin login | Página pública muestra estado: VÁLIDO o INVÁLIDO | P1 |
| CER-05 | PDF alterado | Modificar bytes del PDF y volver a verificar | Estado cambia a INVÁLIDO o FIRMA_INVALIDA | P1 |
| CER-06 | `esFirmado()` | Verificar que solo certificados firmados son verificables | `EstadoCertificado.esVerificable()` = true solo para FIRMADO | P1 |
| CER-07 | Emisión sin ser director | Coordinador intenta emitir | 403 / botón no visible | P1 |

---

# MÓDULO 36 — RRHH — Licencias

**Ruta:** `/licencias` | **Rol:** COORDINADOR_RH (2) o superior

| ID | Caso de prueba | Pasos | Resultado esperado | P |
|----|---------------|-------|--------------------|---|
| LIC-01 | Crear solicitud | Personal → Nueva licencia → tipo, fechas, adjunto → Guardar | Licencia en estado PENDIENTE | P1 |
| LIC-02 | Aprobar licencia | Coordinador RH → Aprobar | Estado APROBADA, días hábiles calculados (Lun-Vie) | P1 |
| LIC-03 | Rechazar licencia | Coordinador RH → Rechazar + motivo | Estado RECHAZADA | P1 |
| LIC-04 | Cálculo días hábiles | Licencia fin de semana incluido | `DiasHabiles` excluye sábado-domingo, calcula correctamente | P1 |
| LIC-05 | Fechas invertidas | Fecha inicio > fecha fin | Error: "La fecha fin debe ser posterior a la fecha inicio" | P2 |
| LIC-06 | Tipo MEDICA | Licencia médica → adjuntar incapacidad | Campo adjunto disponible | P2 |

---

# MÓDULO 37 — RRHH — Capacitaciones

**Ruta:** `/capacitaciones` | **Rol:** COORDINADOR_RH (2) o superior

| ID | Caso de prueba | Pasos | Resultado esperado | P |
|----|---------------|-------|--------------------|---|
| CAP-01 | Registrar capacitación | Personal → Nueva → nombre, horas, modalidad, fecha → Guardar | Capacitación registrada en expediente laboral | P2 |
| CAP-02 | Horas negativas | Horas = -5 | Validación: "Las horas deben ser positivas" | P2 |
| CAP-03 | Ver horas acumuladas | Perfil personal → total horas capacitación | Suma de horas de todas las capacitaciones activas | P2 |

---

# MÓDULO 38 — RRHH — Personal Administrativo

**Ruta:** `/personal-admin` | **Rol:** COORDINADOR_RH (2) o superior

| ID | Caso de prueba | Pasos | Resultado esperado | P |
|----|---------------|-------|--------------------|---|
| PA-01 | Listar personal | Entrar al módulo | Tabla de personal no-docente con filtro por plantel | P2 |
| PA-02 | Registrar personal | Nuevo → nombre, rol (SECRETARIA/PREFECTO/INTENDENCIA/OTRO) → Guardar | Personal registrado con número de empleado | P2 |
| PA-03 | Rol desconocido | Seleccionar tipo no listado | `TipoRolPersonal.of()` retorna OTRO, guardado sin error | P2 |

---

# MÓDULO 39 — RRHH — Expediente Laboral

**Ruta:** `/expediente-laboral` | **Rol:** COORDINADOR_RH (2), `nivel_acceso <= 2`

| ID | Caso de prueba | Pasos | Resultado esperado | P |
|----|---------------|-------|--------------------|---|
| EL-01 | Ver expediente | Seleccionar empleado | Contratos, documentos, nivel de estudios | P1 |
| EL-02 | Agregar documento | Subir contrato PDF | Documento en JSONB, sin cambiar la tabla principal | P1 |
| EL-03 | RBAC nivel 3+ | Coordinador Académico intenta ver expediente laboral | 403 / no visible en menú | P1 |

---

# MÓDULO 40 — RRHH — Disponibilidad Docente

**Ruta:** `/disponibilidad` | **Rol:** COORDINADOR_ACADEMICO (2) o superior

| ID | Caso de prueba | Pasos | Resultado esperado | P |
|----|---------------|-------|--------------------|---|
| DIS-01 | Registrar disponibilidad | Docente → slots lunes-viernes → Guardar | `GuardarDisponibilidadUseCase` guarda todos los slots | P2 |
| DIS-02 | Eliminar slot | Click en slot → Eliminar | `EliminarSlotUseCase` borra solo ese slot | P2 |
| DIS-03 | DiaSemana enum | Lunes = 0, Domingo = 6 | Verificar orden correcto en el selector | P2 |

---

# MÓDULO 41 — Asistencia Personal

**Ruta:** `/asistencia-personal` | **Rol:** COORDINADOR_RH (2) o superior

| ID | Caso de prueba | Pasos | Resultado esperado | P |
|----|---------------|-------|--------------------|---|
| ASP-01 | Tomar asistencia | Personal → fecha hoy → lista de empleados | Estado por defecto PRESENTE | P2 |
| ASP-02 | Registrar tardanza | Empleado → TARDE | `RegistrarAsistenciaUseCase` hace UPSERT con tipo TARDE | P2 |
| ASP-03 | Justificar inasistencia | Supervisor → justificar ausencia del día anterior | `ActualizarAsistenciaUseCase` actualiza con justificación (RBAC nivel <= 2) | P2 |

---

# MÓDULO 42 — Reinscripción / Portal Padres

**Ruta:** `/padres` | **Rol:** PADRE_FAMILIA (5) o alumno

| ID | Caso de prueba | Pasos | Resultado esperado | P |
|----|---------------|-------|--------------------|---|
| PAD-01 | Ver hijos | Login padre → panel principal | Lista de hijos con foto y grupo | P1 |
| PAD-02 | Calificaciones hijo | Click en hijo → tab Calificaciones | Calificaciones por materia y periodo | P1 |
| PAD-03 | Asistencia hijo | Tab Asistencia | % asistencia y fechas de inasistencia | P1 |
| PAD-04 | Comunicados | Tab Comunicados | Comunicados recibidos con opción de acusar | P2 |
| PAD-05 | RBAC aislamiento | Padre con 2 hijos en distintos planteles | Solo ve datos de sus hijos, no de otros alumnos | P1 |

---

# MÓDULO 43 — Padres Admin

**Ruta:** `/padres-admin` | **Rol:** ADMIN_PLANTEL (1) o superior

| ID | Caso de prueba | Pasos | Resultado esperado | P |
|----|---------------|-------|--------------------|---|
| PADA-01 | Listar contactos familiares | Seleccionar alumno | Lista de padres/tutores con banderas: tutor legal, puede recoger, emergencia | P2 |
| PADA-02 | Agregar contacto | Nuevo contacto → padre/tutor → banderas → Guardar | Contacto vinculado al alumno | P2 |
| PADA-03 | Un padre múltiples hijos | Verificar | Mismo `persona_id` vinculado a múltiples alumnos sin duplicar | P2 |

---

# MÓDULO 44 — Portal Público de Convocatorias

**Ruta:** `/portal` (pública) | **Rol:** Sin autenticación

| ID | Caso de prueba | Pasos | Resultado esperado | P |
|----|---------------|-------|--------------------|---|
| POR-01 | Acceso sin login | Abrir `/portal` en incógnito | Página de convocatorias visible sin auth | P1 |
| POR-02 | Filtrar por plantel | Selector plantel | Lista filtrada a convocatorias de ese plantel | P1 |
| POR-03 | Filtrar por nivel | Selector nivel educativo | Solo convocatorias del nivel seleccionado | P2 |
| POR-04 | Detalle convocatoria | Click en tarjeta | Página detalle con requisitos, fechas, imagen | P2 |
| POR-05 | Catálogo `/api/portal/catalogo` | Llamar endpoint | Retorna planteles, niveles, categorías, tipos | P2 |

---

# MÓDULO 45 — Portal Admin (Convocatorias)

**Ruta:** `/portal-admin` | **Rol:** DIRECTOR (2) o superior

| ID | Caso de prueba | Pasos | Resultado esperado | P |
|----|---------------|-------|--------------------|---|
| PADM-01 | CRUD convocatorias | Crear → editar → publicar → cerrar | Estados correctos, visible en portal público | P2 |
| PADM-02 | Subir imagen | Convocatoria → Subir imagen | `POST /convocatorias/{id}/imagen` → nginx assets | P2 |
| PADM-03 | Gestionar secciones | Agregar sección (DOCUMENTOS_REQUERIDOS) → Guardar | Sección en tabla `portal.secciones_convocatoria` | P2 |

---

# MÓDULO 46 — Reportes y Boletas

**Ruta:** `/reportes` | **Rol:** COORDINADOR_ACADEMICO (3) o superior

| ID | Caso de prueba | Pasos | Resultado esperado | P |
|----|---------------|-------|--------------------|---|
| REP-01 | Boleta individual | Alumno + periodo → Generar boleta → PDF | PDF con calificaciones por materia, inasistencias, firma | P1 |
| REP-02 | Boletas de grupo | Grupo + periodo → Generar boletas masivo → ZIP | ZIP con un PDF por alumno, procesado vía Celery | P1 |
| REP-03 | Constancia de estudios | Alumno → Constancia → PDF | PDF con datos del alumno y plantel | P2 |
| REP-04 | Constancia de calificaciones | Alumno → Constancia calificaciones → PDF | PDF con materias y calificaciones aprobatorias | P2 |
| REP-05 | Kardex académico | Alumno → Kardex → PDF | Historial completo de ciclos cursados | P2 |
| REP-06 | Merge PDF de grupo | Seleccionar grupo → Merge con Stirling-PDF | Un solo PDF con todas las boletas | P3 |
| REP-07 | PDF con marca de agua | Boleta provisional → marca "PROVISIONAL" | Stirling-PDF aplica watermark | P3 |

---

# MÓDULO 47 — BI / Superset

**Ruta:** `/bi` | **Rol:** DIRECTOR (2) o superior

| ID | Caso de prueba | Pasos | Resultado esperado | P |
|----|---------------|-------|--------------------|---|
| BI-01 | Carga iframe | Entrar a `/bi` | Iframe de Superset con guest token visible | P2 |
| BI-02 | Dashboard visible | Dashboard "Estadísticas Institucionales" | Gráficas con datos del ciclo activo | P2 |
| BI-03 | RLS Superset | Login director Metepec → dashboard | Solo datos de Metepec en las gráficas | P2 |
| BI-04 | Sin acceso — docente | Login docente → navegar a `/bi` | 403 / redirigido | P2 |

---

# MÓDULO 48 — Monitor / Infraestructura

**Ruta:** `/monitor` | **Rol:** ADMIN_PLANTEL (1) o superior

| ID | Caso de prueba | Pasos | Resultado esperado | P |
|----|---------------|-------|--------------------|---|
| MON-01 | Estado de servicios | Entrar al módulo | Cards de salud: PostgreSQL, Valkey, Celery, ntfy, MinIO — todos verdes | P1 |
| MON-02 | Telemetría AD-030 | Tab Telemetría (nivel ≤ 2) | JVM heap %, threads, disco, pool JDBC, Celery queues | P1 |
| MON-03 | Telemetría — acceso denegado | Login coordinador (nivel 3) → telemetría | Tab no visible o 403 | P1 |
| MON-04 | Grafana embed / link | Botón "Ver Grafana" | Link a `http://localhost:3003` (acceso interno) | P3 |
| MON-05 | n8n workflows | Link a n8n | Flujos activos: asistencia, calificaciones, boletas, comunicados | P2 |

---

# MÓDULO 49 — Mi Progreso (Vista Alumno)

**Ruta:** `/mi-progreso` | **Rol:** ALUMNO (5)

| ID | Caso de prueba | Pasos | Resultado esperado | P |
|----|---------------|-------|--------------------|---|
| MP-01 | Ver materias | Login alumno | Materias del ciclo activo con calificación actual | P1 |
| MP-02 | Countdown tareas | Panel de tareas pendientes | Fechas límite próximas ordenadas por urgencia | P2 |
| MP-03 | Asistencia personal | Tab asistencia | % acumulado del ciclo | P2 |
| MP-04 | Badges obtenidos | Tab badges | Insignias ganadas | P3 |

---

# MÓDULO 50 — Ayuda Contextual

**Ruta:** `/ayuda` | **Rol:** Todos

| ID | Caso de prueba | Pasos | Resultado esperado | P |
|----|---------------|-------|--------------------|---|
| AYU-01 | Icono de ayuda | Click en `?` en cualquier módulo | Panel con guía contextual del módulo actual | P3 |
| AYU-02 | Página de ayuda | Ir a `/ayuda` | Manual de usuario navegable | P3 |

---

---

# SECCIÓN A — Pruebas de API (FastAPI) — Endpoints Críticos

**Base URL:** `https://ades.setag.mx/api/v1/`
**Autenticación:** `Authorization: Bearer <token_oidc>`

## A.1 Health y Catálogos
```
GET  /health                        → 200 {"status":"healthy"}
GET  /catalogs/niveles              → 200 [{id, nombre_nivel}]
GET  /catalogs/grados?nivel_id=...  → 200 [{id, nombre_grado}]
GET  /catalogs/ciclos               → 200 [{id, nombre_ciclo, es_vigente}]
GET  /catalogs/ciclos?solo_vigentes=true → Solo ciclos vigentes
GET  /catalogs/roles                → 200 [{id, nombre_rol, nivel_acceso}]
GET  /catalogs/periodos?ciclo_id=...→ 200 periodos del ciclo
GET  /catalogs/paises               → 200 lista de países
GET  /catalogs/lenguas-indigenas    → 200 68 agrupaciones INALI
GET  /catalogs/niveles-ingles       → 200 niveles CEFR A1-C2
```

## A.2 Core Académico
```
GET  /alumnos?plantel_id=...        → RBAC filtra por plantel
GET  /alumnos/{id}                  → Detalle con inscripciones
POST /alumnos                       → Crear (validar CURP único)
PUT  /alumnos/{id}                  → Editar (row_version optimistic)
GET  /grupos?plantel_id=...         → Solo grupos del plantel
POST /grupos                        → Crear (validar A/B y max 2)
GET  /profesores?plantel_id=...     → RBAC plantel
POST /profesores                    → Crear (RFC único)
GET  /inscripciones?grupo_id=...    → Alumnos del grupo
POST /inscripciones                 → Inscribir (validar no duplicado)
```

## A.3 Operación Académica
```
GET  /asistencias?clase_id=...      → Lista por clase y fecha
POST /asistencias/bulk              → Guardar masivo [{alumno_id, estatus}]
GET  /calificaciones?grupo_id=&periodo_id= → Calificaciones del periodo
POST /calificaciones/bulk           → Guardar masivo (validar escala)
GET  /tareas?grupo_id=              → Tareas del grupo
POST /tareas                        → Crear tarea
GET  /tareas/{id}/entregas          → Entregas por tarea
POST /tareas/{id}/entregas/{alumno_id}/calificar → Calificar
```

## A.4 Gradebook
```
GET  /gradebook?grupo_id=&materia_id= → Panel spreadsheet
POST /gradebook/calificar             → Bulk calificación
POST /gradebook/cerrar                → Cerrar periodo (nivel 3+)
PUT  /gradebook/ajuste/{id}           → Ajuste manual con justificación >= 20 chars
GET  /esquemas-ponderacion?nivel_id=  → Esquemas disponibles
POST /esquemas-ponderacion            → Crear (validar suma = 100%)
```

## A.5 Documentos y IA
```
POST /expediente/{id}/documentos/subir   → Subir PDF → OCR async
GET  /expediente/{id}/documentos/{doc}/estado-ocr → Polling estado
GET  /expediente/alumno/{id}/buscar?q=  → FTS en texto OCR
POST /ai/chat                           → Chat IA (get_ades_user)
GET  /ai/mis-sesiones                   → Historial sesiones del usuario
GET  /ai/sesion/{id}                    → Detalle sesión
DELETE /ai/sesion/{id}                  → Eliminar sesión
GET  /stats/telemetria                  → JVM + infra (nivel_acceso <= 2)
```

## A.6 Certificados
```
POST /certificados                  → Emitir (nivel 2+)
POST /certificados/{id}/firmar      → Firma Ed25519
GET  /certificados/verificar/{folio}→ Sin auth, público
GET  /certificados/{id}/pdf         → Descargar PDF con QR
```

---

# SECCIÓN B — Pruebas BFF (Spring Boot)

**Base URL:** `https://ades.setag.mx/bff/api/`

| ID | Endpoint | Verificar | P |
|----|----------|-----------|---|
| BFF-01 | `GET /actuator/health` | 200 `{"status":"UP"}` | P1 |
| BFF-02 | `GET /actuator/prometheus` | Métricas Micrometer en texto plain | P1 |
| BFF-03 | `GET /api/v1/catalogs/roles` | Via `CatalogsQueryService`, sin JdbcTemplate en controller | P1 |
| BFF-04 | `GET /api/v1/catalogs/periodos?ciclo_id=` | Periodos filtrados por ciclo | P1 |
| BFF-05 | `GET /api/v1/catalogs/lenguas-indigenas?familia=OTO-MANGUE` | Filtro por familia lingüística | P2 |
| BFF-06 | `GET /api/v1/stats/telemetria` | Solo con token nivel_acceso <= 2 | P1 |
| BFF-07 | Auth header faltante | `GET /api/v1/alumnos` sin Authorization | 401 Unauthorized | P1 |
| BFF-08 | Token expirado | Token con `exp` pasado | 401 / refresh automático | P1 |

---

# SECCIÓN C — Pruebas de Reglas de Negocio Transversales

| ID | Regla | Verificación | P |
|----|-------|-------------|---|
| RN-01 | PK siempre UUID | `SELECT id FROM ades_grupos LIMIT 1` → UUID v7, no integer | P1 |
| RN-02 | Auditoría en toda mutación | `SELECT * FROM auditoria.reporte_cobertura()` → 0 tablas sin cobertura | P1 |
| RN-03 | `audit_biu` en toda tabla | Crear cualquier registro → verificar `ref`, `row_version=1`, `fecha_creacion` | P1 |
| RN-04 | `usuario_id` en mutaciones | POST /asistencias → ver `usuario_id` en registro | P1 |
| RN-05 | Suma ponderaciones = 100% | `SELECT * FROM ades_esquemas_ponderacion WHERE ...` → suma items = 100 | P1 |
| RN-06 | RBAC scope plantel | Query `ades_alumnos` con `plantel_id` ≠ del usuario → 0 resultados | P1 |
| RN-07 | Soft delete | Desactivar alumno → `is_active=FALSE`, fila conservada | P1 |
| RN-08 | `cerrar_ciclo_y_promover()` | Ejecutar manualmente en BD → verificar inscripciones creadas | P2 |
| RN-09 | `calcular_calificacion_periodo` idempotente | Disparar trigger 2 veces → mismo resultado (UPSERT) | P2 |
| RN-10 | Calificación cerrada inmutable sin ADMIN | `UPDATE ades_calificaciones_periodo SET ... WHERE cerrada=TRUE` sin token admin → 403 | P1 |
| RN-11 | Escala SEP: 0-10, min 6.0 | POST calificación = 11 → error; = 5 → guardado pero reprobatoria | P1 |
| RN-12 | Escala UAEMEX: 0-100, min 60 | POST calificación = 101 → error | P1 |
| RN-13 | CURP único | Insertar CURP duplicado → constraint violation | P1 |
| RN-14 | RFC profesor único | Insertar RFC duplicado → constraint violation | P1 |
| RN-15 | Máx 2 grupos por grado | Tercer grupo mismo grado/ciclo → constraint violation | P2 |
| RN-16 | `AccionReinscripcion.RECHAZAR.requiereRazon()` | API acepta rechazo sin razón → 422 | P1 |
| RN-17 | Firma Ed25519 verificable | `EstadoCertificado.esVerificable()` solo para FIRMADO | P1 |
| RN-18 | `DiasHabiles` excluye sáb-dom | Licencia viernes → lunes = 1 día hábil, no 3 | P2 |
| RN-19 | Ajuste manual ≥ 20 chars | Justificación 19 chars → 422 | P1 |
| RN-20 | Partición tabla por ciclo | `EXPLAIN SELECT * FROM ades_asistencias WHERE ciclo='2026-2027'` → solo 1 partición scaneada | P2 |

---

# SECCIÓN D — Pruebas de Servicios Externos

## D.1 Celery + Notificaciones
| ID | Prueba | Verificación | P |
|----|--------|-------------|---|
| CEL-01 | Worker activo | `docker compose exec ades-celery-worker celery inspect active` | P1 |
| CEL-02 | Alerta asistencia < 85% | Registrar asistencia que baje al 84% | ntfy recibe push `POST /padre_{id}` | P2 |
| CEL-03 | Alerta calificación reprobatoria | Guardar calificación < 6.0/60 | ntfy push en < 30s | P2 |
| CEL-04 | OCR Paperless | Subir PDF al expediente | `estado_ocr` cambia a COMPLETADO en < 120s | P2 |
| CEL-05 | Refresh MV nocturno | Revisar Celery Beat schedule | `v_asistencias_resumen` y `v_tareas_entregas_resumen` refrescadas | P2 |
| CEL-06 | Boletas batch | Generar boletas de grupo → Celery task | Task en cola, resultado ZIP disponible | P2 |

## D.2 n8n Workflows
| ID | Prueba | Verificación | P |
|----|--------|-------------|---|
| N8N-01 | Webhook asistencia | Registrar asistencia → n8n recibe webhook | Log en n8n muestra ejecución | P2 |
| N8N-02 | Webhook calificación | Calificación reprobatoria → n8n notifica | Flujo ejecutado en n8n | P2 |
| N8N-03 | Batch boletas cierre periodo | Simular cierre → n8n programa generación masiva | Task programada en Celery | P2 |

## D.3 Carbone — Generador de Documentos
| ID | Prueba | Verificación | P |
|----|--------|-------------|---|
| CAR-01 | Health Carbone | `GET http://ades-carbone:3001/` | 200 OK | P1 |
| CAR-02 | Generar boleta | `POST /api/v1/boletas/{alumno_id}?periodo_id=...` | PDF descargable con datos del alumno | P1 |
| CAR-03 | Constancia de estudios | `POST /api/v1/reportes/constancia/{alumno_id}` | PDF con datos correctos | P2 |

## D.4 Paperless-ngx OCR
| ID | Prueba | Verificación | P |
|----|--------|-------------|---|
| PAP-01 | Health Paperless | `GET http://ades-paperless:8000/api/` con credenciales | 200 OK | P1 |
| PAP-02 | Documento subido | Subir PDF → verificar en Paperless UI | Documento con etiqueta alumno visible | P2 |
| PAP-03 | OCR completado | Esperar procesamiento | Campo `ocr_texto` en BD no vacío | P2 |
| PAP-04 | Búsqueda FTS | `GET /expediente/alumno/{id}/buscar?q=CURP` | Resultados con documentos relevantes | P2 |

## D.5 ntfy Push
| ID | Prueba | Verificación | P |
|----|--------|-------------|---|
| NTF-01 | Health ntfy | `GET http://ades-ntfy:2586/v1/health` | `{"healthy":true}` | P1 |
| NTF-02 | Push manual | `POST /padre_test "Prueba de alerta"` | Notificación recibida en app ntfy | P2 |

## D.6 Stirling-PDF
| ID | Prueba | Verificación | P |
|----|--------|-------------|---|
| STR-01 | Health | `GET http://ades-stirling-pdf:8081/api/v1/info` | 200 con versión | P1 |
| STR-02 | Merge PDFs | API merge con 3 boletas | PDF unificado correcto | P3 |

## D.7 Vault
| ID | Prueba | Verificación | P |
|----|--------|-------------|---|
| VLT-01 | Vault accesible | `docker compose exec ades-vault vault status` | Estado `sealed: false` | P2 |
| VLT-02 | Integración pendiente | Verificar que servicios siguen leyendo de `.env` | Nota: integración completa en backlog | P4 |

---

# SECCIÓN E — Pruebas de Seguridad y RBAC

| ID | Prueba | Descripción | P |
|----|--------|-------------|---|
| SEC-01 | Cross-plantel data leak | Token plantel A → `GET /alumnos?plantel_id=B` | 403 o lista vacía | P1 |
| SEC-02 | Elevation of privilege | Token nivel 4 → `DELETE /usuarios/{id}` | 403 | P1 |
| SEC-03 | Token sin firma válida | Token manipulado → cualquier endpoint | 401 | P1 |
| SEC-04 | OIDC sub válido | Token con `sub` no registrado en `ades_usuarios.oidc_sub` | 401 | P1 |
| SEC-05 | AuditMiddleware en mutaciones | `POST /calificaciones/bulk` | `ades_audit_log` tiene registro con `usuario_id` real | P1 |
| SEC-06 | SQL injection | Parámetro `?q='; DROP TABLE ades_alumnos;--` | Query parametrizada, no ejecuta SQL arbitrario | P1 |
| SEC-07 | XSS en campos texto | `<script>alert(1)</script>` en campo nombre | Almacenado como texto plano, Angular escapa al renderizar | P1 |
| SEC-08 | Acceso ruta pública `/verificar/:folio` | Sin token en header | 200 OK con estado del certificado | P1 |
| SEC-09 | Acceso `portal` sin auth | Sin sesión → `/portal` | 200 OK (convocatorias públicas) | P1 |
| SEC-10 | Audit trail inmutable | Intentar `DELETE FROM auditoria.log_auditoria` | Permiso denegado (solo rol `auditoria_admin`) | P1 |

---

# SECCIÓN F — Pruebas de Rendimiento Básico

| ID | Prueba | Métrica esperada | P |
|----|--------|-----------------|---|
| PERF-01 | Dashboard carga | Tiempo hasta primera vista útil (LCP) | < 3s en conexión normal | P2 |
| PERF-02 | Tabla 1,620 alumnos | `GET /alumnos?page=1&limit=50` | < 500ms con paginación server-side | P2 |
| PERF-03 | Gradebook 30 alumnos | Cargar spreadsheet grupo completo | < 2s | P2 |
| PERF-04 | Bulk save asistencias | POST 30 registros simultáneos | < 1s respuesta | P2 |
| PERF-05 | PgBouncer pool | Ver `hikaricp_connections_pending` en Grafana | = 0 en carga normal | P2 |
| PERF-06 | Partición query | EXPLAIN sobre tabla particionada con filtro ciclo | Partition pruning activo | P2 |
| PERF-07 | Cache hit PG | `SELECT blks_hit, blks_read FROM pg_stat_database WHERE datname='ades'` | Ratio > 95% | P3 |

---

# SECCIÓN G — Pruebas de Componentes Compartidos

### G.1 InteractiveGrid (componente custom principal)
| ID | Caso | Resultado esperado | P |
|----|------|--------------------|---|
| IG-01 | Búsqueda inline | Escribir en campo search → filtrado en tiempo real | P2 |
| IG-02 | Ordenamiento columna | Click en header → orden ASC/DESC | P2 |
| IG-03 | Paginación | Navegar páginas → datos correctos cada página | P2 |
| IG-04 | Export CSV | Botón exportar → descarga CSV | P2 |
| IG-05 | Export XLSX | Botón exportar → XLSX con encabezado rojo Nevadi | P2 |
| IG-06 | Columna searchable=false | `@Input() searchable = false` → campo búsqueda oculto | P2 |
| IG-07 | Template column | Columna con template slot → render custom | P2 |
| IG-08 | Alineación columnas | `align: 'right'` → números alineados a derecha | P3 |

### G.2 ImportButton
| ID | Caso | Resultado esperado | P |
|----|------|--------------------|---|
| IMP-01 | Subir CSV válido | Archivo correcto → importación exitosa con resumen | P2 |
| IMP-02 | CSV con errores | Filas con datos inválidos → reporte de errores por fila | P2 |
| IMP-03 | Archivo no CSV | Subir .xlsx o .pdf → error de tipo de archivo | P2 |

### G.3 SelectorGeo
| ID | Caso | Resultado esperado | P |
|----|------|--------------------|---|
| GEO-01 | Búsqueda por CP | Ingresar CP → autocompletar colonia/municipio/estado | P2 |
| GEO-02 | CP inexistente | CP no en catálogo SEPOMEX | Mensaje: "Código postal no encontrado" | P2 |

### G.4 ContextService (plantel + ciclo)
| ID | Caso | Resultado esperado | P |
|----|------|--------------------|---|
| CTX-01 | Cambio plantel | Selector plantel → todos los módulos usan `ctx.plantel()?.id` | P1 |
| CTX-02 | Plantel null | Usuario sin plantel asignado → selector deshabilitado o error claro | P2 |
| CTX-03 | Persistencia en navegación | Cambiar plantel → navegar a otro módulo → plantel conservado | P1 |

---

# Checklist de Cierre QA

## Antes de pasar a producción:
- [ ] **P1**: Todos los casos P1 marcados ✅
- [ ] **Sin regresiones**: Módulos previos no afectados por los nuevos
- [ ] **Audit coverage**: `SELECT * FROM auditoria.reporte_cobertura()` → 0 tablas sin `audit_biu`
- [ ] **Cero JdbcTemplate en controllers**: `grep -r "JdbcTemplate" *Controller.java` → 0 resultados ✅
- [ ] **Build frontend sin errores TypeScript**: `npm run build` sin errores
- [ ] **Tests BFF**: `docker compose build ades-bff` → BUILD SUCCESS ✅
- [ ] **Certificados verificables**: Al menos 1 certificado firmado + verificado con `/verificar/:folio`
- [ ] **OCR funcional**: Al menos 1 documento procesado con `estado_ocr = COMPLETADO`
- [ ] **Particiones activas**: Datos en partición `ciclo_2026_2027`, query planning correcto
- [ ] **Google SSO**: Pendiente credenciales externas (no bloquea QA)
- [ ] **Polygon**: Diferido a producción
- [ ] **Superset dashboards**: Configuración manual pendiente (backlog pre-producción)

## Rastreo de issues
Registrar cada falla como: `[ID-PRUEBA] Descripción breve | Módulo | Prioridad | Fecha`

---

*Generado: 2026-06-16 | ADES Sprint QA Pre-Producción*
