import { describe, expect, it } from 'vitest';
import { ApiProblem, createApiClient } from './api';
import { createAccountApi } from './account';

/**
 * Typed account calls over the shared fetch client (T7d). Every unsafe call
 * goes through the client, so the CSRF token is fetched first and a
 * ProblemDetail body becomes a typed `ApiProblem`; this file pins the exact
 * path, method and body of each call the three pages make.
 */
type Call = { url: string; init: RequestInit };

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  });
}

function mockFetch(handler: (url: string, init: RequestInit) => Response) {
  const calls: Call[] = [];
  const fetchFn = (async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input);
    const requestInit = init ?? {};
    calls.push({ url, init: requestInit });
    return handler(url, requestInit);
  }) as unknown as typeof fetch;
  return { fetch: fetchFn, calls };
}

function api(handler: (url: string, init: RequestInit) => Response) {
  const mocked = mockFetch(handler);
  return { account: createAccountApi(createApiClient({ fetch: mocked.fetch })), ...mocked };
}

const CSRF = '/api/v1/auth/csrf';

function withCsrf(handler: (url: string, init: RequestInit) => Response) {
  return (url: string, init: RequestInit) =>
    url === CSRF ? jsonResponse({ headerName: 'X-XSRF-TOKEN', token: 'raw-token' }) : handler(url, init);
}

describe('magic-link request', () => {
  it('posts the email with a CSRF token and never reads the response body for meaning', async () => {
    const { account, calls } = api(
      withCsrf(() => jsonResponse({ status: 'accepted' }, 202)),
    );

    await expect(account.requestMagicLink('saver@example.com')).resolves.toEqual({
      status: 'accepted',
    });

    expect(calls.map((call) => call.url)).toEqual([CSRF, '/api/v1/auth/magic-link']);
    const request = calls[1];
    expect(request.init.method).toBe('POST');
    expect(new Headers(request.init.headers).get('X-XSRF-TOKEN')).toBe('raw-token');
    expect(request.init.body).toBe(JSON.stringify({ email: 'saver@example.com' }));
  });

  it('rejects with the typed problem when the email is invalid', async () => {
    const { account } = api(
      withCsrf(() =>
        jsonResponse(
          {
            title: 'Bad Request',
            status: 400,
            detail: "Email address 'x' is not valid",
            parameter: 'email',
          },
          400,
        ),
      ),
    );

    const error = await account.requestMagicLink('x').catch((reason: unknown) => reason);
    expect(error).toBeInstanceOf(ApiProblem);
    expect((error as ApiProblem).parameter).toBe('email');
  });
});

describe('current user', () => {
  it('reads /api/v1/me with the session cookie', async () => {
    const { account, calls } = api(() =>
      jsonResponse({ email: 'saver@example.com', roles: ['ROLE_USER'] }),
    );

    await expect(account.loadMe()).resolves.toEqual({
      email: 'saver@example.com',
      roles: ['ROLE_USER'],
    });
    expect(calls[0].url).toBe('/api/v1/me');
    expect(calls[0].init.credentials).toBe('include');
  });
});

describe('alerts', () => {
  const rule = {
    id: 3,
    kind: 'WALLET',
    instrumentId: 7,
    thresholdPercent: 23.5,
    active: true,
    createdAt: '2026-10-15T10:00:00Z',
  };

  it('lists the rules of the session', async () => {
    const { account, calls } = api(() => jsonResponse([rule]));
    await expect(account.listAlerts()).resolves.toEqual([rule]);
    expect(calls[0].url).toBe('/api/v1/alerts');
  });

  it('creates a rule with the percent the API expects', async () => {
    const { account, calls } = api(withCsrf(() => jsonResponse(rule, 201)));

    await expect(
      account.createAlert({ kind: 'WALLET', instrumentId: 7, thresholdPercent: 23.5 }),
    ).resolves.toEqual(rule);

    const request = calls.find((call) => call.url === '/api/v1/alerts')!;
    expect(request.init.method).toBe('POST');
    expect(request.init.body).toBe(
      JSON.stringify({ kind: 'WALLET', instrumentId: 7, thresholdPercent: 23.5 }),
    );
  });

  it('deletes a rule by id and resolves without a body', async () => {
    const { account, calls } = api(withCsrf(() => jsonResponse(undefined, 204)));

    await expect(account.deleteAlert(3)).resolves.toBeUndefined();

    const request = calls.find((call) => call.url === '/api/v1/alerts/3')!;
    expect(request.init.method).toBe('DELETE');
  });

  it('rejects with the typed problem when another user owns the rule', async () => {
    const { account } = api(
      withCsrf(() => jsonResponse({ title: 'Not Found', status: 404, detail: 'Alert rule 3 is unknown' }, 404)),
    );
    const error = await account.deleteAlert(3).catch((reason: unknown) => reason);
    expect(error).toBeInstanceOf(ApiProblem);
    expect((error as ApiProblem).status).toBe(404);
  });
});

describe('logout', () => {
  it('posts to the logout endpoint with the CSRF header and forgets the token afterwards', async () => {
    let issued = 0;
    const { account, calls } = api((url) => {
      if (url === CSRF) {
        issued += 1;
        return jsonResponse({ headerName: 'X-XSRF-TOKEN', token: `token-${issued}` });
      }
      return jsonResponse(undefined, 204);
    });

    await expect(account.logout()).resolves.toBeUndefined();
    await account.deleteAlert(1);

    const logout = calls.find((call) => call.url === '/api/v1/auth/logout')!;
    expect(logout.init.method).toBe('POST');
    expect(logout.init.body).toBeUndefined();
    expect(new Headers(logout.init.headers).get('X-XSRF-TOKEN')).toBe('token-1');

    // The session is gone, so the next unsafe call must fetch a fresh token.
    expect(calls.filter((call) => call.url === CSRF)).toHaveLength(2);
    const remove = calls.find((call) => call.url === '/api/v1/alerts/1')!;
    expect(new Headers(remove.init.headers).get('X-XSRF-TOKEN')).toBe('token-2');
  });

  it('keeps the cached token when the logout fails', async () => {
    const { account, calls } = api((url) => {
      if (url === CSRF) {
        return jsonResponse({ headerName: 'X-XSRF-TOKEN', token: 'token-1' });
      }
      if (url === '/api/v1/auth/logout') {
        return jsonResponse({ title: 'Internal Server Error', status: 500 }, 500);
      }
      return jsonResponse(undefined, 204);
    });

    await expect(account.logout()).rejects.toBeInstanceOf(ApiProblem);
    await account.deleteAlert(1);

    expect(calls.filter((call) => call.url === CSRF)).toHaveLength(1);
  });
});

describe('instruments', () => {
  it('reads the public instrument list', async () => {
    const { account, calls } = api(() => jsonResponse([{ id: '7', displayName: 'Naranja X' }]));
    await expect(account.listInstruments()).resolves.toEqual([{ id: '7', displayName: 'Naranja X' }]);
    expect(calls[0].url).toBe('/api/v1/instruments');
  });
});

describe('curated wallet rates', () => {
  const wallet = {
    walletId: 'naranja-x',
    name: 'Naranja X',
    tiers: [{ floor: null, ceiling: 1_000_000, tnaPercent: 20 }],
    conditions: [],
    note: null,
    verifiedAt: '2026-10-15',
    daysSinceVerified: 0,
    updatedAt: '2026-10-15T10:00:00Z',
    updatedBy: 'admin@example.com',
  };
  const body = {
    name: 'Naranja X',
    tiers: [{ floor: null, ceiling: 1_000_000, tnaPercent: 20 }],
    conditions: [],
    note: null,
  };

  it('lists the curated rates', async () => {
    const { account, calls } = api(() => jsonResponse([wallet]));
    await expect(account.listWalletRates()).resolves.toEqual([wallet]);
    expect(calls[0].url).toBe('/api/v1/admin/wallet-rates');
  });

  it('saves one rate with a percent document', async () => {
    const { account, calls } = api(withCsrf(() => jsonResponse(wallet)));
    await account.saveWalletRate('naranja-x', body);
    const request = calls.find((call) => call.url === '/api/v1/admin/wallet-rates/naranja-x')!;
    expect(request.init.method).toBe('PUT');
    expect(request.init.body).toBe(JSON.stringify(body));
  });

  it('re-verifies one rate without changing its tiers', async () => {
    const { account, calls } = api(withCsrf(() => jsonResponse(wallet)));
    await account.confirmWalletRate('naranja-x');
    const request = calls.find(
      (call) => call.url === '/api/v1/admin/wallet-rates/naranja-x/confirm',
    )!;
    expect(request.init.method).toBe('POST');
    expect(request.init.body).toBeUndefined();
  });

  it('reads the audit trail of one wallet', async () => {
    const entry = {
      id: 1,
      walletId: 'naranja-x',
      action: 'CREATED',
      payload: { name: 'Naranja X' },
      actor: 'admin@example.com',
      at: '2026-10-15T10:00:00Z',
    };
    const { account, calls } = api(() => jsonResponse([entry]));
    await expect(account.loadWalletAudit('naranja-x')).resolves.toEqual([entry]);
    expect(calls[0].url).toBe('/api/v1/admin/wallet-rates/naranja-x/audit');
  });

  it('surfaces a 403 as the typed no-permission problem', async () => {
    const { account } = api(() =>
      jsonResponse({ title: 'Forbidden', status: 403, detail: 'Access Denied' }, 403),
    );
    const error = await account.listWalletRates().catch((reason: unknown) => reason);
    expect(error).toBeInstanceOf(ApiProblem);
    expect((error as ApiProblem).status).toBe(403);
  });
});
