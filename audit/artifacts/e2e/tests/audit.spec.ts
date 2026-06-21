import { test, expect, Page } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import fs from 'node:fs';

const OUT = 'results';
fs.mkdirSync(`${OUT}/axe`, { recursive: true });
fs.mkdirSync(`${OUT}/shots`, { recursive: true });
fs.mkdirSync(`${OUT}/shots/mobile`, { recursive: true });

const AUTH_PAGES = [
  { path: '/risk', name: 'risk-register' },
  { path: '/dashboard', name: 'dashboard' },
  { path: '/compliance', name: 'compliance' },
  { path: '/cyber', name: 'cyber' },
  { path: '/privacy', name: 'privacy' },
  { path: '/tprm', name: 'tprm' },
  { path: '/bcm', name: 'bcm' },
  { path: '/settings', name: 'settings' },
  { path: '/knowledge-base', name: 'knowledge-base' },
];

const WCAG = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'];

async function doLogin(page: Page) {
  await page.goto('/login');
  await page.fill('#username', 'admin');
  await page.fill('#password', 'Admin@12345');
  await page.locator('form.login-form button[type="submit"]').click();
  await page.waitForURL(/\/risk/, { timeout: 30000 });
}

async function settle(page: Page) {
  await page.waitForLoadState('domcontentloaded').catch(() => {});
  await page.waitForTimeout(2500);
}

// ---------- E2E workflow tests ----------
test('E2E: login success → lands on /risk', async ({ page }) => {
  await doLogin(page);
  expect(page.url()).toMatch(/\/risk/);
  const token = await page.evaluate(() => sessionStorage.getItem('grc.accessToken') || sessionStorage.getItem('accessToken') || Object.keys(sessionStorage).join(','));
  fs.writeFileSync(`${OUT}/session-keys.txt`, String(token));
});

test('E2E: login failure → error shown, stays on /login', async ({ page }) => {
  await page.goto('/login');
  await page.fill('#username', 'admin');
  await page.fill('#password', 'WrongPassword!1');
  await page.locator('form.login-form button[type="submit"]').click();
  await expect(page.locator('.login-error')).toBeVisible({ timeout: 15000 });
  expect(page.url()).toMatch(/\/login/);
});

test('E2E: demo-skip bypass navigates without auth', async ({ page }) => {
  await page.goto('/login');
  // The ghost "skip / continue demo" button calls navigate('/risk') with no login
  const ghost = page.locator('form.login-form button[type="button"]').first();
  await ghost.click();
  await page.waitForTimeout(1500);
  fs.writeFileSync(`${OUT}/demo-bypass-url.txt`, page.url());
});

test('E2E: session persists across reload', async ({ page }) => {
  await doLogin(page);
  await page.reload();
  await settle(page);
  fs.writeFileSync(`${OUT}/reload-url.txt`, page.url());
});

// ---------- a11y + UX screenshots ----------
test('a11y + screenshots across key pages', async ({ page }) => {
  const summary: any[] = [];

  // login page (unauthenticated)
  await page.goto('/login');
  await settle(page);
  await page.screenshot({ path: `${OUT}/shots/login.png`, fullPage: true }).catch(() => {});
  try {
    const r = await new AxeBuilder({ page }).withTags(WCAG).analyze();
    fs.writeFileSync(`${OUT}/axe/login.json`, JSON.stringify(r.violations, null, 2));
    summary.push(sumOf('login', '/login', r.violations));
  } catch (e) { summary.push({ page: 'login', error: String(e) }); }

  await doLogin(page);

  for (const p of AUTH_PAGES) {
    try {
      await page.goto(p.path);
      await settle(page);
      await page.screenshot({ path: `${OUT}/shots/${p.name}.png`, fullPage: true }).catch(() => {});
      const r = await new AxeBuilder({ page }).withTags(WCAG).analyze();
      fs.writeFileSync(`${OUT}/axe/${p.name}.json`, JSON.stringify(r.violations, null, 2));
      summary.push(sumOf(p.name, p.path, r.violations));
    } catch (e) {
      summary.push({ page: p.name, path: p.path, error: String(e) });
    }
  }
  fs.writeFileSync(`${OUT}/axe-summary.json`, JSON.stringify(summary, null, 2));
});

// ---------- responsive screenshots (mobile) ----------
test('responsive: mobile screenshots', async ({ browser }) => {
  const ctx = await browser.newContext({ viewport: { width: 390, height: 844 }, channel: 'chrome' } as any);
  const page = await ctx.newPage();
  await doLogin(page);
  for (const p of [{ path: '/risk', name: 'risk-register' }, { path: '/dashboard', name: 'dashboard' }, { path: '/bcm', name: 'bcm' }]) {
    await page.goto(p.path); await settle(page);
    await page.screenshot({ path: `${OUT}/shots/mobile/${p.name}.png`, fullPage: true }).catch(() => {});
  }
  await ctx.close();
});

function sumOf(name: string, path: string, violations: any[]) {
  const byImpact: Record<string, number> = {};
  const rules: string[] = [];
  for (const v of violations) {
    byImpact[v.impact || 'unknown'] = (byImpact[v.impact || 'unknown'] || 0) + 1;
    rules.push(`${v.id}(${v.nodes?.length || 0})`);
  }
  return { page: name, path, violations: violations.length, byImpact, rules };
}
