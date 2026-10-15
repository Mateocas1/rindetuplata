/**
 * `/alertas` island (S3, S13, T7d) — client only.
 *
 * Reads `GET /api/v1/me`, then lists the session's rules, lets the reader create
 * one (optional kind, optional instrument from the public list, threshold as a
 * percent) and delete one with a confirmation. Any 401 leaves for `/ingresar`:
 * a page with no session has nothing to show.
 */
import { useCallback, useEffect, useRef, useState } from 'react';
import { accountApi, type AlertRule, type InstrumentSummary } from '../../lib/account';
import { ApiProblem } from '../../lib/api';
import {
  alertRequest,
  instrumentLabelById,
  percentToFraction,
  selectableInstruments,
  validateAlertForm,
  type AlertFormState,
} from '../../lib/accountForm';
import { problemText, sessionAction, toProblem } from '../../lib/accountStatus';
import { formatPercent } from '../../lib/format';
import { KIND_OPTIONS, kindLabel } from '../../lib/labels';
import InstrumentPicker from './InstrumentPicker';
import ProblemNotice from './ProblemNotice';
import {
  buttonPrimaryClass,
  buttonSecondaryClass,
  cardClass,
  errorTextClass,
  fieldGroupClass,
  inputClass,
  labelClass,
} from './ui';

const LOGIN_PATH = '/ingresar';
const EMPTY_FORM: AlertFormState = { kind: '', instrumentId: '', thresholdPercent: '' };

type Status = 'loading' | 'ready' | 'error';

function ruleKindLabel(rule: AlertRule): string {
  return rule.kind === null ? 'Cualquier tipo' : kindLabel(rule.kind);
}

function ruleInstrumentLabel(rule: AlertRule, instruments: InstrumentSummary[]): string {
  if (rule.instrumentId === null) return 'Cualquier opción';
  return instrumentLabelById(instruments, rule.instrumentId) ?? `La opción #${rule.instrumentId}`;
}

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
  const confirmRef = useRef<HTMLButtonElement>(null);

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

  // The confirmation takes over the row, so focus moves into it and back out.
  useEffect(() => {
    if (confirmingId !== null) confirmRef.current?.focus();
  }, [confirmingId]);

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
      <p role="status" className="text-body-sm text-muted">
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
        <h2 id="crear-alerta" className="text-h2 font-semibold">
          Crear una alerta
        </h2>
        <p className="text-body-sm text-muted">
          Te avisamos por email cuando una opción supera la tasa que elijas. Podés mirar un tipo de
          opción, una opción puntual o todas.
        </p>

        {created && (
          <div
            role="status"
            className="rounded-lg border border-border bg-gain-surface px-4 py-3 text-body-sm text-on-gain"
          >
            Alerta creada. La vas a ver abajo y te avisamos cuando se cumpla.
          </div>
        )}
        {actionProblem !== null && <ProblemNotice problem={actionProblem} />}

        <form onSubmit={onCreate} noValidate className={`${cardClass} flex flex-col gap-block`}>
          <div className={fieldGroupClass}>
            <label htmlFor="alerta-tipo" className={labelClass}>
              Tipo de opción
            </label>
            <select
              id="alerta-tipo"
              name="tipo"
              value={form.kind}
              onChange={(event) => setForm({ ...form, kind: event.target.value })}
              className={inputClass}
            >
              <option value="">Cualquier tipo</option>
              {KIND_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </div>

          <fieldset className="flex flex-col gap-2">
            <legend className={labelClass}>Opción (opcional)</legend>
            {optionsFailed ? (
              <p className="text-body-sm text-loss">
                No pudimos cargar la lista de opciones. Podés crear la alerta sin elegir una.
              </p>
            ) : (
              <InstrumentPicker
                id="alerta-instrumento"
                instruments={instruments}
                selectedId={form.instrumentId === '' ? null : Number(form.instrumentId)}
                onSelect={(id) => setForm({ ...form, instrumentId: id === null ? '' : String(id) })}
                describedBy={errors.instrumentId !== undefined ? 'alerta-instrumento-error' : undefined}
              />
            )}
            {errors.instrumentId !== undefined && (
              <p id="alerta-instrumento-error" className={errorTextClass}>
                {errors.instrumentId}
              </p>
            )}
          </fieldset>

          <div className={fieldGroupClass}>
            <label htmlFor="alerta-tasa" className={labelClass}>
              Avisame si supera esta tasa (TNA anual en porcentaje)
            </label>
            <input
              id="alerta-tasa"
              name="tasa"
              inputMode="decimal"
              value={form.thresholdPercent}
              aria-invalid={errors.thresholdPercent !== undefined}
              aria-describedby={
                errors.thresholdPercent === undefined ? 'alerta-tasa-ayuda' : 'alerta-tasa-ayuda alerta-tasa-error'
              }
              onChange={(event) => setForm({ ...form, thresholdPercent: event.target.value })}
              className={inputClass}
            />
            <p id="alerta-tasa-ayuda" className="text-caption text-muted">
              Escribí el número sin el signo %: por ejemplo, 25 para 25% de TNA.
            </p>
            {errors.thresholdPercent !== undefined && (
              <p id="alerta-tasa-error" className={errorTextClass}>
                {errors.thresholdPercent}
              </p>
            )}
          </div>

          <button type="submit" disabled={creating} className={`${buttonPrimaryClass} self-start`}>
            {creating ? 'Creando…' : 'Crear alerta'}
          </button>
        </form>
      </section>

      <section aria-labelledby="tus-alertas" className="flex flex-col gap-block">
        <h2 id="tus-alertas" tabIndex={-1} className="text-h2 font-semibold">
          Tus alertas
        </h2>

        {alerts.length === 0 ? (
          <div className="flex flex-col gap-2 rounded-lg border border-border bg-surface-raised p-block">
            <p className="font-medium">Todavía no tenés alertas</p>
            <p className="text-body-sm text-muted">
              Una alerta te avisa por email cuando una opción supera la tasa que le pongas. Armá la
              primera con el formulario de arriba.
            </p>
          </div>
        ) : (
          <ul className="flex flex-col gap-3">
            {alerts.map((rule) => {
              const instrument = ruleInstrumentLabel(rule, instruments);
              const description = `Eliminar la alerta de ${ruleKindLabel(rule)} sobre ${instrument}`;
              return (
                <li
                  key={rule.id}
                  className="flex flex-col gap-3 rounded-lg border border-border bg-surface-raised p-block sm:flex-row sm:items-center sm:justify-between"
                >
                  <div className="flex min-w-0 flex-col gap-1">
                    <p className="font-medium">{ruleKindLabel(rule)}</p>
                    <p className="text-body-sm text-muted">
                      {instrument} · avisa si la tasa supera{' '}
                      <span className="tabular-nums">
                        {formatPercent(percentToFraction(rule.thresholdPercent))}
                      </span>
                    </p>
                    {rule.active === false && (
                      <p className="text-body-sm text-warning">Pausada: no envía avisos.</p>
                    )}
                  </div>
                  {confirmingId === rule.id ? (
                    <div
                      role="group"
                      aria-label={`Confirmar: ${description}`}
                      className="flex flex-col gap-2"
                    >
                      <p className="text-body-sm">¿Eliminar esta alerta?</p>
                      <div className="flex flex-wrap gap-2">
                        <button
                          ref={confirmRef}
                          type="button"
                          disabled={deletingId === rule.id}
                          onClick={() => void onDelete(rule.id)}
                          className={buttonPrimaryClass}
                        >
                          {deletingId === rule.id ? 'Eliminando…' : 'Sí, eliminar la alerta'}
                        </button>
                        <button
                          type="button"
                          onClick={() => {
                            setConfirmingId(null);
                            document.getElementById(`alerta-eliminar-${rule.id}`)?.focus();
                          }}
                          className={buttonSecondaryClass}
                        >
                          Cancelar
                        </button>
                      </div>
                    </div>
                  ) : (
                    <button
                      id={`alerta-eliminar-${rule.id}`}
                      type="button"
                      aria-label={description}
                      onClick={() => setConfirmingId(rule.id)}
                      className={buttonSecondaryClass}
                    >
                      Eliminar
                    </button>
                  )}
                </li>
              );
            })}
          </ul>
        )}
      </section>
    </div>
  );
}
