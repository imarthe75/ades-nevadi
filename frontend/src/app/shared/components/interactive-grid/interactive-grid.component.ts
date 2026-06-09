/**
 * Interactive Grid Component — APEX-style data table
 * Características:
 *   - Sortable columns
 *   - Header filters (búsqueda por columna)
 *   - Column chooser (mostrar/ocultar columnas)
 *   - Inline editing con detección de cambios
 *   - Exportación a CSV
 *
 * Uso:
 *   <app-interactive-grid
 *     [data]="datos()"
 *     [columns]="columnas"
 *     [loading]="cargando()"
 *     (rowSelected)="onRowSelect($event)"
 *     (rowEdited)="onRowEdit($event)"
 *   />
 */
import { Component, Input, Output, EventEmitter, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { TooltipModule } from 'primeng/tooltip';
import { MenuModule } from 'primeng/menu';
import { DialogModule } from 'primeng/dialog';
import { SelectModule } from 'primeng/select';

export interface ColumnConfig {
  field: string;
  header: string;
  sortable?: boolean;
  filterable?: boolean;
  editable?: boolean;
  width?: string;
  type?: 'text' | 'number' | 'date' | 'boolean' | 'select';
  selectOptions?: Array<{ label: string; value: any }>;
}

@Component({
  selector: 'app-interactive-grid',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    TableModule, ButtonModule, InputTextModule, TooltipModule, MenuModule, DialogModule, SelectModule,
  ],
  template: `
    <div class="grid-container">
      <!-- TOOLBAR -->
      <div class="grid-toolbar">
        <div class="toolbar-left">
          <p-button icon="pi pi-list" [text]="true" severity="secondary"
            pTooltip="Mostrar/ocultar columnas"
            (onClick)="abrirColumnChooser()" />
          <p-button icon="pi pi-download" [text]="true" severity="secondary"
            pTooltip="Descargar CSV"
            (onClick)="exportToCSV()" />
        </div>
        <div class="toolbar-right">
          <span style="font-size:.85rem;color:var(--text-secondary)">{{ totalFilas() }} registro(s)</span>
        </div>
      </div>

      <!-- TABLA INTERACTIVA -->
      <p-table
        #dt
        [value]="datosActuales"
        [loading]="loading()"
        [columns]="columnasVisibles()"
        [paginator]="true"
        [rows]="20"
        [globalFilterFields]="columnasVisibles().map(c => c.field)"
        [rowsPerPageOptions]="[10, 20, 50, 100]"
        styleClass="p-datatable-sm p-datatable-striped p-datatable-gridlines">

        <ng-template pTemplate="header" let-columns>
          <tr>
            @for (col of columns; track col.field) {
              <th [pSortableColumn]="col.field" [style.width]="col.width || 'auto'">
                <div class="p-column-header">
                  <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:.25rem">
                    <strong style="cursor:pointer" (click)="toggleSort(col.field)">{{ col.header }}</strong>
                    @if (col.sortable !== false) {
                      @if (ordenActual?.field === col.field && ordenActual?.direction === 'asc') {
                        <i class="pi pi-arrow-up" style="font-size:.7rem;color:var(--primary-color);cursor:pointer"></i>
                      } @else if (ordenActual?.field === col.field && ordenActual?.direction === 'desc') {
                        <i class="pi pi-arrow-down" style="font-size:.7rem;color:var(--primary-color);cursor:pointer"></i>
                      } @else {
                        <i class="pi pi-arrow-up-down" style="font-size:.7rem;color:var(--text-muted);cursor:pointer"></i>
                      }
                    }
                  </div>
                  @if (col.filterable !== false) {
                    <input pInputText type="text"
                      placeholder="Filtrar..."
                      (input)="filterByColumn(col.field, $any($event.target).value)"
                      style="width:100%;font-size:.8rem;padding:.3rem;border:1px solid var(--surface-border);border-radius:3px" />
                  }
                </div>
              </th>
            }
            <th style="width:80px;text-align:center"><strong>Acciones</strong></th>
          </tr>
        </ng-template>

        <ng-template pTemplate="body" let-rowData let-columns="columns">
          <tr>
            @for (col of columns; track col.field) {
              <td>
                @if (!col.editable) { <span>{{ rowData[col.field] }}</span> }
                @if (col.editable && col.type !== 'select') {
                  <input pInputText
                    [type]="col.type === 'number' ? 'number' : 'text'"
                    [value]="rowData[col.field]"
                    (blur)="onCellEdit(rowData, col.field, $event)"
                    style="width:100%;font-size:.85rem" />
                }
                @if (col.editable && col.type === 'select') {
                  <p-select
                    [options]="col.selectOptions || []"
                    [(ngModel)]="rowData[col.field]"
                    optionLabel="label"
                    optionValue="value"
                    (onChange)="onCellEdit(rowData, col.field, $event.value)"
                    style="width:100%;font-size:.85rem" />
                }
              </td>
            }
            <td style="text-align:center">
              <p-button icon="pi pi-pencil" severity="warn" [text]="true"
                (onClick)="rowSelected.emit(rowData)"
                pTooltip="Editar" [tooltipPosition]="'top'" />
            </td>
          </tr>
        </ng-template>

        <!-- EMPTY MESSAGE -->
        <ng-template pTemplate="emptymessage" let-columns>
          <tr><td [colSpan]="columns.length + 1"
            style="text-align:center;padding:2rem;color:var(--text-muted)">
            {{ loading() ? 'Cargando datos...' : 'Sin registros' }}
          </td></tr>
        </ng-template>
      </p-table>
    </div>

    <!-- COLUMN CHOOSER DIALOG -->
    <p-dialog [visible]="mostrarColumnChooser()" (visibleChange)="mostrarColumnChooser.set($event)" header="Mostrar/Ocultar Columnas"
      [modal]="true" [style]="{width:'400px'}">
      <ul class="col-chooser-list">
        @for (col of columns; track col.field) {
          <li class="col-chooser-item">
            <input type="checkbox"
              [checked]="columnasVisibles().some(c => c.field === col.field)"
              (change)="toggleColumnVisibility(col.field)"
              [id]="'col-' + col.field" />
            <label [for]="'col-' + col.field">{{ col.header }}</label>
          </li>
        }
      </ul>
      <ng-template pTemplate="footer">
        <p-button label="Cerrar" icon="pi pi-check" (onClick)="mostrarColumnChooser.set(false)" />
      </ng-template>
    </p-dialog>
  `,
  styles: [`
    .grid-container { padding:1rem;background:var(--surface-0);border-radius:8px }
    .grid-toolbar { display:flex;justify-content:space-between;align-items:center;margin-bottom:1rem;gap:.5rem }
    .toolbar-left, .toolbar-right { display:flex;gap:.5rem;align-items:center }
    .p-column-header { display:flex;flex-direction:column;gap:.25rem }
    .col-chooser-list { list-style:none;margin:0;padding:0;display:flex;flex-direction:column;gap:.75rem }
    .col-chooser-item { display:flex;align-items:center;gap:.5rem }
    .col-chooser-item label { cursor:pointer;flex:1;font-size:.9rem }
    :deep(.p-datatable-sm .p-datatable-thead > tr > th) { padding:.5rem }
    :deep(.p-datatable-sm .p-datatable-tbody > tr > td) { padding:.5rem }
    :deep(.p-datatable-sm .p-datatable-thead > tr > th input[pInputText]) {
      font-size:.75rem;padding:.3rem;width:100%
    }
  `],
})
export class InteractiveGridComponent {
  @Input() data: any[] = [];
  @Input() columns: ColumnConfig[] = [];
  @Input() loading = signal(false);
  @Output() rowSelected = new EventEmitter<any>();
  @Output() rowEdited = new EventEmitter<any>();

  columnasVisibles = signal<ColumnConfig[]>([]);
  mostrarColumnChooser = signal(false);
  filtrosActivos = new Map<string, string>();
  datosOriginales: any[] = [];
  datosActuales: any[] = [];
  totalFilas = signal(0);
  ordenActual: { field: string; direction: 'asc' | 'desc' } | null = null;

  constructor() {}

  ngOnInit(): void {
    this.datosOriginales = [...this.data];
    this.datosActuales = [...this.data];
    this.columnasVisibles.set(this.columns);
    this.updateTotalFilas();
  }

  ngOnChanges(): void {
    this.datosOriginales = [...this.data];
    this.datosActuales = [...this.data];
    this.aplicarFiltros();
    this.updateTotalFilas();
  }

  updateTotalFilas(): void {
    this.totalFilas.set(this.data.length);
  }

  abrirColumnChooser(): void {
    this.mostrarColumnChooser.set(true);
  }

  toggleColumnVisibility(field: string): void {
    const visible = this.columnasVisibles().filter(c => c.field !== field);
    const oculta = this.columns.find(c => c.field === field);
    if (oculta && visible.length < this.columns.length) {
      this.columnasVisibles.set([...visible, oculta]);
    } else {
      this.columnasVisibles.set(visible);
    }
  }

  filterByColumn(field: string, value: string): void {
    if (value.trim()) {
      this.filtrosActivos.set(field, value.toLowerCase());
    } else {
      this.filtrosActivos.delete(field);
    }
    this.aplicarFiltros();
  }

  toggleSort(field: string): void {
    const columna = this.columns.find(c => c.field === field);
    if (!columna || columna.sortable === false) return;

    // Cambiar dirección de sort o resetear
    if (this.ordenActual?.field === field) {
      if (this.ordenActual.direction === 'asc') {
        this.ordenActual = { field, direction: 'desc' };
      } else {
        this.ordenActual = null; // Reset
      }
    } else {
      this.ordenActual = { field, direction: 'asc' };
    }
    this.aplicarFiltros();
  }

  private aplicarFiltros(): void {
    // 1. Aplicar filtros
    let resultado = [...this.datosOriginales];

    if (this.filtrosActivos.size > 0) {
      resultado = resultado.filter(row => {
        for (const [field, filtro] of this.filtrosActivos.entries()) {
          const valor = String(row[field] || '').toLowerCase();
          if (!valor.includes(filtro)) {
            return false;
          }
        }
        return true;
      });
    }

    // 2. Aplicar sort
    if (this.ordenActual) {
      resultado.sort((a, b) => {
        const valA = a[this.ordenActual!.field];
        const valB = b[this.ordenActual!.field];

        let cmp = 0;
        if (typeof valA === 'number' && typeof valB === 'number') {
          cmp = valA - valB;
        } else {
          cmp = String(valA || '').localeCompare(String(valB || ''));
        }

        return this.ordenActual!.direction === 'asc' ? cmp : -cmp;
      });
    }

    this.datosActuales = resultado;
    this.updateTotalFilas();
  }

  onCellEdit(row: any, field: string, event: any): void {
    const value = event?.value !== undefined ? event.value : event.target?.value;
    row[field] = value;
    this.rowEdited.emit(row);
  }

  exportToCSV(): void {
    const csv = this.generarCSV();
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const link = document.createElement('a');
    const url = URL.createObjectURL(blob);
    link.setAttribute('href', url);
    link.setAttribute('download', `export-${new Date().toISOString()}.csv`);
    link.style.visibility = 'hidden';
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  }

  private generarCSV(): string {
    const headers = this.columnasVisibles().map(c => c.header).join(',');
    const rows = this.data.map(row =>
      this.columnasVisibles()
        .map(col => {
          const val = row[col.field] || '';
          return `"${String(val).replace(/"/g, '""')}"`;
        })
        .join(',')
    );
    return [headers, ...rows].join('\n');
  }
}
