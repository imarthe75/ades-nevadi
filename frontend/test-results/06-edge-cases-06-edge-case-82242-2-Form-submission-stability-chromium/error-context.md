# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: 06-edge-cases.spec.ts >> 06-edge-cases — Concurrent, RBAC, Network, Timeouts >> G2: Form submission stability
- Location: e2e/tests/06-edge-cases.spec.ts:433:7

# Error details

```
Test timeout of 30000ms exceeded.
```

```
Error: page.click: Test timeout of 30000ms exceeded.
Call log:
  - waiting for locator('p-button[data-testid="btn-crear"]')

```

# Test source

```ts
  336 |     const submitButton = page.locator('p-button:has-text("Crear")');
  337 |     await submitButton.dblclick();
  338 | 
  339 |     await page.waitForTimeout(1000);
  340 | 
  341 |     // Assert: Only one request (debounced)
  342 |     expect(requestCount).toBeLessThanOrEqual(1);
  343 |   });
  344 | 
  345 |   test('E2: Navigation away during form submission', async ({ page }) => {
  346 |     await page.goto('/alumnos');
  347 |     await page.click('p-button[data-testid="btn-crear"]');
  348 |     await page.waitForSelector('p-dialog');
  349 | 
  350 |     await page.fill('input[name="curp"]', 'ABCD123456HDFXYZ01');
  351 |     await page.fill('input[name="nombre"]', 'Juan Pérez');
  352 | 
  353 |     // Click submit
  354 |     await page.click('p-button:has-text("Crear")');
  355 | 
  356 |     // Immediately navigate away
  357 |     await page.goto('/dashboard');
  358 | 
  359 |     // Assert: No error in console
  360 |     const errors = page.context().on('console', msg => {
  361 |       expect(msg.type()).not.toBe('error');
  362 |     });
  363 |   });
  364 | 
  365 |   test('E3: Rapid cascading filter changes', async ({ page }) => {
  366 |     await page.goto('/calificaciones');
  367 | 
  368 |     // Rapidly change filters
  369 |     for (let i = 0; i < 5; i++) {
  370 |       await page.selectOption('select[name="nivel"]', '1');
  371 |       await page.waitForTimeout(100);
  372 |       await page.selectOption('select[name="nivel"]', '2');
  373 |       await page.waitForTimeout(100);
  374 |     }
  375 | 
  376 |     // Should not crash
  377 |     expect(page.url()).toContain('/calificaciones');
  378 |   });
  379 | 
  380 |   // ============================================================================
  381 |   // SUITE F: Memory & Performance Edge Cases
  382 |   // ============================================================================
  383 | 
  384 |   test('F1: Load 1000 rows without lag', async ({ page }) => {
  385 |     await page.goto('/alumnos?limit=1000');
  386 | 
  387 |     const start = Date.now();
  388 |     await page.waitForLoadState('networkidle');
  389 |     const loadTime = Date.now() - start;
  390 | 
  391 |     const rows = page.locator('p-datatable tbody tr');
  392 |     const rowCount = await rows.count();
  393 | 
  394 |     expect(rowCount).toBeGreaterThanOrEqual(100);
  395 |     expect(loadTime).toBeLessThan(5000); // <5s
  396 |   });
  397 | 
  398 |   test('F2: Scroll 1000 rows without jank (CLS <0.1)', async ({ page, context }) => {
  399 |     const client = await context.newCDPSession(page);
  400 | 
  401 |     await client.send('Performance.enable');
  402 |     await page.goto('/alumnos?limit=1000');
  403 |     await page.waitForLoadState('networkidle');
  404 | 
  405 |     // Rapid scroll
  406 |     for (let i = 0; i < 10; i++) {
  407 |       await page.evaluate(() => window.scrollBy(0, 500));
  408 |       await page.waitForTimeout(100);
  409 |     }
  410 | 
  411 |     // CLS metric should be low (no layout shifts)
  412 |     const metrics = await page.evaluate(() => ({
  413 |       cls: (window as any).webvitals?.cls || 0
  414 |     }));
  415 | 
  416 |     expect(metrics.cls).toBeLessThan(0.1);
  417 |   });
  418 | 
  419 |   // ============================================================================
  420 |   // SUITE G: Flakiness Validation (Run 3x)
  421 |   // ============================================================================
  422 | 
  423 |   test('G1: Stability check — run 3x consecutively', async ({ page }) => {
  424 |     for (let run = 0; run < 3; run++) {
  425 |       await page.goto('/alumnos');
  426 |       await page.waitForLoadState('networkidle');
  427 | 
  428 |       const rows = page.locator('p-datatable tbody tr');
  429 |       expect(await rows.count()).toBeGreaterThan(0);
  430 |     }
  431 |   });
  432 | 
  433 |   test('G2: Form submission stability', async ({ page }) => {
  434 |     for (let run = 0; run < 3; run++) {
  435 |       await page.goto('/alumnos');
> 436 |       await page.click('p-button[data-testid="btn-crear"]');
      |                  ^ Error: page.click: Test timeout of 30000ms exceeded.
  437 |       await page.waitForSelector('p-dialog', { timeout: 5000 });
  438 | 
  439 |       await page.fill('input[name="curp"]', `ABCD123456HDFXYZ${String(run).padStart(2, '0')}`);
  440 |       await page.fill('input[name="nombre"]', `Juan Pérez ${run}`);
  441 | 
  442 |       await page.click('p-button:has-text("Crear")');
  443 |       await page.waitForSelector('p-dialog', { state: 'hidden', timeout: 5000 });
  444 |     }
  445 |   });
  446 | 
  447 |   test('G3: Navigation stability (open/close 3x)', async ({ page }) => {
  448 |     for (let run = 0; run < 3; run++) {
  449 |       await page.goto('/alumnos');
  450 |       await page.waitForLoadState('networkidle');
  451 | 
  452 |       await page.goto('/dashboard');
  453 |       await page.waitForLoadState('networkidle');
  454 | 
  455 |       await page.goto('/calificaciones');
  456 |       await page.waitForLoadState('networkidle');
  457 |     }
  458 |   });
  459 | });
  460 | 
```