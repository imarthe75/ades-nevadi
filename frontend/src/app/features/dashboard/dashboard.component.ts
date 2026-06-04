import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { CardModule } from 'primeng/card';
import { SkeletonModule } from 'primeng/skeleton';

import { ApiService } from '../../core/services/api.service';
import { ContextService } from '../../core/services/context.service';
import type { ResumenPlantel } from '../../core/models';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, CardModule, SkeletonModule],
  template: `
    <div class="dashboard">
      <h2>Dashboard</h2>
      <p class="subtitle" *ngIf="ctx.plantel()">{{ ctx.plantel()!.nombre_plantel }}</p>

      <div class="kpi-cards">
        <p-card styleClass="kpi-card">
          <ng-template pTemplate="header">
            <div class="kpi-header">
              <i class="pi pi-users kpi-icon alumnos"></i>
              Alumnos
            </div>
          </ng-template>
          @if (loading()) {
            <p-skeleton width="80px" height="2.5rem" />
          } @else {
            <span class="kpi-value alumnos">{{ resumen()?.total_alumnos ?? 0 | number }}</span>
          }
        </p-card>

        <p-card styleClass="kpi-card">
          <ng-template pTemplate="header">
            <div class="kpi-header">
              <i class="pi pi-id-card kpi-icon profesores"></i>
              Profesores
            </div>
          </ng-template>
          @if (loading()) {
            <p-skeleton width="80px" height="2.5rem" />
          } @else {
            <span class="kpi-value profesores">{{ resumen()?.total_profesores ?? 0 | number }}</span>
          }
        </p-card>

        <p-card styleClass="kpi-card">
          <ng-template pTemplate="header">
            <div class="kpi-header">
              <i class="pi pi-book kpi-icon grupos"></i>
              Grupos Activos
            </div>
          </ng-template>
          @if (loading()) {
            <p-skeleton width="80px" height="2.5rem" />
          } @else {
            <span class="kpi-value grupos">{{ resumen()?.total_grupos_activos ?? 0 | number }}</span>
          }
        </p-card>

        <p-card styleClass="kpi-card">
          <ng-template pTemplate="header">
            <div class="kpi-header">
              <i class="pi pi-calendar kpi-icon clases"></i>
              Clases Hoy
            </div>
          </ng-template>
          @if (loading()) {
            <p-skeleton width="80px" height="2.5rem" />
          } @else {
            <span class="kpi-value clases">{{ resumen()?.total_clases_hoy ?? 0 | number }}</span>
          }
        </p-card>
      </div>
    </div>
  `,
  styles: [`
    .dashboard { padding: 0.5rem 0; }
    .dashboard h2 { margin: 0 0 0.25rem; color: #1e293b; }
    .subtitle { color: #64748b; font-size: 0.9rem; margin: 0 0 1.5rem; }
    .kpi-cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 1rem; margin-top: 1.5rem; }
    :host ::ng-deep .kpi-card { text-align: center; }
    .kpi-header { display: flex; align-items: center; justify-content: center; gap: 0.4rem; color: #64748b; font-size: 0.9rem; font-weight: 600; padding: 0.75rem 0 0; }
    .kpi-icon { font-size: 1.1rem; }
    .kpi-value { font-size: 2.5rem; font-weight: 700; display: block; padding-bottom: 0.5rem; }
    .kpi-value.alumnos, .kpi-icon.alumnos { color: #1d4ed8; }
    .kpi-value.profesores, .kpi-icon.profesores { color: #059669; }
    .kpi-value.grupos, .kpi-icon.grupos { color: #7c3aed; }
    .kpi-value.clases, .kpi-icon.clases { color: #d97706; }
  `],
})
export class DashboardComponent implements OnInit {
  private readonly api = inject(ApiService);
  readonly ctx = inject(ContextService);

  resumen = signal<ResumenPlantel | null>(null);
  loading = signal(true);

  ngOnInit(): void {
    const plantelId = this.ctx.plantel()?.id;
    const params = plantelId ? { plantel_id: plantelId } : {};
    this.api.get<ResumenPlantel>('/stats/resumen', params)
      .subscribe({
        next: (r) => { this.resumen.set(r); this.loading.set(false); },
        error: () => this.loading.set(false),
      });
  }
}
