import { defineConfig } from '@playwright/test';

/**
 * End-to-end smoke runner (T7e).
 *
 * It drives the product on the local Compose stack through Caddy — one origin, the same shape as the
 * deployed site — so it covers what the Vitest build smokes cannot: the account islands talking to the
 * real API, the magic link that really travels through Mailpit, and the session cookie over one
 * origin. `deploy/e2e.sh` brings the stack up, builds the site against it and then runs this.
 *
 * `E2E_BASE_URL` is the published port of the `web` service; it must match the URL the stack's
 * `RINDETUPLATA_AUTH_MAGICLINKURL` points at, because the spec follows the link the email carries.
 */
const baseURL = process.env.E2E_BASE_URL ?? 'http://localhost:4321';

export default defineConfig({
  testDir: './e2e',
  // One browser, one account and one alert at a time: every spec shares the stack's database.
  fullyParallel: false,
  workers: 1,
  forbidOnly: process.env.CI !== undefined,
  reporter: [['list']],
  timeout: 60_000,
  expect: { timeout: 10_000 },
  use: {
    baseURL,
    // The bundled Chromium: `devices['Desktop Chrome']` asks for a channel (a locally installed
    // Google Chrome) that a clean machine and a CI runner do not have.
    browserName: 'chromium',
    trace: 'retain-on-failure',
  },
});
