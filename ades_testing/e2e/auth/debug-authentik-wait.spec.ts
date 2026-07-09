import { test } from '@playwright/test';

test('DEBUG: Authentik wait for form', async ({ page }) => {
  await page.goto('/login', { waitUntil: 'networkidle' });
  await page.waitForTimeout(1000);
  
  const oauthBtn = page.locator('button:has-text("Iniciar sesión")').first();
  if (await oauthBtn.isVisible()) {
    await oauthBtn.click();
    
    // Wait longer for form to render
    console.log('Waiting for Authentik form...');
    await page.waitForTimeout(3000);
    
    // Check for inputs with longer wait
    console.log('Waiting for input elements...');
    try {
      await page.waitForSelector('input', { timeout: 5000 });
      console.log('✓ Input found!');
    } catch {
      console.log('✗ No input found after 5s');
    }
    
    // Check for web components
    const webComponents = await page.locator('[role]').count();
    console.log(`Elements with role attribute: ${webComponents}`);
    
    // List all text content
    const bodyText = await page.textContent('body');
    console.log(`Body contains "Usernename"?: ${bodyText?.includes('Username')}`);
    console.log(`Body contains "Password"?: ${bodyText?.includes('Password')}`);
    console.log(`Body contains "Email"?: ${bodyText?.includes('Email')}`);
    
    // Take screenshot
    await page.screenshot({ path: 'authentik-wait.png' });
  }
});
