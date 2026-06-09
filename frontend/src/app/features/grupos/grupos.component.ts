/**
 * FASE 1 & FASE 24 — Grupos (Class Groups) + Interactive Grid APEX-style
 * Manages academic groups with capacity tracking and admin controls.
 */
import { Component, OnInit, inject, signal, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { DialogModule } from 'primeng/dialog';
import { ToggleSwitchModule } from 'primeng/toggleswitch';
import { SelectModule } from 'primeng/select';
import { ToastModule } from 'primeng/toast';
import { MessageService } from 'primeng/api';

import { ApiService } from '../../core/services/api.service';
import { ContextService } from '../../core/services/context.service';
import { ExportService, ExportColumn } from '../../core/services/export.service';
import { InteractiveGridComponent, ColumnConfig } from '../../shared/components/interactive-grid/interactive-grid.component';
import type { Grupo } from '../../core/models';
import { grupoLabel } from '../../core/models';

@Component({
  selector: 'app-grupos',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    ButtonModule, InputTextModule, DialogModule, ToggleSwitchModule, SelectModule, ToastModule,
    InteractiveGridComponent
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

    <!-- Interactive Grid APEX-style (Spec: spec/modules/fase-24-interactive-grid/) -->
    <app-interactive-grid
      [data]="gruposDatos()"
      [columns]="columnas"
      [loading]="false"
      (rowSelected)="abrirEditarGrupo($event)"
    />

    <!-- Diálogo de Alta/Modificación de Grupos para Administradores -->
    <p-dialog [(visible)]="dlgGrupo" [header]="grupoEdit?.id ? 'Editar grupo' : 'Nuevo grupo'"
      [modal]="true" [style]="{width:'420px'}">
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
    .form-grid { display: grid; grid-template-columns: 140px 1fr; gap: 0.75rem 1rem; align-items: center; padding: 0.5rem 0; }
    .form-grid label { font-size: 0.85rem; color: var(--text-color-secondary); font-weight: 600; }
  `],
})
export class GruposComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly ctx = inject(ContextService);
  private readonly export = inject(ExportService);
  private readonly msg = inject(MessageService);

  grupos = signal<Grupo[]>([]);
  gruposDatos = signal<any[]>([]);
  grados = signal<any[]>([]);

  dlgGrupo = false;
  grupoEdit: any = null;
  saving = signal(false);

  readonly grupoLabel = grupoLabel;

  columnas: ColumnConfig[] = [
    { field: 'nivel_y_grado', header: 'Nivel / Grado', sortable: true, filterable: true, width: '260px' },
    { field: 'nombre_grupo', header: 'Grupo', sortable: true, filterable: true, width: '90px' },
    { field: 'ocupacion', header: 'Ocupación', sortable: false, filterable: false, width: '140px' },
    { field: 'estado', header: 'Estado', sortable: true, filterable: true, width: '100px' },
  ];

  private readonly exportCols: ExportColumn[] = [
    { field: 'nombre_nivel', header: 'Nivel' },
    { field: 'nombre_grado', header: 'Grado' },
    { field: 'nombre_grupo', header: 'Grupo' },
    { field: 'inscritos', header: 'Inscritos', format: v => String(v ?? 0) },
    { field: 'capacidad_maxima', header: 'Capacidad' },
  ];

  isAdmin(): boolean {
    return this.ctx.nivelAcceso() <= 2;
  }

  exportCSV():  void { this.export.toCSV(this.grupos(), this.exportCols, 'grupos'); }
  exportXLSX(): void { this.export.toXLSX(this.grupos(), this.exportCols, 'Grupos', 'grupos'); }

  constructor() {
    effect(() => {
      const plantelId = this.ctx.plantel()?.id;
      this.recargar();
      if (this.isAdmin()) {
        this.loadGrados(plantelId);
      }
    });
  }

  loadGrados(plantelId?: string): void {
    this.grados.set([]);
    if (!plantelId) {
      this.api.get<any[]>('/catalogs/grados').subscribe({
        next: gs => this.grados.set(gs.map(g => ({ id: g.id, label: g.nombre_grado }))),
        error: () => {}
      });
      return;
    }

    this.api.get<any[]>(`/planteles/${plantelId}/niveles`).subscribe({
      next: ns => {
        ns.forEach(n => {
          this.api.get<any[]>(`/catalogs/niveles/${n.id}/grados`).subscribe({
            next: gs => {
              const current = this.grados();
              const mapped = gs.map(g => ({ id: g.id, label: `${n.nombre_nivel} - ${g.nombre_grado}` }));
              this.grados.set([...current, ...mapped]);
            },
            error: () => {
              this.api.get<any[]>('/catalogs/grados').subscribe(gs => {
                this.grados.set(gs.map(g => ({ id: g.id, label: g.nombre_grado })));
              });
            }
          });
        });
      },
      error: () => {
        this.api.get<any[]>('/catalogs/grados').subscribe(gs => {
          this.grados.set(gs.map(g => ({ id: g.id, label: g.nombre_grado })));
        });
      }
    });
  }

  ngOnInit(): void {
  }

  recargar(): void {
    const plantelId = this.ctx.plantel()?.id;
    const params: Record<string, any> = { ciclo_vigente: true };
    if (plantelId) params['plantel_id'] = plantelId;

    this.api.get<Grupo[]>('/grupos', params).subscribe({
      next: g => {
        this.grupos.set(g);
        this.gruposDatos.set(g.map(grp => ({
          id: grp.id,
          nivel_y_grado: `${grp.nombre_nivel} — ${grp.nombre_grado}`,
          nombre_grupo: grp.nombre_grupo,
          ocupacion: `${grp.inscritos ?? 0} / ${grp.capacidad_maxima}`,
          estado: grp.is_active ? '✓ Activo' : 'Inactivo',
          turno: grp.turno || '—',
          _original: grp,
        })));
      },
      error: () => {
        this.grupos.set([]);
        this.gruposDatos.set([]);
      }
    });
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

  abrirEditarGrupo(row: any): void {
    const grp = row._original || this.grupos().find(g => g.id === row.id);
    if (!grp || !this.isAdmin()) return;
    this.grupoEdit = { id: grp.id, nombre_grupo: grp.nombre_grupo, capacidad_maxima: grp.capacidad_maxima, turno: grp.turno, is_active: grp.is_active };
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
