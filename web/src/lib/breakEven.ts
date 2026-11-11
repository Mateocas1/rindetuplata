/**
 * The dólar MEP break-even in words. The API does the maths: this only turns its
 * `breakEvenMep` and `dollarMep` into the three sentences a row and the simulator show, or into null when either
 * is absent, so nothing is said about the dollar without a stored quote.
 */
import { formatDayMonth, formatDays, formatMoney, formatPercent } from './format';
import type { BreakEvenMep, MepQuote } from './types';

export interface BreakEvenNote {
  /** The rule: when the instrument beats the dollar. */
  rule: string;
  /** The quote it was measured against, dated. */
  quote: string;
  /** The same rule as a MEP price at the end of the term. */
  price: string;
}

export function breakEvenNote(
  breakEven: BreakEvenMep | null | undefined,
  quote: MepQuote | null | undefined,
  days: number,
): BreakEvenNote | null {
  if (breakEven == null || quote == null) {
    return null;
  }
  const { devaluation } = breakEven;
  // A negative tolerated devaluation (a quote with sell under buy, or a losing instrument) means the MEP has to fall.
  const rule =
    devaluation >= 0
      ? `Le gana al dólar MEP si el MEP sube menos de ${formatPercent(devaluation)} en ${formatDays(days)}`
      : `Le gana al dólar MEP solo si el MEP baja más de ${formatPercent(-devaluation)} en ${formatDays(days)}`;
  const day = formatDayMonth(quote.updatedAt ?? quote.quoteDate);
  return {
    rule,
    quote: `MEP al ${day}: ${formatMoney(quote.sell)} de venta`,
    price: `Si al vencimiento el MEP de compra pasa de ${formatMoney(breakEven.price)}, el dólar rinde más.`,
  };
}
