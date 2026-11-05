import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { beforeAll, describe, expect, it } from 'vitest';
import { displayName } from '../src/lib/displayName';
import { formatMoney } from '../src/lib/format';
import { ACCOUNT_PATHS } from '../src/lib/sitemapPaths.js';

/**
 * Build smoke test.
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

/** The `<header>` landmark of a built page. */
function headerOf(page: string): string {
  return /<header class="sticky[\s\S]*?<\/header>/.exec(page)?.[0] ?? '';
}

/** The `<footer>` landmark of a built page. */
function footerOf(page: string): string {
  return /<footer[\s\S]*?<\/footer>/.exec(page)?.[0] ?? '';
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
  it('renders the heading the product promises as plain text (D1)', () => {
    const page = html('index.html');
    const h1 = /<h1 class="type-display">([\s\S]*?)<\/h1>/.exec(page);
    expect(h1, 'the home H1').not.toBeNull();
    expect(renderedText(h1![1]).replace(/\s+/g, ' ').trim()).toBe('¿Dónde rinde tu plata hoy?');
    expect(h1![1]).not.toContain('<');
    expect(page).not.toContain('serif-accent');
  });

  it('paints the best option as a plain card with the cobalt rail, with no glass anywhere (D1, D14)', () => {
    const page = html('index.html');

    expect(page).not.toMatch(/class="[^"]*\bglass(-field)?\b/);
    expect(page).toMatch(/class="card border-s-2 border-s-accent p-6"/);
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

  it('keeps the pesos earned whole inside the sticky row header below md (T13e item 1)', () => {
    const page = html('index.html');
    const headers = [...page.matchAll(/<th[^>]*scope="row"[^>]*>([\s\S]*?)<\/th>/g)].map(
      (match) => match[1],
    );

    for (const [index, entry] of fixture.ranked.slice(0, 3).entries()) {
      // Below md the first column is the only one on screen: the row's
      // protagonist figure has to be in it, and the wide copy is hidden so the
      // amount is never announced twice.
      expect(headers[index], `row ${index + 1} earned`).toContain(formatMoney(entry.earned));
      expect(headers[index], `row ${index + 1} narrow copy`).toContain('md:hidden');
    }
    // The wide copy of the same figure steps aside below md, so the amount is
    // never announced twice.
    const wideCopy = [...page.matchAll(/<span class="([^"]*)"/g)]
      .map((match) => match[1].split(/\s+/))
      .filter((tokens) => tokens.includes('hidden') && tokens.includes('md:block'));
    expect(wideCopy.length).toBeGreaterThan(0);
    for (const tokens of wideCopy) expect(tokens).toContain('type-figure');
  });

  it('states where the data comes from (S11)', () => {
    const page = html('index.html');
    expect(page).toContain('Fuente y fecha');
    expect(page).toContain('ArgentinaDatos');
    expect(renderedText(page)).toMatch(/\d{2}\/\d{2}\/\d{4}/);
  });

  it('spends cobalt on the two leader rails only (T13e item 4)', () => {
    const page = html('index.html');
    const tokens = [...page.matchAll(/class="([^"]*)"/g)]
      .flatMap((match) => match[1].split(/\s+/))
      .filter((token) => /^(bg-accent(-solid)?|border(-s)?-accent|text-accent(-solid)?)$/.test(token));

    // The plate's rail, and row 01's rail. The "Mejor" chip is ink now, and no
    // inline link wears the accent: the underline carries it.
    expect(tokens.sort()).toEqual(['bg-accent', 'border-s-accent']);
  });

  it('shortens the row provenance: short kind label, source and date on one line (T13e item 5)', () => {
    const page = html('index.html');
    // The TNA cell names its kind in two words, never a four-line sentence.
    expect(page).toContain('TNA no clientes');
    expect(page).toContain('TNA fondo');
    expect(page).not.toContain('tasa para no clientes');
    expect(page).not.toContain('TNA equivalente del fondo');
    // The row carries one quiet provenance line: its source and its date. The
    // confidence lives on the instrument page and in the methodology.
    expect(page).not.toContain('Agregador automático');
    expect(page).toContain('ArgentinaDatos · 14/10/2026');
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
  it('never glues a word or a comma to the inline emphasis or link that follows (T13e item 3)', () => {
    const offenders = htmlFiles(dist).flatMap((file) => {
      const page = readFileSync(file, 'utf8');
      return [...page.matchAll(/[\w,;:.%]<(?:strong|a)\b/g)].map(
        (match) => `${relative(dist, file)}: ${match[0]}`,
      );
    });
    expect(offenders).toEqual([]);
  });

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

  it('explains why the history chart annualizes both the rate and the inflation', () => {
    const text = renderedText(html('metodologia/index.html')).replace(/\s+/g, ' ');
    expect(text).toContain('Por qué el gráfico anualiza los dos');
    expect(text).toContain('inflación anualizada');
    expect(text).toContain('tasa efectiva anual');
  });

  it('lists its sections in a table of contents whose links resolve to h2 ids (D12)', () => {
    const page = html('metodologia/index.html');
    const ids = new Set([...page.matchAll(/<h2[^>]*\sid="([^"]+)"/g)].map((match) => match[1]));
    const toc = /<nav[^>]*aria-label="En esta página"[^>]*>[\s\S]*?<\/nav>/.exec(page)?.[0] ?? '';
    const targets = [...toc.matchAll(/href="#([^"]+)"/g)].map((match) => match[1]);

    expect(toc, 'a table of contents nav').not.toBe('');
    expect(targets.length).toBeGreaterThanOrEqual(10);
    for (const target of targets) {
      expect(ids.has(target), `#${target} has no h2`).toBe(true);
    }
    // Every h2 is listed, so the contents never drift from the page.
    expect([...ids].sort()).toEqual([...new Set(targets)].sort());
    // At lg the contents sit in a sticky aside next to the reading column.
    expect(page).toMatch(/<aside[^>]*\blg:sticky\b[^>]*>/);
  });

  it('explains liquidity and the deposit guarantee, with the BCRA source and no peso cap (D5)', () => {
    const page = html('metodologia/index.html');
    const text = renderedText(page).replace(/\s+/g, ' ');
    const section = /<section[^>]*aria-labelledby="liquidez-garantia"[\s\S]*?<\/section>/.exec(page)?.[0] ?? '';

    expect(section, 'the liquidity section').not.toBe('');
    expect(text).toContain('Liquidez y garantía de los depósitos');
    expect(section).toContain('href="https://www.bcra.gob.ar/archivos/Pdfs/texord/t-seggar.pdf"');
    expect(section).toContain('Aplicación del sistema de seguro de garantía de los depósitos');
    const prose = renderedText(section).replace(/\s+/g, ' ');
    expect(prose).toContain('al vencimiento');
    expect(prose).toContain('disponible al instante');
    expect(prose).toContain('rescate en el día');
    expect(prose).toContain('cuotapartes');
    expect(prose).toContain('no son depósitos');
    expect(prose).toContain('proveedor de servicios de pago');
    expect(prose).toContain('tasas muy por encima');
    expect(prose).toContain('el ranking no marca la garantía');
    // The cap changes: the source is the truth, so the page never prints an amount for it.
    expect(prose).not.toMatch(/\$\s*\d/);
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

  it('uses the shared roles: one primary action back to the ranking, no mono or uppercase', () => {
    const main = /<main[\s\S]*<\/main>/.exec(html('404.html'))![0];
    expect(main).toContain('btn-primary');
    expect(main).toContain('href="/"');
    expect(main).not.toMatch(/\buppercase\b|\bfont-mono\b|\btracking-/);
  });
});

describe('stylesheet', () => {
  function builtCss(): string {
    return readdirSync(join(dist, '_astro'))
      .filter((file) => file.endsWith('.css'))
      .map((file) => readFileSync(join(dist, '_astro', file), 'utf8'))
      .join('\n');
  }

  it('ships no glass class on any page (D1)', () => {
    for (const file of htmlFiles(dist)) {
      expect(
        normalize(readFileSync(file, 'utf8')),
        `${relative(dist, file)} has a glass class`,
      ).not.toMatch(/class="[^"]*\bglass(-field)?\b/);
    }
  });

  it('ships one text family and no decorative layer in the built CSS (D1, D2)', () => {
    const css = builtCss();
    for (const retired of ['Space Grotesk', 'Geist', 'Instrument Serif', '.glass', '.serif-accent']) {
      expect(css, `${retired} must not reach the built CSS`).not.toContain(retired);
    }
    expect(css).toContain('Archivo Variable');
  });

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
      '.wordmark',
      '.bg-thumb',
      '.text-on-thumb',
    ]) {
      expect(css, `${selector} is missing from the built CSS`).toContain(selector);
    }
  });
});

/**
 * Monthly report pages. The fixture build reads the synthetic month in
 * `fixtures/reports/`: `/informes/` lists it and `/informes/2026-10/` renders its table. The empty state is checked by
 * `reports-empty-smoke.test.ts`, which builds with an empty month list.
 */
const report = JSON.parse(readFileSync(join(webRoot, 'fixtures/reports/2026-10.json'), 'utf8')) as {
  inflation: { value: number };
  rows: Array<{ name: string; nominal: number; real: number; beatInflation: boolean }>;
  leftOut: Array<{ name: string; reason: string }>;
};

describe('prerendered report index', () => {
  it('lists the available month with a link to its page, its inflation and how many beat it', () => {
    const page = html('informes/index.html');
    const text = renderedText(page).replace(/\s+/g, ' ');

    expect(text).toContain('Quién le ganó a la inflación');
    expect(page).toContain('href="/informes/2026-10/"');
    expect(text).toContain('octubre de 2026');
    expect(text).toContain('2,10%');
    expect(text).toContain('4 de 5');
    expect(text).not.toContain('todavía no hay');
  });

  it('carries its canonical link and the breadcrumb JSON-LD', () => {
    const page = html('informes/index.html');

    expect(page).toContain(`rel="canonical" href="${SITE_URL}/informes/"`);
    expect(page).toContain('"@type":"BreadcrumbList"');
  });
});

describe('prerendered month report', () => {
  it('has one page per available month and no more', () => {
    const months = readdirSync(join(dist, 'informes'), { withFileTypes: true })
      .filter((entry) => entry.isDirectory())
      .map((entry) => entry.name);

    expect(months).toEqual(['2026-10']);
  });

  it('answers the question of the month in its heading and states the INDEC source and date', () => {
    const page = html('informes/2026-10/index.html');
    const text = renderedText(page).replace(/\s+/g, ' ');
    const h1 = /<h1[^>]*>([\s\S]*?)<\/h1>/.exec(page);

    expect(h1, 'the month H1').not.toBeNull();
    expect(renderedText(h1![1]).replace(/\s+/g, ' ').trim()).toBe(
      'Quién le ganó a la inflación en octubre de 2026',
    );
    expect(text).toContain('INDEC');
    expect(text).toContain('2,10%');
    expect(text).toContain('31/10/2026');
    expect(text).toContain('BCRA Statistics');
    expect(page).toContain(`rel="canonical" href="${SITE_URL}/informes/2026-10/"`);
  });

  it('renders the table in the API order with nominal, real, a verdict in words and coverage', () => {
    const page = html('informes/2026-10/index.html');
    const headers = rowHeaderTexts(page);

    expect(headers).toHaveLength(report.rows.length);
    for (const [index, row] of report.rows.entries()) {
      expect(headers[index], `row ${index + 1}`).toContain(row.name);
    }
    const text = renderedText(page).replace(/\s+/g, ' ');
    expect(text).toContain('3,50%');
    expect(text).toContain('+1,37%');
    expect(text).toContain('−0,06%');
    expect(text).toContain('Le ganó');
    expect(text).toContain('No le ganó');
    // The fund covers 30 of the 31 days and folds two sibling classes.
    expect(text).toContain('30 de 31 días');
    expect(text).toContain('+2 clases');
    for (const row of report.rows) {
      expect(page, row.name).toContain(row.beatInflation ? 'Le ganó' : 'No le ganó');
    }
  });

  it('names the options left out with their reason in Spanish', () => {
    const text = renderedText(html('informes/2026-10/index.html')).replace(/\s+/g, ' ');

    for (const left of report.leftOut) {
      expect(text, left.name).toContain(left.name);
    }
    expect(text).toContain('No teníamos su tasa al empezar el mes');
    expect(text).toContain('Sin cotización cerca del cierre del mes');
  });

  it('explains the method in two sentences and links the methodology', () => {
    const page = html('informes/2026-10/index.html');

    expect(page).toContain('href="/metodologia/"');
    expect(renderedText(page)).toContain('Cómo medimos');
    // The one sentence specific to reports stays; the generic disclaimer is the footer's (D11).
    expect(renderedText(page).replace(/\s+/g, ' ')).toContain(
      'lo que rindió una opción en el pasado no garantiza que lo repita',
    );
  });

  it('has a plain hero, a wide layout, one quiet data line per row and no page disclaimer (D12, D14)', () => {
    const page = html('informes/2026-10/index.html');
    const main = /<main[\s\S]*<\/main>/.exec(page)![0];
    const text = renderedText(main).replace(/\s+/g, ' ');

    expect(main).not.toContain('border-s-accent');
    expect(main).toMatch(/class="[^"]*\blg:grid-cols-2\b[^"]*"/);
    // "31 de 31 días · Fuente" on one line.
    expect(text).toMatch(/30 de 31 días · [A-Za-z]/);
    expect(text).not.toContain('Esto es información y no es asesoramiento financiero');
    expect(text).not.toContain('no recibe dinero de las entidades');
    expect(main).not.toMatch(/\buppercase\b|\bfont-mono\b/);
  });

  it('shows names through the display-name helper', () => {
    const headers = rowHeaderTexts(html('informes/2026-10/index.html'));
    for (const [index, row] of report.rows.entries()) {
      expect(headers[index]).toContain(displayName(row.name));
    }
  });

  it('ships no island: the report is static markup', () => {
    const page = html('informes/2026-10/index.html');

    expect(page).not.toContain('astro-island');
  });

  it('is in the sitemap together with the index', () => {
    const sitemaps = readdirSync(dist)
      .filter((file) => /^sitemap-\d+\.xml$/.test(file))
      .map((file) => readFileSync(join(dist, file), 'utf8'))
      .join('\n');

    expect(sitemaps).toContain(`${SITE_URL}/informes/`);
    expect(sitemaps).toContain(`${SITE_URL}/informes/2026-10/`);
  });
});

describe('site header', () => {
  it('links the reports from every page and marks the section inside /informes/', () => {
    for (const path of ['index.html', 'metodologia/index.html', 'plazo-fijo/index.html']) {
      expect(html(path), path).toMatch(/<a href="\/informes\/"[^>]*>\s*Informes/);
    }
    // The index is the current page; a month inside it marks the section (`true`), and the breadcrumb names the page.
    expect(html('informes/index.html')).toMatch(/<a href="\/informes\/" aria-current="page"/);
    expect(html('informes/2026-10/index.html')).toMatch(/<a href="\/informes\/" aria-current="true"/);
    expect(html('index.html')).not.toMatch(/<a href="\/informes\/" aria-current=/);
  });

  it('keeps the other sections of the header', () => {
    const page = html('index.html');

    for (const href of ['/', '/plazo-fijo/', '/billeteras/', '/fondos/', '/cripto/', '/metodologia/']) {
      expect(page, href).toContain(`href="${href}"`);
    }
  });

  it('lists the eight reading sections inline and in a zero-JS menu, with no sideways scroller (D10, D20)', () => {
    const header = headerOf(html('index.html'));
    const sections = ['/', '/plazo-fijo/', '/billeteras/', '/fondos/', '/cripto/', '/bancos/', '/informes/', '/metodologia/'];

    for (const href of sections) {
      expect(header.match(new RegExp(`<a href="${href}"[^>]*class="type-(label|body)`, 'g')) ?? [], href).toHaveLength(2);
    }
    expect(header).not.toContain('overflow-x-auto');
    expect(header).not.toContain('mask-image');
    expect(header).not.toContain('<script');
    expect(header).toMatch(/<details[^>]*>\s*<summary[^>]*>\s*Menú\s*(<span aria-hidden="true"[^>]*><\/span>\s*)?<\/summary>/);
  });

  it('keeps Alertas as the one action and moves Claves API out of the header (D20)', () => {
    const header = headerOf(html('index.html'));

    expect(header.match(/<a href="\/alertas\/"[^>]*>\s*Alertas/g) ?? []).toHaveLength(1);
    expect(header).not.toContain('/claves-api/');
    expect(header).not.toContain('btn-primary');
    expect(html('alertas/index.html')).toMatch(/<a href="\/alertas\/" aria-current="page"/);
  });

  it('marks the current section in the menu as well as inline', () => {
    const header = headerOf(html('plazo-fijo/index.html'));

    expect(header.match(/<a href="\/plazo-fijo\/" aria-current="page"/g) ?? []).toHaveLength(2);
  });
});

describe('site footer', () => {
  it('carries the wordmark, three links, one short disclaimer paragraph and the copyright (D11, D20)', () => {
    const footer = footerOf(html('index.html'));

    expect(footer).toMatch(/<a [^>]*href="\/metodologia\/"[^>]*>\s*Cómo se calcula/);
    expect(footer).toMatch(/<a [^>]*href="\/claves-api\/"[^>]*>\s*Claves API/);
    expect(footer).toMatch(/<a [^>]*href="https:\/\/github.com\/Mateocas1\/rindetuplata"[^>]*>\s*Código y capturas diarias/);
    expect(footer.match(/<p\b/g) ?? []).toHaveLength(2);
    const text = renderedText(footer);
    expect(text).toContain('Es información, no asesoramiento financiero');
    expect(text).toContain('ninguna entidad nos paga por aparecer');
    expect(text).toContain('fuente');
    expect(text).toMatch(/©\s*\d{4} rindetuplata/);
  });

  it('keeps the disclaimer to two sentences and uses no hand-rolled underline classes', () => {
    const footer = footerOf(html('index.html'));
    const paragraph = /<p class="type-body[^"]*">([\s\S]*?)<\/p>/.exec(footer);

    expect(paragraph).not.toBeNull();
    expect(renderedText(paragraph![1]).match(/[.!?](\s|$)/g) ?? []).toHaveLength(2);
    expect(footer).not.toContain('underline-offset-2 hover:decoration-text"');
    expect(footer).not.toContain('uppercase');
  });

  it('marks the API keys link as current on its own page', () => {
    expect(footerOf(html('claves-api/index.html'))).toMatch(/<a [^>]*href="\/claves-api\/"[^>]*aria-current="page"|<a [^>]*aria-current="page"[^>]*href="\/claves-api\/"/);
  });
});

describe('internal links', () => {
  /** Every `<a href="/...">` of every built page, with the page it appears on. */
  function internalLinks(): Array<{ page: string; href: string }> {
    return htmlFiles(dist).flatMap((file) =>
      [...readFileSync(file, 'utf8').matchAll(/<a\b[^>]*?\shref="(\/[^"]*)"/g)]
        .map((match) => match[1])
        .filter((href) => !href.startsWith('//'))
        .map((href) => ({ page: relative(dist, file), href })),
    );
  }

  /** The built file a path serves: a directory index, or the file itself when it has an extension. */
  function resolves(href: string): boolean {
    const path = href.split(/[?#]/)[0];
    if (path.endsWith('/')) return existsSync(join(dist, path, 'index.html'));
    return (
      existsSync(join(dist, path)) &&
      !(path.split('/').pop() ?? '').match(/^[^.]*$/) /* has an extension */
    ) || existsSync(join(dist, `${path}/index.html`)) || existsSync(join(dist, `${path}.html`));
  }

  it('resolves to a built file on every page, the reports included', () => {
    const links = internalLinks();
    const missing = links.filter(({ href }) => !resolves(href)).map(({ page, href }) => `${page} -> ${href}`);

    expect(links.length).toBeGreaterThan(100);
    expect(missing).toEqual([]);
  });

  it('includes the links between the index, the month page and the methodology', () => {
    const hrefs = internalLinks()
      .filter(({ page }) => page.startsWith('informes'))
      .map(({ page, href }) => `${page} -> ${href}`);

    expect(hrefs).toContain('informes/index.html -> /informes/2026-10/');
    expect(hrefs).toContain('informes/2026-10/index.html -> /informes/');
    expect(hrefs).toContain('informes/2026-10/index.html -> /metodologia/');
  });
});
