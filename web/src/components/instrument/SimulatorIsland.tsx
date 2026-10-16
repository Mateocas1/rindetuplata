/**
 * Per-instrument simulator (T7c, S3, S14).
 *
 * A React island over `GET /api/v1/simulations`; every number it renders comes
 * from the API (no financial math in the browser). The form validates before it
 * asks, the result region is a stable polite live region, and the four outcomes
 * (simulated, ineligible, excluded, failed) each get one Spanish sentence.
 */
import { useState } from 'react';
import { ApiProblem, api } from '../../lib/api';
import { formatDate, formatDays, formatMoney, formatPercent, formatSignedPercent } from '../../lib/format';
import { confidenceLabel, flagLabel, rateKindLabel } from '../../lib/labels';
import {
  DEFAULT_AMOUNT,
  DEFAULT_DAYS,
  tryParseAmount,
  tryParseDays,
} from '../../lib/rankingQuery';
import { problemMessage, simulationOutcome, type SimulationOutcome } from '../../lib/simulator';
import type { Simulation } from '../../lib/types';

export interface Props {
  instrumentId: string;
  /** Label of the inflation index the API's real return uses (S10). */
  inflationIndexLabel: string;
  defaultAmount?: number;
  defaultDays?: number;
}

const inputClass = 'field tabular-nums';
const labelClass = 'flex flex-col gap-1 type-label';

/** The sign is text; the tone only reinforces it, never replaces it (S10). */
function realReturnTone(value: number | null): string {
  if (value === null || value === 0) return 'text-muted';
  return value > 0 ? 'text-gain' : 'text-loss';
}

function SimulatedResult({
  entry,
  amount,
  days,
  inflationIndexLabel,
}: {
  entry: Extract<SimulationOutcome, { status: 'simulated' }>['entry'];
  amount: number;
  days: number;
  inflationIndexLabel: string;
}) {
  const isStale = entry.warnings.some((warning) => warning.code === 'STALE');
  return (
    <div className="flex flex-col gap-4">
      <div>
        <p className="type-label">Ganancia estimada</p>
        <p className="type-figure mt-2">{formatMoney(entry.earned)}</p>
        <p className="type-data text-muted mt-1">
          en {formatDays(days)} con {formatMoney(amount)}
        </p>
      </div>
      <dl className="grid grid-cols-2 gap-x-6 gap-y-4 border-t border-border pt-4 sm:grid-cols-3">
        <div>
          <dt className="type-label">Al vencimiento</dt>
          <dd className="type-figure text-figure-row">{formatMoney(entry.finalAmount)}</dd>
        </div>
        <div>
          <dt className="type-label">{entry.rateUsed.basis}</dt>
          <dd className="type-figure text-figure-row">{formatPercent(entry.rateUsed.fraction)}</dd>
        </div>
        <div className="col-span-2 sm:col-span-1">
          <dt className="type-label">Rendimiento real</dt>
          <dd className={`type-figure text-figure-row ${realReturnTone(entry.realReturn)}`}>
            {entry.realReturn === null ? 'sin dato' : formatSignedPercent(entry.realReturn)}
            <span className="sr-only">
              {' '}
              real contra la inflación esperada ({inflationIndexLabel})
            </span>
          </dd>
        </div>
      </dl>
      <p className="type-data text-muted">
        {entry.source.displayName}
        {entry.source.official ? ' · oficial' : ''} · {rateKindLabel(entry.rateUsed.kind)} · dato del{' '}
        {formatDate(entry.dataDate)} · {confidenceLabel(entry.confidence)}
        {entry.verifiedAt !== null ? ` · verificada el ${formatDate(entry.verifiedAt)}` : ''}
      </p>
      {entry.upTo !== null && (
        <p className="type-data text-warning">
          Hasta {formatMoney(entry.upTo.finalAmount)} {entry.upTo.condition}.
        </p>
      )}
      {isStale && (
        <p className="chip self-start bg-warning-surface text-on-warning">{flagLabel('STALE')}</p>
      )}
    </div>
  );
}

export default function SimulatorIsland({
  instrumentId,
  inflationIndexLabel,
  defaultAmount = DEFAULT_AMOUNT,
  defaultDays = DEFAULT_DAYS,
}: Props) {
  const [amount, setAmount] = useState(String(defaultAmount));
  const [days, setDays] = useState(String(defaultDays));
  const [amountError, setAmountError] = useState<string | null>(null);
  const [daysError, setDaysError] = useState<string | null>(null);
  const [outcome, setOutcome] = useState<SimulationOutcome | null>(null);
  // The amount and term the shown result belongs to, so editing a field never
  // silently relabels a result that came from the previous query.
  const [submitted, setSubmitted] = useState<{ amount: number; days: number } | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function onSubmit(event: React.SyntheticEvent<HTMLFormElement>) {
    event.preventDefault();
    // The button stays focusable while a request runs (aria-disabled, not
    // `disabled`), so a second submit is ignored here instead.
    if (loading) return;
    const parsedAmount = tryParseAmount(amount);
    const parsedDays = tryParseDays(days);
    setAmountError(parsedAmount === null ? 'Ingresá un monto en pesos mayor a cero.' : null);
    setDaysError(parsedDays === null ? 'Ingresá los días como número entero entre 1 y 3650.' : null);
    if (parsedAmount === null || parsedDays === null) {
      setOutcome(null);
      setSubmitted(null);
      setError(null);
      return;
    }

    setLoading(true);
    setError(null);
    setOutcome(null);
    setSubmitted(null);
    try {
      const params = new URLSearchParams({
        instrumentId,
        amount: String(parsedAmount),
        days: String(parsedDays),
      });
      const simulation = await api.get<Simulation>('/api/v1/simulations', params);
      setSubmitted({ amount: parsedAmount, days: parsedDays });
      setOutcome(simulationOutcome(simulation, { amount: parsedAmount, days: parsedDays }));
    } catch (caught) {
      setError(
        caught instanceof ApiProblem
          ? problemMessage(caught)
          : 'No pudimos simular esta opción. Probá de nuevo en unos minutos.',
      );
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="flex flex-col gap-4">
      <form className="flex flex-col gap-4" onSubmit={onSubmit} noValidate>
        <div className="grid gap-4 sm:grid-cols-2">
          <div className={labelClass}>
            <label htmlFor={`sim-monto-${instrumentId}`}>Monto en pesos</label>
            <input
              id={`sim-monto-${instrumentId}`}
              name="monto"
              type="text"
              inputMode="decimal"
              className={inputClass}
              value={amount}
              onChange={(event) => setAmount(event.target.value)}
              aria-invalid={amountError !== null}
              aria-describedby={amountError !== null ? `sim-monto-error-${instrumentId}` : undefined}
            />
            {amountError !== null && (
              <p id={`sim-monto-error-${instrumentId}`} className="type-data text-loss">
                {amountError}
              </p>
            )}
          </div>
          <div className={labelClass}>
            <label htmlFor={`sim-dias-${instrumentId}`}>Días</label>
            <input
              id={`sim-dias-${instrumentId}`}
              name="dias"
              type="text"
              inputMode="numeric"
              className={inputClass}
              value={days}
              onChange={(event) => setDays(event.target.value)}
              aria-invalid={daysError !== null}
              aria-describedby={daysError !== null ? `sim-dias-error-${instrumentId}` : undefined}
            />
            {daysError !== null && (
              <p id={`sim-dias-error-${instrumentId}`} className="type-data text-loss">
                {daysError}
              </p>
            )}
          </div>
        </div>
        <button type="submit" className="btn-secondary self-start aria-disabled:opacity-60" aria-disabled={loading}>
          {loading ? 'Simulando…' : 'Simular'}
        </button>
      </form>

      <div
        role="status"
        aria-live="polite"
        aria-busy={loading}
        className="card min-h-6 p-6"
      >
        {loading && <p className="type-body text-muted">Simulando con tu monto y tu plazo…</p>}
        {!loading && error !== null && <p className="type-body text-loss">{error}</p>}
        {!loading && outcome?.status === 'simulated' && (
          <SimulatedResult
            entry={outcome.entry}
            amount={submitted?.amount ?? defaultAmount}
            days={submitted?.days ?? defaultDays}
            inflationIndexLabel={inflationIndexLabel}
          />
        )}
        {!loading && outcome?.status === 'ineligible' && (
          <div className="flex flex-col gap-1">
            <p className="type-label">Con ese monto y ese plazo esta opción no entra</p>
            <p className="type-body text-muted">{outcome.message}</p>
          </div>
        )}
        {!loading && outcome?.status === 'excluded' && (
          <div className="flex flex-col gap-1">
            <p className="type-label">Esta opción no se puede simular hoy</p>
            <p className="type-body text-muted">{outcome.message}</p>
          </div>
        )}
        {!loading && outcome?.status === 'unknown' && (
          <p className="type-body text-muted">{outcome.message}</p>
        )}
        {!loading && outcome === null && error === null && (
          <p className="type-body text-muted">
            Poné tu monto y tus días y te mostramos cuánto ganás, con la tasa que publica la
            fuente.
          </p>
        )}
      </div>
      <p className="type-data text-muted">
        La cuenta la hace la API con la tasa y las condiciones de cada producto. Es información,
        no asesoramiento financiero.
      </p>
    </div>
  );
}
