/**
 * EvalDocenteComponent — Evaluación Docente 360°.
 *
 * Dos pestañas:
 *   1. Resumen: promedio global por tipo de evaluador para un docente/ciclo
 *   2. Nueva evaluación: selecciona docente, tipo evaluador, califica criterios (1-5)
 */
import { Component, OnDestroy, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject, takeUntil } from 'rxjs';
import { ButtonModule } from 'primeng/button';
import { SelectModule } from 'primeng/select';
import { TabsModule } from 'primeng/tabs';
import { CardModule } from 'primeng/card';
import { TagModule } from 'primeng/tag';
import { InteractiveGridComponent, ColumnConfig } from '../../shared/components/interactive-grid/interactive-grid.component';
import { TextareaModule } from 'primeng/textarea';
import { SliderModule } from 'primeng/slider';
import { SkeletonModule } from 'primeng/skeleton';

import { ApiService } from '../../core/services/api.service';
import { ContextService } from '../../core/services/context.service';
import { ExportService, ExportColumn } from '../../core/services/export.service';
import type { Profesor } from '../../core/models';
import { ApexNotificationService } from 'apex-component-library';

interface Criterio {
  id: string;
  nombre_criterio: string;
  descripcion: string | null;
  categoria: string;
  peso_porcentual: number;
  escala_min: number;
  escala_max: number;
}

interface CriterioCalif {
  criterio: Criterio;
  calificacion: number;
  observacion: string;
}

interface ResumenEvaluador {
  tipo_evaluador: string;
  total_evaluaciones: number;
  promedio_global: number;
  ultima_fecha: string;
}

interface EvaluacionOut {
  id: string;
  profesor_id: string;
  tipo_evaluador: string;
  fecha_evaluacion: string;
  calificacion_global: number | null;
  estatus: string;
}

type TagSeverity = 'success' | 'info' | 'warn' | 'danger' | 'secondary' | 'contrast';

const ESTATUS_SEV: Record<string, TagSeverity> = {
  BORRADOR: 'secondary', ENVIADA: 'info', APROBADA: 'success',
};

const TIPO_LABELS: Record<string, string> = {
  DIRECTOR: 'Director', COORDINADOR: 'Coordinador', PAR: 'Par docente', AUTO: 'Autoevaluación',
};

@Component({
  selector: 'app-eval-docente',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    ButtonModule, SelectModule, TabsModule, CardModule,
    TagModule, TextareaModule, SliderModule, SkeletonModule,
    InteractiveGridComponent,
  ],
  template: `

    <div class="page-header">
      <div>
        <h2>Evaluación Docente 360°</h2>
        <p class="subtitle">Evaluación integral por criterios ponderados — Director, Coordinador, Par, Autoevaluación</p>
      </div>
    </div>

    <p-tabs value="0">

      <!-- ── Pestaña Resumen ── -->
      <p-tabpanel value="0" header="Resumen por docente">
        @if (!ctx.ciclo()) {
          <div class="ciclo-warn">
            <i class="pi pi-info-circle"></i>
            No hay ciclo escolar seleccionado — se mostrarán evaluaciones de todos los ciclos.
            Selecciona un ciclo en la barra superior para filtrar.
          </div>
        }
        <div class="toolbar-row">
          <p-select
            [options]="profesoresOpts()"
            [(ngModel)]="selectedProfesor"
            optionLabel="_nombre"
            placeholder="Seleccionar docente..."
            [showClear]="true"
            [loading]="loadingProfesores()"
            (onChange)="cargarResumen()"
            styleClass="select-docente"
            [filter]="true" filterPlaceholder="Buscar..."/>
          <p-button label="CSV" icon="pi pi-file" severity="secondary" [text]="true"
            (onClick)="exportCSV()" [disabled]="!resumen().length" pTooltip="Exportar CSV" />
          <p-button label="Excel" icon="pi pi-file-excel" severity="secondary" [text]="true"
            (onClick)="exportXLSX()" [disabled]="!resumen().length" pTooltip="Exportar Excel" />
        </div>

        @if (loadingResumen()) {
          <div class="skeleton-grid">
            @for (_ of [1,2,3,4]; track $index) {
              <p-skeleton height="100px" borderRadius="8px" />
            }
          </div>
        } @else if (resumen().length) {
          <!-- KPI cards por tipo -->
          <div class="kpi-grid">
            @for (r of resumen(); track r.tipo_evaluador) {
              <div class="kpi-card">
                <div class="kpi-tipo">{{ tipoLabel(r.tipo_evaluador) }}</div>
                <div class="kpi-score" [class.score-good]="r.promedio_global >= 4" [class.score-mid]="r.promedio_global >= 3 && r.promedio_global < 4" [class.score-low]="r.promedio_global < 3">
                  {{ r.promedio_global | number:'1.1-1' }}
                  <span class="kpi-denom">/5</span>
                </div>
                <div class="kpi-meta">{{ r.total_evaluaciones }} evaluación(es)</div>
                <div class="kpi-meta">Última: {{ r.ultima_fecha | date:'dd/MM/yyyy' }}</div>
              </div>
            }
          </div>

          <!-- Promedio global ponderado -->
          <div class="global-score">
            <span class="global-label">Promedio global ponderado</span>
            <span class="global-value">{{ promedioGlobal() | number:'1.2-2' }} / 5.00</span>
          </div>

          <!-- Historial de evaluaciones -->
          <app-interactive-grid
            [data]="evaluacionesFlat()"
            [columns]="evaluacionesColumns"
            [loading]="loadingEvals()"
          />
        } @else {
          <div class="empty-state">
            <i class="pi pi-star" style="font-size:2.5rem;color:#CBD5E1"></i>
            <p>Selecciona un docente para ver su historial de evaluaciones</p>
          </div>
        }
      </p-tabpanel>

      <!-- ── Pestaña Nueva Evaluación ── -->
      <p-tabpanel value="1" header="Nueva evaluación">
        <div class="form-section">
          <div class="form-row">
            <div class="field">
              <label>Docente a evaluar *</label>
              <p-select
                [options]="profesoresOpts()"
                [(ngModel)]="formProfesor"
                optionLabel="_nombre"
                placeholder="Seleccionar docente..."
                styleClass="w-full"
                [loading]="loadingProfesores()"
                [filter]="true" filterPlaceholder="Buscar..."/>
            </div>
            <div class="field">
              <label>Tipo de evaluador *</label>
              <p-select
                [options]="tiposEvaluador"
                [(ngModel)]="formTipo"
                optionLabel="label"
                optionValue="value"
                placeholder="Seleccionar tipo..."
                styleClass="w-full"
              

              [filter]="true" filterPlaceholder="Buscar..."/>
            </div>
          </div>

          <div class="field">
            <label>Comentarios generales</label>
            <textarea
              pTextarea
              [(ngModel)]="formComentarios"
              rows="2"
              placeholder="Observaciones generales sobre el desempeño docente..."
              style="width:100%"
            ></textarea>
          </div>
        </div>

        <!-- Criterios de evaluación -->
        @if (criterios().length) {
          <h3 class="criterios-titulo">Criterios de evaluación</h3>
          <div class="criterios-list">
            @for (item of criteriosForm(); track item.criterio.id) {
              <div class="criterio-card">
                <div class="criterio-header">
                  <div>
                    <div class="criterio-nombre">{{ item.criterio.nombre_criterio }}</div>
                    <div class="criterio-cat">{{ item.criterio.categoria }} · {{ item.criterio.peso_porcentual }}% del total</div>
                  </div>
                  <div class="criterio-score-badge" [class.score-good]="item.calificacion >= 4" [class.score-mid]="item.calificacion === 3" [class.score-low]="item.calificacion <= 2">
                    {{ item.calificacion }}
                  </div>
                </div>
                <!-- Botones 1-5 -->
                <div class="score-buttons">
                  @for (n of [1,2,3,4,5]; track n) {
                    <button
                      class="score-btn"
                      [class.selected]="item.calificacion === n"
                      [class.score-btn-good]="n >= 4"
                      [class.score-btn-mid]="n === 3"
                      [class.score-btn-low]="n <= 2"
                      (click)="item.calificacion = n"
                    >{{ n }}</button>
                  }
                  <span class="score-label">{{ scoreName(item.calificacion) }}</span>
                </div>
                <textarea
                  pTextarea
                  [(ngModel)]="item.observacion"
                  [placeholder]="'Observación sobre ' + item.criterio.nombre_criterio + '...'"
                  rows="1"
                  style="width:100%;margin-top:0.4rem;font-size:0.8rem"
                ></textarea>
              </div>
            }
          </div>

          <div class="submit-row">
            <div class="promedio-preview">
              Calificación preliminar: <strong>{{ promedioPreview() | number:'1.2-2' }} / 5.00</strong>
            </div>
            <p-button
              label="Guardar como borrador"
              icon="pi pi-save"
              severity="secondary"
              [loading]="saving()"
              (onClick)="guardar(false)"
              [disabled]="!formProfesor || !formTipo"
            />
            <p-button
              label="Guardar y enviar"
              icon="pi pi-send"
              [loading]="saving()"
              (onClick)="guardar(true)"
              [disabled]="!formProfesor || !formTipo"
            />
          </div>
        } @else {
          <div style="text-align:center;padding:2rem;color:#94A3B8">
            <p-skeleton height="40px" width="100%" styleClass="mb-2" />
            <p-skeleton height="40px" width="100%" styleClass="mb-2" />
            <p-skeleton height="40px" width="100%" />
          </div>
        }
      </p-tabpanel>

    </p-tabs>
  `,
  styles: [`
    .subtitle    { font-size: 0.82rem; color: var(--text-color-secondary); margin: 0; }
    .toolbar-row { display: flex; gap: 0.75rem; align-items: center; margin-bottom: 1.25rem; flex-wrap: wrap; }
    .select-docente { min-width: 280px; }

    /* KPI cards */
    .kpi-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); gap: 1rem; margin-bottom: 1rem; }
    .kpi-card { background: var(--surface-card); border: 1px solid var(--surface-border); border-radius: 10px; padding: 1rem 1.25rem; }
    .kpi-tipo { font-size: 0.75rem; font-weight: 700; text-transform: uppercase; letter-spacing: .05em; color: var(--text-color-secondary); margin-bottom: 0.4rem; }
    .kpi-score { font-family: var(--font-display); font-size: 2.4rem; font-weight: 800; line-height: 1; }
    .kpi-denom { font-size: 0.9rem; font-weight: 400; opacity: 0.5; }
    .kpi-meta  { font-size: 0.75rem; color: var(--text-color-secondary); margin-top: 0.2rem; }
    .score-good { color: #16A34A; }
    .score-mid  { color: #D97706; }
    .score-low  { color: #DC2626; }

    .skeleton-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 1rem; margin-bottom: 1.25rem; }

    .global-score { display: flex; align-items: center; justify-content: space-between; background: var(--surface-card); border-left: 4px solid var(--nevadi-red); border-radius: 6px; padding: 0.75rem 1rem; margin-bottom: 1rem; }
    .global-label { font-size: 0.82rem; font-weight: 600; color: var(--text-color-secondary); }
    .global-value { font-family: var(--font-display); font-size: 1.5rem; font-weight: 800; color: var(--nevadi-red); }

    /* Form */
    .form-section { background: var(--surface-card); border: 1px solid var(--surface-border); border-radius: 8px; padding: 1.25rem; margin-bottom: 1.25rem; }
    .form-row     { display: grid; grid-template-columns: 1fr 1fr; gap: 1rem; margin-bottom: 0.75rem; }
    .field        { display: flex; flex-direction: column; gap: 0.3rem; }
    .field label  { font-size: 0.8rem; font-weight: 600; color: var(--text-color-secondary); }

    /* Criterios */
    .criterios-titulo { font-size: 0.95rem; font-weight: 700; margin: 0 0 0.75rem; color: var(--text-color); }
    .criterios-list   { display: flex; flex-direction: column; gap: 0.75rem; margin-bottom: 1.25rem; }
    .criterio-card    { background: var(--surface-card); border: 1px solid var(--surface-border); border-radius: 8px; padding: 1rem; }
    .criterio-header  { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 0.6rem; }
    .criterio-nombre  { font-weight: 600; font-size: 0.88rem; }
    .criterio-cat     { font-size: 0.73rem; color: var(--text-color-secondary); margin-top: 2px; }
    .criterio-score-badge { width: 40px; height: 40px; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-weight: 800; font-size: 1.1rem; color: #fff; flex-shrink: 0; }
    .criterio-score-badge.score-good { background: #16A34A; }
    .criterio-score-badge.score-mid  { background: #D97706; }
    .criterio-score-badge.score-low  { background: #DC2626; }

    /* Score buttons */
    .score-buttons { display: flex; gap: 0.4rem; align-items: center; }
    .score-btn {
      width: 36px; height: 36px; border-radius: 50%;
      border: 2px solid var(--surface-border);
      background: var(--surface-ground);
      font-weight: 700; font-size: 0.9rem; cursor: pointer;
      transition: all 0.15s;
    }
    .score-btn:hover { border-color: var(--nevadi-red); }
    .score-btn.selected.score-btn-good { background: #16A34A; border-color: #16A34A; color: #fff; }
    .score-btn.selected.score-btn-mid  { background: #D97706; border-color: #D97706; color: #fff; }
    .score-btn.selected.score-btn-low  { background: #DC2626; border-color: #DC2626; color: #fff; }
    .score-label { font-size: 0.75rem; color: var(--text-color-secondary); margin-left: 0.5rem; }

    .submit-row { display: flex; gap: 0.75rem; align-items: center; justify-content: flex-end; padding-top: 0.75rem; border-top: 1px solid var(--surface-border); }
    .promedio-preview { font-size: 0.9rem; margin-right: auto; }
    .promedio-preview strong { color: var(--nevadi-red); }

    .empty-state { display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 1rem; padding: 3rem; color: #94A3B8; text-align: center; }
    .ciclo-warn  { display: flex; align-items: center; gap: 0.5rem; background: #FFF7ED; border: 1px solid #FED7AA; border-radius: 6px; padding: 0.5rem 0.75rem; font-size: 0.8rem; color: #9A3412; margin-bottom: 0.75rem; }
  `],
})
export class EvalDocenteComponent implements OnInit implements OnInit, OnDestroy {
  private destroy$ = new Subject<void>();
  private readonly api    = inject(ApiService);
  readonly ctx            = inject(ContextService);
  private readonly notify = inject(ApexNotificationService);
  private readonly export = inject(ExportService);

  profesores          = signal<Profesor[]>([]);
  loadingProfesores   = signal(false);
  readonly profesoresOpts = computed(() =>
    this.profesores().map(p => ({
      ...p,
      _nombre: p.persona
        ? `${p.persona.apellido_paterno} ${p.persona.apellido_materno ?? ''} ${p.persona.nombre}`.replace(/\s+/g, ' ').trim()
        : p.numero_empleado,
    }))
  );
  criterios        = signal<Criterio[]>([]);
  resumen          = signal<ResumenEvaluador[]>([]);
  evaluaciones     = signal<EvaluacionOut[]>([]);
  loadingResumen   = signal(false);
  loadingEvals     = signal(false);
  saving           = signal(false);

  selectedProfesor: Profesor | null = null;

  // Form
  formProfesor:   Profesor | null = null;
  formTipo:       string = '';
  formComentarios = '';
  criteriosForm   = signal<CriterioCalif[]>([]);

  readonly tiposEvaluador = [
    { label: 'Director',     value: 'DIRECTOR' },
    { label: 'Coordinador',  value: 'COORDINADOR' },
    { label: 'Par docente',  value: 'PAR' },
    { label: 'Autoevaluación', value: 'AUTO' },
  ];

  readonly evaluacionesFlat = computed(() =>
    this.evaluaciones().map(ev => ({
      ...ev,
      tipo_str:   this.tipoLabel(ev.tipo_evaluador),
      fecha_str:  ev.fecha_evaluacion ? new Date(ev.fecha_evaluacion).toLocaleDateString('es-MX') : '—',
      cal_str:    ev.calificacion_global !== null ? Number(ev.calificacion_global).toFixed(1) : '—',
    }))
  );
  readonly evaluacionesColumns: ColumnConfig[] = [
    { field: 'tipo_str',  header: 'Tipo evaluador' },
    { field: 'fecha_str', header: 'Fecha',        width: '120px' },
    { field: 'cal_str',   header: 'Calificación', width: '110px' },
    { field: 'estatus',   header: 'Estatus',      width: '100px' },
  ];

  promedioGlobal = computed(() => {
    if (!this.resumen().length) return 0;
    const total = this.resumen().reduce((s, r) => s + r.promedio_global, 0);
    return total / this.resumen().length;
  });

  promedioPreview = computed(() => {
    const items = this.criteriosForm();
    if (!items.length) return 0;
    const sum = items.reduce((s, it) => s + it.calificacion * (it.criterio.peso_porcentual / 100), 0);
    const totalPeso = items.reduce((s, it) => s + it.criterio.peso_porcentual, 0) / 100;
    return totalPeso > 0 ? (sum / totalPeso) : 0;
  });

  ngOnInit(): void {
    const plantelId = this.ctx.plantel()?.id;
    const params: Record<string, any> = {};
    if (plantelId) params['plantel_id'] = plantelId;
    this.loadingProfesores.set(true);
    this.api.get<{ data: Profesor[]; total: number }>('/profesores', params).subscribe({
      next: resp => { this.profesores.set(resp.data); this.loadingProfesores.set(false); },
      error: () => this.loadingProfesores.set(false),
    });
    this.api.get<Criterio[]>('/eval-docente/criterios').subscribe(c => {
      this.criterios.set(c);
      this.criteriosForm.set(c.map(cr => ({ criterio: cr, calificacion: 3, observacion: '' })));
    });
  }

  cargarResumen(): void {
    if (!this.selectedProfesor) { this.resumen.set([]); this.evaluaciones.set([]); return; }
    this.loadingResumen.set(true);
    const cicloId = this.ctx.ciclo()?.id;
    const params: Record<string, any> = {};
    if (cicloId) params['ciclo_id'] = cicloId;
    this.api.get<{ por_tipo: ResumenEvaluador[]; evaluaciones: EvaluacionOut[] }>(
      `/eval-docente/profesor/${this.selectedProfesor.id}/resumen`, params
    ).subscribe({
      next: data => {
        this.resumen.set(data.por_tipo || []);
        this.evaluaciones.set(data.evaluaciones || []);
        this.loadingResumen.set(false);
        this.loadingEvals.set(false);
      },
      error: () => { this.loadingResumen.set(false); this.loadingEvals.set(false); },
    });
  }

  guardar(enviar: boolean): void {
    if (!this.formProfesor || !this.formTipo) return;
    const ciclo = this.ctx.ciclo();
    if (!ciclo) { this.notify.warning('Ciclo requerido', 'Selecciona un ciclo escolar en la barra superior'); return; }

    this.saving.set(true);
    const currentUser = this.ctx.usuario();

    const payload = {
      profesor_id: this.formProfesor.id,
      ciclo_escolar_id: ciclo.id,
      evaluador_id: currentUser?.id || this.formProfesor.id,
      tipo_evaluador: this.formTipo,
      comentarios: this.formComentarios || null,
    };

    this.api.post<EvaluacionOut>('/eval-docente', payload).subscribe({
      next: (ev) => {
        const criterios = this.criteriosForm().map(it => ({
          criterio_id: it.criterio.id,
          calificacion: it.calificacion,
          observacion: it.observacion || null,
        }));
        this.api.post<any>(`/eval-docente/${ev.id}/criterios`, { criterios }).subscribe({
          next: () => {
            if (enviar) {
              this.api.patch<any>(`/eval-docente/${ev.id}/enviar`, {}).subscribe({
                next: () => { this._doneSaving('Evaluación enviada exitosamente'); },
                error: () => { this._doneSaving('Evaluación guardada (error al enviar)'); },
              });
            } else {
              this._doneSaving('Evaluación guardada como borrador');
            }
          },
          error: (e) => { this.saving.set(false); this.notify.error('Error', e.error?.detail || 'Error al guardar criterios'); },
        });
      },
      error: (e) => { this.saving.set(false); this.notify.error('Error', e.error?.detail || 'Error al crear evaluación'); },
    });
  }

  enviarEval(evalId: string): void {
    this.api.patch<any>(`/eval-docente/${evalId}/enviar`, {}).subscribe({
      next: () => {
        this.notify.success('Evaluación enviada');
        this.cargarResumen();
      },
    });
  }

  tipoLabel(tipo: string): string  { return TIPO_LABELS[tipo] || tipo; }
  estatusSev(est: string): TagSeverity { return ESTATUS_SEV[est] || 'secondary'; }

  scoreName(n: number): string {
    return ['', 'Insuficiente', 'Regular', 'Suficiente', 'Bueno', 'Excelente'][n] || '';
  }

  private _doneSaving(detail: string): void {
    this.saving.set(false);
    this.notify.success('Listo');
    this.formProfesor = null; this.formTipo = ''; this.formComentarios = '';
    this.criteriosForm.update(items => items.map(it => ({ ...it, calificacion: 3, observacion: '' })));
    if (this.selectedProfesor) this.cargarResumen();
  }

  private readonly exportCols: ExportColumn[] = [
    { field: '_docente',         header: 'Docente evaluado', format: v => v || '—' },
    { field: 'tipo_evaluador',   header: 'Tipo evaluador', format: v => TIPO_LABELS[v] || v },
    { field: 'fecha_evaluacion', header: 'Fecha', format: v => v ? new Date(v).toLocaleDateString('es-MX') : '' },
    { field: 'calificacion_global', header: 'Calificación', format: v => v !== null ? String(v) : '—' },
    { field: 'estatus',          header: 'Estatus' },
  ];

  private evaluacionesExport() {
    const docente = (this.selectedProfesor as any)?._nombre ?? '';
    return this.evaluaciones().map(e => ({ ...e, _docente: docente }));
  }
  exportCSV():  void { this.export.toCSV(this.evaluacionesExport(), this.exportCols, 'eval-docente'); }
  exportXLSX(): void { this.export.toXLSX(this.evaluacionesExport(), this.exportCols, 'Eval Docente', 'eval-docente'); }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
