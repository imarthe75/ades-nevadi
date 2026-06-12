import { Component, OnInit, signal, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { TagModule } from 'primeng/tag';
import { ToastModule } from 'primeng/toast';
import { DatePickerModule } from 'primeng/datepicker';
import { CheckboxModule } from 'primeng/checkbox';
import { InputNumberModule } from 'primeng/inputnumber';
import { CardModule } from 'primeng/card';
import { MessageService } from 'primeng/api';
import { ApexNotificationService } from 'apex-component-library';

interface AsistenciaPersonal {
  id: string;
  persona_id: string;
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

@Component({
  selector: 'app-asistencia-personal',
  standalone: true,
  imports: [
    CommonModule, FormsModule, TableModule, ButtonModule,
    DialogModule, InputTextModule, SelectModule, TagModule,
    ToastModule, DatePickerModule, CheckboxModule, InputNumberModule, CardModule,
  ],
  providers: [MessageService],
  template: `
    <p-toast />
    <div class="apex-page">
      <div class="apex-toolbar">
        <h2 class="apex-title">Control de Asistencia del Personal</h2>
        <div class="apex-toolbar-actions">
          <input pInputText [(ngModel)]="filtroPersonaId" placeholder="UUID persona…" style="width:240px" />
          <p-calendar [(ngModel)]="filtroFechaInicio" dateFormat="yy-mm-dd" placeholder="Desde" [showIcon]="true" style="width:160px" />
          <p-calendar [(ngModel)]="filtroFechaFin"    dateFormat="yy-mm-dd" placeholder="Hasta" [showIcon]="true" style="width:160px" />
          <p-button label="Buscar" icon="pi pi-search" size="small" (onClick)="cargar()" />
          <p-button label="Registrar" icon="pi pi-plus" size="small" (onClick)="abrirNuevo()" />
          <p-button label="Reporte" icon="pi pi-chart-bar" size="small" severity="secondary"
            (onClick)="verReporte()" [disabled]="!filtroPersonaId" />
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
          <p-button icon="pi pi-times" [text]="true" size="small" (onClick)="reporte.set(null)" class="ml-auto" />
        </div>
      }

      <p-table [value]="registros()" [loading]="cargando()" [paginator]="true" [rows]="20"
        stripedRows styleClass="p-datatable-sm">
        <ng-template pTemplate="header">
          <tr>
            <th style="width:120px">Fecha</th>
            <th style="width:110px">Entrada</th>
            <th style="width:110px">Salida</th>
            <th style="width:120px">Jornada</th>
            <th style="width:90px">Retardo</th>
            <th style="width:90px">Justif.</th>
            <th>Observaciones</th>
            <th style="width:100px">Acciones</th>
          </tr>
        </ng-template>
        <ng-template pTemplate="body" let-reg>
          <tr>
            <td class="font-medium">{{ reg.fecha }}</td>
            <td>{{ reg.hora_entrada ?? '—' }}</td>
            <td>{{ reg.hora_salida ?? '—' }}</td>
            <td><p-tag [value]="reg.tipo_jornada" [severity]="jornSev(reg.tipo_jornada)" /></td>
            <td class="text-center">
              @if (reg.es_retardo) {
                <span class="text-orange-600 font-semibold">{{ reg.minutos_retardo }} min</span>
              } @else { <span class="text-gray-400">—</span> }
            </td>
            <td class="text-center">
              <span [class]="reg.justificado ? 'pi pi-check text-green-600' : 'pi pi-times text-red-400'"></span>
            </td>
            <td class="text-sm text-gray-600">{{ (reg.observaciones ?? '') | slice:0:60 }}</td>
            <td>
              <p-button icon="pi pi-pencil" size="small" [text]="true" (onClick)="editar(reg)" pTooltip="Editar" />
            </td>
          </tr>
        </ng-template>
        <ng-template pTemplate="emptymessage">
          <tr><td colspan="8" class="text-center py-4 text-gray-500">Sin registros. Filtre por persona y fechas.</td></tr>
        </ng-template>
      </p-table>
    </div>

    <!-- Dialog: Registro -->
    <p-dialog [header]="editandoId ? 'Editar Registro' : 'Registrar Asistencia'"
      [(visible)]="dialogForm" [modal]="true" [style]="{width:'540px'}" [draggable]="false">
      <div class="grid grid-cols-2 gap-3 p-2">
        @if (!editandoId) {
          <div class="col-span-2 flex flex-col gap-1">
            <label class="text-sm font-medium">ID Persona <span class="text-red-500">*</span></label>
            <input pInputText [(ngModel)]="form.persona_id" placeholder="UUID de la persona" />
          </div>
          <div class="flex flex-col gap-1">
            <label class="text-sm font-medium">Fecha <span class="text-red-500">*</span></label>
            <p-calendar [(ngModel)]="form.fecha" dateFormat="yy-mm-dd" [showIcon]="true" />
          </div>
        }
        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium">Tipo Jornada</label>
          <p-select [options]="jornadaOpts" [(ngModel)]="form.tipo_jornada" />
        </div>
        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium">Hora Entrada</label>
          <input pInputText [(ngModel)]="form.hora_entrada" placeholder="08:00" />
        </div>
        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium">Hora Salida</label>
          <input pInputText [(ngModel)]="form.hora_salida" placeholder="14:30" />
        </div>
        <div class="flex items-center gap-2 mt-4">
          <p-checkbox [(ngModel)]="form.es_retardo" [binary]="true" inputId="retardo" />
          <label for="retardo" class="text-sm">Retardo</label>
        </div>
        @if (form.es_retardo) {
          <div class="flex flex-col gap-1">
            <label class="text-sm font-medium">Minutos de retardo</label>
            <p-inputNumber [(ngModel)]="form.minutos_retardo" [min]="1" />
          </div>
        }
        <div class="flex items-center gap-2">
          <p-checkbox [(ngModel)]="form.justificado" [binary]="true" inputId="justif" />
          <label for="justif" class="text-sm">Justificado</label>
        </div>
        @if (form.justificado) {
          <div class="col-span-2 flex flex-col gap-1">
            <label class="text-sm font-medium">Justificación</label>
            <input pInputText [(ngModel)]="form.justificacion" placeholder="Motivo de la falta justificada…" />
          </div>
        }
        <div class="col-span-2 flex flex-col gap-1">
          <label class="text-sm font-medium">Observaciones</label>
          <input pInputText [(ngModel)]="form.observaciones" />
        </div>
      </div>
      <ng-template pTemplate="footer">
        <p-button label="Cancelar" [text]="true" (onClick)="dialogForm=false" />
        <p-button [label]="editandoId ? 'Guardar' : 'Registrar'" icon="pi pi-save"
          [loading]="guardando()" (onClick)="guardar()" />
      </ng-template>
    </p-dialog>

    <!-- Dialog: Reporte mensual -->
    <p-dialog header="Generar Reporte Mensual" [(visible)]="dialogReporte"
      [modal]="true" [style]="{width:'360px'}" [draggable]="false">
      <div class="flex flex-col gap-3 p-2">
        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium">Mes</label>
          <p-select [options]="mesOpts" [(ngModel)]="reporteMes" />
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
export class AsistenciaPersonalComponent implements OnInit {
  private http = inject(HttpClient);
  private notify = inject(ApexNotificationService);

  registros      = signal<AsistenciaPersonal[]>([]);
  reporte        = signal<ReportePersonal | null>(null);
  cargando       = signal(false);
  guardando      = signal(false);
  cargandoReporte = signal(false);

  dialogForm    = false;
  dialogReporte = false;
  editandoId    = '';
  filtroPersonaId = '';
  filtroFechaInicio: Date | null = null;
  filtroFechaFin:    Date | null = null;
  reporteMes  = new Date().getMonth() + 1;
  reporteAnio = new Date().getFullYear();

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

  ngOnInit() {}

  cargar() {
    this.cargando.set(true);
    const params: any = {};
    if (this.filtroPersonaId) params['persona_id'] = this.filtroPersonaId;
    if (this.filtroFechaInicio) params['fecha_inicio'] = this.toDateStr(this.filtroFechaInicio);
    if (this.filtroFechaFin)    params['fecha_fin']    = this.toDateStr(this.filtroFechaFin);
    this.http.get<AsistenciaPersonal[]>('/api/v1/asistencia-personal', { params }).subscribe({
      next: d => { this.registros.set(d); this.cargando.set(false); },
      error: () => { this.cargando.set(false); this.notify.error('Error al cargar registros'); },
    });
  }

  abrirNuevo() {
    this.editandoId = '';
    this.form = this.resetForm();
    if (this.filtroPersonaId) this.form.persona_id = this.filtroPersonaId;
    this.dialogForm = true;
  }

  editar(reg: AsistenciaPersonal) {
    this.editandoId = reg.id;
    this.form = {
      tipo_jornada: reg.tipo_jornada, hora_entrada: reg.hora_entrada,
      hora_salida: reg.hora_salida, es_retardo: reg.es_retardo,
      minutos_retardo: reg.minutos_retardo, justificado: reg.justificado,
      justificacion: reg.justificacion, observaciones: reg.observaciones,
    };
    this.dialogForm = true;
  }

  guardar() {
    this.guardando.set(true);
    const req = this.editandoId
      ? this.http.patch(`/api/v1/asistencia-personal/${this.editandoId}`, this.form)
      : this.http.post('/api/v1/asistencia-personal', {
          ...this.form,
          fecha: this.form.fecha instanceof Date ? this.toDateStr(this.form.fecha) : this.form.fecha,
        });
    req.subscribe({
      next: () => {
        this.guardando.set(false); this.dialogForm = false;
        this.notify.success('Asistencia registrada');
        this.cargar();
      },
      error: (e) => { this.guardando.set(false); this.notify.error(e.error?.detail ?? 'Error al guardar'); },
    });
  }

  verReporte() {
    if (!this.filtroPersonaId) { this.notify.warning('Seleccione una persona'); return; }
    this.dialogReporte = true;
  }

  generarReporte() {
    this.cargandoReporte.set(true);
    this.http.get<ReportePersonal>('/api/v1/asistencia-personal/reporte', {
      params: { persona_id: this.filtroPersonaId, mes: this.reporteMes, anio: this.reporteAnio },
    }).subscribe({
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
}
