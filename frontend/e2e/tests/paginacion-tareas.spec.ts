import { test, expect } from '@playwright/test';

test.describe('Paginación — Tareas API', () => {
  const baseUrl = process.env.BASE_URL || 'http://localhost:4200';
  const apiUrl = process.env.API_URL || 'http://localhost:8080';
  let authToken: string;
  let grupoId: string;
  let materiaId: string;

  test.beforeAll(async () => {
    // Setup: obtener token y IDs necesarios (asumiendo que ya existen en BD)
    // En prueba real, esto vendría de fixtures o setup global
  });

  test('GET /api/v1/tareas retorna Page<Map<>> con paginación', async ({ request }) => {
    // NOTA: Este test requiere autenticación válida
    // Para propósitos de demostración, usamos mock de respuesta esperada

    const response = await request.get(`${apiUrl}/api/v1/tareas`, {
      params: {
        page: 0,
        size: 20,
        grupo_id: 'mock-grupo-id',
        materia_id: 'mock-materia-id',
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
    const tareaId = 'mock-tarea-id';

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
        grupo_id: 'mock-id',
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
          grupo_id: 'mock-id',
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
        grupo_id: 'mock-id',
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
        grupo_id: 'mock-id',
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
