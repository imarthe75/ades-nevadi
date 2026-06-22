import { Component, OnInit, inject, signal, computed, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CardModule } from 'primeng/card';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { FileUploadModule } from 'primeng/fileupload';
import { SelectModule } from 'primeng/select';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { TableModule } from 'primeng/table';

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
    TableModule, CardModule, ButtonModule, TagModule, FileUploadModule,
    SelectModule, DialogModule, InputTextModule, InputNumberModule,
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

    <!-- Filtros de Contexto Cascading (Plantel -> Nivel -> Grado -> Grupo -> Materia) -->
    <div class="filter-bar">
      <p-select
        [options]="plantelesOpts()"
        [(ngModel)]="selectedPlantelId"
        optionLabel="nombre_plantel"
        optionValue="id"
        placeholder="Plantel"
        (onChange)="onPlantelChange()"
        [showClear]="!isPlantelDisabled()"
        [disabled]="isPlantelDisabled()"
        [filter]="true" filterPlaceholder="Buscar..."
        styleClass="filter-select" />

      <p-select
        [options]="nivelesOpts()"
        [(ngModel)]="selectedNivelId"
        optionLabel="nombre_nivel"
        optionValue="id"
        placeholder="Nivel"
        (onChange)="onNivelChange()"
        [showClear]="!isNivelDisabled()"
        [disabled]="isNivelDisabled() || !selectedPlantelId"
        [filter]="true" filterPlaceholder="Buscar..."
        styleClass="filter-select" />

      <p-select
        [options]="gradosOpts()"
        [(ngModel)]="selectedGradoId"
        optionLabel="nombre_grado"
        optionValue="id"
        placeholder="Grado"
        (onChange)="onGradoChange()"
        [showClear]="true"
        [disabled]="!selectedNivelId"
        [filter]="true" filterPlaceholder="Buscar..."
        styleClass="filter-select" />

      <p-select
        [options]="gruposOpts()"
        [(ngModel)]="selectedGrupoId"
        optionLabel="_label"
        optionValue="id"
        placeholder="Grupo"
        (onChange)="onGrupoChange()"
        [showClear]="true"
        [disabled]="!selectedGradoId"
        [filter]="true" filterPlaceholder="Buscar..."
        styleClass="filter-select" />

      <p-select
        [options]="materiasOpts()"
        [(ngModel)]="selectedMateriaId"
        optionLabel="nombre_materia"
        optionValue="id"
        placeholder="Materia"
        (onChange)="loadTareas()"
        [showClear]="true"
        [disabled]="!selectedGrupoId"
        [filter]="true" filterPlaceholder="Buscar..."
        styleClass="filter-select" />
    </div>

    @if (!selectedGrupoId || !selectedMateriaId) {
      <div class="empty-state">
        <i class="pi pi-info-circle" style="font-size:2rem; color:var(--text-muted)"></i>
        <p>Selecciona plantel, nivel, grado, grupo y materia para ver y gestionar las tareas.</p>
      </div>
    } @else {
      <div style="display:grid;grid-template-columns:1fr 1fr;gap:1rem;margin-bottom:2rem">
        <p-card styleClass="stat-card">
          <ng-template pTemplate="header">
            <div style="color:var(--text-secondary);font-size:0.85rem;font-weight:600">Tareas Activas</div>
          </ng-template>
          <div style="font-size:2.5rem;color:#1d4ed8;font-weight:700">{{ tareas().length }}</div>
        </p-card>

        <p-card styleClass="stat-card">
          <ng-template pTemplate="header">
            <div style="color:var(--text-secondary);font-size:0.85rem;font-weight:600">Entregas Pendientes</div>
          </ng-template>
          <div style="font-size:2.5rem;color:var(--color-warning);font-weight:700">{{ pendientes() }}</div>
        </p-card>
      </div>

      <div style="margin-bottom:1rem">
        <h3>Tareas de la Materia</h3>
        <app-interactive-grid
          [data]="tareas()"
          [columns]="tareasColumns"
          [showDelete]="false"
          (rowSelected)="abrirEdicion($event)"
        />
      </div>

      <div>
        <h3>Mis Entregas Pendientes</h3>
        <p-table [value]="entregas()" styleClass="p-datatable-sm">
          <ng-template pTemplate="header">
            <tr>
              <th>Tarea</th>
              <th style="width:120px">Vence</th>
              <th style="width:80px">Estado</th>
              <th style="width:150px">Acción</th>
            </tr>
          </ng-template>

          <ng-template pTemplate="body" let-entrega>
            <tr>
              <td>{{ entrega.titulo }}</td>
              <td>{{ entrega.fecha_limite | date:'dd/MM/yyyy' }}</td>
              <td>
                <p-tag
                   [value]="entrega.estatus_entrega === 'PENDIENTE' ? 'Pendiente' : 'Entregado'"
                   [severity]="entrega.estatus_entrega === 'PENDIENTE' ? 'warn' : 'success'"
                />
                @if (entrega.vencida) {
                  <p-tag value="Vencida" severity="danger" styleClass="ml-1" />
                }
              </td>
              <td>
                @if (entrega.estatus_entrega === 'PENDIENTE') {
                  <p-button
                    label="Subir archivo"
                    icon="pi pi-upload"
                    size="small"
                    (onClick)="showUploadDialog(entrega)"
                  />
                } @else {
                  <span style="font-size:.8rem;color:var(--color-success)"><i class="pi pi-check-circle"></i> Entregado</span>
                }
              </td>
            </tr>
          </ng-template>

          <ng-template pTemplate="emptymessage">
            <tr><td [colSpan]="4">Todas las entregas completadas</td></tr>
          </ng-template>
        </p-table>
      </div>
    }

    <!-- Dialogo Nueva/Editar Tarea -->
    <p-dialog [(visible)]="showDialog" [header]="tareaEditId ? 'Editar Tarea' : 'Nueva Tarea'" [modal]="true" [style]="{width:'450px'}">
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
        <div>
          <p-button label="Guardar" (onClick)="guardarTarea()" [loading]="saving()" />
        </div>
      </div>
    </p-dialog>
  `,
  styles: [`
    .page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 1.5rem; }
    .subtitle { color: var(--text-secondary); font-size: 0.85rem; margin: 0.25rem 0 0; }
    .filter-bar { display: flex; gap: 1rem; margin-bottom: 1.5rem; }
    .filter-select { min-width: 220px; }
    h3 { color: var(--text-primary); margin-bottom: 1rem; margin-top: 2rem; }
    .empty-state { display: flex; flex-direction: column; align-items: center; gap: 0.5rem; padding: 4rem; color: var(--text-muted); }
    .dlg-lbl { display: block; font-size: 0.85rem; margin-bottom: 0.25rem; color: var(--text-secondary); }
    :host ::ng-deep .stat-card { text-align: center; }
  `],
})
export class TareasComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly ctx = inject(ContextService);
  private readonly notify = inject(ApexNotificationService);

  readonly tareasColumns: ColumnConfig[] = [
    { field: 'titulo',         header: 'Tarea' },
    { field: 'fecha_entrega',  header: 'Entrega',      width: '120px' },
    { field: 'puntaje_maximo', header: 'Puntaje Máx.', width: '110px', type: 'number' },
    { field: 'origen',         header: 'Origen',       width: '90px' },
  ];

  plantelesOpts = signal<any[]>([]);
  nivelesOpts = signal<any[]>([]);
  gradosOpts = signal<any[]>([]);
  gruposOpts = signal<GrupoConLabel[]>([]);
  materiasOpts = signal<Materia[]>([]);

  selectedPlantelId: string | null = null;
  selectedNivelId: string | null = null;
  selectedGradoId: string | null = null;
  selectedGrupoId: string | null = null;
  selectedMateriaId: string | null = null;

  readonly isPlantelDisabled = computed(() => this.ctx.nivelAcceso() > 2);
  readonly isNivelDisabled = computed(() => this.ctx.nivelAcceso() > 3);

  get selectedGrupo(): GrupoConLabel | null {
    return this.gruposOpts().find(g => g.id === this.selectedGrupoId) || null;
  }

  get selectedMateria(): Materia | null {
    return this.materiasOpts().find(m => m.id === this.selectedMateriaId) || null;
  }

  tareas = signal<Tarea[]>([]);
  entregas = signal<any[]>([]);
  readonly pendientes = computed(() => this.entregas().filter(e => e.estatus_entrega === 'PENDIENTE').length);

  showDialog = false;
  saving = signal(false);
  tareaEditId: string | null = null;

  form = {
    titulo: '',
    descripcion: '',
    fecha_entrega: '',
    puntaje_maximo: 10,
  };

  readonly puedeCrear = computed(() => this.ctx.nivelAcceso() <= 4);

  constructor() {
    // Escucha cambios del plantel/nivel en el contexto global si no se han seleccionado manualmente
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
        const currentPlantel = this.ctx.plantel();
        if (currentPlantel?.id) {
          this.selectedPlantelId = currentPlantel.id;
          this.onPlantelChange();
        }
      },
      error: () => {}
    });
  }

  onPlantelChange(): void {
    this.selectedNivelId = null;
    this.selectedGradoId = null;
    this.selectedGrupoId = null;
    this.selectedMateriaId = null;
    this.nivelesOpts.set([]);
    this.gradosOpts.set([]);
    this.gruposOpts.set([]);
    this.materiasOpts.set([]);
    this.tareas.set([]);
    this.entregas.set([]);

    if (!this.selectedPlantelId) return;

    this.api.get<any[]>(`/planteles/${this.selectedPlantelId}/niveles`).subscribe({
      next: ns => {
        const mapped = ns.map(x => ({ id: x.id ?? x.nivel_id, nombre_nivel: x.nombre_nivel }));
        this.nivelesOpts.set(mapped);
        
        const ctxNivel = this.ctx.nivel();
        if (ctxNivel && mapped.some(n => n.id === ctxNivel.id)) {
          this.selectedNivelId = ctxNivel.id;
          this.onNivelChange();
        }
      },
      error: () => {}
    });
  }

  onNivelChange(): void {
    this.selectedGradoId = null;
    this.selectedGrupoId = null;
    this.selectedMateriaId = null;
    this.gradosOpts.set([]);
    this.gruposOpts.set([]);
    this.materiasOpts.set([]);
    this.tareas.set([]);
    this.entregas.set([]);

    if (!this.selectedNivelId) return;

    this.api.get<any[]>(`/catalogs/grados`, { nivel_id: this.selectedNivelId }).subscribe({
      next: gs => {
        this.gradosOpts.set(gs);
      },
      error: () => {}
    });
  }

  onGradoChange(): void {
    this.selectedGrupoId = null;
    this.selectedMateriaId = null;
    this.gruposOpts.set([]);
    this.materiasOpts.set([]);
    this.tareas.set([]);
    this.entregas.set([]);

    if (!this.selectedGradoId) return;

    const params: Record<string, any> = { solo_activos: true, ciclo_vigente: true };
    if (this.selectedPlantelId) params['plantel_id'] = this.selectedPlantelId;
    if (this.selectedGradoId) params['grado_id'] = this.selectedGradoId;

    this.api.get<Grupo[]>('/grupos', params).subscribe({
      next: gps => {
        this.gruposOpts.set(gps.map(x => ({ ...x, _label: grupoLabel(x) })));
      },
      error: () => this.gruposOpts.set([])
    });
  }

  onGrupoChange(): void {
    this.selectedMateriaId = null;
    this.materiasOpts.set([]);
    this.tareas.set([]);
    this.entregas.set([]);

    if (!this.selectedGrupoId) return;

    const grupo = this.selectedGrupo;
    if (!grupo) return;

    this.api.get<Materia[]>('/planes-estudio', { grado_id: grupo.grado_id }).subscribe(planes => {
      const materias: Materia[] = (planes as any[]).map((p: any) => p.materia).filter(Boolean);
      this.materiasOpts.set(materias);
    });
  }

  loadTareas(): void {
    if (!this.selectedGrupoId || !this.selectedMateriaId) return;
    this.api.get<Tarea[]>('/tareas', { grupo_id: this.selectedGrupoId, materia_id: this.selectedMateriaId })
      .subscribe(t => {
        this.tareas.set(t);
        const usuarioId = this.ctx.usuario()?.id;
        if (usuarioId && this.ctx.nivelAcceso() >= 5) {
          this.api.get<any[]>(`/entregas/alumno/${usuarioId}`, { solo_pendientes: true })
            .subscribe({
              next: e => this.entregas.set(e),
              error: () => this.entregas.set([]),
            });
        } else {
          this.entregas.set([]);
        }
      });
  }

  abrirCreacion(): void {
    this.tareaEditId = null;
    this.form = {
      titulo: '',
      descripcion: '',
      fecha_entrega: new Date().toISOString().substring(0, 10),
      puntaje_maximo: 10,
    };
    this.showDialog = true;
  }

  abrirEdicion(tarea: Tarea): void {
    this.tareaEditId = tarea.id;
    this.form = {
      titulo: tarea.titulo,
      descripcion: tarea.descripcion || '',
      fecha_entrega: tarea.fecha_entrega,
      puntaje_maximo: tarea.puntaje_maximo,
    };
    this.showDialog = true;
  }

  guardarTarea(): void {
    if (!this.form.titulo || !this.form.fecha_entrega || !this.selectedGrupoId || !this.selectedMateriaId) {
      this.notify.warning('Campos vacíos', 'Debe rellenar los campos obligatorios');
      return;
    }

    this.saving.set(true);
    const payload = {
      titulo: this.form.titulo,
      descripcion: this.form.descripcion,
      fecha_entrega: this.form.fecha_entrega,
      puntaje_maximo: this.form.puntaje_maximo,
      grupo_id: this.selectedGrupoId,
      materia_id: this.selectedMateriaId,
      fecha_asignacion: new Date().toISOString().substring(0, 10),
      permite_entrega_tarde: true,
      origen: 'MANUAL',
    };

    if (this.tareaEditId) {
      this.api.patch<Tarea>(`/tareas/${this.tareaEditId}`, payload).subscribe({
        next: () => {
          this.saving.set(false);
          this.showDialog = false;
          this.notify.success('Actualizado', 'Tarea modificada correctamente');
          this.loadTareas();
        },
        error: () => this.saving.set(false),
      });
    } else {
      this.api.post<Tarea>('/tareas', payload).subscribe({
        next: () => {
          this.saving.set(false);
          this.showDialog = false;
          this.notify.success('Creado', 'Tarea agregada correctamente');
          this.loadTareas();
        },
        error: () => this.saving.set(false),
      });
    }
  }

  showUploadDialog(entrega: any): void {
    this.notify.info('Upload', `Subir entrega de: ${entrega.titulo}`);
  }
}
