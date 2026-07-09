import { Component, OnDestroy, inject, signal, OnInit, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject, takeUntil } from 'rxjs';
import { ChartModule } from 'primeng/chart';
import { CardModule } from 'primeng/card';
import { SelectModule } from 'primeng/select';
import { TagModule } from 'primeng/tag';
import { ApiService } from '../../core/services/api.service';
import { ContextService } from '../../core/services/context.service';

/**
 * Dashboard de KPIs operativos para directores de plantel (nivelAcceso ≥ 4).
 * Presenta métricas clave del ciclo activo: asistencia, calificaciones,
 * evaluaciones docentes y alertas conductuales, segmentadas por grupo y nivel.
 * Usa `ChartModule` de PrimeNG para gráficas y señales reactivas para
 * actualización automática al cambiar el ciclo o plantel en `ContextService`.
 */
@Component({
  selector: 'app-director-dashboard',
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
  imports: [CommonModule, FormsModule, ChartModule, CardModule, SelectModule, TagModule],
  template: `
<div class="page-header">
  <div class="page-title"><i class="pi pi-chart-bar"></i> Panel de Dirección</div>
  <div class="subtitle">Avance académico, asistencia y cobertura curricular a nivel institucional</div>
</div>

@if (loading()) {
  <div class="loading-state">
    <i class="pi pi-spin pi-spinner" style="font-size: 2rem; color: var(--p-primary-color);"></i>
    <span style="margin-top: 8px;">Cargando datos del panel...</span>
  </div>
} @else {
  <!-- KPI Grid (Oracle APEX style) -->
  <div class="kpi-grid">
    <div class="kpi-card border-left-primary">
      <div class="kpi-icon"><i class="pi pi-users text-primary"></i></div>
      <div class="kpi-content">
        <div class="kpi-val">{{ kpis().total_alumnos ?? 0 }}</div>
        <div class="kpi-lbl">Total Alumnos</div>
      </div>
    </div>
    
    <div class="kpi-card border-left-info">
      <div class="kpi-icon"><i class="pi pi-folder text-info"></i></div>
      <div class="kpi-content">
        <div class="kpi-val">{{ kpis().total_grupos ?? 0 }}</div>
        <div class="kpi-lbl">Total Grupos</div>
      </div>
    </div>

    <div class="kpi-card border-left-success">
      <div class="kpi-icon"><i class="pi pi-star text-success"></i></div>
      <div class="kpi-content">
        <div class="kpi-val">{{ kpis().promedio_general ?? '—' }}</div>
        <div class="kpi-lbl">Promedio Gral.</div>
      </div>
    </div>

    <div class="kpi-card border-left-warn">
      <div class="kpi-icon"><i class="pi pi-calendar text-warn"></i></div>
      <div class="kpi-content">
        <div class="kpi-val">{{ kpis().pct_asistencia != null ? kpis().pct_asistencia + '%' : '—' }}</div>
        <div class="kpi-lbl">Asistencia</div>
      </div>
    </div>

    <div class="kpi-card border-left-danger">
      <div class="kpi-icon"><i class="pi pi-exclamation-triangle text-danger"></i></div>
      <div class="kpi-content">
        <div class="kpi-val">{{ kpis().pct_riesgo_alto != null ? kpis().pct_riesgo_alto + '%' : '—' }}</div>
        <div class="kpi-lbl">Riesgo Alto</div>
      </div>
    </div>

    <div class="kpi-card border-left-secondary">
      <div class="kpi-icon"><i class="pi pi-book text-secondary"></i></div>
      <div class="kpi-content">
        <div class="kpi-val">{{ kpis().pct_cobertura != null ? kpis().pct_cobertura + '%' : '—' }}</div>
        <div class="kpi-lbl">Cobertura Curricular</div>
      </div>
    </div>
  </div>

  <div class="charts-grid mt-4">
    <!-- Chart: Promedio por Grado -->
    <div class="chart-card">
      <div class="chart-header">
        <span class="chart-title"><i class="pi pi-sliders-h"></i> Calificación Promedio por Grado</span>
      </div>
      <div class="chart-body">
        <p-chart type="bar" [data]="gradoChartData" [options]="chartOptions"></p-chart>
      </div>
    </div>

    <!-- Chart: Cobertura por Asignatura -->
    <div class="chart-card">
      <div class="chart-header">
        <span class="chart-title"><i class="pi pi-bookmark"></i> Desempeño por Asignatura</span>
      </div>
      <div class="chart-body">
        <p-chart type="bar" [data]="materiaChartData" [options]="chartOptions"></p-chart>
      </div>
    </div>
  </div>
}
  `,
  styles: [`
    .page-header { margin-bottom: 24px; }
    .subtitle { color: var(--text-secondary); font-size: 0.9rem; margin-top: 4px; }
    
    .loading-state {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 60px;
      color: var(--text-secondary);
    }

    .kpi-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(170px, 1fr));
      gap: 16px;
    }

    .kpi-card {
      background: var(--p-surface-0);
      border: 1px solid var(--p-surface-border);
      border-radius: 6px;
      padding: 16px;
      display: flex;
      align-items: center;
      gap: 12px;
      box-shadow: 0 1px 3px rgba(0,0,0,0.05);
    }

    .border-left-primary { border-left: 4px solid #0284c7; }
    .border-left-info { border-left: 4px solid #06b6d4; }
    .border-left-success { border-left: 4px solid #16a34a; }
    .border-left-warn { border-left: 4px solid #d97706; }
    .border-left-danger { border-left: 4px solid #dc2626; }
    .border-left-secondary { border-left: 4px solid #4b5563; }

    .kpi-icon {
      font-size: 1.5rem;
      background: var(--p-surface-50);
      width: 42px;
      height: 42px;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
    }

    .text-primary { color: #0284c7; }
    .text-info { color: #06b6d4; }
    .text-success { color: #16a34a; }
    .text-warn { color: #d97706; }
    .text-danger { color: #dc2626; }
    .text-secondary { color: #4b5563; }

    .kpi-content { display: flex; flex-direction: column; }
    .kpi-val { font-size: 1.5rem; font-weight: 700; font-family: 'Jost', sans-serif; line-height: 1.2; }
    .kpi-lbl { font-size: 0.75rem; color: var(--p-text-muted-color); font-weight: 600; text-transform: uppercase; margin-top: 4px; }

    .charts-grid {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 24px;
    }
    @media (max-width: 991px) {
      .charts-grid { grid-template-columns: 1fr; }
    }

    .chart-card {
      background: var(--p-surface-0);
      border: 1px solid var(--p-surface-border);
      border-radius: 6px;
      box-shadow: 0 1px 3px rgba(0,0,0,0.05);
      padding: 16px;
    }

    .chart-header {
      border-bottom: 1px solid var(--p-surface-border);
      padding-bottom: 12px;
      margin-bottom: 16px;
    }

    .chart-title { font-weight: 700; font-size: 1rem; color: var(--text-primary); display: flex; align-items: center; gap: 8px; }
  `]
})
export class DirectorDashboardComponent implements OnInit implements OnInit, OnDestroy {
  private destroy$ = new Subject<void>();
  private readonly api = inject(ApiService);
  private readonly ctx = inject(ContextService);

  loading = signal(false);
  kpis = signal<any>({});
  
  gradoChartData: any;
  materiaChartData: any;
  chartOptions: any;

  constructor() {
    // Reload dashboard when school campus (plantel) changes in the top bar context
    effect(() => {
      const plantel = this.ctx.plantel();
      this.cargarDatos(plantel?.id);
    });
  }

  ngOnInit() {
    this.chartOptions = {
      plugins: {
        legend: { display: false }
      },
      scales: {
        y: {
          beginAtZero: true,
          grid: { color: 'rgba(0,0,0,0.05)' }
        },
        x: {
          grid: { display: false }
        }
      },
      responsive: true,
      maintainAspectRatio: false
    };
  }

  cargarDatos(plantelId?: string) {
    this.loading.set(true);
    const params: Record<string, any> = {};
    if (plantelId) {
      params['plantel_id'] = plantelId;
    }

    // Parallel fetch KPIs, Grados, Materias
    this.api.get<any>('/stats/director/kpis', params).subscribe({
      next: (kpis) => {
        this.kpis.set(kpis);
        
        // Avance por grado
        this.api.get<any[]>('/stats/director/avance-grado', params).subscribe(grados => {
          this.buildGradoChart(grados);
          
          // Avance por materia
          this.api.get<any[]>('/stats/director/avance-asignatura', params).subscribe(materias => {
            this.buildMateriaChart(materias);
            this.loading.set(false);
          });
        });
      },
      error: () => this.loading.set(false)
    });
  }

  buildGradoChart(grados: any[]) {
    const labels = (grados ?? []).map(g => g.grado);
    const data = (grados ?? []).map(g => g.promedio_grado);

    this.gradoChartData = {
      labels,
      datasets: [
        {
          label: 'Promedio general',
          backgroundColor: '#0284c7',
          borderColor: '#0284c7',
          data
        }
      ]
    };
  }

  buildMateriaChart(materias: any[]) {
    const labels = (materias ?? []).map(m => m.asignatura);
    const data = (materias ?? []).map(m => m.promedio_asignatura);

    this.materiaChartData = {
      labels,
      datasets: [
        {
          label: 'Promedio asignatura',
          backgroundColor: '#16a34a',
          borderColor: '#16a34a',
          data
        }
      ]
    };
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
