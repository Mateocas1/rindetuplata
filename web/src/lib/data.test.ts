import { describe, expect, it } from 'vitest';
import { fixtureFileFor, fixturesEnabled, loadBanks, loadRanking } from './data';

/**
 * Build-time data access. Pages read the Java API while building; with
 * `WEB_FIXTURES=1` they read the recorded JSON in `web/fixtures/` instead, so
 * tests and CI never need a running stack.
 *
 * Fixture mode only serves requests that were actually recorded: an unrecorded
 * request fails the build with the list of recordings, instead of quietly
 * shipping the wrong numbers.
 */
const FIXTURES = { WEB_FIXTURES: '1' } as const;
const DEFAULT_STATE = { amount: 1_000_000, days: 30, kinds: [], clientOf: [] };

function okJson(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  });
}

describe('fixturesEnabled', () => {
  it('is on only when WEB_FIXTURES is exactly 1', () => {
    expect(fixturesEnabled({ WEB_FIXTURES: '1' })).toBe(true);
    for (const value of [undefined, '', '0', 'true', 'yes']) {
      expect(fixturesEnabled({ WEB_FIXTURES: value })).toBe(false);
    }
  });
});

describe('fixtureFileFor', () => {
  it('maps the recorded requests to their files', () => {
    expect(fixtureFileFor(DEFAULT_STATE)).toBe('ranking-default.json');
    expect(fixtureFileFor({ ...DEFAULT_STATE, amount: 500_000, days: 60 })).toBe(
      'ranking-500k-60d.json',
    );
    expect(fixtureFileFor({ ...DEFAULT_STATE, kinds: ['PLAZO_FIJO'] })).toBe(
      'ranking-kind-PLAZO_FIJO.json',
    );
  });

  it('has no recording for anything else', () => {
    expect(fixtureFileFor({ ...DEFAULT_STATE, days: 45 })).toBeNull();
    expect(fixtureFileFor({ ...DEFAULT_STATE, amount: 250_000 })).toBeNull();
    expect(fixtureFileFor({ ...DEFAULT_STATE, kinds: ['WALLET', 'FUND'] })).toBeNull();
    expect(fixtureFileFor({ ...DEFAULT_STATE, clientOf: ['29'] })).toBeNull();
  });
});

describe('loadRanking in fixture mode', () => {
  it('returns the recorded default ranking', async () => {
    const ranking = await loadRanking(DEFAULT_STATE, { env: FIXTURES });
    expect(ranking.request.amount).toBe(1_000_000);
    expect(ranking.request.days).toBe(30);
    expect(ranking.ranked).toHaveLength(50);
    expect(ranking.totals.ranked).toBe(398);
    expect(ranking.ranked[0].name).toBe('REBA COMPAÑIA FINANCIERA S.A.');
    expect(ranking.ranked[0].earned).toBe(19726.03);
    expect(ranking.inflation?.index).toBe('REM_12M_EXPECTED');
  });

  it('returns the recorded 500.000 / 60 days ranking', async () => {
    const ranking = await loadRanking(
      { ...DEFAULT_STATE, amount: 500_000, days: 60 },
      { env: FIXTURES },
    );
    expect(ranking.request.amount).toBe(500_000);
    expect(ranking.request.days).toBe(60);
  });

  it('returns the recorded kind-filtered ranking', async () => {
    const ranking = await loadRanking({ ...DEFAULT_STATE, kinds: ['PLAZO_FIJO'] }, { env: FIXTURES });
    expect(ranking.request.kinds).toEqual(['PLAZO_FIJO']);
    expect(ranking.ranked).toHaveLength(24);
    expect(ranking.excluded).toHaveLength(8);
  });

  it('fails the build for a request nobody recorded, naming the recordings', async () => {
    await expect(loadRanking({ ...DEFAULT_STATE, days: 45 }, { env: FIXTURES })).rejects.toThrow(
      /ranking-default\.json[\s\S]*ranking-500k-60d\.json/,
    );
  });
});

describe('loadRanking against the API', () => {
  it('requires API_URL outside fixture mode', async () => {
    await expect(loadRanking(DEFAULT_STATE, { env: {} })).rejects.toThrow(/API_URL/);
  });

  it('asks the API for the recorded request and returns the payload', async () => {
    const calls: string[] = [];
    const ranking = await loadRanking(DEFAULT_STATE, {
      env: { API_URL: 'http://localhost:8080' },
      fetch: async (input) => {
        calls.push(String(input));
        return okJson({
          request: { amount: 1_000_000, days: 30, clientOf: [], kinds: [], asOf: '2026-10-14', limit: 50 },
          inflation: null,
          ranked: [],
          ineligible: [],
          excluded: [],
          totals: { ranked: 0, ineligible: 0, excluded: 0 },
        });
      },
    });

    const url = new URL(calls[0]);
    expect(url.origin + url.pathname).toBe('http://localhost:8080/api/v1/ranking');
    expect(url.searchParams.get('amount')).toBe('1000000');
    expect(url.searchParams.get('limit')).toBe('50');
    expect(ranking.request.asOf).toBe('2026-10-14');
  });

  it('sends the kind and client filters and a wider limit for the bank list', async () => {
    const calls: string[] = [];
    await loadRanking(
      { ...DEFAULT_STATE, kinds: ['PLAZO_FIJO'], clientOf: ['29'] },
      {
        env: { API_URL: 'http://localhost:8080' },
        limit: 200,
        fetch: async (input) => {
          calls.push(String(input));
          return okJson({ ranked: [], ineligible: [], excluded: [] });
        },
      },
    );
    const url = new URL(calls[0]);
    expect(url.searchParams.getAll('kind')).toEqual(['PLAZO_FIJO']);
    expect(url.searchParams.getAll('clientOf')).toEqual(['29']);
    expect(url.searchParams.get('limit')).toBe('200');
  });

  it('fails the build when the API rejects the request', async () => {
    await expect(
      loadRanking(DEFAULT_STATE, {
        env: { API_URL: 'http://localhost:8080' },
        fetch: async () =>
          new Response('{"detail":"boom"}', {
            status: 500,
            headers: { 'content-type': 'application/json' },
          }),
      }),
    ).rejects.toThrow(/500[\s\S]*boom/);
  });

  it('fails the build when the payload is not a ranking', async () => {
    await expect(
      loadRanking(DEFAULT_STATE, {
        env: { API_URL: 'http://localhost:8080' },
        fetch: async () => okJson({ hello: 'world' }),
      }),
    ).rejects.toThrow(/invalid ranking/i);
  });
});

describe('loadBanks', () => {
  it('offers every plazo fijo bank once, alphabetical, ranked or not', async () => {
    const banks = await loadBanks({ env: FIXTURES });
    expect(banks).toHaveLength(32);
    expect(new Set(banks.map((bank) => bank.id)).size).toBe(32);
    for (const bank of banks) {
      expect(bank.id).toMatch(/^\d+$/);
      expect(bank.name.length).toBeGreaterThan(0);
    }
    const names = banks.map((bank) => bank.name);
    expect([...names].sort((a, b) => a.localeCompare(b, 'es'))).toEqual(names);
    expect(names).toContain('BANCO DE LA NACION ARGENTINA');
  });

  it('asks the API for the plazo fijo kind only', async () => {
    const calls: string[] = [];
    await loadBanks({
      env: { API_URL: 'http://localhost:8080' },
      fetch: async (input) => {
        calls.push(String(input));
        return okJson({
          ranked: [
            {
              id: '29',
              kind: 'PLAZO_FIJO',
              name: 'REBA',
              entity: 'REBA',
              source: { id: 'x', displayName: 'X', official: false },
              confidence: 'AGGREGATOR_AUTOMATIC',
              verifiedAt: null,
              captureDate: '2026-10-14',
              dataDate: '2026-10-14',
              rateUsed: { fraction: 0.24, basis: 'TNA', kind: 'NON_CLIENT' },
              finalAmount: 1,
              earned: 1,
              nominalReturn: 0,
              realReturn: 0,
              upTo: null,
              warnings: [],
            },
          ],
          ineligible: [],
          excluded: [],
        });
      },
    });
    const url = new URL(calls[0]);
    expect(url.searchParams.getAll('kind')).toEqual(['PLAZO_FIJO']);
    expect(url.searchParams.get('limit')).toBe('200');
  });
});
