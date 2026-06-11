# Matriz de Trazabilidad: Casos de Uso → Componentes Técnicos

**Propósito:** Referencia rápida para ubicar dónde está implementado cada CU en la arquitectura ADES.

---

## 📊 Matriz Completa

| # | Caso de Uso | Tablas PostgreSQL (principales) | Endpoints FastAPI | Componente Angular | Triggers/Jobs | Notificaciones |
|---|-------------|----------------------------------|-------------------|------------------|--------------|----------------|
| **CU-01** | Apertura Ciclo Escolar | `ades_ciclos_escolares`, `ades_periodos_evaluacion`, `ades_plantel_niveles` | POST/PATCH `/api/v1/ciclos-escolares` | `admin.component.ts` (Ciclos tab) | Stored proc crear periodos | Email `SECRETARIA_ACADEMICA` |
| **CU-02** | Áreas Académicas | `ades_areas_academicas`, `ades_coordinaciones_area` | GET/PATCH `/api/v1/areas-academicas` | `areas.component.ts` (Editor inline) | — | — |
| **CU-03** | Inscripción Alumnos | `ades_inscripciones`, `ades_estudiantes`, `ades_grupos` | POST/PATCH `/api/v1/inscripciones` | `inscripciones.component.ts` (Dialog + Grid) | Webhook → Authentik | Email bienvenida padre |
| **CU-04** | Pase de Lista Primaria | `ades_asistencias`, `ades_clases` | POST/PATCH `/api/v1/asistencias` | `asistencias.component.ts` (Grid) | n8n alert ausentismo | Push ntfy si ausencias |
| **CU-05** | Pase de Lista Secundaria/Prep | `ades_asistencias`, `ades_clases`, `ades_horarios` | POST/PATCH `/api/v1/asistencias` | `asistencias.component.ts` (Grid por materia) | Celery % ausentismo | Push si riesgo |
| **CU-06** | Gradebook Primaria (NEM) | `ades_calificaciones_periodo`, `ades_materias_plan`, `ades_rubricas` | PATCH `/api/v1/calificaciones` | `gradebook.component.ts` (Spreadsheet) | `calcular_calificacion_periodo()` trigger PG | — |
| **CU-07** | Evaluación Preparatoria | `ades_calificaciones_evaluaciones`, `ades_evaluaciones`, `ades_periodos_evaluacion` | PUT `/api/v1/calificaciones/{id}` | `evaluaciones.component.ts` (Agenda + Libreta) | Recalculador Kárdex | SMS/Email si reprobado |
| **CU-08** | IA Riesgo Académico | `ades_reportes_academicos`, `ades_calificaciones_periodo`, `ades_asistencias` | GET `/api/v1/reportes-academicos` | `reportes.component.ts` (BI) | Celery semanal + Claude API | Email director si alto riesgo |
| **CU-09** | Expediente Médico | `ades_expedientes_medicos`, `ades_incidentes_medicos` | PATCH `/api/v1/expedientes-medicos` | `expediente-medico.component.ts` (Drawer) | Webhook crítico n8n | Push ntfy urgente padre |
| **CU-10** | Reportes Conducta | `ades_reportes_conducta`, `ades_personas`, `ades_acuses_comunicado` | POST/PATCH `/api/v1/reportes-conducta` | `conducta.component.ts` (Grid + Acuse) | Email padre obligatorio | Email + Push acuse |
| **CU-11** | Boletas Lote | `ades_calificaciones_periodo`, `ades_inscripciones`, `ades_archivos` | POST `/api/v1/reportes/boletas-lote` | `reportes.component.ts` (Btn) | Celery → Carbone → Stirling | Email padre con link |
| **CU-12** | Dashboard BI | Vistas `ades_bi.*` (materializadas) | POST `/api/v1/superset/guest-token` | `bi.component.ts` (iframe Superset) | Refresh vistas cada 4h | — |

---

## 🔌 Endpoint REST Completo por CU

### CU-01: Ciclos Escolares
```
POST   /api/v1/ciclos-escolares
       Body: { nombre_ciclo, fecha_inicio, fecha_fin, nivel_educativo_id, plantel_id }
       Response: 201 + { id, periodos_creados: 3 }

GET    /api/v1/ciclos-escolares?plantel_id=...&nivel_id=...
       Response: 200 + [{ id, nombre_ciclo, es_vigente, fecha_inicio, fecha_fin }]

PATCH  /api/v1/ciclos-escolares/{ciclo_id}
       Body: { es_vigente: true/false, row_version }
       Response: 200 + ciclo actualizado
```

### CU-03: Inscripciones
```
POST   /api/v1/estudiantes
       Body: { nombre, apellido_paterno, apellido_materno, curp, nss }
       Response: 201 + { id, persona_id, matricula }

POST   /api/v1/inscripciones
       Body: { estudiante_id, grupo_id, ciclo_escolar_id, autorizar_sobrecupo?: true }
       Response: 201 + { id, fecha_inscripcion }
       Error 409: Si grupo lleno y no sobrecupo

PATCH  /api/v1/inscripciones/{inscripcion_id}
       Body: { grupo_id, row_version }
       Response: 200 + inscripcion actualizada
```

### CU-04/CU-05: Asistencias
```
GET    /api/v1/asistencias/grupo/{grupo_id}?fecha=YYYY-MM-DD
       Response: 200 + [{ id_alumno, nombre, estatus: "P"|"A"|"T"|"J" }]

POST   /api/v1/asistencias/lote
       Body: { grupo_id, fecha, registros: [{ alumno_id, estatus, justificacion? }] }
       Response: 201 + { registrados: N, alertas: [...] }

PATCH  /api/v1/asistencias/{asistencia_id}
       Body: { estatus, justificacion, row_version }
       Response: 200 + asistencia actualizada
```

### CU-06: Gradebook
```
GET    /api/v1/gradebook/{grupo_id}/{periodo_id}
       Response: 200 + { 
         columnas: [campos_formativos...],
         alumnos: [{ id, nombre, calificaciones: [...] }]
       }

PATCH  /api/v1/calificaciones/{calificacion_id}
       Body: { valor: 5-10, justificacion: "...", row_version }
       Response: 200 + { promedio_recalculado: N }
       Error 409: Si row_version no coincide
```

### CU-07: Evaluaciones
```
GET    /api/v1/evaluaciones?periodo_id=...
       Response: 200 + [{ id, tipo: "ORDINARIO|EXTRAORDINARIO", grupo_id, materia_id, fecha }]

PUT    /api/v1/calificaciones/{id}
       Body: { calificacion_final: 0-10, observaciones, row_version }
       Response: 200 + { estado: "aprobado"|"reprobado" }

POST   /api/v1/evaluaciones
       Body: { tipo: "EXTRAORDINARIO", alumno_id, materia_id, fecha_examen }
       Response: 201 + { id, folio_unico: "..." }
```

### CU-08: Reportes Académicos (IA)
```
GET    /api/v1/reportes-academicos?nivel_riesgo=ALTO&plantel_id=...
       Response: 200 + [{ 
         alumno_id, nivel_riesgo, factores, plan_accion, timestamp_analisis 
       }]

GET    /api/v1/reportes-academicos/{alumno_id}/learning-path
       Response: 200 + { modulos: [...], progreso: "..." }
```

### CU-09: Expedientes Médicos
```
PATCH  /api/v1/expedientes-medicos/{estudiante_id}
       Body: { tipo_sangre, alergias: [...], medicamentos: [...], row_version }
       Response: 200 + expediente actualizado

POST   /api/v1/incidentes-medicos
       Body: { estudiante_id, fecha, descripcion, criticidad: "LEVE|MODERADO|GRAVE" }
       Response: 201 + { id }
       Effect: Webhook → n8n → SMS/Email/Push padre
```

### CU-10: Reportes Conducta
```
POST   /api/v1/reportes-conducta
       Body: { alumno_id, fecha, tipo_falta, descripcion, notificado_a_familiar: true }
       Response: 201 + { id }

PATCH  /api/v1/reportes-conducta/{reporte_id}
       Body: { plan_mejora: {...}, row_version }
       Response: 200 + reporte actualizado

POST   /api/v1/acuses/{reporte_id}/firmar
       Body: { timestamp_cliente }
       Response: 201 + { acuse_id, fecha_firma }
```

### CU-11: Boletas Lote
```
POST   /api/v1/reportes/boletas-lote
       Body: { plantel_id, nivel_educativo_id, periodo_id }
       Response: 202 Accepted + { job_id: "uuid", status_url: "..." }

GET    /api/v1/reportes/boletas-lote/{job_id}/status
       Response: 200 + { estado: "procesando"|"completado", progreso: "156/156" }

GET    /api/v1/reportes/boletas-lote/{job_id}/descargar
       Response: 200 + ZIP descargable (o redirect a MinIO presigned URL)
```

### CU-12: Superset Guest Token
```
POST   /api/v1/superset/guest-token
       Response: 200 + { 
         guest_token: "...", 
         embed_url: "https://bi.ades.setag.mx/embedded/...",
         expires_in: 3600
       }
```

---

## 🗂️ Estructura de Archivos Afectados

```
backend/
├── app/
│   ├── api/v1/
│   │   ├── ciclos.py              ← CU-01
│   │   ├── areas_academicas.py    ← CU-02
│   │   ├── inscripciones.py       ← CU-03
│   │   ├── asistencias.py         ← CU-04, CU-05
│   │   ├── calificaciones.py      ← CU-06, CU-07
│   │   ├── reportes.py            ← CU-08, CU-11
│   │   ├── expedientes_medicos.py ← CU-09
│   │   ├── reportes_conducta.py   ← CU-10
│   │   └── superset.py            ← CU-12
│   ├── models/
│   │   ├── academica.py           ← ciclos, grupos, horarios
│   │   ├── personas.py            ← estudiantes, contactos
│   │   ├── operacion.py           ← calificaciones, asistencias
│   │   ├── reportes.py            ← conducta, académicos
│   │   └── salud.py               ← expedientes, incidentes
│   ├── core/
│   │   ├── optimistic_locking.py  ← CU-06, CU-07 (row_version)
│   │   └── security.py            ← RLS rules
│   └── worker/
│       └── tareas.py              ← CU-04, CU-05, CU-08, CU-11

frontend/
├── src/app/
│   └── features/
│       ├── admin/                 ← CU-01 (Ciclos)
│       ├── inscripciones/         ← CU-03
│       ├── asistencias/           ← CU-04, CU-05
│       ├── calificaciones/        ← CU-06
│       ├── gradebook/             ← CU-06
│       ├── evaluaciones/          ← CU-07
│       ├── reportes/              ← CU-08, CU-11, CU-12
│       ├── expediente-medico/     ← CU-09
│       ├── conducta/              ← CU-10
│       └── bi/                    ← CU-12 (iframe Superset)

db/
├── migrations/
│   └── 001_initial_schema.sql     ← Todas las tablas
└── seeds/
    └── 003_alumnos_padres.sql     ← Datos iniciales alumnos

infrastructure/
├── authentik/setup.py             ← CU-03 (creación usuarios)
├── superset/custom_sso_security_manager.py  ← CU-12 (RLS)
└── n8n/workflows/
    ├── asistencias.json           ← CU-04, CU-05
    ├── conducta.json              ← CU-10
    ├── expediente_medico.json     ← CU-09
    └── boletas_email.json         ← CU-11
```

---

## 🔄 Flujos de Datos Por CU

### Flujo CU-04 (Pase de Lista)
```
Docente abre Grid
  ↓
GET /api/v1/asistencias/grupo/{grupo_id}?fecha=...
  ↓
Angular renderiza Interactive Grid (editable)
  ↓
Docente marca ausencias + clic "Guardar"
  ↓
POST /api/v1/asistencias/lote { grupo_id, registros: [...] }
  ↓
FastAPI inserta en ades_asistencias (batch)
  ↓
Trigger: calcula_ausentismo_mensual()
  ↓
Si ausentismo >= 3 días → Webhook → n8n
  ↓
n8n: genera Push notify vía ntfy
  ↓
Padre recibe notificación en app móvil
```

### Flujo CU-07 (Evaluación Extraordinaria)
```
Período ordinario cierra
  ↓
Secretaria abre /inscripciones-extraordinarias
  ↓
Sistema filtra automáticamente alumnos con < 6.0
  ↓
Secretaria crea registro en ades_evaluaciones (tipo=EXTRAORDINARIO)
  ↓
Email automático a alumno + padre (fecha/hora examen)
  ↓
Docente registra calificación
  ↓
PATCH /api/v1/calificaciones/{id} { calificacion_final: X, row_version: N }
  ↓
Backend: check_row_version(calificacion, N) → si falla: 409 Conflict
  ↓
Si OK → actualiza ades_calificaciones_evaluaciones
  ↓
Trigger: recalcula_kardex()
  ↓
Celery job: genera Kárdex PDF (Carbone → Stirling)
  ↓
MinIO almacena PDF con folio único
  ↓
Email padre con link descarga
```

### Flujo CU-08 (IA Riesgo Académico)
```
Domingo 23:00 UTC → Job Celery inicia
  ↓
SELECT alumnos con promedio < 6.0 OR ausentismo > 20%
  ↓
Para cada alumno:
  - Prepara contexto normalizado
  - POST a Claude API (LangChain)
  - Claude devuelve { nivel_riesgo, plan_accion }
  ↓
INSERT en ades_reportes_academicos
  ↓
Si nivel_riesgo=ALTO:
  - Email Director + TUTOR
  - Crea Learning Path automático
  - Push notificación padre
  ↓
Director ve dashboard updated en /bi (Superset)
```

---

## 🛡️ Seguridad y RLS por CU

| CU | RLS Aplicado | Validaciones Críticas | Row Version |
|----|--------------|----------------------|-------------|
| CU-01 | No (nivel admin) | FK plantel+nivel, CHECK ciclo activo | No |
| CU-03 | Sí (docente ve grupos de su plantel) | Capacidad máxima grupo, FK estudiante | Sí (inscripción) |
| CU-04 | Sí (docente solo su grupo) | Docente=titular, ciclo activo | Sí |
| CU-06 | Sí (docente solo calificaciones su grupo) | Período abierto, escala 5-10 | **Sí** (critical) |
| CU-07 | Sí (docente solo su materia/grupo) | Período cerrado, calificación 0-10 | **Sí** (critical) |
| CU-09 | Sí (médico solo su plantel) | Campo "notificado_a_familiar" obligatorio | No |
| CU-10 | Sí (orientador solo su nivel) | Acuse digital obligatorio | No |
| CU-12 | Sí (RLS via Superset guest token) | Filtros plantel_id + nivel_educativo_id | No |

---

## 🧪 Matriz de Pruebas Recomendadas

| CU | Test Unitario | Test E2E | Load Test | Caso de Error |
|----|---|---|---|---|
| CU-01 | Validación ciclo UAEMEX en Ixtapan (debe fallar) | Crear ciclo + periodos generados | 1k ciclos | Ciclo sin fecha fin |
| CU-03 | Verificar sobrecupo bloqueado | Inscribir alumno + acuse email | 5k inscripciones | Grupo lleno sin auth |
| CU-04 | Cálculo ausentismo mensual | Docente marca ausencias | 10k registros asistencia/día | Usuario no es titular |
| CU-06 | Función calcular_calificacion_periodo() | Docente carga calificaciones + promedio | 1k calificaciones | Valor fuera de rango 5-10 |
| CU-07 | Row version conflict (409) | Crear extraordinario + registrar nota | 5k evaluaciones | Concurrencia 2 docentes |
| CU-08 | Mock Claude API | Ejecutar análisis riesgo | 1k alumnos análisis | API Claude timeout |
| CU-11 | Carbone template injection | Generar boletas lote 100+ | 1k boletas/lote | Stirling-PDF fallo |
| CU-12 | RLS filters en Superset | Director ve solo su nivel | 100k KPI queries | Bypass RLS attempt |

---

**Actualizado:** 2026-06-09  
**Mantenedor:** Equipo ADES  
**Validez:** Activa mientras fases 1-10, 15-23 estén en producción
