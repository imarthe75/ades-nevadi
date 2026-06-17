/**
 * FASE 1 & FASE 24 — Alumnos (Students) + Interactive Grid APEX-style
 * Lists, filters, sorts, and manages student records with optimistic locking.
 */
import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { ApiService } from '../../core/services/api.service';
import { ContextService } from '../../core/services/context.service';
import { ExportService } from '../../core/services/export.service';
import { InteractiveGridComponent, ColumnConfig } from '../../shared/components/interactive-grid/interactive-grid.component';
import { ImportButtonComponent } from '../../shared/components/import-button/import-button.component';
import { AlumnoPerfilComponent } from '../../shared/components/alumno-perfil/alumno-perfil.component';
import { HelpButtonComponent } from '../../shared/components/help-button/help-button.component';
import type { Estudiante } from '../../core/models';
import { ApexNotificationService, ApexSearchComponent, ApexModalDialogComponent } from 'apex-component-library';

@Component({
  selector: 'app-alumnos',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    ButtonModule, InputTextModule,
    InteractiveGridComponent, ImportButtonComponent, AlumnoPerfilComponent, HelpButtonComponent,
    ApexSearchComponent, ApexModalDialogComponent,
  ],
  template: `

    <!-- Drawer de perfil completo -->
    <app-alumno-perfil
      [visible]="perfilVisible()"
      (visibleChange)="perfilVisible.set($event)"
      [alumno]="alumnoSeleccionado()"
      (saved)="onPerfilGuardado()"
    />

    <div class="page-header">
      <div>
        <h2>Alumnos</h2>
        <p class="subtitle">{{ totalAlumnos() | number }} alumno(s) registrado(s)</p>
      </div>
      <div style="display:flex;gap:0.5rem;align-items:center">
        <app-help-button modulo="alumnos" />
        <p-button label="CSV"   icon="pi pi-file"       severity="secondary" [text]="true" (onClick)="exportCSV()"  pTooltip="Exportar CSV" />
        <p-button label="Excel" icon="pi pi-file-excel" severity="secondary" [text]="true" (onClick)="exportXLSX()" pTooltip="Exportar Excel" />
        <app-import-button entidad="alumnos" [onSuccess]="recargar" />
        <p-button label="Nuevo alumno" icon="pi pi-plus" (onClick)="openDialog()" />
      </div>
    </div>

    <!-- Búsqueda rápida -->
    <apex-search
      placeholder="Buscar alumno..."
      [debounce]="300"
      (valueChange)="busqueda.set($event)"
    />

    <!-- Interactive Grid APEX-style (Spec: spec/modules/fase-24-interactive-grid/) -->
    <app-interactive-grid
      [data]="alumnosFiltrados()"
      [columns]="columnas"
      [loading]="loadingTabla()"
      (rowSelected)="abrirPerfil($event)"
    />

    <!-- Diálogo de alta rápida -->
    <apex-modal-dialog
      [visible]="showDialog()"
      (visibleChange)="showDialog.set($event)"
      title="Nuevo Alumno"
      size="sm">
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
          <label class="dlg-lbl">CURP (18 caracteres) *</label>
          <input pInputText [(ngModel)]="form.curp" style="width:100%;text-transform:uppercase;font-family:monospace" maxlength="18" />
        </div>
        <p class="dlg-note">Una vez creado podrás completar el expediente completo desde el perfil.</p>
      </div>
      <ng-template #footer>
        <p-button label="Crear alumno" (onClick)="crearAlumno()" [loading]="loading()" />
      </ng-template>
    </apex-modal-dialog>
  `,
  styles: [`
    .page-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 1rem; }
    .subtitle { font-size: 0.82rem; color: var(--text-color-secondary); margin: 0; }
    .dlg-lbl { display: block; font-size: .85rem; margin-bottom: .25rem; color: var(--text-color-secondary); }
    .dlg-note { font-size: .78rem; color: var(--text-color-secondary); margin: 0; }
  `],
})
export class AlumnosComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly ctx = inject(ContextService);
  private readonly notify = inject(ApexNotificationService);
  private readonly exp = inject(ExportService);

  alumnos = signal<Estudiante[]>([]);
  alumnosDatos = signal<any[]>([]);
  totalAlumnos = signal(0);
  busqueda = signal('');

  readonly alumnosFiltrados = computed(() => {
    const q = this.busqueda().toLowerCase();
    if (!q) return this.alumnosDatos();
    return this.alumnosDatos().filter(a =>
      (a.nombre_completo ?? a.nombre ?? '').toLowerCase().includes(q) ||
      (a.matricula ?? '').toLowerCase().includes(q)
    );
  });
  alumnoSeleccionado = signal<Estudiante | null>(null);
  perfilVisible = signal(false);
  showDialog = signal(false);
  loading = signal(false);
  loadingTabla = signal(false);
  form = { nombre: '', apellido_paterno: '', apellido_materno: '', curp: '' };

  readonly recargar = () => this.cargarAlumnos();

  // Spec: spec/modules/fase-24-interactive-grid/specification.md § Pre-configured Schemas
  columnas: ColumnConfig[] = [
    { field: 'matricula', header: 'Matrícula', sortable: true, filterable: true, width: '120px' },
    { field: 'nombre_completo', header: 'Nombre Completo', sortable: true, filterable: true, width: '200px' },
    { field: 'curp', header: 'CURP', sortable: true, filterable: true, width: '170px' },
    { field: 'nss', header: 'NSS', sortable: false, filterable: false, width: '80px' },
    { field: 'nivel', header: 'Nivel', sortable: true, filterable: true, width: '120px' },
    { field: 'grado', header: 'Grado', sortable: true, filterable: true, width: '80px' },
    { field: 'grupo', header: 'Grupo', sortable: true, filterable: true, width: '80px' },
    { field: 'fecha_ingreso', header: 'Ingreso', sortable: true, filterable: false, width: '110px' },
  ];

  private readonly exportCols = [
    { field: 'matricula', header: 'Matrícula' },
    { field: 'nombre_completo', header: 'Nombre Completo' },
    { field: 'curp', header: 'CURP' },
    { field: 'fecha_ingreso', header: 'Fecha Ingreso' },
    { field: 'nss', header: 'NSS' },
  ];

  ngOnInit(): void { this.cargarAlumnos(); }

  cargarAlumnos(): void {
    const params: Record<string, any> = {
      pagina: 1,
      por_pagina: 500,
    };
    const plantelId = this.ctx.plantel()?.id;
    if (plantelId) params['plantel_id'] = plantelId;

    this.loadingTabla.set(true);
    this.api.get<{ data: Estudiante[]; total: number }>('/alumnos', params)
      .subscribe({
        next: resp => {
          this.alumnos.set(resp.data);
          this.totalAlumnos.set(resp.total);
          // Transform for grid display
          this.alumnosDatos.set(resp.data.map((a: any) => ({
            id: a.id,
            matricula: a.matricula,
            nombre_completo: `${a.persona?.nombre} ${a.persona?.apellido_paterno} ${a.persona?.apellido_materno || ''}`.trim(),
            curp: a.persona?.curp,
            nss: a.nss || '',
            nivel: a.nivel_educativo?.nombre_nivel || '—',
            grado: a.grado?.nombre_grado || '—',
            grupo: a.grupo?.nombre_grupo || '—',
            fecha_ingreso: a.fecha_ingreso,
            _original: a,
          })));
          this.loadingTabla.set(false);
        },
        error: () => {
          this.loadingTabla.set(false);
          this.notify.error('Error', 'No se pudieron cargar los alumnos');
        },
      });
  }

  abrirPerfil(row: any): void {
    const alumno = row._original || this.alumnos().find(a => a.id === row.id);
    if (!alumno) return;
    this.api.get<Estudiante>(`/alumnos/${alumno.id}`).subscribe({
      next: full => {
        this.alumnoSeleccionado.set(full);
        this.perfilVisible.set(true);
      },
      error: () => {
        this.notify.error('Error', 'No se pudo cargar el perfil');
      },
    });
  }

  onPerfilGuardado(): void {
    this.cargarAlumnos();
  }

  openDialog(): void {
    this.showDialog.set(true);
    this.form = { nombre: '', apellido_paterno: '', apellido_materno: '', curp: '' };
  }

  exportCSV(): void  { this.exp.toCSV(this.alumnosDatos(), this.exportCols, 'alumnos'); }
  exportXLSX(): void { this.exp.toXLSX(this.alumnosDatos(), this.exportCols, 'Alumnos', 'alumnos'); }

  crearAlumno(): void {
    if (!this.form.nombre || !this.form.apellido_paterno || !this.form.curp) {
      this.notify.warning('Campos requeridos', 'Nombre, apellido paterno y CURP son obligatorios');
      return;
    }
    if (this.form.curp.length !== 18) {
      this.notify.warning('CURP inválida', 'La CURP debe tener exactamente 18 caracteres');
      return;
    }
    this.loading.set(true);
    const payload = {
      persona: {
        nombre: this.form.nombre,
        apellido_paterno: this.form.apellido_paterno,
        apellido_materno: this.form.apellido_materno || '',
        curp: this.form.curp.toUpperCase(),
      },
      plantel_id: this.ctx.plantel()?.id,
    };
    this.api.post<Estudiante>('/alumnos', payload).subscribe({
      next: (newAlumno) => {
        this.showDialog.set(false);
        this.loading.set(false);
        this.notify.success('Creado', `Matrícula: ${newAlumno.matricula}`);
        this.cargarAlumnos();
        this.abrirPerfil({ _original: newAlumno });
      },
      error: (e) => {
        this.loading.set(false);
        const detail = e.error?.detail ?? e.error?.message;
        const status = e.status;
        const msg = Array.isArray(detail) ? detail.map((d: any) => d.msg || d).join('; ')
                  : typeof detail === 'string' ? detail
                  : status === 409 ? 'Ya existe un alumno con esa CURP'
                  : status === 422 ? (e.error?.error || 'Datos inválidos')
                  : 'Error al crear alumno';
        this.notify.error('Error', msg);
      },
    });
  }
}
