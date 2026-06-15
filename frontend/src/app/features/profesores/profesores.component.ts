/**
 * FASE 1 & FASE 24 — Profesores (Teachers) + Interactive Grid APEX-style
 * Manages teacher records with filtering, sorting, and profile management.
 */
import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { DialogModule } from 'primeng/dialog';

import { ApiService } from '../../core/services/api.service';
import { ContextService } from '../../core/services/context.service';
import { ExportService, ExportColumn } from '../../core/services/export.service';
import { InteractiveGridComponent, ColumnConfig } from '../../shared/components/interactive-grid/interactive-grid.component';
import { ProfesorPerfilComponent } from '../../shared/components/profesor-perfil/profesor-perfil.component';
import { HelpButtonComponent } from '../../shared/components/help-button/help-button.component';
import { ImportButtonComponent } from '../../shared/components/import-button/import-button.component';
import type { Profesor } from '../../core/models';
import { ApexNotificationService } from 'apex-component-library';

@Component({
  selector: 'app-profesores',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    ButtonModule, InputTextModule, DialogModule,
    InteractiveGridComponent, ProfesorPerfilComponent, HelpButtonComponent, ImportButtonComponent,
  ],
  template: `

    <app-profesor-perfil
      [visible]="perfilVisible()"
      (visibleChange)="perfilVisible.set($event)"
      [profesor]="profesorSeleccionado()"
      (saved)="cargarProfesores()"
    />

    <div class="page-header">
      <div>
        <h2>Profesores</h2>
        <p class="subtitle">Plantilla docente — {{ totalProfesores() | number }} activos</p>
      </div>
      <div style="display:flex;gap:0.5rem;align-items:center">
        <app-help-button modulo="profesores" />
        <app-import-button entidad="profesores" [onSuccess]="recargar" />
        <p-button label="CSV"   icon="pi pi-file"       severity="secondary" [text]="true" (onClick)="exportCSV()"  pTooltip="Exportar CSV" />
        <p-button label="Excel" icon="pi pi-file-excel" severity="secondary" [text]="true" (onClick)="exportXLSX()" pTooltip="Exportar Excel" />
        <p-button label="Nuevo profesor" icon="pi pi-plus" (onClick)="openDialog()" />
      </div>
    </div>

    <!-- Interactive Grid APEX-style (Spec: spec/modules/fase-24-interactive-grid/) -->
    <app-interactive-grid
      [data]="profesoresDatos()"
      [columns]="columnas"
      [loading]="loadingTabla()"
      (rowSelected)="abrirPerfil($event)"
    />

    <p-dialog [visible]="showDialog()" (visibleChange)="showDialog.set($event)" header="Nuevo Profesor" [modal]="true" [style]="{width:'400px'}">
      <div style="display:flex;flex-direction:column;gap:1rem">
        <div>
          <label class="dlg-lbl">Nombre(s) *</label>
          <input pInputText [(ngModel)]="form.nombre" style="width:100%" />
        </div>
        <div>
          <label class="dlg-lbl">Apellido paterno *</label>
          <input pInputText [(ngModel)]="form.apellido_paterno" style="width:100%" />
        </div>
        <div>
          <label class="dlg-lbl">Apellido materno</label>
          <input pInputText [(ngModel)]="form.apellido_materno" style="width:100%" />
        </div>
        <div>
          <label class="dlg-lbl">Número de empleado *</label>
          <input pInputText [(ngModel)]="form.numero_empleado" style="width:100%" />
        </div>
        <div>
          <label class="dlg-lbl">CURP (18 caracteres) *</label>
          <input pInputText [(ngModel)]="form.curp" style="width:100%;text-transform:uppercase;font-family:monospace" maxlength="18" />
        </div>
        <div>
          <label class="dlg-lbl">Tipo de contrato</label>
          <input pInputText [(ngModel)]="form.tipo_contrato" placeholder="Ej: TIEMPO_COMPLETO" style="width:100%" />
        </div>
        <p-button label="Crear profesor" (onClick)="crearProfesor()" [loading]="loading()" />
      </div>
    </p-dialog>
  `,
  styles: [`
    .page-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 1rem; }
    .subtitle { font-size: 0.82rem; color: var(--text-color-secondary); margin: 0; }
    .dlg-lbl { display: block; font-size: .85rem; margin-bottom: .25rem; color: var(--text-color-secondary); }
  `],
})
export class ProfesoresComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly ctx = inject(ContextService);
  private readonly exp = inject(ExportService);
  private readonly notify = inject(ApexNotificationService);

  profesores = signal<Profesor[]>([]);
  profesoresDatos = signal<any[]>([]);
  totalProfesores = signal(0);
  profesorSeleccionado = signal<Profesor | null>(null);
  perfilVisible = signal(false);
  showDialog = signal(false);
  loading = signal(false);
  loadingTabla = signal(false);
  form = { nombre: '', apellido_paterno: '', apellido_materno: '', numero_empleado: '', curp: '', tipo_contrato: '' };

  columnas: ColumnConfig[] = [
    { field: 'numero_empleado', header: 'Empleado', sortable: true, filterable: true, width: '120px' },
    { field: 'nombre_completo', header: 'Nombre Completo', sortable: true, filterable: true, width: '220px' },
    { field: 'rfc', header: 'RFC', sortable: true, filterable: true, width: '140px' },
    { field: 'tipo_contrato', header: 'Contrato', sortable: true, filterable: true, width: '130px' },
    { field: 'especialidad', header: 'Especialidad', sortable: true, filterable: true, width: '120px' },
    { field: 'turno', header: 'Turno', sortable: true, filterable: true, width: '90px' },
  ];

  private readonly exportCols: ExportColumn[] = [
    { field: 'numero_empleado', header: 'No. Empleado' },
    { field: 'nombre_completo', header: 'Nombre' },
    { field: 'rfc', header: 'RFC' },
    { field: 'tipo_contrato', header: 'Contrato' },
    { field: 'especialidad', header: 'Especialidad' },
  ];

  readonly recargar = () => this.cargarProfesores();

  ngOnInit(): void { this.cargarProfesores(); }

  cargarProfesores(): void {
    const params: Record<string, any> = { pagina: 1, por_pagina: 500 };
    const plantelId = this.ctx.plantel()?.id;
    if (plantelId) params['plantel_id'] = plantelId;

    this.loadingTabla.set(true);
    this.api.get<{ data: Profesor[]; total: number }>('/profesores', params).subscribe({
      next: resp => {
        this.profesores.set(resp.data);
        this.totalProfesores.set(resp.total);
        this.profesoresDatos.set(resp.data.map(p => ({
          id: p.id,
          numero_empleado: p.numero_empleado,
          nombre_completo: `${p.persona?.nombre} ${p.persona?.apellido_paterno} ${p.persona?.apellido_materno || ''}`.trim(),
          rfc: p.rfc || '—',
          tipo_contrato: p.tipo_contrato || '—',
          especialidad: p.especialidad || '—',
          turno: p.turno || '—',
          cedula_profesional: p.cedula_profesional || '',
          _original: p,
        })));
        this.loadingTabla.set(false);
      },
      error: () => {
        this.loadingTabla.set(false);
        this.notify.error('Error', 'No se pudieron cargar los profesores');
      },
    });
  }

  abrirPerfil(row: any): void {
    const prof = row._original || this.profesores().find(p => p.id === row.id);
    if (!prof) return;
    this.api.get<Profesor>(`/profesores/${prof.id}`).subscribe({
      next: full => {
        this.profesorSeleccionado.set(full);
        this.perfilVisible.set(true);
      },
      error: () => {
        this.notify.error('Error', 'No se pudo cargar el expediente');
      },
    });
  }

  openDialog(): void {
    this.showDialog.set(true);
    const nextEmp = `EMP-${this.totalProfesores() + 101}`;
    this.form = { nombre: '', apellido_paterno: '', apellido_materno: '', numero_empleado: nextEmp, curp: '', tipo_contrato: 'TIEMPO_COMPLETO' };
  }

  crearProfesor(): void {
    if (!this.form.nombre || !this.form.apellido_paterno || !this.form.numero_empleado || !this.form.curp) {
      this.notify.warning('Campos requeridos', 'Nombre, apellido paterno, número de empleado y CURP son obligatorios');
      return;
    }
    this.loading.set(true);
    const payload = {
      numero_empleado: this.form.numero_empleado,
      tipo_contrato: this.form.tipo_contrato || 'TIEMPO_COMPLETO',
      persona: {
        nombre: this.form.nombre,
        apellido_paterno: this.form.apellido_paterno,
        apellido_materno: this.form.apellido_materno || '',
        curp: this.form.curp.toUpperCase(),
      },
      plantel_id: this.ctx.plantel()?.id,
    };
    this.api.post<Profesor>('/profesores', payload).subscribe({
      next: (newProf) => {
        this.showDialog.set(false);
        this.loading.set(false);
        this.notify.success('Creado', `Profesor: ${newProf.numero_empleado}`);
        this.cargarProfesores();
      },
      error: (e) => {
        this.loading.set(false);
        const detail = e.error?.detail;
        const msg = Array.isArray(detail) ? detail.map((d: any) => d.msg || d).join('; ')
                  : typeof detail === 'string' ? detail
                  : 'Error al crear profesor';
        this.notify.error('Error', msg);
      },
    });
  }

  exportCSV():  void { this.exp.toCSV(this.profesoresDatos(), this.exportCols, 'profesores'); }
  exportXLSX(): void { this.exp.toXLSX(this.profesoresDatos(), this.exportCols, 'Profesores', 'profesores'); }
}
