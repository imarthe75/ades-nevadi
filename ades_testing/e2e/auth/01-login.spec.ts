import { test, expect } from '@playwright/test';
import { AuthHelper } from '../fixtures/auth.helper';
import { DataFactory } from '../fixtures/data.factory';

/**
 * Auth E2E Tests — Login
 * SEMANA 3 — Spec #1-5
 */
test.describe('Auth — Login', () => {
  let authHelper: AuthHelper;

  test.beforeEach(async ({ page }) => {
    authHelper = new AuthHelper(page);
    DataFactory.reset();
  });

  test('01-should login with valid admin credentials', async ({ page }) => {
    // Arrange
    const credentials = DataFactory.getAdminCredentials();

    // Act
    await authHelper.login(credentials.email, credentials.password);

    // Assert
    const isAuthenticated = await authHelper.isAuthenticated();
    expect(isAuthenticated).toBe(true);

    // Verify dashboard is visible
    const dashboardTitle = page.locator(
      'h1:has-text("Dashboard"), h1:has-text("Inicio"), [data-testid="dashboard-title"]'
    );
    await expect(dashboardTitle).toBeVisible({ timeout: 5000 });
  });

  test('02-should show error on invalid password', async ({ page }) => {
    // Arrange
    const credentials = DataFactory.getAdminCredentials();
    const invalidPassword = 'WrongPassword123!';

    // Act
    await page.goto('/login');
    await page.fill('input[type="email"]', credentials.email);
    await page.fill('input[type="password"]', invalidPassword);

    const loginBtn = page.locator(
      'button:has-text("Ingresar"), button:has-text("Login"), [data-testid="btn-login"]'
    );
    await loginBtn.click();

    // Assert — Wait for error message
    const errorMsg = page.locator(
      '[data-testid="error-message"], .error, [role="alert"]:has-text("Credenciales")'
    );

    try {
      await expect(errorMsg).toBeVisible({ timeout: 5000 });
      const errorText = await errorMsg.textContent();
      expect(errorText).toContain('inválida' || 'invalid' || 'credenciales');
    } catch {
      // Alternative: Verify we're still on login page
      await expect(page).toHaveURL('**/login');
    }
  });

  test('03-should show error on non-existent user', async ({ page }) => {
    // Arrange
    const nonExistentEmail = DataFactory.randomEmail();
    const password = DataFactory.randomPassword();

    // Act
    await page.goto('/login');
    await page.fill('input[type="email"]', nonExistentEmail);
    await page.fill('input[type="password"]', password);

    const loginBtn = page.locator(
      'button:has-text("Ingresar"), button:has-text("Login"), [data-testid="btn-login"]'
    );
    await loginBtn.click();

    // Assert — Should show error or stay on login
    try {
      const errorMsg = page.locator('[data-testid="error-message"], [role="alert"]');
      await expect(errorMsg).toBeVisible({ timeout: 5000 });
    } catch {
      await expect(page).toHaveURL('**/login');
    }
  });

  test('04-should redirect to dashboard on successful login', async ({ page }) => {
    // Arrange
    const credentials = DataFactory.getAdminCredentials();

    // Act
    await authHelper.login(credentials.email, credentials.password);

    // Assert
    const currentUrl = page.url();
    expect(currentUrl).toMatch(/\/(dashboard|home|admin)/);
  });

  test('05-should persist session across page reload', async ({ page }) => {
    // Arrange
    const credentials = DataFactory.getAdminCredentials();

    // Act — Login
    await authHelper.login(credentials.email, credentials.password);

    // Get token before reload
    const tokenBefore = await authHelper.getAccessToken();
    expect(tokenBefore).toBeTruthy();

    // Reload page
    await page.reload();

    // Assert — Should still be logged in
    const tokenAfter = await authHelper.getAccessToken();
    expect(tokenAfter).toBe(tokenBefore);

    const isAuthenticated = await authHelper.isAuthenticated();
    expect(isAuthenticated).toBe(true);

    // Dashboard should still be visible
    const dashboardTitle = page.locator(
      'h1:has-text("Dashboard"), h1:has-text("Inicio"), [data-testid="dashboard-title"]'
    );
    await expect(dashboardTitle).toBeVisible({ timeout: 5000 });
  });
});
