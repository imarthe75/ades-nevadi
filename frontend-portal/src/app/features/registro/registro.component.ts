import { Component, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink, Router, ActivatedRoute } from '@angular/router';
import { InputTextModule } from 'primeng/inputtext';
import { PasswordModule } from 'primeng/password';
import { CheckboxModule } from 'primeng/checkbox';
import { ButtonModule } from 'primeng/button';
import { PortalApiService } from '../../core/services/portal-api.service';

@Component({
  selector: 'app-registro',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, InputTextModule, PasswordModule, CheckboxModule, ButtonModule],
  template: `
    <div class="auth-page">
      <div class="auth-card">
        <div class="auth-header">
          <h2>Crear cuenta</h2>
          <p>Regístrate para postularte a nuestras convocatorias</p>
        </div>

        @if (exito()) {
          <div class="alert-success">
            <i class="pi pi-check-circle"></i>
            <div>
              <strong>¡Registro exitoso!</strong>
              <p>Revisa tu correo electrónico y haz clic en el enlace de verificación para activar tu cuenta.</p>
            </div>
          </div>
          <a routerLink="/login" class="btn-back-login">Ir a iniciar sesión</a>
        } @else {
          @if (error()) {
            <div class="alert-error"><i class="pi pi-times-circle"></i> {{ error() }}</div>
          }

          <div class="form-group">
            <label class="required">Nombre completo</label>
            <input pInputText [(ngModel)]="form.nombreCompleto" placeholder="Nombre(s) y apellidos" />
          </div>
          <div class="form-group">
            <label class="required">Correo electrónico</label>
            <input pInputText type="email" [(ngModel)]="form.email" placeholder="correo@ejemplo.com" />
          </div>
          <div class="form-group">
            <label class="required">Contraseña</label>
            <p-password [(ngModel)]="form.password" [feedback]="true" [toggleMask]="true"
              placeholder="Mínimo 8 caracteres" styleClass="w-full" />
          </div>
          <div class="form-group">
            <label>Teléfono (opcional)</label>
            <input pInputText type="tel" [(ngModel)]="form.telefono" placeholder="10 dígitos" maxlength="15" />
          </div>

          <div class="privacidad-check">
            <p-checkbox [(ngModel)]="form.consentimiento" [binary]="true" inputId="priv" />
            <label for="priv">
              He leído y acepto el <a routerLink="/aviso-privacidad" target="_blank">Aviso de Privacidad</a>
              y el tratamiento de mis datos personales conforme a la LFPDPPP.
            </label>
          </div>

          <p-button label="Crear cuenta" icon="pi pi-user-plus" [loading]="cargando()"
            [disabled]="!form.consentimiento"
            styleClass="w-full btn-nvd" (onClick)="registrar()" />

          <p class="auth-link">¿Ya tienes cuenta? <a routerLink="/login">Inicia sesión aquí</a></p>
        }
      </div>
    </div>
  `,
  styles: [`
    .auth-page { min-height: calc(100vh - 130px); display: flex; align-items: center; justify-content: center; padding: 2rem 1rem; background: linear-gradient(135deg, #f8f9fa 0%, #e9ecef 100%); }
    .auth-card { background: #fff; border-radius: 14px; padding: 2rem; width: 100%; max-width: 440px; box-shadow: 0 4px 24px rgba(0,0,0,.1); }
    .auth-header { text-align: center; margin-bottom: 1.5rem; }
    .auth-header h2 { color: #8B0000; }
    .auth-header p { color: #6c757d; font-size: .9rem; margin-top: .25rem; }
    .form-group { margin-bottom: 1rem; }
    .form-group label { display: block; font-size: .85rem; font-weight: 600; margin-bottom: .35rem; color: #495057; }
    .required::after { content: ' *'; color: #8B0000; }
    .privacidad-check { display: flex; align-items: flex-start; gap: .6rem; margin: 1rem 0 1.25rem; font-size: .85rem; line-height: 1.4; }
    .privacidad-check label a { color: #8B0000; }
    .alert-success { display: flex; gap: .75rem; background: #d1e7dd; border-radius: 8px; padding: 1rem; margin-bottom: 1rem; color: #0a3622; }
    .alert-success i { font-size: 1.5rem; color: #198754; flex-shrink: 0; }
    .alert-success strong { display: block; margin-bottom: .2rem; }
    .alert-error { background: #f8d7da; color: #842029; border-radius: 8px; padding: .75rem 1rem; margin-bottom: 1rem; font-size: .88rem; display: flex; align-items: center; gap: .5rem; }
    .btn-back-login { display: block; text-align: center; margin-top: 1rem; padding: .6rem; background: #8B0000; color: #fff; border-radius: 8px; font-weight: 600; text-decoration: none; }
    .auth-link { text-align: center; font-size: .88rem; margin-top: 1rem; color: #6c757d; }
    .auth-link a { color: #8B0000; font-weight: 600; }
    :host ::ng-deep .w-full { width: 100%; }
    :host ::ng-deep .btn-nvd { background: #8B0000 !important; border-color: #8B0000 !important; width: 100%; justify-content: center; }
  `],
})
export class RegistroComponent {
  private readonly api    = inject(PortalApiService);
  private readonly router = inject(Router);
  private readonly route  = inject(ActivatedRoute);

  form = { nombreCompleto: '', email: '', password: '', telefono: '', consentimiento: false };
  cargando = signal(false);
  exito    = signal(false);
  error    = signal('');

  registrar() {
    if (!this.form.nombreCompleto || !this.form.email || !this.form.password) {
      this.error.set('Nombre, correo y contraseña son obligatorios.');
      return;
    }
    if (this.form.password.length < 8) {
      this.error.set('La contraseña debe tener al menos 8 caracteres.');
      return;
    }
    if (!this.form.consentimiento) {
      this.error.set('Debes aceptar el Aviso de Privacidad.');
      return;
    }
    this.error.set('');
    this.cargando.set(true);
    this.api.post<any>('/auth/registro', {
      nombre_completo: this.form.nombreCompleto.trim(),
      email: this.form.email.trim(),
      password: this.form.password,
      telefono: this.form.telefono || null,
      consentimiento_privacidad: true,
      consentimiento_version: '1.0',
    }).subscribe({
      next: () => { this.cargando.set(false); this.exito.set(true); },
      error: (e: any) => {
        this.cargando.set(false);
        this.error.set(e?.error?.mensaje ?? e?.error?.message ?? 'Error al registrar la cuenta.');
      },
    });
  }
}
