/**
 * Audit trail of one curated wallet rate (S12, T7d).
 *
 * Mounted only when the reader opens the disclosure, so the panel does not
 * fetch every wallet's history up front. Each entry states the action, who did
 * it and when, with the stored document available on demand: a past action
 * stays readable after the rate changes. The table itself is `WalletAuditTable`
 * (T13d), a markup half a test can render; this half owns the fetch.
 */
import { useEffect, useState } from 'react';
import { accountApi, type AuditEntry } from '../../lib/account';
import { ApiProblem } from '../../lib/api';
import { sessionAction, toProblem } from '../../lib/accountStatus';
import ProblemNotice from './ProblemNotice';
import WalletAuditTable from './WalletAuditTable';

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
    return <ProblemNotice problem={problem} title="No pudimos cargar el historial" />;
  }

  if (entries === null) {
    return (
      <p role="status" className="type-body text-muted">
        Cargando el historial…
      </p>
    );
  }

  if (entries.length === 0) {
    return <p className="type-body text-muted">Sin movimientos registrados.</p>;
  }

  return <WalletAuditTable entries={entries} />;
}
