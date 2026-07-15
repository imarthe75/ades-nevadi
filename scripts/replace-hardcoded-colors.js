#!/usr/bin/env node
/**
 * replace-hardcoded-colors.js — Fase D7 (deuda de frontend, cross-check Antigravity
 * 2026-07-15, docs/hallazgos/2026-07-14_analisis_auditoria_antigravity_y_plan.md).
 *
 * Reemplaza usos de colores hexadecimales hardcodeados en frontend/src/app por la
 * variable CSS del sistema de diseño que resuelve EXACTAMENTE al mismo valor —
 * cero cambio visual, solo mantenibilidad. Deliberadamente NO toca:
 *   - Blancos/negros/grises acromáticos (#FFF, #000, #BBB, etc.) — no tienen un token
 *     semántico único correcto, mapear cualquiera de ellos sería una decisión de
 *     diseño, no una sustitución mecánica.
 *   - Cualquier hex sin un token EXACTO definido en styles.scss — esos quedan
 *     reportados para revisión manual, no se adivina el token más "parecido".
 *
 * Prioridad de resolución cuando un hex mapea a más de un token (ej. #D02030 es a
 * la vez --nevadi-red, --sidebar-active y --topbar-bg): semántico > marca >
 * estructural, porque el uso típico de un hex hardcodeado en un componente de
 * feature es indicar estado (éxito/alerta/error), no replicar el chrome de sidebar.
 *
 * USO:
 *   node scripts/replace-hardcoded-colors.js            # dry-run, solo reporta
 *   node scripts/replace-hardcoded-colors.js --apply     # aplica los reemplazos exactos
 *
 * @author ADES
 * @since 2026
 */
'use strict';

const fs = require('fs');
const path = require('path');

const ROOT = path.resolve(__dirname, '..');
const FRONTEND_DIR = path.join(ROOT, 'frontend', 'src', 'app');
const STYLES_FILE = path.join(ROOT, 'frontend', 'src', 'styles.scss');
const APPLY = process.argv.includes('--apply');

// Tokens en orden de prioridad: si un hex resuelve a varios, gana el primero que
// aparezca aquí. Extraído a mano de styles.scss (semántico primero).
const PRIORITY_TOKENS = [
  '--color-danger', '--color-success', '--color-warning', '--color-info',
  '--nevadi-red', '--nevadi-red-dark', '--nevadi-red-darker', '--nevadi-red-light', '--nevadi-red-lighter',
  '--nevadi-sage', '--nevadi-sage-mid', '--nevadi-sage-light',
  '--nevadi-slate', '--nevadi-slate-mid', '--nevadi-slate-light', '--nevadi-slate-text',
  '--text-primary', '--text-secondary', '--text-muted',
  '--surface-border', '--surface-hover', '--surface-ground',
  '--sidebar-bg', '--sidebar-hover', '--sidebar-active', '--sidebar-text', '--sidebar-text-muted',
  '--topbar-bg', '--topbar-border',
];

// Acromáticos / sin token semántico único — nunca se auto-reemplazan.
const EXCLUDED_HEX = new Set([
  'FFF', 'FFFFFF', '000', '000000', 'FFFFFE',
  'FFF5F5', // tinte casi-blanco, ambiguo
]);

function walk(dir, ext, out = []) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) walk(full, ext, out);
    else if (entry.name.endsWith(ext)) out.push(full);
  }
  return out;
}

function buildTokenMap() {
  const text = fs.readFileSync(STYLES_FILE, 'utf8');
  const re = /(--[a-z0-9-]+):\s*(#[0-9A-Fa-f]{3,8})\b/g;
  const byHex = new Map(); // HEX (upper, no #) -> [{token, priority}]
  let m;
  while ((m = re.exec(text))) {
    const token = m[1];
    const hex = m[2].slice(1).toUpperCase();
    if (!byHex.has(hex)) byHex.set(hex, []);
    byHex.get(hex).push(token);
  }
  // Resolver a un solo token ganador por prioridad
  const resolved = new Map();
  for (const [hex, tokens] of byHex) {
    if (EXCLUDED_HEX.has(hex)) continue;
    let winner = tokens.find(t => PRIORITY_TOKENS.includes(t));
    if (!winner) winner = tokens[0];
    resolved.set(hex, winner);
  }
  return resolved;
}

function main() {
  const tokenMap = buildTokenMap();
  console.log(`Tokens exactos disponibles para auto-reemplazo: ${tokenMap.size}`);
  for (const [hex, token] of tokenMap) console.log(`  #${hex} -> var(${token})`);
  console.log('');

  const hexRe = /#([0-9A-Fa-f]{3,8})\b/g;
  let totalReplaced = 0;
  let totalSkipped = 0;
  const skippedByHex = new Map();
  const filesChanged = [];

  for (const file of walk(FRONTEND_DIR, '.ts')) {
    const original = fs.readFileSync(file, 'utf8');
    let changedHere = 0;
    const updated = original.replace(hexRe, (match, hexBody) => {
      const hex = hexBody.toUpperCase();
      const token = tokenMap.get(hex);
      if (!token) {
        totalSkipped++;
        skippedByHex.set(hex, (skippedByHex.get(hex) || 0) + 1);
        return match;
      }
      totalReplaced++;
      changedHere++;
      return `var(${token})`;
    });
    if (changedHere > 0) {
      filesChanged.push({ file: path.relative(ROOT, file), count: changedHere });
      if (APPLY) fs.writeFileSync(file, updated, 'utf8');
    }
  }

  console.log(`${APPLY ? 'Reemplazados' : '[dry-run] Se reemplazarían'}: ${totalReplaced} usos en ${filesChanged.length} archivos.`);
  for (const { file, count } of filesChanged.sort((a, b) => b.count - a.count)) {
    console.log(`  ${count.toString().padStart(3)}  ${file}`);
  }

  console.log(`\n${totalSkipped} usos sin token exacto (quedan para revisión manual), top valores:`);
  const topSkipped = [...skippedByHex.entries()].sort((a, b) => b[1] - a[1]).slice(0, 15);
  for (const [hex, count] of topSkipped) console.log(`  ${count.toString().padStart(3)}  #${hex}`);

  if (!APPLY) console.log('\nModo dry-run — corre con --apply para escribir los cambios.');
}

main();
