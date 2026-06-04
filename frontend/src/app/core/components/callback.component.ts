import { Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { ProgressSpinnerModule } from 'primeng/progressspinner';

@Component({
  selector: 'app-callback',
  standalone: true,
  imports: [ProgressSpinnerModule],
  template: `
    <div style="display:flex;align-items:center;justify-content:center;height:100vh;gap:1rem">
      <p-progressSpinner strokeWidth="4" style="width:50px;height:50px" />
      <span>Iniciando sesión...</span>
    </div>
  `,
})
export class CallbackComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly auth  = inject(AuthService);

  ngOnInit(): void {
    const code = this.route.snapshot.queryParamMap.get('code');
    if (code) this.auth.handleCallback(code);
  }
}
