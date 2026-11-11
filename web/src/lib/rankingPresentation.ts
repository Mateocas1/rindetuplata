/**
 * Ranking presentation. The API serves each amount as a number with
 * up to two decimals; positions and gaps are computed in integer cents so float noise
 * never splits a tie or shifts a gap. Only the display of an API value uses these helpers.
 */

/**
 * Pesos to integer cents, exact for amounts with up to two decimals: 19726.03 → 1972603n.
 * Formats with toFixed(2) instead of multiplying, so 0.1 + 0.2 becomes 30n.
 */
export function toCents(value: number): bigint {
  if (!Number.isFinite(value) || Math.abs(value) >= 1e21) {
    throw new RangeError(`toCents expects a finite amount below 1e21, got ${value}`);
  }
  const [whole, fraction] = Math.abs(value).toFixed(2).split('.');
  const cents = BigInt(whole ?? '0') * 100n + BigInt(fraction ?? '0');
  return value < 0 ? -cents : cents;
}

/**
 * Standard competition ranking (1-2-2-4) of earned amounts served in descending order.
 * Amounts compare in cents; every member of a tied group is marked `tied`.
 */
export function competitionRanks(earned: number[]): { position: number; tied: boolean }[] {
  const cents = earned.map(toCents);
  let previous: bigint | undefined;
  for (const current of cents) {
    if (previous !== undefined && current > previous) {
      throw new RangeError('competitionRanks expects amounts sorted in descending order');
    }
    previous = current;
  }

  const ranks: { position: number; tied: boolean }[] = [];
  let start = 0;
  while (start < cents.length) {
    let end = start + 1;
    while (end < cents.length && cents[end] === cents[start]) {
      end += 1;
    }
    const tied = end - start > 1;
    for (let index = start; index < end; index += 1) {
      ranks.push({ position: start + 1, tied });
    }
    start = end;
  }
  return ranks;
}

/**
 * Pesos an entry earns below the best one, computed in cents:
 * gapToBest(19726.03, 19315.07) → -410.96. The best entry itself gives 0.
 */
export function gapToBest(bestEarned: number, earned: number): number {
  return Number(toCents(earned) - toCents(bestEarned)) / 100;
}

/**
 * Two-digit position with "=" before a tied one: (4, true) → "=04", (12, false) → "12".
 */
export function formatPosition(position: number, tied: boolean): string {
  return `${tied ? '=' : ''}${String(position).padStart(2, '0')}`;
}

/**
 * Pesos earned when an offer ends at `finalAmount` for an invested `amount`, in exact cents:
 * the API serves a conditional "hasta" offer as a final amount, and a row reads earnings, not balances.
 */
export function earnedFrom(amount: number, finalAmount: number): number {
  return Number(toCents(finalAmount) - toCents(amount)) / 100;
}
