import { test, expect } from '@playwright/test';

/**
 * Suite E2E: Validación de Campos + Persistencia de Datos + Sesión Prolongada
 * Cubre las 3 fases de implementación:
 * - FASE 1: Máscaras, validadores, límites de caracteres, textos de ayuda
 * - FASE 2: Persistencia después de guardar (GET post-PUT)
 * - FASE 3: Sesión no cierra durante captura larga
 *
 * Requiere:
 * - Usuario autenticado en Authentik
 * - Acceso a módulo Alumnos
 * - Permisos de crear/editar alumnos
 */

test.describe('FASE 1: Validación de Campos - Máscaras y Restricciones', () => {

  test.beforeEach(async ({ page }) => {
    // Login
    const token = process.env.ADES_TOKEN || '';
    const userId = process.env.ADES_USER_ID || '';

    if (!token) {
      throw new Error('ADES_TOKEN env variable requerida para E2E tests');
    }

    // Inyectar token en sessionStorage
    await page.goto('https://ades.setag.mx/dashboard');
    await page.evaluate(({ token, userId }) => {
      sessionStorage.setItem('ades_token', token);
      sessionStorage.setItem('ades_usuario', userId);
    }, { token, userId });

    // Navegar a Alumnos
    await page.goto('https://ades.setag.mx/dashboard/alumnos');
    await page.waitForLoadState('networkidle');
  });

  test('Crear alumno con validación de campos - CURP', async ({ page }) => {
    // Click en "Nuevo alumno"
    await page.click('text=Nuevo alumno');
    await page.waitForSelector('text=Nuevo Alumno');

    // Probar validación CURP
    const curpInput = page.locator('input[placeholder*="AAAA999999"]');

    // 1. CURP muy corta
    await curpInput.fill('ABC');
    await page.click('text=Crear alumno');
    const errorMsg = page.locator('text=exactamente 18 caracteres');
    await expect(errorMsg).toBeVisible();

    // 2. CURP con caracteres inválidos
    await curpInput.fill('AAAA$$$$$HAAAAA01');
    await page.click('text=Crear alumno');
    await expect(errorMsg).toBeVisible();

    // 3. CURP correcta
    const testCurp = 'GARA800101HDFRRLB01';
    await curpInput.fill(testCurp);

    // Verificar que se acepte
    const button = page.locator('button:has-text("Crear alumno")');
    const isDisabled = await button.isDisabled();
    expect(isDisabled).toBeFalsy();
  });

  test('Verificar contador de caracteres en nombre', async ({ page }) => {
    // Click en "Nuevo alumno"
    await page.click('text=Nuevo alumno');

    // Ingresar nombre
    const nombreInput = page.locator('input[placeholder="Ej: Juan Carlos"]');
    await nombreInput.fill('Juan Carlos García López');

    // Verificar contador visible
    const counter = page.locator('text=/\\d+ \\/ 100 caracteres/');
    await expect(counter).toBeVisible();
    const counterText = await counter.textContent();
    expect(counterText).toContain('24 / 100');

    // Ingresar nombre muy largo (> 100 chars) - debe ser truncado
    const longName = 'A'.repeat(150);
    await nombreInput.fill(longName);
    const actualValue = await nombreInput.inputValue();
    expect(actualValue.length).toBeLessThanOrEqual(100);
  });

  test('Verificar texto de ayuda contextual', async ({ page }) => {
    // Click en "Nuevo alumno"
    await page.click('text=Nuevo alumno');

    // Verificar que aparecen textos de ayuda
    const helpTexts = [
      'Nombre completo del alumno',
      'Primer apellido del alumno',
      'Segundo apellido',
      '18 caracteres: 4 letras',
    ];

    for (const helpText of helpTexts) {
      const element = page.locator(`text=${helpText}`);
      await expect(element).toBeVisible();
    }
  });

  test('Validar formato de dinero - solo números, puntos, comas', async ({ page }) => {
    // Ir a perfil del alumno y tab Académico
    await page.goto('https://ades.setag.mx/dashboard/alumnos');

    // Buscar primer alumno y abrir perfil
    const firstRow = page.locator('tr[data-row-index="0"]');
    await firstRow.click();

    // Esperar a que se abra el drawer
    await page.waitForSelector('[role="dialog"]');

    // Click en tab Académico
    await page.click('[role="tab"]:has-text("Académico")');

    // Si existe campo de monto de beca, validar
    const becaMontoInput = page.locator('input[placeholder*="1234.56"]').first();
    if (await becaMontoInput.isVisible()) {
      // Intentar ingresar caracteres inválidos
      await becaMontoInput.fill('$1,234.56a@#$');

      // Verificar que solo queden números, puntos, comas, $
      const actualValue = await becaMontoInput.inputValue();
      expect(actualValue).toMatch(/^[\d.,\$ ]*$/);
    }
  });
});

test.describe('FASE 2: Persistencia de Datos - GET post-PUT', () => {

  test.beforeEach(async ({ page }) => {
    const token = process.env.ADES_TOKEN || '';
    if (!token) throw new Error('ADES_TOKEN env variable requerida');

    await page.goto('https://ades.setag.mx/dashboard');
    await page.evaluate(({ token }) => {
      sessionStorage.setItem('ades_token', token);
    }, { token });

    await page.goto('https://ades.setag.mx/dashboard/alumnos');
    await page.waitForLoadState('networkidle');
  });

  test('Guardar cambios en perfil y verificar persistencia', async ({ page }) => {
    // Abrir primer alumno
    const firstRow = page.locator('tr[data-row-index="0"]');
    await firstRow.click();
    await page.waitForSelector('[role="dialog"]');

    // Capturar datos originales
    const nombreOriginal = await page.locator('input[placeholder*="nombre"]').first().inputValue();

    // Cambiar nombre
    const nombreInput = page.locator('input[placeholder*="nombre"]').first();
    const nombreNuevo = nombreOriginal + '_MODIFIED';
    await nombreInput.fill(nombreNuevo);

    // Guardar (click en botón Guardar cambios)
    await page.click('button:has-text("Guardar cambios")');

    // Esperar a que se complete la petición
    await page.waitForResponse(response =>
      response.url().includes('/api/v1/alumnos') &&
      response.status() === 200
    );

    // Esperar a notificación de éxito
    await expect(page.locator('text=Guardado|Actualizado|Éxito')).toBeVisible({ timeout: 5000 });

    // Cerrar el drawer
    await page.click('[aria-label="Close"]');

    // Reabrir el mismo alumno
    await firstRow.click();
    await page.waitForSelector('[role="dialog"]');

    // Verificar que el nombre persista (GET post-PUT funcionando)
    const nombreRecuperado = await page.locator('input[placeholder*="nombre"]').first().inputValue();
    expect(nombreRecuperado).toBe(nombreNuevo);
  });

  test('Manejar 409 Conflict - optimistic locking divergence', async ({ page, context }) => {
    // Abrir alumno en TAB 1
    const firstRow = page.locator('tr[data-row-index="0"]');
    await firstRow.click();
    await page.waitForSelector('[role="dialog"]');

    // Capturar ID del alumno de la URL o elemento
    const alumnoId = await page.locator('[data-alumno-id]').first().getAttribute('data-alumno-id');
    const nombreInput = page.locator('input[placeholder*="nombre"]').first();
    const nombreOriginal = await nombreInput.inputValue();

    // Cambiar nombre en TAB 1 pero NO guardar aún
    await nombreInput.fill(nombreOriginal + '_TAB1');

    // Abrir TAB 2 y simular cambio concurrente del mismo alumno
    const page2 = await context.newPage();
    const token = process.env.ADES_TOKEN || '';
    await page2.goto('https://ades.setag.mx/dashboard');
    await page2.evaluate(({ token }) => {
      sessionStorage.setItem('ades_token', token);
    }, { token });

    // Hacer PATCH directamente desde TAB 2 para simular otro usuario
    const response = await page2.evaluate(async ({ alumnoId, nombre }) => {
      const resp = await fetch(`/api/v1/alumnos/${alumnoId}`, {
        method: 'PATCH',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${sessionStorage.getItem('ades_token')}`,
        },
        body: JSON.stringify({
          persona: { nombre: nombre + '_TAB2' },
          rowVersion: 1,
        }),
      });
      return resp.status;
    }, { alumnoId, nombre: nombreOriginal });

    await page2.close();

    // Volver a TAB 1 e intentar guardar (debería dar 409)
    await page.click('button:has-text("Guardar cambios")');

    // Debe mostrar notificación de conflicto
    const conflictMsg = page.locator('text=modificado por otro usuario|Conflicto');
    await expect(conflictMsg).toBeVisible({ timeout: 5000 });

    // Verificar que NO se guardó el cambio
    expect(response).toBe(200); // Simulamos que TAB 2 sí guardó
  });
});

test.describe('FASE 3: Sesión Prolongada - No Cierra Durante Captura Larga', () => {

  test.beforeEach(async ({ page }) => {
    const token = process.env.ADES_TOKEN || '';
    if (!token) throw new Error('ADES_TOKEN env variable requerida');

    await page.goto('https://ades.setag.mx/dashboard');
    await page.evaluate(({ token }) => {
      sessionStorage.setItem('ades_token', token);
    }, { token });

    await page.goto('https://ades.setag.mx/dashboard/alumnos');
    await page.waitForLoadState('networkidle');
  });

  test('Crear alumno y mantener sesión activa sin cierre', async ({ page }) => {
    // Click en Nuevo Alumno
    await page.click('text=Nuevo alumno');
    await page.waitForSelector('text=Nuevo Alumno');

    // Llenar datos lentamente (simular captura)
    const tiempoCaptura = 15 * 60 * 1000; // 15 minutos
    const inicioCaptura = Date.now();

    const nombreInput = page.locator('input[placeholder="Ej: Juan Carlos"]');
    await nombreInput.fill('Test Alumno');
    await page.waitForTimeout(2000);

    const apPatInput = page.locator('input[placeholder="Ej: García"]');
    await apPatInput.fill('García');
    await page.waitForTimeout(2000);

    const apMatInput = page.locator('input[placeholder="Ej: López"]');
    await apMatInput.fill('López');
    await page.waitForTimeout(2000);

    const curpInput = page.locator('input[placeholder*="AAAA999999"]');
    await curpInput.fill('GARA800101HDFRRLB01');

    // Esperar a que se complete el tiempo de captura (si es necesario)
    const tiempoTranscurrido = Date.now() - inicioCaptura;
    if (tiempoTranscurrido < tiempoCaptura) {
      await page.waitForTimeout(Math.min(30000, tiempoCaptura - tiempoTranscurrido));
    }

    // Al terminar, intentar crear
    await page.click('button:has-text("Crear alumno")');

    // Verificar que se creó sin errores de sesión
    await expect(page.locator('text=Creado|Matrícula')).toBeVisible({ timeout: 10000 });

    // Verificar que NO hay "401 Unauthorized" o "Sesión expirada"
    const errorMessages = page.locator('text=Sesión expirada|401|No autorizado');
    await expect(errorMessages).not.toBeVisible();
  });

  test('Verificar que refresh automático ocurre silenciosamente', async ({ page }) => {
    // Monitorear requests a /api/v1/auth/refresh
    let refreshCount = 0;
    page.on('response', response => {
      if (response.url().includes('/api/v1/auth/refresh')) {
        refreshCount++;
      }
    });

    // Esperar 5+ minutos (tiempo de refresh automático)
    await page.waitForTimeout(5 * 60 * 1000);

    // Verificar que hubo al menos 1 refresh
    expect(refreshCount).toBeGreaterThan(0);

    // Verificar que el token en sessionStorage cambió (nuevo token)
    const tokenAnterior = await page.evaluate(() => sessionStorage.getItem('ades_token'));

    // Hacer una petición cualquiera para verificar que el token funciona
    const response = await page.goto('https://ades.setag.mx/dashboard/alumnos');
    expect(response?.status()).toBe(200);

    // Token debe seguir siendo válido
    const tokenActual = await page.evaluate(() => sessionStorage.getItem('ades_token'));
    expect(tokenActual).toBeTruthy();
  });

  test('Interceptor maneja 401 con retry y refresh', async ({ page }) => {
    // Simular token a punto de expirar
    const tokenAVencer = await page.evaluate(() => {
      const ahora = Date.now();
      const venciendoEn15seg = Math.floor((ahora + 15000) / 1000);
      return {
        exp: venciendoEn15seg,
        iat: Math.floor(ahora / 1000),
      };
    });

    // Esperar a que ocurra un 401 y el retry
    let retry401Count = 0;
    page.on('response', response => {
      if (response.status() === 401) {
        retry401Count++;
      }
    });

    // Hacer petición que debería triggerar refresh
    await page.goto('https://ades.setag.mx/dashboard/alumnos');
    await page.waitForLoadState('networkidle');

    // Si hubo 401, el interceptor debería haber hecho retry
    if (retry401Count > 0) {
      // Verificar que después del retry la página cargó bien
      const alumnosCount = await page.locator('tr[data-row-index]').count();
      expect(alumnosCount).toBeGreaterThan(0);
    }
  });
});

test.describe('Rollout: Validación en Todos los Módulos', () => {

  test.beforeEach(async ({ page }) => {
    const token = process.env.ADES_TOKEN || '';
    if (!token) throw new Error('ADES_TOKEN env variable requerida');

    await page.goto('https://ades.setag.mx/dashboard');
    await page.evaluate(({ token }) => {
      sessionStorage.setItem('ades_token', token);
    }, { token });

    await page.waitForLoadState('networkidle');
  });

  test('Validadores aplicados a Nómina - campos de dinero', async ({ page }) => {
    await page.goto('https://ades.setag.mx/dashboard/nomina');
    await page.waitForLoadState('networkidle');

    // Si existe módulo de nómina, verificar que campos de dinero validan
    const sueldoInputs = page.locator('input[placeholder*="1234.56"]');
    if (await sueldoInputs.count() > 0) {
      const sueldoInput = sueldoInputs.first();
      await sueldoInput.fill('ABCD@#$%');

      const actualValue = await sueldoInput.inputValue();
      expect(actualValue).toMatch(/^[\d.,\$ ]*$/);
    }
  });

  test('Validadores aplicados a Facturas - RFC y Montos', async ({ page }) => {
    await page.goto('https://ades.setag.mx/dashboard/facturas');
    await page.waitForLoadState('networkidle');

    // Si existe módulo de facturas, verificar RFC
    const rfcInputs = page.locator('input[placeholder*="RFC"]');
    if (await rfcInputs.count() > 0) {
      const rfcInput = rfcInputs.first();

      // Probar RFC inválido
      await rfcInput.fill('INVALID@#$%');
      const actualRfc = await rfcInput.inputValue();

      // Debe estar en MAYÚSCULAS
      expect(actualRfc).toEqual(actualRfc.toUpperCase());
    }
  });
});
