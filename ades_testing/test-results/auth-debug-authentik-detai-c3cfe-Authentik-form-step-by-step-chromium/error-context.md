# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: auth/debug-authentik-detailed.spec.ts >> DEBUG: Fill Authentik form step by step
- Location: e2e/auth/debug-authentik-detailed.spec.ts:3:5

# Error details

```
TimeoutError: locator.click: Timeout 10000ms exceeded.
Call log:
  - waiting for locator('input').first()
    - locator resolved to <input type="text" name="username" autocomplete="username"/>
  - attempting click action
    2 × waiting for element to be visible, enabled and stable
      - element is visible, enabled and stable
      - scrolling into view if needed
      - done scrolling
      - element is outside of the viewport
    - retrying click action
    - waiting 20ms
    2 × waiting for element to be visible, enabled and stable
      - element is visible, enabled and stable
      - scrolling into view if needed
      - done scrolling
      - element is outside of the viewport
    - retrying click action
      - waiting 100ms
    19 × waiting for element to be visible, enabled and stable
       - element is visible, enabled and stable
       - scrolling into view if needed
       - done scrolling
       - element is outside of the viewport
     - retrying click action
       - waiting 500ms

```

# Page snapshot

```yaml
- generic [active] [ref=e8]:
  - generic [ref=e9] [cursor=pointer]:
    - generic "Seleccionar idioma":
      - img
    - combobox "Seleccionar idioma" [ref=e10]:
      - option "español" [selected]
      - option "Deutsch (alemán)"
      - option "čeština (checo)"
      - option "suomi (finés)"
      - option "français (francés)"
      - option "English (inglés)"
      - option "italiano"
      - option "Nederlands (neerlandés)"
      - option "polski (polaco)"
      - option "portugués"
      - option "русский (ruso)"
      - option "Türkçe (turco)"
      - option "简体中文 (chino simplificado)"
      - option "繁體中文 (chino tradicional)"
      - option "한국어 (coreano)"
      - option "日本語 (japonés)"
    - text: ⋯
  - banner
  - main "Authentication form" [ref=e11]:
    - img "Logotipo de authentik" [ref=e13]
    - generic [ref=e14]:
      - heading "Instituto Nevadi" [level=1] [ref=e16]
      - generic [ref=e18]:
        - paragraph [ref=e19]: Log in to continue to ADES Frontend.
        - generic [ref=e20]:
          - generic [ref=e22]: Email or Username
          - textbox "Email or Username" [ref=e23]
        - button "Iniciar sesión" [ref=e25] [cursor=pointer]
      - group "Additional actions" [ref=e27]:
        - generic [ref=e28]: Additional actions
        - link "¿Olvidaste tu nombre de usuario o contraseña?" [ref=e30] [cursor=pointer]:
          - /url: /if/flow/default-password-recovery/?response_type=code&client_id=ades-frontend&redirect_uri=https%3A%2F%2Fades.setag.mx%2Fcallback&scope=openid+email+profile&state=8d06a3cb-f9c8-4861-961b-cf3c390e767b&code_challenge=VTzH1ImbK_pAxiMlwZlz3a3dQxUDLAc9dyIMw9MPv2M&code_challenge_method=S256&next=%2Fapplication%2Fo%2Fauthorize%2F%3Fresponse_type%3Dcode%26client_id%3Dades-frontend%26redirect_uri%3Dhttps%253A%252F%252Fades.setag.mx%252Fcallback%26scope%3Dopenid%2Bemail%2Bprofile%26state%3D8d06a3cb-f9c8-4861-961b-cf3c390e767b%26code_challenge%3DVTzH1ImbK_pAxiMlwZlz3a3dQxUDLAc9dyIMw9MPv2M%26code_challenge_method%3DS256
  - contentinfo "Site footer" [ref=e31]:
    - list "Site links" [ref=e33]:
      - listitem [ref=e34]: Powered by authentik
```

# Test source

```ts
  1  | import { test } from '@playwright/test';
  2  | 
  3  | test('DEBUG: Fill Authentik form step by step', async ({ page }) => {
  4  |   await page.goto('/login', { waitUntil: 'networkidle' });
  5  |   await page.waitForTimeout(1000);
  6  |   
  7  |   const oauthBtn = page.locator('button:has-text("Iniciar sesión")').first();
  8  |   if (await oauthBtn.isVisible()) {
  9  |     await oauthBtn.click();
  10 |     
  11 |     // Wait for form inputs
  12 |     await page.waitForSelector('input', { timeout: 10000 });
  13 |     console.log('✓ Inputs found');
  14 |     
  15 |     // Get all inputs
  16 |     const inputs = await page.locator('input').all();
  17 |     console.log(`Total inputs: ${inputs.length}`);
  18 |     
  19 |     // Try to fill first input (email/username)
  20 |     const firstInput = inputs[0];
> 21 |     await firstInput.click();
     |                      ^ TimeoutError: locator.click: Timeout 10000ms exceeded.
  22 |     await firstInput.fill('admin@ades.test');
  23 |     console.log('✓ First input filled');
  24 |     
  25 |     // Try to fill second input (password)
  26 |     if (inputs.length > 1) {
  27 |       const secondInput = inputs[1];
  28 |       await secondInput.click();
  29 |       await secondInput.fill('Admin@123456');
  30 |       console.log('✓ Second input filled');
  31 |     }
  32 |     
  33 |     // Take screenshot
  34 |     await page.screenshot({ path: 'authentik-filled.png' });
  35 |     
  36 |     // Find and click submit button
  37 |     const buttons = await page.locator('button').all();
  38 |     console.log(`Total buttons: ${buttons.length}`);
  39 |     
  40 |     for (let i = 0; i < buttons.length; i++) {
  41 |       const text = await buttons[i].textContent();
  42 |       console.log(`  Button ${i}: "${text}"`);
  43 |       
  44 |       // Click first button (usually login)
  45 |       if (i === 0) {
  46 |         console.log('Clicking first button...');
  47 |         await buttons[i].click();
  48 |         break;
  49 |       }
  50 |     }
  51 |     
  52 |     // Wait for navigation
  53 |     await page.waitForTimeout(3000);
  54 |     console.log(`Current URL: ${page.url()}`);
  55 |   }
  56 | });
  57 | 
```