import { readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';
import { addRealEarned, realEarned } from '../scripts/realEarned.mjs';

/**
 * `realEarned`: the pure function of `scripts/derive-real-earned.mjs`, which derives the
 * field for the recorded fixtures with the API's formula, `finalAmount / (1 + overHorizon) - amount`, in exact
 * decimal arithmetic rounded half-even to cents.
 */
describe('realEarned', () => {
  it('derives the REBA row of ranking-default.json', () => {
    // 1019726.03 / 1.01586 = 1003805.672041..., so 3805.67 above the expected inflation.
    expect(realEarned(1000000, 1019726.03, 0.01586)).toBe('3805.67');
  });

  it('derives the REBA row of the 500k, 60 day recording', () => {
    expect(realEarned(500000, 519726.03, 0.031971)).toBe('3624.65');
  });

  it('is negative when inflation beats the investment', () => {
    // 1010000 / 1.02 = 990196.0784..., so 9803.92 below the expected inflation.
    expect(realEarned(1000000, 1010000, 0.02)).toBe('-9803.92');
  });

  it('is the nominal earnings with zero inflation', () => {
    expect(realEarned(500000, 512345.67, 0)).toBe('12345.67');
    expect(realEarned('500000.00', '500000.00', '0.000000')).toBe('0.00');
  });

  it('rounds exact half centavos to the even cent, on both signs', () => {
    // 100% inflation: the factor is exactly 2, so every case is an exact half centavo.
    expect(realEarned(50, 100.01, 1)).toBe('0.00');
    expect(realEarned(50, 100.03, 1)).toBe('0.02');
    expect(realEarned(50, 100.05, 1)).toBe('0.02');
    expect(realEarned(50, 99.99, 1)).toBe('0.00');
    expect(realEarned(50, 99.97, 1)).toBe('-0.02');
    expect(realEarned(50, 99.95, 1)).toBe('-0.02');
  });

  it('never shows a negative zero', () => {
    expect(realEarned(50, 99.99, 1)).toBe('0.00');
  });

  it('keeps cents exact where a float division would not', () => {
    // 0.1 + 0.2 style drift: 3 / 1.2 - 2.5 is exactly 0 in decimal.
    expect(realEarned('2.5', '3', '0.2')).toBe('0.00');
    expect(realEarned('2.5', '3.0000001', '0.2')).toBe('0.00');
  });

  it('accepts exponent notation and plain strings', () => {
    expect(realEarned('1e6', '1.01e6', '2e-2')).toBe('-9803.92');
  });

  it('rejects inflation of -100% or less and malformed numbers', () => {
    expect(() => realEarned(100, 101, -1)).toThrow(/-100%/);
    expect(() => realEarned(100, 101, 'abc')).toThrow(/Not a decimal/);
  });
});

describe('addRealEarned', () => {
  const entries = [{ finalAmount: 1019726.03 }, { finalAmount: 1000000 }];
  const text = '{"ranked":[{"realReturn":0.003806,"upTo":null},{"realReturn":null,"upTo":null}]}';
  const expected =
    '{"ranked":[{"realReturn":0.003806,"realEarned":3805.67,"upTo":null},' +
    '{"realReturn":null,"realEarned":null,"upTo":null}]}';

  it('adds the field after realReturn, null exactly when realReturn is null, and is idempotent', () => {
    const once = addRealEarned(text, entries, 1000000, 0.01586);
    expect(once).toBe(expected);
    expect(addRealEarned(once, entries, 1000000, 0.01586)).toBe(once);
  });

  it('fails loudly when the text and the entries disagree', () => {
    expect(() => addRealEarned(text, entries.slice(0, 1), 1000000, 0.01586)).toThrow(/More realReturn/);
    expect(() => addRealEarned(text, entries, 1000000, null)).toThrow(/no inflation/);
  });
});

describe('recorded fixtures', () => {
  const fixtures = join(resolve(dirname(fileURLToPath(import.meta.url)), '..'), 'fixtures');
  const ranking = JSON.parse(readFileSync(join(fixtures, 'ranking-default.json'), 'utf8'));

  it('carry the derived realEarned of the REBA row', () => {
    const reba = ranking.ranked.find((entry: { id: string }) => entry.id === '29');
    expect(reba.realEarned).toBe(3805.67);
  });

  it('carry realEarned on every ranked entry, null exactly when realReturn is null', () => {
    for (const entry of ranking.ranked) {
      expect(entry.realEarned === null).toBe(entry.realReturn === null);
    }
  });
});
