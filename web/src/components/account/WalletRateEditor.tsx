/**
 * Edit form for one curated wallet rate (S12, T7d).
 *
 * The saved document is the API's percent shape: base tiers, optional labeled
 * conditions with their own tiers, and a note. Saving stamps today as the
 * verification date server-side, so the panel never sends one. Client
 * validation and a 400 from the API are keyed by the API's `parameter` name,
 * which is also this form's field id.
 */
import { useState } from 'react';
import { accountApi, type WalletRate } from '../../lib/account';
import { ApiProblem } from '../../lib/api';
import {
  buildWalletRequest,
  validateWalletForm,
  walletFormFromRate,
  type WalletFormState,
} from '../../lib/accountForm';
import { problemText, sessionAction, toProblem } from '../../lib/accountStatus';
import WalletTierFields from './WalletTierFields';
import {
  buttonPrimaryClass,
  buttonSecondaryClass,
  cardClass,
  errorTextClass,
  fieldGroupClass,
  inputClass,
  labelClass,
  textareaClass,
} from './ui';

const LOGIN_PATH = '/ingresar';

/** The fields this form renders; anything else the API blames goes to the page notice. */
function isWalletField(parameter: string | null): parameter is string {
  return (
    parameter !== null &&
    (parameter === 'name' ||
      parameter === 'tiers' ||
      parameter === 'conditions' ||
      parameter.startsWith('tiers[') ||
      parameter.startsWith('conditions['))
  );
}

export interface WalletRateEditorProps {
  rate: WalletRate;
  onSaved: (rate: WalletRate) => void;
  onCancel: () => void;
  onProblem: (problem: ApiProblem) => void;
}

export default function WalletRateEditor({
  rate,
  onSaved,
  onCancel,
  onProblem,
}: WalletRateEditorProps) {
  const [form, setForm] = useState<WalletFormState>(() => walletFormFromRate(rate));
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState(false);

  async function onSubmit(event: { preventDefault: () => void }): Promise<void> {
    event.preventDefault();
    const found = validateWalletForm(form);
    setErrors(found);
    if (Object.keys(found).length > 0) return;

    setSaving(true);
    try {
      const saved = await accountApi.saveWalletRate(rate.walletId, buildWalletRequest(form));
      onSaved(saved);
    } catch (reason) {
      const failure = toProblem(reason);
      if (sessionAction(failure.status) === 'login') {
        window.location.assign(LOGIN_PATH);
        return;
      }
      if (failure.status === 400 && isWalletField(failure.parameter)) {
        setErrors({ [failure.parameter]: problemText(failure) });
      } else {
        onProblem(failure);
      }
    } finally {
      setSaving(false);
    }
  }

  return (
    <form
      onSubmit={onSubmit}
      noValidate
      aria-labelledby={`editar-${rate.walletId}`}
      className={`${cardClass} flex flex-col gap-block`}
    >
      <h3 id={`editar-${rate.walletId}`} className="text-h3 font-semibold">
        Editar {rate.name}
      </h3>

      <div className={fieldGroupClass}>
        <label htmlFor="wallet-nombre" className={labelClass}>
          Nombre
        </label>
        <input
          id="wallet-nombre"
          name="wallet-nombre"
          value={form.name}
          aria-invalid={errors.name !== undefined}
          aria-describedby={errors.name === undefined ? undefined : 'wallet-nombre-error'}
          onChange={(event) => setForm({ ...form, name: event.target.value })}
          className={inputClass}
        />
        {errors.name !== undefined && (
          <p id="wallet-nombre-error" className={errorTextClass}>
            {errors.name}
          </p>
        )}
      </div>

      <WalletTierFields
        legend="Franjas de tasa"
        fieldPrefix="tiers"
        tiers={form.tiers}
        errors={errors}
        onChange={(tiers) => setForm({ ...form, tiers })}
      />

      <section aria-labelledby="wallet-condiciones" className="flex flex-col gap-3">
        <h4 id="wallet-condiciones" className="text-body font-semibold">
          Condiciones (opcional)
        </h4>
        <p className="text-caption text-muted">
          Por ejemplo, una tasa más alta si cumplís una condición. El ranking usa la tasa base.
        </p>

        {form.conditions.map((condition, index) => (
          <div
            key={index}
            className="flex flex-col gap-3 rounded-lg border border-border bg-surface p-4"
          >
            <div className={fieldGroupClass}>
              <label htmlFor={`conditions[${index}].condition`} className={labelClass}>
                Nombre de la condición
              </label>
              <input
                id={`conditions[${index}].condition`}
                name={`conditions[${index}].condition`}
                value={condition.condition}
                aria-invalid={errors[`conditions[${index}].condition`] !== undefined}
                aria-describedby={
                  errors[`conditions[${index}].condition`] === undefined
                    ? undefined
                    : `conditions[${index}].condition-error`
                }
                onChange={(event) =>
                  setForm({
                    ...form,
                    conditions: form.conditions.map((current, position) =>
                      position === index ? { ...current, condition: event.target.value } : current,
                    ),
                  })
                }
                className={inputClass}
              />
              {errors[`conditions[${index}].condition`] !== undefined && (
                <p id={`conditions[${index}].condition-error`} className={errorTextClass}>
                  {errors[`conditions[${index}].condition`]}
                </p>
              )}
            </div>

            <WalletTierFields
              legend="Franjas de esta condición"
              fieldPrefix={`conditions[${index}].tiers`}
              tiers={condition.tiers}
              errors={errors}
              onChange={(tiers) =>
                setForm({
                  ...form,
                  conditions: form.conditions.map((current, position) =>
                    position === index ? { ...current, tiers } : current,
                  ),
                })
              }
            />

            <button
              type="button"
              onClick={() =>
                setForm({
                  ...form,
                  conditions: form.conditions.filter((_, position) => position !== index),
                })
              }
              className={`${buttonSecondaryClass} self-start`}
            >
              Quitar condición
            </button>
          </div>
        ))}

        <button
          type="button"
          onClick={() =>
            setForm({
              ...form,
              conditions: [
                ...form.conditions,
                { condition: '', tiers: [{ floor: '', ceiling: '', tnaPercent: '' }] },
              ],
            })
          }
          className={`${buttonSecondaryClass} self-start`}
        >
          Agregar condición
        </button>
      </section>

      <div className={fieldGroupClass}>
        <label htmlFor="wallet-nota" className={labelClass}>
          Nota (opcional)
        </label>
        <textarea
          id="wallet-nota"
          name="wallet-nota"
          rows={3}
          value={form.note}
          onChange={(event) => setForm({ ...form, note: event.target.value })}
          className={textareaClass}
        />
      </div>

      <div className="flex flex-wrap gap-2">
        <button type="submit" disabled={saving} className={buttonPrimaryClass}>
          {saving ? 'Guardando…' : 'Guardar y verificar hoy'}
        </button>
        <button type="button" onClick={onCancel} className={buttonSecondaryClass}>
          Cancelar
        </button>
      </div>
    </form>
  );
}
