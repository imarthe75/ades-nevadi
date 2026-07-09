import { defineConfig, devices } from '@playwright/test';

/**
 * Configuración de Playwright para ADES E2E Testing
 * SEMANA 3: E2E Foundation — 35+ specs (Auth + CRUD)
 */
export default defineConfig({
  testDir: './e2e',
  testMatch: '**/*.spec.ts',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 1,
  workers: process.env.CI ? 1 : 1,
  reporter: [
    ['html', { outputFolder: 'playwright-report' }],
    ['json', { outputFile: 'test-results.json' }],
    ['list'],
  ],
  use: {
    baseURL: process.env.ADES_BASE_URL || 'https://ades.setag.mx',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    actionTimeout: 10000,
  },
  timeout: 30 * 1000,
  expect: {
    timeout: 10 * 1000,
  },
  globalTimeout: 60 * 1000 * 60, // 1 hour
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
  webServer: undefined, // ADES is already running (external service)
});
