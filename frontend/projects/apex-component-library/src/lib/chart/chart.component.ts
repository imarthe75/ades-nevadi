import { Component, Input, Output, EventEmitter, ChangeDetectionStrategy, OnChanges, SimpleChanges } from '@angular/core';
import { ChartModule } from 'primeng/chart';

const PALETTE = [
  '#3b82f6', '#10b981', '#f59e0b', '#ef4444',
  '#8b5cf6', '#06b6d4', '#f97316', '#84cc16'
];

/** Default APEX-style Chart.js options */
function apexChartDefaults(type: string): any {
  const gridColor = 'rgba(0,0,0,0.06)';
  const textColor = '#4b5563';
  const base: any = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: {
        position: 'bottom',
        labels: {
          usePointStyle: true,
          pointStyle: 'circle',
          padding: 16,
          font: { family: 'Inter, sans-serif', size: 12 },
          color: textColor
        }
      },
      tooltip: {
        backgroundColor: '#1f2937',
        titleFont: { family: 'Inter, sans-serif', size: 12, weight: '600' },
        bodyFont:  { family: 'Inter, sans-serif', size: 11 },
        padding: 10,
        cornerRadius: 6
      }
    }
  };

  if (type === 'bar' || type === 'line' || type === 'radar') {
    base.scales = {
      x: {
        grid: { color: gridColor, drawBorder: false },
        ticks: { color: textColor, font: { size: 11 } }
      },
      y: {
        grid: { color: gridColor, drawBorder: false },
        ticks: { color: textColor, font: { size: 11 } },
        beginAtZero: true
      }
    };
    if (type === 'line') {
      base.elements = {
        line: { tension: 0.35, borderWidth: 2 },
        point: { radius: 3, hoverRadius: 5 }
      };
    }
  }

  return base;
}

export interface BarLineDataInput {
  labels: string[];
  datasets: Array<{ label: string; data: number[]; color?: string }>;
}

export interface PieDataInput {
  labels: string[];
  data: number[];
  colors?: string[];
}

@Component({
  selector: 'apex-chart',
  standalone: true,
  imports: [ChartModule],
  template: `
    <div class="apex-chart-container" [style.height]="height">
      @if (title) {
        <div class="apex-chart-header">
          <h4 class="apex-chart-title">{{ title }}</h4>
          @if (subtitle) {
            <span class="apex-chart-subtitle">{{ subtitle }}</span>
          }
        </div>
      }
      <div class="apex-chart-body" [style.height]="title ? 'calc(100% - 3rem)' : '100%'">
        <p-chart
          [type]="type"
          [data]="data"
          [options]="mergedOptions"
          [width]="width"
          height="100%"
          (onDataSelect)="dataSelect.emit($event)">
        </p-chart>
      </div>
    </div>
  `,
  styles: [`
    .apex-chart-container {
      background: var(--surface-card);
      border: 1px solid var(--surface-border);
      border-radius: var(--border-radius, 6px);
      overflow: hidden;
      display: flex;
      flex-direction: column;
    }
    .apex-chart-header {
      padding: 0.75rem 1rem 0.5rem;
      border-bottom: 1px solid var(--surface-border);
      background: var(--surface-section);
      flex-shrink: 0;
    }
    .apex-chart-title {
      margin: 0 0 0.15rem;
      font-size: 0.875rem;
      font-weight: 600;
      color: var(--text-color);
      text-transform: uppercase;
      letter-spacing: 0.04em;
    }
    .apex-chart-subtitle {
      font-size: 0.8rem;
      color: var(--text-color-secondary);
    }
    .apex-chart-body {
      padding: 1rem;
      flex: 1;
    }
    :host ::ng-deep canvas { max-width: 100%; }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ApexChartComponent implements OnChanges {
  @Input() type: 'bar' | 'line' | 'pie' | 'doughnut' | 'radar' | 'polarArea' = 'bar';
  @Input() data: any;
  @Input() options: any = {};
  @Input() width: string = '100%';
  @Input() height: string = '320px';
  @Input() title?: string;
  @Input() subtitle?: string;

  /** Convenience input: auto-builds a bar chart from structured data */
  @Input() barData?: BarLineDataInput;
  /** Convenience input: auto-builds a line chart from structured data */
  @Input() lineData?: BarLineDataInput;
  /** Convenience input: auto-builds a pie/doughnut chart from structured data */
  @Input() pieData?: PieDataInput;

  @Output() dataSelect = new EventEmitter<any>();

  mergedOptions: any = {};

  ngOnChanges(changes: SimpleChanges): void {
    // Apply convenience data inputs before merging options
    this._applyConvenienceInputs();

    if (changes['type'] || changes['options'] || changes['barData'] || changes['lineData'] || changes['pieData']) {
      const defaults = apexChartDefaults(this.type);
      this.mergedOptions = this.deepMerge(defaults, this.options ?? {});
    }
  }

  private _applyConvenienceInputs(): void {
    if (this.barData) {
      this.data = {
        labels: this.barData.labels,
        datasets: this.barData.datasets.map((d, i) => ({
          label: d.label,
          data: d.data,
          backgroundColor: (d.color ?? PALETTE[i % PALETTE.length]) + '99',
          borderColor: d.color ?? PALETTE[i % PALETTE.length],
          borderWidth: 1.5,
        }))
      };
      this.type = 'bar';
      return;
    }

    if (this.lineData) {
      this.data = {
        labels: this.lineData.labels,
        datasets: this.lineData.datasets.map((d, i) => ({
          label: d.label,
          data: d.data,
          borderColor: d.color ?? PALETTE[i % PALETTE.length],
          backgroundColor: (d.color ?? PALETTE[i % PALETTE.length]) + '22',
          fill: true,
        }))
      };
      this.type = 'line';
      return;
    }

    if (this.pieData) {
      this.data = {
        labels: this.pieData.labels,
        datasets: [{
          data: this.pieData.data,
          backgroundColor: this.pieData.colors ?? this.pieData.data.map((_, i) => PALETTE[i % PALETTE.length] + 'cc'),
          borderColor: '#fff',
          borderWidth: 2,
        }]
      };
      // Keep current type (pie or doughnut) — caller sets it via [type]
    }
  }

  private deepMerge(target: any, source: any): any {
    const result = { ...target };
    for (const key of Object.keys(source)) {
      if (source[key] && typeof source[key] === 'object' && !Array.isArray(source[key])) {
        result[key] = this.deepMerge(result[key] ?? {}, source[key]);
      } else {
        result[key] = source[key];
      }
    }
    return result;
  }
}
