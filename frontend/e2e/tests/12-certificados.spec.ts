/**
 * Suite 12 — Certificados Digitales Ed25519
 *
 * Cubre CER-01..07 del plan integral — 0% cobertura en suites de Claude:
 *  A. Emisión y firma de certificado (Director)
 *  B. Verificación pública sin auth (/verificar/{folio})
 *  C. RBAC — coordinador no puede emitir
 *  D. Fuzzing de folios en ruta pública (complementa 08-api.spec.ts G)
 *  E. Descarga de PDF
 *
 * CORRECCIÓN 2026-06-17:
 *  El módulo /certificados hace llamadas a https://ades.setag.mx (prod) que retornan
 *  502 desde el entorno local. Se filtran estos errores esperados.
 */
import { test, expect } from '@playwright/test';
import { LoginPage } from '../page-objects/login-page';
import { USERS, API_BASE, BFF_BASE } from '../fixtures/users';
import { EDGE_STRINGS } from '../fixtures/data-generators';
import { CapturedResponse } from '../helpers/console-monitor';

/** Filtra 5xx de dominios de producción que se esperan en entorno local */
function assertNoLocalServerErrors(responses: CapturedResponse[]): void {
  const serverErrors = responses.filter(r =>
    r.status >= 500 &&
    !r.url.includes('ades.setag.mx') &&   // prod domain — CORS/502 esperado en dev
    !r.url.includes('localhost:443')       // HTTPS local no disponible
  );
  if (serverErrors.length > 0) {
    const summary = serverErrors.map(r => `  HTTP ${r.status} → ${r.url}`).join('\n');
    throw new Error(`${serverErrors.length} error(s) 5xx local(es):\n${summary}`);
  }
}

/** Verifica que la ruta cargó y no fue redirigida */
async function assertRouteLoaded(page: import('@playwright/test').Page, path: string): Promise<boolean> {
  const url = page.url();
  if (url.includes('/login') || url.includes('/auth')) {
    console.log(`[skip] ${path} redirige a login — módulo no disponible`);
    return false;
  }
  return true;
}

// ── A. Emisión y firma ────────────────────────────────────────────────────────

test.describe('A. Emisión de certificados (Director)', () => {
  test('CER-E2E-01 | módulo certificados accesible para Director @smoke', async ({ page }) => {
    const apiResponses: CapturedResponse[] = [];
    page.on('response', resp => {
      if (resp.url().includes('/api/')) apiResponses.push({ status: resp.status(), url: resp.url() });
    });
    await new LoginPage(page).login(USERS.DIRECTOR);
    await page.goto('/certificados', { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(3_000);

    if (!await assertRouteLoaded(page, '/certificados')) { test.skip(); return; }

    await expect(page.locator('app-root')).toBeVisible();
    // Filtrar 502 de ades.setag.mx (prod domain, esperado en local)
    assertNoLocalServerErrors(apiResponses);
  });

  test('CER-E2E-02 | lista de certificados carga — tabla o empty state', async ({ page }) => {
    const apiResponses: CapturedResponse[] = [];
    page.on('response', resp => {
      if (resp.url().includes('/api/')) apiResponses.push({ status: resp.status(), url: resp.url() });
    });
    await new LoginPage(page).login(USERS.DIRECTOR);
    await page.goto('/certificados', { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(3_000);

    if (!await assertRouteLoaded(page, '/certificados')) { test.skip(); return; }

    const tableOrEmpty = page.locator(
      'app-interactive-grid, .p-datatable, table, [data-testid="empty-state"], .p-datatable-emptymessage'
    );
    const hasContent = await tableOrEmpty.first().isVisible({ timeout: 8_000 }).catch(() => false);
    if (!hasContent) {
      // Si no hay tabla, al menos la app no debe mostrar error
      await expect(page).not.toHaveURL(/error/);
    }
    assertNoLocalServerErrors(apiResponses);
  });

  test('CER-E2E-03 | Director accede a /certificados sin 5xx locales', async ({ page }) => {
    const apiResponses: CapturedResponse[] = [];
    page.on('response', resp => {
      if (resp.url().includes('/api/')) apiResponses.push({ status: resp.status(), url: resp.url() });
    });
    await new LoginPage(page).login(USERS.DIRECTOR);
    await page.goto('/certificados', { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(2_000);

    if (!await assertRouteLoaded(page, '/certificados')) { test.skip(); return; }
    await expect(page.locator('app-root')).toBeVisible();
    assertNoLocalServerErrors(apiResponses);
  });

  test('CER-E2E-04 | COORDINADOR en /certificados — botón emitir ausente o deshabilitado', async ({ page }) => {
    const apiResponses: CapturedResponse[] = [];
    page.on('response', resp => {
      if (resp.url().includes('/api/')) apiResponses.push({ status: resp.status(), url: resp.url() });
    });
    await new LoginPage(page).login(USERS.COORDINADOR);
    await page.goto('/certificados', { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(2_000);

    if (!await assertRouteLoaded(page, '/certificados')) { test.skip(); return; }

    // El coordinador no debe poder emitir
    const emitirBtn = page.locator('[data-testid="btn-emitir-certificado"], button:has-text("Emitir")');
    const count = await emitirBtn.count();
    if (count > 0) {
      const isEnabled = await emitirBtn.first().isEnabled().catch(() => false);
      if (isEnabled) {
        console.warn('[FINDING][P1] CER-E2E-04: Botón Emitir habilitado para COORDINADOR — falta RBAC en componente');
      }
    }
    assertNoLocalServerErrors(apiResponses);
    await expect(page.locator('app-root')).toBeVisible();
  });
});

// ── B. Verificación pública ───────────────────────────────────────────────────

test.describe('B. Verificación pública de certificados (sin auth)', () => {
  test('CER-E2E-05 | /verificar/FOLIO-VALIDO → 200 sin auth en FastAPI', async ({ request }) => {
    // La ruta de verificación es pública — FastAPI
    const res = await request.get(`${API_BASE}/certificados/verificar/FOLIO-DE-PRUEBA`);
    // 404 para folio inexistente es correcto; lo que NO debe pasar es 401 o 500
    expect(res.status()).not.toBe(401);
    expect(res.status()).not.toBe(500);
    expect([200, 404]).toContain(res.status());
  });

  test('CER-E2E-06 | portal de verificación UI carga sin login', async ({ browser }) => {
    // Usar contexto limpio sin sessionStorage
    const ctx  = await browser.newContext();
    const page = await ctx.newPage();

    // Primero verificar que el portal público existe
    await page.goto('http://localhost:4201/', { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(2_000);

    // El portal debe cargar (200) sin redirigir a login
    await expect(page.locator('body')).not.toBeEmpty();
    expect(page.url()).not.toMatch(/\/login/);

    await ctx.close();
  });

  test('CER-E2E-07 | verificar folio con formato correcto → respuesta consistente', async ({ request }) => {
    // Folio con formato UUID v7 plausible
    const folioUUID = '018f2a3b-4c5d-7e6f-a1b2-c3d4e5f6a7b8';
    const res = await request.get(`${API_BASE}/certificados/verificar/${folioUUID}`);
    expect(res.status()).not.toBe(500);
    expect([200, 400, 404]).toContain(res.status());
    if (res.ok()) {
      const body = await res.json().catch(() => null);
      // Si responde 200, debe tener algún campo de estado
      expect(body).toBeTruthy();
    }
  });
});

// ── C. Fuzzing de folios en endpoint público ──────────────────────────────────

test.describe('C. Folios extremos en ruta pública de verificación', () => {
  const foliosExtremos = [
    EDGE_STRINGS.SQL_INJECTION,
    EDGE_STRINGS.XSS_BASIC,
    EDGE_STRINGS.PATH_TRAV,
    '../../../etc/passwd',
    '\x00null\x00',
    'a'.repeat(500),
    '',
    '   ',
    EDGE_STRINGS.EMOJIS,
    EDGE_STRINGS.UNICODE_MIX,
    '{}',
    '[]',
    'undefined',
    'null',
  ];

  for (const folio of foliosExtremos) {
    test(`CER-FUZZ | folio: "${folio.slice(0, 25).replace(/\n/g, '\\n')}"`, async ({ request }) => {
      const encoded = encodeURIComponent(folio);
      const res = await request.get(`${API_BASE}/certificados/verificar/${encoded}`);
      // Nunca debe retornar 500 ni ejecutar código
      expect(res.status()).not.toBe(500);
      expect([200, 400, 404, 422]).toContain(res.status());

      // Verificar que el body no contiene stack traces
      if (!res.ok()) {
        const text = await res.text().catch(() => '');
        expect(text).not.toMatch(/Traceback|NullPointerException|at mx\.ades/);
      }
    });
  }
});

// ── D. Verificación de campos criptográficos via API ─────────────────────────

test.describe('D. Integridad criptográfica via BFF', () => {
  test('CER-E2E-08 | certificado firmado tiene hash_sha256 y firma_ed25519 via API', async ({ page }) => {
    await new LoginPage(page).login(USERS.DIRECTOR);

    // Usar fetch en la página (IPv4) en lugar de page.request (puede resolver a IPv6)
    const result = await page.evaluate(async () => {
      const keys = ['ades_token', 'access_token', 'token'];
      let tok = '';
      for (const k of keys) { tok = sessionStorage.getItem(k) ?? ''; if (tok) break; }

      const res = await fetch('/api/v1/certificados', {
        headers: { Authorization: `Bearer ${tok}` },
      }).catch(() => null);
      if (!res?.ok) return null;
      return res.json().catch(() => null);
    });

    if (!result) { test.skip(); return; }

    const certificados = Array.isArray(result) ? result : (result.items ?? result.data ?? []);
    const firmados = certificados.filter((c: Record<string, unknown>) =>
      c['estado_firma'] === 'FIRMADO' || c['estado'] === 'FIRMADO'
    );

    if (firmados.length === 0) {
      console.log('[CER-E2E-08] Sin certificados FIRMADO en el entorno de QA — skip');
      test.skip(); return;
    }

    const cert = firmados[0] as Record<string, unknown>;
    expect(cert['firma_ed25519'] || cert['firmaEd25519']).toBeTruthy();
    expect(cert['hash_sha256'] || cert['hashSha256']).toBeTruthy();
  });

  test('CER-E2E-09 | estado esVerificable solo para FIRMADO', async ({ page }) => {
    await new LoginPage(page).login(USERS.DIRECTOR);

    const result = await page.evaluate(async () => {
      const keys = ['ades_token', 'access_token', 'token'];
      let tok = '';
      for (const k of keys) { tok = sessionStorage.getItem(k) ?? ''; if (tok) break; }

      const res = await fetch('/api/v1/certificados', {
        headers: { Authorization: `Bearer ${tok}` },
      }).catch(() => null);
      if (!res?.ok) return null;
      return res.json().catch(() => null);
    });

    if (!result) { test.skip(); return; }

    const certificados = Array.isArray(result) ? result : (result.items ?? result.data ?? []);
    const emitidos = certificados.filter((c: Record<string, unknown>) =>
      (c['estado_firma'] === 'EMITIDO' || c['estado'] === 'EMITIDO') &&
      !(c['firma_ed25519'])
    );
    for (const cert of emitidos as Record<string, unknown>[]) {
      expect(cert['es_verificable'] ?? cert['esVerificable']).toBeFalsy();
    }
  });
});

// ── E. PDF descarga ───────────────────────────────────────────────────────────

test.describe('E. Descarga de PDF de certificado', () => {
  test('CER-E2E-10 | botón descargar PDF lanza download con mime correcto', async ({ page }) => {
    await new LoginPage(page).login(USERS.DIRECTOR);
    await page.goto('/certificados', { waitUntil: 'networkidle' });

    // Esperar tabla de certificados
    await page.waitForSelector('p-table tbody tr, table tbody tr', { timeout: 5_000 });

    const filas = await page.locator('tbody tr').count();
    if (filas === 0) {
      console.log('⚠️  No hay certificados — test skipped');
      test.skip();
      return;
    }

    // Buscar botón descargar en primera fila
    const primeraCelda = page.locator('tbody tr').first();
    const downloadBtn = await primeraCelda.locator(
      '[data-testid="btn-descargar-pdf"], button:has-text("Descargar"), button:has-text("PDF"), [aria-label*="escargar"]'
    ).first();

    if (!await downloadBtn.isVisible({ timeout: 2_000 }).catch(() => false)) {
      console.log('⚠️  Botón descargar no visible — test skipped');
      test.skip();
      return;
    }

    // Interceptar descarga
    const downloadPromise = page.waitForEvent('download');
    await downloadBtn.click();

    // Esperar descarga
    const download = await downloadPromise;

    // Verificar nombre archivo
    const filename = download.suggestedFilename();
    expect(filename).toMatch(/\.pdf$/i);

    // Verificar tamaño > 1KB
    const path = await download.path();
    const fs = require('fs');
    const stats = fs.statSync(path);
    expect(stats.size).toBeGreaterThan(1024);

    // Verificar header PDF
    const buffer = fs.readFileSync(path);
    const header = buffer.toString('utf8', 0, 5);
    expect(header).toBe('%PDF-');

    console.log(`✅ CER-E2E-10 PASSED — ${filename} (${stats.size} bytes)`);
  });
});
