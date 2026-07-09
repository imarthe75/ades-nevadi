import { Component, inject, OnInit, signal, computed, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { ButtonModule } from 'primeng/button';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { MessageModule } from 'primeng/message';
import { TooltipModule } from 'primeng/tooltip';

import { ApiService } from '../../core/services/api.service';
import { ContextService } from '../../core/services/context.service';

interface GuestTokenResp {
  token: string;
  dashboard_id: string;
  embed_url: string;
}

type DashKey = 'instituto' | 'plantel' | 'docente' | 'alumno';

/**
 * Embed de dashboards de Business Intelligence usando Apache Superset.
 * Obtiene un guest token del BFF para el plantel activo y renderiza el dashboard
 * en un iframe con `SafeResourceUrl`. Soporta cuatro vistas (instituto, plantel,
 * docente, alumno) según el nivelAcceso del usuario autenticado. El token se
 * rota automáticamente al cambiar de contexto. Requiere nivelAcceso ≥ 3.
 */
@Component({
  selector: 'app-bi',
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
  imports: [CommonModule, ButtonModule, ProgressSpinnerModule, MessageModule, TooltipModule],
  template: `
    <div class="page-header">
      <div>
        <h2>Dashboards BI</h2>
        <p class="subtitle">Análisis en tiempo real · {{ rolLabel() }}</p>
      </div>
      <div style="display:flex;gap:.5rem;align-items:center">
        @if (!cargando() && !error()) {
          <p-button icon="pi pi-refresh" [text]="true" severity="secondary"
            ariaLabel="Refrescar dashboard" pTooltip="Refrescar dashboard" (onClick)="cargar()" />
          <p-button icon="pi pi-external-link" [text]="true" severity="secondary"
            ariaLabel="Abrir en Superset" pTooltip="Abrir en Superset" (onClick)="abrirSuperset()" />
        }
      </div>
    </div>

    @if (cargando()) {
      <div style="display:flex;flex-direction:column;align-items:center;justify-content:center;min-height:400px;gap:1rem">
        <p-progressSpinner strokeWidth="4" style="width:48px;height:48px" />
        <p style="color:var(--text-color-secondary);font-size:.85rem">Cargando dashboard…</p>
      </div>
    }

    @if (error()) {
      <div style="padding:2rem;text-align:center">
        <p-message severity="warn" [text]="error()!" styleClass="bi-error-msg" />
        <p style="color:var(--text-color-secondary);font-size:.82rem;max-width:520px;margin:0 auto">
          Los dashboards se configuran en Superset UI tras el primer arranque.
          Ver <code>infrastructure/superset/init.sh</code> para instrucciones.
        </p>
        <p-button label="Reintentar" icon="pi pi-refresh" severity="secondary"
          styleClass="mt-3" (onClick)="cargar()" />
      </div>
    }

    @if (iframeUrl() && !cargando() && !error()) {
      <div class="iframe-wrapper">
        <iframe
          [src]="iframeUrl()!"
          frameborder="0"
          allowfullscreen
          style="width:100%;height:calc(100vh - 180px);border:none;border-radius:8px"
        ></iframe>
      </div>
    }
  `,
  styles: [`
    .page-header { display:flex;justify-content:space-between;align-items:flex-start;margin-bottom:1rem }
    .subtitle { font-size:.82rem;color:var(--text-color-secondary);margin:0 }
    .iframe-wrapper { border-radius:8px;overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,.08) }
    code { background:var(--surface-100);padding:.1rem .3rem;border-radius:3px;font-size:.78rem }
    :host ::ng-deep .bi-error-msg { display:block;margin-bottom:1rem }
  `],
})
export class BiComponent implements OnInit, OnDestroy {
  private readonly api  = inject(ApiService);
  private readonly ctx  = inject(ContextService);
  private readonly san  = inject(DomSanitizer);

  cargando   = signal(true);
  error      = signal<string | null>(null);
  iframeUrl  = signal<SafeResourceUrl | null>(null);
  embedUrl   = signal('');
  rolLabel   = computed(() => this.ctx.rolLabel());

  private get dashKey(): DashKey {
    const nivel = this.ctx.nivelAcceso();
    if (nivel === 0) return 'instituto';
    if (nivel <= 2) return 'plantel';
    if (nivel <= 4) return 'docente';
    return 'alumno';
  }

  ngOnInit(): void { this.cargar(); }
  ngOnDestroy(): void {}

  cargar(): void {
    this.cargando.set(true);
    this.error.set(null);
    this.iframeUrl.set(null);

    const params: any = {};
    const plantel = this.ctx.plantel();
    const ciclo = this.ctx.ciclo();
    const nivel = this.ctx.nivel();

    if (plantel?.id) params['plantel_id'] = plantel.id;
    if (ciclo?.id) params['ciclo_id'] = ciclo.id;
    if (nivel?.id) params['nivel_id'] = nivel.id;

    this.api.get<GuestTokenResp>(`/superset/dashboard/${this.dashKey}`, params).subscribe({
      next: resp => {
        this.embedUrl.set(resp.embed_url);
        const contextParams = new URLSearchParams();
        contextParams.append('token', resp.token);
        contextParams.append('standalone', '3');
        contextParams.append('hide_title', 'true');

        if (plantel?.id) contextParams.append('plantel_id', plantel.id);
        if (ciclo?.id) contextParams.append('ciclo_id', ciclo.id);
        if (nivel?.id) contextParams.append('nivel_id', nivel.id);

        const url = `${resp.embed_url}?${contextParams.toString()}`;
        this.iframeUrl.set(this.san.bypassSecurityTrustResourceUrl(url));
        this.cargando.set(false);
      },
      error: err => {
        const msg = err?.error?.detail ?? 'No se pudo cargar el dashboard. Verifique la configuración de Superset.';
        this.error.set(msg);
        this.cargando.set(false);
      },
    });
  }

  abrirSuperset(): void {
    if (this.embedUrl()) window.open(this.embedUrl(), '_blank');
  }
}
