#!/usr/bin/env node
/**
 * check-api-contracts.js — Guardarraíl de CI ligero (Fase 6 del plan de auditoría
 * 2026-07-14, docs/hallazgos/2026-07-14_analisis_auditoria_antigravity_y_plan.md).
 *
 * Detecta el patrón de bug de causa raíz de esta auditoría (HALLAZGO-ADES-001):
 * el frontend Angular envía un payload con una clave (`grupo_id`) que no existe
 * en el DTO Java correspondiente (que espera `grupoId`), y el campo llega `null`
 * sin que nadie se entere hasta producción.
 *
 * MÉTODO: parseo ligero por regex de ambos lados (no un AST completo — es un
 * guardarraíl barato mientras madura la Fase 2, generación de tipos desde
 * OpenAPI/springdoc, que hace esta clase de bug estructuralmente imposible).
 * Por diseño tiene falsos negativos (payloads armados con variables, spreads,
 * objetos anidados) y puede tener algún falso positivo raro — es una red de
 * seguridad adicional, no un reemplazo de code review.
 *
 * USO:
 *   node scripts/check-api-contracts.js            # reporta, exit 0 siempre
 *   node scripts/check-api-contracts.js --strict    # exit 1 si hay mismatches
 *
 * @author ADES
 * @since 2026
 */
'use strict';

const fs = require('fs');
const path = require('path');

const ROOT = path.resolve(__dirname, '..');
const BACKEND_DIR = path.join(ROOT, 'backend-spring', 'src', 'main', 'java');
const FRONTEND_DIR = path.join(ROOT, 'frontend', 'src', 'app');
const STRICT = process.argv.includes('--strict');

// ── Utilidades ───────────────────────────────────────────────────────────────

function walk(dir, ext, out = []) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) walk(full, ext, out);
    else if (entry.name.endsWith(ext)) out.push(full);
  }
  return out;
}

/** camelCase -> snake_case (misma convención que Jackson SNAKE_CASE en el BFF). */
function toSnakeCase(s) {
  return s.replace(/([a-z0-9])([A-Z])/g, '$1_$2').toLowerCase();
}

/**
 * Normaliza un path de endpoint a "forma" comparable: placeholders por variables y
 * sin el prefijo `/api/v1` (los DTOs de Spring lo llevan vía @RequestMapping a nivel de
 * clase, pero ApiService.base ya lo antepone en el frontend, así que las llamadas
 * `this.api.post('/foros', ...)` solo llevan el path relativo).
 */
function normalizePath(p) {
  return p
    .replace(/\$\{[^}]*\}/g, '{}')   // Angular: `${algo}`
    .replace(/\{[^}]*\}/g, '{}')     // Spring: {algo}
    .replace(/\?.*$/, '')            // sin query string
    .replace(/\/+$/, '')             // sin slash final
    .split('/')
    .filter(Boolean)
    .filter((seg, i, arr) => !(i < 2 && (seg === 'api' || /^v\d+$/.test(seg)) && arr.length > 2));
}

function pathsMatch(a, b) {
  const na = normalizePath(a);
  const nb = normalizePath(b);
  if (na.length !== nb.length) return false;
  return na.every((seg, i) => seg === nb[i] || seg === '{}' || nb[i] === '{}');
}

/** Encuentra el bloque {...} balanceado que empieza en `openIdx` (índice de '{'). */
function extractBalancedBraces(text, openIdx) {
  let depth = 0;
  for (let i = openIdx; i < text.length; i++) {
    if (text[i] === '{') depth++;
    else if (text[i] === '}') {
      depth--;
      if (depth === 0) return text.slice(openIdx, i + 1);
    }
  }
  return null;
}

/** Encuentra el bloque (...) balanceado que empieza en `openIdx` (índice de '('). */
function extractBalancedParens(text, openIdx) {
  let depth = 0;
  for (let i = openIdx; i < text.length; i++) {
    if (text[i] === '(') depth++;
    else if (text[i] === ')') {
      depth--;
      if (depth === 0) return text.slice(openIdx, i + 1);
    }
  }
  return null;
}

/** Extrae las claves de primer nivel de un objeto literal TS (best-effort). */
function topLevelKeys(objLiteral) {
  const inner = objLiteral.slice(1, -1);
  const keys = [];
  let depth = 0;
  let token = '';
  for (let i = 0; i < inner.length; i++) {
    const c = inner[i];
    if (c === '{' || c === '[' || c === '(') depth++;
    else if (c === '}' || c === ']' || c === ')') depth--;
    if (c === ',' && depth === 0) {
      keys.push(token.trim());
      token = '';
    } else {
      token += c;
    }
  }
  if (token.trim()) keys.push(token.trim());

  return keys
    .map(t => {
      const spread = t.match(/^\.\.\./);
      if (spread) return null; // `...algo` — no podemos verificar estáticamente, se ignora
      const m = t.match(/^['"]?([A-Za-z_$][\w$]*)['"]?\s*:/) || t.match(/^([A-Za-z_$][\w$]*)\s*(,|$)/);
      return m ? m[1] : null;
    })
    .filter(Boolean);
}

// ── Lado backend: endpoints + campos de DTO ─────────────────────────────────

/** Divide `str` por `,` solo en profundidad 0 (respeta (), {}, [] anidados). */
function splitTopLevelCommas(str) {
  const parts = [];
  let depth = 0;
  let token = '';
  for (const c of str) {
    if (c === '(' || c === '{' || c === '[') depth++;
    else if (c === ')' || c === '}' || c === ']') depth--;
    if (c === ',' && depth === 0) {
      parts.push(token);
      token = '';
    } else {
      token += c;
    }
  }
  if (token.trim()) parts.push(token);
  return parts;
}

function extractJavaFields(fileText, typeName) {
  // Record: record TypeName(@Anotacion(...) Tipo campo1, Tipo campo2, ...) { ... }
  // Los parámetros pueden llevar anotaciones Jakarta Validation con sus propias comas
  // internas (@Size(min=1, max=2, message="...")) — por eso el split debe ser a
  // profundidad 0, y el paréntesis de cierre real hay que hallarlo balanceado, no con
  // el primer ')' que aparezca (que puede pertenecer a una anotación interna).
  const recordDeclRe = new RegExp(`record\\s+${typeName}\\s*\\(`, 's');
  const recordDeclMatch = recordDeclRe.exec(fileText);
  if (recordDeclMatch) {
    const openIdx = recordDeclMatch.index + recordDeclMatch[0].length - 1;
    const paramsBlock = extractBalancedParens(fileText, openIdx);
    if (paramsBlock) {
      return splitTopLevelCommas(paramsBlock.slice(1, -1))
        .map(p => p.trim().split(/\s+/).pop())
        .filter(Boolean);
    }
  }

  // Clase @Data: class TypeName { ... private Tipo campo; ... }
  const classRe = new RegExp(`class\\s+${typeName}\\b[^{]*\\{`, 's');
  const classMatch = classRe.exec(fileText);
  if (!classMatch) return null;
  const body = extractBalancedBraces(fileText, classMatch.index + classMatch[0].length - 1);
  if (!body) return null;
  const fieldRe = /private\s+[\w<>,.\s?]+?\s(\w+)\s*(=[^;]+)?;/g;
  const fields = [];
  let m;
  while ((m = fieldRe.exec(body))) fields.push(m[1]);
  return fields;
}

/**
 * A partir del índice donde termina un `@XxxMapping`(...) o bare `@XxxMapping`, salta
 * cualquier otra anotación (`@Foo` o `@Foo(...)`) hasta encontrar `public ... nombre(`.
 * Devuelve el índice del `(` que abre la lista de parámetros, o null si no se encuentra
 * en una distancia razonable (evita falsos positivos cruzando a otro método).
 */
function findParamListOpenParen(text, fromIdx) {
  const window = text.slice(fromIdx, fromIdx + 800);
  const publicRe = /public\s+[\w<>,\[\]\s?.]+?\s(\w+)\s*\(/;
  const m = publicRe.exec(window);
  if (!m) return null;
  return fromIdx + m.index + m[0].length - 1; // índice del '('
}

function parseBackendEndpoints() {
  const endpoints = [];
  for (const file of walk(BACKEND_DIR, '.java')) {
    if (!file.includes('Controller')) continue;
    const text = fs.readFileSync(file, 'utf8');

    const classMappingMatch = text.match(/@RequestMapping\(\s*["']([^"']*)["']/);
    const basePath = classMappingMatch ? classMappingMatch[1] : '';

    // Bare `@PostMapping` o con argumentos `@PostMapping("...")` / `@PostMapping(value=..., ...)`.
    // OJO: solo cuenta como "tiene paréntesis" si el '(' está en la misma línea (sin \n de por
    // medio) — si no, es una anotación bare y ese '(' pertenece a otra cosa más abajo (p. ej.
    // la lista de parámetros del método).
    const mappingRe = /@(Post|Put|Patch)Mapping([ \t]*\()?/g;
    let m;
    while ((m = mappingRe.exec(text))) {
      const httpMethod = m[1].toUpperCase();
      let subPath = '';
      let afterAnnotationIdx = m.index + m[0].length;

      if (m[2]) {
        // Tiene paréntesis: extraer balanceado para tolerar valores anidados.
        const openIdx = text.indexOf('(', m.index);
        const argsBlock = extractBalancedParens(text, openIdx);
        if (argsBlock) {
          const pathMatch = argsBlock.match(/["']([^"']*)["']/);
          subPath = pathMatch ? pathMatch[1] : '';
          afterAnnotationIdx = openIdx + argsBlock.length;
        }
      }

      const openParenIdx = findParamListOpenParen(text, afterAnnotationIdx);
      if (openParenIdx === null) continue;
      const paramsBlock = extractBalancedParens(text, openParenIdx);
      if (!paramsBlock) continue;
      const params = paramsBlock.slice(1, -1);

      const fullPath = (basePath + '/' + subPath).replace(/\/+/g, '/');

      // Tolera @Valid y List< en cualquier orden antes del tipo (ej. `List<@Valid Item>`).
      const bodyParamMatch = params.match(/@RequestBody\s+(?:@Valid\s+|List<\s*)*([A-Z]\w*)\b/);
      if (!bodyParamMatch) continue; // sin @RequestBody tipado -> nada que verificar
      const typeName = bodyParamMatch[1];
      if (['Map', 'List', 'Object'].includes(typeName)) continue;

      const fields = extractJavaFields(text, typeName);
      if (!fields || fields.length === 0) continue;

      endpoints.push({
        file: path.relative(ROOT, file),
        httpMethod,
        fullPath,
        typeName,
        fields,
        fieldsSnake: fields.map(toSnakeCase),
      });
    }
  }
  return endpoints;
}

// ── Lado frontend: llamadas this.api.post/put/patch con objeto literal inline ──

function parseFrontendCalls() {
  const calls = [];
  for (const file of walk(FRONTEND_DIR, '.ts')) {
    const text = fs.readFileSync(file, 'utf8');
    const callRe = /this\.api\.(post|put|patch)(?:<[^>]*>)?\(\s*(`[^`]*`|'[^']*'|"[^"]*")\s*,\s*/g;
    let m;
    while ((m = callRe.exec(text))) {
      const httpMethod = m[1].toUpperCase();
      const urlLiteral = m[2].slice(1, -1);
      const afterComma = m.index + m[0].length;
      const openBraceIdx = text.indexOf('{', afterComma);
      // Solo evaluamos si el payload es un objeto literal inline pegado a la llamada
      // (permite hasta ~3 caracteres de espacio/salto de línea antes de la '{').
      if (openBraceIdx === -1 || text.slice(afterComma, openBraceIdx).trim().length > 0) continue;

      const objLiteral = extractBalancedBraces(text, openBraceIdx);
      if (!objLiteral) continue;

      const lineNumber = text.slice(0, m.index).split('\n').length;
      calls.push({
        file: path.relative(ROOT, file),
        line: lineNumber,
        httpMethod,
        urlLiteral,
        keys: topLevelKeys(objLiteral),
      });
    }
  }
  return calls;
}

// ── Cruce y reporte ──────────────────────────────────────────────────────────

function main() {
  const backendEndpoints = parseBackendEndpoints();
  const frontendCalls = parseFrontendCalls();

  const mismatches = [];
  let matchedPairs = 0;

  for (const call of frontendCalls) {
    const candidates = backendEndpoints.filter(
      e => e.httpMethod === call.httpMethod && pathsMatch(call.urlLiteral, e.fullPath)
    );
    if (candidates.length === 0) continue; // sin match -> no podemos verificar (no es error)

    for (const endpoint of candidates) {
      matchedPairs++;
      const unknownKeys = call.keys.filter(
        k => !endpoint.fields.includes(k) && !endpoint.fieldsSnake.includes(k)
      );
      if (unknownKeys.length > 0) {
        mismatches.push({ call, endpoint, unknownKeys });
      }
    }
  }

  console.log(`check-api-contracts: ${backendEndpoints.length} endpoints tipados en backend, ` +
    `${frontendCalls.length} llamadas con payload inline en frontend, ${matchedPairs} pares cruzados.\n`);

  if (mismatches.length === 0) {
    console.log('✅ Sin mismatches evidentes de nombres de campo entre Angular y los DTOs de Spring.');
    process.exit(0);
  }

  console.log(`⚠️  ${mismatches.length} posible(s) mismatch(es) de contrato API:\n`);
  for (const { call, endpoint, unknownKeys } of mismatches) {
    console.log(`  ${call.file}:${call.line}`);
    console.log(`    ${call.httpMethod} ${call.urlLiteral}  →  ${endpoint.file} (${endpoint.typeName})`);
    console.log(`    Claves enviadas sin match en el DTO: ${unknownKeys.join(', ')}`);
    console.log(`    Campos esperados por el DTO: ${endpoint.fields.join(', ')}\n`);
  }

  if (STRICT) {
    console.log('Ejecutado en modo --strict: fallando el build.');
    process.exit(1);
  } else {
    console.log('Modo advisory (sin --strict): no se falla el build. Revisar manualmente.');
    process.exit(0);
  }
}

main();
