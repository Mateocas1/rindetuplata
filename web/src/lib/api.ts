/**
 * Browser client for the rindetuplata API (T7b, L48 topology).
 *
 * Same origin by default (`PUBLIC_API_URL` overrides it), session cookie always
 * sent (`credentials: 'include'`), RFC 9457 ProblemDetail bodies turned into a
 * typed `ApiProblem`, and a CSRF token fetched from `GET /api/v1/auth/csrf`
 * before any unsafe method, sent under the header name the API itself names.
 *
 * The token is cached per client. A login rotates it, so the login flow calls
 * `forgetCsrfToken()` before its next unsafe request.
 */
import { toApiParams, type RankingQueryState } from './rankingQuery';
import type { Ranking } from './types';

export interface ProblemDetail {
  type?: string | null;
  title?: string | null;
  status?: number | null;
  detail?: string | null;
  instance?: string | null;
  parameter?: string | null;
}

export interface CsrfToken {
  headerName: string;
  token: string;
}

/** `GET /api/v1/me` and the login response. */
export interface CurrentUser {
  email: string;
  roles: string[];
}

const NETWORK_FAILURE_MESSAGE =
  'No se pudo conectar con el servidor. Revisá tu conexión y volvé a intentar.';
const UNREADABLE_BODY_MESSAGE = 'El servidor devolvió una respuesta inesperada.';

export class ApiProblem extends Error {
  constructor(
    readonly status: number,
    message: string,
    readonly problem: ProblemDetail = {},
  ) {
    super(message);
    this.name = 'ApiProblem';
  }

  get title(): string {
    return this.problem.title ?? '';
  }

  get detail(): string | null {
    return this.problem.detail ?? null;
  }

  get instance(): string | null {
    return this.problem.instance ?? null;
  }

  /** The query parameter the API blamed, when it named one. */
  get parameter(): string | null {
    return this.problem.parameter ?? null;
  }
}

export interface ApiClientOptions {
  /** Empty (default) means same origin, which is how the site is deployed (L48). */
  baseUrl?: string;
  fetch?: typeof fetch;
}

export interface ApiClient {
  get<T>(path: string, params?: URLSearchParams): Promise<T>;
  getRanking(state: RankingQueryState, limit?: number): Promise<Ranking>;
  post<T>(path: string, body?: unknown): Promise<T>;
  put<T>(path: string, body?: unknown): Promise<T>;
  delete(path: string): Promise<void>;
  /**
   * Consumes a magic-link token and starts the session. A successful login
   * rotates the CSRF token (T7a), so the cached one is dropped and the next
   * unsafe request fetches a fresh one.
   */
  login(token: string): Promise<CurrentUser>;
  forgetCsrfToken(): void;
}

/** Rows the ranking island asks for; matches the prerendered table. */
export const RANKING_LIMIT = 50;

async function problemFrom(response: Response): Promise<ApiProblem> {
  try {
    const body = (await response.json()) as ProblemDetail | null;
    if (body && typeof body === 'object') {
      const message =
        typeof body.detail === 'string' && body.detail !== ''
          ? body.detail
          : typeof body.title === 'string' && body.title !== ''
            ? body.title
            : `HTTP ${response.status}`;
      return new ApiProblem(response.status, message, body);
    }
  } catch {
    // Fall through: not a JSON body at all.
  }
  return new ApiProblem(response.status, `HTTP ${response.status}`);
}

export function createApiClient(options: ApiClientOptions = {}): ApiClient {
  const baseUrl = (options.baseUrl ?? '').replace(/\/+$/, '');
  const doFetch = options.fetch ?? globalThis.fetch.bind(globalThis);
  let cachedToken: CsrfToken | null = null;

  function buildUrl(path: string, params?: URLSearchParams): string {
    const query = params?.toString();
    return `${baseUrl}${path}${query ? `?${query}` : ''}`;
  }

  async function send<T>(path: string, init: RequestInit, params?: URLSearchParams): Promise<T> {
    let response: Response;
    try {
      response = await doFetch(buildUrl(path, params), init);
    } catch {
      throw new ApiProblem(0, NETWORK_FAILURE_MESSAGE);
    }

    if (!response.ok) {
      throw await problemFrom(response);
    }
    if (response.status === 204) {
      return undefined as T;
    }
    try {
      return (await response.json()) as T;
    } catch {
      throw new ApiProblem(response.status, UNREADABLE_BODY_MESSAGE);
    }
  }

  async function csrfHeader(): Promise<Record<string, string>> {
    if (cachedToken === null) {
      const token = await send<CsrfToken>('/api/v1/auth/csrf', {
        method: 'GET',
        credentials: 'include',
        headers: { accept: 'application/json' },
      });
      if (
        !token ||
        typeof token.headerName !== 'string' ||
        token.headerName === '' ||
        typeof token.token !== 'string' ||
        token.token === ''
      ) {
        throw new ApiProblem(0, UNREADABLE_BODY_MESSAGE);
      }
      cachedToken = token;
    }
    return { [cachedToken.headerName]: cachedToken.token };
  }

  async function unsafe<T>(method: string, path: string, body?: unknown): Promise<T> {
    const headers = new Headers(await csrfHeader());
    headers.set('accept', 'application/json');
    const init: RequestInit = { method, credentials: 'include', headers };
    if (body !== undefined) {
      headers.set('content-type', 'application/json');
      init.body = JSON.stringify(body);
    }
    return send<T>(path, init);
  }

  return {
    get<T>(path: string, params?: URLSearchParams): Promise<T> {
      return send<T>(
        path,
        { method: 'GET', credentials: 'include', headers: { accept: 'application/json' } },
        params,
      );
    },
    getRanking(state: RankingQueryState, limit: number = RANKING_LIMIT): Promise<Ranking> {
      const params = toApiParams(state);
      params.set('limit', String(limit));
      return send<Ranking>(
        '/api/v1/ranking',
        { method: 'GET', credentials: 'include', headers: { accept: 'application/json' } },
        params,
      );
    },
    post<T>(path: string, body?: unknown): Promise<T> {
      return unsafe<T>('POST', path, body);
    },
    put<T>(path: string, body?: unknown): Promise<T> {
      return unsafe<T>('PUT', path, body);
    },
    async delete(path: string): Promise<void> {
      await unsafe<unknown>('DELETE', path);
    },
    async login(token: string): Promise<CurrentUser> {
      const user = await unsafe<CurrentUser>('POST', '/api/v1/auth/login', { token });
      cachedToken = null;
      return user;
    },
    forgetCsrfToken(): void {
      cachedToken = null;
    },
  };
}

/** Client for the deployed site: same origin, real `fetch`. */
export const api = createApiClient({ baseUrl: import.meta.env.PUBLIC_API_URL ?? '' });
