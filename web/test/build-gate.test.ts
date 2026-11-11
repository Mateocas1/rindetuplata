import { execFile } from 'node:child_process';
import { existsSync, readFileSync, rmSync } from 'node:fs';
import { createServer, type IncomingMessage, type Server, type ServerResponse } from 'node:http';
import type { AddressInfo } from 'node:net';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { promisify } from 'node:util';
import { afterAll, describe, expect, it } from 'vitest';
import { fixtureFileFor } from '../src/lib/data';
import type { RankedKind } from '../src/lib/types';

/**
 * The real `astro build` against a fake API: the pre-render gate refuses a stale, thin or
 * category-less ranking before any page is rendered (exit code, message naming the check, no page
 * requested, nothing written), fixture mode skips only the age check, and a complete build sends every
 * identical request once and rides out a transient 503.
 *
 * Each build writes to its own scratch `--outDir` so it never shares `dist` with the smoke tests.
 */
const webRoot = resolve(fileURLToPath(new URL('.', import.meta.url)), '..');
const astroBin = join(webRoot, 'node_modules/.bin/astro');
const fixtures = join(webRoot, 'fixtures');
// Astro moves assets with `rename`, which fails across devices: the output directory lives inside the project
// (git-ignored `.astro/`), like the other smoke tests', not in the system temp directory.
const scratch = join(webRoot, '.astro', `build-gate-test-${process.pid}`);
const run = promisify(execFile);

afterAll(() => {
  rmSync(scratch, { recursive: true, force: true });
});

function fixture(path: string): unknown {
  return JSON.parse(readFileSync(join(fixtures, path), 'utf8'));
}

type RankingBody = { ranked: Array<{ captureDate: string }> } & Record<string, unknown>;

function argentineToday(): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Argentina/Buenos_Aires' }).format(
    new Date(),
  );
}

interface FakeApi {
  url: string;
  /** `path?query` of every request, in order. */
  requests: string[];
  close(): Promise<void>;
}

interface Overrides {
  /** Replaces the answer to the default ranking request (no `kind`). */
  defaultRanking?: unknown;
  /** Replaces the answer to a category request. */
  kindRanking?: (kind: RankedKind) => unknown;
  /** Answers the first request for this path+query with a 503 before serving normally. */
  failOnceFor?: (target: string) => boolean;
}

function send(response: ServerResponse, status: number, body: unknown): void {
  response.writeHead(status, { 'content-type': 'application/json' });
  response.end(JSON.stringify(body));
}

async function startFakeApi(overrides: Overrides = {}): Promise<FakeApi> {
  const requests: string[] = [];
  const failed = new Set<string>();

  const route = (request: IncomingMessage, response: ServerResponse) => {
    const url = new URL(request.url ?? '/', 'http://fake');
    const target = `${url.pathname}${url.search}`;
    // Only API traffic counts: other processes on a busy machine probe loopback ports at random.
    if (url.pathname.startsWith('/api/')) requests.push(target);

    if (overrides.failOnceFor?.(target) && !failed.has(target)) {
      failed.add(target);
      return send(response, 503, { detail: 'warming up' });
    }

    if (url.pathname === '/api/v1/ranking') {
      const kinds = url.searchParams.getAll('kind') as RankedKind[];
      if (kinds.length === 0 && overrides.defaultRanking !== undefined && url.searchParams.get('limit') === '50') {
        return send(response, 200, overrides.defaultRanking);
      }
      if (kinds.length === 1 && overrides.kindRanking) {
        return send(response, 200, overrides.kindRanking(kinds[0]));
      }
      const file = fixtureFileFor(
        {
          amount: Number(url.searchParams.get('amount') ?? 1_000_000),
          days: Number(url.searchParams.get('days') ?? 30),
          kinds,
          clientOf: url.searchParams.getAll('clientOf'),
        },
        Number(url.searchParams.get('limit') ?? 50),
      );
      return file === null ? send(response, 404, { detail: 'unrecorded' }) : send(response, 200, fixture(file));
    }

    const history = /^\/api\/v1\/instruments\/([^/]+)\/history$/.exec(url.pathname);
    if (history) {
      for (const directory of ['history', 'entities/history']) {
        if (existsSync(join(fixtures, directory, `${history[1]}.json`))) {
          return send(response, 200, fixture(`${directory}/${history[1]}.json`));
        }
      }
      // A live API has a history for every instrument; the recordings cover only some, so the rest are empty.
      return send(response, 200, {
        id: history[1],
        kind: 'PLAZO_FIJO_BANK',
        displayName: `Instrument ${history[1]}`,
        entityName: null,
        segment: null,
        sourceId: 'argentinadatos-plazo-fijo',
        from: '2026-07-17',
        to: '2026-10-14',
        points: [],
        quotes: [],
        monthlyInflation: [],
      });
    }
    if (url.pathname === '/api/v1/entities/banks') return send(response, 200, fixture('entities/banks.json'));
    const bank = /^\/api\/v1\/entities\/banks\/([^/]+)$/.exec(url.pathname);
    if (bank) return send(response, 200, fixture(`entities/banks/${bank[1]}.json`));
    if (url.pathname === '/api/v1/reports/monthly') return send(response, 200, fixture('reports/index.json'));
    const report = /^\/api\/v1\/reports\/monthly\/([^/]+)$/.exec(url.pathname);
    if (report) return send(response, 200, fixture(`reports/${report[1]}.json`));
    return send(response, 404, { detail: 'unknown path' });
  };

  const server: Server = createServer(route);
  await new Promise<void>((done) => server.listen(0, '127.0.0.1', done));
  const { port } = server.address() as AddressInfo;
  return {
    url: `http://127.0.0.1:${port}`,
    requests,
    close: () => new Promise<void>((done) => server.close(() => done())),
  };
}

interface Build {
  code: number;
  output: string;
  outDir: string;
}

/** `astro build` as the deploy runs it: production mode, its own output directory, exit code judged. */
async function build(name: string, env: Record<string, string | undefined>): Promise<Build> {
  const outDir = join(scratch, name);
  const childEnv: Record<string, string | undefined> = {
    ...process.env,
    NODE_ENV: 'production',
    SITE_URL: 'https://gate.rindetuplata.test',
    ...env,
  };
  delete childEnv.VITEST;
  for (const [key, value] of Object.entries(env)) if (value === undefined) delete childEnv[key];
  try {
    const { stdout, stderr } = await run(astroBin, ['build', '--outDir', outDir], {
      cwd: webRoot,
      env: childEnv as NodeJS.ProcessEnv,
      maxBuffer: 32 * 1024 * 1024,
    });
    return { code: 0, output: `${stdout}\n${stderr}`, outDir };
  } catch (error) {
    const failure = error as { code?: number; stdout?: string; stderr?: string };
    return { code: failure.code ?? 1, output: `${failure.stdout ?? ''}\n${failure.stderr ?? ''}`, outDir };
  }
}

function freshDefaultRanking(count?: number): RankingBody {
  const ranking = structuredClone(fixture('ranking-default.json')) as RankingBody;
  const today = argentineToday();
  for (const entry of ranking.ranked) entry.captureDate = today;
  if (count !== undefined) ranking.ranked = ranking.ranked.slice(0, count);
  return ranking;
}

function noPageWasRequested(api: FakeApi): void {
  // The gate reads the default ranking and the four category rankings, nothing a page needs.
  expect(api.requests.filter((target) => !target.startsWith('/api/v1/ranking'))).toEqual([]);
  expect(api.requests).toHaveLength(5);
}

describe('the gate stops a build before it renders', () => {
  it('refuses a stale default ranking, naming data-age', async () => {
    const api = await startFakeApi(); // the recordings are dated 2026-10-14
    try {
      const result = await build('stale', { API_URL: api.url, WEB_FIXTURES: undefined });
      expect(result.code).not.toBe(0);
      expect(result.output).toContain('Build gate failed');
      expect(result.output).toContain('data-age: the newest capture date in the default ranking is 2026-10-14');
      expect(result.output).toContain('BUILD_MAX_DATA_AGE_DAYS=3');
      expect(result.output).not.toContain('min-ranked');
      noPageWasRequested(api);
      expect(existsSync(join(result.outDir, 'index.html'))).toBe(false);
    } finally {
      await api.close();
    }
  }, 180_000);

  it('refuses a fresh but thin default ranking, naming min-ranked', async () => {
    const api = await startFakeApi({ defaultRanking: freshDefaultRanking(5) });
    try {
      const result = await build('thin', { API_URL: api.url, WEB_FIXTURES: undefined });
      expect(result.code).not.toBe(0);
      expect(result.output).toContain('min-ranked: the default ranking has 5 ranked entries, fewer than BUILD_MIN_RANKED=20');
      expect(result.output).not.toContain('data-age');
      noPageWasRequested(api);
      expect(existsSync(join(result.outDir, 'index.html'))).toBe(false);
    } finally {
      await api.close();
    }
  }, 180_000);

  it('refuses an empty category ranking, naming category-rankings', async () => {
    const api = await startFakeApi({
      defaultRanking: freshDefaultRanking(),
      kindRanking: (kind) => {
        const ranking = structuredClone(fixture(`ranking-kind-${kind}.json`)) as RankingBody;
        if (kind === 'CRYPTO') ranking.ranked = [];
        return ranking;
      },
    });
    try {
      const result = await build('empty-category', { API_URL: api.url, WEB_FIXTURES: undefined });
      expect(result.code).not.toBe(0);
      expect(result.output).toContain('category-rankings: no ranked entries for cripto (CRYPTO)');
      expect(result.output).not.toContain('data-age');
      noPageWasRequested(api);
      expect(existsSync(join(result.outDir, 'index.html'))).toBe(false);
    } finally {
      await api.close();
    }
  }, 180_000);
});

describe('fixture mode', () => {
  it('skips only the age check: the minimum of ranked entries still stops the build', async () => {
    const result = await build('fixtures-min', {
      WEB_FIXTURES: '1',
      API_URL: undefined,
      BUILD_MIN_RANKED: '100000',
      BUILD_MAX_DATA_AGE_DAYS: '0',
    });
    expect(result.code).not.toBe(0);
    expect(result.output).toContain('min-ranked');
    expect(result.output).toContain('fewer than BUILD_MIN_RANKED=100000');
    expect(result.output).not.toContain('data-age');
    expect(existsSync(join(result.outDir, 'index.html'))).toBe(false);
  }, 180_000);
});

describe('a complete build against the API', () => {
  it('sends every identical request once and rides out a 503 on the default ranking', async () => {
    const api = await startFakeApi({
      failOnceFor: (target) => target === '/api/v1/ranking?amount=1000000&days=30&limit=50',
    });
    try {
      const result = await build('complete', {
        API_URL: api.url,
        WEB_FIXTURES: undefined,
        // The recordings are old by nature: this test is about requests, not freshness.
        BUILD_MAX_DATA_AGE_DAYS: '100000',
      });
      expect(result.output).not.toContain('Build gate failed');
      expect(result.code).toBe(0);
      expect(existsSync(join(result.outDir, 'index.html'))).toBe(true);

      const counts = new Map<string, number>();
      for (const target of api.requests) counts.set(target, (counts.get(target) ?? 0) + 1);
      const rankingRequests = [...counts].filter(([target]) => target.startsWith('/api/v1/ranking'));
      const history = [...counts].filter(([target]) => target.includes('/history'));
      expect(rankingRequests.length).toBeGreaterThan(0);
      expect(history.length).toBeGreaterThan(0);

      // The 503 costs exactly one extra request, for the default ranking it hit; every other ranking, history,
      // bank and report request went out once however many pages needed it (the bank and report loaders go
      // through the same memoized client, F6).
      const memoized = (target: string) =>
        target.startsWith('/api/v1/ranking') ||
        target.includes('/history') ||
        target.startsWith('/api/v1/entities/') ||
        target.startsWith('/api/v1/reports/');
      const repeated = [...counts].filter(([target, count]) => count > 1 && memoized(target));
      expect(repeated).toEqual([['/api/v1/ranking?amount=1000000&days=30&limit=50', 2]]);
      expect(counts.get('/api/v1/ranking?amount=1000000&days=30&limit=200')).toBe(1);
      // The bank list and a bank page, the report list: asked for by several pages, fetched once each.
      expect(counts.get('/api/v1/entities/banks')).toBe(1);
      expect([...counts.keys()].filter((target) => /^\/api\/v1\/entities\/banks\/[^/]+$/.test(target)).length).toBeGreaterThan(0);
      expect(counts.get('/api/v1/reports/monthly')).toBe(1);
    } finally {
      await api.close();
    }
  }, 600_000);
});

describe('the end-to-end smoke build (deploy/e2e.sh, F5)', () => {
  const e2eScript = readFileSync(join(webRoot, '..', 'deploy', 'e2e.sh'), 'utf8');
  const buildLine = e2eScript.split('\n').find((line) => /\bnpm run build\b/.test(line) && !line.trim().startsWith('#'));

  it('would fail a production-mode build: a fresh stack has no ingested data and the gate says so', async () => {
    const empty = { ...freshDefaultRanking(), ranked: [] };
    const api = await startFakeApi({ defaultRanking: empty, kindRanking: () => ({ ...empty }) });
    try {
      const result = await build('e2e-empty-api', { API_URL: api.url, WEB_FIXTURES: undefined });
      expect(result.code).not.toBe(0);
      expect(result.output).toContain('min-ranked');
      expect(result.output).toContain('category-rankings');
    } finally {
      await api.close();
    }
  }, 180_000);

  it('builds the static pages in fixture mode and leaves the runtime API base alone', async () => {
    expect(buildLine, 'deploy/e2e.sh has no site build step').toBeDefined();
    // Never build:api (it strips WEB_FIXTURES and demands data) and never a gate off-switch.
    expect(buildLine).not.toContain('build:api');
    expect(buildLine).toMatch(/\bWEB_FIXTURES=1\b/);
    expect(buildLine).not.toMatch(/\bAPI_URL=/);
    expect(buildLine).not.toMatch(/BUILD_(MIN_RANKED|MAX_DATA_AGE_DAYS)/);
    // The account pages talk to PUBLIC_API_URL (default: the page's own origin, i.e. the e2e stack behind Caddy),
    // which this build leaves unset, so fixture mode only changes where the static pages get their data.
    const result = await build('e2e-fixture-mode', {
      WEB_FIXTURES: '1',
      API_URL: undefined,
      PUBLIC_API_URL: undefined,
    });
    expect(result.output).not.toContain('Build gate failed');
    expect(result.code).toBe(0);
    for (const page of ['index.html', 'ingresar/index.html', 'alertas/index.html']) {
      expect(existsSync(join(result.outDir, page)), page).toBe(true);
    }
  }, 600_000);
});
