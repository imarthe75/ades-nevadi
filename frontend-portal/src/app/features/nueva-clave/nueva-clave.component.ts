import { Component, OnInit, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink, Router } from '@angular/router';
import { PasswordModule } from 'primeng/password';
import { ButtonModule } from 'primeng/button';
import { PortalApiService } from '../../core/services/portal-api.service';

@Component({
  selector: 'app-nueva-clave',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, PasswordModule, ButtonModule],
  template: `
    <div style="min-height:60vh;display:flex;align-items:center;justify-content:center;padding:2rem">
      <div class="card" style="width:100%;max-width:400px;padding:2rem">
        <div style="text-align:center;margin-bottom:1.5rem">
          <h2 style="color:#8B0000">Nueva contraseña</h2>
          <p style="color:#6c757d;font-size:.9rem">Ingresa y confirma tu nueva contraseña.</p>
        </div>

        @if (!tokenValido()) {
          <div class="alert-error">
            <i class="pi pi-times-circle"></i>
            El enlace de recuperación no es válido o expiró. Solicita uno nuevo desde
            <a routerLink="/login" style="color:#842029">Iniciar sesión</a>.
          </div>
        } @else if (exito()) {
          <div class="alert-success">
            <i class="pi pi-check-circle" style="font-size:1.5rem;color:#198754;flex-shrink:0"></i>
            <div>
              <strong>Contraseña actualizada</strong>
              <p style="font-size:.88rem;margin:.25rem 0">Ya puedes iniciar sesión con tu nueva contraseña.</p>
              <a routerLink="/login" style="color:#0a3622;font-weight:600">Ir a iniciar sesión →</a>
            </div>
          </div>
        } @else {
          @if (error()) {
            <div class="alert-error" style="margin-bottom:1rem">
              <i class="pi pi-times-circle"></i> {{ error() }}
            </div>
          }

          <div class="form-group">
            <label class="required">Nueva contraseña</label>
            <p-password [(ngModel)]="password" [feedback]="true" [toggleMask]="true"
              placeholder="Mínimo 8 caracteres" styleClass="w-full" />
          </div>
          <div class="form-group">
            <label class="required">Confirmar contraseña</label>
            <p-password [(ngModel)]="confirmar" [feedback]="false" [toggleMask]="true"
              placeholder="Repite la contraseña" styleClass="w-full" />
          </div>

          <p-button label="Guardar nueva contraseña" icon="pi pi-lock"
            [style]="{'background':'#8B0000','border-color':'#8B0000'}"
            [loading]="cargando()" styleClass="w-full" (onClick)="guardar()" />
        }
      </div>
    </div>
  `,
  styles: [`
    .form-group { margin-bottom: 1rem; }
    .form-group label { display: block; font-size: .85rem; font-weight: 600; margin-bottom: .35rem; color: #495057; }
    .required::after { content: ' *'; color: #8B0000; }
    .alert-error { background: #f8d7da; color: #842029; border-radius: 8px; padding: .65rem 1rem; font-size: .88rem; display: flex; align-items: center; gap: .5rem; }
    .alert-success { display: flex; gap: .75rem; background: #d1e7dd; border-radius: 8px; padding: 1rem; color: #0a3622; }
    :host ::ng-deep .w-full { width: 100%; }
  `],
})
export class NuevaClaveComponent implements OnInit {
  private readonly api    = inject(PortalApiService);
  private readonly route  = inject(ActivatedRoute);
  private readonly router = inject(Router);

  password  = '';
  confirmar = '';
  token     = '';

  tokenValido = signal(true);
  cargando    = signal(false);
  exito       = signal(false);
  error       = signal('');

  ngOnInit() {
    this.token = this.route.snapshot.queryParamMap.get('token') ?? '';
    if (!this.token) this.tokenValido.set(false);
  }

  guardar() {
    if (this.password.length < 8) { this.error.set('La contraseña debe tener al menos 8 caracteres.'); return; }
    if (this.password !== this.confirmar) { this.error.set('Las contraseñas no coinciden.'); return; }
    this.error.set('');
    this.cargando.set(true);
    this.api.post<any>('/auth/nueva-clave', { token: this.token, nueva_password: this.password }).subscribe({
      next: () => { this.cargando.set(false); this.exito.set(true); },
      error: (e: any) => {
        this.cargando.set(false);
        if (e?.status === 400 || e?.status === 404) { this.tokenValido.set(false); }
        else { this.error.set(e?.error?.mensaje ?? 'Error al actualizar la contraseña.'); }
      },
    });
  }
}
