/**
 * The active keys of `/claves-api/`.
 *
 * A list of one-line rows in the panel's card, in the shape of the alert list: label, then the display prefix in the
 * quiet mono `type-data` line (all the API keeps to show), the plan and the creation day. Revoking opens an inline
 * confirmation that takes over the row; the confirm carries the loss tone and the cancel is a peer action, so the
 * view keeps exactly one primary button, the create form's.
 */
import { useEffect, useRef } from 'react';
import { keyLabel, planLabel, type ApiKey } from '../../lib/apiKeys';
import { formatDayMonth } from '../../lib/format';
import { buttonSecondaryClass, cardClass } from '../account/ui';

const ROW = 'flex flex-col gap-3 border-b border-border px-4 py-4 last:border-b-0';
const CONFIRM = 'btn-danger';

export interface ApiKeyListProps {
  keys: ApiKey[];
  /** The key whose inline confirmation is open, if any. */
  confirmingId: number | null;
  /** The key being revoked right now, if any. */
  revokingId: number | null;
  onRequestRevoke: (id: number) => void;
  onCancelRevoke: (id: number) => void;
  onConfirmRevoke: (id: number) => void;
}

export default function ApiKeyList({
  keys,
  confirmingId,
  revokingId,
  onRequestRevoke,
  onCancelRevoke,
  onConfirmRevoke,
}: ApiKeyListProps) {
  const confirmRef = useRef<HTMLButtonElement>(null);

  // The confirmation takes over the row, so focus moves into it and back out.
  useEffect(() => {
    if (confirmingId !== null) confirmRef.current?.focus();
  }, [confirmingId]);

  if (keys.length === 0) {
    return (
      <div className={`${cardClass} flex flex-col gap-2`}>
        <p className="type-body font-medium">Todavía no tenés claves</p>
        <p className="type-body text-muted">
          Una clave te da un límite propio para consultar la API pública. Creá la primera con el
          formulario de arriba.
        </p>
      </div>
    );
  }

  return (
    <div className="card overflow-hidden">
      <ul className="flex flex-col">
        {keys.map((key) => {
          const description = `Revocar la clave ${keyLabel(key)} (${key.prefix}…)`;
          const confirming = confirmingId === key.id;
          return (
            <li
              key={key.id}
              className={`${ROW} sm:grid sm:grid-cols-[minmax(0,1fr)_auto] sm:items-center sm:gap-6 ${
                confirming ? 'bg-subtle-surface' : ''
              }`}
            >
              <div className="flex min-w-0 flex-col gap-1">
                <p className="type-body font-medium break-words">{keyLabel(key)}</p>
                <p className="type-data text-muted">{key.prefix}…</p>
                <p className="type-data text-muted">
                  Plan {planLabel(key.plan)} · creada el {formatDayMonth(key.createdAt)}
                </p>
              </div>

              {confirming ? (
                <div role="group" aria-label={`Confirmar: ${description}`} className="flex flex-col gap-2">
                  <p className="type-body">¿Revocar esta clave? Deja de funcionar en el próximo pedido.</p>
                  <div className="flex flex-wrap gap-2">
                    <button
                      ref={confirmRef}
                      type="button"
                      disabled={revokingId === key.id}
                      onClick={() => onConfirmRevoke(key.id)}
                      className={CONFIRM}
                    >
                      {revokingId === key.id ? 'Revocando…' : 'Sí, revocar la clave'}
                    </button>
                    <button
                      type="button"
                      onClick={() => onCancelRevoke(key.id)}
                      className={buttonSecondaryClass}
                    >
                      Cancelar
                    </button>
                  </div>
                </div>
              ) : (
                <button
                  id={`clave-revocar-${key.id}`}
                  type="button"
                  aria-label={description}
                  onClick={() => onRequestRevoke(key.id)}
                  className={`${buttonSecondaryClass} self-start sm:self-auto`}
                >
                  Revocar
                </button>
              )}
            </li>
          );
        })}
      </ul>
    </div>
  );
}
