/**
 * The one place a key is ever shown.
 *
 * The API returns the key in the create response only: it stores a hash, so it cannot be shown again. This notice
 * therefore says so, offers a copy button (with the text selectable as the fallback when the clipboard is blocked)
 * and stays until the reader dismisses it or leaves the page. Focus moves here on creation so a keyboard or
 * screen-reader user lands on the key instead of searching for it.
 */
import { useEffect, useRef, useState } from 'react';
import type { CreatedApiKey } from '../../lib/apiKeys';
import StatusNotice from '../account/StatusNotice';
import { buttonPrimaryClass, buttonSecondaryClass } from '../account/ui';

export interface NewKeyNoticeProps {
  created: CreatedApiKey;
  onDismiss: () => void;
}

type CopyState = 'idle' | 'copied' | 'failed';

export default function NewKeyNotice({ created, onDismiss }: NewKeyNoticeProps) {
  const [copy, setCopy] = useState<CopyState>('idle');
  const container = useRef<HTMLDivElement>(null);

  useEffect(() => {
    container.current?.focus();
  }, [created.id]);

  async function copyKey(): Promise<void> {
    try {
      await navigator.clipboard.writeText(created.key);
      setCopy('copied');
    } catch {
      setCopy('failed');
    }
  }

  return (
    <div ref={container} tabIndex={-1} className="flex flex-col gap-2 outline-offset-4">
      <StatusNotice role="status" tone="gain" title="Clave creada">
        <p className="type-body">
          Copiala ahora: por seguridad no la guardamos y no vas a poder volver a verla. Si la perdés,
          revocá esta clave y creá otra.
        </p>
        <p className="type-label mt-1">Tu clave</p>
        <p className="type-data break-all rounded-panel border border-border bg-surface px-3 py-2 select-all">
          {created.key}
        </p>
        <div className="mt-1 flex flex-wrap items-center gap-2">
          <button type="button" onClick={() => void copyKey()} className={buttonPrimaryClass}>
            Copiar clave
          </button>
          <button type="button" onClick={onDismiss} className={buttonSecondaryClass}>
            Ya la guardé
          </button>
          <span role="status" className="type-body">
            {copy === 'copied' && 'Copiada.'}
            {copy === 'failed' && 'No pudimos copiarla: seleccioná el texto y copialo a mano.'}
          </span>
        </div>
      </StatusNotice>
    </div>
  );
}
