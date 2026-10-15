/**
 * RankingExplorer (S15, S10, S19) — the only island on the site.
 *
 * It is server-rendered with the build-time default ranking, so the table works
 * with JavaScript disabled; once hydrated it re-queries `/api/v1/ranking` for the
 * reader's amount, term, kinds and banks, keeps the query string in sync
 * (`?monto=&dias=&tipo=&cliente=`) and reports loading, error and empty states
 * through an always-rendered `role="status"` region.
 *
 * No financial maths happens here: the API returns the amounts and we format them.
 */
import { useEffect, useMemo, useRef, useState } from 'react';
import { ApiProblem, api } from '../../lib/api';
import { formatDate, formatDays, formatInteger, formatMoney, formatPercent } from '../../lib/format';
import { KIND_OPTIONS, inflationIndexLabel } from '../../lib/labels';
import {
  parseRankingQuery,
  serializeRankingQuery,
  tryParseAmount,
  tryParseDays,
  type RankingQueryState,
} from '../../lib/rankingQuery';
import { RANKING_KINDS, type BankOption, type RankedKind, type Ranking } from '../../lib/types';
import ExcludedList from './ExcludedList';
import RankingTable from './RankingTable';

export interface RankingExplorerProps {
  /** The default ranking, rendered on the server. */
  initialRanking: Ranking;
  initialQuery: RankingQueryState;
  /** Banks that report a plazo fijo rate, for "soy cliente de…" (S19). */
  banks: BankOption[];
}

const DEBOUNCE_MS = 400;
const inputClass =
  'w-full max-w-56 rounded-md border border-border-strong bg-surface px-3 py-2 text-base tabular-nums sm:text-body-sm';
const labelClass = 'text-body-sm font-semibold';

export default function RankingExplorer({
  initialRanking,
  initialQuery,
  banks,
}: RankingExplorerProps) {
  const [ranking, setRanking] = useState<Ranking>(initialRanking);
  const [amount, setAmount] = useState(initialQuery.amount);
  const [days, setDays] = useState(initialQuery.days);
  const [clientOf, setClientOf] = useState<string[]>(initialQuery.clientOf);
  const [checkedKinds, setCheckedKinds] = useState<RankedKind[]>(
    initialQuery.kinds.length > 0 ? initialQuery.kinds : [...RANKING_KINDS],
  );
  const [amountText, setAmountText] = useState(String(initialQuery.amount));
  const [daysText, setDaysText] = useState(String(initialQuery.days));
  const [amountTouched, setAmountTouched] = useState(false);
  const [daysTouched, setDaysTouched] = useState(false);
  const [status, setStatus] = useState<'idle' | 'loading' | 'error'>('idle');
  const [error, setError] = useState<ApiProblem | null>(null);
  const [retryToken, setRetryToken] = useState(0);

  const requestedKey = useRef(`${serializeRankingQuery(initialQuery)}|${0}`);
  const requestId = useRef(0);

  const selection = useMemo<RankingQueryState>(
    () => ({
      amount,
      days,
      // Every kind selected means "no filter" for the API.
      kinds: checkedKinds.length === RANKING_KINDS.length ? [] : checkedKinds,
      clientOf,
    }),
    [amount, days, checkedKinds, clientOf],
  );
  const selectionKey = useMemo(() => serializeRankingQuery(selection), [selection]);

  const parsedAmount = tryParseAmount(amountText);
  const parsedDays = tryParseDays(daysText);
  const amountError =
    amountTouched && parsedAmount === null ? 'Escribí un monto en pesos mayor a 0.' : null;
  const daysError =
    daysTouched && parsedDays === null ? 'Escribí un plazo en días, entre 1 y 3650.' : null;
  const noKinds = checkedKinds.length === 0;
  const invalidSelection = noKinds || parsedAmount === null || parsedDays === null;

  // Adopt a shared URL once on mount (?monto=&dias=&tipo=&cliente=).
  useEffect(() => {
    const fromUrl = parseRankingQuery(window.location.search);
    if (serializeRankingQuery(fromUrl) === serializeRankingQuery(initialQuery)) return;
    setAmount(fromUrl.amount);
    setDays(fromUrl.days);
    setAmountText(String(fromUrl.amount));
    setDaysText(String(fromUrl.days));
    setCheckedKinds(fromUrl.kinds.length > 0 ? fromUrl.kinds : [...RANKING_KINDS]);
    setClientOf(fromUrl.clientOf);
  }, [initialQuery]);

  // Debounce typing so a request is not fired per keystroke.
  useEffect(() => {
    if (parsedAmount === null || parsedAmount === amount) return;
    const timer = window.setTimeout(() => setAmount(parsedAmount), DEBOUNCE_MS);
    return () => window.clearTimeout(timer);
  }, [parsedAmount, amount]);

  useEffect(() => {
    if (parsedDays === null || parsedDays === days) return;
    const timer = window.setTimeout(() => setDays(parsedDays), DEBOUNCE_MS);
    return () => window.clearTimeout(timer);
  }, [parsedDays, days]);

  useEffect(() => {
    if (invalidSelection) return;
    const requestKey = `${selectionKey}|${retryToken}`;
    if (requestedKey.current === requestKey) return;
    requestedKey.current = requestKey;

    window.history.replaceState(null, '', `${window.location.pathname}${selectionKey}`);
    const id = requestId.current + 1;
    requestId.current = id;
    setStatus('loading');
    setError(null);

    api
      .getRanking(selection)
      .then((data) => {
        if (id !== requestId.current) return;
        setRanking(data);
        setStatus('idle');
      })
      .catch((reason: unknown) => {
        if (id !== requestId.current) return;
        setError(
          reason instanceof ApiProblem
            ? reason
            : new ApiProblem(0, 'No se pudo cargar el ranking. Volvé a intentar en un rato.'),
        );
        setStatus('error');
      });
  }, [invalidSelection, retryToken, selection, selectionKey]);

  const toggleKind = (kind: RankedKind) => {
    setCheckedKinds((current) =>
      current.includes(kind) ? current.filter((value) => value !== kind) : [...current, kind],
    );
  };

  const toggleBank = (id: string) => {
    setClientOf((current) =>
      current.includes(id) ? current.filter((value) => value !== id) : [...current, id],
    );
  };

  const statusMessage = (() => {
    if (noKinds) return 'Elegí al menos un tipo de opción para ver el ranking.';
    if (parsedAmount === null || parsedDays === null) {
      return 'Revisá el monto y el plazo para ver el ranking.';
    }
    if (status === 'loading') {
      return `Buscando opciones para ${formatMoney(parsedAmount)} a ${formatDays(parsedDays)}…`;
    }
    if (status === 'error') return 'No se pudo cargar el ranking.';
    return `${formatInteger(ranking.ranked.length)} de ${formatInteger(
      ranking.totals.ranked,
    )} opciones para ${formatMoney(ranking.request.amount)} a ${formatDays(ranking.request.days)}.`;
  })();

  return (
    <div className="flex flex-col gap-section">
      <section aria-labelledby="armar-ranking" className="flex flex-col gap-block">
        <h2 id="armar-ranking" className="text-h2 font-semibold">
          Armá tu ranking
        </h2>
        <div className="flex flex-col gap-6 rounded-lg border border-border bg-surface-raised p-block sm:p-6">
          <div className="flex flex-wrap items-end gap-x-8 gap-y-block">
            <div className="flex flex-col gap-1">
              <label htmlFor="monto" className={labelClass}>
                Monto en pesos
              </label>
              <input
                id="monto"
                name="monto"
                type="number"
                inputMode="numeric"
                min="1"
                step="any"
                value={amountText}
                aria-invalid={amountError !== null}
                aria-describedby={amountError !== null ? 'monto-error' : undefined}
                onChange={(event) => {
                  setAmountTouched(true);
                  setAmountText(event.target.value);
                }}
                className={inputClass}
              />
              {amountError !== null && (
                <p id="monto-error" className="text-body-sm text-loss">
                  {amountError}
                </p>
              )}
            </div>
            <div className="flex flex-col gap-1">
              <label htmlFor="dias" className={labelClass}>
                Plazo en días
              </label>
              <input
                id="dias"
                name="dias"
                type="number"
                inputMode="numeric"
                min="1"
                max="3650"
                step="1"
                value={daysText}
                aria-invalid={daysError !== null}
                aria-describedby={daysError !== null ? 'dias-error' : undefined}
                onChange={(event) => {
                  setDaysTouched(true);
                  setDaysText(event.target.value);
                }}
                className={inputClass}
              />
              {daysError !== null && (
                <p id="dias-error" className="text-body-sm text-loss">
                  {daysError}
                </p>
              )}
            </div>
          </div>

          <fieldset
            className="flex flex-col gap-2"
            aria-describedby={noKinds ? 'tipos-ayuda tipos-error' : 'tipos-ayuda'}
          >
            <legend className={labelClass}>Tipos de opción</legend>
            <p id="tipos-ayuda" className="text-caption text-muted">
              Con los cuatro marcados comparás todo. Si sacás alguno, el ranking solo muestra los
              que quedan.
            </p>
            <div className="flex flex-wrap gap-x-6 gap-y-2">
              {KIND_OPTIONS.map((option) => (
                <label key={option.value} className="flex items-center gap-2 text-body-sm">
                  <input
                    type="checkbox"
                    className="size-6 accent-accent-solid"
                    checked={checkedKinds.includes(option.value)}
                    onChange={() => toggleKind(option.value)}
                  />
                  {option.label}
                </label>
              ))}
            </div>
            {noKinds && (
              <p id="tipos-error" className="text-body-sm text-loss">
                Elegí al menos un tipo de opción.
              </p>
            )}
          </fieldset>

          <fieldset className="flex flex-col gap-2">
            <legend className={labelClass}>Soy cliente de… (opcional)</legend>
            <p className="text-caption text-muted">
              Marcá tus bancos y el ranking de plazos fijos usa la tasa para clientes (S19).
            </p>
            <details className="rounded-md border border-border bg-surface px-4 py-3">
              <summary className="cursor-pointer text-body-sm text-accent">
                {clientOf.length === 0
                  ? 'Elegir bancos'
                  : `${formatInteger(clientOf.length)} ${
                      clientOf.length === 1 ? 'banco marcado' : 'bancos marcados'
                    }`}
              </summary>
              <div className="mt-3 max-h-80 overflow-y-auto overscroll-contain pe-2">
                <ul className="flex flex-col gap-2">
                  {banks.map((bank) => (
                    <li key={bank.id}>
                      <label className="flex items-center gap-2 text-body-sm">
                        <input
                          type="checkbox"
                          className="size-6 accent-accent-solid"
                          checked={clientOf.includes(bank.id)}
                          onChange={() => toggleBank(bank.id)}
                        />
                        <span className="min-w-0 break-words">{bank.name}</span>
                      </label>
                    </li>
                  ))}
                </ul>
              </div>
            </details>
          </fieldset>
        </div>
      </section>

      <section aria-labelledby="resultado" className="flex flex-col gap-block">
        <div className="flex flex-col gap-2">
          <h2 id="resultado" className="text-h2 font-semibold">
            El ranking de hoy
          </h2>
          <p role="status" className="text-body-sm text-muted">
            {statusMessage}
          </p>
          {status !== 'error' && ranking.inflation !== null && (
            <p className="text-body-sm text-muted">
              El rendimiento real compara contra la inflación esperada del{' '}
              {inflationIndexLabel(ranking.inflation.index)}:{' '}
              {formatPercent(ranking.inflation.twelveMonthRate)} a 12 meses,{' '}
              {formatPercent(ranking.inflation.overHorizon)} en {formatDays(ranking.request.days)}.
              Observación del {formatDate(ranking.inflation.observationDate)}.
            </p>
          )}
          {status !== 'error' && ranking.inflation === null && (
            <p className="text-body-sm text-muted">
              No hay dato de inflación disponible, así que no mostramos rendimiento real: las
              tasas y los montos son los que devuelve la API.
            </p>
          )}
        </div>

        <div aria-busy={status === 'loading'}>
          {status === 'error' && error !== null ? (
            <div
              role="alert"
              className="flex flex-col gap-2 rounded-lg border border-loss bg-loss-surface px-4 py-3"
            >
              <p className="font-semibold text-on-loss">No se pudo cargar el ranking</p>
              <p className="text-body-sm text-on-loss">{error.message}</p>
              <button
                type="button"
                onClick={() => setRetryToken((token) => token + 1)}
                className="self-start rounded-md bg-accent-solid px-4 py-2 text-body-sm font-semibold text-on-accent hover:bg-accent"
              >
                Reintentar
              </button>
            </div>
          ) : ranking.ranked.length === 0 ? (
            <div className="flex flex-col gap-2 rounded-lg border border-border bg-surface-raised p-block">
              <p className="font-medium">No hay opciones para este monto y plazo</p>
              <p className="text-body-sm text-muted">
                Probá con otro monto, con más días o marcando más tipos de opción. Más abajo están
                las opciones que quedaron afuera y por qué.
              </p>
            </div>
          ) : (
            <RankingTable ranking={ranking} />
          )}
        </div>

        <ExcludedList ranking={ranking} />
      </section>
    </div>
  );
}
