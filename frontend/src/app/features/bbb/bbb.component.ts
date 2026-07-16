import { Component, OnDestroy, OnInit, signal, inject, computed, ChangeDetectionStrategy} from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject, takeUntil } from 'rxjs';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { TagModule } from 'primeng/tag';
import { TextareaModule } from 'primeng/textarea';
import { TooltipModule } from 'primeng/tooltip';
import { CardModule } from 'primeng/card';
import { TabsModule } from 'primeng/tabs';
import { ToggleSwitchModule } from 'primeng/toggleswitch';
import { ConfirmationService } from 'primeng/api';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { ApexNotificationService } from 'apex-component-library';
import { ContextService } from '../../core/services/context.service';
import { AuthService } from '../../core/services/auth.service';
import { ApiService } from '../../core/services/api.service';
import { grupoLabel } from '../../core/models';
import { AdesFormatDirective } from '../../shared/directives/ades-format.directive';

interface BbbReunion {
  id: string;
  meeting_id: string;
  nombre: string;
  descripcion: string | null;
  tipo: string;
  nombre_grupo: string | null;
  plantel_nombre: string | null;
  organizador: string | null;
  fecha_programada: string;
  duracion_max_min: number;
  grabar: boolean;
  estado: string;
  participantes_max: number;
}

interface BbbGrabacion {
  id: string;
  record_id: string;
  nombre: string;
  url_playback: string | null;
  duracion_segundos: number | null;
  publicada: boolean;
  fecha_grabacion: string | null;
}

const TIPOS_REUNION = [
  { label: 'Clase', value: 'CLASE' },
  { label: 'Tutoría', value: 'TUTORIA' },
  { label: 'Reunión de Padres', value: 'REUNION_PADRES' },
  { label: 'Asesoría', value: 'ASESORIA' },
  { label: 'Capacitación', value: 'CAPACITACION' },
  { label: 'Evento', value: 'EVENTO' },
];

const ESTADO_SEV: Record<string, string> = {
  PROGRAMADA: 'info',
  EN_CURSO: 'success',
  FINALIZADA: 'secondary',
  CANCELADA: 'danger',
};

/**
 * Módulo de videoconferencias mediante BigBlueButton (BBB).
 * Permite crear, listar y gestionar reuniones (clases, tutorías, reuniones de
 * padres, capacitaciones) y acceder a grabaciones. La unión a una reunión abre
 * el cliente BBB en una nueva pestaña usando la URL firmada con SHA-1 devuelta
 * por el BFF. Requiere nivelAcceso ≥ 3; la creación de reuniones requiere ≥ 4.
 */
@Component({
  selector: 'app-bbb',
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
  imports: [
    AdesFormatDirective,
    CommonModule, FormsModule, DatePipe,
    TableModule, ButtonModule, DialogModule, InputTextModule,
    SelectModule, TagModule, TextareaModule, TooltipModule,
    CardModule, TabsModule, ToggleSwitchModule, ConfirmDialogModule,
  ],
  providers: [ConfirmationService],
  template: `
<p-confirmDialog />
<div class="apex-page">
  <!-- KPI Strip -->
  <div class="apex-kpi-strip" style="margin-bottom:1rem">
    <div class="apex-kpi-card">
      <span class="apex-kpi-value">{{ programadas() }}</span>
      <span class="apex-kpi-label">Programadas</span>
    </div>
    <div class="apex-kpi-card">
      <span class="apex-kpi-value">{{ enCurso() }}</span>
      <span class="apex-kpi-label">En Curso</span>
    </div>
    <div class="apex-kpi-card">
      <span class="apex-kpi-value">{{ reuniones().length }}</span>
      <span class="apex-kpi-label">Total</span>
    </div>
    @if (!servidorConfigurado()) {
      <div class="apex-kpi-card" style="border-color:var(--color-warning)">
        <span class="apex-kpi-value" style="color:var(--color-warning)">
          <i class="pi pi-exclamation-triangle"></i>
        </span>
        <span class="apex-kpi-label">BBB no configurado</span>
      </div>
    }
  </div>

  @if (!servidorConfigurado()) {
    <div style="background:var(--surface-card);border:1px solid var(--color-warning);border-radius:8px;
                padding:1rem;margin-bottom:1rem;display:flex;align-items:center;gap:.75rem">
      <i class="pi pi-info-circle" style="color:var(--color-warning);font-size:1.25rem"></i>
      <div>
        <strong>Servidor BBB no configurado</strong><br/>
        <small>Establece <code>BBB_SERVER_URL</code> y <code>BBB_SHARED_SECRET</code> en el archivo .env para activar las videoconferencias.
        Puedes auto-hospedar BigBlueButton o usar un proveedor compatible.</small>
      </div>
    </div>
  }

  <p-tabs [value]="'reuniones'">
    <!-- ── Tab Reuniones ── -->
    <p-tabpanel value="reuniones" header="Reuniones">
      <div class="apex-toolbar">
        @if (puedeCrear()) {
          <p-button label="Nueva Reunión" icon="pi pi-plus" (onClick)="abrirCrear()" />
        }
        <span class="apex-toolbar-spacer"></span>
        <p-select [options]="tiposReunion" [(ngModel)]="filtroTipo"
                  optionLabel="label" optionValue="value" placeholder="Filtrar por tipo"
                  [showClear]="true" (onChange)="cargarReuniones()" style="min-width:180px" />
        <p-select [options]="estadosOpt" [(ngModel)]="filtroEstado"
                  optionLabel="label" optionValue="value" placeholder="Estado"
                  [showClear]="true" (onChange)="cargarReuniones()" style="min-width:140px" />
      </div>

      <p-table [value]="reuniones()" [loading]="cargando()" stripedRows
               [paginator]="true" [rows]="10">
        <ng-template pTemplate="header">
          <tr>
            <th>Reunión</th>
            <th>Tipo</th>
            <th>Fecha</th>
            <th>Duración</th>
            <th>Estado</th>
            <th style="width:180px">Acciones</th>
          </tr>
        </ng-template>
        <ng-template pTemplate="body" let-r>
          <tr>
            <td>
              <strong>{{ r.nombre }}</strong>
              <br/><small class="text-muted">{{ r.organizador }} · {{ r.nombre_grupo || r.plantel_nombre }}</small>
            </td>
            <td>{{ tipoLabel(r.tipo) }}</td>
            <td>{{ r.fecha_programada | date:'dd/MM/yy HH:mm' }}</td>
            <td>{{ r.duracion_max_min }} min</td>
            <td>
              <p-tag [value]="r.estado" [severity]="estadoSev(r.estado)" />
              @if (r.grabar) { <i class="pi pi-circle-fill" style="color:red;font-size:.6rem;margin-left:.25rem" pTooltip="Se grabará"></i> }
            </td>
            <td>
              @if (r.estado !== 'CANCELADA' && r.estado !== 'FINALIZADA') {
                <p-button icon="pi pi-video" severity="success" [text]="true" size="small"
                          ariaLabel="Unirse a la reunión como asistente"
                          pTooltip="Unirse como Asistente" (onClick)="unirse(r, 'asistente')" />
                @if (puedeCrear()) {
                  <p-button icon="pi pi-crown" severity="warn" [text]="true" size="small"
                            ariaLabel="Unirse a la reunión como moderador"
                            pTooltip="Unirse como Moderador" (onClick)="unirse(r, 'moderador')" />
                }
              }
              <p-button icon="pi pi-list" [text]="true" size="small"
                        ariaLabel="Ver grabaciones de esta reunión"
                        pTooltip="Ver grabaciones" (onClick)="verGrabaciones(r)" />
              @if (puedeCrear() && r.estado === 'EN_CURSO') {
                <p-button icon="pi pi-stop-circle" severity="danger" [text]="true" size="small"
                          ariaLabel="Terminar la reunión"
                          pTooltip="Terminar reunión" (onClick)="terminarReunion(r)" />
              }
              @if (esCoordi() && r.estado === 'PROGRAMADA') {
                <p-button icon="pi pi-ban" severity="danger" [text]="true" size="small"
                          ariaLabel="Cancelar la reunión"
                          pTooltip="Cancelar" (onClick)="cancelarReunion(r)" />
              }
            </td>
          </tr>
        </ng-template>
        <ng-template pTemplate="emptymessage">
          <tr><td colspan="6" style="text-align:center;padding:2rem;color:var(--text-muted)">
            No hay reuniones registradas.
          </td></tr>
        </ng-template>
      </p-table>
    </p-tabpanel>
  </p-tabs>
</div>

<!-- Dialog — Nueva Reunión -->
<p-dialog [(visible)]="crearVisible" header="Nueva Videoconferencia" [modal]="true"
          [style]="{width:'560px'}">
  <div style="display:flex;flex-direction:column;gap:1rem;padding:.5rem 0">
    <div>
      <label class="apex-label">Nombre de la reunión *</label>
      <input pInputText [(ngModel)]="crearForm.nombre" class="w-full" placeholder="Ej: Clase de Matemáticas 3A" />
    </div>
    <div>
      <label class="apex-label">Tipo *</label>
      <p-select [options]="tiposReunion" [(ngModel)]="crearForm.tipo"
                optionLabel="label" optionValue="value" class="w-full" />
    </div>
    <div style="display:grid;grid-template-columns:1fr 1fr;gap:.75rem">
      <div>
        <label class="apex-label">Grupo (opcional)</label>
        <p-select [options]="grupos()" [(ngModel)]="crearForm.grupo_id"
                  optionLabel="_label" optionValue="id" placeholder="Seleccionar grupo"
                  [showClear]="true" class="w-full" />
      </div>
      <div>
        <label class="apex-label">Duración máx. (min)</label>
        <input pInputText type="number" [(ngModel)]="crearForm.duracion_max_min" class="w-full" min="15" max="480" />
      </div>
    </div>
    <div style="display:grid;grid-template-columns:1fr 1fr;gap:.75rem">
      <div>
        <label class="apex-label">Fecha y hora *</label>
        <input pInputText type="datetime-local" [(ngModel)]="crearForm.fecha_programada" class="w-full" />
      </div>
      <div>
        <label class="apex-label">Participantes máx.</label>
        <input pInputText type="number" [(ngModel)]="crearForm.participantes_max" class="w-full" min="2" max="200" />
      </div>
    </div>
    <div>
      <label class="apex-label">Mensaje de bienvenida</label>
      <textarea pTextarea [(ngModel)]="crearForm.bienvenida_msg" rows="2" class="w-full"
                placeholder="Mensaje que verán los participantes al entrar"></textarea>
    </div>
    <div style="display:flex;align-items:center;gap:.75rem">
      <p-toggleswitch [(ngModel)]="crearForm.grabar" />
      <label>Grabar la sesión</label>
    </div>
  </div>
  <ng-template pTemplate="footer">
    <p-button label="Cancelar" severity="secondary" (onClick)="crearVisible = false" />
    <p-button label="Crear Reunión" icon="pi pi-video" [loading]="creando()" (onClick)="crearReunion()" />
  </ng-template>
</p-dialog>

<!-- Dialog — Grabaciones -->
<p-dialog [(visible)]="grabVisible" [header]="'Grabaciones — ' + grabReunionNombre()"
          [modal]="true" [style]="{width:'680px'}">
  @if (cargandoGrab()) {
    <div style="text-align:center;padding:2rem"><i class="pi pi-spinner pi-spin"></i> Cargando grabaciones...</div>
  } @else if (grabaciones().length === 0) {
    <div style="text-align:center;padding:2rem;color:var(--text-muted)">
      No hay grabaciones disponibles para esta reunión.
    </div>
  } @else {
    <p-table [value]="grabaciones()" stripedRows>
      <ng-template pTemplate="header">
        <tr><th>Nombre</th><th>Duración</th><th>Estado</th><th>Ver</th></tr>
      </ng-template>
      <ng-template pTemplate="body" let-g>
        <tr>
          <td>{{ g.nombre }}<br/><small class="text-muted">{{ g.fecha_grabacion | date:'dd/MM/yy HH:mm' }}</small></td>
          <td>{{ g.duracion_segundos ? formatTiempo(g.duracion_segundos) : '—' }}</td>
          <td><p-tag [value]="g.publicada ? 'Publicada' : 'No publicada'"
                     [severity]="g.publicada ? 'success' : 'secondary'" /></td>
          <td>
            @if (g.url_playback) {
              <a [href]="g.url_playback" target="_blank" rel="noopener">
                <p-button icon="pi pi-external-link" [text]="true" size="small" pTooltip="Abrir grabación" />
              </a>
            }
          </td>
        </tr>
      </ng-template>
    </p-table>
  }
  <ng-template pTemplate="footer">
    <p-button label="Cerrar" severity="secondary" (onClick)="grabVisible = false" />
  </ng-template>
</p-dialog>
  `,
})
export class BbbComponent implements OnInit, OnDestroy {
  private destroy$ = new Subject<void>();
  private api = inject(ApiService);
  private notify = inject(ApexNotificationService);
  private ctx = inject(ContextService);
  private auth = inject(AuthService);
  private readonly confirm = inject(ConfirmationService);

  // State
  reuniones = signal<BbbReunion[]>([]);
  grupos = signal<any[]>([]);
  grabaciones = signal<BbbGrabacion[]>([]);
  cargando = signal(false);
  creando = signal(false);
  cargandoGrab = signal(false);
  servidorConfigurado = signal(true);
  filtroTipo: string | null = null;
  filtroEstado: string | null = null;

  // Dialogs
  crearVisible = false;
  grabVisible = false;
  grabReunionNombre = signal('');

  crearForm = {
    nombre: '', tipo: 'CLASE', grupo_id: null as string | null,
    fecha_programada: '', duracion_max_min: 60,
    grabar: false, bienvenida_msg: '', participantes_max: 50,
  };

  // Options
  tiposReunion = TIPOS_REUNION;
  estadosOpt = [
    { label: 'Programada', value: 'PROGRAMADA' },
    { label: 'En Curso', value: 'EN_CURSO' },
    { label: 'Finalizada', value: 'FINALIZADA' },
    { label: 'Cancelada', value: 'CANCELADA' },
  ];

  // Computed KPIs
  programadas = computed(() => this.reuniones().filter(r => r.estado === 'PROGRAMADA').length);
  enCurso = computed(() => this.reuniones().filter(r => r.estado === 'EN_CURSO').length);
  puedeCrear = computed(() => (this.ctx.nivelAcceso() ?? 5) <= 4);
  esCoordi = computed(() => (this.ctx.nivelAcceso() ?? 5) <= 3);

  ngOnInit() {
    this.cargarReuniones();
    this.cargarGrupos();
    this.verificarServidor();
  }

  verificarServidor() {
    this.api.get<any>('/bbb/info').pipe(takeUntil(this.destroy$)).subscribe({
      next: () => this.servidorConfigurado.set(true),
      error: (e) => {
        if (e?.status === 503) this.servidorConfigurado.set(false);
        else this.servidorConfigurado.set(true);
      },
    });
  }

  cargarReuniones() {
    this.cargando.set(true);
    const params: any = {};
    if (this.ctx.plantel()?.id) params.plantel_id = this.ctx.plantel()?.id;
    if (this.filtroEstado) params.estado = this.filtroEstado;
    this.api.get<BbbReunion[]>('/bbb/reuniones', params).pipe(takeUntil(this.destroy$)).subscribe({
      next: r => { this.reuniones.set(r); this.cargando.set(false); },
      error: () => this.cargando.set(false),
    });
  }

  cargarGrupos() {
    this.api.get<any[]>('/grupos').pipe(takeUntil(this.destroy$)).subscribe({
      next: g => this.grupos.set(g.map((x: any) => ({ ...x, _label: grupoLabel(x) || x.nombre_grupo }))),
      error: () => {},
    });
  }

  abrirCrear() {
    const now = new Date();
    now.setMinutes(now.getMinutes() - now.getTimezoneOffset());
    this.crearForm = {
      nombre: '', tipo: 'CLASE', grupo_id: null,
      fecha_programada: now.toISOString().slice(0, 16),
      duracion_max_min: 60, grabar: false, bienvenida_msg: '', participantes_max: 50,
    };
    this.crearVisible = true;
  }

  crearReunion() {
    if (!this.crearForm.nombre || !this.crearForm.fecha_programada) {
      this.notify.warning('Completa el nombre y la fecha de la reunión');
      return;
    }
    this.creando.set(true);
    this.api.post('/bbb/reuniones', {
      ...this.crearForm,
      plantel_id: this.ctx.plantel()?.id,
    }).pipe(takeUntil(this.destroy$)).subscribe({
      next: () => {
        this.notify.success('Reunión creada. Comparte el enlace cuando sea el momento.');
        this.crearVisible = false;
        this.creando.set(false);
        this.cargarReuniones();
      },
      error: (e) => {
        this.notify.error(e?.error?.detail || 'Error al crear la reunión');
        this.creando.set(false);
      },
    });
  }

  unirse(r: BbbReunion, rol: 'moderador' | 'asistente') {
    this.api.get<{ join_url: string }>(`/bbb/reuniones/${r.id}/join`, { rol }).pipe(takeUntil(this.destroy$)).subscribe({
      next: d => window.open(d.join_url, '_blank', 'noopener'),
      error: (e) => this.notify.error(e?.error?.detail || 'No se pudo obtener el enlace de acceso'),
    });
  }

  terminarReunion(r: BbbReunion) {
    if (!confirm(`¿Terminar la reunión "${r.nombre}"?`)) return;
    this.api.post(`/bbb/reuniones/${r.id}/terminar`, {}).pipe(takeUntil(this.destroy$)).subscribe({
      next: () => { this.notify.success('Reunión terminada'); this.cargarReuniones(); },
      error: () => this.notify.error('Error al terminar la reunión'),
    });
  }

  cancelarReunion(r: BbbReunion) {
    this.confirm.confirm({
      message: `¿Cancelar la reunión "${r.nombre}"?`,
      header: 'Confirmar eliminación',
      icon: 'pi pi-exclamation-triangle',
      accept: () => {
        this.api.delete(`/bbb/reuniones/${r.id}`).pipe(takeUntil(this.destroy$)).subscribe({
          next: () => { this.notify.success('Reunión cancelada'); this.cargarReuniones(); },
          error: () => this.notify.error('Error al cancelar la reunión'),
        });
      },
    });
  }

  verGrabaciones(r: BbbReunion) {
    this.grabReunionNombre.set(r.nombre);
    this.cargandoGrab.set(true);
    this.grabVisible = true;
    this.grabaciones.set([]);
    this.api.get<BbbGrabacion[]>(`/bbb/reuniones/${r.id}/grabaciones`).pipe(takeUntil(this.destroy$)).subscribe({
      next: g => { this.grabaciones.set(g); this.cargandoGrab.set(false); },
      error: () => this.cargandoGrab.set(false),
    });
  }

  tipoLabel(tipo: string) {
    return TIPOS_REUNION.find(t => t.value === tipo)?.label ?? tipo;
  }

  estadoSev(estado: string): any {
    return ESTADO_SEV[estado] ?? 'secondary';
  }

  formatTiempo(segundos: number): string {
    const h = Math.floor(segundos / 3600);
    const m = Math.floor((segundos % 3600) / 60);
    return h > 0 ? `${h}h ${m}m` : `${m}m`;
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
