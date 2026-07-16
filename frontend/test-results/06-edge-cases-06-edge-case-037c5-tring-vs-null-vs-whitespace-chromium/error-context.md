# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: 06-edge-cases.spec.ts >> 06-edge-cases — Concurrent, RBAC, Network, Timeouts >> D4: Empty string vs null vs whitespace
- Location: e2e/tests/06-edge-cases.spec.ts:286:7

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
  188 | 
  189 |     // Come back online
  190 |     await page.context().setOffline(false);
  191 |     await page.reload();
  192 |     await page.waitForLoadState('networkidle');
  193 | 
  194 |     // Assert: Page recovers
  195 |     const rows = page.locator('p-datatable tbody tr');
  196 |     expect(await rows.count()).toBeGreaterThan(0);
  197 |   });
  198 | 
  199 |   test('C3: Slow endpoint (5s) with spinner', async ({ page }) => {
  200 |     await page.goto('/alumnos');
  201 | 
  202 |     // Trigger slow operation
  203 |     await page.click('p-button[data-testid="btn-crear"]');
  204 |     await page.waitForSelector('p-dialog', { timeout: 3000 });
  205 | 
  206 |     // Submit form (slow endpoint returns 200 after 5s)
  207 |     await page.fill('input[name="curp"]', 'ABCD123456HDFXYZ01');
  208 |     await page.fill('input[name="nombre"]', 'Juan Pérez');
  209 | 
  210 |     const startSubmit = Date.now();
  211 |     await page.click('p-button:has-text("Crear")');
  212 | 
  213 |     // Wait for success (spinner shown during request)
  214 |     await page.waitForSelector('[role="progressbar"]', { timeout: 1000 });
  215 |     await page.waitForSelector('p-dialog', { state: 'hidden', timeout: 10000 });
  216 | 
  217 |     const submitTime = Date.now() - startSubmit;
  218 | 
  219 |     // Assert: Slow request succeeded
  220 |     expect(submitTime).toBeGreaterThan(4000);
  221 |   });
  222 | 
  223 |   test('C4: Request timeout (>30s)', async ({ request }) => {
  224 |     // Simulate timeout with custom headers
  225 |     const response = await request.get(`/api/v1/alumnos/slow`, {
  226 |       headers: { 'Authorization': `Bearer test-token` },
  227 |       timeout: 5000  // 5s timeout
  228 |     });
  229 | 
  230 |     // Assert: Timeout or 504
  231 |     expect([408, 504, 0]).toContain(response.status());
  232 |   });
  233 | 
  234 |   // ============================================================================
  235 |   // SUITE D: Boundary Values & Input Validation
  236 |   // ============================================================================
  237 | 
  238 |   test('D1: Minimum valid CURP (18 chars)', async ({ page }) => {
  239 |     await page.goto('/alumnos');
  240 |     await page.click('p-button[data-testid="btn-crear"]');
  241 |     await page.waitForSelector('p-dialog');
  242 | 
  243 |     // Input minimum valid CURP
  244 |     await page.fill('input[name="curp"]', 'ABCD123456HDFXYZ01');
  245 |     await page.click('p-button:has-text("Crear")');
  246 | 
  247 |     // Assert: Success
  248 |     await page.waitForSelector('p-dialog', { state: 'hidden' });
  249 |   });
  250 | 
  251 |   test('D2: CURP too short (17 chars) — validation error', async ({ page }) => {
  252 |     await page.goto('/alumnos');
  253 |     await page.click('p-button[data-testid="btn-crear"]');
  254 |     await page.waitForSelector('p-dialog');
  255 | 
  256 |     // Input short CURP
  257 |     await page.fill('input[name="curp"]', 'ABCD123456HDFXYZ0');
  258 | 
  259 |     // Assert: Error shown
  260 |     const error = page.locator('[role="alert"]');
  261 |     await expect(error).toContainText('18 caracteres');
  262 |   });
  263 | 
  264 |   test('D3: Calificación boundary: 10.0 (valid), 10.1 (invalid)', async ({ page }) => {
  265 |     await page.goto('/calificaciones');
  266 |     await page.waitForLoadState('networkidle');
  267 | 
  268 |     // Valid: 10.0
  269 |     await page.fill('input[data-testid="grade-input"]', '10.0');
  270 |     await page.press('Tab');
  271 |     await page.waitForTimeout(500);
  272 | 
  273 |     // Should not show error
  274 |     let error = page.locator('[role="alert"]').first();
  275 |     await expect(error).not.toBeVisible();
  276 | 
  277 |     // Invalid: 10.1
  278 |     await page.fill('input[data-testid="grade-input"]', '10.1');
  279 |     await page.press('Tab');
  280 | 
  281 |     // Should show error
  282 |     error = page.locator('[role="alert"]').first();
  283 |     await expect(error).toContainText('máximo 10.0');
  284 |   });
  285 | 
  286 |   test('D4: Empty string vs null vs whitespace', async ({ page }) => {
  287 |     await page.goto('/alumnos');
> 288 |     await page.click('p-button[data-testid="btn-crear"]');
      |                ^ Error: page.click: Test timeout of 30000ms exceeded.
  289 |     await page.waitForSelector('p-dialog');
  290 | 
  291 |     // Try whitespace-only
  292 |     await page.fill('input[name="nombre"]', '   ');
  293 |     await page.click('p-button:has-text("Crear")');
  294 | 
  295 |     // Assert: Error
  296 |     const error = page.locator('[role="alert"]');
  297 |     await expect(error).toContainText('requerido');
  298 |   });
  299 | 
  300 |   test('D5: Very long string (1000 chars)', async ({ page }) => {
  301 |     const longString = 'a'.repeat(1000);
  302 |     await page.goto('/alumnos');
  303 |     await page.click('p-button[data-testid="btn-crear"]');
  304 |     await page.waitForSelector('p-dialog');
  305 | 
  306 |     await page.fill('input[name="nombre"]', longString);
  307 |     await page.click('p-button:has-text("Crear")');
  308 | 
  309 |     // Should be truncated or error
  310 |     const error = page.locator('[role="alert"]');
  311 |     const success = page.locator('p-dialog', { state: 'hidden' });
  312 | 
  313 |     const hasError = await error.isVisible();
  314 |     const hasSuccess = await success.isVisible();
  315 | 
  316 |     expect(hasError || hasSuccess).toBe(true);
  317 |   });
  318 | 
  319 |   // ============================================================================
  320 |   // SUITE E: Race Conditions & Timing Issues
  321 |   // ============================================================================
  322 | 
  323 |   test('E1: Double-click button — only one request sent', async ({ page, request }) => {
  324 |     let requestCount = 0;
  325 | 
  326 |     // Monitor requests
  327 |     request.on('response', () => {
  328 |       requestCount++;
  329 |     });
  330 | 
  331 |     await page.goto('/alumnos');
  332 |     await page.click('p-button[data-testid="btn-crear"]');
  333 |     await page.waitForSelector('p-dialog');
  334 | 
  335 |     // Double-click submit
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
```