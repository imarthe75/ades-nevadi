# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: 19-cascadas-grupos.spec.ts >> C. Validación Cascada — Estado consistente >> GRP-CASCADE-07 | TypeScript compila sin errores @smoke
- Location: e2e/tests/19-cascadas-grupos.spec.ts:389:7

# Error details

```
Error: expect(received).toBeTruthy()

Received: false
```

# Page snapshot

```yaml
- generic [ref=e5]:
  - img "Instituto Nevadi" [ref=e7]
  - heading "ADES" [level=1] [ref=e8]
  - paragraph [ref=e9]: Instituto Nevadi
  - paragraph [ref=e10]: Sistema de Administración Escolar
  - button " Iniciar sesión con cuenta institucional" [ref=e12] [cursor=pointer]:
    - generic [ref=e13]: 
    - generic [ref=e14]: Iniciar sesión con cuenta institucional
  - paragraph [ref=e15]: Acceso exclusivo para personal y comunidad del Instituto Nevadi
```

# Test source

```ts
  295 | 
  296 |     // Selecciona grado Primaria
  297 |     await gradoSelect.click();
  298 |     await page.waitForTimeout(600);
  299 |     const primarioGrado = page.locator('.p-select-option, [role="option"]').filter({ hasText: /Primaria.*Primer/ }).first();
  300 |     if (await primarioGrado.isVisible()) {
  301 |       await primarioGrado.click();
  302 |       await page.waitForTimeout(600);
  303 |     }
  304 | 
  305 |     // Intenta guardar e intercepta respuesta
  306 |     let postStatus: number | null = null;
  307 |     page.on('response', async (response) => {
  308 |       if (response.url().includes('/api/v1/admin/grupos') && response.request().method() === 'POST') {
  309 |         postStatus = response.status();
  310 |       }
  311 |     });
  312 | 
  313 |     await guardarBtn.click();
  314 |     await page.waitForTimeout(2_000);
  315 | 
  316 |     if (postStatus) {
  317 |       expect([201, 400]).toContain(postStatus);
  318 |       if (postStatus === 201) {
  319 |         console.log('[INFO] GRP-CASCADE-05: POST exitoso (201), cascada válida');
  320 |       } else {
  321 |         console.log('[WARNING] GRP-CASCADE-05: POST rechazado (400), validación backend activa');
  322 |       }
  323 |     } else {
  324 |       console.log('[INFO] GRP-CASCADE-05: Sin POST interceptado, pero flujo completo');
  325 |     }
  326 |   });
  327 | 
  328 |   test('GRP-CASCADE-06 | Sin errores en consola durante cascada @smoke', async ({ page }) => {
  329 |     const apiResponses = attachApiMonitor(page);
  330 |     const getErrors = attachConsoleMonitor(page);
  331 | 
  332 |     await new LoginPage(page).login(USERS.ADMIN_GLOBAL);
  333 |     await page.goto('/dashboard', { waitUntil: 'domcontentloaded' });
  334 |     await page.waitForTimeout(1_500);
  335 | 
  336 |     // Navega a Administración > Grupos
  337 |     await page.click('text=Administración');
  338 |     await page.waitForTimeout(1_000);
  339 | 
  340 |     const gruposTab = page.locator('[role="tab"]:has-text("Grupos")').first();
  341 |     if (await gruposTab.isVisible()) {
  342 |       await gruposTab.click();
  343 |     }
  344 |     await page.waitForTimeout(1_200);
  345 | 
  346 |     // Abre nuevo grupo
  347 |     const nuevoBtn = page.locator('[data-testid="btn-nuevo-grupo"]');
  348 |     await nuevoBtn.click();
  349 |     await page.waitForTimeout(1_200);
  350 | 
  351 |     // Monitorea errores en consola
  352 |     const errors: string[] = [];
  353 |     page.on('console', (msg) => {
  354 |       if (msg.type() === 'error') {
  355 |         errors.push(msg.text());
  356 |       }
  357 |     });
  358 | 
  359 |     // Realiza cascada
  360 |     const cicloSelect = page.locator('[data-testid="select-ciclo"]');
  361 |     const gradoSelect = page.locator('[data-testid="select-grado"]');
  362 | 
  363 |     await cicloSelect.click();
  364 |     await page.waitForTimeout(600);
  365 |     const primariaCiclo = page.locator('.p-select-option, [role="option"]').filter({ hasText: /Primaria/ }).first();
  366 |     await primariaCiclo.click();
  367 |     await page.waitForTimeout(800);
  368 | 
  369 |     await gradoSelect.click();
  370 |     await page.waitForTimeout(600);
  371 |     const primarioGrado = page.locator('.p-select-option, [role="option"]').filter({ hasText: /Primaria/ }).first();
  372 |     if (await primarioGrado.isVisible()) {
  373 |       await primarioGrado.click();
  374 |       await page.waitForTimeout(600);
  375 |     }
  376 | 
  377 |     if (errors.length === 0) {
  378 |       console.log('[INFO] GRP-CASCADE-06: Cascada sin errores de consola ✓');
  379 |     } else {
  380 |       console.warn('[FINDING][P2] GRP-CASCADE-06: Errores en consola: ' + errors.join(', '));
  381 |     }
  382 | 
  383 |     expect(errors.length).toBe(0);
  384 |   });
  385 | });
  386 | 
  387 | test.describe('C. Validación Cascada — Estado consistente', () => {
  388 | 
  389 |   test('GRP-CASCADE-07 | TypeScript compila sin errores @smoke', async ({ page }) => {
  390 |     // Verificación estática: el código compila
  391 |     await page.goto('/dashboard', { waitUntil: 'domcontentloaded' });
  392 |     const isAngularRunning = await page.evaluate(() => {
  393 |       return (window as any).ng !== undefined;
  394 |     });
> 395 |     expect(isAngularRunning).toBeTruthy();
      |                              ^ Error: expect(received).toBeTruthy()
  396 |     console.log('[INFO] GRP-CASCADE-07: Angular inicializado correctamente');
  397 |   });
  398 | });
  399 | 
```