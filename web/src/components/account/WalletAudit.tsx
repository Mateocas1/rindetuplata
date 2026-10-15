/**
 * Audit trail of one curated wallet rate (S12, T7d).
 *
 * Mounted only when the reader opens the disclosure, so the panel does not
 * fetch every wallet's history up front. Each entry states the action, who did
 * it and when, with the stored document available on demand: a past action
 * stays readable after the rate changes.
 *
 * T12c: a table read the way the ranking table reads — the action is the row
 * header, actor and date are caption-muted with tabular figures — inside the
 * wallet's own card, and a failed load goes through `ProblemNotice`.
 */
import { useEffect, useState } from 'react';
import { accountApi, type AuditEntry } from '../../lib/account';
import { ApiProblem } from '../../lib/api';
import { auditActionLabel, sessionAction, toProblem } from '../../lib/accountStatus';
import { formatDate } from '../../lib/format';
import ProblemNotice from './ProblemNotice';

const LOGIN_PATH = '/ingresar';

const HEAD_CELL = 'border-b border-border px-4 py-3 text-caption font-semibold text-muted';

export interface WalletAuditProps {
  walletId: string;
  onProblem: (problem: ApiProblem) => void;
}

export default function WalletAudit({ walletId, onProblem }: WalletAuditProps) {
  const [entries, setEntries] = useState<AuditEntry[] | null>(null);
  const [problem, setProblem] = useState<ApiProblem | null>(null);

  useEffect(() => {
    let active = true;
    accountApi
      .loadWalletAudit(walletId)
      .then((list) => {
        if (active) setEntries(list);
      })
      .catch((reason: unknown) => {
        if (!active) return;
        const failure = toProblem(reason);
        if (sessionAction(failure.status) === 'login') {
          window.location.assign(LOGIN_PATH);
          return;
        }
        setProblem(failure);
        onProblem(failure);
      });
    return () => {
      active = false;
    };
  }, [walletId, onProblem]);

  if (problem !== null) {
    return <ProblemNotice problem={problem} title="No pudimos cargar el historial" />;
  }

  if (entries === null) {
    return (
      <p role="status" className="text-body-sm text-muted">
        Cargando el historial…
      </p>
    );
  }

  if (entries.length === 0) {
    return <p className="text-body-sm text-muted">Sin movimientos registrados.</p>;
  }

  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[34rem] border-separate border-spacing-0 text-body-sm">
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
                <th scope="row" className={`${cell} text-start font-medium`}>
                  {auditActionLabel(entry.action)}
                </th>
                <td className={`${cell} text-muted`}>{entry.actor}</td>
                <td className={`${cell} text-muted tabular-nums`}>
                  {formatDate(entry.at.slice(0, 10))}
                </td>
                <td className={cell}>
                  <details>
                    <summary className="link cursor-pointer text-caption">
                      Ver lo que quedó guardado
                    </summary>
                    <pre className="mt-2 max-h-64 max-w-md overflow-auto rounded-md border border-border bg-subtle-surface p-3 text-caption">
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
