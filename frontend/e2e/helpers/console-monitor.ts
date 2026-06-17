/**
 * Console & API error monitor for Playwright tests.
 *
 * Attaches listeners to page events (pageerror, console.error, response)
 * so tests can assert that no unexpected errors occurred during a workflow.
 */
import { Page } from '@playwright/test';

export interface CapturedError {
  kind: 'pageerror' | 'console';
  message: string;
  location: string;
}

export interface CapturedResponse {
  status: number;
  url: string;
}

/** Patterns that are known-benign in Angular dev mode — skip them */
const BENIGN_PATTERNS: RegExp[] = [
  /ExpressionChangedAfterItHasBeenCheckedError/,
  /net::ERR_ABORTED/,              // intentional request aborts in chaos tests
  /net::ERR_FAILED/,               // network failures (CORS, unreachable endpoints)
  /favicon\.ico/,
  /WARN.*PrimeNG/,
  /NG0/,                            // Angular runtime warnings (not errors)
  /ResizeObserver loop/,            // browser-level warning, not app code
  /Failed to load resource/,        // HTTP errors (tracked via assertNoServerErrors)
  /Access-Control-Allow-Origin/,   // CORS in dev (localhost vs. ades.setag.mx)
  /CORS policy/,                    // same: CORS in dev environment
  /ades\.setag\.mx/,               // direct prod domain calls in dev (CORS expected)
  /^ERROR\s+/,                     // Angular ErrorHandler output (minified: "ERROR me", "ERROR e2")
];

function isBenign(message: string): boolean {
  return BENIGN_PATTERNS.some(p => p.test(message));
}

/**
 * Attaches console error + pageerror listeners.
 * Returns a getter that returns the accumulated errors array.
 *
 * Usage:
 *   const getErrors = attachConsoleMonitor(page);
 *   // ... do stuff ...
 *   assertNoCriticalErrors(getErrors());
 */
export function attachConsoleMonitor(page: Page): () => CapturedError[] {
  const errors: CapturedError[] = [];

  page.on('console', msg => {
    if (msg.type() === 'error') {
      const text = msg.text();
      if (!isBenign(text)) {
        errors.push({ kind: 'console', message: text, location: page.url() });
      }
    }
  });

  page.on('pageerror', err => {
    if (!isBenign(err.message)) {
      errors.push({ kind: 'pageerror', message: err.message, location: page.url() });
    }
  });

  return () => [...errors];
}

/**
 * Attaches a response listener and returns a getter for all API responses.
 * Only captures calls matching /api/ to avoid noise from CDN/static assets.
 */
export function attachApiMonitor(page: Page): () => CapturedResponse[] {
  const responses: CapturedResponse[] = [];

  page.on('response', resp => {
    if (resp.url().includes('/api/')) {
      responses.push({ status: resp.status(), url: resp.url() });
    }
  });

  return () => [...responses];
}

/**
 * Throws if any critical browser/Angular errors were captured.
 */
export function assertNoCriticalErrors(errors: CapturedError[]): void {
  if (errors.length === 0) return;
  const summary = errors
    .map(e => `  [${e.kind}] ${e.message.slice(0, 250)} @ ${e.location}`)
    .join('\n');
  throw new Error(`${errors.length} error(s) capturado(s) en consola:\n${summary}`);
}

/**
 * Throws if any API call returned a 5xx status.
 */
export function assertNoServerErrors(responses: CapturedResponse[]): void {
  const serverErrors = responses.filter(r => r.status >= 500);
  if (serverErrors.length === 0) return;
  const summary = serverErrors
    .map(r => `  HTTP ${r.status} → ${r.url}`)
    .join('\n');
  throw new Error(`${serverErrors.length} respuesta(s) 5xx detectada(s):\n${summary}`);
}
