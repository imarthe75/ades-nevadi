import { test } from '@playwright/test';

test('DEBUG: Fill Authentik form step by step', async ({ page }) => {
  await page.goto('/login', { waitUntil: 'networkidle' });
  await page.waitForTimeout(1000);
  
  const oauthBtn = page.locator('button:has-text("Iniciar sesión")').first();
  if (await oauthBtn.isVisible()) {
    await oauthBtn.click();
    
    // Wait for form inputs
    await page.waitForSelector('input', { timeout: 10000 });
    console.log('✓ Inputs found');
    
    // Get all inputs
    const inputs = await page.locator('input').all();
    console.log(`Total inputs: ${inputs.length}`);
    
    // Try to fill first input (email/username)
    const firstInput = inputs[0];
    await firstInput.click();
    await firstInput.fill('admin@ades.test');
    console.log('✓ First input filled');
    
    // Try to fill second input (password)
    if (inputs.length > 1) {
      const secondInput = inputs[1];
      await secondInput.click();
      await secondInput.fill('Admin@123456');
      console.log('✓ Second input filled');
    }
    
    // Take screenshot
    await page.screenshot({ path: 'authentik-filled.png' });
    
    // Find and click submit button
    const buttons = await page.locator('button').all();
    console.log(`Total buttons: ${buttons.length}`);
    
    for (let i = 0; i < buttons.length; i++) {
      const text = await buttons[i].textContent();
      console.log(`  Button ${i}: "${text}"`);
      
      // Click first button (usually login)
      if (i === 0) {
        console.log('Clicking first button...');
        await buttons[i].click();
        break;
      }
    }
    
    // Wait for navigation
    await page.waitForTimeout(3000);
    console.log(`Current URL: ${page.url()}`);
  }
});
