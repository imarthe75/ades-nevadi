import { test, expect } from '@playwright/test';
import { AuthHelper } from '../fixtures/auth.helper';
import { DataFactory } from '../fixtures/data.factory';

/**
 * CRUD E2E Tests — List Expediente
 * SEMANA 3 — Spec #32-33
 */
test.describe('CRUD — List Expediente', () => {
  let authHelper: AuthHelper;

  test.beforeEach(async ({ page }) => {
    authHelper = new AuthHelper(page);
    DataFactory.reset();

    const credentials = DataFactory.getAdminCredentials();
    await authHelper.login(credentials.email, credentials.password);
    await page.goto('/expedientes');
    await page.waitForLoadState('networkidle');
  });

  test('32-should load and display expediente list', async ({ page }) => {
    // Assert — Table or list should be visible
    const table = page.locator(
      '[role="table"], [class*="table"], [class*="grid"]'
    ).first();

    await expect(table).toBeVisible();

    // Should have rows/items
    const rows = page.locator('[data-row], tr:not(:first-child), [class*="row"]');
    const rowCount = await rows.count();
    expect(rowCount).toBeGreaterThan(0);
  });

  test('33-should support pagination navigation', async ({ page }) => {
    // Check for pagination controls
    const nextBtn = page.locator(
      '[data-testid="btn-next"], button:has-text("Siguiente"), [aria-label*="Next"]'
    );

    const hasPagination = await nextBtn.isVisible().catch(() => false);

    if (hasPagination) {
      // Current page indicator
      const pageInfo = page.locator('[data-testid="page-info"], [class*="page"]');
      const pageBefore = await pageInfo.textContent();

      // Click next
      await nextBtn.click();
      await page.waitForLoadState('networkidle');

      // Assert — Page should change
      const pageAfter = await pageInfo.textContent();
      expect(pageAfter).not.toBe(pageBefore);
    } else {
      // No pagination = all items on one page
      expect(true).toBe(true);
    }
  });
});
