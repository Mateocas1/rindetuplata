/**
 * The real result of an option: how many pesos it earns above or below the expected
 * inflation over the horizon, in words, with a sign and the gain or loss tone (never color alone), and
 * the real percentage as the secondary figure. One component for the ranking rows, the best option and the
 * instrument page, so the three never drift.
 *
 * `realEarned` comes from the API; when an older response has no such field the line says the same thing
 * with the percentage. Without an inflation observation it says so instead of inventing a value.
 */
import { formatMoneyWhole, formatSignedPercent } from '../../lib/format';

export interface RealResultProps {
  realEarned?: number | null;
  realReturn: number | null;
  /** The inflation index the API used, for assistive technology ("REM, expectativa a 12 meses"). */
  indexLabel: string;
  /** `row` for table cells and cards, `lead` for the best option and the instrument hero. */
  size?: 'row' | 'lead';
}

export default function RealResult({ realEarned, realReturn, indexLabel, size = 'row' }: RealResultProps) {
  if (realReturn === null) {
    return <span className="type-data text-muted">Sin dato de inflación</span>;
  }

  const direction = realEarned ?? realReturn;
  const isGain = direction > 0;
  const isLoss = direction < 0;
  const tone = isGain ? 'text-gain' : isLoss ? 'text-loss' : 'text-muted';
  const arrow = isGain ? '▲' : isLoss ? '▼' : '=';
  const textRole = size === 'lead' ? 'type-body font-semibold' : 'type-data font-medium';

  let headline: string;
  if (!isGain && !isLoss) {
    headline = 'Igual que la inflación';
  } else {
    const relation = isGain ? 'por encima de la inflación' : 'por debajo de la inflación';
    const amount =
      realEarned === undefined || realEarned === null
        ? formatSignedPercent(realReturn)
        : formatMoneyWhole(Math.abs(realEarned));
    headline = `${amount} ${relation}`;
  }
  const showPercent = realEarned !== undefined && realEarned !== null;

  return (
    <span className="inline-flex flex-wrap items-baseline gap-x-2">
      <span className={`inline-flex items-baseline gap-1.5 ${tone}`}>
        <span aria-hidden="true" className="type-data">
          {arrow}
        </span>
        <span className={textRole}>{headline}</span>
      </span>
      {showPercent && <span className="type-data text-muted whitespace-nowrap">{formatSignedPercent(realReturn)} real</span>}
      <span className="sr-only"> contra la inflación esperada ({indexLabel})</span>
    </span>
  );
}
