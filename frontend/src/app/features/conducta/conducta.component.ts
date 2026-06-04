/**
 * ConductaComponent — Reportes de conducta / disciplina.
 *
 * Lista de reportes con filtros por tipo de falta y estado de seguimiento.
 * Permite crear nuevo reporte con dialog.
 */
import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { SelectModule } from 'primeng/select';
import { TagModule } from 'primeng/tag';
import { ToastModule } from 'primeng/toast';
import { DialogModule } from 'primeng/dialog';
import { TextareaModule } from 'primeng/textarea';
import { InputTextModule } from 'primeng/inputtext';
import { ToggleSwitchModule } from 'primeng/toggleswitch';
import { MessageService } from 'primeng/api';

import { ApiService } from '../../core/services/api.service';
import { ContextService } from '../../core/services/context.service';
import { ExportService, ExportColumn } from '../../core/services/export.service';

interface ReporteConducta {
  id: string;
  estudiante_id: string;
  grupo_id: string;
  fecha_reporte: string;
  tipo_falta: 'LEVE' | 'GRAVE' | 'MUY_GRAVE';
  descripcion: string;
  medida_aplicada: string | null;
  compromiso_mejora: string | null;
  requiere_seguimiento: boolean;
}

type TagSeverity = 'success' | 'info' | 'warn' | 'danger' | 'secondary' | 'contrast';
const FALTA_SEVERITY: Record<string, TagSeverity> = {
  LEVE: 'info',
  GRAVE: 'warn',
  MUY_GRAVE: 'danger',
};

@Component({
  selector: 'app-conducta',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    TableModule, ButtonModule, SelectModule, TagModule, ToastModule,
    DialogModule, TextareaModule, InputTextModule, ToggleSwitchModule,
  ],
  providers: [MessageService],
  template: `
    <p-toast />

    <div class="page-header">
      <div>
        <h2>Reportes de Conducta</h2>
        <p class="subtitle">Incidentes disciplinarios y seguimiento</p>
      </div>
      <div style="display:flex;gap:0.5rem">
        <p-button label="CSV"   icon="pi pi-file"       severity="secondary" [text]="true" (onClick)="exportCSV()"  pTooltip="Exportar CSV" />
        <p-button label="Excel" icon="pi pi-file-excel" severity="secondary" [text]="true" (onClick)="exportXLSX()" pTooltip="Exportar Excel" />
        <p-button label="Nuevo reporte" icon="pi pi-plus" (onClick)="abrirDialog()" />
      </div>
    </div>

    <!-- Filtros -->
    <div class="filter-bar">
      <p-select
        [options]="tiposFalta"
        [(ngModel)]="filtroTipo"
        optionLabel="label"
        optionValue="value"
        placeholder="Tipo de falta"
        [showClear]="true"
        (onChange)="cargar()"
      />
      <p-select
        [options]="seguimientoOpts"
        [(ngModel)]="filtroSeguimiento"
        optionLabel="label"
        optionValue="value"
        placeholder="Estado seguimiento"
        [showClear]="true"
        (onChange)="cargar()"
      />
    </div>

    <!-- Tabla -->
    <p-table
      [value]="reportes()"
      [rows]="25"
      [paginator]="true"
      [showCurrentPageReport]="true"
      currentPageReportTemplate="{first}-{last} de {totalRecords}"
      styleClass="p-datatable-sm p-datatable-striped"
      [loading]="loading()"
    >
      <ng-template pTemplate="header">
        <tr>
          <th style="width:110px">Fecha</th>
          <th style="width:110px">Tipo</th>
          <th>Descripción</th>
          <th style="width:130px">Medida aplicada</th>
          <th style="width:100px;text-align:center">Seguimiento</th>
          <th style="width:80px">Acciones</th>
        </tr>
      </ng-template>

      <ng-template pTemplate="body" let-r>
        <tr>
          <td>{{ r.fecha_reporte | date:'dd/MM/yyyy' }}</td>
          <td>
            <p-tag
              [value]="r.tipo_falta"
              [severity]="faltaSeverity(r.tipo_falta)"
            />
          </td>
          <td style="max-width:300px; white-space:nowrap; overflow:hidden; text-overflow:ellipsis">
            {{ r.descripcion }}
          </td>
          <td style="font-size:0.8rem">{{ r.medida_aplicada || '—' }}</td>
          <td style="text-align:center">
            @if (r.requiere_seguimiento) {
              <p-tag value="Pendiente" severity="warn" />
            } @else {
              <p-tag value="Cerrado" severity="success" />
            }
          </td>
          <td>
            <p-button icon="pi pi-eye" [rounded]="true" [text]="true" [plain]="true" pTooltip="Ver detalle" />
          </td>
        </tr>
      </ng-template>

      <ng-template pTemplate="emptymessage">
        <tr><td [colSpan]="6" style="text-align:center;padding:2rem;color:#94A3B8">
          No hay reportes con estos filtros
        </td></tr>
      </ng-template>
    </p-table>

    <!-- Dialog nuevo reporte -->
    <p-dialog
      [(visible)]="showDialog"
      header="Nuevo Reporte de Conducta"
      [modal]="true"
      [style]="{ width: '520px' }"
      [closable]="true"
    >
      <div class="form-fields">
        <div class="field">
          <label>ID Alumno (UUID)</label>
          <input pInputText [(ngModel)]="form.estudiante_id" placeholder="UUID del alumno" style="width:100%" />
        </div>
        <div class="field">
          <label>ID Grupo (UUID)</label>
          <input pInputText [(ngModel)]="form.grupo_id" placeholder="UUID del grupo" style="width:100%" />
        </div>
        <div class="field">
          <label>ID Docente que reporta (UUID)</label>
          <input pInputText [(ngModel)]="form.reportado_por_id" placeholder="UUID del profesor" style="width:100%" />
        </div>
        <div class="field">
          <label>Tipo de falta</label>
          <p-select
            [options]="tiposFalta"
            [(ngModel)]="form.tipo_falta"
            optionLabel="label"
            optionValue="value"
            style="width:100%"
          />
        </div>
        <div class="field">
          <label>Descripción del incidente</label>
          <textarea
            pTextarea
            [(ngModel)]="form.descripcion"
            rows="3"
            style="width:100%"
            placeholder="Describe el incidente..."
          ></textarea>
        </div>
        <div class="field">
          <label>Medida aplicada</label>
          <input pInputText [(ngModel)]="form.medida_aplicada" style="width:100%" />
        </div>
        <div class="field">
          <label>Compromiso de mejora</label>
          <input pInputText [(ngModel)]="form.compromiso_mejora" style="width:100%" />
        </div>
        <div class="field-inline">
          <p-toggleSwitch [(ngModel)]="form.requiere_seguimiento" />
          <label>Requiere seguimiento</label>
        </div>
      </div>
      <ng-template pTemplate="footer">
        <p-button label="Cancelar" severity="secondary" [text]="true" (onClick)="showDialog = false" />
        <p-button label="Guardar" icon="pi pi-save" [loading]="saving()" (onClick)="guardar()" />
      </ng-template>
    </p-dialog>
  `,
  styles: [`
    .filter-bar { display: flex; gap: 0.75rem; margin-bottom: 1rem; flex-wrap: wrap; }
    .form-fields { display: flex; flex-direction: column; gap: 0.75rem; }
    .field { display: flex; flex-direction: column; gap: 0.25rem; }
    .field label { font-size: 0.82rem; font-weight: 600; color: var(--text-secondary); }
    .field-inline { display: flex; align-items: center; gap: 0.5rem; }
    .field-inline label { font-size: 0.85rem; }
  `],
})
export class ConductaComponent implements OnInit {
  private readonly api    = inject(ApiService);
  readonly ctx            = inject(ContextService);
  private readonly msg    = inject(MessageService);
  private readonly export = inject(ExportService);

  reportes = signal<ReporteConducta[]>([]);
  loading  = signal(false);
  saving   = signal(false);
  showDialog = false;

  filtroTipo: string | null = null;
  filtroSeguimiento: boolean | null = null;

  readonly tiposFalta = [
    { label: 'Leve', value: 'LEVE' },
    { label: 'Grave', value: 'GRAVE' },
    { label: 'Muy grave', value: 'MUY_GRAVE' },
  ];
  readonly seguimientoOpts = [
    { label: 'Pendiente seguimiento', value: true },
    { label: 'Cerrado', value: false },
  ];

  form = {
    estudiante_id: '',
    grupo_id: '',
    reportado_por_id: '',
    tipo_falta: 'LEVE' as 'LEVE' | 'GRAVE' | 'MUY_GRAVE',
    descripcion: '',
    medida_aplicada: '',
    compromiso_mejora: '',
    requiere_seguimiento: false,
  };

  ngOnInit(): void { this.cargar(); }

  cargar(): void {
    this.loading.set(true);
    const params: Record<string, any> = {};
    if (this.filtroTipo) params['tipo_falta'] = this.filtroTipo;
    if (this.filtroSeguimiento !== null) params['requiere_seguimiento'] = this.filtroSeguimiento;
    this.api.get<ReporteConducta[]>('/conducta', params)
      .subscribe({ next: r => { this.reportes.set(r); this.loading.set(false); }, error: () => this.loading.set(false) });
  }

  abrirDialog(): void {
    this.form = { estudiante_id: '', grupo_id: '', reportado_por_id: '', tipo_falta: 'LEVE', descripcion: '', medida_aplicada: '', compromiso_mejora: '', requiere_seguimiento: false };
    this.showDialog = true;
  }

  guardar(): void {
    if (!this.form.estudiante_id || !this.form.grupo_id || !this.form.descripcion) {
      this.msg.add({ severity: 'warn', summary: 'Campos requeridos', detail: 'Alumno, grupo y descripción son obligatorios' });
      return;
    }
    this.saving.set(true);
    const payload = { ...this.form, medida_aplicada: this.form.medida_aplicada || null, compromiso_mejora: this.form.compromiso_mejora || null };
    this.api.post<ReporteConducta>('/conducta', payload).subscribe({
      next: (r) => {
        this.reportes.update(list => [r, ...list]);
        this.showDialog = false;
        this.saving.set(false);
        this.msg.add({ severity: 'success', summary: 'Reporte registrado' });
      },
      error: (e) => {
        this.saving.set(false);
        this.msg.add({ severity: 'error', summary: 'Error', detail: e.error?.detail || 'Error al guardar' });
      },
    });
  }

  faltaSeverity(tipo: string): TagSeverity {
    return FALTA_SEVERITY[tipo] || 'info';
  }

  private readonly exportCols: ExportColumn[] = [
    { field: 'fecha_reporte',        header: 'Fecha', format: v => v ? new Date(v).toLocaleDateString('es-MX') : '' },
    { field: 'tipo_falta',           header: 'Tipo de falta' },
    { field: 'descripcion',          header: 'Descripción' },
    { field: 'medida_aplicada',      header: 'Medida aplicada', format: v => v || '' },
    { field: 'compromiso_mejora',    header: 'Compromiso mejora', format: v => v || '' },
    { field: 'requiere_seguimiento', header: 'Seguimiento', format: v => v ? 'Pendiente' : 'Cerrado' },
  ];

  exportCSV():  void { this.export.toCSV(this.reportes(), this.exportCols, 'conducta'); }
  exportXLSX(): void { this.export.toXLSX(this.reportes(), this.exportCols, 'Conducta', 'conducta'); }
}
