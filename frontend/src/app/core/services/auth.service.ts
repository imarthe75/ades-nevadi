import { Injectable, signal, inject } from '@angular/core';
import { Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
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

  /** Inicia el flujo OIDC Authorization Code + PKCE */
  async login(): Promise<void> {
    const verifier  = this.generateVerifier();
    const challenge = await this.generateChallenge(verifier);
    const state     = crypto.randomUUID();

    sessionStorage.setItem('ades_pkce_verifier', verifier);
    sessionStorage.setItem('ades_pkce_state', state);

    const params = new URLSearchParams({
      response_type:         'code',
      client_id:             environment.oidcClientId,
      redirect_uri:          environment.oidcRedirectUri,
      scope:                 'openid email profile',
      state,
      code_challenge:        challenge,
      code_challenge_method: 'S256',
    });

    window.location.href = `${environment.oidcAuthorizeUrl}?${params}`;
  }

  /** Intercambia el código por tokens y carga el perfil del usuario */
  async handleCallback(code: string): Promise<void> {
    this._token.set(null);
    sessionStorage.removeItem('ades_token');

    const verifier = sessionStorage.getItem('ades_pkce_verifier') ?? '';
    sessionStorage.removeItem('ades_pkce_verifier');
    sessionStorage.removeItem('ades_pkce_state');

    // Intercambiar code → access_token via proxy backend (evita CORS con Authentik)
    const body = new URLSearchParams({ code, code_verifier: verifier });

    const tokens = await firstValueFrom(
      this.http.post<{ access_token: string; id_token?: string }>(
        `${environment.apiUrl}/api/v1/auth/callback`,
        body.toString(),
        { headers: { 'Content-Type': 'application/x-www-form-urlencoded' } },
      )
    );

    if (!tokens?.access_token) {
      throw new Error('No se recibió access_token de Authentik');
    }

    this.setToken(tokens.access_token);

    // 2. Cargar perfil — si falla no bloquea el login
    try {
      const me = await firstValueFrom(
        this.http.get<UsuarioMe>(`${environment.apiUrl}/api/v1/auth/me`)
      );
      if (me) this.context.setUsuario(me);
    } catch (e) {
      // El token es válido aunque el perfil ADES no exista aún
      console.warn('[ADES] /auth/me no disponible:', e);
    }

    await this.router.navigate(['/dashboard']);
  }

  logout(): void {
    this._token.set(null);
    sessionStorage.removeItem('ades_token');
    sessionStorage.removeItem('ades_pkce_verifier');
    sessionStorage.removeItem('ades_pkce_state');
    sessionStorage.removeItem('ades_plantel');
    sessionStorage.removeItem('ades_ciclo');
    sessionStorage.removeItem('ades_nivel');
    this.context.setUsuario(null);
    const postLogout = encodeURIComponent(environment.oidcRedirectUri);
    window.location.href = `${environment.oidcEndSessionUrl}?post_logout_redirect_uri=${postLogout}`;
  }

  private setToken(token: string): void {
    this._token.set(token);
    sessionStorage.setItem('ades_token', token);
  }

  private generateVerifier(): string {
    const buf = new Uint8Array(32);
    crypto.getRandomValues(buf);
    return btoa(String.fromCharCode(...buf))
      .replace(/\+/g, '-').replace(/\//g, '_').replace(/=/g, '');
  }

  private async generateChallenge(verifier: string): Promise<string> {
    const encoded = new TextEncoder().encode(verifier);
    const hash    = await crypto.subtle.digest('SHA-256', encoded);
    return btoa(String.fromCharCode(...new Uint8Array(hash)))
      .replace(/\+/g, '-').replace(/\//g, '_').replace(/=/g, '');
  }
}
