/**
 * The best option of the query the reader is looking at, as a plain card with the cobalt rail (ui-clarity D14:
 * the rail marks only the best option, the same job as the rail of row 01).
 *
 * It restates the first row of the board as an answer: the pesos earned as the figure, the display name (D13)
 * with its kind and when the money is available (D5), the real result in words (D6, the shared `RealResult`),
 * the final amount (D8: it lives here and on the instrument page, not in the rows), the rate and one line of
 * provenance. Every figure comes from the API payload; the card only formats it (S14).
 */
import { liquidityForKind } from '../../lib/categories';
import { displayName } from '../../lib/displayName';
import { formatDate, formatDays, formatMoney, formatPercent } from '../../lib/format';
import { inflationIndexLabel, sourceLabel, kindLabel, rateKindShortLabel } from '../../lib/labels';
import type { RankedEntry, Ranking } from '../../lib/types';
import RealResult from '../ui/RealResult';
import { formatQueryAmount } from './positions';

export interface BestOptionPlateProps {
  ranking: Ranking;
  /** The first ranked option of the same ranking. */
  best: RankedEntry;
}

export default function BestOptionPlate({ ranking, best }: BestOptionPlateProps) {
  const { amount, days } = ranking.request;
  const inflation = ranking.inflation;
  const liquidity = liquidityForKind(best.kind);
  // A plazo fijo matures; a wallet, a fund or a crypto balance is simply worth this much after the term.
  const when = best.kind === 'PLAZO_FIJO' ? 'al vencimiento' : `a los ${formatDays(days)}`;
  // "$ 19.726,03" is "$", a no-break space and the number (format.ts); split it so only that space can wrap.
  const [currency, figure = ''] = formatMoney(best.earned).split('\u00a0');
  const source = sourceLabel(best);

  return (
    <div className="card border-s-2 border-s-accent p-4 sm:p-6">
      <p className="type-label">La mejor de hoy · {kindLabel(best.kind)}</p>
      {/* The number never breaks inside itself; on a very narrow card the sign may wrap above it instead. */}
      <p className="type-figure mt-3">
        {currency}{' '}
        <span className="whitespace-nowrap">{figure}</span>
      </p>
      <p className="type-data mt-1 text-muted">
        ganás en {formatDays(days)} con {formatQueryAmount(amount)}
      </p>

      <p className="type-body mt-5 font-semibold break-words">{displayName(best.name)}</p>
      <p className="type-data mt-0.5 text-muted">
        {kindLabel(best.kind)}
        {liquidity === null ? '' : ` · ${liquidity}`}
      </p>

      <div className="mt-5 flex flex-col gap-1 border-t border-border pt-5">
        <RealResult
          realEarned={best.realEarned}
          realReturn={best.realReturn}
          indexLabel={inflation === null ? 'sin dato de inflación' : inflationIndexLabel(inflation.index)}
          size="lead"
        />
        <p className="type-body">
          Tenés {formatMoney(best.finalAmount)} {when}.
        </p>
      </div>

      <p className="type-data mt-5">
        {formatPercent(best.rateUsed.fraction)} <span className="text-muted">{rateKindShortLabel(best.rateUsed.kind)}</span>
      </p>
      <p className="type-data mt-0.5 text-muted">
        {source} · {formatDate(best.dataDate)}
      </p>
    </div>
  );
}
