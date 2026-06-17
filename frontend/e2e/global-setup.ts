/**
 * Playwright global setup — obtiene un JWT real de Authentik antes de que corran los tests.
 * El token se escribe en e2e/.auth/token.txt y los tests lo leen via page.addInitScript().
 */
import { execSync } from 'child_process';
import { mkdirSync, existsSync, readFileSync, writeFileSync } from 'fs';
import { resolve } from 'path';
import { request } from '@playwright/test';

const AUTH_DIR   = resolve(__dirname, '.auth');
const TOKEN_FILE = resolve(AUTH_DIR, 'token.txt');

async function globalSetup() {
  mkdirSync(AUTH_DIR, { recursive: true });

  const USER_FILE = resolve(AUTH_DIR, 'user.json');

  // Si ya hay un token válido Y el perfil de usuario, reutilizar
  if (existsSync(TOKEN_FILE) && existsSync(USER_FILE)) {
    const existing = readFileSync(TOKEN_FILE, 'utf-8').trim();
    if (existing.startsWith('ey') && existing.length > 100) {
      try {
        const payload = JSON.parse(Buffer.from(existing.split('.')[1], 'base64').toString());
        const margin = 5 * 60; // 5 min de margen
        if (payload.exp && payload.exp > Math.floor(Date.now() / 1000) + margin) {
          console.log(`[global-setup] Token + perfil reutilizados (${existing.length} chars)\n`);
          return;
        }
        console.log('[global-setup] Token expirado — regenerando…');
      } catch { /* fallthrough to regenerate */ }
    }
  }

  console.log('\n[global-setup] Obteniendo JWT de Authentik…');

  // Genera un JWT fresh vía IDToken.new() con mock request (exp = 365 días)
  const pyCode = [
    "from authentik.providers.oauth2.models import AccessToken, OAuth2Provider",
    "from authentik.providers.oauth2.id_token import IDToken",
    "from authentik.core.models import User",
    "from django.utils import timezone",
    "from datetime import timedelta",
    "from unittest.mock import MagicMock",
    "import secrets",
    "provider = OAuth2Provider.objects.get(client_id='ades-frontend')",
    "user = User.objects.get(username='akadmin')",
    "at = AccessToken(provider=provider, user=user, expires=timezone.now()+timedelta(days=365), auth_time=timezone.now(), token=secrets.token_hex(32))",
    "at.scope = ['openid','email','profile']",
    "at.save()",
    "req = MagicMock()",
    "req.build_absolute_uri.return_value = 'http://authentik-server:9000/application/o/ades-frontend/'",
    "id_tok = IDToken.new(provider=provider, token=at, request=req)",
    "print(id_tok.to_jwt(provider))",
  ].join('; ');

  let token = '';
  try {
    const raw = execSync(
      `docker compose exec -T authentik-server ak shell -c "${pyCode}"`,
      { encoding: 'utf-8', cwd: resolve(__dirname, '..', '..'), timeout: 20_000 }
    );
    token = raw.split('\n').find(l => l.startsWith('ey'))?.trim() ?? '';
  } catch (err) {
    console.warn('[global-setup] Docker exec falló:', String(err).slice(0, 120));
  }

  if (!token) {
    console.warn('[global-setup] ⚠  Sin token de Authentik — tests de UI correrán sin autenticación real.');
    writeFileSync(TOKEN_FILE, '', 'utf-8');
    return;
  }

  writeFileSync(TOKEN_FILE, token, 'utf-8');
  console.log(`[global-setup] ✓ Token guardado (${token.length} chars).`);

  // Obtener perfil de usuario para que roleGuard pueda calcular nivelAcceso
  try {
    const ctx = await request.newContext();
    const res = await ctx.get('https://ades.setag.mx/api/v1/auth/me', {
      headers: { Authorization: `Bearer ${token}` },
    });
    if (res.ok()) {
      const me = await res.json();
      // Mapear a la interfaz UsuarioMe del frontend
      const usuarioMe = {
        id: me.sub ?? 'test-admin',
        nombre_usuario: me.nombre_usuario ?? me.sub ?? 'admin',
        email_institucional: me.email ?? 'admin@institutonevadi.edu.mx',
        persona_id: me.sub ?? 'test-admin',
        nombre_completo: me.nombre_completo ?? 'Administrador Global',
        rol: Array.isArray(me.roles) ? me.roles[0] : (me.rol ?? 'ADMIN_GLOBAL'),
        nivel_acceso: me.nivel_acceso ?? 0,
        plantel_id: me.plantel_id ?? null,
        nivel_educativo_id: me.nivel_educativo_id ?? null,
      };
      writeFileSync(resolve(AUTH_DIR, 'user.json'), JSON.stringify(usuarioMe), 'utf-8');
      console.log(`[global-setup] ✓ Perfil guardado (nivel_acceso: ${usuarioMe.nivel_acceso}).\n`);
    }
    await ctx.dispose();
  } catch (err) {
    console.warn('[global-setup] No se pudo obtener /auth/me:', String(err).slice(0, 80));
    // Perfil admin de fallback
    const fallback = {
      id: 'admin', nombre_usuario: 'admin', email_institucional: 'admin@institutonevadi.edu.mx',
      persona_id: 'admin', nombre_completo: 'Administrador Global', rol: 'ADMIN_GLOBAL',
      nivel_acceso: 0, plantel_id: null, nivel_educativo_id: null,
    };
    writeFileSync(resolve(AUTH_DIR, 'user.json'), JSON.stringify(fallback), 'utf-8');
  }
}

export default globalSetup;
