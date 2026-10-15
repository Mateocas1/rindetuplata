/**
 * Simulator outcome mapping (T7c, S11, S8 L-G).
 *
 * The API answers a simulation with a status code plus one of entry /
 * ineligible / excluded. This module turns that into one Spanish, informational
 * message; ProblemDetail bodies become a plain Spanish sentence as well, so the
 * API's English `detail` never reaches the page (L52).
 */
import { excludedReasonLabel, ineligibleExplanation } from './labels';
import type { RankedEntry, Simulation } from './types';

export type SimulationOutcome =
  | { status: 'simulated'; entry: RankedEntry }
  | { status: 'ineligible'; reason: string; message: string }
  | { status: 'excluded'; reason: string; message: string }
  | { status: 'unknown'; message: string };

export interface SimulationContext {
  amount: number;
  days: number;
}

const UNKNOWN_MESSAGE = 'No pudimos simular esta opción. Probá de nuevo en unos minutos.';
const SERVER_MESSAGE = 'No pudimos simular en este momento. Probá de nuevo en unos minutos.';
const NETWORK_MESSAGE =
  'No nos pudimos conectar con el servidor. Revisá tu conexión y volvé a intentar.';
const UNKNOWN_INSTRUMENT_MESSAGE = 'No encontramos ese instrumento.';
const AMOUNT_MESSAGE = 'El monto no es válido. Revisalo e intentá de nuevo.';
const DAYS_MESSAGE = 'El plazo no es válido. Revisalo e intentá de nuevo.';

export function simulationOutcome(
  simulation: Simulation,
  context: SimulationContext,
): SimulationOutcome {
  if (simulation.status === 'SIMULATED' && simulation.entry !== null) {
    return { status: 'simulated', entry: simulation.entry };
  }
  if (simulation.status === 'INELIGIBLE' && simulation.ineligible !== null) {
    const { reason } = simulation.ineligible;
    return { status: 'ineligible', reason, message: ineligibleExplanation(reason, context) };
  }
  if (simulation.status === 'EXCLUDED' && simulation.excluded !== null) {
    const { reason } = simulation.excluded;
    return { status: 'excluded', reason, message: excludedReasonLabel(reason) };
  }
  return { status: 'unknown', message: UNKNOWN_MESSAGE };
}

export interface ProblemLike {
  status: number;
  parameter?: string | null;
  detail?: string | null;
}

/** One Spanish sentence for a failed request; the API's own text is never echoed. */
export function problemMessage(problem: ProblemLike): string {
  if (problem.status === 0) return NETWORK_MESSAGE;
  if (problem.status >= 400 && problem.status < 500) {
    if (problem.parameter === 'amount') return AMOUNT_MESSAGE;
    if (problem.parameter === 'days') return DAYS_MESSAGE;
    if (problem.parameter === 'instrumentId') return UNKNOWN_INSTRUMENT_MESSAGE;
    if (problem.status === 404) return UNKNOWN_INSTRUMENT_MESSAGE;
  }
  return SERVER_MESSAGE;
}
