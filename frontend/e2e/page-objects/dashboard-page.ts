import { Page, expect } from '@playwright/test';
import { BasePage } from './base-page';

export class DashboardPage extends BasePage {
  readonly kpiCards     = this.page.locator('[data-testid="kpi-card"], .ades-kpi-card, .p-card');
  readonly plantelSelect = this.page.locator('[data-testid="ctx-plantel"], .context-selector');
  readonly cicloSelect   = this.page.locator('[data-testid="ctx-ciclo"]');

  async navigate() {
    await this.page.goto('/dashboard');
  }

  async expectKpisLoaded() {
    await expect(this.kpiCards.first()).toBeVisible({ timeout: 10_000 });
    const count = await this.kpiCards.count();
    expect(count).toBeGreaterThanOrEqual(2);
  }

  async changePlantel(nombre: string) {
    await this.selectFromDropdown(this.plantelSelect, nombre);
    await this.waitSpinner();
  }

  async changeCiclo(nombre: string) {
    await this.selectFromDropdown(this.cicloSelect, nombre);
    await this.waitSpinner();
  }

  async expectNoDataForRole(rol: string) {
    if (rol === 'PADRE_FAMILIA' || rol === 'ALUMNO') {
      await expect(this.page.locator('[data-testid="dashboard-hijo"]')).toBeVisible();
    }
  }
}
