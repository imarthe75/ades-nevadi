import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { SelectModule } from 'primeng/select';
import { ButtonModule } from 'primeng/button';
import { MessageService } from 'primeng/api';
import { ToastModule } from 'primeng/toast';
import { ToolbarModule } from 'primeng/toolbar';

import { ApiService } from '../../core/services/api.service';
import { ContextService } from '../../core/services/context.service';
import type { Clase, EstatusAsistencia } from '../../core/models';

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
    TableModule, SelectModule, ButtonModule, ToastModule, ToolbarModule,
  ],
  providers: [MessageService],
  template: `
    <p-toast />

    <div class="page-header">
      <h2>Asistencias — Pase de Lista</h2>
    </div>

    <div style="margin-bottom:1rem">
      <p-select
        [options]="clases()"
        [(ngModel)]="selectedClase"
        placeholder="Seleccionar clase"
        optionLabel="id"
        (onChange)="onClaseChange()"
        [showClear]="true"
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
  `],
})
export class AsistenciasComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly ctx = inject(ContextService);
  private readonly msg = inject(MessageService);

  readonly estatusOptions: EstatusAsistencia[] = ['PRESENTE', 'AUSENTE', 'TARDE', 'JUSTIFICADO'];
  clases = signal<any[]>([]);
  asistencias = signal<AsistenciaLocal[]>([]);
  saving = signal(false);
  selectedClase: any = null;

  ngOnInit(): void {
    this.api.get<any[]>('/clases', { solo_activos: true })
      .subscribe(c => this.clases.set(c.slice(0, 10)));
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
          this.msg.add({ severity: 'success', summary: 'Guardado', detail: 'Asistencia registrada' });
        },
        error: (e) => {
          this.saving.set(false);
          this.msg.add({ severity: 'error', summary: 'Error', detail: e.error?.detail || 'Error' });
        },
      });
  }
}
