import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { InputTextModule } from 'primeng/inputtext';
import { DialogModule } from 'primeng/dialog';
import { ToggleSwitchModule } from 'primeng/toggleswitch';
import { SelectModule } from 'primeng/select';
import { ToastModule } from 'primeng/toast';
import { MessageService } from 'primeng/api';

import { ApiService } from '../../core/services/api.service';
import { ContextService } from '../../core/services/context.service';
import { ExportService, ExportColumn } from '../../core/services/export.service';
import type { Grupo } from '../../core/models';
import { grupoLabel } from '../../core/models';

@Component({
  selector: 'app-grupos',
  standalone: true,
  imports: [
    CommonModule, FormsModule, TableModule, ButtonModule, TagModule,
    InputTextModule, DialogModule, ToggleSwitchModule, SelectModule, ToastModule
  ],
  providers: [MessageService],
  template: `
    <p-toast />

    <div class="page-header">
      <div>
        <h2>Grupos</h2>
        <p class="subtitle">Grupos académicos del plantel</p>
      </div>
      <div style="display:flex;gap:0.5rem;align-items:center">
        <p-button label="CSV"   icon="pi pi-file"       severity="secondary" [text]="true" (onClick)="exportCSV()"  pTooltip="Exportar CSV" />
        <p-button label="Excel" icon="pi pi-file-excel" severity="secondary" [text]="true" (onClick)="exportXLSX()" pTooltip="Exportar Excel" />
        @if (isAdmin()) {
          <p-button label="Nuevo grupo" icon="pi pi-plus" (onClick)="abrirNuevoGrupo()" />
        }
      </div>
    </div>

    <p-table
      #dt
      [value]="grupos()"
      [rows]="25"
      [paginator]="true"
      [globalFilterFields]="['nombre_grupo']"
      [showCurrentPageReport]="true"
      currentPageReportTemplate="{first}-{last} de {totalRecords} grupos"
      styleClass="p-datatable-sm p-datatable-striped"
    >
      <ng-template pTemplate="caption">
        <input pInputText type="text" placeholder="Buscar grupo..." (input)="dt.filterGlobal($any($event.target).value,'contains')" style="width:250px" />
      </ng-template>

      <ng-template pTemplate="header">
        <tr>
          <th pSortableColumn="nombre_nivel" style="min-width:260px">Nivel / Grado <p-sortIcon field="nombre_nivel" /></th>
          <th pSortableColumn="nombre_grupo" style="width:90px">Grupo <p-sortIcon field="nombre_grupo" /></th>
          <th style="width:140px;text-align:center">Ocupación</th>
          <th style="width:100px;text-align:center">Estado</th>
          @if (isAdmin()) {
            <th style="width:100px;text-align:center">Acciones</th>
          }
        </tr>
      </ng-template>

      <ng-template pTemplate="body" let-g>
        <tr>
          <td>
            <span class="nivel-badge">{{ g.nombre_nivel }}</span>
            <span class="grado-text">{{ g.nombre_grado }}</span>
          </td>
          <td style="text-align:center"><strong>{{ g.nombre_grupo }}</strong></td>
          <td style="text-align:center">
            <span class="ocupacion" [class.lleno]="g.inscritos >= g.capacidad_maxima">
              {{ g.inscritos ?? 0 }} / {{ g.capacidad_maxima }}
            </span>
          </td>
          <td style="text-align:center">
            <p-tag [value]="g.is_active ? 'Activo' : 'Inactivo'" [severity]="g.is_active ? 'success' : 'secondary'" />
          </td>
          @if (isAdmin()) {
            <td style="text-align:center">
              <p-button icon="pi pi-pencil" [text]="true" [rounded]="true" pTooltip="Editar" (onClick)="abrirEditarGrupo(g)" />
            </td>
          }
        </tr>
      </ng-template>

      <ng-template pTemplate="emptymessage">
        <tr><td [colSpan]="isAdmin() ? 5 : 4" style="text-align:center;padding:2rem;color:#94A3B8">Cargando grupos...</td></tr>
      </ng-template>
    </p-table>

    <!-- Diálogo de Alta/Modificación de Grupos para Administradores -->
    <p-dialog [(visible)]="dlgGrupo" [header]="grupoEdit?.id ? 'Editar grupo' : 'Nuevo grupo'"
      [modal]="true" [draggable]="false" [style]="{width:'420px'}">
      @if (grupoEdit) {
        <div class="form-grid">
          <label>Nombre del Grupo *</label>
          <input pInputText [(ngModel)]="grupoEdit.nombre_grupo" maxlength="10" placeholder="A" />
          <label>Capacidad *</label>
          <input pInputText type="number" [(ngModel)]="grupoEdit.capacidad_maxima" min="1" max="60" />
          <label>Turno *</label>
          <p-select [options]="['MATUTINO','VESPERTINO','NOCTURNO']" [(ngModel)]="grupoEdit.turno" />
          @if (!grupoEdit.id) {
            <label>Nivel / Grado *</label>
            <p-select [options]="grados()" [(ngModel)]="grupoEdit.grado_id" optionLabel="label" optionValue="id" placeholder="Seleccionar grado" />
          }
          @if (grupoEdit.id) {
            <label>Estado</label>
            <p-toggleswitch [(ngModel)]="grupoEdit.is_active" />
          }
        </div>
        <ng-template pTemplate="footer">
          <p-button label="Cancelar" severity="secondary" [text]="true" (onClick)="dlgGrupo=false" />
          <p-button label="Guardar" icon="pi pi-save" [loading]="saving()" (onClick)="guardarGrupo()" />
        </ng-template>
      }
    </p-dialog>
  `,
  styles: [`
    .page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 1.5rem; }
    .subtitle { font-size: 0.82rem; color: var(--text-color-secondary); margin: 0; }
    .nivel-badge {
      display: inline-block; font-size: 0.72rem; font-weight: 700; letter-spacing: .04em;
      text-transform: uppercase; padding: 0.15rem 0.5rem; border-radius: 4px;
      background: var(--primary-100); color: var(--primary-700); margin-right: 0.5rem;
    }
    .grado-text { font-size: 0.88rem; color: var(--text-color); }
    .ocupacion { font-weight: 600; font-size: 0.85rem; color: var(--green-700); }
    .ocupacion.lleno { color: var(--red-600); }
    .form-grid { display: grid; grid-template-columns: 140px 1fr; gap: 0.75rem 1rem; align-items: center; padding: 0.5rem 0; }
    .form-grid label { font-size: 0.85rem; color: var(--text-color-secondary); font-weight: 600; }
  `],
})
export class GruposComponent implements OnInit {
  private readonly api    = inject(ApiService);
  private readonly ctx    = inject(ContextService);
  private readonly export = inject(ExportService);
  private readonly msg    = inject(MessageService);

  grupos = signal<Grupo[]>([]);
  grados = signal<any[]>([]);

  dlgGrupo = false;
  grupoEdit: any = null;
  saving = signal(false);

  readonly grupoLabel = grupoLabel;

  private readonly exportCols: ExportColumn[] = [
    { field: 'nombre_nivel',    header: 'Nivel' },
    { field: 'nombre_grado',    header: 'Grado' },
    { field: 'nombre_grupo',    header: 'Grupo' },
    { field: 'inscritos',       header: 'Inscritos', format: v => String(v ?? 0) },
    { field: 'capacidad_maxima', header: 'Capacidad' },
    { field: 'is_active',       header: 'Activo', format: v => v ? 'Sí' : 'No' },
  ];

  isAdmin(): boolean {
    return this.ctx.nivelAcceso() <= 1;
  }

  exportCSV():  void { this.export.toCSV(this.grupos(), this.exportCols, 'grupos'); }
  exportXLSX(): void { this.export.toXLSX(this.grupos(), this.exportCols, 'Grupos', 'grupos'); }

  ngOnInit(): void {
    this.recargar();
    if (this.isAdmin()) {
      this.api.get<any[]>('/catalogs/niveles').subscribe(niveles => {
        // Cargar todos los grados de los niveles
        this.api.get<any[]>('/admin/usuarios').subscribe(() => {
          // Utilizar el endpoint de grados para cargar las opciones de nuevo grupo
          this.api.get<any[]>('/catalogs/ciclos', { solo_vigentes: true }).subscribe(ciclos => {
            const cicloId = this.ctx.ciclo()?.id || (ciclos[0]?.id);
            // Cargar grados del plantel
            const plantelId = this.ctx.plantel()?.id;
            if (plantelId) {
              this.api.get<any[]>(`/planteles/${plantelId}/niveles`).subscribe(plantelNiveles => {
                const listGrados: any[] = [];
                plantelNiveles.forEach(n => {
                  this.api.get<any[]>('/planes-estudio', { nivel_id: n.id }).subscribe({
                    next: (planes) => {
                      // fallback simple para grados
                    }
                  });
                });
              });
            }
          });
        });
      });

      // Endpoint directo a catálogo de grados si existe
      this.api.get<any[]>('/catalogs/ciclos').subscribe(() => {
        // Cargar todos los grados para la selección de nuevo grupo
        const plantelId = this.ctx.plantel()?.id;
        if (plantelId) {
          this.api.get<any[]>(`/planteles/${plantelId}/niveles`).subscribe(ns => {
            ns.forEach(n => {
              // Cargar grados de este nivel
              this.api.get<any[]>(`/catalogs/niveles/${n.id}/grados`).subscribe({
                next: gs => {
                  const current = this.grados();
                  const mapped = gs.map(g => ({ id: g.id, label: `${n.nombre_nivel} - ${g.nombre_grado}` }));
                  this.grados.set([...current, ...mapped]);
                },
                error: () => {
                  // Fallback: tratar de cargar de grados generales
                  this.api.get<any[]>(`/catalogs/grados`).subscribe(gs => {
                    this.grados.set(gs.map(g => ({ id: g.id, label: g.nombre_grado })));
                  });
                }
              });
            });
          });
        }
      });
    }
  }

  recargar(): void {
    const plantelId = this.ctx.plantel()?.id;
    const cicloId = this.ctx.ciclo()?.id;
    if (plantelId && cicloId) {
      this.api.get<Grupo[]>('/grupos', { plantel_id: plantelId, ciclo_vigente: true })
        .subscribe(g => this.grupos.set(g));
    }
  }

  abrirNuevoGrupo(): void {
    const cicloId = this.ctx.ciclo()?.id;
    if (!cicloId) {
      this.msg.add({ severity: 'warn', summary: 'Ciclo requerido', detail: 'Debe seleccionar un ciclo escolar en la barra superior' });
      return;
    }
    this.grupoEdit = { nombre_grupo: '', capacidad_maxima: 30, turno: 'MATUTINO', ciclo_escolar_id: cicloId, is_active: true };
    this.dlgGrupo = true;
  }

  abrirEditarGrupo(g: Grupo): void {
    this.grupoEdit = { id: g.id, nombre_grupo: g.nombre_grupo, capacidad_maxima: g.capacidad_maxima, turno: g.turno, is_active: g.is_active };
    this.dlgGrupo = true;
  }

  guardarGrupo(): void {
    if (!this.grupoEdit) return;
    if (!this.grupoEdit.nombre_grupo || !this.grupoEdit.capacidad_maxima) {
      this.msg.add({ severity: 'warn', summary: 'Campos requeridos', detail: 'Nombre y capacidad son obligatorios' });
      return;
    }

    this.saving.set(true);
    if (this.grupoEdit.id) {
      this.api.patch(`/admin/grupos/${this.grupoEdit.id}`, {
        nombre_grupo: this.grupoEdit.nombre_grupo,
        capacidad_maxima: this.grupoEdit.capacidad_maxima,
        turno: this.grupoEdit.turno,
        is_active: this.grupoEdit.is_active,
      }).subscribe({
        next: () => {
          this.msg.add({ severity: 'success', summary: 'Guardado', detail: 'Grupo actualizado' });
          this.dlgGrupo = false;
          this.saving.set(false);
          this.recargar();
        },
        error: e => {
          this.msg.add({ severity: 'error', summary: 'Error', detail: e.error?.detail ?? 'Error al actualizar grupo' });
          this.saving.set(false);
        },
      });
    } else {
      this.api.post('/admin/grupos', this.grupoEdit).subscribe({
        next: () => {
          this.msg.add({ severity: 'success', summary: 'Creado', detail: 'Grupo creado exitosamente' });
          this.dlgGrupo = false;
          this.saving.set(false);
          this.recargar();
        },
        error: e => {
          this.msg.add({ severity: 'error', summary: 'Error', detail: e.error?.detail ?? 'Error al crear grupo' });
          this.saving.set(false);
        },
      });
    }
  }
}
