import { Component, OnDestroy, OnInit, signal, inject, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject, takeUntil } from 'rxjs';
import { ApiService } from '../../core/services/api.service';
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
import { AutoCompleteModule } from 'primeng/autocomplete';
import { MessageService } from 'primeng/api';
import { ApexNotificationService } from 'apex-component-library';
import { InteractiveGridComponent, ColumnConfig } from '../../shared/components/interactive-grid/interactive-grid.component';

interface Capacitacion {
  id: string;
  docente_id: string;
  nombre_docente?: string;
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

/**
 * Registro y seguimiento de capacitaciones y certificaciones del personal docente.
 * Captura tipo, modalidad, institución, duración en horas y folio de certificado.
 * Genera resúmenes por docente con totales y distribución por área de formación.
 * El área de RRHH puede validar registros (campo `validado_rh`). Usa optimistic
 * locking (`row_version`) en actualizaciones. Requiere nivelAcceso ≥ 3.
 */
@Component({
  selector: 'app-capacitaciones',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    ButtonModule, DialogModule,
    InputTextModule, SelectModule, TextareaModule,
    TagModule, ToastModule, DatePickerModule,
    InputNumberModule, CardModule, AutoCompleteModule,
    InteractiveGridComponent,
  ],
  providers: [MessageService],
  template: `
    <p-toast />

    <div class="apex-page">
      <!-- Toolbar -->
      <div class="apex-toolbar">
        <h2 class="apex-title">Capacitaciones y Certificaciones Docentes</h2>
        <div class="apex-toolbar-actions">
          <input pInputText [(ngModel)]="filtroNombre" (input)="onFiltroNombre()"
            placeholder="Buscar por docente…" style="min-width:200px" />
          <p-select [options]="tipoOpts" [(ngModel)]="filtroTipo" placeholder="Todos los tipos"
            [showClear]="true" (onChange)="cargar()" style="min-width:150px" />
          <p-select [options]="modalidadOpts" [(ngModel)]="filtroModalidad" placeholder="Modalidad"
            [showClear]="true" (onChange)="cargar()" style="min-width:130px" />
          <p-select [options]="validadoOpts" [(ngModel)]="filtroValidado" placeholder="Validación"
            [showClear]="true" (onChange)="cargar()" style="min-width:120px" />
          <p-button label="Registrar Capacitación" icon="pi pi-plus" size="small" (onClick)="abrirNueva()" />
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
      <app-interactive-grid
        [data]="capacitacionesFlat()"
        [columns]="capacitacionColumns"
        [loading]="cargando()"
        [showDelete]="true"
        (rowSelected)="verDetalle($event)"
        (rowDeleted)="eliminar($event)"
      />
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
          <label class="text-sm font-medium">Docente <span class="text-red-500">*</span></label>
          <p-autocomplete
            [(ngModel)]="docenteSeleccionado"
            [suggestions]="docenteSugerencias()"
            (completeMethod)="buscarDocente($event)"
            optionLabel="label"
            [forceSelection]="true"
            [delay]="300"
            placeholder="Escriba nombre del docente…"
            style="width:100%"
          />
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
          <p-datepicker [(ngModel)]="form.fecha_inicio" dateFormat="yy-mm-dd" [showIcon]="true" />
        </div>

        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium">Fecha Fin <span class="text-red-500">*</span></label>
          <p-datepicker [(ngModel)]="form.fecha_fin" dateFormat="yy-mm-dd" [showIcon]="true" />
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
export class CapacitacionesComponent implements OnInit implements OnInit, OnDestroy {
  private destroy$ = new Subject<void>();
  private api = inject(ApiService);
  private notify = inject(ApexNotificationService);

  capacitaciones = signal<Capacitacion[]>([]);

  readonly capacitacionColumns: ColumnConfig[] = [
    { field: 'nombre_docente', header: 'Docente',      sortable: true, filterable: true,  width: '180px' },
    { field: 'nombreConFolio', header: 'Nombre',       sortable: true, filterable: true },
    { field: 'institucion',    header: 'Institución',  sortable: true, filterable: true,  width: '160px' },
    { field: 'tipoLabel',      header: 'Tipo',         sortable: true, filterable: true,  width: '110px' },
    { field: 'modalidad',      header: 'Modalidad',    sortable: true, filterable: true,  width: '100px' },
    { field: 'fecha_inicio',   header: 'Inicio',       sortable: true, filterable: false, width: '100px' },
    { field: 'duracion_hrs',   header: 'Hrs',          sortable: true, filterable: false, width: '70px' },
    { field: 'validadoLabel',  header: 'Validado',     sortable: true, filterable: true,  width: '90px' },
  ];

  readonly capacitacionesFlat = computed(() =>
    this.capacitaciones().map(cap => ({
      ...cap,
      nombreConFolio: cap.folio_certificado ? `${cap.nombre} (${cap.folio_certificado})` : cap.nombre,
      tipoLabel: this.tipoOpts.find(o => o.value === cap.tipo_certificacion)?.label ?? cap.tipo_certificacion,
      areaFormacion: cap.area_formacion ?? '—',
      validadoLabel: cap.validado_rh ? 'Validado' : 'Pendiente',
    }))
  );
  cargando       = signal(false);
  guardando      = signal(false);
  seleccionada   = signal<Capacitacion | null>(null);
  resumen        = signal<ResumenCapacitaciones | null>(null);
  docenteSugerencias = signal<{ label: string; value: string }[]>([]);
  docenteSeleccionado: { label: string; value: string } | null = null;

  dialogNueva   = false;
  dialogDetalle = false;

  filtroTipo     = '';
  filtroModalidad = '';
  filtroValidado: boolean | null = null;
  filtroNombre   = '';

  private filtroTimer: ReturnType<typeof setTimeout> | null = null;

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

  onFiltroNombre() {
    if (this.filtroTimer) clearTimeout(this.filtroTimer);
    this.filtroTimer = setTimeout(() => this.cargar(), 400);
  }

  cargar() {
    this.cargando.set(true);
    const params: Record<string, string> = {};
    if (this.filtroTipo)               params['tipo']     = this.filtroTipo;
    if (this.filtroModalidad)          params['modalidad'] = this.filtroModalidad;
    if (this.filtroValidado !== null)  params['validado'] = String(this.filtroValidado);
    if (this.filtroNombre)             params['q']        = this.filtroNombre;

    this.api.get<Capacitacion[]>('/capacitaciones', params).subscribe({
      next: data => { this.capacitaciones.set(data); this.cargando.set(false); },
      error: () => { this.cargando.set(false); this.notify.error('Error al cargar capacitaciones'); },
    });
  }

  buscarDocente(event: { query: string }) {
    if (!event.query || event.query.length < 2) { this.docenteSugerencias.set([]); return; }
    this.api.get<any>('/profesores', { buscar: event.query }).subscribe({
      next: res => {
        const data = res?.data ?? res ?? [];
        this.docenteSugerencias.set(data.map((p: any) => ({
          label: [p.nombre ?? p.persona?.nombre, p.apellido_paterno ?? p.persona?.apellido_paterno, p.apellido_materno ?? p.persona?.apellido_materno].filter(Boolean).join(' '),
          value: p.id,
        })));
      },
      error: () => this.docenteSugerencias.set([]),
    });
  }

  abrirNueva() {
    this.form = this.resetForm();
    this.docenteSeleccionado = null;
    this.dialogNueva = true;
  }

  guardar() {
    if (!this.docenteSeleccionado?.value || !this.form.nombre || !this.form.institucion
        || !this.form.tipo_certificacion || !this.form.modalidad
        || !this.form.fecha_inicio || !this.form.fecha_fin || !this.form.duracion_hrs) {
      this.notify.warning('Complete los campos obligatorios');
      return;
    }
    this.guardando.set(true);
    const payload = {
      docente_id:         this.docenteSeleccionado.value,
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
    this.api.post<Capacitacion>('/capacitaciones', payload).subscribe({
      next: () => {
        this.guardando.set(false);
        this.dialogNueva = false;
        this.notify.success('Capacitación registrada');
        this.cargar();
      },
      error: (e: any) => { this.guardando.set(false); this.notify.error(e.error?.detail ?? 'Error al guardar'); },
    });
  }

  verDetalle(cap: Capacitacion) {
    this.seleccionada.set(cap);
    this.dialogDetalle = true;
  }

  validar(cap: Capacitacion) {
    this.api.post<Capacitacion>(`/capacitaciones/${cap.id}/validar`, null).subscribe({
      next: () => { this.notify.success('Capacitación validada'); this.cargar(); },
      error: (e: any) => this.notify.error(e.error?.detail ?? 'Error al validar'),
    });
  }

  eliminar(cap: Capacitacion) {
    this.api.delete(`/capacitaciones/${cap.id}`).subscribe({
      next: () => { this.notify.success('Capacitación eliminada'); this.cargar(); },
      error: (e: any) => this.notify.error(e.error?.detail ?? 'Error al eliminar'),
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
      nombre: '', descripcion: '', institucion: '',
      tipo_certificacion: 'CURSO', modalidad: 'PRESENCIAL',
      fecha_inicio: null, fecha_fin: null, duracion_hrs: 0,
      area_formacion: '', folio_certificado: '', certificado_url: '',
    };
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
