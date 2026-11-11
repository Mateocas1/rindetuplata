import { describe, expect, it } from 'vitest';
import {
  DEFAULT_ATTEMPTS,
  DEFAULT_TIMEOUT_MS,
  MAX_RETRY_AFTER_MS,
  fetchWithRetry,
  memoizedGetJson,
  timeoutFromEnv,
} from './fetchRetry';

/**
 * The build's HTTP discipline: every request has a deadline, only
 * transient failures are retried (network errors, timeouts, 5xx, 429), the wait
 * grows exponentially from one second and honors `Retry-After` up to 30 s, and
 * an identical GET is sent once per build.
 */
const URL_A = 'http://api.test/api/v1/ranking?amount=1';

function ok(body: unknown = { ok: true }): Response {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { 'content-type': 'application/json' },
  });
}

/** A `sleep` that records the waits instead of waiting. */
function recordingSleep() {
  const waits: number[] = [];
  return {
    waits,
    sleep: async (ms: number) => {
      waits.push(ms);
    },
  };
}

/** A fetch that answers from a script and counts what it was asked. */
function scripted(steps: Array<Response | Error | (() => Promise<Response>)>) {
  const calls: Array<{ url: string; signal: AbortSignal | undefined }> = [];
  const fn = (async (input: RequestInfo | URL, init?: RequestInit) => {
    calls.push({ url: String(input), signal: init?.signal ?? undefined });
    const step = steps[Math.min(calls.length - 1, steps.length - 1)];
    if (step instanceof Error) throw step;
    if (typeof step === 'function') return step();
    return step.clone();
  }) as unknown as typeof fetch;
  return { fetch: fn, calls };
}

describe('defaults', () => {
  it('are the values L36a decided', () => {
    expect(DEFAULT_TIMEOUT_MS).toBe(15_000);
    expect(DEFAULT_ATTEMPTS).toBe(3);
    expect(MAX_RETRY_AFTER_MS).toBe(30_000);
  });
});

describe('timeoutFromEnv', () => {
  it('defaults to 15 s when API_TIMEOUT_MS is unset or blank', () => {
    expect(timeoutFromEnv({})).toBe(15_000);
    expect(timeoutFromEnv({ API_TIMEOUT_MS: '  ' })).toBe(15_000);
  });

  it('reads a positive whole number of milliseconds', () => {
    expect(timeoutFromEnv({ API_TIMEOUT_MS: '2500' })).toBe(2500);
  });

  it.each(['abc', '0', '-5', '1.5', '10s'])('rejects %j naming the variable', (value) => {
    expect(() => timeoutFromEnv({ API_TIMEOUT_MS: value })).toThrow(
      `API_TIMEOUT_MS must be a positive whole number of milliseconds, got "${value}"`,
    );
  });
});

describe('fetchWithRetry', () => {
  it('returns the first answer without waiting when it is fine', async () => {
    const { fetch, calls } = scripted([ok()]);
    const { waits, sleep } = recordingSleep();
    const response = await fetchWithRetry(fetch, URL_A, {}, { sleep });
    expect(response.status).toBe(200);
    expect(calls).toHaveLength(1);
    expect(waits).toEqual([]);
  });

  it('retries a 503 with exponential backoff from one second and then succeeds', async () => {
    const { fetch, calls } = scripted([
      new Response('down', { status: 503 }),
      new Response('down', { status: 502 }),
      ok(),
    ]);
    const { waits, sleep } = recordingSleep();
    const response = await fetchWithRetry(fetch, URL_A, {}, { sleep });
    expect(response.status).toBe(200);
    expect(calls).toHaveLength(3);
    expect(waits).toEqual([1000, 2000]);
  });

  it('stops after 3 attempts and hands back the last failing response', async () => {
    const { fetch, calls } = scripted([new Response('still down', { status: 500 })]);
    const { waits, sleep } = recordingSleep();
    const response = await fetchWithRetry(fetch, URL_A, {}, { sleep });
    expect(response.status).toBe(500);
    expect(await response.text()).toBe('still down');
    expect(calls).toHaveLength(3);
    expect(waits).toEqual([1000, 2000]);
  });

  it('retries a 429 and waits what Retry-After says', async () => {
    const { fetch, calls } = scripted([
      new Response('slow down', { status: 429, headers: { 'retry-after': '7' } }),
      ok(),
    ]);
    const { waits, sleep } = recordingSleep();
    await fetchWithRetry(fetch, URL_A, {}, { sleep });
    expect(calls).toHaveLength(2);
    expect(waits).toEqual([7000]);
  });

  it('caps a long Retry-After at 30 s', async () => {
    const { fetch } = scripted([
      new Response('slow down', { status: 429, headers: { 'retry-after': '3600' } }),
      ok(),
    ]);
    const { waits, sleep } = recordingSleep();
    await fetchWithRetry(fetch, URL_A, {}, { sleep });
    expect(waits).toEqual([30_000]);
  });

  it('reads Retry-After as an HTTP date too', async () => {
    const now = Date.parse('2026-11-01T12:00:00Z');
    const { fetch } = scripted([
      new Response('busy', {
        status: 503,
        headers: { 'retry-after': new Date(now + 5000).toUTCString() },
      }),
      ok(),
    ]);
    const { waits, sleep } = recordingSleep();
    await fetchWithRetry(fetch, URL_A, {}, { sleep, now: () => now });
    expect(waits).toEqual([5000]);
  });

  it('ignores a Retry-After it cannot read and keeps the backoff', async () => {
    const { fetch } = scripted([
      new Response('busy', { status: 503, headers: { 'retry-after': 'soon' } }),
      ok(),
    ]);
    const { waits, sleep } = recordingSleep();
    await fetchWithRetry(fetch, URL_A, {}, { sleep });
    expect(waits).toEqual([1000]);
  });

  it.each([400, 401, 403, 404, 422])('never retries a %i', async (status) => {
    const { fetch, calls } = scripted([new Response('no', { status })]);
    const { waits, sleep } = recordingSleep();
    const response = await fetchWithRetry(fetch, URL_A, {}, { sleep });
    expect(response.status).toBe(status);
    expect(calls).toHaveLength(1);
    expect(waits).toEqual([]);
  });

  it('retries a network error and succeeds', async () => {
    const { fetch, calls } = scripted([new TypeError('fetch failed'), ok()]);
    const { waits, sleep } = recordingSleep();
    const response = await fetchWithRetry(fetch, URL_A, {}, { sleep });
    expect(response.status).toBe(200);
    expect(calls).toHaveLength(2);
    expect(waits).toEqual([1000]);
  });

  it('fails naming the URL, the attempts and the cause when the network never comes back', async () => {
    const { fetch, calls } = scripted([new TypeError('fetch failed')]);
    const { waits, sleep } = recordingSleep();
    await expect(fetchWithRetry(fetch, URL_A, {}, { sleep })).rejects.toThrow(
      `GET ${URL_A} failed after 3 attempts: fetch failed`,
    );
    expect(calls).toHaveLength(3);
    expect(waits).toEqual([1000, 2000]);
  });

  it('aborts a request that outlives the timeout and retries it', async () => {
    const calls: Array<AbortSignal | undefined> = [];
    const doFetch = (async (_input: RequestInfo | URL, init?: RequestInit) => {
      calls.push(init?.signal ?? undefined);
      if (calls.length === 1) {
        return new Promise<Response>((_resolve, reject) => {
          init?.signal?.addEventListener('abort', () => reject(init.signal?.reason));
        });
      }
      return ok();
    }) as unknown as typeof fetch;
    const { waits, sleep } = recordingSleep();
    const response = await fetchWithRetry(doFetch, URL_A, {}, { sleep, timeoutMs: 20 });
    expect(response.status).toBe(200);
    expect(calls).toHaveLength(2);
    expect(calls[0]?.aborted).toBe(true);
    expect(waits).toEqual([1000]);
  });

  it('reports a timeout as such after the last attempt', async () => {
    const doFetch = (async (_input: RequestInfo | URL, init?: RequestInit) =>
      new Promise<Response>((_resolve, reject) => {
        init?.signal?.addEventListener('abort', () => reject(init.signal?.reason));
      })) as unknown as typeof fetch;
    const { sleep } = recordingSleep();
    await expect(fetchWithRetry(doFetch, URL_A, {}, { sleep, timeoutMs: 10 })).rejects.toThrow(
      `GET ${URL_A} failed after 3 attempts: timed out after 10 ms`,
    );
  });

  it('keeps the request headers and adds only the abort signal', async () => {
    const seen: RequestInit[] = [];
    const doFetch = (async (_input: RequestInfo | URL, init?: RequestInit) => {
      seen.push(init ?? {});
      return ok();
    }) as unknown as typeof fetch;
    await fetchWithRetry(doFetch, URL_A, { headers: { accept: 'application/json' } }, {});
    expect(seen[0].headers).toEqual({ accept: 'application/json' });
    expect(seen[0].signal).toBeInstanceOf(AbortSignal);
  });

  it('never puts request headers in the failure message', async () => {
    const { fetch } = scripted([new TypeError('fetch failed')]);
    const { sleep } = recordingSleep();
    const failure = fetchWithRetry(
      fetch,
      URL_A,
      { headers: { 'x-build-token': 's3cret' } },
      { sleep },
    );
    await expect(failure).rejects.toThrow(/failed after 3 attempts/);
    await failure.catch((error: Error) => expect(error.message).not.toContain('s3cret'));
  });
});

describe('memoizedGetJson', () => {
  it('fetches an identical URL and headers once and shares the parsed body', async () => {
    const { fetch, calls } = scripted([ok({ n: 1 })]);
    const options = { headers: { accept: 'application/json' } };
    const [first, second] = await Promise.all([
      memoizedGetJson(fetch, URL_A, options, {}),
      memoizedGetJson(fetch, URL_A, options, {}),
    ]);
    const third = await memoizedGetJson(fetch, URL_A, options, {});
    expect(calls).toHaveLength(1);
    expect(first).toEqual({ n: 1 });
    expect(second).toBe(first);
    expect(third).toBe(first);
  });

  it('keeps different URLs and different headers apart', async () => {
    const { fetch, calls } = scripted([ok()]);
    await memoizedGetJson(fetch, URL_A, { headers: { accept: 'application/json' } }, {});
    await memoizedGetJson(fetch, `${URL_A}&limit=2`, { headers: { accept: 'application/json' } }, {});
    await memoizedGetJson(
      fetch,
      URL_A,
      { headers: { accept: 'application/json', 'x-build-token': 't' } },
      {},
    );
    expect(calls).toHaveLength(3);
  });

  it('does not share answers between different fetch implementations', async () => {
    const one = scripted([ok({ from: 'one' })]);
    const two = scripted([ok({ from: 'two' })]);
    expect(await memoizedGetJson(one.fetch, URL_A, {}, {})).toEqual({ from: 'one' });
    expect(await memoizedGetJson(two.fetch, URL_A, {}, {})).toEqual({ from: 'two' });
  });

  it('does not remember a failure: a later identical request asks again', async () => {
    const { fetch, calls } = scripted([new Response('down', { status: 404 }), ok({ n: 2 })]);
    await expect(memoizedGetJson(fetch, URL_A, {}, {})).rejects.toThrow(
      `GET ${URL_A} failed with 404: down`,
    );
    expect(await memoizedGetJson(fetch, URL_A, {}, {})).toEqual({ n: 2 });
    expect(calls).toHaveLength(2);
  });

  it('puts the status and the start of the body in the error of a final failure', async () => {
    const { fetch } = scripted([new Response('x'.repeat(400), { status: 503 })]);
    const { sleep } = recordingSleep();
    await expect(memoizedGetJson(fetch, URL_A, {}, { sleep })).rejects.toThrow(
      `GET ${URL_A} failed with 503: ${'x'.repeat(300)}`,
    );
  });
});
