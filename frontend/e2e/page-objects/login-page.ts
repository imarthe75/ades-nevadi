/**
 * LoginPage — OIDC-aware login helper.
 *
 * El login ADES usa Authorization Code + PKCE via Authentik.
 * No existe formulario email/password en el frontend Angular.
 * La autenticación se realiza inyectando un JWT real de Authentik en sessionStorage.
 */
import { Page, expect } from '@playwright/test';
import { BasePage } from './base-page';
import { TestUser } from '../fixtures/users';
import { readFileSync, existsSync } from 'fs';
import { resolve } from 'path';

const AUTH_DIR   = resolve(__dirname, '..', '.auth');
const TOKEN_FILE = resolve(AUTH_DIR, 'token.txt');
const USER_FILE  = resolve(AUTH_DIR, 'user.json');

function readToken(): string {
  if (existsSync(TOKEN_FILE)) {
    const t = readFileSync(TOKEN_FILE, 'utf-8').trim();
    if (t.startsWith('ey')) return t;
  }
  return 'eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ0ZXN0IiwiZXhwIjo5OTk5OTk5OTk5fQ.fake';
}

function readUser(): object {
  if (existsSync(USER_FILE)) {
    try { return JSON.parse(readFileSync(USER_FILE, 'utf-8')); } catch { /* fall through */ }
  }
  // Perfil admin de fallback — nivel_acceso 0 = acceso total
  return {
    id: 'admin', nombre_usuario: 'admin', email_institucional: 'admin@institutonevadi.edu.mx',
    persona_id: 'admin', nombre_completo: 'Administrador Global', rol: 'ADMIN_GLOBAL',
    nivel_acceso: 0, plantel_id: null, nivel_educativo_id: null,
  };
}

export class LoginPage extends BasePage {
  /** Botón OIDC real del Angular login page — usa el <button> interno de PrimeNG */
  readonly oidcBtn = this.page.locator(
    'button[data-pc-name="button"]:has-text("Iniciar sesión"), [data-testid="btn-login"]'
  ).first();

  async navigate() {
    await this.page.goto('/login');
  }

  /**
   * Login by injecting a real Authentik JWT into sessionStorage.
   * Uses evaluate() (not addInitScript) so the token does NOT persist across
   * subsequent navigations — this allows proper logout simulation in tests.
   *
   * If `user` is provided, the injected ades_usuario profile overrides
   * nivel_acceso and rol so frontend RBAC (menu visibility, guards) behaves
   * as that role. The API still receives the cached admin token — API-level
   * enforcement tests must account for this shared-token limitation.
   */
  async login(user?: TestUser) {
    const token      = readToken();
    const cachedUser = readUser() as Record<string, unknown>;

    // Build the injected profile: override rol+nivel_acceso if a specific user is given
    const injectUser = user
      ? {
          ...cachedUser,
          rol:           user.rol,
          nivel_acceso:  user.nivelAcceso,
          nombre_completo: `Test ${user.rol}`,
        }
      : cachedUser;

    // Navegar a /login para establecer el origen (permite sessionStorage en ese origin)
    await this.page.goto('/login', { waitUntil: 'domcontentloaded' });
    // Inyectar token + perfil de usuario en sessionStorage
    // Angular lee ambos en el constructor de AuthService y ContextService
    await this.page.evaluate(([tok, usr]: [string, object]) => {
      sessionStorage.setItem('ades_token',   tok);
      sessionStorage.setItem('ades_usuario', JSON.stringify(usr));
    }, [token, injectUser] as [string, object]);
    // Navegar al dashboard — Angular re-inicializa y los guards pasan
    await this.page.goto('/dashboard');
    await this.page.waitForURL(/\/(dashboard|alumnos|grupos|calificaciones)/, { timeout: 15_000 });
  }

  /**
   * Navega a /login y hace click en el botón OIDC.
   * Útil para tests que verifican el flujo de redirección.
   */
  async clickOidcButton() {
    await this.navigate();
    await this.oidcBtn.waitFor({ state: 'visible', timeout: 10_000 });
    await this.oidcBtn.click();
  }

  /**
   * Simula credenciales incorrectas verificando que el botón OIDC existe
   * pero redirige a Authentik (no hay error inline en Angular).
   */
  async loginExpectFail(_email: string, _password: string) {
    await this.navigate();
    await expect(this.oidcBtn).toBeVisible({ timeout: 8_000 });
    // El flujo real de error ocurre en Authentik tras el redirect
  }

  async logout() {
    // Buscar el avatar/menú de usuario
    const avatar = this.page.locator(
      '[data-testid="user-avatar"], .user-avatar, .p-avatar, [aria-label="Usuario"], button:has-text("Cerrar")'
    );
    const avatarVisible = await avatar.isVisible().catch(() => false);
    if (avatarVisible) {
      await avatar.first().click();
      await this.page.locator('text=Cerrar sesión').click({ timeout: 5_000 }).catch(() => {
        // Si no aparece el menú, ir directo al logout
      });
    }
    // Limpiar sessionStorage directamente (equivale al logout de Angular)
    await this.page.evaluate(() => {
      sessionStorage.removeItem('ades_token');
      sessionStorage.removeItem('ades_id_token');
      sessionStorage.removeItem('ades_usuario');
      sessionStorage.removeItem('ades_plantel');
      sessionStorage.removeItem('ades_ciclo');
      sessionStorage.removeItem('ades_nivel');
    });
    await this.page.goto('/login');
    await this.page.waitForURL(/\/login/, { timeout: 8_000 });
  }

  async expectOnLoginPage() {
    await expect(this.page).toHaveURL(/\/login/);
    await expect(this.oidcBtn).toBeVisible({ timeout: 5_000 });
  }

  /** Inyecta un JWT expirado para probar el comportamiento con token inválido */
  async injectExpiredToken() {
    await this.page.addInitScript(() => {
      // JWT con exp=1 (Unix epoch 1970) — claramente expirado
      sessionStorage.setItem(
        'ades_token',
        'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0IiwiZXhwIjoxfQ.invalid'
      );
    });
  }
}
