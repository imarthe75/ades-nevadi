import { test, expect } from '@playwright/test';
import { AuthHelper } from '../fixtures/auth.helper';
import { DataFactory } from '../fixtures/data.factory';

/**
 * CRUD E2E Tests — Delete Expediente
 * SEMANA 3 — Spec #29-31
 */
test.describe('CRUD — Delete Expediente', () => {
  let authHelper: AuthHelper;

  test.beforeEach(async ({ page }) => {
    authHelper = new AuthHelper(page);
    DataFactory.reset();

    const credentials = DataFactory.getAdminCredentials();
    await authHelper.login(credentials.email, credentials.password);
    await page.goto('/expedientes');
    await page.waitForLoadState('networkidle');
  });

  test('29-should open delete confirmation dialog', async ({ page }) => {
    // Act — Click delete button on first expediente
    const deleteBtn = page.locator(
      '[data-testid="btn-delete"], button:has-text("Eliminar"), button:has-text("Delete")'
    ).first();

    if (await deleteBtn.isVisible()) {
      // Listen for dialog/modal
      let dialogShown = false;
      page.once('dialog', () => {
        dialogShown = true;
      });

      await deleteBtn.click();

      // Assert — Confirmation should appear
      const confirmDialog = page.locator(
        '[role="dialog"], [role="alertdialog"], [class*="modal"], [class*="confirm"]'
      ).first();

      try {
        await expect(confirmDialog).toBeVisible({ timeout: 3000 });
      } catch {
        // If no modal, dialog event should have fired
        expect(dialogShown || await confirmDialog.isVisible()).toBe(true);
      }
    }
  });

  test('30-should delete expediente after confirmation', async ({ page }) => {
    // Arrange
    const expRow = page.locator('[data-row]').first();
    const expText = await expRow.textContent();

    // Act
    const deleteBtn = page.locator(
      '[data-testid="btn-delete"], button:has-text("Eliminar")'
    ).first();

    if (await deleteBtn.isVisible()) {
      await deleteBtn.click();

      // Confirm deletion
      const confirmBtn = page.locator(
        '[data-testid="btn-confirm"], button:has-text("Confirmar"), button:has-text("Aceptar")'
      );

      if (await confirmBtn.isVisible()) {
        await confirmBtn.click();
      } else {
        // Accept browser dialog
        page.once('dialog', (dialog) => dialog.accept());
        await deleteBtn.click();
      }

      // Assert — Success message or row removed
      try {
        const successMsg = page.locator('[role="alert"]:has-text("eliminado")');
        await expect(successMsg).toBeVisible({ timeout: 5000 });
      } catch {
        // Expediente should no longer be in list
        const remainingRow = page.locator(`text="${expText}"`);
        expect(await remainingRow.isVisible().catch(() => false)).toBe(false);
      }
    }
  });

  test('31-should not delete on cancel', async ({ page }) => {
    // Arrange
    const expRow = page.locator('[data-row]').first();
    const expTextBefore = await expRow.textContent();

    // Act
    const deleteBtn = page.locator(
      '[data-testid="btn-delete"], button:has-text("Eliminar")'
    ).first();

    if (await deleteBtn.isVisible()) {
      await deleteBtn.click();

      // Click cancel
      const cancelBtn = page.locator(
        '[data-testid="btn-cancel"], button:has-text("Cancelar"), button:has-text("Cancel")'
      );

      if (await cancelBtn.isVisible()) {
        await cancelBtn.click();
      } else {
        page.once('dialog', (dialog) => dialog.dismiss());
      }

      // Assert — Expediente should still exist
      await page.waitForTimeout(500);
      const expTextAfter = await expRow.textContent();
      expect(expTextAfter).toBe(expTextBefore);
    }
  });
});
