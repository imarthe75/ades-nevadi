import { Component, OnInit, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { PortalApiService } from '../../core/services/portal-api.service';
import { PortalAuthService } from '../../core/services/portal-auth.service';

const ESTADO_LABEL: Record<string, string> = {
  BORRADOR: 'Borrador', ENVIADA: 'Enviada', EN_REVISION: 'En Revisión',
  PRESELECCIONADA: 'Preseleccionada', ACEPTADA: 'Aceptada',
  RECHAZADA: 'Rechazada', LISTA_ESPERA: 'Lista de Espera',
};

@Component({
  selector: 'app-mis-postulaciones',
  standalone: true,
  imports: [CommonModule, RouterLink, ButtonModule, ProgressSpinnerModule],
  template: `
    <div class="container" style="padding:2rem 1.5rem">
      <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:1.5rem">
        <div>
          <h2>Mis Postulaciones</h2>
          <p style="color:#6c757d;font-size:.9rem">Hola, {{ auth.nombreCompleto() }}</p>
        </div>
        <a routerLink="/convocatorias">
          <p-button label="Ver convocatorias" icon="pi pi-search" severity="secondary" [outlined]="true" />
        </a>
      </div>

      @if (loading()) {
        <div style="text-align:center;padding:3rem"><p-progressSpinner /></div>
      } @else if (postulaciones().length === 0) {
        <div class="empty-state card">
          <i class="pi pi-inbox" style="font-size:2.5rem;color:#dee2e6"></i>
          <h3 style="margin:.5rem 0">Sin postulaciones</h3>
          <p style="color:#6c757d">Aún no te has postulado a ninguna convocatoria.</p>
          <a routerLink="/convocatorias" style="margin-top:.5rem;display:inline-block;color:#8B0000;font-weight:600">
            Ver convocatorias disponibles →
          </a>
        </div>
      } @else {
        <div class="post-list">
          @for (p of postulaciones(); track p['id']) {
            <a [routerLink]="['/postulacion', p['id']]" class="post-card">
              <div class="post-header">
                <span class="badge" [class]="'estado-' + p['estado']">{{ estadoLabel(p['estado']) }}</span>
                <span class="post-folio">{{ p['folio'] }}</span>
              </div>
              <h3 class="post-titulo">{{ p['convocatoria_titulo'] }}</h3>
              <div class="post-meta">
                <span><i class="pi pi-tag"></i> {{ p['convocatoria_tipo'] }}</span>
                <span><i class="pi pi-file"></i> {{ p['total_documentos'] }} doc(s)</span>
                @if (p['fecha_envio']) {
                  <span><i class="pi pi-send"></i> Enviada {{ p['fecha_envio'] | date:'d MMM yyyy' }}</span>
                } @else {
                  <span style="color:#fd7e14"><i class="pi pi-pencil"></i> En borrador</span>
                }
              </div>
              <div class="post-action"><i class="pi pi-arrow-right"></i> Ver detalle</div>
            </a>
          }
        </div>
      }
    </div>
  `,
  styles: [`
    .empty-state { text-align: center; padding: 3rem 2rem; }
    .post-list { display: flex; flex-direction: column; gap: 1rem; max-width: 760px; }
    .post-card { display: block; background: #fff; border: 1px solid #dee2e6; border-radius: 10px; padding: 1.25rem; text-decoration: none; color: inherit; transition: box-shadow .15s; }
    .post-card:hover { box-shadow: 0 4px 16px rgba(0,0,0,.1); }
    .post-header { display: flex; align-items: center; gap: .75rem; margin-bottom: .6rem; }
    .post-folio { font-size: .78rem; color: #6c757d; font-family: monospace; }
    .post-titulo { font-size: 1rem; font-weight: 700; color: #1a1a2e; margin-bottom: .5rem; }
    .post-meta { display: flex; flex-wrap: wrap; gap: .5rem 1.25rem; font-size: .82rem; color: #6c757d; }
    .post-meta span { display: flex; align-items: center; gap: .35rem; }
    .post-action { text-align: right; font-size: .82rem; color: #8B0000; font-weight: 600; margin-top: .5rem; }

    .badge { display: inline-block; padding: .2rem .65rem; border-radius: 20px; font-size: .75rem; font-weight: 700; }
    .estado-BORRADOR        { background: #e9ecef; color: #495057; }
    .estado-ENVIADA         { background: #cfe2ff; color: #084298; }
    .estado-EN_REVISION     { background: #fff3cd; color: #664d03; }
    .estado-PRESELECCIONADA { background: #d1e7dd; color: #0a3622; }
    .estado-ACEPTADA        { background: #198754; color: #fff; }
    .estado-RECHAZADA       { background: #f8d7da; color: #842029; }
    .estado-LISTA_ESPERA    { background: #e2d9f3; color: #41237a; }
  `],
})
export class MisPostulacionesComponent implements OnInit {
  private readonly api = inject(PortalApiService);
  readonly auth        = inject(PortalAuthService);

  postulaciones = signal<any[]>([]);
  loading       = signal(true);

  ngOnInit() {
    this.api.get<any[]>('/usuario/postulaciones').subscribe({
      next: list => { this.postulaciones.set(list); this.loading.set(false); },
      error: ()   => this.loading.set(false),
    });
  }

  estadoLabel(e: string) { return ESTADO_LABEL[e] ?? e; }
}
