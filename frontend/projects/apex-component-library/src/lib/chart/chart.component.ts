import { Component, Input, Output, EventEmitter, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ChartModule } from 'primeng/chart';

@Component({
  selector: 'apex-chart',
  standalone: true,
  imports: [CommonModule, ChartModule],
  template: `
    <div class="apex-chart-container">
      <h3 *ngIf="title" class="apex-chart-title">{{title}}</h3>
      <p-chart 
        [type]="type" 
        [data]="data" 
        [options]="options" 
        [width]="width" 
        [height]="height"
        (onDataSelect)="onSelect.emit($event)">
      </p-chart>
    </div>
  `,
  styles: [`
    .apex-chart-container {
      background: var(--surface-card);
      border-radius: var(--border-radius);
      padding: 1.5rem;
      box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06);
    }
    .apex-chart-title {
      margin-top: 0;
      margin-bottom: 1rem;
      font-size: 1.25rem;
      font-weight: 600;
      color: var(--text-color);
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ApexChartComponent {
  @Input() type: 'line' | 'bar' | 'pie' | 'doughnut' | 'radar' | 'polarArea' = 'bar';
  @Input() data: any;
  @Input() options: any;
  @Input() width: string = '100%';
  @Input() height: string = '300px';
  @Input() title?: string;

  @Output() onSelect = new EventEmitter<any>();
}
