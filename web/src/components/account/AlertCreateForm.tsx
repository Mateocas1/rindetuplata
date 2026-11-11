/**
 * The create-alert form of `/alertas`.
 *
 * It owns the view's one primary action, so it is a component of its own: the
 * panel holds the state and the request, this form renders the card and the
 * fields. Labels sit above their control and help or error text below, tied to
 * the control with `aria-describedby`, and the instrument list comes in as a
 * prop because the panel is the one that fetches it.
 *
 * The first field is the type of alert and each type shows only the
 * fields it takes: a threshold rule keeps the original kind, optional instrument
 * and rate; "cambió la mejor opción" and the weekly digest ask for a kind; "bajó
 * una tasa" asks for the one bank or wallet to follow.
 */
import type { InstrumentSummary } from '../../lib/account';
import {
  ALERT_TYPE_OPTIONS,
  rateDropInstruments,
  type AlertFormState,
} from '../../lib/accountForm';
import { KIND_OPTIONS } from '../../lib/labels';
import InstrumentPicker from './InstrumentPicker';
import {
  buttonPrimaryClass,
  cardClass,
  errorTextClass,
  fieldGroupClass,
  inputClass,
  labelClass,
} from './ui';

const TYPE_SELECT_ID = 'alerta-tipo-alerta';
const TYPE_HINT_ID = 'alerta-tipo-alerta-ayuda';
const KIND_SELECT_ID = 'alerta-tipo';
const INSTRUMENT_ERROR_ID = 'alerta-instrumento-error';
const THRESHOLD_INPUT_ID = 'alerta-tasa';
const THRESHOLD_HINT_ID = 'alerta-tasa-ayuda';
const THRESHOLD_ERROR_ID = 'alerta-tasa-error';

export interface AlertCreateFormProps {
  form: AlertFormState;
  errors: Record<string, string>;
  instruments: InstrumentSummary[];
  /** The instrument list failed to load; a rule can still be created without one. */
  optionsFailed: boolean;
  creating: boolean;
  onChange: (form: AlertFormState) => void;
  onSubmit: (event: { preventDefault: () => void }) => void;
}

export default function AlertCreateForm({
  form,
  errors,
  instruments,
  optionsFailed,
  creating,
  onChange,
  onSubmit,
}: AlertCreateFormProps) {
  const thresholdError = errors.thresholdPercent;
  const { type } = form;
  const typeOption = ALERT_TYPE_OPTIONS.find((option) => option.value === type);
  const followsOneInstrument = type === 'RATE_DROP';

  return (
    <form onSubmit={onSubmit} noValidate className={`${cardClass} flex flex-col gap-block`}>
      <div className={fieldGroupClass}>
        <label htmlFor={TYPE_SELECT_ID} className={labelClass}>
          Tipo de alerta
        </label>
        <select
          id={TYPE_SELECT_ID}
          name="tipo-alerta"
          value={type}
          aria-describedby={TYPE_HINT_ID}
          // The instrument list differs by type (a bank or wallet only for "bajó una tasa"), so a pick is dropped.
          onChange={(event) =>
            onChange({ ...form, type: event.target.value as AlertFormState['type'], instrumentId: '' })
          }
          className={inputClass}
        >
          {ALERT_TYPE_OPTIONS.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
        <p id={TYPE_HINT_ID} className="type-body text-muted">
          {typeOption?.description}
        </p>
      </div>

      {!followsOneInstrument && (
        <div className={fieldGroupClass}>
          <label htmlFor={KIND_SELECT_ID} className={labelClass}>
            Tipo de opción
          </label>
          <select
            id={KIND_SELECT_ID}
            name="tipo"
            value={form.kind}
            onChange={(event) => onChange({ ...form, kind: event.target.value })}
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
      )}

      {(type === 'THRESHOLD' || followsOneInstrument) && (
        <fieldset className="flex flex-col gap-2">
          <legend className={labelClass}>
            {followsOneInstrument ? 'Banco o billetera a seguir' : 'Opción (opcional)'}
          </legend>
          {optionsFailed ? (
            <p className={errorTextClass}>
              {followsOneInstrument
                ? 'No pudimos cargar la lista de opciones. Probá de nuevo en un rato.'
                : 'No pudimos cargar la lista de opciones. Podés crear la alerta sin elegir una.'}
            </p>
          ) : (
            <InstrumentPicker
              id="alerta-instrumento"
              instruments={followsOneInstrument ? rateDropInstruments(instruments) : instruments}
              optional={!followsOneInstrument}
              selectedId={form.instrumentId === '' ? null : Number(form.instrumentId)}
              onSelect={(id) => onChange({ ...form, instrumentId: id === null ? '' : String(id) })}
              describedBy={errors.instrumentId !== undefined ? INSTRUMENT_ERROR_ID : undefined}
            />
          )}
          {errors.instrumentId !== undefined && (
            <p id={INSTRUMENT_ERROR_ID} className={errorTextClass}>
              {errors.instrumentId}
            </p>
          )}
        </fieldset>
      )}

      {type === 'THRESHOLD' && (
        <div className={fieldGroupClass}>
          <label htmlFor={THRESHOLD_INPUT_ID} className={labelClass}>
            Avisame si supera esta tasa
          </label>
          <input
            id={THRESHOLD_INPUT_ID}
            name="tasa"
            inputMode="decimal"
            value={form.thresholdPercent}
            aria-invalid={thresholdError !== undefined}
            aria-describedby={
              thresholdError === undefined
                ? THRESHOLD_HINT_ID
                : `${THRESHOLD_HINT_ID} ${THRESHOLD_ERROR_ID}`
            }
            onChange={(event) => onChange({ ...form, thresholdPercent: event.target.value })}
            className={inputClass}
          />
          <p id={THRESHOLD_HINT_ID} className="type-body text-muted">
            TNA anual en porcentaje: escribí el número sin el signo % (por ejemplo, 25 para 25%).
          </p>
          {thresholdError !== undefined && (
            <p id={THRESHOLD_ERROR_ID} className={errorTextClass}>
              {thresholdError}
            </p>
          )}
        </div>
      )}

      <button type="submit" disabled={creating} className={`${buttonPrimaryClass} self-start`}>
        {creating ? 'Creando…' : 'Crear alerta'}
      </button>
    </form>
  );
}
