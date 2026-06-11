import { Component, OnInit, inject, signal, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { CardModule } from 'primeng/card';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { FileUploadModule } from 'primeng/fileupload';
import { SelectModule } from 'primeng/select';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';

import { ApiService } from '../../core/services/api.service';
import { ContextService } from '../../core/services/context.service';
import type { Tarea, Grupo, Materia } from '../../core/models';
import { grupoLabel } from '../../core/models';
import { ApexNotificationService } from 'apex-component-library';

type GrupoConLabel = Grupo & { _label: string };

@Component({
  selector: 'app-tareas',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    TableModule, CardModule, ButtonModule, TagModule, FileUploadModule,
    SelectModule, DialogModule, InputTextModule, InputNumberModule,
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

    <!-- Filtros de Contexto (APEX Interactive Grid Style) -->
    <div class="filter-bar">
      <p-select
        [options]="grupos()"
        [(ngModel)]="selectedGrupo"
        optionLabel="_label"
        placeholder="Seleccionar grupo"
        (onChange)="onGrupoChange()"
        [showClear]="true"
        styleClass="filter-select"
      

      [filter]="true" filterPlaceholder="Buscar..."/>
      <p-select
        [options]="materias()"
        [(ngModel)]="selectedMateria"
        optionLabel="nombre_materia"
        placeholder="Seleccionar materia"
        (onChange)="loadTareas()"
        [showClear]="true"
        [disabled]="!selectedGrupo"
        styleClass="filter-select"
      

      [filter]="true" filterPlaceholder="Buscar..."/>
    </div>

    @if (!selectedGrupo || !selectedMateria) {
      <div class="empty-state">
        <i class="pi pi-info-circle" style="font-size:2rem; color:var(--text-muted)"></i>
        <p>Selecciona un grupo y una materia para ver y gestionar las tareas.</p>
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
        <p-table [value]="tareas()" styleClass="p-datatable-sm">
          <ng-template pTemplate="header">
            <tr>
              <th pSortableColumn="titulo">Tarea <p-sortIcon field="titulo" /></th>
              <th pSortableColumn="fecha_entrega" style="width:120px">Entrega <p-sortIcon field="fecha_entrega" /></th>
              <th style="width:100px">Puntaje Máx.</th>
              <th style="width:120px">Acciones</th>
            </tr>
          </ng-template>

          <ng-template pTemplate="body" let-tarea>
            <tr>
              <td>{{ tarea.titulo }}</td>
              <td>{{ tarea.fecha_entrega }}</td>
              <td style="text-align:center">{{ tarea.puntaje_maximo }}</td>
              <td>
                <div style="display:flex;gap:0.25rem">
                  <p-button
                    icon="pi pi-search"
                    [rounded]="true"
                    [text]="true"
                    [plain]="true"
                    pTooltip="Ver entregas"
                  />
                  @if (puedeCrear()) {
                    <p-button
                      icon="pi pi-pencil"
                      [rounded]="true"
                      [text]="true"
                      pTooltip="Editar"
                      (onClick)="abrirEdicion(tarea)"
                    />
                  }
                </div>
              </td>
            </tr>
          </ng-template>

          <ng-template pTemplate="emptymessage">
            <tr><td [colSpan]="4">No hay tareas para esta materia y grupo</td></tr>
          </ng-template>
        </p-table>
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

  grupos = signal<GrupoConLabel[]>([]);
  materias = signal<Materia[]>([]);
  selectedGrupo: GrupoConLabel | null = null;
  selectedMateria: Materia | null = null;

  tareas = signal<Tarea[]>([]);
  entregas = signal<any[]>([]);
  pendientes = () => this.entregas().filter(e => e.estado === 'PENDIENTE').length;

  showDialog = false;
  saving = signal(false);
  tareaEditId: string | null = null;

  form = {
    titulo: '',
    descripcion: '',
    fecha_entrega: '',
    puntaje_maximo: 10,
  };

  puedeCrear(): boolean {
    return this.ctx.nivelAcceso() <= 4; // Docentes y administradores
  }

  constructor() {
    effect(() => {
      const plantelId = this.ctx.plantel()?.id;
      const nivelNombre = this.ctx.nivel()?.nombre_nivel;
      this.loadGrupos(plantelId, nivelNombre);
    });
  }

  loadGrupos(plantelId?: string, nivelNombre?: string): void {
    const params: Record<string, any> = { solo_activos: true, ciclo_vigente: true };
    if (plantelId) params['plantel_id'] = plantelId;
    if (nivelNombre && nivelNombre !== 'TODOS') params['nivel'] = nivelNombre;

    this.api.get<Grupo[]>('/grupos', params).subscribe({
      next: g => {
        this.grupos.set(g.map(x => ({ ...x, _label: grupoLabel(x) })));
        if (this.selectedGrupo && !g.some(x => x.id === this.selectedGrupo!.id)) {
          this.selectedGrupo = null;
          this.selectedMateria = null;
          this.tareas.set([]);
          this.entregas.set([]);
        }
      },
      error: () => this.grupos.set([])
    });
  }

  ngOnInit(): void {
  }

  onGrupoChange(): void {
    this.selectedMateria = null;
    this.tareas.set([]);
    this.entregas.set([]);
    if (!this.selectedGrupo) return;

    const gradoId = this.selectedGrupo.grado_id;
    this.api.get<Materia[]>('/planes-estudio', { grado_id: gradoId }).subscribe(planes => {
      const materias: Materia[] = (planes as any[]).map((p: any) => p.materia).filter(Boolean);
      this.materias.set(materias);
    });
  }

  loadTareas(): void {
    if (!this.selectedGrupo || !this.selectedMateria) return;
    this.api.get<Tarea[]>('/tareas', { grupo_id: this.selectedGrupo.id, materia_id: this.selectedMateria.id })
      .subscribe(t => {
        this.tareas.set(t);
        // Solo cargar entregas pendientes si el usuario es alumno
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
    if (!this.form.titulo || !this.form.fecha_entrega || !this.selectedGrupo || !this.selectedMateria) {
      this.notify.warning('Campos vacíos', 'Debe rellenar los campos obligatorios');
      return;
    }

    this.saving.set(true);
    const payload = {
      titulo: this.form.titulo,
      descripcion: this.form.descripcion,
      fecha_entrega: this.form.fecha_entrega,
      puntaje_maximo: this.form.puntaje_maximo,
      grupo_id: this.selectedGrupo.id,
      materia_id: this.selectedMateria.id,
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
