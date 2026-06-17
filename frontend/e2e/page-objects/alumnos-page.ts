import { Page, expect } from '@playwright/test';
import { BasePage } from './base-page';

export class AlumnosPage extends BasePage {
  // Lista principal — apex-interactive-grid
  readonly table    = this.page.locator('app-interactive-grid, [data-testid="tabla-alumnos"], p-table').first();
  readonly rows     = this.page.locator('tr.data-row, .p-datatable-row, [data-testid="grid-row"]');

  // Búsqueda — apex-search component renders <input class="apex-search-input">
  readonly searchInput = this.page.locator('input.apex-search-input, [data-testid="search-alumnos"]');

  // Botones
  readonly newBtn     = this.page.locator('button:has-text("Nuevo alumno"), button:has-text("Nuevo"), [data-testid="btn-nuevo"]');
  readonly importBtn  = this.page.locator('[data-testid="btn-importar"], button:has-text("Importar")');

  // Dialog de alta rápida (apex-modal-dialog → .apex-dialog)
  // Los inputs están en orden: Nombre, Ap. Paterno, Ap. Materno, CURP
  readonly dlgInputs     = this.page.locator('.apex-dialog input, apex-modal-dialog input');
  readonly nombreInput   = this.dlgInputs.nth(0);
  readonly apPaternoInput = this.dlgInputs.nth(1);
  readonly apMaternoInput = this.dlgInputs.nth(2);
  readonly curpInput     = this.page.locator('.apex-dialog input[maxlength="18"], input[maxlength="18"]');

  // Fecha nacimiento — el formulario básico de alta no lo incluye, locator seguro para fuzz
  readonly fechaNacInput = this.page.locator(
    'input[type="date"], [data-testid="fecha-nacimiento"], .apex-dialog input[type="date"]'
  ).first();

  // Botón Guardar/Crear dentro del dialog
  readonly saveBtn = this.page.locator(
    'button:has-text("Crear alumno"), button:has-text("Guardar"), [data-testid="btn-guardar"]'
  );

  async navigate() {
    await this.page.goto('/alumnos');
    await this.waitSpinner();
  }

  async openNewForm() {
    await this.newBtn.click();
    // Esperar a que el dialog de apex-modal-dialog aparezca
    await expect(this.page.locator('.apex-dialog, [role="dialog"]')).toBeVisible({ timeout: 5_000 });
    await this.page.waitForTimeout(300); // pequeña pausa para animación del dialog
  }

  async fillAlumnoForm(data: {
    curp: string;
    nombre: string;
    apellido_paterno: string;
    apellido_materno?: string;
    fecha_nacimiento?: string;
  }) {
    await this.fillAndBlur(this.nombreInput,    data.nombre);
    await this.fillAndBlur(this.apPaternoInput, data.apellido_paterno);
    if (data.apellido_materno) {
      await this.fillAndBlur(this.apMaternoInput, data.apellido_materno);
    }
    await this.fillAndBlur(this.curpInput, data.curp);
  }

  async save() {
    await this.saveBtn.click();
  }

  async saveAndExpectSuccess() {
    await this.save();
    await this.waitForToast('success');
  }

  async saveAndExpectError(message?: string) {
    await this.save();
    await this.waitForToast('error');
    if (message) {
      await expect(this.page.locator('.p-toast-detail')).toContainText(message);
    }
  }

  async searchFor(query: string) {
    await this.searchInput.fill(query);
    await this.page.waitForTimeout(400);
    await this.waitSpinner();
  }

  async expectRowCount(min: number) {
    const count = await this.rows.count();
    expect(count).toBeGreaterThanOrEqual(min);
  }

  async getFirstRowText(): Promise<string> {
    return (await this.rows.first().textContent()) ?? '';
  }

  async clickFirstRow() {
    await this.rows.first().click();
  }

  async uploadCsv(filePath: string) {
    const input = this.page.locator('input[type="file"]');
    await this.importBtn.click();
    await input.setInputFiles(filePath);
    await this.page.locator('button:has-text("Importar")').last().click();
  }
}
