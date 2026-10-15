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
import { formatDate, formatMoney, formatPercent } from '../../lib/format';
import { confidenceLabel, flagLabel, rateKindLabel } from '../../lib/labels';
import {
  DEFAULT_AMOUNT,
  DEFAULT_DAYS,
  tryParseAmount,
  tryParseDays,
} from '../../lib/rankingQuery';
import { problemMessage, simulationOutcome, type SimulationOutcome } from '../../lib/simulator';
import type { Simulation } from '../../lib/types';
import RealReturnBadge from '../ranking/RealReturnBadge';

export interface Props {
  instrumentId: string;
  /** Label of the inflation index the API's real return uses (S10). */
  inflationIndexLabel: string;
  defaultAmount?: number;
  defaultDays?: number;
}

const inputClass =
  'w-full rounded-md border border-border-strong bg-surface-raised px-3 py-2 text-body tabular-nums';
const labelClass = 'flex flex-col gap-1 text-body-sm font-medium';

function SimulatedResult({
  entry,
  inflationIndexLabel,
}: {
  entry: Extract<SimulationOutcome, { status: 'simulated' }>['entry'];
  inflationIndexLabel: string;
}) {
  const isStale = entry.warnings.some((warning) => warning.code === 'STALE');
  return (
    <div className="flex flex-col gap-3">
      <p className="text-lead">
        Con tu monto y tu plazo ganás <span className="font-semibold tabular-nums">{formatMoney(entry.earned)}</span>.
      </p>
      <dl className="grid grid-cols-2 gap-x-6 gap-y-2 text-body-sm">
        <div>
          <dt className="text-muted">Al vencimiento</dt>
          <dd className="tabular-nums">{formatMoney(entry.finalAmount)}</dd>
        </div>
        <div>
          <dt className="text-muted">Rendimiento real</dt>
          <dd>
            <RealReturnBadge realReturn={entry.realReturn} indexLabel={inflationIndexLabel} />
          </dd>
        </div>
        <div>
          <dt className="text-muted">Tasa usada</dt>
          <dd className="tabular-nums">
            {formatPercent(entry.rateUsed.fraction)}{' '}
            <span className="text-muted">({rateKindLabel(entry.rateUsed.kind)})</span>
          </dd>
        </div>
        <div>
          <dt className="text-muted">Fuente y fecha</dt>
          <dd>
            {entry.source.displayName}
            {entry.source.official ? ' · oficial' : ''} · dato del {formatDate(entry.dataDate)} ·{' '}
            {confidenceLabel(entry.confidence)}
            {entry.verifiedAt !== null ? ` · verificada el ${formatDate(entry.verifiedAt)}` : ''}
          </dd>
        </div>
      </dl>
      {entry.upTo !== null && (
        <p className="text-body-sm text-warning tabular-nums">
          Hasta {formatMoney(entry.upTo.finalAmount)} {entry.upTo.condition}.
        </p>
      )}
      {isStale && (
        <p className="inline-flex self-start rounded-pill bg-warning-surface px-2 py-1 text-caption text-on-warning">
          {flagLabel('STALE')}
        </p>
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
      setError(null);
      return;
    }

    setLoading(true);
    setError(null);
    setOutcome(null);
    try {
      const params = new URLSearchParams({
        instrumentId,
        amount: String(parsedAmount),
        days: String(parsedDays),
      });
      const simulation = await api.get<Simulation>('/api/v1/simulations', params);
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
              <p id={`sim-monto-error-${instrumentId}`} className="text-caption text-loss">
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
              <p id={`sim-dias-error-${instrumentId}`} className="text-caption text-loss">
                {daysError}
              </p>
            )}
          </div>
        </div>
        <button
          type="submit"
          className="self-start rounded-md bg-accent-solid px-4 py-2 font-semibold text-on-accent hover:bg-accent aria-disabled:opacity-60"
          aria-disabled={loading}
        >
          {loading ? 'Simulando…' : 'Simular'}
        </button>
      </form>

      <div
        role="status"
        aria-live="polite"
        aria-busy={loading}
        className="min-h-6 rounded-lg border border-border bg-surface-raised px-4 py-3"
      >
        {loading && <p className="text-body-sm text-muted">Simulando con tu monto y tu plazo…</p>}
        {!loading && error !== null && <p className="text-body-sm text-loss">{error}</p>}
        {!loading && outcome?.status === 'simulated' && (
          <SimulatedResult entry={outcome.entry} inflationIndexLabel={inflationIndexLabel} />
        )}
        {!loading && outcome?.status === 'ineligible' && (
          <div className="flex flex-col gap-1">
            <p className="font-medium">Con ese monto y ese plazo esta opción no entra</p>
            <p className="text-body-sm text-muted">{outcome.message}</p>
          </div>
        )}
        {!loading && outcome?.status === 'excluded' && (
          <div className="flex flex-col gap-1">
            <p className="font-medium">Esta opción no se puede simular hoy</p>
            <p className="text-body-sm text-muted">{outcome.message}</p>
          </div>
        )}
        {!loading && outcome?.status === 'unknown' && (
          <p className="text-body-sm text-muted">{outcome.message}</p>
        )}
        {!loading && outcome === null && error === null && (
          <p className="text-body-sm text-muted">
            Poné tu monto y tus días y te mostramos cuánto ganás, con la tasa que publica la
            fuente.
          </p>
        )}
      </div>
      <p className="text-caption text-muted">
        La cuenta la hace la API con la tasa y las condiciones de cada producto. Es información,
        no asesoramiento financiero.
      </p>
    </div>
  );
}
