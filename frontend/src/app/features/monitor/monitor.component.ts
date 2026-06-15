/**
 * MonitorComponent — FASE 22 (Grafana + Prometheus) + FASE 23 (n8n).
 *
 * Panel de monitoreo del sistema para ADMIN_GLOBAL:
 *   - Estado de todos los servicios
 *   - Iframe de Grafana (dashboard ADES API)
 *   - Accesos directos a Flowise, n8n, Superset
 *   - Workflows activos de n8n
 */
import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { CardModule } from 'primeng/card';
import { TooltipModule } from 'primeng/tooltip';
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

@Component({
  selector: 'app-monitor',
  standalone: true,
  imports: [CommonModule, ButtonModule, TagModule, CardModule, TooltipModule, InteractiveGridComponent],
  template: `
    <div class="page-header">
      <div>
        <h2>Monitor del Sistema</h2>
        <p class="subtitle">Estado de servicios · Grafana · n8n Workflows</p>
      </div>
      <p-button icon="pi pi-refresh" [text]="true" severity="secondary"
        pTooltip="Actualizar estado" (onClick)="cargarEstado()" />
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

    <!-- Grafana iframe -->
    @if (grafanaUrl()) {
      <div class="grafana-section">
        <div class="section-header">
          <h4>Dashboard ADES API</h4>
          <a [href]="grafanaExternalUrl" target="_blank">
            <p-button label="Abrir Grafana" icon="pi pi-external-link" size="small" severity="secondary" [text]="true" />
          </a>
        </div>
        <div class="grafana-wrapper">
          <iframe [src]="grafanaUrl()!" frameborder="0"
            style="width:100%;height:480px;border:none;border-radius:8px"></iframe>
        </div>
      </div>
    } @else {
      <div style="padding:1.5rem;text-align:center;color:var(--text-muted);background:var(--surface-50);border-radius:8px;margin-top:1rem">
        <i class="pi pi-chart-line" style="font-size:2rem;display:block;margin-bottom:.5rem"></i>
        Dashboard Grafana no configurado.<br>
        <small>Configurar <code>GRAFANA_URL</code> en .env para habilitar el iframe.</small>
      </div>
    }

    <!-- Workflows n8n -->
    <div class="section-header" style="margin-top:1.5rem">
      <h4>Flujos de Automatización (n8n)</h4>
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
    code { background:var(--surface-100);padding:.1rem .3rem;border-radius:3px;font-size:.75rem }
  `],
})
export class MonitorComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly san = inject(DomSanitizer);

  servicios          = signal<ServicioStatus[]>([]);
  workflows          = signal<Workflow[]>([]);
  grafanaUrl         = signal<SafeResourceUrl | null>(null);
  loadingWorkflows   = signal(false);

  readonly workflowColumns: ColumnConfig[] = [
    { field: 'name',           header: 'Nombre del flujo',     sortable: true, filterable: true },
    { field: 'activoLabel',    header: 'Estado',               sortable: true, filterable: true, width: '100px' },
    { field: 'ultimaModif',    header: 'Última modificación',  sortable: true, filterable: false, width: '170px' },
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
    // Verificar todos los servicios — paths relativos (ApiService ya agrega /api/v1)
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

    // Grafana iframe — usar subdominio si disponible, sino localhost
    const grafBase = window.location.hostname === 'localhost'
      ? 'http://localhost:3003'
      : 'https://monitor.ades.setag.mx';
    const grafUrl = `${grafBase}/d/ades-api-monitor/ades-api-monitoreo?orgId=1&kiosk=tv&refresh=30s`;
    this.grafanaUrl.set(this.san.bypassSecurityTrustResourceUrl(grafUrl));

    // Workflows de n8n
    this._loadWorkflows();
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
}
