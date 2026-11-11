/**
 * Bank entity pages data.
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
import { buildApiGet } from './fetchRetry';
import { formatDate, formatInteger, formatPercent } from './format';
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
  /**
   * Whether these rates are from the newest capture day of the bank's source. False means the source reported a
   * later day without this bank: the rates are its last published ones and the pages must not call them "hoy".
   */
  current: boolean;
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

function assertBank(payload: unknown, source: string): BankRates {
  const candidate = payload as Partial<BankRates> | null;
  if (
    candidate === null ||
    typeof candidate !== 'object' ||
    typeof candidate.slug !== 'string' ||
    typeof candidate.name !== 'string' ||
    typeof candidate.instrumentId !== 'string' ||
    typeof candidate.current !== 'boolean'
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
    payload = await buildApiGet(source, options, env);
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
  return assertDetail(await buildApiGet(url, options, env), url);
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

/**
 * The list split: the banks with the source's newest rates first, sorted as the listing sorts them, and the banks
 * whose last rate is older apart (same order), so "hoy" is only ever said of the first group.
 */
export function splitBanksByFreshness<T extends Pick<BankRates, 'name' | 'nonClientTna' | 'current'>>(
  banks: readonly T[],
): { current: T[]; previous: T[] } {
  return {
    current: sortBanksForListing(banks.filter((bank) => bank.current)),
    previous: sortBanksForListing(banks.filter((bank) => !bank.current)),
  };
}

export interface BankPageCopy {
  title: string;
  description: string;
  /** The rate line of the page's card. */
  headline: string;
}

/**
 * The copy of a bank page that depends on freshness. A current bank is "hoy"; a bank whose last rate is older says
 * it is the last published rate and its date instead, in the title, the description and the headline.
 */
export function bankPageCopy(
  bank: Pick<BankRates, 'name' | 'sourceId' | 'nonClientTna' | 'clientTna' | 'captureDate' | 'dataDate' | 'current'>,
): BankPageCopy {
  const source = sourceName(bank.sourceId);
  const nonClient = bank.nonClientTna === null ? null : formatPercent(bank.nonClientTna);
  const client = bank.clientTna === null ? null : formatPercent(bank.clientTna);
  const tail = ` Mirá su tasa de los últimos 90 días y cuántas veces fue la mejor, según ${source}, dato del ${formatDate(bank.dataDate)}.`;
  const clientPart = client === null ? '' : ` y paga ${client} TNA a sus clientes`;
  if (bank.current) {
    return {
      title:
        nonClient === null
          ? `${bank.name}: plazo fijo, tasa para clientes | rindetuplata`
          : `${bank.name}: plazo fijo ${nonClient} TNA hoy | rindetuplata`,
      description:
        (nonClient === null
          ? `${bank.name} no ofrece plazo fijo a no clientes hoy${clientPart}.`
          : `Cuánto paga hoy ${bank.name} por un plazo fijo: ${nonClient} TNA para no clientes${client === null ? '' : ` y ${client} TNA para clientes`}.`) +
        tail,
      headline: nonClient === null ? 'Hoy no ofrece plazo fijo a no clientes' : `Hoy: ${nonClient} TNA para no clientes`,
    };
  }
  const date = formatDate(bank.captureDate);
  return {
    title:
      nonClient === null
        ? `${bank.name}: plazo fijo, tasa para clientes | rindetuplata`
        : `${bank.name}: plazo fijo ${nonClient} TNA, última tasa publicada | rindetuplata`,
    description:
      (nonClient === null
        ? `La última tasa publicada de ${bank.name}, del ${date}, no ofrecía plazo fijo a no clientes${clientPart}.`
        : `La última tasa de plazo fijo publicada de ${bank.name}, del ${date}: ${nonClient} TNA para no clientes${client === null ? '' : ` y ${client} TNA para clientes`}.`) +
      tail,
    headline:
      nonClient === null
        ? `Última tasa publicada (${date}): no ofrecía plazo fijo a no clientes`
        : `Última tasa publicada (${date}): ${nonClient} TNA para no clientes`,
  };
}

const SOURCE_NAMES: Record<string, string> = {
  'argentinadatos-plazo-fijo': 'ArgentinaDatos',
};

export function sourceName(sourceId: string): string {
  return SOURCE_NAMES[sourceId] ?? sourceId;
}
