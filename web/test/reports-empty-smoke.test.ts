import { execFileSync } from 'node:child_process';
import { existsSync, mkdirSync, mkdtempSync, readFileSync, readdirSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { beforeAll, describe, expect, it } from 'vitest';

/**
 * The reports while no closed month is covered: the history starts on 2026-10-14, so the first full month
 * is November 2026 and nothing can be reported before INDEC publishes it. `/informes/` must say so honestly instead of
 * showing an empty list, and no month page may exist. The build points `WEB_REPORTS_FIXTURES_DIR` at an empty month
 * list and writes to its own output directory, so it never shares `dist` with the other smoke test.
 */
const webRoot = resolve(fileURLToPath(new URL('.', import.meta.url)), '..');
const SITE_URL = 'https://smoke.rindetuplata.test';
// Astro renames assets from its own cache into the output directory, which fails across devices: the output lives under
// node_modules/.cache (ignored by git), on the same device as the project, in a directory of its own.
const cache = join(webRoot, 'node_modules', '.cache');
mkdirSync(cache, { recursive: true });
const out = mkdtempSync(join(cache, 'reports-empty-smoke-'));
const fixtures = mkdtempSync(join(tmpdir(), 'rindetuplata-empty-fixtures-'));

function renderedText(page: string): string {
  return page
    .replace(/<script[\s\S]*?<\/script>/g, ' ')
    .replace(/<style[\s\S]*?<\/style>/g, ' ')
    .replace(/<[^>]+>/g, ' ')
    .replaceAll('&nbsp;', ' ')
    .replace(/\s+/g, ' ');
}

beforeAll(() => {
  writeFileSync(join(fixtures, 'index.json'), JSON.stringify({ months: [] }));
  try {
    execFileSync('npm', ['run', 'build', '--', '--outDir', out], {
      cwd: webRoot,
      env: { ...process.env, WEB_FIXTURES: '1', SITE_URL, WEB_REPORTS_FIXTURES_DIR: fixtures },
      stdio: 'pipe',
    });
  } catch (error) {
    const failure = error as { stdout?: Buffer; stderr?: Buffer };
    throw new Error(
      `fixture build failed:\n${failure.stdout?.toString() ?? ''}\n${failure.stderr?.toString() ?? ''}`,
    );
  }
}, 300_000);

describe('report index without a covered month', () => {
  it('says why there is no report yet, in Spanish, with the dates', () => {
    const text = renderedText(readFileSync(join(out, 'informes/index.html'), 'utf8'));

    expect(text).toContain('Quién le ganó a la inflación');
    expect(text).toContain('14/10/2026');
    expect(text).toContain('noviembre de 2026');
    expect(text).toContain('INDEC');
    expect(text).toContain('mediados de diciembre');
  });

  it('lists no month and builds no month page', () => {
    const page = readFileSync(join(out, 'informes/index.html'), 'utf8');
    const directories = readdirSync(join(out, 'informes'), { withFileTypes: true }).filter((entry) =>
      entry.isDirectory(),
    );

    expect(page).not.toMatch(/href="\/informes\/\d{4}-\d{2}\/"/);
    expect(directories).toEqual([]);
  });

  it('keeps the header link and links the methodology', () => {
    const page = readFileSync(join(out, 'informes/index.html'), 'utf8');

    expect(page).toMatch(/<a href="\/informes\/" aria-current="page"/);
    expect(page).toContain('href="/metodologia/"');
  });

  it('still builds the rest of the site', () => {
    expect(existsSync(join(out, 'index.html'))).toBe(true);
    expect(existsSync(join(out, 'metodologia/index.html'))).toBe(true);
  });
});
