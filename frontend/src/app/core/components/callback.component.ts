import { Component, OnInit, OnDestroy, ChangeDetectionStrategy, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { ProgressSpinnerModule } from 'primeng/progressspinner';

/**
 * Maneja el redirect de retorno del flujo OIDC Authorization Code desde Authentik.
 * Lee el parámetro `code` de la URL, lo intercambia por tokens vía AuthService
 * y redirige al dashboard. Muestra un spinner durante el proceso y un mensaje
 * de error si Authentik devuelve `error` en los query params o si el intercambio falla.
 */
@Component({
  selector: 'app-callback',
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
  imports: [ProgressSpinnerModule],
  template: `
    <div class="cb-wrapper">
      @if (!error()) {
        <p-progressSpinner strokeWidth="3" styleClass="cb-spinner" />
        <span class="cb-label">Iniciando sesión…</span>
      } @else {
        <div class="cb-error">
          <i class="pi pi-times-circle" style="font-size:2rem;color:var(--nevadi-red)"></i>
          <p>{{ error() }}</p>
          <a href="/login">Volver al inicio de sesión</a>
        </div>
      }
    </div>
  `,
  styles: [`
    .cb-wrapper {
      display: flex; flex-direction: column;
      align-items: center; justify-content: center;
      height: 100vh; gap: 1rem;
      background: linear-gradient(160deg, #141929 0%, #1E2940 100%);
      color: #fff;
    }
    .cb-label { font-size: 0.95rem; color: var(--text-muted); }
    .cb-error { text-align: center; color: #fff; }
    .cb-error a { color: var(--nevadi-red); text-decoration: none; margin-top: .5rem; display: block; }
  `],
})
export class CallbackComponent implements OnInit, OnDestroy {
  private readonly route  = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly auth   = inject(AuthService);

  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    const params = this.route.snapshot.queryParamMap;
    const oidcError = params.get('error');
    const code      = params.get('code');

    if (oidcError) {
      const desc = params.get('error_description') ?? oidcError;
      this.error.set(desc.replace(/_/g, ' '));
      return;
    }

    if (!code) {
      this.error.set('No se recibió código de autorización.');
      return;
    }

    this.auth.handleCallback(code).catch(err => {
      console.error('[ADES] callback error:', err);
      this.error.set('Error al completar el inicio de sesión. Inténtalo de nuevo.');
    });
  }

  ngOnDestroy(): void {}
}
