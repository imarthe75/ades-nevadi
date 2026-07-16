# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: 06-edge-cases.spec.ts >> 06-edge-cases — Concurrent, RBAC, Network, Timeouts >> A1: Concurrent edit — second PATCH returns 409
- Location: e2e/tests/06-edge-cases.spec.ts:32:7

# Error details

```
Error: expect(received).toBe(expected) // Object.is equality

Expected: 409
Received: 404
```

# Test source

```ts
  1   | import { test, expect, Browser, BrowserContext, Page } from '@playwright/test';
  2   | 
  3   | /**
  4   |  * SEMANA 5 — Edge Cases E2E Suite (25 tests)
  5   |  * Coverage: Concurrent, RBAC boundary, network failures, timeouts, boundary values
  6   |  * Target: 82/100 score (+1 point)
  7   |  */
  8   | 
  9   | test.describe('06-edge-cases — Concurrent, RBAC, Network, Timeouts', () => {
  10  |   let page: Page;
  11  |   let context: BrowserContext;
  12  |   let token: string;
  13  | 
  14  |   test.beforeAll(async ({ browser }) => {
  15  |     context = await browser.newContext();
  16  |     page = await context.newPage();
  17  | 
  18  |     // Setup: Inject token
  19  |     await page.addInitScript(() => {
  20  |       sessionStorage.setItem('ades_token', 'eyJhbGciOiJIUzI1NiJ9...');
  21  |     });
  22  |   });
  23  | 
  24  |   test.afterAll(async () => {
  25  |     await context.close();
  26  |   });
  27  | 
  28  |   // ============================================================================
  29  |   // SUITE A: Concurrent Edits (Optimistic Locking)
  30  |   // ============================================================================
  31  | 
  32  |   test('A1: Concurrent edit — second PATCH returns 409', async ({ request, context }) => {
  33  |     const alumnoId = '550e8400-e29b-41d4-a716-446655440000';
  34  |     const token = 'test-token';
  35  | 
  36  |     // Tab 1: Fetch alumno (row_version=100)
  37  |     const resp1 = await request.get(`/api/v1/alumnos/${alumnoId}`, {
  38  |       headers: { 'Authorization': `Bearer ${token}` }
  39  |     });
  40  |     const data = await resp1.json();
  41  |     const rowVersion = data.row_version; // 100
  42  | 
  43  |     // Tab 2: PATCH with stale row_version
  44  |     const resp2 = await request.patch(`/api/v1/alumnos/${alumnoId}`, {
  45  |       data: { nombre: 'Updated', row_version: rowVersion },
  46  |       headers: { 'Authorization': `Bearer ${token}` }
  47  |     });
  48  | 
  49  |     // Assert: 409 Conflict
> 50  |     expect(resp2.status()).toBe(409);
      |                            ^ Error: expect(received).toBe(expected) // Object.is equality
  51  |     const error = await resp2.json();
  52  |     expect(error).toHaveProperty('error');
  53  |     expect(error.error).toContain('version');
  54  |   });
  55  | 
  56  |   test('A2: Concurrent uploads — 10 files in parallel', async ({ request }) => {
  57  |     const uploadPromises = [];
  58  |     for (let i = 0; i < 10; i++) {
  59  |       uploadPromises.push(
  60  |         request.post(`/api/v1/expediente/upload`, {
  61  |           headers: { 'Authorization': `Bearer test-token` },
  62  |           multipart: {
  63  |             file: {
  64  |               name: `test-file-${i}.pdf`,
  65  |               mimeType: 'application/pdf',
  66  |               buffer: Buffer.from(`PDF content ${i}`)
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
```