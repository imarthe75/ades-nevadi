import { Component, Input, Output, EventEmitter, ChangeDetectionStrategy, ContentChild, TemplateRef } from '@angular/core';
import { CommonModule } from '@angular/common';

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
  templateUrl: './report.component.html',
  styleUrls: ['./report.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
  imports: [CommonModule]
})
export class ApexReportComponent {
  @Input() title?: string;
  @Input() columns: ReportColumn[] = [];
  @Input() data: any[] = [];
  @Input() config: ReportConfig = {
    striped: true,
    bordered: true,
    hover: true,
    compact: false,
    emptyMessage: 'No data found.'
  };
  
  @Input() sortField?: string;
  @Input() sortOrder: 1 | -1 = 1;
  @Output() onSort = new EventEmitter<{field: string, order: number}>();
  
  @ContentChild('customCellTemplate') customCellTemplate?: TemplateRef<any>;

  handleSort(column: ReportColumn): void {
    if (!column.sortable) return;
    
    if (this.sortField === column.field) {
      this.sortOrder = this.sortOrder === 1 ? -1 : 1;
    } else {
      this.sortField = column.field;
      this.sortOrder = 1;
    }
    
    this.onSort.emit({ field: this.sortField, order: this.sortOrder });
  }

  formatCell(row: any, col: ReportColumn): any {
    const value = row[col.field];
    if (value === null || value === undefined) return '';
    
    switch (col.type) {
      case 'date':
        return new Date(value).toLocaleDateString();
      case 'currency':
        return typeof value === 'number' ? `$${value.toFixed(2)}` : value;
      default:
        return value;
    }
  }

  get tableClasses(): string {
    const classes = ['apex-report-table'];
    if (this.config.striped) classes.push('apex-table-striped');
    if (this.config.bordered) classes.push('apex-table-bordered');
    if (this.config.hover) classes.push('apex-table-hover');
    if (this.config.compact) classes.push('apex-table-compact');
    return classes.join(' ');
  }
}
