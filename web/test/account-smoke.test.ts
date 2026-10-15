import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { beforeAll, describe, expect, it } from 'vitest';

/**
 * Build smoke test for the account pages (T7d).
 *
 * The three pages are pure markup shells around client-only islands, so there is
 * no logic to unit-test here: this builds the site in fixture mode and checks
 * what the static host would serve — the page exists, it keeps itself out of the
 * index, it carries its H1 and a mount point for its island, and it holds no
 * personal or build-time API data.
 *
 * It builds to its own outDir because the T7b smoke test builds to `dist/` and
 * Vitest runs test files in parallel; two builds sharing one outDir would race
 * on Astro's `emptyDir`.
 */
const webRoot = resolve(fileURLToPath(new URL('.', import.meta.url)), '..');
const OUT_DIR = '.astro/account-smoke-dist';
const dist = join(webRoot, OUT_DIR);

interface PageCheck {
  label: string;
  path: string;
  /** The exact H1 text the page promises. */
  h1: string;
  /** The id of the element the island mounts into. */
  mount: string;
  /** The id of the logout island's mount point, on the pages that have a session. */
  logout?: string;
}

const PAGES: PageCheck[] = [
  { label: 'ingresar', path: 'ingresar/index.html', h1: 'Ingresar', mount: 'ingresar-island' },
  {
    label: 'alertas',
    path: 'alertas/index.html',
    h1: 'Tus alertas',
    mount: 'alertas-island',
    logout: 'alertas-logout',
  },
  {
    label: 'admin/tasas',
    path: 'admin/tasas/index.html',
    h1: 'Tasas de billeteras',
    mount: 'admin-tasas-island',
    logout: 'admin-tasas-logout',
  },
];

/** A row only the recorded ranking has: proof the pages hold no build-time API data. */
const fixture = JSON.parse(
  readFileSync(join(webRoot, 'fixtures/ranking-default.json'), 'utf8'),
) as { ranked: Array<{ name: string }> };
const FIXTURE_INSTRUMENT = fixture.ranked[0].name;

const EMAIL = /[\w.+-]+@[\w-]+\.[\w.]+/;

function normalize(html: string): string {
  return html.replaceAll('&nbsp;', '\u00a0').replaceAll('&#160;', '\u00a0');
}

function html(path: string): string {
  return normalize(readFileSync(join(dist, path), 'utf8'));
}

beforeAll(() => {
  try {
    execFileSync('npm', ['run', 'build', '--', '--outDir', OUT_DIR], {
      cwd: webRoot,
      // Vitest runs with NODE_ENV=test, which would make Vite build a
      // development bundle (dev JSX runtime, no minification). The smoke test
      // must inspect the artifact the CDN serves, so force production.
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

describe('the built bundle', () => {
  it('is a production build, the one the CDN would serve', () => {
    const chunks = readdirSync(join(dist, '_astro'));
    expect(chunks.some((file) => file.includes('jsx-dev-runtime'))).toBe(false);
  });
});

describe.each(PAGES)('account page $label', ({ path, h1, mount, logout }) => {
  it('is exported for the static host', () => {
    expect(existsSync(join(dist, path))).toBe(true);
  });

  it('keeps itself out of the index', () => {
    expect(html(path)).toContain('<meta name="robots" content="noindex, nofollow">');
  });

  it('renders its H1', () => {
    expect(html(path)).toContain(`>${h1}</h1>`);
  });

  it('has a mount point and hydrates a client-only island', () => {
    const page = html(path);
    expect(page).toContain(`id="${mount}"`);
    expect(page).toContain('<astro-island');
    expect(page).toContain('client="only"');
    if (logout !== undefined) {
      expect(page).toContain(`id="${logout}"`);
    }
  });

  it('holds no build-time personal data or API data', () => {
    const page = html(path);
    expect(page).not.toMatch(EMAIL);
    expect(page).not.toContain(FIXTURE_INSTRUMENT);
  });
});
