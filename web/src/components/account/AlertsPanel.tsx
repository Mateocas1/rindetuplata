/**
 * `/alertas` island (S3, S13, T7d) — client only.
 *
 * Reads `GET /api/v1/me`, then lists the session's rules, lets the reader create
 * one (optional kind, optional instrument from the public list, threshold as a
 * percent) and delete one with a confirmation. Any 401 leaves for `/ingresar`:
 * a page with no session has nothing to show.
 *
 * The panel owns the session, the data and the requests; `AlertCreateForm` and
 * `AlertRuleList` render the two sections, and the notices go through the shared
 * `StatusNotice`/`ProblemNotice` surfaces (T12c).
 */
import { useCallback, useEffect, useState } from 'react';
import { accountApi, type AlertRule, type InstrumentSummary } from '../../lib/account';
import { ApiProblem } from '../../lib/api';
import {
  alertRequest,
  selectableInstruments,
  validateAlertForm,
  type AlertFormState,
} from '../../lib/accountForm';
import { problemText, sessionAction, toProblem } from '../../lib/accountStatus';
import AlertCreateForm from './AlertCreateForm';
import AlertRuleList from './AlertRuleList';
import ProblemNotice from './ProblemNotice';
import StatusNotice from './StatusNotice';

const LOGIN_PATH = '/ingresar';
const EMPTY_FORM: AlertFormState = { kind: '', instrumentId: '', thresholdPercent: '' };

type Status = 'loading' | 'ready' | 'error';

/** The fields this form renders; anything else the API blames goes to the notice instead. */
function isFormField(parameter: string | null): parameter is string {
  return parameter === 'kind' || parameter === 'instrumentId' || parameter === 'thresholdPercent';
}

export default function AlertsPanel() {
  const [status, setStatus] = useState<Status>('loading');
  const [alerts, setAlerts] = useState<AlertRule[]>([]);
  const [instruments, setInstruments] = useState<InstrumentSummary[]>([]);
  const [optionsFailed, setOptionsFailed] = useState(false);
  const [problem, setProblem] = useState<ApiProblem | null>(null);
  const [form, setForm] = useState<AlertFormState>(EMPTY_FORM);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [creating, setCreating] = useState(false);
  const [created, setCreated] = useState(false);
  const [confirmingId, setConfirmingId] = useState<number | null>(null);
  const [deletingId, setDeletingId] = useState<number | null>(null);
  const [actionProblem, setActionProblem] = useState<ApiProblem | null>(null);

  const load = useCallback(async (): Promise<void> => {
    setStatus('loading');
    setProblem(null);
    try {
      await accountApi.loadMe();
      const [rules, options] = await Promise.allSettled([
        accountApi.listAlerts(),
        accountApi.listInstruments(),
      ]);
      if (rules.status === 'rejected') throw rules.reason;
      setAlerts(rules.value);
      if (options.status === 'fulfilled') {
        setInstruments(selectableInstruments(options.value));
        setOptionsFailed(false);
      } else {
        setInstruments([]);
        setOptionsFailed(true);
      }
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
    const found = validateAlertForm(form);
    setErrors(found);
    setActionProblem(null);
    setCreated(false);
    if (Object.keys(found).length > 0) return;

    setCreating(true);
    try {
      const createdRule = await accountApi.createAlert(alertRequest(form));
      setAlerts((current) => [...current, createdRule]);
      setForm(EMPTY_FORM);
      setCreated(true);
    } catch (reason) {
      const failure = toProblem(reason);
      if (sessionAction(failure.status) === 'login') {
        window.location.assign(LOGIN_PATH);
        return;
      }
      if (failure.status === 400 && isFormField(failure.parameter)) {
        setErrors({ [failure.parameter]: problemText(failure) });
      } else {
        setActionProblem(failure);
      }
    } finally {
      setCreating(false);
    }
  }

  async function onDelete(id: number): Promise<void> {
    setDeletingId(id);
    setActionProblem(null);
    try {
      await accountApi.deleteAlert(id);
      setAlerts((current) => current.filter((rule) => rule.id !== id));
      setConfirmingId(null);
      // The row is gone: put focus on the section heading instead of nowhere.
      document.getElementById('tus-alertas')?.focus();
    } catch (reason) {
      const failure = toProblem(reason);
      if (sessionAction(failure.status) === 'login') {
        window.location.assign(LOGIN_PATH);
        return;
      }
      setActionProblem(failure);
    } finally {
      setDeletingId(null);
    }
  }

  if (status === 'loading') {
    return (
      <p role="status" className="type-body text-muted">
        Cargando tus alertas…
      </p>
    );
  }

  if (status === 'error' && problem !== null) {
    return <ProblemNotice problem={problem} onRetry={() => void load()} />;
  }

  return (
    <div className="flex flex-col gap-section">
      <section aria-labelledby="crear-alerta" className="flex flex-col gap-block">
        <h2 id="crear-alerta" className="type-section">
          Crear una alerta
        </h2>
        <details className="disclosure">
          <summary className="type-label">Cómo funcionan las alertas</summary>
          <p className="mt-2 max-w-prose type-body text-muted">
            Te avisamos por email cuando una opción supera la tasa que elijas. Podés mirar un tipo de
            opción, una opción puntual o todas.
          </p>
        </details>

        {created && (
          <StatusNotice role="status" tone="gain" title="Alerta creada">
            <p className="type-body">La vas a ver abajo y te avisamos cuando se cumpla.</p>
          </StatusNotice>
        )}
        {actionProblem !== null && <ProblemNotice problem={actionProblem} />}

        <AlertCreateForm
          form={form}
          errors={errors}
          instruments={instruments}
          optionsFailed={optionsFailed}
          creating={creating}
          onChange={setForm}
          onSubmit={(event) => void onCreate(event)}
        />
      </section>

      <section aria-labelledby="tus-alertas" className="flex flex-col gap-block">
        <h2 id="tus-alertas" tabIndex={-1} className="type-section">
          Tus alertas
        </h2>

        <AlertRuleList
          rules={alerts}
          instruments={instruments}
          confirmingId={confirmingId}
          deletingId={deletingId}
          onRequestDelete={setConfirmingId}
          onCancelDelete={(id) => {
            setConfirmingId(null);
            // The confirmation closed on a click the row's own button now takes back.
            document.getElementById(`alerta-eliminar-${id}`)?.focus();
          }}
          onConfirmDelete={(id) => void onDelete(id)}
        />
      </section>
    </div>
  );
}
