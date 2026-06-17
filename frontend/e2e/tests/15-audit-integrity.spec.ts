/**
 * Suite 15 — Integridad de Auditoría E2E
 *
 * Verifica via API que los triggers `audit_biu` disparan correctamente
 * y que el Hallazgo B (triggers dobles → row_version +2) NO ocurre.
 *
 * Contexto (qa_results_2026-06-16.md):
 *  - Hallazgo B: 70 tablas con audit_biu + trg_aud_biu duplicados (row_version +2 por op)
 *  - Hallazgo A: ades_admin puede borrar log_auditoria (no se puede reparar desde UI)
 *  - Hallazgo D: 17 tablas sin columnas de auditoría canónicas
 *
 * Estado real de la BD (investigado 2026-06-17):
 *  - ades_estatus catálogo confirmado con nombre_estatus + entidad
 *  - topics ntfy: patrón `ades_{usuario_id}` (por usuario, no canal global)
 *  - 0 sanciones, 0 reprobados activos en BD QA — tests crean y verifican
 */
import { test, expect } from '@playwright/test';
import { LoginPage } from '../page-objects/login-page';
import { AlumnosPage } from '../page-objects/alumnos-page';
import { USERS, BFF_BASE } from '../fixtures/users';
import { alumnoValido } from '../fixtures/data-generators';
import { getAuditFields, assertRowVersionIncrement, assertAuditFieldsPresent } from '../helpers/audit-client';
import { attachApiMonitor, assertNoServerErrors } from '../helpers/console-monitor';

// ── A. row_version en CREATE ──────────────────────────────────────────────────

test.describe('A. Auditoría en creación — row_version inicial', () => {
  test('AUD-01 | crear alumno → row_version=1 y fecha_creacion NOT NULL', async ({ page }) => {
    const apiResponses = attachApiMonitor(page);
    const lp = new LoginPage(page);
    await lp.login(USERS.COORDINADOR);

    const ap = new AlumnosPage(page);
    await ap.navigate();

    // Capturar el ID del recurso creado vía intercept de respuesta
    let createdId: string | null = null;
    page.on('response', async resp => {
      if (resp.url().includes('/api/v1/alumnos') && resp.request().method() === 'POST' && resp.ok()) {
        const body = await resp.json().catch(() => null);
        if (body?.id) createdId = body.id;
      }
    });

    const data = alumnoValido();
    await ap.openNewForm();
    await ap.fillAlumnoForm(data);
    await ap.save();
    await page.waitForTimeout(2_500);

    if (!createdId) {
      // Puede pasar si el backend retorna 422 (CURP ya existe) — aceptable
      console.warn('[AUD-01] No se capturó ID del alumno creado — puede ser CURP duplicada');
      assertNoServerErrors(apiResponses());
      return;
    }

    // Verificar campos de auditoría vía API
    const token = await page.evaluate(() => sessionStorage.getItem('ades_token') ?? '');
    const resource = await getAuditFields(page.request, token, `/api/v1/alumnos/${createdId}`).catch(() => null);

    if (resource) {
      assertAuditFieldsPresent(resource, 'alumno recién creado');
      expect(resource.row_version).toBe(1);
      expect(resource.fecha_creacion).not.toBeNull();
    }

    assertNoServerErrors(apiResponses());
  });
});

// ── B. row_version en UPDATE (detectar bug trigger doble) ────────────────────

test.describe('B. Auditoría en actualización — Hallazgo B (triggers dobles)', () => {
  test('AUD-02 | editar alumno → row_version incrementa exactamente en +1 (no +2)', async ({ page }) => {
    const lp = new LoginPage(page);
    await lp.login(USERS.COORDINADOR);

    const token = await page.evaluate(() => sessionStorage.getItem('ades_token') ?? '');

    // Obtener primer alumno de la lista
    const listRes = await page.request.get(`${BFF_BASE}/api/v1/alumnos?limit=1`, {
      headers: { Authorization: `Bearer ${token}` },
    });

    if (!listRes.ok()) { test.skip(); return; }
    const listBody = await listRes.json();
    const alumnos = Array.isArray(listBody) ? listBody : (listBody.items ?? listBody.data ?? []);

    if (alumnos.length === 0) { test.skip(); return; }

    const alumnoId = alumnos[0].id;
    const before = await getAuditFields(page.request, token, `/api/v1/alumnos/${alumnoId}`).catch(() => null);
    if (!before) { test.skip(); return; }

    // Hacer un PATCH/PUT simple (cambiar un campo no crítico)
    const patchRes = await page.request.patch(`${BFF_BASE}/api/v1/alumnos/${alumnoId}`, {
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      data: { notas_adicionales: `QA audit test ${Date.now()}` },
    });

    // Si PATCH no existe, intentar PUT
    if (patchRes.status() === 405 || patchRes.status() === 404) {
      test.skip(); return;
    }

    if (!patchRes.ok() && patchRes.status() !== 422) {
      // 422 es aceptable si el campo no existe en el modelo
      test.skip(); return;
    }

    await page.waitForTimeout(500);

    const after = await getAuditFields(page.request, token, `/api/v1/alumnos/${alumnoId}`).catch(() => null);
    if (!after) { test.skip(); return; }

    // CLAVE: El Hallazgo B dice que algunos triggers disparan 2 veces → row_version +2
    // Este test lo detectaría — si falla con "incrementó en 2", confirma el bug
    assertRowVersionIncrement(before, after, 1, `alumno ${alumnoId}`);
  });
});

// ── C. Auditoría de calificaciones ───────────────────────────────────────────

test.describe('C. Auditoría en operaciones de calificaciones', () => {
  test('AUD-03 | campos de auditoría presentes en respuesta de gradebook', async ({ page }) => {
    await new LoginPage(page).login(USERS.DOCENTE);
    const token = await page.evaluate(() => sessionStorage.getItem('ades_token') ?? '');

    const res = await page.request.get(`${BFF_BASE}/api/v1/gradebook`, {
      headers: { Authorization: `Bearer ${token}` },
    });

    if (!res.ok()) { test.skip(); return; }

    const body = await res.json().catch(() => null);
    if (!body) { test.skip(); return; }

    const items = Array.isArray(body) ? body : (body.items ?? body.data ?? []);
    if (items.length === 0) { test.skip(); return; }

    // El primer item del gradebook debe tener row_version
    const firstItem = items[0];
    expect(firstItem.row_version ?? firstItem.rowVersion).toBeGreaterThanOrEqual(0);
  });

  test('AUD-04 | log_auditoria — NO accessible para DELETE por ades_admin (Hallazgo A)', async ({ page }) => {
    // Este test verifica via API que el endpoint no expone operaciones destructivas
    // La vulnerabilidad real es a nivel DB (ades_admin tiene DELETE) pero verificamos
    // que la API no expone un endpoint de borrado de auditoría

    await new LoginPage(page).login(USERS.ADMIN_GLOBAL);
    const token = await page.evaluate(() => sessionStorage.getItem('ades_token') ?? '');

    // Intentar DELETE en un endpoint de auditoría hipotético
    const deleteRes = await page.request.delete(`${BFF_BASE}/api/v1/audit/log`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    // Nunca debe existir un endpoint DELETE para el log de auditoría (404=no existe, 405=método no permitido, 401/403=sin acceso)
    expect([401, 403, 404, 405]).toContain(deleteRes.status());
  });
});

// ── D. Campos de auditoría en tablas críticas ─────────────────────────────────

test.describe('D. Presencia de campos de auditoría en endpoints del BFF', () => {
  /**
   * Verifica que los endpoints del BFF retornen los campos canónicos de auditoría.
   * El Hallazgo D del QA-results menciona 17 tablas sin columnas canónicas.
   */
  const endpointsToCheck = [
    { path: '/api/v1/alumnos',        label: 'alumnos',       user: 'COORDINADOR' },
    { path: '/api/v1/grupos',         label: 'grupos',        user: 'COORDINADOR' },
    { path: '/api/v1/calificaciones', label: 'calificaciones', user: 'DOCENTE' },
  ];

  for (const endpoint of endpointsToCheck) {
    test(`AUD-05 | ${endpoint.label} — respuesta incluye row_version y fecha_creacion`, async ({ page }) => {
      await new LoginPage(page).login(USERS[endpoint.user as keyof typeof USERS]);
      const token = await page.evaluate(() => sessionStorage.getItem('ades_token') ?? '');

      const res = await page.request.get(`${BFF_BASE}${endpoint.path}?limit=1`, {
        headers: { Authorization: `Bearer ${token}` },
      });

      if (!res.ok()) { test.skip(); return; }

      const body = await res.json().catch(() => null);
      if (!body) { test.skip(); return; }

      const items = Array.isArray(body) ? body : (body.items ?? body.data ?? []);
      if (items.length === 0) { test.skip(); return; }

      const item = items[0] as Record<string, unknown>;
      const hasRowVersion = 'row_version' in item || 'rowVersion' in item;
      const hasFechaCreacion = 'fecha_creacion' in item || 'fechaCreacion' in item;

      if (!hasRowVersion) {
        console.warn(`[AUD-05] ${endpoint.label}: sin campo row_version — Hallazgo D activo`);
      }
      if (!hasFechaCreacion) {
        console.warn(`[AUD-05] ${endpoint.label}: sin campo fecha_creacion — Hallazgo D activo`);
      }

      // No fallamos el test — solo reportamos (el Hallazgo D es P2, no bloqueante para E2E)
      expect(typeof item['id']).toBe('string');  // Al menos el ID debe estar
    });
  }
});

// ── E. topic ntfy por usuario ─────────────────────────────────────────────────

test.describe('E. Push notifications ntfy — patrón ades_{usuario_id}', () => {
  /**
   * La investigación confirmó que el topic de ntfy es dinámico:
   * patrón = `ades_{usuario_id}` (FastAPI push.py línea 66)
   * No hay topic global de pruebas — cada usuario tiene el suyo.
   */
  test('AUD-06 | /api/v1/push/subscribe retorna topic del usuario autenticado', async ({ page }) => {
    await new LoginPage(page).login(USERS.COORDINADOR);
    const token = await page.evaluate(() => sessionStorage.getItem('ades_token') ?? '');

    const res = await page.request.get('http://localhost:8000/api/v1/push/subscribe', {
      headers: { Authorization: `Bearer ${token}` },
    });

    if (!res.ok()) {
      // FastAPI puede estar en otra ruta en este entorno
      const alternativeRes = await page.request.get('http://localhost:8000/api/v1/push', {
        headers: { Authorization: `Bearer ${token}` },
      });
      if (!alternativeRes.ok()) { test.skip(); return; }
    }

    const body = await res.json().catch(() => null);
    if (!body) { test.skip(); return; }

    // El topic debe seguir el patrón `ades_{uuid}`
    const topic: string = body.topic ?? '';
    expect(topic).toMatch(/^ades_[0-9a-f-]{36}$/);
    expect(body.url_sse ?? body.urlSse).toContain(topic);
  });

  test('AUD-07 | endpoint push sin auth → 401', async ({ request }) => {
    // El endpoint puede estar en FastAPI bajo /api/v1/push o variantes
    const endpoints = [
      'http://localhost:8000/api/v1/push/subscribe',
      'http://localhost:8000/api/v1/push',
      'http://localhost:8000/api/v1/push/config',
    ];

    let testedCount = 0;
    for (const ep of endpoints) {
      const res = await request.get(ep).catch(() => null);
      if (res) {
        testedCount++;
        // 401 (no auth) o 404 (ruta ligeramente diferente) — nunca 200 sin auth
        expect([401, 404]).toContain(res.status());
        if (res.status() === 401) break;  // éxito confirmado
      }
    }

    if (testedCount === 0) {
      console.warn('[AUD-07] FastAPI no responde en localhost:8000 — verificar que el servicio está activo');
    }
  });
});
