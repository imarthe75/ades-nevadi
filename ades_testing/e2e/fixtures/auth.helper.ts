import { Page, expect } from '@playwright/test';

/**
 * AuthHelper — Reusable authentication fixtures for ADES E2E tests
 * Handles: login, logout, token refresh, permission checks
 */
export class AuthHelper {
  constructor(private page: Page) {}

  /**
   * Login with email and password
   * Supports both direct form login and OAuth/OIDC (Authentik)
   * @param email User email (e.g., admin@ades.test)
   * @param password User password
   * @throws Error if login fails
   */
  async login(email: string, password: string): Promise<void> {
    await this.page.goto('/login', { waitUntil: 'networkidle' });
    await this.page.waitForTimeout(1000);

    // Check if OAuth button is present (Authentik flow)
    const oauthBtn = this.page.locator(
      'button:has-text("Iniciar sesión"), button:has-text("Login"), button:has-text("Sign in")'
    );

    if (await oauthBtn.isVisible()) {
      // OAuth/OIDC flow
      await oauthBtn.click();

      // Wait for redirect to Authentik
      await this.page.waitForURL(/auth\.ades\.setag\.mx/, { timeout: 10000 })
        .catch(() => console.warn('Did not redirect to Authentik'));

      // Wait for Authentik form inputs to render (Web Components)
      try {
        await this.page.waitForSelector('input', { timeout: 10000 });
      } catch {
        throw new Error('Authentik form inputs did not load');
      }

      await this.page.waitForTimeout(500);

      // Try to fill Authentik form (email + password)
      const emailInput = this.page.locator(
        'input[name="username"], input[type="email"], input[placeholder*="email"]'
      ).first();

      const passwordInput = this.page.locator(
        'input[name="password"], input[type="password"]'
      ).first();

      if (await emailInput.isVisible()) {
        await emailInput.fill(email);
      }

      if (await passwordInput.isVisible()) {
        await passwordInput.fill(password);
      }

      // Click Authentik login button
      const authBtn = this.page.locator(
        'button[type="submit"], button:has-text("Iniciar sesión"), button:has-text("Sign in")'
      ).first();

      if (await authBtn.isVisible()) {
        await authBtn.click();
      }

      // Handle OAuth consent screen (if present)
      await this.page.waitForTimeout(1000);
      const consentBtn = this.page.locator(
        'button:has-text("Continue"), button:has-text("Allow"), [data-testid="consent-continue"]'
      ).first();

      if (await consentBtn.isVisible()) {
        await consentBtn.click();
      }

      // Wait for redirect back to ADES
      await this.page.waitForURL(/ades\.setag\.mx\/(callback|dashboard|home)/, { timeout: 15000 })
        .catch(() => {
          throw new Error(`OAuth login failed: did not return to ADES for user ${email}`);
        });

      // Wait for final redirect to dashboard
      await this.page.waitForURL(/\/(dashboard|home|admin|app)/, { timeout: 10000 })
        .catch(() => {
          throw new Error(`Login failed: did not redirect to dashboard for user ${email}`);
        });
    } else {
      // Direct form login (fallback)
      const emailInput = this.page.locator('input[type="email"]').first();
      const passwordInput = this.page.locator('input[type="password"]').first();

      if (await emailInput.isVisible()) {
        await emailInput.fill(email);
        await passwordInput.fill(password);

        const loginBtn = this.page.locator(
          'button:has-text("Ingresar"), button:has-text("Login"), [data-testid="btn-login"]'
        ).first();

        if (await loginBtn.isVisible()) {
          await loginBtn.click();
        }
      }

      await this.page.waitForURL(/\/(dashboard|home|admin)/, { timeout: 10000 })
        .catch(() => {
          throw new Error(`Login failed: did not redirect after login for user ${email}`);
        });
    }
  }

  /**
   * Logout from application
   * @throws Error if logout fails
   */
  async logout(): Promise<void> {
    // Click user menu
    const userMenu = this.page.locator(
      '[data-testid="user-menu"], button:has-text("Menu Usuario"), [class*="user"][class*="menu"]'
    ).first();

    if (await userMenu.isVisible()) {
      await userMenu.click();
    }

    // Click logout button
    const logoutBtn = this.page.locator(
      '[data-testid="logout-btn"], button:has-text("Salir"), button:has-text("Logout")'
    );

    if (await logoutBtn.isVisible()) {
      await logoutBtn.click();
    }

    // Wait for redirect to login
    await this.page.waitForURL('**/login', { timeout: 5000 })
      .catch(() => {
        console.warn('Logout may not have completed (not critical)');
      });
  }

  /**
   * Get current access token from sessionStorage
   * @returns The access token string
   */
  async getAccessToken(): Promise<string | null> {
    return await this.page.evaluate(() => {
      return sessionStorage.getItem('ades_token');
    });
  }

  /**
   * Refresh access token via /api/v1/auth/refresh
   * @returns New access token
   * @throws Error if refresh fails
   */
  async refreshToken(): Promise<string> {
    const currentToken = await this.getAccessToken();

    if (!currentToken) {
      throw new Error('No access token found to refresh');
    }

    const response = await this.page.request.post('/api/v1/auth/refresh', {
      headers: {
        'Authorization': `Bearer ${currentToken}`,
      },
    });

    if (!response.ok()) {
      throw new Error(`Token refresh failed: ${response.status()} ${response.statusText()}`);
    }

    const data = await response.json();
    const newToken = data.access_token || data.token;

    if (!newToken) {
      throw new Error('No access token in refresh response');
    }

    // Store new token
    await this.page.evaluate((token) => {
      sessionStorage.setItem('ades_token', token);
    }, newToken);

    return newToken;
  }

  /**
   * Check if user has a specific permission
   * @param permission Permission name (e.g., 'ADMIN', 'TEACHER')
   * @returns True if user has permission
   */
  async hasPermission(permission: string): Promise<boolean> {
    return await this.page.evaluate((perm) => {
      const userJson = sessionStorage.getItem('ades_usuario');
      if (!userJson) return false;

      const user = JSON.parse(userJson);
      const permissions = user.permisos || user.roles || [];

      return permissions.some((p: string) =>
        p.toUpperCase().includes(perm.toUpperCase())
      );
    }, permission);
  }

  /**
   * Get current logged-in user info
   * @returns User object from sessionStorage
   */
  async getCurrentUser(): Promise<any> {
    return await this.page.evaluate(() => {
      const userJson = sessionStorage.getItem('ades_usuario');
      return userJson ? JSON.parse(userJson) : null;
    });
  }

  /**
   * Check if user is authenticated
   * @returns True if token exists in sessionStorage
   */
  async isAuthenticated(): Promise<boolean> {
    const token = await this.getAccessToken();
    return !!token;
  }

  /**
   * Clear session (logout without UI interaction)
   * Used for test cleanup
   */
  async clearSession(): Promise<void> {
    await this.page.evaluate(() => {
      sessionStorage.clear();
      localStorage.clear();
    });
  }

  /**
   * Manually set token and user (for testing)
   * @param token Access token
   * @param user User object
   */
  async setSession(token: string, user: any): Promise<void> {
    await this.page.evaluate(({ token, user }) => {
      sessionStorage.setItem('ades_token', token);
      sessionStorage.setItem('ades_usuario', JSON.stringify(user));
    }, { token, user });
  }
}
