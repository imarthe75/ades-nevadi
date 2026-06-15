import {
  Component, Input, Output, EventEmitter, ChangeDetectionStrategy,
  ContentChild, TemplateRef, OnChanges, SimpleChanges
} from '@angular/core';
import { NgTemplateOutlet, LowerCasePipe } from '@angular/common';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { ProgressBarModule } from 'primeng/progressbar';

export interface ReportColumn {
  field: string;
  header: string;
  type?: 'text' | 'number' | 'date' | 'currency' | 'badge' | 'custom';
  align?: 'left' | 'center' | 'right';
  width?: string;
  sortable?: boolean;
}

export interface ReportConfig {
  striped?: boolean;
  bordered?: boolean;
  hover?: boolean;
  compact?: boolean;
  emptyMessage?: string;
}

@Component({
  selector: 'apex-report',
  standalone: true,
  imports: [TableModule, ButtonModule, ProgressBarModule, NgTemplateOutlet, LowerCasePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="apex-report-container">

      <!-- Toolbar -->
      <div class="apex-report-toolbar">
        @if (title) {
          <h4 class="apex-report-title">{{ title }}</h4>
        }
        <div class="apex-report-actions">
          <button
            pButton
            type="button"
            icon="pi pi-download"
            label="Export CSV"
            class="p-button-sm p-button-outlined"
            (click)="exportCsv()">
          </button>
        </div>
      </div>

      <!-- Loading bar -->
      @if (loading) {
        <p-progressBar mode="indeterminate" styleClass="apex-report-progress" />
      }

      <!-- Table -->
      <p-table
        [value]="data"
        [columns]="columns"
        [paginator]="paginator"
        [rows]="rows"
        [rowsPerPageOptions]="[10, 20, 50, 100]"
        [loading]="loading"
        [sortField]="sortField"
        [sortOrder]="sortOrder"
        (onSort)="onSort.emit($event)"
        (onRowSelect)="rowSelect.emit($event)"
        selectionMode="single"
        styleClass="p-datatable-sm apex-report-table"
        [tableStyle]="{ 'min-width': '100%' }">

        <ng-template pTemplate="header" let-columns>
          <tr>
            @for (col of columns; track col.field) {
              <th
                [pSortableColumn]="col.sortable ? col.field : null"
                [style.width]="col.width"
                [class]="'align-' + (col.align || 'left')">
                {{ col.header }}
                @if (col.sortable) {
                  <p-sortIcon [field]="col.field" />
                }
              </th>
            }
          </tr>
        </ng-template>

        <ng-template pTemplate="body" let-row let-columns="columns">
          <tr [pSelectableRow]="row">
            @for (col of columns; track col.field) {
              <td [class]="'align-' + (col.align || 'left')">
                @if (col.type === 'custom' && customCellTemplate) {
                  <ng-container *ngTemplateOutlet="customCellTemplate; context: { $implicit: row, column: col }" />
                } @else if (col.type === 'badge') {
                  <span class="apex-report-badge apex-badge-{{ row[col.field] | lowercase }}">
                    {{ row[col.field] }}
                  </span>
                } @else {
                  {{ formatCell(row, col) }}
                }
              </td>
            }
          </tr>
        </ng-template>

        <ng-template pTemplate="emptymessage" let-columns>
          <tr>
            <td [attr.colspan]="columns.length" class="apex-report-empty">
              <span class="pi pi-inbox empty-icon"></span>
              <span>{{ config?.emptyMessage ?? 'No data found.' }}</span>
            </td>
          </tr>
        </ng-template>

      </p-table>
    </div>
  `,
  styles: [`
    .apex-report-container {
      background: var(--surface-card);
      border: 1px solid var(--surface-border);
      border-radius: var(--border-radius, 6px);
      overflow: hidden;
    }
    .apex-report-toolbar {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 0.6rem 1rem;
      background: var(--surface-section);
      border-bottom: 1px solid var(--surface-border);
      gap: 1rem;
    }
    .apex-report-title {
      margin: 0;
      font-size: 0.85rem;
      font-weight: 700;
      color: var(--text-color);
      text-transform: uppercase;
      letter-spacing: 0.05em;
    }
    .apex-report-actions { display: flex; gap: 0.5rem; }
    :host ::ng-deep .apex-report-progress { height: 3px; border-radius: 0; }
    :host ::ng-deep .apex-report-table thead th {
      background: var(--surface-50, #f9fafb);
      font-size: 0.78rem;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.05em;
      color: var(--text-color-secondary);
      border-bottom: 2px solid var(--surface-border);
      white-space: nowrap;
    }
    :host ::ng-deep .apex-report-table tbody td {
      font-size: 0.875rem;
      color: var(--text-color);
      border-bottom: 1px solid var(--surface-100, #f3f4f6);
    }
    :host ::ng-deep .apex-report-table tbody tr:hover td {
      background: var(--surface-hover, #f9fafb);
    }
    .apex-report-empty {
      text-align: center;
      padding: 3rem 1rem;
      color: var(--text-color-secondary);
      font-size: 0.875rem;
    }
    .apex-report-empty .empty-icon {
      display: block;
      font-size: 2rem;
      margin-bottom: 0.5rem;
      color: var(--surface-300);
    }
    .align-left   { text-align: left; }
    .align-center { text-align: center; }
    .align-right  { text-align: right; }
    .apex-report-badge {
      display: inline-block;
      padding: 0.15rem 0.6rem;
      border-radius: 20px;
      font-size: 0.72rem;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.04em;
    }
  `]
})
export class ApexReportComponent implements OnChanges {
  @Input() title?: string;
  @Input() columns: ReportColumn[] = [];
  @Input() data: any[] = [];
  @Input() config: ReportConfig = {
    striped: true, bordered: true, hover: true, compact: false,
    emptyMessage: 'No data found.'
  };
  @Input() loading: boolean = false;
  @Input() paginator: boolean = true;
  @Input() rows: number = 20;
  @Input() sortField?: string;
  @Input() sortOrder: 1 | -1 = 1;

  @Output() onSort = new EventEmitter<{ field: string; order: number }>();
  @Output() rowSelect = new EventEmitter<any>();

  @ContentChild('customCellTemplate') customCellTemplate?: TemplateRef<any>;

  ngOnChanges(_changes: SimpleChanges): void {}

  formatCell(row: any, col: ReportColumn): string {
    const value = row[col.field];
    if (value === null || value === undefined) return '';
    switch (col.type) {
      case 'date':
        return new Date(value).toLocaleDateString('es-MX');
      case 'currency':
        return typeof value === 'number'
          ? new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' }).format(value)
          : value;
      case 'number':
        return typeof value === 'number' ? value.toLocaleString('es-MX') : value;
      default:
        return String(value);
    }
  }

  exportCsv(): void {
    if (!this.data?.length || !this.columns?.length) return;
    const headers = this.columns.map(c => c.header).join(',');
    const rows = this.data.map(row =>
      this.columns.map(col => {
        const v = this.formatCell(row, col);
        return `"${String(v).replace(/"/g, '""')}"`;
      }).join(',')
    );
    const csv = [headers, ...rows].join('\n');
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `${this.title ?? 'report'}_${new Date().toISOString().slice(0, 10)}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  }
}
