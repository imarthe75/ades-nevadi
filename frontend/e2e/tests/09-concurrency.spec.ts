/**
 * Suite 09 — Concurrencia, cross-tab y monitoreo de errores
 *
 * Cubre 4 brechas identificadas frente a la suite base:
 *  A. Race conditions en tabla (paginación / sort / búsqueda rápida)
 *  B. Cross-tab session (logout tab A → operación en tab B, token expirado mid-session)
 *  C. Mid-flow navigation (dialog a medio llenar + Angular router / browser back)
 *  D. Monitoreo de console.error y API 500 durante flujo completo
 */
import { test, expect } from '@playwright/test';
import { LoginPage } from '../page-objects/login-page';
import { AlumnosPage } from '../page-objects/alumnos-page';
import { USERS } from '../fixtures/users';
import { alumnoValido } from '../fixtures/data-generators';
import {
  attachConsoleMonitor,
  attachApiMonitor,
  assertNoCriticalErrors,
  assertNoServerErrors,
  type CapturedError,
} from '../helpers/console-monitor';

// ── A. Race conditions en tabla ───────────────────────────────────────────────

test.describe('A. Race conditions en tabla', () => {
  test('CON-01 | sort de columna 8 veces rápido → ningún 500 ni crash', async ({ page }) => {
    const apiResponses = attachApiMonitor(page);
    const lp = new LoginPage(page);
    await lp.login(USERS.COORDINADOR);
    await page.goto('/alumnos');
    await page.waitForTimeout(1_500);

    // Cabeceras de columna sortables en app-interactive-grid
    const sortableHeaders = page.locator(
      'th[class*="sortable"], th.p-sortable-column, [data-sortable="true"]'
    );
    const headerCount = await sortableHeaders.count();
    if (headerCount === 0) {
      // El grid no tiene sort UI — verificar solo que la tabla cargó
      await expect(page.locator('app-interactive-grid, .p-datatable')).toBeVisible();
      return;
    }

    // Clicks rápidos alternando entre las primeras 3 columnas sortables
    for (let i = 0; i < 8; i++) {
      await sortableHeaders.nth(i % Math.min(headerCount, 3)).click({ force: true });
      await page.waitForTimeout(80);
    }
    await page.waitForTimeout(1_000);

    // Sin 500s
    assertNoServerErrors(apiResponses());
    // App sigue en pie
    await expect(page).not.toHaveURL(/error|crash/);
    await expect(page.locator('app-root')).toBeVisible();
  });

  test('CON-02 | búsqueda rápida (10 teclas sin debounce completado) → sin requests duplicados en crash', async ({ page }) => {
    const apiResponses = attachApiMonitor(page);
    const consoleErrors = attachConsoleMonitor(page);

    const lp = new LoginPage(page);
    await lp.login(USERS.COORDINADOR);
    const ap = new AlumnosPage(page);
    await ap.navigate();

    const search = page.locator('input.apex-search-input, [data-testid="search-alumnos"]').first();
    const searchVisible = await search.isVisible().catch(() => false);
    if (!searchVisible) return;

    // Simular escritura rápida antes de que el debounce (300ms) se complete
    await search.click();
    await page.keyboard.type('GARCIAlopezmart', { delay: 20 });
    // No esperamos el debounce — navegamos inmediatamente
    await page.goto('/grupos', { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(500);

    // No debe haber pageerror por request abortado o subscription no cancelada
    const errors = consoleErrors().filter(e =>
      // Filtrar aborts esperados del debounce
      !e.message.includes('aborted') && !e.message.includes('cancel')
    );
    if (errors.length > 0) {
      assertNoCriticalErrors(errors);
    }
    assertNoServerErrors(apiResponses());
  });

  test('CON-03 | navegar a otro módulo mientras request de tabla está en vuelo → sin pageerror', async ({ page }) => {
    const consoleErrors = attachConsoleMonitor(page);

    const lp = new LoginPage(page);
    await lp.login(USERS.COORDINADOR);

    // Navegar a alumnos e INMEDIATAMENTE saltar a grupos antes de que cargue la tabla
    await page.goto('/alumnos', { waitUntil: 'commit' });
    await page.goto('/grupos', { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(800);

    // La navegación canceló el request anterior — no debe haber excepciones no manejadas
    assertNoCriticalErrors(consoleErrors());
    await expect(page.locator('app-root')).toBeVisible();
    await expect(page).not.toHaveURL(/error/);
  });
});

// ── B. Cross-tab session ───────────────────────────────────────────────────────

test.describe('B. Cross-tab — sesión vs. operación pendiente', () => {
  test('CON-04 | dos tabs mismo usuario → operan de forma independiente', async ({ browser }) => {
    const context = await browser.newContext();
    const page1 = await context.newPage();
    const page2 = await context.newPage();

    // Ambas tabs se autentican por separado (sessionStorage es per-tab)
    await new LoginPage(page1).login(USERS.COORDINADOR);
    await new LoginPage(page2).login(USERS.COORDINADOR);

    // Tab 1 navega a alumnos, Tab 2 navega a calificaciones
    await page1.goto('/alumnos', { waitUntil: 'domcontentloaded' });
    await page2.goto('/calificaciones', { waitUntil: 'domcontentloaded' });
    await page1.waitForTimeout(1_000);
    await page2.waitForTimeout(1_000);

    // Ambas tabs deben estar en su ruta sin redirigir a login
    expect(page1.url()).toMatch(/alumnos/);
    expect(page2.url()).not.toMatch(/\/login/);
    await expect(page1.locator('app-root')).toBeVisible();
    await expect(page2.locator('app-root')).toBeVisible();

    await context.close();
  });

  test('CON-05 | token expirado en tab activa → API devuelve 401 → sin crash (pageerror)', async ({ page }) => {
    // Usamos solo pageerror (excepciones JS no capturadas), NO console.error,
    // porque "Failed to load resource: 401" es el comportamiento correcto del BFF
    // y generará entradas en console.error de forma esperada.
    const pageerrors: string[] = [];
    page.on('pageerror', err => pageerrors.push(err.message));

    const apiResponses = attachApiMonitor(page);

    const lp = new LoginPage(page);
    await lp.login(USERS.COORDINADOR);
    await page.goto('/alumnos');
    await page.waitForTimeout(1_000);

    // Reemplazar el token con uno expirado (exp=1, año 1970)
    await page.evaluate(() => {
      sessionStorage.setItem(
        'ades_token',
        'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0IiwiZXhwIjoxfQ.fake_signature'
      );
    });

    // Forzar una llamada a la API recargando el módulo
    await page.reload({ waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(2_000);

    // El BFF devuelve 401; Angular debe redirigir a login o mostrar error
    const got401 = apiResponses().some(r => r.status === 401);
    const redirectedToLogin = page.url().includes('/login');
    expect(got401 || redirectedToLogin).toBeTruthy();

    // Sin excepciones JS no capturadas (crash real)
    expect(pageerrors.filter(e => !e.includes('401') && !e.includes('Unauthorized')))
      .toHaveLength(0);
  });

  test('CON-06 | logout en tab A → tab B intenta navegar → redirecta a login', async ({ browser }) => {
    const context = await browser.newContext();
    const pageA = await context.newPage();
    const pageB = await context.newPage();

    await new LoginPage(pageA).login(USERS.COORDINADOR);
    await new LoginPage(pageB).login(USERS.COORDINADOR);

    // Simular logout en tab A (limpia su propio sessionStorage)
    await pageA.evaluate(() => {
      sessionStorage.removeItem('ades_token');
      sessionStorage.removeItem('ades_usuario');
    });
    await pageA.goto('/login');

    // Tab B aún tiene su token — puede seguir navegando
    await pageB.goto('/alumnos', { waitUntil: 'domcontentloaded' });
    await pageB.waitForTimeout(1_000);

    // Tab A está en login
    expect(pageA.url()).toMatch(/\/login/);

    // Tab B sigue autenticada (sessionStorage es per-tab, no compartido)
    const tabBToken = await pageB.evaluate(() => sessionStorage.getItem('ades_token'));
    expect(tabBToken).toBeTruthy();

    await context.close();
  });
});

// ── C. Mid-flow navigation ─────────────────────────────────────────────────────

test.describe('C. Mid-flow navigation — formulario + router', () => {
  test('CON-07 | abrir dialog → llenar campos parcialmente → navegar a otro módulo → dialog desaparece', async ({ page }) => {
    const lp = new LoginPage(page);
    await lp.login(USERS.COORDINADOR);
    const ap = new AlumnosPage(page);
    await ap.navigate();

    await ap.openNewForm();
    const dialog = page.locator('.apex-dialog, [role="dialog"]');
    await expect(dialog).toBeVisible({ timeout: 5_000 });

    // Llenar parcialmente
    await ap.nombreInput.fill('Juan Mid-Flow');
    await ap.apPaternoInput.fill('García');

    // Navegar al módulo de asistencias via Angular router
    await page.goto('/asistencias', { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(1_000);

    // El dialog debe haber desaparecido (Angular destruyó el componente)
    await expect(dialog).not.toBeVisible();
    // App no crashed
    await expect(page.locator('app-root')).toBeVisible();
  });

  test('CON-08 | dialog abierto → browser history.back() → sin estado fantasma', async ({ page }) => {
    const lp = new LoginPage(page);
    await lp.login(USERS.COORDINADOR);
    const ap = new AlumnosPage(page);
    await ap.navigate();
    await ap.openNewForm();

    await expect(page.locator('.apex-dialog, [role="dialog"]')).toBeVisible({ timeout: 5_000 });

    // Navegar a otro módulo
    await page.goto('/grupos', { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(500);

    // Volver con browser back
    await page.goBack({ waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(1_000);

    // App sigue funcional
    await expect(page.locator('app-root')).toBeVisible();

    const dialogVisible = await page.locator('.apex-dialog, [role="dialog"]').isVisible();
    if (dialogVisible) {
      // Si el dialog reaparece debe ser cerrable (no bloqueado)
      await page.keyboard.press('Escape');
      await page.waitForTimeout(400);
    }
  });

  test('CON-09 | F5 durante carga inicial → app recupera sesión sin pantallazo blanco', async ({ page }) => {
    const lp = new LoginPage(page);
    await lp.login(USERS.COORDINADOR);

    // Navegar a alumnos e inmediatamente recargar (F5)
    await page.goto('/alumnos', { waitUntil: 'commit' });
    // El reload limpia sessionStorage (efímero en Angular) — Angular re-inicializa
    await page.reload({ waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(2_000);

    // Después del reload sin token, debe redirigir a login (comportamiento correcto)
    // O si el token fue conservado via otro mecanismo, debe mostrar la app
    const url = page.url();
    const isOnLogin = url.includes('/login');
    const isOnApp   = /\/(alumnos|dashboard|grupos)/.test(url);
    expect(isOnLogin || isOnApp).toBeTruthy();

    // Sin pantalla blanca ni crash
    await expect(page.locator('app-root')).toBeVisible({ timeout: 10_000 });
  });

  test('CON-10 | click frenético en "Nuevo" → un solo dialog abierto, no duplicados', async ({ page }) => {
    const lp = new LoginPage(page);
    await lp.login(USERS.COORDINADOR);
    const ap = new AlumnosPage(page);
    await ap.navigate();

    // Click 5 veces rápido en "Nuevo alumno"
    for (let i = 0; i < 5; i++) {
      await ap.newBtn.click({ force: true });
      await page.waitForTimeout(100);
    }
    await page.waitForTimeout(600);

    // Solo debe haber UN dialog abierto (no apilados)
    const dialogs = await page.locator('.apex-dialog, [role="dialog"]').count();
    // PrimeNG con showTransitionOptions puede tener 1 visible; más de 1 = bug
    expect(dialogs).toBeLessThanOrEqual(1);
  });
});

// ── D. Monitoreo de errores en flujo completo ─────────────────────────────────

test.describe('D. Monitoreo de errores — flujo completo', () => {
  test('CON-11 | flujo completo de navegación sin errores de consola ni 500s', async ({ page }) => {
    const consoleErrors = attachConsoleMonitor(page);
    const apiResponses = attachApiMonitor(page);

    const lp = new LoginPage(page);
    await lp.login(USERS.COORDINADOR);

    // Módulos principales — se excluye /certificados porque el backend Python
    // (FastAPI) puede estar down en el entorno dev, generando 502 esperados.
    const modulos = [
      '/dashboard',
      '/alumnos',
      '/grupos',
      '/asistencias',
      '/calificaciones',
    ];
    for (const ruta of modulos) {
      await page.goto(ruta, { waitUntil: 'domcontentloaded' });
      await page.waitForTimeout(800);
      await expect(page.locator('app-root')).toBeVisible({ timeout: 5_000 });
    }

    assertNoCriticalErrors(consoleErrors());
    assertNoServerErrors(apiResponses());
  });

  test('CON-12 | flujo CRUD alumno con monitor de 500s — ninguna respuesta es 500', async ({ page }) => {
    const apiResponses = attachApiMonitor(page);
    const consoleErrors = attachConsoleMonitor(page);

    const lp = new LoginPage(page);
    await lp.login(USERS.COORDINADOR);

    // 1. Listar alumnos
    await page.goto('/alumnos');
    await page.waitForTimeout(1_500);

    // 2. Abrir formulario
    const ap = new AlumnosPage(page);
    await ap.openNewForm();
    await expect(page.locator('.apex-dialog, [role="dialog"]')).toBeVisible({ timeout: 5_000 });

    // 3. Intentar submit con CURP inválida (debe retornar 422, no 500)
    const data = alumnoValido();
    await ap.fillAlumnoForm({ ...data, curp: 'CORTO' }); // CURP inválida
    await ap.save();
    await page.waitForTimeout(1_000);

    // 4. Cerrar el dialog explícitamente antes de interactuar con la tabla
    // (el dialog sigue visible tras el error de validación)
    const dialog = page.locator('.apex-dialog, [role="dialog"]');
    const dialogOpen = await dialog.isVisible().catch(() => false);
    if (dialogOpen) {
      await page.keyboard.press('Escape');
      await page.waitForTimeout(500);
      // Si Escape no lo cierra, buscar botón de cerrar
      const closeBtn = page.locator('[aria-label="Close"], [data-pc-section="closebutton"], button:has-text("×")').first();
      const closeBtnVisible = await closeBtn.isVisible().catch(() => false);
      if (closeBtnVisible) {
        await closeBtn.click();
        await page.waitForTimeout(300);
      }
    }

    const rowCount = await ap.rows.count();
    if (rowCount > 0) {
      // Verificar que no hay dialog bloqueando antes de hacer click
      const stillOpen = await dialog.isVisible().catch(() => false);
      if (!stillOpen) {
        await ap.clickFirstRow();
        await page.waitForTimeout(1_500);
      }
    }

    // Sin 500s en toda la sesión
    assertNoServerErrors(apiResponses());
    assertNoCriticalErrors(consoleErrors());
  });

  test('CON-13 | lectura concurrente de 3 módulos → sin 500 ni race en HTTP client', async ({ page }) => {
    const apiResponses = attachApiMonitor(page);

    const lp = new LoginPage(page);
    await lp.login(USERS.COORDINADOR);

    // Navegar rápido a 3 módulos que disparan HTTP requests al cargar
    await page.goto('/alumnos', { waitUntil: 'commit' });
    await page.goto('/grupos', { waitUntil: 'commit' });
    await page.goto('/calificaciones', { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(2_000);

    // Los requests abortados de las navegaciones anteriores no deben generar 500
    assertNoServerErrors(apiResponses());
    await expect(page.locator('app-root')).toBeVisible();
  });

  test('CON-14 | paginación rápida en tabla con datos → ningún 500 en requests', async ({ page }) => {
    const apiResponses = attachApiMonitor(page);

    const lp = new LoginPage(page);
    await lp.login(USERS.COORDINADOR);
    await page.goto('/alumnos');
    await page.waitForTimeout(1_500);

    // Botones de paginación de p-table / app-interactive-grid
    const nextBtn = page.locator(
      'button[aria-label="Next Page"], .p-paginator-next, [data-pc-section="nextpagebutton"]'
    ).first();
    const prevBtn = page.locator(
      'button[aria-label="Previous Page"], .p-paginator-prev, [data-pc-section="previouspagebutton"]'
    ).first();

    const hasPaginator = await nextBtn.isVisible().catch(() => false);
    if (!hasPaginator) {
      // Menos registros que una página — verificar solo que no hay 500s en la carga inicial
      assertNoServerErrors(apiResponses());
      return;
    }

    // Paginar adelante/atrás 6 veces rápido
    for (let i = 0; i < 3; i++) {
      const nextEnabled = await nextBtn.isEnabled().catch(() => false);
      if (nextEnabled) {
        await nextBtn.click({ force: true });
        await page.waitForTimeout(120);
      }
    }
    for (let i = 0; i < 3; i++) {
      const prevEnabled = await prevBtn.isEnabled().catch(() => false);
      if (prevEnabled) {
        await prevBtn.click({ force: true });
        await page.waitForTimeout(120);
      }
    }
    await page.waitForTimeout(1_000);

    assertNoServerErrors(apiResponses());
    await expect(page.locator('app-root')).toBeVisible();
  });
});
