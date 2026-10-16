/**
 * Capsule segmented control and its wiring into the ranking explorer (T13b).
 *
 * The control is a real radio group (the prototype's signature device, L80):
 * plain `input[type=radio]`, one checked option, the group named by its
 * `legend`, and the selected segment painted with the `thumb` role. These are
 * the markup rules the design must keep, and the reason an island renders them
 * as a React component and not decorative buttons.
 *
 * The second half renders the island with `renderToStaticMarkup`: effects do
 * not run, which is exactly the state the static build ships, so the presets
 * must already be there, wired to the same `name`s the explorer's state uses,
 * with the free inputs kept beside them.
 */
import { createElement as h } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';
import { loadRanking } from '../../lib/data';
import { DEFAULT_RANKING_QUERY, serializeRankingQuery } from '../../lib/rankingQuery';
import type { Ranking } from '../../lib/types';
import RankingExplorer from './RankingExplorer';
import { AMOUNT_PRESETS, DAYS_PRESETS } from './presets';
import Segmented from '../ui/Segmented';

const FIXTURES = { WEB_FIXTURES: '1' } as const;

function render(element: ReturnType<typeof h>): string {
  return renderToStaticMarkup(element);
}

/** The radio inputs of one group, in document order, with their checked state. */
function radios(markup: string, name: string): Array<{ value: string; checked: boolean }> {
  const pattern = new RegExp(`<input[^>]*name="${name}"[^>]*>`, 'g');
  return [...markup.matchAll(pattern)].map((match) => ({
    value: /value="([^"]*)"/.exec(match[0])?.[1] ?? '',
    checked: /\bchecked(="")?/.test(match[0]),
  }));
}

const segProps = {
  name: 'monto-preset',
  legend: 'Montos frecuentes',
  options: AMOUNT_PRESETS,
  onSelect: () => {},
};

describe('capsule segmented control', () => {
  it('is a radio group of real inputs, named by its legend, with one checked option', () => {
    const markup = render(h(Segmented, { ...segProps, selected: '1000000' }));

    expect(markup).toContain('<fieldset');
    expect(markup).toContain('<legend class="type-label');
    expect(markup).toContain('Montos frecuentes');
    expect(radios(markup, 'monto-preset')).toEqual([
      { value: '500000', checked: false },
      { value: '1000000', checked: true },
      { value: '5000000', checked: false },
    ]);
  });

  it('paints the selected segment with the thumb role and the unselected ones without it', () => {
    const markup = render(h(Segmented, { ...segProps, selected: '1000000' }));

    expect(markup).toContain('bg-thumb');
    expect(markup).toContain('text-on-thumb');
    expect(markup).toContain('bg-subtle-surface');
    // One thumb, on the checked option only.
    expect(markup.match(/bg-thumb/g) ?? []).toHaveLength(1);
  });

  it('leaves the segment unselected when the reader typed a value of their own', () => {
    const markup = render(h(Segmented, { ...segProps, selected: null }));

    expect(radios(markup, 'monto-preset').every((radio) => !radio.checked)).toBe(true);
    expect(markup).not.toContain('bg-thumb');
  });

  it('shows a visible focus indicator on the focused segment', () => {
    const markup = render(h(Segmented, { ...segProps, selected: '500000' }));

    expect(markup).toMatch(/has-\[:focus-visible\]:outline/);
  });

  it('shows the short amount and names the radio with it plus the full value (T13e item 7, WCAG 2.5.3)', () => {
    const markup = render(h(Segmented, { ...segProps, selected: '1000000' }));

    // Label in name: the accessible name comes from the label text, which starts
    // with the visible short form and adds the full amount for screen readers only.
    expect(markup).not.toContain('aria-label=');
    for (const preset of AMOUNT_PRESETS) {
      expect(markup, preset.value).toContain(
        `${preset.shortLabel}<span class="sr-only"> (${preset.label})</span></label>`,
      );
    }
    expect(markup).toContain('$ 500k');
    expect(markup).toContain('$ 1M');
    expect(markup).toContain('$ 5M');
  });
});

const recorded = await loadRanking(DEFAULT_RANKING_QUERY, { env: FIXTURES });
const small: Ranking = { ...recorded, ranked: recorded.ranked.slice(0, 2), excluded: [] };

describe('ranking explorer presets', () => {
  function explorer(): string {
    return render(
      h(RankingExplorer, {
        initialRanking: small,
        initialQuery: DEFAULT_RANKING_QUERY,
        banks: [],
        pageSlugs: [],
      }),
    );
  }

  it('ships the amount and term presets checked on the default query', () => {
    const markup = explorer();

    expect(radios(markup, 'monto-preset')[1]).toEqual({ value: '1000000', checked: true });
    expect(radios(markup, 'plazo-preset')[0]).toEqual({ value: '30', checked: true });
    expect(markup).toContain('Montos frecuentes');
    expect(markup).toContain('Plazos frecuentes');
  });

  it('keeps the free amount and term inputs beside the presets', () => {
    const markup = explorer();

    expect(markup).toMatch(/<input[^>]*id="monto"[^>]*type="number"/);
    expect(markup).toMatch(/<input[^>]*id="dias"[^>]*type="number"/);
    expect(markup).toContain('Monto en pesos');
    expect(markup).toContain('Plazo en días');
  });

  it('renders the ranked table for the query it was built with', () => {
    const markup = explorer();
    const first = small.ranked[0];

    expect(serializeRankingQuery(DEFAULT_RANKING_QUERY)).toBe('');
    expect(markup).toContain(first.name);
    expect(markup).toContain(DAYS_PRESETS[0].label);
  });
});
