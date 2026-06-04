import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TableModule } from 'primeng/table';
import { InputTextModule } from 'primeng/inputtext';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';

import { ApiService } from '../../core/services/api.service';
import { ContextService } from '../../core/services/context.service';
import { ExportService, ExportColumn } from '../../core/services/export.service';
import type { Profesor } from '../../core/models';

@Component({
  selector: 'app-profesores',
  standalone: true,
  imports: [CommonModule, TableModule, InputTextModule, ButtonModule, TagModule],
  template: `
    <div class="page-header">
      <div>
        <h2>Profesores</h2>
        <p class="subtitle">Plantilla docente del plantel</p>
      </div>
      <div style="display:flex;gap:0.5rem">
        <p-button label="CSV" icon="pi pi-file" severity="secondary" [text]="true" (onClick)="exportCSV()" pTooltip="Exportar CSV" />
        <p-button label="Excel" icon="pi pi-file-excel" severity="secondary" [text]="true" (onClick)="exportXLSX()" pTooltip="Exportar Excel" />
      </div>
    </div>

    <p-table
      #dt
      [value]="profesores()"
      [rows]="25"
      [paginator]="true"
      [globalFilterFields]="['numero_empleado','persona.nombre','persona.apellido_paterno']"
      filterDisplay="row"
      [showCurrentPageReport]="true"
      currentPageReportTemplate="{first}-{last} de {totalRecords} profesores"
      styleClass="p-datatable-sm p-datatable-striped"
    >
      <ng-template pTemplate="caption">
        <input
          pInputText
          type="text"
          placeholder="Buscar profesor..."
          (input)="dt.filterGlobal($any($event.target).value, 'contains')"
          style="width:280px"
        />
      </ng-template>

      <ng-template pTemplate="header">
        <tr>
          <th pSortableColumn="numero_empleado" style="width:120px">Empleado <p-sortIcon field="numero_empleado" /></th>
          <th pSortableColumn="persona.apellido_paterno">Nombre completo <p-sortIcon field="persona.apellido_paterno" /></th>
          <th>CURP</th>
          <th style="width:100px;text-align:center">Activo</th>
          <th style="width:70px">Acciones</th>
        </tr>
        <tr>
          <th><p-columnFilter field="numero_empleado" type="text" matchMode="contains" /></th>
          <th><p-columnFilter field="persona.nombre" type="text" matchMode="contains" /></th>
          <th></th><th></th><th></th>
        </tr>
      </ng-template>

      <ng-template pTemplate="body" let-prof>
        <tr>
          <td><strong>{{ prof.numero_empleado }}</strong></td>
          <td>{{ prof.persona?.apellido_paterno }} {{ prof.persona?.apellido_materno }}, {{ prof.persona?.nombre }}</td>
          <td style="font-size:0.8rem;font-family:monospace">{{ prof.persona?.curp }}</td>
          <td style="text-align:center">
            <p-tag [value]="prof.is_active ? 'Activo' : 'Inactivo'" [severity]="prof.is_active ? 'success' : 'secondary'" />
          </td>
          <td>
            <p-button icon="pi pi-pencil" [rounded]="true" [text]="true" [plain]="true" pTooltip="Editar" />
          </td>
        </tr>
      </ng-template>

      <ng-template pTemplate="emptymessage">
        <tr><td [colSpan]="5" style="text-align:center;padding:2rem;color:#94A3B8">Cargando profesores...</td></tr>
      </ng-template>
    </p-table>
  `,
  styles: [`
    .subtitle { font-size: 0.82rem; color: var(--text-color-secondary); margin: 0; }
  `],
})
export class ProfesoresComponent implements OnInit {
  private readonly api    = inject(ApiService);
  private readonly ctx    = inject(ContextService);
  private readonly export = inject(ExportService);

  profesores = signal<Profesor[]>([]);

  private readonly exportCols: ExportColumn[] = [
    { field: 'numero_empleado',        header: 'No. Empleado' },
    { field: 'persona.apellido_paterno', header: 'Apellido Paterno' },
    { field: 'persona.apellido_materno', header: 'Apellido Materno' },
    { field: 'persona.nombre',           header: 'Nombre' },
    { field: 'persona.curp',             header: 'CURP' },
    { field: 'is_active',                header: 'Activo', format: v => v ? 'Sí' : 'No' },
  ];

  ngOnInit(): void {
    const plantelId = this.ctx.plantel()?.id;
    if (plantelId) {
      this.api.get<Profesor[]>('/profesores', { plantel_id: plantelId })
        .subscribe(p => this.profesores.set(p));
    }
  }

  exportCSV():  void { this.export.toCSV(this.profesores(), this.exportCols, 'profesores'); }
  exportXLSX(): void { this.export.toXLSX(this.profesores(), this.exportCols, 'Profesores', 'profesores'); }
}
