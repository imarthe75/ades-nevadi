import { Component, inject, OnInit, OnDestroy, ChangeDetectionStrategy, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { TabsModule } from 'primeng/tabs';
import { AutoCompleteModule } from 'primeng/autocomplete';
import { ProgressBarModule } from 'primeng/progressbar';
import { TooltipModule } from 'primeng/tooltip';
import { SelectModule } from 'primeng/select';
import { InteractiveGridComponent, ColumnConfig } from '../../shared/components/interactive-grid/interactive-grid.component';

import { ApiService } from '../../core/services/api.service';
import { ContextService } from '../../core/services/context.service';
import { ExportService, ExportColumn } from '../../core/services/export.service';

type TagSeverity = 'success' | 'info' | 'warn' | 'danger' | 'secondary' | 'contrast' | undefined;

interface AlumnoResumen {
  alumno: {
    id: string; nombre: string; matricula: string;
    grupo: string; nivel: string; plantel: string; ciclo: string;
    foto_url: string | null; fecha_nacimiento: string | null; genero: string | null;
  };
  kpis: {
    promedio_general: number; pct_asistencia: number;
    tareas_pendientes: number; badges_count: number;
  };
  alertas: { tipo_alerta: string; nivel_riesgo: string; descripcion: string }[];
  badges:  { nombre: string; icono: string; color: string; tipo: string; fecha_otorgado: string; motivo: string | null }[];
  learning_paths: { nombre: string; pct_completado: number; estatus: string }[];
}

/**
 * Portal institucional de comunicación para alumnos y comunidad escolar.
 * Muestra avisos, noticias y recursos publicados por la administración del plantel.
 * Accesible para todos los roles autenticados.
 */
@Component({
  selector: 'app-portal',
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    ButtonModule, TagModule, TabsModule,
    AutoCompleteModule, ProgressBarModule, TooltipModule, SelectModule,
    InteractiveGridComponent,
  ],
  template: `
<div class="page-header">
  <div class="page-title">
    <i class="pi pi-id-card" style="color:var(--nevadi-red)"></i>
    <h1>Portal del Alumno</h1>
  </div>
</div>

<!-- ── Buscador ─────────────────────────────────────────────────────────── -->
<div class="search-bar">
  <p-autoComplete
    [(ngModel)]="alumnoQuery"
    [suggestions]="sugerencias()"
    field="nombreCompleto"
    placeholder="Buscar alumno por nombre o matrícula..."
    (completeMethod)="buscar($event)"
    (onSelect)="seleccionar($event)"
    [style]="{width:'100%', maxWidth:'500px'}"
  />
  @if (ciclos().length) {
    <p-select [options]="ciclos()" optionLabel="nombre" optionValue="id"
              [(ngModel)]="cicloSeleccionado" placeholder="Ciclo escolar"
              (onChange)="recargar()" 
 [filter]="true" filterPlaceholder="Buscar..." ariaLabel="Ciclo escolar" />
  }
</div>

@if (!resumen()) {
  <div class="empty-portal">
    <i class="pi pi-user" style="font-size:3rem;color:#ddd"></i>
    <p>Selecciona un alumno para ver su expediente</p>
  </div>
}

@if (resumen()) {
  <!-- ── Tarjeta del alumno ─────────────────────────────────────────────── -->
  <div class="alumno-card">
    <div class="avatar">
      @if (resumen()!.alumno.foto_url) {
        <img [src]="resumen()!.alumno.foto_url" alt="foto" loading="lazy" />
      } @else {
        <span>{{ iniciales() }}</span>
      }
    </div>
    <div class="alumno-datos">
      <h2>{{ resumen()!.alumno.nombre }}</h2>
      <div class="alumno-meta">
        <span><i class="pi pi-hashtag"></i> {{ resumen()!.alumno.matricula }}</span>
        <span><i class="pi pi-users"></i> {{ resumen()!.alumno.grupo }}</span>
        <span><i class="pi pi-book"></i> {{ resumen()!.alumno.nivel }}</span>
        <span><i class="pi pi-building"></i> {{ resumen()!.alumno.plantel }}</span>
        <span><i class="pi pi-calendar"></i> {{ resumen()!.alumno.ciclo }}</span>
      </div>
    </div>
    <div class="kpi-strip">
      <div class="kpi" [class.kpi-ok]="resumen()!.kpis.promedio_general >= 8"
                       [class.kpi-warn]="resumen()!.kpis.promedio_general >= 6 && resumen()!.kpis.promedio_general < 8"
                       [class.kpi-danger]="resumen()!.kpis.promedio_general < 6">
        <span class="kpi-val">{{ resumen()!.kpis.promedio_general | number:'1.1-1' }}</span>
        <span class="kpi-label">Promedio</span>
      </div>
      <div class="kpi" [class.kpi-ok]="resumen()!.kpis.pct_asistencia >= 85"
                       [class.kpi-warn]="resumen()!.kpis.pct_asistencia >= 70 && resumen()!.kpis.pct_asistencia < 85"
                       [class.kpi-danger]="resumen()!.kpis.pct_asistencia < 70">
        <span class="kpi-val">{{ resumen()!.kpis.pct_asistencia | number:'1.0-1' }}%</span>
        <span class="kpi-label">Asistencia</span>
      </div>
      <div class="kpi" [class.kpi-danger]="resumen()!.kpis.tareas_pendientes > 3"
                       [class.kpi-warn]="resumen()!.kpis.tareas_pendientes > 0">
        <span class="kpi-val">{{ resumen()!.kpis.tareas_pendientes }}</span>
        <span class="kpi-label">Tareas pend.</span>
      </div>
      <div class="kpi kpi-gold">
        <span class="kpi-val">{{ resumen()!.kpis.badges_count }}</span>
        <span class="kpi-label">Insignias</span>
      </div>
    </div>
  </div>

  <!-- ── Alertas ──────────────────────────────────────────────────────────── -->
  @if (resumen()!.alertas.length) {
    <div class="alertas-banner">
      @for (a of resumen()!.alertas; track a.tipo_alerta) {
        <div class="alerta-item" [class.alerta-alto]="a.nivel_riesgo === 'ALTO'"
             [class.alerta-medio]="a.nivel_riesgo === 'MEDIO'">
          <i class="pi pi-exclamation-triangle"></i>
          <span>{{ a.descripcion }}</span>
          <p-tag [value]="a.nivel_riesgo" [severity]="riesgoSeverity(a.nivel_riesgo)" />
        </div>
      }
    </div>
  }

  <!-- ── Tabs ──────────────────────────────────────────────────────────────── -->
  <p-tabs [(value)]="tabActivo">

    <!-- TAB CALIFICACIONES ──────────────────────────────────────────────────── -->
    <p-tabpanel value="calificaciones" header="Calificaciones">
      @if (cargandoCal()) {
        <div class="loading"><i class="pi pi-spin pi-spinner"></i> Cargando...</div>
      } @else {
        <div class="cal-header-actions">
          <p-button label="Exportar Excel" icon="pi pi-file-excel" severity="secondary"
                    size="small" (onClick)="exportCalXLSX()" />
        </div>
        <app-interactive-grid
          [data]="calificacionesFlat()"
          [columns]="calificacionesColumns()"
        />
      }
    </p-tabpanel>

    <!-- TAB ASISTENCIAS ─────────────────────────────────────────────────────── -->
    <p-tabpanel value="asistencias" header="Asistencias">
      @if (cargandoAsist()) {
        <div class="loading"><i class="pi pi-spin pi-spinner"></i> Cargando...</div>
      } @else {
        <div class="asist-resumen">
          <div class="asist-kpi">
            <span class="ak-val" style="color:#27AE60">{{ asistencias()?.resumen?.presentes ?? 0 }}</span>
            <span class="ak-label">Presentes</span>
          </div>
          <div class="asist-kpi">
            <span class="ak-val" style="color:#E74C3C">{{ asistencias()?.resumen?.ausentes ?? 0 }}</span>
            <span class="ak-label">Ausentes</span>
          </div>
          <div class="asist-kpi">
            <span class="ak-val" style="color:#F39C12">{{ asistencias()?.resumen?.tardes ?? 0 }}</span>
            <span class="ak-label">Tardes</span>
          </div>
          <div class="asist-kpi">
            <span class="ak-val" style="color:#3498DB">{{ asistencias()?.resumen?.pct_asistencia ?? 0 }}%</span>
            <span class="ak-label">% Asistencia</span>
          </div>
          <div style="flex:1; min-width:200px">
            <p-progressBar [value]="asistencias()?.resumen?.pct_asistencia ?? 0"
                           [showValue]="false"
                           [style]="{'height':'8px'}"
                           [styleClass]="(asistencias()?.resumen?.pct_asistencia ?? 0) >= 85 ? 'pbar-ok' : 'pbar-low'" />
          </div>
        </div>
        <app-interactive-grid
          [data]="asistenciasFlat()"
          [columns]="asistenciasColumns"
        />
      }
    </p-tabpanel>

    <!-- TAB TAREAS ──────────────────────────────────────────────────────────── -->
    <p-tabpanel value="tareas" header="Tareas">
      @if (cargandoTareas()) {
        <div class="loading"><i class="pi pi-spin pi-spinner"></i> Cargando...</div>
      } @else {
        <div class="tareas-filter">
          <p-button
            [label]="soloPendientes() ? 'Mostrando: Pendientes' : 'Mostrando: Todas'"
            [icon]="soloPendientes() ? 'pi pi-filter-slash' : 'pi pi-filter'"
            [severity]="soloPendientes() ? 'warn' : 'secondary'"
            size="small"
            (onClick)="togglePendientes()" />
        </div>
        <app-interactive-grid
          [data]="tareasFlat()"
          [columns]="tareasColumns"
        />
      }
    </p-tabpanel>

    <!-- TAB PERFIL ──────────────────────────────────────────────────────────── -->
    <p-tabpanel value="perfil" header="Perfil & Logros">
      <div class="perfil-layout">

        <!-- Insignias -->
        <div class="perfil-section">
          <h3><i class="pi pi-star-fill" style="color:#F39C12"></i> Insignias obtenidas</h3>
          @if (resumen()!.badges.length) {
            <div class="badges-mini-grid">
              @for (b of resumen()!.badges; track b.nombre) {
                <div class="badge-mini" [style.border-color]="b.color" pTooltip="{{ b.nombre }}">
                  <i [class]="'pi ' + b.icono" [style.color]="b.color" style="font-size:1.5rem"></i>
                  <span class="badge-mini-nombre">{{ b.nombre }}</span>
                  <span class="badge-mini-fecha">{{ b.fecha_otorgado | date:'dd/MM/yy' }}</span>
                </div>
              }
            </div>
          } @else {
            <p style="color:#bbb;font-style:italic">Sin insignias en este ciclo</p>
          }
        </div>

        <!-- Learning Paths -->
        <div class="perfil-section">
          <h3><i class="pi pi-sitemap" style="color:#3498DB"></i> Rutas de aprendizaje</h3>
          @if (resumen()!.learning_paths.length) {
            <div class="lp-list">
              @for (lp of resumen()!.learning_paths; track lp.nombre) {
                <div class="lp-item">
                  <div class="lp-header">
                    <span class="lp-nombre">{{ lp.nombre }}</span>
                    <p-tag [value]="lp.estatus" [severity]="lpSeverity(lp.estatus)" />
                  </div>
                  <p-progressBar [value]="lp.pct_completado" [showValue]="false"
                                 [style]="{'height':'6px'}" />
                  <span class="lp-pct">{{ lp.pct_completado }}% completado</span>
                </div>
              }
            </div>
          } @else {
            <p style="color:#bbb;font-style:italic">Sin rutas asignadas</p>
          }
        </div>

        <!-- Datos personales -->
        <div class="perfil-section">
          <h3><i class="pi pi-user"></i> Datos personales</h3>
          <div class="datos-grid">
            <div class="dato-row">
              <span class="dato-label">Fecha de nacimiento</span>
              <span>{{ resumen()!.alumno.fecha_nacimiento | date:'dd/MM/yyyy' }}</span>
            </div>
            <div class="dato-row">
              <span class="dato-label">Género</span>
              <span>{{ resumen()!.alumno.genero ?? '—' }}</span>
            </div>
            <div class="dato-row">
              <span class="dato-label">Matrícula</span>
              <span style="font-family:monospace">{{ resumen()!.alumno.matricula }}</span>
            </div>
            <div class="dato-row">
              <span class="dato-label">Grupo</span>
              <span>{{ resumen()!.alumno.grupo }}</span>
            </div>
          </div>
        </div>

      </div>
    </p-tabpanel>

  </p-tabs>
}
  `,
  styles: [`
    .page-header  { display:flex; align-items:center; justify-content:space-between;
                    margin-bottom:1.25rem; flex-wrap:wrap; gap:.5rem; }
    .page-title   { display:flex; align-items:center; gap:.5rem; }
    .page-title h1 { font-size:1.35rem; font-family:'Jost',sans-serif; margin:0; font-weight:600; }

    .search-bar { display:flex; align-items:center; gap:.75rem; margin-bottom:1.5rem; flex-wrap:wrap; }

    .empty-portal { text-align:center; padding:4rem; color:#bbb; }
    .empty-portal p { margin:.5rem 0 0; font-size:1rem; }

    /* ── Tarjeta alumno ── */
    .alumno-card { display:flex; align-items:flex-start; gap:1.25rem; padding:1.25rem;
                   background:var(--surface-card); border:1px solid var(--surface-border);
                   border-radius:10px; margin-bottom:1rem; flex-wrap:wrap; }
    .avatar { width:72px; height:72px; border-radius:50%; background:var(--nevadi-red);
               display:flex; align-items:center; justify-content:center;
               overflow:hidden; flex-shrink:0; }
    .avatar span { color:#fff; font-size:1.5rem; font-family:'Jost',sans-serif; font-weight:700; }
    .avatar img { width:100%; height:100%; object-fit:cover; }
    .alumno-datos { flex:1; min-width:0; }
    .alumno-datos h2 { margin:0 0 .4rem; font-size:1.2rem; font-family:'Jost',sans-serif; }
    .alumno-meta { display:flex; flex-wrap:wrap; gap:.5rem; font-size:.82rem; color:var(--text-secondary); }
    .alumno-meta span { display:flex; align-items:center; gap:.25rem; }

    /* ── KPI strip ── */
    .kpi-strip { display:flex; gap:.75rem; margin-left:auto; flex-wrap:wrap; }
    .kpi { display:flex; flex-direction:column; align-items:center; padding:.5rem .75rem;
           border-radius:8px; background:var(--surface-ground); min-width:72px; }
    .kpi-val   { font-size:1.4rem; font-family:'Jost',sans-serif; font-weight:700; }
    .kpi-label { font-size:.7rem; color:var(--text-secondary); margin-top:.1rem; }
    .kpi-ok     .kpi-val { color:#27AE60; }
    .kpi-warn   .kpi-val { color:#F39C12; }
    .kpi-danger .kpi-val { color:#E74C3C; }
    .kpi-gold   .kpi-val { color:#F39C12; }

    /* ── Alertas ── */
    .alertas-banner { margin-bottom:1rem; display:flex; flex-direction:column; gap:.4rem; }
    .alerta-item { display:flex; align-items:center; gap:.6rem; padding:.6rem 1rem;
                   border-radius:6px; font-size:.85rem; }
    .alerta-alto  { background:#FFF0F0; border-left:4px solid #E74C3C; }
    .alerta-medio { background:#FFFBEA; border-left:4px solid #F39C12; }

    /* ── Calificaciones ── */
    .cal-header-actions { margin-bottom:.75rem; }
    .cal-chip { display:inline-block; padding:.2rem .5rem; border-radius:4px;
                font-size:.82rem; font-weight:600; }
    .cal-excelente { background:#E8F5E9; color:#27AE60; }
    .cal-bien      { background:#E3F2FD; color:#1565C0; }
    .cal-regular   { background:#FFFDE7; color:#F57F17; }
    .cal-reprobado { background:#FFEBEE; color:#C62828; }

    /* ── Asistencias ── */
    .asist-resumen { display:flex; align-items:center; gap:1.5rem; margin-bottom:1rem;
                     flex-wrap:wrap; padding:.75rem; background:var(--surface-ground);
                     border-radius:8px; }
    .asist-kpi    { display:flex; flex-direction:column; align-items:center; }
    .ak-val       { font-size:1.3rem; font-family:'Jost',sans-serif; font-weight:700; }
    .ak-label     { font-size:.72rem; color:var(--text-secondary); }

    /* ── Tareas ── */
    .tareas-filter { margin-bottom:.75rem; }
    .row-pending   { background:#FFF8F8 !important; }

    /* ── Perfil ── */
    .perfil-layout   { display:grid; grid-template-columns:repeat(auto-fill,minmax(280px,1fr));
                       gap:1.25rem; }
    .perfil-section  { background:var(--surface-card); border:1px solid var(--surface-border);
                       border-radius:8px; padding:1rem; }
    .perfil-section h3 { margin:0 0 .75rem; font-size:.95rem; font-family:'Jost',sans-serif;
                         display:flex; align-items:center; gap:.4rem; }

    .badges-mini-grid { display:flex; flex-wrap:wrap; gap:.6rem; }
    .badge-mini { display:flex; flex-direction:column; align-items:center;
                  padding:.5rem .6rem; border:2px solid; border-radius:8px;
                  width:80px; text-align:center; }
    .badge-mini-nombre { font-size:.65rem; font-weight:600; margin-top:.2rem; line-height:1.2; }
    .badge-mini-fecha  { font-size:.6rem; color:var(--text-secondary); }

    .lp-list { display:flex; flex-direction:column; gap:.75rem; }
    .lp-item { display:flex; flex-direction:column; gap:.3rem; }
    .lp-header { display:flex; align-items:center; justify-content:space-between; gap:.5rem; }
    .lp-nombre { font-size:.85rem; font-weight:500; }
    .lp-pct    { font-size:.72rem; color:var(--text-secondary); }

    .datos-grid { display:flex; flex-direction:column; gap:.4rem; }
    .dato-row   { display:flex; justify-content:space-between; font-size:.85rem;
                  padding:.3rem 0; border-bottom:1px solid var(--surface-border); }
    .dato-label { color:var(--text-secondary); }

    .loading { padding:2rem; text-align:center; color:var(--text-secondary); }

    :host ::ng-deep .pbar-ok .p-progressbar-value  { background:#27AE60 !important; }
    :host ::ng-deep .pbar-low .p-progressbar-value { background:#E74C3C !important; }
  `],
})
export class PortalComponent implements OnInit, OnDestroy {
  private api     = inject(ApiService);
  private ctx     = inject(ContextService);
  private exporter = inject(ExportService);

  resumen          = signal<AlumnoResumen | null>(null);
  calificaciones   = signal<any[]>([]);
  asistencias      = signal<any | null>(null);
  tareas           = signal<any[]>([]);
  ciclos           = signal<any[]>([]);
  sugerencias      = signal<any[]>([]);

  cargandoCal    = signal(false);
  cargandoAsist  = signal(false);
  cargandoTareas = signal(false);
  soloPendientes = signal(false);

  tabActivo       = 'calificaciones';
  alumnoQuery     = '';
  cicloSeleccionado: string | null = null;
  alumnoId: string | null = null;

  iniciales = computed(() => {
    const n = this.resumen()?.alumno.nombre ?? '';
    return n.split(' ').slice(0, 2).map(p => p[0]).join('').toUpperCase();
  });

  periodosHeader = computed(() => {
    const all = this.calificaciones().flatMap((r: any) =>
      r.periodos.map((p: any) => p.periodo as string)
    );
    return [...new Set(all)];
  });

  readonly calificacionesFlat = computed(() => {
    const periodos = this.periodosHeader();
    return this.calificaciones().map((row: any) => {
      const flat: Record<string, any> = { materia: row.materia };
      for (const p of periodos) {
        const found = (row.periodos ?? []).find((x: any) => x.periodo === p);
        flat[`p_${p}`] = found ? Number(found.calificacion).toFixed(1) : '—';
      }
      flat['promedio_str'] = row.promedio !== null ? Number(row.promedio).toFixed(1) : '—';
      return flat;
    });
  });

  readonly calificacionesColumns = computed((): ColumnConfig[] => {
    const periodos = this.periodosHeader();
    const cols: ColumnConfig[] = [
      { field: 'materia', header: 'Materia' },
      ...periodos.map(p => ({ field: `p_${p}`, header: p, width: '90px' } as ColumnConfig)),
      { field: 'promedio_str', header: 'Promedio', width: '90px' },
    ];
    return cols;
  });

  readonly asistenciasFlat = computed(() =>
    (this.asistencias()?.detalle ?? []).map((a: any) => ({
      ...a,
      fecha_str: a.fecha ? new Date(a.fecha).toLocaleDateString('es-MX') : '—',
    }))
  );
  readonly asistenciasColumns: ColumnConfig[] = [
    { field: 'fecha_str',   header: 'Fecha',        width: '110px' },
    { field: 'materia',     header: 'Materia' },
    { field: 'estado',      header: 'Estado',       width: '100px' },
    { field: 'observacion', header: 'Observación' },
  ];

  readonly tareasFlat = computed(() =>
    this.tareas().map((t: any) => ({
      ...t,
      fecha_str:   t.fecha_entrega ? new Date(t.fecha_entrega).toLocaleDateString('es-MX') : '—',
      estado_str:  t.entrega_id ? (t.es_tarde ? 'Entregada (tarde)' : 'Entregada') : 'Pendiente',
      cal_str:     t.calificacion_tarea !== null ? Number(t.calificacion_tarea).toFixed(1) : '—',
    }))
  );
  readonly tareasColumns: ColumnConfig[] = [
    { field: 'titulo',     header: 'Tarea' },
    { field: 'materia',    header: 'Materia' },
    { field: 'fecha_str',  header: 'Entrega',      width: '110px' },
    { field: 'estado_str', header: 'Estado',       width: '130px' },
    { field: 'cal_str',    header: 'Calificación', width: '110px' },
  ];

  ngOnInit() {
    this.cargarCiclos();
  }

  async cargarCiclos() {
    const data = await this.api.get<any[]>('/catalogs/ciclos').toPromise();
    this.ciclos.set(data ?? []);
    if (data?.length) this.cicloSeleccionado = data[0].id;
  }

  async buscar(event: any) {
    const q = event.query;
    const plantelId = this.ctx.plantel()?.id;
    const params = new URLSearchParams({ q });
    if (plantelId) params.set('plantel_id', plantelId);
    if (this.cicloSeleccionado) params.set('ciclo_id', this.cicloSeleccionado);
    const data = await this.api.get<any[]>(`/portal/buscar?${params}`).toPromise().catch(() => []);
    this.sugerencias.set(
      (data ?? []).map((a: any) => ({
        ...a,
        nombreCompleto: `${a.nombre} ${a.apellido_paterno} ${a.apellido_materno ?? ''}`.trim(),
      }))
    );
  }

  async seleccionar(event: any) {
    const al = event.value ?? event;
    this.alumnoId = al.id;
    await this.cargarTodo();
  }

  async recargar() {
    if (this.alumnoId) await this.cargarTodo();
  }

  async cargarTodo() {
    if (!this.alumnoId) return;
    const ciclo = this.cicloSeleccionado ? `?ciclo_id=${this.cicloSeleccionado}` : '';

    // resumen
    const r = await this.api.get<AlumnoResumen>(`/portal/${this.alumnoId}/resumen${ciclo}`).toPromise();
    this.resumen.set(r ?? null);

    // calificaciones
    this.cargandoCal.set(true);
    try {
      const cal = await this.api.get<any[]>(`/portal/${this.alumnoId}/calificaciones${ciclo}`).toPromise();
      this.calificaciones.set(cal ?? []);
    } finally { this.cargandoCal.set(false); }

    // asistencias
    this.cargandoAsist.set(true);
    try {
      const as = await this.api.get<any>(`/portal/${this.alumnoId}/asistencias${ciclo}`).toPromise();
      this.asistencias.set(as ?? null);
    } finally { this.cargandoAsist.set(false); }

    // tareas
    this.cargandoTareas.set(true);
    try {
      const t = await this.api.get<any[]>(`/portal/${this.alumnoId}/tareas${ciclo}`).toPromise();
      this.tareas.set(t ?? []);
    } finally { this.cargandoTareas.set(false); }
  }

  async togglePendientes() {
    this.soloPendientes.update(v => !v);
    if (!this.alumnoId) return;
    const ciclo = this.cicloSeleccionado ? `&ciclo_id=${this.cicloSeleccionado}` : '';
    const pend  = this.soloPendientes() ? '&solo_pendientes=true' : '';
    this.cargandoTareas.set(true);
    try {
      const t = await this.api.get<any[]>(`/portal/${this.alumnoId}/tareas?a=1${ciclo}${pend}`).toPromise();
      this.tareas.set(t ?? []);
    } finally { this.cargandoTareas.set(false); }
  }

  calPeriodo(row: any, periodo: string): number | null {
    const p = (row.periodos as any[]).find(x => x.periodo === periodo);
    return p ? p.calificacion : null;
  }

  calClass(cal: number | null): string {
    if (!cal) return '';
    if (cal >= 9)   return 'cal-excelente';
    if (cal >= 7.5) return 'cal-bien';
    if (cal >= 6)   return 'cal-regular';
    return 'cal-reprobado';
  }

  exportCalXLSX() {
    const flat: any[] = [];
    for (const row of this.calificaciones()) {
      for (const p of row.periodos) {
        flat.push({ materia: row.materia, periodo: p.periodo, calificacion: p.calificacion });
      }
    }
    const cols: ExportColumn[] = [
      { field: 'materia', header: 'Materia' },
      { field: 'periodo', header: 'Periodo' },
      { field: 'calificacion', header: 'Calificación' },
    ];
    const nombre = this.resumen()?.alumno.nombre ?? 'alumno';
    this.exporter.toXLSX(flat, cols, 'Calificaciones', `calificaciones-${nombre}`);
  }

  riesgoSeverity(nivel: string): TagSeverity {
    return nivel === 'ALTO' ? 'danger' : nivel === 'MEDIO' ? 'warn' : 'info';
  }

  asistSeverity(estado: string): TagSeverity {
    const m: Record<string, TagSeverity> = { PRESENTE: 'success', AUSENTE: 'danger', TARDE: 'warn' };
    return m[estado] ?? 'secondary';
  }

  lpSeverity(estatus: string): TagSeverity {
    const m: Record<string, TagSeverity> = {
      COMPLETADO: 'success', EN_PROGRESO: 'info', PENDIENTE: 'secondary', ABANDONADO: 'danger',
    };
    return m[estatus] ?? 'secondary';
  }

  ngOnDestroy(): void {}
}
