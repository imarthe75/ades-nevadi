import { test, expect } from '@playwright/test';
import { getRealToken, CUENTAS_REALES } from '../fixtures/real-tokens';

// Hallazgo corregido 2026-07-16 (docs/hallazgos/2026-07-16_auditoria_gaps_no_revisados.md
// #4): authToken se declaraba pero nunca se asignaba (`Authorization: Bearer undefined`
// en todas las requests) — ningún test validaba paginación real, todos fallaban en 401
// antes de tocar la lógica de negocio. Ahora usa un JWT real de Authentik (ADMIN_GLOBAL,
// alcance institucional libre) y un grupo/materia reales con tareas seeded en BD.
test.describe('Paginación — Tareas API', () => {
  const baseUrl = process.env.BASE_URL || 'http://localhost:4200';
  const apiUrl = process.env.API_URL || 'http://localhost:8080';
  let authToken: string;
  const grupoId = '019f4e48-b0c3-7a66-9742-fdab93a4876a';
  const materiaId = '019f4e44-b45e-7ba4-a3ec-e01ebead8c03';

  test.beforeAll(async () => {
    authToken = getRealToken(CUENTAS_REALES.ADMIN_GLOBAL);
  });

  test('GET /api/v1/tareas retorna Page<Map<>> con paginación', async ({ request }) => {
    const response = await request.get(`${apiUrl}/api/v1/tareas`, {
      params: {
        page: 0,
        size: 20,
        grupo_id: grupoId,
        materia_id: materiaId,
      },
      headers: {
        'Authorization': `Bearer ${authToken}`,
        'Content-Type': 'application/json',
      },
    });

    // Validar que endpoint retorna 200 OK
    expect(response.ok()).toBeTruthy();

    const body = await response.json();

    // Validar estructura Page<T>
    expect(body).toHaveProperty('content');
    expect(body).toHaveProperty('totalElements');
    expect(body).toHaveProperty('totalPages');
    expect(body).toHaveProperty('number');
    expect(body).toHaveProperty('size');
    expect(body).toHaveProperty('numberOfElements');
    expect(body).toHaveProperty('empty');
    expect(body).toHaveProperty('first');
    expect(body).toHaveProperty('last');

    // Validar tipos
    expect(Array.isArray(body.content)).toBeTruthy();
    expect(typeof body.totalElements).toBe('number');
    expect(typeof body.number).toBe('number');
    expect(typeof body.size).toBe('number');

    // Validar que size es correcto
    expect(body.size).toBe(20);
    expect(body.content.length).toBeLessThanOrEqual(20);
  });

  test('GET /api/v1/tareas/{id}/entregas retorna Page<Map<>> paginado', async ({ request }) => {
    const tareaId = '019f547e-530d-70a7-8f2b-82bf676f68fc';

    const response = await request.get(`${apiUrl}/api/v1/tareas/${tareaId}/entregas`, {
      params: {
        page: 0,
        size: 20,
      },
      headers: {
        'Authorization': `Bearer ${authToken}`,
        'Content-Type': 'application/json',
      },
    });

    expect(response.ok()).toBeTruthy();

    const body = await response.json();

    // Validar estructura Page<T>
    expect(body).toHaveProperty('content');
    expect(body).toHaveProperty('totalElements');
    expect(body).toHaveProperty('totalPages');

    // Validar que es array
    expect(Array.isArray(body.content)).toBeTruthy();

    // Cada elemento debe tener estructura de entrega
    if (body.content.length > 0) {
      const entrega = body.content[0];
      expect(entrega).toHaveProperty('id');
      expect(entrega).toHaveProperty('estudiante_id');
      expect(entrega).toHaveProperty('estatus_entrega');
    }
  });

  test('Paginación: page query param funciona correctamente', async ({ request }) => {
    // Page 0
    const page0 = await request.get(`${apiUrl}/api/v1/tareas`, {
      params: {
        page: 0,
        size: 10,
        grupo_id: grupoId,
      },
      headers: {
        'Authorization': `Bearer ${authToken}`,
      },
    });

    const body0 = await page0.json();
    expect(body0.number).toBe(0);
    expect(body0.size).toBe(10);
    expect(body0.first).toBeTruthy();

    // Page 1 (si existe)
    if (body0.totalPages > 1) {
      const page1 = await request.get(`${apiUrl}/api/v1/tareas`, {
        params: {
          page: 1,
          size: 10,
          grupo_id: grupoId,
        },
        headers: {
          'Authorization': `Bearer ${authToken}`,
        },
      });

      const body1 = await page1.json();
      expect(body1.number).toBe(1);
      expect(body1.last).toBeTruthy();
    }
  });

  test('Performance: endpoint responde en < 500ms', async ({ request }) => {
    const start = performance.now();

    await request.get(`${apiUrl}/api/v1/tareas`, {
      params: {
        page: 0,
        size: 20,
        grupo_id: grupoId,
      },
      headers: {
        'Authorization': `Bearer ${authToken}`,
      },
    });

    const end = performance.now();
    const duration = end - start;

    expect(duration).toBeLessThan(500);
  });

  test('Default page size es 20', async ({ request }) => {
    const response = await request.get(`${apiUrl}/api/v1/tareas`, {
      params: {
        grupo_id: grupoId,
        // NO pasar page/size → usar defaults
      },
      headers: {
        'Authorization': `Bearer ${authToken}`,
      },
    });

    const body = await response.json();
    expect(body.size).toBe(20); // @PageableDefault(size = 20)
  });
});
