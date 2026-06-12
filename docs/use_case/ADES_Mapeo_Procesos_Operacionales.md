# ADES Nevadi — Mapeo de Procesos Operacionales Reales
## Formularios, Flujos de Inscripción/Reinscripción y Especificaciones Operacionales

**Versión:** 1.0  
**Fecha:** Junio 2026  
**Fuente:** Diagramas reales y documentos Instituto Nevadi  
**Enfoque:** Automatización de procesos actuales en ADES

---

## 1. Análisis de Procesos Actuales

### 1.1 Proceso de Inscripción (5 Fases)

Basado en: `Diagrama_inscripción.pdf`

#### **FASE 1: Convocatoria y Pre-inscripción**

**Actores:**
- Dirección del Plantel
- Coordinación Administrativa
- Seguridad

**Documentos utilizados:**
- **FSEIAL-01:** Listado de pre-inscritos (registro en entrada)
- **LONA:** Cartel informativo (colocado en entrada)
- **FSEIAL-02:** Solicitud de preinscripción (llenado de información básica)

**Flujo Actual (Manual):**
1. Dirección presenta convocatoria impresa (lona)
2. Seguridad registra a padres que ingresan
3. Coordinación Administrativa proporciona información
4. Padres llenan FSEIAL-02 (papel)

**Requisitos ADES:**
```sql
-- Tabla: Preinscripciones
CREATE TABLE ades_preinscripciones (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    numero_solicitud VARCHAR(20) UNIQUE NOT NULL,
    fecha_solicitud DATE NOT NULL,
    
    -- Datos del aspirante
    nombre_aspirante VARCHAR(150) NOT NULL,
    fecha_nacimiento DATE,
    curp VARCHAR(18),
    
    -- Datos de padres
    padre_nombre VARCHAR(150),
    padre_telefono VARCHAR(20),
    padre_email VARCHAR(100),
    madre_nombre VARCHAR(150),
    madre_telefono VARCHAR(20),
    madre_email VARCHAR(100),
    
    -- Solicitud
    nivel_solicitado_id UUID REFERENCES ades_niveles_educativos(id),
    grado_solicitado_id UUID REFERENCES ades_grados(id),
    plantel_solicitado_id UUID REFERENCES ades_planteles(id),
    
    -- Documentación
    tiene_curp BOOLEAN DEFAULT FALSE,
    tiene_acta_nacimiento BOOLEAN DEFAULT FALSE,
    tiene_certificado_anterior BOOLEAN DEFAULT FALSE,
    
    -- Status
    estatus VARCHAR(30) DEFAULT 'REGISTRADA', -- REGISTRADA, EVALUANDO, ACEPTADA, RESERVA, RECHAZADA
    fecha_cierre_documentos DATE,
    requiere_estudio_socioeconomico BOOLEAN DEFAULT TRUE,
    tiene_hermano_inscrito BOOLEAN DEFAULT FALSE, -- Si aplica, no necesita socio-eco
    
    -- Auditoría
    created_at TIMESTAMP DEFAULT NOW(),
    created_by UUID REFERENCES ades_usuarios(id),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Vista para estadísticas de pre-inscripción
CREATE VIEW ades_v_preinscripcion_stats AS
SELECT 
    DATE(fecha_solicitud) as fecha,
    plantel_solicitado_id,
    nivel_solicitado_id,
    COUNT(*) as total,
    COUNT(CASE WHEN estatus = 'ACEPTADA' THEN 1 END) as aceptadas,
    COUNT(CASE WHEN estatus = 'RECHAZADA' THEN 1 END) as rechazadas
FROM ades_preinscripciones
GROUP BY DATE(fecha_solicitud), plantel_solicitado_id, nivel_solicitado_id;
```

**Componente Angular: Formulario de Pre-inscripción**

```typescript
// preinscripcion.component.ts
export class PreinscripcionComponent {
  
  formPrincipal = new FormGroup({
    aspirante: new FormGroup({
      nombre: new FormControl('', [Validators.required]),
      fechaNacimiento: new FormControl(''),
      curp: new FormControl('')
    }),
    padre: new FormGroup({
      nombre: new FormControl('', [Validators.required]),
      telefono: new FormControl(''),
      email: new FormControl('', [Validators.email])
    }),
    madre: new FormGroup({
      nombre: new FormControl(''),
      telefono: new FormControl(''),
      email: new FormControl('', [Validators.email])
    }),
    solicitud: new FormGroup({
      nivelSolicitado: new FormControl('', [Validators.required]),
      gradoSolicitado: new FormControl('', [Validators.required]),
      plantelSolicitado: new FormControl('', [Validators.required])
    }),
    documentos: new FormGroup({
      tieneCurp: new FormControl(false),
      tieneActa: new FormControl(false),
      tieneCertificado: new FormControl(false)
    })
  });
  
  async guardarPreinscripcion() {
    if (this.formPrincipal.invalid) {
      this.notify.error('Completa todos los campos requeridos');
      return;
    }
    
    try {
      const response = await this.api.post('/preinscripciones', {
        ...this.formPrincipal.value,
        requiereEstudioSocioeconomico: !this.tieneHermanoInscrito(),
        estatus: 'REGISTRADA'
      }).toPromise();
      
      this.numeroSolicitud = response.numero_solicitud;
      this.notify.success(`Pre-inscripción registrada: ${this.numeroSolicitud}`);
      this.generarComprobante(); // PDF
      
    } catch (error) {
      this.notify.error('Error al registrar pre-inscripción');
    }
  }
  
  generarComprobante() {
    // Generar PDF con número de solicitud y próximos pasos
  }
}
```

---

#### **FASE 2: Evaluaciones (Psicométrica, Académica, Socioeconómica)**

**Documentos utilizados:**
- **FSEIAL-05:** Listado de posibles candidatos (clasificación preliminar)
- **FSEIAL-06:** Resultados de evaluaciones (psicométrica, académica, socio-económica)
- **FSEIAL-07:** Citas para evaluaciones

**Flujo Actual (Manual):**
1. Coordinación Académica recibe expedientes y los clasifica
2. Se realiza estudio socio-económico (empresa externa)
3. Se aplican evaluaciones psicométricas y académicas
4. Se generan bases de datos con resultados
5. Se reúnen todos los evaluadores para tomar decisión final

**Requisitos ADES:**

```sql
-- Tabla: Evaluaciones de Ingreso
CREATE TABLE ades_evaluaciones_ingreso (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    preinscripcion_id UUID NOT NULL REFERENCES ades_preinscripciones(id),
    
    -- Evaluación Psicométrica
    fecha_evaluacion_psico DATE,
    evaluador_psico_id UUID REFERENCES ades_usuarios(id),
    resultado_psico_score NUMERIC(5,2), -- 0-100
    resultado_psico_clasificacion VARCHAR(30), -- EXCELENTE, BUENO, REGULAR, BAJO
    notas_psico TEXT,
    
    -- Evaluación Académica
    fecha_evaluacion_acad DATE,
    evaluador_acad_id UUID REFERENCES ades_usuarios(id),
    resultado_acad_score NUMERIC(5,2),
    resultado_acad_clasificacion VARCHAR(30),
    notas_acad TEXT,
    
    -- Evaluación Socioeconómica (empresa externa)
    fecha_evaluacion_socio DATE,
    evaluador_socio_empresa VARCHAR(100),
    resultado_socio_clasificacion VARCHAR(30), -- PERFIL_1 (dentro), PERFIL_2 (puede ser), PERFIL_3 (fuera)
    resultado_socio_score NUMERIC(5,2),
    
    -- Decisión Final
    decision_final VARCHAR(30), -- ACEPTADO, RESERVA, RECHAZADO
    score_promedio NUMERIC(5,2), -- (psico + acad + socio) / 3
    fecha_decision DATE,
    creador_decision_id UUID REFERENCES ades_usuarios(id),
    
    created_at TIMESTAMP DEFAULT NOW()
);

-- Tabla: Entrevista Psicológica
CREATE TABLE ades_entrevistas_psicologicas (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    evaluacion_ingreso_id UUID REFERENCES ades_evaluaciones_ingreso(id),
    evaluador_id UUID REFERENCES ades_usuarios(id),
    
    -- Preguntas de la prueba psicométrica
    escala_inteligencia NUMERIC(3,1), -- IQ estimation
    ajuste_emocional VARCHAR(50), -- EXCELENTE, BUENO, REGULAR, BAJO
    adaptacion_social VARCHAR(50),
    capacidad_aprendizaje VARCHAR(50),
    
    -- Recomendaciones
    recomendaciones TEXT,
    necesita_apoyo_psicologico BOOLEAN DEFAULT FALSE,
    requiere_evaluacion_especialista BOOLEAN DEFAULT FALSE,
    
    created_at TIMESTAMP DEFAULT NOW()
);
```

**Componente: Panel de Evaluaciones (para coordinadores)**

```typescript
// evaluaciones-ingreso.component.ts
export class EvaluacionesIngresoComponent {
  
  preinscripcionesEnEvaluacion: Preinscripcion[] = [];
  
  ngOnInit() {
    this.cargarPendientes();
  }
  
  cargarPendientes() {
    this.api.get('/preinscripciones?estatus=EVALUANDO').subscribe(data => {
      this.preinscripcionesEnEvaluacion = data;
    });
  }
  
  // Abrir dialog para ingresar resultado psicométrico
  abrirDialogPsico(preinscripcion: Preinscripcion) {
    const dialogRef = this.dialog.open(DialogEvaluacionPsicoComponent, {
      data: { preinscripcion }
    });
    
    dialogRef.afterClosed().subscribe(resultado => {
      if (resultado) {
        this.guardarEvaluacionPsico(preinscripcion.id, resultado);
      }
    });
  }
  
  // Calcular score promedio
  calcularScorePromedio(evaluacion: EvaluacionIngreso): number {
    const scores = [
      evaluacion.resultado_psico_score || 0,
      evaluacion.resultado_acad_score || 0,
      evaluacion.resultado_socio_score || 0
    ].filter(s => s > 0);
    
    return scores.reduce((a, b) => a + b, 0) / scores.length;
  }
  
  // Junta de decisión final
  abrirJuntaDecision() {
    // Mostrar tabla de candidatos con scores
    // Permitir seleccionar ACEPTADO / RESERVA / RECHAZADO
  }
}
```

---

#### **FASE 3: Inscripción Oficial**

**Documentos utilizados:**
- **FSEIAL-09:** Ficha de Inscripción
- **FSEIAL-10:** Ficha Médica (Nuevo Ingreso)
- **FSEIAL-11:** Autorización médica y medicamentos
- **FPUNIUTI-01:** Circular de útiles y uniformes
- **FPUNIUTI-02:** Compromiso de aportaciones
- **FPUNI-03:** Solicitud de uniformes
- **FSEIAL-12 a FSEIAL-17:** Compromisos a la excelencia, documentos de inicio
- **FSEIAL-18:** Listado de documentos de inscripción

**Mapeo de Campos:**

```typescript
// inscripcion.component.ts
interface InscripcionOficial {
  // FSEIAL-09: Ficha de Inscripción
  ficha_inscripcion: {
    numero_lista: number,
    nombre_alumno: string,
    fecha_nacimiento: Date,
    lugar_nacimiento: string,
    // ...más campos
  },
  
  // FSEIAL-10: Ficha Médica
  ficha_medica: {
    grupo_sanguineo: string,
    alergias: string[],
    medicamentos_permitidos: string[],
    enfermedades_cronicas: string[],
    contacto_emergencia: string,
    // ...más campos
  },
  
  // FSEIAL-11: Autorización médica
  autorizacion_medica: {
    autoriza_atencion: boolean,
    autoriza_medicamentos: boolean,
    autoriza_traslado: boolean,
    firma_padre: Date,
    // ...
  },
  
  // FPUNIUTI-01/02: Útiles y uniformes
  pago_uniformes: {
    cantidad_uniforme: number,
    monto_pagado: number,
    forma_pago: 'CONTADO' | 'ABONOS',
    fecha_pago: Date,
    // ...
  },
  
  // FSEIAL-12 a 17: Compromisos
  compromisos: {
    compromiso_excelencia_padres: boolean,
    compromiso_excelencia_alumno: boolean,
    compromiso_convivencia: boolean,
    autorizacion_imagen: boolean,
    // ...
  }
}
```

**Tabla en ADES:**

```sql
-- Tabla: Inscripciones de Nuevo Ingreso
CREATE TABLE ades_inscripciones_nuevas (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    evaluacion_ingreso_id UUID REFERENCES ades_evaluaciones_ingreso(id),
    estudiante_id UUID REFERENCES ades_estudiantes(id), -- Creada al aceptar
    
    -- Documentos en MinIO
    doc_fseial_09_id UUID, -- Ficha inscripción
    doc_fseial_10_id UUID, -- Ficha médica
    doc_fseial_11_id UUID, -- Autorización médica
    doc_fpuniuti_01_id UUID, -- Circular útiles
    doc_fseial_12_id UUID, -- Compromiso excelencia padres
    doc_fseial_13_id UUID, -- Compromiso excelencia alumno
    
    -- Datos capturados
    datos_inscripcion JSONB, -- FSEIAL-09
    datos_medicos JSONB, -- FSEIAL-10
    autorizaciones JSONB, -- FSEIAL-11
    pagos_registrados JSONB, -- FPUNIUTI-02
    
    -- Status
    estatus VARCHAR(30) DEFAULT 'EN_PROCESO', -- EN_PROCESO, COMPLETA, LISTA_PARA_INICIAR
    fecha_inscripcion DATE,
    
    -- Junta de inicio (presencial)
    asistio_junta_inicio BOOLEAN DEFAULT FALSE,
    fecha_junta_inicio DATE,
    
    created_at TIMESTAMP DEFAULT NOW()
);
```

---

#### **FASE 4 y 5: Entrega de Credenciales e Inicio de Curso**

**Documentos:**
- **FSEIAL-19:** Listado de asistencia a junta de inicio
- **FSEIAL-20:** Contrato de prestación de servicios

**Flujo:**
1. Se entregan uniformes y útiles (1 semana antes de iniciar)
2. Se entrega credencial
3. Se realiza junta presencial de inicio
4. Se firma contrato de servicios

---

### 1.2 Proceso de Reinscripción (3 Fases)

Basado en: `diagrama_reinscripción.pdf`

#### **FASE 1: Evaluación Previa**

**Actores:**
- Dirección del Plantel
- Coordinación Académica
- Psicología (académica y comunitaria)

**Flujo:**
1. Dirección se reúne con psicólogos y coordinación académica
2. Evalúan si algún alumno no podría continuar
3. Se determina si hay circunstancias especiales

**Requisitos ADES:**

```typescript
// reinscripcion.service.ts
async validarReinscripcion(grupoId: UUID, cicloAnterior: UUID, cicloNuevo: UUID) {
  
  // Obtener alumnos del grupo anterior
  const alumnosActuales = await this.api.get(`/grupos/${grupoId}/alumnos?ciclo=${cicloAnterior}`);
  
  // Evaluar cada alumno
  const validaciones = alumnosActuales.map(alumno => ({
    alumno_id: alumno.id,
    nombre: alumno.nombre,
    
    // Criterios académicos
    promedio_general: await this.calcularPromedio(alumno.id, cicloAnterior),
    requiere_extraordinario: false, // Si promedio < 6
    tiene_extraordinario_aprobado: false,
    
    // Criterios de conducta
    reportes_disciplina: await this.contarReportes(alumno.id, cicloAnterior),
    requiere_revision_psicologica: await this.evaluarConducta(alumno.id),
    
    // Criterios de asistencia
    porcentaje_asistencia: await this.calcularAsistencia(alumno.id, cicloAnterior),
    
    // Status de reinscripción
    puede_reinscribirse: true, // Default, cambiar si hay problemas
    observaciones: '' // Notas sobre la decisión
  }));
  
  return validaciones;
}
```

---

#### **FASE 2: Notificación y Junta de Reinscripción**

**Documentos:**
- **REOBS-01:** Solicitud de reinscripción
- **REIN-01:** Ficha de Reinscripción
- **REIN-02:** Cuestionario Médico de Reinscripción
- **FSEIAL-11:** Autorización médica
- **FPUNIUTI-01/02:** Útiles y uniformes
- **FSEIAL-12 a FSEIAL-17:** Compromisos renovados
- **FSEIAL-18:** Listado de documentos

**Flujo Actual (Manual):**
1. Coordinación Administrativa notifica a padres (Facebook)
2. Padres asisten a junta
3. Se explica renovación de documentos
4. Se completan fichas y compromisos

**Requisitos ADES:**

```sql
-- Tabla: Reinscripciones
CREATE TABLE ades_reinscripciones (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    estudiante_id UUID NOT NULL REFERENCES ades_estudiantes(id),
    ciclo_anterior_id UUID REFERENCES ades_ciclos_escolares(id),
    ciclo_nuevo_id UUID REFERENCES ades_ciclos_escolares(id),
    
    -- Documentos
    doc_reobs_01_id UUID,
    doc_rein_01_id UUID,
    doc_rein_02_id UUID,
    doc_compromisos_id UUID,
    
    -- Datos médicos actualizados (REIN-02)
    grupo_sanguineo VARCHAR(5),
    alergias TEXT,
    medicamentos TEXT,
    
    -- Pagos
    pago_uniformes_registrado BOOLEAN DEFAULT FALSE,
    fecha_pago_uniformes DATE,
    monto_pagado NUMERIC(10,2),
    
    -- Status
    estatus VARCHAR(30) DEFAULT 'PENDIENTE', -- PENDIENTE, VALIDADA, COMPLETADA, RECHAZADA
    fecha_reinscripcion DATE,
    junta_asistida BOOLEAN DEFAULT FALSE,
    
    -- Nuevaasignación de grado
    grado_nuevo_id UUID REFERENCES ades_grados(id), -- Promovido
    grupo_nuevo_id UUID REFERENCES ades_grupos(id),
    
    created_at TIMESTAMP DEFAULT NOW()
);

-- Vista para estadísticas de reinscripción
CREATE VIEW ades_v_reinscripcion_stats AS
SELECT 
    ciclo_nuevo_id,
    COUNT(*) as total_alumnos,
    COUNT(CASE WHEN estatus = 'COMPLETADA' THEN 1 END) as completadas,
    COUNT(CASE WHEN estatus = 'PENDIENTE' THEN 1 END) as pendientes,
    COUNT(CASE WHEN estatus = 'RECHAZADA' THEN 1 END) as rechazadas
FROM ades_reinscripciones
GROUP BY ciclo_nuevo_id;
```

**Componente: Junta de Reinscripción**

```typescript
// junta-reinscripcion.component.ts
export class JuntaReinscripcionComponent {
  
  alumnosReinscripcion: AlumnoReinscripcion[] = [];
  
  // Workflow de junta
  step = signal<1 | 2 | 3 | 4>(1); // 1: recepción, 2: documentos, 3: pagos, 4: cierre
  
  // Step 1: Recepción y datos médicos
  formularioMedico = new FormGroup({
    grupoSanguineo: new FormControl(''),
    alergias: new FormControl(''),
    medicamentos: new FormControl(''),
    // ... más campos
  });
  
  // Step 2: Documentos
  documentosFirmados = {
    rein_01: false,
    rein_02: false,
    fseial_12: false,
    fseial_17: false
  };
  
  // Step 3: Pagos
  pagoUniformes = {
    monto: 0,
    forma: 'CONTADO',
    fecha: new Date()
  };
  
  // Procesar junta
  async procesarJunta() {
    const reinscripcion: Reinscripcion = {
      estudiante_id: this.alumnoActual.id,
      ciclo_anterior_id: this.cicloAnterior.id,
      ciclo_nuevo_id: this.cicloNuevo.id,
      datos_medicos: this.formularioMedico.value,
      documentos: this.documentosFirmados,
      pagos: this.pagoUniformes,
      estatus: 'COMPLETADA',
      junta_asistida: true,
      // ... más datos
    };
    
    await this.api.post('/reinscripciones', reinscripcion).toPromise();
    this.notify.success('Reinscripción completada');
  }
}
```

---

## 2. Estructura de Datos Reales (Basada en Excel de Calificaciones)

### 2.1 Hojas en Archivos de Calificaciones

```
Calificaciones_1A.xlsx (Primero Primaria)
├── DATOS ..................... Info del grupo (grado, plantel, trimestre)
├── ASISTENCIA ................ Registro diario de presencia
├── DISCIPLINA ................ Reportes de conducta
├── ESPAÑOL (materia) ......... Calificaciones por período
├── Español_Eval_Semanal ...... Evaluaciones semanales (detalle)
├── MATEMATICAS ............... Calificaciones por período
├── Matematicas_Eval_Semanal .. Evaluaciones semanales (detalle)
├── CONOCIMIENTO_DEL_MEDIO .... Materia oficial
├── FORMACION_CyE ............ Formación Cívica y Ética (oficial)
├── INGLES .................... Materia oficial (docente diferente)
├── EDUCACION_FISICA .......... Materia oficial
├── ARTES ..................... Materia oficial
├── SOCIOEMOCIONAL ........... Materia NEVADI (formativa)
├── DESARROLLO_COMUNITARIO ... Materia NEVADI (enriquecimiento)
├── COMPUTACION ............... Materia NEVADI (enriquecimiento)
└── TOTALES ................... Consolidado de calificaciones
```

### 2.2 Estructura de Datos por Hoja

**Hoja DATOS:**
```
NUM. LISTA | NOMBRE DEL ALUMNO | DATOS DEL PLANTEL: Instituto Nevadi | TENANCINGO PRIMARIA
          |                   |                   Grado: Primero | Grupo: A
          |                   |                   Trimestre: Tercero
          |                   | MATERIA | DOCENTE
          |                   | ASISTENCIA | Diana Itzel Espinoza
```

**Hoja de Materia (ej: ESPAÑOL):**
```
#  Alumno    | Actividad1 | Actividad2 | Examen | Proyecto | Promedio
1  Arce Mata |    9.5     |    8.0     |  9.0   |   8.5    |  8.7
2  Arevalo   |    8.0     |    7.5     |  8.5   |   8.0    |  8.0
...
```

**Mapeo a BD ADES:**

```sql
-- Una hoja = Una materia × período × grupo
-- Filas = Alumnos
-- Columnas = Actividades/evaluaciones

-- Tabla: Calificaciones por Actividad
CREATE TABLE ades_calificaciones_actividades (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    materia_id UUID REFERENCES ades_materias(id),
    grupo_id UUID REFERENCES ades_grupos(id),
    periodo_evaluacion_id UUID REFERENCES ades_periodos_evaluacion(id),
    
    -- Actividad (fila en Excel)
    numero_orden INT, -- 1, 2, 3... corresponde a columnas B, C, D...
    nombre_actividad VARCHAR(100), -- "Actividad1", "Tarea 1", "Examen", etc.
    tipo_actividad VARCHAR(20), -- TAREA, EXAMEN, PROYECTO, PARTICIPACION
    fecha_asignacion DATE,
    fecha_entrega DATE,
    ponderacion NUMERIC(3,2), -- Peso en cálculo de promedio
    
    created_at TIMESTAMP DEFAULT NOW()
);

-- Tabla: Calificaciones (intersection)
CREATE TABLE ades_calificaciones_actividad_alumno (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    calificacion_actividad_id UUID REFERENCES ades_calificaciones_actividades(id),
    estudiante_id UUID REFERENCES ades_estudiantes(id),
    
    score NUMERIC(3,1) NOT NULL CHECK (score BETWEEN 0 AND 10), -- 0-10
    fecha_calificacion TIMESTAMP,
    calificado_por UUID REFERENCES ades_usuarios(id),
    
    -- Auditoría
    row_version INTEGER DEFAULT 1,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);
```

---

## 3. Casos de Uso Derivados de Procesos Reales

### CU-INSC-001: Crear Preinscripción

**Actor:** Padre de familia (a través de portal o presencial)  
**Precondiciones:** Convocatoria abierta  
**Flujo:**
1. Padre accede al formulario de preinscripción (FSEIAL-02)
2. Completa datos: aspirante, padres, solicitud, documentos
3. Sistema genera número de solicitud
4. Se genera comprobante en PDF
5. Se notifica a coordinación administrativa

**Postcondiciones:**
- Preinscripción creada en BD
- Email de confirmación a padre
- Coordinadora ve nueva pre-inscripción en lista

---

### CU-REINSC-001: Validar Reinscripción de Alumnos

**Actor:** Coordinación Académica + Psicología  
**Precondiciones:** Fin de ciclo escolar  
**Flujo:**
1. Sistema genera listado de alumnos del ciclo anterior
2. Se calcula: promedio, reportes disciplina, asistencia
3. Coordinadora marca: "Puede reinscribirse" o "Revisión especial"
4. Se notifica a padres de alumnos en revisión

**Postcondiciones:**
- Listado de validación completado
- Notificaciones enviadas a padres

---

### CU-REINSC-002: Junta de Reinscripción (Presencial)

**Actor:** Coordinación Administrativa  
**Precondiciones:** Padre asiste a junta  
**Flujo:**
1. Coordinadora abre formulario de junta en tablet/laptop
2. Introduce: datos médicos (REIN-02), pagos (FPUNIUTI-02), firma compromisos
3. Sistema calcula próxima asignación de grado/grupo
4. Se marca junta como completada
5. Se genera constancia

**Postcondiciones:**
- Reinscripción completada
- Alumno listo para ciclo nuevo
- Constancia generada en PDF

---

## 4. Mejoras Operacionales a Implementar

### 4.1 Automatizaciones Propuestas

| Proceso Actual | Automatización Propuesta | Beneficio | Fase |
|---|---|---|---|
| **Pre-inscripción (papel)** | Portal web + forma online | Disponibilidad 24/7, sin errores de transcripción | 28 |
| **Evaluaciones en Excel** | Sistema de evaluaciones integrado | Cálculo automático, estadísticas en vivo | 30 |
| **Generación de reportes** | Dashboards automáticos | Acceso instantáneo, no requiere compilación | 16 |
| **Notificación a padres (Facebook)** | Notificaciones inteligentes (SMS+Email+App) | Garantía de entrega, historial | 20 |
| **Junta de reinscripción (papel)** | Formulario digital + tablet | Documentación automática, firma digital | 28 |
| **Pago de útiles/uniformes (manual)** | Integración con sistema de cobranza | Sincronización automática de pagos | 31 |

### 4.2 Cambios Propuestos en Flujos

**Actual:** Preinscripción → Evaluaciones externas → Junta inscripción (3-4 meses)  
**Propuesto:** Preinscripción online → Evaluaciones integradas → Junta híbrida (online + presencial opcional)

**Actual:** Reinscripción masiva (1-2 semanas de puro trámite)  
**Propuesto:** Reinscripción automatizada (3-4 días, con auditoría digital)

---

## 5. Checklist de Documentos a Digitalizar (FASE 28)

### Documentos Críticos para Expediente Digital

- [ ] FSEIAL-01 → Listado preinscripción (PDF escaneado)
- [ ] FSEIAL-02 → Solicitud preinscripción (PDF digital + formulario web)
- [ ] FSEIAL-05 → Candidatos clasificados (PDF escaneado)
- [ ] FSEIAL-06 → Evaluaciones (PDF digital + BD estructurada)
- [ ] FSEIAL-09 → Ficha inscripción (PDF digital + formulario)
- [ ] FSEIAL-10 → Ficha médica (PDF digital + datos estructurados)
- [ ] FSEIAL-11 → Autorizaciones médicas (PDF firmado digitalmente)
- [ ] FSEIAL-12 a 20 → Compromisos (PDF digital + registro en BD)
- [ ] REIN-01/02 → Reinscripción (PDF digital + formulario)

**Total: 15+ documentos mapeados a ADES**

---

## 6. Próximos Pasos Inmediatos

**Para Julio 2026:**
1. Revisar diagramas detalladamente con Coordinación Administrativa
2. Mapear campos exactos de cada FSEIAL/REIN/FPUNIUTI a tabla BD
3. Diseñar formularios web equivalentes a papeles
4. Definir autoridades de firma (digital + manual)
5. Validar con muestras reales de datos

---

**Preparado por:** Equipo de Arquitectura ADES  
**Última actualización:** Junio 2026  
**Próxima revisión:** Julio 2026 (feedback coordinadores)
