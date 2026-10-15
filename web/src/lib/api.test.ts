import { describe, expect, it } from 'vitest';
import { ApiProblem, createApiClient, type ApiClientOptions } from './api';
import { DEFAULT_RANKING_QUERY } from './rankingQuery';

/**
 * The browser client (T7b/L48): it always talks to the API on the same origin
 * (or `PUBLIC_API_URL`), sends the session cookie with `credentials: 'include'`,
 * turns a ProblemDetail body into a typed `ApiProblem`, and fetches the CSRF
 * token from `GET /api/v1/auth/csrf` before any unsafe method.
 */
type Call = { url: string; init: RequestInit };

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  });
}

function mockFetch(handler: (url: string, init: RequestInit) => Response | Promise<Response>) {
  const calls: Call[] = [];
  const fn = (async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input);
    const requestInit = init ?? {};
    calls.push({ url, init: requestInit });
    return handler(url, requestInit);
  }) as unknown as typeof fetch;
  return { fetch: fn, calls };
}

function client(options: { fetch: typeof fetch; baseUrl?: string }) {
  return createApiClient({ ...options } satisfies ApiClientOptions);
}

describe('GET requests', () => {
  it('uses the same origin by default and always sends cookies', async () => {
    const { fetch, calls } = mockFetch(() => jsonResponse({ ok: true }));
    await client({ fetch }).get('/api/v1/ranking');
    expect(calls[0].url).toBe('/api/v1/ranking');
    expect(calls[0].init.credentials).toBe('include');
    expect(calls[0].init.method).toBe('GET');
  });

  it('prefixes an absolute API base when one is configured', async () => {
    const { fetch, calls } = mockFetch(() => jsonResponse({ ok: true }));
    await client({ fetch, baseUrl: 'https://api.rindetuplata.test' }).get('/api/v1/ranking');
    expect(calls[0].url).toBe('https://api.rindetuplata.test/api/v1/ranking');
  });

  it('appends query parameters', async () => {
    const { fetch, calls } = mockFetch(() => jsonResponse({ ok: true }));
    await client({ fetch }).get(
      '/api/v1/ranking',
      new URLSearchParams({ amount: '500000', days: '60' }),
    );
    expect(calls[0].url).toBe('/api/v1/ranking?amount=500000&days=60');
  });

  it('returns the parsed JSON body', async () => {
    const { fetch } = mockFetch(() => jsonResponse({ status: 'SIMULATED' }));
    await expect(client({ fetch }).get('/api/v1/simulations')).resolves.toEqual({
      status: 'SIMULATED',
    });
  });
});

describe('getRanking', () => {
  it('asks for the personalized ranking with the state parameters', async () => {
    const { fetch, calls } = mockFetch(() => jsonResponse({ ranked: [] }));
    await client({ fetch }).getRanking({ ...DEFAULT_RANKING_QUERY, amount: 500000, days: 60 });
    const url = new URL(calls[0].url, 'http://localhost');
    expect(url.pathname).toBe('/api/v1/ranking');
    expect(url.searchParams.get('amount')).toBe('500000');
    expect(url.searchParams.get('days')).toBe('60');
    expect(url.searchParams.get('limit')).toBe('50');
  });

  it('repeats the kind and client filters', async () => {
    const { fetch, calls } = mockFetch(() => jsonResponse({ ranked: [] }));
    await client({ fetch }).getRanking({ amount: 1, days: 1, kinds: ['WALLET'], clientOf: ['29'] });
    const url = new URL(calls[0].url, 'http://localhost');
    expect(url.searchParams.getAll('kind')).toEqual(['WALLET']);
    expect(url.searchParams.getAll('clientOf')).toEqual(['29']);
  });
});

describe('ProblemDetail mapping', () => {
  it('rejects with a typed error carrying the offending parameter', async () => {
    const { fetch } = mockFetch(() =>
      jsonResponse(
        {
          type: 'about:blank',
          title: 'Bad Request',
          status: 400,
          detail: "Parameter 'amount' must be greater than 0: -1",
          instance: '/api/v1/ranking',
          parameter: 'amount',
        },
        400,
      ),
    );

    const error = await client({ fetch })
      .get('/api/v1/ranking')
      .catch((reason: unknown) => reason);

    expect(error).toBeInstanceOf(ApiProblem);
    const problem = error as ApiProblem;
    expect(problem.status).toBe(400);
    expect(problem.title).toBe('Bad Request');
    expect(problem.detail).toBe("Parameter 'amount' must be greater than 0: -1");
    expect(problem.parameter).toBe('amount');
    expect(problem.message).toBe("Parameter 'amount' must be greater than 0: -1");
  });

  it('still rejects with a typed error when the body is not a ProblemDetail', async () => {
    const { fetch } = mockFetch(() => new Response('<html>500</html>', { status: 500 }));
    const error = await client({ fetch })
      .get('/api/v1/ranking')
      .catch((reason: unknown) => reason);
    expect(error).toBeInstanceOf(ApiProblem);
    expect((error as ApiProblem).status).toBe(500);
    expect((error as ApiProblem).parameter).toBeNull();
  });

  it('reports an unreadable success body as a typed error too', async () => {
    const { fetch } = mockFetch(
      () => new Response('not json', { status: 200, headers: { 'content-type': 'text/plain' } }),
    );
    const error = await client({ fetch })
      .get('/api/v1/ranking')
      .catch((reason: unknown) => reason);
    expect(error).toBeInstanceOf(ApiProblem);
    expect((error as ApiProblem).message).toContain('respuesta');
  });

  it('reports a network failure in Spanish without leaking the browser error', async () => {
    const { fetch } = mockFetch(() => {
      throw new TypeError('Failed to fetch');
    });
    const error = await client({ fetch })
      .get('/api/v1/ranking')
      .catch((reason: unknown) => reason);
    expect(error).toBeInstanceOf(ApiProblem);
    expect((error as ApiProblem).status).toBe(0);
    expect((error as ApiProblem).message).toBe(
      'No se pudo conectar con el servidor. Revisá tu conexión y volvé a intentar.',
    );
  });
});

describe('login', () => {
  it('posts the magic-link token and returns the current user', async () => {
    const { fetch, calls } = mockFetch((url) =>
      url === '/api/v1/auth/csrf'
        ? jsonResponse({ headerName: 'X-XSRF-TOKEN', token: 'token-1' })
        : jsonResponse({ email: 'saver@example.com', roles: [] }),
    );

    const me = await client({ fetch }).login('magic-token');

    expect(me.email).toBe('saver@example.com');
    const login = calls.find((call) => call.url === '/api/v1/auth/login')!;
    expect(login.init.method).toBe('POST');
    expect(login.init.body).toBe(JSON.stringify({ token: 'magic-token' }));
  });

  it('refetches the CSRF token afterwards, because a successful login rotates it', async () => {
    let issued = 0;
    const { fetch, calls } = mockFetch((url) => {
      if (url === '/api/v1/auth/csrf') {
        issued += 1;
        return jsonResponse({ headerName: 'X-XSRF-TOKEN', token: `token-${issued}` });
      }
      if (url === '/api/v1/auth/login') {
        return jsonResponse({ email: 'saver@example.com', roles: ['ROLE_USER'] });
      }
      return jsonResponse({});
    });

    const api = client({ fetch });
    await api.login('magic-token');
    await api.post('/api/v1/alerts', { thresholdPercent: 20 });

    const token = (url: string) => {
      const call = calls.find((entry) => entry.url === url)!;
      return new Headers(call.init.headers).get('X-XSRF-TOKEN');
    };
    expect(calls.filter((call) => call.url === '/api/v1/auth/csrf')).toHaveLength(2);
    expect(token('/api/v1/auth/login')).toBe('token-1');
    expect(token('/api/v1/alerts')).toBe('token-2');
  });

  it('keeps the cached token when the login fails', async () => {
    const { fetch, calls } = mockFetch((url) => {
      if (url === '/api/v1/auth/csrf') {
        return jsonResponse({ headerName: 'X-XSRF-TOKEN', token: 'token-1' });
      }
      if (url === '/api/v1/auth/login') {
        return jsonResponse(
          { title: 'Unauthorized', status: 401, detail: 'Invalid token' },
          401,
        );
      }
      return jsonResponse({});
    });

    const api = client({ fetch });
    await expect(api.login('expired')).rejects.toBeInstanceOf(ApiProblem);
    await api.delete('/api/v1/alerts/1');

    expect(calls.filter((call) => call.url === '/api/v1/auth/csrf')).toHaveLength(1);
  });
});

describe('unsafe methods', () => {
  it('fetches the CSRF token first and sends it under the header the API names', async () => {
    const { fetch, calls } = mockFetch((url) => {
      if (url === '/api/v1/auth/csrf') {
        return jsonResponse({ headerName: 'X-XSRF-TOKEN', token: 'raw-token' });
      }
      return jsonResponse({ id: 1 }, 201);
    });

    await client({ fetch }).post('/api/v1/alerts', { thresholdPercent: 20 });

    expect(calls.map((call) => call.url)).toEqual([
      '/api/v1/auth/csrf',
      '/api/v1/alerts',
    ]);
    expect(calls[0].init.credentials).toBe('include');
    expect(calls[1].init.method).toBe('POST');
    const headers = new Headers(calls[1].init.headers);
    expect(headers.get('X-XSRF-TOKEN')).toBe('raw-token');
    expect(headers.get('content-type')).toBe('application/json');
    expect(calls[1].init.body).toBe(JSON.stringify({ thresholdPercent: 20 }));
    expect(calls[1].init.credentials).toBe('include');
  });

  it('reuses the cached token across unsafe calls', async () => {
    const { fetch, calls } = mockFetch((url) =>
      url === '/api/v1/auth/csrf'
        ? jsonResponse({ headerName: 'X-XSRF-TOKEN', token: 'raw-token' })
        : jsonResponse({}),
    );

    const api = client({ fetch });
    await api.delete('/api/v1/alerts/1');
    await api.put('/api/v1/alerts/1', { thresholdPercent: 25 });

    expect(calls.filter((call) => call.url === '/api/v1/auth/csrf')).toHaveLength(1);
    expect(calls.map((call) => call.init.method)).toEqual(['GET', 'DELETE', 'PUT']);
  });

  it('fetches a fresh token after forgetCsrfToken (login rotates it)', async () => {
    let issued = 0;
    const { fetch, calls } = mockFetch((url) => {
      if (url === '/api/v1/auth/csrf') {
        issued += 1;
        return jsonResponse({ headerName: 'X-XSRF-TOKEN', token: `token-${issued}` });
      }
      return jsonResponse({});
    });

    const api = client({ fetch });
    await api.post('/api/v1/alerts');
    api.forgetCsrfToken();
    await api.post('/api/v1/alerts');

    expect(calls.filter((call) => call.url === '/api/v1/auth/csrf')).toHaveLength(2);
    const tokens = calls
      .filter((call) => call.init.method === 'POST')
      .map((call) => new Headers(call.init.headers).get('X-XSRF-TOKEN'));
    expect(tokens).toEqual(['token-1', 'token-2']);
  });

  it('rejects with a typed error when the CSRF endpoint itself fails', async () => {
    const { fetch, calls } = mockFetch(() =>
      jsonResponse({ title: 'Forbidden', status: 403, detail: 'Access denied' }, 403),
    );
    const error = await client({ fetch })
      .post('/api/v1/alerts')
      .catch((reason: unknown) => reason);
    expect(error).toBeInstanceOf(ApiProblem);
    expect(calls).toHaveLength(1);
  });

  it('rejects when the CSRF endpoint answers without the pair the client needs', async () => {
    const { fetch } = mockFetch(() => jsonResponse({ token: 'raw-token' }));
    const error = await client({ fetch })
      .post('/api/v1/alerts')
      .catch((reason: unknown) => reason);
    expect(error).toBeInstanceOf(ApiProblem);
  });

  it('does not fetch a token for safe methods', async () => {
    const { fetch, calls } = mockFetch(() => jsonResponse({}));
    await client({ fetch }).get('/api/v1/me');
    expect(calls.map((call) => call.url)).toEqual(['/api/v1/me']);
  });
});
