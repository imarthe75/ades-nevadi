import { Component, inject, OnInit, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';

import { ButtonModule }        from 'primeng/button';
import { CardModule }          from 'primeng/card';
import { TableModule }         from 'primeng/table';
import { TagModule }           from 'primeng/tag';
import { TabsModule }          from 'primeng/tabs';
import { DialogModule }        from 'primeng/dialog';
import { SelectModule }        from 'primeng/select';
import { AutoCompleteModule }  from 'primeng/autocomplete';
import { TextareaModule }      from 'primeng/textarea';
import { InputTextModule }     from 'primeng/inputtext';
import { TooltipModule }       from 'primeng/tooltip';
import { ProgressBarModule }   from 'primeng/progressbar';
import { PanelModule }         from 'primeng/panel';
import { DividerModule }       from 'primeng/divider';

import { ApiService }     from '../../core/services/api.service';
import { ContextService } from '../../core/services/context.service';
import { ExportService, ExportColumn } from '../../core/services/export.service';

type TagSeverity = 'success' | 'info' | 'warn' | 'danger' | 'secondary' | 'contrast';

interface LearningPath {
  id: string;
  nombre: string;
  descripcion: string | null;
  criterio_activacion: string;
  umbral_activacion: number | null;
  is_active: boolean;
  total_recursos: number;
}

interface Recurso {
  id: string;
  orden: number;
  tipo: string;
  titulo: string;
  descripcion: string | null;
  url_recurso: string | null;
  duracion_min: number | null;
  obligatorio: boolean;
}

interface Asignacion {
  id: string;
  path_id: string;
  path_nombre: string;
  estudiante_id: string;
  motivo: string;
  estatus: string;
  pct_completado: number;
  fccreacion: string;
  // enriquecido en frontend
  alumno_nombre?: string;
}

const CRITERIO_LABELS: Record<string, string> = {
  MANUAL:       'Manual',
  REPROBACION:  'Reprobación',
  AUSENTISMO:   'Ausentismo',
  RIESGO_ALTO:  'Riesgo alto',
};

const ESTATUS_SEV: Record<string, TagSeverity> = {
  PENDIENTE:    'secondary',
  EN_PROGRESO:  'info',
  COMPLETADO:   'success',
  ABANDONADO:   'danger',
};

const TIPO_ICON: Record<string, string> = {
  VIDEO:     'pi-play-circle',
  PDF:       'pi-file-pdf',
  EJERCICIO: 'pi-pencil',
  ENLACE:    'pi-link',
  QUIZ:      'pi-check-circle',
};

@Component({
  selector: 'app-learning-paths',
  standalone: true,
  imports: [
    CommonModule, FormsModule, RouterModule,
    ButtonModule, CardModule, TableModule, TagModule, TabsModule,
    DialogModule, SelectModule, AutoCompleteModule, TextareaModule, InputTextModule,
    TooltipModule, ProgressBarModule, PanelModule, DividerModule,
  ],
  template: `
    <div class="page-header">
      <div>
        <h2 class="page-title">Learning Paths</h2>
        <span class="page-subtitle">Rutas de refuerzo adaptativas para alumnos en riesgo</span>
      </div>
      <div class="header-actions">
        <p-button label="Nueva ruta" icon="pi pi-plus" size="small" (onClick)="abrirNuevePath()" />
      </div>
    </div>

    <p-tabs [(value)]="tabActivo">

      <p-tablist>
        <p-tab value="0"><i class="pi pi-list"></i>&nbsp;Rutas disponibles</p-tab>
        <p-tab value="1"><i class="pi pi-users"></i>&nbsp;Asignaciones</p-tab>
      </p-tablist>

      <p-tabpanels>

        <!-- ── Tab 1: Rutas disponibles ──────────────────────────────────── -->
        <p-tabpanel value="0">
          <div class="paths-grid">
            @for (path of paths(); track path.id) {
              <div class="path-card" [class.inactivo]="!path.is_active">
                <div class="path-card-header">
                  <span class="path-nombre">{{ path.nombre }}</span>
                  <p-tag
                    [value]="criterioLabel(path.criterio_activacion)"
                    [severity]="criteriaSev(path.criterio_activacion)"
                    styleClass="text-xs"
                  />
                </div>
                <p class="path-desc">{{ path.descripcion || 'Sin descripción' }}</p>
                <div class="path-stats">
                  <span><i class="pi pi-book"></i> {{ path.total_recursos }} recursos</span>
                  @if (path.umbral_activacion) {
                    <span><i class="pi pi-exclamation-triangle"></i> Umbral: {{ path.umbral_activacion }}</span>
                  }
                </div>
                <div class="path-actions">
                  <p-button
                    icon="pi pi-eye" label="Ver detalle" [text]="true" size="small"
                    (onClick)="verPath(path)"
                  />
                  <p-button
                    icon="pi pi-user-plus" label="Asignar" [text]="true" size="small"
                    (onClick)="abrirAsignar(path)"
                  />
                </div>
              </div>
            }
            @empty {
              <div class="empty-state"><i class="pi pi-inbox"></i><p>No hay rutas definidas</p></div>
            }
          </div>
        </p-tabpanel>

        <!-- ── Tab 2: Asignaciones activas ───────────────────────────────── -->
        <p-tabpanel value="1">
          <div class="filter-row">
            <p-select
              [options]="estatusOpts"
              [(ngModel)]="filtroEstatus"
              optionLabel="label" optionValue="value"
              placeholder="Filtrar por estatus..."
              (onChange)="cargarAsignaciones()"
              styleClass="w-auto"
            />
            <p-button icon="pi pi-refresh" [text]="true" size="small"
              pTooltip="Actualizar" (onClick)="cargarAsignaciones()" />
            <p-button icon="pi pi-file" label="Exportar CSV" [text]="true" size="small"
              (onClick)="exportCSV()" />
            <p-button icon="pi pi-file-excel" label="Excel" [text]="true" size="small"
              (onClick)="exportXLSX()" />
          </div>

          <p-table
            [value]="asignaciones()"
            [loading]="cargandoAsig()"
            [paginator]="true" [rows]="15"
            styleClass="p-datatable-sm p-datatable-gridlines"
          >
            <ng-template pTemplate="header">
              <tr>
                <th pSortableColumn="path_nombre">Ruta <p-sortIcon field="path_nombre" /></th>
                <th>Alumno</th>
                <th pSortableColumn="motivo">Motivo <p-sortIcon field="motivo" /></th>
                <th pSortableColumn="estatus">Estatus <p-sortIcon field="estatus" /></th>
                <th style="width:180px">Progreso</th>
                <th pSortableColumn="fccreacion" style="width:130px">Asignado <p-sortIcon field="fccreacion" /></th>
                <th style="width:80px"></th>
              </tr>
            </ng-template>
            <ng-template pTemplate="body" let-asig>
              <tr>
                <td>{{ asig.path_nombre }}</td>
                <td>{{ asig.alumno_nombre || asig.estudiante_id }}</td>
                <td>{{ asig.motivo.replace('_', ' ') }}</td>
                <td>
                  <p-tag [value]="asig.estatus" [severity]="estatusSev(asig.estatus)" />
                </td>
                <td>
                  <div class="progress-cell">
                    <p-progressBar [value]="asig.pct_completado" styleClass="h-1rem" />
                    <span class="pct-label">{{ asig.pct_completado }}%</span>
                  </div>
                </td>
                <td>{{ asig.fccreacion | date:'dd/MM/yy' }}</td>
                <td>
                  <p-button icon="pi pi-eye" [text]="true" size="small"
                    pTooltip="Ver detalle" (onClick)="verAsignacion(asig)" />
                </td>
              </tr>
            </ng-template>
            <ng-template pTemplate="emptymessage">
              <tr><td colspan="7" class="text-center p-4">Sin asignaciones</td></tr>
            </ng-template>
          </p-table>
        </p-tabpanel>

      </p-tabpanels>

    </p-tabs>

    <!-- ── Dialog: Detalle de ruta ────────────────────────────────────── -->
    <p-dialog
      [(visible)]="showPathDetail"
      [header]="pathSeleccionado()?.nombre || ''"
      [modal]="true" [style]="{width: '700px'}"
    >
      @if (pathSeleccionado()) {
        <div class="path-detail">
          <p>{{ pathSeleccionado()!.descripcion }}</p>
          <p-divider />
          <h4>Recursos ({{ recursos().length }})</h4>
          @for (rec of recursos(); track rec.id) {
            <div class="recurso-row">
              <i [class]="'pi ' + tipoIcon(rec.tipo)" class="recurso-icon"></i>
              <div class="recurso-info">
                <strong>{{ rec.orden }}. {{ rec.titulo }}</strong>
                @if (rec.descripcion) { <p class="text-sm text-secondary">{{ rec.descripcion }}</p> }
              </div>
              <div class="recurso-meta">
                @if (rec.duracion_min) { <span>{{ rec.duracion_min }} min</span> }
                @if (!rec.obligatorio) { <p-tag value="Opcional" severity="secondary" /> }
                @if (rec.url_recurso) {
                  <a [href]="rec.url_recurso" target="_blank" class="btn-link">
                    <i class="pi pi-external-link"></i>
                  </a>
                }
              </div>
            </div>
          }
          @empty {
            <p class="text-secondary">Esta ruta no tiene recursos aún.</p>
          }
        </div>
      }
    </p-dialog>

    <!-- ── Dialog: Nueva ruta ─────────────────────────────────────────── -->
    <p-dialog
      [(visible)]="showNuevePath"
      header="Nueva ruta de refuerzo"
      [modal]="true" [style]="{width: '520px'}"
    >
      <div class="form-grid">
        <label>Nombre *</label>
        <input pInputText [(ngModel)]="form.nombre" placeholder="Ej. Refuerzo matemáticas" />

        <label>Descripción</label>
        <textarea pTextarea [(ngModel)]="form.descripcion" rows="3" placeholder="Objetivo y contenido..."></textarea>

        <label>Criterio de activación</label>
        <p-select
          [options]="criterioOpts"
          [(ngModel)]="form.criterio_activacion"
          optionLabel="label" optionValue="value"
        />

        @if (form.criterio_activacion !== 'MANUAL') {
          <label>Umbral</label>
          <input pInputText type="number" [(ngModel)]="form.umbral_activacion"
            placeholder="{{ form.criterio_activacion === 'REPROBACION' ? 'Ej: 6.0' : 'Ej: 80' }}" />
        }
      </div>
      <ng-template pTemplate="footer">
        <p-button label="Cancelar" [text]="true" (onClick)="showNuevePath = false" />
        <p-button label="Crear ruta" icon="pi pi-check" (onClick)="crearPath()" [disabled]="!form.nombre" />
      </ng-template>
    </p-dialog>

    <!-- ── Dialog: Asignar a alumno ───────────────────────────────────── -->
    <p-dialog
      [(visible)]="showAsignar"
      [header]="'Asignar: ' + (pathAsignar()?.nombre || '')"
      [modal]="true" [style]="{width: '440px'}"
    >
      <div class="form-grid">
        <label>Alumno *</label>
        <p-autoComplete
          [(ngModel)]="formAsig._alumnoObj"
          [suggestions]="alumnosSuggAsig()"
          (completeMethod)="buscarAlumnosAsig($event)"
          optionLabel="nombre_completo"
          [forceSelection]="true"
          placeholder="Buscar alumno..."
          (onSelect)="onAlumnoAsigSelect($event.value)"
        />
        <label>Motivo</label>
        <p-select
          [options]="motivoOpts"
          [(ngModel)]="formAsig.motivo"
          optionLabel="label" optionValue="value"
        />
      </div>
      <ng-template pTemplate="footer">
        <p-button label="Cancelar" [text]="true" (onClick)="showAsignar = false" />
        <p-button label="Asignar" icon="pi pi-user-plus" (onClick)="confirmarAsignar()"
          [disabled]="!formAsig.estudiante_id" />
      </ng-template>
    </p-dialog>
  `,
  styles: [`
    .page-header {
      display: flex; justify-content: space-between; align-items: flex-start;
      margin-bottom: 1.25rem;
    }
    .page-title    { font-family: 'Jost', sans-serif; font-size: 1.45rem; font-weight: 700; margin: 0; }
    .page-subtitle { font-size: 0.82rem; color: var(--text-color-secondary); }
    .header-actions { display: flex; gap: .5rem; align-items: center; }

    .paths-grid {
      display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
      gap: 1rem; padding: 1rem 0;
    }
    .path-card {
      background: var(--surface-card); border: 1px solid var(--surface-border);
      border-radius: 8px; padding: 1rem;
      display: flex; flex-direction: column; gap: .5rem;
    }
    .path-card.inactivo { opacity: .6; }
    .path-card-header { display: flex; justify-content: space-between; align-items: flex-start; gap: .5rem; }
    .path-nombre { font-weight: 600; font-size: .95rem; flex: 1; }
    .path-desc { font-size: .82rem; color: var(--text-color-secondary); margin: 0; flex: 1; }
    .path-stats { display: flex; gap: 1rem; font-size: .78rem; color: var(--text-color-secondary); }
    .path-stats i { margin-right: 3px; }
    .path-actions { display: flex; gap: .25rem; margin-top: .25rem; }

    .filter-row { display: flex; gap: .5rem; align-items: center; margin-bottom: 1rem; flex-wrap: wrap; }

    .progress-cell { display: flex; align-items: center; gap: .5rem; }
    .pct-label { font-size: .78rem; min-width: 36px; }

    .path-detail { display: flex; flex-direction: column; gap: .75rem; }
    .recurso-row {
      display: flex; align-items: flex-start; gap: .75rem;
      padding: .5rem; border-radius: 6px;
      border: 1px solid var(--surface-border);
    }
    .recurso-icon { font-size: 1.25rem; color: var(--primary-color); margin-top: 2px; }
    .recurso-info { flex: 1; }
    .recurso-meta { display: flex; align-items: center; gap: .5rem; font-size: .78rem; }
    .btn-link { color: var(--primary-color); }

    .form-grid { display: grid; grid-template-columns: 120px 1fr; gap: .75rem; align-items: center; }
    .form-grid label { font-size: .85rem; font-weight: 500; }
    .form-grid input,
    .form-grid textarea { width: 100%; }

    .empty-state { grid-column: 1 / -1; text-align: center; padding: 3rem; color: var(--text-color-secondary); }
    .empty-state i { font-size: 2rem; display: block; margin-bottom: .5rem; }

    .text-secondary { color: var(--text-color-secondary); }
    .text-sm { font-size: .82rem; }
  `],
})
export class LearningPathsComponent implements OnInit {
  private readonly api    = inject(ApiService);
  private readonly ctx    = inject(ContextService);
  private readonly export = inject(ExportService);

  tabActivo = '0';

  paths        = signal<LearningPath[]>([]);
  asignaciones = signal<Asignacion[]>([]);
  recursos     = signal<Recurso[]>([]);
  cargandoAsig = signal(false);

  pathSeleccionado = signal<LearningPath | null>(null);
  pathAsignar      = signal<LearningPath | null>(null);

  showPathDetail = false;
  showNuevePath  = false;
  showAsignar    = false;

  filtroEstatus: string | null = null;

  form: { nombre: string; descripcion: string; criterio_activacion: string; umbral_activacion: number | null } = {
    nombre: '', descripcion: '', criterio_activacion: 'MANUAL', umbral_activacion: null,
  };
  formAsig: { estudiante_id: string; motivo: string; _alumnoObj: any } = {
    estudiante_id: '', motivo: 'MANUAL', _alumnoObj: null,
  };
  alumnosSuggAsig = signal<any[]>([]);

  readonly criterioOpts = [
    { label: 'Manual',       value: 'MANUAL' },
    { label: 'Reprobación',  value: 'REPROBACION' },
    { label: 'Ausentismo',   value: 'AUSENTISMO' },
    { label: 'Riesgo alto',  value: 'RIESGO_ALTO' },
  ];
  readonly motivoOpts = [
    { label: 'Manual',              value: 'MANUAL' },
    { label: 'Auto-Reprobación',    value: 'AUTO_REPROBACION' },
    { label: 'Auto-Ausentismo',     value: 'AUTO_AUSENTISMO' },
    { label: 'Auto-Riesgo alto',    value: 'AUTO_RIESGO' },
  ];
  readonly estatusOpts = [
    { label: 'Todos',        value: null },
    { label: 'Pendiente',    value: 'PENDIENTE' },
    { label: 'En progreso',  value: 'EN_PROGRESO' },
    { label: 'Completado',   value: 'COMPLETADO' },
    { label: 'Abandonado',   value: 'ABANDONADO' },
  ];

  private readonly exportCols: ExportColumn[] = [
    { field: 'path_nombre',     header: 'Ruta' },
    { field: 'estudiante_id',   header: 'Alumno ID' },
    { field: 'motivo',          header: 'Motivo' },
    { field: 'estatus',         header: 'Estatus' },
    { field: 'pct_completado',  header: '% Completado', format: v => `${v}%` },
    { field: 'fccreacion',      header: 'Asignado',
      format: v => v ? new Date(v).toLocaleDateString('es-MX') : '' },
  ];

  ngOnInit(): void {
    this.cargarPaths();
    this.cargarAsignaciones();
  }

  cargarPaths(): void {
    this.api.get<LearningPath[]>('/learning-paths').subscribe(p => this.paths.set(p));
  }

  cargarAsignaciones(): void {
    this.cargandoAsig.set(true);
    const params: Record<string, string> = {};
    if (this.filtroEstatus) params['estatus'] = this.filtroEstatus;
    this.api.get<Asignacion[]>('/learning-paths/asignaciones', params).subscribe({
      next: a => { this.asignaciones.set(a); this.cargandoAsig.set(false); },
      error: () => this.cargandoAsig.set(false),
    });
  }

  verPath(path: LearningPath): void {
    this.pathSeleccionado.set(path);
    this.api.get<{ recursos: Recurso[] }>(`/learning-paths/${path.id}`).subscribe(d => {
      this.recursos.set(d.recursos || []);
    });
    this.showPathDetail = true;
  }

  abrirAsignar(path: LearningPath): void {
    this.pathAsignar.set(path);
    this.formAsig = { estudiante_id: '', motivo: 'MANUAL', _alumnoObj: null };
    this.showAsignar = true;
  }

  buscarAlumnosAsig(event: { query: string }): void {
    const plantelId = this.ctx.plantel()?.id;
    const params: Record<string, any> = { buscar: event.query, por_pagina: 10 };
    if (plantelId) params['plantel_id'] = plantelId;
    this.api.get<any>('/alumnos', params).subscribe({
      next: (r: any) => {
        const lista = r?.data ?? r;
        this.alumnosSuggAsig.set(lista.map((a: any) => ({
          ...a,
          nombre_completo: a.persona?.nombre_completo ?? a.matricula,
        })));
      },
      error: () => this.alumnosSuggAsig.set([]),
    });
  }

  onAlumnoAsigSelect(alumno: any): void {
    this.formAsig.estudiante_id = alumno?.id ?? '';
  }

  confirmarAsignar(): void {
    const path = this.pathAsignar();
    if (!path || !this.formAsig.estudiante_id) return;
    const { _alumnoObj, ...payload } = this.formAsig;
    this.api.post(`/learning-paths/${path.id}/asignar`, payload).subscribe(() => {
      this.showAsignar = false;
      this.cargarAsignaciones();
    });
  }

  abrirNuevePath(): void {
    this.form = { nombre: '', descripcion: '', criterio_activacion: 'MANUAL', umbral_activacion: null };
    this.showNuevePath = true;
  }

  crearPath(): void {
    this.api.post('/learning-paths', this.form).subscribe(() => {
      this.showNuevePath = false;
      this.cargarPaths();
    });
  }

  verAsignacion(asig: Asignacion): void {
    // TODO: abrir dialog de detalle de asignación con progreso por recurso
  }

  criterioLabel(c: string): string { return CRITERIO_LABELS[c] ?? c; }
  tipoIcon(t: string): string       { return TIPO_ICON[t] ?? 'pi-file'; }
  estatusSev(e: string): TagSeverity { return ESTATUS_SEV[e] ?? 'secondary'; }
  criteriaSev(c: string): TagSeverity {
    const map: Record<string, TagSeverity> = {
      MANUAL: 'secondary', REPROBACION: 'danger', AUSENTISMO: 'warn', RIESGO_ALTO: 'danger',
    };
    return map[c] ?? 'secondary';
  }

  exportCSV():  void { this.export.toCSV(this.asignaciones(), this.exportCols, 'learning-paths'); }
  exportXLSX(): void { this.export.toXLSX(this.asignaciones(), this.exportCols, 'Learning Paths', 'learning-paths'); }
}
