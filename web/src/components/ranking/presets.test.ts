/**
 * Preset wiring of the ranking explorer (T13b).
 *
 * The Cobalto Cristal prototype proposed a capsule segmented control for the
 * amount and the term (L80). In the product those presets are shortcuts over
 * the reader's own query: picking one has to change the query the island sends
 * to `/api/v1/ranking` (the same one the free inputs produce), never a specimen
 * of the control. These are the rules that make that true:
 *
 * 1. A preset changes the amount or the term of the query, and the serialized
 *    query string and the API parameters change with it.
 * 2. Picking a preset keeps the reader's other filters (kinds, banks).
 * 3. A preset the API would reject leaves the query untouched.
 * 4. A free value that matches no preset leaves the control without a thumb.
 */
import { describe, expect, it } from 'vitest';
import {
  DEFAULT_RANKING_QUERY,
  serializeRankingQuery,
  toApiParams,
  type RankingQueryState,
} from '../../lib/rankingQuery';
import { AMOUNT_PRESETS, DAYS_PRESETS, applyAmountPreset, applyDaysPreset, matchPreset } from './presets';

/** A query the reader made up: it must survive every preset pick. */
const typed: RankingQueryState = {
  amount: 750_000,
  days: 45,
  kinds: ['FUND'],
  clientOf: ['7'],
};

describe('amount and term presets', () => {
  it('changes the query a preset asks the API for', () => {
    const next = applyAmountPreset(DEFAULT_RANKING_QUERY, '500000');

    expect(next.amount).toBe(500_000);
    expect(serializeRankingQuery(next)).toBe('?monto=500000');
    expect(serializeRankingQuery(next)).not.toBe(serializeRankingQuery(DEFAULT_RANKING_QUERY));
    expect(toApiParams(next).get('amount')).toBe('500000');
  });

  it('changes the term of the query, and keeps the amount', () => {
    const next = applyDaysPreset(DEFAULT_RANKING_QUERY, '90');

    expect(next.days).toBe(90);
    expect(serializeRankingQuery(next)).toBe('?dias=90');
    expect(next.amount).toBe(DEFAULT_RANKING_QUERY.amount);
  });

  it('keeps the filters the reader set', () => {
    const amount = applyAmountPreset(typed, '5000000');
    const days = applyDaysPreset(typed, '60');

    expect({ amount: amount.amount, days: amount.days }).toEqual({ amount: 5_000_000, days: 45 });
    expect({ amount: days.amount, days: days.days }).toEqual({ amount: 750_000, days: 60 });
    for (const next of [amount, days]) {
      expect(next.kinds).toEqual(['FUND']);
      expect(next.clientOf).toEqual(['7']);
      // A new object, so React re-renders on the change.
      expect(next).not.toBe(typed);
    }
  });

  it('leaves the query alone when the preset value is not usable', () => {
    expect(applyAmountPreset(typed, '0')).toBe(typed);
    expect(applyAmountPreset(typed, 'mil')).toBe(typed);
    expect(applyAmountPreset(typed, '')).toBe(typed);
    expect(applyDaysPreset(typed, '9999')).toBe(typed);
    expect(applyDaysPreset(typed, '30,5')).toBe(typed);
  });

  it('marks the preset a free typed value sits on, and none when it sits on another', () => {
    expect(matchPreset(AMOUNT_PRESETS, 1_000_000)).toBe('1000000');
    expect(matchPreset(AMOUNT_PRESETS, 750_000)).toBeNull();
    expect(matchPreset(DAYS_PRESETS, 30)).toBe('30');
    expect(matchPreset(DAYS_PRESETS, 45)).toBeNull();
  });

  it('offers the amounts and terms of the approved direction (L80)', () => {
    expect(AMOUNT_PRESETS.map((preset) => preset.value)).toEqual(['500000', '1000000', '5000000']);
    expect(DAYS_PRESETS.map((preset) => preset.value)).toEqual(['30', '60', '90']);
    for (const preset of [...AMOUNT_PRESETS, ...DAYS_PRESETS]) {
      expect(preset.label.length).toBeGreaterThan(0);
    }
  });
});
