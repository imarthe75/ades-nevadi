import { Component, OnInit, inject, signal, computed, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { SelectModule } from 'primeng/select';
import { ButtonModule } from 'primeng/button';
import { ToolbarModule } from 'primeng/toolbar';

import { ApiService } from '../../core/services/api.service';
import { ContextService } from '../../core/services/context.service';
import { Clase, EstatusAsistencia, grupoLabel } from '../../core/models';
import { ApexNotificationService } from 'apex-component-library';

interface AsistenciaLocal {
  estudiante_id: string;
  nombre: string;
  matricula: string;
  estatus: EstatusAsistencia;
}

@Component({
  selector: 'app-asistencias',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    TableModule, SelectModule, ButtonModule, ToolbarModule,
  ],
  template: `

    <div class="page-header">
      <h2>Asistencias — Pase de Lista</h2>
    </div>

    <!-- Filtros de Contexto Cascading (Plantel -> Nivel -> Grado -> Grupo) -->
    <div class="filter-bar">
      <p-select
        [options]="plantelesOpts()"
        [(ngModel)]="selectedPlantelId"
        optionLabel="nombre_plantel"
        optionValue="id"
        placeholder="Plantel"
        (onChange)="onPlantelChange()"
        [showClear]="!isPlantelDisabled()"
        [disabled]="isPlantelDisabled()"
        [filter]="true" filterPlaceholder="Buscar..."
        styleClass="filter-select" />

      <p-select
        [options]="nivelesOpts()"
        [(ngModel)]="selectedNivelId"
        optionLabel="nombre_nivel"
        optionValue="id"
        placeholder="Nivel"
        (onChange)="onNivelChange()"
        [showClear]="!isNivelDisabled()"
        [disabled]="isNivelDisabled() || !selectedPlantelId"
        [filter]="true" filterPlaceholder="Buscar..."
        styleClass="filter-select" />

      <p-select
        [options]="gradosOpts()"
        [(ngModel)]="selectedGradoId"
        optionLabel="nombre_grado"
        optionValue="id"
        placeholder="Grado"
        (onChange)="onGradoChange()"
        [showClear]="true"
        [disabled]="!selectedNivelId"
        [filter]="true" filterPlaceholder="Buscar..."
        styleClass="filter-select" />

      <p-select
        [options]="gruposOpts()"
        [(ngModel)]="selectedGrupoId"
        optionLabel="_label"
        optionValue="id"
        placeholder="Grupo"
        (onChange)="onGrupoChange()"
        [showClear]="true"
        [disabled]="!selectedGradoId"
        [filter]="true" filterPlaceholder="Buscar..."
        styleClass="filter-select" />
    </div>

    <div style="margin-bottom:1rem">
      <p-select
        [options]="clases()"
        [(ngModel)]="selectedClase"
        placeholder="Seleccionar clase"
        optionLabel="_label"
        (onChange)="onClaseChange()"
        [showClear]="true"
        [filter]="true"
        filterPlaceholder="Buscar..."
        [disabled]="!selectedGrupoId"
      />
    </div>

    @if (asistencias().length > 0) {
      <p-toolbar style="margin-bottom:0.5rem">
        <ng-template pTemplate="start">
          <span>{{ asistencias().length }} alumnos</span>
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

      <p-table [value]="asistencias()" styleClass="p-datatable-sm">
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
    .filter-bar { display: flex; gap: 0.75rem; align-items: center; margin-bottom: 1.25rem; flex-wrap: wrap; }
    .filter-select { min-width: 220px; }
  `],
})
export class AsistenciasComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly ctx = inject(ContextService);
  private readonly notify = inject(ApexNotificationService);

  readonly estatusOptions: EstatusAsistencia[] = ['PRESENTE', 'AUSENTE', 'TARDANZA', 'JUSTIFICADO'];
  clases = signal<any[]>([]);
  asistencias = signal<AsistenciaLocal[]>([]);
  saving = signal(false);
  selectedClase: any = null;

  plantelesOpts = signal<any[]>([]);
  nivelesOpts = signal<any[]>([]);
  gradosOpts = signal<any[]>([]);
  gruposOpts = signal<any[]>([]);

  selectedPlantelId: string | null = null;
  selectedNivelId: string | null = null;
  selectedGradoId: string | null = null;
  selectedGrupoId: string | null = null;

  readonly isPlantelDisabled = computed(() => this.ctx.nivelAcceso() > 2);
  readonly isNivelDisabled = computed(() => this.ctx.nivelAcceso() > 3);

  constructor() {
    effect(() => {
      const p = this.ctx.plantel();
      if (p?.id && !this.selectedPlantelId) {
        this.selectedPlantelId = p.id;
        this.onPlantelChange();
      }
    });
  }

  ngOnInit(): void {
    this.api.get<any[]>('/planteles').subscribe({
      next: p => {
        this.plantelesOpts.set(p);
        const currentPlantel = this.ctx.plantel();
        if (currentPlantel?.id) {
          this.selectedPlantelId = currentPlantel.id;
          this.onPlantelChange();
        }
      },
      error: () => {}
    });
  }

  onPlantelChange(): void {
    this.selectedNivelId = null;
    this.selectedGradoId = null;
    this.selectedGrupoId = null;
    this.selectedClase = null;
    this.nivelesOpts.set([]);
    this.gradosOpts.set([]);
    this.gruposOpts.set([]);
    this.clases.set([]);
    this.asistencias.set([]);

    if (!this.selectedPlantelId) return;

    this.api.get<any[]>(`/planteles/${this.selectedPlantelId}/niveles`).subscribe({
      next: ns => {
        const mapped = ns.map(x => ({ id: x.id ?? x.nivel_id, nombre_nivel: x.nombre_nivel }));
        this.nivelesOpts.set(mapped);
        
        const ctxNivel = this.ctx.nivel();
        if (ctxNivel && mapped.some(n => n.id === ctxNivel.id)) {
          this.selectedNivelId = ctxNivel.id;
          this.onNivelChange();
        }
      },
      error: () => {}
    });
  }

  onNivelChange(): void {
    this.selectedGradoId = null;
    this.selectedGrupoId = null;
    this.selectedClase = null;
    this.gradosOpts.set([]);
    this.gruposOpts.set([]);
    this.clases.set([]);
    this.asistencias.set([]);

    if (!this.selectedNivelId) return;

    this.api.get<any[]>(`/catalogs/grados`, { nivel_id: this.selectedNivelId }).subscribe({
      next: gs => {
        this.gradosOpts.set(gs);
      },
      error: () => {}
    });
  }

  onGradoChange(): void {
    this.selectedGrupoId = null;
    this.selectedClase = null;
    this.gruposOpts.set([]);
    this.clases.set([]);
    this.asistencias.set([]);

    if (!this.selectedGradoId) return;

    const params: Record<string, any> = { solo_activos: true, ciclo_vigente: true };
    if (this.selectedPlantelId) params['plantel_id'] = this.selectedPlantelId;
    if (this.selectedGradoId) params['grado_id'] = this.selectedGradoId;

    this.api.get<any[]>('/grupos', params).subscribe({
      next: gps => {
        this.gruposOpts.set(gps.map(x => ({ ...x, _label: grupoLabel(x) })));
      },
      error: () => this.gruposOpts.set([])
    });
  }

  onGrupoChange(): void {
    this.selectedClase = null;
    this.clases.set([]);
    this.asistencias.set([]);

    if (!this.selectedGrupoId) return;

    this.api.get<any[]>('/clases', { grupo_id: this.selectedGrupoId, solo_activos: true }).subscribe({
      next: c => {
        const mapped = c.map(x => ({
          ...x,
          _label: `${x.materia_nombre || 'Materia'} (${x.fecha_clase} ${x.hora_inicio})`
        }));
        this.clases.set(mapped);
      },
      error: () => this.clases.set([])
    });
  }

  onClaseChange(): void {
    if (!this.selectedClase) {
      this.asistencias.set([]);
      return;
    }
    this.api.get<any[]>(`/clases/${this.selectedClase.id}/alumnos-esperados`)
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
}
