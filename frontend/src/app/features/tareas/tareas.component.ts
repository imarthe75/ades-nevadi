import { Component, OnInit, inject, signal, computed, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
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

import { ApiService } from '../../core/services/api.service';
import { ContextService } from '../../core/services/context.service';
import type { Tarea, Grupo, Materia } from '../../core/models';
import { grupoLabel } from '../../core/models';
import { ApexNotificationService } from 'apex-component-library';
import { InteractiveGridComponent, ColumnConfig } from '../../shared/components/interactive-grid/interactive-grid.component';

type GrupoConLabel = Grupo & { _label: string };

@Component({
  selector: 'app-tareas',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    TableModule, CardModule, ButtonModule, TagModule,
    SelectModule, DialogModule, InputTextModule, InputNumberModule,
    TextareaModule, TooltipModule,
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

    <!-- Filtros cascading Plantel → Nivel → Grado → Grupo → Materia -->
    <div class="filter-bar">
      <p-select [options]="plantelesOpts()" [(ngModel)]="selectedPlantelId"
        optionLabel="nombre_plantel" optionValue="id" placeholder="Plantel"
        (onChange)="onPlantelChange()"
        [showClear]="!isPlantelDisabled()" [disabled]="isPlantelDisabled()"
        [filter]="true" filterPlaceholder="Buscar..." styleClass="filter-select" />

      <p-select [options]="nivelesOpts()" [(ngModel)]="selectedNivelId"
        optionLabel="nombre_nivel" optionValue="id" placeholder="Nivel"
        (onChange)="onNivelChange()"
        [showClear]="!isNivelDisabled()" [disabled]="isNivelDisabled() || !selectedPlantelId"
        [filter]="true" filterPlaceholder="Buscar..." styleClass="filter-select" />

      <p-select [options]="gradosOpts()" [(ngModel)]="selectedGradoId"
        optionLabel="nombre_grado" optionValue="id" placeholder="Grado"
        (onChange)="onGradoChange()"
        [showClear]="true" [disabled]="!selectedNivelId"
        [filter]="true" filterPlaceholder="Buscar..." styleClass="filter-select" />

      <p-select [options]="gruposOpts()" [(ngModel)]="selectedGrupoId"
        optionLabel="_label" optionValue="id" placeholder="Grupo"
        (onChange)="onGrupoChange()"
        [showClear]="true" [disabled]="!selectedGradoId"
        [filter]="true" filterPlaceholder="Buscar..." styleClass="filter-select" />

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
          <input pInputText [(ngModel)]="form.titulo" style="width:100%" />
        </div>
        <div>
          <label class="dlg-lbl">Descripción</label>
          <input pInputText [(ngModel)]="form.descripcion" style="width:100%" />
        </div>
        <div>
          <label class="dlg-lbl">Fecha de Entrega * (AAAA-MM-DD)</label>
          <input pInputText [(ngModel)]="form.fecha_entrega" style="width:100%" placeholder="2026-06-30" />
        </div>
        <div>
          <label class="dlg-lbl">Puntaje Máximo *</label>
          <p-inputNumber [(ngModel)]="form.puntaje_maximo" [min]="1" [max]="100" styleClass="w-full" />
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
                           styleClass="w-full" />
          </div>
          <div>
            <label class="dlg-lbl">Comentario (opcional)</label>
            <textarea pTextarea [(ngModel)]="calificarForm.comentario" rows="3"
                      placeholder="Retroalimentación para el alumno..."
                      style="width:100%;resize:vertical"></textarea>
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
export class TareasComponent implements OnInit {
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

  // ── Cascade LOVs ───────────────────────────────────────────────────────────
  plantelesOpts = signal<any[]>([]);
  nivelesOpts   = signal<any[]>([]);
  gradosOpts    = signal<any[]>([]);
  gruposOpts    = signal<GrupoConLabel[]>([]);
  materiasOpts  = signal<Materia[]>([]);

  selectedPlantelId: string | null = null;
  selectedNivelId:   string | null = null;
  selectedGradoId:   string | null = null;
  selectedGrupoId:   string | null = null;
  selectedMateriaId: string | null = null;

  readonly isPlantelDisabled = computed(() => this.ctx.nivelAcceso() > 2);
  readonly isNivelDisabled   = computed(() => this.ctx.nivelAcceso() > 3);

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
  form = { titulo: '', descripcion: '', fecha_entrega: '', puntaje_maximo: 10 };

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
    if (!e) return 100;
    const t = this.tareas().find(t => t.id === e.actividad_id);
    return t?.puntaje_maximo ?? 100;
  });

  constructor() {
    effect(() => {
      const p = this.ctx.plantel();
      if (p?.id && !this.selectedPlantelId) {
        this.selectedPlantelId = p.id;
        this.onPlantelChange();
      }
    });
  }

  ngOnInit(): void {
    this.api.get<any[]>('/planteles').subscribe({
      next: p => {
        this.plantelesOpts.set(p);
        const cp = this.ctx.plantel();
        if (cp?.id) {
          this.selectedPlantelId = cp.id;
          this.onPlantelChange();
        }
      },
      error: () => {},
    });
  }

  // ── Cascade handlers ────────────────────────────────────────────────────────
  onPlantelChange(): void {
    this.selectedNivelId = null; this.selectedGradoId = null;
    this.selectedGrupoId = null; this.selectedMateriaId = null;
    this.nivelesOpts.set([]); this.gradosOpts.set([]);
    this.gruposOpts.set([]); this.materiasOpts.set([]);
    this._clearData();
    if (!this.selectedPlantelId) return;
    this.api.get<any[]>(`/planteles/${this.selectedPlantelId}/niveles`).subscribe({
      next: ns => {
        const mapped = ns.map(x => ({ id: x.id ?? x.nivel_id, nombre_nivel: x.nombre_nivel }));
        this.nivelesOpts.set(mapped);
        const cn = this.ctx.nivel();
        if (cn && mapped.some(n => n.id === cn.id)) {
          this.selectedNivelId = cn.id;
          this.onNivelChange();
        }
      },
    });
  }

  onNivelChange(): void {
    this.selectedGradoId = null; this.selectedGrupoId = null; this.selectedMateriaId = null;
    this.gradosOpts.set([]); this.gruposOpts.set([]); this.materiasOpts.set([]);
    this._clearData();
    if (!this.selectedNivelId) return;
    this.api.get<any[]>('/catalogs/grados', { nivel_id: this.selectedNivelId })
      .subscribe({ next: gs => this.gradosOpts.set(gs) });
  }

  onGradoChange(): void {
    this.selectedGrupoId = null; this.selectedMateriaId = null;
    this.gruposOpts.set([]); this.materiasOpts.set([]);
    this._clearData();
    if (!this.selectedGradoId) return;
    const params: Record<string, any> = { solo_activos: true, ciclo_vigente: true };
    if (this.selectedPlantelId) params['plantel_id'] = this.selectedPlantelId;
    params['grado_id'] = this.selectedGradoId;
    this.api.get<Grupo[]>('/grupos', params).subscribe({
      next: gs => this.gruposOpts.set(gs.map(x => ({ ...x, _label: grupoLabel(x) }))),
    });
  }

  onGrupoChange(): void {
    this.selectedMateriaId = null;
    this.materiasOpts.set([]);
    this._clearData();
    if (!this.selectedGrupoId) return;
    const grupo = this.gruposOpts().find(g => g.id === this.selectedGrupoId);
    if (!grupo) return;
    this.api.get<any[]>('/planes-estudio', { grado_id: grupo.grado_id }).subscribe(planes => {
      const materias: Materia[] = (planes as any[]).map((p: any) => p.materia).filter(Boolean);
      this.materiasOpts.set(materias);
    });
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
    }).subscribe(t => {
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
    this.api.get<any[]>(`/entregas/alumno/${alumnoId}`, params).subscribe({
      next: e => this.entregas.set(e),
      error: () => this.entregas.set([]),
    });
  }

  private _loadEntregasPendientes() {
    if (!this.selectedGrupoId) return;
    const params: Record<string, any> = {};
    if (this.selectedMateriaId) params['materia_id'] = this.selectedMateriaId;
    this.api.get<any[]>(`/entregas/pendientes/grupo/${this.selectedGrupoId}`, params).subscribe({
      next: e => this.entregasPendientes.set(e),
      error: () => this.entregasPendientes.set([]),
    });
  }

  // ── Tarea CRUD ──────────────────────────────────────────────────────────────
  abrirCreacion(): void {
    this.tareaEditId = null;
    this.form = { titulo: '', descripcion: '', fecha_entrega: new Date().toISOString().substring(0, 10), puntaje_maximo: 10 };
    this.showDialog = true;
  }

  abrirEdicion(tarea: Tarea): void {
    this.tareaEditId = tarea.id;
    this.form = { titulo: tarea.titulo, descripcion: tarea.descripcion || '', fecha_entrega: tarea.fecha_entrega, puntaje_maximo: tarea.puntaje_maximo };
    this.showDialog = true;
  }

  guardarTarea(): void {
    if (!this.form.titulo || !this.form.fecha_entrega || !this.selectedGrupoId || !this.selectedMateriaId) {
      this.notify.warning('Campos vacíos', 'Rellena los campos obligatorios');
      return;
    }
    this.saving.set(true);
    const payload = {
      titulo: this.form.titulo, descripcion: this.form.descripcion,
      fecha_entrega: this.form.fecha_entrega, puntaje_maximo: this.form.puntaje_maximo,
      grupo_id: this.selectedGrupoId, materia_id: this.selectedMateriaId,
      fecha_asignacion: new Date().toISOString().substring(0, 10),
      permite_entrega_tarde: true, origen: 'MANUAL',
    };
    const req$ = this.tareaEditId
      ? this.api.patch<Tarea>(`/tareas/${this.tareaEditId}`, payload)
      : this.api.post<Tarea>('/tareas', payload);
    req$.subscribe({
      next: () => {
        this.saving.set(false); this.showDialog = false;
        this.notify.success(this.tareaEditId ? 'Actualizado' : 'Creado', 'Tarea guardada');
        this.loadTareas();
      },
      error: () => this.saving.set(false),
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
    this.api.postForm<any>('/entregas', fd).subscribe({
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
  abrirCalificar(entrega: any): void {
    this.calificarEntrega.set(entrega);
    this.calificarForm = { calificacion: 0, comentario: '' };
    this.calificarVisible = true;
  }

  guardarCalificacion(): void {
    const e = this.calificarEntrega();
    if (!e) return;
    this.calificando.set(true);
    this.api.patch<any>(`/entregas/${e.id}/calificar`, {
      calificacion: this.calificarForm.calificacion,
      comentario:   this.calificarForm.comentario,
    }).subscribe({
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
    this.api.post<any>(`/entregas/${e.id}/excusa?motivo=${encodeURIComponent(motivo)}`, {}).subscribe({
      next: () => {
        this.calificarVisible = false;
        this.notify.success('Excusa registrada', e.alumno_nombre);
        this._loadEntregasPendientes();
      },
    });
  }

  // ── Helpers ─────────────────────────────────────────────────────────────────
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
}
