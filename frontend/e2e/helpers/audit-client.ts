/**
 * audit-client.ts
 *
 * Helper para verificar la integridad de auditoría vía API directa al BFF.
 *
 * NOTA: Usa 127.0.0.1 en lugar de localhost para evitar resolución IPv6
 * (ECONNREFUSED ::1:8080 — Playwright en Node.js puede intentar IPv6 primero).
 */
import { APIRequestContext } from '@playwright/test';

// IPv4 explícito — evita ECONNREFUSED ::1:8080 en Node.js
// process.env no disponible en el contexto TS (no hay @types/node en e2e tsconfig)
const BFF_HOST = 'http://127.0.0.1:8080';

export interface AuditableResource {
  id: string;
  row_version: number;
  fecha_creacion: string | null;
  fecha_modificacion: string | null;
  usuario_creacion: string | null;
  usuario_modificacion: string | null;
}

/**
 * Obtiene un recurso del BFF y extrae sus campos de auditoría.
 * Retorna null si la conexión falla (servicio no disponible).
 */
export async function getAuditFields(
  request: APIRequestContext,
  token: string,
  endpoint: string,   // e.g. '/api/v1/alumnos/{id}'
): Promise<AuditableResource | null> {
  const res = await request.get(`${BFF_HOST}${endpoint}`, {
    headers: { Authorization: `Bearer ${token}` },
  }).catch(() => null);

  if (!res || !res.ok()) return null;

  const body = await res.json().catch(() => null);
  if (!body) return null;

  return {
    id:                    body.id,
    row_version:           body.row_version ?? body.rowVersion ?? -1,
    fecha_creacion:        body.fecha_creacion ?? body.fechaCreacion ?? null,
    fecha_modificacion:    body.fecha_modificacion ?? body.fechaModificacion ?? null,
    usuario_creacion:      body.usuario_creacion ?? body.usuarioCreacion ?? null,
    usuario_modificacion:  body.usuario_modificacion ?? body.usuarioModificacion ?? null,
  };
}


/**
 * Verifica que row_version haya incrementado exactamente en `delta` unidades.
 * Útil para detectar el bug de triggers dobles (Hallazgo B: +2 en vez de +1).
 */
export function assertRowVersionIncrement(
  before: AuditableResource,
  after: AuditableResource,
  delta = 1,
  label = 'recurso',
): void {
  const actual   = after.row_version - before.row_version;
  if (actual !== delta) {
    throw new Error(
      `[audit-client] ${label} row_version debería incrementar en ${delta}, ` +
      `pero incrementó en ${actual} (antes=${before.row_version}, después=${after.row_version}). ` +
      `Posible trigger duplicado (Hallazgo B).`
    );
  }
}

/**
 * Verifica que los campos de auditoría mínimos estén presentes y no nulos.
 */
export function assertAuditFieldsPresent(resource: AuditableResource, label = 'recurso'): void {
  if (resource.row_version < 1) {
    throw new Error(`[audit-client] ${label}: row_version inválido (${resource.row_version})`);
  }
  if (!resource.fecha_creacion) {
    throw new Error(`[audit-client] ${label}: fecha_creacion está null`);
  }
}
