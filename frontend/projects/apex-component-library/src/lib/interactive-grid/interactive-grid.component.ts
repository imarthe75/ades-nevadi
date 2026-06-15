import {
  Component, Input, Output, EventEmitter, signal, computed, OnChanges, SimpleChanges,
  ChangeDetectionStrategy
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { TooltipModule } from 'primeng/tooltip';
import { DialogModule } from 'primeng/dialog';
import { AutoCompleteModule } from 'primeng/autocomplete';

export interface GridColumn {
  field: string;
  header: string;
  type?: 'text' | 'number' | 'date' | 'currency' | 'boolean' | 'action' | 'select';
  sortable?: boolean;
  filterable?: boolean;
  editable?: boolean;
  width?: string;
  formatter?: (value: any) => string;
  selectOptions?: Array<{ label: string; value: any }>;
}

export interface GridRow {
  id?: string | number;
  [key: string]: any;
}

@Component({
  selector: 'apex-interactive-grid',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule, FormsModule,
    TableModule, ButtonModule, InputTextModule, TooltipModule,
    DialogModule, AutoCompleteModule,
  ],
  template: `
    <div class="apex-ig-container">
      <!-- TOOLBAR -->
      <div class="apex-ig-toolbar">
        <div class="toolbar-left">
          <p-button icon="pi pi-th-large" [text]="true" severity="secondary"
            pTooltip="Mostrar/ocultar columnas" (onClick)="showChooser.set(true)" />
          <p-button icon="pi pi-download" [text]="true" severity="secondary"
            pTooltip="Descargar CSV" (onClick)="exportCSV()" />
          @if (filtrosActivos.size > 0) {
            <p-button icon="pi pi-filter-slash" [text]="true" severity="secondary"
              pTooltip="Limpiar filtros" (onClick)="limpiarFiltros()" />
          }
        </div>
        <div class="toolbar-right">
          <span class="apex-ig-count">{{ totalFilas() }} registro(s)</span>
        </div>
      </div>

      <!-- TABLE -->
      <p-table
        [value]="datosActuales"
        [loading]="loading"
        [columns]="columnasVisibles()"
        [paginator]="true"
        [rows]="pageSize"
        [rowsPerPageOptions]="[10,20,50,100]"
        [sortMode]="'single'"
        styleClass="p-datatable-sm p-datatable-striped p-datatable-gridlines apex-ig-table">

        <ng-template pTemplate="header" let-columns>
          <tr>
            @for (col of columns; track col.field) {
              <th [pSortableColumn]="col.sortable !== false ? col.field : undefined"
                  [style.width]="col.width || 'auto'"
                  [class.sortable]="col.sortable !== false">
                <div class="col-header-inner">
                  <span class="col-title">{{ col.header }}</span>
                  @if (col.sortable !== false) {
                    <p-sortIcon [field]="col.field" />
                  }
                </div>
                @if (col.filterable !== false) {
                  <div class="col-filter" (click)="$event.stopPropagation()">
                    <p-autoComplete
                      [suggestions]="getFilterSuggestions(col.field)"
                      (completeMethod)="buscarSugerencias(col.field, $event.query)"
                      (onSelect)="onFilterSelect(col.field, $event.value)"
                      (onClear)="clearFilter(col.field)"
                      [(ngModel)]="filterModels[col.field]"
                      [dropdown]="true"
                      [minLength]="0"
                      placeholder="Filtrar..."
                      styleClass="filter-autocomplete"
                      [inputStyle]="{'font-size': '.78rem', 'padding': '.25rem .4rem', 'width': '100%'}"
                      (onKeyUp)="onFilterKeyUp(col.field, $any($event).target?.value ?? '')"
                    />
                  </div>
                }
              </th>
            }
            <th class="col-acciones"><strong>Acciones</strong></th>
          </tr>
        </ng-template>

        <ng-template pTemplate="body" let-rowData let-columns="columns">
          <tr class="apex-ig-row" (click)="rowSelect.emit(rowData)" title="Clic para ver/editar">
            @for (col of columns; track col.field) {
              <td>{{ formatCell(col, rowData[col.field]) }}</td>
            }
            <td class="acciones-cell" (click)="$event.stopPropagation()">
              <p-button icon="pi pi-pencil" severity="secondary" [text]="true" size="small"
                pTooltip="Editar" (onClick)="rowSelect.emit(rowData)" />
              @if (showDelete) {
                <p-button icon="pi pi-trash" severity="danger" [text]="true" size="small"
                  pTooltip="Eliminar" (onClick)="rowDeselect.emit(rowData)" />
              }
            </td>
          </tr>
        </ng-template>

        <ng-template pTemplate="emptymessage" let-columns>
          <tr>
            <td [colSpan]="columns.length + 1" class="empty-msg">
              {{ loading ? 'Cargando...' : emptyMessage }}
            </td>
          </tr>
        </ng-template>
      </p-table>
    </div>

    <!-- COLUMN CHOOSER -->
    <p-dialog
      [visible]="showChooser()"
      (visibleChange)="showChooser.set($event)"
      header="Columnas visibles"
      [modal]="true"
      [style]="{width:'380px'}">
      <ul class="col-chooser-list">
        @for (col of columns; track col.field) {
          <li class="col-chooser-item">
            <input type="checkbox"
              [id]="'chk-' + col.field"
              [checked]="columnasVisibles().some(c => c.field === col.field)"
              (change)="toggleColumna(col.field)" />
            <label [for]="'chk-' + col.field">{{ col.header }}</label>
          </li>
        }
      </ul>
      <ng-template pTemplate="footer">
        <p-button label="Cerrar" icon="pi pi-check" (onClick)="showChooser.set(false)" />
      </ng-template>
    </p-dialog>
  `,
  styles: [`
    .apex-ig-container { background: var(--surface-0); border-radius: 8px; overflow: hidden; }
    .apex-ig-toolbar {
      display: flex; justify-content: space-between; align-items: center;
      padding: .4rem .75rem; border-bottom: 1px solid var(--surface-200);
      background: var(--surface-50);
    }
    .toolbar-left, .toolbar-right { display: flex; gap: .25rem; align-items: center; }
    .apex-ig-count { font-size: .82rem; color: var(--text-color-secondary); }
    .col-header-inner { display: flex; align-items: center; gap: .25rem; margin-bottom: .3rem; }
    .col-title { font-size: .82rem; font-weight: 600; white-space: nowrap; }
    .col-filter { width: 100%; }
    :host ::ng-deep .filter-autocomplete { width: 100%; }
    :host ::ng-deep .filter-autocomplete .p-autocomplete-input { width: 100%; font-size: .78rem; }
    .apex-ig-row { cursor: pointer; transition: background .1s; }
    .apex-ig-row:hover { background: var(--primary-50, #fef2f3) !important; }
    .apex-ig-row td { font-size: .87rem; }
    .col-acciones { width: 90px; text-align: center; }
    .acciones-cell { text-align: center; white-space: nowrap; }
    .empty-msg { text-align: center; padding: 2rem; color: var(--text-color-secondary); }
    :host ::ng-deep .apex-ig-table .p-datatable-thead > tr > th { padding: .4rem .5rem; }
    :host ::ng-deep .apex-ig-table .p-datatable-tbody > tr > td { padding: .45rem .5rem; }
    .col-chooser-list { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: .65rem; }
    .col-chooser-item { display: flex; align-items: center; gap: .5rem; cursor: pointer; }
    .col-chooser-item label { flex: 1; font-size: .9rem; cursor: pointer; }
  `],
})
export class ApexInteractiveGridComponent implements OnChanges {
  /** Data rows */
  @Input() rows: GridRow[] = [];
  /** Alias — accepts `data` like the local app-interactive-grid */
  @Input() data: any[] = [];
  @Input() columns: GridColumn[] = [];
  @Input() loading = false;
  @Input() showDelete = false;
  @Input() selectable = true;
  @Input() editable = false;
  @Input() pageSize = 20;
  @Input() emptyMessage = 'Sin registros';

  @Output() rowSelect    = new EventEmitter<any>();
  /** Backward compat alias */
  @Output() rowSelected  = new EventEmitter<any>();
  @Output() rowDeselect  = new EventEmitter<any>();
  @Output() rowDeleted   = new EventEmitter<any>();
  @Output() cellEdit     = new EventEmitter<{ row: any; field: string; value: any }>();

  columnasVisibles = signal<GridColumn[]>([]);
  showChooser = signal(false);
  totalFilas = signal(0);

  filtrosActivos = new Map<string, string>();
  datosOriginales: any[] = [];
  datosActuales:   any[] = [];
  filterSuggestions: Record<string, string[]> = {};
  filterModels:      Record<string, string>   = {};
  private _suggestionsIndex: Record<string, string[]> = {};

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['columns']) {
      this.columnasVisibles.set(this.columns);
    }
    // Accept either `rows` or `data`
    if (changes['rows'] || changes['data']) {
      const src = this.data?.length ? this.data : this.rows;
      this.datosOriginales = [...src];
      this.datosActuales   = [...src];
      this._rebuildIndex();
      this.aplicarFiltros();
    }
  }

  formatCell(col: GridColumn, value: any): string {
    if (col.formatter) return col.formatter(value);
    if (value === null || value === undefined) return '—';
    if (col.type === 'boolean') return value ? 'Sí' : 'No';
    if (col.type === 'date' && value) return new Date(value).toLocaleDateString('es-MX');
    if (col.type === 'currency' && value != null) return `$${Number(value).toLocaleString('es-MX', { minimumFractionDigits: 2 })}`;
    return String(value);
  }

  private _rebuildIndex(): void {
    this._suggestionsIndex = {};
    for (const col of this.columns) {
      this._suggestionsIndex[col.field] = [...new Set(
        this.datosOriginales.map(r => String(r[col.field] ?? '')).filter(Boolean)
      )].sort();
    }
  }

  getFilterSuggestions(field: string): string[] { return this.filterSuggestions[field] ?? []; }

  buscarSugerencias(field: string, query: string): void {
    const all = this._suggestionsIndex[field] ?? [];
    const q = query.toLowerCase();
    this.filterSuggestions[field] = q ? all.filter(v => v.toLowerCase().includes(q)) : all;
  }

  onFilterSelect(field: string, value: string): void {
    this.filtrosActivos.set(field, value.toLowerCase());
    this.filterModels[field] = value;
    this.aplicarFiltros();
  }

  onFilterKeyUp(field: string, value: string): void {
    if (value.trim()) { this.filtrosActivos.set(field, value.toLowerCase()); }
    else { this.filtrosActivos.delete(field); }
    this.aplicarFiltros();
  }

  clearFilter(field: string): void {
    this.filtrosActivos.delete(field);
    this.filterModels[field] = '';
    this.aplicarFiltros();
  }

  aplicarFiltros(): void {
    let datos = [...this.datosOriginales];
    for (const [field, valor] of this.filtrosActivos) {
      datos = datos.filter(r => String(r[field] ?? '').toLowerCase().includes(valor));
    }
    this.datosActuales = datos;
    this.totalFilas.set(datos.length);
  }

  limpiarFiltros(): void {
    this.filtrosActivos.clear();
    this.filterModels = {};
    this.filterSuggestions = {};
    this.aplicarFiltros();
  }

  toggleColumna(field: string): void {
    const actual = this.columnasVisibles();
    if (actual.some(c => c.field === field)) {
      this.columnasVisibles.set(actual.filter(c => c.field !== field));
    } else {
      const original = this.columns.find(c => c.field === field);
      if (original) {
        const orden = this.columns.map(c => c.field);
        const nuevo = [...actual, original].sort((a, b) => orden.indexOf(a.field) - orden.indexOf(b.field));
        this.columnasVisibles.set(nuevo);
      }
    }
  }

  exportCSV(): void {
    const cols = this.columnasVisibles();
    const header = cols.map(c => c.header).join(',');
    const rows = this.datosActuales.map(r =>
      cols.map(c => `"${String(r[c.field] ?? '').replace(/"/g, '""')}"`).join(',')
    );
    const csv = [header, ...rows].join('\n');
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url; a.download = 'export.csv'; a.click();
    URL.revokeObjectURL(url);
  }
}
