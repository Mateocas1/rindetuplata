/**
 * Amount and term presets of the ranking explorer (T13b).
 *
 * The Cobalto Cristal prototype proposed three amounts and three terms as a
 * capsule segmented control (L80). They are shortcuts over the reader's own
 * query, not a second source of truth: picking one runs through the same
 * validation the free inputs use (`tryParseAmount` / `tryParseDays`), so a
 * preset can never ask the API for something the input would reject, and it
 * never touches the reader's other filters (kinds, banks).
 */
import { tryParseAmount, tryParseDays, type RankingQueryState } from '../../lib/rankingQuery';

export interface Preset {
  /** The value the control carries; the same string the free input would hold. */
  value: string;
  /** The full value, and the accessible name of the option. */
  label: string;
  /**
   * What the segment shows (T13e): the full value is too wide for a third of the
   * capsule at 320/390, where it touched the thumb's edges, so the visible text
   * is abbreviated and `label` stays the name a screen reader reads.
   */
  shortLabel: string;
}

/** The prototype's proposals (L80): frequent amounts, in pesos. */
export const AMOUNT_PRESETS: Preset[] = [
  { value: '500000', label: '$ 500.000', shortLabel: '$ 500k' },
  { value: '1000000', label: '$ 1.000.000', shortLabel: '$ 1M' },
  { value: '5000000', label: '$ 5.000.000', shortLabel: '$ 5M' },
];

/** The prototype's proposals (L80): the terms a plazo fijo is usually quoted at. */
export const DAYS_PRESETS: Preset[] = [
  { value: '30', label: '30 días', shortLabel: '30 días' },
  { value: '60', label: '60 días', shortLabel: '60 días' },
  { value: '90', label: '90 días', shortLabel: '90 días' },
];

/** The same query with the preset's amount, or the same query when it is unusable. */
export function applyAmountPreset(state: RankingQueryState, value: string): RankingQueryState {
  const amount = tryParseAmount(value);
  return amount === null ? state : { ...state, amount };
}

/** The same query with the preset's term, or the same query when it is unusable. */
export function applyDaysPreset(state: RankingQueryState, value: string): RankingQueryState {
  const days = tryParseDays(value);
  return days === null ? state : { ...state, days };
}

/**
 * The preset a value sits on, or null when the reader typed one of their own
 * (the segmented control then shows no thumb, and the query is still theirs).
 */
export function matchPreset(presets: readonly Preset[], value: number): string | null {
  const wanted = String(value);
  return presets.find((preset) => preset.value === wanted)?.value ?? null;
}
