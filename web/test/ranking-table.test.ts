/**
 * A grouped fund row says how many sibling classes it folded.
 *
 * Synthetic data on purpose: the recorded ranking fixtures predate the field. The note is visible text
 * ("+3 clases") and its full meaning continues in an `sr-only` span right after it, so a screen reader reads the
 * visible words first (WCAG 2.5.3) and nothing is announced through an `aria-label`.
 */
import { createElement as h } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';
import RankingTable from '../src/components/ranking/RankingTable';
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
