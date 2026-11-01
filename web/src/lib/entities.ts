/**
 * Bank entity pages data (T6b, S6).
 *
 * Build-time only, like `data.ts`: pages read `GET /api/v1/entities/banks` while building (`API_URL`); with
 * `WEB_FIXTURES=1` they read the synthetic recordings under `web/fixtures/entities/` instead (see its README), so
 * tests and CI never need a running stack. The chart of a bank page reuses the instrument history: its
 * `GET /api/v1/instruments/{id}/history` (90 days by default, TEA against annualized INDEC inflation), or
 * `fixtures/entities/history/<id>.json` in fixture mode.
 *
 * No financial math happens here: every number comes from the API.
 */
import { readFile, readdir } from 'node:fs/promises';
import { join, resolve } from 'node:path';
import { loadHistory, fixturesEnabled, type BuildDataOptions, type Env } from './data';
import { formatInteger } from './format';
import type { InstrumentHistory } from './types';

/** One bank as `GET /api/v1/entities/banks` lists it: the latest valid plazo fijo rates (fractions). */
export interface BankRates {
  slug: string;
  name: string;
  /** Id of the bank's instrument, the one of `/api/v1/instruments/{id}/history`. */
  instrumentId: string;
  sourceId: string;
  nonClientTna: number | null;
  clientTna: number | null;
  captureDate: string;
  dataDate: string;
}

export interface BankSeriesPoint {
  captureDate: string;
  nonClientTna: number;
}

/** `GET /api/v1/entities/banks/{slug}`: the 90-day window and how often the bank paid the highest rate. */
export interface BankDetail extends BankRates {
  from: string;
  to: string;
  series: BankSeriesPoint[];
  bestDays: number;
  observedDays: number;
}

const DEFAULT_ENTITIES_DIR = resolve(process.cwd(), 'fixtures', 'entities');

/** The entities recordings: `<fixturesDir>/entities`, or `web/fixtures/entities`. */
function entitiesDirFor(options: BuildDataOptions): string {
  return options.fixturesDir === undefined
    ? DEFAULT_ENTITIES_DIR
    : join(options.fixturesDir, 'entities');
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

/** The build token, when set, lets the API skip its per-address limit; it never reaches an error message. */
async function apiGet(url: string, options: BuildDataOptions, env: Env): Promise<unknown> {
  const headers: Record<string, string> = { accept: 'application/json' };
  const token = env.API_BUILD_TOKEN?.trim();
  if (token) headers['x-build-token'] = token;
  const response = await (options.fetch ?? globalThis.fetch)(url, { headers });
  if (!response.ok) {
    const body = await response.text();
    throw new Error(`GET ${url} failed with ${response.status}: ${body.slice(0, 300)}`);
  }
  return response.json();
}

function assertBank(payload: unknown, source: string): BankRates {
  const candidate = payload as Partial<BankRates> | null;
  if (
    candidate === null ||
    typeof candidate !== 'object' ||
    typeof candidate.slug !== 'string' ||
    typeof candidate.name !== 'string' ||
    typeof candidate.instrumentId !== 'string'
  ) {
    throw new Error(`Invalid bank payload from ${source}`);
  }
  return candidate as BankRates;
}

function assertDetail(payload: unknown, source: string): BankDetail {
  const bank = assertBank(payload, source);
  const candidate = bank as Partial<BankDetail>;
  if (
    !Array.isArray(candidate.series) ||
    typeof candidate.bestDays !== 'number' ||
    typeof candidate.observedDays !== 'number'
  ) {
    throw new Error(`Invalid bank payload from ${source}`);
  }
  return candidate as BankDetail;
}

export async function loadBankList(options: BuildDataOptions = {}): Promise<BankRates[]> {
  const env = options.env ?? process.env;
  let payload: unknown;
  let source: string;
  if (fixturesEnabled(env)) {
    source = join(entitiesDirFor(options), 'banks.json');
    payload = JSON.parse(await readFile(source, 'utf8'));
  } else {
    source = `${requireApiUrl(env)}/api/v1/entities/banks`;
    payload = await apiGet(source, options, env);
  }
  if (!Array.isArray(payload)) throw new Error(`Invalid bank list payload from ${source}`);
  return payload.map((bank) => assertBank(bank, source));
}

export async function loadBankDetail(
  slug: string,
  options: BuildDataOptions = {},
): Promise<BankDetail> {
  const env = options.env ?? process.env;
  if (fixturesEnabled(env)) {
    const directory = join(entitiesDirFor(options), 'banks');
    const file = join(directory, `${slug}.json`);
    let raw: string;
    try {
      raw = await readFile(file, 'utf8');
    } catch (error) {
      if ((error as NodeJS.ErrnoException | null)?.code !== 'ENOENT') throw error;
      const recorded = (await readdir(directory))
        .filter((entry) => entry.endsWith('.json'))
        .map((entry) => entry.replace('.json', ''))
        .sort();
      throw new Error(
        `No recorded bank "${slug}". The recordings live in fixtures/entities/banks/<slug>.json ` +
          `for [${recorded.join(', ')}] (see web/fixtures/entities/README.md).`,
      );
    }
    return assertDetail(JSON.parse(raw), file);
  }
  const url = `${requireApiUrl(env)}/api/v1/entities/banks/${encodeURIComponent(slug)}`;
  return assertDetail(await apiGet(url, options, env), url);
}

/** The detail of every bank of the list, in list order: the page set of `/bancos/<slug>/`. */
export async function loadBankPages(options: BuildDataOptions = {}): Promise<BankDetail[]> {
  const banks = await loadBankList(options);
  return Promise.all(banks.map((bank) => loadBankDetail(bank.slug, options)));
}

/**
 * The history the bank chart plots: the instrument history of the bank (the existing chart component draws it). In
 * fixture mode it comes from `fixtures/entities/history/`; a bank without a recording throws
 * `MissingHistoryRecording`, which the page turns into the chart's empty state.
 */
export async function loadBankHistory(
  bank: Pick<BankRates, 'instrumentId'>,
  options: BuildDataOptions = {},
): Promise<InstrumentHistory> {
  return loadHistory(bank.instrumentId, { ...options, fixturesDir: entitiesDirFor(options) });
}

/**
 * "Fue la mejor tasa N de los M días que la vimos en los últimos 90": the days it was observed are the capture days
 * with a valid non-client TNA, so a gap is simply not counted.
 */
export function bestRateSentence(bestDays: number, observedDays: number): string {
  const tail = 'en los últimos 90.';
  if (observedDays === 0) {
    return `Todavía no tenemos ningún día con tasa para no clientes de este banco ${tail}`;
  }
  if (observedDays === 1) {
    return bestDays > 0
      ? `Fue la mejor tasa el único día que la vimos ${tail}`
      : `No fue la mejor tasa el único día que la vimos ${tail}`;
  }
  if (bestDays === 0) {
    return `No fue la mejor tasa ninguno de los ${formatInteger(observedDays)} días que la vimos ${tail}`;
  }
  return `Fue la mejor tasa ${formatInteger(bestDays)} de los ${formatInteger(observedDays)} días que la vimos ${tail}`;
}

/** Highest non-client TNA first, ties by name; a bank with no non-client TNA goes last. */
export function sortBanksForListing<T extends Pick<BankRates, 'name' | 'nonClientTna'>>(
  banks: readonly T[],
): T[] {
  return [...banks].sort((a, b) => {
    if (a.nonClientTna === null && b.nonClientTna === null) {
      return a.name.localeCompare(b.name, 'es');
    }
    if (a.nonClientTna === null) return 1;
    if (b.nonClientTna === null) return -1;
    return b.nonClientTna - a.nonClientTna || a.name.localeCompare(b.name, 'es');
  });
}

const SOURCE_NAMES: Record<string, string> = {
  'argentinadatos-plazo-fijo': 'ArgentinaDatos',
};

export function sourceName(sourceId: string): string {
  return SOURCE_NAMES[sourceId] ?? sourceId;
}
