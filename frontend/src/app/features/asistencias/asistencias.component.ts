import { Component, inject, signal, computed, effect, OnDestroy, ChangeDetectionStrategy} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { SelectModule } from 'primeng/select';
import { ButtonModule } from 'primeng/button';
import { ToolbarModule } from 'primeng/toolbar';
import { InputTextModule } from 'primeng/inputtext';
import { Subject, takeUntil } from 'rxjs';

import { ApiService } from '../../core/services/api.service';
import { ContextService } from '../../core/services/context.service';
import { EstatusAsistencia } from '../../core/models';
import { ApexNotificationService } from 'apex-component-library';
import { AdesFormatDirective } from '../../shared/directives/ades-format.directive';

interface AsistenciaLocal {
  estudiante_id: string;
  nombre: string;
  matricula: string;
  estatus: EstatusAsistencia;
}

/**
 * Control de asistencia de alumnos por grupo y fecha.
 * Consume el endpoint `/api/v1/asistencias` del BFF para cargar y persistir
 * el estatus diario (PRESENTE, FALTA, TARDE, JUSTIFICADO) de cada alumno
 * del grupo activo en `ContextService`. Los efectos reactivos recargan
 * el listado al cambiar el grupo o la fecha seleccionada. Requiere nivelAcceso ≥ 3.
 */
@Component({
  selector: 'app-asistencias',
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
  imports: [
    AdesFormatDirective,
    CommonModule, FormsModule,
    TableModule, SelectModule, ButtonModule, ToolbarModule, InputTextModule,
  ],
  template: `

    <div class="page-header">
      <h2>Asistencias — Pase de Lista</h2>
    </div>

    <!-- Selector de clase — habilitado cuando el contexto global tiene grupo seleccionado -->
    <div style="display:flex; gap:0.75rem; align-items:center; margin-bottom:1rem; flex-wrap:wrap">
      <div style="width:280px">
        <p-select
          [options]="clases()"
          [(ngModel)]="selectedClase"
          placeholder="Seleccionar clase"
          optionLabel="_label"
          (onChange)="onClaseChange()"
          [showClear]="true"
          [filter]="true"
          filterPlaceholder="Buscar..."
          [disabled]="!ctx.grupo()?.id"
          styleClass="w-full"
        />
      </div>
      @if (asistencias().length > 0) {
        <div style="flex:1; min-width:250px">
          <input
            pInputText
            type="text"
            placeholder="Buscar alumno..."
            [(ngModel)]="busqueda"
            style="width:100%"
          />
        </div>
      }
    </div>

    @if (asistencias().length > 0) {
      <p-toolbar style="margin-bottom:0.5rem">
        <ng-template pTemplate="start">
          <span>{{ asistenciasFiltradas().length }} de {{ asistencias().length }} alumnos</span>
        </ng-template>
        <ng-template pTemplate="end">
          <p-button
            label="Guardar"
            icon="pi pi-save"
            [loading]="saving()"
            (onClick)="guardar()"
          />
        </ng-template>
      </p-toolbar>

      <p-table [value]="asistenciasFiltradas()" styleClass="p-datatable-sm">
        <ng-template pTemplate="header">
          <tr>
            <th style="width:100px">Matrícula</th>
            <th>Alumno</th>
            <th style="width:180px;text-align:center">Asistencia</th>
          </tr>
        </ng-template>

        <ng-template pTemplate="body" let-row>
          <tr>
            <td>{{ row.matricula }}</td>
            <td>{{ row.nombre }}</td>
            <td style="text-align:center">
              <div style="display:flex;gap:0.25rem;justify-content:center;flex-wrap:wrap">
                @for (est of estatusOptions; track est) {
                  <p-button
                    [label]="est"
                    [size]="'small'"
                    [severity]="row.estatus === est ? 'primary' : 'secondary'"
                    [text]="row.estatus !== est"
                    (onClick)="toggleEstatus(row, $any(est))"
                  />
                }
              </div>
            </td>
          </tr>
        </ng-template>

        <ng-template pTemplate="emptymessage">
          <tr><td [colSpan]="3">Selecciona una clase</td></tr>
        </ng-template>
      </p-table>
    }
  `,
  styles: [`
    .page-header { margin-bottom: 1rem; }
  `],
})
export class AsistenciasComponent implements OnDestroy {
  private destroy$ = new Subject<void>();
  private readonly api = inject(ApiService);
  readonly ctx = inject(ContextService);
  private readonly notify = inject(ApexNotificationService);

  readonly estatusOptions: EstatusAsistencia[] = ['PRESENTE', 'AUSENTE', 'TARDE', 'JUSTIFICADO'];
  clases = signal<any[]>([]);
  asistencias = signal<AsistenciaLocal[]>([]);
  busqueda = signal('');
  readonly asistenciasFiltradas = computed(() => {
    const q = this.busqueda().toLowerCase();
    if (!q) return this.asistencias();
    return this.asistencias().filter(a =>
      (a.nombre || '').toLowerCase().includes(q) ||
      (a.matricula || '').toLowerCase().includes(q)
    );
  });
  saving = signal(false);
  selectedClase: any = null;

  constructor() {
    // Recargar clases cuando cambia el grupo en el contexto global.
    effect(() => {
      const grupo = this.ctx.grupo();
      this.selectedClase = null;
      this.asistencias.set([]);
      if (!grupo?.id) {
        this.clases.set([]);
        return;
      }
      this.api.get<any[]>('/clases', { grupo_id: grupo.id, solo_activos: true }).pipe(takeUntil(this.destroy$)).subscribe({
        next: c => {
          this.clases.set(c.map(x => ({
            ...x,
            _label: `${x.materia_nombre || 'Materia'} (${x.fecha_clase} ${x.hora_inicio})`
          })));
        },
        error: () => this.clases.set([])
      });
    });
  }

  onClaseChange(): void {
    if (!this.selectedClase) {
      this.asistencias.set([]);
      return;
    }
    this.api.get<any[]>(`/clases/${this.selectedClase.id}/alumnos-esperados`)
      .pipe(takeUntil(this.destroy$))
      .subscribe(alumnos => {
        const asist: AsistenciaLocal[] = alumnos.map(a => ({
          estudiante_id: a.estudiante_id,
          nombre: a.nombre || a.matricula,
          matricula: a.matricula,
          estatus: (a.estatus || 'PRESENTE') as EstatusAsistencia,
        }));
        this.asistencias.set(asist);
      });
  }

  toggleEstatus(row: AsistenciaLocal, estatus: EstatusAsistencia): void {
    row.estatus = estatus;
  }

  guardar(): void {
    if (!this.selectedClase) return;
    this.saving.set(true);
    const payload = {
      asistencias: this.asistencias().map(a => ({
        estudiante_id: a.estudiante_id,
        estatus_asistencia: a.estatus,
      })),
    };
    this.api.post(`/asistencias/clase/${this.selectedClase.id}`, payload)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          this.saving.set(false);
          this.notify.success('Guardado', 'Asistencia registrada');
        },
        error: (e) => {
          this.saving.set(false);
          this.notify.error('Error', e.error?.detail || 'Error');
        },
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
