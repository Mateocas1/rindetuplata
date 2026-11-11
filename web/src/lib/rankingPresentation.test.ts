import { describe, expect, it } from 'vitest';
import { competitionRanks, formatPosition, gapToBest, toCents } from './rankingPresentation';

/**
 * Ranking presentation. Ranks and gaps are computed in integer
 * cents, so float noise never splits a tie or shifts a gap.
 */

describe('toCents', () => {
  it('converts API amounts with up to two decimals exactly', () => {
    expect(toCents(19726.03)).toBe(1972603n);
    expect(toCents(19315.07)).toBe(1931507n);
    expect(toCents(4.35)).toBe(435n);
    expect(toCents(1234567.89)).toBe(123456789n);
    expect(toCents(0.1)).toBe(10n);
  });

  it('does not drift on float sums', () => {
    expect(toCents(0.1 + 0.2)).toBe(30n);
  });

  it('handles negatives and zero', () => {
    expect(toCents(-410.96)).toBe(-41096n);
    expect(toCents(-0.01)).toBe(-1n);
    expect(toCents(0)).toBe(0n);
  });

  it('rejects non-finite values', () => {
    expect(() => toCents(Number.NaN)).toThrow(RangeError);
    expect(() => toCents(Number.POSITIVE_INFINITY)).toThrow(RangeError);
  });
});

describe('competitionRanks', () => {
  it('ranks distinct values 1, 2, 3 without ties', () => {
    expect(competitionRanks([300, 200, 100])).toEqual([
      { position: 1, tied: false },
      { position: 2, tied: false },
      { position: 3, tied: false },
    ]);
  });

  it('gives a tied pair the same position and skips the next one (1-2-2-4)', () => {
    expect(competitionRanks([300, 200, 200, 100])).toEqual([
      { position: 1, tied: false },
      { position: 2, tied: true },
      { position: 2, tied: true },
      { position: 4, tied: false },
    ]);
  });

  it('ties a group in the middle: four equal values at positions 4-7 share position 4', () => {
    const ranks = competitionRanks([900, 800, 700, 600, 600, 600, 600, 500]);
    expect(ranks.map((rank) => rank.position)).toEqual([1, 2, 3, 4, 4, 4, 4, 8]);
    expect(ranks.map((rank) => rank.tied)).toEqual([false, false, false, true, true, true, true, false]);
  });

  it('gives every member of an all-equal list position 1 and tied', () => {
    expect(competitionRanks([5, 5, 5])).toEqual([
      { position: 1, tied: true },
      { position: 1, tied: true },
      { position: 1, tied: true },
    ]);
  });

  it('compares in cents, so float noise does not split a tie', () => {
    expect(competitionRanks([0.1 + 0.2, 0.3])).toEqual([
      { position: 1, tied: true },
      { position: 1, tied: true },
    ]);
  });

  it('marks a single entry as untied', () => {
    expect(competitionRanks([5])).toEqual([{ position: 1, tied: false }]);
  });

  it('returns an empty list for no entries', () => {
    expect(competitionRanks([])).toEqual([]);
  });

  it('rejects amounts that are not sorted in descending order', () => {
    expect(() => competitionRanks([100, 200])).toThrow(RangeError);
  });
});

describe('gapToBest', () => {
  it('returns the gap in pesos, computed in cents', () => {
    expect(gapToBest(19726.03, 19315.07)).toBe(-410.96);
    expect(gapToBest(100, 99.5)).toBe(-0.5);
  });

  it('is zero for the best entry itself', () => {
    expect(gapToBest(19726.03, 19726.03)).toBe(0);
  });

  it('is exactly zero for float-noise inputs that are equal in cents', () => {
    expect(gapToBest(0.3, 0.1 + 0.2)).toBe(0);
  });
});

describe('formatPosition', () => {
  it('pads the position to two digits', () => {
    expect(formatPosition(1, false)).toBe('01');
    expect(formatPosition(12, false)).toBe('12');
  });

  it('prefixes "=" for a tied position', () => {
    expect(formatPosition(4, true)).toBe('=04');
  });

  it('never truncates a longer position', () => {
    expect(formatPosition(100, false)).toBe('100');
  });
});

describe('earnedFrom (T7 fix round)', () => {
  it('turns a conditional final amount into pesos earned, in exact cents', async () => {
    const { earnedFrom } = await import('./rankingPresentation');
    expect(earnedFrom(1_000_000, 1_019_915.26)).toBe(19_915.26);
    expect(earnedFrom(500_000, 500_000)).toBe(0);
  });
});
