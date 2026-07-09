import { Component, OnDestroy, OnInit, signal, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject, takeUntil } from 'rxjs';
import { ApiService } from '../../core/services/api.service';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { ToastModule } from 'primeng/toast';
import { CheckboxModule } from 'primeng/checkbox';
import { TooltipModule } from 'primeng/tooltip';
import { MessageService } from 'primeng/api';
import { ApexNotificationService } from 'apex-component-library';
import { InteractiveGridComponent, ColumnConfig } from '../../shared/components/interactive-grid/interactive-grid.component';

interface Condicion {
  id: string;
  alumno_id: string;
  numero_control: string;
  alumno_nombre: string;
  tipo_condicion: string;
  descripcion: string;
  medicacion_nombre: string | null;
  dosis: string | null;
  frecuencia: string | null;
  alergias: string | null;
  medico_responsable: string | null;
  telefono_medico: string | null;
  activa: boolean;
  fecha_creacion: string;
}

type TagSev = 'success' | 'info' | 'warn' | 'danger' | 'secondary' | 'contrast';

interface AlertaEmergencia {
  tipo_condicion: string;
  descripcion: string;
  medicacion_nombre: string | null;
  dosis: string | null;
  frecuencia: string | null;
  alergias: string | null;
  medico_responsable: string | null;
  telefono_medico: string | null;
  alumno_nombre: string;
  contacto_emergencia: string | null;
  tel_emergencia: string | null;
}

const TIPOS = [
  { label: 'Epilepsia',             value: 'EPILEPSIA' },
  { label: 'Diabetes',              value: 'DIABETES' },
  { label: 'Asma',                  value: 'ASMA' },
  { label: 'Alergia',               value: 'ALERGIA' },
  { label: 'Cardiaca',              value: 'CARDIACA' },
  { label: 'Hipertensión',          value: 'HIPERTENSION' },
  { label: 'Discapacidad Visual',   value: 'DISCAPACIDAD_VISUAL' },
  { label: 'Discapacidad Auditiva', value: 'DISCAPACIDAD_AUDITIVA' },
  { label: 'Otra',                  value: 'OTRA' },
];

/**
 * Registro de condiciones crónicas de salud de alumnos (PII sensible bajo LFPDPPP).
 * Almacena diagnósticos, medicación, alergias y datos del médico responsable.
 * Expone una vista de alerta de emergencia para uso inmediato por personal escolar.
 * El acceso está restringido a nivelAcceso ≥ 4; los datos se transmiten solo por
 * HTTPS y se acceden únicamente en contexto del plantel propio (anti-IDOR).
 */
@Component({
  selector: 'app-condiciones-cronicas',
  standalone: true,
  imports: [
    CommonModule, FormsModule, ButtonModule, DialogModule,
    InputTextModule, SelectModule, ToastModule, CheckboxModule, TooltipModule,
    InteractiveGridComponent,
  ],
  providers: [MessageService],
  template: `
    <p-toast />
    <div class="apex-page">
      <div class="apex-toolbar">
        <h2 class="apex-title">Condiciones Crónicas de Alumnos</h2>
        <div class="apex-toolbar-actions">
          <input pInputText [(ngModel)]="busquedaAlumnoId" placeholder="UUID del alumno…" style="width:260px"
            (keyup.enter)="cargar()" />
          <p-button label="Buscar" icon="pi pi-search" size="small" (onClick)="cargar()" />
          <p-button label="Ver alerta" icon="pi pi-bell" size="small" severity="warn"
            (onClick)="verAlerta()" [disabled]="!busquedaAlumnoId" pTooltip="Pantalla de emergencia" />
          <p-button label="Nueva condición" icon="pi pi-plus" size="small" (onClick)="abrirNueva()" />
        </div>
      </div>

      <app-interactive-grid
        [data]="condicionesFlat()"
        [columns]="condicionesColumns"
        [loading]="cargando()"
        [showDelete]="true"
        (rowSelected)="abrirEdicion($event)"
        (rowDeleted)="eliminar($event)"
      />
    </div>

    <!-- Dialog: Crear/Editar -->
    <p-dialog [header]="editandoId ? 'Editar Condición' : 'Nueva Condición Crónica'"
      [(visible)]="dialogForm" [modal]="true" [style]="{width:'580px'}" [draggable]="false">
      <div class="grid grid-cols-2 gap-3 p-3">
        <div class="col-span-2 flex flex-col gap-1">
          <label class="text-sm font-medium">UUID del Alumno *</label>
          <input pInputText [(ngModel)]="form.alumno_id" [disabled]="!!editandoId"
            placeholder="uuid del alumno" />
        </div>
        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium">Tipo de Condición *</label>
          <p-select [options]="tipos" optionLabel="label" optionValue="value"
            [(ngModel)]="form.tipo_condicion" placeholder="Seleccionar…" />
        </div>
        <div class="flex flex-col gap-1 items-center justify-end">
          <label class="text-sm font-medium">¿Activa?</label>
          <p-checkbox [(ngModel)]="form.activa" [binary]="true" />
        </div>
        <div class="col-span-2 flex flex-col gap-1">
          <label class="text-sm font-medium">Descripción *</label>
          <input pInputText [(ngModel)]="form.descripcion" placeholder="Descripción clínica…" />
        </div>
        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium">Medicación</label>
          <input pInputText [(ngModel)]="form.medicacion_nombre" placeholder="Nombre del medicamento" />
        </div>
        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium">Dosis</label>
          <input pInputText [(ngModel)]="form.dosis" placeholder="Ej: 10mg" />
        </div>
        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium">Frecuencia</label>
          <input pInputText [(ngModel)]="form.frecuencia" placeholder="Ej: Cada 8 horas" />
        </div>
        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium">Alergias</label>
          <input pInputText [(ngModel)]="form.alergias" placeholder="Alergias conocidas" />
        </div>
        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium">Médico Responsable</label>
          <input pInputText [(ngModel)]="form.medico_responsable" placeholder="Dr. Nombre Apellido" />
        </div>
        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium">Teléfono Médico</label>
          <input pInputText [(ngModel)]="form.telefono_medico" placeholder="722-000-0000" />
        </div>
      </div>
      <ng-template pTemplate="footer">
        <p-button label="Cancelar" [text]="true" (onClick)="dialogForm=false" />
        <p-button [label]="editandoId ? 'Guardar cambios' : 'Registrar condición'"
          icon="pi pi-save" [loading]="guardando()" (onClick)="guardar()" />
      </ng-template>
    </p-dialog>

    <!-- Dialog: Alerta de emergencia (SB-007) -->
    <p-dialog header="⚠ ALERTA MÉDICA — EMERGENCIA" [(visible)]="dialogAlerta"
      [modal]="true" [style]="{width:'680px'}" [draggable]="false">
      @for (alerta of alertas(); track alerta.tipo_condicion) {
        <div class="alerta-card">
          <div class="alerta-tipo">{{ alerta.tipo_condicion }}</div>
          <div class="alerta-desc">{{ alerta.descripcion }}</div>
          @if (alerta.medicacion_nombre) {
            <div class="alerta-med">
              <strong>Medicación:</strong> {{ alerta.medicacion_nombre }} — {{ alerta.dosis }}
              ({{ alerta.frecuencia }})
            </div>
          }
          @if (alerta.alergias) {
            <div class="alerta-alergia"><strong>⚠ Alergias:</strong> {{ alerta.alergias }}</div>
          }
          @if (alerta.medico_responsable) {
            <div class="alerta-medico">
              <strong>Médico:</strong> {{ alerta.medico_responsable }} — {{ alerta.telefono_medico }}
            </div>
          }
          @if (alerta.contacto_emergencia) {
            <div class="alerta-contacto">
              <strong>Contacto emergencia:</strong> {{ alerta.contacto_emergencia }} —
              <a [href]="'tel:' + alerta.tel_emergencia" class="text-primary-600 font-bold">
                {{ alerta.tel_emergencia }}
              </a>
            </div>
          }
        </div>
      }
      @if (alertas().length === 0) {
        <p class="text-center text-gray-500 py-4">Sin condiciones activas para este alumno.</p>
      }
      <ng-template pTemplate="footer">
        <p-button label="Cerrar" (onClick)="dialogAlerta=false" />
      </ng-template>
    </p-dialog>
  `,
  styles: [`
    .apex-page { padding: 1rem; }
    .apex-toolbar { display:flex; align-items:center; justify-content:space-between; margin-bottom:1rem; flex-wrap:wrap; gap:.5rem; }
    .apex-title { margin:0; font-size:1.25rem; font-weight:600; }
    .apex-toolbar-actions { display:flex; gap:.5rem; align-items:center; flex-wrap:wrap; }
    .alerta-card { background:var(--red-50); border:2px solid var(--red-400); border-radius:8px; padding:1rem; margin-bottom:.75rem; }
    .alerta-tipo { font-size:1.1rem; font-weight:700; color:var(--red-700); margin-bottom:.5rem; }
    .alerta-desc { color:var(--text-color); margin-bottom:.5rem; }
    .alerta-med, .alerta-medico, .alerta-contacto { font-size:.9rem; margin-bottom:.25rem; }
    .alerta-alergia { background:var(--yellow-100); padding:.25rem .5rem; border-radius:4px; color:var(--yellow-800); font-size:.9rem; margin-bottom:.25rem; }
  `],
})
export class CondicionesCronicasComponent implements OnInit implements OnInit, OnDestroy {
  private destroy$ = new Subject<void>();
  private api = inject(ApiService);
  private notify = inject(ApexNotificationService);

  readonly condicionesColumns: ColumnConfig[] = [
    { field: 'alumno_str',   header: 'Alumno' },
    { field: 'tipo_condicion', header: 'Tipo',       width: '130px' },
    { field: 'descripcion_str', header: 'Descripción' },
    { field: 'medicacion_str', header: 'Medicación',  width: '160px' },
    { field: 'medico_str',   header: 'Médico',       width: '160px' },
    { field: 'estado_str',   header: 'Estado',       width: '90px' },
  ];

  readonly condicionesFlat = computed(() =>
    this.condiciones().map(c => ({
      ...c,
      alumno_str:      `${c.alumno_nombre} (${c.numero_control})`,
      descripcion_str: c.descripcion.length > 80 ? c.descripcion.slice(0, 80) + '…' : c.descripcion,
      medicacion_str:  c.medicacion_nombre ? `${c.medicacion_nombre} — ${c.dosis ?? ''} ${c.frecuencia ?? ''}`.trim() : '—',
      medico_str:      c.medico_responsable ? `${c.medico_responsable}${c.telefono_medico ? ' · ' + c.telefono_medico : ''}` : '—',
      estado_str:      c.activa ? 'Activa' : 'Inactiva',
    }))
  );

  condiciones = signal<Condicion[]>([]);
  alertas     = signal<AlertaEmergencia[]>([]);
  cargando    = signal(false);
  guardando   = signal(false);

  dialogForm   = false;
  dialogAlerta = false;
  busquedaAlumnoId = '';
  editandoId: string | null = null;
  tipos = TIPOS;

  form = {
    alumno_id: '', tipo_condicion: '', descripcion: '',
    medicacion_nombre: '', dosis: '', frecuencia: '',
    alergias: '', medico_responsable: '', telefono_medico: '',
    activa: true,
  };

  ngOnInit() { this.cargar(); }

  cargar() {
    this.cargando.set(true);
    const params: any = {};
    if (this.busquedaAlumnoId) params.alumno_id = this.busquedaAlumnoId;
    this.api.get<Condicion[]>('/condiciones-cronicas', params).subscribe({
      next: d => { this.condiciones.set(d); this.cargando.set(false); },
      error: () => { this.cargando.set(false); this.notify.error('Error al cargar condiciones'); },
    });
  }

  abrirNueva() {
    this.editandoId = null;
    this.form = { alumno_id: this.busquedaAlumnoId || '', tipo_condicion: '', descripcion: '',
      medicacion_nombre: '', dosis: '', frecuencia: '', alergias: '', medico_responsable: '',
      telefono_medico: '', activa: true };
    this.dialogForm = true;
  }

  abrirEdicion(c: Condicion) {
    this.editandoId = c.id;
    this.form = {
      alumno_id: c.alumno_id, tipo_condicion: c.tipo_condicion,
      descripcion: c.descripcion, medicacion_nombre: c.medicacion_nombre ?? '',
      dosis: c.dosis ?? '', frecuencia: c.frecuencia ?? '',
      alergias: c.alergias ?? '', medico_responsable: c.medico_responsable ?? '',
      telefono_medico: c.telefono_medico ?? '', activa: c.activa,
    };
    this.dialogForm = true;
  }

  guardar() {
    if (!this.form.alumno_id || !this.form.tipo_condicion || !this.form.descripcion) {
      this.notify.warning('Alumno, tipo y descripción son obligatorios');
      return;
    }
    this.guardando.set(true);
    const req$ = this.editandoId
      ? this.api.patch(`/condiciones-cronicas/${this.editandoId}`, this.form)
      : this.api.post('/condiciones-cronicas', this.form);
    req$.subscribe({
      next: () => { this.guardando.set(false); this.dialogForm = false; this.notify.success('Condición guardada'); this.cargar(); },
      error: e => { this.guardando.set(false); this.notify.error(e.error?.detail ?? 'Error al guardar'); },
    });
  }

  eliminar(c: Condicion) {
    if (!confirm(`¿Eliminar la condición ${c.tipo_condicion} de ${c.alumno_nombre}?`)) return;
    this.api.delete(`/condiciones-cronicas/${c.id}`).subscribe({
      next: () => { this.notify.success('Condición eliminada'); this.cargar(); },
      error: e => this.notify.error(e.error?.detail ?? 'Error'),
    });
  }

  verAlerta() {
    if (!this.busquedaAlumnoId) return;
    this.api.get<AlertaEmergencia[]>(`/condiciones-cronicas/alumno/${this.busquedaAlumnoId}/alerta`).subscribe({
      next: d => { this.alertas.set(d); this.dialogAlerta = true; },
      error: () => this.notify.error('Error al cargar alerta de emergencia'),
    });
  }

  tipoSev(tipo: string): TagSev {
    const map: Record<string, TagSev> = {
      EPILEPSIA: 'danger', DIABETES: 'warn', CARDIACA: 'danger',
      ASMA: 'warn', ALERGIA: 'warn', HIPERTENSION: 'warn',
      DISCAPACIDAD_VISUAL: 'info', DISCAPACIDAD_AUDITIVA: 'info', OTRA: 'secondary',
    };
    return map[tipo] ?? 'secondary';
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
