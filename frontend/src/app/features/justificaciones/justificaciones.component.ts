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
import { TextareaModule } from 'primeng/textarea';
import { MessageService } from 'primeng/api';
import { ApexNotificationService } from 'apex-component-library';
import { InteractiveGridComponent, ColumnConfig } from '../../shared/components/interactive-grid/interactive-grid.component';
import { AdesFormatDirective } from '../../shared/directives/ades-format.directive';

type TagSev = 'success' | 'info' | 'warn' | 'danger' | 'secondary' | 'contrast';

interface Justificacion {
  id: string;
  asistencia_id: string;
  tipo_justificacion: string;
  motivo: string;
  documento_url: string | null;
  estado: string;
  motivo_rechazo: string | null;
  fecha_resolucion: string | null;
  fecha_creacion: string;
  alumno_nombre: string;
  estatus_asistencia: string;
  fecha_clase: string | null;
}

const TIPOS_JUST = [
  { label: 'Médica',          value: 'MEDICA' },
  { label: 'Familiar',        value: 'FAMILIAR' },
  { label: 'Deportiva',       value: 'DEPORTIVA' },
  { label: 'Cultural',        value: 'CULTURAL' },
  { label: 'Administrativa',  value: 'ADMINISTRATIVA' },
  { label: 'Otra',            value: 'OTRA' },
];

const ESTADOS_OPT = [
  { label: 'Todos',     value: '' },
  { label: 'Pendiente', value: 'PENDIENTE' },
  { label: 'Aprobada',  value: 'APROBADA' },
  { label: 'Rechazada', value: 'RECHAZADA' },
];

/**
 * Módulo de justificaciones de inasistencias.
 * Permite a docentes y coordinadores registrar, aprobar o rechazar justificantes
 * de faltas de alumnos con evidencia documental adjunta.
 */
@Component({
  selector: 'app-justificaciones',
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
  imports: [
    AdesFormatDirective,
    CommonModule, FormsModule, ButtonModule, DialogModule,
    InputTextModule, SelectModule, ToastModule, TextareaModule,
    InteractiveGridComponent,
  ],
  providers: [MessageService],
  template: `
    <p-toast />
    <div class="apex-page">
      <div class="apex-toolbar">
        <h2 class="apex-title">Justificaciones de Faltas</h2>
        <div class="apex-toolbar-actions">
          <input pInputText [(ngModel)]="filtroEstudianteId" placeholder="UUID estudiante…" style="width:230px" aria-label="UUID estudiante"/>
          <p-select [options]="estadosOpt" optionLabel="label" optionValue="value"
            [(ngModel)]="filtroEstado" placeholder="Estado…" [style]="{width:'130px'}" ariaLabel="Estado" />
          <p-button label="Filtrar" icon="pi pi-filter" size="small" (onClick)="cargar()" />
          <p-button label="Nueva justificación" icon="pi pi-plus" size="small" (onClick)="abrirNueva()" />
        </div>
      </div>

      <!-- Resumen chips -->
      <div class="stats-bar">
        <span class="stat-chip">Total: <strong>{{ justificaciones().length }}</strong></span>
        <span class="stat-chip warn">Pendientes: <strong>{{ countEstado('PENDIENTE') }}</strong></span>
        <span class="stat-chip success">Aprobadas: <strong>{{ countEstado('APROBADA') }}</strong></span>
        <span class="stat-chip danger">Rechazadas: <strong>{{ countEstado('RECHAZADA') }}</strong></span>
      </div>

      <app-interactive-grid
        [data]="justificacionesFlat()"
        [columns]="justificacionesColumns"
        [loading]="cargando()"
        [showDelete]="false"
        (rowSelected)="abrirDetalle($event)"
      />
    </div>

    <!-- Dialog: Nueva justificación -->
    <p-dialog header="Nueva Justificación de Falta" [(visible)]="dialogNueva"
      [modal]="true" [style]="{width:'520px'}" [draggable]="false">
      <div class="grid grid-cols-1 gap-3 p-3">
        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium" for="just-asist-id">UUID de la Asistencia *</label>
          <input pInputText id="just-asist-id" [(ngModel)]="nuevaForm.asistencia_id" placeholder="id de la asistencia"/>
        </div>
        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium">Tipo de Justificación *</label>
          <p-select [options]="tiposJust" optionLabel="label" optionValue="value"
            [(ngModel)]="nuevaForm.tipo_justificacion" placeholder="Seleccionar…" ariaLabel="Tipo de Justificación"/>
        </div>
        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium" for="just-motivo">Motivo *</label>
          <textarea pTextarea id="just-motivo" [(ngModel)]="nuevaForm.motivo" rows="3"
            placeholder="Descripción del motivo…" style="width:100%"
            [class.p-invalid]="jIntento && !nuevaForm.motivo.trim()"></textarea>
          @if (jIntento && !nuevaForm.motivo.trim()) {
            <small style="color:var(--color-danger);font-size:.78rem">El motivo es obligatorio</small>
          }
        </div>
        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium" for="just-doc-url">URL del Documento de Soporte</label>
          <input pInputText id="just-doc-url" [(ngModel)]="nuevaForm.documento_url" placeholder="https://…"/>
        </div>
      </div>
      <ng-template pTemplate="footer">
        <p-button label="Cancelar" [text]="true" (onClick)="dialogNueva=false" />
        <p-button label="Registrar justificación" icon="pi pi-save"
          [loading]="guardando()" (onClick)="guardarNueva()" />
      </ng-template>
    </p-dialog>

    <!-- Dialog: Rechazo con motivo -->
    <p-dialog header="Rechazar Justificación" [(visible)]="dialogRechazo"
      [modal]="true" [style]="{width:'440px'}" [draggable]="false">
      <div class="p-3">
        <label class="text-sm font-medium block mb-1" for="just-motivo-rechazo">Motivo del rechazo *</label>
        <textarea pTextarea id="just-motivo-rechazo" [(ngModel)]="motivoRechazo" rows="4"
          placeholder="Explique el motivo del rechazo…" style="width:100%"></textarea>
      </div>
      <ng-template pTemplate="footer">
        <p-button label="Cancelar" [text]="true" (onClick)="dialogRechazo=false" />
        <p-button label="Confirmar rechazo" severity="danger" icon="pi pi-times"
          [loading]="guardando()" (onClick)="confirmarRechazo()" />
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
    .stat-chip.success { border-color:var(--green-400); background:var(--green-50); }
    .stat-chip.danger { border-color:var(--red-400); background:var(--red-50); }
  `],
})
export class JustificacionesComponent implements OnInit, OnDestroy {
  private destroy$ = new Subject<void>();
  private api = inject(ApiService);
  private notify = inject(ApexNotificationService);

  readonly justificacionesColumns: ColumnConfig[] = [
    { field: 'alumno_nombre',    header: 'Alumno' },
    { field: 'fecha_str',        header: 'Fecha Clase',  width: '110px' },
    { field: 'estatus_asistencia', header: 'Asistencia', width: '100px' },
    { field: 'tipo_justificacion', header: 'Tipo',       width: '110px' },
    { field: 'motivo_str',       header: 'Motivo' },
    { field: 'estado',           header: 'Estado',       width: '100px' },
  ];

  readonly justificacionesFlat = computed(() =>
    this.justificaciones().map(j => ({
      ...j,
      fecha_str:  j.fecha_clase ? new Date(j.fecha_clase).toLocaleDateString('es-MX') : '—',
      motivo_str: j.motivo && j.motivo.length > 60 ? j.motivo.slice(0, 60) + '…' : (j.motivo ?? ''),
    }))
  );

  justificaciones = signal<Justificacion[]>([]);
  cargando        = signal(false);
  guardando       = signal(false);

  dialogNueva  = false;
  dialogRechazo = false;
  filtroEstudianteId = '';
  filtroEstado = '';
  jIntento = false;
  motivoRechazo = '';
  rechazandoId: string | null = null;

  tiposJust = TIPOS_JUST;
  estadosOpt = ESTADOS_OPT;

  nuevaForm = { asistencia_id: '', tipo_justificacion: 'MEDICA', motivo: '', documento_url: '' };

  ngOnInit() { this.cargar(); }

  cargar() {
    this.cargando.set(true);
    const params: any = {};
    if (this.filtroEstudianteId) params.estudiante_id = this.filtroEstudianteId;
    if (this.filtroEstado) params.estado = this.filtroEstado;
    this.api.get<Justificacion[]>('/justificaciones', params).pipe(takeUntil(this.destroy$)).subscribe({
      next: d => { this.justificaciones.set(d); this.cargando.set(false); },
      error: () => { this.cargando.set(false); this.notify.error('Error al cargar justificaciones'); },
    });
  }

  abrirNueva() {
    this.nuevaForm = { asistencia_id: '', tipo_justificacion: 'MEDICA', motivo: '', documento_url: '' };
    this.jIntento = false;
    this.dialogNueva = true;
  }

  guardarNueva() {
    this.jIntento = true;
    if (!this.nuevaForm.asistencia_id || !this.nuevaForm.motivo.trim()) {
      this.notify.warning('Asistencia y motivo son obligatorios');
      return;
    }
    this.guardando.set(true);
    this.api.post('/justificaciones', this.nuevaForm).pipe(takeUntil(this.destroy$)).subscribe({
      next: () => { this.guardando.set(false); this.dialogNueva = false; this.notify.success('Justificación registrada'); this.cargar(); },
      error: e => { this.guardando.set(false); this.notify.error(e.error?.detail ?? 'Error al registrar'); },
    });
  }

  resolver(j: Justificacion, accion: string) {
    this.api.post(`/justificaciones/${j.id}/resolver`, { accion }).pipe(takeUntil(this.destroy$)).subscribe({
      next: (r: any) => { this.notify.success(`Justificación ${r.estado}`); this.cargar(); },
      error: e => this.notify.error(e.error?.detail ?? 'Error'),
    });
  }

  abrirRechazo(j: Justificacion) {
    this.rechazandoId = j.id;
    this.motivoRechazo = '';
    this.dialogRechazo = true;
  }

  confirmarRechazo() {
    if (!this.motivoRechazo.trim()) { this.notify.warning('El motivo es obligatorio'); return; }
    this.guardando.set(true);
    this.api.post(`/justificaciones/${this.rechazandoId}/resolver`,
      { accion: 'RECHAZAR', motivo_rechazo: this.motivoRechazo }).pipe(takeUntil(this.destroy$)).subscribe({
      next: () => { this.guardando.set(false); this.dialogRechazo = false; this.notify.success('Justificación rechazada'); this.cargar(); },
      error: e => { this.guardando.set(false); this.notify.error(e.error?.detail ?? 'Error'); },
    });
  }

  abrirDetalle(j: Justificacion) {
    if (j.estado === 'PENDIENTE') this.notify.info(`Asistencia: ${j.estatus_asistencia} | ${j.motivo}`);
  }

  countEstado(estado: string): number {
    return this.justificaciones().filter(j => j.estado === estado).length;
  }

  estadoSev(e: string): TagSev {
    const m: Record<string, TagSev> = { PENDIENTE: 'warn', APROBADA: 'success', RECHAZADA: 'danger' };
    return m[e] ?? 'secondary';
  }

  asistSev(e: string): TagSev {
    const m: Record<string, TagSev> = { PRESENTE: 'success', AUSENTE: 'danger', TARDE: 'warn' };
    return m[e] ?? 'secondary';
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
