/**
 * The amount field reads and shows pesos the Argentine way (ui-clarity D16): dots group thousands, a comma
 * starts the cents. Plain digits keep working. The API's range check stays in `tryParseAmount`.
 */
import { describe, expect, it } from 'vitest';
import { formatAmountInput, parseAmountInput } from './amountInput';

describe('parseAmountInput', () => {
  it('reads plain digits and grouped thousands alike', () => {
    expect(parseAmountInput('1000000')).toBe(1_000_000);
    expect(parseAmountInput('1.000.000')).toBe(1_000_000);
    expect(parseAmountInput(' 500.000 ')).toBe(500_000);
    expect(parseAmountInput('$ 2.500.000')).toBe(2_500_000);
  });

  it('reads a comma as the start of the cents', () => {
    expect(parseAmountInput('1.000,50')).toBe(1000.5);
    expect(parseAmountInput('1000,5')).toBe(1000.5);
  });

  it('rejects ambiguous or malformed grouping instead of guessing', () => {
    expect(parseAmountInput('1.0000')).toBeNull();
    expect(parseAmountInput('1000.5')).toBeNull();
    expect(parseAmountInput('1,000,000')).toBeNull();
    expect(parseAmountInput('1.000,505')).toBeNull();
    expect(parseAmountInput('abc')).toBeNull();
    expect(parseAmountInput('')).toBeNull();
  });

  it('keeps the API range: zero, negatives and amounts over the cap are rejected', () => {
    expect(parseAmountInput('0')).toBeNull();
    expect(parseAmountInput('-5')).toBeNull();
    expect(parseAmountInput('2.000.000.000.000')).toBeNull();
  });
});

describe('formatAmountInput', () => {
  it('groups thousands with dots and shows cents only when there are any', () => {
    expect(formatAmountInput(1_000_000)).toBe('1.000.000');
    expect(formatAmountInput(1000.5)).toBe('1.000,50');
    expect(formatAmountInput(999)).toBe('999');
  });

  it('round-trips through the parser', () => {
    for (const value of [1, 999, 1000, 1_234_567.89, 500_000]) {
      expect(parseAmountInput(formatAmountInput(value))).toBe(value);
    }
  });
});
