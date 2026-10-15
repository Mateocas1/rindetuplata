/**
 * Status-code decisions and freshness copy for the account pages (T7d).
 *
 * The API's ProblemDetail `detail` is developer-facing English, so it is never
 * rendered: each status maps to Spanish copy here, 401 routes back to
 * `/ingresar` and 403 becomes the "sin permiso" state. The only message that
 * passes through untouched is the client's own network failure (status 0),
 * which is already Spanish. The freshness and audit-action copy live here too,
 * next to the codes they translate.
 */
import { ApiProblem } from './api';

export type SessionAction = 'login' | 'forbidden' | 'error';

const UNEXPECTED_FAILURE_MESSAGE = 'No pudimos completar la operación. Volvé a intentar en un rato.';

/** Any thrown value as an `ApiProblem`, so a panel has one error shape to render. */
export function toProblem(reason: unknown): ApiProblem {
  return reason instanceof ApiProblem ? reason : new ApiProblem(0, UNEXPECTED_FAILURE_MESSAGE);
}

export const FORBIDDEN_MESSAGE = 'Sin permiso. Esta sección es solo para administradores.';

export const LOGIN_EXPIRED_MESSAGE = 'Tu sesión venció. Volvé a entrar para seguir.';

/** What the page does with a failed request. */
export function sessionAction(status: number): SessionAction {
  if (status === 401) return 'login';
  if (status === 403) return 'forbidden';
  return 'error';
}

/** Spanish copy for a failed request; never the API's English `detail`. */
export function problemText(problem: { status: number; message: string }): string {
  const { status } = problem;
  if (status === 0) return problem.message;
  if (status === 400 || status === 422) return 'Revisá los datos: la API rechazó el pedido.';
  if (status === 401) return LOGIN_EXPIRED_MESSAGE;
  if (status === 403) return FORBIDDEN_MESSAGE;
  if (status === 404) return 'No encontramos ese dato. Puede que lo hayan borrado.';
  if (status >= 500) return 'La API no pudo responder. Volvé a intentar en un rato.';
  return 'No se pudo completar la operación. Volvé a intentar en un rato.';
}

/** "hoy", "hace 1 día", "hace 9 días". */
export function daysSinceText(days: number): string {
  if (days <= 0) return 'hoy';
  if (days === 1) return 'hace 1 día';
  return `hace ${days} días`;
}

/** The API's curated-wallet window (ValidationRules.CURATED_WALLET_STALE_DAYS). */
export const STALE_AFTER_DAYS = 7;

export function isStale(days: number): boolean {
  return days > STALE_AFTER_DAYS;
}

/** Freshness said out loud, so staleness is never colour alone. */
export function stalenessText(days: number): string {
  const age = `Verificado ${daysSinceText(days)}`;
  return isStale(days) ? `${age} · dato desactualizado` : age;
}

const AUDIT_ACTION_LABELS: Record<string, string> = {
  CREATED: 'Creación',
  REPLACED: 'Edición',
  CONFIRMED: 'Confirmación',
};

/** Spanish copy for a curation audit action; an unknown code stays as the raw code. */
export function auditActionLabel(action: string): string {
  return AUDIT_ACTION_LABELS[action] ?? action;
}
