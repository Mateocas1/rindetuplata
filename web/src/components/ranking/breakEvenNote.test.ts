/**
 * Where the dólar MEP break-even shows: inside a closed disclosure of each ranked row, so the row keeps
 * its figures, and under the result of the simulator. Both stay absent when the API sent no quote.
 *
 * Synthetic data on purpose: the recorded ranking fixtures predate the fields. The disclosure's visible words
 * are its accessible name (WCAG 2.5.3): no `aria-label` replaces them.
 */
import { createElement as h } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';
import { SimulatedResult } from '../instrument/SimulatorIsland';
import type { MepQuote, RankedEntry, Ranking } from '../../lib/types';
import RankingTable from './RankingTable';

const quote: MepQuote = {
  buy: 1534.2,
  sell: 1539.4,
  source: { id: 'dolarapi-bolsa', displayName: 'DolarApi', official: false },
  quoteDate: '2026-10-14',
  updatedAt: '2026-10-13T20:57:00Z',
};

const SENTENCE =
  'Le gana al dólar MEP si el MEP sube menos de 2,81% en 30 días (MEP al 13/10: $\u00a01.539,40 de venta).';

function entry(id: string, name: string, withBreakEven: boolean): RankedEntry {
  return {
    id,
    kind: 'PLAZO_FIJO',
    name,
    entity: name,
    source: { id: 'argentinadatos-plazo-fijo', displayName: 'ArgentinaDatos', official: false },
    confidence: 'AGGREGATOR_AUTOMATIC',
    verifiedAt: null,
    captureDate: '2026-10-14',
    dataDate: '2026-10-14',
    rateUsed: { fraction: 0.3, basis: 'TNA', kind: 'NON_CLIENT' },
    finalAmount: 1024657.53,
    earned: 24657.53,
    nominalReturn: 0.024658,
    realReturn: null,
    upTo: null,
    warnings: [],
    ...(withBreakEven ? { breakEvenMep: { devaluation: 0.02813, price: 1577.36 } } : {}),
  };
}

function ranking(ranked: RankedEntry[], dollarMep?: MepQuote | null): Ranking {
  return {
    request: { amount: 1000000, days: 30, clientOf: [], kinds: [], asOf: '2026-10-14', limit: 50 },
    inflation: null,
    ...(dollarMep === undefined ? {} : { dollarMep }),
    ranked,
    ineligible: [],
    excluded: [],
    totals: { ranked: ranked.length, ineligible: 0, excluded: 0 },
  };
}

function table(value: Ranking): string {
  return renderToStaticMarkup(h(RankingTable, { ranking: value, pageSlugs: [] }));
}

describe('RankingTable break-even', () => {
  it('puts the sentence in a closed disclosure of the row, named by its visible summary', () => {
    const html = table(ranking([entry('1', 'Banco A', true)], quote));

    expect(html).toMatch(/<details class="[^"]*disclosure[^"]*">\s*<summary[^>]*>Contra el dólar MEP<\/summary>/);
    expect(html).not.toMatch(/<details[^>]*\bopen\b/);
    expect(html).toContain(SENTENCE);
    expect(html).toContain('Si al vencimiento el MEP de compra pasa de $\u00a01.577,36, el dólar rinde más.');
    expect(html).not.toMatch(/aria-label="[^"]*MEP/);
  });

  it('keeps every figure of the row where it was', () => {
    const html = table(ranking([entry('1', 'Banco A', true)], quote));

    expect(html).toContain('Ganás en 30 días');
    expect(html).toContain('$\u00a024.657,53');
    // The final amount lives on the best-option card and the instrument page (D8), never in a row.
    expect(html).not.toContain('al vencimiento</');
  });

  it('is hidden when the API sent no quote, or the row has no break-even', () => {
    const noQuote = table(ranking([entry('1', 'Banco A', true)]));
    const nullQuote = table(ranking([entry('1', 'Banco A', true)], null));
    const noBreakEven = table(ranking([entry('1', 'Banco A', false)], quote));

    for (const html of [noQuote, nullQuote, noBreakEven]) {
      expect(html).not.toContain('dólar MEP');
      expect(html).not.toContain('<details');
    }
  });

  it('renders one disclosure per row that has a break-even, in each of the two views (D9)', () => {
    const html = table(ranking([entry('1', 'Banco A', true), entry('2', 'Banco B', false)], quote));
    const tableView = html.slice(html.indexOf('<table'), html.indexOf('</table>'));
    const listView = html.slice(html.indexOf('<ol'), html.indexOf('</ol>'));

    expect(tableView.match(/<details/g)).toHaveLength(1);
    expect(listView.match(/<details/g)).toHaveLength(1);
    expect(html.match(/<details/g)).toHaveLength(2);
  });
});

describe('SimulatedResult break-even', () => {
  const props = { entry: entry('1', 'Banco A', true), amount: 1000000, days: 30, inflationIndexLabel: 'REM' };

  it('shows the sentence and the break-even price under the result', () => {
    const html = renderToStaticMarkup(h(SimulatedResult, { ...props, dollarMep: quote }));

    expect(html).toContain(SENTENCE);
    expect(html).toContain('Si al vencimiento el MEP de compra pasa de $\u00a01.577,36, el dólar rinde más.');
    expect(html).toContain('Ganancia estimada');
  });

  it('shows nothing about the dollar without a quote', () => {
    for (const dollarMep of [null, undefined]) {
      const html = renderToStaticMarkup(h(SimulatedResult, { ...props, dollarMep }));

      expect(html).not.toContain('dólar MEP');
      expect(html).toContain('Ganancia estimada');
    }
  });
});
