# ADES Nevadi — Especificaciones Técnicas de Componentes APEX
## Grids Editables, Tipos de Materias y Requisitos de Interfaz

**Versión:** 1.0  
**Fecha:** Junio 2026  
**Enfoque:** Requisitos funcionales APEX-style, validación en tiempo real, UX fluida

---

## 1. Componentes APEX Requeridos en ADES

### 1.1 Grid Editable (Oracle APEX Pattern: Interactive Grid)

**Objetivo:** Captura de datos en modo spreadsheet (sin navegar a formularios individuales)

#### Ubicaciones en ADES Donde Se Requiere

| Módulo | Funcionalidad | Estructura |
|--------|---------------|-----------|
| **GRADEBOOK** | Captura de calificaciones por alumno | Alumnos (filas) × Actividades (columnas) |
| **ASISTENCIA** | Registro diario de presencia | Alumnos (filas) × Fechas de clase (columnas) |
| **EVALUACIONES** | Ingreso de resultados de exámenes | Alumnos (filas) × Preguntas/Criterios (columnas) |
| **TAREAS ENTREGAS** | Calificación de entregas masiva | Entregas (filas) × Criterios rúbrica (columnas) |
| **ADMIN USUARIOS** | Asignación de roles y permisos | Usuarios (filas) × Roles/Permisos (columnas) |
| **HORARIOS** | Grid semanal de clases | Horas (filas) × Días (columnas) |

#### Especificación Técnica: Grid de Calificaciones

**Workflow:**
1. Usuario selecciona: **Nivel** → **Grado** → **Grupo** → **Materia** → **Período**
2. Sistema carga: **Lista de alumnos (filas)** + **Actividades/Criterios (columnas)**
3. Cada celda es **editable inline** (click → input text → guardar con Enter o blur)
4. Validaciones en tiempo real (rango 0-10, solo números)

**Estructura del Componente:**

```
┌─────────────────────────────────────────────────────────────────────┐
│ GRADEBOOK — 1°A Primaria · Español · Periodo 1                     │
├─────────────────────────────────────────────────────────────────────┤
│ Filtros: [Nivel▼] [Grado▼] [Grupo▼] [Materia▼] [Período▼]         │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  #  Alumno         │ Tarea1 │ Tarea2 │ Examen │ Proyecto │ TOTAL  │
│  ────────────────────────────────────────────────────────────────  │
│  1  Arce Mata      │  9.5   │  8.0   │   9.0  │   8.5   │  8.7   │
│  2  Arevalo Monr.  │  8.0   │  7.5   │   8.5  │   8.0   │  8.0   │
│  3  Barrera Vás.   │ [8.5]* │  9.0   │   9.5  │   9.0   │  9.0   │
│  4  Becerril Ros.  │  7.5   │  8.5   │   7.0  │   7.8   │  7.7   │
│  ...               │        │        │        │         │        │
│                                                                     │
├─────────────────────────────────────────────────────────────────────┤
│ * Celda activa (en edición)                                        │
│ [Guardar] [Cancelar] [Exportar CSV] [Imprimir] [Cerrar Período]   │
└─────────────────────────────────────────────────────────────────────┘
```

**Requisitos de Implementación:**

| Aspecto | Especificación |
|--------|-----------------|
| **Librería** | PrimeNG `<p-table>` con `editableColumn` |
| **Validación** | Rango 0-10, decimales .0-.9, campo requerido |
| **Eventos** | `onEditComplete` → guardar en BD via API |
| **Performance** | Lazy load (max 100 filas visibles) + virtualization |
| **Feedback visual** | ✅ Checkmark en guardar, ❌ Error tooltip en fallo |
| **Permisos** | Solo DOCENTE + COORD_ACADEMICO pueden editar |
| **Auditoría** | Cada cambio registra: quién, qué, cuándo, valor anterior/nuevo |
| **Offline** | Valkey cache local, sync al conectar (opcional F27) |

#### Componente Angular TypeScript

```typescript
// gradebook.component.ts
export class GradebookComponent implements OnInit {
  
  // Estado del grid
  alumnos: Alumno[] = [];
  actividades: Actividad[] = [];
  calificaciones: Map<string, number> = new Map();
  
  // Filtros
  nivelActivo = signal<UUID>('');
  gradoActivo = signal<UUID>('');
  grupoActivo = signal<UUID>('');
  materiaActiva = signal<UUID>('');
  periodoActivo = signal<UUID>('');
  
  constructor(private api: ApiService, private notify: ApexNotificationService) {}
  
  ngOnInit() {
    // Cargar inicial
    this.cargarAlumnos();
    this.cargarActividades();
  }
  
  // Cargar alumnos del grupo seleccionado
  cargarAlumnos() {
    this.api.get(`/grupos/${this.grupoActivo()}/alumnos`).subscribe(
      (data) => { this.alumnos = data; }
    );
  }
  
  // Cargar actividades de la materia/período
  cargarActividades() {
    this.api.get(`/materias/${this.materiaActiva()}/actividades?periodo=${this.periodoActivo()}`)
      .subscribe((data) => { this.actividades = data; });
  }
  
  // Evento: cell edit complete
  async onCellEditComplete(event: any, alumnoId: UUID, actividadId: UUID) {
    const nuevoValor = parseFloat(event.data);
    
    // Validación
    if (nuevoValor < 0 || nuevoValor > 10) {
      this.notify.error('Calificación debe estar entre 0 y 10');
      return;
    }
    
    // Guardar en BD
    try {
      await this.api.patch(`/calificaciones/${alumnoId}/${actividadId}`, {
        score: nuevoValor,
        row_version: this.getRowVersion(alumnoId, actividadId)
      }).toPromise();
      
      this.notify.success(`Calificación guardada: ${nuevoValor}`);
      this.calificaciones.set(`${alumnoId}_${actividadId}`, nuevoValor);
      
    } catch (error) {
      this.notify.error('Error al guardar. Intenta de nuevo.');
    }
  }
  
  // Exportar a Excel/CSV
  exportarCSV() {
    const csv = this.generarCSV();
    // descargar
  }
  
  // Guardar todo (bulk save para cierre de período)
  async guardarTodo() {
    const payload = Array.from(this.calificaciones.entries()).map(([key, val]) => {
      const [alumnoId, actividadId] = key.split('_');
      return { alumno_id: alumnoId, actividad_id: actividadId, score: val };
    });
    
    await this.api.post(`/gradebook/bulk-save`, payload).toPromise();
    this.notify.success('Todas las calificaciones guardadas');
  }
}
```

#### Template Angular HTML

```html
<!-- gradebook.component.html -->
<div class="gradebook-container">
  <h2>Libreta de Calificaciones</h2>
  
  <!-- FILTROS -->
  <div class="filtros">
    <p-select 
      [options]="niveles" 
      [(ngModel)]="nivelActivo()" 
      optionLabel="nombre"
      optionValue="id"
      (onChange)="cargarGrados()"
      placeholder="Nivel educativo">
    </p-select>
    
    <p-select 
      [options]="grados" 
      [(ngModel)]="gradoActivo()" 
      (onChange)="cargarGrupos()"
      placeholder="Grado">
    </p-select>
    
    <p-select 
      [options]="grupos" 
      [(ngModel)]="grupoActivo()" 
      (onChange)="cargarAlumnos()"
      placeholder="Grupo">
    </p-select>
    
    <p-select 
      [options]="materias" 
      [(ngModel)]="materiaActiva()" 
      (onChange)="cargarActividades()"
      placeholder="Materia"
      [attr.data-testid]="'select-materia'">
    </p-select>
    
    <p-select 
      [options]="periodos" 
      [(ngModel)]="periodoActivo()" 
      (onChange)="cargarActividades()"
      placeholder="Período">
    </p-select>
  </div>
  
  <!-- GRID EDITABLE -->
  <div class="gradebook-grid">
    <p-table 
      [value]="alumnos" 
      [scrollable]="true"
      scrollHeight="70vh"
      responsiveLayout="scroll">
      
      <!-- Columna #: Número de lista -->
      <p-column field="numero_lista" header="#" [style]="{'width':'40px'}"></p-column>
      
      <!-- Columna Alumno: Nombre (no editable) -->
      <p-column field="nombre" header="Alumno" [style]="{'width':'200px'}"></p-column>
      
      <!-- Columnas dinámicas por actividad (EDITABLES) -->
      <p-column 
        *ngFor="let actividad of actividades" 
        [field]="'cal_' + actividad.id"
        [header]="actividad.nombre"
        [style]="{'width':'80px'}"
        [editable]="canEdit()">
        
        <ng-template pTemplate="body" let-rowData>
          {{ getCalificacion(rowData.id, actividad.id) }}
        </ng-template>
        
        <ng-template pTemplate="editor" let-rowData>
          <input 
            type="number" 
            min="0" 
            max="10" 
            step="0.5"
            pInputText 
            [ngModel]="getCalificacion(rowData.id, actividad.id)"
            (onblur)="onCellEditComplete($event, rowData.id, actividad.id)">
        </ng-template>
      </p-column>
      
      <!-- Columna TOTAL: Promedio (calculado, no editable) -->
      <p-column 
        field="total" 
        header="Total" 
        [style]="{'width':'80px'}"
        sortable="true">
        <ng-template pTemplate="body" let-rowData>
          <strong>{{ calcularPromedio(rowData.id) | number:'1.2-2' }}</strong>
        </ng-template>
      </p-column>
    </p-table>
  </div>
  
  <!-- ACCIONES -->
  <div class="acciones">
    <button pButton label="Guardar Todo" icon="pi pi-save" 
            (click)="guardarTodo()" class="p-button-success"></button>
    <button pButton label="Exportar CSV" icon="pi pi-download" 
            (click)="exportarCSV()" class="p-button-info"></button>
    <button pButton label="Imprimir" icon="pi pi-print" 
            (click)="imprimir()" class="p-button-secondary"></button>
    <button pButton label="Cerrar Período" icon="pi pi-lock" 
            (click)="cerrarPeriodo()" class="p-button-warning"></button>
  </div>
</div>
```

---

## 2. Taxonomía de Materias en ADES

### 2.1 Clasificación de Materias

**Problema:** ADES debe distinguir entre **materias oficiales** (dictadas por SEP/UAEMEX) y **materias Nevadi** (ofrecidas únicamente por el Instituto).

#### Tipos de Materias

| Categoría | Descripción | Ejemplo | Reportaje a | Ponderación |
|-----------|-------------|---------|-----------|------------|
| **OFICIAL_SEP_PRIMARIA** | Materias curriculo oficial SEP primaria (NEM) | Español, Matemáticas, Conocimiento del Medio | ✅ SEP | ✅ Boleta oficial |
| **OFICIAL_SEP_SECUNDARIA** | Materias curriculo oficial SEP secundaria (NEM) | Español, Matemáticas, Ciencias | ✅ SEP | ✅ Boleta oficial |
| **OFICIAL_UAEMEX_PREP** | Materias curriculo oficial UAEMEX preparatoria (CBU 2024) | Matemáticas, Química, Historia | ✅ UAEMEX | ✅ Boleta oficial |
| **NEVADI_FORMATIVA** | Materias formativas Nevadi (desarrollo integral) | Socioemocional, Desarrollo Comunitario, Valores | ❌ No reporta | ❌ Opcional en boleta |
| **NEVADI_ENRIQUECIMIENTO** | Materias de enriquecimiento (clubs, talleres) | Computación, Artes, Música, Deportes | ❌ No reporta | ❌ Opcional en boleta |
| **NEVADI_ESPECIALIZADA** | Materias especializadas (enseñanza de idiomas, técnica) | Inglés avanzado, Programación | Parcial | ✅ Boleta si es oficial |

#### Ejemplo de Estructura en BD

```sql
-- Tabla ades_materias con clasificación
CREATE TABLE ades_materias (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    nombre VARCHAR(100) NOT NULL,
    codigo_sep VARCHAR(20), -- Código SEP/UAEMEX si aplica
    tipo_materia VARCHAR(50) NOT NULL, -- OFICIAL_SEP_PRIMARIA, NEVADI_FORMATIVA, etc.
    nivel_educativo_id UUID REFERENCES ades_niveles_educativos(id),
    es_obligatoria BOOLEAN DEFAULT TRUE,
    reporta_a_sep_uaemex BOOLEAN DEFAULT FALSE,
    incluir_en_boleta BOOLEAN DEFAULT TRUE,
    horas_semanales NUMERIC(4,2),
    ponderacion_default NUMERIC(3,2), -- Peso en promedio
    descripcion TEXT,
    created_at TIMESTAMP DEFAULT NOW()
);

-- Vista para materias oficiales (reportables)
CREATE VIEW ades_v_materias_oficiales AS
SELECT * FROM ades_materias
WHERE tipo_materia IN ('OFICIAL_SEP_PRIMARIA', 'OFICIAL_SEP_SECUNDARIA', 'OFICIAL_UAEMEX_PREP');

-- Vista para materias Nevadi
CREATE VIEW ades_v_materias_nevadi AS
SELECT * FROM ades_materias
WHERE tipo_materia LIKE 'NEVADI_%';
```

#### Impacto en Diferentes Módulos

**GRADEBOOK:**
- Mostrar materias **oficiales** en boleta oficial
- Mostrar materias **Nevadi** en sección separada (opcional)

**PLANES DE ESTUDIO:**
- Filtro: mostrar solo materias oficiales por nivel
- Sublista: mostrar materias Nevadi disponibles

**REPORTES A SEP/UAEMEX:**
- Excluir materias Nevadi
- Solo reportar calificaciones de **OFICIAL_***

**BOLETA PDF:**
- **Primera sección:** Materias oficiales (calificaciones, fórmulas SEP/UAEMEX)
- **Segunda sección (opcional):** Materias Nevadi (informativo, sin afectar promedio oficial)

### 2.2 Componente de Selección de Materias

```typescript
// materias-selector.component.ts
export class MateriasSelectorComponent {
  
  materiasPorTipo: Map<string, Materia[]> = new Map();
  
  ngOnInit() {
    // Cargar materias y agrupar por tipo
    this.api.get('/materias').subscribe((materias: Materia[]) => {
      materias.forEach(m => {
        if (!this.materiasPorTipo.has(m.tipo_materia)) {
          this.materiasPorTipo.set(m.tipo_materia, []);
        }
        this.materiasPorTipo.get(m.tipo_materia)!.push(m);
      });
    });
  }
  
  // Getter para materias oficiales
  get materiasOficiales() {
    return [
      ...(this.materiasPorTipo.get('OFICIAL_SEP_PRIMARIA') || []),
      ...(this.materiasPorTipo.get('OFICIAL_SEP_SECUNDARIA') || []),
      ...(this.materiasPorTipo.get('OFICIAL_UAEMEX_PREP') || [])
    ];
  }
  
  // Getter para materias Nevadi
  get materiasNevadi() {
    return Array.from(this.materiasPorTipo.values())
      .flat()
      .filter(m => m.tipo_materia.startsWith('NEVADI_'));
  }
}
```

```html
<!-- materias-selector.component.html -->
<div class="materias-selector">
  <fieldset>
    <legend>MATERIAS OFICIALES (SEP/UAEMEX)</legend>
    <div class="materias-list">
      <label *ngFor="let materia of materiasOficiales" class="materia-checkbox">
        <input type="checkbox" [(ngModel)]="materiasSeleccionadas" [value]="materia.id">
        {{ materia.nombre }}
        <span class="oficial-badge">OFICIAL</span>
      </label>
    </div>
  </fieldset>
  
  <fieldset>
    <legend>MATERIAS NEVADI (Enriquecimiento)</legend>
    <small>Estas materias son complementarias y NO afectan el promedio oficial</small>
    <div class="materias-list">
      <label *ngFor="let materia of materiasNevadi" class="materia-checkbox">
        <input type="checkbox" [(ngModel)]="materiasSeleccionadas" [value]="materia.id">
        {{ materia.nombre }}
        <span class="nevadi-badge">NEVADI</span>
      </label>
    </div>
  </fieldset>
</div>
```

---

## 3. Flujos de Inscripción y Reinscripción Mapeados

### 3.1 Flujo de Inscripción (Datos Reales del Instituto)

Basado en diagramas proporcionados (Diagrama_inscripción.pdf):

**Fases:**
1. **Pre-inscripción:** Captación y evaluación de candidatos
2. **Evaluaciones:** Psicométrica, académica, socioeconómica
3. **Inscripción oficial:** Firma de documentos y pago de cuotas
4. **Inducción:** Entrega de uniformes y credenciales

**Formatos Mapeados a ADES:**
- `FSEIAL-01`: Listado de pre-inscritos
- `FSEIAL-02`: Solicitud de preinscripción
- `FSEIAL-05`: Candidatos clasificados
- `FSEIAL-06`: Evaluaciones (psicométrica, académica, socioeconómica)
- `FSEIAL-09`: Ficha de Inscripción
- `FSEIAL-10`: Ficha Médica (Nuevo Ingreso)
- `FSEIAL-11 a FSEIAL-20`: Autorizaciones, compromisos, pagos

**Modelo de Datos:**

```sql
-- Fase 1: Pre-inscripción
CREATE TABLE ades_preinscripcion (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    fecha_solicitud TIMESTAMP NOT NULL,
    nombre_aspirante VARCHAR(150),
    nivel_solicitado_id UUID REFERENCES ades_niveles_educativos(id),
    estado VARCHAR(20), -- REGISTRADO, EVALUANDO, ACEPTADO, RECHAZADO
    documento_fseial_01_id UUID, -- Referencia a archivo
    documento_fseial_02_id UUID
);

-- Fase 2: Evaluaciones
CREATE TABLE ades_evaluaciones_ingreso (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    preinscripcion_id UUID REFERENCES ades_preinscripcion(id),
    evaluacion_psicometrica_id UUID,
    evaluacion_academica_id UUID,
    evaluacion_socioeconomica_id UUID,
    resultado_global VARCHAR(20), -- ACEPTADO, RESERVA, RECHAZADO
    documento_fseial_06_id UUID
);

-- Fase 3: Inscripción oficial
CREATE TABLE ades_inscripciones_nuevas (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    evaluacion_ingreso_id UUID REFERENCES ades_evaluaciones_ingreso(id),
    estudiante_id UUID REFERENCES ades_estudiantes(id),
    fecha_inscripcion TIMESTAMP,
    documentos_firmados JSONB, -- Array de documentos (FSEIAL-09, 10, 11, etc.)
    pagos_registrados JSONB -- útiles, uniformes
);
```

### 3.2 Flujo de Reinscripción (Datos Reales del Instituto)

Basado en diagrama_reinscripción.pdf:

**Fases:**
1. **Validación:** Coordinación Académica/Psicología evalúa si el alumno continúa
2. **Notificación:** Se informa a padres sobre fechas de reinscripción
3. **Reunión de reinscripción:** Recepción y documentos
4. **Expediente:** Se actualiza y archiva

**Formatos Mapeados:**
- `REOBS-01`: Solicitud de reinscripción
- `REIN-01`: Ficha de Reinscripción
- `REIN-02`: Cuestionario Médico de Reinscripción
- `FSEIAL-11`: Autorización médica/medicamentos
- `FPUNIUTI-01/02`: Útiles y uniformes
- `FSEIAL-12 a FSEIAL-18`: Compromisos y documentos

**Workflow Automatizado en ADES:**

```
1. FECHA INICIO REINSCRIPCIÓN (Coordinator triggers)
   ├─ Validar: Buscar alumnos activos del ciclo anterior
   ├─ Checar: adeudos, disciplina, riesgo académico
   ├─ Generar: Listado de "reinscripción recomendada" vs "revisión"
   
2. NOTIFICACIÓN A PADRES (Sistema automático)
   ├─ Email/SMS con fecha de junta
   ├─ Listado de documentos requeridos
   ├─ Enlace a portal para pre-carga de documentos
   
3. JUNTA DE REINSCRIPCIÓN (Coordinación)
   ├─ Recepción de documentos (REIN-01, REIN-02, etc.)
   ├─ Validación de pagos (útiles, uniformes)
   ├─ Actualización de datos médicos
   ├─ Firma de nuevos compromisos (FSEIAL-12 a 17)
   
4. CIERRE DE REINSCRIPCIÓN
   ├─ Crear inscripción del nuevo ciclo
   ├─ Copiar datos académicos históricos
   ├─ Archivar expediente
```

---

## 4. Requisitos de Validación en Tiempo Real

### 4.1 Validaciones en Grids Editables

```typescript
// validators-grid.ts
export const validatorsGrid = {
  
  // Calificaciones: 0-10, decimales .0 a .9
  calificacion: (value: any): string | null => {
    if (value === null || value === undefined || value === '') return null; // Optional
    const num = parseFloat(value);
    if (isNaN(num)) return 'Debe ser un número';
    if (num < 0 || num > 10) return 'Rango: 0-10';
    if (!/^\d+(\.\d)?$/.test(value)) return 'Solo 1 decimal (.0 a .9)';
    return null;
  },
  
  // Asistencia: P (presente), F (falta), R (retardo), J (justificada)
  asistencia: (value: any): string | null => {
    const valid = ['P', 'F', 'R', 'J'];
    return valid.includes(value) ? null : `Valores válidos: ${valid.join(', ')}`;
  },
  
  // Ponderación: 0-100, sumar a 100% en fila
  ponderacion: (value: any, rowValues: any[]): string | null => {
    const num = parseFloat(value);
    if (num < 0 || num > 100) return 'Rango: 0-100';
    const total = rowValues.reduce((a, b) => a + parseFloat(b || 0), 0);
    if (total > 100) return `Total excede 100% (actual: ${total}%)`;
    return null;
  }
};
```

### 4.2 Feedback Visual en Grid

```typescript
// grid-styles.scss
.p-datatable {
  // Celda en edición
  .p-cell-editing input {
    border-color: var(--primary-500);
    background-color: var(--primary-50);
  }
  
  // Error en validación
  input.ng-invalid.ng-touched {
    border-color: var(--red-500);
    background-color: var(--red-50);
    
    &::after {
      content: '⚠️';
      color: var(--red-500);
    }
  }
  
  // Celda guardada exitosamente
  .celda-saved {
    background-color: var(--green-50);
    animation: fadeGreen 1s ease-out;
  }
  
  @keyframes fadeGreen {
    0% { background-color: var(--green-200); }
    100% { background-color: transparent; }
  }
  
  // Celda modificada (pendiente guardar)
  .celda-dirty {
    background-color: var(--yellow-50);
    border-left: 3px solid var(--yellow-500);
  }
}
```

---

## 5. Integración con Módulo de Planes de Estudio

### 5.1 Vista Dinámica de Materias (Oficial vs Nevadi)

```typescript
// planes-estudio.component.ts
export class PlanesEstudioComponent {
  
  // Datos
  planSeleccionado: PlanEstudio;
  materiasPorGrado: Map<UUID, Materia[]> = new Map();
  
  // Tabs
  tabActivo = signal<'oficiales' | 'nevadi' | 'todas'>('oficiales');
  
  // Computeds
  materiasFiltradas = computed(() => {
    const todas = Array.from(this.materiasPorGrado.values()).flat();
    
    switch (this.tabActivo()) {
      case 'oficiales':
        return todas.filter(m => m.tipo_materia.startsWith('OFICIAL_'));
      case 'nevadi':
        return todas.filter(m => m.tipo_materia.startsWith('NEVADI_'));
      default:
        return todas;
    }
  });
  
  // Horas totales (solo oficiales)
  horasTotalesOficiales = computed(() => {
    return this.materiasFiltradas()
      .filter(m => m.reporta_a_sep_uaemex)
      .reduce((sum, m) => sum + m.horas_semanales, 0);
  });
  
  ngOnInit() {
    this.cargarPlanEstudio();
  }
  
  cargarPlanEstudio() {
    this.api.get(`/planes-estudio/${this.planId}`).subscribe(plan => {
      this.planSeleccionado = plan;
      this.cargarMaterias(plan.id);
    });
  }
}
```

```html
<!-- planes-estudio.component.html -->
<div class="planes-estudio">
  <h2>Mapa Curricular: {{ planSeleccionado.nombre }}</h2>
  
  <!-- Tabs: Oficiales vs Nevadi -->
  <p-tabView [(activeIndex)]="tabActivoIndex">
    
    <!-- TAB 1: Materias Oficiales -->
    <p-tabPanel header="Materias Oficiales (SEP/UAEMEX)" leftIcon="pi pi-book">
      <div class="header-info">
        <span class="badge official">{{ materiasFiltradas().length }} materias</span>
        <span class="horas">{{ horasTotalesOficiales() }} horas/semana</span>
        <span class="info">Reportables a {{ planSeleccionado.reporta_a }}</span>
      </div>
      
      <p-table [value]="materiasFiltradas()">
        <p-column field="nombre" header="Materia"></p-column>
        <p-column field="codigo_sep" header="Código"></p-column>
        <p-column field="horas_semanales" header="Horas"></p-column>
        <p-column field="ponderacion_default" header="Ponderación"></p-column>
        <p-column header="Acciones">
          <ng-template pTemplate="body">
            <button pButton icon="pi pi-pencil" class="p-button-sm"></button>
          </ng-template>
        </p-column>
      </p-table>
    </p-tabPanel>
    
    <!-- TAB 2: Materias Nevadi -->
    <p-tabPanel header="Materias Nevadi (Enriquecimiento)" leftIcon="pi pi-star">
      <div class="info-box">
        <p-icon name="pi pi-info-circle"></p-icon>
        <p>Estas materias son complementarias y enriquecen la formación integral del alumno. 
           No afectan el promedio oficial ni se reportan a SEP/UAEMEX.</p>
      </div>
      
      <p-table [value]="materiasFiltradas()">
        <p-column field="nombre" header="Materia"></p-column>
        <p-column field="tipo_materia" header="Tipo">
          <ng-template pTemplate="body" let-row>
            <span [ngClass]="getBadgeClass(row.tipo_materia)">
              {{ row.tipo_materia }}
            </span>
          </ng-template>
        </p-column>
        <p-column header="Descripción" field="descripcion"></p-column>
      </p-table>
    </p-tabPanel>
    
    <!-- TAB 3: Todas las Materias -->
    <p-tabPanel header="Vista Completa" leftIcon="pi pi-list">
      <p-table [value]="materiasFiltradas()">
        <!-- ... similar con todas -->
      </p-table>
    </p-tabPanel>
    
  </p-tabView>
</div>
```

---

## 6. Recomendaciones APEX Pattern Adicionales

### 6.1 Master-Detail para Edición

**Caso:** Editar detalles de una materia con sus temas

```html
<p-table [value]="materias" (onRowSelect)="onMateriaSeleccionada($event)" [rowsPerPageOptions]="[10,20,50]">
  <!-- Master: List of Materias -->
  <p-column field="nombre" header="Materia"></p-column>
  <p-column field="horas_semanales" header="Horas"></p-column>
</p-table>

<!-- Detail: Temas de materia seleccionada -->
<div *ngIf="materiaSeleccionada" class="detalle-materia">
  <h3>Temas: {{ materiaSeleccionada.nombre }}</h3>
  <p-table [value]="materiaSeleccionada.temas" [editable]="true">
    <p-column field="nombre" header="Tema" [editable]="true"></p-column>
    <p-column field="orden" header="Orden"></p-column>
    <p-column field="horas_estimadas" header="Horas" [editable]="true"></p-column>
  </p-table>
</div>
```

### 6.2 LOV (List of Values)

**Caso:** Seleccionar profesor para una materia

```html
<p-autocomplete 
  [(ngModel)]="profesorSeleccionado"
  [suggestions]="profesoresFiltrados"
  field="nombre"
  [minLength]="2"
  (completeMethod)="buscarProfesores($event)"
  placeholder="Selecciona profesor...">
  <ng-template let-value pTemplate="item">
    <div class="profesor-item">
      <span>{{ value.nombre }}</span>
      <small>{{ value.especialidad }}</small>
    </div>
  </ng-template>
</p-autocomplete>
```

---

## 7. Checklist de Implementación

### Para FASE 10 (Gradebook Mejorado)

- [ ] Grid editable para calificaciones (PrimeNG p-table + editableColumn)
- [ ] Validación en tiempo real (rango 0-10, decimales)
- [ ] Feedback visual (colores, checkmark, errores)
- [ ] Bulk save (guardar todo con 1 click)
- [ ] Exportar CSV/Excel
- [ ] Auditoría de cambios (quién, qué, cuándo)
- [ ] Tests unitarios (>=80% coverage)
- [ ] Documentación de usuario

### Para FASE 27 (Ampliación de Materias)

- [ ] DDL con campo `tipo_materia` y `reporta_a_sep_uaemex`
- [ ] Vistas para materias oficiales vs Nevadi
- [ ] Componente selector con dos grupos (oficial/nevadi)
- [ ] Filtrado en planes de estudio
- [ ] Boleta con secciones separadas (oficial/nevadi)

---

**Preparado por:** Equipo de Arquitectura ADES  
**Última actualización:** Junio 2026  
**Próxima revisión:** Julio 2026 (post-FASE 27 kickoff)
