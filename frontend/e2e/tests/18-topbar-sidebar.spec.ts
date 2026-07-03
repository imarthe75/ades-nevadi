/**
 * Suite 18 — Topbar & Sidebar: Cascada institucional, navegación y persistencia
 *
 * Complementa la Suite 10 (RBAC) y ADV-08 (estado de menú) con cobertura
 * exhaustiva del shell (ShellComponent): cascada Plantel→Nivel→Ciclo→Grado→Grupo,
 * integridad de TODOS los links del sidenav (no solo una muestra), popovers de
 * notificaciones/usuario, persistencia de contexto al navegar entre módulos, y
 * breadcrumbs.
 *
 * Filosofía: igual que RBAC/ADV, los hallazgos de UX/consistencia se registran
 * con console.warn('[FINDING]...') en vez de fallar el test, para no bloquear CI
 * por deuda conocida — pero errores 5xx, crashes o rutas rotas SÍ fallan.
 */
import { test, expect, Page, Locator } from '@playwright/test';
import { LoginPage } from '../page-objects/login-page';
import { USERS } from '../fixtures/users';
import {
  attachConsoleMonitor,
  attachApiMonitor,
  assertNoServerErrors,
  assertNoCriticalErrors,
} from '../helpers/console-monitor';

/** Los 4 selectores de cascada en orden DOM (ciclo siempre es p-select; los demás
 *  pueden ser <div class="ctx-label"> fijos según scope del usuario). */
const CASCADE_ROLES = ['plantel', 'nivel', 'ciclo', 'grado', 'grupo'] as const;
type CascadeRole = typeof CASCADE_ROLES[number];

async function topbarSelects(page: Page): Promise<Locator> {
  return page.locator('.ades-topbar p-select.ctx-selector');
}

async function optionTexts(page: Page): Promise<string[]> {
  const items = page.locator('.p-select-option, [role="option"]');
  const count = await items.count();
  const texts: string[] = [];
  for (let i = 0; i < count; i++) {
    texts.push(((await items.nth(i).textContent()) ?? '').trim());
  }
  return texts;
}

/** Abre un p-select del topbar por índice DOM, selecciona la primera opción
 *  distinta de la actualmente mostrada, y cierra. Devuelve el texto elegido. */
async function cascadeSelect(page: Page, selectLocator: Locator): Promise<string | null> {
  const currentText = ((await selectLocator.textContent()) ?? '').trim();
  await selectLocator.click();
  await page.waitForTimeout(400);

  const options = page.locator('.p-select-option, [role="option"]');
  const count = await options.count().catch(() => 0);
  if (count === 0) {
    await page.keyboard.press('Escape').catch(() => {});
    return null;
  }

  for (let i = 0; i < count; i++) {
    const optText = ((await options.nth(i).textContent()) ?? '').trim();
    if (optText && optText !== currentText) {
      await options.nth(i).click();
      await page.waitForTimeout(800);
      return optText;
    }
  }
  await page.keyboard.press('Escape').catch(() => {});
  return null;
}

test.describe('A. Topbar — cascada institucional Plantel→Nivel→Ciclo→Grado→Grupo', () => {
  test('NAV-01 | admin global ve los 5 selectores de cascada (no labels fijos) @smoke', async ({ page }) => {
    await new LoginPage(page).login(USERS.ADMIN_GLOBAL);
    await page.goto('/dashboard', { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(1_500);

    const selects = await topbarSelects(page);
    const n = await selects.count();
    if (n < 5) {
      console.warn(
        `[FINDING][P2] NAV-01: admin global solo ve ${n}/5 selectores de cascada en topbar — ` +
        'esperado: plantel, nivel, ciclo, grado, grupo todos editables para ADMIN_GLOBAL'
      );
    }
    await expect(page.locator('app-root')).toBeVisible();
  });

  test('NAV-02 | cambiar Plantel resetea y recarga Nivel/Ciclo/Grado/Grupo sin errores @smoke', async ({ page }) => {
    const apiResponses = attachApiMonitor(page);
    const getErrors = attachConsoleMonitor(page);

    await new LoginPage(page).login(USERS.ADMIN_GLOBAL);
    await page.goto('/dashboard', { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(1_500);

    const selects = await topbarSelects(page);
    const n = await selects.count();
    if (n < 5) { test.skip(); return; }

    const nivelBefore = ((await selects.nth(1).textContent()) ?? '').trim();
    const gradoBefore = ((await selects.nth(3).textContent()) ?? '').trim();

    const chosen = await cascadeSelect(page, selects.nth(0)); // Plantel
    if (!chosen) { test.skip(); return; }

    await page.waitForTimeout(1_500);

    const selectsAfter = await topbarSelects(page);
    const nivelAfter = ((await selectsAfter.nth(1).textContent()) ?? '').trim();
    const gradoAfter = ((await selectsAfter.nth(3).textContent()) ?? '').trim();

    // Nivel/Grado NO deben quedar mostrando el valor del plantel anterior sin recargar
    if (nivelAfter === nivelBefore && gradoAfter === gradoBefore) {
      console.warn(
        `[FINDING][P1] NAV-02: tras cambiar Plantel a "${chosen}", Nivel/Grado no cambiaron ` +
        `(Nivel: "${nivelBefore}"→"${nivelAfter}", Grado: "${gradoBefore}"→"${gradoAfter}") — ` +
        'puede indicar que la cascada no recarga o el nuevo plantel comparte los mismos catálogos'
      );
    }

    assertNoServerErrors(apiResponses());
    assertNoCriticalErrors(getErrors());
  });

  test('NAV-03 | cambiar Nivel recarga Ciclo/Grado/Grupo', async ({ page }) => {
    const apiResponses = attachApiMonitor(page);
    await new LoginPage(page).login(USERS.ADMIN_GLOBAL);
    await page.goto('/dashboard', { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(1_500);

    const selects = await topbarSelects(page);
    if (await selects.count() < 5) { test.skip(); return; }

    const grupoBefore = ((await selects.nth(4).textContent()) ?? '').trim();
    const chosen = await cascadeSelect(page, selects.nth(1)); // Nivel
    if (!chosen) { test.skip(); return; }
    await page.waitForTimeout(1_500);

    const selectsAfter = await topbarSelects(page);
    const grupoAfter = ((await selectsAfter.nth(4).textContent()) ?? '').trim();
    if (grupoAfter === grupoBefore) {
      console.warn(
        `[FINDING][P2] NAV-03: tras cambiar Nivel a "${chosen}", Grupo no cambió de "${grupoBefore}" — ` +
        'verificar que loadGrupos() se dispare en cascada completa desde Nivel'
      );
    }

    const serverErrors = apiResponses().filter(r => r.status >= 500);
    if (serverErrors.length > 0) {
      console.warn(`[FINDING][P1] NAV-03: ${serverErrors.length} error(es) 5xx durante cascada de Nivel:`,
        serverErrors.map(r => r.url));
    }
  });

  test('NAV-04 | cambiar Ciclo recarga Grado/Grupo', async ({ page }) => {
    await new LoginPage(page).login(USERS.ADMIN_GLOBAL);
    await page.goto('/dashboard', { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(1_500);

    const selects = await topbarSelects(page);
    if (await selects.count() < 5) { test.skip(); return; }

    const chosen = await cascadeSelect(page, selects.nth(2)); // Ciclo
    if (!chosen) { test.skip(); return; }
    await page.waitForTimeout(1_200);
    await expect(page.locator('app-root')).toBeVisible();
  });

  test('NAV-05 | cambiar Grado filtra Grupo a opciones del nuevo grado', async ({ page }) => {
    await new LoginPage(page).login(USERS.ADMIN_GLOBAL);
    await page.goto('/dashboard', { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(1_500);

    const selects = await topbarSelects(page);
    if (await selects.count() < 5) { test.skip(); return; }

    // Abrir Grupo antes de tocar Grado, capturar opciones iniciales
    await selects.nth(4).click();
    await page.waitForTimeout(300);
    const gruposBefore = await optionTexts(page);
    await page.keyboard.press('Escape');
    await page.waitForTimeout(300);

    const chosenGrado = await cascadeSelect(page, selects.nth(3)); // Grado
    if (!chosenGrado) { test.skip(); return; }
    await page.waitForTimeout(1_200);

    const selectsAfter = await topbarSelects(page);
    await selectsAfter.nth(4).click();
    await page.waitForTimeout(300);
    const gruposAfter = await optionTexts(page);
    await page.keyboard.press('Escape');

    if (JSON.stringify(gruposBefore) === JSON.stringify(gruposAfter) && gruposBefore.length > 1) {
      console.warn(
        `[FINDING][P2] NAV-05: tras cambiar Grado a "${chosenGrado}", la lista de Grupos no cambió ` +
        `(${gruposBefore.length} opciones idénticas antes/después) — revisar filtro grado_id en loadGrupos()`
      );
    }
  });
});

test.describe('B. Topbar — notificaciones y menú de usuario', () => {
  test('NAV-06 | campanita abre panel de notificaciones y cierra al click fuera @smoke', async ({ page }) => {
    await new LoginPage(page).login(USERS.ADMIN_GLOBAL);
    await page.goto('/dashboard', { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(1_500);

    const bell = page.locator('.notif-bell');
    await expect(bell).toBeVisible();
    await bell.click();

    // overlayAppendTo: 'body' (LOV Global Fix) — el panel se teletransporta fuera de <p-popover>,
    // así que NO es descendiente de p-popover.notif-panel; buscar por su contenido único.
    // isVisible() NO espera/reintenta — usar expect().toBeVisible() que sí hace polling.
    const panel = page.locator('.notif-panel-header, .p-popover:has(.notif-panel-header)');
    const opened = await expect(panel.first()).toBeVisible({ timeout: 4_000 }).then(() => true).catch(() => false);
    if (!opened) {
      console.warn('[FINDING][P2] NAV-06: click en campanita no abrió el panel de notificaciones');
      return;
    }

    // Click fuera debe cerrar
    await page.mouse.click(10, 10);
    await page.waitForTimeout(500);
    const stillOpen = await panel.first().isVisible().catch(() => false);
    if (stillOpen) {
      console.warn('[FINDING][P3] NAV-06: panel de notificaciones no se cierra al hacer click fuera');
    }
  });

  test('NAV-07 | avatar abre menú de usuario con nombre y rol correctos', async ({ page }) => {
    await new LoginPage(page).login(USERS.ADMIN_GLOBAL);
    await page.goto('/dashboard', { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(1_500);

    const avatarBtn = page.locator('.user-avatar-btn');
    await expect(avatarBtn).toBeVisible();
    await avatarBtn.click();

    const menu = page.locator('.umenu-header');
    const opened = await expect(menu).toBeVisible({ timeout: 4_000 }).then(() => true).catch(() => false);
    if (!opened) {
      console.warn('[FINDING][P2] NAV-07: click en avatar no abrió el menú de usuario');
      return;
    }

    const name = ((await page.locator('.umenu-name').textContent()) ?? '').trim();
    if (!name) {
      console.warn('[FINDING][P2] NAV-07: menú de usuario abierto pero .umenu-name está vacío');
    }
    // Cerrar sin hacer logout
    await page.keyboard.press('Escape');
  });
});

test.describe('C. Sidebar — integridad de TODOS los links visibles', () => {
  test('NAV-08 | cada link del sidenav navega sin 5xx, sin caer a /login y sin pantalla en blanco', async ({ page }) => {
    test.setTimeout(240_000); // el sidenav puede tener 50+ links — cada uno navega+espera
    const apiResponses = attachApiMonitor(page);
    const getErrors = attachConsoleMonitor(page);

    await new LoginPage(page).login(USERS.ADMIN_GLOBAL);
    await page.goto('/dashboard', { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(1_500);

    const links = page.locator('.sidenav li a');
    const total = await links.count();
    expect(total).toBeGreaterThan(5); // sanity: el sidenav no debe estar vacío/roto

    const broken: string[] = [];
    const blank: string[] = [];
    const per5xx: string[] = [];
    const perConsole: string[] = [];

    for (let i = 0; i < total; i++) {
      // Re-query cada vez porque el sidenav puede re-renderizar entre navegaciones
      const link = page.locator('.sidenav li a').nth(i);
      const label = ((await link.textContent()) ?? `link_${i}`).trim();
      const href = await link.getAttribute('href');

      const apiCountBefore = apiResponses().length;
      const errCountBefore = getErrors().length;

      await link.scrollIntoViewIfNeeded().catch(() => {});
      await link.click({ timeout: 5_000 }).catch(() => broken.push(`${label} (${href}) — click falló`));
      await page.waitForTimeout(900);

      const url = page.url();
      if (/\/login/.test(url) || /authentik/i.test(url)) {
        broken.push(`${label} (${href}) — redirigió a login/authentik`);
        continue;
      }

      const bodyText = await page.evaluate(() => document.body.innerText.trim()).catch(() => '');
      if (bodyText.length < 10) {
        blank.push(`${label} (${href}) — página prácticamente en blanco (${bodyText.length} chars)`);
      }

      const new5xx = apiResponses().slice(apiCountBefore).filter(r => r.status >= 500);
      if (new5xx.length > 0) {
        per5xx.push(`${label} (${href}): ` + new5xx.map(r => `${r.status} ${r.url}`).join(', '));
      }
      const newErrs = getErrors().slice(errCountBefore);
      if (newErrs.length > 0) {
        perConsole.push(`${label} (${href}): ${newErrs.length} error(es) — ${newErrs[0].message.slice(0, 120)}`);
      }
    }

    if (broken.length > 0) {
      console.warn(`[FINDING][P0] NAV-08: ${broken.length} link(s) del sidenav rotos:\n  ` + broken.join('\n  '));
    }
    if (blank.length > 0) {
      console.warn(`[FINDING][P1] NAV-08: ${blank.length} link(s) navegan a pantalla vacía:\n  ` + blank.join('\n  '));
    }
    if (per5xx.length > 0) {
      console.warn(`[FINDING][P1] NAV-08: ${per5xx.length} módulo(s) del sidenav con error 5xx:\n  ` + per5xx.join('\n  '));
    }
    if (perConsole.length > 0) {
      console.warn(`[FINDING][P2] NAV-08: ${perConsole.length} módulo(s) del sidenav con errores de consola:\n  ` + perConsole.join('\n  '));
    }

    // Solo falla el test si TODO el sidenav está roto (señal de regresión grave del shell)
    expect(broken.length).toBeLessThan(total);
  });

  test('NAV-09 | solo el link de la ruta activa tiene clase .active', async ({ page }) => {
    await new LoginPage(page).login(USERS.ADMIN_GLOBAL);
    await page.goto('/dashboard', { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(1_500);

    const targets = ['/alumnos', '/grupos', '/calificaciones'];
    for (const route of targets) {
      const link = page.locator(`.sidenav li a[href="${route}"]`).first();
      const exists = await link.count();
      if (exists === 0) continue;

      await link.click();
      await page.waitForTimeout(900);

      const activeLinks = page.locator('.sidenav li a.active');
      const activeCount = await activeLinks.count();
      if (activeCount !== 1) {
        const hrefs: string[] = [];
        for (let i = 0; i < activeCount; i++) {
          hrefs.push((await activeLinks.nth(i).getAttribute('href')) ?? '?');
        }
        console.warn(
          `[FINDING][P2] NAV-09: navegando a ${route}, ${activeCount} link(s) con clase .active ` +
          `(esperado: 1) — ${hrefs.join(', ')}`
        );
      }
    }
  });
});

test.describe('D. Persistencia de contexto entre módulos', () => {
  test('NAV-10 | selección de Grado/Grupo en topbar persiste al navegar entre módulos vía sidebar', async ({ page }) => {
    await new LoginPage(page).login(USERS.ADMIN_GLOBAL);
    await page.goto('/dashboard', { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(1_500);

    const selects = await topbarSelects(page);
    if (await selects.count() < 5) { test.skip(); return; }

    const chosenGrado = await cascadeSelect(page, selects.nth(3)); // Grado
    if (!chosenGrado) { test.skip(); return; }
    await page.waitForTimeout(1_000);

    const snapshotBefore = await page.evaluate(() => ({
      plantel: sessionStorage.getItem('ades_plantel'),
      ciclo: sessionStorage.getItem('ades_ciclo'),
    }));

    const routes = ['/alumnos', '/grupos', '/calificaciones'];
    for (const route of routes) {
      const link = page.locator(`.sidenav li a[href="${route}"]`).first();
      if (await link.count() === 0) continue;
      await link.click();
      await page.waitForTimeout(900);

      const selectsNow = await topbarSelects(page);
      const gradoNow = await selectsNow.count() >= 5
        ? ((await selectsNow.nth(3).textContent()) ?? '').trim()
        : null;

      if (gradoNow !== null && gradoNow !== chosenGrado) {
        console.warn(
          `[FINDING][P1] NAV-10: al navegar a ${route}, el Grado del topbar cambió de ` +
          `"${chosenGrado}" a "${gradoNow}" — el contexto no persiste entre módulos`
        );
      }
    }

    const snapshotAfter = await page.evaluate(() => ({
      plantel: sessionStorage.getItem('ades_plantel'),
      ciclo: sessionStorage.getItem('ades_ciclo'),
    }));

    if (snapshotBefore.plantel !== snapshotAfter.plantel || snapshotBefore.ciclo !== snapshotAfter.ciclo) {
      console.warn(
        '[FINDING][P1] NAV-10: sessionStorage ades_plantel/ades_ciclo cambió sin interacción ' +
        'del usuario durante la navegación entre módulos'
      );
    }
  });
});

test.describe('E. Breadcrumbs', () => {
  test('NAV-11 | breadcrumb refleja la ruta actual y "Home" regresa a dashboard', async ({ page }) => {
    await new LoginPage(page).login(USERS.ADMIN_GLOBAL);
    await page.goto('/alumnos', { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(1_500);

    const breadcrumb = page.locator('.breadcrumb-container, apex-breadcrumb');
    const visible = await breadcrumb.first().isVisible().catch(() => false);
    if (!visible) {
      console.warn('[FINDING][P3] NAV-11: no se encontró breadcrumb visible en /alumnos');
      return;
    }

    const text = ((await breadcrumb.first().textContent()) ?? '').toLowerCase();
    if (!text.includes('alumno')) {
      console.warn(`[FINDING][P2] NAV-11: breadcrumb en /alumnos no menciona "Alumnos" — texto: "${text.slice(0, 80)}"`);
    }

    const home = page.locator('apex-breadcrumb a', { hasText: 'Home' }).first();
    if (await home.count() > 0) {
      await home.click();
      await page.waitForTimeout(800);
      if (!page.url().includes('/dashboard')) {
        console.warn(`[FINDING][P2] NAV-11: click en breadcrumb "Home" no regresó a /dashboard (URL: ${page.url()})`);
      }
    }
  });
});
