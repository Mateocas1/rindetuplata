/**
 * `/admin/tasas` island (S12, T7d) — client only.
 *
 * Curated wallet rates: today's document, how long ago it was verified (stale
 * ones flagged, never shown as current), an edit form, a one-click confirm and
 * the audit trail per wallet. A 403 becomes the "Sin permiso" state; a 401
 * leaves for `/ingresar`.
 *
 * T12c/T13d: one `card` per wallet, the tier tables read like the ranking table
 * (mono caps `type-label` heads, mono tabular `type-data` figures, right-aligned
 * numbers), each wallet carries one quiet mono provenance line, and every notice
 * — the confirmation, a failure, the 403 — goes through the shared surfaces.
 */
import { useCallback, useEffect, useState } from 'react';
import { accountApi, type WalletRate } from '../../lib/account';
import { ApiProblem } from '../../lib/api';
import { FORBIDDEN_MESSAGE, isStale, sessionAction, stalenessText, toProblem } from '../../lib/accountStatus';
import { formatDate } from '../../lib/format';
import ProblemNotice from './ProblemNotice';
import StatusNotice from './StatusNotice';
import WalletAudit from './WalletAudit';
import WalletRateEditor from './WalletRateEditor';
import WalletTierTable from './WalletTierTable';
import { buttonSecondaryClass, cardClass } from './ui';

const LOGIN_PATH = '/ingresar';

type Status = 'loading' | 'ready' | 'forbidden' | 'error';

export default function WalletRatesPanel() {
  const [status, setStatus] = useState<Status>('loading');
  const [rates, setRates] = useState<WalletRate[]>([]);
  const [problem, setProblem] = useState<ApiProblem | null>(null);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [confirmingId, setConfirmingId] = useState<string | null>(null);
  const [auditOpenId, setAuditOpenId] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const load = useCallback(async (): Promise<void> => {
    setStatus('loading');
    setProblem(null);
    try {
      setRates(await accountApi.listWalletRates());
      setStatus('ready');
    } catch (reason) {
      const failure = toProblem(reason);
      if (sessionAction(failure.status) === 'login') {
        window.location.assign(LOGIN_PATH);
        return;
      }
      if (sessionAction(failure.status) === 'forbidden') {
        setStatus('forbidden');
        return;
      }
      setProblem(failure);
      setStatus('error');
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const handleProblem = useCallback((failure: ApiProblem): void => {
    if (sessionAction(failure.status) === 'login') {
      window.location.assign(LOGIN_PATH);
      return;
    }
    if (sessionAction(failure.status) === 'forbidden') {
      setStatus('forbidden');
      return;
    }
    setProblem(failure);
  }, []);

  async function onConfirm(walletId: string): Promise<void> {
    setConfirmingId(walletId);
    setProblem(null);
    setMessage(null);
    try {
      const saved = await accountApi.confirmWalletRate(walletId);
      setRates((current) => current.map((rate) => (rate.walletId === walletId ? saved : rate)));
      setMessage(`${saved.name}: tasa confirmada hoy.`);
    } catch (reason) {
      handleProblem(toProblem(reason));
    } finally {
      setConfirmingId(null);
    }
  }

  if (status === 'loading') {
    return (
      <p role="status" className="type-body text-muted">
        Cargando las tasas cargadas a mano…
      </p>
    );
  }

  if (status === 'forbidden') {
    return (
      <ProblemNotice problem={{ status: 403, message: FORBIDDEN_MESSAGE }} title="Sin permiso">
        <a href="/" className={`${buttonSecondaryClass} mt-1 self-start`}>
          Volver al ranking
        </a>
      </ProblemNotice>
    );
  }

  if (status === 'error' && problem !== null) {
    return <ProblemNotice problem={problem} onRetry={() => void load()} />;
  }

  return (
    <div className="flex flex-col gap-section">
      {problem !== null && <ProblemNotice problem={problem} />}
      {message !== null && <StatusNotice role="status" tone="gain" title={message} />}

      {rates.length === 0 ? (
        <div className={`${cardClass} flex flex-col gap-2`}>
          <p className="type-body font-medium">No hay tasas cargadas a mano</p>
          <p className="type-body text-muted">
            Cuando exista una, va a aparecer acá para editarla, confirmarla y ver su historial.
          </p>
        </div>
      ) : (
        <ul className="flex flex-col gap-4">
          {rates.map((rate) => {
            const stale = isStale(rate.daysSinceVerified);
            return (
              <li key={rate.walletId} className={`${cardClass} flex flex-col gap-3`}>
                {editingId === rate.walletId ? (
                  <WalletRateEditor
                    rate={rate}
                    onProblem={handleProblem}
                    onCancel={() => setEditingId(null)}
                    onSaved={(saved) => {
                      setRates((current) =>
                        current.map((entry) => (entry.walletId === rate.walletId ? saved : entry)),
                      );
                      setEditingId(null);
                      setMessage(`${saved.name}: tasa guardada y verificada hoy.`);
                    }}
                  />
                ) : (
                  <>
                    <div className="flex flex-wrap items-start justify-between gap-3">
                      <div className="flex min-w-0 flex-col gap-1">
                        <h2 className="type-section">{rate.name}</h2>
                        <p className={stale ? 'type-data text-warning' : 'type-data text-muted'}>
                          {rate.walletId} · {stalenessText(rate.daysSinceVerified)} ·{' '}
                          {formatDate(rate.updatedAt.slice(0, 10))} por {rate.updatedBy}
                        </p>
                      </div>
                      <div className="flex flex-wrap gap-2">
                        <button
                          type="button"
                          onClick={() => {
                            setEditingId(rate.walletId);
                            setMessage(null);
                          }}
                          className={buttonSecondaryClass}
                        >
                          Editar {rate.name}
                        </button>
                        <button
                          type="button"
                          disabled={confirmingId === rate.walletId}
                          onClick={() => void onConfirm(rate.walletId)}
                          className={buttonSecondaryClass}
                        >
                          {confirmingId === rate.walletId ? 'Confirmando…' : 'Confirmar hoy'}
                        </button>
                      </div>
                    </div>

                    <WalletTierTable tiers={rate.tiers} label="Franjas de tasa" />

                    {rate.conditions.length > 0 && (
                      <div className="flex flex-col gap-2">
                        <h3 className="type-label">Condiciones</h3>
                        {rate.conditions.map((condition, index) => (
                          <div key={index} className="flex flex-col gap-1">
                            <p className="type-body">{condition.condition}</p>
                            <WalletTierTable
                              tiers={condition.tiers}
                              label={`Franjas de la condición: ${condition.condition}`}
                            />
                          </div>
                        ))}
                      </div>
                    )}

                    {rate.note !== null && rate.note !== '' && (
                      <p className="type-body text-muted">Nota: {rate.note}</p>
                    )}

                    <details
                      className="disclosure"
                      onToggle={(event) =>
                        setAuditOpenId(event.currentTarget.open ? rate.walletId : null)
                      }
                    >
                      <summary className="type-label cursor-pointer">
                        Historial de cambios
                      </summary>
                      <div className="mt-3">
                        {auditOpenId === rate.walletId && (
                          <WalletAudit walletId={rate.walletId} onProblem={handleProblem} />
                        )}
                      </div>
                    </details>
                  </>
                )}
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
