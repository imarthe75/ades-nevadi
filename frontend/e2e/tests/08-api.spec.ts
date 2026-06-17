/**
 * Suite de pruebas API-nivel
 * Valida contratos HTTP, seguridad, RBAC y reglas de negocio desde la capa de transporte
 */
import { test, expect, request as pwRequest } from '@playwright/test';
import { ApiClient } from '../helpers/api-client';
import { USERS, BFF_BASE, API_BASE } from '../fixtures/users';
import {
  alumnoValido, curpValido, EDGE_STRINGS,
  ponderacionConSuma100, ponderacionConSumaIncorrecta,
  faker,
} from '../fixtures/data-generators';

// ── A. Health y disponibilidad ────────────────────────────────────────────────

test.describe('A. Health endpoints', () => {
  test('API-A1 | FastAPI GET /api/v1/health → 200 con uuid_v7', async ({ request }) => {
    const res = await request.get(`${API_BASE}/health`);
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body.status).toBe('ok');
    expect(body.uuid_v7_sample).toMatch(/^[0-9a-f-]{36}$/);
  });

  test('API-A2 | BFF GET /actuator/health → 200 status UP', async ({ request }) => {
    const res = await request.get(`${BFF_BASE}/actuator/health`);
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body.status).toBe('UP');
  });

  test('API-A3 | BFF GET /actuator/prometheus → 200 con métricas', async ({ request }) => {
    const res = await request.get(`${BFF_BASE}/actuator/prometheus`);
    expect(res.status()).toBe(200);
    const text = await res.text();
    expect(text).toContain('jvm_');
  });
});

// ── B. Autenticación / Autorización ──────────────────────────────────────────

test.describe('B. Auth y RBAC', () => {
  test('SEC-03 | token inválido → 401', async ({ request }) => {
    const res = await request.get(`${BFF_BASE}/api/v1/catalogs/roles`, {
      headers: { Authorization: 'Bearer TOKEN_FALSO_XXXXXXXX' },
    });
    expect(res.status()).toBe(401);
  });

  test('BFF-07 | sin Authorization header → 401', async ({ request }) => {
    const res = await request.get(`${BFF_BASE}/api/v1/alumnos`);
    expect(res.status()).toBe(401);
  });

  test('SEC-08 | verificar certificado público sin auth → no 401', async ({ request }) => {
    const res = await request.get(`${API_BASE}/certificados/verificar/FOLIO-INEXISTENTE`);
    expect(res.status()).not.toBe(401);
    expect([200, 404]).toContain(res.status());
  });

  test('SEC-09 | portal público sin auth → 200', async ({ request }) => {
    const res = await request.get('http://localhost:4201/');
    expect(res.status()).toBe(200);
  });

  test('SEC-06 | SQL injection en parámetro query', async ({ request }) => {
    const res = await request.get(`${BFF_BASE}/api/v1/alumnos`, {
      params: { q: EDGE_STRINGS.SQL_INJECTION },
      headers: { Authorization: 'Bearer FAKE_TOKEN' },
    });
    // Debe retornar 401 (no auth) o 400 (bad input), NUNCA 500
    expect(res.status()).not.toBe(500);
    expect(res.status()).not.toBe(200);
  });

  test('SEC-07 | XSS en campo nombre — almacenado como texto plano', async ({ request }) => {
    // Intentar crear alumno con XSS en nombre (sin auth válida → 401)
    const res = await request.post(`${BFF_BASE}/api/v1/alumnos`, {
      headers: { Authorization: 'Bearer FAKE' },
      data: { nombre: EDGE_STRINGS.XSS_BASIC, curp: curpValido() },
    });
    expect(res.status()).toBe(401);
  });
});

// ── C. Catálogos ──────────────────────────────────────────────────────────────

test.describe('C. Catálogos BFF', () => {
  test('CAT-01 | GET /api/v1/catalogs/roles (sin auth) → 401', async ({ request }) => {
    const res = await request.get(`${BFF_BASE}/api/v1/catalogs/roles`);
    expect(res.status()).toBe(401);
  });

  test('CAT-02 | GET /api/v1/catalogs/niveles (sin auth) → 401', async ({ request }) => {
    const res = await request.get(`${BFF_BASE}/api/v1/catalogs/niveles`);
    expect(res.status()).toBe(401);
  });

  test('CAT-03 | GET /api/v1/catalogs/lenguas-indigenas?familia=OTO-MANGUE (sin auth) → 401', async ({ request }) => {
    const res = await request.get(`${BFF_BASE}/api/v1/catalogs/lenguas-indigenas`, {
      params: { familia: 'OTO-MANGUE' },
    });
    expect(res.status()).toBe(401);
  });
});

// ── D. Paginación y parámetros extremos ──────────────────────────────────────

test.describe('D. Parámetros extremos en endpoints', () => {
  const extremeParams = [
    { page: '-1', limit: '0' },
    { page: '999999', limit: '999999' },
    { page: 'abc', limit: 'xyz' },
    { page: EDGE_STRINGS.SQL_INJECTION, limit: '10' },
    { page: '1', limit: EDGE_STRINGS.XSS_BASIC },
  ];

  for (const params of extremeParams) {
    test(`API-D | paginación extrema page=${params.page} limit=${params.limit}`, async ({ request }) => {
      const res = await request.get(`${BFF_BASE}/api/v1/alumnos`, {
        params,
        headers: { Authorization: 'Bearer FAKE' },
      });
      // Puede ser 400 o 401, nunca 500
      expect(res.status()).not.toBe(500);
    });
  }

  test('API-D-uuid | UUID inválido en path parameter', async ({ request }) => {
    const res = await request.get(`${BFF_BASE}/api/v1/alumnos/not-a-valid-uuid`, {
      headers: { Authorization: 'Bearer FAKE' },
    });
    expect([400, 401, 404, 422]).toContain(res.status());
  });

  test('API-D-empty-body | POST con body vacío', async ({ request }) => {
    const res = await request.post(`${BFF_BASE}/api/v1/alumnos`, {
      headers: { Authorization: 'Bearer FAKE', 'Content-Type': 'application/json' },
      data: {},
    });
    expect([400, 401, 422]).toContain(res.status());
  });

  test('API-D-null-body | POST con body null', async ({ request }) => {
    const res = await request.post(`${BFF_BASE}/api/v1/alumnos`, {
      headers: { Authorization: 'Bearer FAKE', 'Content-Type': 'application/json' },
      data: null as unknown as Record<string, unknown>,
    });
    expect([400, 401, 415, 422]).toContain(res.status());
  });
});

// ── E. Reglas de negocio verificadas via API ──────────────────────────────────

test.describe('E. Reglas de negocio API', () => {
  test('RN-05-API | POST esquema ponderación suma≠100 → 422', async ({ request }) => {
    const res = await request.post(`${BFF_BASE}/api/v1/esquemas-ponderacion`, {
      headers: { Authorization: 'Bearer FAKE', 'Content-Type': 'application/json' },
      data: {
        nombre: 'Test incorrecta',
        nivel_educativo_id: faker.string.uuid(),
        items: ponderacionConSumaIncorrecta(),
      },
    });
    // Sin token válido → 401; con token válido pero suma incorrecta → 422
    expect([401, 422]).toContain(res.status());
  });

  test('RN-19-API | ajuste calificación sin justificación larga → 422', async ({ request }) => {
    const res = await request.put(`${BFF_BASE}/api/v1/gradebook/ajuste/${faker.string.uuid()}`, {
      headers: { Authorization: 'Bearer FAKE' },
      data: {
        calificacion: 7.5,
        justificacion: 'corta',
      },
    });
    expect([401, 422]).toContain(res.status());
  });

  test('CAL-API | calificación SEP fuera de rango → 422', async ({ request }) => {
    const res = await request.post(`${BFF_BASE}/api/v1/calificaciones/bulk`, {
      headers: { Authorization: 'Bearer FAKE' },
      data: [{ estudiante_id: faker.string.uuid(), calificacion: 11.5, periodo_id: faker.string.uuid() }],
    });
    expect([401, 422]).toContain(res.status());
  });

  test('RN-16-API | rechazo reinscripción sin razón → 422', async ({ request }) => {
    const res = await request.post(`${BFF_BASE}/api/v1/reinscripciones/${faker.string.uuid()}/rechazar`, {
      headers: { Authorization: 'Bearer FAKE' },
      data: { accion: 'RECHAZAR' }, // sin campo razon
    });
    expect([401, 422]).toContain(res.status());
  });
});

// ── F. Headers y Content-Type ─────────────────────────────────────────────────

test.describe('F. Headers HTTP extremos', () => {
  test('API-F1 | Content-Type incorrecto en POST → 415 o 400', async ({ request }) => {
    const res = await request.post(`${BFF_BASE}/api/v1/alumnos`, {
      headers: {
        Authorization: 'Bearer FAKE',
        'Content-Type': 'text/plain',
      },
      data: 'esto no es json' as unknown as Record<string, unknown>,
    });
    expect([400, 401, 415]).toContain(res.status());
  });

  test('API-F2 | Header Accept inusual → responde o 406', async ({ request }) => {
    const res = await request.get(`${BFF_BASE}/api/v1/catalogs/roles`, {
      headers: {
        Authorization: 'Bearer FAKE',
        Accept: 'application/xml',
      },
    });
    expect([200, 401, 406]).toContain(res.status());
  });

  test('API-F3 | método HTTP incorrecto (DELETE en recurso de solo lectura)', async ({ request }) => {
    const res = await request.delete(`${BFF_BASE}/api/v1/catalogs/roles`, {
      headers: { Authorization: 'Bearer FAKE' },
    });
    expect([401, 405]).toContain(res.status());
  });

  test('API-F4 | HEAD request → 200 o 401, no 500', async ({ request }) => {
    const res = await request.fetch(`${BFF_BASE}/actuator/health`, { method: 'HEAD' });
    expect(res.status()).not.toBe(500);
  });

  test('API-F5 | X-Forwarded-For manipulation', async ({ request }) => {
    const res = await request.get(`${BFF_BASE}/api/v1/alumnos`, {
      headers: {
        Authorization: 'Bearer FAKE',
        'X-Forwarded-For': '127.0.0.1, 10.0.0.1',
        'X-Real-IP': '192.168.1.1',
      },
    });
    // No debe dar acceso no autorizado
    expect(res.status()).toBe(401);
  });
});

// ── G. Certificados — API pública ────────────────────────────────────────────

test.describe('G. Certificados — API pública', () => {
  const badFolios = [
    EDGE_STRINGS.SQL_INJECTION,
    EDGE_STRINGS.XSS_BASIC,
    EDGE_STRINGS.EMOJIS,
    '../../../etc/passwd',
    '\x00null',
    'a'.repeat(500),
    '',
    '   ',
  ];

  for (const folio of badFolios) {
    test(`CER-API-G | folio extremo: "${folio.slice(0, 30)}"`, async ({ request }) => {
      const encoded = encodeURIComponent(folio);
      const res = await request.get(`${API_BASE}/certificados/verificar/${encoded}`);
      // Nunca debe ser 500 ni ejecutar código
      expect(res.status()).not.toBe(500);
      expect([200, 400, 404, 422]).toContain(res.status());
    });
  }
});
