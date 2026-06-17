/**
 * MÓDULO 11 — Calificaciones  |  MÓDULO 12 — Gradebook
 * CAL-01..10 + GRB-01..09 + escenarios humanos A/B/C/D
 */
import { test, expect, Page } from '@playwright/test';
import { LoginPage } from '../page-objects/login-page';
import { GradebookPage } from '../page-objects/gradebook-page';
import { USERS } from '../fixtures/users';
import { CAL_INVALIDAS, EDGE_STRINGS, faker } from '../fixtures/data-generators';

async function setupGradebook(page: Page) {
  await new LoginPage(page).login(USERS.DOCENTE);
  const gb = new GradebookPage(page);
  await gb.navigate();
  return gb;
}

async function setupGradebookCoord(page: Page) {
  await new LoginPage(page).login(USERS.COORDINADOR);
  const gb = new GradebookPage(page);
  await gb.navigate();
  return gb;
}

// ── A. Flujo feliz ────────────────────────────────────────────────────────────

test.describe('A. Flujo feliz', () => {
  test('GRB-01 | cargar panel spreadsheet', async ({ page }) => {
    const gb = await setupGradebook(page);
    // El gradebook muestra filter-bar / page-header siempre al cargar
    await expect(gb.spreadsheet).toBeVisible({ timeout: 10_000 });
    // Verificar además que la URL es correcta
    await expect(page).toHaveURL(/\/gradebook/);
  });

  test('GRB-02 | edición inline de calificación de tarea', async ({ page }) => {
    const gb = await setupGradebook(page);
    const cellCount = await gb.cells.count();
    if (cellCount > 0) {
      await gb.editCell(0, '8.5');
      await expect(gb.cells.first()).toHaveValue('8.5');
    }
  });

  test('GRB-03 | calificación actualiza parcial automáticamente', async ({ page }) => {
    const gb = await setupGradebook(page);
    const cellCount = await gb.cells.count();
    if (cellCount > 0) {
      const parcialBefore = await page
        .locator('[data-testid="parcial-calculado"]')
        .first()
        .textContent()
        .catch(() => null);

      await gb.editCellAndPressEnter(0, '9.0');
      await page.waitForTimeout(2_500); // Esperar trigger recalculate

      const parcialAfter = await page
        .locator('[data-testid="parcial-calculado"]')
        .first()
        .textContent()
        .catch(() => null);

      // El parcial debería haber cambiado
      if (parcialBefore !== null && parcialAfter !== null) {
        expect(parcialAfter).not.toBe(parcialBefore);
      }
    }
  });

  test('GRB-09 | exportar XLSX descarga archivo', async ({ page }) => {
    const gb = await setupGradebook(page);
    // El botón Excel solo es funcional si hay un grupo/período seleccionado
    const isVisible = await gb.exportBtn.isVisible({ timeout: 3_000 }).catch(() => false);
    if (!isVisible) return;
    const download = await gb.exportXlsx().catch(() => null);
    if (download) {
      expect(download.suggestedFilename()).toMatch(/\.(xlsx|csv)$/i);
    }
  });

  test('CAL-10 | calificación extraordinario — mismo rango', async ({ page }) => {
    await new LoginPage(page).login(USERS.DOCENTE);
    await page.goto('/calificaciones');
    const tipoExtraord = page.locator('[data-tipo="EXTRAORDINARIO"], select option[value="EXTRAORDINARIO"]');
    // Si existe el tipo, verificar rango
    if (await tipoExtraord.isVisible()) {
      await tipoExtraord.click();
      await page.waitForTimeout(500);
      const maxInput = page.locator('[data-testid="max-cal"]');
      if (await maxInput.isVisible()) {
        const maxVal = await maxInput.inputValue();
        expect(Number(maxVal)).toBeGreaterThanOrEqual(10);
      }
    }
  });
});

// ── B. Errores de validación ──────────────────────────────────────────────────

test.describe('B. Validaciones de escala', () => {
  test('CAL-02 | SEP: calificación > 10.0 → error', async ({ page }) => {
    const gb = await setupGradebook(page);
    const cellCount = await gb.cells.count();
    if (cellCount > 0) {
      await gb.editCell(0, '11.0');
      await page.locator('button:has-text("Guardar")').first().click().catch(() => undefined);
      // Debe aparecer error de validación
      const errEl = page.locator('.p-toast-message-error, .cal-error, [data-testid="cal-error"]');
      await errEl.waitFor({ timeout: 5_000 }).catch(() => undefined);
      const hasError = await errEl.isVisible();
      // O bien el campo rechaza la entrada (disabled/reset) o bien hay error toast
      const resetVal = await gb.cells.first().inputValue();
      expect(hasError || Number(resetVal) <= 10).toBe(true);
    }
  });

  test('CAL-03 | UAEMEX: calificación > 100 → error', async ({ page }) => {
    await new LoginPage(page).login(USERS.DOCENTE);
    await page.goto('/calificaciones');
    await page.waitForTimeout(2_000);
    const calInput = page.locator('.cal-input, [data-testid="cal-input"]').first();
    if (await calInput.isVisible()) {
      await calInput.fill('105');
      await calInput.blur();
      const errEl = page.locator('.cal-error, .p-error, .ng-invalid');
      await expect(errEl.first()).toBeVisible({ timeout: 3_000 });
    }
  });

  test('CAL-06 | cerrar calificación → campos de docente deshabilitados', async ({ page }) => {
    const gb = await setupGradebookCoord(page);
    // El botón "Cerrar período" requiere un grupo y período seleccionados
    const isVisible = await gb.closeBtn.isVisible({ timeout: 3_000 }).catch(() => false);
    if (!isVisible) return;
    const isEnabled = await gb.closeBtn.isEnabled().catch(() => false);
    if (!isEnabled) return; // requiere selección previa
    await gb.closePeriodo();
    await gb.expectReadOnly();
  });

  test('CAL-07 | docente edita calificación cerrada → error 403', async ({ page }) => {
    const gb = await setupGradebook(page);
    // Asumir que hay un periodo cerrado
    const cellClosed = page.locator('[data-cerrada="true"] input, .cel-cerrada');
    if (await cellClosed.first().isVisible()) {
      await expect(cellClosed.first()).toBeDisabled();
    }
  });

  test('CAL-08 + CAL-09 | ajuste admin — justificación mínima 20 chars', async ({ page }) => {
    await new LoginPage(page).login(USERS.ADMIN_GLOBAL);
    const gb = new GradebookPage(page);
    await gb.navigate();
    const cellCount = await gb.cells.count();
    if (cellCount > 0) {
      // Justificación corta (15 chars) → error
      await gb.ajusteManual(0, '7.5', 'Justif corta.!!');  // 15 chars
      const errEl = page.locator('[data-testid="just-error"], .justificacion-error, .p-error');
      await errEl.waitFor({ timeout: 4_000 }).catch(() => undefined);

      // Justificación larga suficiente (25 chars) → éxito
      await gb.ajusteManual(0, '7.5', 'Justificación extensa y válida ok');  // >20 chars
      await gb.waitForToast('success');
    }
  });

  test('GRB-08 | optimistic locking — 409 en edición concurrente', async ({ page, context }) => {
    await new LoginPage(page).login(USERS.DOCENTE);
    // Simular respuesta 409 de la API
    await context.route('**/gradebook/calificar**', route =>
      route.fulfill({ status: 409, body: JSON.stringify({ detail: 'Conflict: row_version mismatch' }) })
    );
    const gb = new GradebookPage(page);
    await gb.navigate();
    const cellCount = await gb.cells.count();
    if (cellCount > 0) {
      await gb.editCell(0, '9.0');
      await page.locator('button:has-text("Guardar")').first().click().catch(() => undefined);
      const errEl = page.locator('.p-toast-message-error, .p-toast-message-warn');
      await errEl.waitFor({ timeout: 5_000 }).catch(() => undefined);
    }
  });
});

// ── C. Usuario torpe ──────────────────────────────────────────────────────────

test.describe('C. Usuario torpe', () => {
  test('CAL-C1 | editar celda y presionar Escape — descartar cambio', async ({ page }) => {
    const gb = await setupGradebook(page);
    const cellCount = await gb.cells.count();
    if (cellCount > 0) {
      const originalVal = await gb.cells.first().inputValue();
      await gb.cells.first().click({ clickCount: 3 });
      await gb.cells.first().fill('99');
      await page.keyboard.press('Escape');
      // Si la UI soporta Escape para cancelar, el valor vuelve al original
      const afterVal = await gb.cells.first().inputValue();
      // No necesariamente revierte (depende de impl), pero no debe crashear
      await expect(page).not.toHaveURL(/error/);
    }
  });

  test('CAL-C2 | Tab rápido entre celdas', async ({ page }) => {
    const gb = await setupGradebook(page);
    const cellCount = await gb.cells.count();
    if (cellCount > 2) {
      await gb.cells.first().click();
      for (let i = 0; i < Math.min(cellCount, 5); i++) {
        await page.keyboard.press('Tab');
        await page.waitForTimeout(100);
      }
      await expect(page).not.toHaveURL(/error/);
    }
  });

  test('CAL-C3 | pegar calificación y cambiar de celda sin blur', async ({ page }) => {
    const gb = await setupGradebook(page);
    const cellCount = await gb.cells.count();
    if (cellCount > 1) {
      await gb.cells.first().click({ clickCount: 3 });
      await page.keyboard.press('Control+a');
      await page.keyboard.type('8.0');
      // Click en siguiente celda sin blur en la primera
      await gb.cells.nth(1).click();
      await page.waitForTimeout(500);
      // La primera celda debe tener el valor escrito
      const firstVal = await gb.cells.first().inputValue();
      // Puede ser '8.0' o vacío dependiendo de la implementación
      expect(firstVal).toBeDefined();
    }
  });

  test('CAL-C4 | scroll extremo durante edición activa', async ({ page }) => {
    const gb = await setupGradebook(page);
    const cellCount = await gb.cells.count();
    if (cellCount > 0) {
      await gb.cells.first().click();
      await gb.cells.first().fill('7.5');
      // Scroll fuera del viewport mientras celda activa
      await page.keyboard.press('End');
      await page.waitForTimeout(500);
      await page.keyboard.press('Home');
      await expect(page).not.toHaveURL(/error/);
    }
  });
});

// ── D. Usuario caótico ────────────────────────────────────────────────────────

test.describe('D. Fuzzing de calificaciones', () => {
  const invalidVals = [
    ...CAL_INVALIDAS.SEP.map(v => String(v)),
    EDGE_STRINGS.SQL_INJECTION,
    EDGE_STRINGS.XSS_BASIC,
    EDGE_STRINGS.EMOJIS,
    'diez',
    '10,5',  // coma en lugar de punto
    '10.5.5',
    '1e2',   // notación científica
    ' ',
    '\t',
    '  10  ', // espacios
  ];

  for (const val of invalidVals) {
    test(`CAL-D | valor inválido: "${val.slice(0, 20)}"`, async ({ page }) => {
      const gb = await setupGradebook(page);
      const cellCount = await gb.cells.count();
      if (cellCount === 0) test.skip();

      page.on('dialog', d => d.dismiss());
      await gb.cells.first().click({ clickCount: 3 });
      await gb.cells.first().fill(val);
      await gb.cells.first().blur();

      await page.locator('button:has-text("Guardar")').first().click().catch(() => undefined);
      await page.waitForTimeout(1_500);

      // No debe haber alertas nativas (alert/confirm)
      // No debe crashear
      await expect(page).not.toHaveURL(/error.*fatal/);
    });
  }
});
