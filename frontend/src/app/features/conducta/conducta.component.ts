/**
 * ConductaComponent — Reportes de conducta, sanciones y planes de mejora.
 * SB-010: Lista/creación de reportes (existente)
 * SB-012: Sanción formal (dialog detalle, tab Sanción)
 * SB-013: Plan de mejora conductual (tab Plan de Mejora)
 * SB-014: Seguimiento del plan (tab Seguimientos)
 */
import { Component, OnDestroy, OnInit, inject, signal, computed, effect, ChangeDetectionStrategy} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject, takeUntil } from 'rxjs';
import { ButtonModule } from 'primeng/button';
import { SelectModule } from 'primeng/select';
import { AutoCompleteModule } from 'primeng/autocomplete';
import { TagModule } from 'primeng/tag';
import { DialogModule } from 'primeng/dialog';
import { TextareaModule } from 'primeng/textarea';
import { InputTextModule } from 'primeng/inputtext';
import { ToggleSwitchModule } from 'primeng/toggleswitch';
import { TabsModule } from 'primeng/tabs';
import { DatePickerModule } from 'primeng/datepicker';
import { DividerModule } from 'primeng/divider';
import { CheckboxModule } from 'primeng/checkbox';
import { TableModule } from 'primeng/table';

import { ApiService } from '../../core/services/api.service';
import { ContextService } from '../../core/services/context.service';
import { ExportService, ExportColumn } from '../../core/services/export.service';
import type { Grupo } from '../../core/models';
import { grupoLabel } from '../../core/models';
import { ApexNotificationService, ApexTimelineComponent } from 'apex-component-library';
import { InteractiveGridComponent, ColumnConfig } from '../../shared/components/interactive-grid/interactive-grid.component';
import { AdesFormatDirective } from '../../shared/directives/ades-format.directive';

type TagSeverity = 'success' | 'info' | 'warn' | 'danger' | 'secondary' | 'contrast';
type GrupoConLabel = Grupo & { _label: string };

const FALTA_SEVERITY: Record<string, TagSeverity> = {
  LEVE: 'info', GRAVE: 'warn', MUY_GRAVE: 'danger',
};

const SANCION_OPTS = [
  { label: 'Amonestación verbal',   value: 'AMONESTACION_VERBAL' },
  { label: 'Amonestación escrita',  value: 'AMONESTACION_ESCRITA' },
  { label: 'Citatorio a padres',    value: 'CITATORIO_PADRES' },
  { label: 'Suspensión 1 día',      value: 'SUSPENSION_1_DIA' },
  { label: 'Suspensión 3 días',     value: 'SUSPENSION_3_DIAS' },
  { label: 'Suspensión 5 días',     value: 'SUSPENSION_5_DIAS' },
  { label: 'Condicional',           value: 'CONDICIONAL' },
  { label: 'Expulsión',             value: 'EXPULSION' },
];
const AVANCE_OPTS = [
  { label: 'Sin avance',     value: 'SIN_AVANCE' },
  { label: 'Parcial',        value: 'PARCIAL' },
  { label: 'Satisfactorio',  value: 'SATISFACTORIO' },
  { label: 'Excelente',      value: 'EXCELENTE' },
];
const ESTADO_PLAN_OPTS = [
  { label: 'Activo',       value: 'ACTIVO' },
  { label: 'En proceso',   value: 'EN_PROCESO' },
  { label: 'Cumplido',     value: 'CUMPLIDO' },
  { label: 'Incumplido',   value: 'INCUMPLIDO' },
  { label: 'Cancelado',    value: 'CANCELADO' },
];
const MEDIO_NOTIF_OPTS = [
  { label: 'Presencial', value: 'PRESENCIAL' },
  { label: 'Teléfono',   value: 'TELEFONO' },
  { label: 'Email',      value: 'EMAIL' },
  { label: 'WhatsApp',   value: 'WHATSAPP' },
];

interface Compromiso {
  texto: string;
  plazo_dias: number | null;
  cumplido: boolean;
}

@Component({
  selector: 'app-conducta',
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
  imports: [
    AdesFormatDirective,
    CommonModule, FormsModule,
    ButtonModule, SelectModule, AutoCompleteModule, TagModule,
    DialogModule, TextareaModule, InputTextModule, ToggleSwitchModule,
    TabsModule, DatePickerModule, DividerModule, CheckboxModule, TableModule,
    InteractiveGridComponent, ApexTimelineComponent,
  ],
  template: `
    <div class="page-header">
      <div>
        <h2>Reportes de Conducta</h2>
        <p class="subtitle">Incidentes disciplinarios, sanciones y planes de mejora</p>
      </div>
      <div style="display:flex;gap:0.5rem">
        <p-button label="CSV"   icon="pi pi-file"       severity="secondary" [text]="true" (onClick)="exportCSV()"  />
        <p-button label="Excel" icon="pi pi-file-excel" severity="secondary" [text]="true" (onClick)="exportXLSX()" />
        <p-button label="Nuevo reporte" icon="pi pi-plus" data-testid="btn-nueva-sancion" (onClick)="abrirNuevoReporte()" />
      </div>
    </div>

    <!-- Filtros de dominio: tipo de falta y seguimiento (la cascada vive en el topbar) -->
    <div class="filter-bar">
      <p-select [options]="tiposFalta" [(ngModel)]="filtroTipo" optionLabel="label" optionValue="value"
                placeholder="Tipo de falta" [showClear]="true" (onChange)="cargar()" [filter]="true"
                styleClass="filter-select" />
      <p-select [options]="seguimientoOpts" [(ngModel)]="filtroSeguimiento" optionLabel="label" optionValue="value"
                placeholder="Estado seguimiento" [showClear]="true" (onChange)="cargar()" [filter]="true"
                styleClass="filter-select" />
    </div>

    <!-- Búsqueda rápida -->
    <div style="display:flex; gap:0.75rem; align-items:center; margin-top:0.5rem; margin-bottom:1rem; flex-wrap:wrap">
      <div style="flex:1; min-width:250px">
        <input
          pInputText
          type="text"
          placeholder="Buscar por alumno o descripción..."
          [(ngModel)]="busqueda"
          style="width:100%"
        />
      </div>
    </div>

    <app-interactive-grid
      [data]="reportesFiltrados()"
      [columns]="columnasReportes"
      [loading]="loading()"
      (rowSelected)="abrirDetalle($event)"
    />

    <!-- ═══════════════════════════════════════════════════════════
         DIALOG: Nuevo reporte
    ═══════════════════════════════════════════════════════════════ -->
    <p-dialog [(visible)]="showNuevoDialog" header="Nuevo Reporte de Conducta"
              [modal]="true" [style]="{ width: '520px' }" [closable]="true">
      <div class="form-fields">
        <div class="field">
          <label>Alumno *</label>
          <p-autoComplete [(ngModel)]="form._alumnoObj" [suggestions]="alumnosSugg()"
            (completeMethod)="buscarAlumnos($event)" optionLabel="nombre_completo" [forceSelection]="true"
            placeholder="Buscar alumno por nombre o matrícula..." styleClass="w-full"
            (onSelect)="onAlumnoSelect($event.value)" />
        </div>
        <div class="field">
          <label>Grupo *</label>
          <p-select [options]="gruposOpts()" [(ngModel)]="form._grupoObj" optionLabel="_label"
            placeholder="Seleccionar grupo" [showClear]="true" styleClass="w-full"
            (onChange)="onGrupoSelect($event.value)" [filter]="true" />
        </div>
        <div class="field">
          <label>Tipo de falta</label>
          <p-select [options]="tiposFalta" [(ngModel)]="form.tipo_falta" optionLabel="label"
            optionValue="value" style="width:100%" [filter]="true" />
        </div>
        <div class="field">
          <label>Descripción del incidente</label>
          <textarea pTextarea [(ngModel)]="form.descripcion" rows="3" style="width:100%"
            placeholder="Describe el incidente..."></textarea>
        </div>
        <div class="field">
          <label>Medida aplicada</label>
          <input pInputText [(ngModel)]="form.medida_aplicada" style="width:100%" />
        </div>
        <div class="field-inline">
          <p-toggleSwitch [(ngModel)]="form.requiere_seguimiento" />
          <label>Requiere seguimiento</label>
        </div>
      </div>
      <ng-template pTemplate="footer">
        <p-button label="Cancelar" severity="secondary" [text]="true" (onClick)="showNuevoDialog = false" />
        <p-button label="Guardar" icon="pi pi-save" [loading]="saving()" (onClick)="guardar()" />
      </ng-template>
    </p-dialog>

    <!-- ═══════════════════════════════════════════════════════════
         DIALOG: Detalle completo (SB-012 / SB-013 / SB-014)
    ═══════════════════════════════════════════════════════════════ -->
    <p-dialog [(visible)]="showDetalleDialog" [header]="detalleHeader()"
              [modal]="true" [style]="{ width: '700px', maxHeight: '85vh' }"
              [closable]="true" [draggable]="false" (onHide)="onDetalleClosed()">

      <div *ngIf="loadingDetalle()" style="text-align:center;padding:2rem">
        <i class="pi pi-spin pi-spinner" style="font-size:2rem"></i>
      </div>

      <div *ngIf="!loadingDetalle() && detalle()">
        <p-tabs [(value)]="detalleTab">
          <p-tablist>
            <p-tab value="0">Reporte</p-tab>
            <p-tab value="1">
              Sanción
              <p-tag *ngIf="detalle()?.sancion" [value]="detalle()!.sancion!.estado"
                     severity="warn" styleClass="ml-1" style="font-size:0.7rem" />
            </p-tab>
            <p-tab value="2">
              Plan de mejora
              <p-tag *ngIf="detalle()?.plan_mejora" [value]="detalle()!.plan_mejora!.estado"
                     severity="info" styleClass="ml-1" style="font-size:0.7rem" />
            </p-tab>
            <p-tab value="3" [disabled]="!detalle()?.plan_mejora">
              Seguimientos
              @if (detalle()?.seguimientos?.length) {
                <p-tag [value]="'' + detalle()!.seguimientos!.length" severity="secondary" styleClass="ml-1" style="font-size:0.7rem" />
              }
            </p-tab>
          </p-tablist>
          <p-tabpanels>

            <!-- TAB 0: Detalle del reporte -->
            <p-tabpanel value="0">
              <div class="detail-grid">
                <div class="detail-item">
                  <span class="detail-label">Alumno</span>
                  <span class="detail-value">{{ detalle()?.reporte?.nombre_alumno ?? '—' }}</span>
                </div>
                <div class="detail-item">
                  <span class="detail-label">Matrícula</span>
                  <span class="detail-value">{{ detalle()?.reporte?.matricula ?? '—' }}</span>
                </div>
                <div class="detail-item">
                  <span class="detail-label">Fecha</span>
                  <span class="detail-value">{{ detalle()?.reporte?.fecha_reporte ?? '—' }}</span>
                </div>
                <div class="detail-item">
                  <span class="detail-label">Tipo de falta</span>
                  <span>
                    <p-tag [value]="detalle()?.reporte?.tipo_falta ?? ''"
                           [severity]="faltaSeverity(detalle()?.reporte?.tipo_falta ?? '')" />
                  </span>
                </div>
                <div class="detail-item full-width">
                  <span class="detail-label">Descripción</span>
                  <span class="detail-value">{{ detalle()?.reporte?.descripcion ?? '—' }}</span>
                </div>
                <div class="detail-item full-width">
                  <span class="detail-label">Medida aplicada</span>
                  <span class="detail-value">{{ detalle()?.reporte?.medida_aplicada ?? 'No especificada' }}</span>
                </div>
              </div>
            </p-tabpanel>

            <!-- TAB 1: Sanción Formal (SB-012) -->
            <p-tabpanel value="1">
              <!-- Sanción ya existe -->
              @if (detalle()?.sancion) {
                <div class="detail-grid">
                  <div class="detail-item">
                    <span class="detail-label">Tipo de sanción</span>
                    <span class="detail-value">{{ sancionLabel(detalle()!.sancion!.tipo_sancion) }}</span>
                  </div>
                  <div class="detail-item">
                    <span class="detail-label">Estado</span>
                    <p-tag [value]="detalle()!.sancion!.estado" severity="warn" />
                  </div>
                  <div class="detail-item">
                    <span class="detail-label">Fecha de sanción</span>
                    <span class="detail-value">{{ detalle()!.sancion!.fecha_sancion ?? '—' }}</span>
                  </div>
                  <div class="detail-item">
                    <span class="detail-label">Notificado a padres</span>
                    <span class="detail-value">{{ detalle()!.sancion!.notificado_padres ? 'Sí' : 'No' }}</span>
                  </div>
                </div>
                <p-divider />
                <!-- Actualizar estado/notificación -->
                <div style="display:flex;flex-direction:column;gap:0.75rem">
                  <h3 style="margin:0;font-size:0.9rem;color:var(--text-secondary)">Actualizar estado</h3>
                  <div class="field-row">
                    <label>Estado</label>
                    <p-select [options]="estadosSancion" [(ngModel)]="sancionUpdate.estado"
                              optionLabel="label" optionValue="value" style="width:200px" />
                  </div>
                  <div class="field-row">
                    <label>Notificado a padres</label>
                    <p-toggleSwitch [(ngModel)]="sancionUpdate.notificado_padres" />
                  </div>
                  @if (sancionUpdate.notificado_padres) {
                    <div class="field-row">
                      <label>Medio de notificación</label>
                      <p-select [options]="medioNotifOpts" [(ngModel)]="sancionUpdate.medio_notificacion"
                                optionLabel="label" optionValue="value" style="width:200px" />
                    </div>
                  }
                  <p-button label="Actualizar sanción" icon="pi pi-check" [loading]="savingSancion()"
                            (onClick)="actualizarSancion()" [style]="{ 'align-self': 'flex-start' }" />
                </div>
              }
              <!-- Sin sanción — formulario para aplicar -->
              @else {
                @if (puedeAplicarSancion()) {
                  <div style="display:flex;flex-direction:column;gap:0.75rem">
                    <h3 style="margin:0;font-size:0.9rem">Aplicar sanción formal</h3>
                    <div class="field">
                      <label>Tipo de sanción *</label>
                      <p-select [options]="sancionOpts" [(ngModel)]="sancionForm.tipo_sancion"
                                optionLabel="label" optionValue="value" style="width:100%" />
                    </div>
                    <div class="field">
                      <label>Justificación * (mín. 20 caracteres)</label>
                      <textarea pTextarea [(ngModel)]="sancionForm.justificacion" rows="3"
                                style="width:100%" placeholder="Justificación detallada..."></textarea>
                    </div>
                    <div class="field-row">
                      <label>Fecha de sanción</label>
                      <p-datepicker [(ngModel)]="sancionForm.fecha_sancion" dateFormat="yy-mm-dd"
                                    [showIcon]="true" style="width:200px" />
                    </div>
                    <div class="field-row">
                      <label>Notificar a padres</label>
                      <p-toggleSwitch [(ngModel)]="sancionForm.notificado_padres" />
                    </div>
                    <p-button label="Aplicar sanción" icon="pi pi-gavel" [loading]="savingSancion()"
                              (onClick)="aplicarSancion()" [style]="{ 'align-self': 'flex-start' }" />
                  </div>
                } @else {
                  <div style="text-align:center;padding:2rem;color:var(--text-secondary)">
                    <i class="pi pi-lock" style="font-size:2rem;display:block;margin-bottom:0.5rem"></i>
                    Solo el Director o Administrador puede aplicar sanciones formales.
                  </div>
                }
              }
            </p-tabpanel>

            <!-- TAB 2: Plan de Mejora (SB-013) -->
            <p-tabpanel value="2">
              @if (detalle()?.plan_mejora) {
                <div class="detail-grid">
                  <div class="detail-item">
                    <span class="detail-label">Estado del plan</span>
                    <p-tag [value]="detalle()!.plan_mejora!.estado" severity="info" />
                  </div>
                  <div class="detail-item">
                    <span class="detail-label">Elaborado</span>
                    <span class="detail-value">{{ detalle()!.plan_mejora!.fecha_elaboracion }}</span>
                  </div>
                  <div class="detail-item full-width">
                    <span class="detail-label">Objetivo general</span>
                    <span class="detail-value">{{ detalle()!.plan_mejora!.objetivo_general }}</span>
                  </div>
                </div>
                <p-divider />
                <div style="display:flex;gap:1rem;flex-wrap:wrap">
                  <p-tag [value]="'Alumno ' + (detalle()!.plan_mejora!.firmado_alumno ? '✓' : '○')"
                         [severity]="detalle()!.plan_mejora!.firmado_alumno ? 'success' : 'secondary'" />
                  <p-tag [value]="'Padre ' + (detalle()!.plan_mejora!.firmado_padre ? '✓' : '○')"
                         [severity]="detalle()!.plan_mejora!.firmado_padre ? 'success' : 'secondary'" />
                  <p-tag [value]="'Director ' + (detalle()!.plan_mejora!.firmado_director ? '✓' : '○')"
                         [severity]="detalle()!.plan_mejora!.firmado_director ? 'success' : 'secondary'" />
                </div>
                <p-divider />
                <!-- Actualizar firmas/estado -->
                <div style="display:flex;flex-direction:column;gap:0.75rem">
                  <h3 style="margin:0;font-size:0.9rem;color:var(--text-secondary)">Actualizar plan</h3>
                  <div class="firmas-row">
                    <label><p-checkbox [(ngModel)]="planUpdate.firmado_alumno" [binary]="true" />
                      Firmado por alumno</label>
                    <label><p-checkbox [(ngModel)]="planUpdate.firmado_padre" [binary]="true" />
                      Firmado por padre</label>
                    <label><p-checkbox [(ngModel)]="planUpdate.firmado_director" [binary]="true" />
                      Firmado por director</label>
                  </div>
                  <div class="field-row">
                    <label>Estado</label>
                    <p-select [options]="estadoPlanOpts" [(ngModel)]="planUpdate.estado"
                              optionLabel="label" optionValue="value" style="width:200px" />
                  </div>
                  <p-button label="Actualizar plan" icon="pi pi-check" [loading]="savingPlan()"
                            (onClick)="actualizarPlan()" [style]="{ 'align-self': 'flex-start' }" />
                </div>
              }
              @else {
                @if (puedeGestionarPlan()) {
                  <div style="display:flex;flex-direction:column;gap:0.75rem">
                    <h3 style="margin:0;font-size:0.9rem">Crear Plan de Mejora Conductual</h3>
                    <div class="field">
                      <label>Objetivo general * (mín. 20 caracteres)</label>
                      <textarea pTextarea [(ngModel)]="planForm.objetivo_general" rows="3"
                                style="width:100%" placeholder="Describe el objetivo del plan..."></textarea>
                    </div>
                    <p-divider align="left"><span style="font-size:0.8rem;font-weight:600">Compromisos del alumno</span></p-divider>
                    <div *ngFor="let c of planForm.compromisos_alumno; let i = index" class="compromiso-row">
                      <input pInputText [(ngModel)]="c.texto" placeholder="Compromiso..." style="flex:1" />
                      <input pInputText [(ngModel)]="c.plazo_dias" type="number" placeholder="Días" style="width:70px" />
                      <p-button icon="pi pi-trash" severity="danger" [text]="true" ariaLabel="Eliminar compromiso del alumno" (onClick)="eliminarCompromiso('alumno', i)" />
                    </div>
                    <p-button icon="pi pi-plus" label="Agregar compromiso alumno" severity="secondary" [text]="true"
                              (onClick)="agregarCompromiso('alumno')" [style]="{ 'align-self': 'flex-start' }" />

                    <p-divider align="left"><span style="font-size:0.8rem;font-weight:600">Compromisos del padre</span></p-divider>
                    <div *ngFor="let c of planForm.compromisos_padre; let i = index" class="compromiso-row">
                      <input pInputText [(ngModel)]="c.texto" placeholder="Compromiso..." style="flex:1" />
                      <p-button icon="pi pi-trash" severity="danger" [text]="true" ariaLabel="Eliminar compromiso del padre" (onClick)="eliminarCompromiso('padre', i)" />
                    </div>
                    <p-button icon="pi pi-plus" label="Agregar compromiso padre" severity="secondary" [text]="true"
                              (onClick)="agregarCompromiso('padre')" [style]="{ 'align-self': 'flex-start' }" />

                    <p-divider align="left"><span style="font-size:0.8rem;font-weight:600">Compromisos de la escuela</span></p-divider>
                    <div *ngFor="let c of planForm.compromisos_escuela; let i = index" class="compromiso-row">
                      <input pInputText [(ngModel)]="c.texto" placeholder="Compromiso..." style="flex:1" />
                      <p-button icon="pi pi-trash" severity="danger" [text]="true" ariaLabel="Eliminar compromiso de la escuela" (onClick)="eliminarCompromiso('escuela', i)" />
                    </div>
                    <p-button icon="pi pi-plus" label="Agregar compromiso escuela" severity="secondary" [text]="true"
                              (onClick)="agregarCompromiso('escuela')" [style]="{ 'align-self': 'flex-start' }" />

                    <div class="field-row">
                      <label>Primer seguimiento</label>
                      <p-datepicker [(ngModel)]="planForm.fecha_primer_seguimiento" dateFormat="yy-mm-dd"
                                    [showIcon]="true" style="width:200px" />
                    </div>
                    <p-button label="Crear plan de mejora" icon="pi pi-file-plus" [loading]="savingPlan()"
                              (onClick)="crearPlan()" [style]="{ 'align-self': 'flex-start' }" />
                  </div>
                } @else {
                  <div style="text-align:center;padding:2rem;color:var(--text-secondary)">
                    <i class="pi pi-lock" style="font-size:2rem;display:block;margin-bottom:0.5rem"></i>
                    Solo el Coordinador, Director o Administrador puede crear planes de mejora.
                  </div>
                }
              }
            </p-tabpanel>

            <!-- TAB 3: Seguimientos (SB-014) -->
            <p-tabpanel value="3">
              <!-- Lista de seguimientos como timeline -->
              @if (detalle()?.seguimientos?.length) {
                <apex-timeline
                  [value]="seguimientosTimeline()"
                  title="Seguimientos del Plan"
                  [showRelativeTime]="false"
                  [showActor]="true"
                />
                <p-divider />
              }
              <!-- Formulario nuevo seguimiento -->
              @if (puedeGestionarPlan()) {
                <div style="display:flex;flex-direction:column;gap:0.75rem">
                  <h3 style="margin:0;font-size:0.9rem">Registrar seguimiento</h3>
                  <div class="field-row">
                    <label>Fecha</label>
                    <p-datepicker [(ngModel)]="segForm.fecha_seguimiento" dateFormat="yy-mm-dd"
                                  [showIcon]="true" style="width:200px" />
                  </div>
                  <div class="field-row">
                    <label>Avance</label>
                    <p-select [options]="avanceOpts" [(ngModel)]="segForm.avance"
                              optionLabel="label" optionValue="value" style="width:200px" />
                  </div>
                  <div class="field">
                    <label>Descripción * (mín. 20 caracteres)</label>
                    <textarea pTextarea [(ngModel)]="segForm.descripcion" rows="3" style="width:100%"
                              placeholder="Describe el avance observado..."></textarea>
                  </div>
                  <div class="field-row">
                    <label>Nuevo estado del plan</label>
                    <p-select [options]="estadoPlanOpts" [(ngModel)]="segForm.nuevo_estado_plan"
                              optionLabel="label" optionValue="value" style="width:200px"
                              [showClear]="true" placeholder="Sin cambio" />
                  </div>
                  <p-button label="Registrar seguimiento" icon="pi pi-plus-circle" [loading]="savingSeg()"
                            (onClick)="agregarSeguimiento()" [style]="{ 'align-self': 'flex-start' }" />
                </div>
              }
            </p-tabpanel>

          </p-tabpanels>
        </p-tabs>
      </div>
    </p-dialog>
  `,
  styles: [`
    .filter-bar { display: flex; gap: 0.75rem; margin-bottom: 1rem; flex-wrap: wrap; }
    .filter-select { min-width: 220px; }
    .form-fields { display: flex; flex-direction: column; gap: 0.75rem; }
    .field { display: flex; flex-direction: column; gap: 0.25rem; }
    .field label { font-size: 0.82rem; font-weight: 600; color: var(--text-secondary); }
    .field-inline { display: flex; align-items: center; gap: 0.5rem; }
    .field-inline label { font-size: 0.85rem; }
    .field-row { display: flex; align-items: center; gap: 0.75rem; }
    .field-row label { font-size: 0.82rem; font-weight: 600; color: var(--text-secondary); min-width: 160px; }
    .detail-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 0.75rem 1.5rem; }
    .detail-item { display: flex; flex-direction: column; gap: 0.2rem; }
    .detail-item.full-width { grid-column: 1 / -1; }
    .detail-label { font-size: 0.75rem; font-weight: 600; color: var(--text-secondary); text-transform: uppercase; letter-spacing: 0.05em; }
    .detail-value { font-size: 0.9rem; }
    .firmas-row { display: flex; gap: 1.5rem; flex-wrap: wrap; }
    .firmas-row label { display: flex; align-items: center; gap: 0.4rem; font-size: 0.85rem; cursor: pointer; }
    .compromiso-row { display: flex; gap: 0.5rem; align-items: center; }
    .seguimientos-list { display: flex; flex-direction: column; gap: 0.75rem; margin-bottom: 1rem; }
    .seguimiento-card { background: var(--surface-ground); border-radius: 6px; padding: 0.75rem 1rem; border-left: 3px solid var(--primary-color); }
    .seg-header { display: flex; align-items: center; gap: 0.75rem; margin-bottom: 0.4rem; }
    .seg-fecha { font-size: 0.8rem; font-weight: 600; }
    .seg-autor { font-size: 0.78rem; color: var(--text-secondary); margin-left: auto; }
    .seg-desc { margin: 0; font-size: 0.88rem; }
  `],
})
export class ConductaComponent implements OnInit, OnDestroy {
  private destroy$ = new Subject<void>();
  private readonly api    = inject(ApiService);
  readonly ctx            = inject(ContextService);
  private readonly notify = inject(ApexNotificationService);
  private readonly export = inject(ExportService);

  // ── List state ────────────────────────────────────────────────
  reportes    = signal<any[]>([]);
  busqueda    = signal('');
  readonly reportesFiltrados = computed(() => {
    const q = this.busqueda().toLowerCase();
    if (!q) return this.reportes();
    return this.reportes().filter(r =>
      (r.nombre_alumno || '').toLowerCase().includes(q) ||
      (r.descripcion || '').toLowerCase().includes(q)
    );
  });
  loading     = signal(false);
  saving      = signal(false);
  gruposOpts  = signal<GrupoConLabel[]>([]);
  alumnosSugg = signal<any[]>([]);
  showNuevoDialog = false;

  filtroTipo: string | null = null;
  filtroSeguimiento: boolean | null = null;

  readonly columnasReportes: ColumnConfig[] = [
    { field: 'fecha_reporte',   header: 'Fecha',           sortable: true,  filterable: false, width: '110px' },
    { field: 'tipo_falta',      header: 'Tipo',            sortable: true,  filterable: true,  width: '120px' },
    { field: 'descripcion',     header: 'Descripción',     sortable: false, filterable: false },
    { field: 'medida_aplicada', header: 'Medida aplicada', sortable: false, filterable: false, width: '160px' },
    { field: 'seguimiento_str', header: 'Seguimiento',     sortable: true,  filterable: true,  width: '120px' },
    { field: 'sancion_str',     header: 'Sanción',         sortable: false, filterable: false, width: '100px' },
  ];

  readonly tiposFalta = [
    { label: 'Leve', value: 'LEVE' },
    { label: 'Grave', value: 'GRAVE' },
    { label: 'Muy grave', value: 'MUY_GRAVE' },
  ];
  readonly seguimientoOpts = [
    { label: 'Pendiente seguimiento', value: true },
    { label: 'Cerrado', value: false },
  ];
  readonly sancionOpts = SANCION_OPTS;
  readonly avanceOpts  = AVANCE_OPTS;
  readonly estadosSancion = [
    { label: 'Aplicada',    value: 'APLICADA' },
    { label: 'En proceso',  value: 'EN_PROCESO' },
    { label: 'Cumplida',    value: 'CUMPLIDA' },
    { label: 'Apelada',     value: 'APELADA' },
    { label: 'Revocada',    value: 'REVOCADA' },
  ];
  readonly estadoPlanOpts = ESTADO_PLAN_OPTS;
  readonly medioNotifOpts = MEDIO_NOTIF_OPTS;

  // ── Detalle dialog ────────────────────────────────────────────
  showDetalleDialog = false;
  detalleTab        = '0';
  loadingDetalle    = signal(false);
  detalle           = signal<any>(null);
  selectedReporteId = signal<string | null>(null);

  readonly detalleHeader = computed(() => {
    const d = this.detalle();
    if (!d) return 'Detalle';
    return `Reporte — ${d.reporte?.nombre_alumno ?? ''} — ${d.reporte?.fecha_reporte ?? ''}`;
  });

  readonly seguimientosTimeline = computed(() => {
    const segs = this.detalle()?.seguimientos ?? [];
    const avanceSev: Record<string, string> = {
      SIN_AVANCE: 'error', PARCIAL: 'warning', SATISFACTORIO: 'info', EXCELENTE: 'success',
    };
    return segs.map((seg: any) => ({
      title: seg.avance,
      date: seg.fecha_seguimiento,
      actor: seg.registrado_por_nombre,
      description: [
        seg.descripcion,
        seg.nuevo_estado_plan ? `Plan → ${seg.nuevo_estado_plan}` : null,
      ].filter(Boolean).join(' · '),
      severity: avanceSev[seg.avance] ?? 'info',
    }));
  });

  // ── RBAC helpers ──────────────────────────────────────────────
  readonly puedeAplicarSancion = computed(() => (this.ctx.usuario()?.nivel_acceso ?? 99) <= 2);
  readonly puedeGestionarPlan  = computed(() => (this.ctx.usuario()?.nivel_acceso ?? 99) <= 3);

  // ── Sanción form ──────────────────────────────────────────────
  savingSancion = signal(false);
  sancionForm = {
    tipo_sancion: 'AMONESTACION_ESCRITA',
    justificacion: '',
    notificado_padres: false,
    fecha_sancion: null as Date | null,
  };
  sancionUpdate = {
    estado: null as string | null,
    notificado_padres: false,
    medio_notificacion: null as string | null,
  };

  // ── Plan de mejora form ───────────────────────────────────────
  savingPlan = signal(false);
  planForm = {
    objetivo_general: '',
    fecha_primer_seguimiento: null as Date | null,
    compromisos_alumno:  [] as Compromiso[],
    compromisos_padre:   [] as Compromiso[],
    compromisos_escuela: [] as Compromiso[],
  };
  planUpdate = {
    firmado_alumno:   false,
    firmado_padre:    false,
    firmado_director: false,
    estado: null as string | null,
  };

  // ── Seguimiento form ──────────────────────────────────────────
  savingSeg = signal(false);
  segForm = {
    avance: 'PARCIAL',
    descripcion: '',
    fecha_seguimiento: null as Date | null,
    nuevo_estado_plan: null as string | null,
  };

  // ── Nuevo reporte form ────────────────────────────────────────
  form: {
    estudiante_id: string;
    grupo_id: string;
    reportado_por_id: string;
    tipo_falta: 'LEVE' | 'GRAVE' | 'MUY_GRAVE';
    descripcion: string;
    medida_aplicada: string;
    requiere_seguimiento: boolean;
    _alumnoObj: any;
    _grupoObj: GrupoConLabel | null;
  } = this.formVacio();

  constructor() {
    // Recargar cuando cambia cualquier dimensión de la cascada global.
    effect(() => {
      this.ctx.plantel(); this.ctx.nivel(); this.ctx.grado(); this.ctx.grupo();
      this.cargar();
    });

    // Cargar grupos del ciclo vigente para el formulario
    const ctx = this.ctx;
    const api  = this.api;
    const gOpts = this.gruposOpts;
    const plantelId = ctx.plantel()?.id;
    const params: Record<string, any> = { solo_activos: true, ciclo_vigente: true };
    if (plantelId) params['plantel_id'] = plantelId;
    api.get<Grupo[]>('/grupos', params).pipe(takeUntil(this.destroy$)).subscribe({
      next: gs => gOpts.set(gs.map(g => ({ ...g, _label: grupoLabel(g) }))),
      error: () => {},
    });
  }

  ngOnInit(): void {
    // ngOnInit no necesita cargar planteles — la cascada vive en el topbar.
  }

  cargar(): void {
    this.loading.set(true);
    const params: Record<string, any> = {};
    if (this.filtroTipo) params['tipo_falta'] = this.filtroTipo;
    if (this.filtroSeguimiento !== null) params['requiere_seguimiento'] = this.filtroSeguimiento;

    const plantel = this.ctx.plantel(); if (plantel?.id) params['plantel_id'] = plantel.id;
    const nivel   = this.ctx.nivel();   if (nivel?.id)   params['nivel_id']   = nivel.id;
    const grado   = this.ctx.grado();   if (grado?.id)   params['grado_id']   = grado.id;
    const grupo   = this.ctx.grupo();   if (grupo?.id)   params['grupo_id']   = grupo.id;

    this.api.get<any[]>('/conducta', params).pipe(takeUntil(this.destroy$)).subscribe({
      next: r => {
        this.reportes.set(r.map(x => ({
          ...x,
          medida_aplicada: x.medida_aplicada ?? '—',
          seguimiento_str: x.requiere_seguimiento ? 'Pendiente' : 'Cerrado',
          sancion_str: x.sancion_id ? 'Sí' : '—',
        })));
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  // ── Detalle ───────────────────────────────────────────────────
  abrirDetalle(reporte: any): void {
    this.selectedReporteId.set(reporte.id);
    this.showDetalleDialog = true;
    this.detalleTab = '0';
    this.loadingDetalle.set(true);
    this.detalle.set(null);
    this.api.get<any>(`/conducta/${reporte.id}/detalle-completo`).pipe(takeUntil(this.destroy$)).subscribe({
      next: d => {
        this.detalle.set(d);
        // Pre-cargar valores de actualización
        if (d.sancion) {
          this.sancionUpdate.estado = d.sancion.estado;
          this.sancionUpdate.notificado_padres = d.sancion.notificado_padres;
        }
        if (d.plan_mejora) {
          this.planUpdate.firmado_alumno   = d.plan_mejora.firmado_alumno;
          this.planUpdate.firmado_padre    = d.plan_mejora.firmado_padre;
          this.planUpdate.firmado_director = d.plan_mejora.firmado_director;
          this.planUpdate.estado           = d.plan_mejora.estado;
        }
        this.loadingDetalle.set(false);
      },
      error: () => {
        this.notify.error('Error', 'No se pudo cargar el detalle del reporte');
        this.loadingDetalle.set(false);
        this.showDetalleDialog = false;
      },
    });
  }

  onDetalleClosed(): void {
    this.detalle.set(null);
    this.selectedReporteId.set(null);
    this.resetForms();
  }

  // ── SB-012: Aplicar sanción ───────────────────────────────────
  aplicarSancion(): void {
    const reporteId = this.selectedReporteId();
    if (!reporteId) return;
    if (!this.sancionForm.tipo_sancion) {
      this.notify.warning('Campo requerido', 'Selecciona un tipo de sanción'); return;
    }
    if (this.sancionForm.justificacion.length < 20) {
      this.notify.warning('Justificación insuficiente', 'Mínimo 20 caracteres'); return;
    }
    this.savingSancion.set(true);
    const u = this.ctx.usuario();
    this.api.post(`/conducta/${reporteId}/sancion`, {
      tipo_sancion: this.sancionForm.tipo_sancion,
      justificacion: this.sancionForm.justificacion,
      autorizado_por_id: u?.id,
      fecha_sancion: this.sancionForm.fecha_sancion
        ? new Date(this.sancionForm.fecha_sancion).toISOString().slice(0, 10)
        : null,
      notificado_padres: this.sancionForm.notificado_padres,
    }).pipe(takeUntil(this.destroy$)).subscribe({
      next: () => {
        this.notify.success('Sanción aplicada');
        this.savingSancion.set(false);
        this.recargarDetalle();
      },
      error: (e: any) => {
        this.notify.error('Error', e.error?.detail ?? 'Error al aplicar sanción');
        this.savingSancion.set(false);
      },
    });
  }

  actualizarSancion(): void {
    const d = this.detalle();
    if (!d?.sancion) return;
    this.savingSancion.set(true);
    const reporteId = this.selectedReporteId();
    this.api.patch(`/conducta/${reporteId}/sancion/${d.sancion.id}`, {
      estado: this.sancionUpdate.estado,
      notificado_padres: this.sancionUpdate.notificado_padres,
      medio_notificacion: this.sancionUpdate.notificado_padres ? this.sancionUpdate.medio_notificacion : null,
    }).pipe(takeUntil(this.destroy$)).subscribe({
      next: () => {
        this.notify.success('Sanción actualizada');
        this.savingSancion.set(false);
        this.recargarDetalle();
      },
      error: (e: any) => {
        this.notify.error('Error', e.error?.detail ?? 'Error al actualizar');
        this.savingSancion.set(false);
      },
    });
  }

  // ── SB-013: Plan de mejora ────────────────────────────────────
  agregarCompromiso(tipo: 'alumno' | 'padre' | 'escuela'): void {
    const c: Compromiso = { texto: '', plazo_dias: null, cumplido: false };
    if (tipo === 'alumno')   this.planForm.compromisos_alumno  = [...this.planForm.compromisos_alumno, c];
    if (tipo === 'padre')    this.planForm.compromisos_padre   = [...this.planForm.compromisos_padre, c];
    if (tipo === 'escuela')  this.planForm.compromisos_escuela = [...this.planForm.compromisos_escuela, c];
  }

  eliminarCompromiso(tipo: 'alumno' | 'padre' | 'escuela', idx: number): void {
    if (tipo === 'alumno')   this.planForm.compromisos_alumno  = this.planForm.compromisos_alumno.filter((_, i) => i !== idx);
    if (tipo === 'padre')    this.planForm.compromisos_padre   = this.planForm.compromisos_padre.filter((_, i) => i !== idx);
    if (tipo === 'escuela')  this.planForm.compromisos_escuela = this.planForm.compromisos_escuela.filter((_, i) => i !== idx);
  }

  crearPlan(): void {
    const reporteId = this.selectedReporteId();
    if (!reporteId) return;
    if (this.planForm.objetivo_general.length < 20) {
      this.notify.warning('Objetivo insuficiente', 'Mínimo 20 caracteres'); return;
    }
    this.savingPlan.set(true);
    const u = this.ctx.usuario();
    this.api.post(`/conducta/${reporteId}/plan-mejora`, {
      elaborado_por_id: u?.id,
      objetivo_general: this.planForm.objetivo_general,
      compromisos_alumno:  this.planForm.compromisos_alumno,
      compromisos_padre:   this.planForm.compromisos_padre,
      compromisos_escuela: this.planForm.compromisos_escuela,
      fecha_primer_seguimiento: this.planForm.fecha_primer_seguimiento
        ? new Date(this.planForm.fecha_primer_seguimiento).toISOString().slice(0, 10)
        : null,
    }).pipe(takeUntil(this.destroy$)).subscribe({
      next: () => {
        this.notify.success('Plan de mejora creado');
        this.savingPlan.set(false);
        this.recargarDetalle();
      },
      error: (e: any) => {
        this.notify.error('Error', e.error?.detail ?? 'Error al crear plan');
        this.savingPlan.set(false);
      },
    });
  }

  actualizarPlan(): void {
    const d = this.detalle();
    if (!d?.plan_mejora) return;
    this.savingPlan.set(true);
    const reporteId = this.selectedReporteId();
    this.api.patch(`/conducta/${reporteId}/plan-mejora/${d.plan_mejora.id}`, {
      firmado_alumno:   this.planUpdate.firmado_alumno,
      firmado_padre:    this.planUpdate.firmado_padre,
      firmado_director: this.planUpdate.firmado_director,
      estado:           this.planUpdate.estado,
    }).pipe(takeUntil(this.destroy$)).subscribe({
      next: () => {
        this.notify.success('Plan actualizado');
        this.savingPlan.set(false);
        this.recargarDetalle();
      },
      error: (e: any) => {
        this.notify.error('Error', e.error?.detail ?? 'Error al actualizar');
        this.savingPlan.set(false);
      },
    });
  }

  // ── SB-014: Seguimiento ───────────────────────────────────────
  agregarSeguimiento(): void {
    const d = this.detalle();
    if (!d?.plan_mejora) return;
    if (this.segForm.descripcion.length < 20) {
      this.notify.warning('Descripción insuficiente', 'Mínimo 20 caracteres'); return;
    }
    this.savingSeg.set(true);
    const reporteId = this.selectedReporteId();
    const u = this.ctx.usuario();
    this.api.post(`/conducta/${reporteId}/plan-mejora/${d.plan_mejora.id}/seguimiento`, {
      registrado_por_id: u?.id,
      avance: this.segForm.avance,
      descripcion: this.segForm.descripcion,
      fecha_seguimiento: this.segForm.fecha_seguimiento
        ? new Date(this.segForm.fecha_seguimiento).toISOString().slice(0, 10)
        : null,
      nuevo_estado_plan: this.segForm.nuevo_estado_plan,
    }).pipe(takeUntil(this.destroy$)).subscribe({
      next: () => {
        this.notify.success('Seguimiento registrado');
        this.savingSeg.set(false);
        this.segForm = { avance: 'PARCIAL', descripcion: '', fecha_seguimiento: null, nuevo_estado_plan: null };
        this.recargarDetalle();
      },
      error: (e: any) => {
        this.notify.error('Error', e.error?.detail ?? 'Error al registrar seguimiento');
        this.savingSeg.set(false);
      },
    });
  }

  // ── Nuevo reporte (SB-010) ────────────────────────────────────
  abrirNuevoReporte(): void {
    this.form = this.formVacio();
    this.showNuevoDialog = true;
  }

  guardar(): void {
    if (!this.form.estudiante_id || !this.form.grupo_id || !this.form.descripcion) {
      this.notify.warning('Campos requeridos', 'Alumno, grupo y descripción son obligatorios');
      return;
    }
    this.saving.set(true);
    const { _alumnoObj, _grupoObj, ...payload } = this.form;
    this.api.post<any>('/conducta', {
      ...payload,
      medida_aplicada: payload.medida_aplicada || null,
    }).pipe(takeUntil(this.destroy$)).subscribe({
      next: (r) => {
        this.reportes.update(list => [{
          ...r,
          medida_aplicada: r.medida_aplicada ?? '—',
          seguimiento_str: r.requiere_seguimiento ? 'Pendiente' : 'Cerrado',
          sancion_str: '—',
        }, ...list]);
        this.showNuevoDialog = false;
        this.saving.set(false);
        this.notify.success('Reporte registrado');
      },
      error: (e: any) => {
        this.saving.set(false);
        this.notify.error('Error', e.error?.detail || 'Error al guardar');
      },
    });
  }

  buscarAlumnos(event: { query: string }): void {
    const params: Record<string, any> = { q: event.query };
    const plantelId = this.ctx.plantel()?.id;
    if (plantelId) params['plantel_id'] = plantelId;
    this.api.get<any[]>('/portal/buscar', params).pipe(takeUntil(this.destroy$)).subscribe({
      next: (r: any) => {
        this.alumnosSugg.set((r ?? []).map((a: any) => ({
          id: a.id,
          nombre_completo: [a.nombre, a.apellido_paterno, a.apellido_materno]
            .filter(Boolean).join(' ') + (a.matricula ? ` — ${a.matricula}` : ''),
        })));
      },
      error: () => this.alumnosSugg.set([]),
    });
  }

  onAlumnoSelect(alumno: any): void { this.form.estudiante_id = alumno?.id ?? ''; }
  onGrupoSelect(grupo: GrupoConLabel | null): void { this.form.grupo_id = grupo?.id ?? ''; }

  // ── Helpers ───────────────────────────────────────────────────
  faltaSeverity(tipo: string): TagSeverity { return FALTA_SEVERITY[tipo] ?? 'info'; }

  avanceSeverity(avance: string): TagSeverity {
    const m: Record<string, TagSeverity> = {
      SIN_AVANCE: 'danger', PARCIAL: 'warn', SATISFACTORIO: 'info', EXCELENTE: 'success',
    };
    return m[avance] ?? 'secondary';
  }

  sancionLabel(tipo: string): string {
    return SANCION_OPTS.find(o => o.value === tipo)?.label ?? tipo;
  }

  private recargarDetalle(): void {
    const reporteId = this.selectedReporteId();
    if (!reporteId) return;
    this.api.get<any>(`/conducta/${reporteId}/detalle-completo`).pipe(takeUntil(this.destroy$)).subscribe({
      next: d => {
        this.detalle.set(d);
        if (d.sancion) {
          this.sancionUpdate.estado = d.sancion.estado;
          this.sancionUpdate.notificado_padres = d.sancion.notificado_padres;
        }
        if (d.plan_mejora) {
          this.planUpdate.firmado_alumno   = d.plan_mejora.firmado_alumno;
          this.planUpdate.firmado_padre    = d.plan_mejora.firmado_padre;
          this.planUpdate.firmado_director = d.plan_mejora.firmado_director;
          this.planUpdate.estado           = d.plan_mejora.estado;
        }
      },
      error: () => {},
    });
    // Recargar la lista también para reflejar cambios
    this.cargar();
  }

  private resetForms(): void {
    this.sancionForm = { tipo_sancion: 'AMONESTACION_ESCRITA', justificacion: '', notificado_padres: false, fecha_sancion: null };
    this.sancionUpdate = { estado: null, notificado_padres: false, medio_notificacion: null };
    this.planForm = { objetivo_general: '', fecha_primer_seguimiento: null, compromisos_alumno: [], compromisos_padre: [], compromisos_escuela: [] };
    this.planUpdate = { firmado_alumno: false, firmado_padre: false, firmado_director: false, estado: null };
    this.segForm = { avance: 'PARCIAL', descripcion: '', fecha_seguimiento: null, nuevo_estado_plan: null };
  }

  private formVacio() {
    const u = this.ctx.usuario();
    return {
      estudiante_id: '', grupo_id: '', reportado_por_id: u?.id ?? '',
      tipo_falta: 'LEVE' as const, descripcion: '',
      medida_aplicada: '', requiere_seguimiento: false,
      _alumnoObj: null, _grupoObj: null as GrupoConLabel | null,
    };
  }

  // ── Exports ───────────────────────────────────────────────────
  private readonly exportCols: ExportColumn[] = [
    { field: 'nombre_alumno',        header: 'Alumno', format: v => v || '—' },
    { field: 'fecha_reporte',        header: 'Fecha' },
    { field: 'tipo_falta',           header: 'Tipo de falta' },
    { field: 'descripcion',          header: 'Descripción' },
    { field: 'medida_aplicada',      header: 'Medida aplicada', format: v => v || '' },
    { field: 'sancion_str',          header: 'Sanción', format: v => v || '—' },
    { field: 'reportado_por_nombre', header: 'Reportado por', format: v => v || '—' },
    { field: 'requiere_seguimiento', header: 'Seguimiento', format: v => v ? 'Pendiente' : 'Cerrado' },
  ];
  exportCSV():  void { this.export.toCSV(this.reportes(), this.exportCols, 'conducta'); }
  exportXLSX(): void { this.export.toXLSX(this.reportes(), this.exportCols, 'Conducta', 'conducta'); }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
