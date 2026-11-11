import { defineConfig, devices } from '@playwright/test'

// End-to-end tests against the running Compose stack (gateway on :8080). Playwright starts the
// production build with `vite preview` on :5173, which proxies /api like the dev server; a dev server
// already running there is reused. Screenshots of every screen are written when SCREENSHOTS=1.
export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  workers: 1,
  forbidOnly: !!process.env.CI,
  retries: 0,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:5173',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    command: 'npm run build && npx vite preview',
    url: 'http://localhost:5173',
    reuseExistingServer: true,
    timeout: 180_000,
  },
})
