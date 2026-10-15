/**
 * Spanish copy for the codes the API returns (S11: every row states its source,
 * its data date and — when excluded — its reason).
 *
 * Labels live here, not in components, so a new code is one edit and the copy can
 * be reviewed in one place. Unknown codes fall back to the raw code: a fresh API
 * reason shows up as `NEW_REASON`, never as "undefined".
 */
import type { RankedKind } from './types';
import { formatDays, formatMoney } from './format';

export const KIND_LABELS: Record<string, string> = {
  PLAZO_FIJO: 'Plazo fijo',
  WALLET: 'Billetera',
  FUND: 'Fondo',
  CRYPTO: 'Cripto',
  PLAZO_FIJO_OFFICIAL: 'Plazo fijo oficial',
  PLAZO_FIJO_BANK: 'Plazo fijo',
  FUND_CLASS: 'Fondo',
  CRYPTO_YIELD: 'Cripto',
};

export const CONFIDENCE_LABELS: Record<string, string> = {
  OFFICIAL_AUTOMATIC: 'Oficial automática',
  AGGREGATOR_AUTOMATIC: 'Agregador automático',
  SCRAPED_ENTITY_WEB: 'Sitio de la entidad',
  CURATED_MANUAL: 'Carga manual verificada',
};

/** Longer explanations for the methodology page. */
export const CONFIDENCE_DESCRIPTIONS: Record<string, string> = {
  OFFICIAL_AUTOMATIC: 'Dato descargado automáticamente de una fuente oficial (por ejemplo, el BCRA).',
  AGGREGATOR_AUTOMATIC:
    'Dato descargado automáticamente de un agregador comunitario que publica datos de la fuente original.',
  SCRAPED_ENTITY_WEB: 'Dato leído de la web pública de la entidad.',
  CURATED_MANUAL: 'Dato cargado a mano a partir de información pública y verificado en la fecha indicada.',
};

export const EXCLUDED_REASON_LABELS: Record<string, string> = {
  STALE: 'Datos desactualizados',
  NOT_OFFERED_TO_NON_CLIENTS: 'No se ofrece a quienes no son clientes',
  NO_CLIENT_RATE: 'Sin tasa para no clientes',
  INSUFFICIENT_HISTORY: 'Historial insuficiente',
  NEGATIVE_RETURN: 'Rendimiento negativo',
  INVALID_DATA: 'Datos inválidos',
};

export const INELIGIBLE_REASON_LABELS: Record<string, string> = {
  MINIMUM_TERM: 'No alcanza el plazo mínimo',
  MINIMUM_AMOUNT: 'No alcanza el monto mínimo',
};

export const FLAG_LABELS: Record<string, string> = {
  STALE: 'Dato desactualizado',
  INVALID: 'Dato inválido',
  MISSING_VALUE: 'Falta un dato',
  TERM_CODE_UNDOCUMENTED: 'Plazo mínimo sin documentar',
};

export const RATE_KIND_LABELS: Record<string, string> = {
  NON_CLIENT: 'tasa para no clientes',
  CLIENT: 'tasa para clientes',
  CLIENT_TERM_BAND: 'tasa de tu franja',
  WALLET_BASE: 'tasa base',
  FUND_TNA_EQUIVALENT: 'TNA equivalente del fondo',
  APY_TNA_EQUIVALENT: 'TNA equivalente del APY',
};

export const INFLATION_INDEX_LABELS: Record<string, string> = {
  REM_12M_EXPECTED: 'REM (12 meses, BCRA)',
  INDEC_CPI_MONTHLY: 'IPC mensual (INDEC)',
};

/** Short index names, for the column header next to the real-return badge (S10). */
export const INFLATION_INDEX_SHORT_LABELS: Record<string, string> = {
  REM_12M_EXPECTED: 'REM',
  INDEC_CPI_MONTHLY: 'INDEC',
};

function labelFrom(labels: Record<string, string>, code: string): string {
  return labels[code] ?? code;
}

export function kindLabel(kind: string): string {
  return labelFrom(KIND_LABELS, kind);
}

export function confidenceLabel(confidence: string): string {
  return labelFrom(CONFIDENCE_LABELS, confidence);
}

export function confidenceDescription(confidence: string): string {
  return labelFrom(CONFIDENCE_DESCRIPTIONS, confidence);
}

export function excludedReasonLabel(reason: string): string {
  return labelFrom(EXCLUDED_REASON_LABELS, reason);
}

export function ineligibleReasonLabel(reason: string): string {
  return labelFrom(INELIGIBLE_REASON_LABELS, reason);
}

/**
 * What the reader can change to bring an ineligible option back into the
 * ranking. The API's own `detail` is developer-facing English, so the copy is
 * built here from the request the reader made.
 */
export function ineligibleExplanation(
  reason: string,
  context: { days: number; amount: number },
): string {
  if (reason === 'MINIMUM_TERM') {
    return `Pide un plazo mínimo mayor a ${formatDays(context.days)}.`;
  }
  if (reason === 'MINIMUM_AMOUNT') {
    return `Pide un monto mínimo mayor a ${formatMoney(context.amount)}.`;
  }
  return ineligibleReasonLabel(reason);
}

export function flagLabel(code: string): string {
  return labelFrom(FLAG_LABELS, code);
}

export function rateKindLabel(kind: string): string {
  return labelFrom(RATE_KIND_LABELS, kind);
}

export function inflationIndexLabel(index: string): string {
  return labelFrom(INFLATION_INDEX_LABELS, index);
}

export function inflationIndexShortLabel(index: string): string {
  return labelFrom(INFLATION_INDEX_SHORT_LABELS, index);
}

export const KIND_OPTIONS: Array<{ value: RankedKind; label: string }> = (
  ['PLAZO_FIJO', 'WALLET', 'FUND', 'CRYPTO'] as const
).map((value) => ({ value, label: KIND_LABELS[value] }));
