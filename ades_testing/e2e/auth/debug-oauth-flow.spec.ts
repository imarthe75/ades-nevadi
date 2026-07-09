import { test, expect } from '@playwright/test';

test('DEBUG: OAuth login flow', async ({ page }) => {
  // Go to login
  await page.goto('/login', { waitUntil: 'networkidle' });
  await page.waitForTimeout(1000);
  
  // Click Authentik button
  const authBtn = page.locator('button:has-text("Iniciar sesión")');
  console.log('Button visible:', await authBtn.isVisible());
  
  if (await authBtn.isVisible()) {
    await authBtn.click();
    
    // Wait for redirect to Authentik
    await page.waitForTimeout(2000);
    
    // Check URL
    const url = page.url();
    console.log('Current URL:', url);
    
    // Screenshot
    await page.screenshot({ path: 'oauth-flow.png' });
  }
});
