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

  trackByRowId(index: number, row: GridRow): string | number {
    return row.id;
  }
  
  onRowSelect(row: GridRow, event: Event): void {
    const isChecked = (event.target as HTMLInputElement).checked;
    if (!isChecked) {
      this.selectedRows = this.selectedRows.filter(r => r.id !== row.id);
      this.rowDeselect.emit(row);
    } else {
      this.selectedRows = [...this.selectedRows, row];
      this.rowSelect.emit(row);
    }
  }

  isRowSelected(row: GridRow): boolean {
    return this.selectedRows.some(r => r.id === row.id);
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
  
  Math = Math;
}
