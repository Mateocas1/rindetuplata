/**
 * Value handling for the account pages (T7d).
 *
 * Components hold form state as strings, exactly what the user typed, and this
 * module is the only place those strings become numbers: percents stay percents
 * (the alert and wallet-rate APIs take `thresholdPercent` / `tnaPercent`), and
 * amounts are ARS. Validation mirrors the API's own rules and keys every error
 * by the API's `parameter` name, so a 400 ProblemDetail attaches to the same
 * field without a second mapping.
 *
 * No financial maths happens here: converting a percent to a fraction is only
 * so the shared `formatPercent` can render it.
 */
import { kindLabel } from './labels';
import type { AlertInput, InstrumentSummary, WalletRate, WalletRateInput, WalletTier } from './account';

export interface TierFormState {
  floor: string;
  ceiling: string;
  tnaPercent: string;
}

export interface ConditionFormState {
  condition: string;
  tiers: TierFormState[];
}

export interface WalletFormState {
  name: string;
  note: string;
  tiers: TierFormState[];
  conditions: ConditionFormState[];
}

export interface AlertFormState {
  kind: string;
  instrumentId: string;
  thresholdPercent: string;
}

/** The API's own bounds (WalletRateValidator, AlertService): 0 < percent <= 300. */
export const MAX_PERCENT = 300;

/** Options that can never trigger an alert: the ranking does not list them. */
const UNRANKABLE_KIND = 'PLAZO_FIJO_OFFICIAL';

const PERCENT_PATTERN = /^\d+(?:[.,]\d+)?$/;
const AMOUNT_GROUPED_PATTERN = /^\d+(?:\.\d{3})+(?:,\d+)?$/;
const AMOUNT_PLAIN_PATTERN = /^\d+(?:,\d+)?$/;
const INTEGER_PATTERN = /^\d+$/;

const EMAIL_MESSAGE = 'Escribí un email válido, como nombre@ejemplo.com.';
const PERCENT_MESSAGE = 'Escribí una tasa en porcentaje, mayor a 0 y de hasta 300.';
const AMOUNT_MESSAGE = 'El piso tiene que ser un monto en pesos, de 0 para arriba.';
const CEILING_MESSAGE = 'El techo tiene que ser un monto en pesos.';
const CEILING_ORDER_MESSAGE = 'El techo tiene que ser mayor que el piso.';
const TIERS_MESSAGE = 'Cargá al menos una franja de tasa.';
const TIERS_ORDER_MESSAGE = 'Las franjas tienen que ir en orden y sin superponerse.';
const CONDITION_MESSAGE = 'Escribí un nombre para la condición.';
const INSTRUMENT_MESSAGE = 'Elegí una opción de la lista.';

const percentFormatter = new Intl.NumberFormat('es-AR', { maximumFractionDigits: 2 });

/** A percent typed in the field ("20", "20,5", "21.1") or null when it is out of the API's range. */
export function parsePercentInput(raw: string): number | null {
  const value = raw.trim();
  if (!PERCENT_PATTERN.test(value)) return null;
  const percent = Number(value.replace(',', '.'));
  if (!Number.isFinite(percent) || percent <= 0 || percent > MAX_PERCENT) return null;
  return percent;
}

/** An ARS amount typed in the field, with optional thousand dots and a decimal comma. */
export function parseAmountInput(raw: string): number | null {
  const value = raw.trim();
  if (value === '') return null;
  let normalized: string;
  if (AMOUNT_GROUPED_PATTERN.test(value)) {
    normalized = value.replaceAll('.', '').replace(',', '.');
  } else if (AMOUNT_PLAIN_PATTERN.test(value)) {
    normalized = value.replace(',', '.');
  } else {
    return null;
  }
  const amount = Number(normalized);
  return Number.isFinite(amount) ? amount : null;
}

/** A percent as the input text that parses back to it. */
export function formatPercentInput(percent: number): string {
  return Number.isFinite(percent) ? percentFormatter.format(percent) : '';
}

/** An amount as the input text that parses back to it (no thousand separators, comma decimal). */
export function formatAmountInput(amount: number): string {
  return Number.isFinite(amount) ? String(amount).replace('.', ',') : '';
}

export function percentToFraction(percent: number): number {
  return percent / 100;
}

export function fractionToPercent(fraction: number): number {
  return fraction * 100;
}

export function normalizeEmail(raw: string): string {
  return raw.trim().toLowerCase();
}

/** The API's own syntax check (Emails.isValid): null when fine, the fix otherwise. */
export function validateEmail(raw: string): string | null {
  const email = normalizeEmail(raw);
  if (email === '' || email.length > 254) return EMAIL_MESSAGE;
  if (/\s/.test(email)) return EMAIL_MESSAGE;
  const at = email.indexOf('@');
  if (at <= 0 || at !== email.lastIndexOf('@') || at === email.length - 1) return EMAIL_MESSAGE;
  const domain = email.slice(at + 1);
  const dot = domain.indexOf('.');
  if (!(dot > 0 && dot < domain.length - 1)) return EMAIL_MESSAGE;
  return null;
}

function parseInstrumentId(raw: string): number | null {
  const value = raw.trim();
  if (!INTEGER_PATTERN.test(value)) return null;
  const id = Number(value);
  if (!Number.isSafeInteger(id) || id <= 0) return null;
  return id;
}

/** Field-keyed errors for the alert form; an empty kind or instrument means "cualquiera". */
export function validateAlertForm(form: AlertFormState): Record<string, string> {
  const errors: Record<string, string> = {};
  if (parsePercentInput(form.thresholdPercent) === null) {
    errors.thresholdPercent = PERCENT_MESSAGE;
  }
  const instrument = form.instrumentId.trim();
  if (instrument !== '' && parseInstrumentId(instrument) === null) {
    errors.instrumentId = INSTRUMENT_MESSAGE;
  }
  return errors;
}

export function alertRequest(form: AlertFormState): AlertInput {
  if (Object.keys(validateAlertForm(form)).length > 0) {
    throw new Error('alert form is invalid');
  }
  const kind = form.kind.trim();
  return {
    kind: kind === '' ? null : kind,
    instrumentId: form.instrumentId.trim() === '' ? null : parseInstrumentId(form.instrumentId),
    thresholdPercent: parsePercentInput(form.thresholdPercent)!,
  };
}

function validateTiers(
  tiers: TierFormState[],
  errors: Record<string, string>,
  field: string,
): void {
  if (tiers.length === 0) {
    errors[field] = TIERS_MESSAGE;
    return;
  }

  let previousCeiling: number | null = null;
  tiers.forEach((tier, index) => {
    const prefix = `${field}[${index}]`;
    if (parsePercentInput(tier.tnaPercent) === null) {
      errors[`${prefix}.tnaPercent`] = PERCENT_MESSAGE;
    }

    const floorRaw = tier.floor.trim();
    const floor = floorRaw === '' ? 0 : parseAmountInput(floorRaw);
    if (floor === null || floor < 0) {
      errors[`${prefix}.floor`] = AMOUNT_MESSAGE;
    }

    const ceilingRaw = tier.ceiling.trim();
    const ceiling = ceilingRaw === '' ? null : parseAmountInput(ceilingRaw);
    if (ceilingRaw !== '' && ceiling === null) {
      errors[`${prefix}.ceiling`] = CEILING_MESSAGE;
    } else if (ceiling !== null && floor !== null && ceiling <= floor) {
      errors[`${prefix}.ceiling`] = CEILING_ORDER_MESSAGE;
    }

    if (index > 0 && (previousCeiling === null || floor === null || floor < previousCeiling)) {
      errors[field] = TIERS_ORDER_MESSAGE;
    }
    previousCeiling = ceiling;
  });
}

/** Field-keyed errors for the wallet-rate document, mirroring WalletRateValidator. */
export function validateWalletForm(form: WalletFormState): Record<string, string> {
  const errors: Record<string, string> = {};
  if (form.name.trim() === '') {
    errors.name = 'Escribí un nombre para la billetera.';
  }
  validateTiers(form.tiers, errors, 'tiers');
  form.conditions.forEach((condition, index) => {
    if (condition.condition.trim() === '') {
      errors[`conditions[${index}].condition`] = CONDITION_MESSAGE;
    }
    validateTiers(condition.tiers, errors, `conditions[${index}].tiers`);
  });
  return errors;
}

function tierPayload(tiers: TierFormState[]) {
  return tiers.map((tier) => ({
    floor: tier.floor.trim() === '' ? null : parseAmountInput(tier.floor.trim()),
    ceiling: tier.ceiling.trim() === '' ? null : parseAmountInput(tier.ceiling.trim()),
    tnaPercent: parsePercentInput(tier.tnaPercent)!,
  }));
}

/** The percent document `PUT /api/v1/admin/wallet-rates/{walletId}` expects. */
export function buildWalletRequest(form: WalletFormState): WalletRateInput {
  if (Object.keys(validateWalletForm(form)).length > 0) {
    throw new Error('wallet form is invalid');
  }
  const note = form.note.trim();
  return {
    name: form.name.trim(),
    note: note === '' ? null : note,
    tiers: tierPayload(form.tiers),
    conditions: form.conditions.map((condition) => ({
      condition: condition.condition.trim(),
      tiers: tierPayload(condition.tiers),
    })),
  };
}

/** The editable strings of an existing rate; `buildWalletRequest` turns them back into the document. */
export function walletFormFromRate(rate: WalletRate): WalletFormState {
  const tiers = (source: WalletTier[]): TierFormState[] =>
    source.map((tier) => ({
      floor: tier.floor === null ? '' : formatAmountInput(tier.floor),
      ceiling: tier.ceiling === null ? '' : formatAmountInput(tier.ceiling),
      tnaPercent: formatPercentInput(tier.tnaPercent),
    }));

  return {
    name: rate.name,
    note: rate.note ?? '',
    tiers: tiers(rate.tiers),
    conditions: rate.conditions.map((condition) => ({
      condition: condition.condition,
      tiers: tiers(condition.tiers),
    })),
  };
}

/** The options an alert can watch: valid rows the ranking actually lists. */
export function selectableInstruments(instruments: InstrumentSummary[]): InstrumentSummary[] {
  return instruments.filter(
    (instrument) => !instrument.invalid && instrument.kind !== UNRANKABLE_KIND,
  );
}

export function instrumentOptionLabel(instrument: InstrumentSummary): string {
  const parts = [instrument.displayName, kindLabel(instrument.kind)];
  if (instrument.entityName !== null && instrument.entityName !== instrument.displayName) {
    parts.push(instrument.entityName);
  }
  return parts.join(' · ');
}

/** The label of an alert's stored instrument id, or null when the option is gone. */
export function instrumentLabelById(
  instruments: InstrumentSummary[],
  id: number | null,
): string | null {
  if (id === null) return null;
  const instrument = instruments.find((entry) => entry.id === String(id));
  return instrument === undefined ? null : instrumentOptionLabel(instrument);
}
