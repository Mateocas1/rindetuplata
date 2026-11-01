/**
 * Typed API-key calls and copy for `/claves-api/` (T6d, S6, L32 b).
 *
 * They go through the shared client in `api.ts`, so the session cookie, the CSRF token before unsafe methods and the
 * ProblemDetail -> `ApiProblem` mapping are handled once. The shapes follow `ApiKeyResponse` and
 * `CreatedApiKeyResponse` of the API, checked against the generated schema at the bottom of the file.
 */
import type { components } from '../api/schema';
import { api, type ApiClient } from './api';

/** The API's cap of active keys per user (`ApiKeyService.MAX_ACTIVE_KEYS`). */
export const MAX_ACTIVE_KEYS = 3;

/** The API's bound of a key label, counted after trimming (`ApiKeyService.MAX_NAME_LENGTH`). */
export const MAX_NAME_LENGTH = 60;

const KEYS_PATH = '/api/v1/account/api-keys';

export interface ApiKey {
  id: number;
  name: string | null;
  /** The first ten characters of the key: all the list can show. */
  prefix: string;
  plan: string;
  createdAt: string;
}

/** The answer of the one request that shows the key itself. */
export interface CreatedApiKey extends ApiKey {
  key: string;
}

export interface ApiKeysApi {
  list(): Promise<ApiKey[]>;
  create(name: string): Promise<CreatedApiKey>;
  revoke(id: number): Promise<void>;
}

/** The list row of a freshly created key: everything but the secret, which only the notice may hold. */
export function listedKey(created: CreatedApiKey): ApiKey {
  return {
    id: created.id,
    name: created.name,
    prefix: created.prefix,
    plan: created.plan,
    createdAt: created.createdAt,
  };
}

/** The body of a create: the trimmed label, or none when it is blank. */
export function keyRequestBody(name: string): { name?: string } {
  const trimmed = name.trim();
  return trimmed === '' ? {} : { name: trimmed };
}

export function createApiKeysApi(client: ApiClient = api): ApiKeysApi {
  return {
    list(): Promise<ApiKey[]> {
      return client.get<ApiKey[]>(KEYS_PATH);
    },
    create(name: string): Promise<CreatedApiKey> {
      return client.post<CreatedApiKey>(KEYS_PATH, keyRequestBody(name));
    },
    revoke(id: number): Promise<void> {
      return client.delete(`${KEYS_PATH}/${id}`);
    },
  };
}

/** Client for the deployed site, on the shared fetch client. */
export const apiKeysApi: ApiKeysApi = createApiKeysApi();

// Compile-time guard against the committed OpenAPI snapshot (same idea as account.ts): a field rename in the API
// breaks the build instead of rendering `undefined`.
type Schema = components['schemas'];

type KeysAreKnown<Mine, Generated> = keyof Mine extends keyof Generated ? true : false;

type AssertKnown<Value extends true> = Value;

type _ApiKey = AssertKnown<KeysAreKnown<ApiKey, Schema['ApiKeyResponse']>>;
type _CreatedApiKey = AssertKnown<KeysAreKnown<CreatedApiKey, Schema['CreatedApiKeyResponse']>>;
type _KeyRequest = AssertKnown<
  KeysAreKnown<ReturnType<typeof keyRequestBody>, Schema['CreateApiKeyRequest']>
>;

export type ApiKeyTypeGuards = [_ApiKey, _CreatedApiKey, _KeyRequest];

/** A message for a label the API would refuse, or `null` when it is fine. */
export function validateKeyName(name: string): string | null {
  return name.trim().length > MAX_NAME_LENGTH
    ? `El nombre puede tener hasta ${MAX_NAME_LENGTH} caracteres.`
    : null;
}

export function keyLabel(key: ApiKey): string {
  return key.name === null || key.name === '' ? 'Sin nombre' : key.name;
}

const PLAN_LABELS: Record<string, string> = {
  FREE: 'Gratis',
  PARTNER: 'Partner',
};

export function planLabel(plan: string): string {
  return PLAN_LABELS[plan] ?? plan;
}

export function canCreateKey(activeCount: number): boolean {
  return activeCount < MAX_ACTIVE_KEYS;
}

/** How many of the three slots are used, said out loud next to the form. */
export function keySlotsText(activeCount: number): string {
  if (activeCount === 0) return `Todavía no tenés claves activas (hasta ${MAX_ACTIVE_KEYS}).`;
  if (!canCreateKey(activeCount)) {
    return `${activeCount} de ${MAX_ACTIVE_KEYS} claves activas: revocá una para crear otra.`;
  }
  return `${activeCount} de ${MAX_ACTIVE_KEYS} claves activas.`;
}
