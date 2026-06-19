# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: 14-a11y.spec.ts >> A. Audit axe-core por módulo >> A11Y-01 | /dashboard (autenticado) — sin violaciones críticas @smoke
- Location: e2e/tests/14-a11y.spec.ts:28:7

# Error details

```
Error: page.goto: net::ERR_CONNECTION_REFUSED at http://localhost:4200/login
Call log:
  - navigating to "http://localhost:4200/login", waiting until "domcontentloaded"

```

# Test source

```ts
  1   | /**
  2   |  * LoginPage — OIDC-aware login helper.
  3   |  *
  4   |  * El login ADES usa Authorization Code + PKCE via Authentik.
  5   |  * No existe formulario email/password en el frontend Angular.
  6   |  * La autenticación se realiza inyectando un JWT real de Authentik en sessionStorage.
  7   |  */
  8   | import { Page, expect } from '@playwright/test';
  9   | import { BasePage } from './base-page';
  10  | import { TestUser } from '../fixtures/users';
  11  | import { readFileSync, existsSync } from 'fs';
  12  | import { resolve } from 'path';
  13  | 
  14  | const AUTH_DIR   = resolve(__dirname, '..', '.auth');
  15  | const TOKEN_FILE = resolve(AUTH_DIR, 'token.txt');
  16  | const USER_FILE  = resolve(AUTH_DIR, 'user.json');
  17  | 
  18  | function readToken(): string {
  19  |   if (existsSync(TOKEN_FILE)) {
  20  |     const t = readFileSync(TOKEN_FILE, 'utf-8').trim();
  21  |     if (t.startsWith('ey')) return t;
  22  |   }
  23  |   return 'eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ0ZXN0IiwiZXhwIjo5OTk5OTk5OTk5fQ.fake';
  24  | }
  25  | 
  26  | function readUser(): object {
  27  |   if (existsSync(USER_FILE)) {
  28  |     try { return JSON.parse(readFileSync(USER_FILE, 'utf-8')); } catch { /* fall through */ }
  29  |   }
  30  |   // Perfil admin de fallback — nivel_acceso 0 = acceso total
  31  |   return {
  32  |     id: 'admin', nombre_usuario: 'admin', email_institucional: 'admin@institutonevadi.edu.mx',
  33  |     persona_id: 'admin', nombre_completo: 'Administrador Global', rol: 'ADMIN_GLOBAL',
  34  |     nivel_acceso: 0, plantel_id: null, nivel_educativo_id: null,
  35  |   };
  36  | }
  37  | 
  38  | export class LoginPage extends BasePage {
  39  |   /** Botón OIDC real del Angular login page — usa el <button> interno de PrimeNG */
  40  |   readonly oidcBtn = this.page.locator(
  41  |     'button[data-pc-name="button"]:has-text("Iniciar sesión"), [data-testid="btn-login"]'
  42  |   ).first();
  43  | 
  44  |   async navigate() {
  45  |     await this.page.goto('/login');
  46  |   }
  47  | 
  48  |   /**
  49  |    * Login by injecting a real Authentik JWT into sessionStorage.
  50  |    * Uses evaluate() (not addInitScript) so the token does NOT persist across
  51  |    * subsequent navigations — this allows proper logout simulation in tests.
  52  |    *
  53  |    * If `user` is provided, the injected ades_usuario profile overrides
  54  |    * nivel_acceso and rol so frontend RBAC (menu visibility, guards) behaves
  55  |    * as that role. The API still receives the cached admin token — API-level
  56  |    * enforcement tests must account for this shared-token limitation.
  57  |    */
  58  |   async login(user?: TestUser) {
  59  |     const token      = readToken();
  60  |     const cachedUser = readUser() as Record<string, unknown>;
  61  | 
  62  |     // Build the injected profile: override rol+nivel_acceso if a specific user is given
  63  |     const injectUser = user
  64  |       ? {
  65  |           ...cachedUser,
  66  |           rol:           user.rol,
  67  |           nivel_acceso:  user.nivelAcceso,
  68  |           nombre_completo: `Test ${user.rol}`,
  69  |         }
  70  |       : cachedUser;
  71  | 
  72  |     // Navegar a /login para establecer el origen (permite sessionStorage en ese origin)
> 73  |     await this.page.goto('/login', { waitUntil: 'domcontentloaded' });
      |                     ^ Error: page.goto: net::ERR_CONNECTION_REFUSED at http://localhost:4200/login
  74  |     // Inyectar token + perfil de usuario en sessionStorage
  75  |     // Angular lee ambos en el constructor de AuthService y ContextService
  76  |     await this.page.evaluate(([tok, usr]: [string, object]) => {
  77  |       sessionStorage.setItem('ades_token',   tok);
  78  |       sessionStorage.setItem('ades_usuario', JSON.stringify(usr));
  79  |     }, [token, injectUser] as [string, object]);
  80  |     // Navegar al dashboard — Angular re-inicializa y los guards pasan
  81  |     await this.page.goto('/dashboard');
  82  |     await this.page.waitForURL(/\/(dashboard|alumnos|grupos|calificaciones)/, { timeout: 15_000 });
  83  |   }
  84  | 
  85  |   /**
  86  |    * Navega a /login y hace click en el botón OIDC.
  87  |    * Útil para tests que verifican el flujo de redirección.
  88  |    */
  89  |   async clickOidcButton() {
  90  |     await this.navigate();
  91  |     await this.oidcBtn.waitFor({ state: 'visible', timeout: 10_000 });
  92  |     await this.oidcBtn.click();
  93  |   }
  94  | 
  95  |   /**
  96  |    * Simula credenciales incorrectas verificando que el botón OIDC existe
  97  |    * pero redirige a Authentik (no hay error inline en Angular).
  98  |    */
  99  |   async loginExpectFail(_email: string, _password: string) {
  100 |     await this.navigate();
  101 |     await expect(this.oidcBtn).toBeVisible({ timeout: 8_000 });
  102 |     // El flujo real de error ocurre en Authentik tras el redirect
  103 |   }
  104 | 
  105 |   async logout() {
  106 |     // Buscar el avatar/menú de usuario
  107 |     const avatar = this.page.locator(
  108 |       '[data-testid="user-avatar"], .user-avatar, .p-avatar, [aria-label="Usuario"], button:has-text("Cerrar")'
  109 |     );
  110 |     const avatarVisible = await avatar.isVisible().catch(() => false);
  111 |     if (avatarVisible) {
  112 |       await avatar.first().click();
  113 |       await this.page.locator('text=Cerrar sesión').click({ timeout: 5_000 }).catch(() => {
  114 |         // Si no aparece el menú, ir directo al logout
  115 |       });
  116 |     }
  117 |     // Limpiar sessionStorage directamente (equivale al logout de Angular)
  118 |     await this.page.evaluate(() => {
  119 |       sessionStorage.removeItem('ades_token');
  120 |       sessionStorage.removeItem('ades_id_token');
  121 |       sessionStorage.removeItem('ades_usuario');
  122 |       sessionStorage.removeItem('ades_plantel');
  123 |       sessionStorage.removeItem('ades_ciclo');
  124 |       sessionStorage.removeItem('ades_nivel');
  125 |     });
  126 |     await this.page.goto('/login');
  127 |     await this.page.waitForURL(/\/login/, { timeout: 8_000 });
  128 |   }
  129 | 
  130 |   async expectOnLoginPage() {
  131 |     await expect(this.page).toHaveURL(/\/login/);
  132 |     await expect(this.oidcBtn).toBeVisible({ timeout: 5_000 });
  133 |   }
  134 | 
  135 |   /** Inyecta un JWT expirado para probar el comportamiento con token inválido */
  136 |   async injectExpiredToken() {
  137 |     await this.page.addInitScript(() => {
  138 |       // JWT con exp=1 (Unix epoch 1970) — claramente expirado
  139 |       sessionStorage.setItem(
  140 |         'ades_token',
  141 |         'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0IiwiZXhwIjoxfQ.invalid'
  142 |       );
  143 |     });
  144 |   }
  145 | }
  146 | 
```