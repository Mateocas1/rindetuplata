/**
 * Build-time data access (T7b).
 *
 * Pages read the Java API while building (`API_URL`); with `WEB_FIXTURES=1`
 * they read the recorded JSON in `web/fixtures/` instead, so tests and CI never
 * need a running stack. This module is build-time only — it reads the file
 * system in fixture mode — and is never imported by a client component.
 *
 * Fixture mode serves only requests that were actually recorded: an unrecorded
 * request fails the build with the list of recordings instead of quietly
 * shipping numbers that do not match the request (S11).
 */
import { readFile } from 'node:fs/promises';
import { join, resolve } from 'node:path';
import { DEFAULT_AMOUNT, DEFAULT_DAYS, toApiParams, type RankingQueryState } from './rankingQuery';
import type { BankOption, Ranking } from './types';

export type Env = Record<string, string | undefined>;

export interface BuildDataOptions {
  env?: Env;
  fetch?: typeof fetch;
  /** Rows to request from the API; the recorded fixture decides its own limit. */
  limit?: number;
}

export const DEFAULT_RANKING_LIMIT = 50;
export const BANK_LIST_LIMIT = 200;

const FIXTURES_DIR = resolve(process.cwd(), 'fixtures');

/** Requests recorded under `web/fixtures/` (see its README). */
const RECORDED_FIXTURES = [
  'ranking-default.json',
  'ranking-500k-60d.json',
  'ranking-kind-PLAZO_FIJO.json',
  'ranking-kind-WALLET.json',
  'ranking-kind-FUND.json',
  'ranking-kind-CRYPTO.json',
];

export function fixturesEnabled(env: Env = process.env): boolean {
  return env.WEB_FIXTURES === '1';
}

/** The recording that answers this request, or null when there is none. */
export function fixtureFileFor(state: RankingQueryState): string | null {
  if (state.clientOf.length > 0) return null;
  const isDefaultAmountAndTerm = state.amount === DEFAULT_AMOUNT && state.days === DEFAULT_DAYS;
  if (state.kinds.length === 1 && isDefaultAmountAndTerm) {
    return `ranking-kind-${state.kinds[0]}.json`;
  }
  if (state.kinds.length > 0) return null;
  if (isDefaultAmountAndTerm) return 'ranking-default.json';
  if (state.amount === 500_000 && state.days === 60) return 'ranking-500k-60d.json';
  return null;
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

export async function loadRanking(
  state: RankingQueryState,
  options: BuildDataOptions = {},
): Promise<Ranking> {
  const env = options.env ?? process.env;
  const limit = options.limit ?? DEFAULT_RANKING_LIMIT;

  if (fixturesEnabled(env)) {
    const file = fixtureFileFor(state);
    if (file === null) {
      throw new Error(
        `No recorded fixture for amount=${state.amount} days=${state.days} ` +
          `kinds=[${state.kinds.join(',')}] clientOf=[${state.clientOf.join(',')}]. ` +
          `Recorded requests: ${RECORDED_FIXTURES.join(', ')} (see web/fixtures/README.md).`,
      );
    }
    const raw = await readFile(join(FIXTURES_DIR, file), 'utf8');
    return assertRanking(JSON.parse(raw), file);
  }

  const baseUrl = env.API_URL;
  if (baseUrl === undefined || baseUrl === '') {
    throw new Error(
      'API_URL is required to build the site outside fixture mode; set WEB_FIXTURES=1 to build from web/fixtures.',
    );
  }

  const params = toApiParams(state);
  params.set('limit', String(limit));
  const url = `${baseUrl.replace(/\/+$/, '')}/api/v1/ranking?${params.toString()}`;
  const doFetch = options.fetch ?? globalThis.fetch;
  const response = await doFetch(url, { headers: { accept: 'application/json' } });
  if (!response.ok) {
    const body = await response.text();
    throw new Error(`GET ${url} failed with ${response.status}: ${body.slice(0, 300)}`);
  }
  return assertRanking(await response.json(), url);
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
