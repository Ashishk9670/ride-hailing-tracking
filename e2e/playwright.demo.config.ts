import { defineConfig } from '@playwright/test';

// Captures README media (docs/img). Run with: npx playwright test -c playwright.demo.config.ts
export default defineConfig({
  testDir: './demo',
  timeout: 180_000,
  workers: 1,
  reporter: [['list']],
  outputDir: 'demo-results',
  use: {
    baseURL: process.env.BASE_URL ?? 'http://localhost:8080',
    viewport: { width: 1280, height: 720 },
    video: { mode: 'on', size: { width: 1280, height: 720 } },
  },
  projects: [{ name: 'chromium', use: { browserName: 'chromium' } }],
});
