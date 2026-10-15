/**
 * `/ingresar` island (S13, T7d) — client only.
 *
 * Two flows in one panel. An email submit posts to `POST /api/v1/auth/magic-link`
 * and always answers with the same confirmation copy, mirroring the API's 202 so
 * the page never reveals whether the email is known. A `?token=` in the URL is
 * consumed once by `POST /api/v1/auth/login`, then the token is dropped from the
 * URL with `history.replaceState` and the browser goes to `/alertas`; a used or
 * expired link shows the form again with a clear message.
 */
import { useEffect, useRef, useState } from 'react';
import { accountApi } from '../../lib/account';
import { ApiProblem } from '../../lib/api';
import { normalizeEmail, validateEmail } from '../../lib/accountForm';
import { toProblem } from '../../lib/accountStatus';
import ProblemNotice from './ProblemNotice';
import StatusNotice from './StatusNotice';
import { buttonPrimaryClass, cardClass, fieldGroupClass, inputClass, labelClass } from './ui';

const ALERTS_PATH = '/alertas';
const EMAIL_INPUT_ID = 'ingresar-email';
const EMAIL_HINT_ID = 'ingresar-email-ayuda';
const EMAIL_ERROR_ID = 'ingresar-email-error';
const API_REJECTED_EMAIL = 'Revisá el email: la API no lo aceptó.';

type Status = 'idle' | 'sending' | 'sent' | 'consuming' | 'token-error';

export default function LoginPanel() {
  const [email, setEmail] = useState('');
  const [status, setStatus] = useState<Status>('idle');
  const [emailError, setEmailError] = useState<string | null>(null);
  const [problem, setProblem] = useState<ApiProblem | null>(null);
  const emailRef = useRef<HTMLInputElement>(null);
  const consumed = useRef(false);

  // Consume a magic-link token exactly once, then take it out of the URL.
  useEffect(() => {
    if (consumed.current) return;
    const params = new URLSearchParams(window.location.search);
    const token = params.get('token');
    if (token === null || token === '') return;
    consumed.current = true;
    params.delete('token');
    const query = params.toString();
    window.history.replaceState(
      null,
      '',
      `${window.location.pathname}${query === '' ? '' : `?${query}`}`,
    );
    setStatus('consuming');
    accountApi
      .login(token)
      .then(() => {
        window.location.assign(ALERTS_PATH);
      })
      .catch(() => {
        // A consumed or expired link is the overwhelmingly likely cause, and the
        // form below already carries the way forward.
        setStatus('token-error');
      });
  }, []);

  async function requestLink(): Promise<void> {
    setProblem(null);
    const error = validateEmail(email);
    setEmailError(error);
    if (error !== null) {
      emailRef.current?.focus();
      return;
    }
    setStatus('sending');
    try {
      await accountApi.requestMagicLink(normalizeEmail(email));
      setStatus('sent');
    } catch (reason) {
      const failure = toProblem(reason);
      setStatus('idle');
      if (failure.status === 400 && failure.parameter === 'email') {
        setEmailError(API_REJECTED_EMAIL);
        emailRef.current?.focus();
      } else {
        setProblem(failure);
      }
    }
  }

  function onSubmit(event: { preventDefault: () => void }): void {
    event.preventDefault();
    void requestLink();
  }

  if (status === 'consuming') {
    return (
      <p role="status" className="text-body-sm text-muted">
        Estamos entrando con tu link…
      </p>
    );
  }

  const describedBy = [EMAIL_HINT_ID, emailError !== null ? EMAIL_ERROR_ID : null]
    .filter((id): id is string => id !== null)
    .join(' ');

  return (
    <div className="flex flex-col gap-block">
      {status === 'token-error' && (
        <StatusNotice role="alert" tone="loss" title="No pudimos entrar con ese link">
          <p className="text-body-sm">El link ya se usó o venció. Pedí uno nuevo con tu email.</p>
        </StatusNotice>
      )}

      {status === 'sent' && (
        <StatusNotice role="status" tone="gain" title="Revisá tu email">
          <p className="text-body-sm">
            Si esa dirección está bien escrita, te llega un link para entrar. Puede tardar unos
            minutos; fijate también en el correo no deseado.
          </p>
        </StatusNotice>
      )}

      {problem !== null && (
        <ProblemNotice problem={problem} onRetry={() => void requestLink()} />
      )}

      <form onSubmit={onSubmit} noValidate className={`${cardClass} flex flex-col gap-block`}>
        <div className={fieldGroupClass}>
          <label htmlFor={EMAIL_INPUT_ID} className={labelClass}>
            Tu email
          </label>
          <input
            ref={emailRef}
            id={EMAIL_INPUT_ID}
            name="email"
            type="email"
            autoComplete="email"
            inputMode="email"
            required
            value={email}
            aria-invalid={emailError !== null}
            aria-describedby={describedBy}
            onChange={(event) => {
              setEmail(event.target.value);
              if (emailError !== null) setEmailError(null);
            }}
            className={inputClass}
          />
          <p id={EMAIL_HINT_ID} className="text-caption text-muted">
            Sin contraseñas: te mandamos un link de un solo uso, válido por 15 minutos.
          </p>
          {emailError !== null && (
            <p id={EMAIL_ERROR_ID} className="text-body-sm text-loss">
              {emailError}
            </p>
          )}
        </div>
        <button
          type="submit"
          disabled={status === 'sending'}
          className={`${buttonPrimaryClass} self-start`}
        >
          {status === 'sending' ? 'Enviando…' : 'Mandarme el link para entrar'}
        </button>
      </form>
    </div>
  );
}
