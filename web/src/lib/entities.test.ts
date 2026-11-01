import { describe, expect, it } from 'vitest';
import { MissingHistoryRecording } from './data';
import {
  bestRateSentence,
  loadBankDetail,
  loadBankHistory,
  loadBankList,
  loadBankPages,
  sortBanksForListing,
  sourceName,
  type BankRates,
} from './entities';

/**
 * Bank entity pages data (T6b). Fixture mode reads the synthetic recordings under `web/fixtures/entities/`
 * (listed in its README); live mode reads the Java API and sends the build token like the rest of the build.
 */
const FIXTURES = { WEB_FIXTURES: '1' } as const;
const REBA = 'reba-compania-financiera-s-a';

function okJson(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  });
}

describe('loadBankList', () => {
  it('reads the recorded list in fixture mode', async () => {
    const banks = await loadBankList({ env: FIXTURES });
    expect(banks).toHaveLength(6);
    const reba = banks.find((bank) => bank.slug === REBA);
    expect(reba).toMatchObject({
      name: 'REBA COMPAÑIA FINANCIERA S.A.',
      instrumentId: '29',
      nonClientTna: 0.24,
      captureDate: '2026-10-14',
    });
    // A bank that only publishes a client rate has no non-client TNA.
    const bbva = banks.find((bank) => bank.slug === 'banco-bbva-argentina-s-a');
    expect(bbva?.nonClientTna).toBeNull();
    expect(bbva?.clientTna).toBe(0.3);
  });

  it('calls the entities endpoint with the build token in live mode', async () => {
    const calls: Array<{ url: string; headers: Record<string, string> }> = [];
    const doFetch = (async (url: string, init?: RequestInit) => {
      calls.push({ url, headers: init?.headers as Record<string, string> });
      return okJson([]);
    }) as unknown as typeof fetch;

    const banks = await loadBankList({
      env: { API_URL: 'http://api.test/', API_BUILD_TOKEN: ' s3cret\n' },
      fetch: doFetch,
    });

    expect(banks).toEqual([]);
    expect(calls).toHaveLength(1);
    expect(calls[0].url).toBe('http://api.test/api/v1/entities/banks');
    expect(calls[0].headers['x-build-token']).toBe('s3cret');
  });

  it('fails with the status and body of a non-OK answer, and without the token in the message', async () => {
    const doFetch = (async () => okJson({ detail: 'boom' }, 500)) as unknown as typeof fetch;
    const failure = loadBankList({
      env: { API_URL: 'http://api.test', API_BUILD_TOKEN: 's3cret' },
      fetch: doFetch,
    });
    await expect(failure).rejects.toThrow(/failed with 500.*boom/);
    await expect(failure).rejects.not.toThrow(/s3cret/);
  });

  it('refuses to build outside fixture mode without an API_URL', async () => {
    await expect(loadBankList({ env: {} })).rejects.toThrow(/API_URL is required/);
  });

  it('rejects a payload that is not a list', async () => {
    const doFetch = (async () => okJson({ banks: [] })) as unknown as typeof fetch;
    await expect(loadBankList({ env: { API_URL: 'http://api.test' }, fetch: doFetch })).rejects.toThrow(
      /Invalid bank list payload/,
    );
  });
});

describe('loadBankDetail', () => {
  it('reads one recorded bank with its series and best-rate counts', async () => {
    const detail = await loadBankDetail(REBA, { env: FIXTURES });
    expect(detail.slug).toBe(REBA);
    expect(detail.series.map((point) => point.captureDate)).toEqual([
      '2026-10-12',
      '2026-10-13',
      '2026-10-14',
    ]);
    expect(detail.bestDays).toBe(2);
    expect(detail.observedDays).toBe(3);
    expect(detail.from).toBe('2026-07-17');
    expect(detail.to).toBe('2026-10-14');
  });

  it('names the recorded slugs when a fixture is missing', async () => {
    await expect(loadBankDetail('banco-que-no-existe', { env: FIXTURES })).rejects.toThrow(
      /No recorded bank "banco-que-no-existe".*banco-bica-s-a/,
    );
  });

  it('url-encodes the slug in live mode and rejects an invalid payload', async () => {
    const urls: string[] = [];
    const doFetch = (async (url: string) => {
      urls.push(url);
      return okJson({ slug: 'x' });
    }) as unknown as typeof fetch;
    await expect(
      loadBankDetail('a b', { env: { API_URL: 'http://api.test' }, fetch: doFetch }),
    ).rejects.toThrow(/Invalid bank payload/);
    expect(urls).toEqual(['http://api.test/api/v1/entities/banks/a%20b']);
  });
});

describe('loadBankPages', () => {
  it('returns the detail of every bank of the list, in list order', async () => {
    const list = await loadBankList({ env: FIXTURES });
    const pages = await loadBankPages({ env: FIXTURES });
    expect(pages.map((page) => page.slug)).toEqual(list.map((bank) => bank.slug));
  });
});

describe('loadBankHistory', () => {
  it('reads the synthetic history of the bank instrument from the entities fixtures', async () => {
    const history = await loadBankHistory({ instrumentId: '29' }, { env: FIXTURES });
    expect(history.id).toBe('29');
    expect(history.points.map((point) => point.captureDate)).toEqual([
      '2026-10-12',
      '2026-10-13',
      '2026-10-14',
    ]);
    expect(history.monthlyInflation.length).toBeGreaterThan(0);
  });

  it('is a missing recording, not a failure, for a bank without a history fixture', async () => {
    await expect(loadBankHistory({ instrumentId: '3' }, { env: FIXTURES })).rejects.toBeInstanceOf(
      MissingHistoryRecording,
    );
  });

  it('asks the instrument history endpoint in live mode', async () => {
    const urls: string[] = [];
    const doFetch = (async (url: string) => {
      urls.push(url);
      return okJson({ id: '29', points: [], quotes: [], monthlyInflation: [] });
    }) as unknown as typeof fetch;
    await loadBankHistory({ instrumentId: '29' }, { env: { API_URL: 'http://api.test' }, fetch: doFetch });
    expect(urls).toEqual(['http://api.test/api/v1/instruments/29/history']);
  });
});

describe('bestRateSentence', () => {
  it('says N of the M days it was seen in the last 90', () => {
    expect(bestRateSentence(2, 3)).toBe('Fue la mejor tasa 2 de los 3 días que la vimos en los últimos 90.');
    expect(bestRateSentence(12, 40)).toBe('Fue la mejor tasa 12 de los 40 días que la vimos en los últimos 90.');
  });

  it('handles a bank that was never the best, a single observed day, and no observed day', () => {
    expect(bestRateSentence(0, 5)).toBe('No fue la mejor tasa ninguno de los 5 días que la vimos en los últimos 90.');
    expect(bestRateSentence(1, 1)).toBe('Fue la mejor tasa el único día que la vimos en los últimos 90.');
    expect(bestRateSentence(0, 1)).toBe('No fue la mejor tasa el único día que la vimos en los últimos 90.');
    expect(bestRateSentence(0, 0)).toBe(
      'Todavía no tenemos ningún día con tasa para no clientes de este banco en los últimos 90.',
    );
  });
});

describe('sortBanksForListing', () => {
  const base = { sourceId: 's', clientTna: null, captureDate: '2026-10-14', dataDate: '2026-10-14' };
  const bank = (name: string, nonClientTna: number | null): BankRates => ({
    ...base,
    slug: name.toLowerCase(),
    name,
    instrumentId: name,
    nonClientTna,
  });

  it('puts the highest non-client TNA first, ties by name, and banks without one last', () => {
    const sorted = sortBanksForListing([
      bank('Zeta', 0.2),
      bank('Alfa', null),
      bank('Beta', 0.25),
      bank('Ñandú', 0.2),
      bank('Álamo', 0.2),
    ]);
    expect(sorted.map((entry) => entry.name)).toEqual(['Beta', 'Álamo', 'Ñandú', 'Zeta', 'Alfa']);
  });
});

describe('sourceName', () => {
  it('names the known source and falls back to the id', () => {
    expect(sourceName('argentinadatos-plazo-fijo')).toBe('ArgentinaDatos');
    expect(sourceName('some-new-source')).toBe('some-new-source');
  });
});
