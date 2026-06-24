# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: 09-concurrency.spec.ts >> C. Mid-flow navigation — formulario + router >> CON-09 | F5 durante carga inicial → app recupera sesión sin pantallazo blanco
- Location: e2e/tests/09-concurrency.spec.ts:255:7

# Error details

```
Error: expect(received).toBeTruthy()

Received: false
```

# Test source

```ts
  170 |   test('CON-06 | logout en tab A → tab B intenta navegar → redirecta a login', async ({ browser }) => {
  171 |     const context = await browser.newContext();
  172 |     const pageA = await context.newPage();
  173 |     const pageB = await context.newPage();
  174 | 
  175 |     await new LoginPage(pageA).login(USERS.COORDINADOR);
  176 |     await new LoginPage(pageB).login(USERS.COORDINADOR);
  177 | 
  178 |     // Simular logout en tab A (limpia su propio sessionStorage)
  179 |     await pageA.evaluate(() => {
  180 |       sessionStorage.removeItem('ades_token');
  181 |       sessionStorage.removeItem('ades_usuario');
  182 |     });
  183 |     await pageA.goto('/login');
  184 | 
  185 |     // Tab B aún tiene su token — puede seguir navegando
  186 |     await pageB.goto('/alumnos', { waitUntil: 'domcontentloaded' });
  187 |     await pageB.waitForTimeout(1_000);
  188 | 
  189 |     // Tab A está en login
  190 |     expect(pageA.url()).toMatch(/\/login/);
  191 | 
  192 |     // Tab B sigue autenticada (sessionStorage es per-tab, no compartido)
  193 |     const tabBToken = await pageB.evaluate(() => sessionStorage.getItem('ades_token'));
  194 |     expect(tabBToken).toBeTruthy();
  195 | 
  196 |     await context.close();
  197 |   });
  198 | });
  199 | 
  200 | // ── C. Mid-flow navigation ─────────────────────────────────────────────────────
  201 | 
  202 | test.describe('C. Mid-flow navigation — formulario + router', () => {
  203 |   test('CON-07 | abrir dialog → llenar campos parcialmente → navegar a otro módulo → dialog desaparece', async ({ page }) => {
  204 |     const lp = new LoginPage(page);
  205 |     await lp.login(USERS.COORDINADOR);
  206 |     const ap = new AlumnosPage(page);
  207 |     await ap.navigate();
  208 | 
  209 |     await ap.openNewForm();
  210 |     const dialog = page.locator('.apex-dialog, [role="dialog"]');
  211 |     await expect(dialog).toBeVisible({ timeout: 5_000 });
  212 | 
  213 |     // Llenar parcialmente
  214 |     await ap.nombreInput.fill('Juan Mid-Flow');
  215 |     await ap.apPaternoInput.fill('García');
  216 | 
  217 |     // Navegar al módulo de asistencias via Angular router
  218 |     await page.goto('/asistencias', { waitUntil: 'domcontentloaded' });
  219 |     await page.waitForTimeout(1_000);
  220 | 
  221 |     // El dialog debe haber desaparecido (Angular destruyó el componente)
  222 |     await expect(dialog).not.toBeVisible();
  223 |     // App no crashed
  224 |     await expect(page.locator('app-root')).toBeVisible();
  225 |   });
  226 | 
  227 |   test('CON-08 | dialog abierto → browser history.back() → sin estado fantasma', async ({ page }) => {
  228 |     const lp = new LoginPage(page);
  229 |     await lp.login(USERS.COORDINADOR);
  230 |     const ap = new AlumnosPage(page);
  231 |     await ap.navigate();
  232 |     await ap.openNewForm();
  233 | 
  234 |     await expect(page.locator('.apex-dialog, [role="dialog"]')).toBeVisible({ timeout: 5_000 });
  235 | 
  236 |     // Navegar a otro módulo
  237 |     await page.goto('/grupos', { waitUntil: 'domcontentloaded' });
  238 |     await page.waitForTimeout(500);
  239 | 
  240 |     // Volver con browser back
  241 |     await page.goBack({ waitUntil: 'domcontentloaded' });
  242 |     await page.waitForTimeout(1_000);
  243 | 
  244 |     // App sigue funcional
  245 |     await expect(page.locator('app-root')).toBeVisible();
  246 | 
  247 |     const dialogVisible = await page.locator('.apex-dialog, [role="dialog"]').isVisible();
  248 |     if (dialogVisible) {
  249 |       // Si el dialog reaparece debe ser cerrable (no bloqueado)
  250 |       await page.keyboard.press('Escape');
  251 |       await page.waitForTimeout(400);
  252 |     }
  253 |   });
  254 | 
  255 |   test('CON-09 | F5 durante carga inicial → app recupera sesión sin pantallazo blanco', async ({ page }) => {
  256 |     const lp = new LoginPage(page);
  257 |     await lp.login(USERS.COORDINADOR);
  258 | 
  259 |     // Navegar a alumnos e inmediatamente recargar (F5)
  260 |     await page.goto('/alumnos', { waitUntil: 'commit' });
  261 |     // El reload limpia sessionStorage (efímero en Angular) — Angular re-inicializa
  262 |     await page.reload({ waitUntil: 'domcontentloaded' });
  263 |     await page.waitForTimeout(2_000);
  264 | 
  265 |     // Después del reload sin token, debe redirigir a login (comportamiento correcto)
  266 |     // O si el token fue conservado via otro mecanismo, debe mostrar la app
  267 |     const url = page.url();
  268 |     const isOnLogin = url.includes('/login');
  269 |     const isOnApp   = /\/(alumnos|dashboard|grupos)/.test(url);
> 270 |     expect(isOnLogin || isOnApp).toBeTruthy();
      |                                  ^ Error: expect(received).toBeTruthy()
  271 | 
  272 |     // Sin pantalla blanca ni crash
  273 |     await expect(page.locator('app-root')).toBeVisible({ timeout: 10_000 });
  274 |   });
  275 | 
  276 |   test('CON-10 | click frenético en "Nuevo" → un solo dialog abierto, no duplicados', async ({ page }) => {
  277 |     const lp = new LoginPage(page);
  278 |     await lp.login(USERS.COORDINADOR);
  279 |     const ap = new AlumnosPage(page);
  280 |     await ap.navigate();
  281 | 
  282 |     // Click 5 veces rápido en "Nuevo alumno"
  283 |     for (let i = 0; i < 5; i++) {
  284 |       await ap.newBtn.click({ force: true });
  285 |       await page.waitForTimeout(100);
  286 |     }
  287 |     await page.waitForTimeout(600);
  288 | 
  289 |     // Solo debe haber UN dialog abierto (no apilados)
  290 |     const dialogs = await page.locator('.apex-dialog, [role="dialog"]').count();
  291 |     // PrimeNG con showTransitionOptions puede tener 1 visible; más de 1 = bug
  292 |     expect(dialogs).toBeLessThanOrEqual(1);
  293 |   });
  294 | });
  295 | 
  296 | // ── D. Monitoreo de errores en flujo completo ─────────────────────────────────
  297 | 
  298 | test.describe('D. Monitoreo de errores — flujo completo', () => {
  299 |   test('CON-11 | flujo completo de navegación sin errores de consola ni 500s', async ({ page }) => {
  300 |     const consoleErrors = attachConsoleMonitor(page);
  301 |     const apiResponses = attachApiMonitor(page);
  302 | 
  303 |     const lp = new LoginPage(page);
  304 |     await lp.login(USERS.COORDINADOR);
  305 | 
  306 |     // Módulos principales — se excluye /certificados porque el backend Python
  307 |     // (FastAPI) puede estar down en el entorno dev, generando 502 esperados.
  308 |     const modulos = [
  309 |       '/dashboard',
  310 |       '/alumnos',
  311 |       '/grupos',
  312 |       '/asistencias',
  313 |       '/calificaciones',
  314 |     ];
  315 |     for (const ruta of modulos) {
  316 |       await page.goto(ruta, { waitUntil: 'domcontentloaded' });
  317 |       await page.waitForTimeout(800);
  318 |       await expect(page.locator('app-root')).toBeVisible({ timeout: 5_000 });
  319 |     }
  320 | 
  321 |     assertNoCriticalErrors(consoleErrors());
  322 |     assertNoServerErrors(apiResponses());
  323 |   });
  324 | 
  325 |   test('CON-12 | flujo CRUD alumno con monitor de 500s — ninguna respuesta es 500', async ({ page }) => {
  326 |     const apiResponses = attachApiMonitor(page);
  327 |     const consoleErrors = attachConsoleMonitor(page);
  328 | 
  329 |     const lp = new LoginPage(page);
  330 |     await lp.login(USERS.COORDINADOR);
  331 | 
  332 |     // 1. Listar alumnos
  333 |     await page.goto('/alumnos');
  334 |     await page.waitForTimeout(1_500);
  335 | 
  336 |     // 2. Abrir formulario
  337 |     const ap = new AlumnosPage(page);
  338 |     await ap.openNewForm();
  339 |     await expect(page.locator('.apex-dialog, [role="dialog"]')).toBeVisible({ timeout: 5_000 });
  340 | 
  341 |     // 3. Intentar submit con CURP inválida (debe retornar 422, no 500)
  342 |     const data = alumnoValido();
  343 |     await ap.fillAlumnoForm({ ...data, curp: 'CORTO' }); // CURP inválida
  344 |     await ap.save();
  345 |     await page.waitForTimeout(1_000);
  346 | 
  347 |     // 4. Cerrar el dialog explícitamente antes de interactuar con la tabla
  348 |     // (el dialog sigue visible tras el error de validación)
  349 |     const dialog = page.locator('.apex-dialog, [role="dialog"]');
  350 |     const dialogOpen = await dialog.isVisible().catch(() => false);
  351 |     if (dialogOpen) {
  352 |       await page.keyboard.press('Escape');
  353 |       await page.waitForTimeout(500);
  354 |       // Si Escape no lo cierra, buscar botón de cerrar
  355 |       const closeBtn = page.locator('[aria-label="Close"], [data-pc-section="closebutton"], button:has-text("×")').first();
  356 |       const closeBtnVisible = await closeBtn.isVisible().catch(() => false);
  357 |       if (closeBtnVisible) {
  358 |         await closeBtn.click();
  359 |         await page.waitForTimeout(300);
  360 |       }
  361 |     }
  362 | 
  363 |     const rowCount = await ap.rows.count();
  364 |     if (rowCount > 0) {
  365 |       // Verificar que no hay dialog bloqueando antes de hacer click
  366 |       const stillOpen = await dialog.isVisible().catch(() => false);
  367 |       if (!stillOpen) {
  368 |         await ap.clickFirstRow();
  369 |         await page.waitForTimeout(1_500);
  370 |       }
```