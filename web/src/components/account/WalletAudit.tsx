/**
 * Audit trail of one curated wallet rate (S12, T7d).
 *
 * Mounted only when the reader opens the disclosure, so the panel does not
 * fetch every wallet's history up front. Each entry states the action, who did
 * it and when, with the stored document available on demand: a past action
 * stays readable after the rate changes.
 */
import { useEffect, useState } from 'react';
import { accountApi, type AuditEntry } from '../../lib/account';
import { ApiProblem } from '../../lib/api';
import { auditActionLabel, problemText, sessionAction, toProblem } from '../../lib/accountStatus';
import { formatDate } from '../../lib/format';

const LOGIN_PATH = '/ingresar';

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
    return <p className="text-body-sm text-loss">{problemText(problem)}</p>;
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
    <ol className="flex flex-col gap-3">
      {entries.map((entry) => (
        <li key={entry.id} className="flex flex-col gap-1 border-s-2 border-border ps-3">
          <p className="text-body-sm font-medium">
            {auditActionLabel(entry.action)}
          </p>
          <p className="text-caption text-muted">
            {entry.actor} · {formatDate(entry.at.slice(0, 10))}
          </p>
          <details>
            <summary className="cursor-pointer text-caption text-accent">
              Ver lo que quedó guardado
            </summary>
            <pre className="mt-2 max-h-64 overflow-auto rounded-md border border-border bg-surface p-3 text-caption">
              {JSON.stringify(entry.payload, null, 2)}
            </pre>
          </details>
        </li>
      ))}
    </ol>
  );
}
