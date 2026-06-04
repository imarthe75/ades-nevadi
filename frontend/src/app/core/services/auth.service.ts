/**
 * AuthService — gestiona el flujo OIDC Authorization Code con Authentik.
 * El token se obtiene via redirect a auth.ades.setag.mx y se almacena en memoria.
 */
import { Injectable, signal, inject } from '@angular/core';
import { Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { ContextService } from './context.service';
import type { UsuarioMe } from '../models';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly router  = inject(Router);
  private readonly http    = inject(HttpClient);
  private readonly context = inject(ContextService);

  private readonly _token = signal<string | null>(sessionStorage.getItem('ades_token'));
  readonly accessToken = this._token.asReadonly();
  readonly isLoggedIn  = () => !!this._token();

  /** Inicia el flujo OIDC — redirige a Authentik */
  login(): void {
    const params = new URLSearchParams({
      response_type: 'code',
      client_id:     environment.oidcClientId,
      redirect_uri:  environment.oidcRedirectUri,
      scope:         'openid email profile',
      state:         crypto.randomUUID(),
    });
    window.location.href = `${environment.oidcIssuer}authorize/?${params}`;
  }

  /** Maneja el callback de Authentik con el code */
  async handleCallback(code: string): Promise<void> {
    const body = new URLSearchParams({
      grant_type:   'authorization_code',
      code,
      redirect_uri: environment.oidcRedirectUri,
      client_id:    environment.oidcClientId,
    });

    const tokens: any = await this.http.post(
      `${environment.oidcIssuer}token/`,
      body.toString(),
      { headers: { 'Content-Type': 'application/x-www-form-urlencoded' } },
    ).toPromise();

    this.setToken(tokens.access_token);

    // Cargar perfil del usuario en el contexto
    const me = await this.http.get<UsuarioMe>(
      `${environment.apiUrl}/api/v1/auth/me`,
      { headers: { Authorization: `Bearer ${tokens.access_token}` } },
    ).toPromise();
    if (me) this.context.setUsuario(me);

    await this.router.navigate(['/dashboard']);
  }

  logout(): void {
    this._token.set(null);
    sessionStorage.removeItem('ades_token');
    this.context.setUsuario(null);
    window.location.href =
      `${environment.oidcIssuer}end-session/?post_logout_redirect_uri=${environment.oidcRedirectUri}`;
  }

  private setToken(token: string): void {
    this._token.set(token);
    sessionStorage.setItem('ades_token', token);
  }
}
