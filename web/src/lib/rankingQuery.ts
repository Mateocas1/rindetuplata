/**
 * Query-string state for the home ranking (T7b).
 *
 * Every value is validated on read, so a hand-edited URL can only ever ask the
 * API for something valid: an unusable amount, term, kind or client id falls
 * back to its default instead of producing a 400.
 */
import { RANKING_KINDS, type RankedKind } from './types';

export interface RankingQueryState {
  amount: number;
  days: number;
  kinds: RankedKind[];
  clientOf: string[];
}

export const DEFAULT_AMOUNT = 1_000_000;
export const DEFAULT_DAYS = 30;
export const MIN_DAYS = 1;
export const MAX_DAYS = 3650;
export const MAX_AMOUNT = 1_000_000_000_000;

export const DEFAULT_RANKING_QUERY: RankingQueryState = {
  amount: DEFAULT_AMOUNT,
  days: DEFAULT_DAYS,
  kinds: [],
  clientOf: [],
};

const AMOUNT_PATTERN = /^\d+(?:\.\d+)?$/;
const INTEGER_PATTERN = /^\d+$/;
const DIGITS_PATTERN = /^\d+$/;

/** The amount a reader typed, or null when the API would reject it. */
export function tryParseAmount(raw: string): number | null {
  const value = raw.trim();
  if (!AMOUNT_PATTERN.test(value)) return null;
  const parsed = Number(value);
  if (!Number.isFinite(parsed) || parsed <= 0 || parsed > MAX_AMOUNT) return null;
  return parsed;
}

/** The whole number of days a reader typed, or null when it is out of range. */
export function tryParseDays(raw: string): number | null {
  const value = raw.trim();
  if (!INTEGER_PATTERN.test(value)) return null;
  const parsed = Number(value);
  if (!Number.isInteger(parsed) || parsed < MIN_DAYS || parsed > MAX_DAYS) return null;
  return parsed;
}

function parseAmount(raw: string | null): number {
  if (raw === null) return DEFAULT_AMOUNT;
  return tryParseAmount(raw) ?? DEFAULT_AMOUNT;
}

function parseDays(raw: string | null): number {
  if (raw === null) return DEFAULT_DAYS;
  return tryParseDays(raw) ?? DEFAULT_DAYS;
}

/** Canonical order, so the same selection always serializes to the same URL. */
function canonicalKinds(kinds: readonly string[]): RankedKind[] {
  const wanted = new Set(kinds.map((kind) => kind.toUpperCase()));
  return RANKING_KINDS.filter((kind) => wanted.has(kind));
}

function parseKinds(raw: string | null): RankedKind[] {
  if (raw === null || raw.trim() === '') return [];
  return canonicalKinds(raw.split(','));
}

function sortClients(ids: readonly string[]): string[] {
  const unique = new Set(ids.map((id) => id.trim()).filter((id) => DIGITS_PATTERN.test(id)));
  return [...unique].sort((a, b) => Number(a) - Number(b));
}

function parseClients(raw: string | null): string[] {
  if (raw === null) return [];
  return sortClients(raw.split(','));
}

export function parseRankingQuery(search: string): RankingQueryState {
  const params = new URLSearchParams(search.startsWith('?') ? search.slice(1) : search);
  return {
    amount: parseAmount(params.get('monto')),
    days: parseDays(params.get('dias')),
    kinds: parseKinds(params.get('tipo')),
    clientOf: parseClients(params.get('cliente')),
  };
}

/** Only the values that differ from the default reach the URL. */
export function serializeRankingQuery(state: RankingQueryState): string {
  const params = new URLSearchParams();
  if (state.amount !== DEFAULT_AMOUNT) params.set('monto', String(state.amount));
  if (state.days !== DEFAULT_DAYS) params.set('dias', String(state.days));
  const kinds = canonicalKinds(state.kinds);
  if (kinds.length > 0) params.set('tipo', kinds.join(','));
  const clients = sortClients(state.clientOf);
  if (clients.length > 0) params.set('cliente', clients.join(','));
  const query = params.toString();
  return query === '' ? '' : `?${query}`;
}

/** API parameters for `GET /api/v1/ranking`. */
export function toApiParams(state: RankingQueryState): URLSearchParams {
  const params = new URLSearchParams();
  params.set('amount', String(state.amount));
  params.set('days', String(state.days));
  for (const kind of canonicalKinds(state.kinds)) params.append('kind', kind);
  for (const id of sortClients(state.clientOf)) params.append('clientOf', id);
  return params;
}
