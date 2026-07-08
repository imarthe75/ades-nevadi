import { Injectable, signal, inject } from '@angular/core';
import { Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ContextService } from './context.service';
import type { UsuarioMe } from '../models';

/**
 * Renovar el access token con este margen ANTES de que expire (ms).
 * Aumentado a 60 segundos para evitar race conditions entre la expiración
 * del token y la finalización de una request. Authentik: access_token_validity=5min.
 */
const REFRESH_MARGIN_MS = 60_000; // 1 minuto antes de expiración

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly router = inject(Router);
  private readonly http = inject(HttpClient);
  private readonly context = inject(ContextService);

  private readonly _token = signal<string | null>(sessionStorage.getItem('ades_token'));
  private readonly _idToken = signal<string | null>(sessionStorage.getItem('ades_id_token'));
  readonly accessToken = this._token.asReadonly();
  readonly idToken = this._idToken.asReadonly();
  readonly isLoggedIn = () => !!this._token();

  private refreshTimer: ReturnType<typeof setTimeout> | null = null;
  /** Deduplica llamadas de refresh concurrentes (varias peticiones 401 a la vez). */
  private refreshInFlight: Promise<string | null> | null = null;

  constructor() {
    const expiresAt = Number(sessionStorage.getItem('ades_expires_at') ?? 0);
    if (this._token() && expiresAt) {
      this.scheduleRefresh(expiresAt - Date.now());
    }
  }

  /** Inicia el flujo OIDC Authorization Code + PKCE */
  async login(): Promise<void> {
    const verifier = this.generateVerifier();
    const challenge = await this.generateChallenge(verifier);
    const state = crypto.randomUUID();

    sessionStorage.setItem('ades_pkce_verifier', verifier);
    sessionStorage.setItem('ades_pkce_state', state);

    const params = new URLSearchParams({
      response_type: 'code',
      client_id: environment.oidcClientId,
      redirect_uri: environment.oidcRedirectUri,
      scope: 'openid email profile',
      state,
      code_challenge: challenge,
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
      this.http.post<{ access_token: string; id_token?: string; refresh_token?: string; expires_in?: number }>(
        `${environment.apiUrl}/api/v1/auth/callback`,
        body.toString(),
        { headers: { 'Content-Type': 'application/x-www-form-urlencoded' } },
      )
    );

    if (!tokens?.access_token) {
      throw new Error('No se recibió access_token de Authentik');
    }

    this.setToken(tokens.access_token, tokens.id_token ?? null, tokens.refresh_token ?? null, tokens.expires_in ?? null);

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

  /**
   * Renueva el access token con el refresh_token guardado. Deduplica llamadas
   * concurrentes (varias peticiones en paralelo recibiendo 401 a la vez).
   * Devuelve el nuevo access_token, o null si no se pudo renovar (requiere re-login).
   */
  refreshAccessToken(): Promise<string | null> {
    if (this.refreshInFlight) return this.refreshInFlight;

    const refreshToken = sessionStorage.getItem('ades_refresh_token');
    if (!refreshToken) return Promise.resolve(null);

    const body = new URLSearchParams({ refresh_token: refreshToken });
    this.refreshInFlight = firstValueFrom(
      this.http.post<{ access_token: string; id_token?: string; refresh_token?: string; expires_in?: number }>(
        `${environment.apiUrl}/api/v1/auth/refresh`,
        body.toString(),
        { headers: { 'Content-Type': 'application/x-www-form-urlencoded' } },
      )
    )
      .then((tokens) => {
        if (!tokens?.access_token) return null;
        this.setToken(tokens.access_token, tokens.id_token ?? null, tokens.refresh_token ?? refreshToken, tokens.expires_in ?? null);
        return tokens.access_token;
      })
      .catch(() => null)
      .finally(() => { this.refreshInFlight = null; });

    return this.refreshInFlight;
  }

  /** Limpia la sesión local y redirige a login sin pasar por Authentik (sesión ya perdida). */
  forceReLogin(): void {
    this.clearSession();
    this.router.navigate(['/login']);
  }

  /**
   * Programa el refresh automático del token.
   * Si ya estamos dentro del margen de refresh (menos de 1 min de vida restante),
   * se ejecuta inmediatamente. De lo contrario, se programa para hacerlo
   * 1 minuto antes de la expiración.
   */
  private scheduleRefresh(msUntilExpiry: number): void {
    if (this.refreshTimer) clearTimeout(this.refreshTimer);

    const delay = Math.max(msUntilExpiry - REFRESH_MARGIN_MS, 0);

    // Si el token ya casi expira (< 30 seg), refresh inmediatamente
    if (delay < 30_000) {
      this.refreshAccessToken().catch(err => {
        console.warn('[AUTH] Immediate refresh failed:', err);
      });
    } else {
      // Programar para después
      this.refreshTimer = setTimeout(() => {
        this.refreshAccessToken().catch(err => {
          console.warn('[AUTH] Scheduled refresh failed, but retrying on 401:', err);
        });
      }, delay);
    }
  }

  private clearSession(): void {
    if (this.refreshTimer) {
      clearTimeout(this.refreshTimer);
      this.refreshTimer = null;
    }
    this._token.set(null);
    this._idToken.set(null);
    sessionStorage.removeItem('ades_token');
    sessionStorage.removeItem('ades_id_token');
    sessionStorage.removeItem('ades_refresh_token');
    sessionStorage.removeItem('ades_expires_at');
    sessionStorage.removeItem('ades_pkce_verifier');
    sessionStorage.removeItem('ades_pkce_state');
    sessionStorage.removeItem('ades_plantel');
    sessionStorage.removeItem('ades_ciclo');
    sessionStorage.removeItem('ades_nivel');
    this.context.setUsuario(null);
  }

  logout(): void {
    this.clearSession();
    // Redirige a la página de inicio después del logout
    const homeUrl = environment.apiUrl.endsWith('/') ? environment.apiUrl : environment.apiUrl + '/';
    const params = new URLSearchParams({ post_logout_redirect_uri: homeUrl });
    const idToken = this._idToken();
    if (idToken) {
      params.set('id_token_hint', idToken);
    }
    const endSessionUrl = environment.oidcEndSessionUrl;
    window.location.href = `${endSessionUrl}?${params.toString()}`;
  }

  private setToken(
    token: string,
    idToken: string | null = null,
    refreshToken: string | null = null,
    expiresInSeconds: number | null = null,
  ): void {
    this._token.set(token);
    sessionStorage.setItem('ades_token', token);
    if (idToken) {
      this._idToken.set(idToken);
      sessionStorage.setItem('ades_id_token', idToken);
    } else {
      this._idToken.set(null);
      sessionStorage.removeItem('ades_id_token');
    }
    if (refreshToken) {
      sessionStorage.setItem('ades_refresh_token', refreshToken);
    }
    // Authentik: access_token_validity=minutes=5. Default conservador si no viene expires_in.
    const msUntilExpiry = (expiresInSeconds ?? 300) * 1000;
    const expiresAt = Date.now() + msUntilExpiry;
    sessionStorage.setItem('ades_expires_at', String(expiresAt));
    this.scheduleRefresh(msUntilExpiry);
  }

  private generateVerifier(): string {
    const buf = new Uint8Array(32);
    crypto.getRandomValues(buf);
    return btoa(String.fromCharCode(...buf))
      .replace(/\+/g, '-').replace(/\//g, '_').replace(/=/g, '');
  }

  private async generateChallenge(verifier: string): Promise<string> {
    const encoded = new TextEncoder().encode(verifier);
    const hash = await crypto.subtle.digest('SHA-256', encoded);
    return btoa(String.fromCharCode(...new Uint8Array(hash)))
      .replace(/\+/g, '-').replace(/\//g, '_').replace(/=/g, '');
  }
}
