/**
 * The dólar MEP break-even sentence (S5, L24 b): the API computes the numbers, the browser only words them.
 *
 * Synthetic data on purpose (L16): the recorded ranking fixtures predate the field. The quote is the repository
 * fixture of the API tests (buy 1534.20, sell 1539.40, reported 2026-10-13T20:57Z).
 */
import { describe, expect, it } from 'vitest';
import { breakEvenNote } from './breakEven';
import type { BreakEvenMep, MepQuote } from './types';

const SOURCE = { id: 'dolarapi-bolsa', displayName: 'DolarApi', official: false };

const quote: MepQuote = {
  buy: 1534.2,
  sell: 1539.4,
  source: SOURCE,
  quoteDate: '2026-10-14',
  updatedAt: '2026-10-13T20:57:00Z',
};

const breakEven: BreakEvenMep = { devaluation: 0.02813, price: 1577.36 };

describe('breakEvenNote', () => {
  it('words the rule, the quote and the break-even price from the API values', () => {
    expect(breakEvenNote(breakEven, quote, 30)).toEqual({
      rule: 'Le gana al dólar MEP si el MEP sube menos de 2,81% en 30 días',
      quote: 'MEP al 13/10: $\u00a01.539,40 de venta',
      price: 'Si al vencimiento el MEP de compra pasa de $\u00a01.577,36, el dólar rinde más.',
    });
  });

  it('dates the quote by the instant the source reports, in Buenos Aires', () => {
    // 01:30Z is still the 13th at 22:30 in Buenos Aires (UTC-3).
    const lateNight = { ...quote, updatedAt: '2026-10-14T01:30:00Z' };

    expect(breakEvenNote(breakEven, lateNight, 30)?.quote).toBe('MEP al 13/10: $\u00a01.539,40 de venta');
  });

  it('falls back to the capture date when the source reports no instant', () => {
    const noInstant = { ...quote, updatedAt: null };

    expect(breakEvenNote(breakEven, noInstant, 30)?.quote).toBe('MEP al 14/10: $\u00a01.539,40 de venta');
  });

  it('uses the singular for one day', () => {
    expect(breakEvenNote(breakEven, quote, 1)?.rule).toBe(
      'Le gana al dólar MEP si el MEP sube menos de 2,81% en 1 día',
    );
  });

  it('says the MEP has to fall when the tolerated devaluation is negative', () => {
    const inverted = { devaluation: -0.001623, price: 1537.5 };

    expect(breakEvenNote(inverted, quote, 30)?.rule).toBe(
      'Le gana al dólar MEP solo si el MEP baja más de 0,16% en 30 días',
    );
  });

  it('says nothing without a break-even or without the quote it came from', () => {
    expect(breakEvenNote(null, quote, 30)).toBeNull();
    expect(breakEvenNote(undefined, quote, 30)).toBeNull();
    expect(breakEvenNote(breakEven, null, 30)).toBeNull();
    expect(breakEvenNote(breakEven, undefined, 30)).toBeNull();
  });
});
