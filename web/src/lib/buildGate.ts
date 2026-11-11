/**
 * Pre-render gate of the static build.
 *
 * Before Astro renders a single page, the default ranking must be fresh and big enough and every category page
 * must have rows. Otherwise the build fails naming the check, so a deploy keeps serving the previous site
 * instead of publishing an empty or stale one that search engines would index. The three checks:
 *
 * - `data-age`: the newest capture date in the default ranking is at most `BUILD_MAX_DATA_AGE_DAYS` old (3).
 * - `min-ranked`: the default ranking has at least `BUILD_MIN_RANKED` ranked entries (20).
 * - `category-rankings`: no category page's ranking (`categories.ts`) is empty.
 *
 * Fixture mode (`WEB_FIXTURES=1`: CI, tests) skips only `data-age`, since recordings are old by nature; the
 * minimum-data checks always run. The gate reads exactly the requests the pages read, so with the build's
 * memoization (`fetchRetry.ts`) it costs the build no extra fetch. Build-time only.
 */
import { CATEGORIES } from './categories';
import {
  CATALOG_LIMIT,
  DEFAULT_RANKING_LIMIT,
  fixturesEnabled,
  loadRanking,
  type BuildDataOptions,
  type Env,
} from './data';
import { DEFAULT_RANKING_QUERY } from './rankingQuery';
import type { Ranking } from './types';

export const DEFAULT_MAX_DATA_AGE_DAYS = 3;
export const DEFAULT_MIN_RANKED = 20;

/** The Argentine calendar day decides how old a capture is. */
const TIME_ZONE = 'America/Argentina/Buenos_Aires';
const DAY_MS = 86_400_000;

export interface GateSettings {
  maxDataAgeDays: number;
  minRanked: number;
}

export interface BuildGateOptions extends BuildDataOptions {
  /** Replaced by tests so the age does not depend on the day the suite runs. */
  now?: () => Date;
}

export class BuildGateError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'BuildGateError';
  }
}

function wholeNumber(env: Env, name: string, fallback: number): number {
  const raw = env[name]?.trim();
  if (raw === undefined || raw === '') return fallback;
  if (!/^\d+$/.test(raw)) {
    throw new BuildGateError(`${name} must be a non-negative whole number, got "${env[name]}"`);
  }
  return Number(raw);
}

export function gateSettingsFromEnv(env: Env): GateSettings {
  return {
    maxDataAgeDays: wholeNumber(env, 'BUILD_MAX_DATA_AGE_DAYS', DEFAULT_MAX_DATA_AGE_DAYS),
    minRanked: wholeNumber(env, 'BUILD_MIN_RANKED', DEFAULT_MIN_RANKED),
  };
}

function utcDay(isoDate: string): number {
  return Date.parse(`${isoDate}T00:00:00Z`);
}

/** Whole calendar days between an ISO capture date and `now`, both read as Argentine days. */
export function dataAgeDays(captureDate: string, now: Date): number {
  const today = new Intl.DateTimeFormat('en-CA', { timeZone: TIME_ZONE }).format(now);
  return Math.round((utcDay(today) - utcDay(captureDate)) / DAY_MS);
}

function newestCaptureDate(ranking: Ranking): string | null {
  const dates = ranking.ranked.map((entry) => entry.captureDate).filter(Boolean);
  return dates.length === 0 ? null : dates.reduce((a, b) => (a > b ? a : b));
}

function days(count: number): string {
  return `${count} ${count === 1 ? 'day' : 'days'}`;
}

function entries(count: number): string {
  return `${count} ranked ${count === 1 ? 'entry' : 'entries'}`;
}

async function readRankings(options: BuildGateOptions) {
  try {
    const fallback = await loadRanking(DEFAULT_RANKING_QUERY, { ...options, limit: DEFAULT_RANKING_LIMIT });
    const categories = await Promise.all(
      CATEGORIES.map(async (category) => ({
        category,
        ranking: await loadRanking(
          { ...DEFAULT_RANKING_QUERY, kinds: [category.kind] },
          { ...options, limit: CATALOG_LIMIT },
        ),
      })),
    );
    return { fallback, categories };
  } catch (error) {
    const reason = error instanceof Error ? error.message : String(error);
    throw new BuildGateError(
      `Build gate failed (nothing was rendered, the previous site stays published):\n- api-read: ${reason}`,
    );
  }
}

/** Throws a {@link BuildGateError} listing every failed check; resolves when the data may be published. */
export async function runBuildGate(options: BuildGateOptions = {}): Promise<void> {
  const env = options.env ?? process.env;
  const settings = gateSettingsFromEnv(env);
  const now = (options.now ?? (() => new Date()))();
  const { fallback: defaultRanking, categories } = await readRankings({ ...options, env });

  const failures: string[] = [];

  if (!fixturesEnabled(env)) {
    const newest = newestCaptureDate(defaultRanking);
    if (newest === null) {
      failures.push('data-age: the default ranking has no ranked entry, so it has no capture date');
    } else {
      const age = dataAgeDays(newest, now);
      if (age > settings.maxDataAgeDays) {
        failures.push(
          `data-age: the newest capture date in the default ranking is ${newest}, ` +
            `${days(age)} old, more than BUILD_MAX_DATA_AGE_DAYS=${settings.maxDataAgeDays}`,
        );
      }
    }
  }

  const rankedCount = defaultRanking.ranked.length;
  if (rankedCount < settings.minRanked) {
    failures.push(
      `min-ranked: the default ranking has ${entries(rankedCount)}, ` +
        `fewer than BUILD_MIN_RANKED=${settings.minRanked}`,
    );
  }

  const empty = categories
    .filter(({ ranking }) => ranking.ranked.length === 0)
    .map(({ category }) => `${category.slug} (${category.kind})`);
  if (empty.length > 0) {
    failures.push(`category-rankings: no ranked entries for ${empty.join(', ')}`);
  }

  if (failures.length > 0) {
    throw new BuildGateError(
      `Build gate failed (nothing was rendered, the previous site stays published):\n` +
        failures.map((failure) => `- ${failure}`).join('\n'),
    );
  }
}
