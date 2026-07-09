import { Component, OnDestroy, OnInit, signal, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { Subject, takeUntil } from 'rxjs';
import { ApiService } from '../../core/services/api.service';
import { ButtonModule } from 'primeng/button';
import { InteractiveGridComponent, ColumnConfig } from '../../shared/components/interactive-grid/interactive-grid.component';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { TextareaModule } from 'primeng/textarea';
import { TagModule } from 'primeng/tag';
import { ToastModule } from 'primeng/toast';
import { DatePickerModule } from 'primeng/datepicker';
import { CheckboxModule } from 'primeng/checkbox';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { AutoCompleteModule } from 'primeng/autocomplete';
import { MessageService, ConfirmationService } from 'primeng/api';
import { ApexNotificationService } from 'apex-component-library';

interface Licencia {
  id: string;
  personal_id: string;
  nombre_completo?: string;
  tipo_licencia: string;
  fecha_inicio: string;
  fecha_fin: string;
  dias_habiles: number;
  estado: string;
  motivo: string | null;
  observaciones_rh: string | null;
  con_goce_sueldo: boolean;
  row_version: number;
  fecha_creacion: string;
}

/**
 * Módulo de gestión de licencias y permisos del personal (DP-006).
 * Permite registrar, aprobar y consultar licencias médicas, personales y de capacitación.
 * Requiere nivelAcceso 4 (AdminPlantel) o superior.
 */
@Component({
  selector: 'app-licencias',
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
  imports: [
    CommonModule, FormsModule, ReactiveFormsModule,
    ButtonModule, DialogModule,
    InputTextModule, SelectModule, TextareaModule,
    TagModule, ToastModule, DatePickerModule,
    CheckboxModule, ConfirmDialogModule, AutoCompleteModule,
    InteractiveGridComponent,
  ],
  providers: [MessageService, ConfirmationService],
  template: `
    <p-toast />
    <p-confirmDialog />

    <div class="apex-page">
      <div class="apex-toolbar">
        <h2 class="apex-title">Licencias y Permisos de Personal</h2>
        <div class="apex-toolbar-actions">
          <input pInputText [(ngModel)]="filtroNombre" (input)="onFiltroNombre()"
            placeholder="Buscar por nombre…" style="min-width:200px" />
          <p-select [options]="estadoOpts" [(ngModel)]="filtroEstado" placeholder="Todos los estados"
            [showClear]="true" (onChange)="cargar()" style="min-width:160px" />
          <p-select [options]="tipoOpts" [(ngModel)]="filtroTipo" placeholder="Todos los tipos"
            [showClear]="true" (onChange)="cargar()" style="min-width:160px" />
          <p-button label="Nueva Licencia" icon="pi pi-plus" size="small" (onClick)="abrirNueva()" />
        </div>
      </div>

      <app-interactive-grid
        [data]="licenciasFlat()"
        [columns]="licenciaColumns"
        [loading]="cargando()"
        [showDelete]="false"
        (rowSelected)="verDetalle($event)"
      />
    </div>

    <!-- Dialog: Nueva Licencia -->
    <p-dialog header="Solicitar Licencia o Permiso" [(visible)]="dialogNueva"
      [modal]="true" [style]="{width:'580px'}" [draggable]="false">

      <div class="grid grid-cols-2 gap-3 p-2">
        <div class="col-span-2 flex flex-col gap-1">
          <label class="text-sm font-medium">Personal <span class="text-red-500">*</span></label>
          <p-autocomplete
            [(ngModel)]="personalSeleccionado"
            [suggestions]="personalSugerencias()"
            (completeMethod)="buscarPersonal($event)"
            optionLabel="label"
            [dropdown]="false"
            placeholder="Escriba nombre del empleado…"
            [forceSelection]="true"
            [delay]="300"
            style="width:100%"
          />
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
          <p-datepicker [(ngModel)]="form.fecha_inicio" dateFormat="yy-mm-dd" [showIcon]="true" />
        </div>

        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium">Fecha Fin <span class="text-red-500">*</span></label>
          <p-datepicker [(ngModel)]="form.fecha_fin" dateFormat="yy-mm-dd" [showIcon]="true" />
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
    <p-dialog header="Detalle de Licencia" [(visible)]="dialogDetalle"
      [modal]="true" [style]="{width:'560px'}" [draggable]="false">
      @if (seleccionada()) {
        <div class="grid grid-cols-2 gap-y-3 gap-x-4 p-2 text-sm">
          <div class="col-span-2"><span class="font-medium">Personal:</span> {{ seleccionada()!.nombre_completo || seleccionada()!.personal_id }}</div>
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
        </div>
      }
    </p-dialog>

    <!-- Dialog: Rechazar -->
    <p-dialog header="Rechazar Licencia" [(visible)]="dialogRechazar"
      [modal]="true" [style]="{width:'420px'}" [draggable]="false">
      <div class="flex flex-col gap-2 p-2">
        <label class="text-sm font-medium">Motivo de rechazo <span class="text-red-500">*</span></label>
        <textarea pTextarea [(ngModel)]="motivoRechazo" rows="3" placeholder="Indique el motivo…"></textarea>
      </div>
      <ng-template pTemplate="footer">
        <p-button label="Cancelar" [text]="true" (onClick)="dialogRechazar=false" />
        <p-button label="Confirmar rechazo" severity="danger" [loading]="guardando()"
          [disabled]="motivoRechazo.length < 5" (onClick)="confirmarRechazo()" />
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
  `],
})
export class LicenciasComponent implements OnInit implements OnInit, OnDestroy {
  private destroy$ = new Subject<void>();
  private api = inject(ApiService);
  private notify = inject(ApexNotificationService);

  licencias = signal<Licencia[]>([]);
  personalSugerencias = signal<{ label: string; value: string }[]>([]);
  personalSeleccionado: { label: string; value: string } | null = null;

  readonly licenciaColumns: ColumnConfig[] = [
    { field: 'nombrePersonal',  header: 'Personal',     sortable: true, filterable: true },
    { field: 'tipoLabel',       header: 'Tipo',         sortable: true, filterable: true,  width: '140px' },
    { field: 'fecha_inicio',    header: 'Fecha Inicio', sortable: true, filterable: false, width: '110px' },
    { field: 'fecha_fin',       header: 'Fecha Fin',    sortable: true, filterable: false, width: '110px' },
    { field: 'dias_habiles',    header: 'Días',         sortable: true, filterable: false, width: '80px' },
    { field: 'goceLabel',       header: 'Goce',         sortable: true, filterable: true,  width: '90px' },
    { field: 'estado',          header: 'Estado',       sortable: true, filterable: true,  width: '120px' },
    { field: 'motivoCorto',     header: 'Motivo',       sortable: false, filterable: true },
  ];

  readonly licenciasFlat = computed(() =>
    this.licencias().map(lic => ({
      ...lic,
      nombrePersonal: lic.nombre_completo || lic.personal_id,
      tipoLabel: this.tipoOpts.find(o => o.value === lic.tipo_licencia)?.label ?? lic.tipo_licencia,
      goceLabel: lic.con_goce_sueldo ? 'Con goce' : 'Sin goce',
      motivoCorto: lic.motivo ? lic.motivo.slice(0, 60) : '',
    }))
  );

  cargando  = signal(false);
  guardando = signal(false);
  seleccionada = signal<Licencia | null>(null);

  dialogNueva    = false;
  dialogDetalle  = false;
  dialogRechazar = false;

  filtroEstado = '';
  filtroTipo   = '';
  filtroNombre = '';
  motivoRechazo = '';
  licenciaARechazar: Licencia | null = null;

  form = this.resetForm();

  private filtroTimer: ReturnType<typeof setTimeout> | null = null;

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

  onFiltroNombre() {
    if (this.filtroTimer) clearTimeout(this.filtroTimer);
    this.filtroTimer = setTimeout(() => this.cargar(), 400);
  }

  cargar() {
    this.cargando.set(true);
    const params: Record<string, string> = {};
    if (this.filtroEstado) params['estado'] = this.filtroEstado;
    if (this.filtroTipo)   params['tipo']   = this.filtroTipo;
    if (this.filtroNombre) params['q']      = this.filtroNombre;
    this.api.get<Licencia[]>('/licencias', params).subscribe({
      next: data => { this.licencias.set(data); this.cargando.set(false); },
      error: () => { this.cargando.set(false); this.notify.error('Error al cargar licencias'); },
    });
  }

  buscarPersonal(event: { query: string }) {
    if (!event.query || event.query.length < 2) { this.personalSugerencias.set([]); return; }
    this.api.get<any>('/profesores', { buscar: event.query }).subscribe({
      next: res => {
        const data = res?.data ?? res ?? [];
        this.personalSugerencias.set(data.map((p: any) => ({
          label: [p.nombre ?? p.persona?.nombre, p.apellido_paterno ?? p.persona?.apellido_paterno, p.apellido_materno ?? p.persona?.apellido_materno].filter(Boolean).join(' '),
          value: p.id,
        })));
      },
      error: () => this.personalSugerencias.set([]),
    });
  }

  abrirNueva() {
    this.form = this.resetForm();
    this.personalSeleccionado = null;
    this.dialogNueva = true;
  }

  guardar() {
    if (!this.personalSeleccionado?.value || !this.form.tipo_licencia || !this.form.fecha_inicio || !this.form.fecha_fin) {
      this.notify.warning('Complete los campos obligatorios');
      return;
    }
    this.guardando.set(true);
    const payload = {
      personal_id:     this.personalSeleccionado.value,
      tipo_licencia:   this.form.tipo_licencia,
      fecha_inicio:    this.toDateStr(this.form.fecha_inicio),
      fecha_fin:       this.toDateStr(this.form.fecha_fin),
      motivo:          this.form.motivo || null,
      con_goce_sueldo: this.form.con_goce_sueldo,
    };
    this.api.post<Licencia>('/licencias', payload).subscribe({
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
    this.api.post<Licencia>(`/licencias/${lic.id}/aprobar`, null).subscribe({
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
    this.api.post<Licencia>(
      `/licencias/${this.licenciaARechazar.id}/rechazar?motivo_rechazo=${encodeURIComponent(this.motivoRechazo)}`,
      null
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
    this.api.delete(`/licencias/${lic.id}`).subscribe({
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

  private resetForm() {
    return { tipo_licencia: '', fecha_inicio: null as Date | null, fecha_fin: null as Date | null, motivo: '', con_goce_sueldo: true };
  }

  private toDateStr(d: Date | null): string {
    if (!d) return '';
    return d.toISOString().split('T')[0];
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
