import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './tests',
  timeout: 120000,
  expect: { timeout: 15000 },
  retries: 0,
  workers: 1,
  reporter: [['json', { outputFile: 'results/report.json' }], ['list']],
  use: {
    baseURL: 'http://localhost:5173',
    headless: true,
    channel: 'chrome',
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
    viewport: { width: 1366, height: 900 },
    ignoreHTTPSErrors: true,
  },
});
