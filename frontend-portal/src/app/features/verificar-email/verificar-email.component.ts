import { Component, OnInit, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { PortalApiService } from '../../core/services/portal-api.service';

@Component({
  selector: 'app-verificar-email',
  standalone: true,
  imports: [CommonModule, RouterLink, ProgressSpinnerModule],
  template: `
    <div style="min-height:60vh;display:flex;align-items:center;justify-content:center;padding:2rem">
      <div style="text-align:center;max-width:420px">
        @if (verificando()) {
          <p-progressSpinner />
          <p style="color:#6c757d;margin-top:1rem">Verificando tu correo electrónico…</p>
        } @else if (exito()) {
          <div class="result-icon success"><i class="pi pi-check-circle"></i></div>
          <h2 style="color:#198754">¡Correo verificado!</h2>
          <p style="color:#6c757d">Tu cuenta ha sido activada correctamente. Ya puedes iniciar sesión y postularte a nuestras convocatorias.</p>
          <a routerLink="/login" class="btn-nvd" style="display:inline-block;margin-top:1.5rem">
            Iniciar sesión
          </a>
        } @else {
          <div class="result-icon error"><i class="pi pi-times-circle"></i></div>
          <h2 style="color:#8B0000">Enlace inválido o expirado</h2>
          <p style="color:#6c757d">El enlace de verificación no es válido o ya expiró. Los enlaces tienen una vigencia de 24 horas.</p>
          <p style="margin-top:1rem;font-size:.9rem">
            ¿Necesitas un nuevo enlace? Inicia sesión con tu cuenta y te pediremos verificar tu correo nuevamente,
            o contacta a <a href="mailto:admisiones@nevadi.edu.mx" style="color:#8B0000">admisiones@nevadi.edu.mx</a>.
          </p>
          <a routerLink="/login" class="btn-nvd" style="display:inline-block;margin-top:1.5rem">
            Ir a iniciar sesión
          </a>
        }
      </div>
    </div>
  `,
  styles: [`
    .result-icon { font-size: 3.5rem; margin-bottom: 1rem; }
    .success i { color: #198754; }
    .error i   { color: #8B0000; }
    .btn-nvd { background: #8B0000; color: #fff; padding: .6rem 1.75rem; border-radius: 8px; font-weight: 600; text-decoration: none; }
  `],
})
export class VerificarEmailComponent implements OnInit {
  private readonly api   = inject(PortalApiService);
  private readonly route = inject(ActivatedRoute);

  verificando = signal(true);
  exito       = signal(false);

  ngOnInit() {
    const token = this.route.snapshot.queryParamMap.get('token');
    if (!token) { this.verificando.set(false); return; }
    this.api.post<any>('/auth/verificar-email', { token }).subscribe({
      next: () => { this.verificando.set(false); this.exito.set(true); },
      error: () => { this.verificando.set(false); },
    });
  }
}
