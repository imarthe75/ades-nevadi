# 🎯 SEMANA 3 — E2E FOUNDATION ROADMAP
**Start Date:** Monday 2026-07-22  
**Duration:** 5 days (Mon-Fri)  
**Esfuerzo:** 60 horas  
**Score Target:** 78 → 80/100 (+2 points)

---

## 📋 OVERVIEW

SEMANA 3 es el foundation para toda la E2E automation. Incluye:
1. **15+ Auth Specs** (login, logout, permissions, token refresh)
2. **20+ CRUD Specs** (expediente create/edit/delete/list/search)
3. **Helper Functions** (reusable, 0% duplication)

Total: 35+ Playwright specs en producción

---

## 🏗️ ARCHITECTURA E2E

```
├─ ades_testing/
│  ├─ e2e/
│  │  ├─ auth/
│  │  │  ├─ 01-login.spec.ts         (5 specs)
│  │  │  ├─ 02-logout.spec.ts        (3 specs)
│  │  │  ├─ 03-token-refresh.spec.ts (3 specs)
│  │  │  └─ 04-permissions.spec.ts    (4 specs)
│  │  │  └─ Total: 15 specs
│  │  │
│  │  ├─ crud/
│  │  │  ├─ 05-expediente-create.spec.ts  (6 specs)
│  │  │  ├─ 06-expediente-edit.spec.ts    (7 specs)
│  │  │  ├─ 07-expediente-delete.spec.ts  (3 specs)
│  │  │  ├─ 08-expediente-list.spec.ts    (2 specs)
│  │  │  └─ 09-expediente-search.spec.ts  (2 specs)
│  │  │  └─ Total: 20 specs
│  │  │
│  │  └─ fixtures/
│  │     ├─ auth.helper.ts              (reusable login/logout)
│  │     ├─ api.helper.ts               (request/response mocks)
│  │     ├─ data.factory.ts             (seed users, groups, expedientes)
│  │     ├─ selector.helper.ts          (cascade selectors)
│  │     └─ form.helper.ts              (fill forms, validation)
│  │
│  └─ playwright.config.ts
│     ├─ baseURL: https://ades.setag.mx
│     ├─ browser: chromium
│     ├─ retries: 2 (for flaky tests)
│     └─ timeout: 30s
```

---

## 📅 SCHEDULE POR DÍA

### 🔵 LUNES (Day 1) — Setup + Auth Helpers

**MAÑANA (4h):**
```bash
# 1. Clone ades_testing (if not exists)
git clone https://github.com/imarthe75/ades-testing.git

# 2. Install Playwright
cd ades_testing
npm install
npx playwright install

# 3. Configure playwright.config.ts
cat > playwright.config.ts << 'EOF'
import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  timeout: 30 * 1000,
  expect: { timeout: 10 * 1000 },
  use: {
    baseURL: 'https://ades.setag.mx',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  webServer: {
    command: 'npm run start',
    url: 'https://ades.setag.mx',
    reuseExistingServer: true,
  },
});
EOF

# 4. Create fixtures directory
mkdir -p e2e/fixtures e2e/auth e2e/crud
```

**TARDE (4h):**
```typescript
// e2e/fixtures/auth.helper.ts
export class AuthHelper {
  constructor(private page: Page) {}

  async login(email: string, password: string) {
    await this.page.goto('/login');
    await this.page.fill('input[type="email"]', email);
    await this.page.fill('input[type="password"]', password);
    await this.page.click('button:has-text("Ingresar")');
    
    // Wait for redirect to dashboard or home
    await this.page.waitForURL('**/dashboard', { timeout: 10000 });
  }

  async logout() {
    await this.page.click('[data-testid="user-menu"]');
    await this.page.click('[data-testid="logout-btn"]');
    await this.page.waitForURL('**/login');
  }

  async refreshToken() {
    // Extract token from sessionStorage
    const token = await this.page.evaluate(() => {
      return sessionStorage.getItem('ades_token');
    });
    
    // Call refresh endpoint
    const response = await this.page.request.post('/api/v1/auth/refresh', {
      headers: { 'Authorization': `Bearer ${token}` },
    });
    
    return response.json();
  }

  async hasPermission(permission: string): Promise<boolean> {
    return await this.page.evaluate((perm) => {
      const user = JSON.parse(sessionStorage.getItem('ades_usuario') || '{}');
      return (user.permisos || []).includes(perm);
    }, permission);
  }
}

// e2e/fixtures/api.helper.ts
export class ApiHelper {
  constructor(private page: Page) {}

  async mockApiResponse(method: string, url: string, response: object) {
    await this.page.route(url, (route) => {
      if (route.request().method() === method) {
        route.fulfill({ json: response });
      }
    });
  }

  async interceptApiRequest(method: string, url: string): Promise<string> {
    let capturedBody = '';
    await this.page.route(url, (route) => {
      capturedBody = route.request().postData() || '';
      route.continue();
    });
    return capturedBody;
  }
}

// e2e/fixtures/data.factory.ts
export class DataFactory {
  static createUser() {
    return {
      email: `test-${Date.now()}@ades.test`,
      password: 'Test@123456',
      name: 'Test User',
      rol_id: '550e8400-e29b-41d4-a716-446655440000', // admin role
    };
  }

  static createExpediente() {
    return {
      alumno_id: '650e8400-e29b-41d4-a716-446655440000',
      descripcion: `Expediente Test ${Date.now()}`,
      archivo: null,
      estado: 'ACTIVO',
    };
  }
}
```

**CHECKPOINT LUNES EOD:**
- ✅ Playwright configured
- ✅ AuthHelper.ts created (login, logout, refresh, permissions)
- ✅ ApiHelper.ts created (mocking, interception)
- ✅ DataFactory.ts created (seed data)

---

### 🟢 MARTES (Day 2) — Auth Specs

**MAÑANA (6h):**
```typescript
// e2e/auth/01-login.spec.ts
import { test, expect } from '@playwright/test';
import { AuthHelper } from '../fixtures/auth.helper';

test.describe('Auth — Login', () => {
  test('should login with valid credentials', async ({ page }) => {
    const auth = new AuthHelper(page);
    await auth.login('admin@ades.test', 'Admin@123456');
    
    // Verify logged-in state
    expect(await page.isVisible('[data-testid="dashboard-title"]')).toBe(true);
  });

  test('should show error on invalid password', async ({ page }) => {
    const auth = new AuthHelper(page);
    await page.goto('/login');
    await page.fill('input[type="email"]', 'admin@ades.test');
    await page.fill('input[type="password"]', 'WrongPassword');
    await page.click('button:has-text("Ingresar")');
    
    // Wait for error message
    const errorMsg = await page.locator('[data-testid="error-message"]');
    await expect(errorMsg).toContainText('Credenciales inválidas');
  });

  test('should show error on non-existent user', async ({ page }) => {
    // Similar to above, but with non-existent email
  });

  test('should redirect to dashboard on successful login', async ({ page }) => {
    // Already validated in test 1
  });

  test('should persist session across page reload', async ({ page }) => {
    const auth = new AuthHelper(page);
    await auth.login('admin@ades.test', 'Admin@123456');
    
    // Reload page
    await page.reload();
    
    // Should still be logged in
    expect(await page.isVisible('[data-testid="dashboard-title"]')).toBe(true);
  });
});

// e2e/auth/02-logout.spec.ts
test.describe('Auth — Logout', () => {
  test('should logout and redirect to login', async ({ page }) => {
    const auth = new AuthHelper(page);
    await auth.login('admin@ades.test', 'Admin@123456');
    await auth.logout();
    
    // Should be on login page
    await expect(page).toHaveURL('**/login');
  });

  test('should clear session storage on logout', async ({ page }) => {
    const auth = new AuthHelper(page);
    await auth.login('admin@ades.test', 'Admin@123456');
    await auth.logout();
    
    const token = await page.evaluate(() => sessionStorage.getItem('ades_token'));
    expect(token).toBeNull();
  });

  test('should prevent access to protected routes after logout', async ({ page }) => {
    const auth = new AuthHelper(page);
    await auth.login('admin@ades.test', 'Admin@123456');
    await auth.logout();
    
    // Try to access dashboard
    await page.goto('/dashboard');
    
    // Should redirect to login
    await expect(page).toHaveURL('**/login');
  });
});

// e2e/auth/03-token-refresh.spec.ts
test.describe('Auth — Token Refresh', () => {
  test('should refresh token when access token expires', async ({ page }) => {
    const auth = new AuthHelper(page);
    await auth.login('admin@ades.test', 'Admin@123456');
    
    // Simulate token expiration (remove token from sessionStorage)
    await page.evaluate(() => sessionStorage.removeItem('ades_token'));
    
    // Call refresh endpoint
    const newToken = await auth.refreshToken();
    expect(newToken.access_token).toBeDefined();
  });
  
  // 2-3 more specs for edge cases
});

// e2e/auth/04-permissions.spec.ts
test.describe('Auth — Permissions', () => {
  test('should enforce role-based access control', async ({ page }) => {
    const auth = new AuthHelper(page);
    
    // Login as non-admin (e.g., teacher)
    await page.goto('/login');
    // ... fill teacher credentials
    
    // Try to access admin panel
    await page.goto('/admin/users');
    
    // Should get 403 or redirect to home
    // (depending on app behavior)
  });
  
  // 3 more specs for different roles
});
```

**TARDE (4h):**
- Run & debug auth specs
- Measure test execution time
- Fix flaky tests (retry logic)

**CHECKPOINT MARTES EOD:**
- ✅ 15 Auth specs written
- ✅ 12+ specs passing (some may be flaky, will fix Wed-Thu)

---

### 🟡 MIÉRCOLES (Day 3) — CRUD Specs Part 1

**TODO (8h):**
```typescript
// e2e/crud/05-expediente-create.spec.ts
test.describe('Expediente — Create', () => {
  test.beforeEach(async ({ page }) => {
    const auth = new AuthHelper(page);
    await auth.login('admin@ades.test', 'Admin@123456');
    await page.goto('/expedientes');
  });

  test('should create expediente with valid data', async ({ page }) => {
    await page.click('[data-testid="btn-create-expediente"]');
    
    // Fill form
    await page.fill('[data-testid="input-descripcion"]', 'Test Expediente');
    await page.click('[data-testid="btn-save"]');
    
    // Verify success message
    const toast = page.locator('[role="alert"]:has-text("Expediente creado")');
    await expect(toast).toBeVisible();
    
    // Verify expediente in list
    const row = page.locator('text="Test Expediente"');
    await expect(row).toBeVisible();
  });

  test('should validate required fields', async ({ page }) => {
    await page.click('[data-testid="btn-create-expediente"]');
    
    // Try to save without description
    await page.click('[data-testid="btn-save"]');
    
    // Verify error message
    const error = page.locator('[data-testid="error-descripcion"]');
    await expect(error).toContainText('requerido');
  });

  test('should support file upload', async ({ page }) => {
    await page.click('[data-testid="btn-create-expediente"]');
    
    // Upload file
    await page.locator('[data-testid="input-archivo"]').setInputFiles('test-file.pdf');
    
    // Verify file preview/size
    const fileSize = page.locator('[data-testid="file-size"]');
    await expect(fileSize).toBeVisible();
  });

  // 3 more specs
});

// e2e/crud/06-expediente-edit.spec.ts
test.describe('Expediente — Edit', () => {
  test('should edit expediente with optimistic locking', async ({ page }) => {
    // Create expediente first
    const data = DataFactory.createExpediente();
    
    // Open edit form
    await page.click('[data-testid="btn-edit-expediente"]');
    
    // Modify data
    await page.fill('[data-testid="input-descripcion"]', 'Updated Description');
    
    // Save
    await page.click('[data-testid="btn-save"]');
    
    // Verify update
    const toast = page.locator('[role="alert"]:has-text("Expediente actualizado")');
    await expect(toast).toBeVisible();
  });

  // 6 more specs
});

// etc...
```

**CHECKPOINT MIÉRCOLES EOD:**
- ✅ 20 CRUD specs written
- ✅ 12+ specs passing

---

### 🔵 JUEVES (Day 4) — Flakiness Fix + Integration

**TODO (6h):**
- Run full suite: `npx playwright test`
- Debug failing specs (especially timeout issues)
- Fix async/await race conditions
- Improve selectors (from `text=` to `data-testid`)

**CHECKPOINT JUEVES EOD:**
- ✅ 30+ specs passing (Auth + CRUD)

---

### 🔴 VIERNES (Day 5) — CI/CD Integration + Documentation

**MAÑANA (4h):**
```yaml
# .github/workflows/e2e-tests.yml
name: E2E Tests

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-node@v3
        with:
          node-version: '18'
      
      - name: Install dependencies
        run: cd ades_testing && npm install
      
      - name: Run E2E tests
        run: cd ades_testing && npx playwright test
      
      - name: Upload test results
        if: always()
        uses: actions/upload-artifact@v3
        with:
          name: playwright-report
          path: ades_testing/playwright-report/
```

**TARDE (2h):**
- Document all 35+ specs in `E2E_TEST_CATALOG.md`
- Create fixture documentation
- Prepare for SEMANA 4 onboarding

---

## 🎯 SEMANA 3 DELIVERABLES

### Code
- ✅ 35+ Playwright specs (Auth + CRUD)
- ✅ 5 Helper functions (reusable)
- ✅ CI/CD GitHub Actions workflow
- ✅ playwright.config.ts configured

### Documentation
- ✅ E2E_TEST_CATALOG.md (spec index)
- ✅ E2E_FIXTURES_GUIDE.md (helper usage)
- ✅ E2E_SETUP.md (local development)

### Metrics
- ✅ 35+ specs passing
- ✅ <30s average execution per spec
- ✅ 0% flakiness (retries = 0)
- ✅ Baseline established for SEMANA 4

---

## 📊 SUCCESS CRITERIA

| Criterion | Target | Status |
|-----------|--------|--------|
| Auth specs passing | 15 | ✅ |
| CRUD specs passing | 20 | ✅ |
| Helper coverage | 100% | ✅ |
| CI/CD workflow | Passing | ✅ |
| Flakiness | <5% | ✅ |
| Execution time | <60m | ✅ |
| Score gain | +2 | 🟡 Expected |

---

## 📞 NOTES FOR NEXT SESSION

**SEMANA 4 (2026-07-29):** Performance + OnPush Migration
- Build on top of these E2E specs
- Add 20+ performance specs (pagination, search, loading)
- Start OnPush migration (45 components)

**Dependency:** SEMANA 3 must deliver 35+ passing specs before SEMANA 4 starts.

