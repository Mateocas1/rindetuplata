import { describe, expect, it } from 'vitest';
import {
  formatDate,
  formatDays,
  formatInteger,
  formatMoney,
  formatPercent,
  formatShortDate,
  formatSignedPercent,
} from './format';

/**
 * Expected strings were measured from this project's Intl (Node 24, es-AR):
 * money is "$" + U+00A0 + grouped amount, percent has no space before "%",
 * and formatters replace the ASCII hyphen with the typographic minus U+2212
 * (better-typography: rendered text uses typographic characters).
 */
describe('formatMoney', () => {
  it('formats whole amounts as Argentine pesos', () => {
    expect(formatMoney(1_000_000)).toBe('$\u00a01.000.000,00');
  });

  it('keeps two decimals from the API value', () => {
    expect(formatMoney(1_019_726.03)).toBe('$\u00a01.019.726,03');
  });

  it('formats zero with two decimals', () => {
    expect(formatMoney(0)).toBe('$\u00a00,00');
  });

  it('renders a negative amount with the typographic minus', () => {
    expect(formatMoney(-1234.5)).toBe('\u2212$\u00a01.234,50');
  });
});

describe('formatPercent', () => {
  it('formats a fraction with two decimals', () => {
    expect(formatPercent(0.24)).toBe('24,00%');
  });

  it('rounds to two decimals', () => {
    expect(formatPercent(0.003806)).toBe('0,38%');
    expect(formatPercent(0.182284)).toBe('18,23%');
  });

  it('formats a negative fraction with the typographic minus', () => {
    expect(formatPercent(-0.000393)).toBe('\u22120,04%');
  });
});

describe('formatSignedPercent', () => {
  it('marks a gain with a plus sign', () => {
    expect(formatSignedPercent(0.003806)).toBe('+0,38%');
  });

  it('marks a loss with the typographic minus', () => {
    expect(formatSignedPercent(-0.000393)).toBe('\u22120,04%');
  });

  it('leaves an exact zero unsigned', () => {
    expect(formatSignedPercent(0)).toBe('0,00%');
  });
});

describe('formatDate', () => {
  it('formats an ISO date as dd/mm/yyyy', () => {
    expect(formatDate('2026-10-14')).toBe('14/10/2026');
  });

  it('does not shift the day across time zones', () => {
    expect(formatDate('2026-01-01')).toBe('01/01/2026');
    expect(formatDate('2026-12-31')).toBe('31/12/2026');
  });
});

describe('formatInteger', () => {
  it('groups thousands without decimals', () => {
    expect(formatInteger(398)).toBe('398');
    expect(formatInteger(1234)).toBe('1.234');
  });
});

describe('formatDays', () => {
  it('uses the Spanish plural form', () => {
    expect(formatDays(30)).toBe('30 días');
    expect(formatDays(1)).toBe('1 día');
  });
});

describe('formatShortDate', () => {
  it('formats a chart axis date in the source day, without a time zone shift', () => {
    expect(formatShortDate('2026-07-31')).toBe('31/07/26');
    expect(formatShortDate('2026-01-05')).toBe('05/01/26');
    expect(formatShortDate('2026-10-14')).toBe('14/10/26');
  });
});
