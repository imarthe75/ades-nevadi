import { test, expect } from '@playwright/test';
import { AuthHelper } from '../fixtures/auth.helper';
import { DataFactory } from '../fixtures/data.factory';

/**
 * Auth E2E Tests — Permissions & RBAC
 * SEMANA 3 — Spec #12-15
 */
test.describe('Auth — Permissions & RBAC', () => {
  let authHelper: AuthHelper;

  test.beforeEach(async ({ page }) => {
    authHelper = new AuthHelper(page);
    DataFactory.reset();
  });

  test('12-admin user should have all permissions', async ({ page }) => {
    // Arrange
    const credentials = DataFactory.getAdminCredentials();

    // Act
    await authHelper.login(credentials.email, credentials.password);

    // Assert
    const hasAdminPerm = await authHelper.hasPermission('ADMIN');
    expect(hasAdminPerm).toBe(true);

    const user = await authHelper.getCurrentUser();
    expect(user).toBeTruthy();
    expect(user.rol).toBe('ADMIN');
  });

  test('13-teacher user should have limited permissions', async ({ page }) => {
    // Arrange
    const teacherCreds = {
      email: 'teacher@ades.test',
      password: 'Teacher@123456',
    };

    // Act
    await authHelper.login(teacherCreds.email, teacherCreds.password);

    // Assert
    const hasCalificacionesPerm = await authHelper.hasPermission('CALIFICACIONES');
    expect(hasCalificacionesPerm).toBe(true);

    // Should NOT have admin permission
    const hasAdminPerm = await authHelper.hasPermission('ADMIN');
    expect(hasAdminPerm).toBe(false);
  });

  test('14-non-admin should not access admin panel', async ({ page }) => {
    // Arrange
    const teacherCreds = {
      email: 'teacher@ades.test',
      password: 'Teacher@123456',
    };

    // Act
    await authHelper.login(teacherCreds.email, teacherCreds.password);
    await page.goto('/admin/users', { waitUntil: 'load' });

    // Assert — Should redirect or show 403
    const url = page.url();
    expect(url).toMatch(/login|403|access-denied|dashboard/i);

    // Check for error message or redirect
    const errorMsg = page.locator('[role="alert"], [class*="error"]');
    const hasError = await errorMsg.isVisible().catch(() => false);
    expect(url.includes('admin')).toBe(false); // Not on admin page
  });

  test('15-permission check should work after session restore', async ({ page }) => {
    // Arrange
    const credentials = DataFactory.getAdminCredentials();
    await authHelper.login(credentials.email, credentials.password);

    // Get permissions
    const hasPerm1 = await authHelper.hasPermission('ADMIN');
    expect(hasPerm1).toBe(true);

    // Act — Reload page
    await page.reload();

    // Assert — Should still have permissions
    const hasPerm2 = await authHelper.hasPermission('ADMIN');
    expect(hasPerm2).toBe(true);
  });
});
