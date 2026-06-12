import { Component, OnInit, signal, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { TextareaModule } from 'primeng/textarea';
import { TagModule } from 'primeng/tag';
import { ToastModule } from 'primeng/toast';
import { DatePickerModule } from 'primeng/datepicker';
import { CheckboxModule } from 'primeng/checkbox';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { MessageService, ConfirmationService } from 'primeng/api';
import { ApexNotificationService } from 'apex-component-library';

interface Licencia {
  id: string;
  personal_id: string;
  tipo_licencia: string;
  fecha_inicio: string;
  fecha_fin: string;
  dias_habiles: number;
  estado: string;
  motivo: string | null;
  observaciones_rh: string | null;
  sustituto_id: string | null;
  aprobado_por: string | null;
  fecha_aprobacion: string | null;
  con_goce_sueldo: boolean;
  row_version: number;
  fecha_creacion: string;
}

interface LicenciaForm {
  personal_id: string;
  tipo_licencia: string;
  fecha_inicio: Date | null;
  fecha_fin: Date | null;
  motivo: string;
  con_goce_sueldo: boolean;
}

@Component({
  selector: 'app-licencias',
  standalone: true,
  imports: [
    CommonModule, FormsModule, ReactiveFormsModule,
    TableModule, ButtonModule, DialogModule,
    InputTextModule, SelectModule, TextareaModule,
    TagModule, ToastModule, DatePickerModule,
    CheckboxModule, ConfirmDialogModule,
  ],
  providers: [MessageService, ConfirmationService],
  template: `
    <p-toast />
    <p-confirmDialog />

    <div class="apex-page">
      <!-- Toolbar -->
      <div class="apex-toolbar">
        <h2 class="apex-title">Licencias y Permisos de Personal</h2>
        <div class="apex-toolbar-actions">
          <!-- Filtros rápidos -->
          <p-select
            [options]="estadoOpts"
            [(ngModel)]="filtroEstado"
            placeholder="Todos los estados"
            [showClear]="true"
            (onChange)="cargar()"
            style="min-width:180px">
          </p-select>
          <p-select
            [options]="tipoOpts"
            [(ngModel)]="filtroTipo"
            placeholder="Todos los tipos"
            [showClear]="true"
            (onChange)="cargar()"
            style="min-width:180px">
          </p-select>
          <p-button
            label="Nueva Licencia"
            icon="pi pi-plus"
            size="small"
            (onClick)="abrirNueva()">
          </p-button>
        </div>
      </div>

      <!-- Grid -->
      <p-table
        [value]="licencias()"
        [loading]="cargando()"
        [paginator]="true"
        [rows]="15"
        [rowsPerPageOptions]="[15,30,50]"
        stripedRows
        styleClass="p-datatable-sm">

        <ng-template pTemplate="header">
          <tr>
            <th style="width:180px">Personal</th>
            <th style="width:140px">Tipo</th>
            <th style="width:110px">Fecha Inicio</th>
            <th style="width:110px">Fecha Fin</th>
            <th style="width:80px" pSortableColumn="dias_habiles">Días<p-sortIcon field="dias_habiles"/></th>
            <th style="width:90px">Goce</th>
            <th style="width:120px">Estado</th>
            <th>Motivo</th>
            <th style="width:160px">Acciones</th>
          </tr>
        </ng-template>

        <ng-template pTemplate="body" let-lic>
          <tr>
            <td><code class="text-xs">{{ lic.personal_id | slice:0:8 }}…</code></td>
            <td>{{ tipoLabel(lic.tipo_licencia) }}</td>
            <td>{{ lic.fecha_inicio }}</td>
            <td>{{ lic.fecha_fin }}</td>
            <td class="text-center font-semibold">{{ lic.dias_habiles }}</td>
            <td class="text-center">
              <span [class]="lic.con_goce_sueldo ? 'text-green-600 font-semibold' : 'text-gray-500'">
                {{ lic.con_goce_sueldo ? 'Con goce' : 'Sin goce' }}
              </span>
            </td>
            <td>
              <p-tag [value]="lic.estado" [severity]="estadoSeverity(lic.estado)" />
            </td>
            <td class="text-sm text-gray-600">{{ lic.motivo | slice:0:60 }}</td>
            <td>
              <div class="flex gap-1">
                <p-button icon="pi pi-eye"    size="small" [text]="true" severity="info"
                  (onClick)="verDetalle(lic)" pTooltip="Ver detalle" />
                @if (lic.estado === 'PENDIENTE') {
                  <p-button icon="pi pi-check" size="small" [text]="true" severity="success"
                    (onClick)="aprobar(lic)" pTooltip="Aprobar" />
                  <p-button icon="pi pi-times" size="small" [text]="true" severity="danger"
                    (onClick)="rechazar(lic)" pTooltip="Rechazar" />
                  <p-button icon="pi pi-ban"   size="small" [text]="true" severity="warn"
                    (onClick)="cancelar(lic)" pTooltip="Cancelar" />
                }
              </div>
            </td>
          </tr>
        </ng-template>

        <ng-template pTemplate="emptymessage">
          <tr><td colspan="9" class="text-center py-4 text-gray-500">Sin licencias registradas.</td></tr>
        </ng-template>
      </p-table>
    </div>

    <!-- Dialog: Nueva Licencia -->
    <p-dialog
      header="Solicitar Licencia o Permiso"
      [(visible)]="dialogNueva"
      [modal]="true"
      [style]="{width:'580px'}"
      [draggable]="false">

      <div class="grid grid-cols-2 gap-3 p-2">
        <div class="col-span-2 flex flex-col gap-1">
          <label class="text-sm font-medium">ID de Personal <span class="text-red-500">*</span></label>
          <input pInputText [(ngModel)]="form.personal_id" placeholder="UUID del empleado" />
        </div>

        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium">Tipo de Licencia <span class="text-red-500">*</span></label>
          <p-select [options]="tipoOpts" [(ngModel)]="form.tipo_licencia" placeholder="Selecciona…" />
        </div>

        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium">Goce de Sueldo</label>
          <div class="flex items-center gap-2 mt-2">
            <p-checkbox [(ngModel)]="form.con_goce_sueldo" [binary]="true" inputId="goce" />
            <label for="goce" class="text-sm">Con goce de sueldo</label>
          </div>
        </div>

        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium">Fecha Inicio <span class="text-red-500">*</span></label>
          <p-calendar [(ngModel)]="form.fecha_inicio" dateFormat="yy-mm-dd" [showIcon]="true" />
        </div>

        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium">Fecha Fin <span class="text-red-500">*</span></label>
          <p-calendar [(ngModel)]="form.fecha_fin" dateFormat="yy-mm-dd" [showIcon]="true" />
        </div>

        <div class="col-span-2 flex flex-col gap-1">
          <label class="text-sm font-medium">Motivo</label>
          <textarea pTextarea [(ngModel)]="form.motivo" rows="3" placeholder="Descripción del motivo…"></textarea>
        </div>
      </div>

      <ng-template pTemplate="footer">
        <p-button label="Cancelar" icon="pi pi-times" [text]="true" (onClick)="dialogNueva=false" />
        <p-button label="Guardar" icon="pi pi-save" [loading]="guardando()" (onClick)="guardar()" />
      </ng-template>
    </p-dialog>

    <!-- Dialog: Detalle -->
    <p-dialog
      header="Detalle de Licencia"
      [(visible)]="dialogDetalle"
      [modal]="true"
      [style]="{width:'560px'}"
      [draggable]="false">
      @if (seleccionada()) {
        <div class="grid grid-cols-2 gap-y-3 gap-x-4 p-2 text-sm">
          <div><span class="font-medium">Tipo:</span> {{ tipoLabel(seleccionada()!.tipo_licencia) }}</div>
          <div><span class="font-medium">Estado:</span>
            <p-tag [value]="seleccionada()!.estado" [severity]="estadoSeverity(seleccionada()!.estado)" class="ml-1" />
          </div>
          <div><span class="font-medium">Inicio:</span> {{ seleccionada()!.fecha_inicio }}</div>
          <div><span class="font-medium">Fin:</span> {{ seleccionada()!.fecha_fin }}</div>
          <div><span class="font-medium">Días hábiles:</span> {{ seleccionada()!.dias_habiles }}</div>
          <div><span class="font-medium">Goce sueldo:</span> {{ seleccionada()!.con_goce_sueldo ? 'Sí' : 'No' }}</div>
          @if (seleccionada()!.motivo) {
            <div class="col-span-2"><span class="font-medium">Motivo:</span> {{ seleccionada()!.motivo }}</div>
          }
          @if (seleccionada()!.observaciones_rh) {
            <div class="col-span-2"><span class="font-medium">Observaciones RH:</span> {{ seleccionada()!.observaciones_rh }}</div>
          }
          @if (seleccionada()!.fecha_aprobacion) {
            <div class="col-span-2"><span class="font-medium">Aprobada:</span> {{ seleccionada()!.fecha_aprobacion }}</div>
          }
          <div class="col-span-2 text-gray-400 text-xs">Versión {{ seleccionada()!.row_version }} — {{ seleccionada()!.fecha_creacion }}</div>
        </div>
      }
    </p-dialog>

    <!-- Dialog: Rechazar -->
    <p-dialog
      header="Rechazar Licencia"
      [(visible)]="dialogRechazar"
      [modal]="true"
      [style]="{width:'420px'}"
      [draggable]="false">
      <div class="flex flex-col gap-2 p-2">
        <label class="text-sm font-medium">Motivo de rechazo <span class="text-red-500">*</span></label>
        <textarea pTextarea [(ngModel)]="motivoRechazo" rows="3" placeholder="Indique el motivo…"></textarea>
      </div>
      <ng-template pTemplate="footer">
        <p-button label="Cancelar" [text]="true" (onClick)="dialogRechazar=false" />
        <p-button label="Confirmar rechazo" severity="danger" [loading]="guardando()"
          [disabled]="motivoRechazo.length < 5"
          (onClick)="confirmarRechazo()" />
      </ng-template>
    </p-dialog>
  `,
  styles: [`
    .apex-page { padding: 1rem; }
    .apex-toolbar {
      display: flex; align-items: center; justify-content: space-between;
      margin-bottom: 1rem; flex-wrap: wrap; gap: 0.5rem;
    }
    .apex-title { margin: 0; font-size: 1.25rem; font-weight: 600; }
    .apex-toolbar-actions { display: flex; gap: 0.5rem; align-items: center; flex-wrap: wrap; }
    code.text-xs { font-family: monospace; font-size: 0.75rem; }
  `],
})
export class LicenciasComponent implements OnInit {
  private http = inject(HttpClient);
  private notify = inject(ApexNotificationService);

  licencias  = signal<Licencia[]>([]);
  cargando   = signal(false);
  guardando  = signal(false);
  seleccionada = signal<Licencia | null>(null);

  dialogNueva    = false;
  dialogDetalle  = false;
  dialogRechazar = false;

  filtroEstado = '';
  filtroTipo   = '';
  motivoRechazo = '';
  licenciaARechazar: Licencia | null = null;

  form: LicenciaForm = this.resetForm();

  estadoOpts = [
    { label: 'Pendiente',  value: 'PENDIENTE' },
    { label: 'Aprobada',   value: 'APROBADA' },
    { label: 'Rechazada',  value: 'RECHAZADA' },
    { label: 'Cancelada',  value: 'CANCELADA' },
  ];

  tipoOpts = [
    { label: 'Médica',        value: 'MEDICA' },
    { label: 'Maternidad',    value: 'MATERNIDAD' },
    { label: 'Paternidad',    value: 'PATERNIDAD' },
    { label: 'Duelo',         value: 'DUELO' },
    { label: 'Personal',      value: 'PERSONAL' },
    { label: 'Comisión',      value: 'COMISION' },
    { label: 'Capacitación',  value: 'CAPACITACION' },
    { label: 'Otro',          value: 'OTRO' },
  ];

  ngOnInit() { this.cargar(); }

  cargar() {
    this.cargando.set(true);
    const params: Record<string, string> = {};
    if (this.filtroEstado) params['estado'] = this.filtroEstado;
    if (this.filtroTipo)   params['tipo']   = this.filtroTipo;
    this.http.get<Licencia[]>('/api/v1/licencias', { params }).subscribe({
      next: data => { this.licencias.set(data); this.cargando.set(false); },
      error: () => { this.cargando.set(false); this.notify.error('Error al cargar licencias'); },
    });
  }

  abrirNueva() {
    this.form = this.resetForm();
    this.dialogNueva = true;
  }

  guardar() {
    if (!this.form.personal_id || !this.form.tipo_licencia || !this.form.fecha_inicio || !this.form.fecha_fin) {
      this.notify.warning('Complete los campos obligatorios');
      return;
    }
    this.guardando.set(true);
    const payload = {
      personal_id:     this.form.personal_id,
      tipo_licencia:   this.form.tipo_licencia,
      fecha_inicio:    this.toDateStr(this.form.fecha_inicio),
      fecha_fin:       this.toDateStr(this.form.fecha_fin),
      motivo:          this.form.motivo || null,
      con_goce_sueldo: this.form.con_goce_sueldo,
    };
    this.http.post<Licencia>('/api/v1/licencias', payload).subscribe({
      next: () => {
        this.guardando.set(false);
        this.dialogNueva = false;
        this.notify.success('Licencia registrada');
        this.cargar();
      },
      error: () => { this.guardando.set(false); this.notify.error('Error al guardar'); },
    });
  }

  verDetalle(lic: Licencia) {
    this.seleccionada.set(lic);
    this.dialogDetalle = true;
  }

  aprobar(lic: Licencia) {
    this.http.post<Licencia>(`/api/v1/licencias/${lic.id}/aprobar`, null).subscribe({
      next: () => { this.notify.success('Licencia aprobada'); this.cargar(); },
      error: (e) => this.notify.error(e.error?.detail ?? 'Error al aprobar'),
    });
  }

  rechazar(lic: Licencia) {
    this.licenciaARechazar = lic;
    this.motivoRechazo = '';
    this.dialogRechazar = true;
  }

  confirmarRechazo() {
    if (!this.licenciaARechazar) return;
    this.guardando.set(true);
    this.http.post<Licencia>(
      `/api/v1/licencias/${this.licenciaARechazar.id}/rechazar`,
      null,
      { params: { motivo_rechazo: this.motivoRechazo } }
    ).subscribe({
      next: () => {
        this.guardando.set(false);
        this.dialogRechazar = false;
        this.notify.success('Licencia rechazada');
        this.cargar();
      },
      error: (e) => { this.guardando.set(false); this.notify.error(e.error?.detail ?? 'Error'); },
    });
  }

  cancelar(lic: Licencia) {
    this.http.delete(`/api/v1/licencias/${lic.id}`).subscribe({
      next: () => { this.notify.success('Licencia cancelada'); this.cargar(); },
      error: (e) => this.notify.error(e.error?.detail ?? 'Error al cancelar'),
    });
  }

  estadoSeverity(estado: string): 'warn' | 'success' | 'danger' | 'secondary' {
    const map: Record<string, 'warn' | 'success' | 'danger' | 'secondary'> = {
      PENDIENTE: 'warn', APROBADA: 'success', RECHAZADA: 'danger', CANCELADA: 'secondary',
    };
    return map[estado] ?? 'secondary';
  }

  tipoLabel(tipo: string): string {
    return this.tipoOpts.find(o => o.value === tipo)?.label ?? tipo;
  }

  private resetForm(): LicenciaForm {
    return { personal_id: '', tipo_licencia: '', fecha_inicio: null, fecha_fin: null, motivo: '', con_goce_sueldo: true };
  }

  private toDateStr(d: Date | null): string {
    if (!d) return '';
    return d.toISOString().split('T')[0];
  }
}
