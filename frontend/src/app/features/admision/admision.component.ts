import { Component, OnInit, signal, inject, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/services/api.service';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { TagModule } from 'primeng/tag';
import { ToastModule } from 'primeng/toast';
import { TextareaModule } from 'primeng/textarea';
import { TooltipModule } from 'primeng/tooltip';
import { InputNumberModule } from 'primeng/inputnumber';
import { MessageModule } from 'primeng/message';
import { MessageService } from 'primeng/api';
import { ApexNotificationService } from 'apex-component-library';
import { ImportButtonComponent } from '../../shared/components/import-button/import-button.component';
import { ContextService } from '../../core/services/context.service';
import { InteractiveGridComponent, ColumnConfig } from '../../shared/components/interactive-grid/interactive-grid.component';

type TagSev = 'success' | 'info' | 'warn' | 'danger' | 'secondary' | 'contrast';

interface Solicitud {
  id: string;
  nombre: string;
  apellido_paterno: string;
  apellido_materno: string | null;
  curp: string;
  nivel_solicitado: string;
  grado_solicitado: number;
  estado: string;
  nombre_tutor: string;
  email_tutor: string | null;
  fecha_solicitud: string;
  promedio_procedencia: number | null;
  escuela_procedencia: string | null;
  puntuacion_diagnostico?: number | null;
  observaciones_diagnostico?: string | null;
}

const ESTADOS = [
  { label: 'Todos', value: '' },
  { label: 'Pendiente', value: 'PENDIENTE' },
  { label: 'Diagnóstico', value: 'DIAGNOSTICO' },
  { label: 'Aceptado', value: 'ACEPTADO' },
  { label: 'Lista de espera', value: 'LISTA_ESPERA' },
  { label: 'Rechazado', value: 'RECHAZADO' },
  { label: 'Inscrito', value: 'INSCRITO' },
];

const NIVELES = [
  { label: 'Primaria', value: 'PRIMARIA' },
  { label: 'Secundaria', value: 'SECUNDARIA' },
  { label: 'Preparatoria', value: 'PREPARATORIA' },
];

/**
 * Gestiona el proceso de admisión de nuevos alumnos al Instituto Nevadi.
 * Administra el ciclo de vida de solicitudes: PENDIENTE → DIAGNOSTICO →
 * ACEPTADO / LISTA_ESPERA / RECHAZADO → INSCRITO, para los tres niveles
 * (Primaria, Secundaria, Preparatoria). Permite importar solicitudes masivas
 * vía CSV/Excel y asignar resultados de diagnóstico. Requiere nivelAcceso ≥ 4.
 */
@Component({
  selector: 'app-admision',
  standalone: true,
  imports: [
    CommonModule, FormsModule, ButtonModule, DialogModule,
    InputTextModule, SelectModule, TagModule, ToastModule, TextareaModule,
    TooltipModule, InputNumberModule, MessageModule, ImportButtonComponent,
    InteractiveGridComponent,
  ],
  providers: [MessageService],
  template: `
    <p-toast />
    <div class="apex-page">
      <div class="apex-toolbar">
        <h2 class="apex-title">Admisión de Alumnos</h2>
        <div class="apex-toolbar-actions">
          <p-select [options]="estados" optionLabel="label" optionValue="value"
            [(ngModel)]="filtroEstado" placeholder="Estado…" [style]="{width:'150px'}" />
          <p-button label="Filtrar" icon="pi pi-filter" size="small" (onClick)="cargar()" />
          <app-import-button entidad="preinscritos-sep" [onSuccess]="cargar.bind(this)" />
          <p-button label="Nueva Solicitud" icon="pi pi-plus" size="small" (onClick)="abrirNueva()" />
        </div>
      </div>

      <div class="stats-bar">
        <span class="stat-chip">Total: <strong>{{ solicitudes().length }}</strong></span>
        <span class="stat-chip warn">Pendientes: <strong>{{ countEstado('PENDIENTE') }}</strong></span>
        <span class="stat-chip success">Aceptadas: <strong>{{ countEstado('ACEPTADO') }}</strong></span>
        <span class="stat-chip info">Lista espera: <strong>{{ countEstado('LISTA_ESPERA') }}</strong></span>
        <span class="stat-chip danger">Rechazadas: <strong>{{ countEstado('RECHAZADO') }}</strong></span>
      </div>

      <app-interactive-grid
        [data]="solicitudesFlat()"
        [columns]="solicitudColumns"
        [loading]="cargando()"
        [showDelete]="false"
        (rowSelected)="abrirDetalle($event)"
      />
    </div>

    <!-- Dialog: Detalle de Solicitud y Evaluación -->
    <p-dialog header="Detalle de la Solicitud" [(visible)]="dlgDetalle"
      [modal]="true" [style]="{width:'550px'}" [draggable]="false">
      @if (solicitudSeleccionada()) {
        <div class="flex flex-col gap-3 p-3">
          <div class="grid grid-cols-2 gap-2 border-b pb-3">
            <div><strong>Aspirante:</strong> {{ solicitudSeleccionada()!.nombre }} {{ solicitudSeleccionada()!.apellido_paterno }}</div>
            <div><strong>CURP:</strong> {{ solicitudSeleccionada()!.curp }}</div>
            <div><strong>Nivel/Grado:</strong> {{ solicitudSeleccionada()!.nivel_solicitado }} {{ solicitudSeleccionada()!.grado_solicitado }}°</div>
            <div><strong>Estado Actual:</strong> <p-tag [value]="solicitudSeleccionada()!.estado" [severity]="estadoSev(solicitudSeleccionada()!.estado)" /></div>
          </div>

          <div class="flex flex-col gap-2 border-b pb-3">
            <h3 class="font-semibold text-primary m-0">Evaluación Diagnóstica</h3>
            <div class="flex items-center gap-3">
              <label class="text-sm font-medium w-24">Puntuación:</label>
              <p-inputnumber [(ngModel)]="evalScore" [min]="0" [max]="100" [showButtons]="true" [style]="{width: '120px'}" />
            </div>
            <div class="flex flex-col gap-1">
              <label class="text-sm font-medium">Observaciones:</label>
              <textarea pTextarea [(ngModel)]="evalObs" rows="3" placeholder="Comentarios del evaluador..."></textarea>
            </div>
            <div class="flex justify-end mt-2">
              <p-button label="Guardar Evaluación" icon="pi pi-save" severity="primary" size="small" (onClick)="guardarEvaluacion()" />
            </div>
          </div>

          <div class="flex flex-col gap-2">
            <h3 class="font-semibold text-primary m-0">Carta de Aceptación / Rechazo</h3>
            <div class="flex items-center gap-2">
              <input pInputText [(ngModel)]="cartaTemplateId" placeholder="ID Plantilla Carbone" class="w-full text-sm" />
              <p-button label="Descargar PDF" icon="pi pi-file-pdf" severity="danger" size="small" (onClick)="descargarCarta()" />
            </div>
          </div>

          @if (solicitudSeleccionada()!.estado === 'ACEPTADO' || solicitudSeleccionada()!.estado === 'PENDIENTE') {
            <div class="flex flex-col gap-2 border-t pt-3">
              <h3 class="font-semibold text-primary m-0" style="color:var(--green-700)">
                <i class="pi pi-user-plus"></i> Inscribir Alumno
              </h3>
              <p-message severity="info" icon="pi pi-info-circle"
                text="Al inscribir se creará automáticamente la cuenta de sistema del alumno y del padre/tutor." />
              <div class="flex gap-2 mt-1">
                <p-button label="Aprobar e Inscribir" icon="pi pi-check-circle"
                  severity="success" size="small"
                  [loading]="inscribiendo()"
                  (onClick)="confirmarInscripcion()" />
              </div>
            </div>
          }
          @if (solicitudSeleccionada()!.estado === 'INSCRITO') {
            <p-message severity="success" icon="pi pi-check"
              text="Este aspirante ya fue inscrito como alumno del sistema." />
          }
        </div>
      }
      <ng-template pTemplate="footer">
        <p-button label="Cerrar" [text]="true" (onClick)="dlgDetalle=false" />
      </ng-template>
    </p-dialog>

    <!-- Dialog confirmación inscripción -->
    <p-dialog [visible]="dlgConfirmInscripcion()" (visibleChange)="dlgConfirmInscripcion.set($event)"
      header="Confirmar Inscripción" [modal]="true" [draggable]="false" [style]="{width:'460px'}">
      @if (solicitudSeleccionada()) {
        <div style="display:flex;flex-direction:column;gap:.75rem">
          <p>¿Confirmas la inscripción de <strong>{{ solicitudSeleccionada()!.nombre }} {{ solicitudSeleccionada()!.apellido_paterno }}</strong>?</p>
          <p-message severity="warn" icon="pi pi-exclamation-triangle"
            text="Esta acción creará cuentas de acceso al sistema para el alumno y su tutor. No se puede deshacer automáticamente." />
          <div style="display:grid;grid-template-columns:1fr 1fr;gap:.5rem">
            <div style="display:flex;flex-direction:column;gap:.25rem">
              <label style="font-size:.82rem;color:var(--text-secondary)">Grupo / Ciclo escolar</label>
              <input pInputText [(ngModel)]="inscribirForm.grupoClave" placeholder="Opcional: clave de grupo" />
            </div>
            <div style="display:flex;flex-direction:column;gap:.25rem">
              <label style="font-size:.82rem;color:var(--text-secondary)">Matrícula (opcional)</label>
              <input pInputText [(ngModel)]="inscribirForm.matricula" placeholder="Se genera automática si vacío" />
            </div>
          </div>
        </div>
      }
      <ng-template pTemplate="footer">
        <p-button label="Cancelar" severity="secondary" [text]="true" (onClick)="dlgConfirmInscripcion.set(false)" />
        <p-button label="Inscribir" icon="pi pi-check" severity="success"
          [loading]="inscribiendo()" (onClick)="ejecutarInscripcion()" />
      </ng-template>
    </p-dialog>

    <!-- Dialog: Nueva Solicitud -->
    <p-dialog header="Nueva Solicitud de Admisión" [(visible)]="dlgNueva"
      [modal]="true" [style]="{width:'640px'}" [draggable]="false">
      <div class="grid grid-cols-2 gap-3 p-3">
        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium">Nombre *</label>
          <input pInputText [(ngModel)]="form.nombre" />
        </div>
        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium">Apellido Paterno *</label>
          <input pInputText [(ngModel)]="form.apellidoPaterno" />
        </div>
        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium">Apellido Materno</label>
          <input pInputText [(ngModel)]="form.apellidoMaterno" />
        </div>
        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium">CURP *</label>
          <input pInputText [(ngModel)]="form.curp" maxlength="18" style="text-transform:uppercase" />
        </div>
        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium">Fecha de Nacimiento *</label>
          <input pInputText [(ngModel)]="form.fechaNacimiento" type="date" />
        </div>
        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium">Nivel Solicitado *</label>
          <p-select [options]="niveles" optionLabel="label" optionValue="value"
            [(ngModel)]="form.nivel" placeholder="Seleccionar…" />
        </div>
        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium">Grado *</label>
          <p-inputnumber [(ngModel)]="form.grado" [min]="1" [max]="6" [showButtons]="true" />
        </div>
        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium">Promedio Procedencia</label>
          <p-inputnumber [(ngModel)]="form.promedio" [minFractionDigits]="1" [maxFractionDigits]="2" />
        </div>
        <div class="flex flex-col gap-1 col-span-2">
          <label class="text-sm font-medium">Escuela de Procedencia</label>
          <input pInputText [(ngModel)]="form.escuela" />
        </div>
        <div class="flex flex-col gap-1 col-span-2 border-t pt-3">
          <label class="text-sm font-semibold">Datos del Tutor</label>
        </div>
        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium">Nombre del Tutor *</label>
          <input pInputText [(ngModel)]="form.tutorNombre" />
        </div>
        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium">Teléfono del Tutor *</label>
          <input pInputText [(ngModel)]="form.tutorTelefono" />
        </div>
        <div class="flex flex-col gap-1 col-span-2">
          <label class="text-sm font-medium">Email del Tutor</label>
          <input pInputText [(ngModel)]="form.tutorEmail" type="email" />
        </div>
      </div>
      <ng-template pTemplate="footer">
        <p-button label="Cancelar" [text]="true" (onClick)="dlgNueva=false" />
        <p-button label="Registrar Solicitud" icon="pi pi-save"
          [loading]="guardando()" (onClick)="guardar()" />
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
    .stat-chip.info { border-color:var(--blue-400); background:var(--blue-50); }
    .stat-chip.danger { border-color:var(--red-400); background:var(--red-50); }
    .grid { display:grid; }
    .grid-cols-2 { grid-template-columns: 1fr 1fr; }
    .col-span-2 { grid-column: span 2; }
  `],
})
export class AdmisionComponent implements OnInit {
  private api = inject(ApiService);
  private notify = inject(ApexNotificationService);
  private ctx = inject(ContextService);

  solicitudes = signal<Solicitud[]>([]);

  readonly solicitudColumns: ColumnConfig[] = [
    { field: 'nombreCompleto',  header: 'Nombre',       sortable: true, filterable: true },
    { field: 'curp',            header: 'CURP',         sortable: true, filterable: true },
    { field: 'nivelGrado',      header: 'Nivel / Grado',sortable: true, filterable: true },
    { field: 'promedio',        header: 'Promedio',     sortable: true, filterable: false },
    { field: 'diagScore',       header: 'Diag. Score',  sortable: true, filterable: false },
    { field: 'nombre_tutor',    header: 'Tutor',        sortable: true, filterable: true },
    { field: 'fechaSolicitud',  header: 'Fecha',        sortable: true, filterable: false },
    { field: 'estado',          header: 'Estado',       sortable: true, filterable: true },
  ];

  readonly solicitudesFlat = computed(() =>
    this.solicitudes().map(s => ({
      ...s,
      nombreCompleto: `${s.nombre} ${s.apellido_paterno}`,
      nivelGrado: `${s.nivel_solicitado} ${s.grado_solicitado}°`,
      promedio: s.promedio_procedencia != null ? String(s.promedio_procedencia) : '—',
      diagScore: s.puntuacion_diagnostico != null ? String(s.puntuacion_diagnostico) : '—',
      fechaSolicitud: s.fecha_solicitud ? new Date(s.fecha_solicitud).toLocaleDateString('es-MX') : '',
    }))
  );
  cargando = signal(false);
  guardando = signal(false);

  filtroEstado = '';
  dlgNueva = false;
  dlgDetalle = false;

  solicitudSeleccionada = signal<Solicitud | null>(null);
  evalScore = 0;
  evalObs = '';
  cartaTemplateId = 'carta_admision_template';

  estados = ESTADOS;
  niveles = NIVELES;

  form = {
    nombre: '', apellidoPaterno: '', apellidoMaterno: '', curp: '',
    fechaNacimiento: '', nivel: 'PRIMARIA', grado: 1,
    promedio: null as number | null, escuela: '',
    tutorNombre: '', tutorTelefono: '', tutorEmail: '',
  };

  ngOnInit() { this.cargar(); }

  cargar() {
    this.cargando.set(true);
    const params: any = {};
    if (this.filtroEstado) params.estado = this.filtroEstado;
    const plantelId = this.ctx.plantel()?.id;
    if (plantelId) params.plantel_id = plantelId;

    this.api.get<Solicitud[]>('/procesos/admision', params).subscribe({
      next: d => { this.solicitudes.set(d); this.cargando.set(false); },
      error: () => { this.cargando.set(false); this.notify.error('Error', 'No se pudieron cargar las solicitudes'); },
    });
  }

  abrirNueva() {
    this.form = { nombre: '', apellidoPaterno: '', apellidoMaterno: '', curp: '', fechaNacimiento: '', nivel: 'PRIMARIA', grado: 1, promedio: null, escuela: '', tutorNombre: '', tutorTelefono: '', tutorEmail: '' };
    this.dlgNueva = true;
  }

  guardar() {
    if (!this.form.nombre || !this.form.apellidoPaterno || !this.form.curp || !this.form.fechaNacimiento || !this.form.tutorNombre || !this.form.tutorTelefono) {
      this.notify.warning('Validación', 'Complete los campos obligatorios');
      return;
    }
    const plantelId = this.ctx.plantel()?.id;
    if (!plantelId) {
      this.notify.warning('Plantel requerido', 'Seleccione un plantel desde el contexto antes de registrar');
      return;
    }
    this.guardando.set(true);
    const payload = {
      nombre: this.form.nombre,
      apellido_paterno: this.form.apellidoPaterno,
      apellido_materno: this.form.apellidoMaterno || null,
      fecha_nacimiento: this.form.fechaNacimiento,
      curp: this.form.curp.toUpperCase(),
      nivel_solicitado: this.form.nivel,
      grado_solicitado: this.form.grado,
      plantel_id: plantelId,
      nombre_tutor: this.form.tutorNombre,
      telefono_tutor: this.form.tutorTelefono,
      email_tutor: this.form.tutorEmail || null,
      escuela_procedencia: this.form.escuela || null,
      promedio_procedencia: this.form.promedio
    };
    this.api.post('/procesos/admision', payload).subscribe({
      next: () => {
        this.notify.success('Registrada', 'Solicitud de admisión registrada');
        this.dlgNueva = false;
        this.cargar();
      },
      error: (e: any) => {
        this.guardando.set(false);
        this.notify.error('Error', e.error?.detail ?? 'Error al registrar solicitud');
      }
    });
  }

  resolver(s: Solicitud, decision: string) {
    this.api.post(`/procesos/admision/${s.id}/aceptar`, { decision }).subscribe({
      next: (r: any) => { this.notify.success('Éxito', r.message); this.cargar(); },
      error: e => this.notify.error('Error', e.error?.detail ?? 'Error'),
    });
  }

  notificarEspera(s: Solicitud) {
    this.api.post(`/procesos/lista-espera/${s.id}/notificar`, {}).subscribe({
      next: (r: any) => { this.notify.success('Éxito', r.message); this.cargar(); },
      error: e => this.notify.error('Error', e.error?.detail ?? 'Error'),
    });
  }

  abrirDetalle(s: Solicitud) {
    this.solicitudSeleccionada.set(s);
    this.evalScore = s.puntuacion_diagnostico ?? 0;
    this.evalObs = s.observaciones_diagnostico ?? '';
    this.dlgDetalle = true;
  }

  guardarEvaluacion() {
    const s = this.solicitudSeleccionada();
    if (!s) return;
    this.api.patch(`/procesos/admision/${s.id}/evaluacion`, {
      puntuacion_diagnostico: this.evalScore,
      observaciones_diagnostico: this.evalObs
    }).subscribe({
      next: (r: any) => {
        this.notify.success('Guardado', r.message);
        this.dlgDetalle = false;
        this.cargar();
      },
      error: (e) => this.notify.error('Error', e.error?.detail ?? 'Error al registrar evaluación')
    });
  }

  descargarCarta() {
    const s = this.solicitudSeleccionada();
    if (!s) return;
    if (!this.cartaTemplateId) {
      this.notify.warning('Aviso', 'Ingrese el ID de la plantilla Carbone');
      return;
    }
    const url = `/api/v1/procesos/admision/${s.id}/carta?template_id=${this.cartaTemplateId}`;
    window.open(url, '_blank');
    this.notify.success('PDF', 'Generando carta de admisión PDF...');
  }

  // ── Aprobar e Inscribir ───────────────────────────────────────────────────
  inscribiendo = signal(false);
  dlgConfirmInscripcion = signal(false);
  inscribirForm = { grupoClave: '', matricula: '' };

  confirmarInscripcion(): void {
    this.inscribirForm = { grupoClave: '', matricula: '' };
    this.dlgConfirmInscripcion.set(true);
  }

  ejecutarInscripcion(): void {
    const s = this.solicitudSeleccionada();
    if (!s) return;
    this.inscribiendo.set(true);
    const payload: Record<string, string> = {};
    if (this.inscribirForm.grupoClave) payload['grupoClave'] = this.inscribirForm.grupoClave;
    if (this.inscribirForm.matricula)  payload['matricula']  = this.inscribirForm.matricula;

    this.api.post<any>(`/procesos/admision/${s.id}/aprobar-e-inscribir`, payload).subscribe({
      next: (r) => {
        this.inscribiendo.set(false);
        this.dlgConfirmInscripcion.set(false);
        this.dlgDetalle = false;
        this.notify.success(
          'Alumno inscrito',
          `Matrícula: ${r.matricula ?? '—'} · Usuario: ${r.usuario_alumno ?? '—'}`
        );
        this.cargar();
      },
      error: (e) => {
        this.inscribiendo.set(false);
        this.notify.error('Error al inscribir', e.error?.detail ?? e.error?.message ?? 'Error desconocido');
      },
    });
  }

  countEstado(estado: string): number {
    return this.solicitudes().filter(s => s.estado === estado).length;
  }

  estadoSev(e: string): TagSev {
    const m: Record<string, TagSev> = {
      PENDIENTE: 'warn', DIAGNOSTICO: 'info', ACEPTADO: 'success', INSCRITO: 'success',
      LISTA_ESPERA: 'info', RECHAZADO: 'danger', NOTIFICADO: 'info',
    };
    return m[e] ?? 'secondary';
  }
}
