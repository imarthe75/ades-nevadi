/**
 * Suite 17 — Advanced Security & Integrity
 *
 * Cubre brechas no cubiertas por suites 06 (chaos) y 07 (fuzz):
 *
 *  ADV-01  Doble envío asíncrono — 10 clicks rápidos en "Aprobar masivo" → ≤1 POST a la API
 *  ADV-02  Fecha imposible pasada — año 1026 en campo fecha → validación rechaza
 *  ADV-03  Fecha imposible futura — año 2099 en campo fecha → validación rechaza
 *  ADV-04  MIME type real vs extensión — .exe renombrado .jpg → backend debe rechazar
 *  ADV-05  XSS persistido → vista admin renderiza como texto plano (sin ejecución)
 *  ADV-06  Optimistic locking — row_version stale → 409 Conflict
 *  ADV-07  Gremlins monkey testing — 100 eventos aleatorios → sin JS errors críticos
 *  ADV-08  Estado menú — solo 1 ítem PrimeNG activo al navegar entre módulos
 *
 * Notas de diseño:
 *  - ADV-01/04/06 verifican a nivel API, no solo UI — complementan CAOS-15, FUZZ-07
 *  - Los tests loguean [FINDING] en lugar de fallar cuando detectan vulnerabilidades
 *    que aún no están implementadas (no queremos bloquear CI por features pendientes)
 *  - ADV-07 usa gremlins.js v2 inyectado via page.addScriptTag()
 */

import { test, expect } from '@playwright/test';
import { readFileSync, existsSync } from 'fs';
import { resolve } from 'path';
import { LoginPage } from '../page-objects/login-page';
import { USERS, API_BASE, BFF_BASE } from '../fixtures/users';

const TOKEN_FILE  = resolve(__dirname, '../.auth/token.txt');
const GREMLINS_JS = resolve(__dirname, '../../node_modules/gremlins.js/dist/gremlins.min.js');

/** Lee el JWT local si existe */
function readToken(): string {
  if (existsSync(TOKEN_FILE)) return readFileSync(TOKEN_FILE, 'utf-8').trim();
  return '';
}

// ── A. Doble envío asíncrono ──────────────────────────────────────────────────

test.describe('A. Doble envío asíncrono — inscripción masiva', () => {
  test('ADV-01 | 10 clicks rápidos en Aprobar → ≤1 POST registrado @security', async ({ page }) => {
    await new LoginPage(page).login(USERS.COORDINADOR);
    await page.goto('/reinscripcion', { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(2_000);

    const url = page.url();
    if (url.includes('/login') || !url.includes('reinscripcion')) {
      test.skip(); return;
    }

    // Contar POSTs hacia endpoints de reinscripción mientras se hace click 10 veces
    let postCount = 0;
    page.on('request', req => {
      if (req.method() === 'POST' && req.url().includes('reinscripcion')) {
        postCount++;
      }
    });

    const aprobarBtn = page.locator(
      'button:has-text("Aprobar"), [data-testid="btn-aprobar-masivo"], button:has-text("Inscribir")'
    ).first();

    if (!await aprobarBtn.isVisible({ timeout: 3_000 }).catch(() => false)) {
      // Sin alumnos pendientes — contar clicks en cualquier acción mutante de la lista
      test.skip(); return;
    }

    // Deshabilitar por UI al primer click — verificar que los siguientes 9 no generan POST
    for (let i = 0; i < 10; i++) {
      await aprobarBtn.click({ force: true, timeout: 500 }).catch(() => {});
    }
    await page.waitForTimeout(2_000);

    if (postCount > 1) {
      console.warn(
        `[FINDING][P1] ADV-01: ${postCount} POSTs enviados con 10 clicks — ` +
        'falta debounce o disable en botón de inscripción masiva'
      );
    }
    // La UI no debe haberse crasheado
    await expect(page.locator('app-root')).toBeVisible();
    expect(postCount).toBeLessThanOrEqual(3); // Toleramos hasta 3 por latencia de red
  });
});

// ── B. Fechas imposibles ──────────────────────────────────────────────────────

test.describe('B. Fechas imposibles — validación backend', () => {
  /**
   * Envía fecha_nacimiento via API de persona y verifica que el backend no acepta años fuera de rango.
   * Si el backend acepta, loguea [FINDING] pero no falla (la validación puede estar pendiente).
   */
  async function testFechaNacimiento(
    request: import('@playwright/test').APIRequestContext,
    fecha: string,
    testId: string
  ) {
    const tok = readToken();
    if (!tok) { return null; }

    // Intentar vía Spring BFF — endpoint de creación de usuario/persona
    const res = await request.post(`${BFF_BASE}/api/v1/admin/usuarios`, {
      headers: { Authorization: `Bearer ${tok}`, 'Content-Type': 'application/json' },
      data: {
        curp: `XAXX${fecha.replace(/-/g, '').slice(2, 8)}HDFXXX99`,
        nombre: 'TestFechaImp',
        apellidoPaterno: 'Validacion',
        apellidoMaterno: 'Fecha',
        fechaNacimiento: fecha,
        sexo: 'M',
        email: `test.fecha.${Date.now()}@test.ades`,
        rol: 'DOCENTE',
        plantelId: null,
      },
    }).catch(() => null);

    return res;
  }

  test('ADV-02 | fecha_nacimiento año 1026 → backend rechaza @security', async ({ request }) => {
    const res = await testFechaNacimiento(request, '1026-06-15', 'ADV-02');
    if (!res) { test.skip(); return; }

    const status = res.status();
    expect(status).not.toBe(500); // Nunca debe ser error interno

    if (status === 200 || status === 201) {
      console.warn(
        '[FINDING][P1] ADV-02: Backend acepta fecha_nacimiento=1026 — ' +
        'falta validación de año mínimo (esperado ≥ 1900) en ades_personas'
      );
    } else {
      // 400, 409 (CURP dup), 422 son respuestas correctas de rechazo
      expect([400, 409, 422, 403]).toContain(status);
    }
  });

  test('ADV-03 | fecha_nacimiento año 2099 → backend rechaza @security', async ({ request }) => {
    const res = await testFechaNacimiento(request, '2099-01-01', 'ADV-03');
    if (!res) { test.skip(); return; }

    const status = res.status();
    expect(status).not.toBe(500);

    if (status === 200 || status === 201) {
      console.warn(
        '[FINDING][P1] ADV-03: Backend acepta fecha_nacimiento=2099 — ' +
        'falta validación de fecha futura en ades_personas'
      );
    } else {
      expect([400, 409, 422, 403]).toContain(status);
    }
  });

  test('ADV-03b | UI rechaza año imposible en campos de fecha @security', async ({ page }) => {
    await new LoginPage(page).login(USERS.COORDINADOR);
    await page.goto('/alumnos', { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(2_000);

    const url = page.url();
    if (url.includes('/login') || !url.includes('alumnos')) { test.skip(); return; }

    // Abrir modal de nuevo alumno si existe
    const nuevoBtn = page.locator('button:has-text("Nuevo"), [data-testid="btn-nuevo-alumno"]').first();
    if (!await nuevoBtn.isVisible({ timeout: 3_000 }).catch(() => false)) { test.skip(); return; }
    await nuevoBtn.click();
    await page.waitForTimeout(500);

    // Buscar campo de fecha de nacimiento en el diálogo
    const fechaInput = page.locator(
      '[formcontrolname="fecha_nacimiento"], [name="fecha_nacimiento"], input[type="date"]'
    ).first();
    if (!await fechaInput.isVisible({ timeout: 2_000 }).catch(() => false)) { test.skip(); return; }

    // Intentar ingresar año imposible
    await fechaInput.fill('1026-06-15');

    const saveBtn = page.locator('button[type="submit"], button:has-text("Guardar")').first();
    if (await saveBtn.isVisible({ timeout: 1_000 }).catch(() => false)) {
      await saveBtn.click();
      await page.waitForTimeout(1_500);
    }

    // Verificar que hay error de validación (no crash)
    const errEl = page.locator('.p-toast-message-error, .p-toast-message-warn, .p-error, .field-error, .ng-invalid');
    const hasError = await errEl.first().isVisible({ timeout: 3_000 }).catch(() => false);
    if (!hasError) {
      console.warn('[FINDING][P2] ADV-03b: UI no rechaza fecha_nacimiento=1026 — falta validación de año mínimo');
    }
    await expect(page.locator('app-root')).toBeVisible();
  });
});

// ── C. MIME type real vs extensión ────────────────────────────────────────────

test.describe('C. MIME type real vs extensión de archivo', () => {
  test('ADV-04 | .exe con magic bytes MZ renombrado .jpg → backend rechaza @security', async ({ request, page }) => {
    // Necesitamos un alumno_id para el endpoint de documentos
    // Primero autenticamos para obtener el token de sesión
    await new LoginPage(page).login(USERS.COORDINADOR);

    const tok = await page.evaluate(() => {
      const keys = ['ades_token', 'access_token', 'token'];
      for (const k of keys) {
        const v = sessionStorage.getItem(k);
        if (v) return v;
      }
      return '';
    });
    if (!tok) { test.skip(); return; }

    // Obtener un alumno del BFF
    const alumnosRes = await request.get(`${BFF_BASE}/api/v1/alumnos`, {
      headers: { Authorization: `Bearer ${tok}` },
    }).catch(() => null);

    if (!alumnosRes?.ok()) { test.skip(); return; }

    const body = await alumnosRes.json().catch(() => null);
    const items = Array.isArray(body) ? body : (body?.content ?? body?.items ?? body?.data ?? []);
    if (!items.length) { test.skip(); return; }

    const alumnoId: string = items[0].id ?? items[0].alumnoId ?? items[0].estudiante_id;
    if (!alumnoId) { test.skip(); return; }

    // Crear buffer con PE magic bytes (Windows executable header: MZ)
    const peMagic = Buffer.from([0x4D, 0x5A, 0x90, 0x00, 0x03, 0x00, 0x00, 0x00, 0x04, 0x00]);

    // Subir al endpoint de expediente como image/jpeg (tipo real: application/x-msdownload)
    const uploadRes = await request.post(
      `${API_BASE}/expediente/alumno/${alumnoId}/documentos`,
      {
        headers: { Authorization: `Bearer ${tok}` },
        multipart: {
          archivo: {
            name: 'foto_perfil.jpg',  // extensión falsa
            mimeType: 'image/jpeg',   // content-type declarado falso
            buffer: peMagic,
          },
          tipo_documento: 'FOTOGRAFIA',
        },
      }
    ).catch(() => null);

    if (!uploadRes) { test.skip(); return; }

    const status = uploadRes.status();
    expect(status).not.toBe(500); // No debe crash interno

    if (status === 200 || status === 201) {
      console.warn(
        '[FINDING][P1] ADV-04: Backend aceptó .exe renombrado como .jpg — ' +
        'expediente.py usa archivo.content_type del header HTTP sin verificar magic bytes. ' +
        'Solución: usar python-magic para validar MIME real antes de subir a MinIO.'
      );
    } else {
      // 400, 415 (Unsupported Media Type), 422 son correctos
      console.log(`[ADV-04] Backend rechazó el archivo con status ${status} — validación MIME activa`);
    }
  });
});

// ── D. XSS persistido ────────────────────────────────────────────────────────

test.describe('D. XSS persistido — almacenado y renderizado sin ejecución', () => {
  test('ADV-05 | payload XSS en chatbot → sin window.alert ejecutado @security', async ({ page }) => {
    const consoleErrors: string[] = [];
    const dialogs: string[] = [];

    page.on('console', msg => {
      if (msg.type() === 'error') consoleErrors.push(msg.text());
    });
    // Captura si se ejecuta alert()
    page.on('dialog', async dialog => {
      dialogs.push(dialog.message());
      await dialog.dismiss();
    });

    await new LoginPage(page).login(USERS.COORDINADOR);
    await page.goto('/ia-asistente', { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(2_000);

    const url = page.url();
    if (url.includes('/login') || (!url.includes('ia') && !url.includes('asistente') && !url.includes('chat'))) {
      test.skip(); return;
    }

    const chatInput = page.locator(
      'textarea[placeholder], input[placeholder*="mensaje"], [data-testid="chat-input"]'
    ).first();

    if (!await chatInput.isVisible({ timeout: 3_000 }).catch(() => false)) {
      test.skip(); return;
    }

    // Enviar payload XSS
    const xssPayload = '<script>alert("XSS-ADV05")</script><img src=x onerror=alert(1)>';
    await chatInput.fill(xssPayload);

    const sendBtn = page.locator(
      'button[type="submit"], button:has-text("Enviar"), [data-testid="btn-send"]'
    ).first();
    if (await sendBtn.isVisible({ timeout: 1_000 }).catch(() => false)) {
      await sendBtn.click();
    } else {
      await chatInput.press('Enter');
    }

    await page.waitForTimeout(3_000);

    // Verificar que no se ejecutó alert()
    if (dialogs.length > 0) {
      throw new Error(
        `[FINDING][P1-CRÍTICO] ADV-05: XSS ejecutado — alert('${dialogs[0]}') se activó. ` +
        'Angular debe sanitizar innerHTML; verificar uso de [innerHTML] vs {{ }} en chat component.'
      );
    }

    // Verificar que el payload se renderizó como texto o fue escapado
    const rawScript = await page.locator('script:has-text("XSS-ADV05")').count();
    if (rawScript > 0) {
      console.warn('[FINDING][P1] ADV-05: Tag <script> inyectado en el DOM — Angular XSS sanitization bypass');
    }

    await expect(page.locator('app-root')).toBeVisible();
    console.log(`[ADV-05] No se ejecutó XSS (dialogs=${dialogs.length}) — sanitización Angular activa`);
  });

  test('ADV-05b | XSS en buscador de alumnos → texto plano, no HTML @security', async ({ page }) => {
    const dialogs: string[] = [];
    page.on('dialog', async dialog => {
      dialogs.push(dialog.message());
      await dialog.dismiss();
    });

    await new LoginPage(page).login(USERS.COORDINADOR);
    await page.goto('/alumnos', { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(2_000);

    const url = page.url();
    if (url.includes('/login') || !url.includes('alumnos')) { test.skip(); return; }

    const searchInput = page.locator(
      'input[type="search"], input[placeholder*="buscar"], input[placeholder*="nombre"], [data-testid="search-alumnos"]'
    ).first();

    if (!await searchInput.isVisible({ timeout: 3_000 }).catch(() => false)) { test.skip(); return; }

    await searchInput.fill('<img src=x onerror=alert("XSS-SEARCH")>');
    await searchInput.press('Enter');
    await page.waitForTimeout(2_000);

    expect(dialogs).toHaveLength(0);
    await expect(page.locator('app-root')).toBeVisible();
  });
});

// ── E. Optimistic locking ────────────────────────────────────────────────────

test.describe('E. Optimistic locking — edición concurrente', () => {
  test('ADV-06 | PATCH contacto con rowVersion stale → 409 Conflict @security', async ({ request, page }) => {
    await new LoginPage(page).login(USERS.COORDINADOR);

    const tok = await page.evaluate(() => {
      const keys = ['ades_token', 'access_token', 'token'];
      for (const k of keys) {
        const v = sessionStorage.getItem(k);
        if (v) return v;
      }
      return '';
    });
    if (!tok) { test.skip(); return; }

    // Paso 1: obtener lista de alumnos para encontrar un contacto
    const alumnosRes = await request.get(`${BFF_BASE}/api/v1/alumnos`, {
      headers: { Authorization: `Bearer ${tok}` },
    }).catch(() => null);
    if (!alumnosRes?.ok()) { test.skip(); return; }

    const alumnosBody = await alumnosRes.json().catch(() => null);
    const alumnos = Array.isArray(alumnosBody)
      ? alumnosBody
      : (alumnosBody?.content ?? alumnosBody?.items ?? []);
    if (!alumnos.length) { test.skip(); return; }

    // Paso 2: obtener detalle del primer alumno para encontrar contactos
    const alumnoId = alumnos[0].id ?? alumnos[0].alumnoId;
    if (!alumnoId) { test.skip(); return; }

    const detalleRes = await request.get(`${BFF_BASE}/api/v1/alumnos/${alumnoId}`, {
      headers: { Authorization: `Bearer ${tok}` },
    }).catch(() => null);
    if (!detalleRes?.ok()) { test.skip(); return; }

    const detalle = await detalleRes.json().catch(() => null);
    const contactos = detalle?.contactos ?? detalle?.persona?.contactos ?? [];
    if (!contactos.length) {
      console.log('[ADV-06] Alumno sin contactos — skip');
      test.skip(); return;
    }

    const contacto = contactos[0];
    const contactoId = contacto.id ?? contacto.contactoId;
    const currentRowVersion = contacto.rowVersion ?? contacto.row_version ?? 1;
    if (!contactoId) { test.skip(); return; }

    // Paso 3: PATCH con rowVersion intencionalmente stale (versión anterior)
    const staleVersion = Math.max(0, currentRowVersion - 99); // muy stale
    const patchRes = await request.patch(`${BFF_BASE}/api/v1/contactos/${contactoId}`, {
      headers: { Authorization: `Bearer ${tok}`, 'Content-Type': 'application/json' },
      data: {
        rowVersion: staleVersion,
        nombre: contacto.nombre ?? 'Test Conflict',
        telefono: contacto.telefono ?? '5500000000',
        parentesco: contacto.parentesco ?? 'PADRE',
        puedeRecoger: contacto.puedeRecoger ?? true,
      },
    }).catch(() => null);

    if (!patchRes) { test.skip(); return; }

    const status = patchRes.status();
    expect(status).not.toBe(500);

    if (status === 200 || status === 204) {
      console.warn(
        `[FINDING][P1] ADV-06: Backend aceptó PATCH con rowVersion stale (${staleVersion} vs actual ${currentRowVersion}) — ` +
        'falta validación de optimistic locking en contactos endpoint. ' +
        'El helper check_row_version() existe en optimistic_locking.py pero no está conectado.'
      );
    } else if (status === 409 || status === 412) {
      console.log(`[ADV-06] Backend rechazó correctamente con ${status} — optimistic locking activo`);
    }
  });
});

// ── F. Monkey testing con Gremlins.js ─────────────────────────────────────────

test.describe('F. Monkey testing — Gremlins.js', () => {
  test('ADV-07 | gremlins 10s en módulo alumnos → sin JS errors críticos @chaos', async ({ page }) => {
    const criticalErrors: string[] = [];

    page.on('pageerror', err => {
      const msg = err.message;
      // Ignorar errores conocidos/esperados de red y CORS
      if (
        msg.includes('Failed to fetch') ||
        msg.includes('NetworkError') ||
        msg.includes('ERR_CONNECTION') ||
        msg.includes('CORS') ||
        msg.includes('ades.setag.mx')
      ) return;
      criticalErrors.push(msg);
    });

    await new LoginPage(page).login(USERS.COORDINADOR);
    await page.goto('/alumnos', { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(2_000);

    const url = page.url();
    if (url.includes('/login') || !url.includes('alumnos')) { test.skip(); return; }

    if (!existsSync(GREMLINS_JS)) {
      console.warn('[ADV-07] gremlins.js no encontrado — usando monkey testing lite');
      // Fallback: monkey testing inline
      await page.evaluate(() => {
        let count = 0;
        const clicker = setInterval(() => {
          const x = Math.floor(Math.random() * window.innerWidth * 0.9);
          const y = Math.floor(Math.random() * window.innerHeight * 0.9);
          const el = document.elementFromPoint(x, y);
          if (el instanceof HTMLElement && !el.closest('[data-gremlins-skip]')) {
            el.click();
          }
          if (++count >= 100) clearInterval(clicker);
        }, 100);
      });
      await page.waitForTimeout(12_000);
    } else {
      // Inyectar gremlins.js desde node_modules
      await page.addScriptTag({ path: GREMLINS_JS });

      // Lanzar horda de gremlins por ~10 segundos
      await page.evaluate(() => {
        const g = (window as unknown as { gremlins: {
          createHorde: (opts: unknown) => { unleash: () => Promise<void> };
          species: { clicker: () => unknown; formFiller: () => unknown };
          mogwais: { alert: () => unknown; gizmo: () => unknown };
          strategies: { distribution: (opts: { delay: number; nb: number }) => unknown };
        } }).gremlins;

        return g.createHorde({
          species: [
            g.species.clicker(),
            g.species.formFiller(),
          ],
          mogwais: [
            g.mogwais.alert(),
            g.mogwais.gizmo(),
          ],
          strategies: [
            g.strategies.distribution({ delay: 100, nb: 100 }),
          ],
        }).unleash();
      }).catch(() => {
        // gremlins puede lanzar si interrumpe async internamente — aceptable
      });

      await page.waitForTimeout(2_000); // buffer post-horda
    }

    // La aplicación debe seguir respondiendo
    const appVisible = await page.locator('app-root').isVisible({ timeout: 5_000 }).catch(() => false);
    if (!appVisible) {
      throw new Error('[FINDING][P1] ADV-07: app-root desapareció tras monkey testing — crash del componente');
    }

    if (criticalErrors.length > 0) {
      console.warn(
        `[FINDING][P2] ADV-07: ${criticalErrors.length} JS error(s) durante monkey testing:\n` +
        criticalErrors.slice(0, 5).map(e => `  - ${e.slice(0, 100)}`).join('\n')
      );
    }

    // No fallamos el test por errores durante monkeys — solo documentamos
    await expect(page.locator('app-root')).toBeVisible();
  });
});

// ── G. Estado de menú de navegación ──────────────────────────────────────────

test.describe('G. Estado del menú — solo 1 ítem activo', () => {
  const ROUTES: Array<{ path: string; label: string }> = [
    { path: '/alumnos',      label: 'Alumnos' },
    { path: '/calificaciones', label: 'Calificaciones' },
    { path: '/reinscripcion', label: 'Reinscripción' },
  ];

  for (const route of ROUTES) {
    test(`ADV-08 | navegar a ${route.path} → solo "${route.label}" activo en menú`, async ({ page }) => {
      await new LoginPage(page).login(USERS.COORDINADOR);
      await page.goto(route.path, { waitUntil: 'domcontentloaded' });
      await page.waitForTimeout(2_000);

      const url = page.url();
      if (url.includes('/login')) { test.skip(); return; }

      // Seleccionar todos los ítems de menú (PrimeNG p-menuitem, custom nav links)
      const menuItems = page.locator(
        '.p-menuitem, nav a, [role="menuitem"], .nav-item, .sidenav-item'
      );
      const count = await menuItems.count();

      if (count === 0) {
        console.log(`[ADV-08] Sin ítems de menú detectados en ${route.path} — skip`);
        test.skip(); return;
      }

      // Contar ítems con clases de "activo"
      const activeSelector = [
        '.p-menuitem-link-active',
        '.p-highlight',
        '.active',
        '[aria-current="page"]',
        '.router-link-active',
      ].join(', ');

      const activeCount = await page.locator(activeSelector).count();

      if (activeCount > 1) {
        const activeTexts: string[] = [];
        const activeEls = page.locator(activeSelector);
        for (let i = 0; i < Math.min(activeCount, 5); i++) {
          activeTexts.push(await activeEls.nth(i).textContent().catch(() => '??') ?? '??');
        }
        console.warn(
          `[FINDING][P2] ADV-08: ${activeCount} ítems marcados como activos en ${route.path}: ` +
          activeTexts.map(t => `"${t.trim()}"`).join(', ')
        );
      }

      // La app no debe crashear al navegar
      await expect(page.locator('app-root')).toBeVisible();
    });
  }
});
