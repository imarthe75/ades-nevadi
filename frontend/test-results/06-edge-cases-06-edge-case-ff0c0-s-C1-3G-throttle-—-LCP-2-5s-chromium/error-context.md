# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: 06-edge-cases.spec.ts >> 06-edge-cases — Concurrent, RBAC, Network, Timeouts >> C1: 3G throttle — LCP <2.5s
- Location: e2e/tests/06-edge-cases.spec.ts:151:7

# Error details

```
Error: expect(received).toBeLessThan(expected)

Expected: < 2500
Received:   14916
```

# Test source

```ts
  67  |             }
  68  |           }
  69  |         })
  70  |       );
  71  |     }
  72  | 
  73  |     const results = await Promise.all(uploadPromises);
  74  |     const successful = results.filter(r => r.status() === 201).length;
  75  | 
  76  |     // Assert: All uploads succeed
  77  |     expect(successful).toBe(10);
  78  |   });
  79  | 
  80  |   test('A3: Concurrent grade saves — race condition check', async ({ request }) => {
  81  |     const grupoId = '550e8400-e29b-41d4-a716-446655440001';
  82  |     const updates = [
  83  |       { alumno_id: 'a', calificacion: 8.5 },
  84  |       { alumno_id: 'b', calificacion: 9.0 },
  85  |       { alumno_id: 'c', calificacion: 7.5 }
  86  |     ];
  87  | 
  88  |     const savePromises = updates.map((update, idx) =>
  89  |       request.patch(`/api/v1/calificaciones/${grupoId}/alumno/${update.alumno_id}`, {
  90  |         data: update,
  91  |         headers: { 'Authorization': `Bearer test-token` }
  92  |       })
  93  |     );
  94  | 
  95  |     const results = await Promise.all(savePromises);
  96  | 
  97  |     // Assert: All saves succeed (no race condition)
  98  |     results.forEach(result => {
  99  |       expect(result.status()).toBe(200);
  100 |     });
  101 |   });
  102 | 
  103 |   // ============================================================================
  104 |   // SUITE B: RBAC Boundary Violations
  105 |   // ============================================================================
  106 | 
  107 |   test('B1: DOCENTE cannot access PLANTEL_2 alumno', async ({ request, page }) => {
  108 |     const alumnoId = '550e8400-e29b-41d4-a716-446655440002'; // Plantel 2
  109 |     const token = 'docente-plantel-1-token';
  110 | 
  111 |     const response = await request.get(`/api/v1/alumnos/${alumnoId}`, {
  112 |       headers: { 'Authorization': `Bearer ${token}` }
  113 |     });
  114 | 
  115 |     // Assert: 403 Forbidden
  116 |     expect(response.status()).toBe(403);
  117 |   });
  118 | 
  119 |   test('B2: COORDINADOR cannot create USER (admin only)', async ({ request }) => {
  120 |     const newUser = {
  121 |       email: 'test@example.com',
  122 |       rol: 'DOCENTE',
  123 |       nombre: 'Test User'
  124 |     };
  125 | 
  126 |     const response = await request.post(`/api/v1/usuarios`, {
  127 |       data: newUser,
  128 |       headers: { 'Authorization': `Bearer coordinador-token` }
  129 |     });
  130 | 
  131 |     // Assert: 403 Forbidden
  132 |     expect(response.status()).toBe(403);
  133 |   });
  134 | 
  135 |   test('B3: Cross-plantel GRUPO access blocked', async ({ request }) => {
  136 |     const grupoId = '550e8400-e29b-41d4-a716-446655440003'; // Plantel 2
  137 |     const token = 'docente-plantel-1-token';
  138 | 
  139 |     const response = await request.get(`/api/v1/grupos/${grupoId}/roster`, {
  140 |       headers: { 'Authorization': `Bearer ${token}` }
  141 |     });
  142 | 
  143 |     // Assert: 403 Forbidden (cross-plantel)
  144 |     expect(response.status()).toBe(403);
  145 |   });
  146 | 
  147 |   // ============================================================================
  148 |   // SUITE C: Network Failures & Timeouts
  149 |   // ============================================================================
  150 | 
  151 |   test('C1: 3G throttle — LCP <2.5s', async ({ page, context }) => {
  152 |     const client = await context.newCDPSession(page);
  153 | 
  154 |     // Simulate 3G: 1.6 Mbps down, 400 Kbps up, 400ms latency
  155 |     await client.send('Network.emulateNetworkConditions', {
  156 |       offline: false,
  157 |       uploadThroughput: 400 * 1024 / 8,
  158 |       downloadThroughput: 1600 * 1024 / 8,
  159 |       latency: 400
  160 |     });
  161 | 
  162 |     const start = Date.now();
  163 |     await page.goto('/calificaciones');
  164 |     const lcp = Date.now() - start;
  165 | 
  166 |     // Assert: LCP <2.5s even on 3G
> 167 |     expect(lcp).toBeLessThan(2500);
      |                 ^ Error: expect(received).toBeLessThan(expected)
  168 |   });
  169 | 
  170 |   test('C2: Network offline recovery', async ({ page }) => {
  171 |     // Navigate to page
  172 |     await page.goto('/alumnos');
  173 |     await page.waitForLoadState('networkidle');
  174 | 
  175 |     // Go offline
  176 |     await page.context().setOffline(true);
  177 | 
  178 |     // Trigger data fetch (will fail)
  179 |     const response = await page.evaluate(async () => {
  180 |       try {
  181 |         return await fetch('/api/v1/alumnos');
  182 |       } catch (e) {
  183 |         return { ok: false, error: e.message };
  184 |       }
  185 |     });
  186 | 
  187 |     expect(response.ok).toBe(false);
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
```