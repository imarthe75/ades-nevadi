import { Component, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink, Router, ActivatedRoute } from '@angular/router';
import { InputTextModule } from 'primeng/inputtext';
import { PasswordModule } from 'primeng/password';
import { ButtonModule } from 'primeng/button';
import { PortalAuthService } from '../../core/services/portal-auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, InputTextModule, PasswordModule, ButtonModule],
  template: `
    <div class="auth-page">
      <div class="auth-card">
        <div class="auth-header">
          <h2>Iniciar sesión</h2>
          <p>Accede a tu cuenta para gestionar tus postulaciones</p>
        </div>

        @if (error()) {
          <div class="alert-error"><i class="pi pi-times-circle"></i> {{ error() }}</div>
        }

        <div class="form-group">
          <label class="required">Correo electrónico</label>
          <input pInputText type="email" [(ngModel)]="email" placeholder="correo@ejemplo.com"
            (keyup.enter)="login()" />
        </div>
        <div class="form-group">
          <label class="required">Contraseña</label>
          <p-password [(ngModel)]="password" [feedback]="false" [toggleMask]="true"
            placeholder="Tu contraseña" styleClass="w-full" (keyup.enter)="login()" />
        </div>

        <p-button label="Entrar" icon="pi pi-sign-in" [loading]="cargando()"
          styleClass="w-full btn-nvd" (onClick)="login()" />

        <div class="auth-links">
          <a routerLink="/registro">Crear cuenta nueva</a>
          <a (click)="recuperar()" style="cursor:pointer">¿Olvidaste tu contraseña?</a>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .auth-page { min-height: calc(100vh - 130px); display: flex; align-items: center; justify-content: center; padding: 2rem 1rem; background: linear-gradient(135deg, #f8f9fa 0%, #e9ecef 100%); }
    .auth-card { background: #fff; border-radius: 14px; padding: 2rem; width: 100%; max-width: 400px; box-shadow: 0 4px 24px rgba(0,0,0,.1); }
    .auth-header { text-align: center; margin-bottom: 1.5rem; }
    .auth-header h2 { color: #8B0000; }
    .auth-header p { color: #6c757d; font-size: .9rem; margin-top: .25rem; }
    .form-group { margin-bottom: 1rem; }
    .form-group label { display: block; font-size: .85rem; font-weight: 600; margin-bottom: .35rem; color: #495057; }
    .required::after { content: ' *'; color: #8B0000; }
    .alert-error { background: #f8d7da; color: #842029; border-radius: 8px; padding: .75rem 1rem; margin-bottom: 1rem; font-size: .88rem; display: flex; align-items: center; gap: .5rem; }
    .auth-links { display: flex; justify-content: space-between; margin-top: 1rem; font-size: .85rem; }
    .auth-links a { color: #8B0000; }
    :host ::ng-deep .w-full { width: 100%; }
    :host ::ng-deep .btn-nvd { background: #8B0000 !important; border-color: #8B0000 !important; width: 100%; justify-content: center; }
  `],
})
export class LoginComponent {
  private readonly auth   = inject(PortalAuthService);
  private readonly router = inject(Router);
  private readonly route  = inject(ActivatedRoute);

  email    = '';
  password = '';
  cargando = signal(false);
  error    = signal('');

  login() {
    if (!this.email || !this.password) { this.error.set('Correo y contraseña son requeridos.'); return; }
    this.error.set('');
    this.cargando.set(true);
    this.auth.login(this.email.trim(), this.password).subscribe({
      next: () => {
        this.cargando.set(false);
        const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl') ?? '/mis-postulaciones';
        this.router.navigateByUrl(returnUrl);
      },
      error: (e: any) => {
        this.cargando.set(false);
        this.error.set(e?.error?.mensaje ?? e?.error?.message ?? 'Credenciales incorrectas.');
      },
    });
  }

  recuperar() {
    const em = prompt('Ingresa tu correo para recuperar tu contraseña:');
    if (!em) return;
    fetch('/api/portal/auth/recuperar', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: em }),
    }).then(() => alert('Si el correo existe en nuestro sistema, recibirás instrucciones para recuperar tu contraseña.'));
  }
}
