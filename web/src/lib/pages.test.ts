import { describe, expect, it } from 'vitest';
import { loadInstrumentPages } from './pages';
import { instrumentSlug } from './slug';
import { RANKING_KINDS } from './types';

/**
 * The instrument page set (T7c, D1).
 *
 * A page must exist for every instrument any prerendered table can link to: the
 * union of the global recording and every kind recording. The table asks this
 * module before it renders a link, so a row without a page is plain text and the
 * site never links to a 404.
 */
const FIXTURES = { WEB_FIXTURES: '1' } as const;

describe('loadInstrumentPages', () => {
  it('covers every instrument a prerendered table can link to', async () => {
    const pages = await loadInstrumentPages({ env: FIXTURES });
    // 200 rows of the global recording plus the 29 that only the kind
    // recordings hold (a kind recording keeps rows the global limit cut).
    expect(pages.slugs).toHaveLength(229);
    expect(pages.pages).toHaveLength(229);
  });

  it('keys pages by slug: one instrument id may be a wallet and its fund class', async () => {
    const pages = await loadInstrumentPages({ env: FIXTURES });
    expect(new Set(pages.slugs).size).toBe(pages.slugs.length);
    expect(pages.pages.map((page) => instrumentSlug(page.entry.name, page.entry.id))).toEqual(
      pages.slugs,
    );

    const wallet = pages.pages.find(
      (page) => page.entry.id === '423' && page.entry.name === 'Mercado Pago',
    );
    const fundClass = pages.pages.find(
      (page) => page.entry.id === '423' && page.entry.name.startsWith('Mercado Fondo'),
    );
    expect(wallet).toBeDefined();
    expect(fundClass).toBeDefined();
    expect(instrumentSlug(wallet!.entry.name, wallet!.entry.id)).not.toBe(
      instrumentSlug(fundClass!.entry.name, fundClass!.entry.id),
    );
  });

  it('has pages for every kind and knows which entries the ranking scored', async () => {
    const pages = await loadInstrumentPages({ env: FIXTURES });
    for (const kind of RANKING_KINDS) {
      const ofKind = pages.pages.filter((page) => page.entry.kind === kind);
      expect(ofKind.length, kind).toBeGreaterThan(0);
      expect(
        ofKind.some((page) => page.isRanked),
        kind,
      ).toBe(true);
    }
    expect(pages.pages.every((page) => page.entry.id !== '' && page.entry.name !== '')).toBe(true);
  });

  it('prefers the global recording when a row is in both', async () => {
    const pages = await loadInstrumentPages({ env: FIXTURES });
    const wallet = pages.pages.find(
      (page) => page.entry.id === '423' && page.entry.name === 'Mercado Pago',
    );
    // The global recording is the one with the inflation and the default request.
    expect(wallet?.isRanked).toBe(true);
  });

  it('carries the inflation index the pages label their real return with (S10)', async () => {
    const pages = await loadInstrumentPages({ env: FIXTURES });
    expect(pages.inflation?.index).toBe('REM_12M_EXPECTED');
  });

  it('is deterministic', async () => {
    const [first, second] = await Promise.all([
      loadInstrumentPages({ env: FIXTURES }),
      loadInstrumentPages({ env: FIXTURES }),
    ]);
    expect(first.slugs).toEqual(second.slugs);
  });

  it('fails the build when a recording is missing', async () => {
    await expect(loadInstrumentPages({ env: {} })).rejects.toThrow(/API_URL/);
  });
});
