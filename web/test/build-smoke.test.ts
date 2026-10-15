import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { beforeAll, describe, expect, it } from 'vitest';
import { formatMoney } from '../src/lib/format';
import { ACCOUNT_PATHS } from '../src/lib/sitemapPaths.js';

/**
 * Build smoke test (T7b, T7e).
 *
 * Builds the site in fixture mode (`WEB_FIXTURES=1 astro build`) and checks the
 * prerendered output: the ranking table is in the HTML in ranked order with the
 * API's own amounts, the SEO head is complete, the page that ships no island
 * ships no script at all, and the generated `robots.txt` takes its host from
 * `SITE_URL`.
 *
 * `SITE_URL` is passed explicitly so the canonical URLs and the `robots.txt`
 * host are tied to that one variable instead of to the config's fallback.
 */
const webRoot = resolve(fileURLToPath(new URL('.', import.meta.url)), '..');
const dist = join(webRoot, 'dist');
const SITE_URL = 'https://smoke.rindetuplata.test';

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

/** Every HTML file the build emitted, at any depth, except the asset folder. */
function htmlFiles(directory: string): string[] {
  return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const full = join(directory, entry.name);
    if (entry.isDirectory()) return entry.name === '_astro' ? [] : htmlFiles(full);
    return entry.name.endsWith('.html') ? [full] : [];
  });
}

/** Only the text a reader sees: no script, no style, no markup. */
function renderedText(page: string): string {
  return page
    .replace(/<script[\s\S]*?<\/script>/g, ' ')
    .replace(/<style[\s\S]*?<\/style>/g, ' ')
    .replace(/<[^>]+>/g, ' ')
    .replaceAll('&nbsp;', '\u00a0')
    .replaceAll('&#160;', '\u00a0');
}

/** The visible text of every row header cell, in document order. */
function rowHeaderTexts(page: string): string[] {
  return [...page.matchAll(/<th[^>]*scope="row"[^>]*>([\s\S]*?)<\/th>/g)].map((match) =>
    renderedText(match[1]).replace(/\s+/g, ' ').trim(),
  );
}

beforeAll(() => {
  try {
    execFileSync('npm', ['run', 'build'], {
      cwd: webRoot,
      env: { ...process.env, WEB_FIXTURES: '1', SITE_URL },
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
  it('renders the heading the product promises, with "plata" as the one serif accent', () => {
    const page = html('index.html');
    const h1 = /<h1 class="type-display">([\s\S]*?)<\/h1>/.exec(page);
    expect(h1, 'the home H1').not.toBeNull();
    expect(renderedText(h1![1]).replace(/\s+/g, ' ').trim()).toBe('¿Dónde rinde tu plata hoy?');
    expect(h1![1]).toContain('<span class="serif-accent">plata</span>');
  });

  it('paints the best option as the only glass card, inside the field it blurs', () => {
    const page = html('index.html');

    // One glass element per page (T13), over the cobalt glow and hairline grid
    // its `glass-field` parent paints, with the cobalt rail of the leader.
    expect(page.match(/class="glass /g) ?? []).toHaveLength(1);
    expect(page).toContain('glass-field');
    expect(page).toContain('border-s-accent');
    expect(renderedText(page)).toContain('La mejor de hoy');
  });

  it('offers the amount and term presets as real radios, and keeps the free inputs', () => {
    const page = html('index.html');
    const radios = [...page.matchAll(/<input[^>]*type="radio"[^>]*>/g)].map((match) => match[0]);

    expect(radios).toHaveLength(6);
    for (const [name, values, preselected] of [
      ['monto-preset', ['500000', '1000000', '5000000'], '1000000'],
      ['plazo-preset', ['30', '60', '90'], '30'],
    ] as const) {
      const group = radios.filter((tag) => tag.includes(`name="${name}"`));
      expect(group.map((tag) => /value="([^"]*)"/.exec(tag)?.[1])).toEqual([...values]);
      // The default query of the build is the checked preset, and only one.
      const checked = group.filter((tag) => /\bchecked=""/.test(tag));
      expect(checked).toHaveLength(1);
      expect(checked[0]).toContain(`value="${preselected}"`);
    }
    // The capsule control paints its thumb with the roles the design names.
    expect(page).toContain('bg-thumb');
    expect(page).toContain('text-on-thumb');
    expect(page).toContain('bg-subtle-surface');
    // The prototype's presets are shortcuts: the reader's own inputs stay.
    expect(page).toMatch(/<input[^>]*id="monto"[^>]*type="number"/);
    expect(page).toMatch(/<input[^>]*id="dias"[^>]*type="number"/);
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

  it('contains the table inside its scroll region and keeps its focus ring visible (T12e)', () => {
    const region = /<div class="([^"]*)" role="region" aria-label="Ranking para/.exec(html('index.html'));
    expect(region, 'ranking scroll region').not.toBeNull();
    // `relative` keeps the sr-only spans inside the scroller (no page-level sideways scroll at 320px);
    // an inset outline is never clipped by the card's overflow-hidden.
    expect(region![1].split(/\s+/)).toEqual(expect.arrayContaining(['relative', 'focus-visible:-outline-offset-2']));
  });

  it('renders their earned amounts with the API values', () => {
    const page = html('index.html');
    for (const entry of fixture.ranked.slice(0, 3)) {
      expect(page).toContain(formatMoney(entry.earned));
    }
  });

  it('numbers the board with a mono 01-08 gutter and keeps the rest behind a disclosure', () => {
    const page = html('index.html');
    const headers = rowHeaderTexts(page);
    const expected = fixture.ranked.slice(0, 3);
    expect(headers.length).toBeGreaterThanOrEqual(expected.length);
    for (const [index, entry] of expected.entries()) {
      const position = String(index + 1).padStart(2, '0');
      expect(headers[index], `row ${index + 1}`).toMatch(new RegExp(`^${position}\\s`));
      expect(headers[index], `row ${index + 1}`).toContain(entry.name);
    }

    // Nothing is dropped: the first table is the day's eight, the second one
    // continues the gutter inside one disclosure.
    const boards = [...page.matchAll(/<th[^>]*scope="row"/g)].map((match) => match.index ?? -1);
    expect(boards).toHaveLength(fixture.ranked.length);
    const summary = page.indexOf('opciones siguientes');
    expect(summary).toBeGreaterThan(-1);
    expect(boards[7]).toBeLessThan(summary);
    expect(boards[8]).toBeGreaterThan(summary);
    expect(headers[8]).toMatch(/^09\s/);
  });

  it('states where the data comes from (S11)', () => {
    const page = html('index.html');
    expect(page).toContain('ArgentinaDatos');
    expect(page).toContain('Dato del');
  });

  it('carries the canonical link, the social tags and the WebSite JSON-LD', () => {
    const page = html('index.html');
    expect(page).toContain(`rel="canonical" href="${SITE_URL}/"`);
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

describe('rendered copy', () => {
  it('never shows an internal spec id to the reader', () => {
    const offenders = htmlFiles(dist).flatMap((file) => {
      const match = /\(S\d+\)/.exec(renderedText(readFileSync(file, 'utf8')));
      return match === null ? [] : [`${relative(dist, file)}: ${match[0]}`];
    });
    expect(offenders).toEqual([]);
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

describe('robots.txt', () => {
  it('points at the sitemap of the site the build was configured with', () => {
    const robots = readFileSync(join(dist, 'robots.txt'), 'utf8');
    expect(robots).toContain(`Sitemap: ${SITE_URL}/sitemap-index.xml`);
  });

  it('leaves the noindex account pages crawlable, so the crawler can read their noindex', () => {
    const robots = readFileSync(join(dist, 'robots.txt'), 'utf8');
    for (const path of ACCOUNT_PATHS) {
      expect(robots, `${path} must not be disallowed`).not.toContain(`Disallow: ${path}`);
    }
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
      '.type-label',
      '.type-data',
      '.type-figure',
      '.type-index',
      '.serif-accent',
      '.glass-field',
      '.bg-thumb',
      '.text-on-thumb',
    ]) {
      expect(css, `${selector} is missing from the built CSS`).toContain(selector);
    }
  });
});
