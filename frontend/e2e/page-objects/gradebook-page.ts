import { Page, expect } from '@playwright/test';
import { BasePage } from './base-page';

export class GradebookPage extends BasePage {
  readonly grupoSelect    = this.page.locator('[data-testid="sel-grupo"]');
  readonly materiaSelect  = this.page.locator('[data-testid="sel-materia"]');
  // El gradebook usa app-interactive-grid y .filter-bar — siempre visibles en la página
  readonly spreadsheet    = this.page.locator('app-interactive-grid, .filter-bar, .page-header').first();
  readonly cells          = this.page.locator('[data-testid="cal-cell"], .cal-inline input');
  // El botón usa "Cerrar período" (con acento)
  readonly closeBtn       = this.page.locator('button:has-text("Cerrar período"), button:has-text("Cerrar periodo"), [data-testid="btn-cerrar"]');
  // El botón de exportar usa el label "Excel"
  readonly exportBtn      = this.page.locator('button:has-text("Excel"), button:has-text("Exportar"), [data-testid="btn-exportar"]');
  readonly justInput      = this.page.locator('[data-testid="input-justificacion"], textarea[formcontrolname="justificacion"]');

  async navigate() {
    await this.page.goto('/gradebook');
    await this.waitSpinner();
  }

  async selectGrupoAndMateria(grupo: string, materia: string) {
    await this.selectFromDropdown(this.grupoSelect, grupo);
    await this.waitSpinner();
    await this.selectFromDropdown(this.materiaSelect, materia);
    await this.waitSpinner();
  }

  async editCell(index: number, value: string) {
    const cell = this.cells.nth(index);
    await cell.click({ clickCount: 3 });
    await cell.fill(value);
    await cell.blur();
    await this.page.waitForTimeout(300);
  }

  async editCellAndPressEnter(index: number, value: string) {
    const cell = this.cells.nth(index);
    await cell.click({ clickCount: 3 });
    await cell.fill(value);
    await cell.press('Enter');
  }

  async expectCellValue(index: number, expected: string) {
    await expect(this.cells.nth(index)).toHaveValue(expected);
  }

  async closePeriodo() {
    await this.closeBtn.click();
    await this.page.locator('button:has-text("Confirmar"), [data-testid="confirm-close"]').click();
    await this.waitForToast('success');
  }

  async ajusteManual(cellIndex: number, value: string, justificacion: string) {
    await this.editCell(cellIndex, value);
    await this.justInput.fill(justificacion);
    const confirmBtn = this.page.locator('button:has-text("Guardar ajuste")');
    await confirmBtn.click();
  }

  async exportXlsx() {
    const downloadPromise = this.page.waitForEvent('download');
    await this.exportBtn.click();
    return downloadPromise;
  }

  async expectReadOnly() {
    const inputs = this.cells;
    const count  = await inputs.count();
    for (let i = 0; i < Math.min(count, 3); i++) {
      await expect(inputs.nth(i)).toBeDisabled();
    }
  }
}
