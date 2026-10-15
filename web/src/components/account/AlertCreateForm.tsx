/**
 * The create-alert form of `/alertas` (T12c).
 *
 * It owns the view's one primary action, so it is a component of its own: the
 * panel holds the state and the request, this form renders the card and the
 * fields. Labels sit above their control and help or error text below, tied to
 * the control with `aria-describedby`, and the optional instrument list comes in
 * as a prop because the panel is the one that fetches it.
 */
import type { InstrumentSummary } from '../../lib/account';
import type { AlertFormState } from '../../lib/accountForm';
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

  return (
    <form onSubmit={onSubmit} noValidate className={`${cardClass} flex flex-col gap-block`}>
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

      <fieldset className="flex flex-col gap-2">
        <legend className={labelClass}>Opción (opcional)</legend>
        {optionsFailed ? (
          <p className={errorTextClass}>
            No pudimos cargar la lista de opciones. Podés crear la alerta sin elegir una.
          </p>
        ) : (
          <InstrumentPicker
            id="alerta-instrumento"
            instruments={instruments}
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

      <div className={fieldGroupClass}>
        <label htmlFor={THRESHOLD_INPUT_ID} className={labelClass}>
          Avisame si supera esta tasa (TNA anual en porcentaje)
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
        <p id={THRESHOLD_HINT_ID} className="text-caption text-muted">
          Escribí el número sin el signo %: por ejemplo, 25 para 25% de TNA.
        </p>
        {thresholdError !== undefined && (
          <p id={THRESHOLD_ERROR_ID} className={errorTextClass}>
            {thresholdError}
          </p>
        )}
      </div>

      <button type="submit" disabled={creating} className={`${buttonPrimaryClass} self-start`}>
        {creating ? 'Creando…' : 'Crear alerta'}
      </button>
    </form>
  );
}
