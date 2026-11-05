/**
 * The ranking board (RankingTable), the best-option card and the home answer sentence.
 *
 * T2c: a grouped fund row says how many sibling classes it folded (S2, L5).
 *
 * Synthetic data on purpose (L16): the recorded ranking fixtures predate the field. The note is visible text
 * ("+3 clases") and its full meaning continues in an `sr-only` span right after it, so a screen reader reads the
 * visible words first (WCAG 2.5.3) and nothing is announced through an `aria-label`.
 */
import { createElement as h } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';
import BestOptionPlate from '../src/components/ranking/BestOptionPlate';
import RankingTable from '../src/components/ranking/RankingTable';
import { answerSentence } from '../src/components/ranking/answer';
import { loadRanking } from '../src/lib/data';
import { DEFAULT_RANKING_QUERY } from '../src/lib/rankingQuery';
import { instrumentSlug } from '../src/lib/slug';
import type { RankedEntry, Ranking } from '../src/lib/types';

function entry(id: string, name: string, kind: RankedEntry['kind'], foldedClasses?: number): RankedEntry {
  return {
    id,
    kind,
    name,
    entity: null,
    source: { id: 'argentinadatos-fci', displayName: 'ArgentinaDatos', official: false },
    confidence: 'AGGREGATOR_AUTOMATIC',
    verifiedAt: null,
    captureDate: '2026-10-14',
    dataDate: '2026-10-14',
    rateUsed: { fraction: 0.3, basis: 'TNA', kind: 'FUND_TNA_EQUIVALENT' },
    finalAmount: 1024657.53,
    earned: 24657.53,
    nominalReturn: 0.024658,
    realReturn: 0.01,
    upTo: null,
    warnings: [],
    ...(foldedClasses === undefined ? {} : { foldedClasses }),
  };
}

function render(ranked: RankedEntry[]): string {
  const ranking: Ranking = {
    request: { amount: 1000000, days: 30, clientOf: [], kinds: [], asOf: '2026-10-14', limit: 50 },
    inflation: null,
    ranked,
    ineligible: [],
    excluded: [],
    totals: { ranked: ranked.length, ineligible: 0, excluded: 0 },
  };
  return renderToStaticMarkup(h(RankingTable, { ranking, pageSlugs: [] }));
}

describe('RankingTable folded classes note', () => {
  it('shows the visible count with its full meaning in an sr-only span', () => {
    const html = render([entry('1', 'Fondo Uno - Clase A', 'FUND', 3)]);

    expect(html).toContain('+3 clases');
    expect(html).toMatch(/\+3 clases<span class="sr-only"> más del mismo fondo[^<]*<\/span>/);
    expect(html).not.toMatch(/aria-label="[^"]*clases/);
  });

  it('uses the singular for one folded class', () => {
    const html = render([entry('1', 'Fondo Uno - Clase A', 'FUND', 1)]);

    expect(html).toContain('+1 clase');
    expect(html).not.toContain('+1 clases');
    expect(html).toContain('<span class="sr-only"> más del mismo fondo');
  });

  it('shows no note when nothing was folded or the field is absent', () => {
    const html = render([
      entry('1', 'Fondo Solo', 'FUND', 0),
      entry('2', 'Banco A', 'PLAZO_FIJO', 0),
      entry('3', 'Billetera', 'WALLET'),
    ]);

    expect(html).not.toContain('+0');
    expect(html).not.toContain('clases más');
    expect(html).not.toContain('clase más');
  });
});

/**
 * ui-clarity T4: the board reads like an answer. One provenance line per row instead of a "Fuente y fecha" column
 * (D18), positions that share a tie (D7), the gap to the best in whole pesos (D8), the real result in words (D6,
 * D19), display names (D13) and, below `md`, a card list carrying the same rows (D9).
 */
const FIXTURES = { WEB_FIXTURES: '1' } as const;
const plazoFijo = await loadRanking(
  { ...DEFAULT_RANKING_QUERY, kinds: ['PLAZO_FIJO'] },
  { env: FIXTURES, limit: 200 },
);
const wallets = await loadRanking({ ...DEFAULT_RANKING_QUERY, kinds: ['WALLET'] }, { env: FIXTURES, limit: 200 });
const home = await loadRanking(DEFAULT_RANKING_QUERY, { env: FIXTURES });

/** What a sighted reader sees: no tags, no screen-reader-only spans. */
const visible = (markup: string) =>
  markup
    .replace(/<span class="sr-only">[^<]*<\/span>/g, '')
    .replace(/<[^>]+>/g, ' ')
    .replaceAll('&nbsp;', '\u00a0')
    .replaceAll('&quot;', '"')
    .replaceAll('\u00a0', ' ')
    .replace(/\s+/g, ' ')
    .trim();

function board(value: Ranking, props: { limit?: number; offset?: number; pageSlugs?: string[] } = {}): string {
  return renderToStaticMarkup(h(RankingTable, { ranking: value, pageSlugs: [], ...props }));
}

const slugsOf = (value: Ranking) => value.ranked.map((entry) => instrumentSlug(entry.name, entry.id));

/** The table rows (md and up) of a board, each as its inner markup. */
function tableRows(markup: string): string[] {
  const body = /<tbody>([\s\S]*?)<\/tbody>/.exec(markup)?.[1] ?? '';
  return [...body.matchAll(/<tr\b[^>]*>([\s\S]*?)<\/tr>/g)].map((match) => match[1]);
}

/** The first cell of a table row: its position. */
function positionCell(row: string): string {
  return visible(/<td\b[^>]*>([\s\S]*?)<\/td>/.exec(row)![1]);
}

/** The cards of the mobile list (below md), each as its inner markup. */
function listItems(markup: string): string[] {
  const list = /<ol\b[^>]*class="[^"]*md:hidden[^"]*"[^>]*>([\s\S]*?)<\/ol>/.exec(markup)?.[1] ?? '';
  return [...list.matchAll(/<li\b[^>]*>([\s\S]*?)<\/li>/g)].map((match) => match[1]);
}

describe('ranking board columns and provenance (D18)', () => {
  it('has the five columns and no "Fuente y fecha" column', () => {
    const markup = board(plazoFijo, { limit: 8 });
    const heads = [...markup.matchAll(/<th scope="col"[^>]*>([\s\S]*?)<\/th>/g)].map((match) => visible(match[1]));

    expect(heads).toEqual(['Nº', 'Opción', 'Ganás en 30 días', 'Contra la inflación (REM)', 'TNA']);
    expect(markup).not.toContain('Fuente y fecha');
  });

  it('puts kind, liquidity, source and a short date in every row secondary line', () => {
    const rows = tableRows(board(plazoFijo, { limit: 8 }));

    expect(rows).toHaveLength(8);
    for (const row of rows) {
      expect(visible(row)).toContain('Plazo fijo · Al vencimiento · ArgentinaDatos 14/10');
    }
  });

  it('states the common source and the newest date once, in a caption above the table', () => {
    const markup = board(plazoFijo, { limit: 8 });
    const caption = /<p class="type-label[^"]*">([\s\S]*?)<\/p>/.exec(markup);

    expect(caption).not.toBeNull();
    expect(visible(caption![1])).toBe('Fuente: ArgentinaDatos · datos al 14/10/2026');
    expect(markup.indexOf(caption![0])).toBeLessThan(markup.indexOf('<table'));
  });

  it('names a curated rate as a manual load and keeps a stale flag in the line', () => {
    const stale: Ranking = {
      ...wallets,
      ranked: wallets.ranked.map((entry, index) =>
        index === 1 ? { ...entry, warnings: [{ code: 'STALE', severity: 'WARN', message: 'old' }] } : entry,
      ),
    };
    const rows = tableRows(board(stale));

    expect(visible(rows[0])).toContain('Billetera · Disponible al instante · Carga manual verificada 13/10');
    expect(rows[0]).not.toContain('Curated by the project owner');
    expect(visible(rows[1])).toContain('Dato desactualizado');
    expect(rows[1]).toContain('text-warning');
  });

  it('has no sideways scroll region and no swipe hint', () => {
    const markup = board(home, { limit: 8 });

    expect(markup).not.toContain('role="region"');
    expect(markup).not.toContain('Deslizá');
    expect(markup).not.toContain('sticky');
  });
});

describe('ranking positions, ties and the gap to the best (D7, D8)', () => {
  it('shares a position between equal earnings, marked "=" with "empate" for assistive technology', () => {
    const rows = tableRows(board(plazoFijo, { limit: 8 }));

    expect(rows.map(positionCell)).toEqual(['01', '=02', '=02', '=04', '=04', '=04', '=04', '=08']);
    expect(rows[3]).toContain('<span class="sr-only">4, empate</span>');
    expect(rows[0]).not.toContain('empate');
  });

  it('continues the competition ranks in the table that follows a limited one', () => {
    expect(positionCell(tableRows(board(plazoFijo, { offset: 8 }))[0])).toBe('=08');
  });

  it('shows the gap to the best in whole pesos from row 2 on, and no final amount in rows', () => {
    const rows = tableRows(board(plazoFijo, { limit: 8 }));

    expect(visible(rows[0])).not.toContain('vs. la mejor');
    expect(visible(rows[1])).toContain('$ 19.315,07');
    expect(visible(rows[1])).toContain('−$ 411 vs. la mejor');
    for (const row of rows) expect(visible(row)).not.toContain('al vencimiento');
  });

  it('gives every row tied for first the "Mejor" chip and the gap to none of them', () => {
    const tiedTop: Ranking = { ...plazoFijo, ranked: plazoFijo.ranked.slice(1, 5) };
    const rows = tableRows(board(tiedTop));

    expect(rows.map((row) => visible(row).includes('Mejor'))).toEqual([true, true, false, false]);
    expect(visible(rows[1])).not.toContain('vs. la mejor');
    expect(visible(rows[2])).toContain('−$ 411 vs. la mejor');
  });
});

describe('ranking row content (D6, D13, D19)', () => {
  it('says the real result in words in every row', () => {
    for (const row of tableRows(board(home, { limit: 8 }))) {
      expect(visible(row)).toContain('por encima de la inflación');
    }
  });

  it('shows the display name, never the legal one', () => {
    const markup = board(plazoFijo, { limit: 3, pageSlugs: slugsOf(plazoFijo) });
    const rows = tableRows(markup);

    expect(visible(rows[0])).toContain('Reba');
    expect(markup).not.toContain('REBA COMPAÑIA FINANCIERA S.A.');
    expect(visible(rows[1])).toContain('Banco CMF');
  });

  it('opens each row with its name as a link without a resting underline', () => {
    const markup = board(home, { limit: 2, pageSlugs: slugsOf(home) });
    const link = /<a href="\/instrumentos\/[^"]+" class="([^"]*)">([^<]*)<\/a>/.exec(markup);

    expect(link).not.toBeNull();
    expect(link![2]).toBe('Reba');
    const tokens = link![1].split(/\s+/);
    expect(tokens).not.toContain('underline');
    expect(tokens).toEqual(expect.arrayContaining(['hover:underline', 'focus-visible:underline']));
  });

  it('keeps the conditional "hasta" note of a row', () => {
    expect(visible(board(wallets))).toContain('Hasta $ 1.019.915,26 · Si sumás $500.000');
  });
});

describe('mobile ranking list (D9)', () => {
  it('renders one card per table row, and only one of the two views shows at a time', () => {
    const markup = board(home, { limit: 8 });

    expect(listItems(markup)).toHaveLength(tableRows(markup).length);
    expect(listItems(markup)).toHaveLength(8);
    expect(markup).toMatch(/<div class="[^"]*\bhidden\b[^"]*\bmd:block\b[^"]*"><table/);
    expect(markup).toMatch(/<ol\b[^>]*class="[^"]*\bmd:hidden\b/);
  });

  it('carries the same facts as the table row: position, name, earned, gap, real result, TNA, provenance', () => {
    const items = listItems(board(plazoFijo, { limit: 8 }));

    expect(visible(items[0])).toMatch(/^01 Reba Mejor/);
    expect(visible(items[1])).toMatch(/^=02 Banco CMF/);
    for (const fact of [
      '$ 19.315,07',
      '−$ 411 vs. la mejor',
      'por encima de la inflación',
      'Al vencimiento',
      'ArgentinaDatos 14/10',
      '23,50% TNA',
    ]) {
      expect(visible(items[1]), fact).toContain(fact);
    }
  });
});

describe('ranking footnote (D11)', () => {
  it('says what the amounts include and which inflation they compare with, linking the methodology', () => {
    const markup = board(home, { limit: 8 });
    const note = /<p class="type-body max-w-prose text-muted">([\s\S]*?)<\/p>/.exec(markup);

    expect(note).not.toBeNull();
    const text = visible(note![1]);
    expect(text).toContain('topes');
    expect(text).toContain('21,10% a 12 meses');
    expect(text).toContain('1,59% en 30 días');
    expect(text).toContain('30/09/2026');
    expect(note![1]).toMatch(/ <a class="link" href="\/metodologia\/">Cómo se calcula cada número<\/a>/);
    expect(markup).not.toContain('asesoramiento');
  });

  it('is printed once, under the first table only', () => {
    expect(board(home, { offset: 8 })).not.toContain('Cómo se calcula cada número');
  });
});

describe('best option card (D6, D8, D13, D14)', () => {
  it('names the best with its display name, kind, liquidity, real result, final amount, TNA and provenance', () => {
    const markup = renderToStaticMarkup(h(BestOptionPlate, { ranking: plazoFijo, best: plazoFijo.ranked[0] }));
    const text = visible(markup);

    expect(markup).toMatch(/class="card border-s-2 border-s-accent/);
    expect(text).toContain('La mejor de hoy · Plazo fijo');
    expect(text).toContain('$ 19.726,03');
    expect(text).toContain('Reba');
    expect(text).toContain('Al vencimiento');
    expect(text).toContain('$ 3.806 por encima de la inflación');
    expect(text).toContain('Tenés $ 1.019.726,03 al vencimiento');
    expect(text).toContain('24,00%');
    expect(text).toContain('TNA no clientes');
    expect(text).toContain('ArgentinaDatos · 14/10/2026');
    expect(markup).not.toContain('REBA');
  });

  it('says "a los N días" for an option that does not mature', () => {
    const text = visible(renderToStaticMarkup(h(BestOptionPlate, { ranking: wallets, best: wallets.ranked[0] })));

    expect(text).toContain('Tenés $ 1.016.438,36 a los 30 días');
  });
});

describe('home answer sentence (D15)', () => {
  const nbsp = (value: string) => value.replaceAll('\u00a0', ' ');

  it('answers the H1 with the best option, the pesos and the real result', () => {
    expect(nbsp(answerSentence(home))).toBe(
      'Hoy, con $ 1.000.000 a 30 días, lo que más rinde es Reba: ganás $ 19.726,03, $ 3.806 por encima de la inflación esperada.',
    );
  });

  it('says "por debajo" for a loss', () => {
    const losing: Ranking = { ...wallets, ranked: wallets.ranked.slice(1) };
    expect(nbsp(answerSentence(losing))).toBe(
      'Hoy, con $ 1.000.000 a 30 días, lo que más rinde es Mercado Pago: ganás $ 15.571,75, $ 284 por debajo de la inflación esperada.',
    );
  });

  it('names both options of a two-way tie, and counts the rest of a wider one', () => {
    const two: Ranking = { ...plazoFijo, ranked: plazoFijo.ranked.slice(1, 4) };
    const four: Ranking = { ...plazoFijo, ranked: plazoFijo.ranked.slice(3, 8) };

    expect(nbsp(answerSentence(two))).toBe(
      'Hoy, con $ 1.000.000 a 30 días, empatan Banco CMF y Banco Voii: ganás $ 19.315,07, $ 3.401 por encima de la inflación esperada.',
    );
    expect(nbsp(answerSentence(four))).toContain('empatan Banco BICA y 3 opciones más: ganás $ 18.904,11');
  });

  it('falls back to the percentage, or to the pesos alone, without the real result in pesos', () => {
    const [first] = home.ranked;
    const noPesos: Ranking = { ...home, ranked: [{ ...first, realEarned: undefined }] };
    const noInflation: Ranking = {
      ...home,
      inflation: null,
      ranked: [{ ...first, realEarned: null, realReturn: null }],
    };

    expect(nbsp(answerSentence(noPesos))).toContain('ganás $ 19.726,03, 0,38% por encima de la inflación esperada.');
    expect(nbsp(answerSentence(noInflation))).toBe(
      'Hoy, con $ 1.000.000 a 30 días, lo que más rinde es Reba: ganás $ 19.726,03.',
    );
  });

  it('says so when nothing is ranked', () => {
    expect(nbsp(answerSentence({ ...home, ranked: [] }))).toBe(
      'Hoy no encontramos opciones para $ 1.000.000 a 30 días.',
    );
  });
});
