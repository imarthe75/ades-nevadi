import { Component, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { InputTextModule } from 'primeng/inputtext';
import { ButtonModule } from 'primeng/button';
import { PortalApiService } from '../../core/services/portal-api.service';

const ESTADO_LABEL: Record<string, string> = {
  BORRADOR: 'Borrador', ENVIADA: 'Enviada', EN_REVISION: 'En Revisión',
  PRESELECCIONADA: 'Preseleccionada', ACEPTADA: 'Aceptada',
  RECHAZADA: 'Rechazada', LISTA_ESPERA: 'Lista de Espera',
};

@Component({
  selector: 'app-seguimiento',
  standalone: true,
  imports: [CommonModule, FormsModule, InputTextModule, ButtonModule],
  template: `
    <div class="container" style="padding:3rem 1.5rem;max-width:640px;margin:0 auto">
      <h2 style="text-align:center;color:#8B0000">Consultar estado de postulación</h2>
      <p style="text-align:center;color:#6c757d;font-size:.9rem;margin-bottom:2rem">
        Ingresa el folio que recibiste por correo al enviar tu postulación.
      </p>

      <div class="buscar-card card">
        <div style="display:flex;gap:.75rem;flex-wrap:wrap">
          <input pInputText [(ngModel)]="folio" placeholder="NVD-INSC-2026-00001"
            style="flex:1;min-width:200px;font-family:monospace"
            (keyup.enter)="consultar()" />
          <p-button label="Consultar" icon="pi pi-search"
            [style]="{'background':'#8B0000','border-color':'#8B0000'}"
            [loading]="cargando()" (onClick)="consultar()" />
        </div>

        @if (error()) {
          <div class="alert-error" style="margin-top:1rem">
            <i class="pi pi-times-circle"></i> {{ error() }}
          </div>
        }
      </div>

      @if (resultado()) {
        <div class="card resultado" style="margin-top:1.5rem">
          <div style="display:flex;justify-content:space-between;align-items:flex-start;flex-wrap:wrap;gap:.5rem">
            <div>
              <p style="font-size:.78rem;color:#6c757d;font-family:monospace;margin-bottom:.25rem">{{ resultado()!['folio'] }}</p>
              <h3 style="margin:0;font-size:1.05rem">{{ resultado()!['convocatoria_titulo'] }}</h3>
              <p style="font-size:.82rem;color:#6c757d;margin-top:.2rem">{{ resultado()!['tipo'] }}</p>
            </div>
            <span class="badge" [class]="'estado-' + resultado()!['estado']">
              {{ estadoLabel(resultado()!['estado']) }}
            </span>
          </div>

          <div class="fechas">
            @if (resultado()!['fecha_envio']) {
              <div><span>Enviada</span><strong>{{ resultado()!['fecha_envio'] | date:'d MMM yyyy' }}</strong></div>
            }
            @if (resultado()!['fecha_resolucion']) {
              <div><span>Resolución</span><strong>{{ resultado()!['fecha_resolucion'] | date:'d MMM yyyy' }}</strong></div>
            }
          </div>

          @if (resultado()!['estado'] === 'RECHAZADA' && resultado()!['motivo_rechazo']) {
            <div class="motivo">
              <strong>Motivo:</strong> {{ resultado()!['motivo_rechazo'] }}
            </div>
          }
          @if (resultado()!['notas_internas_visible']) {
            <div class="notas"><i class="pi pi-info-circle"></i> {{ resultado()!['notas_internas_visible'] }}</div>
          }
        </div>
      }

      <p style="text-align:center;margin-top:2rem;font-size:.82rem;color:#6c757d">
        ¿No encuentras tu folio? Contacta a
        <a href="mailto:admisiones@nevadi.edu.mx" style="color:#8B0000">admisiones@nevadi.edu.mx</a>
      </p>
    </div>
  `,
  styles: [`
    .buscar-card { padding: 1.5rem; }
    .resultado { padding: 1.5rem; }
    .fechas { display: flex; gap: 1.5rem; margin-top: 1rem; font-size: .85rem; }
    .fechas div { display: flex; flex-direction: column; gap: .2rem; }
    .fechas span { color: #6c757d; }
    .motivo { background: #f8d7da; color: #842029; border-radius: 6px; padding: .6rem .75rem; font-size: .88rem; margin-top: 1rem; }
    .notas { background: #e8f4fd; color: #084298; border-radius: 6px; padding: .6rem .75rem; font-size: .88rem; margin-top: .75rem; display: flex; gap: .5rem; }
    .alert-error { background: #f8d7da; color: #842029; border-radius: 8px; padding: .65rem 1rem; font-size: .88rem; display: flex; align-items: center; gap: .5rem; }
    .badge { display: inline-block; padding: .25rem .75rem; border-radius: 20px; font-size: .8rem; font-weight: 700; white-space: nowrap; }
    .estado-BORRADOR        { background: #e9ecef; color: #495057; }
    .estado-ENVIADA         { background: #cfe2ff; color: #084298; }
    .estado-EN_REVISION     { background: #fff3cd; color: #664d03; }
    .estado-PRESELECCIONADA { background: #d1e7dd; color: #0a3622; }
    .estado-ACEPTADA        { background: #198754; color: #fff; }
    .estado-RECHAZADA       { background: #f8d7da; color: #842029; }
    .estado-LISTA_ESPERA    { background: #e2d9f3; color: #41237a; }
  `],
})
export class SeguimientoComponent {
  private readonly api = inject(PortalApiService);

  folio    = '';
  cargando = signal(false);
  error    = signal('');
  resultado = signal<any | null>(null);

  consultar() {
    const f = this.folio.trim().toUpperCase();
    if (!f) { this.error.set('Ingresa un folio válido.'); return; }
    this.error.set('');
    this.resultado.set(null);
    this.cargando.set(true);
    this.api.get<any>(`/seguimiento/${encodeURIComponent(f)}`).subscribe({
      next: r  => { this.resultado.set(r); this.cargando.set(false); },
      error: () => { this.error.set('Folio no encontrado. Verifica el número e intenta de nuevo.'); this.cargando.set(false); },
    });
  }

  estadoLabel(e: string) { return ESTADO_LABEL[e] ?? e; }
}
