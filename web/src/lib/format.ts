/**
 * Presentation formatters (all financial math lives in the Java API; the
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

const moneyWhole = new Intl.NumberFormat('es-AR', {
  style: 'currency',
  currency: 'ARS',
  minimumFractionDigits: 0,
  maximumFractionDigits: 0,
});

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

const dayMonth = new Intl.DateTimeFormat('es-AR', {
  day: '2-digit',
  month: '2-digit',
  timeZone: 'UTC',
});

const dayMonthBuenosAires = new Intl.DateTimeFormat('es-AR', {
  day: '2-digit',
  month: '2-digit',
  timeZone: 'America/Argentina/Buenos_Aires',
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

/**
 * Whole pesos, for a difference read at a glance (ui-clarity D6, D8): "$ 3.806".
 * The sign goes before the currency symbol: "−$ 2.402".
 */
export function formatMoneyWhole(amount: number): string {
  const formatted = moneyWhole.format(Math.abs(amount));
  return Math.round(amount) < 0 ? `${MINUS}${formatted}` : formatted;
}

/** Fraction to percent with two decimals: 0.24 → "24,00%" */
export function formatPercent(fraction: number): string {
  return withTypographicMinus(percent.format(fraction));
}

/**
 * Percent with an explicit sign, for gains and losses (the sign is text,
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

/**
 * dd/mm of a plain date (the source's own day) or of an instant ("T" in it), which is read in Buenos Aires: a
 * quote reported at 22:30 local time belongs to that day, not to the next day in UTC.
 */
export function formatDayMonth(value: string): string {
  return value.includes('T')
    ? dayMonthBuenosAires.format(new Date(value))
    : dayMonth.format(new Date(`${value}T00:00:00Z`));
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
