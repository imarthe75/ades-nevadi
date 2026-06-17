import { Page, expect } from '@playwright/test';
import { BasePage } from './base-page';

export class AsistenciasPage extends BasePage {
  readonly grupoSelect  = this.page.locator('[data-testid="sel-grupo"]');
  readonly fechaInput   = this.page.locator('[data-testid="input-fecha"], input[type="date"]').first();
  readonly saveBtn      = this.page.locator('button:has-text("Guardar"), [data-testid="btn-guardar"]');
  // p-table renderiza plain <tr> en <tbody>; fallback a data-testid para cuando se añadan
  readonly alumnoRows   = this.page.locator('.p-datatable-tbody tr, p-table tbody tr, [data-testid="asistencia-row"], .asistencia-row');
  readonly alertaBadges = this.page.locator('[data-testid="alerta-85"], .asistencia-alerta');

  async navigate() {
    await this.page.goto('/asistencias');
    await this.waitSpinner();
  }

  async selectGrupo(nombre: string) {
    await this.selectFromDropdown(this.grupoSelect, nombre);
    await this.waitSpinner();
  }

  async setFecha(fecha: string) {
    await this.fillAndBlur(this.fechaInput, fecha);
    await this.waitSpinner();
  }

  async setEstadoAlumno(index: number, estado: 'PRESENTE' | 'AUSENTE' | 'TARDE' | 'JUSTIFICADO') {
    const row = this.alumnoRows.nth(index);
    await row.locator(`[data-estado="${estado}"], button:has-text("${estado}")`).click();
  }

  async toggleEstado(index: number) {
    const row = this.alumnoRows.nth(index);
    const btn = row.locator('[data-testid="toggle-asistencia"]').first();
    await btn.click();
  }

  async saveAll() {
    await this.saveBtn.click();
  }

  async saveAndExpectSuccess() {
    await this.saveAll();
    await this.waitForToast('success');
  }

  async expectAlertaOnRow(index: number) {
    const row = this.alumnoRows.nth(index);
    await expect(row.locator('.asistencia-alerta, [data-alerta]')).toBeVisible();
  }

  async getRowCount(): Promise<number> {
    return this.alumnoRows.count();
  }
}
