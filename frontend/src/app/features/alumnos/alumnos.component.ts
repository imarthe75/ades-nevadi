import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { ToolbarModule } from 'primeng/toolbar';
import { DialogModule } from 'primeng/dialog';
import { TagModule } from 'primeng/tag';
import { TooltipModule } from 'primeng/tooltip';
import { MessageService } from 'primeng/api';
import { ToastModule } from 'primeng/toast';

import { ApiService } from '../../core/services/api.service';
import { ContextService } from '../../core/services/context.service';
import { ExportService } from '../../core/services/export.service';
import { ImportButtonComponent } from '../../shared/components/import-button/import-button.component';
import { AlumnoPerfilComponent } from '../../shared/components/alumno-perfil/alumno-perfil.component';
import { HelpButtonComponent } from '../../shared/components/help-button/help-button.component';
import type { Estudiante } from '../../core/models';

@Component({
  selector: 'app-alumnos',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    TableModule, ButtonModule, InputTextModule, ToolbarModule,
    DialogModule, TagModule, TooltipModule, ToastModule,
    ImportButtonComponent, AlumnoPerfilComponent, HelpButtonComponent,
  ],
  providers: [MessageService],
  template: `
    <p-toast />

    <!-- Drawer de perfil completo -->
    <app-alumno-perfil
      [(visible)]="perfilVisible"
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

    <p-table
      #dt
      [value]="alumnos()"
      [rows]="porPagina()"
      [totalRecords]="totalAlumnos()"
      [paginator]="true"
      [lazy]="true"
      [loading]="loadingTabla()"
      [rowsPerPageOptions]="[15,30,50,100]"
      (onLazyLoad)="onPage($event)"
      [showCurrentPageReport]="true"
      currentPageReportTemplate="{first}-{last} de {totalRecords} alumnos"
      styleClass="p-datatable-striped p-datatable-sm"
    >
      <ng-template pTemplate="caption">
        <input pInputText type="text" placeholder="Buscar por nombre, matrícula o CURP..."
          [value]="busqueda"
          (input)="onBusquedaChange($any($event.target).value)"
          style="width:320px" />
      </ng-template>

      <ng-template pTemplate="header">
        <tr>
          <th style="width:120px">Matrícula</th>
          <th>Apellidos</th>
          <th>Nombre(s)</th>
          <th style="width:170px">CURP</th>
          <th style="width:70px;text-align:center">NSS</th>
          <th style="width:60px"></th>
        </tr>
      </ng-template>

      <ng-template pTemplate="body" let-alumno>
        <tr [class.row-selectable]="true">
          <td><code>{{ alumno.matricula }}</code></td>
          <td>{{ alumno.persona?.apellido_paterno }} {{ alumno.persona?.apellido_materno }}</td>
          <td>{{ alumno.persona?.nombre }}</td>
          <td style="font-size:0.78rem;font-family:monospace">{{ alumno.persona?.curp }}</td>
          <td style="text-align:center">
            @if (alumno.nss) { <span class="nss-dot" pTooltip="NSS registrado" tooltipPosition="left">✓</span> }
          </td>
          <td>
            <p-button icon="pi pi-id-card" [rounded]="true" [text]="true"
              pTooltip="Ver perfil completo" (onClick)="abrirPerfil(alumno)" />
          </td>
        </tr>
      </ng-template>

      <ng-template pTemplate="emptymessage">
        <tr><td [colSpan]="6" style="text-align:center;padding:2rem;color:#94a3b8">
          Sin alumnos en el contexto seleccionado
        </td></tr>
      </ng-template>
    </p-table>

    <!-- Diálogo de alta rápida -->
    <p-dialog [(visible)]="showDialog" header="Nuevo Alumno" [modal]="true" [style]="{width:'400px'}">
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
        <p-button label="Crear alumno" (onClick)="crearAlumno()" [loading]="loading()" />
      </div>
    </p-dialog>
  `,
  styles: [`
    .page-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 1rem; }
    .subtitle { font-size: 0.82rem; color: var(--text-color-secondary); margin: 0; }
    code { font-size: 0.8rem; background: var(--surface-100); padding: .1rem .35rem; border-radius: 3px; }
    .nss-dot { color: var(--green-500); font-size: .9rem; margin-left: .3rem; cursor: default; }
    .dlg-lbl { display: block; font-size: .85rem; margin-bottom: .25rem; color: var(--text-color-secondary); }
    .dlg-note { font-size: .78rem; color: var(--text-color-secondary); margin: 0; }
    :host ::ng-deep .row-selectable { cursor: pointer; }
    :host ::ng-deep .row-selectable:hover { background: var(--surface-50) !important; }
  `],
})
export class AlumnosComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly ctx = inject(ContextService);
  private readonly msg = inject(MessageService);
  private readonly exp = inject(ExportService);

  alumnos           = signal<Estudiante[]>([]);
  totalAlumnos      = signal(0);
  pagina            = signal(1);
  porPagina         = signal(30);
  alumnoSeleccionado = signal<Estudiante | null>(null);
  perfilVisible     = false;
  showDialog        = false;
  loading           = signal(false);
  loadingTabla      = signal(false);
  busqueda          = '';
  private busquedaTimer: any;
  form = { nombre: '', apellido_paterno: '', apellido_materno: '', curp: '' };

  readonly recargar = () => this.cargarAlumnos();

  private readonly exportCols = [
    { field: 'matricula',               header: 'Matrícula' },
    { field: 'persona.apellido_paterno', header: 'Apellido Paterno' },
    { field: 'persona.apellido_materno', header: 'Apellido Materno' },
    { field: 'persona.nombre',           header: 'Nombre' },
    { field: 'persona.curp',             header: 'CURP' },
    { field: 'fecha_ingreso',            header: 'Fecha Ingreso' },
    { field: 'nss',                      header: 'NSS' },
    { field: 'beca_tipo',                header: 'Beca' },
  ];

  ngOnInit(): void { this.cargarAlumnos(); }

  onBusquedaChange(valor: string): void {
    this.busqueda = valor;
    clearTimeout(this.busquedaTimer);
    this.busquedaTimer = setTimeout(() => {
      this.pagina.set(1);
      this.cargarAlumnos();
    }, 350);
  }

  onPage(event: any): void {
    this.pagina.set(Math.floor(event.first / event.rows) + 1);
    this.porPagina.set(event.rows);
    this.cargarAlumnos();
  }

  cargarAlumnos(): void {
    const params: Record<string, any> = {
      pagina: this.pagina(),
      por_pagina: this.porPagina(),
    };
    const plantelId = this.ctx.plantel()?.id;
    if (plantelId) params['plantel_id'] = plantelId;
    if (this.busqueda.trim()) params['buscar'] = this.busqueda.trim();

    this.loadingTabla.set(true);
    this.api.get<{ data: Estudiante[]; total: number }>('/alumnos', params)
      .subscribe({
        next: resp => {
          this.alumnos.set(resp.data);
          this.totalAlumnos.set(resp.total);
          this.loadingTabla.set(false);
        },
        error: () => this.loadingTabla.set(false),
      });
  }

  abrirPerfil(alumno: Estudiante): void {
    // Cargar perfil completo con todos los campos complementarios
    this.api.get<Estudiante>(`/alumnos/${alumno.id}`).subscribe(full => {
      this.alumnoSeleccionado.set(full);
      this.perfilVisible = true;
    });
  }

  onPerfilGuardado(): void {
    this.cargarAlumnos();
  }

  openDialog(): void {
    this.showDialog = true;
    this.form = { nombre: '', apellido_paterno: '', apellido_materno: '', curp: '' };
  }

  exportCSV(): void  { this.exp.toCSV(this.alumnos(), this.exportCols, 'alumnos'); }
  exportXLSX(): void { this.exp.toXLSX(this.alumnos(), this.exportCols, 'Alumnos', 'alumnos'); }

  crearAlumno(): void {
    if (!this.form.nombre || !this.form.apellido_paterno || !this.form.curp) {
      this.msg.add({ severity: 'warn', summary: 'Campos requeridos', detail: 'Nombre, apellido paterno y CURP son obligatorios' });
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
        this.alumnos.update(a => [...a, newAlumno]);
        this.showDialog = false;
        this.loading.set(false);
        this.msg.add({ severity: 'success', summary: 'Creado', detail: `Matrícula: ${newAlumno.matricula}` });
        // Abrir perfil para completar datos
        this.abrirPerfil(newAlumno);
      },
      error: (e) => {
        this.loading.set(false);
        this.msg.add({ severity: 'error', summary: 'Error', detail: e.error?.detail ?? 'Error al crear' });
      },
    });
  }
}
