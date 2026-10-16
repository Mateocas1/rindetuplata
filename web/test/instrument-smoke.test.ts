import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { beforeAll, describe, expect, it } from 'vitest';
import { CATEGORIES } from '../src/lib/categories';
import { CHART_HEIGHT, CHART_WIDTH } from '../src/lib/chart';
import { CATALOG_LIMIT, loadRanking } from '../src/lib/data';
import { formatDate, formatMoney, formatPercent } from '../src/lib/format';
import { loadInstrumentPages } from '../src/lib/pages';
import { DEFAULT_RANKING_QUERY } from '../src/lib/rankingQuery';
import { instrumentPath, instrumentSlug } from '../src/lib/slug';
import { RANKING_KINDS, type RankedEntry } from '../src/lib/types';

/**
 * Instrument and category build smoke test (T7c).
 *
 * Builds the site in fixture mode and checks the prerendered output: every
 * `href="/instrumentos/…"` on every page resolves to a built file (D1), one page
 * per kind with its H1, an inline SVG chart with a title and a description, the
 * same data as a table, the JSON-LD blocks and no React for the chart; the four
 * category landings with each row linked to a built page; the sitemap with the
 * instrument pages and none of the account paths.
 */
const webRoot = resolve(fileURLToPath(new URL('.', import.meta.url)), '..');
const dist = join(webRoot, 'dist');
const FIXTURES = { WEB_FIXTURES: '1' } as const;

const pageSet = await loadInstrumentPages({ env: FIXTURES });
const categoryRankings = await Promise.all(
  CATEGORIES.map((category) =>
    loadRanking(
      { ...DEFAULT_RANKING_QUERY, kinds: [category.kind] },
      { env: FIXTURES, limit: CATALOG_LIMIT },
    ),
  ),
);

/** One ranked instrument per kind: the page this kind must produce. */
const samplePerKind = RANKING_KINDS.map((kind) => {
  const page = pageSet.pages.find(
    (candidate) => candidate.entry.kind === kind && 'rateUsed' in candidate.entry,
  );
  if (page === undefined || !('rateUsed' in page.entry)) {
    throw new Error(`the fixture has no ranked ${kind}`);
  }
  const entry = page.entry as RankedEntry;
  return {
    kind,
    entry,
    slug: instrumentSlug(entry.name, entry.id),
    href: instrumentPath(entry.name, entry.id),
  };
});

function decode(html: string): string {
  return html
    .replaceAll('&amp;', '&')
    .replaceAll('&quot;', '"')
    .replaceAll('&#39;', "'")
    .replaceAll('&lt;', '<')
    .replaceAll('&gt;', '>')
    .replaceAll('&nbsp;', '\u00a0')
    .replaceAll('&#160;', '\u00a0');
}

function html(path: string): string {
  return decode(readFileSync(join(dist, path), 'utf8'));
}

function instrumentHtml(slug: string): string {
  return html(`instrumentos/${slug}/index.html`);
}

/** Every HTML file the build emitted, at any depth, except the asset folder. */
function htmlFiles(directory: string): string[] {
  return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const full = join(directory, entry.name);
    if (entry.isDirectory()) return entry.name === '_astro' ? [] : htmlFiles(full);
    return entry.name.endsWith('.html') ? [full] : [];
  });
}

/** The structured-data blocks of a page, parsed. */
function jsonLdBlocks(page: string): Array<Record<string, unknown>> {
  return [...page.matchAll(/<script type="application\/ld\+json">(.*?)<\/script>/gs)].map(
    (match) => JSON.parse(match[1]) as Record<string, unknown>,
  );
}

/** Every client JavaScript chunk the build emitted. */
function clientChunkNames(): string[] {
  return readdirSync(join(dist, '_astro')).filter((file) => file.endsWith('.js'));
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

describe('instrument pages', () => {
  it('links only to instrument pages that exist in dist', () => {
    const missing: string[] = [];
    for (const file of htmlFiles(dist)) {
      const page = decode(readFileSync(file, 'utf8'));
      for (const match of page.matchAll(/href="\/instrumentos\/([^"?#]+)"/g)) {
        const href = match[1].replace(/\/+$/, '');
        if (!existsSync(join(dist, 'instrumentos', href, 'index.html'))) {
          missing.push(`dist/${relative(dist, file)} -> /instrumentos/${href}`);
        }
      }
    }
    const broken = [...new Set(missing)].sort();
    expect(broken, `${broken.length} instrument links have no built page:\n${broken.join('\n')}`).toEqual(
      [],
    );
  });

  it('builds one page per instrument any prerendered table can link to', () => {
    const built = readdirSync(join(dist, 'instrumentos'));
    expect(built).toHaveLength(pageSet.slugs.length);
    for (const slug of pageSet.slugs) {
      expect(existsSync(join(dist, 'instrumentos', slug, 'index.html')), slug).toBe(true);
    }
  });

  it('exists once per kind with its own H1, rate, source and dates', () => {
    for (const { entry, slug } of samplePerKind) {
      const page = instrumentHtml(slug);
      expect(page, slug).toContain(`<h1 class="type-display`);
      expect(page, slug).toContain(entry.name);
      expect(page, slug).toContain(formatPercent(entry.rateUsed.fraction));
      // The rate and its unit are separate readout cells, never run together (T13c).
      expect(page, slug).toContain(`<dt class="type-label">${entry.rateUsed.basis}</dt>`);
      expect(page, slug).toContain(`<dd class="type-data">${formatPercent(entry.rateUsed.fraction)}</dd>`);
      expect(page, slug).toContain(entry.source.displayName);
      // Source and data date travel in one quiet provenance line (T13c, L77).
      expect(page, slug).toContain(`· dato del ${formatDate(entry.dataDate)}`);
    }
  });

  it("features the default query's pesos earned as the page figure", () => {
    for (const { entry, slug } of samplePerKind) {
      const page = instrumentHtml(slug);
      // The hero states the same fact as the reference plate: what the default
      // ranking's amount earns in the default term, as the protagonist figure.
      expect(page, slug).toContain('class="type-figure');
      expect(page, slug).toContain(formatMoney(entry.earned));
      expect(page, slug).toContain('en 30 días con');
      // Provenance is one quiet data line, not a wall of small grey prose (L77).
      expect(page, slug).toMatch(/class="type-data text-muted/);
    }
  });

  it('flags the confidence of a curated rate', () => {
    const wallet = pageSet.pages.find(
      (page) => page.entry.kind === 'WALLET' && 'confidence' in page.entry &&
        page.entry.confidence === 'CURATED_MANUAL',
    );
    if (wallet === undefined) throw new Error('the wallet fixture should have a curated rate');
    expect(instrumentHtml(instrumentSlug(wallet.entry.name, wallet.entry.id))).toContain(
      'Carga manual verificada',
    );
  });

  it('renders the history as an inline SVG with a title and a description', () => {
    for (const { entry, slug } of samplePerKind) {
      const page = instrumentHtml(slug);
      expect(page, slug).toContain('<svg');
      expect(page, slug).toContain(`<title id="chart-title-${entry.id}">`);
      expect(page, slug).toContain(`<desc id="chart-desc-${entry.id}">`);
      expect(page, slug).toContain('Inflación mensual (INDEC)');
      expect(page, slug).toContain('role="img"');
    }
  });

  it('draws the chart at its intrinsic pixel size, so axis labels never scale with the viewBox', () => {
    for (const { slug } of samplePerKind) {
      const page = instrumentHtml(slug);
      const tag = page.match(/<svg[^>]*role="img"[^>]*>/)?.[0];
      if (tag === undefined) throw new Error(`${slug} has no chart svg`);
      expect(tag, slug).toContain(`viewBox="0 0 ${CHART_WIDTH} ${CHART_HEIGHT}"`);
      expect(tag, slug).toContain(`width="${CHART_WIDTH}"`);
      expect(tag, slug).toContain(`height="${CHART_HEIGHT}"`);
      expect(tag, slug).not.toContain('w-full');
      // Inside the row-reverse scroller (T12e) the svg is a flex item: shrink-0 keeps its 640px.
      expect(tag, slug).toMatch(/class="[^"]*\bshrink-0\b/);
      // 13px from --text-data: the labels sit on a 1:1 surface, never a scaled one.
      expect(page, slug).toContain('fill-muted type-data');
    }
  });

  it('pins the y-axis outside the chart scroll region (T13e item 8)', () => {
    const page = instrumentHtml(samplePerKind[0].slug);
    const [before, ...rest] = page.split(/<div class="[^"]*" role="region" aria-label="Gráfico:/);
    const region = rest.join('');
    const axis = /<svg[^>]*chart-axis[^>]*>[\s\S]*?<\/svg>/.exec(page)?.[0];

    // The tick labels live in an aria-hidden strip pinned outside the scroller, so
    // a 320px screen keeps the scale while the plot scrolls under it.
    expect(axis, 'the pinned axis').toBeDefined();
    expect(axis).toContain('aria-hidden="true"');
    expect(axis).toContain('text-anchor="end"');
    expect(axis).toContain('fill-muted type-data');
    expect(before, 'the axis is not inside the scroller').toContain('chart-axis');
    expect(region, 'no y tick label scrolls away').not.toContain('text-anchor="end"');
  });

  it('keeps the hero rail as the only cobalt decoration, and Simular a peer action (T13e item 4)', () => {
    const page = instrumentHtml(samplePerKind[0].slug);
    const tokens = [...page.matchAll(/class="([^"]*)"/g)].flatMap((match) =>
      match[1].split(/\s+/),
    );

    // The hero rail is the page's cobalt moment; the chart's series colour is
    // data, not decoration.
    expect(tokens.filter((token) => token === 'border-s-accent')).toHaveLength(1);
    expect(tokens.filter((token) => /^bg-accent/.test(token))).toEqual([]);
    // The simulator's submit is a peer action, never the filled accent button.
    expect(tokens).toContain('btn-secondary');
    expect(tokens).not.toContain('btn-primary');
  });

  it('repeats the chart data in a secondary table', () => {
    for (const { slug } of samplePerKind) {
      const page = instrumentHtml(slug);
      expect(page, slug).toContain('Ver los datos del gráfico en una tabla');
      expect(page, slug).toContain('<table');
      expect(page, slug).toContain('<caption');
      expect(page.indexOf('<svg'), slug).toBeLessThan(page.indexOf('<table'));
      expect(page.indexOf('<details'), slug).toBeLessThan(page.indexOf('<table'));
    }
  });

  it('carries a BreadcrumbList whose last item is the canonical URL', () => {
    for (const { kind, slug, href } of samplePerKind) {
      const page = instrumentHtml(slug);
      const canonical = `http://localhost:4321${href}`;
      expect(page, slug).toContain(`rel="canonical" href="${canonical}"`);
      const crumb = jsonLdBlocks(page).find((block) => block['@type'] === 'BreadcrumbList');
      if (crumb === undefined) throw new Error(`${slug} has no BreadcrumbList`);
      const items = crumb.itemListElement as Array<{ position: number; item: string }>;
      expect(items.map((item) => item.position), slug).toEqual([1, 2, 3]);
      expect(items[0].item, slug).toBe('http://localhost:4321/');
      const category = CATEGORIES.find((candidate) => candidate.kind === kind);
      if (category === undefined) throw new Error(`no category for ${kind}`);
      // The middle crumb is the category page, with the same URL shape as the sitemap.
      expect(items[1].item, slug).toBe(`http://localhost:4321${category.path}`);
      expect(items.at(-1)?.item, slug).toBe(canonical);
    }
  });

  it('marks the current crumb for assistive technology', () => {
    for (const { slug } of samplePerKind) {
      expect(instrumentHtml(slug), slug).toContain('aria-current="page"');
    }
  });

  it('carries a FinancialProduct with the API rate and only schema.org properties', () => {
    for (const { entry, slug } of samplePerKind) {
      const page = instrumentHtml(slug);
      const product = jsonLdBlocks(page).find((block) => block['@type'] === 'FinancialProduct');
      if (product === undefined) throw new Error(`${slug} has no FinancialProduct`);
      expect(product.name, slug).toBe(entry.name);
      expect(product.interestRate, slug).toBe(
        Number((entry.rateUsed.fraction * 100).toFixed(2)),
      );
      const serialized = JSON.stringify(product);
      expect(serialized, slug).not.toContain('rateBasis');
      expect(serialized, slug).not.toContain('inLanguage');
    }
  });

  it('links back to its category and to the methodology', () => {
    for (const { kind, slug } of samplePerKind) {
      const page = instrumentHtml(slug);
      const category = CATEGORIES.find((candidate) => candidate.kind === kind);
      if (category === undefined) throw new Error(`no category for ${kind}`);
      expect(page, slug).toContain(`href="${category.path}"`);
      expect(page, slug).toContain('href="/metodologia/"');
    }
  });

  it('ships no React for the chart: the chart is inline HTML and one island hydrates the simulator', () => {
    const chunks = clientChunkNames();
    for (const { slug } of samplePerKind) {
      const page = instrumentHtml(slug);
      expect(page.match(/<astro-island/g) ?? [], slug).toHaveLength(1);
      expect(page, slug).toContain('<svg');
      expect(page, slug).toContain('Simulá con tu monto y tu plazo');
    }
    expect(chunks.some((file) => file.startsWith('HistoryChart'))).toBe(false);
    expect(chunks.some((file) => file.startsWith('SimulatorIsland'))).toBe(true);
  });
});

describe('category pages', () => {
  it('exists for every kind with its own H1, title and description', () => {
    for (const category of CATEGORIES) {
      const page = html(`${category.slug}/index.html`);
      expect(page, category.slug).toContain(category.h1);
      expect(page, category.slug).toContain(`<title>${category.title}</title>`);
      expect(page, category.slug).toContain('name="description"');
    }
  });

  it('links every ranked row to its instrument page, with no first-rows shortcut', () => {
    for (const [index, category] of CATEGORIES.entries()) {
      const page = html(`${category.slug}/index.html`);
      const ranking = categoryRankings[index];
      for (const entry of ranking.ranked) {
        expect(page, `${category.slug}: ${entry.name}`).toContain(
          `href="${instrumentPath(entry.name, entry.id)}"`,
        );
      }
    }
  });

  it('groups the ranking it shows under its own heading with a breadcrumb', () => {
    for (const category of CATEGORIES) {
      const page = html(`${category.slug}/index.html`);
      expect(page, category.slug).toContain('El ranking de hoy');
      const crumb = jsonLdBlocks(page).find((block) => block['@type'] === 'BreadcrumbList');
      if (crumb === undefined) throw new Error(`${category.slug} has no BreadcrumbList`);
      const items = crumb.itemListElement as Array<{ item: string }>;
      expect(items.at(-1)?.item, category.slug).toBe(
        `http://localhost:4321${category.path}`,
      );
    }
  });

  it('ships no island at all: the table is static markup', () => {
    for (const category of CATEGORIES) {
      expect(html(`${category.slug}/index.html`).match(/<astro-island/g) ?? []).toHaveLength(0);
    }
  });

  it('is reachable from the header nav on every page, marked current on its own page', () => {
    const home = html('index.html');
    for (const category of CATEGORIES) {
      expect(home, category.slug).toContain(`href="${category.path}"`);
      expect(home, category.slug).toContain(category.navLabel);
      expect(html(`${category.slug}/index.html`), category.slug).toContain('aria-current="page"');
    }
  });
});

describe('sitemap and robots', () => {
  function sitemapLocations(): string[] {
    const files = readdirSync(dist).filter((file) => /^sitemap.*\.xml$/.test(file));
    return files.flatMap((file) =>
      [...readFileSync(join(dist, file), 'utf8').matchAll(/<loc>([^<]+)<\/loc>/g)].map(
        (match) => match[1],
      ),
    );
  }

  it('lists every instrument page and every category page', () => {
    const locations = sitemapLocations();
    const instrumentUrls = locations.filter((url) => url.includes('/instrumentos/'));
    expect(instrumentUrls).toHaveLength(pageSet.slugs.length);
    for (const { href } of samplePerKind) {
      expect(instrumentUrls).toContain(`http://localhost:4321${href}`);
    }
    for (const category of CATEGORIES) {
      expect(locations).toContain(`http://localhost:4321${category.path}`);
    }
  });

  it('excludes the account paths that carry noindex (T7d)', () => {
    const locations = sitemapLocations();
    for (const excluded of ['/ingresar', '/alertas', '/admin']) {
      expect(
        locations.some((url) => url.includes(excluded)),
        `${excluded} must stay out of the sitemap`,
      ).toBe(false);
    }
  });

  it('ships a robots.txt that points at the sitemap', () => {
    const robots = readFileSync(join(dist, 'robots.txt'), 'utf8');
    expect(robots).toContain('User-agent: *');
    expect(robots).toContain('Sitemap: ');
    expect(robots).toContain('/sitemap-index.xml');
  });
});
