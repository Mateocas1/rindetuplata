/**
 * Build-time data access (T7b, T7c).
 *
 * Pages read the Java API while building (`API_URL`); with `WEB_FIXTURES=1`
 * they read the recorded JSON in `web/fixtures/` instead, so tests and CI never
 * need a running stack. This module is build-time only — it reads the file
 * system in fixture mode — and is never imported by a client component.
 *
 * Fixture mode serves only requests that were actually recorded, matched exactly
 * on amount, days, kinds, clientOf and limit: an unrecorded request fails the
 * build with the list of recordings instead of quietly shipping numbers that do
 * not match the request (S11).
 */
import { readFile, readdir } from 'node:fs/promises';
import { join, resolve } from 'node:path';
import { DEFAULT_AMOUNT, DEFAULT_DAYS, toApiParams, type RankingQueryState } from './rankingQuery';
import type { BankOption, InstrumentHistory, Ranking } from './types';

export type Env = Record<string, string | undefined>;

export interface BuildDataOptions {
  env?: Env;
  fetch?: typeof fetch;
  /** Rows to request from the API; fixture mode matches this exactly. */
  limit?: number;
  /** Fixtures directory override, so a test can exercise a missing directory. */
  fixturesDir?: string;
}

export const DEFAULT_RANKING_LIMIT = 50;
export const BANK_LIST_LIMIT = 200;
/** Wide limit used by the instrument pages so every ranked option has a page. */
export const CATALOG_LIMIT = 200;

const DEFAULT_FIXTURES_DIR = resolve(process.cwd(), 'fixtures');

export interface RecordedRanking {
  file: string;
  amount: number;
  days: number;
  kinds: readonly string[];
  clientOf: readonly string[];
  limit: number;
}

/**
 * Exactly the ranking requests recorded under `web/fixtures/` (see its README),
 * limits included: the table is cross-checked against the files by a test, so it
 * cannot drift into answering a request nobody made.
 */
export const RECORDED_RANKINGS: readonly RecordedRanking[] = [
  { file: 'ranking-default.json', amount: 1_000_000, days: 30, kinds: [], clientOf: [], limit: 50 },
  { file: 'ranking-500k-60d.json', amount: 500_000, days: 60, kinds: [], clientOf: [], limit: 50 },
  { file: 'ranking-all.json', amount: 1_000_000, days: 30, kinds: [], clientOf: [], limit: 200 },
  {
    file: 'ranking-kind-PLAZO_FIJO.json',
    amount: 1_000_000,
    days: 30,
    kinds: ['PLAZO_FIJO'],
    clientOf: [],
    limit: 200,
  },
  {
    file: 'ranking-kind-WALLET.json',
    amount: 1_000_000,
    days: 30,
    kinds: ['WALLET'],
    clientOf: [],
    limit: 200,
  },
  {
    file: 'ranking-kind-FUND.json',
    amount: 1_000_000,
    days: 30,
    kinds: ['FUND'],
    clientOf: [],
    limit: 200,
  },
  {
    file: 'ranking-kind-CRYPTO.json',
    amount: 1_000_000,
    days: 30,
    kinds: ['CRYPTO'],
    clientOf: [],
    limit: 200,
  },
];

const RECORDED_FIXTURE_FILES = RECORDED_RANKINGS.map((entry) => entry.file);

function fixturesDirFor(options: BuildDataOptions): string {
  return options.fixturesDir ?? DEFAULT_FIXTURES_DIR;
}

export function fixturesEnabled(env: Env = process.env): boolean {
  return env.WEB_FIXTURES === '1';
}

/** Order-insensitive, duplicate-free form of a list of filters. */
function normalized(value: readonly string[]): string {
  return [...new Set(value)].sort().join(',');
}

/** The recording that answers this exact request, or null when there is none. */
export function fixtureFileFor(
  state: RankingQueryState,
  limit: number = DEFAULT_RANKING_LIMIT,
): string | null {
  const kinds = normalized(state.kinds);
  const clientOf = normalized(state.clientOf);
  const match = RECORDED_RANKINGS.find(
    (recorded) =>
      recorded.amount === state.amount &&
      recorded.days === state.days &&
      recorded.limit === limit &&
      normalized(recorded.kinds) === kinds &&
      normalized(recorded.clientOf) === clientOf,
  );
  return match?.file ?? null;
}

function requireApiUrl(env: Env): string {
  const baseUrl = env.API_URL;
  if (baseUrl === undefined || baseUrl === '') {
    throw new Error(
      'API_URL is required to build the site outside fixture mode; set WEB_FIXTURES=1 to build from web/fixtures.',
    );
  }
  return baseUrl.replace(/\/+$/, '');
}

async function apiGet(url: string, doFetch: typeof fetch): Promise<unknown> {
  const response = await doFetch(url, { headers: { accept: 'application/json' } });
  if (!response.ok) {
    const body = await response.text();
    throw new Error(`GET ${url} failed with ${response.status}: ${body.slice(0, 300)}`);
  }
  return response.json();
}

function assertRanking(payload: unknown, source: string): Ranking {
  const candidate = payload as Partial<Ranking> | null;
  if (
    candidate === null ||
    typeof candidate !== 'object' ||
    !Array.isArray(candidate.ranked) ||
    !Array.isArray(candidate.ineligible) ||
    !Array.isArray(candidate.excluded)
  ) {
    throw new Error(`Invalid ranking payload from ${source}`);
  }
  return candidate as Ranking;
}

function assertHistory(payload: unknown, source: string): InstrumentHistory {
  const candidate = payload as Partial<InstrumentHistory> | null;
  if (
    candidate === null ||
    typeof candidate !== 'object' ||
    typeof candidate.id !== 'string' ||
    !Array.isArray(candidate.points) ||
    !Array.isArray(candidate.quotes) ||
    !Array.isArray(candidate.monthlyInflation)
  ) {
    throw new Error(`Invalid history payload from ${source}`);
  }
  return candidate as InstrumentHistory;
}

export async function loadRanking(
  state: RankingQueryState,
  options: BuildDataOptions = {},
): Promise<Ranking> {
  const env = options.env ?? process.env;
  const limit = options.limit ?? DEFAULT_RANKING_LIMIT;

  if (fixturesEnabled(env)) {
    const file = fixtureFileFor(state, limit);
    if (file === null) {
      throw new Error(
        `No recorded fixture for amount=${state.amount} days=${state.days} ` +
          `kinds=[${state.kinds.join(',')}] clientOf=[${state.clientOf.join(',')}] limit=${limit}. ` +
          `Recorded requests: ${RECORDED_FIXTURE_FILES.join(', ')} (see web/fixtures/README.md).`,
      );
    }
    const raw = await readFile(join(fixturesDirFor(options), file), 'utf8');
    return assertRanking(JSON.parse(raw), file);
  }

  const params = toApiParams(state);
  params.set('limit', String(limit));
  const url = `${requireApiUrl(env)}/api/v1/ranking?${params.toString()}`;
  const doFetch = options.fetch ?? globalThis.fetch;
  return assertRanking(await apiGet(url, doFetch), url);
}

/**
 * The instrument catalogue: the same default ranking, with the wide limit, so a
 * page exists for every ranked or ineligible instrument of the day and never for
 * the unranked rows of `instruments.json` (T7c).
 */
export async function loadRankingCatalog(options: BuildDataOptions = {}): Promise<Ranking> {
  return loadRanking(
    { amount: DEFAULT_AMOUNT, days: DEFAULT_DAYS, kinds: [], clientOf: [] },
    { ...options, limit: options.limit ?? CATALOG_LIMIT },
  );
}

function isNotFound(error: unknown): boolean {
  return (error as NodeJS.ErrnoException | null)?.code === 'ENOENT';
}

/**
 * Thrown when fixture mode has no recording for an instrument's history. The
 * instrument page catches it and renders its empty-history state: a page exists
 * for every instrument a table can link to, and not every one of those was
 * recorded yet (D1).
 */
export class MissingHistoryRecording extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'MissingHistoryRecording';
  }
}

/** What recordings exist, or a note that the directory itself is missing. */
async function missingHistoryMessage(
  instrumentId: string,
  historyDirectory: string,
): Promise<string> {
  const head =
    `No recorded history for instrument ${instrumentId}. ` +
    `The recordings live in fixtures/history/<id>.json`;
  let recorded: string[];
  try {
    recorded = (await readdir(historyDirectory))
      .filter((entry) => entry.endsWith('.json'))
      .map((entry) => entry.replace('.json', ''))
      .sort((a, b) => Number(a) - Number(b));
  } catch {
    return `${head}, and there is no history directory to read here: ${historyDirectory} (see web/fixtures/README.md).`;
  }
  return `${head} for [${recorded.join(', ')}] (see web/fixtures/README.md).`;
}

/** One instrument's history: rate snapshots or fund quotes plus INDEC monthly inflation. */
export async function loadHistory(
  instrumentId: string,
  options: BuildDataOptions = {},
): Promise<InstrumentHistory> {
  const env = options.env ?? process.env;

  if (fixturesEnabled(env)) {
    const directory = join(fixturesDirFor(options), 'history');
    const file = join(directory, `${instrumentId}.json`);
    let raw: string;
    try {
      raw = await readFile(file, 'utf8');
    } catch (error) {
      // Only a missing recording is a "not recorded" case; anything else is a
      // real read error and must not be dressed up as one.
      if (!isNotFound(error)) throw error;
      throw new MissingHistoryRecording(await missingHistoryMessage(instrumentId, directory));
    }
    return assertHistory(JSON.parse(raw), file);
  }

  const url = `${requireApiUrl(env)}/api/v1/instruments/${encodeURIComponent(instrumentId)}/history`;
  return assertHistory(await apiGet(url, options.fetch ?? globalThis.fetch), url);
}

/**
 * Every bank that reports a plazo fijo rate, ranked or not: the ones that only
 * offer a client rate are exactly the ones a reader needs for "soy cliente de…"
 * (S19). Alphabetical and deduplicated.
 */
export async function loadBanks(options: BuildDataOptions = {}): Promise<BankOption[]> {
  const ranking = await loadRanking(
    { amount: DEFAULT_AMOUNT, days: DEFAULT_DAYS, kinds: ['PLAZO_FIJO'], clientOf: [] },
    { ...options, limit: options.limit ?? BANK_LIST_LIMIT },
  );

  const byId = new Map<string, BankOption>();
  for (const entry of [...ranking.ranked, ...ranking.excluded]) {
    if (entry.kind !== 'PLAZO_FIJO' || byId.has(entry.id)) continue;
    byId.set(entry.id, { id: entry.id, name: entry.name });
  }
  return [...byId.values()].sort((a, b) => a.name.localeCompare(b.name, 'es'));
}
