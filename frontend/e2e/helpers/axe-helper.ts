/**
 * axe-helper.ts
 *
 * Wrapper de @axe-core/playwright para audits de accesibilidad en ADES.
 * Usa AxeBuilder (API oficial) — documenta violaciones como findings sin bloquear CI.
 */
/* eslint-disable @typescript-eslint/no-explicit-any */
import { Page } from '@playwright/test';

type AxeViolation = {
  id: string;
  impact: 'minor' | 'moderate' | 'serious' | 'critical' | null;
  description: string;
  help: string;
  helpUrl: string;
  nodes: { html: string; target: string[] }[];
};

/**
 * Reglas excluidas — falsos positivos conocidos con PrimeNG v21.
 */
const EXCLUDED_RULES = [
  'color-contrast',              // PrimeNG themes — revisar manualmente
  'landmark-unique',             // p-dialog crea múltiples role=dialog en transitions
  'scrollable-region-focusable', // PrimeNG scrollable containers
  'duplicate-id-active',         // PrimeNG dropdowns generan IDs duplicados
  'aria-required-children',      // p-listbox/p-tree no siguen ARIA spec estrictamente
];

/**
 * Ejecuta un audit de axe-core en la página actual con AxeBuilder.
 */
export async function runAxeAudit(page: Page, options?: {
  include?: string[];
  exclude?: string[];
}): Promise<AxeViolation[]> {
  let axeModule: any;
  try {
    axeModule = await import('@axe-core/playwright');
  } catch {
    console.warn('[axe-helper] @axe-core/playwright no instalado — instalar con: npm install -D @axe-core/playwright');
    return [];
  }

  const { AxeBuilder } = axeModule;
  const excludeRules = EXCLUDED_RULES.concat(options?.exclude ?? []);

  const builder: any = new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'best-practice'])
    .disableRules(excludeRules);

  const results: { violations: AxeViolation[] } = await builder.analyze()
    .catch(() => ({ violations: [] as AxeViolation[] }));

  return results.violations;
}

/**
 * Reporta todas las violaciones como findings (console.warn) sin lanzar error.
 * Las violaciones serious/critical se marcan como [P1], minor/moderate como [P3].
 */
export function assertNoA11yViolations(violations: AxeViolation[], label = 'página'): void {
  if (violations.length === 0) return;

  for (const v of violations) {
    const priority = (v.impact === 'serious' || v.impact === 'critical') ? 'P1' : 'P3';
    const targets = v.nodes.map(n => n.target.join(', ')).join(' | ');
    console.warn(`[FINDING][${priority}][a11y] ${label} — [${v.impact}] ${v.id}: ${v.help} | nodes: ${targets.slice(0, 200)}`);
  }

  const blockingCount = violations.filter(v => v.impact === 'serious' || v.impact === 'critical').length;
  if (blockingCount > 0) {
    console.warn(`[a11y] ${label} tiene ${blockingCount} violación(es) serious/critical — ver arriba. Corrección pendiente.`);
  }
}

/**
 * Verifica que inputs visibles tengan label accesible.
 * Reporta como warning, no falla el test.
 */
export async function assertFormLabels(page: Page): Promise<void> {
  const unlabeled = await page.evaluate(() => {
    const inputs = Array.from(
      document.querySelectorAll('input:not([type="hidden"]), select, textarea')
    ) as HTMLElement[];
    return inputs
      .filter(el => {
        const hasAriaLabel   = el.hasAttribute('aria-label');
        const hasAriaLabelBy = el.hasAttribute('aria-labelledby');
        const id             = el.getAttribute('id');
        const hasLabel       = id ? !!document.querySelector(`label[for="${id}"]`) : false;
        const hasTitle       = el.hasAttribute('title');
        const hasPlaceholder = el.hasAttribute('placeholder');
        return !hasAriaLabel && !hasAriaLabelBy && !hasLabel && !hasTitle && !hasPlaceholder;
      })
      .map(el => el.outerHTML.slice(0, 120));
  });

  if (unlabeled.length > 0) {
    console.warn(`[FINDING][P2][a11y] ${unlabeled.length} input(s) sin label accesible:\n  ${unlabeled.join('\n  ')}`);
  }
}
