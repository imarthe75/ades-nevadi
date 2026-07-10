import { Component, OnDestroy, ChangeDetectionStrategy, inject } from '@angular/core';
import { ButtonModule } from 'primeng/button';
import { AuthService } from '../services/auth.service';

/**
 * Pantalla de inicio de sesión de ADES.
 * Muestra el logo institucional y el botón que inicia el flujo PKCE OIDC
 * contra Authentik. No gestiona credenciales directamente; delega a AuthService.
 * Accesible sin autenticación (ruta pública `/login`).
 */
@Component({
  selector: 'app-login',
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
  imports: [ButtonModule],
  template: `
    <div class="login-wrapper">
      <div class="login-card">

        <!-- Logo oficial Instituto Nevadi -->
        <div class="login-logo">
          <img src="/nevadi-logo.jpg" alt="Instituto Nevadi" class="inst-logo" loading="eager" />
        </div>

        <h1>ADES</h1>
        <p class="brand-sub">Instituto Nevadi</p>
        <p class="brand-desc">Sistema de Administración Escolar</p>

        <p-button
          label="Iniciar sesión con cuenta institucional"
          icon="pi pi-sign-in"
          size="large"
          styleClass="w-full"
          (onClick)="auth.login()"
        />

        <p class="login-footer">
          Acceso exclusivo para personal y comunidad del Instituto Nevadi
        </p>
      </div>
    </div>
  `,
  styles: [`
    /* ── Wrapper: fondo oscuro del triada (H=235°, muy desaturado) ── */
    .login-wrapper {
      display: flex;
      align-items: center;
      justify-content: center;
      min-height: 100vh;
      background: linear-gradient(160deg, #141929 0%, #1E2940 50%, #161E30 100%);
    }

    /* ── Card flotante ── */
    .login-card {
      background: #FFFFFF;
      border-radius: 16px;
      padding: 3rem 2.5rem 2.5rem;
      width: 420px;
      max-width: calc(100vw - 2rem);
      text-align: center;
      box-shadow:
        0 0 0 1px rgba(0,0,0,.06),
        0 4px 6px rgba(0,0,0,.08),
        0 20px 40px rgba(0,0,0,.24);
    }

    /* ── Logo ── */
    .login-logo {
      margin-bottom: 1.5rem;
      display: flex;
      justify-content: center;
    }
    .inst-logo {
      max-width: 180px;
      max-height: 80px;
      object-fit: contain;
    }

    /* ── Tipografía ── */
    h1 {
      font-family: 'Jost', sans-serif;
      font-size: 2.75rem;
      font-weight: 800;
      color: #1A1F2E;         /* gris carbón, matiz frío — triada H=235° desaturado */
      letter-spacing: -0.04em;
      line-height: 1;
      margin: 0 0 0.25rem;
    }

    .brand-sub {
      font-family: 'Jost', sans-serif;
      font-size: 1rem;
      font-weight: 600;
      color: #D02030;          /* rojo Nevadi */
      letter-spacing: 0.06em;
      text-transform: uppercase;
      margin: 0 0 0.5rem;
    }

    .brand-desc {
      font-size: 0.85rem;
      color: #64748B;
      margin: 0 0 2rem;
    }

    /* ── Botón ── */
    :host ::ng-deep .p-button {
      width: 100%;
      justify-content: center;
      font-weight: 600;
    }

    /* ── Footer note ── */
    .login-footer {
      font-size: 0.75rem;
      color: #94A3B8;
      margin: 1.5rem 0 0;
      line-height: 1.4;
    }
  `],
})
export class LoginComponent implements OnDestroy {
  readonly auth = inject(AuthService);

  ngOnDestroy(): void {}
}
