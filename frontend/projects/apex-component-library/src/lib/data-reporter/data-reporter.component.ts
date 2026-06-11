import { Component, Input, Output, EventEmitter, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApexInteractiveGridComponent, GridColumn } from '../interactive-grid/interactive-grid.component';

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
  imports: [CommonModule, FormsModule, ApexInteractiveGridComponent]
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
