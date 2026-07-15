#!/usr/bin/env node
/**
 * find-duplicate-styles.js — Fase D8 (deuda de frontend, cross-check Antigravity
 * 2026-07-15). Analiza los bloques `styles: [\`...\`]` de los componentes Angular y
 * encuentra reglas CSS (selector + cuerpo) BYTE-IDÉNTICAS repetidas en 3+ archivos
 * distintos — esos son los candidatos reales de extracción a styles.scss. No hace
 * ninguna extracción automática (es un juicio de diseño, no mecánico); solo reporta.
 */
'use strict';
const fs = require('fs');
const path = require('path');

const ROOT = path.resolve(__dirname, '..');
const FRONTEND_DIR = path.join(ROOT, 'frontend', 'src', 'app');

function walk(dir, ext, out = []) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) walk(full, ext, out);
    else if (entry.name.endsWith(ext)) out.push(full);
  }
  return out;
}

function extractStylesBlock(text) {
  const idx = text.indexOf('styles: [`');
  if (idx === -1) return null;
  const start = idx + 'styles: [`'.length;
  const end = text.indexOf('`]', start);
  if (end === -1) return null;
  return text.slice(start, end);
}

function splitRules(css) {
  // Reglas de primer nivel: selector { ... } (sin anidar @media, esos se ignoran aquí)
  const rules = [];
  let depth = 0, ruleStart = -1;
  for (let i = 0; i < css.length; i++) {
    if (css[i] === '{') { if (depth === 0) ruleStart = css.lastIndexOf('\n', i) + 1 || 0; depth++; }
    else if (css[i] === '}') {
      depth--;
      if (depth === 0 && ruleStart !== -1) {
        rules.push(css.slice(ruleStart, i + 1).trim());
        ruleStart = -1;
      }
    }
  }
  return rules;
}

function normalize(rule) {
  return rule.replace(/\s+/g, ' ').trim();
}

function main() {
  const ruleToFiles = new Map(); // normalized rule -> Set(files)
  let filesWithStyles = 0;

  for (const file of walk(FRONTEND_DIR, '.ts')) {
    const text = fs.readFileSync(file, 'utf8');
    const block = extractStylesBlock(text);
    if (!block) continue;
    filesWithStyles++;
    const rel = path.relative(ROOT, file);
    for (const rule of splitRules(block)) {
      const norm = normalize(rule);
      if (norm.length < 15) continue; // reglas triviales, no vale la pena
      if (!ruleToFiles.has(norm)) ruleToFiles.set(norm, new Set());
      ruleToFiles.get(norm).add(rel);
    }
  }

  console.log(`${filesWithStyles} componentes con bloque styles: [...] inline.\n`);

  const duplicated = [...ruleToFiles.entries()]
    .filter(([, files]) => files.size >= 3)
    .sort((a, b) => b[1].size - a[1].size);

  console.log(`${duplicated.length} reglas CSS byte-idénticas repetidas en 3+ archivos:\n`);
  for (const [rule, files] of duplicated) {
    console.log(`  [${files.size}x] ${rule}`);
    console.log(`         ${[...files].join(', ')}\n`);
  }
}

main();
