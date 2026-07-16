import { execSync } from 'child_process';
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'fs';
import { resolve } from 'path';

/**
 * Hallazgo corregido 2026-07-16 (docs/hallazgos/
 * 2026-07-16_auditoria_gaps_no_revisados.md #4): 06-edge-cases.spec.ts y
 * paginacion-tareas.spec.ts usaban tokens literales falsos ('docente-plantel-1-token',
 * etc.) o una variable authToken nunca asignada — las aserciones de seguridad
 * (403 esperado) nunca ejercitaban el camino real de autorización, dando cobertura
 * "fantasma" a los fixes BOLA/BFLA de las sesiones 07-15/07-16.
 *
 * Este helper reutiliza el mismo mecanismo que global-setup.ts (IDToken.new() vía
 * `ak shell`) para emitir un JWT real de Authentik, pero parametrizado por email —
 * permite generar tokens para cualquier cuenta ADES YA EXISTENTE en Authentik.
 *
 * Deliberadamente NO crea cuentas nuevas: fixtures/users.ts describe usuarios de
 * prueba (docente.primaria@test.ades, etc.) que NUNCA se aprovisionaron en el
 * Authentik de producción (verificado 2026-07-16, ver bitácora en .agent/STATE.md)
 * — crear cuentas ahí es una decisión de producto/ops fuera de alcance de un fix de
 * testing. En su lugar, usa las cuentas `test.*@institutonevadi.edu.mx` que SÍ
 * existen y están activas en el Authentik real (confirmado por consulta directa).
 * Si el email no existe, retorna '' — el llamador debe manejarlo (test.skip).
 */

const AUTH_DIR = resolve(__dirname, '..', '.auth');
const REPO_ROOT = resolve(__dirname, '..', '..', '..');

function tokenFile(email: string): string {
  return resolve(AUTH_DIR, `token-${email.replace(/[^a-zA-Z0-9]/g, '_')}.txt`);
}

function isTokenFresh(token: string): boolean {
  if (!token.startsWith('ey') || token.length < 100) return false;
  try {
    const payload = JSON.parse(Buffer.from(token.split('.')[1], 'base64').toString());
    return !!payload.exp && payload.exp > Math.floor(Date.now() / 1000) + 300;
  } catch {
    return false;
  }
}

export function getRealToken(email: string): string {
  mkdirSync(AUTH_DIR, { recursive: true });
  const file = tokenFile(email);

  if (existsSync(file)) {
    const existing = readFileSync(file, 'utf-8').trim();
    if (isTokenFresh(existing)) return existing;
  }

  const pyCode = [
    'from authentik.providers.oauth2.models import AccessToken, OAuth2Provider',
    'from authentik.providers.oauth2.id_token import IDToken',
    'from authentik.core.models import User',
    'from django.utils import timezone',
    'from datetime import timedelta',
    'from unittest.mock import MagicMock',
    'import secrets',
    "provider = OAuth2Provider.objects.get(client_id='ades-frontend')",
    `user = User.objects.get(email='${email}')`,
    "at = AccessToken(provider=provider, user=user, expires=timezone.now()+timedelta(hours=6), auth_time=timezone.now(), token=secrets.token_hex(32))",
    "at.scope = ['openid','email','profile']",
    'at.save()',
    'req = MagicMock()',
    "req.build_absolute_uri.return_value = 'http://authentik-server:9000/application/o/ades-frontend/'",
    'id_tok = IDToken.new(provider=provider, token=at, request=req)',
    'print(id_tok.to_jwt(provider))',
  ].join('; ');

  let token = '';
  try {
    const raw = execSync(
      `docker compose exec -T authentik-server ak shell -c "${pyCode}"`,
      { encoding: 'utf-8', cwd: REPO_ROOT, timeout: 20_000 }
    );
    token = raw.split('\n').find((l) => l.startsWith('ey'))?.trim() ?? '';
  } catch (err) {
    console.warn(`[real-tokens] No se pudo generar token para ${email}:`, String(err).slice(0, 150));
  }

  if (token) writeFileSync(file, token, 'utf-8');
  return token;
}

/** Cuentas reales confirmadas activas en Authentik (2026-07-16) usadas por los E2E de RBAC. */
export const CUENTAS_REALES = {
  ADMIN_GLOBAL: 'admin@institutonevadi.edu.mx',
  ADMIN_PLANTEL_METEPEC: 'test.admin_plantel@institutonevadi.edu.mx',
  COORDINADOR_METEPEC: 'test.coordinador_academico@institutonevadi.edu.mx',
  DOCENTE_METEPEC: 'test.docente@institutonevadi.edu.mx',
} as const;
