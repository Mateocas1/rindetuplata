/**
 * The best option of the query the reader is looking at, as the one frosted
 * glass card of the page (T13b, L80).
 *
 * It restates the first row of the board: the same fact, so the cobalt it
 * spends (the 2px rail on its start edge) is the same job as the serif "plata"
 * of the headline and the "Mejor" chip of row 01. The glass is blurred over the
 * cobalt glow and hairline grid its `glass-field` parent paints, and the parent
 * that clips the overflow is the hero band in the page.
 *
 * Every figure comes from the API payload; the card only formats it (S14).
 */
import {
  formatDate,
  formatDays,
  formatMoney,
  formatPercent,
  formatSignedPercent,
} from '../../lib/format';
import { inflationIndexShortLabel, kindLabel } from '../../lib/labels';
import type { RankedEntry, Ranking } from '../../lib/types';

export interface BestOptionPlateProps {
  ranking: Ranking;
  /** The first ranked option of the same ranking. */
  best: RankedEntry;
}

export default function BestOptionPlate({ ranking, best }: BestOptionPlateProps) {
  const { amount, days } = ranking.request;
  const inflation = ranking.inflation;
  const real = best.realReturn;

  return (
    <div className="glass-field">
      <div className="glass border-s-2 border-s-accent p-6">
        <p className="type-label">La mejor de hoy · {kindLabel(best.kind)}</p>
        <p className="type-figure mt-2.5 break-words">{formatMoney(best.earned)}</p>
        <p className="type-body mt-2 font-medium">{best.name}</p>
        <p className="type-data mt-0.5 text-muted">
          en {formatDays(days)} con {formatMoney(amount)}
        </p>

        <dl className="mt-6 grid grid-cols-3 gap-4 border-t border-border pt-5">
          <div className="flex flex-col justify-between gap-2">
            <dt className="type-label">TNA</dt>
            <dd className="type-data">{formatPercent(best.rateUsed.fraction)}</dd>
          </div>
          <div className="flex flex-col justify-between gap-2">
            <dt className="type-label">Retorno real</dt>
            <dd className="type-data">{real === null ? 'sin dato' : formatSignedPercent(real)}</dd>
          </div>
          <div className="flex flex-col justify-between gap-2">
            <dt className="type-label">
              {inflation === null ? 'Inflación' : `Inflación ${inflationIndexShortLabel(inflation.index)}`}
            </dt>
            <dd className="type-data">
              {inflation === null ? 'sin dato' : formatPercent(inflation.overHorizon)}
            </dd>
          </div>
        </dl>

        <p className="type-data mt-5 text-muted">
          {best.source.displayName} · dato del {formatDate(best.dataDate)}
        </p>
      </div>
    </div>
  );
}
