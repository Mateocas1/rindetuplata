/**
 * View types for the API payloads the pages render.
 *
 * Written out explicitly (instead of using the generated schema directly) so
 * components get non-optional fields, and checked against the committed OpenAPI
 * snapshot at compile time below, so a field rename in the API breaks the build
 * instead of rendering `undefined`.
 *
 * Note: the generated schema does not model nullability. The recorded fixtures
 * show `entity: null` for wallets and `verifiedAt: null` for automatic sources,
 * so those fields are nullable here on purpose.
 */
import type { components } from '../api/schema';

export const RANKING_KINDS = ['PLAZO_FIJO', 'WALLET', 'FUND', 'CRYPTO'] as const;

export type RankedKind = (typeof RANKING_KINDS)[number];

export interface SourceRef {
  id: string;
  displayName: string;
  official: boolean;
}

export interface ValidationFlag {
  code: string;
  severity: string;
  message: string;
}

export interface RateUsed {
  fraction: number;
  basis: string;
  kind: string;
}

export interface UpTo {
  condition: string;
  finalAmount: number;
}

export interface RankedEntry {
  id: string;
  kind: RankedKind;
  name: string;
  entity: string | null;
  source: SourceRef;
  confidence: string;
  verifiedAt: string | null;
  captureDate: string;
  dataDate: string;
  rateUsed: RateUsed;
  finalAmount: number;
  earned: number;
  nominalReturn: number;
  realReturn: number | null;
  upTo: UpTo | null;
  warnings: ValidationFlag[];
}

export interface IneligibleEntry {
  id: string;
  kind: string;
  name: string;
  source: SourceRef;
  captureDate: string;
  dataDate: string;
  reason: string;
  detail: string | null;
}

export interface ExcludedEntry {
  id: string;
  kind: string;
  name: string;
  source: SourceRef;
  captureDate: string;
  dataDate: string;
  reason: string;
  flags: ValidationFlag[];
}

export interface Inflation {
  index: string;
  twelveMonthRate: number;
  observationDate: string;
  overHorizon: number;
}

export interface RankingRequest {
  amount: number;
  days: number;
  clientOf: string[];
  kinds: string[];
  asOf: string;
  limit: number;
}

export interface RankingTotals {
  ranked: number;
  ineligible: number;
  excluded: number;
}

export interface Ranking {
  request: RankingRequest;
  inflation: Inflation | null;
  ranked: RankedEntry[];
  ineligible: IneligibleEntry[];
  excluded: ExcludedEntry[];
  totals: RankingTotals;
}

/** A bank the reader can mark as their own ("soy cliente de…"), from the plazo fijo ranking. */
export interface BankOption {
  id: string;
  name: string;
}

/** One rate snapshot of an instrument, as served by `GET /api/v1/instruments/{id}/history`. */
export interface SnapshotPoint {
  captureDate: string;
  dataDate: string;
  basis: string;
  rate: number | null;
  clientRate: number | null;
  invalid: boolean;
  flags: ValidationFlag[];
}

/** One fund cuotaparte quote; `tnaEquivalent` is null when the value fell. */
export interface FundQuotePoint {
  quoteDate: string;
  vcp: number;
  tnaEquivalent: number | null;
}

/** INDEC monthly inflation, the past index used by history (S10). */
export interface MonthlyInflationPoint {
  index: string;
  observationDate: string;
  value: number;
  confidence: string;
  sourceId: string;
}

export interface InstrumentHistory {
  id: string;
  kind: string;
  displayName: string;
  entityName: string | null;
  segment: string | null;
  sourceId: string;
  from: string;
  to: string;
  points: SnapshotPoint[];
  quotes: FundQuotePoint[];
  monthlyInflation: MonthlyInflationPoint[];
}

export interface IneligibleSimulation {
  id: string;
  name: string;
  reason: string;
  detail: string | null;
}

/** `GET /api/v1/simulations` excluded branch, as the OpenAPI snapshot types it. */
export interface ExcludedSimulation {
  id: string;
  name: string;
  reason: string;
  flags: ValidationFlag[];
}

/** `GET /api/v1/simulations`: exactly one of entry / ineligible / excluded. */
export interface Simulation {
  status: string;
  entry: RankedEntry | null;
  ineligible: IneligibleSimulation | null;
  excluded: ExcludedSimulation | null;
}

// ---------------------------------------------------------------------------
// Compile-time guard against the committed OpenAPI snapshot: every field this
// module declares must exist in the generated schema.
// ---------------------------------------------------------------------------
type Schema = components['schemas'];

type KeysAreKnown<Mine, Generated> = keyof Mine extends keyof Generated ? true : false;

/** Fails to compile (`Type 'false' does not satisfy the constraint 'true'`) on a field mismatch. */
type AssertKnown<Value extends true> = Value;

type _Ranking = AssertKnown<KeysAreKnown<Ranking, Schema['RankingResponse']>>;
type _RankedEntry = AssertKnown<KeysAreKnown<RankedEntry, Schema['RankedEntryView']>>;
type _IneligibleEntry = AssertKnown<KeysAreKnown<IneligibleEntry, Schema['IneligibleEntryView']>>;
type _ExcludedEntry = AssertKnown<KeysAreKnown<ExcludedEntry, Schema['ExcludedEntryView']>>;
type _Inflation = AssertKnown<KeysAreKnown<Inflation, Schema['InflationResponse']>>;
type _RankingRequest = AssertKnown<KeysAreKnown<RankingRequest, Schema['RankingRequestView']>>;
type _Totals = AssertKnown<KeysAreKnown<RankingTotals, Schema['TotalsView']>>;
type _UpTo = AssertKnown<KeysAreKnown<UpTo, Schema['UpToView']>>;
type _SourceRef = AssertKnown<KeysAreKnown<SourceRef, Schema['SourceView']>>;
type _RateUsed = AssertKnown<KeysAreKnown<RateUsed, Schema['RateUsedView']>>;
type _Flag = AssertKnown<KeysAreKnown<ValidationFlag, Schema['ValidationFlagView']>>;
type _History = AssertKnown<KeysAreKnown<InstrumentHistory, Schema['InstrumentHistory']>>;
type _SnapshotPoint = AssertKnown<KeysAreKnown<SnapshotPoint, Schema['SnapshotPoint']>>;
type _FundQuotePoint = AssertKnown<KeysAreKnown<FundQuotePoint, Schema['FundQuotePoint']>>;
type _MonthlyInflationPoint = AssertKnown<
  KeysAreKnown<MonthlyInflationPoint, Schema['MonthlyInflationPoint']>
>;
type _Simulation = AssertKnown<KeysAreKnown<Simulation, Schema['SimulationResponse']>>;
type _IneligibleSimulation = AssertKnown<KeysAreKnown<IneligibleSimulation, Schema['IneligibleView']>>;
type _ExcludedSimulation = AssertKnown<KeysAreKnown<ExcludedSimulation, Schema['ExcludedView']>>;

// Referenced so the guards are not flagged as unused by the checker.
export type TypeGuards = [
  _Ranking,
  _RankedEntry,
  _IneligibleEntry,
  _ExcludedEntry,
  _Inflation,
  _RankingRequest,
  _Totals,
  _UpTo,
  _SourceRef,
  _RateUsed,
  _Flag,
  _History,
  _SnapshotPoint,
  _FundQuotePoint,
  _MonthlyInflationPoint,
  _Simulation,
  _IneligibleSimulation,
  _ExcludedSimulation,
];
