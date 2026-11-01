/**
 * The monthly "who beat inflation" report (T6a, S6, L29 a): types, build-time loaders and Spanish copy.
 *
 * Like `data.ts`, pages read the Java API while building (`API_URL`) and, with `WEB_FIXTURES=1`, the files in
 * `web/fixtures/reports/` (synthetic, see their README: the recorded fixtures cannot cover a whole month, L16).
 * `WEB_REPORTS_FIXTURES_DIR` points a build at another directory with the same layout, which is how the empty state is
 * tested. Build-time only: it reads the file system in fixture mode and is never imported by a client component.
 *
 * The request helpers repeat the few lines `data.ts` keeps private, so this feature adds files instead of editing
 * the shared loader.
 */
import { readFile } from 'node:fs/promises';
import { join, resolve } from 'node:path';
import { fixturesEnabled, type BuildDataOptions, type Env } from './data';

export const REPORTS_FIXTURES_DIR_ENV = 'WEB_REPORTS_FIXTURES_DIR';

/** The first day of the stored history: the site started recording on this capture date. */
export const HISTORY_START = '2026-10-14';

/**
 * The first month the history covers from its first day, which is what a report needs: a month that starts before the
 * history cannot be measured over its whole length. It appears once INDEC publishes its inflation.
 */
export const FIRST_FULL_MONTH = '2026-11';

export interface ReportSource {
  id: string;
  displayName: string;
  official: boolean;
}

/** One month of the list: its INDEC inflation and how many options were measured and beat it. */
export interface ReportMonthSummary {
  month: string;
  monthlyInflation: number;
  ranked: number;
  beatInflation: number;
}

export interface ReportInflation {
  index: string;
  value: number;
  observationDate: string;
  confidence: string;
  source: ReportSource;
}

export interface ReportRow {
  id: string;
  kind: string;
  name: string;
  entity: string | null;
  source: ReportSource;
  nominal: number;
  real: number;
  beatInflation: boolean;
  foldedClasses: number;
  coverage: { days: number; total: number };
}

export interface ReportLeftOut {
  id: string;
  kind: string;
  name: string;
  entity: string | null;
  source: ReportSource;
  reason: string;
}

export interface MonthlyReport {
  month: string;
  from: string;
  to: string;
  inflation: ReportInflation;
  rows: ReportRow[];
  leftOut: ReportLeftOut[];
  totals: { ranked: number; beatInflation: number; leftOut: number };
}

const MONTH_PATTERN = /^\d{4}-(0[1-9]|1[0-2])$/;

const monthName = new Intl.DateTimeFormat('es-AR', { month: 'long', year: 'numeric', timeZone: 'UTC' });

/** "2026-10" to "octubre de 2026". */
export function monthLabel(month: string): string {
  return monthName.format(new Date(`${month}-01T00:00:00Z`));
}

/** The URL of a month's page; every internal link ends in a slash like the canonical. */
export function reportPath(month: string): string {
  return `/informes/${month}/`;
}

export const REPORTS_PATH = '/informes/';

const LEFT_OUT_REASON_LABELS: Record<string, string> = {
  NOT_CAPTURED_AT_START: 'No teníamos su tasa al empezar el mes',
  GAP_IN_CAPTURES: 'Faltaron datos durante más de 3 días seguidos',
  MISSING_BASE_QUOTE: 'Sin cotización cerca del cierre del mes anterior',
  MISSING_END_QUOTE: 'Sin cotización cerca del cierre del mes',
  NOT_OFFERED_TO_NON_CLIENTS: 'No publicó tasa para quienes no son clientes',
  INVALID_DATA: 'Datos inválidos durante el mes',
  FOREIGN_CURRENCY: 'Fondo en dólares: no se compara contra la inflación en pesos',
  RESTRICTED_CLASS: 'Clase exclusiva del régimen de regularización (Ley 27.743)',
  FUND_CLASS_NOT_FOUND: 'La clase de fondo de la billetera no estaba en la fuente',
};

/** Why an option is not in the table, in Spanish; an unknown code shows as itself, never as "undefined". */
export function leftOutReasonLabel(reason: string): string {
  return LEFT_OUT_REASON_LABELS[reason] ?? reason;
}

function requireMonth(month: string): void {
  if (!MONTH_PATTERN.test(month)) {
    throw new Error(`Invalid month "${month}": expected yyyy-MM`);
  }
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

/** `API_BUILD_TOKEN`, when set, skips the API's per-address limit for this build; it never reaches an error. */
function apiHeaders(env: Env): Record<string, string> {
  const headers: Record<string, string> = { accept: 'application/json' };
  const token = env.API_BUILD_TOKEN?.trim();
  if (token) headers['x-build-token'] = token;
  return headers;
}

async function apiGet(url: string, options: BuildDataOptions, env: Env): Promise<unknown> {
  const doFetch = options.fetch ?? globalThis.fetch;
  const response = await doFetch(url, { headers: apiHeaders(env) });
  if (!response.ok) {
    const body = await response.text();
    throw new Error(`GET ${url} failed with ${response.status}: ${body.slice(0, 300)}`);
  }
  return response.json();
}

function reportsDirectory(options: BuildDataOptions, env: Env): string {
  const override = env[REPORTS_FIXTURES_DIR_ENV];
  if (override !== undefined && override !== '') return resolve(override);
  return join(options.fixturesDir ?? resolve(process.cwd(), 'fixtures'), 'reports');
}

function isObject(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === 'object';
}

function assertList(payload: unknown, source: string): ReportMonthSummary[] {
  if (!isObject(payload) || !Array.isArray(payload.months)) {
    throw new Error(`Invalid report list from ${source}`);
  }
  return payload.months as ReportMonthSummary[];
}

function assertReport(payload: unknown, source: string): MonthlyReport {
  if (
    !isObject(payload) ||
    typeof payload.month !== 'string' ||
    !isObject(payload.inflation) ||
    !Array.isArray(payload.rows) ||
    !Array.isArray(payload.leftOut) ||
    !isObject(payload.totals)
  ) {
    throw new Error(`Invalid report from ${source}`);
  }
  return payload as unknown as MonthlyReport;
}

/** The months that have a report, newest first; empty while no closed month is covered. */
export async function loadReportMonths(options: BuildDataOptions = {}): Promise<ReportMonthSummary[]> {
  const env = options.env ?? process.env;
  if (fixturesEnabled(env)) {
    const file = join(reportsDirectory(options, env), 'index.json');
    return assertList(JSON.parse(await readFile(file, 'utf8')), file);
  }
  const url = `${requireApiUrl(env)}/api/v1/reports/monthly`;
  return assertList(await apiGet(url, options, env), url);
}

/** The report of one month. In fixture mode a month that is not recorded names the ones that are. */
export async function loadMonthlyReport(
  month: string,
  options: BuildDataOptions = {},
): Promise<MonthlyReport> {
  requireMonth(month);
  const env = options.env ?? process.env;
  if (fixturesEnabled(env)) {
    const directory = reportsDirectory(options, env);
    const file = join(directory, `${month}.json`);
    let raw: string;
    try {
      raw = await readFile(file, 'utf8');
    } catch (error) {
      if ((error as NodeJS.ErrnoException | null)?.code !== 'ENOENT') throw error;
      const recorded = (await loadReportMonths(options)).map((entry) => entry.month);
      throw new Error(
        `No report fixture for ${month} in ${directory}. Months listed: [${recorded.join(', ')}] (see web/fixtures/reports/README.md).`,
      );
    }
    return assertReport(JSON.parse(raw), file);
  }
  const url = `${requireApiUrl(env)}/api/v1/reports/monthly/${month}`;
  return assertReport(await apiGet(url, options, env), url);
}

/** Every available month with its report, newest first: one static page each. */
export async function loadMonthlyReports(options: BuildDataOptions = {}): Promise<MonthlyReport[]> {
  const months = await loadReportMonths(options);
  return Promise.all(months.map((entry) => loadMonthlyReport(entry.month, options)));
}
