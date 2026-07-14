import { Component, OnDestroy, inject, signal, computed, OnInit, effect, ChangeDetectionStrategy} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject, takeUntil } from 'rxjs';
import { ButtonModule } from 'primeng/button';
import { SelectModule } from 'primeng/select';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { TagModule } from 'primeng/tag';
import { TooltipModule } from 'primeng/tooltip';
import { DialogModule } from 'primeng/dialog';
import { DrawerModule } from 'primeng/drawer';
import { TabsModule } from 'primeng/tabs';
import { ProgressBarModule } from 'primeng/progressbar';
import { DatePickerModule } from 'primeng/datepicker';
import { TableModule } from 'primeng/table';
import { ApiService } from '../../core/services/api.service';
import { ContextService } from '../../core/services/context.service';
import { ExportService } from '../../core/services/export.service';
import { ApexNotificationService } from 'apex-component-library';
import { CierrePeriodoComponent } from './cierre-periodo.component';
import { InteractiveGridComponent, ColumnConfig } from '../../shared/components/interactive-grid/interactive-grid.component';
import { AdesFormatDirective } from '../../shared/directives/ades-format.directive';

interface Actividad {
  id: string;
  titulo: string;
  tipo_item: string;
  fecha_entrega: string;
  fecha_examen: string | null;
  puntaje_maximo: number;
  nombre_materia: string;
  nombre_periodo: string;
  total_alumnos: number;
  entregadas: number;
  calificadas: number;
}

interface Entrega {
  id: string;
  estudiante_id: string;
  alumno_nombre: string;
  numero_matricula: string;
  estatus_entrega: string;
  calificacion_obtenida: number | null;
  comentario_profesor: string | null;
  archivo_url: string | null;
  _cal?: number | null;
}

interface CalPeriodo {
  alumno: string;
  numero_matricula: string;
  nombre_materia: string;
  calificacion_final: number | null;
  calificacion_calculada: number | null;
  ajuste_manual: number | null;
  score_por_item: Record<string, number>;
  cerrada: boolean;
  en_riesgo: boolean;
  minimo_aprobatorio: number;
  escala_maxima: number;
  cal_periodo_id: string;
}

interface MateriaOpt { id: string; nombre_materia: string; }
interface PeriodoOpt { id: string; nombre_periodo: string; }

interface InsightCobertura {
  materia_id: string;
  nombre_materia: string;
  tipo_materia: string;
  total_temas: number;
  temas_impartidos: number;
  temas_planeados: number;
  temas_pendientes: number;
  pct_cobertura: number;
}
interface Insights {
  resumen: { total_temas: number; temas_impartidos: number; pct_cobertura: number; estado: string };
  cobertura_por_materia: InsightCobertura[];
  tareas: { total_tareas: number; tareas_con_tema: number; tareas_sin_tema: number; pct_vinculadas: number };
  calificaciones: { nombre_materia: string; promedio: number; alumnos_evaluados: number; en_riesgo: number }[];
}

/**
 * Libreta de calificaciones granular (gradebook) con edición inline al estilo APEX.
 * Permite capturar calificaciones por evaluación, alumno y período para docentes.
 * Implementa cascada Plantel→Nivel→Grado→Grupo con computed() signals y
 * optimistic locking vía row_version.
 */
@Component({
  selector: 'app-gradebook',
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
  imports: [
    AdesFormatDirective,
    CommonModule, FormsModule, ButtonModule, SelectModule,
    InputTextModule, InputNumberModule, TagModule, TooltipModule,
    DialogModule, DrawerModule, TabsModule, ProgressBarModule, DatePickerModule, TableModule,
    CierrePeriodoComponent, InteractiveGridComponent,
  ],
  template: `
<div class="page-header">
  <div class="page-title">
    <i class="pi pi-book"></i> Gradebook — Calificaciones
  </div>
  <div class="page-actions">
    <button pButton icon="pi pi-refresh" label="Recalcular" severity="secondary"
            (click)="recalcularPeriodo()" [disabled]="!periodoSel"></button>
    <button pButton icon="pi pi-file-excel" label="Excel" severity="success"
            (click)="exportarExcel()"></button>
    <button pButton icon="pi pi-plus" label="Nueva actividad"
            (click)="abrirNuevaActividad()"></button>
    <button pButton icon="pi pi-lock" label="Cerrar período" severity="warn"
            pTooltip="Cierre formal del período seleccionado"
            [disabled]="!grupoSel || !periodoSel"
            (click)="abrirCierrePeriodo()"></button>
  </div>
</div>

<!-- Filtros de dominio: Materia y Período (la cascada plantel→nivel→grado→grupo vive en el topbar) -->
<div class="filter-bar">
  <p-select [options]="materias()" optionLabel="nombre_materia" optionValue="id"
            placeholder="Materia" [(ngModel)]="materiaSel"
            (onChange)="cargarActividades()" [disabled]="!grupoSel"
 [filter]="true" filterPlaceholder="Buscar..."/>
  <p-select [options]="periodos()" optionLabel="nombre_periodo" optionValue="id"
            placeholder="Período" [(ngModel)]="periodoSel"
            (onChange)="cargarConcentrado()" [disabled]="!grupoSel"
 [filter]="true" filterPlaceholder="Buscar..."/>
</div>

@if (!grupoSel) {
  <div class="empty-state">
    <i class="pi pi-book" style="font-size:2.5rem;color:#CBD5E1"></i>
    <h3 style="margin:.5rem 0 .25rem;color:#475569">Selecciona un grupo</h3>
    <p style="color:#94A3B8;max-width:320px;text-align:center">
      Selecciona el grupo en el menú de contexto superior para ver las actividades, calificaciones y cobertura curricular.
    </p>
  </div>
} @else {
  <!-- Búsqueda rápida -->
  <div style="display:flex; gap:0.75rem; align-items:center; margin-bottom:1rem; flex-wrap:wrap">
    <div style="flex:1; min-width:250px">
      <input
        pInputText
        type="text"
        placeholder="Buscar alumno, actividad o materia..."
        [(ngModel)]="busqueda"
        style="width:100%"
      />
    </div>
  </div>

  <p-tabs value="0">
  <p-tablist>
    <p-tab value="0"><i class="pi pi-table"></i> Actividades</p-tab>
    <p-tab value="1"><i class="pi pi-calendar"></i> Concentrado por período</p-tab>
    <p-tab value="2"><i class="pi pi-chart-bar"></i> Cobertura curricular</p-tab>
    <p-tab value="3"><i class="pi pi-lightbulb"></i> Insights académicos</p-tab>
  </p-tablist>
  <!-- ── TAB 1: Spreadsheet de actividades ── -->
  <p-tabpanel header="Actividades" value="0">
    @if (actividadesFlat().length === 0 && !cargando()) {
      <div class="empty-state" style="padding:3rem">
        <i class="pi pi-inbox" style="font-size:2rem;color:#CBD5E1"></i>
        <p style="color:#94A3B8">No hay actividades registradas para este grupo.
          Usa "Nueva actividad" para crear la primera.</p>
      </div>
    } @else {
      <app-interactive-grid
        [data]="actividadesFlat()"
        [columns]="actividadesColumns"
        [loading]="cargando()"
        (rowSelected)="abrirCalificacion($event)"
      />
    }
  </p-tabpanel>

  <!-- ── TAB 2: Concentrado de calificaciones ── -->
  <p-tabpanel header="Concentrado por período" value="1">
    @if (!periodoSel) {
      <div class="empty-state" style="padding:3rem">
        <i class="pi pi-calendar" style="font-size:2rem;color:#CBD5E1"></i>
        <p style="color:#94A3B8">Selecciona un período de evaluación para ver el concentrado.</p>
      </div>
    } @else {
      <app-interactive-grid
        [data]="concentradoFlat()"
        [columns]="concentradoColumns"
        [loading]="cargandoConc()"
        (rowSelected)="abrirAjuste($event)"
      />
    }
  </p-tabpanel>

  <!-- ── TAB 3: Cobertura curricular ── -->
  <p-tabpanel header="Cobertura curricular" value="2">
    @if (cobertura()) {
      <div class="kpi-row">
        <div class="kpi-card">
          <div class="kpi-value">{{ cobertura()?.pct_cobertura }}%</div>
          <div class="kpi-label">Cobertura</div>
        </div>
        <div class="kpi-card">
          <div class="kpi-value text-success">{{ cobertura()?.con_evidencia }}</div>
          <div class="kpi-label">Con evidencia</div>
        </div>
        <div class="kpi-card">
          <div class="kpi-value text-danger">{{ cobertura()?.sin_evidencia }}</div>
          <div class="kpi-label">Sin evidencia</div>
        </div>
      </div>
    }
    <app-interactive-grid
      [data]="coberturaTemasFlat()"
      [columns]="coberturaTemasColumns"
    />
  </p-tabpanel>

  <!-- ── TAB 4: Insights académicos ── -->
  <p-tabpanel header="Insights académicos" value="3">
    @if (!grupoSel) {
      <p class="text-center text-muted mt-4">Selecciona un grupo para ver insights.</p>
    } @else if (cargandoInsights()) {
      <p class="text-center text-muted mt-4">Cargando insights…</p>
    } @else if (insights()) {
      <!-- KPIs globales -->
      <div class="kpi-row">
        <div class="kpi-card" [class.kpi-danger]="insights()!.resumen.estado==='CRITICO'"
             [class.kpi-warn]="insights()!.resumen.estado==='ALERTA'"
             [class.kpi-ok]="insights()!.resumen.estado==='OK'">
          <div class="kpi-value">{{ insights()!.resumen.pct_cobertura }}%</div>
          <div class="kpi-label">Cobertura curricular</div>
          <p-tag [value]="insights()!.resumen.estado" [severity]="estadoSev(insights()!.resumen.estado)" />
        </div>
        <div class="kpi-card">
          <div class="kpi-value">{{ insights()!.resumen.temas_impartidos }}/{{ insights()!.resumen.total_temas }}</div>
          <div class="kpi-label">Temas impartidos</div>
        </div>
        <div class="kpi-card" [class.kpi-warn]="insights()!.tareas.pct_vinculadas < 50">
          <div class="kpi-value">{{ insights()!.tareas.pct_vinculadas }}%</div>
          <div class="kpi-label">Tareas vinculadas a temario</div>
          <small class="text-muted">{{ insights()!.tareas.tareas_con_tema }}/{{ insights()!.tareas.total_tareas }}</small>
        </div>
      </div>

      <!-- Barra de cobertura por materia -->
      <h4 class="section-title">Avance del temario por materia</h4>
      <div class="coverage-list">
        @for (m of insights()!.cobertura_por_materia; track m.materia_id) {
          <div class="coverage-row">
            <div class="coverage-name">
              <span class="materia-name">{{ m.nombre_materia }}</span>
              <span class="tipo-badge">{{ tipoLabel(m.tipo_materia) }}</span>
            </div>
            <div class="coverage-bar-wrap">
              <p-progressBar [value]="m.pct_cobertura"
                             [style]="{'height':'8px'}"
                             [showValue]="false"
                             [style.background]="'#e5e7eb'" />
            </div>
            <div class="coverage-pct">
              <strong [class]="pctClass(m.pct_cobertura)">{{ m.pct_cobertura }}%</strong>
              <small class="text-muted"> ({{ m.temas_impartidos }}/{{ m.total_temas }})</small>
            </div>
            <div class="coverage-tags">
              @if (m.temas_planeados > 0) {
                <p-tag value="{{ m.temas_planeados }} plan." severity="info" />
              }
              @if (m.temas_pendientes > 0) {
                <p-tag value="{{ m.temas_pendientes }} pend." severity="warn" />
              }
            </div>
          </div>
        }
      </div>

      <!-- Promedios por materia -->
      @if (insights()!.calificaciones.length > 0) {
        <h4 class="section-title mt-4">Promedios por materia</h4>
        <app-interactive-grid
          [data]="insightsCalFlat()"
          [columns]="insightsCalColumns"
        />
      }
    }
  </p-tabpanel>
</p-tabs>
} <!-- end @else grupoSel -->

<!-- ── Drawer: calificar actividad ── -->
<p-drawer [(visible)]="drawerCalifVisible" header="Calificar actividad"
           position="right" [style]="{width:'540px'}">
  @if (actividadSeleccionada()) {
    <div>
      <div class="mb-3">
        <strong>{{ actividadSeleccionada()?.titulo }}</strong><br>
        <small>{{ actividadSeleccionada()?.nombre_materia }} — {{ actividadSeleccionada()?.nombre_periodo }}</small>
      </div>
      <p-table [value]="entregasActiva()" [loading]="cargandoEntregas()" responsiveLayout="scroll">
        <ng-template pTemplate="header">
          <tr>
            <th>Alumno</th>
            <th style="width:110px">Estado</th>
            <th style="width:130px">Calificación</th>
          </tr>
        </ng-template>
        <ng-template pTemplate="body" let-e>
          <tr>
            <td>{{ e.alumno_nombre }}</td>
            <td><p-tag [value]="e.estatus_entrega"></p-tag></td>
            <td>
              <p-inputNumber [(ngModel)]="e._cal" [min]="0" [max]="10" [minFractionDigits]="1"
                             placeholder="0.0" styleClass="w-full" />
            </td>
          </tr>
        </ng-template>
        <ng-template pTemplate="emptymessage">
          <tr><td colspan="3" style="text-align:center;padding:1.5rem;color:var(--text-muted)">Sin entregas</td></tr>
        </ng-template>
      </p-table>
      <div class="mt-3 flex gap-2 justify-end">
        <button pButton label="Guardar calificaciones" icon="pi pi-save"
                (click)="guardarCalificacionMasiva()"></button>
      </div>
    </div>
  }
</p-drawer>

<!-- ── Dialog: ajuste manual ── -->
<p-dialog [(visible)]="dialogAjusteVisible" header="Ajuste manual" [modal]="true" [style]="{width:'420px'}">
  @if (rowAjuste()) {
    <div>
      <p><strong>{{ rowAjuste()?.alumno }}</strong> — {{ rowAjuste()?.nombre_materia }}</p>
      <p>Calificación calculada: <strong>{{ rowAjuste()?.calificacion_calculada }}</strong></p>
      <div class="field mt-3">
        <label>Nueva calificación</label>
        <p-inputNumber [(ngModel)]="ajusteValor" [min]="0" [max]="rowAjuste()?.escala_maxima"
                       [step]="0.1" [useGrouping]="false" styleClass="w-full" />
      </div>
      <div class="field mt-2">
        <label>Justificación (mín. 20 caracteres)</label>
        <textarea pInputText [(ngModel)]="ajusteJustificacion" rows="3"
                  class="w-full" style="width:100%;resize:vertical;padding:6px;border:1px solid #ccc;border-radius:4px"></textarea>
        <small class="text-muted">{{ ajusteJustificacion.length }} caracteres</small>
      </div>
    </div>
  }
  <ng-template pTemplate="footer">
    <button pButton label="Cancelar" severity="secondary" (click)="dialogAjusteVisible = false"></button>
    <button pButton label="Aplicar ajuste" (click)="aplicarAjuste()"></button>
  </ng-template>
</p-dialog>

<!-- ── Dialog: nueva actividad ── -->
<p-dialog [(visible)]="mostrarNuevaActividad" header="Nueva actividad evaluable"
          [modal]="true" [style]="{width:'560px'}">
  <div class="grid grid-cols-2 gap-3">
    <div class="field col-span-2">
      <label>Título *</label>
      <input pInputText [(ngModel)]="nuevaAct.titulo" class="w-full"
             placeholder="Ej. Examen del bloque 2"
             pTooltip="Nombre breve y descriptivo de la actividad" tooltipPosition="top" />
    </div>
    <div class="field">
      <label>Tipo</label>
      <p-select [options]="tiposItem" optionLabel="label" optionValue="value"
                [(ngModel)]="nuevaAct.tipo_item" styleClass="w-full"
 [filter]="true" filterPlaceholder="Buscar..."/>
    </div>
    <div class="field">
      <label>Puntaje máximo</label>
      <p-inputNumber [(ngModel)]="nuevaAct.puntaje_maximo" [min]="0.01" [max]="10" [step]="0.1"
                     placeholder="10.0" styleClass="w-full"
                     pTooltip="Escala oficial SEP: de 0 a 10" tooltipPosition="top" />
    </div>
    <div class="field">
      <label>Fecha asignación</label>
      <p-datepicker [(ngModel)]="nuevaAct.fecha_asignacion" dateFormat="dd/mm/yy"
                    [showIcon]="true" placeholder="DD/MM/AAAA"
                    [style]="{width:'100%'}" [inputStyle]="{width:'100%'}" />
    </div>
    <div class="field">
      <label>Fecha entrega *</label>
      <p-datepicker [(ngModel)]="nuevaAct.fecha_entrega" dateFormat="dd/mm/yy"
                    [showIcon]="true" [minDate]="nuevaAct.fecha_asignacion" placeholder="DD/MM/AAAA"
                    [style]="{width:'100%'}" [inputStyle]="{width:'100%'}"
                    pTooltip="No puede ser anterior a la fecha de asignación" tooltipPosition="top" />
    </div>
    <div class="field col-span-2">
      <label>Descripción</label>
      <textarea pInputText [(ngModel)]="nuevaAct.descripcion" rows="2"
                placeholder="Instrucciones para el alumno (opcional)"
                class="w-full" style="width:100%;padding:6px;border:1px solid #ccc;border-radius:4px"></textarea>
    </div>
  </div>
  <ng-template pTemplate="footer">
    <button pButton label="Cancelar" severity="secondary" (click)="mostrarNuevaActividad = false"></button>
    <button pButton label="Crear y generar slots" icon="pi pi-plus" (click)="crearActividad()"></button>
  </ng-template>
</p-dialog>
<!-- ── Wizard: Cierre de Período ── -->
<app-cierre-periodo
  [(visible)]="cierreVisible"
  [periodoId]="periodoSel"
  [periodoNombre]="periodoNombreSel()"
  [grupoId]="grupoSel"
  [grupoNombre]="grupoNombreSel()"
  (periodoCerrado)="onPeriodoCerrado()"
/>
  `,
  styles: [`
    .filter-bar { display:flex; gap:8px; flex-wrap:wrap; margin-bottom:16px; }
    .row-riesgo td { background:#fff5f5 !important; }
    .kpi-row { display:flex; gap:16px; margin-bottom:16px; }
    .kpi-card { background:var(--p-surface-0); border:1px solid var(--p-surface-border);
                border-radius:8px; padding:16px; min-width:120px; text-align:center; }
    .kpi-value { font-size:2rem; font-weight:700; font-family:'Jost',sans-serif; }
    .cal-excelente { color:#15803d; font-weight:600; }
    .cal-bien      { color:#0369a1; font-weight:600; }
    .cal-regular   { color:#b45309; font-weight:600; }
    .cal-reprobado { color:#dc2626; font-weight:600; }
    :host ::ng-deep .p-datatable-sm .p-datatable-tbody > tr > td { padding:4px 8px; }
    .kpi-card.kpi-danger { border-color:#dc2626; }
    .kpi-card.kpi-warn   { border-color:#f59e0b; }
    .kpi-card.kpi-ok     { border-color:#16a34a; }
    .section-title { font-size:.95rem; font-weight:600; margin:1rem 0 .5rem; color:var(--p-text-color); }
    .coverage-list { display:flex; flex-direction:column; gap:.4rem; }
    .coverage-row  { display:grid; grid-template-columns:260px 1fr 120px 120px; align-items:center; gap:8px; padding:4px 0; border-bottom:1px solid var(--p-surface-border); }
    .coverage-name { display:flex; flex-direction:column; }
    .materia-name  { font-size:.85rem; font-weight:500; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
    .tipo-badge    { font-size:.7rem; color:var(--p-text-muted-color); }
    .coverage-pct  { text-align:right; font-size:.85rem; white-space:nowrap; }
    .coverage-tags { display:flex; gap:4px; flex-wrap:wrap; }
    .pct-ok        { color:#16a34a; font-weight:600; }
    .pct-warn      { color:#b45309; font-weight:600; }
    .pct-danger    { color:#dc2626; font-weight:600; }
    .text-muted    { color:var(--p-text-muted-color); }
    .empty-state { display:flex; flex-direction:column; align-items:center; justify-content:center; gap:.75rem; padding:5rem 2rem; }
    .empty-state p { margin:0; font-size:.88rem; }
    .page-title { font-size:1.35rem; font-weight:700; font-family:'Jost',sans-serif; display:flex; align-items:center; gap:.5rem; }
    .page-actions { display:flex; gap:.5rem; align-items:center; flex-wrap:wrap; }
    .page-header { display:flex; justify-content:space-between; align-items:flex-start; margin-bottom:1rem; }
  `],
})
export class GradebookComponent implements OnInit, OnDestroy {
  private destroy$ = new Subject<void>();
  private api = inject(ApiService);
  readonly ctx = inject(ContextService);
  private exporter = inject(ExportService);
  private readonly notify = inject(ApexNotificationService);

  constructor() {
    // Recargar cuando cambia el grupo en el contexto global (topbar).
    effect(() => {
      const grupo = this.ctx.grupo();
      this.grupoSel   = grupo?.id ?? null;
      this.materiaSel = null;
      this.periodoSel = null;
      this.materias.set([]);
      this.periodos.set([]);
      this.actividades.set([]);
      this.concentrado.set([]);
      if (this.grupoSel) {
        this.onGrupoChange();
      }
    });
  }

  materias = signal<MateriaOpt[]>([]);
  periodos = signal<PeriodoOpt[]>([]);
  actividades = signal<Actividad[]>([]);
  concentrado = signal<CalPeriodo[]>([]);
  entregasActiva = signal<Entrega[]>([]);
  cobertura = signal<any>(null);
  insights = signal<Insights | null>(null);
  actividadSeleccionada = signal<Actividad | null>(null);
  rowAjuste = signal<CalPeriodo | null>(null);

  cargando = signal(false);
  cargandoConc = signal(false);
  cargandoEntregas = signal(false);
  cargandoInsights = signal(false);

  grupoSel: string | null = null;
  materiaSel: string | null = null;
  periodoSel: string | null = null;

  drawerCalifVisible = false;
  dialogAjusteVisible = false;
  mostrarNuevaActividad = false;
  cierreVisible = false;
  ajusteValor: number | null = null;
  ajusteJustificacion = '';

  readonly periodoNombreSel = computed(() =>
    this.periodos().find(p => p.id === this.periodoSel)?.nombre_periodo ?? ''
  );
  readonly grupoNombreSel = computed(() =>
    this.ctx.grupo()?.nombre_grupo ?? ''
  );

  busqueda = signal('');

  readonly actividadesFlat = computed(() => {
    const q = this.busqueda().toLowerCase();
    const list = this.actividades().map(a => ({
      ...a,
      titulo_mat:   `${a.titulo} — ${a.nombre_materia}`,
      entrega_str:  `${a.entregadas}/${a.total_alumnos}`,
      fecha_str:    a.fecha_entrega ? new Date(a.fecha_entrega).toLocaleDateString('es-MX') : '—',
    }));
    if (!q) return list;
    return list.filter(a =>
      a.titulo_mat.toLowerCase().includes(q) ||
      (a.tipo_item || '').toLowerCase().includes(q)
    );
  });
  readonly actividadesColumns: ColumnConfig[] = [
    { field: 'titulo_mat',  header: 'Actividad' },
    { field: 'tipo_item',   header: 'Tipo',          width: '100px' },
    { field: 'fecha_str',   header: 'Fecha entrega', width: '120px' },
    { field: 'entrega_str', header: 'Entregas',      width: '90px' },
    { field: 'calificadas', header: 'Calificadas',   width: '100px' },
  ];

  readonly concentradoFlat = computed(() => {
    const q = this.busqueda().toLowerCase();
    const list = this.concentrado().map(r => ({
      ...r,
      cal_calc_str:  r.calificacion_calculada !== null ? String(r.calificacion_calculada) : '—',
      ajuste_str:    r.ajuste_manual !== null ? String(r.ajuste_manual) : '—',
      cal_final_str: r.calificacion_final !== null ? String(r.calificacion_final) : '—',
      estado_str:    r.cerrada ? 'Cerrada' : 'Abierta',
    }));
    if (!q) return list;
    return list.filter(r =>
      (r.alumno || '').toLowerCase().includes(q) ||
      (r.numero_matricula || '').toLowerCase().includes(q) ||
      (r.nombre_materia || '').toLowerCase().includes(q)
    );
  });
  readonly concentradoColumns: ColumnConfig[] = [
    { field: 'alumno',          header: 'Alumno' },
    { field: 'numero_matricula',header: 'Matrícula',  width: '110px' },
    { field: 'nombre_materia',  header: 'Materia' },
    { field: 'cal_calc_str',    header: 'Calculada',  width: '90px' },
    { field: 'ajuste_str',      header: 'Ajuste',     width: '80px' },
    { field: 'cal_final_str',   header: 'Final',      width: '80px' },
    { field: 'estado_str',      header: 'Estado',     width: '80px' },
  ];

  readonly coberturaTemasFlat = computed(() =>
    (this.cobertura()?.temas ?? []).map((t: any) => ({
      ...t,
      evidencia_str: t.tiene_evidencia ? 'Sí' : 'Sin evidencia',
    }))
  );
  readonly coberturaTemasColumns: ColumnConfig[] = [
    { field: 'orden',          header: '#',            width: '50px' },
    { field: 'nombre_tema',    header: 'Tema' },
    { field: 'nombre_materia', header: 'Materia' },
    { field: 'num_actividades',header: 'Actividades',  width: '100px' },
    { field: 'evidencia_str',  header: 'Evidencia',    width: '120px' },
  ];

  readonly insightsCalFlat = computed(() =>
    (this.insights()?.calificaciones ?? []).map((r: any) => ({
      ...r,
      promedio_str:  r.promedio !== null ? String(r.promedio) : '—',
      riesgo_str:    r.en_riesgo > 0 ? `${r.en_riesgo} alumnos` : 'Ninguno',
    }))
  );
  readonly insightsCalColumns: ColumnConfig[] = [
    { field: 'nombre_materia',   header: 'Materia' },
    { field: 'promedio_str',     header: 'Promedio',     width: '100px' },
    { field: 'alumnos_evaluados',header: 'Alumnos eval.', width: '120px' },
    { field: 'riesgo_str',       header: 'En riesgo (<6)', width: '130px' },
  ];


  tiposItem = [
    { label: 'Tarea', value: 'tarea' },
    { label: 'Examen', value: 'examen' },
    { label: 'Proyecto', value: 'proyecto' },
    { label: 'Laboratorio', value: 'laboratorio' },
    { label: 'Participación', value: 'participacion' },
    { label: 'Otro', value: 'otro' },
  ];

  nuevaAct: { titulo: string; descripcion: string; tipo_item: string; fecha_asignacion: Date | null; fecha_entrega: Date | null; puntaje_maximo: number } = {
    titulo: '', descripcion: '', tipo_item: 'tarea',
    fecha_asignacion: new Date(), fecha_entrega: null, puntaje_maximo: 10.0,
  };

  private _toIso(d: Date): string {
    return d.toISOString().substring(0, 10);
  }

  abrirNuevaActividad(): void {
    this.nuevaAct = { titulo: '', descripcion: '', tipo_item: 'tarea', fecha_asignacion: new Date(), fecha_entrega: null, puntaje_maximo: 10 };
    this.mostrarNuevaActividad = true;
  }

  ngOnInit() {
    // ngOnInit no necesita cargar planteles — la cascada vive en el topbar.
  }

  onGrupoChange() {
    this.materiaSel = null;
    this.periodoSel = null;
    this.actividades.set([]);
    this.concentrado.set([]);
    if (!this.grupoSel) return;
    this.api.get(`/materias?grupo_id=${this.grupoSel}`).pipe(takeUntil(this.destroy$)).subscribe((r: any) =>
      this.materias.set(r.data ?? r));
    this.api.get(`/catalogs/periodos?grupo_id=${this.grupoSel}`).pipe(takeUntil(this.destroy$)).subscribe((r: any) =>
      this.periodos.set(r ?? []));
    this.cargarActividades();
    this.cargarCobertura();
    this.cargarInsights();
  }

  cargarActividades() {
    if (!this.grupoSel) return;
    let url = `/actividades/grupo/${this.grupoSel}?`;
    if (this.materiaSel) url += `materia_id=${this.materiaSel}&`;
    if (this.periodoSel) url += `periodo_id=${this.periodoSel}&`;
    this.cargando.set(true);
    this.api.get(url).pipe(takeUntil(this.destroy$)).subscribe({ next: (r: any) => { this.actividades.set(r); this.cargando.set(false); },
      error: () => this.cargando.set(false) });
  }

  cargarConcentrado() {
    if (!this.grupoSel || !this.periodoSel) return;
    this.cargandoConc.set(true);
    const url = `/gradebook/grupo/${this.grupoSel}/concentrado?periodo_id=${this.periodoSel}`;
    this.api.get(url).pipe(takeUntil(this.destroy$)).subscribe({ next: (r: any) => {
      const rows = (r.detalle ?? []).map((row: any) => ({
        ...row,
        en_riesgo: row.calificacion_final !== null && row.calificacion_final < row.minimo_aprobatorio,
      }));
      this.concentrado.set(rows);
      this.cargandoConc.set(false);
    }, error: () => this.cargandoConc.set(false) });
  }

  cargarCobertura() {
    if (!this.grupoSel) return;
    let url = `/gradebook/grupo/${this.grupoSel}/cobertura-curricular`;
    if (this.materiaSel) url += `?materia_id=${this.materiaSel}`;
    this.api.get(url).pipe(takeUntil(this.destroy$)).subscribe((r: any) => this.cobertura.set(r));
  }

  cargarInsights() {
    if (!this.grupoSel) return;
    this.cargandoInsights.set(true);
    this.api.get(`/planeacion/insights/${this.grupoSel}`).pipe(takeUntil(this.destroy$)).subscribe({
      next: (r: any) => { this.insights.set(r); this.cargandoInsights.set(false); },
      error: () => this.cargandoInsights.set(false),
    });
  }

  abrirCalificacion(act: Actividad) {
    this.actividadSeleccionada.set(act);
    this.cargandoEntregas.set(true);
    this.drawerCalifVisible = true;
    this.api.get(`/actividades/${act.id}/entregas`).pipe(takeUntil(this.destroy$)).subscribe({ next: (r: any) => {
      const entregas = r.map((e: any) => ({ ...e, _cal: e.calificacion_obtenida ?? null }));
      this.entregasActiva.set(entregas);
      this.cargandoEntregas.set(false);
    }, error: () => this.cargandoEntregas.set(false) });
  }

  guardarCalificacionMasiva() {
    const act = this.actividadSeleccionada();
    if (!act) return;
    const items = this.entregasActiva()
      .filter(e => e._cal !== null && e._cal !== undefined)
      .map(e => ({ alumnoId: e.estudiante_id, calificacion: e._cal!, comentario: e.comentario_profesor }));
    if (!items.length) return;
    this.api.patch(`/actividades/${act.id}/calificar-masivo`, items).pipe(takeUntil(this.destroy$)).subscribe(() => {
      this.notify.success('Guardado', `${items.length} calificaciones guardadas`);
      this.drawerCalifVisible = false;
      this.cargarActividades();
      this.cargarConcentrado();
    });
  }

  abrirAjuste(row: CalPeriodo) {
    this.rowAjuste.set(row);
    this.ajusteValor = row.ajuste_manual ?? row.calificacion_calculada;
    this.ajusteJustificacion = '';
    this.dialogAjusteVisible = true;
  }

  aplicarAjuste() {
    const row = this.rowAjuste();
    if (!row || this.ajusteValor === null) return;
    if (this.ajusteJustificacion.trim().length < 20) {
      this.notify.warning('Justificación corta', 'Mínimo 20 caracteres');
      return;
    }
    this.api.post(`/gradebook/${row.cal_periodo_id}/ajuste-manual`, {
      ajuste_manual: this.ajusteValor,
      justificacion_ajuste: this.ajusteJustificacion,
    }).pipe(takeUntil(this.destroy$)).subscribe(() => {
      this.notify.success('Ajuste aplicado', 'Calificación actualizada');
      this.dialogAjusteVisible = false;
      this.cargarConcentrado();
    });
  }

  recalcularPeriodo() {
    if (!this.grupoSel || !this.periodoSel) return;
    // recalcularPeriodo() del backend recibe grupo_id/materia_id como query param, no como body
    // (no tiene @RequestBody) — enviarlo en el body se ignoraba silenciosamente y recalculaba TODO el período.
    this.api.post(`/gradebook/periodo/${this.periodoSel}/recalcular-todo?grupo_id=${encodeURIComponent(this.grupoSel)}`,
      {}).pipe(takeUntil(this.destroy$)).subscribe((r: any) => {
      this.notify.info('Recalculado', `${r.recalculados} registros actualizados`);
      this.cargarConcentrado();
    });
  }

  crearActividad() {
    if (!this.grupoSel || !this.materiaSel) {
      this.notify.warning('Falta información', 'Selecciona grupo y materia');
      return;
    }
    if (!this.nuevaAct.titulo || !this.nuevaAct.fecha_entrega) {
      this.notify.warning('Campos vacíos', 'Título y fecha de entrega son obligatorios');
      return;
    }
    const fechaAsignacion = this.nuevaAct.fecha_asignacion ?? new Date();
    if (this._toIso(this.nuevaAct.fecha_entrega) < this._toIso(fechaAsignacion)) {
      this.notify.warning('Fecha inválida', 'La fecha de entrega no puede ser anterior a la de asignación');
      return;
    }
    // ActividadesController.ActividadIn espera camelCase estricto (clase @Data sin @JsonProperty).
    this.api.post('/actividades', {
      titulo: this.nuevaAct.titulo,
      descripcion: this.nuevaAct.descripcion,
      tipoItem: this.nuevaAct.tipo_item,
      fechaAsignacion: this._toIso(fechaAsignacion),
      fechaEntrega: this._toIso(this.nuevaAct.fecha_entrega),
      puntajeMaximo: this.nuevaAct.puntaje_maximo,
      grupoId: this.grupoSel,
      materiaId: this.materiaSel,
      periodoEvaluacionId: this.periodoSel,
    }).pipe(takeUntil(this.destroy$)).subscribe({
      next: (r: any) => {
        this.notify.success('Creada', `${r.slots_creados} slots generados`);
        this.mostrarNuevaActividad = false;
        this.nuevaAct = { titulo: '', descripcion: '', tipo_item: 'tarea', fecha_asignacion: new Date(), fecha_entrega: null, puntaje_maximo: 10 };
        this.cargarActividades();
      },
      error: (err: any) => {
        this.notify.error('Error', err?.error?.message ?? 'No se pudo crear la actividad');
      },
    });
  }

  abrirCierrePeriodo() {
    if (!this.grupoSel || !this.periodoSel) return;
    this.cierreVisible = true;
  }

  onPeriodoCerrado() {
    this.cierreVisible = false;
    this.cargarConcentrado();
  }

  exportarExcel() {
    const cols = [
      { field: 'alumno', header: 'Alumno' },
      { field: 'numero_matricula', header: 'Matrícula' },
      { field: 'nombre_materia', header: 'Materia' },
      { field: 'calificacion_calculada', header: 'Calculada' },
      { field: 'ajuste_manual', header: 'Ajuste' },
      { field: 'calificacion_final', header: 'Final' },
    ];
    this.exporter.toXLSX(this.concentrado(), cols, 'Calificaciones', 'gradebook');
  }

  pctEntregas(act: Actividad): number {
    return act.total_alumnos ? Math.round((act.entregadas / act.total_alumnos) * 100) : 0;
  }

  calClass(cal: number | null, escala: number): string {
    if (cal === null) return '';
    const ratio = escala >= 100 ? cal / 10 : cal;
    if (ratio >= 9) return 'cal-excelente';
    if (ratio >= 7.5) return 'cal-bien';
    if (ratio >= 6) return 'cal-regular';
    return 'cal-reprobado';
  }

  tipoSeverity(tipo: string): 'success' | 'info' | 'warn' | 'danger' | 'secondary' {
    const map: Record<string, any> = {
      examen: 'danger', tarea: 'info', proyecto: 'success',
      laboratorio: 'warn', participacion: 'secondary', otro: 'secondary',
    };
    return map[tipo] ?? 'secondary';
  }

  estatusSeverity(s: string): 'success' | 'info' | 'warn' | 'danger' | 'secondary' {
    const map: Record<string, any> = {
      CALIFICADA: 'success', ENTREGADA: 'info',
      PENDIENTE: 'warn', SIN_ENTREGA: 'danger', EXCUSA: 'secondary',
    };
    return map[s] ?? 'secondary';
  }

  estadoSev(e: string): 'success' | 'warn' | 'danger' | 'secondary' {
    return e === 'OK' ? 'success' : e === 'ALERTA' ? 'warn' : 'danger';
  }

  pctClass(pct: number): string {
    if (pct >= 70) return 'pct-ok';
    if (pct >= 40) return 'pct-warn';
    return 'pct-danger';
  }

  tipoLabel(tipo: string): string {
    const labels: Record<string, string> = {
      OFICIAL_SEP_PRIMARIA:   'SEP Primaria',
      OFICIAL_SEP_SECUNDARIA: 'SEP Secundaria',
      OFICIAL_UAEMEX_PREP:    'UAEMEX CBU',
      NEVADI_FORMATIVA:       'Nevadi Formativa',
      NEVADI_ENRIQUECIMIENTO: 'Nevadi Enriquecimiento',
      NEVADI_ESPECIALIZADA:   'Nevadi Especializada',
    };
    return labels[tipo] ?? tipo;
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
