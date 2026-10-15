/**
 * "Cerrar sesión" island (S13, T7d).
 *
 * Ends the session through `POST /api/v1/auth/logout` (204) and, on success,
 * drops the CSRF token the shared client had cached for the dead session before
 * leaving for `/ingresar`. An already-expired session has nothing to close, so a
 * 401 also lands on the login page; anything else is shown as a notice with a
 * retry, next to the button that failed.
 */
import { useState } from 'react';
import { accountApi } from '../../lib/account';
import { ApiProblem } from '../../lib/api';
import { sessionAction, toProblem } from '../../lib/accountStatus';
import ProblemNotice from './ProblemNotice';
import { buttonSecondaryClass } from './ui';

const LOGIN_PATH = '/ingresar';

export default function LogoutButton() {
  const [busy, setBusy] = useState(false);
  const [problem, setProblem] = useState<ApiProblem | null>(null);

  async function logout(): Promise<void> {
    setBusy(true);
    setProblem(null);
    try {
      await accountApi.logout();
      window.location.assign(LOGIN_PATH);
    } catch (reason) {
      const failure = toProblem(reason);
      if (sessionAction(failure.status) === 'login') {
        window.location.assign(LOGIN_PATH);
        return;
      }
      setProblem(failure);
      setBusy(false);
    }
  }

  return (
    <div className="flex flex-col items-start gap-2">
      <button
        type="button"
        disabled={busy}
        onClick={() => void logout()}
        className={buttonSecondaryClass}
      >
        {busy ? 'Cerrando sesión…' : 'Cerrar sesión'}
      </button>
      {problem !== null && (
        <ProblemNotice
          problem={problem}
          title="No pudimos cerrar la sesión"
          onRetry={() => void logout()}
        />
      )}
    </div>
  );
}
