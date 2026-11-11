/**
 * `/claves-api/` island — client only.
 *
 * Reads `GET /api/v1/me` to confirm the session, lists the active keys, lets the reader create one (the key is shown
 * once, in `NewKeyNotice`, and lives only in this component's state) and revoke one with a confirmation. Any 401
 * leaves for `/ingresar`: a page with no session has nothing to show. The key is never written anywhere else: not to
 * storage, not to the URL.
 */
import { useCallback, useEffect, useState } from 'react';
import { accountApi } from '../../lib/account';
import { ApiProblem } from '../../lib/api';
import {
  apiKeysApi,
  canCreateKey,
  listedKey,
  validateKeyName,
  type ApiKey,
  type CreatedApiKey,
} from '../../lib/apiKeys';
import { problemText, sessionAction, toProblem } from '../../lib/accountStatus';
import ProblemNotice from '../account/ProblemNotice';
import ApiKeyCreateForm from './ApiKeyCreateForm';
import ApiKeyList from './ApiKeyList';
import NewKeyNotice from './NewKeyNotice';

const LOGIN_PATH = '/ingresar';

type Status = 'loading' | 'ready' | 'error';

export default function ApiKeysPanel() {
  const [status, setStatus] = useState<Status>('loading');
  const [keys, setKeys] = useState<ApiKey[]>([]);
  const [problem, setProblem] = useState<ApiProblem | null>(null);
  const [name, setName] = useState('');
  const [nameError, setNameError] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);
  const [created, setCreated] = useState<CreatedApiKey | null>(null);
  const [confirmingId, setConfirmingId] = useState<number | null>(null);
  const [revokingId, setRevokingId] = useState<number | null>(null);
  const [actionProblem, setActionProblem] = useState<ApiProblem | null>(null);

  const load = useCallback(async (): Promise<void> => {
    setStatus('loading');
    setProblem(null);
    try {
      await accountApi.loadMe();
      setKeys(await apiKeysApi.list());
      setStatus('ready');
    } catch (reason) {
      const failure = toProblem(reason);
      if (sessionAction(failure.status) === 'login') {
        window.location.assign(LOGIN_PATH);
        return;
      }
      setProblem(failure);
      setStatus('error');
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  async function onCreate(event: { preventDefault: () => void }): Promise<void> {
    event.preventDefault();
    const found = validateKeyName(name);
    setNameError(found);
    setActionProblem(null);
    if (found !== null) return;

    setCreating(true);
    try {
      const key = await apiKeysApi.create(name);
      setCreated(key);
      setKeys((current) => [...current, listedKey(key)]);
      setName('');
    } catch (reason) {
      const failure = toProblem(reason);
      if (sessionAction(failure.status) === 'login') {
        window.location.assign(LOGIN_PATH);
        return;
      }
      if (failure.status === 400 && failure.parameter === 'name') {
        setNameError(problemText(failure));
      } else {
        setActionProblem(failure);
      }
    } finally {
      setCreating(false);
    }
  }

  async function onRevoke(id: number): Promise<void> {
    setRevokingId(id);
    setActionProblem(null);
    try {
      await apiKeysApi.revoke(id);
      setKeys((current) => current.filter((key) => key.id !== id));
      // Revoking the key still on screen must not leave its secret behind.
      setCreated((current) => (current !== null && current.id === id ? null : current));
      setConfirmingId(null);
      // The row is gone: put focus on the section heading instead of nowhere.
      document.getElementById('tus-claves')?.focus();
    } catch (reason) {
      const failure = toProblem(reason);
      if (sessionAction(failure.status) === 'login') {
        window.location.assign(LOGIN_PATH);
        return;
      }
      setActionProblem(failure);
    } finally {
      setRevokingId(null);
    }
  }

  if (status === 'loading') {
    return (
      <p role="status" className="type-body text-muted">
        Cargando tus claves…
      </p>
    );
  }

  if (status === 'error' && problem !== null) {
    return <ProblemNotice problem={problem} onRetry={() => void load()} />;
  }

  return (
    <div className="flex flex-col gap-section">
      <section aria-labelledby="crear-clave" className="flex flex-col gap-block">
        <h2 id="crear-clave" className="type-section">
          Crear una clave
        </h2>
        <details className="disclosure">
          <summary className="type-label">Cómo se usa una clave</summary>
          <div className="mt-2 flex max-w-prose flex-col gap-2 type-body text-muted">
            <p>
              Mandala en el encabezado <code className="type-data">X-Api-Key</code> de tus pedidos a la
              API pública de lectura (ranking, simulaciones, instrumentos, entidades e informes). Con
              clave, el límite es tuyo y no del conjunto de gente que comparte tu dirección.
            </p>
            <p>
              El plan Gratis admite ráfagas de 600 pedidos, 10 por segundo y 20.000 por día (el día
              cambia a medianoche, hora de Argentina). Una clave revocada o inventada recibe un 401.
            </p>
            <p>La clave solo sirve para consultar datos públicos: no entra a tu cuenta ni a tus alertas.</p>
          </div>
        </details>

        {created !== null && <NewKeyNotice created={created} onDismiss={() => setCreated(null)} />}
        {actionProblem !== null && <ProblemNotice problem={actionProblem} />}

        <ApiKeyCreateForm
          name={name}
          error={nameError}
          activeCount={keys.length}
          canCreate={canCreateKey(keys.length)}
          creating={creating}
          onChange={setName}
          onSubmit={(event) => void onCreate(event)}
        />
      </section>

      <section aria-labelledby="tus-claves" className="flex flex-col gap-block">
        <h2 id="tus-claves" tabIndex={-1} className="type-section">
          Tus claves
        </h2>

        <ApiKeyList
          keys={keys}
          confirmingId={confirmingId}
          revokingId={revokingId}
          onRequestRevoke={setConfirmingId}
          onCancelRevoke={(id) => {
            setConfirmingId(null);
            document.getElementById(`clave-revocar-${id}`)?.focus();
          }}
          onConfirmRevoke={(id) => void onRevoke(id)}
        />
      </section>
    </div>
  );
}
