/**
 * Real-return readout (S10): the sign is text, never color alone, and the
 * readout names the index that produced the number in its accessible name. When
 * the API has no inflation observation it says so instead of inventing a value.
 *
 * T13b: the prototype's "delta" — an arrow, the signed percent and the word
 * "real" in one line — instead of the T12 chip, so the row's grey register
 * stays thin and the pesos earned keeps dominating the row.
 */
import { formatSignedPercent } from '../../lib/format';

export interface RealReturnBadgeProps {
  realReturn: number | null;
  indexLabel: string;
}

export default function RealReturnBadge({ realReturn, indexLabel }: RealReturnBadgeProps) {
  if (realReturn === null) {
    return <span className="type-data text-muted">Sin dato de inflación</span>;
  }

  const isGain = realReturn > 0;
  const isLoss = realReturn < 0;
  const tone = isGain ? 'text-gain' : isLoss ? 'text-loss' : 'text-muted';
  const arrow = isGain ? '▲' : isLoss ? '▼' : '=';

  return (
    <span className={`inline-flex items-baseline gap-1.5 whitespace-nowrap ${tone}`}>
      <span aria-hidden="true" className="type-data">
        {arrow}
      </span>
      <span className="type-data">{formatSignedPercent(realReturn)}</span>
      <span className="type-data text-muted">real</span>
      <span className="sr-only"> contra la inflación esperada ({indexLabel})</span>
    </span>
  );
}
