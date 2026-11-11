/**
 * HTTP discipline of the static build.
 *
 * A build used to send about thirty identical ranking requests with no deadline and no second chance: one
 * slow answer hung it, one 502 failed it. Every request now has a deadline (`API_TIMEOUT_MS`, default 15 s),
 * transient failures are retried (network errors, timeouts, 5xx, 429) up to 3 attempts with an exponential
 * wait from one second that honors `Retry-After` up to 30 s, and an identical GET is sent once per build.
 * Any other 4xx is a real answer and is never retried.
 *
 * Build-time only, like `data.ts`.
 */
import type { BuildDataOptions, Env } from './data';

export const DEFAULT_TIMEOUT_MS = 15_000;
export const DEFAULT_ATTEMPTS = 3;
export const BASE_DELAY_MS = 1000;
export const MAX_RETRY_AFTER_MS = 30_000;

/** Characters of an error body that reach a build message. */
const ERROR_BODY_LIMIT = 300;

export interface RetryOptions {
  timeoutMs?: number;
  attempts?: number;
  baseDelayMs?: number;
  /** Replaced by tests so a retry never really waits. */
  sleep?: (ms: number) => Promise<void>;
  /** Replaced by tests to read a `Retry-After` HTTP date. */
  now?: () => number;
}

/** `API_TIMEOUT_MS`: a positive whole number of milliseconds; blank or unset means the default. */
export function timeoutFromEnv(env: Env): number {
  const raw = env.API_TIMEOUT_MS?.trim();
  if (raw === undefined || raw === '') return DEFAULT_TIMEOUT_MS;
  if (!/^\d+$/.test(raw) || Number(raw) <= 0) {
    throw new Error(
      `API_TIMEOUT_MS must be a positive whole number of milliseconds, got "${env.API_TIMEOUT_MS}"`,
    );
  }
  return Number(raw);
}

function defaultSleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function isTransient(status: number): boolean {
  return status === 429 || status >= 500;
}

/** The wait a `Retry-After` header asks for (seconds or an HTTP date), capped, or null when unreadable. */
function retryAfterMs(response: Response, now: () => number): number | null {
  const raw = response.headers.get('retry-after')?.trim();
  if (raw === undefined || raw === '') return null;
  let ms: number;
  if (/^\d+$/.test(raw)) {
    ms = Number(raw) * 1000;
  } else {
    const date = Date.parse(raw);
    if (Number.isNaN(date)) return null;
    ms = date - now();
  }
  return Math.min(Math.max(ms, 0), MAX_RETRY_AFTER_MS);
}

class AttemptTimeout extends Error {
  constructor(timeoutMs: number) {
    super(`timed out after ${timeoutMs} ms`);
    this.name = 'AttemptTimeout';
  }
}

/**
 * One attempt under a deadline that covers the headers and the whole body, so a stalled download cannot
 * hang the build either. The body is buffered and handed back as a fresh `Response`.
 */
async function attempt(
  doFetch: typeof fetch,
  url: string,
  init: RequestInit,
  timeoutMs: number,
): Promise<Response> {
  const controller = new AbortController();
  let timer: ReturnType<typeof setTimeout> | undefined;
  const deadline = new Promise<never>((_resolve, reject) => {
    timer = setTimeout(() => {
      const timeout = new AttemptTimeout(timeoutMs);
      controller.abort(timeout);
      reject(timeout);
    }, timeoutMs);
  });
  try {
    const exchange = (async () => {
      const response = await doFetch(url, { ...init, signal: controller.signal });
      const body = await response.arrayBuffer();
      return new Response(body, {
        status: response.status,
        statusText: response.statusText,
        headers: response.headers,
      });
    })();
    // The loser must not become an unhandled rejection.
    exchange.catch(() => undefined);
    return await Promise.race([exchange, deadline]);
  } finally {
    clearTimeout(timer);
  }
}

/**
 * GET `url` with a deadline and bounded retries. A response that is not transient (or the last transient
 * one) is returned for the caller to judge; a request that never got a response throws, naming the URL,
 * the attempts and the cause, never the headers.
 */
export async function fetchWithRetry(
  doFetch: typeof fetch,
  url: string,
  init: RequestInit = {},
  options: RetryOptions = {},
): Promise<Response> {
  const timeoutMs = options.timeoutMs ?? DEFAULT_TIMEOUT_MS;
  const attempts = options.attempts ?? DEFAULT_ATTEMPTS;
  const baseDelayMs = options.baseDelayMs ?? BASE_DELAY_MS;
  const sleep = options.sleep ?? defaultSleep;
  const now = options.now ?? Date.now;

  for (let number = 1; ; number += 1) {
    let response: Response | null = null;
    let failure: unknown = null;
    try {
      response = await attempt(doFetch, url, init, timeoutMs);
    } catch (error) {
      failure = error;
    }

    if (response !== null && !isTransient(response.status)) return response;
    if (number >= attempts) {
      if (response !== null) return response;
      const cause = failure instanceof Error ? failure.message : String(failure);
      throw new Error(`GET ${url} failed after ${attempts} attempts: ${cause}`);
    }

    const backoff = baseDelayMs * 2 ** (number - 1);
    await sleep((response !== null ? retryAfterMs(response, now) : null) ?? backoff);
  }
}

/** Parsed answers by fetch implementation, then by request; shared by every module instance of one build. */
const CACHE_KEY = Symbol.for('rindetuplata.build.fetchCache');
type Cache = WeakMap<object, Map<string, Promise<unknown>>>;

function cacheFor(doFetch: typeof fetch): Map<string, Promise<unknown>> {
  const holder = globalThis as unknown as Record<symbol, Cache | undefined>;
  const caches = (holder[CACHE_KEY] ??= new WeakMap());
  let cache = caches.get(doFetch);
  if (cache === undefined) {
    cache = new Map();
    caches.set(doFetch, cache);
  }
  return cache;
}

function requestKey(url: string, init: RequestInit): string {
  const headers = Object.entries((init.headers ?? {}) as Record<string, string>)
    .map(([name, value]) => [name.toLowerCase(), value] as const)
    .sort(([a], [b]) => (a < b ? -1 : a > b ? 1 : 0));
  return `${url}\n${JSON.stringify(headers)}`;
}

async function getJson(
  doFetch: typeof fetch,
  url: string,
  init: RequestInit,
  options: RetryOptions,
): Promise<unknown> {
  const response = await fetchWithRetry(doFetch, url, init, options);
  if (!response.ok) {
    const body = await response.text();
    throw new Error(`GET ${url} failed with ${response.status}: ${body.slice(0, ERROR_BODY_LIMIT)}`);
  }
  return response.json();
}

/**
 * GET and parse JSON, once per identical URL and headers for the build process (the `astro build` that
 * renders every page runs in one process). A failure is not remembered. Callers share the parsed value and
 * must not modify it.
 */
export function memoizedGetJson(
  doFetch: typeof fetch,
  url: string,
  init: RequestInit = {},
  options: RetryOptions = {},
): Promise<unknown> {
  const cache = cacheFor(doFetch);
  const key = requestKey(url, init);
  const known = cache.get(key);
  if (known !== undefined) return known;
  const pending = getJson(doFetch, url, init, options);
  cache.set(key, pending);
  pending.catch(() => cache.delete(key));
  return pending;
}

/**
 * The build token, when set, is sent as `X-Build-Token` so the API skips its per-address limit for this build; a
 * blank value sends nothing. It never reaches an error or a log line (errors name the URL, never the headers).
 */
function apiHeaders(env: Env): Record<string, string> {
  const headers: Record<string, string> = { accept: 'application/json' };
  const token = env.API_BUILD_TOKEN?.trim();
  if (token) headers['x-build-token'] = token;
  return headers;
}

/**
 * The one way a loader reads the API while building: deadline and retries from the environment
 * (`API_TIMEOUT_MS`), the build token, and one request per identical URL and headers for the build. `data.ts`
 * builds the same request, so a URL asked for by two modules is still sent once. (F6: the bank and report loaders
 * had their own bare `fetch`, so a hung API hung the build on those pages.)
 */
export function buildApiGet(url: string, options: BuildDataOptions, env: Env): Promise<unknown> {
  // `globalThis.fetch` is one stable function, so every module asking through it shares one memo.
  const doFetch = options.fetch ?? globalThis.fetch;
  return memoizedGetJson(
    doFetch,
    url,
    { headers: apiHeaders(env) },
    { timeoutMs: timeoutFromEnv(env), sleep: options.sleep },
  );
}
