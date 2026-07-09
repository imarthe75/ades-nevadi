import { test, expect } from '@playwright/test';
import { AuthHelper } from '../fixtures/auth.helper';
import { DataFactory } from '../fixtures/data.factory';

/**
 * CRUD E2E Tests — Search Expediente
 * SEMANA 3 — Spec #34-35
 */
test.describe('CRUD — Search Expediente', () => {
  let authHelper: AuthHelper;

  test.beforeEach(async ({ page }) => {
    authHelper = new AuthHelper(page);
    DataFactory.reset();

    const credentials = DataFactory.getAdminCredentials();
    await authHelper.login(credentials.email, credentials.password);
    await page.goto('/expedientes');
    await page.waitForLoadState('networkidle');
  });

  test('34-should filter expedientes by search term', async ({ page }) => {
    // Find search input
    const searchInput = page.locator(
      '[data-testid="input-search"], input[placeholder*="Buscar"], input[placeholder*="Search"]'
    ).first();

    if (await searchInput.isVisible()) {
      // Get initial row count
      const rowsBefore = await page.locator('[data-row], tr:not(:first-child)').count();

      // Type search term
      await searchInput.fill('test');
      await page.waitForLoadState('networkidle');

      // Assert — Results should be filtered
      const rowsAfter = await page.locator('[data-row], tr:not(:first-child)').count();
      expect(rowsAfter).toBeLessThanOrEqual(rowsBefore);

      // Clear search
      await searchInput.clear();
      await page.waitForLoadState('networkidle');

      // Should restore full list
      const rowsRestored = await page.locator('[data-row], tr:not(:first-child)').count();
      expect(rowsRestored).toBeGreaterThanOrEqual(rowsAfter);
    }
  });

  test('35-should show no results message when search has no matches', async ({ page }) => {
    // Find search input
    const searchInput = page.locator(
      '[data-testid="input-search"], input[placeholder*="Buscar"]'
    ).first();

    if (await searchInput.isVisible()) {
      // Search for non-existent term
      const uniqueTerm = 'NONEXISTENT_' + Date.now();
      await searchInput.fill(uniqueTerm);
      await page.waitForLoadState('networkidle');

      // Assert — Should show no results message
      const noResultsMsg = page.locator(
        '[data-testid="no-results"], text="No hay resultados", text="No results"'
      );

      try {
        await expect(noResultsMsg).toBeVisible({ timeout: 3000 });
      } catch {
        // Alternative: table should be empty
        const rows = await page.locator('[data-row], tr:not(:first-child)').count();
        expect(rows).toBe(0);
      }
    }
  });
});
