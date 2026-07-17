/**
 * FASE 2 & FASE 24 — Evaluaciones (Tests/Exams) + Interactive Grid APEX-style
 * Agenda tab: read-only grid of evaluations
 * Libreta tab: inline editing of grades (preserved, complex pattern)
 */
import { Component, OnDestroy, inject, OnInit, signal, ChangeDetectionStrategy} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject, takeUntil } from 'rxjs';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { SelectModule } from 'primeng/select';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { TooltipModule } from 'primeng/tooltip';
import { MessageModule } from 'primeng/message';
import { DatePickerModule } from 'primeng/datepicker';

import { ApiService } from '../../core/services/api.service';
import { ContextService } from '../../core/services/context.service';
import { ExportService, ExportColumn } from '../../core/services/export.service';
import { InteractiveGridComponent, ColumnConfig } from '../../shared/components/interactive-grid/interactive-grid.component';
import { grupoLabel } from '../../core/models';
import { AdesFormatDirective } from '../../shared/directives/ades-format.directive';
import { ApexNotificationService } from 'apex-component-library';

type TagSeverity = 'success' | 'info' | 'warn' | 'danger' | 'secondary' | 'contrast' | undefined;

interface Evaluacion {
  id: string;
  nombre_evaluacion: string;
  nombre_grupo: string;
  nombre_materia: string;
  nombre_periodo: string;
  numero_periodo: number;
  fecha_evaluacion: string;
  tipo_evaluacion: string;
  puntaje_maximo: number;
  total_calificados: number;
  promedio: number | null;
  aprobados: number;
  reprobados: number;
}

interface AlumnoCalif {
  estudiante_id: string;
  nombre_alumno: string;
  calificacion: number | null;
  es_acreditado: boolean | null;
  comentarios: string | null;
  calificacion_id: string | null;
  _editada?: boolean;
}

const TIPO_SEV: Record<string, TagSeverity> = {
  ORDINARIO:      'info',
  FINAL:          'success',
  EXTRAORDINARIO: 'danger',
  DIAGNOSTICO:    'secondary',
};

@Component({
  selector: 'app-evaluaciones',
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
  imports: [
    AdesFormatDirective,
    CommonModule, FormsModule,
    ButtonModule, DialogModule, SelectModule, InputTextModule, InputNumberModule,
    TooltipModule, MessageModule, DatePickerModule,
    InteractiveGridComponent,
  ],
  template: `
    <div class="page-header">
      <div>
        <h2 class="page-title">Evaluaciones y Exámenes</h2>
        <p class="page-subtitle">Programación y registro de calificaciones por evaluación</p>
      </div>
      <div class="page-actions">
        <p-button icon="pi pi-plus" label="Nueva evaluación" (onClick)="abrirNueva()" />
      </div>
    </div>

    <!-- Tabs -->
    <div class="tabs">
      <button class="tab-btn" [class.active]="activeTab === 'agenda'" (click)="activeTab='agenda'">
        <i class="pi pi-calendar"></i> Agenda
      </button>
      <button class="tab-btn" [class.active]="activeTab === 'libreta'" (click)="activeTab='libreta'; selEval = null">
        <i class="pi pi-book"></i> Libreta de calificaciones
      </button>
    </div>

    <!-- TAB: Agenda de evaluaciones (Interactive Grid APEX-style) -->
    @if (activeTab === 'agenda') {
      <app-interactive-grid
        [data]="evaluacionesDatos()"
        [columns]="columnasAgenda"
        [loading]="loading()"
        (rowSelected)="abrirLibreta($event)"
      />
    }

    <!-- TAB: Libreta de calificaciones (Preserved inline-edit pattern) -->
    @if (activeTab === 'libreta') {
      @if (!selEval) {
        <div class="empty-msg" style="padding:2rem; text-align:center">
          <i class="pi pi-info-circle" style="font-size:2rem; color:var(--primary-color); display:block; margin-bottom:.5rem"></i>
          Selecciona una evaluación desde la Agenda usando el ícono para ver su libreta.
        </div>
      } @else {
        <div class="libreta-header">
          <div>
            <h3>{{ selEval.nombre_evaluacion }}</h3>
            <span class="libreta-meta">
              {{ selEval.nombre_grupo }} &bull; {{ selEval.nombre_materia }} &bull;
              {{ selEval.nombre_periodo }}
            </span>
          </div>
          <div class="page-actions">
            <p-button icon="pi pi-download" label="CSV" severity="secondary" [text]="true"
                      (onClick)="exportLibretaCSV()" />
            <p-button icon="pi pi-file-excel" label="Excel" severity="secondary" [text]="true"
                      (onClick)="exportLibretaXLSX()" />
            <p-button icon="pi pi-save" label="Guardar" (onClick)="guardarCalificaciones()"
                      [loading]="saving()" [disabled]="!hayEdiciones()" />
          </div>
        </div>

        <div style="overflow-x:auto">
          <table class="libreta-table">
            <thead>
              <tr>
                <th>#</th>
                <th>Alumno</th>
                <th>Calificación (/ {{ selEval.puntaje_maximo }})</th>
                <th>Estado</th>
                <th style="width:220px">Comentarios</th>
              </tr>
            </thead>
            <tbody>
              @for (a of alumnos(); track a.estudiante_id) {
                <tr [class.row-editada]="a._editada">
                  <td style="color:var(--text-color-secondary); font-size:.8rem">{{ $index + 1 }}</td>
                  <td><strong>{{ a.nombre_alumno }}</strong></td>
                  <td>
                    <input type="number" [(ngModel)]="a.calificacion"
                      [attr.aria-label]="'Calificación de ' + a.nombre_alumno"
                      [min]="0" [max]="selEval.puntaje_maximo" step="0.1"
                      (ngModelChange)="onCalifChange(a)"
                      class="cal-input" />
                  </td>
                  <td>
                    @if (a.calificacion != null) {
                      <span [class]="a.calificacion >= 6 ? 'estado-acreditado' : 'estado-reprobado'">
                        {{ a.calificacion >= 6 ? 'Acreditado' : 'No acreditado' }}
                      </span>
                    }
                  </td>
                  <td>
                    <input type="text" [(ngModel)]="a.comentarios"
                      placeholder="Opcional..."
                      (input)="a._editada = true"
                      class="comentarios-input" [attr.aria-label]="'Comentarios de ' + a.nombre_alumno"/>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    }

    <!-- Dialog nueva evaluación -->
    <p-dialog header="Nueva evaluación" [visible]="showDialog()" (visibleChange)="showDialog.set($event)" [modal]="true" [style]="{width:'520px'}">
      <div class="form-grid">
        <div class="form-field full">
          <label for="ev-nombre">Nombre *</label>
          <input pInputText id="ev-nombre" [(ngModel)]="form.nombre_evaluacion"
                 placeholder="Ej. Examen Bimestral 1" style="width:100%"/>
        </div>
        <div class="form-field">
          <label>Nivel *</label>
          <p-select [options]="nivelesOpts()" [(ngModel)]="form._nivelId"
                    optionLabel="nombre_nivel" optionValue="id"
                    placeholder="Nivel…" styleClass="w-full"
                    (onChange)="onNivelChange()" ariaLabel="Nivel"/>
        </div>
        <div class="form-field">
          <label>Grado *</label>
          <p-select [options]="gradosOpts()" [(ngModel)]="form._gradoId"
                    optionLabel="nombre_grado" optionValue="id"
                    placeholder="Grado…" styleClass="w-full"
                    [disabled]="!form._nivelId"
                    (onChange)="onGradoChange()" ariaLabel="Grado"/>
        </div>
        <div class="form-field">
          <label>Grupo *</label>
          <p-select [options]="grupos()" [(ngModel)]="form.grupo_id"
                    optionLabel="label" optionValue="value"
                    placeholder="Seleccionar..." styleClass="w-full"
                    [disabled]="!form._gradoId"
                    (onChange)="onGrupoChange()"
 [filter]="true" filterPlaceholder="Buscar..." ariaLabel="Grupo" />
        </div>
        <div class="form-field">
          <label>Materia *</label>
          <p-select [options]="materias()" [(ngModel)]="form.materia_id"
                    optionLabel="label" optionValue="value"
                    placeholder="Seleccionar..." styleClass="w-full" 
 [filter]="true" filterPlaceholder="Buscar..." ariaLabel="Materia" />
        </div>
        <div class="form-field">
          <label>Periodo *</label>
          <p-select [options]="periodos()" [(ngModel)]="form.periodo_evaluacion_id"
                    optionLabel="label" optionValue="value"
                    placeholder="Seleccionar..." styleClass="w-full" 
 [filter]="true" filterPlaceholder="Buscar..." ariaLabel="Periodo" />
        </div>
        <div class="form-field">
          <label>Tipo</label>
          <p-select [options]="types" [(ngModel)]="form.tipo_evaluacion"
                    optionLabel="label" optionValue="value" styleClass="w-full" 
 [filter]="true" filterPlaceholder="Buscar..." ariaLabel="Tipo" />
        </div>
        <div class="form-field">
          <label>Fecha *</label>
          <p-datepicker [(ngModel)]="form.fecha_evaluacion" dateFormat="dd/mm/yy" [showIcon]="true"
                        placeholder="DD/MM/AAAA" [style]="{width:'100%'}" [inputStyle]="{width:'100%'}" ariaLabel="Fecha"/>
        </div>
        <div class="form-field">
          <label>Puntaje máximo</label>
          <p-inputNumber [(ngModel)]="form.puntaje_maximo" [min]="0" [max]="100"
                         [minFractionDigits]="1" inputStyleClass="w-full" />
        </div>
      </div>
      <ng-template pTemplate="footer">
        <p-button label="Cancelar" severity="secondary" [text]="true" (onClick)="showDialog.set(false)" />
        <p-button label="Crear evaluación" icon="pi pi-check"
                  (onClick)="crearEvaluacion()" [loading]="saving()" />
      </ng-template>
    </p-dialog>
  `,
  styles: [`
    .page-header   { display:flex; justify-content:space-between; align-items:flex-start; margin-bottom:1.25rem; }
    .page-title    { font-size:1.25rem; font-weight:700; margin:0 0 .2rem; }
    .page-subtitle { color:var(--text-color-secondary); font-size:.82rem; margin:0; }
    .page-actions  { display:flex; gap:.5rem; align-items:center; }

    .tabs { display:flex; gap:.25rem; margin-bottom:1rem; border-bottom:2px solid var(--surface-200); padding-bottom:.25rem; }
    .tab-btn { background:none; border:none; padding:.4rem .85rem; cursor:pointer; border-radius:4px 4px 0 0;
      font-size:.85rem; color:var(--text-color-secondary); display:flex; align-items:center; gap:.35rem;
      transition:background .12s; }
    .tab-btn:hover { background:var(--surface-100); }
    .tab-btn.active { color:var(--primary-color); font-weight:600; border-bottom:2px solid var(--primary-color); }

    .libreta-header { display:flex; justify-content:space-between; align-items:center; margin-bottom:1rem;
      padding:.75rem 1rem; background:var(--surface-50); border-radius:8px; }
    .libreta-meta   { font-size:.8rem; color:var(--text-color-secondary); display:flex; gap:.5rem; align-items:center; }

    .libreta-table { width:100%; border-collapse:collapse; font-size:.85rem; }
    .libreta-table thead { background:var(--surface-100); }
    .libreta-table th { padding:.6rem; text-align:left; font-weight:600; border-bottom:1px solid var(--surface-border); }
    .libreta-table td { padding:.4rem .6rem; border-bottom:1px solid var(--surface-border); }
    .libreta-table tbody tr:hover { background:var(--surface-50); }
    .libreta-table tbody tr.row-editada { background:var(--yellow-50) !important; }

    .cal-input { width:80px; padding:.3rem; border:1px solid var(--surface-border); border-radius:3px; text-align:center; font-weight:600; }
    .comentarios-input { width:100%; padding:.3rem; border:1px solid var(--surface-border); border-radius:3px; font-size:.8rem; }

    .estado-acreditado { color:var(--green-600); font-weight:600; }
    .estado-reprobado { color:var(--red-600); font-weight:600; }

    .form-grid  { display:grid; grid-template-columns:1fr 1fr; gap:.85rem; }
    .form-field { display:flex; flex-direction:column; gap:.3rem; font-size:.875rem; }
    .form-field.full { grid-column:1/-1; }
    label       { font-weight:500; font-size:.82rem; }
    .empty-msg  { text-align:center; padding:2rem; color:var(--text-color-secondary); }
  `],
})
export class EvaluacionesComponent implements OnInit, OnDestroy {
  private destroy$ = new Subject<void>();
  private readonly api      = inject(ApiService);
  readonly ctx              = inject(ContextService);
  private readonly exporter = inject(ExportService);
  private readonly notify   = inject(ApexNotificationService);

  // ── Cascada Nivel → Grado → Grupo (en el diálogo de nueva evaluación) ──
  nivelesOpts = signal<any[]>([]);
  gradosOpts  = signal<any[]>([]);

  evaluaciones   = signal<Evaluacion[]>([]);
  evaluacionesDatos = signal<any[]>([]);
  alumnos        = signal<AlumnoCalif[]>([]);
  grupos         = signal<{ label: string; value: string }[]>([]);
  materias       = signal<{ label: string; value: string }[]>([]);
  periodos       = signal<{ label: string; value: string }[]>([]);

  loading        = signal(false);
  saving         = signal(false);
  showDialog     = signal(false);
  activeTab      = 'agenda';
  selEval: Evaluacion | null = null;

  types = [
    { label: 'Ordinario',      value: 'ORDINARIO' },
    { label: 'Final',          value: 'FINAL' },
    { label: 'Extraordinario', value: 'EXTRAORDINARIO' },
    { label: 'Diagnóstico',    value: 'DIAGNOSTICO' },
  ];

  // Spec: spec/modules/fase-24-interactive-grid/specification.md § Pre-configured Schemas
  columnasAgenda: ColumnConfig[] = [
    { field: 'fecha_evaluacion', header: 'Fecha', sortable: true, filterable: false, width: '110px' },
    { field: 'tipo_evaluacion', header: 'Tipo', sortable: true, filterable: true, width: '120px' },
    { field: 'nombre_evaluacion', header: 'Evaluación', sortable: true, filterable: true, width: '220px' },
    { field: 'nombre_grupo', header: 'Grupo', sortable: true, filterable: true, width: '100px' },
    { field: 'nombre_materia', header: 'Materia', sortable: true, filterable: true, width: '150px' },
    { field: 'nombre_periodo', header: 'Período', sortable: true, filterable: true, width: '110px' },
    { field: 'promedio', header: 'Promedio', sortable: true, filterable: false, width: '90px' },
    { field: 'total_calificados', header: 'Calificados', sortable: false, filterable: false, width: '110px' },
  ];

  form = this.emptyForm();

  private readonly libretaCols: ExportColumn[] = [
    { field: 'nombre_alumno', header: 'Alumno' },
    { field: 'calificacion',  header: 'Calificación', format: (v: number | null) => v != null ? Number(v).toFixed(1) : '' },
    { field: 'es_acreditado', header: 'Estado', format: (v: boolean | null) => v === true ? 'Acreditado' : v === false ? 'No acreditado' : '' },
    { field: 'comentarios',   header: 'Comentarios', format: (v: string | null) => v ?? '' },
  ];

  ngOnInit(): void {
    this._initNiveles();
    this.cargar();
  }

  private _initNiveles(): void {
    const plantel = this.ctx.plantel();
    if (!plantel) return;
    this.api.get<any[]>(`/planteles/${plantel.id}/niveles`).pipe(takeUntil(this.destroy$)).subscribe({
      next: list => {
        this.nivelesOpts.set(list);
        const ctxNivel = this.ctx.nivel();
        if (ctxNivel && list.some((n: any) => n.id === ctxNivel.id)) {
          this.form._nivelId = ctxNivel.id;
          this._loadGrados(ctxNivel.id);
        }
      },
    });
  }

  private _loadGrados(nivelId: string): void {
    const plantelId = this.ctx.plantel()?.id;
    this.api.get<any[]>('/catalogs/grados', { nivel_id: nivelId, plantel_id: plantelId || undefined }).pipe(takeUntil(this.destroy$)).subscribe({
      next: list => this.gradosOpts.set(list),
    });
  }

  onNivelChange(): void {
    this.form._gradoId = '';
    this.form.grupo_id = '';
    this.form.materia_id = '';
    this.gradosOpts.set([]); this.grupos.set([]); this.materias.set([]);
    if (this.form._nivelId) this._loadGrados(this.form._nivelId);
  }

  onGradoChange(): void {
    this.form.grupo_id   = '';
    this.form.materia_id = '';
    this.grupos.set([]); this.materias.set([]);
    const plantel = this.ctx.plantel();
    if (plantel && this.form._gradoId) {
      this.api.get<any[]>('/grupos', { plantel_id: plantel.id, grado_id: this.form._gradoId }).pipe(takeUntil(this.destroy$)).subscribe({
        next: list => this.grupos.set(list.map((g: any) => ({
          label: grupoLabel(g) || `${g.nombre_grupo} — ${g.nombre_grado ?? ''}`,
          value: g.id,
        }))),
      });
    }
  }

  cargar(): void {
    this.loading.set(true);
    const ciclo = this.ctx.ciclo();
    const params: Record<string, string> = {};
    if (ciclo) params['ciclo_id'] = ciclo.id;
    this.api.get<Evaluacion[]>('/evaluaciones', params).pipe(takeUntil(this.destroy$)).subscribe({
      next: (r) => {
        this.evaluaciones.set(r);
        this.evaluacionesDatos.set(r.map(e => ({
          id: e.id,
          fecha_evaluacion: e.fecha_evaluacion,
          tipo_evaluacion: e.tipo_evaluacion,
          nombre_evaluacion: e.nombre_evaluacion,
          nombre_grupo: e.nombre_grupo,
          nombre_materia: e.nombre_materia,
          nombre_periodo: e.nombre_periodo,
          promedio: e.promedio != null ? Number(e.promedio).toFixed(1) : '—',
          total_calificados: `${e.total_calificados}${e.reprobados > 0 ? ` (${e.reprobados} rep.)` : ''}`,
          _original: e,
        })));
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  onGrupoChange(): void {
    if (!this.form.grupo_id) return;
    this.api.get<any[]>('/materias', { grupo_id: this.form.grupo_id }).pipe(takeUntil(this.destroy$)).subscribe(list => {
      this.materias.set(list.map(m => ({ label: m.nombre_materia, value: m.id })));
    });
    const ciclo = this.ctx.ciclo();
    if (ciclo) {
      this.api.get<any[]>('/calificaciones/periodos', { ciclo_id: ciclo.id }).pipe(takeUntil(this.destroy$)).subscribe(list => {
        this.periodos.set(list.map(p => ({ label: p.nombre_periodo, value: p.id })));
      });
    }
  }

  abrirLibreta(row: any): void {
    const eval_data = row._original || this.evaluaciones().find(e => e.id === row.id);
    if (!eval_data) return;
    this.selEval = eval_data;
    this.activeTab = 'libreta';
    this.api.get<AlumnoCalif[]>(`/evaluaciones/${eval_data.id}/calificaciones`).pipe(takeUntil(this.destroy$)).subscribe({
      next: (r) => {
        this.alumnos.set(r.map(a => ({ ...a, _editada: false })));
      },
    });
  }

  onCalifChange(a: AlumnoCalif): void { a._editada = true; }
  hayEdiciones(): boolean { return this.alumnos().some(a => a._editada); }

  guardarCalificaciones(): void {
    if (!this.selEval) return;
    const editadas = this.alumnos().filter(a => a._editada && a.calificacion != null);
    if (!editadas.length) return;

    const puntajeMax = this.selEval.puntaje_maximo;
    const fueraDeRango = editadas.find(a => a.calificacion! < 0 || a.calificacion! > puntajeMax);
    if (fueraDeRango) {
      this.notify.warning('Calificación fuera de rango',
        `${fueraDeRango.nombre_alumno}: la calificación debe estar entre 0 y ${puntajeMax}`);
      return;
    }

    this.saving.set(true);
    const ciclo = this.ctx.ciclo();
    const payload: any = {
      calificaciones: editadas.map(a => ({
        estudiante_id: a.estudiante_id,
        calificacion:  a.calificacion,
        comentarios:   a.comentarios,
      }))
    };
    if (ciclo) {
      payload['ciclo_id'] = ciclo.id;
    }

    this.api.post(`/evaluaciones/${this.selEval!.id}/calificaciones/bulk`, payload).pipe(takeUntil(this.destroy$)).subscribe({
      next: () => {
        this.saving.set(false);
        this.alumnos.update(list => list.map(a => ({ ...a, _editada: false })));
      },
      error: (e: any) => { this.saving.set(false); this.notify.error('Error', e?.error?.detail ?? 'No se pudieron guardar las calificaciones'); },
    });
  }

  exportLibretaCSV(): void {
    if (!this.selEval) return;
    const header = ['#', 'Alumno', `Calificación / ${this.selEval.puntaje_maximo}`, 'Estado', 'Comentarios'].join(',');
    const rows = this.alumnos().map((a, i) => [
      i + 1,
      a.nombre_alumno,
      a.calificacion ?? '',
      a.calificacion != null ? (a.calificacion >= 6 ? 'Acreditado' : 'No acreditado') : '',
      a.comentarios ?? '',
    ].join(','));
    const csv = [header, ...rows].join('\n');
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `libreta_${this.selEval.nombre_evaluacion}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  }

  exportLibretaXLSX(): void {
    this.exporter.toXLSX(this.alumnos(), this.libretaCols, 'Libreta', 'libreta');
  }

  abrirNueva(): void {
    this.showDialog.set(true);
    this.form = this.emptyForm();
  }

  crearEvaluacion(): void {
    if (!this.form.nombre_evaluacion || !this.form.grupo_id || !this.form.materia_id || !this.form.periodo_evaluacion_id || !this.form.fecha_evaluacion) {
      return;
    }
    this.saving.set(true);
    const { _nivelId, _gradoId, ...rest } = this.form;
    const payload: Record<string, unknown> = {
      ...rest,
      fecha_evaluacion: this.form.fecha_evaluacion!.toISOString().substring(0, 10),
    };
    const ciclo = this.ctx.ciclo();
    if (ciclo) {
      payload['ciclo_id'] = ciclo.id;
    }
    this.api.post('/evaluaciones', payload).pipe(takeUntil(this.destroy$)).subscribe({
      next: () => {
        this.saving.set(false);
        this.showDialog.set(false);
        this.cargar();
      },
      error: () => this.saving.set(false),
    });
  }

  private emptyForm() {
    return {
      nombre_evaluacion: '',
      _nivelId: '',
      _gradoId: '',
      grupo_id: '',
      materia_id: '',
      periodo_evaluacion_id: '',
      tipo_evaluacion: 'ORDINARIO',
      fecha_evaluacion: null as Date | null,
      puntaje_maximo: 10,
    };
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
