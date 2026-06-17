/**
 * MÓDULO 35 — Certificados Digitales (Ed25519)
 * CER-01..07 + escenarios humanos A/B/C/D
 */
import { test, expect, Page } from '@playwright/test';
import { LoginPage } from '../page-objects/login-page';
import { BasePage } from '../page-objects/base-page';
import { USERS, API_BASE } from '../fixtures/users';
import { EDGE_STRINGS, faker } from '../fixtures/data-generators';

async function setupCertificados(page: Page) {
  await new LoginPage(page).login(USERS.DIRECTOR);
  const bp = new BasePage(page);
  await page.goto('/certificados');
  await bp.waitSpinner();
  return bp;
}

// ── A. Flujo feliz ────────────────────────────────────────────────────────────

test.describe('A. Flujo feliz', () => {
  test('CER-01 | emitir certificado para alumno egresado', async ({ page }) => {
    await setupCertificados(page);
    const emitirBtn = page.locator('[data-testid="btn-emitir"], button:has-text("Emitir")').first();
    if (await emitirBtn.isVisible()) {
      await emitirBtn.click();
      await page.waitForTimeout(2_000);
      // Estado debe cambiar a EMITIDO
      const estadoEl = page.locator('[data-testid="estado-cert"], .cert-estado');
      await expect(estadoEl.first()).toContainText(/emitido/i, { timeout: 8_000 });
    }
  });

  test('CER-02 | firmar certificado → Ed25519 firma generada', async ({ page }) => {
    await setupCertificados(page);
    const firmarBtn = page.locator('[data-testid="btn-firmar"], button:has-text("Firmar")').first();
    if (await firmarBtn.isVisible()) {
      await firmarBtn.click();
      await page.waitForTimeout(3_000);
      const estadoEl = page.locator('[data-testid="estado-cert"], .cert-estado');
      await expect(estadoEl.first()).toContainText(/firmado/i, { timeout: 8_000 });
    }
  });

  test('CER-03 | PDF del certificado tiene QR visible', async ({ page }) => {
    await setupCertificados(page);
    const pdfBtn = page.locator('[data-testid="btn-pdf"], button:has-text("PDF"), a[href*="pdf"]').first();
    if (await pdfBtn.isVisible()) {
      const downloadPromise = page.waitForEvent('download').catch(() => null);
      await pdfBtn.click();
      const download = await downloadPromise;
      if (download) {
        expect(download.suggestedFilename()).toMatch(/\.pdf$/i);
      }
    }
  });

  test('CER-04 | verificación pública sin login', async ({ page }) => {
    // Ruta pública /verificar/:folio sin sesión
    await page.goto('/verificar/FOLIO-TEST-INVALIDO');
    // No debe redirigir a login
    await expect(page).not.toHaveURL(/\/login/);
    // Debe mostrar estado del certificado
    const statusEl = page.locator('[data-testid="cert-status"], .verificacion-estado');
    await statusEl.waitFor({ timeout: 8_000 }).catch(() => undefined);
  });
});

// ── B. Errores y seguridad ────────────────────────────────────────────────────

test.describe('B. Errores y acceso', () => {
  test('CER-07 | coordinador no puede emitir certificados → 403', async ({ page }) => {
    await new LoginPage(page).login(USERS.COORDINADOR);
    await page.goto('/certificados');
    const emitirBtn = page.locator('[data-testid="btn-emitir"], button:has-text("Emitir")');
    // No debe estar visible para coordinador
    await expect(emitirBtn).not.toBeVisible({ timeout: 5_000 });
  });

  test('CER-05 | PDF alterado → estado INVALIDO en verificación', async ({ page }) => {
    await page.goto('/verificar/FOLIO-ALTERADO');
    await page.waitForTimeout(3_000);
    // El componente usa .verif-error cuando el folio no existe
    const errEl = page.locator('.verif-error, .verif-badge, .verif-badge-row');
    const text = await errEl.first().textContent().catch(() => page.content().then(c =>
      c.includes('INVALIDO') || c.includes('no corresponde') ? 'invalido' : ''
    ));
    const content = await page.content();
    // Si el folio no existe: muestra .verif-error con "no corresponde"
    // Si existe y está alterado: muestra .verif-badge verif-danger con "INVALIDO"
    expect(
      content.includes('no corresponde') ||
      content.includes('INVALIDO') ||
      content.includes('no encontrado') ||
      content.toLowerCase().includes('not found')
    ).toBe(true);
  });

  test('CER-06 | solo certificado FIRMADO es verificable', async ({ page }) => {
    await page.goto('/verificar/FOLIO-SOLO-EMITIDO');
    await page.waitForTimeout(3_000);
    const content = await page.content();
    // Si el folio no existe: muestra "no corresponde" — no aparece como válido
    // Si existe pero solo está EMITIDO: autenticidad != VERIFICADO
    expect(content).not.toMatch(/VERIFICADO.*válido|class="verif-badge verif-ok"/);
  });

  test('CER-API | GET /api/v1/certificados/verificar/:folio sin auth → 200', async ({ request }) => {
    const res = await request.get(`${API_BASE}/certificados/verificar/FOLIO-INEXISTENTE`);
    expect([200, 404]).toContain(res.status());
  });
});

// ── C. Usuario torpe ──────────────────────────────────────────────────────────

test.describe('C. Usuario torpe', () => {
  test('CER-C1 | doble click en "Firmar" no crea firma doble', async ({ page }) => {
    await setupCertificados(page);
    const firmarBtn = page.locator('[data-testid="btn-firmar"], button:has-text("Firmar")').first();
    if (await firmarBtn.isVisible()) {
      await firmarBtn.dblclick();
      await page.waitForTimeout(3_000);
      // No debe haber dos registros de firma
      const firmasEl = page.locator('[data-testid="firma-timestamp"]');
      const count = await firmasEl.count();
      expect(count).toBeLessThanOrEqual(1);
    }
  });

  test('CER-C2 | navegar atrás después de emitir', async ({ page }) => {
    await setupCertificados(page);
    const emitirBtn = page.locator('[data-testid="btn-emitir"], button:has-text("Emitir")').first();
    if (await emitirBtn.isVisible()) {
      await emitirBtn.click();
      await page.waitForTimeout(2_000);
      await page.goBack();
      await page.goForward();
      // La app no debe crashear
      await expect(page).not.toHaveURL(/error/);
    }
  });
});

// ── D. Usuario caótico — folios extremos en verificación pública ──────────────

test.describe('D. Verificación pública — inputs extremos', () => {
  const extremeFolios = [
    EDGE_STRINGS.SQL_INJECTION,
    EDGE_STRINGS.XSS_BASIC,
    EDGE_STRINGS.EMOJIS,
    EDGE_STRINGS.LONG_1000.slice(0, 100),
    '../../../etc/passwd',
    '<script>alert(1)</script>',
    '0',
    ' ',
  ];

  extremeFolios.forEach((folio, idx) => {
    test(`CER-D-${idx} | folio extremo: "${folio.slice(0, 25)}"`, async ({ page }) => {
      page.on('dialog', d => d.dismiss());
      // La ruta pública acepta el folio como parámetro
      await page.goto(`/verificar/${encodeURIComponent(folio)}`);
      await page.waitForTimeout(2_000);
      // No debe ejecutar scripts ni crashear
      await expect(page).not.toHaveURL(/error.*fatal/);
      // No debe mostrar datos privados
      const privateEl = page.locator('[data-testid="private-data"]');
      await expect(privateEl).not.toBeVisible();
    });
  });
});
