/**
 * Per-instrument simulator (T7c, S3, S14; ui-clarity D12, D16).
 *
 * A React island over `GET /api/v1/simulations`; every number it renders comes
 * from the API (no financial math in the browser). It never starts empty: the
 * page hands it the result for the default amount and term from its static
 * data, so the first paint already answers; "Simular" asks the API for the
 * reader's own amount and term. The submit stays explicit because the public
 * API is rate limited per IP: nothing fires on a keystroke. The amount field
 * reads and shows Argentine notation (`amountInput`). The result region is a
 * stable polite live region, and the four outcomes (simulated, ineligible,
 * excluded, failed) each get one Spanish sentence.
 */
import { useState } from 'react';
import { ApiProblem, api } from '../../lib/api';
import { formatAmountInput, parseAmountInput } from '../../lib/amountInput';
import { earnedFrom } from '../../lib/rankingPresentation';
import { formatDate, formatDays, formatMoney, formatPercent } from '../../lib/format';
import { confidenceLabel, flagLabel, rateKindLabel, sourceLabel } from '../../lib/labels';
import { DEFAULT_AMOUNT, DEFAULT_DAYS, MAX_DAYS, tryParseDays } from '../../lib/rankingQuery';
import { problemMessage, simulationOutcome, type SimulationOutcome } from '../../lib/simulator';
import type { MepQuote, RankedEntry, Simulation } from '../../lib/types';
import BreakEvenNote from '../ranking/BreakEvenNote';
import RealResult from '../ui/RealResult';

export interface Props {
  instrumentId: string;
  /** Label of the inflation index the API's real return uses (S10). */
  inflationIndexLabel: string;
  defaultAmount?: number;
  defaultDays?: number;
  /** The page's own entry for the default amount and term: the result the simulator starts with. */
  initialEntry?: RankedEntry | null;
}

const labelClass = 'flex flex-col gap-1 type-label';

export function SimulatedResult({
  entry,
  dollarMep,
  amount,
  days,
  inflationIndexLabel,
}: {
  entry: Extract<SimulationOutcome, { status: 'simulated' }>['entry'];
  /** The MEP quote `entry.breakEvenMep` used; the dollar line is hidden without it (S5, L24 b). */
  dollarMep?: MepQuote | null;
  amount: number;
  days: number;
  inflationIndexLabel: string;
}) {
  const isStale = entry.warnings.some((warning) => warning.code === 'STALE');
  return (
    <div className="flex flex-col gap-3">
      <div>
        <p className="type-label">Ganancia estimada</p>
        <p className="type-figure text-figure-row mt-1">{formatMoney(entry.earned)}</p>
        <p className="type-data text-muted mt-1">
          en {formatDays(days)} con {formatMoney(amount)}
        </p>
      </div>
      <p>
        <RealResult
          realEarned={entry.realEarned}
          realReturn={entry.realReturn}
          indexLabel={inflationIndexLabel}
          size="lead"
        />
      </p>
      <dl className="grid grid-cols-2 gap-x-6 gap-y-4 border-t border-border pt-4">
        <div>
          <dt className="type-label">Al vencimiento</dt>
          <dd className="type-data font-medium">{formatMoney(entry.finalAmount)}</dd>
        </div>
        <div>
          <dt className="type-label">{entry.rateUsed.basis}</dt>
          <dd className="type-data font-medium">{formatPercent(entry.rateUsed.fraction)}</dd>
        </div>
      </dl>
      <p className="type-data text-muted">
        {sourceLabel(entry)}
        {entry.source.official ? ' · oficial' : ''} · {rateKindLabel(entry.rateUsed.kind)} · dato del{' '}
        {formatDate(entry.dataDate)}
        {/* A curated rate already names its load as the source (sourceLabel). */}
        {entry.confidence === 'CURATED_MANUAL' ? '' : ` · ${confidenceLabel(entry.confidence)}`}
        {entry.verifiedAt !== null ? ` · verificada el ${formatDate(entry.verifiedAt)}` : ''}
      </p>
      <BreakEvenNote breakEven={entry.breakEvenMep} quote={dollarMep} days={days} />
      {entry.upTo !== null && (
        <p className="type-data text-warning">
          Ganás hasta {formatMoney(earnedFrom(amount, entry.upTo.finalAmount))}{' '}
          {entry.upTo.condition.charAt(0).toLowerCase() + entry.upTo.condition.slice(1)}.
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
  initialEntry = null,
}: Props) {
  const [amount, setAmount] = useState(formatAmountInput(defaultAmount));
  const [days, setDays] = useState(String(defaultDays));
  const [amountError, setAmountError] = useState<string | null>(null);
  const [daysError, setDaysError] = useState<string | null>(null);
  // An instrument the ranking cannot score has no entry: its hero says why, and
  // the simulator waits for a query instead of repeating it.
  const [outcome, setOutcome] = useState<SimulationOutcome | null>(
    initialEntry === null ? null : { status: 'simulated', entry: initialEntry, dollarMep: null },
  );
  // The amount and term the shown result belongs to, so editing a field never
  // silently relabels a result that came from the previous query.
  const [submitted, setSubmitted] = useState<{ amount: number; days: number } | null>(
    initialEntry === null ? null : { amount: defaultAmount, days: defaultDays },
  );
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  /** Back to "1.000.000" when the field loses focus with a readable amount. */
  function onAmountBlur() {
    const parsed = parseAmountInput(amount);
    if (parsed !== null) setAmount(formatAmountInput(parsed));
  }

  async function onSubmit(event: React.SyntheticEvent<HTMLFormElement>) {
    event.preventDefault();
    // The button stays focusable while a request runs (aria-disabled, not
    // `disabled`), so a second submit is ignored here instead.
    if (loading) return;
    const parsedAmount = parseAmountInput(amount);
    const parsedDays = tryParseDays(days);
    setAmountError(parsedAmount === null ? 'Ingresá un monto en pesos mayor a cero.' : null);
    setDaysError(parsedDays === null ? `Ingresá los días como número entero entre 1 y ${MAX_DAYS}.` : null);
    if (parsedAmount === null || parsedDays === null) {
      setOutcome(null);
      setSubmitted(null);
      setError(null);
      return;
    }
    setAmount(formatAmountInput(parsedAmount));

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

  const hasResult = loading || error !== null || outcome !== null;

  return (
    <div className="flex flex-col gap-5">
      <form className="flex flex-col gap-4" onSubmit={onSubmit} noValidate>
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-1">
          <div className={labelClass}>
            <label htmlFor={`sim-monto-${instrumentId}`}>Monto en pesos</label>
            <input
              id={`sim-monto-${instrumentId}`}
              name="monto"
              type="text"
              inputMode="numeric"
              autoComplete="off"
              className="field tabular-nums"
              value={amount}
              onChange={(event) => setAmount(event.target.value)}
              onBlur={onAmountBlur}
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
              autoComplete="off"
              className="field tabular-nums"
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
        <button type="submit" className="btn-primary aria-disabled:opacity-60" aria-disabled={loading}>
          {loading ? 'Simulando…' : 'Simular'}
        </button>
      </form>

      <div
        role="status"
        aria-live="polite"
        aria-busy={loading}
        className={hasResult ? 'border-t border-border pt-5' : undefined}
      >
        {loading && <p className="type-body text-muted">Simulando con tu monto y tu plazo…</p>}
        {!loading && error !== null && <p className="type-body text-loss">{error}</p>}
        {!loading && outcome?.status === 'simulated' && (
          <SimulatedResult
            entry={outcome.entry}
            dollarMep={outcome.dollarMep}
            amount={submitted?.amount ?? defaultAmount}
            days={submitted?.days ?? defaultDays}
            inflationIndexLabel={inflationIndexLabel}
          />
        )}
        {!loading && outcome?.status === 'ineligible' && (
          <div className="flex flex-col gap-1">
            <p className="type-body font-medium">Con ese monto y ese plazo esta opción no entra</p>
            <p className="type-body text-muted">{outcome.message}</p>
          </div>
        )}
        {!loading && outcome?.status === 'excluded' && (
          <div className="flex flex-col gap-1">
            <p className="type-body font-medium">Esta opción no se puede simular hoy</p>
            <p className="type-body text-muted">{outcome.message}</p>
          </div>
        )}
        {!loading && outcome?.status === 'unknown' && (
          <p className="type-body text-muted">{outcome.message}</p>
        )}
      </div>
    </div>
  );
}
