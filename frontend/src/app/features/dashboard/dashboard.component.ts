import { Component, inject, signal, computed, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { CardModule } from 'primeng/card';
import { SkeletonModule } from 'primeng/skeleton';
import { ButtonModule } from 'primeng/button';
import { DividerModule } from 'primeng/divider';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { TooltipModule } from 'primeng/tooltip';
import { TagModule } from 'primeng/tag';

import { ApiService } from '../../core/services/api.service';
import { ContextService } from '../../core/services/context.service';
import type { ResumenPlantel, Plantel } from '../../core/models';
import { ApexNotificationService } from 'apex-component-library';

interface NivelStats {
  nivel_educativo_id: string;
  nombre_nivel: string;
  grupos_activos: number;
  grados: number;
}

interface PlantelStats {
  id: string;
  nombre_plantel: string;
  clave_ct: string | null;
  total_alumnos: number;
  total_profesores: number;
  total_grupos: number;
  niveles: NivelStats[];
}

interface DistribucionNivel {
  nombre_nivel: string;
  total_alumnos: number;
  total_grupos: number;
}

const NIVEL_COLOR: Record<string, string> = {
  PRIMARIA: '#22c55e', SECUNDARIA: '#3b82f6', PREPARATORIA: '#a855f7',
};
const NIVEL_ICON: Record<string, string> = {
  PRIMARIA: 'pi-star', SECUNDARIA: 'pi-book', PREPARATORIA: 'pi-graduation-cap',
};

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
    CommonModule, RouterModule, FormsModule, CardModule, SkeletonModule,
    ButtonModule, DividerModule, DialogModule, InputTextModule, TooltipModule, TagModule
  ],
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

      <!-- ── Mi Plantel (para usuarios con plantel seleccionado que NO son admin global) ── -->
      @if (!ctx.esAdminGlobal() && ctx.plantel() && resumen()) {
        <p-divider styleClass="dash-divider" />
        <h4 class="section-title">Mi Plantel</h4>
        <div class="mi-plantel-card">
          <div class="card-title-row">
            <div class="avatar">{{ ctx.plantel()!.nombre_plantel[0] }}</div>
            <div>
              <h3 class="card-nombre">{{ ctx.plantel()!.nombre_plantel }}</h3>
              @if (ctx.plantel()!.clave_ct) {
                <code class="clave-ct">{{ ctx.plantel()!.clave_ct }}</code>
              }
            </div>
          </div>
          <div class="kpi-row">
            <div class="kpi">
              <span class="kv">{{ resumen()!.total_alumnos | number }}</span>
              <span class="kl">Alumnos</span>
            </div>
            <div class="sep"></div>
            <div class="kpi">
              <span class="kv">{{ resumen()!.total_profesores }}</span>
              <span class="kl">Profesores</span>
            </div>
            <div class="sep"></div>
            <div class="kpi">
              <span class="kv">{{ resumen()!.total_grupos_activos }}</span>
              <span class="kl">Grupos activos</span>
            </div>
            <div class="sep"></div>
            <div class="kpi">
              <span class="kv">{{ resumen()!.total_clases_hoy }}</span>
              <span class="kl">Clases hoy</span>
            </div>
          </div>
        </div>
      }

      <!-- ── Planteles del Instituto (Administradores Globales — siempre visible) ── -->
      @if (ctx.esAdminGlobal()) {
        <p-divider styleClass="dash-divider" />
        <h4 class="section-title">Planteles del Instituto</h4>
        @if (loadingPlanteles()) {
          <div class="cards-grid">
            @for (i of [1, 2, 3]; track i) {
              <p-skeleton height="280px" />
            }
          </div>
        } @else {
          <div class="cards-grid">
            @for (p of stats(); track p.id) {
              <div class="plantel-card" [class.activo]="ctx.plantel()?.id === p.id">
                <div class="card-top">
                  <div class="card-title-row">
                    <div class="avatar">{{ p.nombre_plantel[0] }}</div>
                    <div>
                      <h3 class="card-nombre">{{ p.nombre_plantel }}</h3>
                      @if (p.clave_ct) { <code class="clave-ct">{{ p.clave_ct }}</code> }
                    </div>
                  </div>
                  @if (ctx.plantel()?.id === p.id) {
                    <p-tag value="Contexto actual" severity="success" />
                  }
                </div>

                <div class="kpi-row">
                  <div class="kpi">
                    <span class="kv">{{ p.total_alumnos | number }}</span>
                    <span class="kl">Alumnos</span>
                  </div>
                  <div class="sep"></div>
                  <div class="kpi">
                    <span class="kv">{{ p.total_profesores }}</span>
                    <span class="kl">Profesores</span>
                  </div>
                  <div class="sep"></div>
                  <div class="kpi">
                    <span class="kv">{{ p.total_grupos }}</span>
                    <span class="kl">Grupos</span>
                  </div>
                </div>

                <div class="niveles">
                  @for (n of p.niveles; track n.nivel_educativo_id) {
                    <div class="nivel-row" [style.border-left-color]="color(n.nombre_nivel)">
                      <i class="pi" [class]="icon(n.nombre_nivel)" [style.color]="color(n.nombre_nivel)"></i>
                      <span class="nn">{{ n.nombre_nivel | titlecase }}</span>
                      <span class="nd">{{ n.grados }} grados</span>
                      <span class="nd">{{ n.grupos_activos }} grupos</span>
                    </div>
                  }
                </div>

                <div class="card-btns">
                  <p-button label="Seleccionar" icon="pi pi-check" severity="primary" size="small"
                    [text]="true" (onClick)="seleccionar(p)" />
                  @if (isAdmin()) {
                    <p-button icon="pi pi-pencil" severity="warn" size="small"
                      [text]="true" pTooltip="Editar plantel" (onClick)="abrirEditar(p)" />
                  }
                </div>
              </div>
            }
          </div>
        }
      }

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

    <!-- Diálogo editar plantel -->
    <p-dialog [(visible)]="dlgPlantel" header="Editar plantel"
      [modal]="true" [draggable]="false" [style]="{width:'380px'}">
      @if (plantelEdit) {
        <div class="form-grid">
          <label>Nombre *</label>
          <input pInputText [(ngModel)]="plantelEdit.nombre_plantel" />
          <label>Clave CT</label>
          <input pInputText [(ngModel)]="plantelEdit.clave_ct" placeholder="CCT12345" />
        </div>
        <ng-template pTemplate="footer">
          <p-button label="Cancelar" severity="secondary" [text]="true" (onClick)="dlgPlantel=false" />
          <p-button label="Guardar" icon="pi pi-save" [loading]="saving()" (onClick)="guardarPlantel()" />
        </ng-template>
      }
    </p-dialog>
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

    /* Mi Plantel card (usuarios no-admin con plantel activo) */
    .mi-plantel-card {
      background: var(--surface-0); border: 1px solid var(--primary-200); border-radius: 12px;
      padding: 1.25rem; display: flex; flex-direction: column; gap: .9rem;
      box-shadow: 0 0 0 2px var(--primary-50); max-width: 480px;
    }

    /* Cards Grid (Planteles) */
    .cards-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 1.25rem; margin-top: 0.5rem; }
    .plantel-card {
      background: var(--surface-0); border: 1px solid var(--surface-200); border-radius: 12px;
      padding: 1.25rem; display: flex; flex-direction: column; gap: .9rem;
      transition: box-shadow .15s, border-color .15s;
    }
    .plantel-card:hover { box-shadow: 0 4px 16px rgba(0,0,0,.08); border-color: var(--primary-200); }
    .plantel-card.activo { border-color: var(--primary-color); box-shadow: 0 0 0 2px var(--primary-100); }

    .card-top { display: flex; justify-content: space-between; align-items: flex-start; }
    .card-title-row { display: flex; gap: .7rem; align-items: center; }
    .avatar {
      width: 42px; height: 42px; border-radius: 10px; background: var(--primary-color); color: #fff;
      display: flex; align-items: center; justify-content: center;
      font-weight: 800; font-size: 1.1rem; flex-shrink: 0;
    }
    .card-nombre { font-size: 1rem; font-weight: 700; margin: 0; }
    .clave-ct { font-size: .72rem; color: var(--text-color-secondary); background: var(--surface-100); padding: .1rem .4rem; border-radius: 3px; }

    .kpi-row { display: flex; align-items: center; justify-content: space-around; background: var(--surface-50); border-radius: 8px; padding: .7rem; }
    .kpi { display: flex; flex-direction: column; align-items: center; }
    .kv { font-size: 1.35rem; font-weight: 700; color: var(--primary-color); line-height: 1.1; }
    .kl { font-size: .7rem; color: var(--text-color-secondary); }
    .sep { width: 1px; height: 28px; background: var(--surface-200); }

    .niveles { display: flex; flex-direction: column; gap: .35rem; }
    .nivel-row {
      display: flex; align-items: center; gap: .55rem; padding: .4rem .65rem;
      border-radius: 6px; background: var(--surface-50); border-left: 3px solid #ccc;
    }
    .nn { font-size: .83rem; font-weight: 600; flex: 1; text-transform: capitalize; }
    .nd { font-size: .72rem; color: var(--text-color-secondary); background: var(--surface-100); padding: .1rem .35rem; border-radius: 10px; }
    .nivel-row i { font-size: .82rem; }

    .card-btns { display: flex; gap: .4rem; padding-top: .25rem; border-top: 1px solid var(--surface-100); flex-wrap: wrap; }
    .form-grid { display: grid; grid-template-columns: 100px 1fr; gap: .75rem 1rem; align-items: center; padding: .5rem 0; }
    .form-grid label { font-size: .85rem; color: var(--text-color-secondary); font-weight: 600; }

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
  private readonly notify = inject(ApexNotificationService);

  resumen      = signal<ResumenPlantel | null>(null);
  distribucion = signal<DistribucionNivel[]>([]);
  loading      = signal(true);
  loadingChart = signal(true);

  // Planteles properties
  stats            = signal<PlantelStats[]>([]);
  loadingPlanteles = signal(true);
  dlgPlantel       = false;
  plantelEdit: { id: string; nombre_plantel: string; clave_ct: string | null } | null = null;
  saving           = signal(false);

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

    // Load planteles stats if user is global admin
    if (this.ctx.esAdminGlobal()) {
      this.loadingPlanteles.set(true);
      this.api.get<PlantelStats[]>('/planteles/stats').subscribe({
        next: s => { this.stats.set(s); this.loadingPlanteles.set(false); },
        error: () => this.loadingPlanteles.set(false),
      });
    }
  }

  readonly maxAlumnos = computed(() => Math.max(1, ...this.distribucion().map(d => d.total_alumnos)));
  readonly maxGrupos  = computed(() => Math.max(1, ...this.distribucion().map(d => d.total_grupos)));

  barPct(value: number, max: number): number {
    return max > 0 ? Math.round((value / max) * 100) : 0;
  }

  isAdmin(): boolean { return this.ctx.nivelAcceso() <= 1; }

  abrirEditar(p: PlantelStats): void {
    this.plantelEdit = { id: p.id, nombre_plantel: p.nombre_plantel, clave_ct: p.clave_ct };
    this.dlgPlantel = true;
  }

  guardarPlantel(): void {
    if (!this.plantelEdit || !this.plantelEdit.nombre_plantel.trim()) return;
    this.saving.set(true);
    this.api.patch(`/admin/planteles/${this.plantelEdit.id}`, {
      nombre_plantel: this.plantelEdit.nombre_plantel,
      clave_ct: this.plantelEdit.clave_ct,
    }).subscribe({
      next: () => {
        this.stats.update(list => list.map(s =>
          s.id === this.plantelEdit!.id
            ? { ...s, nombre_plantel: this.plantelEdit!.nombre_plantel, clave_ct: this.plantelEdit!.clave_ct }
            : s
        ));
        this.notify.success('Guardado', 'Plantel actualizado');
        this.dlgPlantel = false;
        this.saving.set(false);
      },
      error: e => {
        this.notify.error('Error', e.error?.detail ?? 'Error al guardar');
        this.saving.set(false);
      },
    });
  }

  seleccionar(p: PlantelStats): void {
    this.ctx.setPlantel({
      id: p.id, nombre_plantel: p.nombre_plantel,
      clave_ct: p.clave_ct ?? undefined,
      escuela_id: '', is_active: true,
    } as Plantel);
  }

  color(n: string): string { return NIVEL_COLOR[n] ?? '#94a3b8'; }
  icon(n: string):  string { return NIVEL_ICON[n]  ?? 'pi-school'; }
}
