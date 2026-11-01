import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { beforeAll, describe, expect, it } from 'vitest';

/**
 * Build smoke test for `/claves-api/` (T6d, S6, L32 b).
 *
 * The page is a markup shell around a client-only island, so this builds the site in fixture mode and checks what the
 * static host would serve: the page exists, keeps itself out of the index, carries its H1 and the mount points of its
 * islands, holds no personal data and no key, and the site header links it and marks it as the current page.
 *
 * It builds to its own outDir: Vitest runs test files in parallel and two builds sharing one outDir would race on
 * Astro's `emptyDir`.
 */
const webRoot = resolve(fileURLToPath(new URL('.', import.meta.url)), '..');
const OUT_DIR = '.astro/apikeys-smoke-dist';
const dist = join(webRoot, OUT_DIR);
const PAGE = 'claves-api/index.html';

const EMAIL = /[\w.+-]+@[\w-]+\.[\w.]+/;
const API_KEY = /rtp_[A-Za-z0-9_-]{20,}/;

function html(path: string): string {
  return readFileSync(join(dist, path), 'utf8').replaceAll('&nbsp;', '\u00a0').replaceAll('&#160;', '\u00a0');
}

beforeAll(() => {
  try {
    execFileSync('npm', ['run', 'build', '--', '--outDir', OUT_DIR], {
      cwd: webRoot,
      // Vitest runs with NODE_ENV=test, which would make Vite build a development bundle; inspect the real artifact.
      env: { ...process.env, WEB_FIXTURES: '1', NODE_ENV: 'production' },
      stdio: 'pipe',
    });
  } catch (error) {
    const failure = error as { stdout?: Buffer; stderr?: Buffer };
    throw new Error(
      `fixture build failed:\n${failure.stdout?.toString() ?? ''}\n${failure.stderr?.toString() ?? ''}`,
    );
  }
}, 300_000);

describe('the API keys page', () => {
  it('is exported for the static host', () => {
    expect(existsSync(join(dist, PAGE))).toBe(true);
  });

  it('keeps itself out of the index', () => {
    expect(html(PAGE)).toContain('<meta name="robots" content="noindex, nofollow">');
  });

  it('renders its H1', () => {
    expect(html(PAGE)).toContain('>Claves de API</h1>');
  });

  it('has mount points and hydrates client-only islands for the session and the logout', () => {
    const page = html(PAGE);
    expect(page).toContain('id="claves-api-island"');
    expect(page).toContain('id="claves-api-logout"');
    expect(page).toContain('<astro-island');
    expect(page).toContain('client="only"');
  });

  it('holds no personal data and no key', () => {
    const page = html(PAGE);
    expect(page).not.toMatch(EMAIL);
    expect(page).not.toMatch(API_KEY);
  });

  it('is linked from the site header of every page and marked as current on its own page', () => {
    for (const path of ['index.html', 'metodologia/index.html']) {
      expect(html(path), path).toMatch(/<a href="\/claves-api\/"[^>]*>\s*Claves API/);
    }
    expect(html(PAGE)).toMatch(/<a href="\/claves-api\/" aria-current="page"/);
    expect(html('index.html')).not.toMatch(/<a href="\/claves-api\/" aria-current=/);
  });
});
