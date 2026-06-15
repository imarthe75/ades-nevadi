import { Component, OnInit, signal, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { ToastModule } from 'primeng/toast';
import { TextareaModule } from 'primeng/textarea';
import { DatePickerModule } from 'primeng/datepicker';
import { TooltipModule } from 'primeng/tooltip';
import { MessageService } from 'primeng/api';
import { ApexNotificationService } from 'apex-component-library';
import { InteractiveGridComponent, ColumnConfig } from '../../shared/components/interactive-grid/interactive-grid.component';

type TagSev = 'success' | 'info' | 'warn' | 'danger' | 'secondary' | 'contrast';

interface Baja {
  id: string;
  tipo_baja: string;
  motivo: string;
  fecha_efectiva: string;
  fecha_reingreso: string | null;
  plantel_destino: string | null;
  is_active: boolean;
  nombre_alumno: string;
  numero_control: string;
  nombre_grupo: string;
  nombre_plantel: string;
}

const TIPOS_BAJA = [
  { label: 'Temporal',   value: 'TEMPORAL' },
  { label: 'Definitiva', value: 'DEFINITIVA' },
  { label: 'Traslado',   value: 'TRASLADO' },
  { label: 'Voluntaria', value: 'VOLUNTARIA' },
];

@Component({
  selector: 'app-movilidad',
  standalone: true,
  imports: [
    CommonModule, FormsModule, ButtonModule, DialogModule,
    InputTextModule, SelectModule, ToastModule, TextareaModule,
    DatePickerModule, TooltipModule, InteractiveGridComponent,
  ],
  providers: [MessageService],
  template: `
    <p-toast />
    <div class="apex-page">
      <div class="apex-toolbar">
        <h2 class="apex-title">Movilidad Estudiantil</h2>
        <div class="apex-toolbar-actions">
          <p-select [options]="tiposBaja" optionLabel="label" optionValue="value"
            [(ngModel)]="filtroTipo" placeholder="Tipo de baja…" [style]="{width:'140px'}" />
          <p-button label="Filtrar" icon="pi pi-filter" size="small" (onClick)="cargar()" />
        </div>
      </div>

      <div class="stats-bar">
        <span class="stat-chip">Total: <strong>{{ bajas().length }}</strong></span>
        <span class="stat-chip warn">Temporales activas: <strong>{{ countTipo('TEMPORAL') }}</strong></span>
        <span class="stat-chip danger">Definitivas: <strong>{{ countTipo('DEFINITIVA') }}</strong></span>
        <span class="stat-chip info">Traslados: <strong>{{ countTipo('TRASLADO') }}</strong></span>
      </div>

      <app-interactive-grid
        [data]="bajasFlat()"
        [columns]="bajasColumns"
        [loading]="cargando()"
        [showDelete]="false"
        (rowSelected)="abrirReactivar($event)"
      />
    </div>

    <!-- Dialog: Baja Temporal -->
    <p-dialog header="Registrar Baja Temporal" [(visible)]="dlgTemporal"
      [modal]="true" [style]="{width:'480px'}" [draggable]="false">
      <div class="grid grid-cols-1 gap-3 p-3">
        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium">N° Control del Alumno *</label>
          <input pInputText [(ngModel)]="bajaForm.estudianteId" placeholder="UUID del alumno" />
        </div>
        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium">Motivo *</label>
          <textarea pTextarea [(ngModel)]="bajaForm.motivo" rows="3" style="width:100%"></textarea>
        </div>
        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium">Fecha Efectiva *</label>
          <p-datepicker [(ngModel)]="bajaForm.fechaEfectiva" [showIcon]="true" dateFormat="dd/mm/yy" />
        </div>
        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium">Fecha Estimada de Reingreso</label>
          <p-datepicker [(ngModel)]="bajaForm.fechaReingreso" [showIcon]="true" dateFormat="dd/mm/yy" />
        </div>
        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium">Observaciones</label>
          <textarea pTextarea [(ngModel)]="bajaForm.observaciones" rows="2" style="width:100%"></textarea>
        </div>
      </div>
      <ng-template pTemplate="footer">
        <p-button label="Cancelar" [text]="true" (onClick)="dlgTemporal=false" />
        <p-button label="Registrar Baja" icon="pi pi-save" severity="warn"
          [loading]="guardando()" (onClick)="guardarBaja('temporal')" />
      </ng-template>
    </p-dialog>

    <!-- Dialog: Reactivar -->
    <p-dialog header="Reactivar Alumno" [(visible)]="dlgReactivar"
      [modal]="true" [style]="{width:'440px'}" [draggable]="false">
      <div class="grid grid-cols-1 gap-3 p-3">
        <p class="text-sm">Alumno: <strong>{{ reactivandoBaja?.nombre_alumno }}</strong></p>
        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium">UUID del Grupo Destino *</label>
          <input pInputText [(ngModel)]="reactivarForm.grupoId" placeholder="UUID del grupo" />
        </div>
        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium">UUID del Ciclo Escolar *</label>
          <input pInputText [(ngModel)]="reactivarForm.cicloId" placeholder="UUID del ciclo" />
        </div>
        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium">Observaciones</label>
          <textarea pTextarea [(ngModel)]="reactivarForm.observaciones" rows="2" style="width:100%"></textarea>
        </div>
      </div>
      <ng-template pTemplate="footer">
        <p-button label="Cancelar" [text]="true" (onClick)="dlgReactivar=false" />
        <p-button label="Reactivar Alumno" icon="pi pi-user-plus" severity="success"
          [loading]="guardando()" (onClick)="confirmarReactivar()" />
      </ng-template>
    </p-dialog>
  `,
  styles: [`
    .apex-page { padding: 1rem; }
    .apex-toolbar { display:flex; align-items:center; justify-content:space-between; margin-bottom:.75rem; flex-wrap:wrap; gap:.5rem; }
    .apex-title { margin:0; font-size:1.25rem; font-weight:600; }
    .apex-toolbar-actions { display:flex; gap:.5rem; align-items:center; flex-wrap:wrap; }
    .stats-bar { display:flex; gap:.75rem; margin-bottom:.75rem; flex-wrap:wrap; }
    .stat-chip { background:var(--surface-100); border:1px solid var(--surface-200); padding:.2rem .75rem; border-radius:12px; font-size:.8rem; }
    .stat-chip.warn { border-color:var(--yellow-400); background:var(--yellow-50); }
    .stat-chip.danger { border-color:var(--red-400); background:var(--red-50); }
    .stat-chip.info { border-color:var(--blue-400); background:var(--blue-50); }
  `],
})
export class MovilidadComponent implements OnInit {
  private http = inject(HttpClient);
  private notify = inject(ApexNotificationService);

  readonly bajasColumns: ColumnConfig[] = [
    { field: 'nombre_alumno',  header: 'Alumno' },
    { field: 'numero_control', header: 'N° Control',      width: '110px' },
    { field: 'nombre_grupo',   header: 'Grupo',           width: '80px' },
    { field: 'nombre_plantel', header: 'Plantel',         width: '120px' },
    { field: 'tipo_baja',      header: 'Tipo Baja',       width: '110px' },
    { field: 'fecha_efectiva_str', header: 'Fecha Efectiva', width: '115px' },
    { field: 'fecha_reingreso_str', header: 'Reingreso',  width: '105px' },
    { field: 'estado_str',     header: 'Estado',          width: '90px' },
  ];

  readonly bajasFlat = computed(() =>
    this.bajas().map(b => ({
      ...b,
      fecha_efectiva_str:  b.fecha_efectiva ? new Date(b.fecha_efectiva).toLocaleDateString('es-MX') : '—',
      fecha_reingreso_str: b.fecha_reingreso ? new Date(b.fecha_reingreso).toLocaleDateString('es-MX') : '—',
      estado_str:          b.is_active ? 'Activa' : 'Cerrada',
    }))
  );

  bajas    = signal<Baja[]>([]);
  cargando = signal(false);
  guardando = signal(false);

  filtroTipo = '';
  dlgTemporal = false;
  dlgReactivar = false;
  reactivandoBaja: Baja | null = null;

  tiposBaja = [{ label: 'Todos', value: '' }, ...TIPOS_BAJA];

  bajaForm = { estudianteId: '', motivo: '', fechaEfectiva: null as Date | null, fechaReingreso: null as Date | null, observaciones: '' };
  reactivarForm = { grupoId: '', cicloId: '', observaciones: '' };

  ngOnInit() { this.cargar(); }

  cargar() {
    this.cargando.set(true);
    const params: any = { solo_activas: false };
    if (this.filtroTipo) params.tipo_baja = this.filtroTipo;
    this.http.get<Baja[]>('/api/v1/movilidad/bajas', { params }).subscribe({
      next: d => { this.bajas.set(d); this.cargando.set(false); },
      error: () => { this.cargando.set(false); this.notify.error('Error al cargar bajas'); },
    });
  }

  abrirReactivar(b: Baja) {
    this.reactivandoBaja = b;
    this.reactivarForm = { grupoId: '', cicloId: '', observaciones: '' };
    this.dlgReactivar = true;
  }

  guardarBaja(tipo: string) {
    if (!this.bajaForm.estudianteId || !this.bajaForm.motivo || !this.bajaForm.fechaEfectiva) {
      this.notify.warning('Estudiante, motivo y fecha son obligatorios');
      return;
    }
    this.guardando.set(true);
    const body = {
      motivo: this.bajaForm.motivo,
      fecha_efectiva: this.bajaForm.fechaEfectiva?.toISOString().split('T')[0],
      fecha_reingreso: this.bajaForm.fechaReingreso?.toISOString().split('T')[0] ?? null,
      observaciones: this.bajaForm.observaciones || null,
    };
    const endpoint = tipo === 'temporal' ? 'baja-temporal' : 'baja-definitiva';
    this.http.post(`/api/v1/movilidad/${endpoint}/${this.bajaForm.estudianteId}`, body).subscribe({
      next: () => { this.guardando.set(false); this.dlgTemporal = false; this.notify.success('Baja registrada'); this.cargar(); },
      error: e => { this.guardando.set(false); this.notify.error(e.error?.detail ?? 'Error'); },
    });
  }

  confirmarReactivar() {
    if (!this.reactivarForm.grupoId || !this.reactivarForm.cicloId) {
      this.notify.warning('Grupo y ciclo son obligatorios');
      return;
    }
    this.guardando.set(true);
    const body = {
      grupo_id: this.reactivarForm.grupoId,
      ciclo_escolar_id: this.reactivarForm.cicloId,
      observaciones: this.reactivarForm.observaciones || null,
    };
    this.http.post(`/api/v1/movilidad/reactivar/${this.reactivandoBaja!.id}`, body).subscribe({
      next: () => { this.guardando.set(false); this.dlgReactivar = false; this.notify.success('Alumno reactivado'); this.cargar(); },
      error: e => { this.guardando.set(false); this.notify.error(e.error?.detail ?? 'Error'); },
    });
  }

  countTipo(tipo: string): number {
    return this.bajas().filter(b => b.tipo_baja === tipo && b.is_active).length;
  }

  tipoSev(t: string): TagSev {
    const m: Record<string, TagSev> = { TEMPORAL: 'warn', DEFINITIVA: 'danger', TRASLADO: 'info', VOLUNTARIA: 'secondary' };
    return m[t] ?? 'secondary';
  }
}
