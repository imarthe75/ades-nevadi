import { Component, OnInit, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
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
import { InputNumberModule } from 'primeng/inputnumber';
import { CardModule } from 'primeng/card';
import { MessageService } from 'primeng/api';
import { ApexNotificationService } from 'apex-component-library';

interface Capacitacion {
  id: string;
  docente_id: string;
  nombre: string;
  descripcion: string | null;
  institucion: string;
  tipo_certificacion: string;
  modalidad: string;
  fecha_inicio: string;
  fecha_fin: string;
  duracion_hrs: number;
  area_formacion: string | null;
  certificado_url: string | null;
  folio_certificado: string | null;
  validado_rh: boolean;
  fecha_validacion: string | null;
  row_version: number;
  fecha_creacion: string;
}

interface ResumenCapacitaciones {
  docente_id: string;
  total_hrs: number;
  total_eventos: number;
  por_tipo: Record<string, number>;
  por_modalidad: Record<string, number>;
  validadas: number;
}

interface CapacitacionForm {
  docente_id: string;
  nombre: string;
  descripcion: string;
  institucion: string;
  tipo_certificacion: string;
  modalidad: string;
  fecha_inicio: Date | null;
  fecha_fin: Date | null;
  duracion_hrs: number;
  area_formacion: string;
  folio_certificado: string;
  certificado_url: string;
}

@Component({
  selector: 'app-capacitaciones',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    TableModule, ButtonModule, DialogModule,
    InputTextModule, SelectModule, TextareaModule,
    TagModule, ToastModule, DatePickerModule,
    InputNumberModule, CardModule,
  ],
  providers: [MessageService],
  template: `
    <p-toast />

    <div class="apex-page">
      <!-- Toolbar -->
      <div class="apex-toolbar">
        <h2 class="apex-title">Capacitaciones y Certificaciones Docentes</h2>
        <div class="apex-toolbar-actions">
          <p-select
            [options]="tipoOpts"
            [(ngModel)]="filtroTipo"
            placeholder="Todos los tipos"
            [showClear]="true"
            (onChange)="cargar()"
            style="min-width:160px">
          </p-select>
          <p-select
            [options]="modalidadOpts"
            [(ngModel)]="filtroModalidad"
            placeholder="Modalidad"
            [showClear]="true"
            (onChange)="cargar()"
            style="min-width:140px">
          </p-select>
          <p-select
            [options]="validadoOpts"
            [(ngModel)]="filtroValidado"
            placeholder="Validación"
            [showClear]="true"
            (onChange)="cargar()"
            style="min-width:130px">
          </p-select>
          <p-button
            label="Registrar Capacitación"
            icon="pi pi-plus"
            size="small"
            (onClick)="abrirNueva()">
          </p-button>
        </div>
      </div>

      <!-- Resumen stats (si hay filtro por docente) -->
      @if (resumen()) {
        <div class="resumen-bar">
          <div class="resumen-item">
            <span class="resumen-num">{{ resumen()!.total_hrs }}</span>
            <span class="resumen-lbl">Horas totales</span>
          </div>
          <div class="resumen-item">
            <span class="resumen-num">{{ resumen()!.total_eventos }}</span>
            <span class="resumen-lbl">Eventos</span>
          </div>
          <div class="resumen-item">
            <span class="resumen-num text-green-600">{{ resumen()!.validadas }}</span>
            <span class="resumen-lbl">Validadas</span>
          </div>
        </div>
      }

      <!-- Grid -->
      <p-table
        [value]="capacitaciones()"
        [loading]="cargando()"
        [paginator]="true"
        [rows]="15"
        [rowsPerPageOptions]="[15,30,50]"
        stripedRows
        styleClass="p-datatable-sm">

        <ng-template pTemplate="header">
          <tr>
            <th>Nombre</th>
            <th style="width:180px">Institución</th>
            <th style="width:120px">Tipo</th>
            <th style="width:100px">Modalidad</th>
            <th style="width:110px">Fecha Inicio</th>
            <th style="width:110px">Fecha Fin</th>
            <th style="width:80px" pSortableColumn="duracion_hrs">Hrs<p-sortIcon field="duracion_hrs"/></th>
            <th style="width:100px">Área</th>
            <th style="width:100px">Validado</th>
            <th style="width:120px">Acciones</th>
          </tr>
        </ng-template>

        <ng-template pTemplate="body" let-cap>
          <tr>
            <td>
              <div class="font-medium text-sm">{{ cap.nombre }}</div>
              @if (cap.folio_certificado) {
                <div class="text-xs text-gray-400">Folio: {{ cap.folio_certificado }}</div>
              }
            </td>
            <td class="text-sm">{{ cap.institucion }}</td>
            <td>{{ tipoLabel(cap.tipo_certificacion) }}</td>
            <td>
              <p-tag [value]="cap.modalidad" [severity]="modalidadSeverity(cap.modalidad)" />
            </td>
            <td>{{ cap.fecha_inicio }}</td>
            <td>{{ cap.fecha_fin }}</td>
            <td class="text-center font-semibold">{{ cap.duracion_hrs }}</td>
            <td class="text-xs">{{ cap.area_formacion ?? '—' }}</td>
            <td class="text-center">
              @if (cap.validado_rh) {
                <p-tag value="Validado" severity="success" />
              } @else {
                <p-tag value="Pendiente" severity="warn" />
              }
            </td>
            <td>
              <div class="flex gap-1">
                <p-button icon="pi pi-eye"  size="small" [text]="true" severity="info"
                  (onClick)="verDetalle(cap)" pTooltip="Ver detalle" />
                @if (cap.certificado_url) {
                  <p-button icon="pi pi-file-pdf" size="small" [text]="true" severity="secondary"
                    (onClick)="verPdf(cap.certificado_url)" pTooltip="Ver certificado" />
                }
                @if (!cap.validado_rh) {
                  <p-button icon="pi pi-verified" size="small" [text]="true" severity="success"
                    (onClick)="validar(cap)" pTooltip="Validar (RH)" />
                }
                <p-button icon="pi pi-trash" size="small" [text]="true" severity="danger"
                  (onClick)="eliminar(cap)" pTooltip="Eliminar" />
              </div>
            </td>
          </tr>
        </ng-template>

        <ng-template pTemplate="emptymessage">
          <tr><td colspan="10" class="text-center py-4 text-gray-500">Sin capacitaciones registradas.</td></tr>
        </ng-template>
      </p-table>
    </div>

    <!-- Dialog: Nueva Capacitación -->
    <p-dialog
      header="Registrar Capacitación / Certificación"
      [(visible)]="dialogNueva"
      [modal]="true"
      [style]="{width:'640px'}"
      [draggable]="false">

      <div class="grid grid-cols-2 gap-3 p-2">
        <div class="col-span-2 flex flex-col gap-1">
          <label class="text-sm font-medium">ID Docente <span class="text-red-500">*</span></label>
          <input pInputText [(ngModel)]="form.docente_id" placeholder="UUID del docente" />
        </div>

        <div class="col-span-2 flex flex-col gap-1">
          <label class="text-sm font-medium">Nombre del evento <span class="text-red-500">*</span></label>
          <input pInputText [(ngModel)]="form.nombre" placeholder="Ej: Taller de Evaluación Formativa" />
        </div>

        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium">Institución <span class="text-red-500">*</span></label>
          <input pInputText [(ngModel)]="form.institucion" placeholder="Ej: SEP, UAEMEX…" />
        </div>

        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium">Tipo <span class="text-red-500">*</span></label>
          <p-select [options]="tipoOpts" [(ngModel)]="form.tipo_certificacion" placeholder="Selecciona…" />
        </div>

        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium">Modalidad <span class="text-red-500">*</span></label>
          <p-select [options]="modalidadOpts" [(ngModel)]="form.modalidad" placeholder="Selecciona…" />
        </div>

        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium">Área de Formación</label>
          <p-select [options]="areaOpts" [(ngModel)]="form.area_formacion" placeholder="Ninguna" [showClear]="true" />
        </div>

        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium">Fecha Inicio <span class="text-red-500">*</span></label>
          <p-calendar [(ngModel)]="form.fecha_inicio" dateFormat="yy-mm-dd" [showIcon]="true" />
        </div>

        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium">Fecha Fin <span class="text-red-500">*</span></label>
          <p-calendar [(ngModel)]="form.fecha_fin" dateFormat="yy-mm-dd" [showIcon]="true" />
        </div>

        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium">Duración (hrs) <span class="text-red-500">*</span></label>
          <p-inputNumber [(ngModel)]="form.duracion_hrs" [min]="0.5" [step]="0.5" [maxFractionDigits]="1" />
        </div>

        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium">Folio Certificado</label>
          <input pInputText [(ngModel)]="form.folio_certificado" placeholder="Número de folio…" />
        </div>

        <div class="col-span-2 flex flex-col gap-1">
          <label class="text-sm font-medium">URL Certificado (MinIO)</label>
          <input pInputText [(ngModel)]="form.certificado_url" placeholder="https://…" />
        </div>

        <div class="col-span-2 flex flex-col gap-1">
          <label class="text-sm font-medium">Descripción</label>
          <textarea pTextarea [(ngModel)]="form.descripcion" rows="2" placeholder="Descripción breve…"></textarea>
        </div>
      </div>

      <ng-template pTemplate="footer">
        <p-button label="Cancelar" icon="pi pi-times" [text]="true" (onClick)="dialogNueva=false" />
        <p-button label="Guardar" icon="pi pi-save" [loading]="guardando()" (onClick)="guardar()" />
      </ng-template>
    </p-dialog>

    <!-- Dialog: Detalle -->
    <p-dialog
      header="Detalle de Capacitación"
      [(visible)]="dialogDetalle"
      [modal]="true"
      [style]="{width:'560px'}"
      [draggable]="false">
      @if (seleccionada()) {
        <div class="grid grid-cols-2 gap-y-3 gap-x-4 p-2 text-sm">
          <div class="col-span-2"><span class="font-medium">Nombre:</span> {{ seleccionada()!.nombre }}</div>
          <div><span class="font-medium">Institución:</span> {{ seleccionada()!.institucion }}</div>
          <div><span class="font-medium">Tipo:</span> {{ tipoLabel(seleccionada()!.tipo_certificacion) }}</div>
          <div><span class="font-medium">Modalidad:</span> {{ seleccionada()!.modalidad }}</div>
          <div><span class="font-medium">Área:</span> {{ seleccionada()!.area_formacion ?? '—' }}</div>
          <div><span class="font-medium">Inicio:</span> {{ seleccionada()!.fecha_inicio }}</div>
          <div><span class="font-medium">Fin:</span> {{ seleccionada()!.fecha_fin }}</div>
          <div><span class="font-medium">Horas:</span> {{ seleccionada()!.duracion_hrs }}</div>
          <div><span class="font-medium">Validado RH:</span>
            <p-tag [value]="seleccionada()!.validado_rh ? 'Sí' : 'No'"
              [severity]="seleccionada()!.validado_rh ? 'success' : 'warn'" class="ml-1" />
          </div>
          @if (seleccionada()!.folio_certificado) {
            <div class="col-span-2"><span class="font-medium">Folio:</span> {{ seleccionada()!.folio_certificado }}</div>
          }
          @if (seleccionada()!.descripcion) {
            <div class="col-span-2"><span class="font-medium">Descripción:</span> {{ seleccionada()!.descripcion }}</div>
          }
          <div class="col-span-2 text-gray-400 text-xs">Versión {{ seleccionada()!.row_version }} — {{ seleccionada()!.fecha_creacion }}</div>
        </div>
      }
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
    .resumen-bar {
      display: flex; gap: 2rem; padding: 0.75rem 1rem; background: var(--surface-100);
      border-radius: 8px; margin-bottom: 1rem; border: 1px solid var(--surface-200);
    }
    .resumen-item { display: flex; flex-direction: column; align-items: center; }
    .resumen-num { font-size: 1.5rem; font-weight: 700; color: var(--primary-color); }
    .resumen-lbl { font-size: 0.75rem; color: var(--text-color-secondary); }
  `],
})
export class CapacitacionesComponent implements OnInit {
  private http = inject(HttpClient);
  private notify = inject(ApexNotificationService);

  capacitaciones = signal<Capacitacion[]>([]);
  cargando       = signal(false);
  guardando      = signal(false);
  seleccionada   = signal<Capacitacion | null>(null);
  resumen        = signal<ResumenCapacitaciones | null>(null);

  dialogNueva   = false;
  dialogDetalle = false;

  filtroTipo     = '';
  filtroModalidad = '';
  filtroValidado: boolean | null = null;

  form: CapacitacionForm = this.resetForm();

  tipoOpts = [
    { label: 'Curso',          value: 'CURSO' },
    { label: 'Taller',         value: 'TALLER' },
    { label: 'Diplomado',      value: 'DIPLOMADO' },
    { label: 'Posgrado',       value: 'POSGRADO' },
    { label: 'Certificación',  value: 'CERTIFICACION' },
    { label: 'Congreso',       value: 'CONGRESO' },
    { label: 'Otro',           value: 'OTRO' },
  ];

  modalidadOpts = [
    { label: 'Presencial', value: 'PRESENCIAL' },
    { label: 'En línea',   value: 'EN_LINEA' },
    { label: 'Híbrida',    value: 'HIBRIDA' },
  ];

  areaOpts = [
    { label: 'Pedagogía',    value: 'PEDAGOGIA' },
    { label: 'TIC',          value: 'TIC' },
    { label: 'Disciplinar',  value: 'DISCIPLINAR' },
    { label: 'Idiomas',      value: 'IDIOMAS' },
    { label: 'Liderazgo',    value: 'LIDERAZGO' },
    { label: 'Otro',         value: 'OTRO' },
  ];

  validadoOpts = [
    { label: 'Validadas',    value: true },
    { label: 'No validadas', value: false },
  ];

  ngOnInit() { this.cargar(); }

  cargar() {
    this.cargando.set(true);
    const params: Record<string, string> = {};
    if (this.filtroTipo)                   params['tipo']     = this.filtroTipo;
    if (this.filtroModalidad)              params['modalidad'] = this.filtroModalidad;
    if (this.filtroValidado !== null)      params['validado'] = String(this.filtroValidado);

    this.http.get<Capacitacion[]>('/api/v1/capacitaciones', { params }).subscribe({
      next: data => { this.capacitaciones.set(data); this.cargando.set(false); },
      error: () => { this.cargando.set(false); this.notify.error('Error al cargar capacitaciones'); },
    });
  }

  abrirNueva() {
    this.form = this.resetForm();
    this.dialogNueva = true;
  }

  guardar() {
    if (!this.form.docente_id || !this.form.nombre || !this.form.institucion
        || !this.form.tipo_certificacion || !this.form.modalidad
        || !this.form.fecha_inicio || !this.form.fecha_fin || !this.form.duracion_hrs) {
      this.notify.warning('Complete los campos obligatorios');
      return;
    }
    this.guardando.set(true);
    const payload = {
      docente_id:         this.form.docente_id,
      nombre:             this.form.nombre,
      descripcion:        this.form.descripcion || null,
      institucion:        this.form.institucion,
      tipo_certificacion: this.form.tipo_certificacion,
      modalidad:          this.form.modalidad,
      fecha_inicio:       this.toDateStr(this.form.fecha_inicio),
      fecha_fin:          this.toDateStr(this.form.fecha_fin),
      duracion_hrs:       this.form.duracion_hrs,
      area_formacion:     this.form.area_formacion || null,
      folio_certificado:  this.form.folio_certificado || null,
      certificado_url:    this.form.certificado_url || null,
    };
    this.http.post<Capacitacion>('/api/v1/capacitaciones', payload).subscribe({
      next: () => {
        this.guardando.set(false);
        this.dialogNueva = false;
        this.notify.success('Capacitación registrada');
        this.cargar();
      },
      error: (e) => { this.guardando.set(false); this.notify.error(e.error?.detail ?? 'Error al guardar'); },
    });
  }

  verDetalle(cap: Capacitacion) {
    this.seleccionada.set(cap);
    this.dialogDetalle = true;
  }

  validar(cap: Capacitacion) {
    this.http.post<Capacitacion>(`/api/v1/capacitaciones/${cap.id}/validar`, null).subscribe({
      next: () => { this.notify.success('Capacitación validada'); this.cargar(); },
      error: (e) => this.notify.error(e.error?.detail ?? 'Error al validar'),
    });
  }

  eliminar(cap: Capacitacion) {
    this.http.delete(`/api/v1/capacitaciones/${cap.id}`).subscribe({
      next: () => { this.notify.success('Capacitación eliminada'); this.cargar(); },
      error: (e) => this.notify.error(e.error?.detail ?? 'Error al eliminar'),
    });
  }

  verPdf(url: string) { window.open(url, '_blank'); }

  modalidadSeverity(modalidad: string): 'info' | 'success' | 'warn' {
    const map: Record<string, 'info' | 'success' | 'warn'> = {
      PRESENCIAL: 'info', EN_LINEA: 'success', HIBRIDA: 'warn',
    };
    return map[modalidad] ?? 'info';
  }

  tipoLabel(tipo: string): string {
    return this.tipoOpts.find(o => o.value === tipo)?.label ?? tipo;
  }

  private resetForm(): CapacitacionForm {
    return {
      docente_id: '', nombre: '', descripcion: '', institucion: '',
      tipo_certificacion: 'CURSO', modalidad: 'PRESENCIAL',
      fecha_inicio: null, fecha_fin: null, duracion_hrs: 0,
      area_formacion: '', folio_certificado: '', certificado_url: '',
    };
  }

  private toDateStr(d: Date | null): string {
    if (!d) return '';
    return d.toISOString().split('T')[0];
  }
}
