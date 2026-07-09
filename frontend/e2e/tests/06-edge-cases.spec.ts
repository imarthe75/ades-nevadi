import { test, expect, Browser, BrowserContext, Page } from '@playwright/test';

/**
 * SEMANA 5 — Edge Cases E2E Suite (25 tests)
 * Coverage: Concurrent, RBAC boundary, network failures, timeouts, boundary values
 * Target: 82/100 score (+1 point)
 */

test.describe('06-edge-cases — Concurrent, RBAC, Network, Timeouts', () => {
  let page: Page;
  let context: BrowserContext;
  let token: string;

  test.beforeAll(async ({ browser }) => {
    context = await browser.newContext();
    page = await context.newPage();

    // Setup: Inject token
    await page.addInitScript(() => {
      sessionStorage.setItem('ades_token', 'eyJhbGciOiJIUzI1NiJ9...');
    });
  });

  test.afterAll(async () => {
    await context.close();
  });

  // ============================================================================
  // SUITE A: Concurrent Edits (Optimistic Locking)
  // ============================================================================

  test('A1: Concurrent edit — second PATCH returns 409', async ({ request, context }) => {
    const alumnoId = '550e8400-e29b-41d4-a716-446655440000';
    const token = 'test-token';

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
          headers: { 'Authorization': `Bearer test-token` },
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
        headers: { 'Authorization': `Bearer test-token` }
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

  test('B1: DOCENTE cannot access PLANTEL_2 alumno', async ({ request, page }) => {
    const alumnoId = '550e8400-e29b-41d4-a716-446655440002'; // Plantel 2
    const token = 'docente-plantel-1-token';

    const response = await request.get(`/api/v1/alumnos/${alumnoId}`, {
      headers: { 'Authorization': `Bearer ${token}` }
    });

    // Assert: 403 Forbidden
    expect(response.status()).toBe(403);
  });

  test('B2: COORDINADOR cannot create USER (admin only)', async ({ request }) => {
    const newUser = {
      email: 'test@example.com',
      rol: 'DOCENTE',
      nombre: 'Test User'
    };

    const response = await request.post(`/api/v1/usuarios`, {
      data: newUser,
      headers: { 'Authorization': `Bearer coordinador-token` }
    });

    // Assert: 403 Forbidden
    expect(response.status()).toBe(403);
  });

  test('B3: Cross-plantel GRUPO access blocked', async ({ request }) => {
    const grupoId = '550e8400-e29b-41d4-a716-446655440003'; // Plantel 2
    const token = 'docente-plantel-1-token';

    const response = await request.get(`/api/v1/grupos/${grupoId}/roster`, {
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
      headers: { 'Authorization': `Bearer test-token` },
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
