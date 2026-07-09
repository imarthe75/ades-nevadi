import { test, expect } from '@playwright/test';
import { AuthHelper } from '../fixtures/auth.helper';
import { DataFactory } from '../fixtures/data.factory';

/**
 * Auth E2E Tests — Logout
 * SEMANA 3 — Spec #6-8
 */
test.describe('Auth — Logout', () => {
  let authHelper: AuthHelper;

  test.beforeEach(async ({ page }) => {
    authHelper = new AuthHelper(page);
    DataFactory.reset();

    // Login before each test
    const credentials = DataFactory.getAdminCredentials();
    await authHelper.login(credentials.email, credentials.password);
  });

  test('06-should logout and redirect to login', async ({ page }) => {
    // Act
    await authHelper.logout();

    // Assert
    await expect(page).toHaveURL('**/login', { timeout: 5000 });
  });

  test('07-should clear session storage on logout', async ({ page }) => {
    // Arrange
    const tokenBefore = await authHelper.getAccessToken();
    expect(tokenBefore).toBeTruthy();

    // Act
    await authHelper.logout();

    // Assert
    const tokenAfter = await authHelper.getAccessToken();
    expect(tokenAfter).toBeNull();

    const userAfter = await authHelper.getCurrentUser();
    expect(userAfter).toBeNull();
  });

  test('08-should prevent access to protected routes after logout', async ({ page }) => {
    // Arrange
    await authHelper.logout();

    // Act — Try to access dashboard
    await page.goto('/dashboard', { waitUntil: 'load' });

    // Assert — Should redirect to login or show access denied
    const url = page.url();
    expect(url).toMatch(/login|access-denied/i);
  });
});
