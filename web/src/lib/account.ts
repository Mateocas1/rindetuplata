/**
 * Typed account calls for the three account pages (T7d).
 *
 * They all go through the shared client in `api.ts`, so the session cookie,
 * the CSRF token before unsafe methods and the ProblemDetail -> `ApiProblem`
 * mapping are handled once. This module only names the paths, shapes and verbs
 * the OpenAPI snapshot documents, checked against the generated schema at the
 * bottom of the file.
 */
import type { components } from '../api/schema';
import { api, type ApiClient, type CurrentUser } from './api';
import type { ValidationFlag } from './types';

export interface MagicLinkResponse {
  status: string;
}

export interface AlertRule {
  id: number;
  kind: string | null;
  instrumentId: number | null;
  thresholdPercent: number;
  active: boolean;
  createdAt: string;
}

export interface AlertInput {
  kind: string | null;
  instrumentId: number | null;
  thresholdPercent: number;
}

export interface InstrumentSummary {
  id: string;
  kind: string;
  displayName: string;
  entityName: string | null;
  segment: string | null;
  sourceId: string;
  rate: number | null;
  basis: string;
  clientRate: number | null;
  captureDate: string;
  dataDate: string;
  confidence: string;
  verifiedAt: string | null;
  invalid: boolean;
  flags: ValidationFlag[];
  quoteDate: string | null;
  vcp: number | null;
}

export interface WalletTier {
  floor: number | null;
  ceiling: number | null;
  tnaPercent: number;
}

export interface WalletCondition {
  condition: string;
  tiers: WalletTier[];
}

export interface WalletRate {
  walletId: string;
  name: string;
  tiers: WalletTier[];
  conditions: WalletCondition[];
  note: string | null;
  verifiedAt: string;
  daysSinceVerified: number;
  updatedAt: string;
  updatedBy: string;
}

export interface WalletRateInput {
  name: string;
  tiers: WalletTier[];
  conditions: WalletCondition[];
  note: string | null;
}

export interface AuditEntry {
  id: number;
  walletId: string;
  action: string;
  payload: unknown;
  actor: string;
  at: string;
}

export interface AccountApi {
  requestMagicLink(email: string): Promise<MagicLinkResponse>;
  login(token: string): Promise<CurrentUser>;
  /** Ends the session; on success the cached CSRF token is dropped with it. */
  logout(): Promise<void>;
  loadMe(): Promise<CurrentUser>;
  listAlerts(): Promise<AlertRule[]>;
  createAlert(input: AlertInput): Promise<AlertRule>;
  deleteAlert(id: number): Promise<void>;
  listInstruments(): Promise<InstrumentSummary[]>;
  listWalletRates(): Promise<WalletRate[]>;
  saveWalletRate(walletId: string, input: WalletRateInput): Promise<WalletRate>;
  confirmWalletRate(walletId: string): Promise<WalletRate>;
  loadWalletAudit(walletId: string): Promise<AuditEntry[]>;
}

export function createAccountApi(client: ApiClient = api): AccountApi {
  return {
    requestMagicLink(email: string): Promise<MagicLinkResponse> {
      return client.post<MagicLinkResponse>('/api/v1/auth/magic-link', { email });
    },
    login(token: string): Promise<CurrentUser> {
      return client.login(token);
    },
    async logout(): Promise<void> {
      await client.post<void>('/api/v1/auth/logout');
      // The session that owned the token is gone, so the next unsafe request
      // must fetch a fresh one instead of replaying a token for a dead session.
      client.forgetCsrfToken();
    },
    loadMe(): Promise<CurrentUser> {
      return client.get<CurrentUser>('/api/v1/me');
    },
    listAlerts(): Promise<AlertRule[]> {
      return client.get<AlertRule[]>('/api/v1/alerts');
    },
    createAlert(input: AlertInput): Promise<AlertRule> {
      return client.post<AlertRule>('/api/v1/alerts', input);
    },
    deleteAlert(id: number): Promise<void> {
      return client.delete(`/api/v1/alerts/${id}`);
    },
    listInstruments(): Promise<InstrumentSummary[]> {
      return client.get<InstrumentSummary[]>('/api/v1/instruments');
    },
    listWalletRates(): Promise<WalletRate[]> {
      return client.get<WalletRate[]>('/api/v1/admin/wallet-rates');
    },
    saveWalletRate(walletId: string, input: WalletRateInput): Promise<WalletRate> {
      return client.put<WalletRate>(
        `/api/v1/admin/wallet-rates/${encodeURIComponent(walletId)}`,
        input,
      );
    },
    confirmWalletRate(walletId: string): Promise<WalletRate> {
      return client.post<WalletRate>(
        `/api/v1/admin/wallet-rates/${encodeURIComponent(walletId)}/confirm`,
      );
    },
    loadWalletAudit(walletId: string): Promise<AuditEntry[]> {
      return client.get<AuditEntry[]>(
        `/api/v1/admin/wallet-rates/${encodeURIComponent(walletId)}/audit`,
      );
    },
  };
}

/** Client for the deployed site, on the shared fetch client. */
export const accountApi: AccountApi = createAccountApi();

// ---------------------------------------------------------------------------
// Compile-time guard against the committed OpenAPI snapshot (same idea as
// types.ts): a field rename in the API breaks the build instead of rendering
// `undefined`.
// ---------------------------------------------------------------------------
type Schema = components['schemas'];

type KeysAreKnown<Mine, Generated> = keyof Mine extends keyof Generated ? true : false;

type AssertKnown<Value extends true> = Value;

type _MagicLink = AssertKnown<KeysAreKnown<MagicLinkResponse, Schema['MagicLinkResponse']>>;
type _AlertRule = AssertKnown<KeysAreKnown<AlertRule, Schema['AlertResponse']>>;
type _AlertInput = AssertKnown<KeysAreKnown<AlertInput, Schema['AlertRequest']>>;
type _Instrument = AssertKnown<KeysAreKnown<InstrumentSummary, Schema['InstrumentSummary']>>;
type _WalletTier = AssertKnown<
  KeysAreKnown<WalletTier, Schema['TierView']> & KeysAreKnown<WalletTier, Schema['TierRequest']>
>;
type _WalletCondition = AssertKnown<
  KeysAreKnown<WalletCondition, Schema['ConditionView']> &
    KeysAreKnown<WalletCondition, Schema['ConditionRequest']>
>;
type _WalletRate = AssertKnown<KeysAreKnown<WalletRate, Schema['WalletRateView']>>;
type _WalletRateInput = AssertKnown<KeysAreKnown<WalletRateInput, Schema['WalletRateRequest']>>;
type _AuditEntry = AssertKnown<KeysAreKnown<AuditEntry, Schema['AuditEntryView']>>;

export type AccountTypeGuards = [
  _MagicLink,
  _AlertRule,
  _AlertInput,
  _Instrument,
  _WalletTier,
  _WalletCondition,
  _WalletRate,
  _WalletRateInput,
  _AuditEntry,
];
