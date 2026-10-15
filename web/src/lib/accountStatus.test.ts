import { describe, expect, it } from 'vitest';
import {
  FORBIDDEN_MESSAGE,
  LOGIN_EXPIRED_MESSAGE,
  auditActionLabel,
  daysSinceText,
  isStale,
  problemText,
  sessionAction,
  stalenessText,
  toProblem,
} from './accountStatus';
import { ApiProblem } from './api';

/**
 * Status-code decisions and staleness copy (T7d). The API's ProblemDetail
 * `detail` is developer-facing English, so it is never rendered: every status
 * maps to Spanish copy here, and the account pages route 401 to `/ingresar`
 * and show a "Sin permiso" state for 403.
 */

describe('session action from a status code', () => {
  it('sends an expired session back to the login page', () => {
    expect(sessionAction(401)).toBe('login');
  });

  it('shows the no-permission state for a forbidden request', () => {
    expect(sessionAction(403)).toBe('forbidden');
  });

  it('treats anything else as a retryable error', () => {
    for (const status of [0, 400, 404, 409, 500, 503]) {
      expect(sessionAction(status)).toBe('error');
    }
  });
});

describe('problem copy', () => {
  it('never renders the API English detail for a known status', () => {
    const text = problemText({ status: 401, message: 'The login token is unknown' });
    expect(text).toBe(LOGIN_EXPIRED_MESSAGE);
    expect(text).not.toContain('token');
  });

  it('names the no-permission state', () => {
    expect(problemText({ status: 403, message: 'Access Denied' })).toBe(FORBIDDEN_MESSAGE);
  });

  it('asks for the data to be fixed on a validation error', () => {
    expect(problemText({ status: 400, message: "Parameter 'amount' must be > 0" })).toContain(
      'Revisá los datos',
    );
  });

  it("keeps the client's own Spanish message for a network failure", () => {
    const network = 'No se pudo conectar con el servidor. Revisá tu conexión y volvé a intentar.';
    expect(problemText({ status: 0, message: network })).toBe(network);
  });

  it('offers a retry for a server error', () => {
    expect(problemText({ status: 500, message: 'Internal Server Error' })).toContain('Volvé a intentar');
  });

  it('passes an ApiProblem through and turns anything else into a retryable failure', () => {
    const known = new ApiProblem(403, 'Access Denied');
    expect(toProblem(known)).toBe(known);

    const unknown = toProblem(new TypeError('Failed to fetch'));
    expect(unknown.status).toBe(0);
    expect(problemText(unknown)).not.toContain('fetch');
  });
});

describe('days since verification', () => {
  it('formats the age in Spanish', () => {
    expect(daysSinceText(0)).toBe('hoy');
    expect(daysSinceText(1)).toBe('hace 1 día');
    expect(daysSinceText(3)).toBe('hace 3 días');
    expect(daysSinceText(40)).toBe('hace 40 días');
  });

  it("flags a curated rate older than the API's 7-day window", () => {
    expect(isStale(7)).toBe(false);
    expect(isStale(8)).toBe(true);
    expect(isStale(0)).toBe(false);
  });

  it('says the freshness out loud, so staleness is never colour alone', () => {
    expect(stalenessText(0)).toBe('Verificado hoy');
    expect(stalenessText(3)).toBe('Verificado hace 3 días');
    expect(stalenessText(9)).toContain('desactualizado');
  });
});

describe('audit actions', () => {
  it('names the curation actions in Spanish and passes an unknown code through', () => {
    expect(auditActionLabel('CREATED')).toBe('Creación');
    expect(auditActionLabel('REPLACED')).toBe('Edición');
    expect(auditActionLabel('CONFIRMED')).toBe('Confirmación');
    expect(auditActionLabel('SOMETHING_NEW')).toBe('SOMETHING_NEW');
  });
});
