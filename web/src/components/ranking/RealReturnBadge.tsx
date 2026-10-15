/**
 * Real-return badge (S10): the sign is text, never color alone, and the badge
 * names the index that produced the number in its accessible name. When the API
 * has no inflation observation the badge says so instead of inventing a value.
 * It is a `chip` like every other flag (T12b), with the tone as its surface.
 */
import { formatSignedPercent } from '../../lib/format';

export interface RealReturnBadgeProps {
  realReturn: number | null;
  indexLabel: string;
}

export default function RealReturnBadge({ realReturn, indexLabel }: RealReturnBadgeProps) {
  if (realReturn === null) {
    return <span className="chip bg-subtle-surface text-muted">Sin dato de inflación</span>;
  }

  const isGain = realReturn > 0;
  const isLoss = realReturn < 0;
  const tone = isGain
    ? 'bg-gain-surface text-on-gain'
    : isLoss
      ? 'bg-loss-surface text-on-loss'
      : 'bg-subtle-surface text-muted';
  const arrow = isGain ? '▲' : isLoss ? '▼' : '=';

  return (
    <span className={`chip ${tone}`}>
      <span aria-hidden="true">{arrow}</span>
      <span className="tabular-nums">{formatSignedPercent(realReturn)}</span>
      <span className="sr-only"> real contra la inflación esperada ({indexLabel})</span>
    </span>
  );
}
