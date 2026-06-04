import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { InputTextModule } from 'primeng/inputtext';

import { ApiService } from '../../core/services/api.service';
import { ContextService } from '../../core/services/context.service';
import { ExportService, ExportColumn } from '../../core/services/export.service';
import type { Grupo } from '../../core/models';

@Component({
  selector: 'app-grupos',
  standalone: true,
  imports: [CommonModule, TableModule, ButtonModule, TagModule, InputTextModule],
  template: `
    <div class="page-header">
      <div>
        <h2>Grupos</h2>
        <p class="subtitle">Grupos académicos del plantel</p>
      </div>
      <div style="display:flex;gap:0.5rem">
        <p-button label="CSV"   icon="pi pi-file"       severity="secondary" [text]="true" (onClick)="exportCSV()"  pTooltip="Exportar CSV" />
        <p-button label="Excel" icon="pi pi-file-excel" severity="secondary" [text]="true" (onClick)="exportXLSX()" pTooltip="Exportar Excel" />
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
          <th pSortableColumn="nombre_grupo">Grupo <p-sortIcon field="nombre_grupo" /></th>
          <th>Nivel / Grado</th>
          <th style="width:110px;text-align:center">Capacidad</th>
          <th style="width:100px;text-align:center">Estado</th>
          <th style="width:80px">Acciones</th>
        </tr>
      </ng-template>

      <ng-template pTemplate="body" let-g>
        <tr>
          <td><strong>{{ g.nombre_grupo }}</strong></td>
          <td style="font-size:0.85rem">{{ g.grado_id }}</td>
          <td style="text-align:center">{{ g.capacidad_maxima }}</td>
          <td style="text-align:center">
            <p-tag [value]="g.is_active ? 'Activo' : 'Inactivo'" [severity]="g.is_active ? 'success' : 'secondary'" />
          </td>
          <td>
            <p-button icon="pi pi-users" [rounded]="true" [text]="true" [plain]="true" pTooltip="Ver alumnos" />
          </td>
        </tr>
      </ng-template>

      <ng-template pTemplate="emptymessage">
        <tr><td [colSpan]="5" style="text-align:center;padding:2rem;color:#94A3B8">Cargando grupos...</td></tr>
      </ng-template>
    </p-table>
  `,
  styles: [`
    .subtitle { font-size: 0.82rem; color: var(--text-color-secondary); margin: 0; }
  `],
})
export class GruposComponent implements OnInit {
  private readonly api    = inject(ApiService);
  private readonly ctx    = inject(ContextService);
  private readonly export = inject(ExportService);

  grupos = signal<Grupo[]>([]);

  private readonly exportCols: ExportColumn[] = [
    { field: 'nombre_grupo',    header: 'Grupo' },
    { field: 'grado_id',        header: 'Grado ID' },
    { field: 'capacidad_maxima', header: 'Capacidad' },
    { field: 'is_active',       header: 'Activo', format: v => v ? 'Sí' : 'No' },
  ];

  exportCSV():  void { this.export.toCSV(this.grupos(), this.exportCols, 'grupos'); }
  exportXLSX(): void { this.export.toXLSX(this.grupos(), this.exportCols, 'Grupos', 'grupos'); }

  ngOnInit(): void {
    const plantelId = this.ctx.plantel()?.id;
    const cicloId = this.ctx.ciclo()?.id;
    if (plantelId && cicloId) {
      this.api.get<Grupo[]>('/grupos', { plantel_id: plantelId, ciclo_vigente: true })
        .subscribe(g => this.grupos.set(g));
    }
  }
}
