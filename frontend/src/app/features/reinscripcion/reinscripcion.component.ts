import { Component, OnDestroy, OnInit, inject, signal, computed, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject, takeUntil } from 'rxjs';
import { ButtonModule } from 'primeng/button';
import { SelectModule } from 'primeng/select';
import { TagModule } from 'primeng/tag';
import { ProgressBarModule } from 'primeng/progressbar';
import { DialogModule } from 'primeng/dialog';
import { TooltipModule } from 'primeng/tooltip';
import { TextareaModule } from 'primeng/textarea';
import { ApiService } from '../../core/services/api.service';
import { ContextService } from '../../core/services/context.service';
import { ExportService } from '../../core/services/export.service';
import { ApexNotificationService } from 'apex-component-library';
import { InteractiveGridComponent, ColumnConfig } from '../../shared/components/interactive-grid/interactive-grid.component';

interface CicloOpt   { id: string; nombre_ciclo: string; es_vigente: boolean; }

/**
 * Módulo de reinscripción de alumnos para el nuevo ciclo escolar.
 * Gestiona el proceso de continuidad académica: validación de requisitos,
 * asignación de grupo y generación de documentos de inscripción.
 * Requiere nivelAcceso 4 (AdminPlantel) o superior.
 */
@Component({
  selector: 'app-reinscripcion',
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    ButtonModule, SelectModule, TagModule,
    ProgressBarModule, DialogModule, TooltipModule, TextareaModule,
    InteractiveGridComponent,
  ],
  template: `
<div class="page-header">
  <div class="page-title"><i class="pi pi-refresh"></i> Reinscripción Masiva</div>
  <div class="page-actions">
    <button pButton icon="pi pi-download" label="Exportar" severity="secondary"
            (click)="exportar()" [disabled]="!alumnos().length"></button>
  </div>
</div>

<!-- Selectores de ciclo -->
<div class="filter-bar">
  <p-select [options]="ciclos()" optionLabel="nombre_ciclo" optionValue="id"
            placeholder="Ciclo origen (actual)" [(ngModel)]="cicloOrigenId"
            (onChange)="onCicloChange()" styleClass="w-200" [filter]="true" filterPlaceholder="Buscar..." />
  <p-select [options]="ciclos()" optionLabel="nombre_ciclo" optionValue="id"
            placeholder="Ciclo destino (nuevo)" [(ngModel)]="cicloDestinoId"
            (onChange)="onCicloChange()" styleClass="w-200" [filter]="true" filterPlaceholder="Buscar..." />
  <p-select [options]="estadoOpts" optionLabel="label" optionValue="value"
            placeholder="Todos los estados" [(ngModel)]="estadoFiltro"
            (onChange)="cargarEstado()" [showClear]="true" styleClass="w-180" />
</div>

<!-- KPIs -->
@if (reporte()) {
  <div class="kpi-row">
    <div class="kpi-card">
      <div class="kpi-value">{{ reporte()?.resumen?.total ?? 0 }}</div>
      <div class="kpi-label">Total alumnos</div>
    </div>
    <div class="kpi-card kpi-green">
      <div class="kpi-value">{{ reporte()?.resumen?.aprobados ?? 0 }}</div>
      <div class="kpi-label">Aprobados</div>
    </div>
    <div class="kpi-card kpi-blue">
      <div class="kpi-value">{{ reporte()?.resumen?.validados ?? 0 }}</div>
      <div class="kpi-label">Listos para aprobar</div>
    </div>
    <div class="kpi-card kpi-orange">
      <div class="kpi-value">{{ reporte()?.resumen?.pendientes ?? 0 }}</div>
      <div class="kpi-label">Requieren atención</div>
    </div>
    <div class="kpi-card kpi-red">
      <div class="kpi-value">{{ reporte()?.resumen?.rechazados ?? 0 }}</div>
      <div class="kpi-label">Rechazados</div>
    </div>
    <div class="kpi-card">
      <div class="kpi-value pct">{{ reporte()?.resumen?.pct_completado ?? 0 }}%</div>
      <div class="kpi-label">Completado</div>
      <p-progressBar [value]="reporte()?.resumen?.pct_completado ?? 0"
                     [style]="{'height':'6px','margin-top':'4px'}" [showValue]="false" />
    </div>
  </div>
}

<!-- Acciones masivas -->
@if (cicloOrigenId && cicloDestinoId) {
  <div class="acciones-masivas">
    <button pButton icon="pi pi-check-circle" label="1. Validar masivo"
            severity="info" [loading]="validando()"
            pTooltip="Detecta adeudos y bloqueos para todos los alumnos"
            (click)="validarMasivo()"></button>
    <button pButton icon="pi pi-users" label="2. Aprobar masivo"
            severity="success" [loading]="aprobando()"
            [disabled]="!puedeAprobar()"
            pTooltip="Aprueba todos los VALIDADOS y ejecuta la promoción"
            (click)="confirmarAprobarMasivo()"></button>
    <small class="text-muted">
      {{ totalValidados() }} listos para aprobar
      @if (totalPendientes() > 0) {
        · <span class="text-warning">{{ totalPendientes() }} requieren atención manual</span>
      }
    </small>
  </div>
}

<!-- Grid de alumnos -->
<app-interactive-grid
  [data]="alumnos()"
  [columns]="columnas"
  [loading]="cargando()"
  (rowSelected)="abrirDetalle($event)"
/>

<!-- Dialog: detalle + acción individual -->
<p-dialog [(visible)]="dialogVisible" header="Detalle de Reinscripción"
          [modal]="true" [style]="{width:'520px'}">
  @if (seleccionado()) {
    <div class="detalle-box">
      <div class="detalle-row"><span>Alumno</span><strong>{{ seleccionado()?.alumno }}</strong></div>
      <div class="detalle-row"><span>Matrícula</span><strong>{{ seleccionado()?.matricula }}</strong></div>
      <div class="detalle-row"><span>Grado / Grupo</span><strong>{{ seleccionado()?.grado_grupo }}</strong></div>
      <div class="detalle-row"><span>Plantel</span><strong>{{ seleccionado()?.nombre_plantel }}</strong></div>
      <div class="detalle-row">
        <span>Estado</span>
        <p-tag [value]="seleccionado()?.estado" [severity]="estadoSeverity(seleccionado()?.estado)" />
      </div>
      @if (seleccionado()?.tiene_adeudos) {
        <div class="detalle-row alert-row">
          <span>Adeudo</span>
          <strong class="text-danger">\${{ seleccionado()?.monto_adeudado | number:'1.2-2' }} MXN</strong>
        </div>
      }
      @if (seleccionado()?.razon_rechazo) {
        <div class="detalle-row alert-row">
          <span>Razón rechazo</span>
          <span class="text-danger">{{ seleccionado()?.razon_rechazo }}</span>
        </div>
      }
    </div>

    @if (seleccionado()?.estado === 'PENDIENTE' || seleccionado()?.estado === 'VALIDADO') {
      <div class="field mt-3">
        <label style="font-weight:600;display:block;margin-bottom:4px">Acción manual</label>
        <div class="acciones-ind">
          <button pButton label="Aprobar" icon="pi pi-check" severity="success"
                  (click)="accionIndividual('APROBAR')"></button>
          <button pButton label="Rechazar" icon="pi pi-times" severity="danger"
                  data-testid="btn-rechazar"
                  (click)="mostrarRazonRechazo = true"></button>
        </div>
        @if (mostrarRazonRechazo) {
          <div class="mt-2">
            <label>Razón de rechazo *</label>
            <textarea pTextarea [(ngModel)]="razonRechazo" rows="2"
                      placeholder="Ej: Adeudo de cuotas pendiente de regularizar"
                      style="width:100%;resize:vertical;margin-top:4px"></textarea>
            <button pButton label="Confirmar rechazo" severity="danger" icon="pi pi-times"
                    class="mt-2" [disabled]="!razonRechazo"
                    data-testid="btn-confirmar-rechazo"
                    (click)="accionIndividual('RECHAZAR')"></button>
          </div>
        }
      </div>
    }
  }
</p-dialog>

<!-- Dialog: confirmar aprobar masivo -->
<p-dialog [(visible)]="confirmAprobarVisible" header="Confirmar aprobación masiva"
          [modal]="true" [style]="{width:'460px'}">
  <p>Se aprobarán <strong>{{ totalValidados() }}</strong> alumnos y se ejecutará la promoción al siguiente ciclo.</p>
  <p class="text-muted" style="font-size:.9rem">Esta acción no se puede deshacer fácilmente.</p>
  <ng-template pTemplate="footer">
    <button pButton label="Cancelar" severity="secondary" (click)="confirmAprobarVisible = false"></button>
    <button pButton label="Sí, aprobar y promover" severity="success" icon="pi pi-check"
            [loading]="aprobando()" (click)="aprobarMasivo()"></button>
  </ng-template>
</p-dialog>
  `,
  styles: [`
    .filter-bar { display:flex; gap:8px; flex-wrap:wrap; margin-bottom:16px; align-items:center; }
    .w-200 { width:200px; }
    .w-180 { width:180px; }
    .kpi-row { display:flex; gap:12px; flex-wrap:wrap; margin-bottom:16px; }
    .kpi-card {
      background:var(--p-surface-0); border:1px solid var(--p-surface-border);
      border-radius:8px; padding:12px 16px; min-width:110px; text-align:center;
    }
    .kpi-value { font-size:1.8rem; font-weight:700; font-family:'Jost',sans-serif; }
    .kpi-value.pct { font-size:1.5rem; }
    .kpi-label { font-size:.75rem; color:var(--p-text-muted-color); margin-top:2px; }
    .kpi-green .kpi-value { color:#15803d; }
    .kpi-blue  .kpi-value { color:#0369a1; }
    .kpi-orange .kpi-value { color:#b45309; }
    .kpi-red   .kpi-value { color:#dc2626; }
    .acciones-masivas {
      display:flex; align-items:center; gap:12px; flex-wrap:wrap;
      padding:12px 16px; background:var(--p-surface-50,#f8f9fa);
      border:1px solid var(--p-surface-border); border-radius:8px; margin-bottom:16px;
    }
    .text-warning { color:#b45309; }
    .text-danger  { color:#dc2626; }
    .text-muted   { color:var(--p-text-muted-color); font-size:.85rem; }
    .mt-2 { margin-top:8px; }
    .mt-3 { margin-top:12px; }
    .detalle-box { border:1px solid var(--p-surface-border); border-radius:8px; overflow:hidden; }
    .detalle-row {
      display:flex; justify-content:space-between; padding:8px 12px;
      border-bottom:1px solid var(--p-surface-border); font-size:.9rem;
    }
    .detalle-row:last-child { border-bottom:none; }
    .detalle-row span:first-child { color:var(--p-text-muted-color); }
    .alert-row { background:#fff5f5; }
    .acciones-ind { display:flex; gap:8px; }
  `],
})
export class ReinscripcionComponent implements OnInit implements OnInit, OnDestroy {
  private destroy$ = new Subject<void>();
  private api      = inject(ApiService);
  private ctx      = inject(ContextService);
  private exporter = inject(ExportService);
  private notify   = inject(ApexNotificationService);

  ciclos   = signal<CicloOpt[]>([]);
  alumnos  = signal<any[]>([]);
  reporte  = signal<any>(null);

  cargando  = signal(false);
  validando = signal(false);
  aprobando = signal(false);

  cicloOrigenId:  string | null = null;
  cicloDestinoId: string | null = null;
  plantelFiltro:  string | null = null;
  estadoFiltro:   string | null = null;

  seleccionado    = signal<any>(null);
  dialogVisible   = false;
  confirmAprobarVisible = false;
  mostrarRazonRechazo   = false;
  razonRechazo = '';

  estadoOpts = [
    { label: 'Pendiente',  value: 'PENDIENTE' },
    { label: 'Validado',   value: 'VALIDADO'  },
    { label: 'Aprobado',   value: 'APROBADO'  },
    { label: 'Rechazado',  value: 'RECHAZADO' },
  ];

  readonly columnas: ColumnConfig[] = [
    { field: 'alumno',          header: 'Alumno',       sortable: true,  filterable: true  },
    { field: 'matricula',       header: 'Matrícula',    sortable: true,  filterable: true,  width: '120px' },
    { field: 'grado_grupo',     header: 'Grado / Grupo', sortable: true, filterable: true,  width: '150px' },
    { field: 'nombre_plantel',  header: 'Plantel',      sortable: true,  filterable: true,  width: '150px' },
    { field: 'estado',          header: 'Estado',       sortable: true,  filterable: true,  width: '110px' },
    { field: 'adeudo_str',      header: 'Adeudo',       sortable: true,  filterable: false, width: '110px' },
    { field: 'fecha_val_str',   header: 'Validado',     sortable: false, filterable: false, width: '110px' },
  ];

  readonly totalValidados  = computed(() => this.reporte()?.resumen?.validados  ?? 0);
  readonly totalPendientes = computed(() => this.reporte()?.resumen?.pendientes ?? 0);
  readonly puedeAprobar    = computed(() => this.totalValidados() > 0 && !this.aprobando());

  constructor() {
    effect(() => {
      this.plantelFiltro = this.ctx.plantel()?.id ?? null;
      if (this.cicloDestinoId) {
        this.cargarEstado();
        this.cargarReporte();
      }
    });
  }

  ngOnInit() {
    this.cargarCiclos();
  }

  cargarCiclos() {
    this.api.get('/catalogs/ciclos').subscribe((r: any) =>
      this.ciclos.set(r ?? []));
  }

  onCicloChange() {
    if (this.cicloOrigenId && this.cicloDestinoId) {
      this.cargarEstado();
      this.cargarReporte();
    }
  }

  cargarEstado() {
    if (!this.cicloDestinoId) return;
    this.cargando.set(true);
    let url = `/reinscripcion/${this.cicloDestinoId}/estado?por_pagina=500`;
    if (this.estadoFiltro)  url += `&estado=${this.estadoFiltro}`;
    if (this.plantelFiltro) url += `&plantel_id=${this.plantelFiltro}`;
    this.api.get(url).subscribe({
      next: (r: any) => {
        const flat = (r.data ?? []).map((x: any) => ({
          ...x,
          adeudo_str:   x.tiene_adeudos ? `$${Number(x.monto_adeudado).toFixed(2)}` : '—',
          fecha_val_str: x.fecha_validacion ? x.fecha_validacion.substring(0, 10) : '—',
        }));
        this.alumnos.set(flat);
        this.cargando.set(false);
      },
      error: () => this.cargando.set(false),
    });
  }

  cargarReporte() {
    if (!this.cicloDestinoId) return;
    this.api.get(`/reinscripcion/${this.cicloDestinoId}/reporte`).subscribe(
      (r: any) => this.reporte.set(r)
    );
  }

  validarMasivo() {
    if (!this.cicloOrigenId || !this.cicloDestinoId) return;
    this.validando.set(true);
    this.api.post(
      `/reinscripcion/${this.cicloDestinoId}/validar-masivo?ciclo_origen_id=${this.cicloOrigenId}`,
      {}
    ).subscribe({
      next: (r: any) => {
        this.validando.set(false);
        const res = r.resumen;
        this.notify.success(
          'Validación completada',
          `${res.validados} listos · ${res.bloqueados} con bloqueos`
        );
        this.cargarEstado();
        this.cargarReporte();
      },
      error: () => this.validando.set(false),
    });
  }

  confirmarAprobarMasivo() {
    this.confirmAprobarVisible = true;
  }

  aprobarMasivo() {
    if (!this.cicloOrigenId || !this.cicloDestinoId) return;
    this.aprobando.set(true);
    this.confirmAprobarVisible = false;
    this.api.post(
      `/reinscripcion/${this.cicloDestinoId}/aprobar-masivo?ciclo_origen_id=${this.cicloOrigenId}`,
      {}
    ).subscribe({
      next: (r: any) => {
        this.aprobando.set(false);
        const promo = r.resultado_promocion;
        this.notify.success(
          'Reinscripción completada',
          `${r.aprobados} aprobados · ${promo?.promovidos ?? 0} promovidos`
        );
        this.cargarEstado();
        this.cargarReporte();
      },
      error: (err: any) => {
        this.aprobando.set(false);
        const msg = err?.error?.detail ?? 'Error en la aprobación masiva';
        this.notify.error('Error', typeof msg === 'string' ? msg : JSON.stringify(msg));
      },
    });
  }

  abrirDetalle(row: any) {
    this.seleccionado.set(row);
    this.mostrarRazonRechazo = false;
    this.razonRechazo = '';
    this.dialogVisible = true;
  }

  accionIndividual(accion: 'APROBAR' | 'RECHAZAR') {
    const reg = this.seleccionado();
    if (!reg) return;
    if (accion === 'RECHAZAR' && !this.razonRechazo.trim()) return;
    this.api.patch(`/reinscripcion/${reg.id}`, {
      accion,
      razon_rechazo: accion === 'RECHAZAR' ? this.razonRechazo : null,
    }).subscribe({
      next: () => {
        this.notify.success(
          accion === 'APROBAR' ? 'Alumno aprobado' : 'Alumno rechazado',
          reg.alumno
        );
        this.dialogVisible = false;
        this.cargarEstado();
        this.cargarReporte();
      },
      error: () => this.notify.error('Error', 'No se pudo procesar la acción'),
    });
  }

  exportar() {
    const cols = [
      { field: 'alumno',         header: 'Alumno' },
      { field: 'matricula',      header: 'Matrícula' },
      { field: 'grado_grupo',    header: 'Grado / Grupo' },
      { field: 'nombre_plantel', header: 'Plantel' },
      { field: 'estado',         header: 'Estado' },
      { field: 'adeudo_str',     header: 'Adeudo' },
      { field: 'fecha_val_str',  header: 'Validado', format: (v: any) => v || '—' },
      { field: 'razon_rechazo',  header: 'Razón rechazo' },
    ];
    this.exporter.toXLSX(this.alumnos(), cols, 'Reinscripción', 'reinscripcion');
  }

  estadoSeverity(estado: string | undefined): 'success' | 'info' | 'warn' | 'danger' | 'secondary' {
    const map: Record<string, any> = {
      APROBADO: 'success', VALIDADO: 'info',
      PENDIENTE: 'warn',   RECHAZADO: 'danger',
    };
    return map[estado ?? ''] ?? 'secondary';
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
