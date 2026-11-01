import { describe, expect, it } from 'vitest';
import { ApiProblem, createApiClient } from './api';
import {
  MAX_ACTIVE_KEYS,
  MAX_NAME_LENGTH,
  canCreateKey,
  createApiKeysApi,
  keyLabel,
  keyRequestBody,
  keySlotsText,
  listedKey,
  planLabel,
  validateKeyName,
  type ApiKey,
} from './apiKeys';

/**
 * Typed API-key calls and the copy around them (T6d, S6, L32 b). Every unsafe call goes through the shared client,
 * so the CSRF token is fetched first and a ProblemDetail becomes a typed `ApiProblem`; this file pins the exact path,
 * method and body of the three calls the `/claves-api/` page makes.
 */
type Call = { url: string; init: RequestInit };

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  });
}

function api(handler: (url: string, init: RequestInit) => Response) {
  const calls: Call[] = [];
  const fetchFn = (async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input);
    calls.push({ url, init: init ?? {} });
    return url === '/api/v1/auth/csrf'
      ? jsonResponse({ headerName: 'X-XSRF-TOKEN', token: 'raw-token' })
      : handler(url, init ?? {});
  }) as unknown as typeof fetch;
  return { keys: createApiKeysApi(createApiClient({ fetch: fetchFn })), calls };
}

const KEY: ApiKey = {
  id: 7,
  name: 'mi script',
  prefix: 'rtp_AbCdEf',
  plan: 'FREE',
  createdAt: '2026-10-20T12:00:00Z',
};

describe('the three calls', () => {
  it('lists the active keys with a plain GET', async () => {
    const { keys, calls } = api(() => jsonResponse([KEY]));

    await expect(keys.list()).resolves.toEqual([KEY]);

    expect(calls.map((call) => call.url)).toEqual(['/api/v1/account/api-keys']);
    expect(calls[0].init.method).toBe('GET');
    expect(calls[0].init.credentials).toBe('include');
  });

  it('creates with a CSRF token and returns the key the response shows once', async () => {
    const created = { ...KEY, key: `rtp_${'A'.repeat(43)}` };
    const { keys, calls } = api(() => jsonResponse(created, 201));

    await expect(keys.create('mi script')).resolves.toEqual(created);

    expect(calls.map((call) => call.url)).toEqual(['/api/v1/auth/csrf', '/api/v1/account/api-keys']);
    expect(calls[1].init.method).toBe('POST');
    expect(new Headers(calls[1].init.headers).get('X-XSRF-TOKEN')).toBe('raw-token');
    expect(calls[1].init.body).toBe(JSON.stringify({ name: 'mi script' }));
  });

  it('sends an empty body when the label is blank', async () => {
    const { keys, calls } = api(() => jsonResponse({ ...KEY, key: 'rtp_x' }, 201));

    await keys.create('   ');

    expect(calls[1].init.body).toBe(JSON.stringify({}));
  });

  it('revokes with a CSRF token on DELETE', async () => {
    const { keys, calls } = api(() => new Response(null, { status: 204 }));

    await expect(keys.revoke(7)).resolves.toBeUndefined();

    expect(calls.map((call) => call.url)).toEqual(['/api/v1/auth/csrf', '/api/v1/account/api-keys/7']);
    expect(calls[1].init.method).toBe('DELETE');
    expect(new Headers(calls[1].init.headers).get('X-XSRF-TOKEN')).toBe('raw-token');
  });

  it('turns the cap refusal into a typed problem', async () => {
    const { keys } = api(() =>
      jsonResponse({ title: 'Bad Request', status: 400, detail: 'At most 3 active API keys per user' }, 400),
    );

    const error = await keys.create('x').catch((reason: unknown) => reason);

    expect(error).toBeInstanceOf(ApiProblem);
    expect((error as ApiProblem).status).toBe(400);
  });
});

describe('the label of a key', () => {
  it('is trimmed, optional and at most 60 characters', () => {
    expect(MAX_NAME_LENGTH).toBe(60);
    expect(validateKeyName('')).toBeNull();
    expect(validateKeyName('  mi script  ')).toBeNull();
    expect(validateKeyName('x'.repeat(60))).toBeNull();
    expect(validateKeyName('x'.repeat(61))).toBe('El nombre puede tener hasta 60 caracteres.');
  });

  it('counts the characters after trimming, like the API', () => {
    expect(validateKeyName(` ${'x'.repeat(60)} `)).toBeNull();
  });

  it('builds the request body from the trimmed label or none', () => {
    expect(keyRequestBody('  mi script ')).toEqual({ name: 'mi script' });
    expect(keyRequestBody('   ')).toEqual({});
  });
});

describe('copy for the list', () => {
  it('names a key by its label or says it has none', () => {
    expect(keyLabel(KEY)).toBe('mi script');
    expect(keyLabel({ ...KEY, name: null })).toBe('Sin nombre');
    expect(keyLabel({ ...KEY, name: '' })).toBe('Sin nombre');
  });

  it('names the plans in Spanish and keeps an unknown one as it came', () => {
    expect(planLabel('FREE')).toBe('Gratis');
    expect(planLabel('PARTNER')).toBe('Partner');
    expect(planLabel('GOLD')).toBe('GOLD');
  });

  it('allows three active keys and says how many slots are used', () => {
    expect(MAX_ACTIVE_KEYS).toBe(3);
    expect(canCreateKey(0)).toBe(true);
    expect(canCreateKey(2)).toBe(true);
    expect(canCreateKey(3)).toBe(false);
    expect(keySlotsText(0)).toBe('Todavía no tenés claves activas (hasta 3).');
    expect(keySlotsText(1)).toBe('1 de 3 claves activas.');
    expect(keySlotsText(3)).toBe('3 de 3 claves activas: revocá una para crear otra.');
  });
});

describe('the row of a new key', () => {
  it('never carries the secret into the list', () => {
    const row = listedKey({ ...KEY, key: `rtp_${'A'.repeat(43)}` });

    expect(row).toEqual(KEY);
    expect(JSON.stringify(row)).not.toContain('rtp_AAAA');
    expect('key' in row).toBe(false);
  });
});
