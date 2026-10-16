/**
 * Read-only balance bands of one curated wallet rate (S12, T13d).
 *
 * The same table serves the base schedule and each labelled condition; the
 * caller gives it the accessible name of its region. It reads like the ranking
 * table: mono caps `type-label` heads, mono tabular `type-data` figures,
 * right-aligned numbers.
 *
 * A narrow phone cannot fit three money columns: the table keeps its intrinsic
 * width and scrolls inside a focusable, labelled region, so it never spills past
 * the hairline card that holds it (the ranking table's own pattern).
 */
import type { WalletTier } from '../../lib/account';
import { percentToFraction } from '../../lib/accountForm';
import { formatMoney, formatPercent } from '../../lib/format';

/* A narrow phone fits three money columns only with tighter gutters; 320px still
   scrolls inside the region below. */
const HEAD_CELL = 'border-b border-border px-1.5 py-2 type-label text-end sm:px-3';

function bodyCell(isLast: boolean): string {
  return `px-1.5 py-2 type-data sm:px-3 ${isLast ? '' : 'border-b border-border'}`;
}

export interface WalletTierTableProps {
  tiers: WalletTier[];
  /** The accessible name of the scroll region: "Franjas de tasa". */
  label: string;
}

export default function WalletTierTable({ tiers, label }: WalletTierTableProps) {
  if (tiers.length === 0) {
    return <p className="type-body text-muted">Sin franjas.</p>;
  }

  return (
    <div
      className="relative overflow-x-auto focus-visible:-outline-offset-2"
      role="region"
      aria-label={label}
      tabIndex={0}
    >
      <table className="w-full max-w-lg border-collapse">
        <caption className="sr-only">{label}</caption>
        <thead>
          <tr>
            <th scope="col" className={HEAD_CELL}>
              Desde
            </th>
            <th scope="col" className={HEAD_CELL}>
              Hasta
            </th>
            <th scope="col" className={HEAD_CELL}>
              TNA
            </th>
          </tr>
        </thead>
        <tbody>
          {tiers.map((tier, index) => {
            const cell = bodyCell(index === tiers.length - 1);
            return (
              <tr key={index}>
                <td className={`${cell} text-end`}>
                  {tier.floor === null ? 'Sin piso' : formatMoney(tier.floor)}
                </td>
                <td className={`${cell} text-end`}>
                  {tier.ceiling === null ? 'Sin techo' : formatMoney(tier.ceiling)}
                </td>
                <td className={`${cell} text-end`}>
                  {formatPercent(percentToFraction(tier.tnaPercent))}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
