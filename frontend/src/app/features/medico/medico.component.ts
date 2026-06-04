/**
 * MedicoComponent — Expediente médico del alumno + historial de incidentes.
 *
 * Flujo: buscar alumno → ver/editar expediente base → ver incidentes → registrar nuevo incidente.
 */
import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { TextareaModule } from 'primeng/textarea';
import { CardModule } from 'primeng/card';
import { TagModule } from 'primeng/tag';
import { ToastModule } from 'primeng/toast';
import { DialogModule } from 'primeng/dialog';
import { DividerModule } from 'primeng/divider';
import { ToggleSwitchModule } from 'primeng/toggleswitch';
import { MessageService } from 'primeng/api';

import { ApiService } from '../../core/services/api.service';
import { ContextService } from '../../core/services/context.service';
import type { Estudiante } from '../../core/models';

interface ExpedienteMedico {
  id: string;
  estudiante_id: string;
  tipo_sangre: string | null;
  alergias: string | null;
  medicamentos_autorizados: string | null;
  condiciones_cronicas: string | null;
  observaciones_generales: string | null;
}

interface IncidenteMedico {
  id: string;
  fecha_incidente: string;
  descripcion: string;
  tratamiento_aplicado: string | null;
  requirio_traslado: boolean;
  notificado_tutor: boolean;
}

@Component({
  selector: 'app-medico',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    TableModule, ButtonModule, InputTextModule, TextareaModule, CardModule,
    TagModule, ToastModule, DialogModule, DividerModule, ToggleSwitchModule,
  ],
  providers: [MessageService],
  template: `
    <p-toast />

    <div class="page-header">
      <div>
        <h2>Expediente Médico</h2>
        <p class="subtitle">Ficha médica y registro de incidentes por alumno</p>
      </div>
    </div>

    <!-- Buscador de alumno -->
    <div class="search-bar">
      <input
        pInputText
        [(ngModel)]="buscarTerm"
        placeholder="Buscar alumno por nombre o matrícula..."
        style="width: 340px"
        (keyup.enter)="buscarAlumno()"
      />
      <p-button label="Buscar" icon="pi pi-search" (onClick)="buscarAlumno()" [loading]="buscando()" />
    </div>

    <!-- Resultados búsqueda -->
    @if (resultados().length > 0 && !alumnoSeleccionado()) {
      <p-table [value]="resultados()" styleClass="p-datatable-sm" [rows]="10">
        <ng-template pTemplate="header">
          <tr>
            <th>Matrícula</th><th>Nombre</th><th></th>
          </tr>
        </ng-template>
        <ng-template pTemplate="body" let-a>
          <tr>
            <td>{{ a.matricula }}</td>
            <td>{{ a.persona?.apellido_paterno }} {{ a.persona?.nombre }}</td>
            <td>
              <p-button label="Ver expediente" [size]="'small'" (onClick)="seleccionar(a)" />
            </td>
          </tr>
        </ng-template>
      </p-table>
    }

    <!-- Expediente del alumno seleccionado -->
    @if (alumnoSeleccionado()) {
      <div class="expediente-layout">

        <!-- Ficha base -->
        <p-card>
          <ng-template pTemplate="title">
            <div class="card-title-row">
              <span>{{ alumnoSeleccionado()!.persona?.apellido_paterno }} {{ alumnoSeleccionado()!.persona?.nombre }}</span>
              <span class="matricula-badge">{{ alumnoSeleccionado()!.matricula }}</span>
            </div>
          </ng-template>

          @if (expediente()) {
            <div class="expediente-form">
              <div class="field-row">
                <div class="field">
                  <label>Tipo de sangre</label>
                  <input pInputText [(ngModel)]="expediente()!.tipo_sangre" style="width:100px" />
                </div>
              </div>
              <div class="field">
                <label>Alergias</label>
                <textarea pTextarea [(ngModel)]="expediente()!.alergias" rows="2" style="width:100%"></textarea>
              </div>
              <div class="field">
                <label>Medicamentos autorizados</label>
                <textarea pTextarea [(ngModel)]="expediente()!.medicamentos_autorizados" rows="2" style="width:100%"></textarea>
              </div>
              <div class="field">
                <label>Condiciones crónicas</label>
                <textarea pTextarea [(ngModel)]="expediente()!.condiciones_cronicas" rows="2" style="width:100%"></textarea>
              </div>
              <div class="field">
                <label>Observaciones generales</label>
                <textarea pTextarea [(ngModel)]="expediente()!.observaciones_generales" rows="2" style="width:100%"></textarea>
              </div>
              <div style="margin-top:0.75rem">
                <p-button label="Guardar expediente" icon="pi pi-save" [loading]="savingExp()" (onClick)="guardarExpediente()" />
              </div>
            </div>
          } @else {
            <p style="color:var(--text-secondary)">Sin expediente médico registrado.</p>
            <p-button label="Crear expediente" icon="pi pi-plus" styleClass="mt-2" (onClick)="crearExpediente()" />
          }
        </p-card>

        <!-- Incidentes -->
        <p-card>
          <ng-template pTemplate="title">
            <div class="card-title-row">
              <span>Incidentes médicos</span>
              <p-button label="Registrar incidente" icon="pi pi-plus" [size]="'small'" (onClick)="abrirIncidente()" />
            </div>
          </ng-template>

          <p-table [value]="incidentes()" styleClass="p-datatable-sm" [rows]="10">
            <ng-template pTemplate="header">
              <tr>
                <th style="width:130px">Fecha</th>
                <th>Descripción</th>
                <th style="width:90px;text-align:center">Traslado</th>
                <th style="width:90px;text-align:center">Tutor notif.</th>
              </tr>
            </ng-template>
            <ng-template pTemplate="body" let-inc>
              <tr>
                <td>{{ inc.fecha_incidente | date:'dd/MM/yyyy HH:mm' }}</td>
                <td style="max-width:260px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">
                  {{ inc.descripcion }}
                </td>
                <td style="text-align:center">
                  <p-tag [value]="inc.requirio_traslado ? 'Sí' : 'No'" [severity]="inc.requirio_traslado ? 'danger' : 'success'" />
                </td>
                <td style="text-align:center">
                  <p-tag [value]="inc.notificado_tutor ? 'Sí' : 'Pendiente'" [severity]="inc.notificado_tutor ? 'success' : 'warn'" />
                </td>
              </tr>
            </ng-template>
            <ng-template pTemplate="emptymessage">
              <tr><td [colSpan]="4" style="text-align:center;color:#94A3B8;padding:1.5rem">Sin incidentes registrados</td></tr>
            </ng-template>
          </p-table>
        </p-card>

      </div>
    }

    <!-- Dialog incidente -->
    <p-dialog [(visible)]="showIncidenteDialog" header="Registrar Incidente Médico" [modal]="true" [style]="{width:'460px'}">
      <div class="form-fields">
        <div class="field">
          <label>Descripción del incidente</label>
          <textarea pTextarea [(ngModel)]="incForm.descripcion" rows="3" style="width:100%"></textarea>
        </div>
        <div class="field">
          <label>Tratamiento aplicado</label>
          <textarea pTextarea [(ngModel)]="incForm.tratamiento_aplicado" rows="2" style="width:100%"></textarea>
        </div>
        <div class="field-inline">
          <p-toggleSwitch [(ngModel)]="incForm.requirio_traslado" />
          <label>Requirió traslado</label>
        </div>
        <div class="field-inline">
          <p-toggleSwitch [(ngModel)]="incForm.notificado_tutor" />
          <label>Tutor notificado</label>
        </div>
      </div>
      <ng-template pTemplate="footer">
        <p-button label="Cancelar" severity="secondary" [text]="true" (onClick)="showIncidenteDialog = false" />
        <p-button label="Guardar" icon="pi pi-save" [loading]="savingInc()" (onClick)="guardarIncidente()" />
      </ng-template>
    </p-dialog>
  `,
  styles: [`
    .search-bar { display: flex; gap: 0.75rem; margin-bottom: 1.25rem; align-items: center; }
    .expediente-layout { display: grid; grid-template-columns: 1fr 1fr; gap: 1.25rem; margin-top: 1rem; }
    @media (max-width: 900px) { .expediente-layout { grid-template-columns: 1fr; } }
    .card-title-row { display: flex; justify-content: space-between; align-items: center; }
    .matricula-badge { background: var(--nevadi-red-lighter); color: var(--nevadi-red-dark); border-radius: 12px; padding: 0.15rem 0.6rem; font-size: 0.78rem; font-weight: 700; }
    .expediente-form { display: flex; flex-direction: column; gap: 0.75rem; }
    .field { display: flex; flex-direction: column; gap: 0.2rem; }
    .field label { font-size: 0.8rem; font-weight: 600; color: var(--text-secondary); }
    .field-row { display: flex; gap: 1rem; }
    .form-fields { display: flex; flex-direction: column; gap: 0.75rem; }
    .field-inline { display: flex; align-items: center; gap: 0.5rem; }
    .field-inline label { font-size: 0.85rem; }
  `],
})
export class MedicoComponent implements OnInit {
  private readonly api = inject(ApiService);
  readonly ctx = inject(ContextService);
  private readonly msg = inject(MessageService);

  buscarTerm = '';
  buscando  = signal(false);
  resultados = signal<Estudiante[]>([]);
  alumnoSeleccionado = signal<Estudiante | null>(null);
  expediente = signal<ExpedienteMedico | null>(null);
  incidentes = signal<IncidenteMedico[]>([]);
  savingExp = signal(false);
  savingInc = signal(false);
  showIncidenteDialog = false;
  incForm = { descripcion: '', tratamiento_aplicado: '', requirio_traslado: false, notificado_tutor: false };

  ngOnInit(): void {}

  buscarAlumno(): void {
    if (!this.buscarTerm.trim()) return;
    this.buscando.set(true);
    const plantelId = this.ctx.plantel()?.id;
    this.api.get<Estudiante[]>('/alumnos', { buscar: this.buscarTerm, plantel_id: plantelId })
      .subscribe({ next: r => { this.resultados.set(r); this.buscando.set(false); }, error: () => this.buscando.set(false) });
  }

  seleccionar(a: Estudiante): void {
    this.alumnoSeleccionado.set(a);
    this.resultados.set([]);
    this.cargarExpediente(a.id);
    this.cargarIncidentes(a.id);
  }

  private cargarExpediente(id: string): void {
    this.api.get<ExpedienteMedico>(`/expedientes-medicos/alumno/${id}`)
      .subscribe({ next: e => this.expediente.set(e), error: () => this.expediente.set(null) });
  }

  private cargarIncidentes(id: string): void {
    this.api.get<IncidenteMedico[]>(`/incidentes-medicos/alumno/${id}`)
      .subscribe(r => this.incidentes.set(r));
  }

  crearExpediente(): void {
    const alumno = this.alumnoSeleccionado();
    if (!alumno) return;
    this.api.post<ExpedienteMedico>('/expedientes-medicos', { estudiante_id: alumno.id }).subscribe({
      next: e => { this.expediente.set(e); this.msg.add({ severity: 'success', summary: 'Expediente creado' }); },
      error: (e) => this.msg.add({ severity: 'error', detail: e.error?.detail }),
    });
  }

  guardarExpediente(): void {
    const exp = this.expediente();
    if (!exp) return;
    this.savingExp.set(true);
    const { estudiante_id, ...datos } = exp as any;
    this.api.put(`/expedientes-medicos/${exp.id}`, datos).subscribe({
      next: () => { this.savingExp.set(false); this.msg.add({ severity: 'success', summary: 'Expediente actualizado' }); },
      error: () => this.savingExp.set(false),
    });
  }

  abrirIncidente(): void {
    this.incForm = { descripcion: '', tratamiento_aplicado: '', requirio_traslado: false, notificado_tutor: false };
    this.showIncidenteDialog = true;
  }

  guardarIncidente(): void {
    const alumno = this.alumnoSeleccionado();
    if (!alumno || !this.incForm.descripcion) return;
    this.savingInc.set(true);
    const payload = { ...this.incForm, estudiante_id: alumno.id };
    this.api.post<IncidenteMedico>('/incidentes-medicos', payload).subscribe({
      next: (r) => {
        this.incidentes.update(list => [r, ...list]);
        this.showIncidenteDialog = false;
        this.savingInc.set(false);
        this.msg.add({ severity: 'success', summary: 'Incidente registrado' });
      },
      error: () => this.savingInc.set(false),
    });
  }
}
