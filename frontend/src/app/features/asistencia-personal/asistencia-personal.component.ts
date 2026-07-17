import { Component, OnDestroy, OnInit, signal, computed, inject, ChangeDetectionStrategy} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject, takeUntil } from 'rxjs';
import { ApiService } from '../../core/services/api.service';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { ToastModule } from 'primeng/toast';
import { DatePickerModule } from 'primeng/datepicker';
import { CheckboxModule } from 'primeng/checkbox';
import { InputNumberModule } from 'primeng/inputnumber';
import { CardModule } from 'primeng/card';
import { AutoCompleteModule } from 'primeng/autocomplete';
import { MessageService } from 'primeng/api';
import { ApexNotificationService } from 'apex-component-library';
import { InteractiveGridComponent, ColumnConfig } from '../../shared/components/interactive-grid/interactive-grid.component';
import { AdesFormatDirective } from '../../shared/directives/ades-format.directive';

interface AsistenciaPersonal {
  id: string;
  persona_id: string;
  nombre_completo?: string;
  fecha: string;
  hora_entrada: string | null;
  hora_salida: string | null;
  tipo_jornada: string;
  es_retardo: boolean;
  minutos_retardo: number;
  justificado: boolean;
  justificacion: string | null;
  observaciones: string | null;
  row_version: number;
}

interface ReportePersonal {
  persona_id: string;
  mes: number;
  anio: number;
  total_dias: number;
  dias_asistio: number;
  dias_falta: number;
  dias_incapacidad: number;
  dias_vacaciones: number;
  dias_permiso: number;
  total_retardos: number;
  porcentaje_asistencia: number;
}

/**
 * Registro y seguimiento de asistencia del personal administrativo y docente.
 * Permite registrar hora de entrada/salida, retardos y justificaciones por
 * trabajador. Genera resúmenes mensuales con porcentaje de asistencia.
 * Usa optimistic locking (`row_version`) en actualizaciones. Requiere
 * nivelAcceso ≥ 4 (AdminPlantel) para acceder a registros de otros usuarios.
 */
@Component({
  selector: 'app-asistencia-personal',
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
  imports: [
    AdesFormatDirective,
    CommonModule, FormsModule, ButtonModule,
    DialogModule, InputTextModule, SelectModule,
    ToastModule, DatePickerModule, CheckboxModule, InputNumberModule, CardModule,
    AutoCompleteModule, InteractiveGridComponent,
  ],
  providers: [MessageService],
  template: `
    <p-toast />
    <div class="apex-page">
      <div class="apex-toolbar">
        <h2 class="apex-title">Control de Asistencia del Personal</h2>
        <div class="apex-toolbar-actions">
          <input pInputText [(ngModel)]="filtroNombre" (input)="onFiltroNombre()"
            placeholder="Buscar por nombre…" style="width:220px" aria-label="Buscar por nombre"/>
          <p-datepicker [(ngModel)]="filtroFechaInicio" dateFormat="yy-mm-dd" placeholder="Desde" [showIcon]="true" style="width:150px" />
          <p-datepicker [(ngModel)]="filtroFechaFin"    dateFormat="yy-mm-dd" placeholder="Hasta" [showIcon]="true" style="width:150px" />
          <p-button label="Registrar" icon="pi pi-plus" size="small" (onClick)="abrirNuevo()" />
          <p-button label="Reporte" icon="pi pi-chart-bar" size="small" severity="secondary" (onClick)="verReporte()" />
        </div>
      </div>

      <!-- Reporte mensual -->
      @if (reporte()) {
        <div class="reporte-bar">
          <div class="rep-item"><span class="rep-num text-green-600">{{ reporte()!.dias_asistio }}</span><span class="rep-lbl">Asistió</span></div>
          <div class="rep-item"><span class="rep-num text-red-600">{{ reporte()!.dias_falta }}</span><span class="rep-lbl">Faltas</span></div>
          <div class="rep-item"><span class="rep-num text-orange-500">{{ reporte()!.total_retardos }}</span><span class="rep-lbl">Retardos</span></div>
          <div class="rep-item"><span class="rep-num">{{ reporte()!.dias_incapacidad }}</span><span class="rep-lbl">Incapacidad</span></div>
          <div class="rep-item"><span class="rep-num">{{ reporte()!.dias_vacaciones }}</span><span class="rep-lbl">Vacaciones</span></div>
          <div class="rep-item">
            <span class="rep-num" [class.text-green-600]="reporte()!.porcentaje_asistencia>=90"
              [class.text-red-600]="reporte()!.porcentaje_asistencia<80">
              {{ reporte()!.porcentaje_asistencia }}%
            </span>
            <span class="rep-lbl">Asistencia</span>
          </div>
          <p-button icon="pi pi-times" [text]="true" size="small" ariaLabel="Cerrar reporte" (onClick)="reporte.set(null)" class="ml-auto" />
        </div>
      }

      <app-interactive-grid
        [data]="registrosFlat()"
        [columns]="registrosColumns"
        [loading]="cargando()"
        (rowSelected)="editar($event)"
      />
    </div>

    <!-- Dialog: Registro -->
    <p-dialog [header]="editandoId ? 'Editar Registro' : 'Registrar Asistencia'"
      [(visible)]="dialogForm" [modal]="true" [style]="{width:'540px'}" [draggable]="false">
      <div class="grid grid-cols-2 gap-3 p-2">
        @if (!editandoId) {
          <div class="col-span-2 flex flex-col gap-1">
            <label class="text-sm font-medium">Personal <span class="text-red-500">*</span></label>
            <p-autocomplete
              [(ngModel)]="personaSeleccionada"
              [suggestions]="personaSugerencias()"
              (completeMethod)="buscarPersona($event)"
              optionLabel="label"
              [forceSelection]="true"
              [delay]="300"
              placeholder="Escriba nombre del empleado…"
              style="width:100%" ariaLabel="Personal"/>
          </div>
          <div class="flex flex-col gap-1">
            <label class="text-sm font-medium">Fecha <span class="text-red-500">*</span></label>
            <p-datepicker [(ngModel)]="form.fecha" dateFormat="yy-mm-dd" [showIcon]="true" ariaLabel="Fecha"/>
          </div>
        }
        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium">Tipo Jornada</label>
          <p-select [options]="jornadaOpts" [(ngModel)]="form.tipo_jornada" ariaLabel="Tipo Jornada"/>
        </div>
        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium" for="ap-hora-entrada">Hora Entrada</label>
          <input pInputText id="ap-hora-entrada" [(ngModel)]="form.hora_entrada" placeholder="08:00"/>
        </div>
        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium" for="ap-hora-salida">Hora Salida</label>
          <input pInputText id="ap-hora-salida" [(ngModel)]="form.hora_salida" placeholder="14:30"/>
        </div>
        <div class="flex items-center gap-2 mt-4">
          <p-checkbox [(ngModel)]="form.es_retardo" [binary]="true" inputId="retardo"/>
          <label for="retardo" class="text-sm">Retardo</label>
        </div>
        @if (form.es_retardo) {
          <div class="flex flex-col gap-1">
            <label class="text-sm font-medium">Minutos de retardo</label>
            <p-inputNumber [(ngModel)]="form.minutos_retardo" [min]="1" />
          </div>
        }
        <div class="flex items-center gap-2">
          <p-checkbox [(ngModel)]="form.justificado" [binary]="true" inputId="justif"/>
          <label for="justif" class="text-sm">Justificado</label>
        </div>
        @if (form.justificado) {
          <div class="col-span-2 flex flex-col gap-1">
            <label class="text-sm font-medium" for="ap-justificacion">Justificación</label>
            <input pInputText id="ap-justificacion" [(ngModel)]="form.justificacion" placeholder="Motivo de la falta justificada…"/>
          </div>
        }
        <div class="col-span-2 flex flex-col gap-1">
          <label class="text-sm font-medium" for="ap-obs">Observaciones</label>
          <input pInputText id="ap-obs" [(ngModel)]="form.observaciones"/>
        </div>
      </div>
      <ng-template pTemplate="footer">
        <p-button label="Cancelar" [text]="true" (onClick)="dialogForm=false" />
        <p-button [label]="editandoId ? 'Guardar' : 'Registrar'" icon="pi pi-save" ariaLabel="Guardar"
          [loading]="guardando()" (onClick)="guardar()" />
      </ng-template>
    </p-dialog>

    <!-- Dialog: Reporte mensual -->
    <p-dialog header="Generar Reporte Mensual" [(visible)]="dialogReporte"
      [modal]="true" [style]="{width:'400px'}" [draggable]="false">
      <div class="flex flex-col gap-3 p-2">
        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium">Personal <span class="text-red-500">*</span></label>
          <p-autocomplete
            [(ngModel)]="personaReporteSelec"
            [suggestions]="personaReporteSug()"
            (completeMethod)="buscarPersonaReporte($event)"
            optionLabel="label"
            [forceSelection]="true"
            [delay]="300"
            placeholder="Buscar empleado…"
            style="width:100%" ariaLabel="Personal"/>
        </div>
        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium">Mes</label>
          <p-select [options]="mesOpts" [(ngModel)]="reporteMes" ariaLabel="Mes"/>
        </div>
        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium">Año</label>
          <p-inputNumber [(ngModel)]="reporteAnio" [min]="2020" [max]="2030" />
        </div>
      </div>
      <ng-template pTemplate="footer">
        <p-button label="Cancelar" [text]="true" (onClick)="dialogReporte=false" />
        <p-button label="Generar" icon="pi pi-chart-bar" [loading]="cargandoReporte()" (onClick)="generarReporte()" />
      </ng-template>
    </p-dialog>
  `,
  styles: [`
    .apex-page { padding: 1rem; }
    .apex-toolbar { display:flex; align-items:center; justify-content:space-between; margin-bottom:1rem; flex-wrap:wrap; gap:.5rem; }
    .apex-title { margin:0; font-size:1.25rem; font-weight:600; }
    .apex-toolbar-actions { display:flex; gap:.5rem; align-items:center; flex-wrap:wrap; }
    .reporte-bar { display:flex; gap:1.5rem; padding:.75rem 1rem; background:var(--surface-100); border-radius:8px; margin-bottom:1rem; border:1px solid var(--surface-200); align-items:center; flex-wrap:wrap; }
    .rep-item { display:flex; flex-direction:column; align-items:center; }
    .rep-num { font-size:1.4rem; font-weight:700; }
    .rep-lbl { font-size:.7rem; color:var(--text-color-secondary); }
  `],
})
export class AsistenciaPersonalComponent implements OnInit, OnDestroy {
  private destroy$ = new Subject<void>();
  private api = inject(ApiService);
  private notify = inject(ApexNotificationService);

  registros      = signal<AsistenciaPersonal[]>([]);

  readonly registrosFlat = computed(() =>
    this.registros().map(r => ({
      ...r,
      hora_entrada_str: r.hora_entrada ?? '—',
      hora_salida_str:  r.hora_salida ?? '—',
      retardo_str:      r.es_retardo ? `${r.minutos_retardo} min` : '—',
      justificado_str:  r.justificado ? 'Sí' : 'No',
      obs_str:          (r.observaciones ?? '').slice(0, 60),
    }))
  );
  readonly registrosColumns: ColumnConfig[] = [
    { field: 'nombre_completo', header: 'Personal',  sortable: true, filterable: true },
    { field: 'fecha',           header: 'Fecha',     sortable: true, width: '110px' },
    { field: 'hora_entrada_str',header: 'Entrada',   width: '90px' },
    { field: 'hora_salida_str', header: 'Salida',    width: '90px' },
    { field: 'tipo_jornada',    header: 'Jornada',   width: '110px' },
    { field: 'retardo_str',     header: 'Retardo',   width: '85px' },
    { field: 'justificado_str', header: 'Justif.',   width: '70px' },
    { field: 'obs_str',         header: 'Observaciones' },
  ];
  reporte        = signal<ReportePersonal | null>(null);
  cargando       = signal(false);
  guardando      = signal(false);
  cargandoReporte = signal(false);
  personaSugerencias = signal<{ label: string; value: string }[]>([]);
  personaSeleccionada: { label: string; value: string } | null = null;
  personaReporteSug = signal<{ label: string; value: string }[]>([]);
  personaReporteSelec: { label: string; value: string } | null = null;

  dialogForm    = false;
  dialogReporte = false;
  editandoId    = '';
  filtroNombre  = '';
  filtroFechaInicio: Date | null = null;
  filtroFechaFin:    Date | null = null;
  reporteMes  = new Date().getMonth() + 1;
  reporteAnio = new Date().getFullYear();
  private filtroTimer: ReturnType<typeof setTimeout> | null = null;

  form: any = this.resetForm();

  jornadaOpts = [
    { label: 'Jornada completa', value: 'COMPLETA' },
    { label: 'Media jornada',    value: 'MEDIA' },
    { label: 'Falta',            value: 'NINGUNA' },
    { label: 'Incapacidad',      value: 'INCAPACIDAD' },
    { label: 'Vacaciones',       value: 'VACACIONES' },
    { label: 'Permiso',          value: 'PERMISO' },
  ];
  mesOpts = [
    {label:'Enero',value:1},{label:'Febrero',value:2},{label:'Marzo',value:3},{label:'Abril',value:4},
    {label:'Mayo',value:5},{label:'Junio',value:6},{label:'Julio',value:7},{label:'Agosto',value:8},
    {label:'Septiembre',value:9},{label:'Octubre',value:10},{label:'Noviembre',value:11},{label:'Diciembre',value:12},
  ];
  ngOnInit() { this.cargar(); }

  onFiltroNombre() {
    if (this.filtroTimer) clearTimeout(this.filtroTimer);
    this.filtroTimer = setTimeout(() => this.cargar(), 400);
  }

  cargar() {
    this.cargando.set(true);
    const params: any = {};
    if (this.filtroNombre)      params['q']           = this.filtroNombre;
    if (this.filtroFechaInicio) params['fecha_inicio'] = this.toDateStr(this.filtroFechaInicio);
    if (this.filtroFechaFin)    params['fecha_fin']    = this.toDateStr(this.filtroFechaFin);
    this.api.get<AsistenciaPersonal[]>('/asistencia-personal', params).pipe(takeUntil(this.destroy$)).subscribe({
      next: d => { this.registros.set(d); this.cargando.set(false); },
      error: () => { this.cargando.set(false); this.notify.error('Error al cargar registros'); },
    });
  }

  buscarPersona(event: { query: string }) {
    if (!event.query || event.query.length < 2) { this.personaSugerencias.set([]); return; }
    this.api.get<any>('/profesores', { buscar: event.query }).pipe(takeUntil(this.destroy$)).subscribe({
      next: res => {
        const data = res?.data ?? res ?? [];
        this.personaSugerencias.set(data.map((p: any) => ({
          label: [p.nombre ?? p.persona?.nombre, p.apellido_paterno ?? p.persona?.apellido_paterno].filter(Boolean).join(' '),
          value: p.persona_id ?? p.id,
        })));
      },
      error: () => this.personaSugerencias.set([]),
    });
  }

  buscarPersonaReporte(event: { query: string }) {
    if (!event.query || event.query.length < 2) { this.personaReporteSug.set([]); return; }
    this.api.get<any>('/profesores', { buscar: event.query }).pipe(takeUntil(this.destroy$)).subscribe({
      next: res => {
        const data = res?.data ?? res ?? [];
        this.personaReporteSug.set(data.map((p: any) => ({
          label: [p.nombre ?? p.persona?.nombre, p.apellido_paterno ?? p.persona?.apellido_paterno].filter(Boolean).join(' '),
          value: p.persona_id ?? p.id,
        })));
      },
      error: () => this.personaReporteSug.set([]),
    });
  }

  abrirNuevo() {
    this.editandoId = '';
    this.form = this.resetForm();
    this.personaSeleccionada = null;
    this.dialogForm = true;
  }

  editar(reg: AsistenciaPersonal) {
    this.editandoId = reg.id;
    this.form = {
      fecha: reg.fecha ? new Date(reg.fecha) : new Date(),
      tipo_jornada: reg.tipo_jornada, hora_entrada: reg.hora_entrada,
      hora_salida: reg.hora_salida, es_retardo: reg.es_retardo,
      minutos_retardo: reg.minutos_retardo, justificado: reg.justificado,
      justificacion: reg.justificacion, observaciones: reg.observaciones,
    };
    this.dialogForm = true;
  }

  guardar() {
    if (!this.editandoId && !this.personaSeleccionada?.value) {
      this.notify.warning('Seleccione la persona');
      return;
    }
    this.guardando.set(true);
    const req = this.editandoId
      ? this.api.patch(`/asistencia-personal/${this.editandoId}`, this.form)
      : this.api.post('/asistencia-personal', {
          ...this.form,
          persona_id: this.personaSeleccionada!.value,
          fecha: this.form.fecha instanceof Date ? this.toDateStr(this.form.fecha) : this.form.fecha,
        });
    req.pipe(takeUntil(this.destroy$)).subscribe({
      next: () => {
        this.guardando.set(false); this.dialogForm = false;
        this.notify.success('Asistencia registrada');
        this.cargar();
      },
      error: (e) => { this.guardando.set(false); this.notify.error(e.error?.detail ?? 'Error al guardar'); },
    });
  }

  verReporte() {
    this.personaReporteSelec = null;
    this.dialogReporte = true;
  }

  generarReporte() {
    if (!this.personaReporteSelec?.value) { this.notify.warning('Seleccione una persona para el reporte'); return; }
    this.cargandoReporte.set(true);
    this.api.get<ReportePersonal>('/asistencia-personal/reporte', {
      persona_id: this.personaReporteSelec.value, mes: this.reporteMes, anio: this.reporteAnio,
    }).pipe(takeUntil(this.destroy$)).subscribe({
      next: r => {
        this.reporte.set(r); this.cargandoReporte.set(false); this.dialogReporte = false;
        this.notify.success(`Reporte ${this.reporteMes}/${this.reporteAnio} generado`);
      },
      error: () => { this.cargandoReporte.set(false); this.notify.error('Error al generar reporte'); },
    });
  }

  jornSev(j: string): 'success' | 'warn' | 'danger' | 'secondary' | 'info' {
    const m: Record<string, any> = {
      COMPLETA:'success', MEDIA:'warn', NINGUNA:'danger',
      INCAPACIDAD:'secondary', VACACIONES:'info', PERMISO:'secondary',
    };
    return m[j] ?? 'secondary';
  }
  private toDateStr(d: Date): string { return d.toISOString().split('T')[0]; }
  private resetForm() {
    return { persona_id:'', fecha:null, hora_entrada:'', hora_salida:'',
             tipo_jornada:'COMPLETA', es_retardo:false, minutos_retardo:0,
             justificado:false, justificacion:'', observaciones:'' };
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
