import { describe, expect, it } from 'vitest';
import {
  BuildGateError,
  DEFAULT_MAX_DATA_AGE_DAYS,
  DEFAULT_MIN_RANKED,
  dataAgeDays,
  gateSettingsFromEnv,
  runBuildGate,
} from './buildGate';
import { CATEGORIES } from './categories';

/**
 * The pre-render gate (T4d, L36a): before any page is rendered the default
 * ranking must be fresh, big enough, and every category page must have rows, or
 * the build fails naming the check and the previous site keeps being served.
 * Fixture mode skips only the age check.
 */
const API = { API_URL: 'http://api.test' };
const NOW = () => new Date('2026-11-01T15:00:00Z'); // 12:00 in Buenos Aires
const noSleep = async () => {};

function entry(id: number, captureDate: string, kind = 'PLAZO_FIJO') {
  return { id: String(id), kind, name: `Entry ${id}`, captureDate };
}

function ranking(entries: ReturnType<typeof entry>[]) {
  return { request: { asOf: '2026-11-01' }, ranked: entries, ineligible: [], excluded: [] };
}

function entries(count: number, captureDate = '2026-11-01', kind = 'PLAZO_FIJO') {
  return Array.from({ length: count }, (_, index) => entry(index + 1, captureDate, kind));
}

/** A fake API: the default ranking, plus whatever a kind asks for. */
function fakeApi(handlers: {
  default?: () => Response;
  kind?: (kind: string) => Response;
}) {
  const calls: string[] = [];
  const fetch = (async (input: RequestInfo | URL) => {
    const url = new URL(String(input));
    calls.push(`${url.pathname}${url.search}`);
    const kind = url.searchParams.get('kind');
    if (kind === null) return (handlers.default ?? (() => json(ranking(entries(30)))))();
    return (handlers.kind ?? ((k) => json(ranking(entries(3, '2026-11-01', k)))))(kind);
  }) as unknown as typeof globalThis.fetch;
  return { fetch, calls };
}

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  });
}

async function gateMessage(promise: Promise<unknown>): Promise<string> {
  try {
    await promise;
  } catch (error) {
    expect(error).toBeInstanceOf(BuildGateError);
    return (error as Error).message;
  }
  throw new Error('the gate did not fail');
}

describe('defaults', () => {
  it('are the values L36a decided', () => {
    expect(DEFAULT_MAX_DATA_AGE_DAYS).toBe(3);
    expect(DEFAULT_MIN_RANKED).toBe(20);
  });
});

describe('gateSettingsFromEnv', () => {
  it('uses the defaults when the variables are unset or blank', () => {
    expect(gateSettingsFromEnv({})).toEqual({ maxDataAgeDays: 3, minRanked: 20 });
    expect(gateSettingsFromEnv({ BUILD_MAX_DATA_AGE_DAYS: ' ', BUILD_MIN_RANKED: '' })).toEqual({
      maxDataAgeDays: 3,
      minRanked: 20,
    });
  });

  it('reads whole numbers, zero included', () => {
    expect(gateSettingsFromEnv({ BUILD_MAX_DATA_AGE_DAYS: '0', BUILD_MIN_RANKED: '40' })).toEqual({
      maxDataAgeDays: 0,
      minRanked: 40,
    });
  });

  it.each([
    ['BUILD_MAX_DATA_AGE_DAYS', 'yesterday'],
    ['BUILD_MAX_DATA_AGE_DAYS', '-1'],
    ['BUILD_MIN_RANKED', '2.5'],
    ['BUILD_MIN_RANKED', 'many'],
  ])('rejects %s=%s naming the variable', (name, value) => {
    expect(() => gateSettingsFromEnv({ [name]: value })).toThrow(
      `${name} must be a non-negative whole number, got "${value}"`,
    );
  });
});

describe('dataAgeDays', () => {
  it('counts calendar days in Buenos Aires, not hours', () => {
    expect(dataAgeDays('2026-11-01', new Date('2026-11-01T02:30:00Z'))).toBe(-1); // still Oct 31 at 23:30 -03
    expect(dataAgeDays('2026-10-31', new Date('2026-11-01T02:30:00Z'))).toBe(0);
    expect(dataAgeDays('2026-10-29', new Date('2026-11-01T15:00:00Z'))).toBe(3);
    expect(dataAgeDays('2026-10-14', new Date('2026-11-01T15:00:00Z'))).toBe(18);
  });
});

describe('runBuildGate against the API', () => {
  it('passes with a fresh, big enough default ranking and rows in every category', async () => {
    const { fetch, calls } = fakeApi({});
    await expect(runBuildGate({ env: API, fetch, sleep: noSleep, now: NOW })).resolves.toBeUndefined();
    // The default ranking plus one ranking per category: nothing else is read.
    expect(calls).toHaveLength(1 + CATEGORIES.length);
  });

  it('accepts data exactly BUILD_MAX_DATA_AGE_DAYS old', async () => {
    const { fetch } = fakeApi({ default: () => json(ranking(entries(30, '2026-10-29'))) });
    await expect(runBuildGate({ env: API, fetch, sleep: noSleep, now: NOW })).resolves.toBeUndefined();
  });

  it('fails with the data-age check when the newest capture date is too old', async () => {
    const { fetch } = fakeApi({
      default: () => json(ranking([...entries(29, '2026-10-14'), entry(30, '2026-10-20')])),
    });
    const message = await gateMessage(runBuildGate({ env: API, fetch, sleep: noSleep, now: NOW }));
    expect(message).toContain('data-age');
    expect(message).toContain('newest capture date in the default ranking is 2026-10-20, 12 days old');
    expect(message).toContain('BUILD_MAX_DATA_AGE_DAYS=3');
    expect(message).not.toContain('min-ranked');
    expect(message).not.toContain('category-rankings');
  });

  it('the newest capture date decides, not the first row', async () => {
    const { fetch } = fakeApi({
      default: () => json(ranking([entry(1, '2026-10-01'), ...entries(29, '2026-11-01')])),
    });
    await expect(runBuildGate({ env: API, fetch, sleep: noSleep, now: NOW })).resolves.toBeUndefined();
  });

  it('honors BUILD_MAX_DATA_AGE_DAYS', async () => {
    const { fetch } = fakeApi({ default: () => json(ranking(entries(30, '2026-10-29'))) });
    const message = await gateMessage(
      runBuildGate({
        env: { ...API, BUILD_MAX_DATA_AGE_DAYS: '2' },
        fetch,
        sleep: noSleep,
        now: NOW,
      }),
    );
    expect(message).toContain('data-age');
    expect(message).toContain('3 days old');
    expect(message).toContain('BUILD_MAX_DATA_AGE_DAYS=2');
  });

  it('fails with the min-ranked check when the default ranking is too small', async () => {
    const { fetch } = fakeApi({ default: () => json(ranking(entries(19))) });
    const message = await gateMessage(runBuildGate({ env: API, fetch, sleep: noSleep, now: NOW }));
    expect(message).toContain('min-ranked');
    expect(message).toContain('the default ranking has 19 ranked entries, fewer than BUILD_MIN_RANKED=20');
    expect(message).not.toContain('data-age');
  });

  it('honors BUILD_MIN_RANKED', async () => {
    const { fetch } = fakeApi({});
    const message = await gateMessage(
      runBuildGate({ env: { ...API, BUILD_MIN_RANKED: '31' }, fetch, sleep: noSleep, now: NOW }),
    );
    expect(message).toContain('the default ranking has 30 ranked entries, fewer than BUILD_MIN_RANKED=31');
  });

  it('an empty default ranking fails both data-age and min-ranked', async () => {
    const { fetch } = fakeApi({ default: () => json(ranking([])) });
    const message = await gateMessage(runBuildGate({ env: API, fetch, sleep: noSleep, now: NOW }));
    expect(message).toContain('data-age: the default ranking has no ranked entry, so it has no capture date');
    expect(message).toContain('min-ranked');
  });

  it('fails with the category-rankings check naming every empty category', async () => {
    const { fetch } = fakeApi({
      kind: (kind) => json(ranking(kind === 'WALLET' || kind === 'CRYPTO' ? [] : entries(2, '2026-11-01', kind))),
    });
    const message = await gateMessage(runBuildGate({ env: API, fetch, sleep: noSleep, now: NOW }));
    expect(message).toContain('category-rankings');
    expect(message).toContain('billeteras (WALLET)');
    expect(message).toContain('cripto (CRYPTO)');
    expect(message).not.toContain('plazo-fijo (PLAZO_FIJO)');
    expect(message).not.toContain('fondos (FUND)');
  });

  it('lists every failing check in one message', async () => {
    const { fetch } = fakeApi({
      default: () => json(ranking(entries(3, '2026-10-01'))),
      kind: () => json(ranking([])),
    });
    const message = await gateMessage(runBuildGate({ env: API, fetch, sleep: noSleep, now: NOW }));
    for (const check of ['data-age', 'min-ranked', 'category-rankings']) {
      expect(message).toContain(check);
    }
    expect(message).toContain('nothing was rendered');
  });

  it('fails with the api-read check when the API stays down after the retries', async () => {
    const calls: string[] = [];
    const fetch = (async (input: RequestInfo | URL) => {
      calls.push(String(input));
      return new Response('bad gateway', { status: 502 });
    }) as unknown as typeof globalThis.fetch;
    const message = await gateMessage(runBuildGate({ env: API, fetch, sleep: noSleep, now: NOW }));
    expect(message).toContain('api-read');
    expect(message).toContain('failed with 502: bad gateway');
    expect(calls).toHaveLength(3);
  });

  it('rejects an unreadable setting before reading anything', async () => {
    const { fetch, calls } = fakeApi({});
    await expect(
      runBuildGate({ env: { ...API, BUILD_MIN_RANKED: 'many' }, fetch, sleep: noSleep, now: NOW }),
    ).rejects.toThrow('BUILD_MIN_RANKED must be a non-negative whole number, got "many"');
    expect(calls).toHaveLength(0);
  });

  it('reads the same requests the pages read, so the pages cost no second fetch', async () => {
    const { fetch, calls } = fakeApi({});
    const { loadRanking, CATALOG_LIMIT } = await import('./data');
    const { DEFAULT_RANKING_QUERY } = await import('./rankingQuery');
    const options = { env: API, fetch, sleep: noSleep, now: NOW };
    await runBuildGate(options);
    const before = calls.length;
    await loadRanking(DEFAULT_RANKING_QUERY, options);
    for (const category of CATEGORIES) {
      await loadRanking({ ...DEFAULT_RANKING_QUERY, kinds: [category.kind] }, { ...options, limit: CATALOG_LIMIT });
    }
    expect(calls).toHaveLength(before);
  });
});

describe('runBuildGate in fixture mode', () => {
  const FIXTURES = { WEB_FIXTURES: '1' };

  it('skips only the age check: 2026-10-14 data passes however strict the age setting', async () => {
    await expect(
      runBuildGate({ env: { ...FIXTURES, BUILD_MAX_DATA_AGE_DAYS: '0' }, now: NOW }),
    ).resolves.toBeUndefined();
  });

  it('still enforces the minimum of ranked entries', async () => {
    const message = await gateMessage(
      runBuildGate({ env: { ...FIXTURES, BUILD_MIN_RANKED: '100000' }, now: NOW }),
    );
    expect(message).toContain('min-ranked');
    expect(message).toContain('fewer than BUILD_MIN_RANKED=100000');
    expect(message).not.toContain('data-age');
  });

  it('still enforces non-empty category rankings', async () => {
    const { mkdtemp, cp, writeFile, rm } = await import('node:fs/promises');
    const { tmpdir } = await import('node:os');
    const { join, resolve } = await import('node:path');
    const directory = await mkdtemp(join(tmpdir(), 'gate-fixtures-'));
    try {
      await cp(resolve(process.cwd(), 'fixtures'), directory, { recursive: true });
      const emptied = JSON.parse(
        (await import('node:fs')).readFileSync(join(directory, 'ranking-kind-WALLET.json'), 'utf8'),
      ) as { ranked: unknown[]; totals: { ranked: number } };
      emptied.ranked = [];
      await writeFile(join(directory, 'ranking-kind-WALLET.json'), JSON.stringify(emptied));
      const message = await gateMessage(runBuildGate({ env: FIXTURES, fixturesDir: directory, now: NOW }));
      expect(message).toContain('category-rankings');
      expect(message).toContain('billeteras (WALLET)');
      expect(message).not.toContain('data-age');
    } finally {
      await rm(directory, { recursive: true, force: true });
    }
  });

  it('passes on the recorded fixtures with the defaults', async () => {
    await expect(runBuildGate({ env: FIXTURES, now: NOW })).resolves.toBeUndefined();
  });
});
