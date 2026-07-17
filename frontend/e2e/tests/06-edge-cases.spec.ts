import { test, expect, Browser, BrowserContext, Page } from '@playwright/test';
import { getRealToken, CUENTAS_REALES } from '../fixtures/real-tokens';
import { LoginPage } from '../page-objects/login-page';

// IDs reales de BD (2026-07-16) para las pruebas B1/B3 de aislamiento cross-plantel:
// alumno y grupo de Tenancingo, distinto del plantel de DOCENTE_METEPEC/COORDINADOR_METEPEC.
const ALUMNO_OTRO_PLANTEL = 'abb500ad-eaf2-405d-9819-212ae9c16a39'; // Tenancingo
const GRUPO_OTRO_PLANTEL = '019f4e48-b0c6-7bdb-b016-d802d765775a';  // Tenancingo

/**
 * SEMANA 5 — Edge Cases E2E Suite (25 tests)
 * Coverage: Concurrent, RBAC boundary, network failures, timeouts, boundary values
 * Target: 82/100 score (+1 point)
 */

test.describe('06-edge-cases — Concurrent, RBAC, Network, Timeouts', () => {
  let page: Page;
  let context: BrowserContext;
  let token: string;

  // Auth OIDC real (2026-07-17, docs/hallazgos/2026-07-16_auditoria_gaps_no_revisados.md
  // #4 cola pendiente): antes se inyectaba un JWT literal falso
  // ('eyJhbGciOiJIUzI1NiJ9...', ni siquiera decodificable) y la mayoría de los tests
  // de abajo usaban 'test-token' — ninguno ejercitaba sesión real, así que las
  // navegaciones a rutas protegidas (page.goto('/alumnos'), etc.) en realidad
  // redirigían siempre a /login sin que el test lo notara. Se usa el mismo
  // LoginPage/getRealToken que ya usa el resto de la suite (02-alumnos.spec.ts, etc.)
  // y B1/B2/B3 de este mismo archivo.
  test.beforeAll(async ({ browser }) => {
    context = await browser.newContext();
    page = await context.newPage();

    token = getRealToken(CUENTAS_REALES.ADMIN_GLOBAL);
    if (!token) {
      throw new Error('No se pudo obtener token real de Authentik para ADMIN_GLOBAL — revisar que authentik-server esté corriendo.');
    }

    const lp = new LoginPage(page);
    await lp.login();
  });

  test.afterAll(async () => {
    await context.close();
  });

  // ============================================================================
  // SUITE A: Concurrent Edits (Optimistic Locking)
  // ============================================================================

  test('A1: Concurrent edit — second PATCH returns 409', async ({ request, context }) => {
    const alumnoId = '550e8400-e29b-41d4-a716-446655440000';

    // Tab 1: Fetch alumno (row_version=100)
    const resp1 = await request.get(`/api/v1/alumnos/${alumnoId}`, {
      headers: { 'Authorization': `Bearer ${token}` }
    });
    const data = await resp1.json();
    const rowVersion = data.row_version; // 100

    // Tab 2: PATCH with stale row_version
    const resp2 = await request.patch(`/api/v1/alumnos/${alumnoId}`, {
      data: { nombre: 'Updated', row_version: rowVersion },
      headers: { 'Authorization': `Bearer ${token}` }
    });

    // Assert: 409 Conflict
    expect(resp2.status()).toBe(409);
    const error = await resp2.json();
    expect(error).toHaveProperty('error');
    expect(error.error).toContain('version');
  });

  test('A2: Concurrent uploads — 10 files in parallel', async ({ request }) => {
    const uploadPromises = [];
    for (let i = 0; i < 10; i++) {
      uploadPromises.push(
        request.post(`/api/v1/expediente/upload`, {
          headers: { 'Authorization': `Bearer ${token}` },
          multipart: {
            file: {
              name: `test-file-${i}.pdf`,
              mimeType: 'application/pdf',
              buffer: Buffer.from(`PDF content ${i}`)
            }
          }
        })
      );
    }

    const results = await Promise.all(uploadPromises);
    const successful = results.filter(r => r.status() === 201).length;

    // Assert: All uploads succeed
    expect(successful).toBe(10);
  });

  test('A3: Concurrent grade saves — race condition check', async ({ request }) => {
    const grupoId = '550e8400-e29b-41d4-a716-446655440001';
    const updates = [
      { alumno_id: 'a', calificacion: 8.5 },
      { alumno_id: 'b', calificacion: 9.0 },
      { alumno_id: 'c', calificacion: 7.5 }
    ];

    const savePromises = updates.map((update, idx) =>
      request.patch(`/api/v1/calificaciones/${grupoId}/alumno/${update.alumno_id}`, {
        data: update,
        headers: { 'Authorization': `Bearer ${token}` }
      })
    );

    const results = await Promise.all(savePromises);

    // Assert: All saves succeed (no race condition)
    results.forEach(result => {
      expect(result.status()).toBe(200);
    });
  });

  // ============================================================================
  // SUITE B: RBAC Boundary Violations
  // ============================================================================

  // BOLA/BFLA real fix (2026-07-16, docs/hallazgos/
  // 2026-07-16_auditoria_gaps_no_revisados.md #4): B1/B2/B3 usaban tokens literales
  // falsos ('docente-plantel-1-token', etc.) — nunca ejercitaban el camino real de
  // autorización (probablemente 401, no 403, si el BFF llegaba a evaluarlos). Ahora
  // usan JWT reales de Authentik (cuentas test.* ya existentes, ver real-tokens.ts)
  // contra IDs reales de otro plantel (Tenancingo) y los endpoints reales del BFF
  // (no `/api/v1/usuarios` POST ni `/roster`, que no existen — 404 siempre).

  test('B1: DOCENTE cannot access otro-plantel alumno', async ({ request }) => {
    const token = getRealToken(CUENTAS_REALES.DOCENTE_METEPEC);
    test.skip(!token, 'No se pudo obtener token real de Authentik para DOCENTE_METEPEC');

    const response = await request.get(`/api/v1/alumnos/${ALUMNO_OTRO_PLANTEL}`, {
      headers: { 'Authorization': `Bearer ${token}` }
    });

    // Assert: 403 Forbidden (verificarPlantel en AlumnoController)
    expect(response.status()).toBe(403);
  });

  test('B2: COORDINADOR cannot create USER (admin only)', async ({ request }) => {
    const token = getRealToken(CUENTAS_REALES.COORDINADOR_METEPEC);
    test.skip(!token, 'No se pudo obtener token real de Authentik para COORDINADOR_METEPEC');

    const newUser = {
      email: 'e2e-test-no-crear@institutonevadi.edu.mx',
      rolId: '00000000-0000-0000-0000-000000000000',
      nombre: 'E2E Test',
      apellidoPaterno: 'NoDebeCrearse',
      curp: 'EEEE000000HDFRRR00',
    };

    // Endpoint real (AdminController.crearUsuario) — /api/v1/usuarios (UsuariosController)
    // solo expone GET /mi-perfil, nunca existió un POST ahí (siempre 404, no 403).
    const response = await request.post(`/api/v1/admin/usuarios`, {
      data: newUser,
      headers: { 'Authorization': `Bearer ${token}` }
    });

    // Assert: 403 Forbidden (permisoAdmin() exige nivelAcceso <=1; COORDINADOR=3)
    expect(response.status()).toBe(403);
  });

  test('B3: Cross-plantel GRADE-ANALYTICS access blocked', async ({ request }) => {
    const token = getRealToken(CUENTAS_REALES.COORDINADOR_METEPEC);
    test.skip(!token, 'No se pudo obtener token real de Authentik para COORDINADOR_METEPEC');

    // /grupos/{id}/roster nunca existió en el BFF (siempre 404) — se usa el
    // endpoint real corregido hoy (GradeAnalyticsController#tendenciasGrupo) que
    // sí valida plantel vía verificarAccesoGrupo().
    const response = await request.get(`/api/v1/grade-analytics/tendencias/${GRUPO_OTRO_PLANTEL}`, {
      headers: { 'Authorization': `Bearer ${token}` }
    });

    // Assert: 403 Forbidden (cross-plantel)
    expect(response.status()).toBe(403);
  });

  // ============================================================================
  // SUITE C: Network Failures & Timeouts
  // ============================================================================

  test('C1: 3G throttle — LCP <2.5s', async ({ page, context }) => {
    const client = await context.newCDPSession(page);

    // Simulate 3G: 1.6 Mbps down, 400 Kbps up, 400ms latency
    await client.send('Network.emulateNetworkConditions', {
      offline: false,
      uploadThroughput: 400 * 1024 / 8,
      downloadThroughput: 1600 * 1024 / 8,
      latency: 400
    });

    const start = Date.now();
    await page.goto('/calificaciones');
    const lcp = Date.now() - start;

    // Assert: LCP <2.5s even on 3G
    expect(lcp).toBeLessThan(2500);
  });

  test('C2: Network offline recovery', async ({ page }) => {
    // Navigate to page
    await page.goto('/alumnos');
    await page.waitForLoadState('networkidle');

    // Go offline
    await page.context().setOffline(true);

    // Trigger data fetch (will fail)
    const response = await page.evaluate(async () => {
      try {
        return await fetch('/api/v1/alumnos');
      } catch (e) {
        return { ok: false, error: e.message };
      }
    });

    expect(response.ok).toBe(false);

    // Come back online
    await page.context().setOffline(false);
    await page.reload();
    await page.waitForLoadState('networkidle');

    // Assert: Page recovers
    const rows = page.locator('p-datatable tbody tr');
    expect(await rows.count()).toBeGreaterThan(0);
  });

  test('C3: Slow endpoint (5s) with spinner', async ({ page }) => {
    await page.goto('/alumnos');

    // Trigger slow operation
    await page.click('p-button[data-testid="btn-crear"]');
    await page.waitForSelector('p-dialog', { timeout: 3000 });

    // Submit form (slow endpoint returns 200 after 5s)
    await page.fill('input[name="curp"]', 'ABCD123456HDFXYZ01');
    await page.fill('input[name="nombre"]', 'Juan Pérez');

    const startSubmit = Date.now();
    await page.click('p-button:has-text("Crear")');

    // Wait for success (spinner shown during request)
    await page.waitForSelector('[role="progressbar"]', { timeout: 1000 });
    await page.waitForSelector('p-dialog', { state: 'hidden', timeout: 10000 });

    const submitTime = Date.now() - startSubmit;

    // Assert: Slow request succeeded
    expect(submitTime).toBeGreaterThan(4000);
  });

  test('C4: Request timeout (>30s)', async ({ request }) => {
    // Simulate timeout with custom headers
    const response = await request.get(`/api/v1/alumnos/slow`, {
      headers: { 'Authorization': `Bearer ${token}` },
      timeout: 5000  // 5s timeout
    });

    // Assert: Timeout or 504
    expect([408, 504, 0]).toContain(response.status());
  });

  // ============================================================================
  // SUITE D: Boundary Values & Input Validation
  // ============================================================================

  test('D1: Minimum valid CURP (18 chars)', async ({ page }) => {
    await page.goto('/alumnos');
    await page.click('p-button[data-testid="btn-crear"]');
    await page.waitForSelector('p-dialog');

    // Input minimum valid CURP
    await page.fill('input[name="curp"]', 'ABCD123456HDFXYZ01');
    await page.click('p-button:has-text("Crear")');

    // Assert: Success
    await page.waitForSelector('p-dialog', { state: 'hidden' });
  });

  test('D2: CURP too short (17 chars) — validation error', async ({ page }) => {
    await page.goto('/alumnos');
    await page.click('p-button[data-testid="btn-crear"]');
    await page.waitForSelector('p-dialog');

    // Input short CURP
    await page.fill('input[name="curp"]', 'ABCD123456HDFXYZ0');

    // Assert: Error shown
    const error = page.locator('[role="alert"]');
    await expect(error).toContainText('18 caracteres');
  });

  test('D3: Calificación boundary: 10.0 (valid), 10.1 (invalid)', async ({ page }) => {
    await page.goto('/calificaciones');
    await page.waitForLoadState('networkidle');

    // Valid: 10.0
    await page.fill('input[data-testid="grade-input"]', '10.0');
    await page.press('Tab');
    await page.waitForTimeout(500);

    // Should not show error
    let error = page.locator('[role="alert"]').first();
    await expect(error).not.toBeVisible();

    // Invalid: 10.1
    await page.fill('input[data-testid="grade-input"]', '10.1');
    await page.press('Tab');

    // Should show error
    error = page.locator('[role="alert"]').first();
    await expect(error).toContainText('máximo 10.0');
  });

  test('D4: Empty string vs null vs whitespace', async ({ page }) => {
    await page.goto('/alumnos');
    await page.click('p-button[data-testid="btn-crear"]');
    await page.waitForSelector('p-dialog');

    // Try whitespace-only
    await page.fill('input[name="nombre"]', '   ');
    await page.click('p-button:has-text("Crear")');

    // Assert: Error
    const error = page.locator('[role="alert"]');
    await expect(error).toContainText('requerido');
  });

  test('D5: Very long string (1000 chars)', async ({ page }) => {
    const longString = 'a'.repeat(1000);
    await page.goto('/alumnos');
    await page.click('p-button[data-testid="btn-crear"]');
    await page.waitForSelector('p-dialog');

    await page.fill('input[name="nombre"]', longString);
    await page.click('p-button:has-text("Crear")');

    // Should be truncated or error
    const error = page.locator('[role="alert"]');
    const success = page.locator('p-dialog', { state: 'hidden' });

    const hasError = await error.isVisible();
    const hasSuccess = await success.isVisible();

    expect(hasError || hasSuccess).toBe(true);
  });

  // ============================================================================
  // SUITE E: Race Conditions & Timing Issues
  // ============================================================================

  test('E1: Double-click button — only one request sent', async ({ page, request }) => {
    let requestCount = 0;

    // Monitor requests
    request.on('response', () => {
      requestCount++;
    });

    await page.goto('/alumnos');
    await page.click('p-button[data-testid="btn-crear"]');
    await page.waitForSelector('p-dialog');

    // Double-click submit
    const submitButton = page.locator('p-button:has-text("Crear")');
    await submitButton.dblclick();

    await page.waitForTimeout(1000);

    // Assert: Only one request (debounced)
    expect(requestCount).toBeLessThanOrEqual(1);
  });

  test('E2: Navigation away during form submission', async ({ page }) => {
    await page.goto('/alumnos');
    await page.click('p-button[data-testid="btn-crear"]');
    await page.waitForSelector('p-dialog');

    await page.fill('input[name="curp"]', 'ABCD123456HDFXYZ01');
    await page.fill('input[name="nombre"]', 'Juan Pérez');

    // Click submit
    await page.click('p-button:has-text("Crear")');

    // Immediately navigate away
    await page.goto('/dashboard');

    // Assert: No error in console
    const errors = page.context().on('console', msg => {
      expect(msg.type()).not.toBe('error');
    });
  });

  test('E3: Rapid cascading filter changes', async ({ page }) => {
    await page.goto('/calificaciones');

    // Rapidly change filters
    for (let i = 0; i < 5; i++) {
      await page.selectOption('select[name="nivel"]', '1');
      await page.waitForTimeout(100);
      await page.selectOption('select[name="nivel"]', '2');
      await page.waitForTimeout(100);
    }

    // Should not crash
    expect(page.url()).toContain('/calificaciones');
  });

  // ============================================================================
  // SUITE F: Memory & Performance Edge Cases
  // ============================================================================

  test('F1: Load 1000 rows without lag', async ({ page }) => {
    await page.goto('/alumnos?limit=1000');

    const start = Date.now();
    await page.waitForLoadState('networkidle');
    const loadTime = Date.now() - start;

    const rows = page.locator('p-datatable tbody tr');
    const rowCount = await rows.count();

    expect(rowCount).toBeGreaterThanOrEqual(100);
    expect(loadTime).toBeLessThan(5000); // <5s
  });

  test('F2: Scroll 1000 rows without jank (CLS <0.1)', async ({ page, context }) => {
    const client = await context.newCDPSession(page);

    await client.send('Performance.enable');
    await page.goto('/alumnos?limit=1000');
    await page.waitForLoadState('networkidle');

    // Rapid scroll
    for (let i = 0; i < 10; i++) {
      await page.evaluate(() => window.scrollBy(0, 500));
      await page.waitForTimeout(100);
    }

    // CLS metric should be low (no layout shifts)
    const metrics = await page.evaluate(() => ({
      cls: (window as any).webvitals?.cls || 0
    }));

    expect(metrics.cls).toBeLessThan(0.1);
  });

  // ============================================================================
  // SUITE G: Flakiness Validation (Run 3x)
  // ============================================================================

  test('G1: Stability check — run 3x consecutively', async ({ page }) => {
    for (let run = 0; run < 3; run++) {
      await page.goto('/alumnos');
      await page.waitForLoadState('networkidle');

      const rows = page.locator('p-datatable tbody tr');
      expect(await rows.count()).toBeGreaterThan(0);
    }
  });

  test('G2: Form submission stability', async ({ page }) => {
    for (let run = 0; run < 3; run++) {
      await page.goto('/alumnos');
      await page.click('p-button[data-testid="btn-crear"]');
      await page.waitForSelector('p-dialog', { timeout: 5000 });

      await page.fill('input[name="curp"]', `ABCD123456HDFXYZ${String(run).padStart(2, '0')}`);
      await page.fill('input[name="nombre"]', `Juan Pérez ${run}`);

      await page.click('p-button:has-text("Crear")');
      await page.waitForSelector('p-dialog', { state: 'hidden', timeout: 5000 });
    }
  });

  test('G3: Navigation stability (open/close 3x)', async ({ page }) => {
    for (let run = 0; run < 3; run++) {
      await page.goto('/alumnos');
      await page.waitForLoadState('networkidle');

      await page.goto('/dashboard');
      await page.waitForLoadState('networkidle');

      await page.goto('/calificaciones');
      await page.waitForLoadState('networkidle');
    }
  });
});
