/**
 * Shared presentation of a served ranking: the positions of its rows (ui-clarity D7) and how the reader's
 * query amount reads in copy.
 */
import { formatMoney, formatMoneyWhole } from '../../lib/format';
import { competitionRanks } from '../../lib/rankingPresentation';
import type { RankedEntry } from '../../lib/types';

export interface RowPosition {
  position: number;
  tied: boolean;
}

/**
 * Standard competition ranks over the API's `earned`, compared in cents. The API sorts by pesos earned (MVP
 * S15), which `competitionRanks` requires; should a response ever arrive out of order, the board falls back to
 * plain 1..n positions instead of failing to render the reader's ranking.
 */
export function rankPositions(ranked: readonly RankedEntry[]): RowPosition[] {
  try {
    return competitionRanks(ranked.map((entry) => entry.earned));
  } catch {
    return ranked.map((_, index) => ({ position: index + 1, tied: false }));
  }
}

/** The reader's amount in copy: "$ 1.000.000" for whole pesos, "$ 1.000,50" when it has cents. */
export function formatQueryAmount(amount: number): string {
  return Number.isInteger(amount) ? formatMoneyWhole(amount) : formatMoney(amount);
}
