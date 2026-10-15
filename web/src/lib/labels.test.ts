import { describe, expect, it } from 'vitest';
import { formatMoney } from './format';
import {
  KIND_LABELS,
  RATE_KIND_LABELS,
  confidenceLabel,
  excludedReasonLabel,
  flagLabel,
  ineligibleExplanation,
  ineligibleReasonLabel,
  inflationIndexLabel,
  inflationIndexShortLabel,
  kindLabel,
  rateKindLabel,
} from './labels';

/**
 * Every code the API can return must render as Spanish copy, and an unknown code
 * must fall back to the code itself: a new reason shipped by the API shows a raw
 * code, never the string "undefined" (S11: every excluded row states its reason).
 */
describe('kindLabel', () => {
  it('names each ranked kind in Spanish', () => {
    expect(KIND_LABELS).toMatchObject({
      PLAZO_FIJO: 'Plazo fijo',
      WALLET: 'Billetera',
      FUND: 'Fondo',
      CRYPTO: 'Cripto',
    });
  });

  it('falls back to the raw code', () => {
    expect(kindLabel('PLAZO_FIJO_OFFICIAL')).toBe('Plazo fijo oficial');
    expect(kindLabel('SOMETHING_NEW')).toBe('SOMETHING_NEW');
  });
});

describe('confidenceLabel', () => {
  it('names every confidence level', () => {
    for (const code of [
      'OFFICIAL_AUTOMATIC',
      'AGGREGATOR_AUTOMATIC',
      'SCRAPED_ENTITY_WEB',
      'CURATED_MANUAL',
    ]) {
      expect(confidenceLabel(code)).not.toBe(code);
      expect(confidenceLabel(code).length).toBeGreaterThan(0);
    }
  });

  it('falls back to the raw code', () => {
    expect(confidenceLabel('UNHEARD_OF')).toBe('UNHEARD_OF');
  });
});

describe('excluded and ineligible reasons', () => {
  it('explains every reason the fixtures contain', () => {
    for (const reason of [
      'STALE',
      'NOT_OFFERED_TO_NON_CLIENTS',
      'NO_CLIENT_RATE',
      'INSUFFICIENT_HISTORY',
      'NEGATIVE_RETURN',
      'INVALID_DATA',
    ]) {
      expect(excludedReasonLabel(reason)).not.toBe(reason);
    }
    for (const reason of ['MINIMUM_TERM', 'MINIMUM_AMOUNT']) {
      expect(ineligibleReasonLabel(reason)).not.toBe(reason);
    }
  });

  it('falls back to the raw code', () => {
    expect(excludedReasonLabel('WHATEVER')).toBe('WHATEVER');
    expect(ineligibleReasonLabel('WHATEVER')).toBe('WHATEVER');
  });
});

describe('flagLabel', () => {
  it('names every validation flag the pipeline emits', () => {
    for (const code of ['STALE', 'INVALID', 'MISSING_VALUE', 'TERM_CODE_UNDOCUMENTED']) {
      expect(flagLabel(code)).not.toBe(code);
    }
  });

  it('falls back to the raw code', () => {
    expect(flagLabel('UNKNOWN_FLAG')).toBe('UNKNOWN_FLAG');
  });
});

describe('rateKindLabel', () => {
  it('tells the reader which rate was used', () => {
    expect(RATE_KIND_LABELS).toMatchObject({
      NON_CLIENT: 'tasa para no clientes',
      CLIENT: 'tasa para clientes',
      CLIENT_TERM_BAND: 'tasa de tu franja',
      WALLET_BASE: 'tasa base',
      FUND_TNA_EQUIVALENT: 'TNA equivalente del fondo',
      APY_TNA_EQUIVALENT: 'TNA equivalente del APY',
    });
  });

  it('falls back to the raw code', () => {
    expect(rateKindLabel('BRAND_NEW')).toBe('BRAND_NEW');
  });
});

describe('ineligibleExplanation', () => {
  it('tells the reader what to change when the term is too short', () => {
    expect(ineligibleExplanation('MINIMUM_TERM', { days: 30, amount: 1_000_000 })).toBe(
      'Pide un plazo mínimo mayor a 30 días.',
    );
  });

  it('tells the reader what to change when the amount is too low', () => {
    expect(ineligibleExplanation('MINIMUM_AMOUNT', { days: 30, amount: 1_000_000 })).toBe(
      `Pide un monto mínimo mayor a ${formatMoney(1_000_000)}.`,
    );
  });

  it('falls back to the reason label', () => {
    expect(ineligibleExplanation('SOMETHING_NEW', { days: 30, amount: 1_000_000 })).toBe(
      'SOMETHING_NEW',
    );
  });
});

describe('inflationIndexLabel', () => {
  it('names the index that produced the real return (S10)', () => {
    expect(inflationIndexLabel('REM_12M_EXPECTED')).toContain('REM');
    expect(inflationIndexLabel('INDEC_CPI_MONTHLY')).toContain('INDEC');
  });

  it('falls back to the raw code', () => {
    expect(inflationIndexLabel('OTHER')).toBe('OTHER');
  });
});

describe('inflationIndexShortLabel', () => {
  it('gives the column header a short name for the index', () => {
    expect(inflationIndexShortLabel('REM_12M_EXPECTED')).toBe('REM');
    expect(inflationIndexShortLabel('INDEC_CPI_MONTHLY')).toBe('INDEC');
  });

  it('falls back to the raw code', () => {
    expect(inflationIndexShortLabel('OTHER')).toBe('OTHER');
  });
});
