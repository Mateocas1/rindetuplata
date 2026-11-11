import { describe, expect, it } from 'vitest';
import { MissingHistoryRecording } from './data';
import { formatDate } from './format';
import {
  bestRateSentence,
  loadBankDetail,
  loadBankHistory,
  loadBankList,
  loadBankPages,
  bankPageCopy,
  sortBanksForListing,
  splitBanksByFreshness,
  sourceName,
  type BankRates,
} from './entities';

/**
 * Bank entity pages data. Fixture mode reads the synthetic recordings under `web/fixtures/entities/`
 * (listed in its README); live mode reads the Java API and sends the build token like the rest of the build.
 */
const FIXTURES = { WEB_FIXTURES: '1' } as const;
const REBA = 'reba-compania-financiera-s-a';
const VOII = 'banco-voii-s-a';

const noSleep = async () => undefined;

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

  it('carries `current`, with one bank whose last rate is older than its source\'s newest capture', async () => {
    const banks = await loadBankList({ env: FIXTURES });
    expect(banks.every((bank) => typeof bank.current === 'boolean')).toBe(true);
    expect(banks.filter((bank) => !bank.current).map((bank) => bank.slug)).toEqual([VOII]);
    expect(banks.find((bank) => bank.slug === VOII)?.captureDate).toBe('2026-10-12');
    expect(banks.find((bank) => bank.slug === REBA)?.current).toBe(true);
  });

  it('rejects a bank payload without a boolean `current`', async () => {
    const { current: _current, ...stale } = (await loadBankList({ env: FIXTURES }))[0];
    const doFetch = (async () => okJson([stale])) as unknown as typeof fetch;
    await expect(loadBankList({ env: { API_URL: 'http://api.test' }, fetch: doFetch })).rejects.toThrow(
      /Invalid bank payload/,
    );
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
      sleep: noSleep,
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

  it('reads `current` on the detail, false for the bank whose rate is not from the newest capture', async () => {
    expect((await loadBankDetail(REBA, { env: FIXTURES })).current).toBe(true);
    const voii = await loadBankDetail(VOII, { env: FIXTURES });
    expect(voii.current).toBe(false);
    expect(voii.captureDate).toBe('2026-10-12');
    expect(voii.series.map((point) => point.captureDate)).toEqual(['2026-10-12']);
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
  const base = {
    sourceId: 's',
    clientTna: null,
    captureDate: '2026-10-14',
    dataDate: '2026-10-14',
    current: true,
  };
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

describe('splitBanksByFreshness', () => {
  const base = { sourceId: 's', clientTna: null, captureDate: '2026-10-14', dataDate: '2026-10-14' };
  const bank = (name: string, nonClientTna: number | null, current: boolean): BankRates => ({
    ...base,
    slug: name.toLowerCase(),
    name,
    instrumentId: name,
    nonClientTna,
    current,
  });

  it('keeps current banks sorted as the listing sorts them and puts the others apart, sorted the same way', () => {
    const { current, previous } = splitBanksByFreshness([
      bank('Zeta', 0.2, true),
      bank('Viejo alto', 0.4, false),
      bank('Beta', 0.25, true),
      bank('Viejo sin tasa', null, false),
      bank('Alfa', null, true),
      bank('Viejo bajo', 0.1, false),
    ]);
    expect(current.map((entry) => entry.name)).toEqual(['Beta', 'Zeta', 'Alfa']);
    expect(previous.map((entry) => entry.name)).toEqual(['Viejo alto', 'Viejo bajo', 'Viejo sin tasa']);
  });

  it('leaves `previous` empty when every bank is current', () => {
    const { current, previous } = splitBanksByFreshness([bank('Uno', 0.2, true)]);
    expect(current).toHaveLength(1);
    expect(previous).toEqual([]);
  });
});

describe('bankPageCopy', () => {
  const base = {
    slug: 'banco-x',
    name: 'BANCO X',
    sourceId: 'argentinadatos-plazo-fijo',
    clientTna: 0.2,
    captureDate: '2026-10-14',
    dataDate: '2026-10-14',
  };

  it('says "hoy" in the title, description and headline of a current bank', () => {
    const copy = bankPageCopy({ ...base, nonClientTna: 0.24, current: true });
    expect(copy.title).toBe('BANCO X: plazo fijo 24,00% TNA hoy | rindetuplata');
    expect(copy.description).toContain('Cuánto paga hoy BANCO X');
    expect(copy.headline).toBe('Hoy: 24,00% TNA para no clientes');
  });

  it('says "hoy" in the headline of a current bank that offers no non-client plazo fijo', () => {
    const copy = bankPageCopy({ ...base, nonClientTna: null, current: true });
    expect(copy.headline).toBe('Hoy no ofrece plazo fijo a no clientes');
    expect(copy.title).toBe('BANCO X: plazo fijo, tasa para clientes | rindetuplata');
    expect(copy.description).toContain('hoy');
  });

  it('states the last published rate and its date, and never says "hoy", for a bank that is not current', () => {
    for (const nonClientTna of [0.24, null]) {
      const copy = bankPageCopy({ ...base, nonClientTna, captureDate: '2026-10-12', current: false });
      const date = formatDate('2026-10-12');
      expect(copy.headline).toContain(`Última tasa publicada (${date})`);
      expect(copy.title).not.toMatch(/\bhoy\b/i);
      expect(copy.description).not.toMatch(/\bhoy\b/i);
      expect(copy.headline).not.toMatch(/\bhoy\b/i);
      expect(copy.description).toContain(date);
    }
    const copy = bankPageCopy({ ...base, nonClientTna: 0.24, captureDate: '2026-10-12', current: false });
    expect(copy.headline).toBe(`Última tasa publicada (${formatDate('2026-10-12')}): 24,00% TNA para no clientes`);
    expect(copy.title).toContain('24,00% TNA');
  });
});

describe('sourceName', () => {
  it('names the known source and falls back to the id', () => {
    expect(sourceName('argentinadatos-plazo-fijo')).toBe('ArgentinaDatos');
    expect(sourceName('some-new-source')).toBe('some-new-source');
  });
});

describe('the build client on the bank loaders (T4d, L36a; F6)', () => {
  const ENV = { API_URL: 'http://api.test' };
  const BANK = {
    slug: 'banco-x',
    name: 'Banco X',
    instrumentId: '7',
    nonClientTna: 0.3,
    clientTna: null,
    captureDate: '2026-10-14',
    current: true,
  };
  const DETAIL = { ...BANK, from: '2026-07-17', to: '2026-10-14', series: [], bestDays: 0, observedDays: 0 };

  function counting(respond: (call: number) => Response | Promise<Response>) {
    const calls: string[] = [];
    const doFetch = (async (input: RequestInfo | URL) => {
      calls.push(String(input));
      return respond(calls.length);
    }) as unknown as typeof fetch;
    return { fetch: doFetch, calls };
  }

  it('retries a 503 on the list and returns it', async () => {
    const waits: number[] = [];
    const { fetch, calls } = counting((call) => (call === 1 ? new Response('down', { status: 503 }) : okJson([BANK])));
    const banks = await loadBankList({
      env: ENV,
      fetch,
      sleep: async (ms) => {
        waits.push(ms);
      },
    });
    expect(banks.map((bank) => bank.slug)).toEqual(['banco-x']);
    expect(calls).toHaveLength(2);
    expect(waits).toEqual([1000]);
  });

  it('gives a hung list request a deadline (API_TIMEOUT_MS) and names the URL', async () => {
    const { fetch, calls } = counting(() => new Promise<Response>(() => undefined));
    const failure = loadBankList({ env: { ...ENV, API_TIMEOUT_MS: '20' }, fetch, sleep: noSleep });
    await expect(failure).rejects.toThrow(/GET http:\/\/api\.test\/api\/v1\/entities\/banks failed after 3 attempts: timed out after 20 ms/);
    expect(calls).toHaveLength(3);
  });

  it('retries a network error and a 429 on a bank detail, but never a 404', async () => {
    let attempts = 0;
    const flaky = counting(() => {
      attempts += 1;
      if (attempts === 1) throw new TypeError('fetch failed');
      return attempts === 2 ? new Response('slow down', { status: 429 }) : okJson(DETAIL);
    });
    expect((await loadBankDetail('banco-x', { env: ENV, fetch: flaky.fetch, sleep: noSleep })).slug).toBe('banco-x');
    expect(flaky.calls).toHaveLength(3);

    const missing = counting(() => okJson({ detail: 'no such bank' }, 404));
    await expect(loadBankDetail('banco-y', { env: ENV, fetch: missing.fetch, sleep: noSleep })).rejects.toThrow(
      /failed with 404.*no such bank/,
    );
    expect(missing.calls).toHaveLength(1);
  });

  it('sends an identical request once per build: the list for the page and for every bank page', async () => {
    const { fetch, calls } = counting((call) => okJson(call === 1 ? [BANK] : DETAIL));
    const options = { env: ENV, fetch, sleep: noSleep };
    await loadBankList(options);
    await loadBankList(options);
    await loadBankPages(options);
    await loadBankDetail('banco-x', options);
    expect(calls).toEqual(['http://api.test/api/v1/entities/banks', 'http://api.test/api/v1/entities/banks/banco-x']);
  });
});
