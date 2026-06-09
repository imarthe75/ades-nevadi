import { Component, inject, signal, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { CardModule } from 'primeng/card';
import { SkeletonModule } from 'primeng/skeleton';
import { ButtonModule } from 'primeng/button';
import { DividerModule } from 'primeng/divider';

import { ApiService } from '../../core/services/api.service';
import { ContextService } from '../../core/services/context.service';
import type { ResumenPlantel } from '../../core/models';

interface DistribucionNivel {
  nombre_nivel: string;
  total_alumnos: number;
  total_grupos: number;
}

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterModule, CardModule, SkeletonModule, ButtonModule, DividerModule],
  template: `
    <div class="dashboard">
      <!-- ── Bienvenida contextual ── -->
      <div class="welcome-bar">
        <div class="welcome-left">
          <h2>Dashboard</h2>
          <p class="subtitle">
            @if (ctx.plantel()) {
              <i class="pi pi-map-marker"></i> {{ ctx.plantel()!.nombre_plantel }}
            } @else {
              <i class="pi pi-building"></i> Instituto Nevadi — Vista global
            }
            @if (ctx.ciclo()) {
              <span class="ciclo-chip">Ciclo {{ ctx.ciclo()!.nombre_ciclo }}</span>
            }
          </p>
        </div>
        @if (ctx.usuario()) {
          <div class="user-greeting">
            <span class="greeting-name">{{ ctx.usuario()!.nombre_completo }}</span>
            <span class="greeting-rol">{{ ctx.rolLabel() }}</span>
          </div>
        }
      </div>

      <!-- ── KPI Cards ── -->
      <div class="kpi-cards">
        <div class="kpi-card" routerLink="/alumnos">
          <div class="kpi-icon-wrap alumnos"><i class="pi pi-users"></i></div>
          <div class="kpi-body">
            @if (loading()) { <p-skeleton width="60px" height="2rem" /> }
            @else { <span class="kpi-value">{{ resumen()?.total_alumnos ?? 0 | number }}</span> }
            <span class="kpi-label">Alumnos inscritos</span>
          </div>
          <i class="pi pi-chevron-right kpi-arrow"></i>
        </div>

        <div class="kpi-card" routerLink="/profesores">
          <div class="kpi-icon-wrap profesores"><i class="pi pi-id-card"></i></div>
          <div class="kpi-body">
            @if (loading()) { <p-skeleton width="60px" height="2rem" /> }
            @else { <span class="kpi-value">{{ resumen()?.total_profesores ?? 0 | number }}</span> }
            <span class="kpi-label">Profesores activos</span>
          </div>
          <i class="pi pi-chevron-right kpi-arrow"></i>
        </div>

        <div class="kpi-card" routerLink="/grupos">
          <div class="kpi-icon-wrap grupos"><i class="pi pi-book"></i></div>
          <div class="kpi-body">
            @if (loading()) { <p-skeleton width="60px" height="2rem" /> }
            @else { <span class="kpi-value">{{ resumen()?.total_grupos_activos ?? 0 | number }}</span> }
            <span class="kpi-label">Grupos activos</span>
          </div>
          <i class="pi pi-chevron-right kpi-arrow"></i>
        </div>

        <div class="kpi-card kpi-highlight">
          <div class="kpi-icon-wrap clases"><i class="pi pi-calendar"></i></div>
          <div class="kpi-body">
            @if (loading()) { <p-skeleton width="60px" height="2rem" /> }
            @else { <span class="kpi-value">{{ resumen()?.total_clases_hoy ?? 0 | number }}</span> }
            <span class="kpi-label">Clases hoy</span>
          </div>
        </div>
      </div>

      <!-- ── Gráfico: distribución por nivel ── -->
      @if (distribucion().length > 0) {
        <p-divider styleClass="dash-divider" />
        <h4 class="section-title">Distribución por nivel educativo</h4>
        <div class="chart-panel">
          @for (item of distribucion(); track item.nombre_nivel) {
            <div class="chart-row">
              <span class="chart-label">{{ item.nombre_nivel }}</span>
              <div class="chart-bars">
                <div class="bar-wrap">
                  <div class="bar alumnos-bar"
                    [style.width]="barPct(item.total_alumnos, maxAlumnos()) + '%'"
                    [title]="item.total_alumnos + ' alumnos'">
                  </div>
                  <span class="bar-count">{{ item.total_alumnos }}</span>
                </div>
                <div class="bar-wrap">
                  <div class="bar grupos-bar"
                    [style.width]="barPct(item.total_grupos, maxGrupos()) + '%'"
                    [title]="item.total_grupos + ' grupos'">
                  </div>
                  <span class="bar-count grupos-count">{{ item.total_grupos }}</span>
                </div>
              </div>
            </div>
          }
          <div class="chart-legend">
            <span class="legend-dot alumnos-dot"></span> Alumnos &nbsp;
            <span class="legend-dot grupos-dot"></span> Grupos
          </div>
        </div>
      } @else if (loadingChart()) {
        <p-divider styleClass="dash-divider" />
        <p-skeleton height="120px" />
      }

      <!-- ── Accesos rápidos ── -->
      <p-divider styleClass="dash-divider" />
      <h4 class="section-title">Accesos rápidos</h4>
      <div class="quick-links">
        @for (link of quickLinks; track link.route) {
          <a [routerLink]="link.route" class="quick-link">
            <i [class]="'pi ' + link.icon"></i>
            <span>{{ link.label }}</span>
          </a>
        }
      </div>
    </div>
  `,
  styles: [`
    .dashboard { padding: 0.5rem 0; }

    /* Bienvenida */
    .welcome-bar { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 1.5rem; }
    .welcome-bar h2 { margin: 0 0 0.2rem; color: var(--text-primary); }
    .subtitle { display: flex; align-items: center; gap: 0.4rem; color: var(--text-secondary); font-size: 0.85rem; margin: 0; }
    .subtitle i { font-size: 0.8rem; }
    .ciclo-chip { background: var(--nevadi-red-lighter); color: var(--nevadi-red-dark); font-size: 0.72rem; font-weight: 600; padding: 0.1rem 0.5rem; border-radius: 20px; margin-left: 0.25rem; }
    .user-greeting { display: flex; flex-direction: column; align-items: flex-end; gap: 0.1rem; }
    .greeting-name { font-size: 0.85rem; font-weight: 600; color: var(--text-primary); }
    .greeting-rol { font-size: 0.72rem; color: var(--text-secondary); }

    /* KPI cards — clicables tipo APEX */
    .kpi-cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(210px, 1fr)); gap: 0.75rem; }
    .kpi-card {
      display: flex; align-items: center; gap: 1rem;
      background: var(--surface-card); border: 1px solid var(--surface-border);
      border-radius: 10px; padding: 1rem 1.25rem;
      cursor: pointer; text-decoration: none;
      transition: box-shadow .15s, border-color .15s;
    }
    .kpi-card:hover { box-shadow: 0 4px 12px rgba(0,0,0,.08); border-color: var(--nevadi-red-light); }
    .kpi-card.kpi-highlight { border-left: 3px solid var(--color-warning); }
    .kpi-icon-wrap {
      width: 44px; height: 44px; border-radius: 10px; flex-shrink: 0;
      display: flex; align-items: center; justify-content: center;
      font-size: 1.25rem;
    }
    .kpi-icon-wrap.alumnos   { background: #EFF6FF; color: var(--nevadi-slate-mid); }
    .kpi-icon-wrap.profesores{ background: #F0FDF4; color: var(--nevadi-sage-mid); }
    .kpi-icon-wrap.grupos    { background: #F5F3FF; color: #7c3aed; }
    .kpi-icon-wrap.clases    { background: #FFFBEB; color: var(--color-warning); }
    .kpi-body { flex: 1; }
    .kpi-value { display: block; font-size: 1.9rem; font-weight: 800; line-height: 1.1; color: var(--text-primary); }
    .kpi-label { font-size: 0.75rem; color: var(--text-secondary); font-weight: 500; }
    .kpi-arrow { color: var(--text-muted); font-size: 0.8rem; }

    /* CSS bar chart — distribución por nivel */
    .chart-panel { background: var(--surface-card); border: 1px solid var(--surface-border); border-radius: 10px; padding: 1rem 1.25rem; }
    .chart-row { display: flex; align-items: center; gap: 0.75rem; margin-bottom: 0.75rem; }
    .chart-label { font-size: 0.78rem; font-weight: 600; color: var(--text-secondary); min-width: 110px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .chart-bars { flex: 1; display: flex; flex-direction: column; gap: 0.3rem; }
    .bar-wrap { display: flex; align-items: center; gap: 0.5rem; }
    .bar { height: 12px; border-radius: 6px; min-width: 4px; transition: width .4s ease; }
    .alumnos-bar { background: var(--nevadi-slate-mid, #4A6FA5); opacity: .85; }
    .grupos-bar  { background: var(--nevadi-sage-mid, #5A7A5A); opacity: .75; }
    .bar-count { font-size: 0.72rem; color: var(--text-muted); min-width: 28px; }
    .grupos-count { color: var(--nevadi-sage-mid, #5A7A5A); }
    .chart-legend { display: flex; align-items: center; gap: 0.25rem; font-size: 0.72rem; color: var(--text-secondary); margin-top: 0.5rem; padding-top: 0.5rem; border-top: 1px solid var(--surface-border); }
    .legend-dot { display: inline-block; width: 10px; height: 10px; border-radius: 50%; }
    .alumnos-dot { background: var(--nevadi-slate-mid, #4A6FA5); }
    .grupos-dot  { background: var(--nevadi-sage-mid, #5A7A5A); }

    /* Accesos rápidos */
    .dash-divider { margin: 1.25rem 0 1rem !important; }
    .section-title { margin: 0 0 0.75rem; font-size: 0.85rem; font-weight: 700; text-transform: uppercase; letter-spacing: .05em; color: var(--text-secondary); }
    .quick-links { display: flex; flex-wrap: wrap; gap: 0.5rem; }
    .quick-link {
      display: flex; align-items: center; gap: 0.4rem;
      background: var(--surface-50); border: 1px solid var(--surface-border);
      border-radius: 8px; padding: 0.45rem 0.85rem;
      color: var(--text-primary); text-decoration: none; font-size: 0.83rem;
      transition: background .12s, border-color .12s;
    }
    .quick-link:hover { background: var(--nevadi-red-lighter); border-color: var(--nevadi-red-light); color: var(--nevadi-red-dark); }
    .quick-link i { font-size: 0.82rem; color: var(--nevadi-red); }
  `],
})
export class DashboardComponent {
  private readonly api = inject(ApiService);
  readonly ctx = inject(ContextService);

  resumen      = signal<ResumenPlantel | null>(null);
  distribucion = signal<DistribucionNivel[]>([]);
  loading      = signal(true);
  loadingChart = signal(true);

  readonly quickLinks = [
    { route: '/calificaciones',  icon: 'pi-star',          label: 'Calificaciones' },
    { route: '/asistencias',     icon: 'pi-check-square',  label: 'Asistencias' },
    { route: '/tareas',          icon: 'pi-file-edit',     label: 'Tareas' },
    { route: '/comunicados',     icon: 'pi-envelope',      label: 'Comunicados' },
    { route: '/gradebook',       icon: 'pi-book',          label: 'Gradebook' },
    { route: '/grade-analytics', icon: 'pi-chart-bar',     label: 'Grade Analytics' },
    { route: '/portal',          icon: 'pi-id-card',       label: 'Portal Alumno' },
    { route: '/reportes',        icon: 'pi-file-pdf',      label: 'Reportes' },
  ];

  constructor() {
    effect(() => {
      const plantelId = this.ctx.plantel()?.id;
      const params = plantelId ? { plantel_id: plantelId } : {};

      this.loading.set(true);
      this.api.get<ResumenPlantel>('/stats/resumen', params).subscribe({
        next: (r) => { this.resumen.set(r); this.loading.set(false); },
        error: () => this.loading.set(false),
      });

      this.loadingChart.set(true);
      this.api.get<DistribucionNivel[]>('/stats/distribucion', params).subscribe({
        next: (d) => { this.distribucion.set(d); this.loadingChart.set(false); },
        error: () => { this.distribucion.set([]); this.loadingChart.set(false); },
      });
    });
  }

  maxAlumnos(): number {
    return Math.max(1, ...this.distribucion().map(d => d.total_alumnos));
  }

  maxGrupos(): number {
    return Math.max(1, ...this.distribucion().map(d => d.total_grupos));
  }

  barPct(value: number, max: number): number {
    return max > 0 ? Math.round((value / max) * 100) : 0;
  }
}
