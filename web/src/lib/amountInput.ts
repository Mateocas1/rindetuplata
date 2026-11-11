/**
 * The amount field in Argentine notation: "1.000.000" or "1.000,50". Dots only group
 * thousands and a comma starts the cents, so "1000.5" is rejected instead of guessed. The parsed value goes
 * through `tryParseAmount`, which keeps the API's range (greater than 0, at most the API cap).
 */
import { tryParseAmount } from './rankingQuery';

const GROUPED = /^\d{1,3}(?:\.\d{3})+(?:,\d{1,2})?$/;
const PLAIN = /^\d+(?:,\d{1,2})?$/;

const grouping = new Intl.NumberFormat('es-AR', { minimumFractionDigits: 0, maximumFractionDigits: 2 });

/** The amount a reader typed, in es-AR notation or plain digits; null when malformed or out of range. */
export function parseAmountInput(raw: string): number | null {
  const value = raw.replace(/\$/g, '').trim();
  if (!GROUPED.test(value) && !PLAIN.test(value)) return null;
  const canonical = value.replace(/\./g, '').replace(',', '.');
  return tryParseAmount(canonical);
}

/** An amount as the field shows it when not focused: "1.000.000", "1.000,50". */
export function formatAmountInput(amount: number): string {
  const formatted = grouping.format(amount);
  return formatted.includes(',') && /,\d$/.test(formatted) ? `${formatted}0` : formatted;
}
