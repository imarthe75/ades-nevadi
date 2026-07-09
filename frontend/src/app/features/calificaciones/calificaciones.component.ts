/**
 * CalificacionesComponent — Libreta de calificaciones.
 *
 * Patrón Oracle APEX: Editable Interactive Report
 *   - Tabla con filtro por columna en header (filterDisplay="row")
 *   - Edición inline por celda (p-cellEditor) — igual que APEX Editable IR
 *   - Cambios se acumulan localmente
 *   - Un solo botón "Guardar cambios" envía el PATCH bulk
 *   - Exportar a CSV con un click
 */
import { Component, OnInit, OnDestroy, inject, signal, computed, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject, takeUntil } from 'rxjs';
// PrimeNG
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { SelectModule } from 'primeng/select';
import { InputNumberModule } from 'primeng/inputnumber';
import { TagModule } from 'primeng/tag';
import { TooltipModule } from 'primeng/tooltip';
import { ToolbarModule } from 'primeng/toolbar';
import { Table } from 'primeng/table';

import { ContextService } from '../../core/services/context.service';
import { ApiService } from '../../core/services/api.service';
import type { Materia, LibretaGrupo, RegistroLibreta, Grupo, PeriodoSimple } from '../../core/models';
import { grupoLabel } from '../../core/models';

type GrupoConLabel = Grupo & { _label: string };
import { HelpButtonComponent } from '../../shared/components/help-button/help-button.component';
import { ApexNotificationService } from 'apex-component-library';

@Component({
  selector: 'app-calificaciones',
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    TableModule, ButtonModule, SelectModule, InputNumberModule,
    TagModule, TooltipModule, ToolbarModule,
    HelpButtonComponent,
  ],
  template: `

    <div class="page-header">
      <div>
        <h2>Calificaciones</h2>
        <p class="subtitle">
          Libreta de calificaciones — edición inline
          @if (ctx.ciclo()?.sistema_educativo) {
            <p-tag [value]="ctx.ciclo()!.sistema_educativo"
                   [severity]="ctx.ciclo()!.sistema_educativo === 'SEP' ? 'info' : 'success'"
                   [style]="{'margin-left': '1rem'}"></p-tag>
          }
        </p>
      </div>
      <app-help-button modulo="calificaciones" />
    </div>

    <!-- Filtro de dominio: Materia (la cascada plantel→nivel→grado→grupo vive en el topbar) -->
    <div class="filter-bar">
      <p-select
        [options]="materias()"
        [(ngModel)]="selectedMateria"
        optionLabel="nombre_materia"
        placeholder="Seleccionar materia"
        (onChange)="loadLibreta()"
        [showClear]="true"
        [disabled]="!selectedGrupo"
        [filter]="true" filterPlaceholder="Buscar..."/>
    </div>

    <!-- ── Libreta — Editable Interactive Report ── -->
    @if (libreta()) {
      @if (esCualitativa()) {
        <div class="cual-badge">
          <i class="pi pi-star-fill"></i>
          Evaluación cualitativa NEM — Grado {{ selectedGrupo?.numero_grado }}° Primaria
          <span class="cual-desc-legend">
            @for (d of cualDescriptores(); track d.nivel) {
              <span class="cual-chip" [style.background]="d.color" [pTooltip]="d.descripcion">
                {{ d.nivel }} – {{ d.label }}
              </span>
            }
          </span>
        </div>
      }
      <p-toolbar styleClass="libreta-toolbar">
        <ng-template pTemplate="start">
          <span class="changes-badge" [class.has-changes]="pendingChanges() > 0">
            {{ pendingChanges() }} cambio(s) sin guardar
          </span>
        </ng-template>
        <ng-template pTemplate="end">
          <p-button
            label="Guardar cambios"
            icon="pi pi-save"
            [disabled]="pendingChanges() === 0"
            [loading]="saving()"
            (onClick)="guardarCambios()"
          />
          <p-button
            label="Exportar CSV"
            icon="pi pi-download"
            severity="secondary"
            [text]="true"
            (onClick)="exportarCSV()"
            styleClass="ml-2"
          />
        </ng-template>
      </p-toolbar>

      <p-table
        #dt
        [value]="libreta()!.registros"
        [columns]="columnas()"
        filterDisplay="row"
        [paginator]="true"
        [rows]="30"
        [rowsPerPageOptions]="[15, 30, 50]"
        [showCurrentPageReport]="true"
        currentPageReportTemplate="{first}-{last} de {totalRecords} alumnos"
        styleClass="p-datatable-sm p-datatable-gridlines libreta-table"
        [editMode]="'cell'"
      >
        <ng-template pTemplate="caption">
          <div class="table-caption">
            <input
              pInputText
              type="text"
              placeholder="Buscar alumno..."
              (input)="dt.filterGlobal($any($event.target).value, 'contains')"
              class="search-input"
            />
          </div>
        </ng-template>

        <ng-template pTemplate="header" let-columns>
          <tr>
            <th pSortableColumn="matricula" style="width:110px">
              Matrícula <p-sortIcon field="matricula" />
            </th>
            <th pSortableColumn="nombre_completo">
              Alumno <p-sortIcon field="nombre_completo" />
            </th>
            @for (col of columns; track col) {
              <th style="width:110px; text-align:center">{{ col }}</th>
            }
            <th style="width:90px; text-align:center; font-weight:700">Promedio</th>
            <th style="width:70px; text-align:center">Acredita</th>
          </tr>
          <tr>
            <th><p-columnFilter field="matricula" type="text" matchMode="contains" /></th>
            <th><p-columnFilter field="nombre_completo" type="text" matchMode="contains" /></th>
            @for (col of columns; track col) { <th></th> }
            <th></th><th></th>
          </tr>
        </ng-template>

        <ng-template pTemplate="body" let-row let-columns="columns">
          <tr>
            <td>{{ row.matricula }}</td>
            <td>{{ row.nombre_completo }}</td>
            @for (col of columns; track col) {
              <td
                [pEditableColumn]="esCualitativa() ? (row.niveles_logro?.[col] ?? null) : row.calificaciones[col]"
                pEditableColumnField="col"
                style="text-align:center; padding:0.2rem"
                [class.edited]="isEdited(row.estudiante_id, col)"
              >
                @if (esCualitativa()) {
                  <p-cellEditor>
                    <ng-template pTemplate="input">
                      <p-select
                        [options]="cualDescriptoresOpts()"
                        [(ngModel)]="row.niveles_logro[col]"
                        placeholder="—"
                        [style]="{width:'110px'}"
                        (onChange)="onLogrolChange(row, col, $event.value)"
                      />
                    </ng-template>
                    <ng-template pTemplate="output">
                      @if (row.niveles_logro?.[col]) {
                        <span class="cual-cell"
                          [style.background]="nivelColor(row.niveles_logro[col])">
                          {{ row.niveles_logro[col] }}
                          @if (cualMostrarEquiv()) {
                            <span class="cual-equiv">({{ row.calificaciones[col] | number:'1.1-1' }})</span>
                          }
                        </span>
                      } @else { <span style="color:var(--text-muted)">—</span> }
                    </ng-template>
                  </p-cellEditor>
                } @else {
                  <p-cellEditor>
                    <ng-template pTemplate="input">
                      <p-inputNumber
                        [(ngModel)]="row.calificaciones[col]"
                        [min]="0" [max]="10" [maxFractionDigits]="1"
                        [inputStyle]="{width:'60px', textAlign:'center'}"
                        (onBlur)="onCalificacionChange(row, col)"
                      />
                    </ng-template>
                    <ng-template pTemplate="output">
                      {{ row.calificaciones[col] | number:'1.1-1' }}
                    </ng-template>
                  </p-cellEditor>
                }
              </td>
            }
            <td style="text-align:center; font-weight:600">
              @if (esCualitativa()) {
                @if (nivelPromedio(row); as np) {
                  <span class="cual-cell" [style.background]="nivelColor(np)">{{ np }}</span>
                } @else { — }
              } @else {
                {{ row.promedio | number:'1.1-1' }}
              }
            </td>
            <td style="text-align:center">
              @if (row.promedio !== null) {
                <p-tag
                  [value]="row.promedio! >= 6 ? 'Sí' : 'No'"
                  [severity]="row.promedio! >= 6 ? 'success' : 'danger'"
                />
              }
            </td>
          </tr>
        </ng-template>

        <ng-template pTemplate="emptymessage">
          <tr><td [colSpan]="3 + columnas().length">No hay registros para esta materia.</td></tr>
        </ng-template>
      </p-table>
    } @else {
      <div class="empty-state">
        <i class="pi pi-info-circle" style="font-size:2rem; color:var(--text-muted)"></i>
        <p>Selecciona un grupo y una materia para ver la libreta.</p>
      </div>
    }
  `,
  styles: [`
    .page-header { margin-bottom: 1rem; }
    .filter-bar { display: flex; gap: 1rem; margin-bottom: 1rem; flex-wrap: wrap; }
    .libreta-toolbar { margin-bottom: 0.5rem; }
    .changes-badge { font-size: 0.85rem; color: var(--text-secondary); }
    .changes-badge.has-changes { color: var(--color-warning); font-weight: 600; }
    .table-caption { display: flex; justify-content: flex-end; }
    .search-input { padding: 0.4rem 0.6rem; border: 1px solid var(--surface-border); border-radius: 4px; }
    td.edited { background-color: #fef9c3 !important; }
    .empty-state { display: flex; flex-direction: column; align-items: center; gap: 0.5rem; padding: 4rem; color: var(--text-muted); }
    :host ::ng-deep .libreta-table .p-datatable-tbody > tr > td { padding: 0.3rem 0.5rem; }
    /* Evaluación cualitativa */
    .cual-badge { display:flex; align-items:center; gap:.5rem; flex-wrap:wrap;
      background:#eff6ff; border:1px solid #bfdbfe; border-radius:6px;
      padding:.5rem .75rem; margin-bottom:.75rem; font-size:.85rem; color:#1e40af; font-weight:500; }
    .cual-desc-legend { display:flex; gap:.4rem; flex-wrap:wrap; margin-left:.5rem; }
    .cual-chip { color:#fff; font-size:.75rem; font-weight:600; padding:.15rem .5rem;
      border-radius:4px; cursor:help; }
    .cual-cell { display:inline-flex; align-items:center; gap:.25rem; color:#fff;
      font-weight:700; font-size:.82rem; padding:.15rem .45rem; border-radius:4px; }
    .cual-equiv { font-weight:400; opacity:.85; font-size:.75rem; }
  `],
})
export class CalificacionesComponent implements OnInit, OnDestroy {
  private destroy$ = new Subject<void>();
  private readonly api = inject(ApiService);
  readonly ctx = inject(ContextService);
  private readonly notify = inject(ApexNotificationService);

  materias      = signal<Materia[]>([]);
  libreta       = signal<LibretaGrupo | null>(null);
  saving        = signal(false);

  /** Grupo derivado del contexto global (topbar). Expone nombre_nivel y numero_grado para esCualitativa. */
  get selectedGrupo(): GrupoConLabel | null {
    const g = this.ctx.grupo();
    if (!g?.id) return null;
    return { ...g, _label: grupoLabel(g) } as GrupoConLabel;
  }
  selectedMateria:   Materia | null = null;

  private editadas = new Set<string>();

  // ── Evaluación Cualitativa NEM ───────────────────────────────────────────
  cualConfig    = signal<{ config: any; escala: any } | null>(null);
  cualGrados    = computed<number[]>(() => {
    const v = this.cualConfig()?.config?.['EVAL_CUAL_GRADOS_PRIMARIA'];
    return Array.isArray(v) ? v : [];
  });
  cualDescriptores = computed<any[]>(() => {
    return this.cualConfig()?.escala?.valores_json ?? [];
  });
  cualDescriptoresOpts = computed(() =>
    this.cualDescriptores().map(d => ({ label: `${d.nivel} – ${d.label}`, value: d.nivel }))
  );
  cualMostrarEquiv = computed<boolean>(() =>
    !!this.cualConfig()?.config?.['EVAL_CUAL_MOSTRAR_EQUIVALENCIA']
  );

  /** true cuando el grupo seleccionado es de primaria y su grado está en la lista cualitativa */
  esCualitativa = computed(() => {
    const grupo = this.selectedGrupo;
    if (!grupo) return false;
    const nivel = (grupo as any).nombre_nivel ?? (grupo as any).nivel ?? '';
    if (!nivel.toLowerCase().includes('primaria')) return false;
    const grado = (grupo as any).numero_grado ?? (grupo as any).numero ?? 0;
    return this.cualGrados().includes(Number(grado));
  });

  /** Calcula el nivel de logro dominante (el más frecuente) para una fila */
  nivelPromedio(row: any): string | null {
    if (!row.niveles_logro) return null;
    const counts: Record<string, number> = {};
    const orden = ['D', 'C', 'B', 'A'];
    for (const v of Object.values(row.niveles_logro)) {
      if (v) counts[v as string] = (counts[v as string] ?? 0) + 1;
    }
    if (!Object.keys(counts).length) return null;
    let best: string | null = null;
    for (const n of orden) {
      if (counts[n]) { best = n; break; }
    }
    return best;
  }

  nivelColor(nivel: string | null): string {
    const d = this.cualDescriptores().find((x: any) => x.nivel === nivel);
    return d?.color ?? '#94a3b8';
  }

  columnas       = computed(() => this.libreta()?.periodos ?? []);
  pendingChanges = computed(() => this.editadas.size);

  constructor() {
    // Recargar materias cuando cambia el grupo en el contexto global.
    effect(() => {
      const grupo = this.ctx.grupo();
      this.selectedMateria = null;
      this.libreta.set(null);
      this.editadas.clear();
      if (!grupo?.id) {
        this.materias.set([]);
        return;
      }
      this.onGrupoChange();
    });
  }

  ngOnInit(): void {
    // ngOnInit no necesita cargar planteles — la cascada vive en el topbar.
  }

  onGrupoChange(): void {
    this.selectedMateria = null;
    this.libreta.set(null);
    this.editadas.clear();
    if (!this.selectedGrupo) return;

    // Cargar config cualitativa si aún no está cargada
    if (!this.cualConfig()) {
      this.api.get<any>('/calificaciones/config-cualitativa', { nivel: 'PRIMARIA' }).subscribe({
        next: cfg => this.cualConfig.set(cfg),
        error: () => {},
      });
    }

    const gradoId = this.selectedGrupo.grado_id;
    const cicloId = this.ctx.ciclo()?.id;
    const planParams: Record<string, any> = { grado_id: gradoId };
    if (cicloId) planParams['ciclo_id'] = cicloId;

    // Cargar planes del grado para obtener las materia_ids vigentes
    this.api.get<any[]>('/planes-estudio', planParams).subscribe({
      next: planes => {
        const materiaIds = new Set(planes.map((p: any) => p.materia_id).filter(Boolean));
        // Cargar todas las materias y filtrar por las que están en el plan del grado
        this.api.get<Materia[]>('/materias').subscribe({
          next: all => {
            const filtradas = materiaIds.size > 0
              ? all.filter(m => materiaIds.has(m.id))
              : all;
            this.materias.set(filtradas.sort((a, b) => a.nombre_materia.localeCompare(b.nombre_materia)));
          },
          error: () => this.materias.set([]),
        });
      },
      error: () => {
        // Fallback: cargar materias por nivel del grupo
        const nivelNombre = this.selectedGrupo?.nombre_nivel;
        const params: Record<string, any> = {};
        if (nivelNombre) params['nivel'] = nivelNombre;
        this.api.get<Materia[]>('/materias', params).subscribe({
          next: all => this.materias.set(all.sort((a, b) => a.nombre_materia.localeCompare(b.nombre_materia))),
          error: () => this.materias.set([]),
        });
      },
    });
  }

  loadLibreta(): void {
    if (!this.selectedGrupo || !this.selectedMateria) return;
    this.editadas.clear();
    this.api.get<LibretaGrupo>(
      `/calificaciones/grupo/${this.selectedGrupo.id}/libreta`,
      { materia_id: this.selectedMateria.id },
    ).subscribe(l => this.libreta.set(l));
  }

  isEdited(estudianteId: string, periodo: string): boolean {
    return this.editadas.has(`${estudianteId}|${periodo}`);
  }

  onCalificacionChange(row: RegistroLibreta, periodo: string): void {
    this.editadas.add(`${row.estudiante_id}|${periodo}`);
    const vals = Object.values(row.calificaciones).filter((v): v is number => v !== null);
    row.promedio = vals.length ? parseFloat((vals.reduce((a, b) => a + b, 0) / vals.length).toFixed(1)) : null;
  }

  onLogrolChange(row: any, periodo: string, nivel: string): void {
    this.editadas.add(`${row.estudiante_id}|${periodo}`);
    // Actualizar calificacion_final con equiv_num del descriptor
    const d = this.cualDescriptores().find((x: any) => x.nivel === nivel);
    if (d && d.equiv_num !== undefined) {
      if (!row.calificaciones) row.calificaciones = {};
      row.calificaciones[periodo] = d.equiv_num;
      // Recalcular promedio local
      const vals = Object.values(row.calificaciones as Record<string, number>).filter(v => v !== null);
      row.promedio = vals.length ? parseFloat((vals.reduce((a, b) => a + b, 0) / vals.length).toFixed(1)) : null;
    }
  }

  guardarCambios(): void {
    if (!this.libreta() || !this.selectedMateria || !this.selectedGrupo) return;
    this.saving.set(true);

    const libreta = this.libreta()!;
    const periodoMap = new Map<string, string>(
      libreta.periodos_detalle.map((p: PeriodoSimple) => [p.nombre_periodo, p.id]),
    );
    const esCual = this.esCualitativa();

    const requests = [...this.editadas].map(key => {
      const [estudianteId, periodoNombre] = key.split('|');
      const row = libreta.registros.find((r: any) => r.estudiante_id === estudianteId);
      const periodoId = periodoMap.get(periodoNombre);
      if (!row || !periodoId) return null;

      if (esCual) {
        const nivelLogro = (row as any).niveles_logro?.[periodoNombre];
        if (!nivelLogro) return null;
        return {
          _endpoint: '/calificaciones/cualitativa',
          estudianteId, grupoId: this.selectedGrupo!.id,
          materiaId: this.selectedMateria!.id,
          periodoEvaluacionId: periodoId,
          nivelLogro,
        };
      } else {
        const valor = row?.calificaciones[periodoNombre];
        if (valor === null || valor === undefined) return null;
        return {
          _endpoint: '/calificaciones/manual',
          estudiante_id: estudianteId,
          grupo_id: this.selectedGrupo!.id,
          materia_id: this.selectedMateria!.id,
          periodo_id: periodoId,
          calificacion_final: valor,
        };
      }
    }).filter(Boolean);

    if (requests.length === 0) {
      this.saving.set(false);
      this.editadas.clear();
      return;
    }

    let completed = 0;
    let hasError = false;
    requests.forEach((payload: any) => {
      const endpoint = payload._endpoint;
      const { _endpoint, ...body } = payload;
      this.api.post(endpoint, body).subscribe({
        next: () => {
          completed++;
          if (completed === requests.length && !hasError) {
            this.saving.set(false);
            this.editadas.clear();
            this.notify.success('Guardado', `${completed} calificación(es) guardadas`);
          }
        },
        error: (e) => {
          hasError = true;
          this.saving.set(false);
          this.notify.error('Error', e.error?.detail || e.error?.message || 'Error al guardar');
        },
      });
    });
  }

  exportarCSV(): void {
    // Construir CSV manualmente
    const libreta = this.libreta();
    if (!libreta) return;
    const header = ['Matrícula', 'Alumno', ...libreta.periodos, 'Promedio'].join(',');
    const rows = libreta.registros.map(r => [
      r.matricula,
      r.nombre_completo,
      ...libreta.periodos.map(p => r.calificaciones[p] ?? ''),
      r.promedio ?? '',
    ].join(','));
    const csv = [header, ...rows].join('\n');
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url; a.download = `calificaciones_${this.selectedGrupo?.nombre_grupo ?? 'grupo'}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
