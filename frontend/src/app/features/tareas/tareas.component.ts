import { Component, OnDestroy, OnInit, inject, signal, computed, effect, ChangeDetectionStrategy} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject, takeUntil } from 'rxjs';
import { CardModule } from 'primeng/card';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { SelectModule } from 'primeng/select';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { TextareaModule } from 'primeng/textarea';
import { TableModule } from 'primeng/table';
import { TooltipModule } from 'primeng/tooltip';
import { DatePickerModule } from 'primeng/datepicker';

import { ApiService } from '../../core/services/api.service';
import { ContextService } from '../../core/services/context.service';
import type { Tarea, Materia } from '../../core/models';
import { ApexNotificationService } from 'apex-component-library';
import { InteractiveGridComponent, ColumnConfig } from '../../shared/components/interactive-grid/interactive-grid.component';
import { AdesFormatDirective } from '../../shared/directives/ades-format.directive';

@Component({
  selector: 'app-tareas',
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
  imports: [
    AdesFormatDirective,
    CommonModule, FormsModule,
    TableModule, CardModule, ButtonModule, TagModule,
    SelectModule, DialogModule, InputTextModule, InputNumberModule,
    TextareaModule, TooltipModule, DatePickerModule,
    InteractiveGridComponent,
  ],
  template: `
    <div class="page-header">
      <div>
        <h2>Tareas</h2>
        <p class="subtitle">Gestión de tareas y entregas</p>
      </div>
      @if (puedeCrear()) {
        <p-button label="Nueva Tarea" icon="pi pi-plus" (onClick)="abrirCreacion()" />
      }
    </div>

    <!-- Filtro de dominio: Materia (la cascada plantel→nivel→grado→grupo vive en el topbar) -->
    <div class="filter-bar">
      <p-select [options]="materiasOpts()" [(ngModel)]="selectedMateriaId"
        optionLabel="nombre_materia" optionValue="id" placeholder="Materia"
        (onChange)="loadTareas()"
        [showClear]="true" [disabled]="!selectedGrupoId"
        [filter]="true" filterPlaceholder="Buscar..." styleClass="filter-select" />
    </div>

    @if (!selectedGrupoId || !selectedMateriaId) {
      <div class="empty-state">
        <i class="pi pi-info-circle" style="font-size:2rem;color:var(--text-muted)"></i>
        <p>Selecciona plantel, nivel, grado, grupo y materia para ver y gestionar las tareas.</p>
      </div>
    } @else {

      <!-- KPIs -->
      <div class="kpi-row">
        <div class="kpi-card">
          <div class="kpi-value">{{ tareas().length }}</div>
          <div class="kpi-label">Tareas activas</div>
        </div>
        <div class="kpi-card kpi-warn">
          <div class="kpi-value">{{ pendientesAlumno() }}</div>
          <div class="kpi-label">Pendientes alumno</div>
        </div>
        @if (esDocente()) {
          <div class="kpi-card kpi-blue">
            <div class="kpi-value">{{ entregasPendientes().length }}</div>
            <div class="kpi-label">Por calificar</div>
          </div>
        }
      </div>

      <!-- Listado de tareas -->
      <h3>Tareas de la materia</h3>
      <app-interactive-grid
        [data]="tareas()"
        [columns]="tareasColumns"
        [showDelete]="false"
        (rowSelected)="abrirEdicion($event)"
      />

      <!-- ── Vista ALUMNO: mis entregas ── -->
      @if (!esDocente()) {
        <h3 style="margin-top:2rem">Mis entregas</h3>
        <p-table [value]="entregas()" styleClass="p-datatable-sm">
          <ng-template pTemplate="header">
            <tr>
              <th>Tarea</th>
              <th style="width:120px">Vence</th>
              <th style="width:90px">Estado</th>
              <th style="width:120px">Calificación</th>
              <th style="width:130px">Acción</th>
            </tr>
          </ng-template>
          <ng-template pTemplate="body" let-e>
            <tr>
              <td>{{ e.titulo }}</td>
              <td>{{ e.fecha_limite | date:'dd/MM/yyyy' }}</td>
              <td>
                <p-tag [value]="estatusLabel(e.estatus_entrega)"
                       [severity]="estatusSeverity(e.estatus_entrega)" />
                @if (e.vencida) {
                  <p-tag value="Vencida" severity="danger" style="margin-left:4px" />
                }
              </td>
               <td>
                 @if (e.calificacion_obtenida != null) {
                   <strong>{{ e.calificacion_obtenida }}</strong>
                   @if (e.comentario_profesor) {
                     <span style="font-size:.8rem;color:var(--text-muted);display:block">{{ e.comentario_profesor }}</span>
                   }
                   @if (e.plagio_porcentaje !== undefined && e.plagio_porcentaje !== null) {
                     <div style="margin-top:4px; font-size:.75rem; color:#b45309; display:flex; align-items:center; gap:4px;">
                       <span style="font-weight:600">Plagio:</span>
                       <p-tag [value]="e.plagio_porcentaje + '%'" [severity]="plagioSeverity(e.plagio_porcentaje)" styleClass="scale-75"></p-tag>
                       @if (e.plagio_reporte_url) {
                         · <a [href]="e.plagio_reporte_url" target="_blank" style="text-decoration:underline;">Reporte</a>
                       }
                     </div>
                   }
                   @if (e.feedback_audio_url) {
                     <div style="margin-top:6px;">
                       <span style="font-size:.75rem; display:block; color:#166534; font-weight:600;"><i class="pi pi-volume-up"></i> Retroalimentación de audio:</span>
                       <audio [src]="mediaUrl(e.feedback_audio_url)" controls style="width: 100%; max-width: 220px; height: 32px; margin-top:2px;"></audio>
                     </div>
                   }
                   @if (e.feedback_video_url) {
                     <div style="margin-top:6px;">
                       <span style="font-size:.75rem; display:block; color:#166534; font-weight:600;"><i class="pi pi-video"></i> Retroalimentación de video:</span>
                       <video [src]="mediaUrl(e.feedback_video_url)" controls style="width: 100%; max-width: 220px; height: 120px; margin-top:2px; border-radius:4px; border:1px solid #bbf7d0;"></video>
                     </div>
                   }
                 } @else { — }
               </td>
              <td>
                @if (e.estatus_entrega === 'PENDIENTE') {
                  <p-button label="Subir archivo" icon="pi pi-upload" size="small"
                            (onClick)="abrirUpload(e)" />
                } @else {
                  <span style="font-size:.8rem;color:#15803d">
                    <i class="pi pi-check-circle"></i> Entregado
                    @if (e.archivo_url) {
                      · <a (click)="$event.preventDefault()" href="#"
                          pTooltip="Descarga disponible próximamente" style="font-size:.8rem">Ver archivo</a>
                    }
                  </span>
                }
              </td>
            </tr>
          </ng-template>
          <ng-template pTemplate="emptymessage">
            <tr><td [colSpan]="5" style="text-align:center;padding:2rem;color:var(--text-muted)">
              Sin entregas registradas
            </td></tr>
          </ng-template>
        </p-table>
      }

      <!-- ── Vista DOCENTE: entregas del grupo por calificar ── -->
      @if (esDocente()) {
        <h3 style="margin-top:2rem">Entregas por calificar</h3>
        <p-table [value]="entregasPendientes()" styleClass="p-datatable-sm">
          <ng-template pTemplate="header">
            <tr>
              <th>Alumno</th>
              <th>Tarea</th>
              <th style="width:120px">Entregado</th>
              <th style="width:90px">Estado</th>
              <th style="width:110px">Acción</th>
            </tr>
          </ng-template>
          <ng-template pTemplate="body" let-e>
            <tr>
              <td>{{ e.alumno_nombre }}</td>
              <td>{{ e.titulo }}</td>
              <td>{{ e.fecha_entrega | date:'dd/MM/yyyy HH:mm' }}</td>
              <td>
                <p-tag [value]="estatusLabel(e.estatus_entrega)"
                       [severity]="estatusSeverity(e.estatus_entrega)" />
              </td>
              <td>
                <p-button label="Calificar" icon="pi pi-star" size="small"
                          (onClick)="abrirCalificar(e)" />
              </td>
            </tr>
          </ng-template>
          <ng-template pTemplate="emptymessage">
            <tr><td [colSpan]="5" style="text-align:center;padding:2rem;color:var(--text-muted)">
              Sin entregas pendientes de calificación
            </td></tr>
          </ng-template>
        </p-table>
      }
    }

    <!-- Dialog: Nueva/Editar Tarea -->
    <p-dialog [(visible)]="showDialog" [header]="tareaEditId ? 'Editar Tarea' : 'Nueva Tarea'"
              [modal]="true" [style]="{width:'450px'}">
      <div style="display:flex;flex-direction:column;gap:1rem">
        <div>
          <label class="dlg-lbl">Título *</label>
          <input pInputText [(ngModel)]="form.titulo" style="width:100%"
                 placeholder="Ej. Resolver ejercicios 1-10 del libro"
                 pTooltip="Nombre breve y descriptivo de la tarea" tooltipPosition="top" />
        </div>
        <div>
          <label class="dlg-lbl">Descripción</label>
          <input pInputText [(ngModel)]="form.descripcion" style="width:100%"
                 placeholder="Instrucciones para el alumno (opcional)" />
        </div>
        <div>
          <label class="dlg-lbl">Fecha de Entrega *</label>
          <p-datepicker [(ngModel)]="form.fecha_entrega" dateFormat="dd/mm/yy"
                        [showIcon]="true" [minDate]="hoy" placeholder="DD/MM/AAAA"
                        [style]="{width:'100%'}" [inputStyle]="{width:'100%'}"
                        pTooltip="La fecha de entrega no puede ser anterior a hoy" tooltipPosition="top" />
        </div>
        <div>
          <label class="dlg-lbl">Puntaje Máximo *</label>
          <p-inputNumber [(ngModel)]="form.puntaje_maximo" [min]="0.01" [max]="10" [minFractionDigits]="1"
                         placeholder="10.0" styleClass="w-full"
                         pTooltip="Escala oficial SEP: de 0 a 10" tooltipPosition="top" />
        </div>
        <p-button label="Guardar" (onClick)="guardarTarea()" [loading]="saving()" />
      </div>
    </p-dialog>

    <!-- Dialog: Subir Entrega (alumno) -->
    <p-dialog [(visible)]="uploadVisible" header="Subir entrega"
              [modal]="true" [style]="{width:'440px'}">
      @if (uploadEntrega()) {
        <div style="margin-bottom:12px;font-size:.9rem;color:var(--text-muted)">
          <strong>{{ uploadEntrega()?.titulo }}</strong>
          &nbsp;·&nbsp;Vence {{ uploadEntrega()?.fecha_limite | date:'dd/MM/yyyy' }}
        </div>
        <div style="display:flex;flex-direction:column;gap:12px">
          <div>
            <label class="dlg-lbl">Archivo (opcional)</label>
            <input type="file" (change)="onFileSelected($event)"
                   style="display:block;margin-top:4px;font-size:.9rem" />
          </div>
          <div>
            <label class="dlg-lbl">Comentario (opcional)</label>
            <textarea pTextarea [(ngModel)]="uploadComment" rows="3"
                      placeholder="Notas para el docente..."
                      style="width:100%;resize:vertical"></textarea>
          </div>
          <p-button label="Enviar entrega" icon="pi pi-send"
                    [loading]="uploading()" (onClick)="enviarEntrega()" />
        </div>
      }
    </p-dialog>

    <!-- Dialog: Calificar Entrega (docente) -->
    <p-dialog [(visible)]="calificarVisible" header="Calificar entrega"
              [modal]="true" [style]="{width:'420px'}">
      @if (calificarEntrega()) {
        <div style="margin-bottom:12px;font-size:.9rem;color:var(--text-muted)">
          <strong>{{ calificarEntrega()?.alumno_nombre }}</strong>
          &nbsp;·&nbsp;{{ calificarEntrega()?.titulo }}
          @if (calificarEntrega()?.comentario_alumno) {
            <div style="margin-top:6px;padding:6px 8px;background:var(--p-surface-50);border-radius:4px">
              <em>"{{ calificarEntrega()?.comentario_alumno }}"</em>
            </div>
          }
        </div>
        <div style="display:flex;flex-direction:column;gap:12px">
          <div>
            <label class="dlg-lbl">Calificación * (0 – {{ tareaMaxPuntaje() }})</label>
            <p-inputNumber [(ngModel)]="calificarForm.calificacion"
                           [min]="0" [max]="tareaMaxPuntaje()" [minFractionDigits]="1"
                           placeholder="0.0" styleClass="w-full"
                           [pTooltip]="'Escala 0 a ' + tareaMaxPuntaje() + ' (SEP)'" tooltipPosition="top" />
          </div>
          <div>
            <label class="dlg-lbl">Comentario (opcional)</label>
            <textarea pTextarea [(ngModel)]="calificarForm.comentario" rows="3"
                      placeholder="Retroalimentación para el alumno..."
                      maxlength="500"
                      style="width:100%;resize:vertical"></textarea>
          </div>

          @if (calificarEntrega()?.archivo_url) {
            <div style="padding:10px; background:var(--surface-hover); border-radius:6px; border:1px solid #e5e7eb; display:flex; flex-direction:column; gap:8px;">
              <div style="font-weight:600; font-size:.85rem; display:flex; align-items:center; gap:6px;">
                <i class="pi pi-shield" style="color:#0284c7"></i> Detección de Plagio (Turnitin)
              </div>
              @if (calificarEntrega()?.plagio_porcentaje !== undefined && calificarEntrega()?.plagio_porcentaje !== null) {
                <div style="display:flex; justify-content:space-between; align-items:center;">
                  <span style="font-size:.85rem">Similitud encontrada:</span>
                  <p-tag [value]="calificarEntrega().plagio_porcentaje + '%'" [severity]="plagioSeverity(calificarEntrega().plagio_porcentaje)"></p-tag>
                </div>
                @if (calificarEntrega()?.plagio_reporte_url) {
                  <a [href]="calificarEntrega().plagio_reporte_url" target="_blank" style="font-size:.8rem; color:#0284c7; text-decoration:underline;">
                    Ver reporte de originalidad
                  </a>
                }
              } @else {
                <p-button label="Escanear con Turnitin" icon="pi pi-refresh" size="small" [loading]="escaneandoPlagio()" (onClick)="escanearPlagio()"></p-button>
              }
            </div>
          }

          <div style="padding:10px; background:#f0fdf4; border-radius:6px; border:1px solid #bbf7d0; display:flex; flex-direction:column; gap:8px;">
            <div style="font-weight:600; font-size:.85rem; display:flex; align-items:center; gap:6px; color:#166534;">
              <i class="pi pi-volume-up"></i> Retroalimentación Multimedia
            </div>
            
            <div style="display:flex; flex-direction:column; gap:6px;">
              <div>
                <label class="dlg-lbl" style="font-size:.75rem">Audio de retroalimentación (.mp3, .wav)</label>
                <input type="file" accept="audio/*" (change)="onAudioSelected($event)" style="font-size:.8rem" />
              </div>
              <div>
                <label class="dlg-lbl" style="font-size:.75rem">Video de retroalimentación (.mp4, .webm)</label>
                <input type="file" accept="video/*" (change)="onVideoSelected($event)" style="font-size:.8rem" />
              </div>
            </div>

            @if (audioFileForFeedback || videoFileForFeedback) {
              <p-button label="Subir Multimedia" icon="pi pi-upload" severity="success" size="small"
                        [loading]="subiendoMultimedia()" (onClick)="guardarMultimedia()"></p-button>
            }

            @if (calificarEntrega()?.feedback_audio_url || calificarEntrega()?.feedback_video_url) {
              <div style="margin-top:6px; border-top:1px solid var(--nevadi-sage-light); padding-top:6px; display:flex; flex-direction:column; gap:4px; font-size:.8rem;">
                <span style="font-weight:600; color:#166534">Feedback guardado:</span>
                @if (calificarEntrega()?.feedback_audio_url) {
                  <div style="display:flex; align-items:center; gap:4px;">
                    <i class="pi pi-play" style="color:#166534"></i> Audio cargado
                  </div>
                }
                @if (calificarEntrega()?.feedback_video_url) {
                  <div style="display:flex; align-items:center; gap:4px;">
                    <i class="pi pi-video" style="color:#166534"></i> Video cargado
                  </div>
                }
              </div>
            }
          </div>
          <div style="display:flex;gap:8px">
            <p-button label="Calificar" icon="pi pi-check" severity="success"
                      [loading]="calificando()" (onClick)="guardarCalificacion()" />
            <p-button label="Marcar excusa" icon="pi pi-ban" severity="secondary"
                      (onClick)="marcarExcusa()" />
          </div>
        </div>
      }
    </p-dialog>
  `,
  styles: [`
    .page-header { display:flex; justify-content:space-between; align-items:center; margin-bottom:1.5rem; }
    .subtitle { color:var(--text-secondary); font-size:.85rem; margin:.25rem 0 0; }
    .filter-bar { display:flex; gap:1rem; flex-wrap:wrap; margin-bottom:1.5rem; }
    .filter-select { min-width:180px; }
    .kpi-row { display:flex; gap:12px; flex-wrap:wrap; margin-bottom:1.5rem; }
    .kpi-card {
      background:var(--p-surface-0); border:1px solid var(--p-surface-border);
      border-radius:8px; padding:12px 16px; min-width:110px; text-align:center;
    }
    .kpi-value { font-size:1.8rem; font-weight:700; font-family:'Jost',sans-serif; }
    .kpi-label { font-size:.75rem; color:var(--p-text-muted-color); margin-top:2px; }
    .kpi-warn .kpi-value { color:#b45309; }
    .kpi-blue .kpi-value { color:#0369a1; }
    h3 { color:var(--text-primary); margin-bottom:1rem; }
    .empty-state { display:flex; flex-direction:column; align-items:center; gap:.5rem; padding:4rem; color:var(--text-muted); }
    .dlg-lbl { display:block; font-size:.85rem; margin-bottom:.25rem; color:var(--text-secondary); font-weight:600; }
  `],
})
export class TareasComponent implements OnInit, OnDestroy {
  private destroy$ = new Subject<void>();
  private readonly api    = inject(ApiService);
  private readonly ctx    = inject(ContextService);
  private readonly notify = inject(ApexNotificationService);

  // ── Columns ────────────────────────────────────────────────────────────────
  readonly tareasColumns: ColumnConfig[] = [
    { field: 'titulo',         header: 'Tarea' },
    { field: 'fecha_entrega',  header: 'Entrega',      width: '120px' },
    { field: 'puntaje_maximo', header: 'Puntaje Máx.', width: '110px', type: 'number' },
    { field: 'origen',         header: 'Origen',       width: '90px' },
  ];

  // ── Filtros de dominio ─────────────────────────────────────────────────────
  materiasOpts  = signal<Materia[]>([]);
  /** ID del grupo activo; viene del contexto global (topbar). */
  selectedGrupoId:   string | null = null;
  selectedMateriaId: string | null = null;

  // ── Data ───────────────────────────────────────────────────────────────────
  tareas              = signal<Tarea[]>([]);
  entregas            = signal<any[]>([]);           // alumno: mis entregas
  entregasPendientes  = signal<any[]>([]);           // docente: por calificar
  readonly pendientesAlumno = computed(() =>
    this.entregas().filter(e => e.estatus_entrega === 'PENDIENTE').length);

  readonly esDocente  = computed(() => this.ctx.nivelAcceso() <= 3);
  readonly puedeCrear = computed(() => this.ctx.nivelAcceso() <= 4);

  // ── Tarea dialog ───────────────────────────────────────────────────────────
  showDialog   = false;
  saving       = signal(false);
  tareaEditId: string | null = null;
  readonly hoy = new Date();
  form: { titulo: string; descripcion: string; fecha_entrega: Date | null; puntaje_maximo: number } =
    { titulo: '', descripcion: '', fecha_entrega: null, puntaje_maximo: 10 };

  // ── Upload dialog (alumno) ─────────────────────────────────────────────────
  uploadVisible = false;
  uploading     = signal(false);
  uploadEntrega = signal<any>(null);
  uploadComment = '';
  uploadFile_: File | null = null;

  // ── Calificar dialog (docente) ─────────────────────────────────────────────
  calificarVisible = false;
  calificando      = signal(false);
  calificarEntrega = signal<any>(null);
  calificarForm    = { calificacion: 0, comentario: '' };
  readonly tareaMaxPuntaje = computed(() => {
    const e = this.calificarEntrega();
    if (!e) return 10;
    const t = this.tareas().find(t => t.id === e.actividad_id);
    return t?.puntaje_maximo ?? 10;
  });

  constructor() {
    // Recargar materias cuando cambia el grupo en el contexto global.
    effect(() => {
      const grupo = this.ctx.grupo();
      this.selectedGrupoId  = grupo?.id ?? null;
      this.selectedMateriaId = null;
      this.materiasOpts.set([]);
      this._clearData();
      if (!grupo?.id) return;
      this.api.get<any[]>('/planes-estudio', { grado_id: grupo.grado_id }).pipe(takeUntil(this.destroy$)).subscribe(planes => {
        const materias: Materia[] = (planes as any[]).map((p: any) => p.materia).filter(Boolean);
        this.materiasOpts.set(materias);
      });
    });
  }

  ngOnInit(): void {
    // ngOnInit no necesita cargar planteles — la cascada vive en el topbar.
  }

  private _clearData() {
    this.tareas.set([]); this.entregas.set([]); this.entregasPendientes.set([]);
  }

  // ── Data loading ────────────────────────────────────────────────────────────
  loadTareas(): void {
    if (!this.selectedGrupoId || !this.selectedMateriaId) return;
    this.api.get<Tarea[]>('/tareas', {
      grupo_id: this.selectedGrupoId,
      materia_id: this.selectedMateriaId,
    }).pipe(takeUntil(this.destroy$)).subscribe(t => {
      this.tareas.set(t);
      if (this.esDocente()) {
        this._loadEntregasPendientes();
      } else {
        this._loadMisEntregas();
      }
    });
  }

  private _loadMisEntregas() {
    const alumnoId = this.ctx.usuario()?.id;
    if (!alumnoId) return;
    const params: Record<string, any> = {};
    if (this.selectedMateriaId) params['materia_id'] = this.selectedMateriaId;
    this.api.get<any[]>(`/entregas/alumno/${alumnoId}`, params).pipe(takeUntil(this.destroy$)).subscribe({
      next: e => this.entregas.set(e),
      error: () => this.entregas.set([]),
    });
  }

  private _loadEntregasPendientes() {
    if (!this.selectedGrupoId) return;
    const params: Record<string, any> = {};
    if (this.selectedMateriaId) params['materia_id'] = this.selectedMateriaId;
    this.api.get<any[]>(`/entregas/pendientes/grupo/${this.selectedGrupoId}`, params).pipe(takeUntil(this.destroy$)).subscribe({
      next: e => this.entregasPendientes.set(e),
      error: () => this.entregasPendientes.set([]),
    });
  }

  // ── Tarea CRUD ──────────────────────────────────────────────────────────────
  private _toIso(d: Date): string {
    return d.toISOString().substring(0, 10);
  }

  abrirCreacion(): void {
    this.tareaEditId = null;
    this.form = { titulo: '', descripcion: '', fecha_entrega: null, puntaje_maximo: 10 };
    this.showDialog = true;
  }

  abrirEdicion(tarea: Tarea): void {
    this.tareaEditId = tarea.id;
    this.form = {
      titulo: tarea.titulo, descripcion: tarea.descripcion || '',
      fecha_entrega: tarea.fecha_entrega ? new Date(tarea.fecha_entrega) : null,
      puntaje_maximo: tarea.puntaje_maximo,
    };
    this.showDialog = true;
  }

  guardarTarea(): void {
    if (!this.form.titulo || !this.form.fecha_entrega || !this.selectedGrupoId || !this.selectedMateriaId) {
      this.notify.warning('Campos vacíos', 'Rellena los campos obligatorios');
      return;
    }
    const hoyIso = this._toIso(new Date());
    const fechaEntregaIso = this._toIso(this.form.fecha_entrega);
    if (fechaEntregaIso < hoyIso) {
      this.notify.warning('Fecha inválida', 'La fecha de entrega no puede ser anterior a hoy');
      return;
    }
    this.saving.set(true);
    // POST /tareas (crear) espera camelCase estricto (CrearActividadRequest, record Java sin @JsonProperty);
    // PATCH /tareas/{id} (editar) espera snake_case (TareaQueryService.actualizarTarea, Map<String,Object> crudo).
    const req$ = this.tareaEditId
      ? this.api.patch<Tarea>(`/tareas/${this.tareaEditId}`, {
          titulo: this.form.titulo, descripcion: this.form.descripcion,
          fecha_entrega: fechaEntregaIso, puntaje_maximo: this.form.puntaje_maximo,
          permite_entrega_tarde: true,
        })
      : this.api.post<Tarea>('/tareas', {
          titulo: this.form.titulo, descripcion: this.form.descripcion,
          grupoId: this.selectedGrupoId, materiaId: this.selectedMateriaId,
          fechaAsignacion: hoyIso, fechaEntrega: fechaEntregaIso,
          puntajeMaximo: this.form.puntaje_maximo,
          permiteEntregaTarde: true, tipoItem: 'tarea',
        });
    req$.pipe(takeUntil(this.destroy$)).subscribe({
      next: () => {
        this.saving.set(false); this.showDialog = false;
        this.notify.success(this.tareaEditId ? 'Actualizado' : 'Creado', 'Tarea guardada');
        this.loadTareas();
      },
      error: (err: any) => {
        this.saving.set(false);
        this.notify.error('Error', err?.error?.message ?? 'No se pudo guardar la tarea');
      },
    });
  }

  // ── Upload (alumno) ─────────────────────────────────────────────────────────
  abrirUpload(entrega: any): void {
    this.uploadEntrega.set(entrega);
    this.uploadComment = '';
    this.uploadFile_ = null;
    this.uploadVisible = true;
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.uploadFile_ = input.files?.[0] ?? null;
  }

  enviarEntrega(): void {
    const e = this.uploadEntrega();
    if (!e) return;
    this.uploading.set(true);
    const fd = new FormData();
    fd.append('tarea_id', e.tarea_id);
    const alumnoId = this.ctx.usuario()?.id;
    if (alumnoId) fd.append('alumno_id', alumnoId);
    if (this.uploadComment) fd.append('comentario', this.uploadComment);
    if (this.uploadFile_) fd.append('archivo', this.uploadFile_, this.uploadFile_.name);
    this.api.postForm<any>('/entregas', fd).pipe(takeUntil(this.destroy$)).subscribe({
      next: () => {
        this.uploading.set(false); this.uploadVisible = false;
        this.notify.success('Entrega enviada', e.titulo);
        this._loadMisEntregas();
      },
      error: (err: any) => {
        this.uploading.set(false);
        this.notify.error('Error', err?.error?.detail ?? 'No se pudo subir la entrega');
      },
    });
  }

  // ── Calificar (docente) ─────────────────────────────────────────────────────
  escaneandoPlagio = signal(false);
  subiendoMultimedia = signal(false);
  audioFileForFeedback: File | null = null;
  videoFileForFeedback: File | null = null;

  abrirCalificar(entrega: any): void {
    this.calificarEntrega.set(entrega);
    this.calificarForm = { calificacion: entrega.calificacion_obtenida ?? 0, comentario: entrega.comentario_profesor ?? '' };
    this.audioFileForFeedback = null;
    this.videoFileForFeedback = null;
    this.escaneandoPlagio.set(false);
    this.subiendoMultimedia.set(false);
    this.calificarVisible = true;
  }

  guardarCalificacion(): void {
    const e = this.calificarEntrega();
    if (!e) return;
    this.calificando.set(true);
    this.api.patch<any>(`/entregas/${e.id}/calificar`, {
      calificacion: this.calificarForm.calificacion,
      comentario:   this.calificarForm.comentario,
    }).pipe(takeUntil(this.destroy$)).subscribe({
      next: () => {
        this.calificando.set(false); this.calificarVisible = false;
        this.notify.success('Calificación guardada', e.alumno_nombre);
        this._loadEntregasPendientes();
      },
      error: (err: any) => {
        this.calificando.set(false);
        this.notify.error('Error', err?.error?.detail ?? 'No se pudo calificar');
      },
    });
  }

  marcarExcusa(): void {
    const e = this.calificarEntrega();
    if (!e) return;
    const motivo = this.calificarForm.comentario || 'Sin motivo especificado';
    this.api.post<any>(`/entregas/${e.id}/excusa?motivo=${encodeURIComponent(motivo)}`, {}).pipe(takeUntil(this.destroy$)).subscribe({
      next: () => {
        this.calificarVisible = false;
        this.notify.success('Excusa registrada', e.alumno_nombre);
        this._loadEntregasPendientes();
      },
      error: (err: any) => this.notify.error('Error', err?.error?.detail ?? 'No se pudo registrar la excusa'),
    });
  }

  // ── Helpers ─────────────────────────────────────────────────────────────────
  plagioSeverity(score: number): 'success' | 'warn' | 'danger' {
    if (score < 15) return 'success';
    if (score < 30) return 'warn';
    return 'danger';
  }

  escanearPlagio(): void {
    const e = this.calificarEntrega();
    if (!e) return;
    this.escaneandoPlagio.set(true);
    this.api.post<any>(`/entregas/${e.id}/plagio-check`, {}).pipe(takeUntil(this.destroy$)).subscribe({
      next: (res) => {
        this.escaneandoPlagio.set(false);
        this.notify.success('Plagio escaneado', `${res.plagio_porcentaje}% similitud`);
        e.plagio_porcentaje = res.plagio_porcentaje;
        e.plagio_reporte_url = res.plagio_reporte_url;
      },
      error: (err) => {
        this.escaneandoPlagio.set(false);
        this.notify.error('Error', err?.error?.detail ?? 'No se pudo escanear');
      }
    });
  }

  onAudioSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.audioFileForFeedback = input.files?.[0] ?? null;
  }

  onVideoSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.videoFileForFeedback = input.files?.[0] ?? null;
  }

  guardarMultimedia(): void {
    const e = this.calificarEntrega();
    if (!e) return;
    this.subiendoMultimedia.set(true);
    const fd = new FormData();
    if (this.audioFileForFeedback) fd.append('audio', this.audioFileForFeedback, this.audioFileForFeedback.name);
    if (this.videoFileForFeedback) fd.append('video', this.videoFileForFeedback, this.videoFileForFeedback.name);
    
    this.api.postForm<any>(`/entregas/${e.id}/feedback-multimedia`, fd).pipe(takeUntil(this.destroy$)).subscribe({
      next: (res: any) => {
        this.subiendoMultimedia.set(false);
        this.notify.success('Multimedia guardada', e.alumno_nombre);
        e.feedback_audio_url = res.feedback_audio_url;
        e.feedback_video_url = res.feedback_video_url;
        this.audioFileForFeedback = null;
        this.videoFileForFeedback = null;
      },
      error: (err) => {
        this.subiendoMultimedia.set(false);
        this.notify.error('Error', err?.error?.detail ?? 'No se pudo guardar la multimedia');
      }
    });
  }

  mediaUrl(minioUrl: string): string {
    if (!minioUrl) return '';
    return `/api/v1/entregas/media?url=${encodeURIComponent(minioUrl)}`;
  }

  estatusLabel(s: string): string {
    const map: Record<string, string> = {
      PENDIENTE: 'Pendiente', ENTREGADA: 'Entregado',
      CALIFICADA: 'Calificada', EXCUSA: 'Excusa',
    };
    return map[s] ?? s;
  }

  estatusSeverity(s: string): 'success' | 'info' | 'warn' | 'danger' | 'secondary' {
    const map: Record<string, any> = {
      PENDIENTE: 'warn', ENTREGADA: 'info',
      CALIFICADA: 'success', EXCUSA: 'secondary',
    };
    return map[s] ?? 'secondary';
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
