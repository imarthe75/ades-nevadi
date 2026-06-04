import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { ToolbarModule } from 'primeng/toolbar';
import { DialogModule } from 'primeng/dialog';
import { MessageService } from 'primeng/api';
import { ToastModule } from 'primeng/toast';
import { SplitButtonModule } from 'primeng/splitbutton';

import { ApiService } from '../../core/services/api.service';
import { ContextService } from '../../core/services/context.service';
import { ExportService } from '../../core/services/export.service';
import type { Estudiante } from '../../core/models';

@Component({
  selector: 'app-alumnos',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    TableModule, ButtonModule, InputTextModule, ToolbarModule, DialogModule, ToastModule, SplitButtonModule,
  ],
  providers: [MessageService],
  template: `
    <p-toast />

    <div class="page-header">
      <h2>Alumnos</h2>
      <div style="display:flex;gap:0.5rem">
        <p-button label="CSV" icon="pi pi-file" severity="secondary" [text]="true" (onClick)="exportCSV()" pTooltip="Exportar CSV" />
        <p-button label="Excel" icon="pi pi-file-excel" severity="secondary" [text]="true" (onClick)="exportXLSX()" pTooltip="Exportar Excel" />
        <p-button label="Nuevo alumno" icon="pi pi-plus" (onClick)="openDialog()" />
      </div>
    </div>

    <p-table
      #dt
      [value]="alumnos()"
      [rows]="30"
      [paginator]="true"
      [globalFilterFields]="['persona.nombre', 'persona.apellido_paterno', 'matricula']"
      filterDisplay="row"
      [showCurrentPageReport]="true"
      currentPageReportTemplate="{first}-{last} de {totalRecords} alumnos"
      styleClass="p-datatable-striped"
    >
      <ng-template pTemplate="caption">
        <input
          pInputText
          type="text"
          placeholder="Buscar..."
          (input)="dt.filterGlobal($any($event.target).value, 'contains')"
          style="width:300px"
        />
      </ng-template>

      <ng-template pTemplate="header">
        <tr>
          <th pSortableColumn="matricula">Matrícula <p-sortIcon field="matricula" /></th>
          <th pSortableColumn="persona.nombre">Nombre <p-sortIcon field="persona.nombre" /></th>
          <th>Apellido</th>
          <th>CURP</th>
          <th style="width:80px">Acciones</th>
        </tr>
        <tr>
          <th><p-columnFilter field="matricula" type="text" matchMode="contains" /></th>
          <th><p-columnFilter field="persona.nombre" type="text" matchMode="contains" /></th>
          <th></th><th></th><th></th>
        </tr>
      </ng-template>

      <ng-template pTemplate="body" let-alumno>
        <tr>
          <td>{{ alumno.matricula }}</td>
          <td>{{ alumno.persona?.nombre }}</td>
          <td>{{ alumno.persona?.apellido_paterno }}</td>
          <td style="font-size:0.8rem">{{ alumno.persona?.curp }}</td>
          <td>
            <p-button icon="pi pi-eye" [rounded]="true" [text]="true" [plain]="true" pTooltip="Ver" />
            <p-button icon="pi pi-pencil" [rounded]="true" [text]="true" [plain]="true" pTooltip="Editar" styleClass="ml-1" />
          </td>
        </tr>
      </ng-template>

      <ng-template pTemplate="emptymessage">
        <tr><td [colSpan]="5">Cargando alumnos...</td></tr>
      </ng-template>
    </p-table>

    <p-dialog [(visible)]="showDialog" header="Nuevo Alumno" [modal]="true" [style]="{ width: '400px' }">
      <div style="display:flex;flex-direction:column;gap:1rem">
        <div>
          <label style="display:block;font-size:0.85rem;margin-bottom:0.25rem">Nombre</label>
          <input pInputText [(ngModel)]="form.nombre" style="width:100%" />
        </div>
        <div>
          <label style="display:block;font-size:0.85rem;margin-bottom:0.25rem">Apellido Paterno</label>
          <input pInputText [(ngModel)]="form.apellido_paterno" style="width:100%" />
        </div>
        <div>
          <label style="display:block;font-size:0.85rem;margin-bottom:0.25rem">CURP (18 caracteres)</label>
          <input pInputText [(ngModel)]="form.curp" style="width:100%;text-transform:uppercase" maxlength="18" />
        </div>
        <p-button label="Crear alumno" (onClick)="crearAlumno()" [loading]="loading()" />
      </div>
    </p-dialog>
  `,
  styles: [`
    .page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 1rem; }
  `],
})
export class AlumnosComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly ctx = inject(ContextService);
  private readonly msg = inject(MessageService);
  private readonly exp = inject(ExportService);

  private readonly exportCols = [
    { field: 'matricula',               header: 'Matrícula' },
    { field: 'persona.apellido_paterno', header: 'Apellido Paterno' },
    { field: 'persona.apellido_materno', header: 'Apellido Materno' },
    { field: 'persona.nombre',           header: 'Nombre' },
    { field: 'persona.curp',             header: 'CURP' },
    { field: 'fecha_ingreso',            header: 'Fecha Ingreso' },
  ];

  alumnos = signal<Estudiante[]>([]);
  showDialog = false;
  loading = signal(false);
  form = { nombre: '', apellido_paterno: '', curp: '' };

  ngOnInit(): void {
    const plantelId = this.ctx.plantel()?.id;
    if (plantelId) {
      this.api.get<Estudiante[]>('/alumnos', { plantel_id: plantelId })
        .subscribe(a => this.alumnos.set(a));
    }
  }

  openDialog(): void {
    this.showDialog = true;
    this.form = { nombre: '', apellido_paterno: '', curp: '' };
  }

  exportCSV(): void  { this.exp.toCSV(this.alumnos(), this.exportCols, 'alumnos'); }
  exportXLSX(): void { this.exp.toXLSX(this.alumnos(), this.exportCols, 'Alumnos', 'alumnos'); }

  crearAlumno(): void {
    if (!this.form.nombre || !this.form.apellido_paterno || !this.form.curp) {
      this.msg.add({ severity: 'warn', summary: 'Campos requeridos' });
      return;
    }
    this.loading.set(true);
    const payload = {
      persona: {
        nombre: this.form.nombre,
        apellido_paterno: this.form.apellido_paterno,
        curp: this.form.curp.toUpperCase(),
        apellido_materno: '',
      },
      plantel_id: this.ctx.plantel()?.id,
    };
    this.api.post<Estudiante>('/alumnos', payload).subscribe({
      next: (newAlumno) => {
        this.alumnos.update(a => [...a, newAlumno]);
        this.showDialog = false;
        this.loading.set(false);
        this.msg.add({ severity: 'success', summary: 'Creado', detail: `${newAlumno.matricula}` });
      },
      error: (e) => {
        this.loading.set(false);
        this.msg.add({ severity: 'error', summary: 'Error', detail: e.error?.detail || 'Error al crear' });
      },
    });
  }
}
