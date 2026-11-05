import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { beforeAll, describe, expect, it } from 'vitest';
import { displayName } from '../src/lib/displayName';
import { formatDate, formatPercent } from '../src/lib/format';
import { loadBankList, loadBankPages, bestRateSentence } from '../src/lib/entities';
import { loadInstrumentPages } from '../src/lib/pages';
import { instrumentSlug } from '../src/lib/slug';

/**
 * Bank entity pages build smoke test.
 *
 * Builds the site in fixture mode to its own outDir (the other smoke tests build to `dist/` and `.astro/…`; two
 * builds sharing one outDir would race on Astro's `emptyDir`) and checks what the static host would serve:
 * `/bancos/` lists every bank of the fixture with its non-client TNA and links its page, each `/bancos/<slug>/`
 * states today's rate, the best-rate count, the chart, the source and dates and the links the brief names, the
 * header nav reaches it, the sitemap lists it, and EVERY internal href of EVERY built page resolves to a built file.
 */
const webRoot = resolve(fileURLToPath(new URL('.', import.meta.url)), '..');
const OUT_DIR = '.astro/entities-smoke-dist';
const dist = join(webRoot, OUT_DIR);
const FIXTURES = { WEB_FIXTURES: '1' } as const;

const banks = await loadBankList({ env: FIXTURES });
const details = await loadBankPages({ env: FIXTURES });
const instrumentPages = await loadInstrumentPages({ env: FIXTURES });

function decode(page: string): string {
  return page
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

/** Only the text a reader sees: no script, no style, no markup, whitespace collapsed. */
function visibleText(page: string): string {
  return page
    .replace(/<script[\s\S]*?<\/script>/g, ' ')
    .replace(/<style[\s\S]*?<\/style>/g, ' ')
    .replace(/<[^>]+>/g, ' ')
    .replace(/\s+/g, ' ');
}

/** The inner HTML of the `<section>` labelled by `id`. */
function section(page: string, id: string): string {
  const match = new RegExp(`<section[^>]*aria-labelledby="${id}"[^>]*>([\\s\\S]*?)</section>`).exec(page);
  expect(match, `section ${id}`).not.toBeNull();
  return match![1];
}

function rowNames(fragment: string): string[] {
  return [...fragment.matchAll(/<th[^>]*scope="row"[^>]*>([\s\S]*?)<\/th>/g)].map((match) =>
    visibleText(match[1]).trim(),
  );
}

/** Everything that says anything on a page: title, description, JSON-LD and the visible article. */
function wordsOf(page: string): string {
  const head = [
    /<title>([\s\S]*?)<\/title>/.exec(page)?.[1] ?? '',
    /<meta name="description" content="([^"]*)"/.exec(page)?.[1] ?? '',
    ...[...page.matchAll(/<meta property="og:(?:title|description)" content="([^"]*)"/g)].map((match) => match[1]),
    ...[...page.matchAll(/<script type="application\/ld\+json">([\s\S]*?)<\/script>/g)].map((match) => match[1]),
  ];
  return [...head, visibleText(/<article[\s\S]*<\/article>/.exec(page)?.[0] ?? '')].join(' ');
}

function htmlFiles(directory: string): string[] {
  return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const full = join(directory, entry.name);
    if (entry.isDirectory()) return entry.name === '_astro' ? [] : htmlFiles(full);
    return entry.name.endsWith('.html') ? [full] : [];
  });
}

/** Whether an internal path is something the static host would serve. */
function resolves(path: string): boolean {
  const clean = decodeURIComponent(path.split(/[?#]/)[0]);
  const target = join(dist, clean);
  if (clean.endsWith('/')) return existsSync(join(target, 'index.html'));
  return existsSync(target) || existsSync(join(target, 'index.html')) || existsSync(`${target}.html`);
}

beforeAll(() => {
  try {
    execFileSync('npm', ['run', 'build', '--', '--outDir', OUT_DIR], {
      cwd: webRoot,
      // Vitest runs with NODE_ENV=test, which would make Vite build a development bundle: force production.
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

describe('/bancos/', () => {
  it('lists every bank with its non-client TNA, highest first, each linking its page', () => {
    const page = html('bancos/index.html');
    expect(page).toContain('<h1 class="type-display">');
    for (const bank of banks) {
      expect(page, bank.slug).toContain(`href="/bancos/${bank.slug}/"`);
      expect(visibleText(page), bank.slug).toContain(displayName(bank.name));
    }
    const rows = rowNames(section(page, 'bancos-de-hoy'));
    expect(rows).toHaveLength(banks.filter((bank) => bank.current).length);
    // REBA pays 24,00% and leads; the two current banks with no non-client rate come last.
    expect(rows[0]).toBe('Reba');
    expect(rows.slice(-2).join(' ')).toContain('BBVA');
    expect(rows.slice(-2).join(' ')).toContain('Banco Galicia');
    expect(visibleText(page)).toContain(formatPercent(0.24));
    expect(visibleText(page)).toContain('No ofrece');
  });

  it('shows names in the body role, links quiet at rest, numbers tabular and right-aligned (D13)', () => {
    const page = html('bancos/index.html');
    const today = section(page, 'bancos-de-hoy');
    const link = /<a[^>]*href="\/bancos\/reba-compania-financiera-s-a\/"[^>]*>/.exec(today)![0];
    expect(link).not.toMatch(/(?<![:-])\bunderline\b(?!-offset)/);
    expect(link).toContain('hover:underline');
    expect(link).toContain('focus-visible:underline');
    const row = /<th[^>]*scope="row"[^>]*>/.exec(today)![0];
    expect(row).toContain('type-body');
    expect(row).not.toMatch(/uppercase|font-mono/);
    expect(today).toMatch(/<td[^>]*class="[^"]*type-data[^"]*text-end/);
    // No page-level disclaimer; the useful links stay.
    expect(visibleText(/<main[\s\S]*<\/main>/.exec(page)![0])).not.toContain('asesoramiento financiero');
    expect(page).toContain('href="/plazo-fijo/"');
  });

  it('keeps a bank whose last rate is older apart from "Las tasas de hoy", after it, with its rate and date', () => {
    const page = html('bancos/index.html');
    const stale = banks.filter((bank) => !bank.current);
    expect(stale.map((bank) => bank.slug)).toEqual(['banco-voii-s-a']);

    const today = section(page, 'bancos-de-hoy');
    expect(visibleText(today)).toContain('Las tasas de hoy');
    expect(today).not.toContain('href="/bancos/banco-voii-s-a/"');
    expect(visibleText(today)).not.toContain('Voii');

    const apart = section(page, 'bancos-anteriores');
    expect(page.indexOf('id="bancos-anteriores"')).toBeGreaterThan(page.indexOf('id="bancos-de-hoy"'));
    expect(visibleText(apart)).not.toMatch(/\bhoy\b/i);
    expect(rowNames(apart)).toEqual(['Banco Voii']);
    expect(apart).toContain('href="/bancos/banco-voii-s-a/"');
    expect(visibleText(apart)).toContain(formatPercent(0.235));
    expect(visibleText(apart)).toContain(formatDate('2026-10-12'));
  });

  it('states the source and the date, and links the methodology', () => {
    const page = html('bancos/index.html');
    expect(visibleText(page)).toContain(`ArgentinaDatos · dato del ${formatDate('2026-10-14')}`);
    expect(page).toContain('href="/metodologia/"');
  });

  it('is reachable from the header nav on every page and marked current on its own page', () => {
    expect(html('index.html')).toContain('href="/bancos/"');
    expect(html('metodologia/index.html')).toContain('href="/bancos/"');
    const nav = /<a href="\/bancos\/"[^>]*aria-current="page"/.test(html('bancos/index.html'));
    expect(nav).toBe(true);
  });

  it('keeps the existing nav links', () => {
    const home = html('index.html');
    for (const href of ['/plazo-fijo/', '/billeteras/', '/fondos/', '/cripto/', '/metodologia/']) {
      expect(home, href).toContain(`href="${href}"`);
    }
  });
});

describe('/bancos/<slug>/', () => {
  it('builds exactly one page per bank of the list', () => {
    expect(readdirSync(join(dist, 'bancos')).filter((entry) => entry !== 'index.html').sort()).toEqual(
      banks.map((bank) => bank.slug).sort(),
    );
  });

  it('has the bank name as H1, today\'s non-client rate, the client rate and the best-rate sentence', () => {
    for (const detail of details.filter((bank) => bank.nonClientTna !== null && bank.current)) {
      const page = html(`bancos/${detail.slug}/index.html`);
      const h1 = /<h1 class="type-display[^"]*">([\s\S]*?)<\/h1>/.exec(page);
      expect(h1, detail.slug).not.toBeNull();
      expect(visibleText(h1![1]).trim(), detail.slug).toBe(displayName(detail.name));
      const text = visibleText(page);
      expect(text, detail.slug).toContain(
        `Hoy: ${formatPercent(detail.nonClientTna as number)} TNA para no clientes`,
      );
      if (detail.clientTna !== null) {
        expect(text, detail.slug).toContain(`${formatPercent(detail.clientTna)} TNA para clientes`);
      } else {
        expect(text, detail.slug).not.toContain('TNA para clientes');
      }
      expect(text, detail.slug).toContain(bestRateSentence(detail.bestDays, detail.observedDays));
    }
    expect(visibleText(html('bancos/reba-compania-financiera-s-a/index.html'))).toContain(
      'Fue la mejor tasa 2 de los 3 días que la vimos en los últimos 90.',
    );
  });

  it('states the legal name once as a quiet line under the H1, only when it differs (D13)', () => {
    const bbva = html('bancos/banco-bbva-argentina-s-a/index.html');
    expect(/<h1 class="type-display[^"]*">([\s\S]*?)<\/h1>/.exec(bbva)![1].trim()).toBe('BBVA');
    expect(visibleText(bbva)).toContain('Nombre legal: BANCO BBVA ARGENTINA S.A.');
    for (const detail of details) {
      const text = visibleText(html(`bancos/${detail.slug}/index.html`));
      expect(text.includes('Nombre legal'), detail.slug).toBe(displayName(detail.name) !== detail.name);
    }
  });

  it('lays the hero and the best-rate block side by side at lg, with a plain hero and no disclaimer (D12, D14)', () => {
    const page = html('bancos/reba-compania-financiera-s-a/index.html');
    expect(page).toMatch(/class="[^"]*\blg:grid-cols-2\b[^"]*"/);
    expect(page).not.toContain('border-s-accent');
    expect(visibleText(/<main[\s\S]*<\/main>/.exec(page)![0])).not.toContain('asesoramiento financiero');
  });

  it('says "hoy" on a current bank page: title, description and headline', () => {
    const page = html('bancos/reba-compania-financiera-s-a/index.html');
    expect(page).toContain('<title>Reba: plazo fijo 24,00% TNA hoy | rindetuplata</title>');
    expect(wordsOf(page)).toContain('Cuánto paga hoy Reba');
    expect(visibleText(page)).toContain('Hoy: 24,00% TNA para no clientes');
  });

  it('shows the last published rate with its date on a bank that is not current, and never says "hoy"', () => {
    const detail = details.find((bank) => bank.slug === 'banco-voii-s-a')!;
    expect(detail.current).toBe(false);
    const page = html('bancos/banco-voii-s-a/index.html');
    const date = formatDate(detail.captureDate);
    const text = visibleText(page);
    expect(text).toContain(
      `Última tasa publicada (${date}): ${formatPercent(detail.nonClientTna as number)} TNA para no clientes`,
    );
    expect(text).not.toContain('Hoy: ');
    expect(wordsOf(page)).not.toMatch(/\bhoy\b/i);
    expect(page).toContain(`<title>Banco Voii: plazo fijo ${formatPercent(0.235)} TNA`);
    expect(/<meta name="description" content="([^"]*)"/.exec(page)?.[1]).toContain(date);
    // The page keeps its best-rate count and its chart.
    expect(text).toContain(bestRateSentence(detail.bestDays, detail.observedDays));
    expect(page).toContain('<svg');
  });

  it('says so when a bank does not offer a plazo fijo to non-clients, and shows its client rate', () => {
    const text = visibleText(html('bancos/banco-bbva-argentina-s-a/index.html'));
    expect(text).toContain('Hoy no ofrece plazo fijo a no clientes');
    expect(text).toContain(`${formatPercent(0.3)} TNA para clientes`);
    expect(text).not.toContain('Hoy: ');
    expect(text).toContain(bestRateSentence(0, 0));
  });

  it('reuses the history chart: an inline SVG with title, description and the data table', () => {
    const page = html('bancos/reba-compania-financiera-s-a/index.html');
    expect(page).toContain('<svg');
    expect(page).toContain('<title id="chart-title-29">');
    expect(page).toContain('<desc id="chart-desc-29">');
    expect(page).toContain('Tasa efectiva anual (TEA)');
    expect(page).toContain('Ver los datos del gráfico en una tabla');
    expect(page.match(/<astro-island/g) ?? []).toHaveLength(0);
  });

  it('shows the chart empty state for a bank with no recorded history', () => {
    const page = html('bancos/banco-bbva-argentina-s-a/index.html');
    expect(page).toContain('Todavía no hay historial para este instrumento');
    expect(page).not.toContain('role="img"');
    // A compact line of muted body text inside the chart area, not a card with a label.
    const figure = /<figure[\s\S]*?<\/figure>/.exec(page)![0];
    expect(figure).not.toContain('type-label');
    expect(figure).toMatch(/<p class="[^"]*type-body[^"]*text-muted[^"]*">/);
  });

  it('states the source and the capture date, and links the methodology', () => {
    for (const detail of details) {
      const page = html(`bancos/${detail.slug}/index.html`);
      expect(visibleText(page), detail.slug).toContain(
        `ArgentinaDatos · dato del ${formatDate(detail.dataDate)}`,
      );
      expect(page, detail.slug).toContain('href="/metodologia/"');
      expect(page, detail.slug).toContain('href="/bancos/"');
    }
  });

  it('links the instrument page of the bank only when one was built', () => {
    for (const detail of details) {
      const page = html(`bancos/${detail.slug}/index.html`);
      const slug = instrumentSlug(detail.name, detail.instrumentId);
      if (instrumentPages.slugs.includes(slug)) {
        expect(page, detail.slug).toContain(`href="/instrumentos/${slug}/"`);
      } else {
        expect(page, detail.slug).not.toContain('href="/instrumentos/');
      }
    }
    // The fixture covers both cases.
    expect(instrumentPages.slugs).toContain('reba-compania-financiera-s-a-29');
    expect(instrumentPages.slugs).not.toContain('banco-bbva-argentina-s-a-3');
  });

  it('carries the canonical link and a BreadcrumbList ending in it', () => {
    const page = html('bancos/banco-cmf-s-a/index.html');
    const canonical = 'http://localhost:4321/bancos/banco-cmf-s-a/';
    expect(page).toContain(`rel="canonical" href="${canonical}"`);
    const blocks = [...page.matchAll(/<script type="application\/ld\+json">(.*?)<\/script>/gs)].map(
      (match) => JSON.parse(match[1]) as { '@type': string; itemListElement?: Array<{ item: string }> },
    );
    const crumb = blocks.find((block) => block['@type'] === 'BreadcrumbList');
    expect(crumb?.itemListElement?.map((item) => item.item)).toEqual([
      'http://localhost:4321/',
      'http://localhost:4321/bancos/',
      canonical,
    ]);
  });
});

describe('sitemap', () => {
  it('lists the bank index and every bank page', () => {
    const locations = readdirSync(dist)
      .filter((file) => /^sitemap.*\.xml$/.test(file))
      .flatMap((file) =>
        [...readFileSync(join(dist, file), 'utf8').matchAll(/<loc>([^<]+)<\/loc>/g)].map((match) => match[1]),
      );
    expect(locations).toContain('http://localhost:4321/bancos/');
    for (const bank of banks) {
      expect(locations).toContain(`http://localhost:4321/bancos/${bank.slug}/`);
    }
  });
});

describe('internal links', () => {
  it('resolve to a built file on every page of the site', () => {
    const broken: string[] = [];
    for (const file of htmlFiles(dist)) {
      const page = decode(readFileSync(file, 'utf8'));
      for (const match of page.matchAll(/\b(?:href|src)="(\/(?!\/)[^"]*)"/g)) {
        if (!resolves(match[1])) broken.push(`${relative(dist, file)} -> ${match[1]}`);
      }
    }
    const unique = [...new Set(broken)].sort();
    expect(unique, `${unique.length} internal links do not resolve:\n${unique.join('\n')}`).toEqual([]);
  });
});
