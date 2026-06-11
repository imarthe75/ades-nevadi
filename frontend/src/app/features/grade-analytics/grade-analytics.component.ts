import { Component, inject, OnInit, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { SelectModule } from 'primeng/select';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ProgressBarModule } from 'primeng/progressbar';
import { TooltipModule } from 'primeng/tooltip';
import { CardModule } from 'primeng/card';

import { ApiService } from '../../core/services/api.service';
import { ContextService } from '../../core/services/context.service';
import { ExportService, ExportColumn } from '../../core/services/export.service';

type TagSeverity = 'success' | 'info' | 'warn' | 'danger' | 'secondary' | 'contrast' | undefined;

interface TendenciaRow {
  nombre_materia: string;
  nombre_periodo: string;
  numero_periodo: number;
  promedio: number;
  minimo: number;
  maximo: number;
  aprobados: number;
  reprobados: number;
  pct_aprobados: number;
  alumnos_evaluados: number;
}

interface RiesgoRow {
  estudiante_id: string;
  nombre_alumno: string;
  nombre_grupo: string;
  nombre_grado: string;
  nombre_plantel: string;
  promedio_general: number;
  pct_asistencia: number;
  materias_reprobadas: number;
  nivel_riesgo: string;
}

interface DistribucionRow { rango: string; total_alumnos: number; pct: number; }
interface ResumenRow { nombre_plantel: string; nombre_nivel: string; total_alumnos: number; promedio_general: number; pct_aprobados: number; pct_riesgo: number; }

const RIESGO_SEV: Record<string, TagSeverity> = { ALTO: 'danger', MEDIO: 'warn', BAJO: 'success' };

@Component({
  selector: 'app-grade-analytics',
  standalone: true,
  imports: [
    CommonModule, FormsModule, RouterModule,
    ButtonModule, SelectModule, TableModule, TagModule,
    ProgressBarModule, TooltipModule, CardModule,
  ],
  template: `
    <div class="page-header">
      <div>
        <h2 class="page-title">Grade Analytics</h2>
        <p class="page-subtitle">Analítica de calificaciones y riesgo académico</p>
      </div>
      <div class="page-actions">
        <p-button icon="pi pi-download" label="CSV"   severity="secondary" [text]="true" (onClick)="exportRiesgoCSV()" />
        <p-button icon="pi pi-file-excel" label="Excel" severity="secondary" [text]="true" (onClick)="exportRiesgoXLSX()" />
        <p-button icon="pi pi-refresh" icon="pi pi-refresh" severity="secondary" [text]="true"
                  pTooltip="Datos de vistas materializadas — actualizadas cada hora"
                  (onClick)="cargarTodo()" />
      </div>
    </div>

    <!-- Filtros -->
    <div class="filter-bar">
      <p-select
        [options]="grupos()"
        [(ngModel)]="selectedGrupoId"
        optionLabel="label" optionValue="value"
        placeholder="Seleccionar grupo..."
        styleClass="filter-select"
        (onChange)="onGrupoChange()"
      

      [filter]="true" filterPlaceholder="Buscar..."/>
      <p-select
        [options]="nivelesRiesgo"
        [(ngModel)]="filtroRiesgo"
        optionLabel="label" optionValue="value"
        placeholder="Nivel de riesgo"
        styleClass="filter-select"
        (onChange)="cargarRiesgo()"
      

      [filter]="true" filterPlaceholder="Buscar..."/>
    </div>

    <!-- ── KPI cards ─────────────────────────────────────────────────── -->
    <div class="kpi-grid">
      <div class="kpi-card">
        <div class="kpi-icon" style="background:var(--blue-100); color:var(--blue-700)">
          <i class="pi pi-users"></i>
        </div>
        <div>
          <div class="kpi-value">{{ totalAlumnos() }}</div>
          <div class="kpi-label">Alumnos evaluados</div>
        </div>
      </div>
      <div class="kpi-card">
        <div class="kpi-icon" style="background:var(--green-100); color:var(--green-700)">
          <i class="pi pi-star-fill"></i>
        </div>
        <div>
          <div class="kpi-value">{{ promedioGeneral() | number:'1.1-1' }}</div>
          <div class="kpi-label">Promedio general</div>
        </div>
      </div>
      <div class="kpi-card">
        <div class="kpi-icon" style="background:var(--teal-100); color:var(--teal-700)">
          <i class="pi pi-check-circle"></i>
        </div>
        <div>
          <div class="kpi-value">{{ pctAprobados() | number:'1.0-1' }}%</div>
          <div class="kpi-label">% aprobados</div>
        </div>
      </div>
      <div class="kpi-card">
        <div class="kpi-icon" style="background:var(--red-100); color:var(--red-700)">
          <i class="pi pi-exclamation-triangle"></i>
        </div>
        <div>
          <div class="kpi-value">{{ enRiesgoAlto() }}</div>
          <div class="kpi-label">En riesgo ALTO</div>
        </div>
      </div>
    </div>

    <!-- ── Tabs: Tendencias / Riesgo / Distribución / Resumen plantel ── -->
    <div class="tabs">
      @for (t of tabs; track t.key) {
        <button class="tab-btn" [class.active]="activeTab === t.key" (click)="activeTab = t.key">
          <i [class]="'pi ' + t.icon"></i> {{ t.label }}
        </button>
      }
    </div>

    <!-- Tab: Tendencias por materia -->
    @if (activeTab === 'tendencias') {
      <p-table [value]="tendencias()" [loading]="loadingTendencias()" dataKey="nombre_materia"
               styleClass="p-datatable-sm p-datatable-striped" [paginator]="true" [rows]="20">
        <ng-template pTemplate="header">
          <tr>
            <th pSortableColumn="nombre_materia">Materia <p-sortIcon field="nombre_materia" /></th>
            <th pSortableColumn="nombre_periodo">Periodo <p-sortIcon field="nombre_periodo" /></th>
            <th pSortableColumn="promedio">Promedio <p-sortIcon field="promedio" /></th>
            <th>Rango (min–max)</th>
            <th>% Aprobados</th>
            <th>Evaluados</th>
          </tr>
        </ng-template>
        <ng-template pTemplate="body" let-r>
          <tr>
            <td><strong>{{ r.nombre_materia }}</strong></td>
            <td>{{ r.nombre_periodo }}</td>
            <td>
              <span [class]="promedioClass(r.promedio)">{{ r.promedio | number:'1.1-1' }}</span>
            </td>
            <td style="font-size:.8rem; color:var(--text-color-secondary)">
              {{ r.minimo | number:'1.1-1' }} – {{ r.maximo | number:'1.1-1' }}
            </td>
            <td>
              <div class="pct-bar-row">
                <div class="pct-bar-fill" [style.width]="r.pct_aprobados + '%'"
                     [style.background]="r.pct_aprobados >= 80 ? 'var(--green-400)' : r.pct_aprobados >= 60 ? 'var(--yellow-400)' : 'var(--red-400)'">
                </div>
                <span>{{ r.pct_aprobados | number:'1.0-1' }}%</span>
              </div>
            </td>
            <td>{{ r.alumnos_evaluados }}</td>
          </tr>
        </ng-template>
        <ng-template pTemplate="emptymessage">
          <tr><td colspan="6" class="empty-msg">Selecciona un grupo para ver las tendencias.</td></tr>
        </ng-template>
      </p-table>
    }

    <!-- Tab: Alumnos en riesgo -->
    @if (activeTab === 'riesgo') {
      <p-table [value]="riesgo()" [loading]="loadingRiesgo()" dataKey="estudiante_id"
               styleClass="p-datatable-sm p-datatable-striped" [paginator]="true" [rows]="20"
               [globalFilterFields]="['nombre_alumno','nombre_grupo']">
        <ng-template pTemplate="caption">
          <div style="display:flex; justify-content:flex-end;">
            <input pInputText placeholder="Buscar alumno..." style="font-size:.85rem; padding:.35rem .7rem;"
                   (input)="onFilterRiesgo($event)" />
          </div>
        </ng-template>
        <ng-template pTemplate="header">
          <tr>
            <th pSortableColumn="nombre_alumno">Alumno <p-sortIcon field="nombre_alumno" /></th>
            <th>Grupo</th>
            <th pSortableColumn="promedio_general">Promedio <p-sortIcon field="promedio_general" /></th>
            <th pSortableColumn="pct_asistencia">% Asistencia <p-sortIcon field="pct_asistencia" /></th>
            <th>Materias reprobadas</th>
            <th pSortableColumn="nivel_riesgo">Riesgo <p-sortIcon field="nivel_riesgo" /></th>
          </tr>
        </ng-template>
        <ng-template pTemplate="body" let-r>
          <tr>
            <td><strong>{{ r.nombre_alumno }}</strong></td>
            <td>{{ r.nombre_grupo }}</td>
            <td>
              <span [class]="promedioClass(r.promedio_general)">{{ r.promedio_general | number:'1.1-1' }}</span>
            </td>
            <td>
              <p-progressBar [value]="r.pct_asistencia"
                [style]="{'height':'6px'}"
                [styleClass]="r.pct_asistencia < 80 ? 'pbar-danger' : 'pbar-ok'" />
              <span style="font-size:.75rem">{{ r.pct_asistencia | number:'1.0-1' }}%</span>
            </td>
            <td style="text-align:center;">
              @if (r.materias_reprobadas > 0) {
                <span class="rep-badge">{{ r.materias_reprobadas }}</span>
              } @else {
                <span style="color:var(--green-500)">0</span>
              }
            </td>
            <td>
              <p-tag [value]="r.nivel_riesgo" [severity]="riesgoSev(r.nivel_riesgo)" />
            </td>
          </tr>
        </ng-template>
        <ng-template pTemplate="emptymessage">
          <tr><td colspan="6" class="empty-msg">No se encontraron alumnos con los filtros aplicados.</td></tr>
        </ng-template>
      </p-table>
    }

    <!-- Tab: Distribución calificaciones -->
    @if (activeTab === 'distribucion') {
      @if (distribucion().length === 0) {
        <div class="empty-msg" style="padding:2rem; text-align:center">
          Selecciona un grupo para ver la distribución de calificaciones.
        </div>
      } @else {
        <div class="dist-chart">
          <div class="dist-title">Distribución de calificaciones — {{ selectedGrupoLabel() }}</div>
          @for (d of distribucion(); track d.rango) {
            <div class="dist-row">
              <span class="dist-label">{{ d.rango }}</span>
              <div class="dist-bar-track">
                <div class="dist-bar-fill"
                     [style.width]="d.pct + '%'"
                     [style.background]="distColor(d.rango)">
                </div>
              </div>
              <span class="dist-count">{{ d.total_alumnos }} <span class="dist-pct">({{ d.pct | number:'1.0-1' }}%)</span></span>
            </div>
          }
        </div>
      }
    }

    <!-- Tab: Resumen ejecutivo por plantel -->
    @if (activeTab === 'resumen') {
      <p-table [value]="resumen()" [loading]="loadingResumen()"
               styleClass="p-datatable-sm p-datatable-striped">
        <ng-template pTemplate="header">
          <tr>
            <th>Plantel</th>
            <th>Nivel</th>
            <th>Alumnos</th>
            <th>Promedio gral.</th>
            <th>% Aprobados</th>
            <th>% En riesgo</th>
          </tr>
        </ng-template>
        <ng-template pTemplate="body" let-r>
          <tr>
            <td><strong>{{ r.nombre_plantel }}</strong></td>
            <td>{{ r.nombre_nivel }}</td>
            <td>{{ r.total_alumnos }}</td>
            <td><span [class]="promedioClass(r.promedio_general)">{{ r.promedio_general | number:'1.1-1' }}</span></td>
            <td>
              <div class="pct-bar-row">
                <div class="pct-bar-fill" [style.width]="r.pct_aprobados + '%'"
                     [style.background]="r.pct_aprobados >= 80 ? 'var(--green-400)' : 'var(--yellow-400)'"></div>
                <span>{{ r.pct_aprobados | number:'1.0-1' }}%</span>
              </div>
            </td>
            <td>
              <p-tag [value]="(r.pct_riesgo | number:'1.0-1') + '%'"
                     [severity]="r.pct_riesgo > 20 ? 'danger' : r.pct_riesgo > 10 ? 'warn' : 'success'" />
            </td>
          </tr>
        </ng-template>
      </p-table>
    }
  `,
  styles: [`
    .page-header    { display:flex; justify-content:space-between; align-items:flex-start; margin-bottom:1.25rem; }
    .page-title     { font-size:1.25rem; font-weight:700; margin:0 0 .2rem; }
    .page-subtitle  { color:var(--text-color-secondary); font-size:.82rem; margin:0; }
    .page-actions   { display:flex; gap:.5rem; align-items:center; }

    .filter-bar { display:flex; gap:.75rem; margin-bottom:1rem; flex-wrap:wrap; }
    :host ::ng-deep .filter-select { min-width:200px; }

    /* KPI cards */
    .kpi-grid { display:grid; grid-template-columns:repeat(4,1fr); gap:1rem; margin-bottom:1.25rem; }
    @media (max-width:900px) { .kpi-grid { grid-template-columns:repeat(2,1fr); } }
    .kpi-card {
      background: var(--surface-card); border-radius: 8px;
      padding: .9rem 1rem; display:flex; align-items:center; gap:.85rem;
      box-shadow: 0 1px 3px rgba(0,0,0,.08);
    }
    .kpi-icon { width:40px; height:40px; border-radius:8px; display:flex; align-items:center; justify-content:center; }
    .kpi-icon i { font-size:1.1rem; }
    .kpi-value { font-size:1.5rem; font-weight:700; line-height:1.1; }
    .kpi-label { font-size:.75rem; color:var(--text-color-secondary); margin-top:.15rem; }

    /* Tabs */
    .tabs { display:flex; gap:.25rem; margin-bottom:1rem; border-bottom:2px solid var(--surface-200); padding-bottom:.25rem; }
    .tab-btn {
      background:none; border:none; padding:.4rem .85rem; cursor:pointer; border-radius:4px 4px 0 0;
      font-size:.85rem; color:var(--text-color-secondary); display:flex; align-items:center; gap:.35rem;
      transition: background .12s, color .12s;
    }
    .tab-btn:hover { background:var(--surface-100); color:var(--text-color); }
    .tab-btn.active { color:var(--primary-color); font-weight:600; border-bottom:2px solid var(--primary-color); }

    /* % bar inline */
    .pct-bar-row { display:flex; align-items:center; gap:.4rem; }
    .pct-bar-fill { height:6px; border-radius:3px; min-width:3px; }

    /* Badges */
    .rep-badge {
      background:var(--red-400); color:#fff; border-radius:12px;
      font-size:.72rem; font-weight:700; padding:0 .45rem; height:18px;
      display:inline-flex; align-items:center;
    }

    /* Colores promedio */
    .prom-ok   { color:var(--green-600); font-weight:600; }
    .prom-warn { color:var(--yellow-700); font-weight:600; }
    .prom-bad  { color:var(--red-600); font-weight:600; }

    /* Progress bars */
    :host ::ng-deep .pbar-ok .p-progressbar-value   { background: var(--green-400); }
    :host ::ng-deep .pbar-danger .p-progressbar-value { background: var(--red-400); }

    /* Distribución chart */
    .dist-chart { max-width:600px; }
    .dist-title  { font-weight:600; margin-bottom:1rem; font-size:.95rem; }
    .dist-row    { display:grid; grid-template-columns:90px 1fr 90px; align-items:center; gap:.75rem; margin-bottom:.6rem; }
    .dist-label  { font-size:.83rem; text-align:right; color:var(--text-color-secondary); }
    .dist-bar-track { background:var(--surface-200); border-radius:4px; height:22px; overflow:hidden; }
    .dist-bar-fill  { height:100%; border-radius:4px; transition: width .5s ease; min-width:4px; }
    .dist-count  { font-size:.83rem; }
    .dist-pct    { color:var(--text-color-secondary); }

    .empty-msg { text-align:center; padding:2rem; color:var(--text-color-secondary); }
  `],
})
export class GradeAnalyticsComponent implements OnInit {
  private readonly api      = inject(ApiService);
  readonly ctx              = inject(ContextService);
  private readonly exporter = inject(ExportService);

  grupos         = signal<{ label: string; value: string }[]>([]);
  tendencias     = signal<TendenciaRow[]>([]);
  riesgo         = signal<RiesgoRow[]>([]);
  distribucion   = signal<DistribucionRow[]>([]);
  resumen        = signal<ResumenRow[]>([]);

  loadingTendencias = signal(false);
  loadingRiesgo     = signal(false);
  loadingResumen    = signal(false);

  selectedGrupoId = '';
  filtroRiesgo    = '';

  activeTab = 'riesgo';
  tabs = [
    { key: 'riesgo',      label: 'Alumnos en riesgo',   icon: 'pi-exclamation-triangle' },
    { key: 'tendencias',  label: 'Tendencias por materia', icon: 'pi-chart-line' },
    { key: 'distribucion', label: 'Distribución',        icon: 'pi-chart-bar' },
    { key: 'resumen',     label: 'Resumen ejecutivo',    icon: 'pi-building' },
  ];

  nivelesRiesgo = [
    { label: 'Todos',  value: '' },
    { label: 'Alto',   value: 'ALTO' },
    { label: 'Medio',  value: 'MEDIO' },
    { label: 'Bajo',   value: 'BAJO' },
  ];

  // KPI computeds
  totalAlumnos   = computed(() => this.riesgo().length);
  promedioGeneral = computed(() => {
    const list = this.riesgo();
    if (!list.length) return 0;
    return list.reduce((s, r) => s + Number(r.promedio_general), 0) / list.length;
  });
  pctAprobados = computed(() => {
    const list = this.riesgo();
    if (!list.length) return 0;
    return (list.filter(r => r.nivel_riesgo !== 'ALTO').length / list.length) * 100;
  });
  enRiesgoAlto = computed(() => this.riesgo().filter(r => r.nivel_riesgo === 'ALTO').length);

  selectedGrupoLabel = computed(() => {
    const g = this.grupos().find(g => g.value === this.selectedGrupoId);
    return g?.label ?? '';
  });

  private readonly exportCols: ExportColumn[] = [
    { field: 'nombre_alumno',      header: 'Alumno' },
    { field: 'nombre_grupo',       header: 'Grupo' },
    { field: 'promedio_general',   header: 'Promedio', format: (v: number) => Number(v).toFixed(1) },
    { field: 'pct_asistencia',     header: '% Asistencia', format: (v: number) => Number(v).toFixed(1) + '%' },
    { field: 'materias_reprobadas', header: 'Mat. Reprobadas' },
    { field: 'nivel_riesgo',       header: 'Nivel Riesgo' },
  ];

  ngOnInit(): void {
    this.cargarGrupos();
    this.cargarRiesgo();
    this.cargarResumen();
  }

  cargarTodo(): void {
    this.cargarRiesgo();
    this.cargarResumen();
    if (this.selectedGrupoId) {
      this.cargarTendencias();
      this.cargarDistribucion();
    }
  }

  cargarGrupos(): void {
    const plantel = this.ctx.plantel();
    const ciclo   = this.ctx.ciclo();
    const params: Record<string, string> = {};
    if (plantel) params['plantel_id'] = plantel.id;
    if (ciclo)   params['ciclo_id']   = ciclo.id;

    this.api.get<any[]>('/grupos', params).subscribe(list => {
      this.grupos.set(list.map(g => ({
        label: `${g.nombre_grupo} — ${g.nombre_grado ?? ''}`,
        value: g.id,
      })));
    });
  }

  onGrupoChange(): void {
    if (!this.selectedGrupoId) return;
    this.cargarTendencias();
    this.cargarDistribucion();
  }

  cargarTendencias(): void {
    if (!this.selectedGrupoId) return;
    this.loadingTendencias.set(true);
    this.api.get<TendenciaRow[]>(`/grade-analytics/tendencias/${this.selectedGrupoId}`).subscribe({
      next: (r) => { this.tendencias.set(r); this.loadingTendencias.set(false); },
      error: () => this.loadingTendencias.set(false),
    });
  }

  cargarRiesgo(): void {
    this.loadingRiesgo.set(true);
    const plantel = this.ctx.plantel();
    const params: Record<string, string> = {};
    if (plantel)          params['plantel_id']  = plantel.id;
    if (this.filtroRiesgo) params['nivel_riesgo'] = this.filtroRiesgo;
    if (this.selectedGrupoId) params['grupo_id'] = this.selectedGrupoId;

    this.api.get<RiesgoRow[]>('/grade-analytics/riesgo', { ...params, limit: 200 }).subscribe({
      next: (r) => { this.riesgo.set(r); this.loadingRiesgo.set(false); },
      error: () => this.loadingRiesgo.set(false),
    });
  }

  cargarDistribucion(): void {
    if (!this.selectedGrupoId) return;
    this.api.get<DistribucionRow[]>(`/grade-analytics/distribucion/${this.selectedGrupoId}`).subscribe({
      next: (r) => this.distribucion.set(r),
    });
  }

  cargarResumen(): void {
    this.loadingResumen.set(true);
    const plantel = this.ctx.plantel();
    const params = plantel ? { plantel_id: plantel.id } : {};
    this.api.get<ResumenRow[]>('/grade-analytics/resumen-plantel', params).subscribe({
      next: (r) => { this.resumen.set(r); this.loadingResumen.set(false); },
      error: () => this.loadingResumen.set(false),
    });
  }

  onFilterRiesgo(e: Event): void {
    const val = (e.target as HTMLInputElement).value.toLowerCase();
    if (!val) { this.cargarRiesgo(); return; }
    this.riesgo.update(list => list.filter(
      r => r.nombre_alumno.toLowerCase().includes(val) || r.nombre_grupo.toLowerCase().includes(val)
    ));
  }

  riesgoSev(nivel: string): TagSeverity { return RIESGO_SEV[nivel] ?? 'secondary'; }

  promedioClass(v: number): string {
    if (v >= 8) return 'prom-ok';
    if (v >= 6) return 'prom-warn';
    return 'prom-bad';
  }

  distColor(rango: string): string {
    if (rango.startsWith('<')) return 'var(--red-400)';
    if (rango.startsWith('6')) return 'var(--orange-400)';
    if (rango.startsWith('7')) return 'var(--yellow-400)';
    if (rango.startsWith('8')) return 'var(--teal-400)';
    return 'var(--green-400)';
  }

  exportRiesgoCSV():  void { this.exporter.toCSV(this.riesgo(),  this.exportCols, 'riesgo-academico'); }
  exportRiesgoXLSX(): void { this.exporter.toXLSX(this.riesgo(), this.exportCols, 'Riesgo Académico', 'riesgo-academico'); }
}
