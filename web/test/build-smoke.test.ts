import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { beforeAll, describe, expect, it } from 'vitest';
import { formatMoney } from '../src/lib/format';

/**
 * Build smoke test (T7b).
 *
 * Builds the site in fixture mode (`WEB_FIXTURES=1 astro build`) and checks the
 * prerendered output: the ranking table is in the HTML in ranked order with the
 * API's own amounts, the SEO head is complete, and the page that ships no island
 * ships no script at all.
 */
const webRoot = resolve(fileURLToPath(new URL('.', import.meta.url)), '..');
const dist = join(webRoot, 'dist');

const fixture = JSON.parse(
  readFileSync(join(webRoot, 'fixtures/ranking-default.json'), 'utf8'),
) as {
  ranked: Array<{ name: string; earned: number }>;
};

/** React may escape a non-breaking space; normalise before comparing. */
function normalize(html: string): string {
  return html.replaceAll('&nbsp;', '\u00a0').replaceAll('&#160;', '\u00a0');
}

function html(path: string): string {
  return normalize(readFileSync(join(dist, path), 'utf8'));
}

beforeAll(() => {
  try {
    execFileSync('npm', ['run', 'build'], {
      cwd: webRoot,
      env: { ...process.env, WEB_FIXTURES: '1' },
      stdio: 'pipe',
    });
  } catch (error) {
    const failure = error as { stdout?: Buffer; stderr?: Buffer };
    throw new Error(
      `fixture build failed:\n${failure.stdout?.toString() ?? ''}\n${failure.stderr?.toString() ?? ''}`,
    );
  }
}, 300_000);

describe('prerendered home page', () => {
  it('renders the heading the product promises', () => {
    expect(html('index.html')).toContain('¿Dónde rinde tu plata hoy?');
  });

  it('renders the first three ranked options in order', () => {
    const page = html('index.html');
    const positions = fixture.ranked
      .slice(0, 3)
      .map((entry) => page.indexOf(entry.name));

    for (const [index, position] of positions.entries()) {
      expect(position, `option ${index + 1} is missing from the page`).toBeGreaterThan(-1);
    }
    expect(positions[0]).toBeLessThan(positions[1]);
    expect(positions[1]).toBeLessThan(positions[2]);
  });

  it('renders their earned amounts with the API values', () => {
    const page = html('index.html');
    for (const entry of fixture.ranked.slice(0, 3)) {
      expect(page).toContain(formatMoney(entry.earned));
    }
  });

  it('states where the data comes from (S11)', () => {
    const page = html('index.html');
    expect(page).toContain('ArgentinaDatos');
    expect(page).toContain('Dato del');
  });

  it('carries the canonical link, the social tags and the WebSite JSON-LD', () => {
    const page = html('index.html');
    expect(page).toContain('rel="canonical" href="http://localhost:4321/"');
    expect(page).toContain('property="og:title"');
    expect(page).toContain('name="twitter:card"');
    expect(page).toContain('application/ld+json');
    expect(page).toContain('"@type":"WebSite"');
    expect(page).toContain('lang="es-AR"');
  });

  it('hydrates the explorer island', () => {
    expect(html('index.html')).toContain('<script');
  });
});

describe('prerendered methodology page', () => {
  it('exists', () => {
    expect(existsSync(join(dist, 'metodologia/index.html'))).toBe(true);
  });

  it('ships no script tag at all', () => {
    expect(html('metodologia/index.html')).not.toContain('<script');
  });
});

describe('error page', () => {
  it('is exported for the static host', () => {
    expect(existsSync(join(dist, '404.html'))).toBe(true);
    expect(html('404.html')).toContain('No encontramos esta página');
  });
});

describe('stylesheet', () => {
  it('emits the token utilities the markup uses', () => {
    const cssFiles = readdirSync(join(dist, '_astro')).filter((file) => file.endsWith('.css'));
    expect(cssFiles.length).toBeGreaterThan(0);
    const css = cssFiles
      .map((file) => readFileSync(join(dist, '_astro', file), 'utf8'))
      .join('\n');

    for (const selector of [
      '.bg-surface',
      '.bg-surface-raised',
      '.text-muted',
      '.tabular-nums',
      '.px-gutter',
      '.text-display',
    ]) {
      expect(css, `${selector} is missing from the built CSS`).toContain(selector);
    }
  });
});
