# 🎨 TASK_02: Análisis APEX 26.1.0 y Librería de Componentes Reutilizable
## Oracle APEX → Angular 19+ Component Library para ADES

**Objetivo**: Analizar cada componente de Oracle APEX 26.1.0, mapear a Angular 19+ + PrimeNG 21+, crear librería reutilizable y aplicar en ADES con breadcrumbs, interactive grids, data reporter, AI interactive reports y todos los componentes APEX.

**Scope**: APEX 26.1.0 components + PrimeNG + Angular 19+  
**Duración Estimada**: 12-16 horas de ejecución  
**Output**: 
- `apex-component-library/` (librería reutilizable)
- ADES integration con componentes APEX
- Documentación de cada componente
- Data Reporter + AI Interactive Reports

---

## 📋 FASE 1: Análisis de Componentes APEX 26.1.0

### 1.1 URLs Base y Estructura

**URL Principal**: `https://oracleapex.com/ords/r/apex_pm/ut/components`

**URLs de Análisis Requerido**:
1. Components Overview: `https://oracleapex.com/ords/r/apex_pm/ut/components`
2. Data Reporter: `https://blogs.oracle.com/apex/oracle-apex-data-reporter`
3. AI Interactive Reports: `https://blogs.oracle.com/apex/introducing-apex-ai-interactive-reports`

**Componentes Individuales** (a fetchear uno por uno):
- Alert Region: `https://oracleapex.com/ords/r/apex_pm/ut/alert-region`
- Badges List: `https://oracleapex.com/ords/r/apex_pm/ut/badges-list`
- Breadcrumb: `https://oracleapex.com/ords/r/apex_pm/ut/breadcrumb`
- Button: `https://oracleapex.com/ords/r/apex_pm/ut/button`
- Card: `https://oracleapex.com/ords/r/apex_pm/ut/card`
- Chart: `https://oracleapex.com/ords/r/apex_pm/ut/chart`
- Data Grid (Interactive Grid): `https://oracleapex.com/ords/r/apex_pm/ut/interactive-grid`
- Form: `https://oracleapex.com/ords/r/apex_pm/ut/form`
- Modal Dialog: `https://oracleapex.com/ords/r/apex_pm/ut/modal-dialog`
- Navigation: `https://oracleapex.com/ords/r/apex_pm/ut/navigation`
- Popup LOV: `https://oracleapex.com/ords/r/apex_pm/ut/popup-lov`
- Report: `https://oracleapex.com/ords/r/apex_pm/ut/report`
- Rich Text Editor: `https://oracleapex.com/ords/r/apex_pm/ut/rich-text-editor`
- Search: `https://oracleapex.com/ords/r/apex_pm/ut/search`
- Shuttle: `https://oracleapex.com/ords/r/apex_pm/ut/shuttle`
- Slider: `https://oracleapex.com/ords/r/apex_pm/ut/slider`
- Spinner: `https://oracleapex.com/ords/r/apex_pm/ut/spinner`
- Tabs: `https://oracleapex.com/ords/r/apex_pm/ut/tabs`
- Timeline: `https://oracleapex.com/ords/r/apex_pm/ut/timeline`
- Tree: `https://oracleapex.com/ords/r/apex_pm/ut/tree`

### 1.2 Plan de Fetch y Análisis

**PASO 1.2.1**: Fetch de URL principal y extracción de lista completa de componentes

```bash
# Usaremos web_fetch para obtener la página principal
# Extraer:
# - Listado completo de componentes
# - URLs individuales
# - Categorías
# - Descripción breve

# Guardamos en: analysis/apex_components_list.json
```

**PASO 1.2.2**: Fetch individual de cada componente

Para cada componente, extraer:
- Nombre
- Descripción
- Propiedades
- Atributos configurables
- Ejemplos de uso (si hay)
- Comportamientos especiales
- Accesibilidad
- Responsividad

---

## 📊 FASE 2: Mapeo APEX → Angular + PrimeNG

### 2.1 Matriz de Mapeo (APEX → Angular + PrimeNG)

**Archivo a crear**: `analysis/apex_to_angular_mapping.md`

```markdown
# Mapeo: Oracle APEX 26.1.0 → Angular 19+ + PrimeNG 21+

## Componentes Principales

| APEX Component | Angular Component | PrimeNG | Notes |
|---|---|---|---|
| Alert Region | AlertComponent | p-message, p-toast | Toast notifications, dismissible alerts |
| Badges List | BadgeComponent | p-tag | Badge display, categories |
| Breadcrumb | BreadcrumbComponent | p-breadcrumb | Navigation history |
| Button | ButtonComponent | p-button | Variantes: primary, secondary, danger |
| Card | CardComponent | p-card | Contenedor con header/footer |
| Chart | ChartComponent | p-chart (Chart.js) | Gráficos: línea, barra, pie, etc. |
| Interactive Grid | DataGridComponent | p-dataTable + custom | Edición inline, sorting, filtering |
| Form | FormComponent | Reactive Forms | Validación, grupos, arrays |
| Modal Dialog | DialogComponent | p-dialog | Modal, drawer, sidebar |
| Navigation | NavigationComponent | p-menu, p-toolbar | Sidebar, top nav, contextual |
| Popup LOV | AutocompleteComponent | p-autoComplete | Búsqueda con dropdown |
| Report | TableComponent | p-dataTable | Solo lectura, exporte, paginación |
| Rich Text Editor | EditorComponent | p-editor | WYSIWYG, markdown support |
| Search | SearchComponent | p-inputSearch | Input con debounce, suggestions |
| Shuttle | TransferComponent | p-pickList, p-transfer | Movimiento bidireccional |
| Slider | SliderComponent | p-slider | Range slider, dual handle |
| Spinner | SpinnerComponent | p-spinner, p-progressSpinner | Input numérico, loading |
| Tabs | TabsComponent | p-tabView | Navegación por tabs |
| Timeline | TimelineComponent | p-timeline | Eventos secuenciales |
| Tree | TreeComponent | p-tree | Estructura jerárquica, expandible |

## Especiales (APEX Features)

| APEX Feature | Implementation | Notes |
|---|---|---|
| Data Reporter | ReportComponent + pgvector | Análisis, exportación, scheduled reports |
| AI Interactive Reports | AIReportComponent + Anthropic API | Insights automáticos, recomendaciones |
| Dynamic Actions | @HostListener, RxJS | Eventos reactivos |
| Validations | Validators (sync/async) | Real-time, server-side |
| Themes | CSS Variables + TailwindCSS | Light/dark, custom |

---

## Implementación Priority

**Fase 1 (MVP)**:
- Alert
- Breadcrumb
- Button
- Card
- Form
- Interactive Grid
- Modal Dialog
- Tabs

**Fase 2 (Core)**:
- Chart
- Navigation
- Report
- Rich Text Editor
- Search

**Fase 3 (Extended)**:
- Badges, Shuttle, Slider, Spinner, Timeline, Tree
- Data Reporter
- AI Interactive Reports

```

### 2.2 Estructura de Carpetas para Librería

```
apex-component-library/
├── README.md
├── package.json
├── tsconfig.json
├── ng-package.json (para publicar en npm)
│
├── src/
│   ├── public-api.ts
│   │
│   ├── lib/
│   │   ├── alert/
│   │   │   ├── alert.component.ts
│   │   │   ├── alert.component.html
│   │   │   ├── alert.component.scss
│   │   │   └── alert.component.spec.ts
│   │   │
│   │   ├── breadcrumb/
│   │   │   ├── breadcrumb.component.ts
│   │   │   ├── breadcrumb.component.html
│   │   │   ├── breadcrumb.component.scss
│   │   │   └── breadcrumb.component.spec.ts
│   │   │
│   │   ├── button/
│   │   ├── card/
│   │   ├── form/
│   │   ├── interactive-grid/
│   │   ├── modal-dialog/
│   │   ├── ...
│   │   │
│   │   ├── shared/
│   │   │   ├── models/
│   │   │   │   ├── component.model.ts
│   │   │   │   ├── theme.model.ts
│   │   │   │   └── ...
│   │   │   ├── services/
│   │   │   │   ├── theme.service.ts
│   │   │   │   └── ...
│   │   │   └── styles/
│   │   │       ├── variables.scss
│   │   │       └── theme.scss
│   │   │
│   │   └── apex-component-library.module.ts
│   │
│   └── assets/
│       ├── icons/
│       └── themes/
│
├── docs/
│   ├── components/
│   │   ├── alert.md
│   │   ├── breadcrumb.md
│   │   └── ...
│   └── integration-guide.md
│
└── examples/
    ├── app.module.ts
    └── component-showcase.component.ts
```

---

## 🏗️ FASE 3: Implementación de Componentes Base

### 3.1 Alert Component

**Archivo**: `src/lib/alert/alert.component.ts`

```typescript
import { Component, Input, Output, EventEmitter, ChangeDetectionStrategy } from '@angular/core';

export type AlertSeverity = 'success' | 'info' | 'warning' | 'error';

@Component({
  selector: 'apex-alert',
  templateUrl: './alert.component.html',
  styleUrls: ['./alert.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
  imports: [CommonModule]
})
export class ApexAlertComponent {
  @Input() severity: AlertSeverity = 'info';
  @Input() title?: string;
  @Input() message: string = '';
  @Input() closable: boolean = true;
  @Input() icon?: string;
  @Input() styleClass?: string;
  
  @Output() closed = new EventEmitter<void>();
  
  visible = true;
  
  onClose(): void {
    this.visible = false;
    this.closed.emit();
  }
  
  get severityClass(): string {
    return `alert-${this.severity}`;
  }
}
```

**Archivo**: `src/lib/alert/alert.component.html`

```html
<div *ngIf="visible" 
     class="apex-alert" 
     [ngClass]="[severityClass, styleClass]"
     role="alert">
  
  <div class="alert-content">
    <span *ngIf="icon" [class]="icon" class="alert-icon"></span>
    
    <div class="alert-text">
      <strong *ngIf="title">{{ title }}</strong>
      <p>{{ message }}</p>
    </div>
  </div>
  
  <button *ngIf="closable" 
          class="alert-close"
          (click)="onClose()"
          aria-label="Close alert">
    ✕
  </button>
</div>
```

**Archivo**: `src/lib/alert/alert.component.scss`

```scss
.apex-alert {
  display: flex;
  padding: 1rem;
  border-radius: 0.5rem;
  border-left: 4px solid;
  gap: 1rem;
  align-items: flex-start;
  font-size: 0.9rem;
  
  &.alert-success {
    background-color: #d4edda;
    border-color: #28a745;
    color: #155724;
  }
  
  &.alert-info {
    background-color: #d1ecf1;
    border-color: #17a2b8;
    color: #0c5460;
  }
  
  &.alert-warning {
    background-color: #fff3cd;
    border-color: #ffc107;
    color: #856404;
  }
  
  &.alert-error {
    background-color: #f8d7da;
    border-color: #dc3545;
    color: #721c24;
  }
  
  .alert-content {
    flex: 1;
    display: flex;
    gap: 0.5rem;
  }
  
  .alert-icon {
    min-width: 1.5rem;
  }
  
  .alert-text {
    flex: 1;
    
    strong {
      display: block;
      margin-bottom: 0.25rem;
    }
    
    p {
      margin: 0;
    }
  }
  
  .alert-close {
    background: none;
    border: none;
    font-size: 1.5rem;
    cursor: pointer;
    opacity: 0.7;
    
    &:hover {
      opacity: 1;
    }
  }
}
```

### 3.2 Breadcrumb Component

**Archivo**: `src/lib/breadcrumb/breadcrumb.component.ts`

```typescript
import { Component, Input, Output, EventEmitter, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';

export interface BreadcrumbItem {
  label: string;
  routerLink?: string | any[];
  icon?: string;
  activeIcon?: string;
  action?: () => void;
}

@Component({
  selector: 'apex-breadcrumb',
  templateUrl: './breadcrumb.component.html',
  styleUrls: ['./breadcrumb.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
  imports: [CommonModule, RouterModule]
})
export class ApexBreadcrumbComponent {
  @Input() items: BreadcrumbItem[] = [];
  @Input() home?: BreadcrumbItem;
  @Input() styleClass?: string;
  @Input() separatorIcon: string = 'pi pi-chevron-right';
  
  @Output() itemClick = new EventEmitter<BreadcrumbItem>();
  
  onClick(item: BreadcrumbItem, index: number, event: Event): void {
    event.preventDefault();
    
    if (item.action) {
      item.action();
    }
    
    this.itemClick.emit(item);
  }
  
  isLast(index: number): boolean {
    return index === this.items.length - 1;
  }
}
```

**Archivo**: `src/lib/breadcrumb/breadcrumb.component.html`

```html
<nav class="apex-breadcrumb" [ngClass]="styleClass" aria-label="Breadcrumb">
  <ol class="breadcrumb-list">
    <!-- Home item -->
    <li *ngIf="home" class="breadcrumb-item">
      <a [routerLink]="home.routerLink"
         (click)="onClick(home, -1, $event)"
         class="breadcrumb-link">
        <span *ngIf="home.icon" [class]="home.icon"></span>
        {{ home.label }}
      </a>
    </li>
    
    <!-- Regular items -->
    <li *ngFor="let item of items; let i = index; let last = last"
        class="breadcrumb-item"
        [attr.aria-current]="last ? 'page' : null">
      
      <span *ngIf="!last" [class]="separatorIcon" class="breadcrumb-separator"></span>
      
      <a *ngIf="item.routerLink && !last"
         [routerLink]="item.routerLink"
         (click)="onClick(item, i, $event)"
         class="breadcrumb-link">
        <span *ngIf="item.icon" [class]="item.icon"></span>
        {{ item.label }}
      </a>
      
      <span *ngIf="!item.routerLink || last" class="breadcrumb-text">
        <span *ngIf="item.activeIcon" [class]="item.activeIcon"></span>
        {{ item.label }}
      </span>
    </li>
  </ol>
</nav>
```

**Archivo**: `src/lib/breadcrumb/breadcrumb.component.scss`

```scss
.apex-breadcrumb {
  margin-bottom: 1rem;
  
  .breadcrumb-list {
    display: flex;
    list-style: none;
    padding: 0;
    margin: 0;
    gap: 0.25rem;
    align-items: center;
    flex-wrap: wrap;
  }
  
  .breadcrumb-item {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    
    &:last-child {
      font-weight: 600;
      color: #333;
    }
  }
  
  .breadcrumb-link {
    color: #0066cc;
    text-decoration: none;
    display: flex;
    align-items: center;
    gap: 0.25rem;
    padding: 0.25rem 0.5rem;
    border-radius: 0.25rem;
    transition: background-color 0.2s;
    
    &:hover {
      background-color: rgba(0, 102, 204, 0.1);
      text-decoration: underline;
    }
    
    &:active {
      background-color: rgba(0, 102, 204, 0.2);
    }
  }
  
  .breadcrumb-text {
    display: flex;
    align-items: center;
    gap: 0.25rem;
    color: #333;
  }
  
  .breadcrumb-separator {
    color: #999;
    font-size: 0.8rem;
  }
}
```

### 3.3 Interactive Grid Component (Data Table)

**Archivo**: `src/lib/interactive-grid/interactive-grid.component.ts`

```typescript
import { Component, Input, Output, EventEmitter, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';

export interface GridColumn {
  field: string;
  header: string;
  type?: 'text' | 'number' | 'date' | 'currency' | 'boolean' | 'action';
  sortable?: boolean;
  filterable?: boolean;
  editable?: boolean;
  width?: string;
  formatter?: (value: any) => string;
}

export interface GridRow {
  id: string | number;
  [key: string]: any;
}

@Component({
  selector: 'apex-interactive-grid',
  templateUrl: './interactive-grid.component.html',
  styleUrls: ['./interactive-grid.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
  imports: [CommonModule]
})
export class ApexInteractiveGridComponent {
  @Input() columns: GridColumn[] = [];
  @Input() rows: GridRow[] = [];
  @Input() selectedRows: GridRow[] = [];
  @Input() sortField?: string;
  @Input() sortOrder: 1 | -1 = 1;
  @Input() pageable: boolean = true;
  @Input() pageSize: number = 10;
  @Input() filterable: boolean = true;
  @Input() selectable: boolean = true;
  @Input() editable: boolean = false;
  
  @Output() rowSelect = new EventEmitter<GridRow>();
  @Output() rowDeselect = new EventEmitter<GridRow>();
  @Output() cellEdit = new EventEmitter<{ row: GridRow; field: string; value: any }>();
  @Output() sort = new EventEmitter<{ field: string; order: 1 | -1 }>();
  @Output() filter = new EventEmitter<{ field: string; value: string }>();
  
  currentPage: number = 0;
  filters: { [key: string]: string } = {};
  editingCell: { rowId: string | number; field: string } | null = null;
  
  get paginatedRows(): GridRow[] {
    if (!this.pageable) return this.rows;
    const start = this.currentPage * this.pageSize;
    return this.rows.slice(start, start + this.pageSize);
  }
  
  get totalPages(): number {
    return Math.ceil(this.rows.length / this.pageSize);
  }
  
  onRowSelect(row: GridRow): void {
    if (this.selectedRows.includes(row)) {
      this.selectedRows = this.selectedRows.filter(r => r !== row);
      this.rowDeselect.emit(row);
    } else {
      this.selectedRows = [...this.selectedRows, row];
      this.rowSelect.emit(row);
    }
  }
  
  onSort(field: string): void {
    const newOrder = this.sortField === field && this.sortOrder === 1 ? -1 : 1;
    this.sortField = field;
    this.sortOrder = newOrder;
    this.sort.emit({ field, order: newOrder });
  }
  
  onFilter(field: string, value: string): void {
    this.filters[field] = value;
    this.filter.emit({ field, value });
  }
  
  startEditCell(row: GridRow, column: GridColumn): void {
    if (this.editable && column.editable) {
      this.editingCell = { rowId: row.id, field: column.field };
    }
  }
  
  endEditCell(row: GridRow, column: GridColumn, value: any): void {
    if (this.editingCell) {
      this.cellEdit.emit({ row, field: column.field, value });
      this.editingCell = null;
    }
  }
  
  isEditing(rowId: string | number, field: string): boolean {
    return this.editingCell?.rowId === rowId && this.editingCell?.field === field;
  }
  
  formatValue(column: GridColumn, value: any): string {
    if (column.formatter) {
      return column.formatter(value);
    }
    
    switch (column.type) {
      case 'date':
        return value ? new Date(value).toLocaleDateString() : '';
      case 'currency':
        return value ? `$${parseFloat(value).toFixed(2)}` : '';
      case 'boolean':
        return value ? 'Yes' : 'No';
      default:
        return String(value);
    }
  }
}
```

**Archivo**: `src/lib/interactive-grid/interactive-grid.component.html`

```html
<div class="apex-interactive-grid">
  <!-- Toolbar -->
  <div class="grid-toolbar" *ngIf="filterable || pageable">
    <div class="grid-filters">
      <input *ngFor="let col of columns"
             *ngIf="col.filterable"
             type="text"
             [placeholder]="'Filter ' + col.header"
             [value]="filters[col.field] || ''"
             (input)="onFilter(col.field, $event.target.value)"
             class="grid-filter-input">
    </div>
    
    <div class="grid-pagination" *ngIf="pageable">
      <button (click)="currentPage = Math.max(0, currentPage - 1)"
              [disabled]="currentPage === 0">← Previous</button>
      <span>Page {{ currentPage + 1 }} of {{ totalPages }}</span>
      <button (click)="currentPage = Math.min(totalPages - 1, currentPage + 1)"
              [disabled]="currentPage === totalPages - 1">Next →</button>
    </div>
  </div>
  
  <!-- Table -->
  <table class="grid-table" role="grid">
    <thead>
      <tr>
        <th *ngIf="selectable" class="col-checkbox">
          <input type="checkbox">
        </th>
        <th *ngFor="let col of columns"
            [style.width]="col.width"
            (click)="col.sortable && onSort(col.field)"
            [class.sortable]="col.sortable">
          {{ col.header }}
          <span *ngIf="sortField === col.field" 
                class="sort-icon">
            {{ sortOrder === 1 ? '↑' : '↓' }}
          </span>
        </th>
      </tr>
    </thead>
    <tbody>
      <tr *ngFor="let row of paginatedRows; trackBy: trackByRowId"
          [class.selected]="selectedRows.includes(row)">
        
        <td *ngIf="selectable" class="col-checkbox">
          <input type="checkbox"
                 [checked]="selectedRows.includes(row)"
                 (change)="onRowSelect(row)">
        </td>
        
        <td *ngFor="let col of columns"
            (dblclick)="startEditCell(row, col)"
            [class.editable]="col.editable">
          
          <!-- View mode -->
          <span *ngIf="!isEditing(row.id, col.field)">
            {{ formatValue(col, row[col.field]) }}
          </span>
          
          <!-- Edit mode -->
          <input *ngIf="isEditing(row.id, col.field)"
                 type="text"
                 [value]="row[col.field]"
                 (blur)="endEditCell(row, col, $event.target.value)"
                 autofocus>
        </td>
      </tr>
    </tbody>
  </table>
</div>
```

**Archivo**: `src/lib/interactive-grid/interactive-grid.component.scss`

```scss
.apex-interactive-grid {
  display: flex;
  flex-direction: column;
  border: 1px solid #ddd;
  border-radius: 0.5rem;
  overflow: hidden;
  
  .grid-toolbar {
    display: flex;
    justify-content: space-between;
    padding: 1rem;
    background-color: #f9f9f9;
    gap: 1rem;
    flex-wrap: wrap;
  }
  
  .grid-filters {
    display: flex;
    gap: 0.5rem;
    flex-wrap: wrap;
  }
  
  .grid-filter-input {
    padding: 0.5rem;
    border: 1px solid #ddd;
    border-radius: 0.25rem;
    font-size: 0.9rem;
  }
  
  .grid-pagination {
    display: flex;
    gap: 0.5rem;
    align-items: center;
    
    button {
      padding: 0.5rem 1rem;
      border: 1px solid #ddd;
      background: white;
      cursor: pointer;
      border-radius: 0.25rem;
      
      &:disabled {
        opacity: 0.5;
        cursor: not-allowed;
      }
      
      &:hover:not(:disabled) {
        background-color: #f0f0f0;
      }
    }
  }
  
  .grid-table {
    width: 100%;
    border-collapse: collapse;
    
    thead tr {
      background-color: #f5f5f5;
      border-bottom: 2px solid #ddd;
    }
    
    th {
      padding: 0.75rem;
      text-align: left;
      font-weight: 600;
      cursor: default;
      user-select: none;
      
      &.sortable {
        cursor: pointer;
        
        &:hover {
          background-color: #efefef;
        }
      }
      
      .sort-icon {
        margin-left: 0.5rem;
      }
    }
    
    td {
      padding: 0.75rem;
      border-bottom: 1px solid #ddd;
      
      &.editable {
        background-color: #fffbea;
      }
      
      input[type="text"] {
        width: 100%;
        padding: 0.25rem;
        border: 1px solid #0066cc;
      }
      
      &.col-checkbox {
        width: 3rem;
        text-align: center;
      }
    }
    
    tbody tr {
      &:hover {
        background-color: #f9f9f9;
      }
      
      &.selected {
        background-color: #e3f2fd;
      }
    }
  }
}
```

---

## 🔧 FASE 4: Implementación de Componentes Adicionales

### 4.1 Button Component
### 4.2 Modal Dialog Component
### 4.3 Form Component
### 4.4 Card Component
### 4.5 Tabs Component
### 4.6 Report Component (Data Table - Read Only)
### 4.7 Search Component
### 4.8 Navigation Component

*[Implementación detallada para cada uno siguiendo el patrón de Alert + Breadcrumb + Interactive Grid]*

---

## 📊 FASE 5: Componentes Especiales

### 5.1 Data Reporter Component

**Archivo**: `src/lib/data-reporter/data-reporter.component.ts`

```typescript
import { Component, Input, Output, EventEmitter, ChangeDetectionStrategy } from '@angular/core';

export interface ReportData {
  id: string;
  name: string;
  description?: string;
  data: any[];
  columns: GridColumn[];
  createdAt: Date;
  createdBy: string;
}

export interface ReportExportOptions {
  format: 'csv' | 'pdf' | 'xlsx' | 'json';
  includeHeaders: boolean;
  filters?: { [key: string]: any };
}

@Component({
  selector: 'apex-data-reporter',
  templateUrl: './data-reporter.component.html',
  styleUrls: ['./data-reporter.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
  imports: [CommonModule, ApexInteractiveGridComponent]
})
export class ApexDataReporterComponent {
  @Input() report: ReportData | null = null;
  @Input() showExportOptions: boolean = true;
  @Input() showScheduleOptions: boolean = true;
  @Input() allowSave: boolean = true;
  
  @Output() export = new EventEmitter<ReportExportOptions>();
  @Output() schedule = new EventEmitter<{ frequency: string; email: string }>();
  @Output() save = new EventEmitter<ReportData>();
  
  exportFormat: 'csv' | 'pdf' | 'xlsx' | 'json' = 'csv';
  scheduleFrequency: string = 'weekly';
  scheduleEmail: string = '';
  
  onExport(): void {
    if (this.report) {
      this.export.emit({
        format: this.exportFormat,
        includeHeaders: true,
        filters: {}
      });
    }
  }
  
  onSchedule(): void {
    if (this.scheduleEmail && this.scheduleFrequency) {
      this.schedule.emit({
        frequency: this.scheduleFrequency,
        email: this.scheduleEmail
      });
    }
  }
  
  onSave(): void {
    if (this.report && this.allowSave) {
      this.save.emit(this.report);
    }
  }
}
```

### 5.2 AI Interactive Reports Component

**Archivo**: `src/lib/ai-interactive-report/ai-interactive-report.component.ts`

```typescript
import { Component, Input, Output, EventEmitter, ChangeDetectionStrategy, OnInit } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface AIInsight {
  type: 'recommendation' | 'anomaly' | 'trend' | 'summary';
  title: string;
  description: string;
  confidence: number; // 0-100
  relatedMetrics?: string[];
  action?: () => void;
}

@Component({
  selector: 'apex-ai-interactive-report',
  templateUrl: './ai-interactive-report.component.html',
  styleUrls: ['./ai-interactive-report.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
  imports: [CommonModule, ApexInteractiveGridComponent]
})
export class ApexAIInteractiveReportComponent implements OnInit {
  @Input() report: ReportData | null = null;
  @Input() aiEndpoint: string = '/api/v1/ai/analyze';
  
  @Output() insightGenerated = new EventEmitter<AIInsight[]>();
  
  insights$: Observable<AIInsight[]> | null = null;
  loading: boolean = false;
  
  constructor(private http: HttpClient) {}
  
  ngOnInit(): void {
    if (this.report) {
      this.generateInsights();
    }
  }
  
  generateInsights(): void {
    if (!this.report) return;
    
    this.loading = true;
    
    // Llamada al backend que usa Anthropic API vía resident agent
    this.insights$ = this.http.post<AIInsight[]>(this.aiEndpoint, {
      reportData: this.report.data,
      columns: this.report.columns,
      reportName: this.report.name
    });
    
    this.insights$?.subscribe({
      next: (insights) => {
        this.loading = false;
        this.insightGenerated.emit(insights);
      },
      error: () => {
        this.loading = false;
      }
    });
  }
}
```

---

## 🎯 FASE 6: Integración en ADES

### 6.1 Módulo Angular de Importación

**Archivo**: `ades_frontend/src/app/shared/apex-components.module.ts`

```typescript
import { NgModule } from '@angular/core';

// Importar todos los componentes de apex-component-library
import {
  ApexAlertComponent,
  ApexBreadcrumbComponent,
  ApexButtonComponent,
  ApexCardComponent,
  ApexInteractiveGridComponent,
  ApexModalDialogComponent,
  ApexFormComponent,
  ApexTabsComponent,
  ApexDataReporterComponent,
  ApexAIInteractiveReportComponent,
  // ... rest de componentes
} from 'apex-component-library';

@NgModule({
  imports: [
    ApexAlertComponent,
    ApexBreadcrumbComponent,
    ApexButtonComponent,
    ApexCardComponent,
    ApexInteractiveGridComponent,
    ApexModalDialogComponent,
    ApexFormComponent,
    ApexTabsComponent,
    ApexDataReporterComponent,
    ApexAIInteractiveReportComponent,
    // ...
  ],
  exports: [
    ApexAlertComponent,
    ApexBreadcrumbComponent,
    ApexButtonComponent,
    ApexCardComponent,
    ApexInteractiveGridComponent,
    ApexModalDialogComponent,
    ApexFormComponent,
    ApexTabsComponent,
    ApexDataReporterComponent,
    ApexAIInteractiveReportComponent,
    // ...
  ]
})
export class ApexComponentsModule {}
```

### 6.2 Layout Principal con Breadcrumb

**Archivo**: `ades_frontend/src/app/layouts/main-layout.component.ts`

```typescript
import { Component, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { Router, NavigationEnd } from '@angular/router';
import { BreadcrumbItem } from 'apex-component-library';

@Component({
  selector: 'app-main-layout',
  templateUrl: './main-layout.component.html',
  styleUrls: ['./main-layout.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
  imports: [CommonModule, ApexComponentsModule, RouterOutlet]
})
export class MainLayoutComponent implements OnInit {
  breadcrumbItems: BreadcrumbItem[] = [];
  
  constructor(private router: Router) {}
  
  ngOnInit(): void {
    this.router.events.subscribe(event => {
      if (event instanceof NavigationEnd) {
        this.buildBreadcrumbs();
      }
    });
  }
  
  buildBreadcrumbs(): void {
    const urlSegments = this.router.url.split('/').filter(s => s);
    
    this.breadcrumbItems = urlSegments.map((segment, index) => {
      const path = '/' + urlSegments.slice(0, index + 1).join('/');
      return {
        label: this.humanize(segment),
        routerLink: path
      };
    });
  }
  
  private humanize(text: string): string {
    return text
      .split('-')
      .map(word => word.charAt(0).toUpperCase() + word.slice(1))
      .join(' ');
  }
}
```

**Archivo**: `ades_frontend/src/app/layouts/main-layout.component.html`

```html
<div class="main-layout">
  <!-- Breadcrumb Navigation -->
  <div class="breadcrumb-section">
    <apex-breadcrumb 
      [items]="breadcrumbItems"
      [home]="{ label: 'Home', routerLink: '/' }">
    </apex-breadcrumb>
  </div>
  
  <!-- Main Content -->
  <div class="main-content">
    <router-outlet></router-outlet>
  </div>
</div>
```

### 6.3 Ejemplo: Usuarios List con Interactive Grid

**Archivo**: `ades_frontend/src/app/pages/usuarios/usuarios-list.component.ts`

```typescript
import { Component, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { UsuariosService } from './usuarios.service';
import { GridColumn } from 'apex-component-library';

@Component({
  selector: 'app-usuarios-list',
  templateUrl: './usuarios-list.component.html',
  styleUrls: ['./usuarios-list.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
  imports: [CommonModule, ApexComponentsModule]
})
export class UsuariosListComponent implements OnInit {
  usuarios$ = this.usuariosService.getAll$();
  
  columns: GridColumn[] = [
    {
      field: 'id',
      header: 'ID',
      type: 'text',
      sortable: true,
      filterable: true,
      width: '100px'
    },
    {
      field: 'nombre',
      header: 'Nombre',
      type: 'text',
      sortable: true,
      filterable: true,
      editable: true
    },
    {
      field: 'email',
      header: 'Email',
      type: 'text',
      sortable: true,
      filterable: true,
      editable: true
    },
    {
      field: 'rol_id',
      header: 'Rol',
      type: 'text',
      sortable: true,
      filterable: true
    },
    {
      field: 'creado_en',
      header: 'Creado',
      type: 'date',
      sortable: true,
      width: '150px'
    }
  ];
  
  constructor(private usuariosService: UsuariosService) {}
  
  ngOnInit(): void {}
  
  onCellEdit(event: any): void {
    // Guardar cambio en backend
    this.usuariosService.update(event.row.id, {
      [event.field]: event.value
    }).subscribe(() => {
      // Refresh
    });
  }
}
```

**Archivo**: `ades_frontend/src/app/pages/usuarios/usuarios-list.component.html`

```html
<div class="usuarios-container">
  <apex-card>
    <ng-container *ngTemplateOutlet="cardHeader"></ng-container>
    
    <apex-interactive-grid
      [columns]="columns"
      [rows]="(usuarios$ | async) || []"
      [editable]="true"
      [selectable]="true"
      (cellEdit)="onCellEdit($event)">
    </apex-interactive-grid>
  </apex-card>
</div>

<ng-template #cardHeader>
  <div class="card-header">
    <h2>Usuarios</h2>
    <apex-button 
      label="Nuevo Usuario"
      icon="pi pi-plus"
      (click)="openNewModal()">
    </apex-button>
  </div>
</ng-template>
```

---

## 📝 FASE 7: Testing de Componentes

**Archivo**: `src/lib/alert/alert.component.spec.ts`

```typescript
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ApexAlertComponent } from './alert.component';

describe('ApexAlertComponent', () => {
  let component: ApexAlertComponent;
  let fixture: ComponentFixture<ApexAlertComponent>;
  
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ApexAlertComponent]
    }).compileComponents();
    
    fixture = TestBed.createComponent(ApexAlertComponent);
    component = fixture.componentInstance;
  });
  
  it('should create', () => {
    expect(component).toBeTruthy();
  });
  
  it('should display message', () => {
    component.message = 'Test message';
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Test message');
  });
  
  it('should emit closed event when close button clicked', (done) => {
    component.closable = true;
    component.closed.subscribe(() => {
      expect(component.visible).toBeFalsy();
      done();
    });
    
    component.onClose();
  });
  
  it('should apply correct severity class', () => {
    component.severity = 'error';
    fixture.detectChanges();
    expect(component.severityClass).toBe('alert-error');
  });
});
```

---

## 🎨 FASE 8: Documentación de Componentes

### 8.1 Storybook Configuration

**Archivo**: `apex-component-library/.storybook/main.ts`

```typescript
import type { StorybookConfig } from '@storybook/angular';

const config: StorybookConfig = {
  stories: ['../src/**/*.stories.ts'],
  addons: ['@storybook/addon-essentials'],
  framework: {
    name: '@storybook/angular',
    options: {},
  },
};

export default config;
```

### 8.2 Component Stories

**Archivo**: `src/lib/alert/alert.stories.ts`

```typescript
import { Meta, StoryObj } from '@storybook/angular';
import { ApexAlertComponent } from './alert.component';

const meta: Meta<ApexAlertComponent> = {
  title: 'Components/Alert',
  component: ApexAlertComponent,
  tags: ['autodocs'],
};

export default meta;
type Story = StoryObj<typeof meta>;

export const Success: Story = {
  args: {
    severity: 'success',
    title: 'Success!',
    message: 'Operation completed successfully.',
    closable: true
  }
};

export const Error: Story = {
  args: {
    severity: 'error',
    title: 'Error!',
    message: 'Something went wrong. Please try again.',
    closable: true
  }
};
```

### 8.3 Markdown Documentation

**Archivo**: `docs/components/alert.md`

```markdown
# Alert Component

The Alert component is used to display important messages to the user.

## Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| severity | AlertSeverity | 'info' | Level of alert (success, info, warning, error) |
| title | string | undefined | Optional alert title |
| message | string | '' | Alert message text |
| closable | boolean | true | Whether alert can be dismissed |
| icon | string | undefined | Optional icon class |
| styleClass | string | undefined | Additional CSS classes |

## Events

| Event | Type | Description |
|-------|------|-------------|
| closed | EventEmitter<void> | Emitted when alert is closed |

## Example

```html
<apex-alert 
  severity="error"
  title="Validation Error"
  message="Please fill in all required fields"
  (closed)="onAlertClosed()">
</apex-alert>
```

## Accessibility

- Uses `role="alert"` for screen readers
- Dismissible button has `aria-label`
- Proper color contrast ratios

## Responsive

- Mobile-friendly with stacked layout
- Touch-friendly dismiss button
```

---

## ✅ FASE 9: Checklist de Cierre

- [ ] Librería creada: `apex-component-library/`
- [ ] 8+ componentes implementados y testeados
- [ ] Breadcrumb integrado en layout principal ADES
- [ ] Interactive Grid funcionando con ADES usuarios
- [ ] Data Reporter para reportes
- [ ] AI Interactive Reports integrado con Anthropic
- [ ] Todos los componentes con documentación
- [ ] Storybook configurado
- [ ] Tests unitarios pasando (90%+ coverage)
- [ ] Integración ADES lista
- [ ] Componentes reutilizables en otros proyectos

---

## 📊 Análisis Requerido (Fase 0)

### Fetch URLs Necesarias

Antes de implementar, necesitamos:

1. **https://oracleapex.com/ords/r/apex_pm/ut/components**
   - Extraer lista completa de componentes
   - Categorías
   - Descripción breve de cada uno

2. **https://blogs.oracle.com/apex/oracle-apex-data-reporter**
   - Funcionalidades de Data Reporter
   - Opciones de exportación
   - Programación de reportes

3. **https://blogs.oracle.com/apex/introducing-apex-ai-interactive-reports**
   - Capacidades de AI Interactive Reports
   - Insights automáticos
   - Integración con LLMs

4. **Cada URL de componente individual**
   - Propiedades
   - Comportamientos
   - Ejemplos

### Output de Fase 0

- `analysis/apex_components_extracted.json`
- `analysis/apex_to_angular_mapping.md`
- `analysis/apex_features_summary.md`

---

## 🚀 Ejecución

Este plan está listo para ejecutar en Claude Code en 2-3 sesiones:

**Sesión 1**: Fases 0-2 (Análisis y Mapeo)
**Sesión 2**: Fases 3-5 (Componentes Base + Especiales)
**Sesión 3**: Fases 6-9 (Integración ADES + Testing + Documentación)

---

## 📞 Notas

- Librería debe ser publicable en npm
- Componentes standalone (Angular 14+)
- Compatible con PrimeNG 21+
- Theming con CSS variables
- Accesibilidad WCAG 2.1 AA
- Backward compatible con ADES existente
