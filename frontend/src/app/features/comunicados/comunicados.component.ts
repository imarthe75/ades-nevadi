import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { DialogModule } from 'primeng/dialog';
import { SelectModule } from 'primeng/select';
import { InputTextModule } from 'primeng/inputtext';
import { TextareaModule } from 'primeng/textarea';
import { TooltipModule } from 'primeng/tooltip';
import { CheckboxModule } from 'primeng/checkbox';
import { MessageModule } from 'primeng/message';

import { ApiService } from '../../core/services/api.service';
import { ContextService } from '../../core/services/context.service';
import { ExportService, ExportColumn } from '../../core/services/export.service';

type TagSeverity = 'success' | 'info' | 'warn' | 'danger' | 'secondary' | 'contrast' | undefined;

interface Comunicado {
  id: string;
  titulo: string;
  contenido: string;
  tipo_comunicado: string;
  plantel_id: string | null;
  requiere_acuse: boolean;
  fecha_publicacion: string;
  fecha_vencimiento: string | null;
  total_acuses: number;
  acusado_por_mi: boolean;
  creado_por_nombre: string;
}

const TIPO_SEV: Record<string, TagSeverity> = {
  GENERAL: 'info',
  URGENTE: 'danger',
  ACADEMICO: 'success',
  ADMINISTRATIVO: 'secondary',
  SALUD: 'warn',
};

const TIPOS = [
  { label: 'General',        value: 'GENERAL' },
  { label: 'Urgente',        value: 'URGENTE' },
  { label: 'Académico',      value: 'ACADEMICO' },
  { label: 'Administrativo', value: 'ADMINISTRATIVO' },
  { label: 'Salud',          value: 'SALUD' },
];

@Component({
  selector: 'app-comunicados',
  standalone: true,
  imports: [
    CommonModule, DatePipe, FormsModule,
    ButtonModule, TableModule, TagModule, DialogModule,
    SelectModule, InputTextModule, TextareaModule,
    TooltipModule, CheckboxModule, MessageModule,
  ],
  template: `
    <div class="page-header">
      <div>
        <h2 class="page-title">Comunicados y Circulares</h2>
        <p class="page-subtitle">Mensajes institucionales del plantel</p>
      </div>
      <div class="page-actions">
        <p-button icon="pi pi-download" label="CSV"   severity="secondary" [text]="true" (onClick)="exportCSV()" />
        <p-button icon="pi pi-file-excel" label="Excel" severity="secondary" [text]="true" (onClick)="exportXLSX()" />
        <p-button icon="pi pi-plus" label="Nuevo comunicado" (onClick)="abrirNuevo()" />
      </div>
    </div>

    <!-- Filtro rápido tipo -->
    <div class="filter-bar" style="margin-bottom:1rem; display:flex; gap:.5rem; flex-wrap:wrap;">
      @for (t of tiposConTodos; track t.value) {
        <p-button
          [label]="t.label"
          [outlined]="filtroTipo !== t.value"
          size="small"
          severity="secondary"
          (onClick)="filtroTipo = t.value; cargar()"
        />
      }
    </div>

    <p-table
      [value]="comunicados()"
      [loading]="loading()"
      dataKey="id"
      [expandedRowKeys]="expandedRows"
      [paginator]="true" [rows]="15"
      styleClass="p-datatable-sm p-datatable-striped"
      filterDisplay="row"
    >
      <ng-template pTemplate="header">
        <tr>
          <th style="width:2.5rem"></th>
          <th pSortableColumn="tipo_comunicado">Tipo <p-sortIcon field="tipo_comunicado" /></th>
          <th pSortableColumn="titulo">Título <p-sortIcon field="titulo" /></th>
          <th>Plantel</th>
          <th pSortableColumn="fecha_publicacion">Fecha <p-sortIcon field="fecha_publicacion" /></th>
          <th>Acuses</th>
          <th>Acciones</th>
        </tr>
      </ng-template>

      <ng-template pTemplate="body" let-c let-expanded="expanded">
        <tr [class.comunicado-leido]="c.acusado_por_mi">
          <td>
            <p-button
              [icon]="expanded ? 'pi pi-chevron-down' : 'pi pi-chevron-right'"
              [text]="true" size="small"
              (onClick)="toggleExpand(c.id)"
            />
          </td>
          <td>
            <p-tag [value]="c.tipo_comunicado" [severity]="tipoSev(c.tipo_comunicado)" />
          </td>
          <td>
            <span [class.font-semibold]="!c.acusado_por_mi">{{ c.titulo }}</span>
            @if (!c.acusado_por_mi && c.requiere_acuse) {
              <i class="pi pi-exclamation-circle" style="color:var(--orange-400); margin-left:.4rem"
                 pTooltip="Requiere acuse de recibo"></i>
            }
          </td>
          <td>{{ c.plantel_id ? 'Plantel' : 'Todos' }}</td>
          <td>{{ c.fecha_publicacion | date:'dd/MM/yyyy' }}</td>
          <td>
            <span class="acuse-badge">{{ c.total_acuses }}</span>
          </td>
          <td>
            <div style="display:flex; gap:.25rem;">
              @if (!c.acusado_por_mi && c.requiere_acuse) {
                <p-button
                  icon="pi pi-check"
                  size="small" [text]="true" severity="success"
                  pTooltip="Acusar recibo"
                  (onClick)="acusarRecibo(c)"
                />
              }
              <p-button
                icon="pi pi-eye"
                size="small" [text]="true"
                pTooltip="Ver detalle"
                (onClick)="toggleExpand(c.id)"
              />
            </div>
          </td>
        </tr>
      </ng-template>

      <ng-template pTemplate="rowexpansion" let-c>
        <tr class="row-expansion">
          <td colspan="7">
            <div class="comunicado-body">
              <pre class="comunicado-contenido">{{ c.contenido }}</pre>
              <div class="comunicado-meta">
                <span>Publicado por: <strong>{{ c.creado_por_nombre }}</strong></span>
                @if (c.fecha_vencimiento) {
                  <span>Vigente hasta: <strong>{{ c.fecha_vencimiento | date:'dd/MM/yyyy' }}</strong></span>
                }
                @if (c.acusado_por_mi) {
                  <p-tag value="Ya acusé recibo" severity="success" icon="pi pi-check" />
                }
              </div>
            </div>
          </td>
        </tr>
      </ng-template>

      <ng-template pTemplate="emptymessage">
        <tr><td colspan="7" style="text-align:center; padding:2rem; color:var(--text-color-secondary)">
          No hay comunicados para los filtros seleccionados.
        </td></tr>
      </ng-template>
    </p-table>

    <!-- Dialog nuevo comunicado -->
    <p-dialog
      header="Nuevo comunicado"
      [visible]="showDialog()" (visibleChange)="showDialog.set($event)"
      [modal]="true"
      [style]="{width:'600px'}"
      [closable]="true"
    >
      <div class="form-grid">
        <div class="form-field full">
          <label>Título *</label>
          <input pInputText [(ngModel)]="form.titulo" placeholder="Título del comunicado" style="width:100%" />
        </div>
        <div class="form-field">
          <label>Tipo</label>
          <p-select [options]="tipos" [(ngModel)]="form.tipo_comunicado"
                    optionLabel="label" optionValue="value" styleClass="w-full" 
 [filter]="true" filterPlaceholder="Buscar..."/>
        </div>
        <div class="form-field">
          <label>Fecha vencimiento</label>
          <input pInputText type="date" [(ngModel)]="form.fecha_vencimiento" style="width:100%" />
        </div>
        <div class="form-field full">
          <label>Contenido *</label>
          <textarea pTextarea [(ngModel)]="form.contenido"
                    rows="6" style="width:100%; resize:vertical"
                    placeholder="Texto del comunicado..."></textarea>
        </div>
        <div class="form-field full" style="display:flex; align-items:center; gap:.5rem;">
          <p-checkbox [(ngModel)]="form.requiere_acuse" [binary]="true" inputId="acuse" />
          <label for="acuse">Requiere acuse de recibo</label>
        </div>
      </div>

      <ng-template pTemplate="footer">
        <p-button label="Cancelar" severity="secondary" [text]="true" (onClick)="showDialog.set(false)" />
        <p-button label="Publicar" icon="pi pi-send" (onClick)="publicar()" [loading]="saving()" />
      </ng-template>
    </p-dialog>
  `,
  styles: [`
    .page-header    { display:flex; justify-content:space-between; align-items:flex-start; margin-bottom:1.25rem; }
    .page-title     { font-size:1.25rem; font-weight:700; margin:0 0 .2rem; }
    .page-subtitle  { color:var(--text-color-secondary); font-size:.82rem; margin:0; }
    .page-actions   { display:flex; gap:.5rem; align-items:center; flex-wrap:wrap; }

    .acuse-badge {
      display:inline-flex; align-items:center; justify-content:center;
      background:var(--surface-200); border-radius:12px;
      font-size:.75rem; padding:0 .5rem; min-width:1.5rem; height:1.4rem;
    }

    .comunicado-leido td { opacity: 0.7; }

    .row-expansion td { background: var(--surface-50); }

    .comunicado-body {
      padding: .75rem 1rem;
    }
    .comunicado-contenido {
      font-family: inherit; white-space: pre-wrap; word-break: break-word;
      background: var(--surface-100); border-radius: 6px; padding: .75rem;
      font-size: .875rem; margin: 0 0 .75rem; max-height: 200px; overflow-y: auto;
    }
    .comunicado-meta {
      display: flex; gap: 1.5rem; align-items: center; font-size: .8rem;
      color: var(--text-color-secondary);
    }

    .form-grid   { display:grid; grid-template-columns:1fr 1fr; gap:.85rem; }
    .form-field  { display:flex; flex-direction:column; gap:.3rem; font-size:.875rem; }
    .form-field.full { grid-column: 1 / -1; }
    label { font-weight:500; font-size:.82rem; }
  `],
})
export class ComunicadosComponent implements OnInit {
  private readonly api    = inject(ApiService);
  readonly ctx            = inject(ContextService);
  private readonly exporter = inject(ExportService);

  comunicados = signal<Comunicado[]>([]);
  loading     = signal(false);
  saving      = signal(false);
  showDialog  = signal(false);
  expandedRows: Record<string, boolean> = {};
  filtroTipo  = '';

  tipos = TIPOS;
  tiposConTodos = [{ label: 'Todos', value: '' }, ...TIPOS];

  form = this.emptyForm();

  private readonly exportCols: ExportColumn[] = [
    { field: 'tipo_comunicado', header: 'Tipo' },
    { field: 'titulo',          header: 'Título' },
    { field: 'fecha_publicacion', header: 'Fecha publicación', format: (v: string) => v ? new Date(v).toLocaleDateString('es-MX') : '' },
    { field: 'total_acuses',    header: 'Acuses' },
    { field: 'creado_por_nombre', header: 'Publicado por' },
    { field: 'requiere_acuse',  header: 'Requiere acuse', format: (v: boolean) => v ? 'Sí' : 'No' },
  ];

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.loading.set(true);
    const params: Record<string, string | boolean> = { solo_vigentes: false };
    const plantel = this.ctx.plantel();
    if (plantel) params['plantel_id'] = plantel.id;
    if (this.filtroTipo) params['tipo'] = this.filtroTipo;

    this.api.get<Comunicado[]>('/comunicados', params).subscribe({
      next: (r) => { this.comunicados.set(r); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }

  tipoSev(tipo: string): TagSeverity { return TIPO_SEV[tipo] ?? 'info'; }

  toggleExpand(id: string): void {
    this.expandedRows = this.expandedRows[id] ? {} : { [id]: true };
  }

  acusarRecibo(c: Comunicado): void {
    this.api.put<{ ok: boolean }>(`/comunicados/${c.id}/acusar`, {}).subscribe(() => {
      this.comunicados.update(list => list.map(x => x.id === c.id ? { ...x, acusado_por_mi: true } : x));
    });
  }

  abrirNuevo(): void { this.form = this.emptyForm(); this.showDialog.set(true); }

  publicar(): void {
    if (!this.form.titulo.trim() || !this.form.contenido.trim()) return;
    this.saving.set(true);
    const plantel = this.ctx.plantel();
    const payload = {
      ...this.form,
      plantel_id: plantel?.id ?? null,
      fecha_vencimiento: this.form.fecha_vencimiento || null,
    };
    this.api.post('/comunicados', payload).subscribe({
      next: () => { this.showDialog.set(false); this.saving.set(false); this.cargar(); },
      error: () => this.saving.set(false),
    });
  }

  exportCSV(): void  { this.exporter.toCSV(this.comunicados(), this.exportCols, 'comunicados'); }
  exportXLSX(): void { this.exporter.toXLSX(this.comunicados(), this.exportCols, 'Comunicados', 'comunicados'); }

  private emptyForm() {
    return { titulo: '', contenido: '', tipo_comunicado: 'GENERAL', requiere_acuse: false, fecha_vencimiento: '' };
  }
}
