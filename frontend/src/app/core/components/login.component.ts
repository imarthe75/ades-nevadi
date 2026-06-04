import { Component, inject } from '@angular/core';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { AuthService } from '../services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [ButtonModule, CardModule],
  template: `
    <div class="login-wrapper">
      <p-card styleClass="login-card">
        <div class="login-content">
          <h1>ADES</h1>
          <p>Sistema Escolar Instituto Nevadi</p>
          <p-button
            label="Iniciar sesión con cuenta institucional"
            icon="pi pi-sign-in"
            size="large"
            (onClick)="auth.login()"
          />
        </div>
      </p-card>
    </div>
  `,
  styles: [`
    .login-wrapper { display:flex; align-items:center; justify-content:center; height:100vh; background:#f0f4f8; }
    .login-card { width:420px; }
    .login-content { text-align:center; padding:2rem 1rem; }
    h1 { font-size:2.5rem; color:#1d4ed8; margin-bottom:0.25rem; }
    p { color:#64748b; margin-bottom:2rem; }
  `],
})
export class LoginComponent {
  readonly auth = inject(AuthService);
}
