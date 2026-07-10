/**
 * MonitorComponent — FASE 22 (Grafana + Prometheus) + AD-030 (Telemetría Servidor).
 *
 * Panel de monitoreo del sistema para ADMIN_GLOBAL / DIRECTOR:
 *   - Estado de todos los servicios
 *   - Telemetría de recursos: BD, conexiones, disco, JVM, colas Celery
 *   - Iframe de Grafana (dashboard ADES API)
 *   - Workflows activos de n8n
 */
import { Component, OnDestroy, OnInit, inject, signal, computed, ChangeDetectionStrategy} from '@angular/core';
import { CommonModule } from '@angular/common';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { Subject, takeUntil } from 'rxjs';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { CardModule } from 'primeng/card';
import { TooltipModule } from 'primeng/tooltip';
import { ProgressBarModule } from 'primeng/progressbar';
import { ApiService } from '../../core/services/api.service';
import { InteractiveGridComponent, ColumnConfig } from '../../shared/components/interactive-grid/interactive-grid.component';

interface ServicioStatus {
  nombre: string;
  url: string;
  disponible: boolean;
  icono: string;
  fase: number;
  admin_url?: string;
}

interface Workflow {
  id: string;
  name: string;
  active: boolean;
  createdAt?: string;
  updatedAt?: string;
}

interface TablaGrande {
  tabla: string;
  size_legible: string;
  size_bytes: number;
  filas_estimadas: number;
}

interface Telemetria {
  base_de_datos: {
    db_size_bytes: number;
    db_size_mb: number;
    db_size_gb: number;
    total_tablas: number;
    total_mv: number;
    total_indices: number;
    total_filas_estimadas: number;
  };
  conexiones: {
    total_conexiones: number;
    activas: number;
    inactivas: number;
    idle_in_transaction: number;
    max_conexiones: number;
    pct_uso: number;
  };
  tablas_grandes: TablaGrande[];
  particiones: {
    total_particiones: number;
    tablas_particionadas: number;
    size_total_mb: number;
    size_legible: string;
  };
  sistema: {
    jvm_heap_used_mb: number;
    jvm_heap_max_mb: number;
    jvm_heap_pct: number;
    cpu_disponibles: number;
    carga_sistema: number;
    disco_total_gb: number;
    disco_libre_gb: number;
    disco_usado_gb: number;
    disco_pct: number;
    jvm_threads_activos: number;
    jvm_uptime_min: number;
  };
  colas_celery: Record<string, number>;
  actualizacion: string;
}

@Component({
  selector: 'app-monitor',
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
  imports: [CommonModule, ButtonModule, TagModule, CardModule, TooltipModule,
            ProgressBarModule, InteractiveGridComponent],
  template: `
    <div class="page-header">
      <div>
        <h2>Monitor del Sistema</h2>
        <p class="subtitle">Estado de servicios · Telemetría AD-030 · Grafana · n8n Workflows</p>
      </div>
      <p-button icon="pi pi-refresh" [text]="true" severity="secondary"
        ariaLabel="Actualizar estado" pTooltip="Actualizar estado" (onClick)="cargarEstado()" />
    </div>

    <!-- Grid de servicios -->
    <div class="servicios-grid">
      @for (s of servicios(); track s.nombre) {
        <div class="servicio-card" [class.down]="!s.disponible">
          <div class="serv-icon"><i class="pi {{ s.icono }}"></i></div>
          <div class="serv-info">
            <div class="serv-nombre">{{ s.nombre }}</div>
            <div class="serv-fase" style="font-size:.72rem;color:var(--text-muted)">FASE {{ s.fase }}</div>
          </div>
          <div class="serv-status">
            <p-tag [value]="s.disponible ? 'Online' : 'Offline'"
              [severity]="s.disponible ? 'success' : 'danger'" />
          </div>
          @if (s.admin_url) {
            <a [href]="s.admin_url" target="_blank" class="serv-link" pTooltip="Abrir UI">
              <i class="pi pi-external-link"></i>
            </a>
          }
        </div>
      }
    </div>

    <!-- AD-030 Telemetría del servidor -->
    @if (telemetria(); as t) {
      <div class="telemetria-section">
        <div class="section-header">
          <h3><i class="pi pi-server" style="margin-right:.4rem"></i>Telemetría del Servidor</h3>
          <small style="color:var(--text-muted)">
            Actualizado: {{ t.actualizacion | date:'HH:mm:ss' }}
          </small>
        </div>

        <div class="telem-grid">

          <!-- Base de datos -->
          <div class="telem-card">
            <div class="telem-title"><i class="pi pi-database"></i> Base de Datos</div>
            <div class="telem-kpi">{{ t.base_de_datos.db_size_gb }} GB</div>
            <div class="telem-sub">{{ t.base_de_datos.total_tablas }} tablas ·
              {{ t.base_de_datos.total_mv }} MVs ·
              {{ t.base_de_datos.total_indices }} índices</div>
            <div class="telem-sub">{{ (t.base_de_datos.total_filas_estimadas || 0) | number }} filas estimadas</div>
          </div>

          <!-- Conexiones -->
          <div class="telem-card">
            <div class="telem-title"><i class="pi pi-share-alt"></i> Conexiones PostgreSQL</div>
            <div class="telem-kpi">{{ t.conexiones.total_conexiones }}
              <span class="telem-kpi-sub">/ {{ t.conexiones.max_conexiones }}</span>
            </div>
            <p-progressBar [value]="t.conexiones.pct_uso"
              [style]="{'height':'6px','margin':'6px 0'}"
              [color]="t.conexiones.pct_uso > 80 ? '#ef4444' : t.conexiones.pct_uso > 60 ? '#f59e0b' : '#22c55e'" />
            <div class="telem-sub">
              {{ t.conexiones.activas }} activas ·
              {{ t.conexiones.inactivas }} idle ·
              {{ t.conexiones.idle_in_transaction }} idle-tx
            </div>
          </div>

          <!-- Disco -->
          <div class="telem-card">
            <div class="telem-title"><i class="pi pi-hdd"></i> Disco</div>
            <div class="telem-kpi">{{ t.sistema.disco_usado_gb }} GB
              <span class="telem-kpi-sub">/ {{ t.sistema.disco_total_gb }} GB</span>
            </div>
            <p-progressBar [value]="t.sistema.disco_pct"
              [style]="{'height':'6px','margin':'6px 0'}"
              [color]="t.sistema.disco_pct > 85 ? '#ef4444' : t.sistema.disco_pct > 70 ? '#f59e0b' : '#22c55e'" />
            <div class="telem-sub">{{ t.sistema.disco_libre_gb }} GB libres</div>
          </div>

          <!-- JVM -->
          <div class="telem-card">
            <div class="telem-title"><i class="pi pi-microchip"></i> JVM (Spring BFF)</div>
            <div class="telem-kpi">{{ t.sistema.jvm_heap_used_mb }} MB
              <span class="telem-kpi-sub">/ {{ t.sistema.jvm_heap_max_mb }} MB</span>
            </div>
            <p-progressBar [value]="t.sistema.jvm_heap_pct"
              [style]="{'height':'6px','margin':'6px 0'}"
              [color]="t.sistema.jvm_heap_pct > 85 ? '#ef4444' : t.sistema.jvm_heap_pct > 70 ? '#f59e0b' : '#22c55e'" />
            <div class="telem-sub">
              {{ t.sistema.jvm_threads_activos }} threads ·
              carga CPU: {{ t.sistema.carga_sistema }} ·
              uptime: {{ t.sistema.jvm_uptime_min }} min
            </div>
          </div>

          <!-- Particionamiento -->
          <div class="telem-card">
            <div class="telem-title"><i class="pi pi-table"></i> Particionamiento</div>
            <div class="telem-kpi">{{ t.particiones.total_particiones }}
              <span style="font-size:.75rem;font-weight:400"> particiones</span>
            </div>
            <div class="telem-sub">{{ t.particiones.tablas_particionadas }} tablas particionadas</div>
            <div class="telem-sub">{{ t.particiones.size_legible }} total en particiones</div>
          </div>

          <!-- Colas Celery -->
          <div class="telem-card">
            <div class="telem-title"><i class="pi pi-inbox"></i> Colas Celery</div>
            @for (entry of celeryEntries(t); track entry.key) {
              <div class="cola-row">
                <span class="cola-nombre">{{ entry.key }}</span>
                <p-tag [value]="entry.value.toString()"
                  [severity]="entry.value > 50 ? 'danger' : entry.value > 10 ? 'warn' : 'success'" />
              </div>
            }
          </div>

        </div>

        <!-- Top 10 tablas más grandes -->
        <div style="margin-top:1rem">
          <div class="section-header">
            <h3 style="font-size:.82rem">Top 10 Tablas por Tamaño</h3>
          </div>
          <app-interactive-grid
            [data]="tablasGrandesFlat(t.tablas_grandes)"
            [columns]="columnasTablas"
            [loading]="loadingTelemetria()"
            [showDelete]="false"
          />
        </div>
      </div>
    }

    <!-- Grafana iframe -->
    @if (grafanaUrl()) {
      <div class="grafana-section">
        <div class="section-header">
          <h3>Dashboard ADES API</h3>
          <a [href]="grafanaExternalUrl" target="_blank">
            <p-button label="Abrir Grafana" icon="pi pi-external-link" size="small" severity="secondary" [text]="true" />
          </a>
        </div>
        <div class="grafana-wrapper">
          <iframe [src]="grafanaUrl()!" frameborder="0"
            style="width:100%;height:480px;border:none;border-radius:8px"></iframe>
        </div>
      </div>
    }

    <!-- Workflows n8n -->
    <div class="section-header" style="margin-top:1.5rem">
      <h3>Flujos de Automatización (n8n)</h3>
      <a href="http://localhost:5678" target="_blank">
        <p-button label="Abrir n8n" icon="pi pi-external-link" size="small" severity="secondary" [text]="true" />
      </a>
    </div>

    <app-interactive-grid
      [data]="workflowsFlat()"
      [columns]="workflowColumns"
      [loading]="loadingWorkflows()"
      [showDelete]="false"
    />
  `,
  styles: [`
    .page-header { display:flex;justify-content:space-between;align-items:flex-start;margin-bottom:1rem }
    .subtitle { font-size:.82rem;color:var(--text-color-secondary);margin:0 }
    .servicios-grid { display:grid;grid-template-columns:repeat(auto-fill,minmax(230px,1fr));gap:.75rem;margin-bottom:1.5rem }
    .servicio-card { display:flex;align-items:center;gap:.75rem;padding:.75rem 1rem;background:var(--surface-0);border:1px solid var(--surface-200);border-radius:10px;transition:border-color .15s }
    .servicio-card.down { border-color:#fca5a5;background:#fff5f5 }
    .serv-icon { width:36px;height:36px;border-radius:8px;background:var(--primary-50);display:flex;align-items:center;justify-content:center;color:var(--primary-color);font-size:1.1rem;flex-shrink:0 }
    .servicio-card.down .serv-icon { background:#fee2e2;color:#ef4444 }
    .serv-info { flex:1;min-width:0 }
    .serv-nombre { font-weight:600;font-size:.85rem }
    .serv-status { flex-shrink:0 }
    .serv-link { margin-left:.4rem;color:var(--text-muted);text-decoration:none;font-size:.9rem }
    .serv-link:hover { color:var(--primary-color) }
    .section-header { display:flex;justify-content:space-between;align-items:center;margin-bottom:.75rem }
    .section-header h4 { margin:0;font-size:.9rem;font-weight:700 }
    .grafana-section { margin-top:1rem }
    .grafana-wrapper { border-radius:8px;overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,.08) }

    /* Telemetría AD-030 */
    .telemetria-section { margin-bottom:1.5rem }
    .telem-grid { display:grid;grid-template-columns:repeat(auto-fill,minmax(260px,1fr));gap:.75rem;margin-bottom:1rem }
    .telem-card { background:var(--surface-0);border:1px solid var(--surface-200);border-radius:10px;padding:1rem }
    .telem-title { font-size:.75rem;font-weight:600;color:var(--text-muted);text-transform:uppercase;letter-spacing:.03em;margin-bottom:.5rem;display:flex;align-items:center;gap:.35rem }
    .telem-kpi { font-size:1.6rem;font-weight:700;color:var(--text-primary);line-height:1.1 }
    .telem-kpi-sub { font-size:.9rem;font-weight:400;color:var(--text-muted) }
    .telem-sub { font-size:.72rem;color:var(--text-muted);margin-top:.25rem }
    .cola-row { display:flex;justify-content:space-between;align-items:center;padding:.2rem 0;border-bottom:1px solid var(--surface-100) }
    .cola-nombre { font-size:.78rem;font-family:monospace }
  `],
})
export class MonitorComponent implements OnInit, OnDestroy {
  private destroy$ = new Subject<void>();
  private readonly api = inject(ApiService);
  private readonly san = inject(DomSanitizer);

  servicios          = signal<ServicioStatus[]>([]);
  workflows          = signal<Workflow[]>([]);
  telemetria         = signal<Telemetria | null>(null);
  grafanaUrl         = signal<SafeResourceUrl | null>(null);
  loadingWorkflows   = signal(false);
  loadingTelemetria  = signal(false);

  readonly workflowColumns: ColumnConfig[] = [
    { field: 'name',           header: 'Nombre del flujo',     sortable: true, filterable: true },
    { field: 'activoLabel',    header: 'Estado',               sortable: true, filterable: true, width: '100px' },
    { field: 'ultimaModif',    header: 'Última modificación',  sortable: true, filterable: false, width: '170px' },
  ];

  readonly columnasTablas: ColumnConfig[] = [
    { field: 'tabla',            header: 'Tabla',          sortable: true,  filterable: true },
    { field: 'size_legible',     header: 'Tamaño',         sortable: false, filterable: false, width: '100px' },
    { field: 'filas_str',        header: 'Filas (~)',       sortable: true,  filterable: false, width: '110px' },
  ];

  readonly workflowsFlat = computed(() =>
    this.workflows().map(w => ({
      ...w,
      activoLabel: w.active ? 'Activo' : 'Inactivo',
      ultimaModif: w.updatedAt ? new Date(w.updatedAt).toLocaleDateString('es-MX') : '',
    }))
  );

  readonly grafanaExternalUrl = 'http://localhost:3003';

  ngOnInit(): void { this.cargarEstado(); }

  cargarEstado(): void {
    const checks: Promise<void>[] = [
      this._checkService('ADES API',       '/health',              'pi-server',     1),
      this._checkService('Superset BI',    '/superset/dashboards', 'pi-chart-pie',  16, 'http://bi.ades.setag.mx'),
      this._checkService('Carbone PDF',    '/carbone/status',      'pi-file-pdf',   18),
      this._checkService('Flowise AI',     '/chatbot/status',      'pi-sparkles',   17),
      this._checkService('ntfy Push',      '/push/status',         'pi-bell',       20, 'https://notify.ades.setag.mx'),
      this._checkService('Stirling PDF',   '/pdf/status',          'pi-copy',       21),
      this._checkService('n8n Automation', '/automations/status',  'pi-sitemap',    23),
    ];
    Promise.all(checks);

    const grafBase = window.location.hostname === 'localhost'
      ? 'http://localhost:3003'
      : 'https://monitor.ades.setag.mx';
    const grafUrl = `${grafBase}/d/ades-api-monitor/ades-api-monitoreo?orgId=1&kiosk=tv&refresh=30s`;
    this.grafanaUrl.set(this.san.bypassSecurityTrustResourceUrl(grafUrl));

    this._loadWorkflows();
    this._loadTelemetria();
  }

  celeryEntries(t: Telemetria): { key: string; value: number }[] {
    return Object.entries(t.colas_celery).map(([key, value]) => ({ key, value: value as number }));
  }

  tablasGrandesFlat(tablas: TablaGrande[]): any[] {
    return (tablas || []).map(t => ({
      ...t,
      filas_str: (t.filas_estimadas || 0).toLocaleString('es-MX'),
    }));
  }

  private async _checkService(
    nombre: string, endpoint: string, icono: string, fase: number, admin_url?: string,
  ): Promise<void> {
    this.api.get<any>(endpoint).subscribe({
      next: data => {
        const disponible = data?.disponible !== undefined ? data.disponible : (data?.status === 'ok');
        this._upsertServicio({ nombre, url: endpoint, disponible, icono, fase, admin_url });
      },
      error: () => {
        this._upsertServicio({ nombre, url: endpoint, disponible: false, icono, fase, admin_url });
      },
    });
  }

  private _upsertServicio(s: ServicioStatus): void {
    this.servicios.update(list => {
      const idx = list.findIndex(x => x.nombre === s.nombre);
      if (idx >= 0) { const updated = [...list]; updated[idx] = s; return updated; }
      return [...list, s].sort((a, b) => a.fase - b.fase);
    });
  }

  private _loadWorkflows(): void {
    this.loadingWorkflows.set(true);
    this.api.get<any>('/automations/workflows').subscribe({
      next: data => {
        const items: Workflow[] = data?.data ?? data?.workflows ?? [];
        this.workflows.set(items);
        this.loadingWorkflows.set(false);
      },
      error: () => this.loadingWorkflows.set(false),
    });
  }

  private _loadTelemetria(): void {
    this.loadingTelemetria.set(true);
    this.api.get<Telemetria>('/stats/telemetria').subscribe({
      next: data => {
        this.telemetria.set(data);
        this.loadingTelemetria.set(false);
      },
      error: () => this.loadingTelemetria.set(false),
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
