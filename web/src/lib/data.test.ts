import { mkdtemp, rm, writeFile } from 'node:fs/promises';
import { readFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';
import {
  MissingHistoryRecording,
  RECORDED_RANKINGS,
  fixtureFileFor,
  fixturesEnabled,
  loadBanks,
  loadHistory,
  loadRanking,
  loadRankingCatalog,
} from './data';
import { RANKING_KINDS } from './types';

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
const webRoot = resolve(fileURLToPath(new URL('.', import.meta.url)), '../..');

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
  it('matches the recorded request exactly, limit included', () => {
    expect(fixtureFileFor(DEFAULT_STATE)).toBe('ranking-default.json');
    expect(fixtureFileFor(DEFAULT_STATE, 50)).toBe('ranking-default.json');
    expect(fixtureFileFor(DEFAULT_STATE, 200)).toBe('ranking-all.json');
    expect(fixtureFileFor({ ...DEFAULT_STATE, amount: 500_000, days: 60 })).toBe(
      'ranking-500k-60d.json',
    );
    expect(fixtureFileFor({ ...DEFAULT_STATE, kinds: ['PLAZO_FIJO'] }, 200)).toBe(
      'ranking-kind-PLAZO_FIJO.json',
    );
    expect(fixtureFileFor({ ...DEFAULT_STATE, kinds: ['FUND'] }, 200)).toBe(
      'ranking-kind-FUND.json',
    );
  });

  it('has no recording for any other combination', () => {
    expect(fixtureFileFor({ ...DEFAULT_STATE, days: 45 })).toBeNull();
    expect(fixtureFileFor({ ...DEFAULT_STATE, amount: 250_000 })).toBeNull();
    expect(fixtureFileFor({ ...DEFAULT_STATE, clientOf: ['29'] })).toBeNull();
    expect(fixtureFileFor({ ...DEFAULT_STATE, kinds: ['WALLET', 'FUND'] }, 200)).toBeNull();
    // Recorded at 200 only: a 50-row kind request was never made.
    expect(fixtureFileFor({ ...DEFAULT_STATE, kinds: ['PLAZO_FIJO'] }, 50)).toBeNull();
    // Neither the 50-row nor the 200-row recording answers a 100-row request.
    expect(fixtureFileFor(DEFAULT_STATE, 100)).toBeNull();
    expect(fixtureFileFor({ ...DEFAULT_STATE, amount: 500_000, days: 60 }, 200)).toBeNull();
  });

  it('declares exactly the request each recording holds', () => {
    for (const recorded of RECORDED_RANKINGS) {
      const file = JSON.parse(
        readFileSync(join(webRoot, 'fixtures', recorded.file), 'utf8'),
      ) as {
        request: {
          amount: number;
          days: number;
          kinds: string[];
          clientOf: string[];
          limit: number;
        };
      };
      expect(recorded.amount, recorded.file).toBe(file.request.amount);
      expect(recorded.days, recorded.file).toBe(file.request.days);
      expect(recorded.limit, recorded.file).toBe(file.request.limit);
      // An unfiltered request comes back listing every kind, so the table stores
      // the filter (empty) and the echo is compared as the equivalent full set.
      const effectiveKinds =
        recorded.kinds.length === 0 ? [...RANKING_KINDS] : [...recorded.kinds];
      expect([...file.request.kinds].sort(), recorded.file).toEqual(effectiveKinds.sort());
      expect([...recorded.clientOf], recorded.file).toEqual([...file.request.clientOf]);
    }
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
    const ranking = await loadRanking(
      { ...DEFAULT_STATE, kinds: ['PLAZO_FIJO'] },
      { env: FIXTURES, limit: 200 },
    );
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

describe('loadRankingCatalog', () => {
  it('returns the wide recording with every ranked and ineligible instrument', async () => {
    const catalog = await loadRankingCatalog({ env: FIXTURES });
    expect(catalog.request.limit).toBe(200);
    expect(catalog.ranked).toHaveLength(200);
    expect(catalog.totals.ranked).toBe(398);
    expect(catalog.ranked.some((entry) => entry.kind === 'FUND')).toBe(true);
    expect(catalog.ranked.some((entry) => entry.kind === 'PLAZO_FIJO')).toBe(true);
  });

  it('asks the API for 200 rows', async () => {
    const calls: string[] = [];
    await loadRankingCatalog({
      env: { API_URL: 'http://localhost:8080' },
      fetch: async (input) => {
        calls.push(String(input));
        return okJson({ ranked: [], ineligible: [], excluded: [] });
      },
    });
    const url = new URL(calls[0]);
    expect(url.searchParams.get('limit')).toBe('200');
  });
});

describe('loadHistory in fixture mode', () => {
  it('returns the recorded history of a bank', async () => {
    const history = await loadHistory('29', { env: FIXTURES });
    expect(history.id).toBe('29');
    expect(history.points).toHaveLength(1);
    expect(history.points[0].rate).toBe(0.24);
    expect(history.monthlyInflation).toHaveLength(3);
  });

  it('returns the recorded quotes of a fund', async () => {
    const history = await loadHistory('102', { env: FIXTURES });
    expect(history.quotes).toHaveLength(2);
    expect(history.quotes[1].tnaEquivalent).toBeCloseTo(0.175192, 6);
  });

  it('fails the build for an unrecorded instrument, naming the recordings', async () => {
    await expect(loadHistory('999', { env: FIXTURES })).rejects.toThrow(
      /No recorded history for instrument 999[\s\S]*fixtures\/history/,
    );
  });

  it('asks the API for the recorded instrument outside fixture mode', async () => {
    const calls: string[] = [];
    await loadHistory('29', {
      env: { API_URL: 'http://localhost:8080' },
      fetch: async (input) => {
        calls.push(String(input));
        return okJson({
          id: '29',
          kind: 'PLAZO_FIJO_BANK',
          displayName: 'REBA',
          entityName: null,
          segment: null,
          sourceId: 'argentinadatos-plazo-fijo',
          from: '2026-07-17',
          to: '2026-10-14',
          points: [],
          quotes: [],
          monthlyInflation: [],
        });
      },
    });
    expect(calls[0]).toBe('http://localhost:8080/api/v1/instruments/29/history');
  });

  it('fails the build when the API rejects the request or the payload is wrong', async () => {
    await expect(
      loadHistory('29', {
        env: { API_URL: 'http://localhost:8080' },
        fetch: async () => new Response('nope', { status: 404 }),
      }),
    ).rejects.toThrow(/404/);
    await expect(
      loadHistory('29', {
        env: { API_URL: 'http://localhost:8080' },
        fetch: async () => okJson({ id: '29' }),
      }),
    ).rejects.toThrow(/invalid history/i);
  });
});

describe('loadHistory complaints (D9)', () => {
  it('reports a missing recording with a type the page can catch', async () => {
    await expect(loadHistory('999', { env: FIXTURES })).rejects.toBeInstanceOf(
      MissingHistoryRecording,
    );
  });

  it('names the recorded ids when an instrument was never recorded', async () => {
    await expect(loadHistory('999', { env: FIXTURES })).rejects.toThrow(
      /No recorded history for instrument 999[\s\S]*fixtures\/history\/<id>\.json[\s\S]*\[5, 10, 11/,
    );
  });

  it('gives the same helpful message when there is no history directory at all', async () => {
    await expect(
      loadHistory('29', { env: FIXTURES, fixturesDir: join(webRoot, 'fixtures-nonexistent') }),
    ).rejects.toThrow(/No recorded history for instrument 29[\s\S]*fixtures\/history/);
  });

  it('does not report an unexpected read error as a missing recording', async () => {
    const scratch = await mkdtemp(join(tmpdir(), 'rindetuplata-fixtures-'));
    await writeFile(join(scratch, 'history'), 'not a directory', 'utf8');
    await expect(
      loadHistory('29', { env: FIXTURES, fixturesDir: scratch }),
    ).rejects.not.toThrow(/No recorded history/);
    await rm(scratch, { recursive: true, force: true });
  });
});
