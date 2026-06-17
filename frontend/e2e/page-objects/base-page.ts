import { Page, Locator, expect } from '@playwright/test';

export class BasePage {
  constructor(protected page: Page) {}

  async waitForToast(type: 'success' | 'error' | 'warn' = 'success') {
    const sel: Record<string, string> = {
      success: '.p-toast-message-success',
      error:   '.p-toast-message-error',
      warn:    '.p-toast-message-warn',
    };
    await expect(this.page.locator(sel[type])).toBeVisible({ timeout: 8_000 });
  }

  async dismissToasts() {
    const toasts = this.page.locator('.p-toast-close-button');
    const count = await toasts.count();
    for (let i = 0; i < count; i++) {
      await toasts.nth(i).click().catch(() => undefined);
    }
  }

  async selectFromDropdown(trigger: Locator, optionLabel: string) {
    await trigger.click();
    await this.page
      .locator('.p-dropdown-item, .p-select-option', { hasText: optionLabel })
      .first()
      .click();
  }

  async fillAndBlur(locator: Locator, value: string) {
    await locator.click({ clickCount: 3 });
    await locator.fill(value);
    await locator.blur();
  }

  async expectFieldError(fieldLocator: Locator, message?: string) {
    const parent = fieldLocator.locator('..').locator('..');
    const errEl  = parent.locator('.ng-invalid ~ small, .p-error, [class*="error"]');
    await expect(errEl.first()).toBeVisible({ timeout: 4_000 });
    if (message) {
      await expect(errEl.first()).toContainText(message);
    }
  }

  async waitSpinner() {
    const spinner = this.page.locator('.p-progress-spinner, [data-loading]');
    await spinner.waitFor({ state: 'hidden', timeout: 15_000 }).catch(() => undefined);
  }

  async expectBadge(text: string) {
    await expect(
      this.page.locator('.p-badge, .ades-badge', { hasText: text })
    ).toBeVisible();
  }

  url() {
    return this.page.url();
  }
}
