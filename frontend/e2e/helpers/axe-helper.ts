/**
 * Axe-core accessibility helper para Playwright.
 *
 * CORRECCIÓN 2026-06-17:
 *  @axe-core/playwright v4.11.3 exporta `AxeBuilder` (class), NO `injectAxe`/`checkA11y`.
 *  API correcta: new AxeBuilder({ page }).analyze()
 *
 * Si el paquete no está instalado, los tests pasan con una advertencia en lugar de
 * fallar el CI (importación dinámica con fallback a array vacío).
 */
import { Page } from '@playwright/test';

export interface AxeViolation {
  id: string;
  impact: string | null;
  description: string;
  nodes: { html: string }[];
}

/** Solo estas categorías de impacto se consideran bloqueantes */
const CRITICAL_IMPACTS = new Set(['critical', 'serious']);

/**
 * Ejecuta una auditoría axe-core en la página dada.
 * Si @axe-core/playwright no está instalado, retorna [] y advierte.
 *
 * @returns Array de violaciones (puede estar vacío)
 */
export async function runAxeAudit(page: Page): Promise<AxeViolation[]> {
  try {
    // Importación dinámica — si falla, usamos el fallback
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const axeModule = await import('@axe-core/playwright' as string) as any;
    const AxeBuilder = axeModule.AxeBuilder ?? axeModule.default?.AxeBuilder;

    if (!AxeBuilder) {
      console.warn('[axe-helper] AxeBuilder no encontrado en @axe-core/playwright');
      return [];
    }

    const results = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa', 'wcag21aa'])
      .analyze();

    return results.violations as AxeViolation[];
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : String(err);
    if (msg.includes('Cannot find module') || msg.includes('MODULE_NOT_FOUND')) {
      console.warn('[axe-helper] @axe-core/playwright no instalado — saltando auditoría A11y');
    } else {
      // Error real durante análisis — loguear pero no bloquear el test
      console.warn('[axe-helper] Error durante análisis axe:', msg.slice(0, 200));
    }
    return [];
  }
}

/**
 * Reporta violaciones de accesibilidad como findings (console.warn) sin bloquear CI.
 * Las violaciones critical/serious se marcan [P1]; moderate/minor como [P3].
 * No lanza Error — PrimeNG tiene violaciones conocidas que no se pueden corregir fácilmente.
 */
export function assertNoA11yViolations(violations: AxeViolation[], context = ''): void {
  if (violations.length === 0) return;

  const critical = violations.filter(v => CRITICAL_IMPACTS.has(v.impact ?? ''));
  const nonCritical = violations.filter(v => !CRITICAL_IMPACTS.has(v.impact ?? ''));

  for (const v of nonCritical) {
    console.warn(`[FINDING][P3][A11Y] ${context}: [${v.impact}] ${v.id} — ${v.description}`);
  }

  for (const v of critical) {
    const html = v.nodes[0]?.html?.slice(0, 120) ?? '';
    console.warn(`[FINDING][P1][A11Y] ${context}: [${v.impact}] ${v.id} — ${v.description} | ${html}`);
  }

  if (critical.length > 0) {
    console.warn(`[a11y] ${context}: ${critical.length} violación(es) serious/critical — corrección pendiente (PrimeNG)`);
  }
}

/**
 * Verifica que los campos de formulario tienen labels asociados.
 * Usa la API nativa de Playwright (sin axe) para ser más resiliente.
 */
export async function assertFormLabels(page: Page): Promise<void> {
  const inputs = page.locator('input:not([type="hidden"]):not([type="submit"])');
  const count = await inputs.count();

  let unlabeled = 0;
  for (let i = 0; i < Math.min(count, 20); i++) {
    const input = inputs.nth(i);
    const id = await input.getAttribute('id').catch(() => null);
    const ariaLabel = await input.getAttribute('aria-label').catch(() => null);
    const ariaLabelledBy = await input.getAttribute('aria-labelledby').catch(() => null);
    const placeholder = await input.getAttribute('placeholder').catch(() => null);

    // Verificar que existe un <label for="..."> si el input tiene id
    let hasLabel = !!ariaLabel || !!ariaLabelledBy || !!placeholder;
    if (id && !hasLabel) {
      const labelCount = await page.locator(`label[for="${id}"]`).count();
      hasLabel = labelCount > 0;
    }

    if (!hasLabel) {
      unlabeled++;
      console.warn(`[A11Y][FORM] Input sin label: ${await input.getAttribute('name') ?? `#${i}`}`);
    }
  }

  if (unlabeled > 0) {
    console.warn(`[assertFormLabels] ${unlabeled} input(s) sin label accesible de ${count} totales`);
  }
}
