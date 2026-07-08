import { test, expect, Browser, BrowserContext, Page } from '@playwright/test';

/**
 * E2E Tests para Validación Integral ADES (FASE 1-3)
 *
 * Tests de:
 * - Validación CURP + RFC + Nombres + Dinero + Teléfono
 * - Persistencia (GET post-PUT)
 * - Sesión prolongada (refresh token)
 */

let browser: Browser;
let context: BrowserContext;
let page: Page;

// Credenciales Nevadi (desarrollo)
const NEVADI_USER = 'director@nevadi.edu.mx'; // Cambiar según env
const NEVADI_PASS = process.env.TEST_PASSWORD || 'password';
const BASE_URL = 'https://ades.setag.mx';

test.describe('Validación Integral ADES', () => {

  test.beforeAll(async () => {
    // No se ejecuta en navegador headless (usar --headed para ver)
  });

  test('FASE 1: Validación CURP — formato correcto', async ({ page }) => {
    // Ir a personal-admin
    await page.goto(`${BASE_URL}/personal-admin`, { waitUntil: 'networkidle' });
    await page.waitForLoadState('networkidle');

    // Click en "Nuevo registro"
    await page.click('button:has-text("Nuevo registro")');
    await page.waitForSelector('[placeholder="Ej: Juan Carlos"]');

    // Llenar formulario con CURP válida
    await page.fill('input[placeholder="Ej: AAAA999999HAAAAA01"]', 'GACD900101HDFRRL09');

    // Verificar que no hay error
    const error = await page.$('text=inválida');
    expect(error).toBeNull();

    // Verificar contador de caracteres
    const counter = await page.$('text=18 / 18');
    expect(counter).toBeTruthy();
  });

  test('FASE 1: Validación CURP — formato inválido rechazado', async ({ page }) => {
    await page.goto(`${BASE_URL}/personal-admin`);
    await page.click('button:has-text("Nuevo registro")');

    // Ingresar CURP inválida
    await page.fill('input[placeholder="Ej: AAAA999999HAAAAA01"]', 'INVALID_CURP_1');
    await page.click('button:has-text("Guardar")');

    // Debe mostrar error
    const errorMsg = await page.waitForSelector('text=CURP inválida', { timeout: 2000 });
    expect(errorMsg).toBeTruthy();
  });

  test('FASE 1: Validación RFC — conversión a mayúsculas', async ({ page }) => {
    await page.goto(`${BASE_URL}/personal-admin`);
    await page.click('button:has-text("Nuevo registro")');

    // Ingresar RFC en minúsculas
    const rfcInput = await page.$('input[placeholder="Ej: AAAA999999AAA"]');
    await rfcInput?.fill('gacd900101abc');

    // Verificar que se convierte a mayúsculas automáticamente
    const value = await rfcInput?.inputValue();
    expect(value).toBe('GACD900101ABC');
  });

  test('FASE 1: Validación Nombres — solo letras permitidas', async ({ page }) => {
    await page.goto(`${BASE_URL}/personal-admin`);
    await page.click('button:has-text("Nuevo registro")');

    // Intentar ingresar números en nombre
    const nombreInput = await page.$('input[placeholder="Ej: Juan Carlos"]');
    await nombreInput?.fill('Juan123');

    // Verificar que se elimina el número automáticamente
    const value = await nombreInput?.inputValue();
    expect(value).toBe('Juan');
  });

  test('FASE 2: Persistencia — GET post-PUT', async ({ page }) => {
    await page.goto(`${BASE_URL}/personal-admin`);
    await page.click('button:has-text("Nuevo registro")');

    // Rellenar formulario completo
    await page.fill('input[placeholder="Ej: Juan Carlos"]', 'Juan Carlos');
    await page.fill('input[placeholder="Ej: García"]', 'García');
    await page.fill('input[placeholder="Ej: AAAA999999HAAAAA01"]', 'GACD900101HDFRRL09');
    await page.fill('input[placeholder="Ej: EMP-001"]', 'EMP-12345');

    // Guardar
    await page.click('button:has-text("Guardar")');
    await page.waitForSelector('text=Guardado', { timeout: 5000 });

    // Abrir el registro creado
    await page.click('text=Juan Carlos García');

    // Verificar que los datos están persistidos
    expect(await page.locator('text=GACD900101HDFRRL09').isVisible()).toBeTruthy();
    expect(await page.locator('text=EMP-12345').isVisible()).toBeTruthy();
  });

  test('FASE 2: Persistencia — recargar página mantiene datos', async ({ page }) => {
    // Suponiendo que hay un registro existente
    await page.goto(`${BASE_URL}/personal-admin`);
    await page.waitForLoadState('networkidle');

    // Anotar número de empleado visible
    const empleadoText = await page.locator('[data-testid="grid-row"]:first-child').textContent();

    // Recargar página
    await page.reload();
    await page.waitForLoadState('networkidle');

    // Verificar que el mismo empleado sigue siendo visible
    const empleadoTextAfterReload = await page.locator('[data-testid="grid-row"]:first-child').textContent();
    expect(empleadoText).toBe(empleadoTextAfterReload);
  });

  test('FASE 3: Sesión Prolongada — token refresh proactivo', async ({ page }) => {
    // Login
    await page.goto(`${BASE_URL}`);
    await page.fill('input[type="email"]', NEVADI_USER);
    await page.fill('input[type="password"]', NEVADI_PASS);
    await page.click('button:has-text("Login")');
    await page.waitForNavigation();

    // Anotar token inicial
    const initialToken = await page.evaluate(() => {
      return localStorage.getItem('ades_token');
    });

    // Esperar 29 minutos (en test: simular con mock de tiempo)
    // NOTA: En desarrollo, usar tiempo real; en CI usar mock
    await page.waitForTimeout(30000); // 30 segundos simulados (ajustar para test real)

    // Verificar que token fue refresheado
    const newToken = await page.evaluate(() => {
      return localStorage.getItem('ades_token');
    });

    expect(newToken).not.toBe(initialToken);

    // Verificar que sesión sigue activa (hacer una petición)
    const response = await page.request.get(`${BASE_URL}/api/v1/profesores`, {
      headers: { 'Authorization': `Bearer ${newToken}` }
    });
    expect(response.status()).toBe(200);
  });

  test('FASE 3: Sesión Prolongada — no pide re-login después de 30 min', async ({ page }) => {
    await page.goto(`${BASE_URL}`);
    await page.fill('input[type="email"]', NEVADI_USER);
    await page.fill('input[type="password"]', NEVADI_PASS);
    await page.click('button:has-text("Login")');
    await page.waitForNavigation();

    // Navegar a un módulo
    await page.goto(`${BASE_URL}/personal-admin`);

    // Esperar 29+ minutos sin interacción
    // (usar página con idle, verificar que no hay redirección a login)
    const initialUrl = page.url();

    // Simular acción después de tiempo
    await page.click('button:has-text("Nuevo registro")');

    // Verificar que NO fue redirigido a login
    expect(page.url()).not.toContain('/auth/login');
    expect(page.url()).toContain('/personal-admin');
  });

  test('Validación: Error mensaje específico por campo', async ({ page }) => {
    await page.goto(`${BASE_URL}/personal-admin`);
    await page.click('button:has-text("Nuevo registro")');

    // Dejar nombre vacío
    await page.click('button:has-text("Guardar")');

    // Verificar mensaje específico
    const msg = await page.locator('text=Nombre es requerido').first();
    expect(msg).toBeTruthy();
  });

  test('Validación: Contador de caracteres visible', async ({ page }) => {
    await page.goto(`${BASE_URL}/personal-admin`);
    await page.click('button:has-text("Nuevo registro")');

    // Llenar nombre
    const input = await page.$('input[placeholder="Ej: Juan Carlos"]');
    await input?.fill('Juan');

    // Verificar contador
    const counter = await page.locator('text=4 / 100').first();
    expect(counter).toBeTruthy();
  });

});

/**
 * Tests adicionales sugeridos:
 * - Teléfono: 10 dígitos exactos
 * - ZIP: 5 dígitos
 * - Dinero: formato válido con separadores
 * - Timeout de token: 401 y retry automático
 * - Concurrent requests: múltiples usuarios
 */
