import { describe, expect, it } from 'vitest';
import {
  alertRequest,
  buildWalletRequest,
  formatAmountInput,
  formatPercentInput,
  fractionToPercent,
  instrumentLabelById,
  instrumentOptionLabel,
  normalizeEmail,
  parseAmountInput,
  parsePercentInput,
  percentToFraction,
  selectableInstruments,
  validateAlertForm,
  validateEmail,
  validateWalletForm,
  walletFormFromRate,
  type AlertFormState,
  type WalletFormState,
} from './accountForm';
import type { InstrumentSummary, WalletRate } from './account';

/**
 * Value handling of the account forms (T7d). These are the only place a typed
 * value is converted, so components stay thin and the unit conventions live in
 * one tested file: percent inputs are percents, amounts are ARS, and the API's
 * `parameter` names are the error keys (a 400 attaches to the same field).
 */

const WALLET: WalletFormState = {
  name: 'Naranja X',
  note: 'Tope $1.000.000.',
  tiers: [{ floor: '', ceiling: '1000000', tnaPercent: '20' }],
  conditions: [],
};

describe('percent and amount inputs', () => {
  it('parses a percent typed with a comma or a dot', () => {
    expect(parsePercentInput('20')).toBe(20);
    expect(parsePercentInput(' 20,5 ')).toBe(20.5);
    expect(parsePercentInput('21.1')).toBe(21.1);
    expect(parsePercentInput('300')).toBe(300);
  });

  it('rejects a percent outside the API range or not a number', () => {
    expect(parsePercentInput('')).toBeNull();
    expect(parsePercentInput('0')).toBeNull();
    expect(parsePercentInput('-5')).toBeNull();
    expect(parsePercentInput('301')).toBeNull();
    expect(parsePercentInput('20,5,5')).toBeNull();
    expect(parsePercentInput('veinte')).toBeNull();
  });

  it('parses an amount with optional thousand dots and a decimal comma', () => {
    expect(parseAmountInput('1000000')).toBe(1_000_000);
    expect(parseAmountInput('1.000.000')).toBe(1_000_000);
    expect(parseAmountInput('1500,50')).toBe(1500.5);
    expect(parseAmountInput(' 0 ')).toBe(0);
    expect(parseAmountInput('')).toBeNull();
    expect(parseAmountInput('1.5')).toBeNull();
    expect(parseAmountInput('monto')).toBeNull();
  });

  it('formats an input value so it parses back unchanged', () => {
    expect(formatPercentInput(20)).toBe('20');
    expect(formatPercentInput(20.5)).toBe('20,5');
    expect(formatAmountInput(1_000_000)).toBe('1000000');
    expect(formatAmountInput(1500.5)).toBe('1500,5');
    expect(parseAmountInput(formatAmountInput(1500.5))).toBe(1500.5);
    expect(parsePercentInput(formatPercentInput(21.1))).toBe(21.1);
  });

  it('converts between percent and fraction for the shared formatters', () => {
    expect(percentToFraction(20)).toBeCloseTo(0.2, 12);
    expect(percentToFraction(21.1)).toBeCloseTo(0.211, 12);
    expect(fractionToPercent(0.2)).toBeCloseTo(20, 12);
    expect(fractionToPercent(0.211)).toBeCloseTo(21.1, 12);
  });
});

describe('email validation', () => {
  it('normalizes the email the way the API stores it', () => {
    expect(normalizeEmail('  Saver@Example.com ')).toBe('saver@example.com');
    expect(normalizeEmail('')).toBe('');
  });

  it('accepts a valid email and names the fix for a broken one', () => {
    expect(validateEmail('saver@example.com')).toBeNull();
    expect(validateEmail('SAVER@EXAMPLE.COM')).toBeNull();
    expect(validateEmail('sin-arroba')).toContain('email');
    expect(validateEmail('dos@@example.com')).toContain('email');
    expect(validateEmail('saver@example')).toContain('email');
    expect(validateEmail('saver @example.com')).toContain('email');
    expect(validateEmail('')).toContain('email');
  });
});

describe('alert form', () => {
  it('accepts an empty kind and instrument', () => {
    expect(validateAlertForm({ kind: '', instrumentId: '', thresholdPercent: '25' })).toEqual({});
  });

  it("requires a threshold in the API's percent range", () => {
    expect(validateAlertForm({ kind: '', instrumentId: '', thresholdPercent: '' })).toHaveProperty(
      'thresholdPercent',
    );
    expect(validateAlertForm({ kind: '', instrumentId: '', thresholdPercent: '0' })).toHaveProperty(
      'thresholdPercent',
    );
    expect(validateAlertForm({ kind: '', instrumentId: '', thresholdPercent: '301' })).toHaveProperty(
      'thresholdPercent',
    );
  });

  it('rejects an instrument id that is not a number', () => {
    expect(
      validateAlertForm({ kind: 'WALLET', instrumentId: 'abc', thresholdPercent: '20' }),
    ).toHaveProperty('instrumentId');
    expect(
      validateAlertForm({ kind: 'WALLET', instrumentId: '12', thresholdPercent: '20' }),
    ).toEqual({});
  });

  it('builds the request the API documents: percent as a percent number', () => {
    expect(
      alertRequest({ kind: 'WALLET', instrumentId: '12', thresholdPercent: '23,5' }),
    ).toEqual({ kind: 'WALLET', instrumentId: 12, thresholdPercent: 23.5 });
    expect(alertRequest({ kind: '', instrumentId: '', thresholdPercent: '20' })).toEqual({
      kind: null,
      instrumentId: null,
      thresholdPercent: 20,
    });
  });
});

describe('wallet form validation', () => {
  it('accepts a well formed document', () => {
    expect(validateWalletForm(WALLET)).toEqual({});
  });

  it('requires a name and at least one tier', () => {
    expect(validateWalletForm({ ...WALLET, name: '   ' })).toHaveProperty('name');
    expect(validateWalletForm({ ...WALLET, tiers: [] })).toHaveProperty('tiers');
  });

  it('rejects a tier without a valid TNA percent', () => {
    for (const tnaPercent of ['', '0', '301', 'veinte']) {
      expect(
        validateWalletForm({ ...WALLET, tiers: [{ floor: '', ceiling: '', tnaPercent }] }),
      ).toHaveProperty('tiers[0].tnaPercent');
    }
  });

  it('rejects a negative floor and a ceiling that does not exceed it', () => {
    expect(
      validateWalletForm({ ...WALLET, tiers: [{ floor: '-1', ceiling: '10', tnaPercent: '20' }] }),
    ).toHaveProperty('tiers[0].floor');
    expect(
      validateWalletForm({ ...WALLET, tiers: [{ floor: '100', ceiling: '100', tnaPercent: '20' }] }),
    ).toHaveProperty('tiers[0].ceiling');
  });

  it('rejects tiers that overlap or restart below the previous ceiling', () => {
    expect(
      validateWalletForm({
        ...WALLET,
        tiers: [
          { floor: '', ceiling: '1000', tnaPercent: '20' },
          { floor: '500', ceiling: '', tnaPercent: '18' },
        ],
      }),
    ).toHaveProperty('tiers');
    expect(
      validateWalletForm({
        ...WALLET,
        tiers: [
          { floor: '', ceiling: '', tnaPercent: '20' },
          { floor: '500', ceiling: '', tnaPercent: '18' },
        ],
      }),
    ).toHaveProperty('tiers');
  });

  it('accepts a following tier that starts exactly at the previous ceiling', () => {
    expect(
      validateWalletForm({
        ...WALLET,
        tiers: [
          { floor: '', ceiling: '1000', tnaPercent: '20' },
          { floor: '1000', ceiling: '', tnaPercent: '18' },
        ],
      }),
    ).toEqual({});
  });

  it('requires a label and a tier for every condition', () => {
    expect(
      validateWalletForm({
        ...WALLET,
        conditions: [{ condition: '  ', tiers: [{ floor: '', ceiling: '', tnaPercent: '24' }] }],
      }),
    ).toHaveProperty('conditions[0].condition');
    expect(
      validateWalletForm({ ...WALLET, conditions: [{ condition: 'Si gastás', tiers: [] }] }),
    ).toHaveProperty('conditions[0].tiers');
  });

  it('builds the percent document the API expects', () => {
    expect(
      buildWalletRequest({
        name: '  Naranja X  ',
        note: '  ',
        tiers: [{ floor: '', ceiling: '1.000.000', tnaPercent: '20' }],
        conditions: [{ condition: 'Si gastás $1.000.000', tiers: [{ floor: '', ceiling: '', tnaPercent: '24' }] }],
      }),
    ).toEqual({
      name: 'Naranja X',
      note: null,
      tiers: [{ floor: null, ceiling: 1_000_000, tnaPercent: 20 }],
      conditions: [
        { condition: 'Si gastás $1.000.000', tiers: [{ floor: null, ceiling: null, tnaPercent: 24 }] },
      ],
    });
  });

  it('loads an existing rate into the editable strings, round-tripping its document', () => {
    const rate: WalletRate = {
      walletId: 'naranja-x',
      name: 'Naranja X',
      tiers: [{ floor: null, ceiling: 1_000_000, tnaPercent: 20 }],
      conditions: [{ condition: 'Si gastás', tiers: [{ floor: 0, ceiling: null, tnaPercent: 24 }] }],
      note: 'Tope $1.000.000.',
      verifiedAt: '2026-10-15',
      daysSinceVerified: 0,
      updatedAt: '2026-10-15T10:00:00Z',
      updatedBy: 'admin@example.com',
    };

    const form = walletFormFromRate(rate);
    expect(form).toEqual({
      name: 'Naranja X',
      note: 'Tope $1.000.000.',
      tiers: [{ floor: '', ceiling: '1000000', tnaPercent: '20' }],
      conditions: [
        { condition: 'Si gastás', tiers: [{ floor: '0', ceiling: '', tnaPercent: '24' }] },
      ],
    });
    expect(buildWalletRequest(form)).toEqual({
      name: rate.name,
      note: rate.note,
      tiers: [{ floor: null, ceiling: 1_000_000, tnaPercent: 20 }],
      conditions: [{ condition: 'Si gastás', tiers: [{ floor: 0, ceiling: null, tnaPercent: 24 }] }],
    });
  });
});

describe('instrument picker', () => {
  const instruments: InstrumentSummary[] = [
    {
      id: '7',
      kind: 'WALLET',
      displayName: 'Naranja X',
      entityName: 'Naranja X',
      segment: null,
      sourceId: 'curated-wallet',
      rate: 0.2,
      basis: 'TNA',
      clientRate: null,
      captureDate: '2026-10-14',
      dataDate: '2026-10-14',
      confidence: 'CURATED_MANUAL',
      verifiedAt: '2026-10-13',
      invalid: false,
      flags: [],
      quoteDate: null,
      vcp: null,
    },
    {
      id: '8',
      kind: 'PLAZO_FIJO_OFFICIAL',
      displayName: 'PF TRADICIONAL',
      entityName: 'Banco Nación',
      segment: null,
      sourceId: 'bcra',
      rate: 0.21,
      basis: 'TEA',
      clientRate: null,
      captureDate: '2026-10-14',
      dataDate: '2026-06-01',
      confidence: 'OFFICIAL_AUTOMATIC',
      verifiedAt: null,
      invalid: false,
      flags: [],
      quoteDate: null,
      vcp: null,
    },
  ];

  it('leaves out options that can never trigger an alert', () => {
    expect(selectableInstruments(instruments).map((entry) => entry.id)).toEqual(['7']);
  });

  it('names an option with its kind and deduplicates the entity', () => {
    expect(instrumentOptionLabel(instruments[0])).toBe('Naranja X · Billetera');
    expect(instrumentOptionLabel(instruments[1])).toBe('PF TRADICIONAL · Plazo fijo oficial · Banco Nación');
  });

  it('finds an option by the numeric id an alert stores, and says nothing when it is unknown', () => {
    expect(instrumentLabelById(instruments, 7)).toBe('Naranja X · Billetera');
    expect(instrumentLabelById(instruments, 999)).toBeNull();
    expect(instrumentLabelById(instruments, null)).toBeNull();
  });
});

describe('form state type', () => {
  it('is the transport shape of the alert form', () => {
    const form: AlertFormState = { kind: '', instrumentId: '', thresholdPercent: '' };
    expect(form.thresholdPercent).toBe('');
  });
});
