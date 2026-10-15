/**
 * Presentation formatters (S14: all financial math lives in the Java API; the
 * browser only formats the values it receives).
 *
 * Measured facts of this project's Intl (Node 24 / es-AR): money is
 * "$" + U+00A0 + grouped amount, percent has no space before "%". Every
 * formatter swaps the ASCII hyphen for the typographic minus U+2212.
 */

const money = new Intl.NumberFormat('es-AR', { style: 'currency', currency: 'ARS' });

const percent = new Intl.NumberFormat('es-AR', {
  style: 'percent',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

const integer = new Intl.NumberFormat('es-AR', { maximumFractionDigits: 0 });

const shortDate = new Intl.DateTimeFormat('es-AR', {
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  timeZone: 'UTC',
});

const axisDate = new Intl.DateTimeFormat('es-AR', {
  day: '2-digit',
  month: '2-digit',
  year: '2-digit',
  timeZone: 'UTC',
});

const plural = new Intl.PluralRules('es-AR');

const MINUS = '\u2212';

function withTypographicMinus(value: string): string {
  return value.replace('-', MINUS);
}

/** "$ 1.019.726,03" */
export function formatMoney(amount: number): string {
  return withTypographicMinus(money.format(amount));
}

/** Fraction to percent with two decimals: 0.24 → "24,00%" */
export function formatPercent(fraction: number): string {
  return withTypographicMinus(percent.format(fraction));
}

/**
 * Percent with an explicit sign, for gains and losses (S10: the sign is text,
 * never color alone). An exact zero stays unsigned.
 */
export function formatSignedPercent(fraction: number): string {
  if (fraction === 0) {
    return formatPercent(0);
  }
  const sign = fraction > 0 ? '+' : MINUS;
  return `${sign}${formatPercent(Math.abs(fraction))}`;
}

/** ISO date to dd/mm/yyyy in the source's own day, never shifted by a time zone. */
export function formatDate(isoDate: string): string {
  return shortDate.format(new Date(`${isoDate}T00:00:00Z`));
}

/** ISO date to dd/mm/yy, for chart axis labels where space is tight. */
export function formatShortDate(isoDate: string): string {
  return axisDate.format(new Date(`${isoDate}T00:00:00Z`));
}

/** Grouped integer without decimals: 1234 → "1.234" */
export function formatInteger(value: number): string {
  return integer.format(value);
}

/** Days with the Spanish plural form: 30 → "30 días" */
export function formatDays(days: number): string {
  const unit = plural.select(days) === 'one' ? 'día' : 'días';
  return `${integer.format(days)} ${unit}`;
}
