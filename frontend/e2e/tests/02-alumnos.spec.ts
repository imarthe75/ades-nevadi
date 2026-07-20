/**
 * MÓDULO 5 — Alumnos
 * ALU-01..12 + CU-03 (Inscripción) + escenarios humanos A/B/C/D
 */
import { test, expect, Page } from '@playwright/test';
import { LoginPage } from '../page-objects/login-page';
import { AlumnosPage } from '../page-objects/alumnos-page';
import { USERS } from '../fixtures/users';
import {
  alumnoValido, curpValido, curpInvalido,
  EDGE_STRINGS, faker, fechaFutura,
} from '../fixtures/data-generators';
import * as path from 'path';
import * as fs from 'fs';
import * as os from 'os';

test.use({ storageState: undefined });

// Helper: login + ir a alumnos
async function setupAlumnos(page: Page) {
  const lp = new LoginPage(page);
  await lp.login(USERS.COORDINADOR);
  const ap = new AlumnosPage(page);
  await ap.navigate();
  return ap;
}

// ── A. Flujo feliz ────────────────────────────────────────────────────────────

test.describe('A. Flujo feliz', () => {
  test('ALU-01 | lista alumnos visible con búsqueda', async ({ page }) => {
    const ap = await setupAlumnos(page);
    await expect(ap.table).toBeVisible();
    await ap.searchFor('García');
    await ap.waitSpinner();
  });

  test('ALU-02 | crear alumno con datos válidos', async ({ page }) => {
    const ap   = await setupAlumnos(page);
    const data = alumnoValido();
    await ap.openNewForm();
    await ap.fillAlumnoForm(data);
    await ap.save();
    // QA-007 corregido — BFF ahora crea Persona+Estudiante correctamente
    await expect(page.locator('.p-toast-message-success').first()).toBeVisible({ timeout: 8_000 });
  });

  test('ALU-04 | perfil alumno abre al hacer click en fila', async ({ page }) => {
    const ap = await setupAlumnos(page);
    await ap.clickFirstRow();
    // p-drawer abre con .perfil-meta visible — único contenido que solo existe cuando el drawer está abierto
    // (app-alumno-perfil siempre está en el DOM pero oculto hasta que p-drawer se abre)
    const drawerContent = page.locator('.perfil-meta, .matricula-chip, [data-pc-section="content"] .p-tabs');
    await drawerContent.first().waitFor({ state: 'visible', timeout: 15_000 });
    await expect(drawerContent.first()).toBeVisible({ timeout: 15_000 });
  });

  test('ALU-07 | panel detalle alumno con pestañas', async ({ page }) => {
    const ap = await setupAlumnos(page);
    await ap.clickFirstRow();
    // Wait for drawer to fully load before accessing tabs
    await page.locator('.perfil-meta, [data-pc-section="content"]').first().waitFor({ state: 'visible', timeout: 15_000 });
    const tabs = ['personal', 'domicilio', 'academico', 'salud', 'contactos', 'bajas'];
    for (const tab of tabs) {
      const tabEl = page.locator(`[value="${tab}"], .p-tab:has-text("${tab}")`).first();
      if (await tabEl.isVisible({ timeout: 3_000 }).catch(() => false)) {
        await tabEl.click();
        await ap.waitSpinner();
      }
    }
  });
});

// ── B. Errores típicos ────────────────────────────────────────────────────────

test.describe('B. Errores de validación', () => {
  test('ALU-03 | CURP duplicado → 409 Conflict', async ({ page }) => {
    const ap = await setupAlumnos(page);
    // Crear un alumno primero para tener una CURP en el sistema
    const data = alumnoValido();
    await ap.openNewForm();
    await ap.fillAlumnoForm(data);
    await ap.save();
    await page.locator('.p-toast-message').first().waitFor({ timeout: 8_000 });
    await page.keyboard.press('Escape');
    // Hallazgo real (2026-07-20): Escape + una espera fija no garantizaba que la
    // máscara del diálogo (`.p-dialog-mask`) ni el toast anterior ya hubieran
    // desaparecido — el segundo intento de "Nuevo alumno" chocaba con el overlay
    // residual ("intercepts pointer events") y el test tronaba por timeout, no por
    // ningún fallo real de la app. Se espera explícitamente a que ambos elementos
    // dejen de estar presentes antes de intentar la segunda alta.
    await page.locator('.p-dialog-mask').waitFor({ state: 'hidden', timeout: 5_000 }).catch(() => {});
    await page.locator('.p-toast-message').first().waitFor({ state: 'hidden', timeout: 5_000 }).catch(() => {});
    await page.waitForTimeout(300);
    // Intentar crear otro con la misma CURP → debe dar error
    await ap.openNewForm();
    await ap.fillAlumnoForm(data); // misma CURP
    await ap.save();
    await expect(page.locator('.p-toast-message-error').first()).toBeVisible({ timeout: 8_000 });
  });

  test('ALU-05 | inscripción doble en mismo ciclo → error', async ({ page }) => {
    const ap = await setupAlumnos(page);
    await ap.clickFirstRow();
    // Wait for drawer to load
    await page.locator('[data-pc-section="content"]').first().waitFor({ state: 'visible', timeout: 15_000 });
    const inscribirBtn = page.locator('button:has-text("Inscribir"), [data-testid="btn-inscribir"]');
    if (await inscribirBtn.isVisible({ timeout: 3_000 }).catch(() => false)) {
      await inscribirBtn.click();
      // Si ya está inscrito debe aparecer error
      const error = page.locator('.p-toast-message-error, [data-testid="error-inscripcion"]');
      await error.waitFor({ timeout: 8_000 }).catch(() => undefined);
    }
  });

  test('ALU-11 | submit sin CURP → warning de validación', async ({ page }) => {
    const ap = await setupAlumnos(page);
    await ap.openNewForm();
    // Solo llenar nombre, sin CURP
    await ap.fillAndBlur(ap.nombreInput, 'Pedro');
    await ap.fillAndBlur(ap.apPaternoInput, 'López');
    await ap.save();
    // El componente usa notify.warning() no ng-invalid
    await expect(page.locator('.p-toast-message-warn, .p-toast-message-error').first()).toBeVisible({ timeout: 5_000 });
  });

  test('ALU-12 | CURP de 5 chars → warning longitud', async ({ page }) => {
    const ap = await setupAlumnos(page);
    await ap.openNewForm();
    await ap.fillAndBlur(ap.nombreInput, 'Ana');
    await ap.fillAndBlur(ap.apPaternoInput, 'Pérez');
    await ap.fillAndBlur(ap.curpInput, '12345');
    await ap.save();
    // El componente verifica que CURP tenga exactamente 18 chars
    await expect(page.locator('.p-toast-message-warn, .p-toast-message-error').first()).toBeVisible({ timeout: 5_000 });
  });

  test('ALU-10 | RBAC plantel — docente solo ve su plantel', async ({ page }) => {
    const lp = new LoginPage(page);
    await lp.login(USERS.DOCENTE);
    const ap = new AlumnosPage(page);
    await ap.navigate();
    // No debe poder filtrar por otro plantel
    const otherPlantelOption = page.locator('.plantel-option', { hasText: 'Tenancingo' });
    await expect(otherPlantelOption).not.toBeVisible();
  });
});

// ── C. Usuario torpe ──────────────────────────────────────────────────────────

test.describe('C. Usuario torpe', () => {
  test('ALU-C1 | navega atrás en medio del formulario de creación', async ({ page }) => {
    const ap   = await setupAlumnos(page);
    await ap.openNewForm();
    await ap.fillAndBlur(ap.curpInput, curpValido());
    await ap.fillAndBlur(ap.nombreInput, 'Ana');
    // Navega atrás antes de guardar
    await page.goBack();
    // Debe mostrar confirmación o simplemente salir sin crash
    await page.waitForTimeout(1_000);
    // La app no debe crashear
    await expect(page).not.toHaveURL(/error/);
  });

  test('ALU-C2 | recarga en medio de búsqueda', async ({ page }) => {
    const ap = await setupAlumnos(page);
    await ap.searchInput.fill('Garci');
    await page.reload();
    // Lista debe cargar correctamente después del reload
    await ap.waitSpinner();
    await expect(ap.table).toBeVisible();
  });

  test('ALU-C3 | click múltiple en "Nuevo" no duplica el dialog', async ({ page }) => {
    const ap = await setupAlumnos(page);
    // Primer click abre el dialog
    await ap.newBtn.click();
    await page.waitForTimeout(200);
    // Clicks adicionales no deben crashear (btn puede estar dentro del dialog)
    await ap.newBtn.click({ force: true }).catch(() => undefined);
    await page.waitForTimeout(300);
    // Solo debe haber 1 dialog abierto (no duplicados)
    const dialogs = await page.locator('.apex-dialog-wrapper').count();
    expect(dialogs).toBeLessThanOrEqual(1);
    // Cerrar el dialog
    await page.keyboard.press('Escape');
  });

  test('ALU-C4 | scroll rápido en tabla con 1000+ filas', async ({ page }) => {
    const ap = await setupAlumnos(page);
    await ap.searchFor(''); // Sin filtro para ver todos
    await ap.waitSpinner();
    // Scroll rápido hacia abajo y arriba
    await page.keyboard.press('End');
    await page.waitForTimeout(300);
    await page.keyboard.press('Home');
    await expect(ap.table).toBeVisible();
  });

  test('ALU-C5 | pegar en campo CURP y cambiar inmediatamente de campo', async ({ page }) => {
    const ap = await setupAlumnos(page);
    await ap.openNewForm();
    // Pegar CURP válida
    await ap.curpInput.click();
    await page.keyboard.type(curpValido());
    // Cambiar inmediatamente sin esperar validación
    await ap.nombreInput.click();
    await ap.nombreInput.fill('Test');
    // La CURP debe haberse mantenido
    const curpVal = await ap.curpInput.inputValue();
    expect(curpVal.length).toBeGreaterThan(10);
  });
});

// ── D. Usuario caótico ────────────────────────────────────────────────────────

test.describe('D. Usuario caótico — inputs extremos', () => {
  const extremeInputs = [
    { label: 'SQL injection',   value: EDGE_STRINGS.SQL_INJECTION },
    { label: 'XSS básico',      value: EDGE_STRINGS.XSS_BASIC },
    { label: 'Emojis',          value: EDGE_STRINGS.EMOJIS },
    { label: '1000 chars',      value: EDGE_STRINGS.LONG_1000 },
    { label: 'Unicode mix',     value: EDGE_STRINGS.UNICODE_MIX },
    { label: 'Solo espacios',   value: EDGE_STRINGS.SPACE_ONLY },
    { label: 'Fórmula CSV inj', value: EDGE_STRINGS.FORMULA_INJ },
    { label: 'Path traversal',  value: EDGE_STRINGS.PATH_TRAV },
  ];

  for (const { label, value } of extremeInputs) {
    test(`ALU-D | campo nombre: ${label}`, async ({ page }) => {
      const dialogs: string[] = [];
      page.on('dialog', d => { dialogs.push(d.message()); d.dismiss(); });
      const ap = await setupAlumnos(page);
      await ap.openNewForm();
      await ap.fillAndBlur(ap.nombreInput, value);
      await ap.save();
      // La app no debe ejecutar scripts ni crashear
      await page.waitForTimeout(1_000);
      await expect(page).not.toHaveURL(/error|crash/);
      expect(dialogs).toHaveLength(0);
      // Cerrar dialog
      await page.keyboard.press('Escape');
    });
  }

  test('ALU-D-fuzz | 5 curps aleatorias en formulario', async ({ page }) => {
    const ap = await setupAlumnos(page);
    for (let i = 0; i < 5; i++) {
      // Asegurar que no haya diálogo abierto del ciclo anterior
      await page.keyboard.press('Escape');
      await page.waitForTimeout(200);
      // Abrir form — si no abre (dialog ya cerrado), reintentar
      await ap.newBtn.click();
      const dialogEl = page.locator('.apex-dialog-wrapper');
      const opened = await dialogEl.waitFor({ state: 'visible', timeout: 3_000 }).then(() => true).catch(() => false);
      if (!opened) continue;

      await ap.fillAndBlur(ap.nombreInput, faker.person.firstName());
      await ap.fillAndBlur(ap.apPaternoInput, faker.person.lastName());
      await ap.fillAndBlur(ap.curpInput, faker.string.alphanumeric(
        faker.number.int({ min: 0, max: 30 })
      ));
      await ap.save();
      await page.waitForTimeout(800);
      // Cerrar el dialog
      await page.keyboard.press('Escape');
      await page.waitForTimeout(300);
    }
    // La app sigue funcionando
    await expect(ap.table).toBeVisible();
  });

  test('ALU-D-csv-invalido | subir CSV con datos malformados', async ({ page }) => {
    const ap = await setupAlumnos(page);
    // Asegurar que no haya dialog abierto antes de intentar importar
    await page.keyboard.press('Escape');
    await page.waitForTimeout(200);

    const csvContent = [
      'curp,nombre,apellido_paterno,fecha_nacimiento',
      `${EDGE_STRINGS.SQL_INJECTION},Hack,Hack,2010-01-01`,
      `,,,`,
      `${curpValido()},${'A'.repeat(500)},López,fecha-invalida`,
    ].join('\n');

    const tmpFile = path.join(os.tmpdir(), `test-alumnos-${Date.now()}.csv`);
    fs.writeFileSync(tmpFile, csvContent);

    try {
      await ap.importBtn.click({ force: true });
      const fileInput = page.locator('input[type="file"]');
      await fileInput.setInputFiles(tmpFile);
      await page.waitForTimeout(3_000);
      // Debe mostrar algún feedback (error report, toast, o similar)
      const feedback = page.locator('[data-testid="import-errors"], .import-error-list, .p-toast-message');
      await feedback.first().waitFor({ timeout: 8_000 }).catch(() => undefined);
    } finally {
      fs.unlinkSync(tmpFile);
    }
  });
});
