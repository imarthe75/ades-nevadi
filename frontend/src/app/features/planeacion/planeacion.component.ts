import { Component, OnDestroy, inject, OnInit, signal, computed, effect, ChangeDetectionStrategy} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject, takeUntil } from 'rxjs';
import { ButtonModule } from 'primeng/button';
import { SelectModule } from 'primeng/select';
import { TagModule } from 'primeng/tag';
import { ProgressBarModule } from 'primeng/progressbar';
import { TableModule } from 'primeng/table';
import { TooltipModule } from 'primeng/tooltip';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { TextareaModule } from 'primeng/textarea';
import { DatePickerModule } from 'primeng/datepicker';

import { ApiService } from '../../core/services/api.service';
import { ContextService } from '../../core/services/context.service';
import { grupoLabel } from '../../core/models';
import { AdesFormatDirective } from '../../shared/directives/ades-format.directive';
import { ApexNotificationService } from 'apex-component-library';

type TagSeverity = 'success' | 'info' | 'warn' | 'danger' | 'secondary' | 'contrast' | undefined;

interface Tema {
  tema_id: string;
  nombre_tema: string;
  descripcion_tema: string | null;
  orden: number;
  periodo_sugerido: number | null;
  materia_id: string;
  nombre_materia: string;
  planeacion_id: string | null;
  fecha_planeada: string | null;
  descripcion_actividades: string | null;
  avance_id: string | null;
  fecha_ejecucion: string | null;
  es_completado: boolean | null;
  comentarios_profesor: string | null;
  estado: 'IMPARTIDO' | 'PLANEADO' | 'PENDIENTE';
}

interface Cobertura {
  materia_id: string;
  nombre_materia: string;
  total_temas: number;
  temas_impartidos: number;
  temas_planeados: number;
  pct_cobertura: number | null;
}

const ESTADO_SEV: Record<string, TagSeverity> = {
  IMPARTIDO: 'success',
  PLANEADO:  'info',
  PENDIENTE: 'secondary',
};

/**
 * Módulo de planeación didáctica docente.
 * Permite a los docentes crear y gestionar planes de clase alineados a los
 * temarios SEP/UAEMEX, con seguimiento de avance por grupo y materia.
 */
@Component({
  selector: 'app-planeacion',
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
  imports: [
    AdesFormatDirective,
    CommonModule, FormsModule,
    ButtonModule, SelectModule, TagModule, ProgressBarModule,
    TableModule, TooltipModule, DialogModule, InputTextModule, TextareaModule, DatePickerModule,
  ],
  template: `
    <div class="page-header">
      <div>
        <h2 class="page-title">Planeación de Clases</h2>
        <p class="page-subtitle">Avance del plan de estudios por grupo y materia</p>
      </div>
    </div>

    <!-- Filtros -->
    <div class="filter-bar">
      <p-select [options]="grupos()" [(ngModel)]="selGrupoId"
                optionLabel="label" optionValue="value"
                placeholder="Seleccionar grupo..."
                styleClass="filter-select"
                (onChange)="onGrupoChange()" 
 [filter]="true" filterPlaceholder="Buscar..." ariaLabel="Grupo" />
      <p-select [options]="materiasGrupo()" [(ngModel)]="selMateriaId"
                optionLabel="label" optionValue="value"
                placeholder="Todas las materias"
                [showClear]="true"
                styleClass="filter-select"
                (onChange)="cargarTemas()" 
 [filter]="true" filterPlaceholder="Buscar..." ariaLabel="Materias" />
      <p-button icon="pi pi-chart-bar" label="Ver cobertura" severity="secondary"
                [text]="true" (onClick)="vistaCobertura = !vistaCobertura" />
    </div>

    <!-- KPIs rápidos -->
    @if (selGrupoId && !vistaCobertura) {
      <div class="kpi-strip">
        <div class="kpi-chip kpi-green">
          <i class="pi pi-check-circle"></i>
          <span><strong>{{ totalImpartidos() }}</strong> impartidos</span>
        </div>
        <div class="kpi-chip kpi-blue">
          <i class="pi pi-calendar"></i>
          <span><strong>{{ totalPlaneados() }}</strong> planeados</span>
        </div>
        <div class="kpi-chip kpi-gray">
          <i class="pi pi-clock"></i>
          <span><strong>{{ totalPendientes() }}</strong> pendientes</span>
        </div>
        <div class="kpi-chip kpi-main">
          <i class="pi pi-percentage"></i>
          <span><strong>{{ pctCobertura() | number:'1.0-1' }}%</strong> cobertura</span>
        </div>
      </div>
    }

    <!-- Vista cobertura por materia -->
    @if (vistaCobertura && selGrupoId) {
      <div class="cobertura-grid">
        @for (c of cobertura(); track c.materia_id) {
          <div class="cob-card">
            <div class="cob-materia">{{ c.nombre_materia }}</div>
            <p-progressBar [value]="c.pct_cobertura ?? 0"
                           [style]="{'height':'8px'}"
                           [styleClass]="cobBarClass(c.pct_cobertura ?? 0)" />
            <div class="cob-stats">
              <span class="cob-stat green">{{ c.temas_impartidos }}/{{ c.total_temas }} impartidos</span>
              <span class="cob-pct">{{ c.pct_cobertura ?? 0 | number:'1.0-1' }}%</span>
            </div>
          </div>
        }
      </div>
    }

    <!-- Grid de temas por materia -->
    @if (!vistaCobertura && selGrupoId) {
      @for (mat of materiasConTemas(); track mat.materia_id) {
        <div class="materia-section">
          <div class="materia-header">
            <h3>{{ mat.nombre_materia }}</h3>
            <div class="materia-stats">
              <span class="stat-chip green">{{ mat.impartidos }} impartidos</span>
              <span class="stat-chip blue">{{ mat.planeados }} planeados</span>
              <span class="stat-chip gray">{{ mat.pendientes }} pendientes</span>
            </div>
            <p-progressBar [value]="mat.pct" [style]="{'height':'5px'}" />
          </div>
          <div class="temas-grid">
            @for (t of mat.temas; track t.tema_id) {
              <div class="tema-card" [class]="'tema-' + t.estado.toLowerCase()">
                <div class="tema-top">
                  <span class="tema-orden">#{{ t.orden }}</span>
                  <p-tag [value]="t.estado" [severity]="estadoSev(t.estado)" />
                </div>
                <div class="tema-nombre">{{ t.nombre_tema }}</div>
                @if (t.fecha_planeada) {
                  <div class="tema-fecha"><i class="pi pi-calendar"></i> {{ t.fecha_planeada | date:'dd/MM' }}</div>
                }
                @if (t.fecha_ejecucion) {
                  <div class="tema-fecha" style="color:var(--green-600)">
                    <i class="pi pi-check"></i> {{ t.fecha_ejecucion | date:'dd/MM' }}
                  </div>
                }
                <div class="tema-actions">
                  @if (t.estado === 'PENDIENTE') {
                    <p-button icon="pi pi-calendar-plus" size="small" [text]="true"
                              ariaLabel="Planear tema" pTooltip="Planear tema" (onClick)="abrirPlanear(t)" />
                  }
                  @if (t.estado === 'PLANEADO') {
                    <p-button icon="pi pi-check-circle" size="small" severity="success" [text]="true"
                              [loading]="marcandoImpartidoId() === t.tema_id"
                              ariaLabel="Marcar como impartido" pTooltip="Marcar impartido" (onClick)="marcarImpartido(t)" />
                  }
                  @if (t.estado === 'IMPARTIDO') {
                    <i class="pi pi-check-circle" style="color:var(--green-500); font-size:.85rem"></i>
                  }
                </div>
              </div>
            }
          </div>
        </div>
      }
    }

    @if (!selGrupoId) {
      <div class="empty-msg" style="padding:3rem; text-align:center;">
        <i class="pi pi-book" style="font-size:2.5rem; color:var(--primary-color); display:block; margin-bottom:.75rem"></i>
        Selecciona un grupo para ver su plan de estudios.
      </div>
    }

    <!-- Dialog planear tema -->
    <p-dialog header="Planear tema" [(visible)]="showPlanear" [modal]="true"
              [style]="{width:'460px'}">
      @if (temaPlanear) {
        <p style="font-size:.9rem; margin-bottom:1rem">
          <strong>{{ temaPlanear.nombre_materia }}</strong> &mdash; {{ temaPlanear.nombre_tema }}
        </p>
        <div class="form-grid">
          <div class="form-field full">
            <label>Fecha planeada *</label>
            <p-datepicker [(ngModel)]="planForm.fecha_planeada" dateFormat="dd/mm/yy" [showIcon]="true"
                          placeholder="DD/MM/AAAA" [style]="{width:'100%'}" [inputStyle]="{width:'100%'}" ariaLabel="Fecha planeada"/>
          </div>
          <div class="form-field full">
            <label for="plan-desc-act">Descripción de actividades</label>
            <textarea pTextarea id="plan-desc-act" [(ngModel)]="planForm.descripcion_actividades"
                      rows="3" style="width:100%; resize:vertical"></textarea>
          </div>
          <div class="form-field full">
            <label for="plan-recursos">Recursos didácticos</label>
            <input pInputText id="plan-recursos" [(ngModel)]="planForm.recursos_didacticos" style="width:100%"
                   placeholder="Libro, proyector, guía…"/>
          </div>
        </div>
      }
      <ng-template pTemplate="footer">
        <p-button label="Cancelar" severity="secondary" [text]="true" (onClick)="showPlanear = false" />
        <p-button label="Planear" icon="pi pi-calendar-plus" [loading]="savingPlanear()" (onClick)="confirmarPlanear()" />
      </ng-template>
    </p-dialog>
  `,
  styles: [`
    .page-header    { display:flex; justify-content:space-between; align-items:flex-start; margin-bottom:1.25rem; }
    .page-title     { font-size:1.25rem; font-weight:700; margin:0 0 .2rem; }
    .page-subtitle  { color:var(--text-color-secondary); font-size:.82rem; margin:0; }

    .filter-bar { display:flex; gap:.75rem; margin-bottom:1rem; flex-wrap:wrap; }
    :host ::ng-deep .filter-select { min-width:200px; }

    /* KPIs strip */
    .kpi-strip { display:flex; gap:.75rem; margin-bottom:1.25rem; flex-wrap:wrap; }
    .kpi-chip {
      display:flex; align-items:center; gap:.4rem; padding:.35rem .85rem;
      border-radius:20px; font-size:.82rem;
    }
    .kpi-green { background:var(--green-50); color:var(--green-700); }
    .kpi-blue  { background:var(--blue-50);  color:var(--blue-700); }
    .kpi-gray  { background:var(--surface-200); color:var(--text-color-secondary); }
    .kpi-main  { background:var(--primary-50, #fff0f1); color:var(--primary-color); font-weight:600; }

    /* Cobertura cards */
    .cobertura-grid { display:grid; grid-template-columns:repeat(auto-fill, minmax(220px,1fr)); gap:.75rem; margin-bottom:1.5rem; }
    .cob-card { background:var(--surface-card); border-radius:8px; padding:.85rem; box-shadow:0 1px 3px rgba(0,0,0,.07); }
    .cob-materia { font-weight:600; font-size:.85rem; margin-bottom:.5rem; }
    .cob-stats { display:flex; justify-content:space-between; margin-top:.35rem; font-size:.75rem; }
    .cob-stat.green { color:var(--green-600); }
    .cob-pct { font-weight:700; color:var(--primary-color); }

    :host ::ng-deep .pbar-ok .p-progressbar-value    { background: var(--green-400); }
    :host ::ng-deep .pbar-mid .p-progressbar-value   { background: var(--yellow-400); }
    :host ::ng-deep .pbar-low .p-progressbar-value   { background: var(--red-400); }

    /* Materia sections */
    .materia-section { margin-bottom:1.75rem; }
    .materia-header {
      background:var(--surface-100); border-radius:8px 8px 0 0;
      padding:.6rem 1rem; border-left:4px solid var(--primary-color);
    }
    .materia-header h4 { font-size:.95rem; font-weight:700; margin:0 0 .4rem; color:var(--primary-color); }
    .materia-stats { display:flex; gap:.5rem; margin-bottom:.4rem; }
    .stat-chip { font-size:.72rem; font-weight:600; padding:.1rem .5rem; border-radius:10px; }
    .stat-chip.green { background:var(--green-100); color:var(--green-700); }
    .stat-chip.blue  { background:var(--blue-100);  color:var(--blue-700); }
    .stat-chip.gray  { background:var(--surface-200); color:var(--text-color-secondary); }

    /* Temas grid */
    .temas-grid {
      display:grid; grid-template-columns:repeat(auto-fill, minmax(160px,1fr));
      gap:.5rem; padding:.75rem; background:var(--surface-50);
      border-radius:0 0 8px 8px; border:1px solid var(--surface-200); border-top:none;
    }
    .tema-card {
      background:#fff; border-radius:6px; padding:.6rem .75rem;
      border:1px solid var(--surface-200);
      display:flex; flex-direction:column; gap:.3rem; font-size:.8rem;
    }
    .tema-impartido { border-color:var(--green-300); background:var(--green-50); }
    .tema-planeado  { border-color:var(--blue-300);  background:var(--blue-50); }

    .tema-top { display:flex; justify-content:space-between; align-items:center; }
    .tema-orden { font-size:.7rem; color:var(--text-color-secondary); font-weight:600; }
    .tema-nombre { font-weight:600; font-size:.82rem; line-height:1.3; }
    .tema-fecha  { font-size:.72rem; color:var(--text-color-secondary); display:flex; align-items:center; gap:.2rem; }
    .tema-actions { margin-top:.2rem; }

    .form-grid  { display:grid; grid-template-columns:1fr; gap:.75rem; }
    .form-field { display:flex; flex-direction:column; gap:.3rem; }
    .form-field.full { grid-column:1/-1; }
    label       { font-weight:500; font-size:.82rem; }
    .empty-msg  { color:var(--text-color-secondary); }
  `],
})
export class PlaneacionComponent implements OnInit, OnDestroy {
  private destroy$ = new Subject<void>();
  private readonly api = inject(ApiService);
  readonly ctx         = inject(ContextService);
  private readonly notify = inject(ApexNotificationService);

  grupos        = signal<{ label: string; value: string }[]>([]);
  temas         = signal<Tema[]>([]);
  cobertura     = signal<Cobertura[]>([]);
  materiasGrupo = signal<{ label: string; value: string; materia_id: string }[]>([]);

  selGrupoId   = '';
  selMateriaId = '';
  vistaCobertura = false;

  loading = signal(false);
  showPlanear = false;
  temaPlanear: Tema | null = null;
  savingPlanear = signal(false);
  marcandoImpartidoId = signal<string | null>(null);
  planForm: { fecha_planeada: Date | null; descripcion_actividades: string; recursos_didacticos: string } =
    { fecha_planeada: null, descripcion_actividades: '', recursos_didacticos: '' };

  // KPI computeds
  totalImpartidos = computed(() => this.temas().filter(t => t.estado === 'IMPARTIDO').length);
  totalPlaneados  = computed(() => this.temas().filter(t => t.estado === 'PLANEADO').length);
  totalPendientes = computed(() => this.temas().filter(t => t.estado === 'PENDIENTE').length);
  pctCobertura    = computed(() => {
    const total = this.temas().length;
    if (!total) return 0;
    return (this.totalImpartidos() / total) * 100;
  });

  materiasConTemas = computed(() => {
    const mapa = new Map<string, { materia_id: string; nombre_materia: string; temas: Tema[]; impartidos: number; planeados: number; pendientes: number; pct: number }>();
    for (const t of this.temas()) {
      if (!mapa.has(t.materia_id)) {
        mapa.set(t.materia_id, { materia_id: t.materia_id, nombre_materia: t.nombre_materia, temas: [], impartidos: 0, planeados: 0, pendientes: 0, pct: 0 });
      }
      const m = mapa.get(t.materia_id)!;
      m.temas.push(t);
      if (t.estado === 'IMPARTIDO') m.impartidos++;
      else if (t.estado === 'PLANEADO') m.planeados++;
      else m.pendientes++;
    }
    for (const m of mapa.values()) {
      m.pct = m.temas.length ? Math.round(m.impartidos / m.temas.length * 100) : 0;
    }
    return [...mapa.values()].sort((a, b) => a.nombre_materia.localeCompare(b.nombre_materia));
  });

  constructor() {
    // Recargar lista de grupos cuando cambia el plantel o ciclo en el contexto global.
    effect(() => {
      const plantel = this.ctx.plantel();
      const ciclo   = this.ctx.ciclo();
      const params: Record<string, string> = {};
      if (plantel) params['plantel_id'] = plantel.id;
      if (ciclo)   params['ciclo_id']   = ciclo.id;
      this.api.get<any[]>('/grupos', params).pipe(takeUntil(this.destroy$)).subscribe(list => {
        this.grupos.set(list.map(g => ({ label: grupoLabel(g) || `${g.nombre_grupo} — ${g.nombre_grado ?? ''}`, value: g.id })));
      });
    });
  }

  ngOnInit(): void {
    // Grupos cargados reactivamente desde el effect en el constructor.
  }

  onGrupoChange(): void {
    this.selMateriaId = '';
    if (!this.selGrupoId) { this.temas.set([]); return; }
    this.cargarTemas();
    this.cargarCobertura();
    // Extraer materias únicas de los temas para el filtro
    this.api.get<Tema[]>('/planeacion/temas', { grupo_id: this.selGrupoId }).pipe(takeUntil(this.destroy$)).subscribe(list => {
      const matMap = new Map<string, string>();
      list.forEach(t => matMap.set(t.materia_id, t.nombre_materia));
      this.materiasGrupo.set([...matMap.entries()].map(([value, label]) => ({ label, value, materia_id: value })));
    });
  }

  cargarTemas(): void {
    if (!this.selGrupoId) return;
    this.loading.set(true);
    const params: Record<string, string> = { grupo_id: this.selGrupoId };
    if (this.selMateriaId) params['materia_id'] = this.selMateriaId;
    this.api.get<Tema[]>('/planeacion/temas', params).pipe(takeUntil(this.destroy$)).subscribe({
      next: (r) => { this.temas.set(r); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }

  cargarCobertura(): void {
    if (!this.selGrupoId) return;
    this.api.get<Cobertura[]>(`/planeacion/cobertura/${this.selGrupoId}`).pipe(takeUntil(this.destroy$)).subscribe(r => this.cobertura.set(r));
  }

  abrirPlanear(t: Tema): void {
    this.temaPlanear = t;
    this.planForm = { fecha_planeada: null, descripcion_actividades: '', recursos_didacticos: '' };
    this.showPlanear = true;
  }

  confirmarPlanear(): void {
    if (!this.temaPlanear || !this.planForm.fecha_planeada) return;
    const body = {
      grupo_id: this.selGrupoId,
      tema_id:  this.temaPlanear.tema_id,
      ...this.planForm,
      fecha_planeada: this.planForm.fecha_planeada!.toISOString().substring(0, 10),
    };
    this.savingPlanear.set(true);
    this.api.post('/planeacion/clases', body).pipe(takeUntil(this.destroy$)).subscribe({
      next: () => {
        this.savingPlanear.set(false);
        this.showPlanear = false;
        this.cargarTemas();
      },
      error: e => { this.savingPlanear.set(false); this.notify.error('Error', e?.error?.detail ?? 'No se pudo guardar la planeación'); },
    });
  }

  marcarImpartido(t: Tema): void {
    if (!t.planeacion_id) return;
    const body = { fecha_ejecucion: new Date().toISOString().split('T')[0] };
    this.marcandoImpartidoId.set(t.tema_id);
    this.api.post(`/planeacion/clases/${t.planeacion_id}/completar`, body).pipe(takeUntil(this.destroy$)).subscribe({
      next: () => {
        this.marcandoImpartidoId.set(null);
        this.cargarTemas();
        this.cargarCobertura();
      },
      error: e => { this.marcandoImpartidoId.set(null); this.notify.error('Error', e?.error?.detail ?? 'No se pudo marcar la clase como impartida'); },
    });
  }

  estadoSev(estado: string): TagSeverity { return ESTADO_SEV[estado] ?? 'secondary'; }

  cobBarClass(pct: number): string {
    if (pct >= 80) return 'pbar-ok';
    if (pct >= 50) return 'pbar-mid';
    return 'pbar-low';
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
