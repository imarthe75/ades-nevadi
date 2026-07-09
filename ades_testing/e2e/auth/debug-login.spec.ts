import { test } from '@playwright/test';

test('DEBUG: Inspect login page', async ({ page }) => {
  // Go to login page
  await page.goto('/login', { waitUntil: 'networkidle' });
  
  // Wait a bit for Angular to render
  await page.waitForTimeout(2000);
  
  // Take screenshot
  await page.screenshot({ path: 'login-page.png' });
  
  // Log all input fields
  const inputs = await page.locator('input').all();
  console.log(`Found ${inputs.length} input fields`);
  
  for (let i = 0; i < inputs.length; i++) {
    const type = await inputs[i].getAttribute('type');
    const placeholder = await inputs[i].getAttribute('placeholder');
    console.log(`  Input ${i}: type=${type}, placeholder=${placeholder}`);
  }
  
  // Log all buttons
  const buttons = await page.locator('button').all();
  console.log(`Found ${buttons.length} buttons`);
  
  for (let i = 0; i < Math.min(3, buttons.length); i++) {
    const text = await buttons[i].textContent();
    console.log(`  Button ${i}: ${text}`);
  }
  
  // Check for h1/h2
  const heading = await page.locator('h1, h2').first().textContent();
  console.log(`Heading: ${heading}`);
});
