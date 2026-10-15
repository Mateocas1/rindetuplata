import { describe, expect, it } from 'vitest';
import { problemMessage, simulationOutcome } from './simulator';
import type { Simulation } from './types';

/**
 * Simulator outcome mapping (T7c, S11, S8 L-G): the API's status codes and
 * ProblemDetail parameters become one Spanish, informational message. The
 * API's own English `detail` is never rendered (L52).
 */
const simulated: Simulation = {
  status: 'SIMULATED',
  entry: {
    id: '29',
    kind: 'PLAZO_FIJO',
    name: 'REBA COMPAÑIA FINANCIERA S.A.',
    entity: 'REBA COMPAÑIA FINANCIERA S.A.',
    source: { id: 'argentinadatos-plazo-fijo', displayName: 'ArgentinaDatos', official: false },
    confidence: 'AGGREGATOR_AUTOMATIC',
    verifiedAt: null,
    captureDate: '2026-10-14',
    dataDate: '2026-10-14',
    rateUsed: { fraction: 0.24, basis: 'TNA', kind: 'NON_CLIENT' },
    finalAmount: 519726.03,
    earned: 19726.03,
    nominalReturn: 0.039452,
    realReturn: 0.007249,
    upTo: null,
    warnings: [],
  },
  ineligible: null,
  excluded: null,
};

const ineligible: Simulation = {
  status: 'INELIGIBLE',
  entry: null,
  ineligible: {
    id: '29',
    name: 'REBA COMPAÑIA FINANCIERA S.A.',
    reason: 'MINIMUM_TERM',
    detail: 'Minimum term is 30 days, requested 20',
  },
  excluded: null,
};

const excluded: Simulation = {
  status: 'EXCLUDED',
  entry: null,
  ineligible: null,
  excluded: {
    id: '38',
    name: 'ADBlick Ganaderia FCIC',
    reason: 'STALE',
    flags: [{ code: 'STALE', severity: 'WARNING', message: 'Data date is older than 7 days' }],
  },
};

describe('simulationOutcome', () => {
  it('passes a simulated entry through', () => {
    const outcome = simulationOutcome(simulated, { amount: 500_000, days: 60 });
    expect(outcome.status).toBe('simulated');
    if (outcome.status !== 'simulated') throw new Error('unreachable');
    expect(outcome.entry.id).toBe('29');
    expect(outcome.entry.earned).toBe(19726.03);
  });

  it('explains an ineligible simulation with the reader amount and term in Spanish', () => {
    const outcome = simulationOutcome(ineligible, { amount: 500_000, days: 20 });
    expect(outcome.status).toBe('ineligible');
    if (outcome.status !== 'ineligible') throw new Error('unreachable');
    expect(outcome.reason).toBe('MINIMUM_TERM');
    expect(outcome.message).toBe('Pide un plazo mínimo mayor a 20 días.');
    expect(outcome.message).not.toMatch(/Minimum term is 30 days/);
  });

  it('explains an excluded simulation with the reason label from labels.ts', () => {
    const outcome = simulationOutcome(excluded, { amount: 500_000, days: 60 });
    expect(outcome.status).toBe('excluded');
    if (outcome.status !== 'excluded') throw new Error('unreachable');
    expect(outcome.message).toBe('Datos desactualizados');
  });

  it('falls back to a neutral message for a status it does not know', () => {
    const unknown = { ...simulated, status: 'SOMETHING_ELSE' } as unknown as Simulation;
    const outcome = simulationOutcome(unknown, { amount: 500_000, days: 60 });
    expect(outcome.status).toBe('unknown');
    if (outcome.status !== 'unknown') throw new Error('unreachable');
    expect(outcome.message).toMatch(/No pudimos simular/i);
  });
});

describe('problemMessage', () => {
  it('names the field the API blamed', () => {
    expect(problemMessage({ status: 400, parameter: 'amount' })).toMatch(/monto/i);
    expect(problemMessage({ status: 400, parameter: 'days' })).toMatch(/plazo/i);
    expect(problemMessage({ status: 400, parameter: 'instrumentId' })).toMatch(
      /instrumento/i,
    );
  });

  it('is a plain Spanish message for the rest, never the API English detail', () => {
    expect(problemMessage({ status: 404 })).toMatch(/instrumento/i);
    expect(problemMessage({ status: 503 })).toMatch(/No pudimos simular/i);
    expect(problemMessage({ status: 0 })).toMatch(/conectar/i);
    expect(problemMessage({ status: 418 })).toMatch(/No pudimos simular/i);
  });

  it('does not echo the ProblemDetail text', () => {
    const message = problemMessage({ status: 500, detail: 'Internal server error' });
    expect(message).not.toContain('Internal server error');
  });
});
