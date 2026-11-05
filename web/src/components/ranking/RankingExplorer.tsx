/**
 * RankingExplorer (S15, S10, S19) — the home island.
 *
 * It is server-rendered with the build-time default ranking, so the page works with JavaScript disabled; once
 * hydrated it re-queries `/api/v1/ranking` for the reader's amount, term, kinds and banks, keeps the query string
 * in sync (`?monto=&dias=&tipo=&cliente=`) and reports loading, error and empty states through an always-rendered
 * `role="status"` region.
 *
 * The island opens with the answer to the page's question (ui-clarity D15): one sentence, rendered on the server
 * and updated with the query. Then the controls: the amount and the term, each a free field next to its capsule
 * presets on one grid whose label row shares a baseline (D16); the amount field reads in Argentine notation
 * ("1.000.000") when it is not being edited. The presets are shortcuts over the same query state: picking one goes
 * through `applyAmountPreset` / `applyDaysPreset`, and the reader's own value leaves the control unselected. Kinds
 * and banks stay behind one disclosure; the best option sits beside the controls and the board follows.
 *
 * No financial maths happens here: the API returns the amounts and we format them.
 */
import { useEffect, useMemo, useRef, useState } from 'react';
import { formatAmountInput, parseAmountInput } from '../../lib/amountInput';
import { ApiProblem, api } from '../../lib/api';
import { displayName } from '../../lib/displayName';
import { formatDays, formatInteger } from '../../lib/format';
import { KIND_OPTIONS } from '../../lib/labels';
import {
  MAX_DAYS,
  parseRankingQuery,
  serializeRankingQuery,
  tryParseDays,
  type RankingQueryState,
} from '../../lib/rankingQuery';
import { RANKING_KINDS, type BankOption, type RankedKind, type Ranking } from '../../lib/types';
import Segmented from '../ui/Segmented';
import BestOptionPlate from './BestOptionPlate';
import ExcludedList from './ExcludedList';
import RankingTable, { RankingFootnote } from './RankingTable';
import { answerSentence } from './answer';
import { formatQueryAmount } from './positions';
import { AMOUNT_PRESETS, DAYS_PRESETS, applyAmountPreset, applyDaysPreset, matchPreset } from './presets';

export interface RankingExplorerProps {
  /** The default ranking, rendered on the server. */
  initialRanking: Ranking;
  initialQuery: RankingQueryState;
  /** Banks that report a plazo fijo rate, for "soy cliente de…". */
  banks: BankOption[];
  /** Slugs the build produced; rows outside it render as plain text, never a 404 link. */
  pageSlugs: string[];
}

const DEBOUNCE_MS = 400;
/** The board is the day's best eight; the rest follow behind a disclosure. */
const BOARD_SIZE = 8;
// One recipe, one place (T12a): the field shape comes from `field`; its height matches the capsule control
// beside it (40px segments + 3px track + 1px border, each side), so the two controls of a row line up.
const inputClass = 'field h-12 tabular-nums';
const labelClass = 'type-label';

export default function RankingExplorer({
  initialRanking,
  initialQuery,
  banks,
  pageSlugs,
}: RankingExplorerProps) {
  const [ranking, setRanking] = useState<Ranking>(initialRanking);
  const [amount, setAmount] = useState(initialQuery.amount);
  const [days, setDays] = useState(initialQuery.days);
  const [clientOf, setClientOf] = useState<string[]>(initialQuery.clientOf);
  const [checkedKinds, setCheckedKinds] = useState<RankedKind[]>(
    initialQuery.kinds.length > 0 ? initialQuery.kinds : [...RANKING_KINDS],
  );
  const [amountText, setAmountText] = useState(formatAmountInput(initialQuery.amount));
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

  const parsedAmount = parseAmountInput(amountText);
  const parsedDays = tryParseDays(daysText);
  const amountError =
    amountTouched && parsedAmount === null
      ? 'Escribí un monto en pesos mayor a 0, por ejemplo 1.000.000.'
      : null;
  const daysError =
    daysTouched && parsedDays === null ? `Escribí un plazo en días, entre 1 y ${MAX_DAYS}.` : null;
  const noKinds = checkedKinds.length === 0;
  const invalidSelection = noKinds || parsedAmount === null || parsedDays === null;

  /** Adopt a whole selection: a preset pick, or the query string of a shared URL. */
  const adopt = (next: RankingQueryState) => {
    setAmount(next.amount);
    setDays(next.days);
    setAmountText(formatAmountInput(next.amount));
    setDaysText(String(next.days));
    setAmountTouched(true);
    setDaysTouched(true);
    setCheckedKinds(next.kinds.length > 0 ? next.kinds : [...RANKING_KINDS]);
    setClientOf(next.clientOf);
  };

  // Adopt a shared URL once on mount (?monto=&dias=&tipo=&cliente=).
  useEffect(() => {
    const fromUrl = parseRankingQuery(window.location.search);
    if (serializeRankingQuery(fromUrl) === serializeRankingQuery(initialQuery)) return;
    adopt(fromUrl);
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
      return `Buscando opciones para ${formatQueryAmount(parsedAmount)} a ${formatDays(parsedDays)}…`;
    }
    if (status === 'error') return 'No se pudo cargar el ranking.';
    return `${formatInteger(ranking.ranked.length)} de ${formatInteger(
      ranking.totals.ranked,
    )} opciones para ${formatQueryAmount(ranking.request.amount)} a ${formatDays(
      ranking.request.days,
    )}.`;
  })();

  const best = ranking.ranked[0];
  const rest = Math.max(ranking.ranked.length - BOARD_SIZE, 0);

  return (
    <div className="flex flex-col gap-section">
      {/* The answer to the H1, for the ranking on screen: it names its own amount and term, so it stays true
          while a new query loads. Like the best-option card, it steps aside when a query fails, so it never
          answers a question the reader stopped asking next to the error. */}
      {status !== 'error' && (
        <p className="type-body text-lead max-w-prose font-medium text-text">{answerSentence(ranking)}</p>
      )}

      <section aria-labelledby="armar-ranking">
        <h2 id="armar-ranking" className="sr-only">
          Armá tu ranking
        </h2>
        <div className="grid gap-section lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)] lg:items-start">
          <div className="flex min-w-0 flex-col gap-6">
            <div className="grid grid-cols-1 gap-x-6 gap-y-5 sm:grid-cols-[minmax(0,13rem)_max-content]">
              <div className="flex min-w-0 flex-col gap-2">
                <label htmlFor="monto" className={labelClass}>
                  Monto en pesos
                </label>
                <input
                  id="monto"
                  name="monto"
                  type="text"
                  inputMode="numeric"
                  autoComplete="off"
                  value={amountText}
                  aria-invalid={amountError !== null}
                  aria-describedby={amountError !== null ? 'monto-error' : undefined}
                  onChange={(event) => {
                    setAmountTouched(true);
                    setAmountText(event.target.value);
                  }}
                  onBlur={() => {
                    // On blur a valid amount takes its grouped form; an invalid one stays as typed, with its message.
                    if (parsedAmount !== null) setAmountText(formatAmountInput(parsedAmount));
                  }}
                  className={inputClass}
                />
                {amountError !== null && (
                  <p id="monto-error" className="type-body text-loss">
                    {amountError}
                  </p>
                )}
              </div>
              <Segmented
                name="monto-preset"
                legend="Montos frecuentes"
                options={AMOUNT_PRESETS}
                selected={matchPreset(AMOUNT_PRESETS, amount)}
                onSelect={(value) => adopt(applyAmountPreset(selection, value))}
              />

              <div className="flex min-w-0 flex-col gap-2">
                <label htmlFor="dias" className={labelClass}>
                  Plazo en días
                </label>
                <input
                  id="dias"
                  name="dias"
                  type="text"
                  inputMode="numeric"
                  autoComplete="off"
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
                  <p id="dias-error" className="type-body text-loss">
                    {daysError}
                  </p>
                )}
              </div>
              <Segmented
                name="plazo-preset"
                legend="Plazos frecuentes"
                options={DAYS_PRESETS}
                selected={matchPreset(DAYS_PRESETS, days)}
                onSelect={(value) => adopt(applyDaysPreset(selection, value))}
              />
            </div>

            <details className="disclosure card px-4 py-3">
              <summary className="type-label text-text">
                Más filtros · {formatInteger(checkedKinds.length)} de {KIND_OPTIONS.length} tipos
                {clientOf.length === 0
                  ? ''
                  : ` · ${formatInteger(clientOf.length)} ${
                      clientOf.length === 1 ? 'banco' : 'bancos'
                    }`}
              </summary>
              <div className="mt-4 flex flex-col gap-6">
                <fieldset
                  className="flex flex-col gap-2 border-0 p-0"
                  aria-describedby={noKinds ? 'tipos-ayuda tipos-error' : 'tipos-ayuda'}
                >
                  <legend className={labelClass}>Tipos de opción</legend>
                  <p id="tipos-ayuda" className="type-data text-muted">
                    Con los cuatro marcados comparás todo. Si sacás alguno, el ranking solo muestra
                    los que quedan.
                  </p>
                  <div className="flex flex-wrap gap-x-6 gap-y-2">
                    {KIND_OPTIONS.map((option) => (
                      <label key={option.value} className="flex items-center gap-2 type-body">
                        <input
                          type="checkbox"
                          className="size-6 accent-text"
                          checked={checkedKinds.includes(option.value)}
                          onChange={() => toggleKind(option.value)}
                        />
                        {option.label}
                      </label>
                    ))}
                  </div>
                  {noKinds && (
                    <p id="tipos-error" className="type-body text-loss">
                      Elegí al menos un tipo de opción.
                    </p>
                  )}
                </fieldset>

                <fieldset className="flex flex-col gap-2 border-0 p-0">
                  <legend className={labelClass}>Soy cliente de… (opcional)</legend>
                  <p className="type-data text-muted">
                    Marcá tus bancos y el ranking de plazos fijos usa la tasa para clientes.
                  </p>
                  <div className="max-h-80 overflow-y-auto overscroll-contain pe-2">
                    <ul className="flex flex-col gap-2">
                      {banks.map((bank) => (
                        <li key={bank.id}>
                          <label className="flex items-center gap-2 type-body">
                            <input
                              type="checkbox"
                              className="size-6 accent-text"
                              checked={clientOf.includes(bank.id)}
                              onChange={() => toggleBank(bank.id)}
                            />
                            <span className="min-w-0 break-words">{displayName(bank.name)}</span>
                          </label>
                        </li>
                      ))}
                    </ul>
                  </div>
                </fieldset>
              </div>
            </details>
          </div>

          <div className="min-w-0">
            {status !== 'error' && best !== undefined && (
              <BestOptionPlate ranking={ranking} best={best} />
            )}
          </div>
        </div>
      </section>

      <section aria-labelledby="resultado" className="flex flex-col gap-block">
        <div className="flex flex-wrap items-baseline justify-between gap-x-8 gap-y-2">
          <h2 id="resultado" className="type-section">
            El ranking de hoy
          </h2>
          <p role="status" className="type-label">
            {statusMessage}
          </p>
        </div>

        <div aria-busy={status === 'loading'}>
          {status === 'error' && error !== null ? (
            <div className="card flex flex-col gap-2 border-loss bg-loss-surface p-6" role="alert">
              <p className="type-body font-semibold text-on-loss">No se pudo cargar el ranking</p>
              <p className="type-body text-on-loss">{error.message}</p>
              <button
                type="button"
                onClick={() => setRetryToken((token) => token + 1)}
                className="btn-primary self-start"
              >
                Reintentar
              </button>
            </div>
          ) : ranking.ranked.length === 0 ? (
            <div className="card flex flex-col gap-2 p-6">
              <p className="type-body font-medium">No hay opciones para este monto y plazo</p>
              <p className="type-body text-muted">
                Probá con otro monto, con más días o marcando más tipos de opción. Más abajo están
                las opciones que quedaron afuera y por qué.
              </p>
            </div>
          ) : (
            <>
              <RankingTable ranking={ranking} pageSlugs={pageSlugs} limit={BOARD_SIZE} footnote={false} />
              {rest > 0 && (
                <details className="disclosure mt-6">
                  <summary className="type-label text-text">
                    Las {formatInteger(rest)} opciones siguientes
                  </summary>
                  <div className="mt-4">
                    <RankingTable ranking={ranking} pageSlugs={pageSlugs} offset={BOARD_SIZE} />
                  </div>
                </details>
              )}
            </>
          )}
        </div>

        <ExcludedList ranking={ranking} />
        {status !== 'error' && ranking.ranked.length > 0 && <RankingFootnote ranking={ranking} />}
      </section>
    </div>
  );
}
