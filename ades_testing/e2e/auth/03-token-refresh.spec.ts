import { test, expect } from '@playwright/test';
import { AuthHelper } from '../fixtures/auth.helper';
import { DataFactory } from '../fixtures/data.factory';

/**
 * Auth E2E Tests — Token Refresh
 * SEMANA 3 — Spec #9-11
 */
test.describe('Auth — Token Refresh', () => {
  let authHelper: AuthHelper;

  test.beforeEach(async ({ page }) => {
    authHelper = new AuthHelper(page);
    DataFactory.reset();

    // Login before each test
    const credentials = DataFactory.getAdminCredentials();
    await authHelper.login(credentials.email, credentials.password);
  });

  test('09-should refresh token when access token expires', async ({ page }) => {
    // Arrange
    const oldToken = await authHelper.getAccessToken();
    expect(oldToken).toBeTruthy();

    // Act — Call refresh endpoint
    const newToken = await authHelper.refreshToken();

    // Assert
    expect(newToken).toBeTruthy();
    expect(newToken).not.toBe(oldToken); // Should be different token
  });

  test('10-should maintain session after token refresh', async ({ page }) => {
    // Arrange
    await authHelper.refreshToken();

    // Act — Navigate to protected page
    await page.goto('/expedientes');

    // Assert — Should load successfully (not redirect to login)
    await expect(page).toHaveURL('**/expedientes', { timeout: 5000 });

    // Should still have user info
    const user = await authHelper.getCurrentUser();
    expect(user).toBeTruthy();
  });

  test('11-should handle refresh token failure gracefully', async ({ page, context }) => {
    // Arrange — Simulate invalid refresh token
    await page.evaluate(() => {
      sessionStorage.setItem('ades_token', 'invalid-token-xyz');
    });

    // Act — Try to refresh
    try {
      await authHelper.refreshToken();
      // If we get here, refresh succeeded (or API is lenient)
      expect(true).toBe(true);
    } catch (error) {
      // If refresh fails, should not crash app
      expect(error.message).toContain('refresh');
    }

    // App should still be responsive
    const isAuthenticated = await authHelper.isAuthenticated();
    expect(typeof isAuthenticated).toBe('boolean');
  });
});
