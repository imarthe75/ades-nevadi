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
import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
// PrimeNG
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { SelectModule } from 'primeng/select';
import { InputNumberModule } from 'primeng/inputnumber';
import { TagModule } from 'primeng/tag';
import { TooltipModule } from 'primeng/tooltip';
import { MessageService } from 'primeng/api';
import { ToastModule } from 'primeng/toast';
import { ToolbarModule } from 'primeng/toolbar';
import { Table } from 'primeng/table';

import { ContextService } from '../../core/services/context.service';
import { ApiService } from '../../core/services/api.service';
import type { Materia, LibretaGrupo, RegistroLibreta, Grupo, PeriodoSimple } from '../../core/models';
import { grupoLabel } from '../../core/models';

type GrupoConLabel = Grupo & { _label: string };
import { HelpButtonComponent } from '../../shared/components/help-button/help-button.component';

@Component({
  selector: 'app-calificaciones',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    TableModule, ButtonModule, SelectModule, InputNumberModule,
    TagModule, TooltipModule, ToastModule, ToolbarModule,
    HelpButtonComponent,
  ],
  providers: [MessageService],
  template: `
    <p-toast />

    <div class="page-header">
      <div>
        <h2>Calificaciones</h2>
        <p class="subtitle">Libreta de calificaciones — edición inline</p>
      </div>
      <app-help-button modulo="calificaciones" />
    </div>

    <!-- ── Filtros APEX-style ── -->
    <div class="filter-bar">
      <p-select
        [options]="grupos()"
        [(ngModel)]="selectedGrupo"
        optionLabel="_label"
        placeholder="Seleccionar grupo"
        (onChange)="onGrupoChange()"
        [showClear]="true"
      />
      <p-select
        [options]="materias()"
        [(ngModel)]="selectedMateria"
        optionLabel="nombre_materia"
        placeholder="Seleccionar materia"
        (onChange)="loadLibreta()"
        [showClear]="true"
        [disabled]="!selectedGrupo"
      />
    </div>

    <!-- ── Libreta — Editable Interactive Report ── -->
    @if (libreta()) {
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
        [globalFilterFields]="['nombre_completo', 'matricula']"
        sortField="nombre_completo"
        [editMode]="'cell'"
      >
        <!-- Caption con búsqueda global -->
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
              <th style="width:90px; text-align:center">{{ col }}</th>
            }
            <th style="width:80px; text-align:center; font-weight:700">Promedio</th>
            <th style="width:70px; text-align:center">Acredita</th>
          </tr>
          <!-- Fila de filtros (estilo APEX) -->
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
                [pEditableColumn]="row.calificaciones[col]"
                pEditableColumnField="col"
                style="text-align:center; padding:0.2rem"
                [class.edited]="isEdited(row.estudiante_id, col)"
              >
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
              </td>
            }
            <td style="text-align:center; font-weight:600">
              {{ row.promedio | number:'1.1-1' }}
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
        <i class="pi pi-info-circle" style="font-size:2rem; color:#94a3b8"></i>
        <p>Selecciona un grupo y una materia para ver la libreta.</p>
      </div>
    }
  `,
  styles: [`
    .page-header { margin-bottom: 1rem; }
    .page-header h2 { margin: 0; color: #1e293b; }
    .subtitle { color: #64748b; font-size: 0.85rem; margin: 0.25rem 0 0; }
    .filter-bar { display: flex; gap: 1rem; margin-bottom: 1rem; }
    .libreta-toolbar { margin-bottom: 0.5rem; }
    .changes-badge { font-size: 0.85rem; color: #64748b; }
    .changes-badge.has-changes { color: #d97706; font-weight: 600; }
    .table-caption { display: flex; justify-content: flex-end; }
    .search-input { padding: 0.4rem 0.6rem; border: 1px solid #cbd5e1; border-radius: 4px; }
    td.edited { background-color: #fef9c3 !important; }
    .empty-state { display: flex; flex-direction: column; align-items: center; gap: 0.5rem; padding: 4rem; color: #94a3b8; }
    :host ::ng-deep .libreta-table .p-datatable-tbody > tr > td { padding: 0.3rem 0.5rem; }
  `],
})
export class CalificacionesComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly ctx = inject(ContextService);
  private readonly msg = inject(MessageService);

  grupos    = signal<GrupoConLabel[]>([]);
  materias  = signal<Materia[]>([]);
  libreta   = signal<LibretaGrupo | null>(null);
  saving    = signal(false);

  selectedGrupo: GrupoConLabel | null = null;
  selectedMateria: Materia | null = null;

  // Registro de celdas editadas: { `${estudiante_id}|${periodo}` }
  private editadas = new Set<string>();

  columnas = computed(() => this.libreta()?.periodos ?? []);
  pendingChanges = computed(() => this.editadas.size);


  ngOnInit(): void {
    const plantelId = this.ctx.plantel()?.id;
    const nivelNombre = this.ctx.nivel()?.nombre_nivel;
    if (plantelId) {
      const params: Record<string, string | boolean> = { plantel_id: plantelId, solo_activos: true, ciclo_vigente: true };
      if (nivelNombre) params['nivel'] = nivelNombre;
      this.api.get<Grupo[]>('/grupos', params).subscribe(g =>
        this.grupos.set(g.map(x => ({ ...x, _label: grupoLabel(x) })))
      );
    }
  }

  onGrupoChange(): void {
    this.selectedMateria = null;
    this.libreta.set(null);
    this.editadas.clear();
    if (!this.selectedGrupo) return;

    const gradoId = this.selectedGrupo.grado_id;
    this.api.get<Materia[]>('/planes-estudio', { grado_id: gradoId }).subscribe(planes => {
      // planes contiene MateriaPlanOut — extraemos materias únicas
      const materias: Materia[] = (planes as any[]).map((p: any) => p.materia).filter(Boolean);
      this.materias.set(materias);
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
    // Recalcular promedio local
    const vals = Object.values(row.calificaciones).filter((v): v is number => v !== null);
    row.promedio = vals.length ? parseFloat((vals.reduce((a, b) => a + b, 0) / vals.length).toFixed(1)) : null;
  }

  guardarCambios(): void {
    if (!this.libreta() || !this.selectedMateria || !this.selectedGrupo) return;
    this.saving.set(true);

    const libreta = this.libreta()!;
    // Mapa nombre_periodo → id para construir el payload correcto
    const periodoMap = new Map<string, string>(
      libreta.periodos_detalle.map((p: PeriodoSimple) => [p.nombre_periodo, p.id]),
    );

    const requests = [...this.editadas].map(key => {
      const [estudianteId, periodoNombre] = key.split('|');
      const row = libreta.registros.find(r => r.estudiante_id === estudianteId);
      const valor = row?.calificaciones[periodoNombre];
      const periodoId = periodoMap.get(periodoNombre);
      if (!row || valor === null || valor === undefined || !periodoId) return null;

      return {
        estudiante_id: estudianteId,
        grupo_id: this.selectedGrupo!.id,
        materia_id: this.selectedMateria!.id,
        periodo_evaluacion_id: periodoId,
        calificacion_final: valor,
      };
    }).filter(Boolean);

    if (requests.length === 0) {
      this.saving.set(false);
      this.editadas.clear();
      return;
    }

    let completed = 0;
    let hasError = false;
    requests.forEach(payload => {
      this.api.post('/calificaciones', payload).subscribe({
        next: () => {
          completed++;
          if (completed === requests.length && !hasError) {
            this.saving.set(false);
            this.editadas.clear();
            this.msg.add({ severity: 'success', summary: 'Guardado', detail: `${completed} calificación(es) guardadas` });
          }
        },
        error: (e) => {
          hasError = true;
          this.saving.set(false);
          this.msg.add({ severity: 'error', summary: 'Error', detail: e.error?.detail || 'Error al guardar' });
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
}
