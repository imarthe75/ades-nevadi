/**
 * Interactive Grid Component — APEX-style
 *
 * Comportamiento estilo Oracle APEX Interactive Grid:
 *   - Click en fila → rowSelected (abre drawer/detalle)
 *   - Filtro por columna: autocomplete LOV con valores únicos + texto libre
 *   - Sort nativo de PrimeNG (clic en cabecera)
 *   - Columna Acciones: botón eliminar (rowDeleted)
 *   - Column chooser, exportar CSV
 */
import {
  Component, Input, Output, EventEmitter, signal, computed, OnChanges, SimpleChanges
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { TooltipModule } from 'primeng/tooltip';
import { DialogModule } from 'primeng/dialog';
import { AutoCompleteModule } from 'primeng/autocomplete';

export interface ColumnConfig {
  field: string;
  header: string;
  sortable?: boolean;
  filterable?: boolean;
  editable?: boolean;
  width?: string;
  align?: 'left' | 'center' | 'right';
  type?: 'text' | 'number' | 'date' | 'boolean' | 'select';
  selectOptions?: Array<{ label: string; value: any }>;
  template?: (row: any) => string;
}

@Component({
  selector: 'app-interactive-grid',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    TableModule, ButtonModule, InputTextModule, TooltipModule,
    DialogModule, AutoCompleteModule,
  ],
  template: `
    <div class="grid-container">
      <!-- TOOLBAR -->
      <div class="grid-toolbar">
        <div class="toolbar-left">
          <p-button icon="pi pi-th-large" [text]="true" severity="secondary"
            pTooltip="Mostrar/ocultar columnas"
            (onClick)="mostrarColumnChooser.set(true)" />
          <p-button icon="pi pi-download" [text]="true" severity="secondary"
            pTooltip="Descargar CSV"
            (onClick)="exportToCSV()" />
          @if (filtrosActivos.size > 0) {
            <p-button icon="pi pi-filter-slash" [text]="true" severity="secondary"
              pTooltip="Limpiar filtros"
              (onClick)="limpiarFiltros()" />
          }
        </div>
        <div class="toolbar-right">
          <span class="grid-count">{{ totalFilas() }} registro(s)</span>
        </div>
      </div>

      <!-- TABLA -->
      <p-table
        [value]="datosActuales"
        [loading]="loading"
        [columns]="columnasVisibles()"
        [paginator]="true"
        [rows]="20"
        [rowsPerPageOptions]="[10,20,50,100]"
        [sortMode]="'single'"
        styleClass="p-datatable-sm p-datatable-striped p-datatable-gridlines apex-grid">

        <!-- CABECERA -->
        <ng-template pTemplate="header" let-columns>
          <tr class="header-row">
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

        <!-- CUERPO -->
        <ng-template pTemplate="body" let-rowData let-columns="columns">
          <tr class="data-row" (click)="rowSelected.emit(rowData)" title="Clic para ver/editar">
            @for (col of columns; track col.field) {
              <td>{{ rowData[col.field] }}</td>
            }
            <td class="acciones-cell" (click)="$event.stopPropagation()">
              <p-button icon="pi pi-pencil" severity="secondary" [text]="true" size="small"
                pTooltip="Editar" tooltipPosition="top"
                (onClick)="rowSelected.emit(rowData)" />
              @if (showDelete) {
                <p-button icon="pi pi-trash" severity="danger" [text]="true" size="small"
                  pTooltip="Eliminar" tooltipPosition="top"
                  (onClick)="rowDeleted.emit(rowData)" />
              }
            </td>
          </tr>
        </ng-template>

        <ng-template pTemplate="emptymessage" let-columns>
          <tr>
            <td [colSpan]="columns.length + 1" class="empty-msg">
              {{ loading ? 'Cargando...' : 'Sin registros' }}
            </td>
          </tr>
        </ng-template>
      </p-table>
    </div>

    <!-- COLUMN CHOOSER -->
    <p-dialog
      [visible]="mostrarColumnChooser()"
      (visibleChange)="mostrarColumnChooser.set($event)"
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
        <p-button label="Cerrar" icon="pi pi-check"
          (onClick)="mostrarColumnChooser.set(false)" />
      </ng-template>
    </p-dialog>
  `,
  styles: [`
    .grid-container { background: var(--surface-0); border-radius: 8px; padding: 0; overflow: hidden; }

    .grid-toolbar {
      display: flex; justify-content: space-between; align-items: center;
      padding: .4rem .75rem; border-bottom: 1px solid var(--surface-200);
      background: var(--surface-50);
    }
    .toolbar-left, .toolbar-right { display: flex; gap: .25rem; align-items: center; }
    .grid-count { font-size: .82rem; color: var(--text-color-secondary); }

    /* Cabecera */
    .header-row th { vertical-align: top; padding: .4rem .5rem !important; }
    .col-header-inner {
      display: flex; align-items: center; justify-content: space-between; gap: .25rem;
      margin-bottom: .3rem;
    }
    .col-title { font-size: .82rem; font-weight: 600; white-space: nowrap; }

    /* Filtro LOV */
    .col-filter { width: 100%; }
    :host ::ng-deep .filter-autocomplete { width: 100%; }
    :host ::ng-deep .filter-autocomplete .p-autocomplete-input { width: 100%; font-size: .78rem; }
    :host ::ng-deep .filter-autocomplete .p-autocomplete-dropdown { padding: .2rem .4rem; }

    /* Fila clickeable */
    .data-row { cursor: pointer; transition: background .1s; }
    .data-row:hover { background: var(--primary-50, #fef2f3) !important; }
    .data-row td { font-size: .87rem; }

    /* Acciones */
    .col-acciones { width: 90px; text-align: center; }
    .acciones-cell { text-align: center; white-space: nowrap; }

    /* Misc */
    .empty-msg { text-align: center; padding: 2rem; color: var(--text-color-secondary); }

    /* Column chooser */
    .col-chooser-list { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: .65rem; }
    .col-chooser-item { display: flex; align-items: center; gap: .5rem; cursor: pointer; }
    .col-chooser-item label { flex: 1; font-size: .9rem; cursor: pointer; }

    /* Override PrimeNG table spacing */
    :host ::ng-deep .apex-grid .p-datatable-thead > tr > th { padding: .4rem .5rem; }
    :host ::ng-deep .apex-grid .p-datatable-tbody > tr > td { padding: .45rem .5rem; }
  `],
})
export class InteractiveGridComponent implements OnChanges {
  @Input() data: any[] = [];
  @Input() columns: ColumnConfig[] = [];
  @Input() loading = false;
  @Input() showDelete = false;
  @Input() searchable = true;
  @Output() rowSelected = new EventEmitter<any>();
  @Output() rowDeleted  = new EventEmitter<any>();

  columnasVisibles = signal<ColumnConfig[]>([]);
  mostrarColumnChooser = signal(false);
  totalFilas = signal(0);

  filtrosActivos = new Map<string, string>();
  datosOriginales: any[] = [];
  datosActuales:   any[] = [];

  // LOV suggestions per column
  filterSuggestions: Record<string, string[]> = {};
  filterModels:      Record<string, string>   = {};

  // Índice precalculado de valores únicos por campo — se reconstruye en ngOnChanges
  private _suggestionsIndex: Record<string, string[]> = {};

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['columns']) {
      this.columnasVisibles.set(this.columns);
    }
    if (changes['data']) {
      this.datosOriginales = [...this.data];
      this.datosActuales   = [...this.data];
      this._rebuildSuggestionsIndex();
      this.aplicarFiltros();
    }
  }

  // ── LOV filter ───────────────────────────────────────────────────────────────

  private _rebuildSuggestionsIndex(): void {
    this._suggestionsIndex = {};
    for (const col of this.columns) {
      this._suggestionsIndex[col.field] = [...new Set(
        this.datosOriginales.map(r => String(r[col.field] ?? '')).filter(Boolean)
      )].sort();
    }
  }

  getFilterSuggestions(field: string): string[] {
    return this.filterSuggestions[field] ?? [];
  }

  buscarSugerencias(field: string, query: string): void {
    const all = this._suggestionsIndex[field] ?? [];
    const q = query.toLowerCase();
    this.filterSuggestions[field] = q
      ? all.filter(v => v.toLowerCase().includes(q))
      : all;
  }

  onFilterSelect(field: string, value: string): void {
    this.filtrosActivos.set(field, value.toLowerCase());
    this.filterModels[field] = value;
    this.aplicarFiltros();
  }

  onFilterKeyUp(field: string, value: string): void {
    if (value.trim()) {
      this.filtrosActivos.set(field, value.toLowerCase());
    } else {
      this.filtrosActivos.delete(field);
      this.filterModels[field] = '';
    }
    this.aplicarFiltros();
  }

  clearFilter(field: string): void {
    this.filtrosActivos.delete(field);
    this.filterModels[field] = '';
    this.aplicarFiltros();
  }

  limpiarFiltros(): void {
    this.filtrosActivos.clear();
    this.filterModels = {};
    this.datosActuales = [...this.datosOriginales];
    this.totalFilas.set(this.datosActuales.length);
  }

  // ── Sort / filter ─────────────────────────────────────────────────────────────

  private aplicarFiltros(): void {
    let res = [...this.datosOriginales];
    for (const [field, filtro] of this.filtrosActivos.entries()) {
      res = res.filter(r => String(r[field] ?? '').toLowerCase().includes(filtro));
    }
    this.datosActuales = res;
    this.totalFilas.set(res.length);
  }

  // ── Column chooser ────────────────────────────────────────────────────────────

  toggleColumna(field: string): void {
    const actual = this.columnasVisibles();
    const existe = actual.some(c => c.field === field);
    if (existe) {
      this.columnasVisibles.set(actual.filter(c => c.field !== field));
    } else {
      const col = this.columns.find(c => c.field === field);
      if (col) this.columnasVisibles.set([...actual, col]);
    }
  }

  // ── Export ────────────────────────────────────────────────────────────────────

  exportToCSV(): void {
    const cols = this.columnasVisibles();
    const headers = cols.map(c => c.header).join(',');
    const rows = this.datosActuales.map(r =>
      cols.map(c => `"${String(r[c.field] ?? '').replace(/"/g, '""')}"`).join(',')
    );
    const csv = [headers, ...rows].join('\n');
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = `export-${new Date().toISOString().slice(0,10)}.csv`;
    a.click();
  }
}
