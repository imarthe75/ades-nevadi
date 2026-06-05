import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { InputTextModule } from 'primeng/inputtext';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { TooltipModule } from 'primeng/tooltip';
import { DialogModule } from 'primeng/dialog';
import { ToastModule } from 'primeng/toast';
import { MessageService } from 'primeng/api';

import { ApiService } from '../../core/services/api.service';
import { ContextService } from '../../core/services/context.service';
import { ExportService, ExportColumn } from '../../core/services/export.service';
import { ProfesorPerfilComponent } from '../../shared/components/profesor-perfil/profesor-perfil.component';
import { HelpButtonComponent } from '../../shared/components/help-button/help-button.component';
import type { Profesor } from '../../core/models';

@Component({
  selector: 'app-profesores',
  standalone: true,
  imports: [
    CommonModule, FormsModule, TableModule, InputTextModule, ButtonModule, TagModule, TooltipModule,
    DialogModule, ToastModule, ProfesorPerfilComponent, HelpButtonComponent,
  ],
  providers: [MessageService],
  template: `
    <p-toast />

    <!-- Drawer de perfil completo -->
    <app-profesor-perfil
      [(visible)]="perfilVisible"
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
        <p-button label="CSV"   icon="pi pi-file"       severity="secondary" [text]="true" (onClick)="exportCSV()"  pTooltip="Exportar CSV" />
        <p-button label="Excel" icon="pi pi-file-excel" severity="secondary" [text]="true" (onClick)="exportXLSX()" pTooltip="Exportar Excel" />
        <p-button label="Nuevo profesor" icon="pi pi-plus" (onClick)="openDialog()" />
      </div>
    </div>

    <p-table
      #dt
      [value]="profesores()"
      [rows]="porPagina()"
      [totalRecords]="totalProfesores()"
      [paginator]="true"
      [lazy]="true"
      [loading]="loadingTabla()"
      [rowsPerPageOptions]="[15,30,50]"
      (onLazyLoad)="onPage($event)"
      [showCurrentPageReport]="true"
      currentPageReportTemplate="{first}-{last} de {totalRecords} profesores"
      styleClass="p-datatable-sm p-datatable-striped"
    >
      <ng-template pTemplate="caption">
        <input pInputText type="text" placeholder="Buscar por nombre, empleado, RFC o cédula..."
          [value]="busqueda"
          (input)="onBusquedaChange($any($event.target).value)"
          style="width:320px" />
      </ng-template>

      <ng-template pTemplate="header">
        <tr>
          <th style="width:120px">Empleado</th>
          <th>Apellidos, Nombre</th>
          <th style="width:140px">RFC / Cédula</th>
          <th style="width:110px">Contrato / Turno</th>
          <th style="width:90px;text-align:center">Especialidad</th>
          <th style="width:70px"></th>
        </tr>
      </ng-template>

      <ng-template pTemplate="body" let-prof>
        <tr>
          <td><code>{{ prof.numero_empleado }}</code></td>
          <td>
            <strong>{{ prof.persona?.apellido_paterno }} {{ prof.persona?.apellido_materno }}</strong>,
            {{ prof.persona?.nombre }}
          </td>
          <td style="font-size:.78rem">
            @if (prof.rfc) { <div><i class="pi pi-id-card" style="opacity:.5;margin-right:.2rem"></i>{{ prof.rfc }}</div> }
            @if (prof.cedula_profesional) { <div style="color:#64748b">Céd: {{ prof.cedula_profesional }}</div> }
          </td>
          <td style="font-size:.8rem">
            @if (prof.tipo_contrato) { <p-tag [value]="prof.tipo_contrato" severity="secondary" /> }
            @if (prof.turno) { <div style="margin-top:.2rem;color:#64748b">{{ prof.turno }}</div> }
          </td>
          <td style="text-align:center;font-size:.8rem;color:#64748b">
            {{ prof.especialidad ?? '—' }}
          </td>
          <td>
            <p-button icon="pi pi-id-card" [rounded]="true" [text]="true"
              pTooltip="Ver expediente completo" (onClick)="abrirPerfil(prof)" />
          </td>
        </tr>
      </ng-template>

      <ng-template pTemplate="emptymessage">
        <tr><td [colSpan]="6" style="text-align:center;padding:2rem;color:#94a3b8">
          Sin profesores en el contexto seleccionado
        </td></tr>
      </ng-template>
    </p-table>

    <!-- Diálogo de alta rápida -->
    <p-dialog [(visible)]="showDialog" header="Nuevo Profesor" [modal]="true" [style]="{width:'400px'}">
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
          <input pInputText [(ngModel)]="form.tipo_contrato" placeholder="Ej: TIEMPO_COMPLETO, ASIGNATURA" style="width:100%" />
        </div>
        <p-button label="Crear profesor" (onClick)="crearProfesor()" [loading]="loading()" />
      </div>
    </p-dialog>
  `,
  styles: [`
    .page-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 1rem; }
    .subtitle { font-size: 0.82rem; color: var(--text-color-secondary); margin: 0; }
    code { font-size: 0.8rem; background: var(--surface-100); padding: .1rem .35rem; border-radius: 3px; }
    .dlg-lbl { display: block; font-size: .85rem; margin-bottom: .25rem; color: var(--text-color-secondary); }
  `],
})
export class ProfesoresComponent implements OnInit {
  private readonly api    = inject(ApiService);
  private readonly ctx    = inject(ContextService);
  private readonly exp    = inject(ExportService);
  private readonly msg    = inject(MessageService);

  profesores             = signal<Profesor[]>([]);
  totalProfesores        = signal(0);
  pagina                 = signal(1);
  porPagina              = signal(30);
  loadingTabla           = signal(false);
  busqueda               = '';
  private busquedaTimer: any;
  profesorSeleccionado   = signal<Profesor | null>(null);
  perfilVisible          = false;
  showDialog             = false;
  loading                = signal(false);
  form = { nombre: '', apellido_paterno: '', apellido_materno: '', numero_empleado: '', curp: '', tipo_contrato: '' };

  private readonly exportCols: ExportColumn[] = [
    { field: 'numero_empleado',           header: 'No. Empleado' },
    { field: 'persona.apellido_paterno',  header: 'Apellido Paterno' },
    { field: 'persona.apellido_materno',  header: 'Apellido Materno' },
    { field: 'persona.nombre',            header: 'Nombre' },
    { field: 'persona.curp',              header: 'CURP' },
    { field: 'rfc',                       header: 'RFC' },
    { field: 'nss',                       header: 'NSS' },
    { field: 'cedula_profesional',        header: 'Cédula Prof.' },
    { field: 'especialidad',              header: 'Especialidad' },
    { field: 'tipo_contrato',             header: 'Contrato' },
    { field: 'turno',                     header: 'Turno' },
    { field: 'nivel_estudios',            header: 'Nivel Estudios' },
    { field: 'is_active',                 header: 'Activo', format: (v: boolean) => v ? 'Sí' : 'No' },
  ];

  ngOnInit(): void { this.cargarProfesores(); }

  onBusquedaChange(valor: string): void {
    this.busqueda = valor;
    clearTimeout(this.busquedaTimer);
    this.busquedaTimer = setTimeout(() => { this.pagina.set(1); this.cargarProfesores(); }, 350);
  }

  onPage(event: any): void {
    this.pagina.set(Math.floor(event.first / event.rows) + 1);
    this.porPagina.set(event.rows);
    this.cargarProfesores();
  }

  cargarProfesores(): void {
    const params: Record<string, any> = {
      pagina: this.pagina(),
      por_pagina: this.porPagina(),
    };
    const plantelId = this.ctx.plantel()?.id;
    if (plantelId) params['plantel_id'] = plantelId;
    if (this.busqueda.trim()) params['buscar'] = this.busqueda.trim();

    this.loadingTabla.set(true);
    this.api.get<{ data: Profesor[]; total: number }>('/profesores', params).subscribe({
      next: resp => {
        this.profesores.set(resp.data);
        this.totalProfesores.set(resp.total);
        this.loadingTabla.set(false);
      },
      error: () => this.loadingTabla.set(false),
    });
  }

  abrirPerfil(prof: Profesor): void {
    this.api.get<Profesor>(`/profesores/${prof.id}`).subscribe(full => {
      this.profesorSeleccionado.set(full);
      this.perfilVisible = true;
    });
  }

  openDialog(): void {
    this.showDialog = true;
    const nextEmp = `EMP-${this.totalProfesores() + 101}`;
    this.form = { nombre: '', apellido_paterno: '', apellido_materno: '', numero_empleado: nextEmp, curp: '', tipo_contrato: 'TIEMPO_COMPLETO' };
  }

  crearProfesor(): void {
    if (!this.form.nombre || !this.form.apellido_paterno || !this.form.numero_empleado || !this.form.curp) {
      this.msg.add({ severity: 'warn', summary: 'Campos requeridos', detail: 'Nombre, apellido paterno, número de empleado y CURP son obligatorios' });
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
        this.profesores.update(p => [...p, newProf]);
        this.showDialog = false;
        this.loading.set(false);
        this.msg.add({ severity: 'success', summary: 'Creado', detail: `Profesor: ${newProf.numero_empleado}` });
        this.cargarProfesores();
      },
      error: (e) => {
        this.loading.set(false);
        this.msg.add({ severity: 'error', summary: 'Error', detail: e.error?.detail ?? 'Error al crear' });
      },
    });
  }

  exportCSV():  void { this.exp.toCSV(this.profesores(), this.exportCols, 'profesores'); }
  exportXLSX(): void { this.exp.toXLSX(this.profesores(), this.exportCols, 'Profesores', 'profesores'); }
}
