import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { DialogModule } from 'primeng/dialog';
import { SelectModule } from 'primeng/select';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { TooltipModule } from 'primeng/tooltip';
import { MessageModule } from 'primeng/message';

import { ApiService } from '../../core/services/api.service';
import { ContextService } from '../../core/services/context.service';
import { ExportService, ExportColumn } from '../../core/services/export.service';

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
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    ButtonModule, TableModule, TagModule, DialogModule,
    SelectModule, InputTextModule, InputNumberModule,
    TooltipModule, MessageModule,
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

    <!-- TAB: Agenda de evaluaciones -->
    @if (activeTab === 'agenda') {
      <p-table [value]="evaluaciones()" [loading]="loading()" dataKey="id"
               styleClass="p-datatable-sm p-datatable-striped"
               [paginator]="true" [rows]="15">
        <ng-template pTemplate="header">
          <tr>
            <th pSortableColumn="fecha_evaluacion">Fecha <p-sortIcon field="fecha_evaluacion" /></th>
            <th pSortableColumn="tipo_evaluacion">Tipo <p-sortIcon field="tipo_evaluacion" /></th>
            <th>Nombre</th>
            <th>Grupo</th>
            <th pSortableColumn="nombre_materia">Materia <p-sortIcon field="nombre_materia" /></th>
            <th>Periodo</th>
            <th>Promedio</th>
            <th>Calificados</th>
            <th>Acciones</th>
          </tr>
        </ng-template>
        <ng-template pTemplate="body" let-e>
          <tr>
            <td>{{ e.fecha_evaluacion | date:'dd/MM/yy' }}</td>
            <td><p-tag [value]="e.tipo_evaluacion" [severity]="tipoSev(e.tipo_evaluacion)" /></td>
            <td><strong>{{ e.nombre_evaluacion }}</strong></td>
            <td>{{ e.nombre_grupo }}</td>
            <td>{{ e.nombre_materia }}</td>
            <td>{{ e.nombre_periodo }}</td>
            <td>
              @if (e.promedio != null) {
                <span [class]="promClass(e.promedio)">{{ e.promedio | number:'1.1-1' }}</span>
              } @else { <span class="text-muted">—</span> }
            </td>
            <td>
              <span class="acuse-badge">{{ e.total_calificados }}</span>
              @if (e.reprobados > 0) {
                <span class="rep-badge" style="margin-left:.3rem">{{ e.reprobados }} rep.</span>
              }
            </td>
            <td>
              <p-button icon="pi pi-book" size="small" [text]="true"
                        pTooltip="Abrir libreta" (onClick)="abrirLibreta(e)" />
            </td>
          </tr>
        </ng-template>
        <ng-template pTemplate="emptymessage">
          <tr><td colspan="9" class="empty-msg">No hay evaluaciones registradas para el grupo/ciclo seleccionado.</td></tr>
        </ng-template>
      </p-table>
    }

    <!-- TAB: Libreta de calificaciones -->
    @if (activeTab === 'libreta') {
      @if (!selEval) {
        <div class="empty-msg" style="padding:2rem; text-align:center">
          <i class="pi pi-info-circle" style="font-size:2rem; color:var(--primary-color); display:block; margin-bottom:.5rem"></i>
          Selecciona una evaluación desde la Agenda usando el ícono
          <i class="pi pi-book"></i> para ver su libreta.
        </div>
      } @else {
        <div class="libreta-header">
          <div>
            <h3>{{ selEval.nombre_evaluacion }}</h3>
            <span class="libreta-meta">
              {{ selEval.nombre_grupo }} &bull; {{ selEval.nombre_materia }} &bull;
              {{ selEval.nombre_periodo }} &bull;
              <p-tag [value]="selEval.tipo_evaluacion" [severity]="tipoSev(selEval.tipo_evaluacion)" />
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

        <p-table [value]="alumnos()" [loading]="loadingAlumnos()" dataKey="estudiante_id"
                 styleClass="p-datatable-sm p-datatable-striped" [paginator]="true" [rows]="30">
          <ng-template pTemplate="header">
            <tr>
              <th>#</th>
              <th pSortableColumn="nombre_alumno">Alumno <p-sortIcon field="nombre_alumno" /></th>
              <th style="width:160px">Calificación (/ {{ selEval.puntaje_maximo }})</th>
              <th>Estado</th>
              <th style="width:220px">Comentarios</th>
            </tr>
          </ng-template>
          <ng-template pTemplate="body" let-a let-i="rowIndex">
            <tr [class.row-editada]="a._editada">
              <td style="color:var(--text-color-secondary); font-size:.8rem">{{ i + 1 }}</td>
              <td><strong>{{ a.nombre_alumno }}</strong></td>
              <td>
                <p-inputNumber
                  [(ngModel)]="a.calificacion"
                  [min]="0" [max]="selEval.puntaje_maximo"
                  [minFractionDigits]="1" [maxFractionDigits]="2"
                  inputStyleClass="cal-input"
                  (ngModelChange)="onCalifChange(a)"
                />
              </td>
              <td>
                @if (a.calificacion != null) {
                  <p-tag
                    [value]="a.calificacion >= 6 ? 'Acreditado' : 'No acreditado'"
                    [severity]="a.calificacion >= 6 ? 'success' : 'danger'" />
                }
              </td>
              <td>
                <input pInputText [(ngModel)]="a.comentarios"
                       placeholder="Opcional..."
                       style="width:100%; font-size:.82rem; padding:.25rem .5rem"
                       (input)="a._editada = true" />
              </td>
            </tr>
          </ng-template>
        </p-table>
      }
    }

    <!-- Dialog nueva evaluación -->
    <p-dialog header="Nueva evaluación" [(visible)]="showDialog" [modal]="true"
              [style]="{width:'520px'}" [closable]="true">
      <div class="form-grid">
        <div class="form-field full">
          <label>Nombre *</label>
          <input pInputText [(ngModel)]="form.nombre_evaluacion"
                 placeholder="Ej. Examen Bimestral 1" style="width:100%" />
        </div>
        <div class="form-field">
          <label>Grupo *</label>
          <p-select [options]="grupos()" [(ngModel)]="form.grupo_id"
                    optionLabel="label" optionValue="value"
                    placeholder="Seleccionar..." styleClass="w-full"
                    (onChange)="onGrupoChange()" />
        </div>
        <div class="form-field">
          <label>Materia *</label>
          <p-select [options]="materias()" [(ngModel)]="form.materia_id"
                    optionLabel="label" optionValue="value"
                    placeholder="Seleccionar..." styleClass="w-full" />
        </div>
        <div class="form-field">
          <label>Periodo *</label>
          <p-select [options]="periodos()" [(ngModel)]="form.periodo_evaluacion_id"
                    optionLabel="label" optionValue="value"
                    placeholder="Seleccionar..." styleClass="w-full" />
        </div>
        <div class="form-field">
          <label>Tipo</label>
          <p-select [options]="tipos" [(ngModel)]="form.tipo_evaluacion"
                    optionLabel="label" optionValue="value" styleClass="w-full" />
        </div>
        <div class="form-field">
          <label>Fecha *</label>
          <input pInputText type="date" [(ngModel)]="form.fecha_evaluacion" style="width:100%" />
        </div>
        <div class="form-field">
          <label>Puntaje máximo</label>
          <p-inputNumber [(ngModel)]="form.puntaje_maximo" [min]="0" [max]="100"
                         [minFractionDigits]="1" inputStyleClass="w-full" />
        </div>
      </div>
      <ng-template pTemplate="footer">
        <p-button label="Cancelar" severity="secondary" [text]="true" (onClick)="showDialog = false" />
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

    .acuse-badge { background:var(--surface-200); border-radius:12px; font-size:.75rem;
      padding:0 .5rem; height:1.4rem; display:inline-flex; align-items:center; }
    .rep-badge   { background:var(--red-400); color:#fff; border-radius:12px; font-size:.72rem;
      font-weight:700; padding:0 .45rem; height:18px; display:inline-flex; align-items:center; }

    .prom-ok   { color:var(--green-600); font-weight:600; }
    .prom-warn { color:var(--yellow-700); font-weight:600; }
    .prom-bad  { color:var(--red-600); font-weight:600; }
    .text-muted { color:var(--text-color-secondary); }

    :host ::ng-deep .cal-input { width: 80px; text-align:center; font-weight:600; }
    .row-editada td { background: var(--yellow-50) !important; }

    .form-grid  { display:grid; grid-template-columns:1fr 1fr; gap:.85rem; }
    .form-field { display:flex; flex-direction:column; gap:.3rem; font-size:.875rem; }
    .form-field.full { grid-column:1/-1; }
    label       { font-weight:500; font-size:.82rem; }
    .empty-msg  { text-align:center; padding:2rem; color:var(--text-color-secondary); }
  `],
})
export class EvaluacionesComponent implements OnInit {
  private readonly api      = inject(ApiService);
  readonly ctx              = inject(ContextService);
  private readonly exporter = inject(ExportService);

  evaluaciones   = signal<Evaluacion[]>([]);
  alumnos        = signal<AlumnoCalif[]>([]);
  grupos         = signal<{ label: string; value: string }[]>([]);
  materias       = signal<{ label: string; value: string }[]>([]);
  periodos       = signal<{ label: string; value: string }[]>([]);

  loading        = signal(false);
  loadingAlumnos = signal(false);
  saving         = signal(false);
  showDialog     = false;
  activeTab      = 'agenda';
  selEval: Evaluacion | null = null;

  tipos = [
    { label: 'Ordinario',      value: 'ORDINARIO' },
    { label: 'Final',          value: 'FINAL' },
    { label: 'Extraordinario', value: 'EXTRAORDINARIO' },
    { label: 'Diagnóstico',    value: 'DIAGNOSTICO' },
  ];

  form = this.emptyForm();

  private readonly libretaCols: ExportColumn[] = [
    { field: 'nombre_alumno', header: 'Alumno' },
    { field: 'calificacion',  header: 'Calificación', format: (v: number | null) => v != null ? Number(v).toFixed(1) : '' },
    { field: 'es_acreditado', header: 'Estado', format: (v: boolean | null) => v === true ? 'Acreditado' : v === false ? 'No acreditado' : '' },
    { field: 'comentarios',   header: 'Comentarios', format: (v: string | null) => v ?? '' },
  ];

  ngOnInit(): void {
    this.cargarGrupos();
    this.cargar();
  }

  cargar(): void {
    this.loading.set(true);
    const ciclo = this.ctx.ciclo();
    const params: Record<string, string> = {};
    if (ciclo) params['ciclo_id'] = ciclo.id;
    this.api.get<Evaluacion[]>('/evaluaciones', params).subscribe({
      next: (r) => { this.evaluaciones.set(r); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }

  cargarGrupos(): void {
    const plantel = this.ctx.plantel();
    const ciclo   = this.ctx.ciclo();
    const params: Record<string, string> = {};
    if (plantel) params['plantel_id'] = plantel.id;
    if (ciclo)   params['ciclo_id']   = ciclo.id;
    this.api.get<any[]>('/grupos', params).subscribe(list => {
      this.grupos.set(list.map(g => ({ label: `${g.nombre_grupo} — ${g.nombre_grado ?? ''}`, value: g.id })));
    });
  }

  onGrupoChange(): void {
    if (!this.form.grupo_id) return;
    this.api.get<any[]>('/materias', { grupo_id: this.form.grupo_id }).subscribe(list => {
      this.materias.set(list.map(m => ({ label: m.nombre_materia, value: m.id })));
    });
    const ciclo = this.ctx.ciclo();
    if (ciclo) {
      this.api.get<any[]>('/calificaciones/periodos', { ciclo_id: ciclo.id }).subscribe(list => {
        this.periodos.set(list.map(p => ({ label: p.nombre_periodo, value: p.id })));
      });
    }
  }

  abrirLibreta(e: Evaluacion): void {
    this.selEval = e;
    this.activeTab = 'libreta';
    this.loadingAlumnos.set(true);
    this.api.get<AlumnoCalif[]>(`/evaluaciones/${e.id}/calificaciones`).subscribe({
      next: (r) => { this.alumnos.set(r.map(a => ({ ...a, _editada: false }))); this.loadingAlumnos.set(false); },
      error: () => this.loadingAlumnos.set(false),
    });
  }

  onCalifChange(a: AlumnoCalif): void { a._editada = true; }
  hayEdiciones(): boolean { return this.alumnos().some(a => a._editada); }

  guardarCalificaciones(): void {
    if (!this.selEval) return;
    const editadas = this.alumnos().filter(a => a._editada && a.calificacion != null);
    if (!editadas.length) return;

    this.saving.set(true);
    const payload = { calificaciones: editadas.map(a => ({
      estudiante_id: a.estudiante_id,
      calificacion:  a.calificacion,
      comentarios:   a.comentarios,
    })) };

    this.api.post<{ ok: boolean; guardadas: number }>(
      `/evaluaciones/${this.selEval!.id}/calificaciones/bulk`, payload
    ).subscribe({
      next: () => {
        this.saving.set(false);
        this.alumnos.update(list => list.map(a => ({ ...a, _editada: false })));
      },
      error: () => this.saving.set(false),
    });
  }

  abrirNueva(): void { this.form = this.emptyForm(); this.showDialog = true; }

  crearEvaluacion(): void {
    if (!this.form.nombre_evaluacion || !this.form.grupo_id || !this.form.materia_id
        || !this.form.periodo_evaluacion_id || !this.form.fecha_evaluacion) return;
    this.saving.set(true);
    this.api.post('/evaluaciones', this.form).subscribe({
      next: () => { this.showDialog = false; this.saving.set(false); this.cargar(); },
      error: () => this.saving.set(false),
    });
  }

  tipoSev(tipo: string): TagSeverity { return TIPO_SEV[tipo] ?? 'secondary'; }
  promClass(v: number): string { return v >= 8 ? 'prom-ok' : v >= 6 ? 'prom-warn' : 'prom-bad'; }

  exportLibretaCSV():  void { this.exporter.toCSV(this.alumnos(), this.libretaCols, 'calificaciones-evaluacion'); }
  exportLibretaXLSX(): void { this.exporter.toXLSX(this.alumnos(), this.libretaCols, 'Calificaciones', 'calificaciones-evaluacion'); }

  private emptyForm() {
    return { nombre_evaluacion: '', grupo_id: '', materia_id: '', periodo_evaluacion_id: '',
             tipo_evaluacion: 'ORDINARIO', fecha_evaluacion: '', puntaje_maximo: 10.0 };
  }
}
