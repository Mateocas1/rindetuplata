/**
 * The instrument page set (T7c, D1).
 *
 * A page must exist for every instrument any prerendered table can link to: the
 * union of the global ranking recording and every per-kind recording, because a
 * kind recording keeps rows the global limit cut off. The home island can still
 * ask the API for a combination nobody recorded, so the table gates its links on
 * this set and prints plain text for a row without a page.
 *
 * Pages are keyed by slug, not by id: one instrument id can be both a wallet and
 * its fund class (Mercado Pago / Mercado Fondo), and those two names are two
 * different pages.
 *
 * Build-time only: it reads the Java API, or the recorded fixtures in fixture mode.
 */
import { CATALOG_LIMIT, loadRanking, loadRankingCatalog, type BuildDataOptions } from './data';
import { DEFAULT_AMOUNT, DEFAULT_DAYS, type RankingQueryState } from './rankingQuery';
import { instrumentSlug } from './slug';
import {
  RANKING_KINDS,
  type IneligibleEntry,
  type Inflation,
  type RankedEntry,
} from './types';

export type InstrumentPageEntry = RankedEntry | IneligibleEntry;

export interface InstrumentPage {
  entry: InstrumentPageEntry;
  /** True when the entry came from a ranking the API scored (it carries a rate). */
  isRanked: boolean;
}

export interface InstrumentPages {
  pages: InstrumentPage[];
  /** Slugs in page order; the table gates its links on this set. */
  slugs: string[];
  /** The REM observation the pages label their real return with (S10). */
  inflation: Inflation | null;
}

const DEFAULT_RANKING: RankingQueryState = {
  amount: DEFAULT_AMOUNT,
  days: DEFAULT_DAYS,
  kinds: [],
  clientOf: [],
};

export async function loadInstrumentPages(
  options: BuildDataOptions = {},
): Promise<InstrumentPages> {
  // The global recording first: it is the one the home page renders, so its row
  // wins when an instrument appears in several recordings.
  const globalRanking = await loadRankingCatalog({ ...options, limit: CATALOG_LIMIT });
  const kindRankings = await Promise.all(
    RANKING_KINDS.map((kind) =>
      loadRanking({ ...DEFAULT_RANKING, kinds: [kind] }, { ...options, limit: CATALOG_LIMIT }),
    ),
  );

  const bySlug = new Map<string, InstrumentPage>();
  const add = (entry: InstrumentPageEntry, isRanked: boolean) => {
    const slug = instrumentSlug(entry.name, entry.id);
    if (!bySlug.has(slug)) bySlug.set(slug, { entry, isRanked });
  };

  for (const entry of globalRanking.ranked) add(entry, true);
  for (const entry of globalRanking.ineligible) add(entry, false);
  for (const ranking of kindRankings) {
    for (const entry of ranking.ranked) add(entry, true);
    for (const entry of ranking.ineligible) add(entry, false);
  }

  return {
    pages: [...bySlug.values()],
    slugs: [...bySlug.keys()],
    inflation: globalRanking.inflation,
  };
}
