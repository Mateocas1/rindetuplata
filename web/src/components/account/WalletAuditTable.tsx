/**
 * Audit trail of one curated wallet rate (S12, T13d).
 *
 * A table read the way the ranking table reads: the action is the row header in
 * `type-body`, actor, date and the stored document are mono `type-data` under
 * mono caps `type-label` heads. It keeps its intrinsic width and scrolls inside
 * a focusable, labelled region, so it never spills past the wallet's hairline
 * card on a phone (the ranking table's own pattern).
 *
 * Pure presentation: `WalletAudit` fetches and owns the loading, error and empty
 * states; this half is what a markup test can render.
 */
import type { AuditEntry } from '../../lib/account';
import { auditActionLabel } from '../../lib/accountStatus';
import { formatDate } from '../../lib/format';

const HEAD_CELL = 'border-b border-border px-4 py-3 text-start type-label';

export interface WalletAuditTableProps {
  entries: AuditEntry[];
}

export default function WalletAuditTable({ entries }: WalletAuditTableProps) {
  return (
    <div
      className="relative overflow-x-auto focus-visible:-outline-offset-2"
      role="region"
      aria-label="Historial de cambios de la tasa"
      tabIndex={0}
    >
      <table className="w-full min-w-[34rem] border-separate border-spacing-0">
        <caption className="sr-only">Historial de cambios de la tasa</caption>
        <thead>
          <tr>
            <th scope="col" className={HEAD_CELL}>
              Acción
            </th>
            <th scope="col" className={HEAD_CELL}>
              Quién
            </th>
            <th scope="col" className={HEAD_CELL}>
              Cuándo
            </th>
            <th scope="col" className={HEAD_CELL}>
              Detalle
            </th>
          </tr>
        </thead>
        <tbody>
          {entries.map((entry, index) => {
            const cell = `px-4 py-3 align-top ${index === entries.length - 1 ? '' : 'border-b border-border'}`;
            return (
              <tr key={entry.id}>
                <th scope="row" className={`${cell} text-start type-body font-medium`}>
                  {auditActionLabel(entry.action)}
                </th>
                <td className={`${cell} type-data text-muted`}>{entry.actor}</td>
                <td className={`${cell} type-data text-muted`}>
                  {formatDate(entry.at.slice(0, 10))}
                </td>
                <td className={cell}>
                  <details className="disclosure">
                    <summary className="type-label cursor-pointer">
                      Ver lo que quedó guardado
                    </summary>
                    <pre className="mt-2 max-h-64 max-w-md overflow-auto rounded-panel border border-border bg-subtle-surface p-3 type-data">
                      {JSON.stringify(entry.payload, null, 2)}
                    </pre>
                  </details>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
