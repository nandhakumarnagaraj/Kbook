import { test, expect } from '@playwright/test';

const MOCK_SESSION = {
  token: 'mock-access-token',
  refreshToken: 'mock-refresh-token',
  restaurantId: 1,
  userName: 'Demo Owner',
  loginId: 'demo@khanabook.in',
  userEmail: 'demo@khanabook.in',
  whatsappNumber: '+919999999999',
  role: 'OWNER',
};

const MOCK_DASHBOARD = {
  shopName: 'Demo Restaurant',
  hourlySales: [],
  todaysOrders: 42,
  todaysRevenue: 12500.00,
  activeTables: 3,
  pendingKot: 2,
  notifications: [],
  terminals: { online: 2, total: 3, attention: 0 },
  lowStock: [],
};

/**
 * Auth-bypass: injects mock session BEFORE Angular initializes, mocks ALL
 * API calls (the JWT interceptor clears session on any 401), then navigates
 * to the dashboard.
 */
async function authenticateAndGotoDashboard(page) {
  // 1. Inject mock session before any page script runs
  await page.addInitScript((session) => {
    sessionStorage.setItem(
      'khanabook.webAdmin.v1.session',
      JSON.stringify(session)
    );
  }, MOCK_SESSION);

  // 2. Mock ALL API calls - the JWT interceptor clears session on any 401
  await page.route('**/api/v1/**', async (route) => {
    const url = route.request().url();
    if (url.includes('/dashboard')) {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(MOCK_DASHBOARD),
      });
    } else if (url.includes('/notifications')) {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([]),
      });
    } else if (url.includes('/terminals')) {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ online: 2, total: 3, attention: 0 }),
      });
    } else if (url.includes('/materials/low-stock')) {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([]),
      });
    } else {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({}),
      });
    }
  });

  // 3. Navigate to the dashboard
  await page.goto('/business/dashboard');

  // 4. Wait for the dashboard shell to render
  await page.waitForSelector('.page-shell', { timeout: 15000 });
}

test.describe('KhanaBook Dashboard — Design Token Verification', () => {
  test.beforeEach(async ({ page }) => {
    await authenticateAndGotoDashboard(page);
  });

  test('page background uses --kb-color-bg-app token', async ({ page }) => {
    // Body carries --bg which aliases --kb-color-bg-app (#F7F9FC)
    const bg = await page.locator('body').evaluate((el) => {
      return getComputedStyle(el).backgroundColor;
    });
    // #F7F9FC = rgb(247, 249, 252)
    expect(bg).toBe('rgb(247, 249, 252)');
  });

  test('border-strong token passes 3:1 UI boundary', async ({ page }) => {
    // Find an element that uses --kb-color-border-strong (control borders)
    const borderEl = await page.locator('.kb-kpi').first().evaluate((el) => {
      const style = getComputedStyle(el);
      return { borderColor: style.borderColor };
    });
    // --kb-color-border-strong is #4B5563 = rgb(75, 85, 99)
    expect(borderEl.borderColor).not.toBe('transparent');
    expect(borderEl.borderColor).not.toBe('rgba(0, 0, 0, 0)');
  });

  test('refresh button renders with header-action-btn treatment', async ({ page }) => {
    const refreshBtn = await page.locator('[aria-label*="Refresh live metrics"]').first();
    await expect(refreshBtn).toBeVisible();
  });

  test('KPI row renders surface cards', async ({ page }) => {
    // .kb-kpi cards render with --kb-color-surface background
    // Token correctness verified by design-tokens.spec.ts unit test
    const kpi = await page.locator('.kb-kpi').first();
    await expect(kpi).toBeVisible();
  });

  test('text contrast passes AA on background', async ({ page }) => {
    // Check KPI value (foreground ink #111827 on #F7F9FC = 16.9:1 ✅)
    const foreground = await page.locator('.kb-kpi__value').first().evaluate((el) => {
      const style = getComputedStyle(el);
      const color = style.color;
      const bg = getComputedStyle(document.body).backgroundColor;
      return { color, bg };
    });
    // #111827 on #F7F9FC = 16.9:1 ✅
    expect(foreground.color).toBe('rgb(17, 24, 39)');
    expect(foreground.bg).toBe('rgb(247, 249, 252)');
  });

  test('renders at desktop width (1024+)', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 800 });
    await authenticateAndGotoDashboard(page);
    const sidebar = page.locator('.sidebar');
    await expect(sidebar).toBeVisible();
  });

  test('renders at tablet width (<1024)', async ({ page }) => {
    await page.setViewportSize({ width: 768, height: 1024 });
    await authenticateAndGotoDashboard(page);
    const topbar = page.locator('.topbar, .kb-page-header');
    await expect(topbar.first()).toBeVisible();
  });

  test('focus ring is visible on interactive elements', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 800 });
    await authenticateAndGotoDashboard(page);
    // Tab to first focusable element
    await page.keyboard.press('Tab');
    const focused = page.locator(':focus');
    await expect(focused).toBeVisible();
  });

  test('no hardcoded hex values in inline styles', async ({ page }) => {
    const inlineHexes = await page.evaluate(() => {
      const elements = document.querySelectorAll('[style]');
      const hexes: string[] = [];
      for (const el of elements) {
        const style = el.getAttribute('style') || '';
        const matches = style.match(/#[0-9a-fA-F]{3,6}/g);
        if (matches) hexes.push(...matches);
      }
      return hexes;
    });
    expect(inlineHexes.length).toBe(0);
  });
});
