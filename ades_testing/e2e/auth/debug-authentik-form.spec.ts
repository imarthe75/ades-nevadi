import { test } from '@playwright/test';

test('DEBUG: Authentik form inspection', async ({ page }) => {
  await page.goto('/login', { waitUntil: 'networkidle' });
  await page.waitForTimeout(1000);
  
  const oauthBtn = page.locator('button:has-text("Iniciar sesión")').first();
  if (await oauthBtn.isVisible()) {
    await oauthBtn.click();
    
    // Wait for Authentik to load
    await page.waitForTimeout(2000);
    
    // Log all input fields
    const inputs = await page.locator('input').all();
    console.log(`\n=== AUTHENTIK FORM INPUTS ===`);
    console.log(`Total inputs: ${inputs.length}`);
    
    for (let i = 0; i < inputs.length; i++) {
      const type = await inputs[i].getAttribute('type');
      const name = await inputs[i].getAttribute('name');
      const placeholder = await inputs[i].getAttribute('placeholder');
      console.log(`  Input ${i}: type=${type}, name=${name}, placeholder=${placeholder}`);
    }
    
    // Log all buttons
    const buttons = await page.locator('button').all();
    console.log(`\n=== AUTHENTIK BUTTONS ===`);
    console.log(`Total buttons: ${buttons.length}`);
    
    for (let i = 0; i < buttons.length; i++) {
      const text = await buttons[i].textContent();
      const type = await buttons[i].getAttribute('type');
      console.log(`  Button ${i}: type=${type}, text="${text}"`);
    }
    
    // Screenshot
    await page.screenshot({ path: 'authentik-form.png' });
    console.log(`\nScreenshot: authentik-form.png`);
    console.log(`Current URL: ${page.url()}`);
  }
});
