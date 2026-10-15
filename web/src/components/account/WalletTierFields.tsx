/**
 * Editable balance bands of a curated wallet rate (T7d).
 *
 * One row per tier (floor, ceiling, TNA percent), reused for the base schedule
 * and for each condition's schedule. Errors are keyed by the API's own
 * `parameter` names, so a 400 from `PUT /wallet-rates/{id}` lands on the same
 * input. Empty floor or ceiling means "no bound", which is what the API stores
 * as null. Labels use the one field label style of the account pages (T12c).
 */
import type { TierFormState } from '../../lib/accountForm';
import { formatInteger } from '../../lib/format';
import { buttonSecondaryClass, errorTextClass, inputClass, labelClass } from './ui';

export interface WalletTierFieldsProps {
  legend: string;
  fieldPrefix: string;
  tiers: TierFormState[];
  errors: Record<string, string>;
  onChange: (tiers: TierFormState[]) => void;
  describedBy?: string;
}

export default function WalletTierFields({
  legend,
  fieldPrefix,
  tiers,
  errors,
  onChange,
  describedBy,
}: WalletTierFieldsProps) {
  const orderingErrorId = `${fieldPrefix}-error`;
  const orderingError = errors[fieldPrefix];
  const describedIds = [
    orderingError !== undefined ? orderingErrorId : undefined,
    describedBy,
  ].filter((value): value is string => value !== undefined);

  const update = (index: number, patch: Partial<TierFormState>): void => {
    onChange(tiers.map((tier, position) => (position === index ? { ...tier, ...patch } : tier)));
  };

  return (
    <fieldset
      className="flex flex-col gap-3"
      aria-describedby={describedIds.length > 0 ? describedIds.join(' ') : undefined}
    >
      <legend className={labelClass}>{legend}</legend>
      <p className="type-body text-muted">
        Dejá el piso o el techo vacío cuando la franja no tiene ese límite.
      </p>
      {orderingError !== undefined && (
        <p id={orderingErrorId} className={errorTextClass}>
          {orderingError}
        </p>
      )}

      <ul className="flex flex-col gap-3">
        {tiers.map((tier, index) => {
          const floorId = `${fieldPrefix}[${index}].floor`;
          const ceilingId = `${fieldPrefix}[${index}].ceiling`;
          const tnaId = `${fieldPrefix}[${index}].tnaPercent`;
          return (
            <li
              key={index}
              className="flex flex-wrap items-start gap-x-4 gap-y-3 rounded-panel border border-border bg-surface p-3"
            >
              <div className="flex flex-col gap-1">
                <label htmlFor={floorId} className={labelClass}>
                  Piso en pesos
                </label>
                <input
                  id={floorId}
                  name={floorId}
                  inputMode="decimal"
                  value={tier.floor}
                  aria-invalid={errors[floorId] !== undefined}
                  aria-describedby={errors[floorId] === undefined ? undefined : `${floorId}-error`}
                  onChange={(event) => update(index, { floor: event.target.value })}
                  className={inputClass}
                />
                {errors[floorId] !== undefined && (
                  <p id={`${floorId}-error`} className={errorTextClass}>
                    {errors[floorId]}
                  </p>
                )}
              </div>

              <div className="flex flex-col gap-1">
                <label htmlFor={ceilingId} className={labelClass}>
                  Techo en pesos
                </label>
                <input
                  id={ceilingId}
                  name={ceilingId}
                  inputMode="decimal"
                  value={tier.ceiling}
                  aria-invalid={errors[ceilingId] !== undefined}
                  aria-describedby={
                    errors[ceilingId] === undefined ? undefined : `${ceilingId}-error`
                  }
                  onChange={(event) => update(index, { ceiling: event.target.value })}
                  className={inputClass}
                />
                {errors[ceilingId] !== undefined && (
                  <p id={`${ceilingId}-error`} className={errorTextClass}>
                    {errors[ceilingId]}
                  </p>
                )}
              </div>

              <div className="flex flex-col gap-1">
                <label htmlFor={tnaId} className={labelClass}>
                  TNA en porcentaje
                </label>
                <input
                  id={tnaId}
                  name={tnaId}
                  inputMode="decimal"
                  value={tier.tnaPercent}
                  aria-invalid={errors[tnaId] !== undefined}
                  aria-describedby={errors[tnaId] === undefined ? undefined : `${tnaId}-error`}
                  onChange={(event) => update(index, { tnaPercent: event.target.value })}
                  className={inputClass}
                />
                {errors[tnaId] !== undefined && (
                  <p id={`${tnaId}-error`} className={errorTextClass}>
                    {errors[tnaId]}
                  </p>
                )}
              </div>

              <button
                type="button"
                onClick={() => onChange(tiers.filter((_, position) => position !== index))}
                className={`${buttonSecondaryClass} self-end`}
              >
                Quitar franja {formatInteger(index + 1)}
              </button>
            </li>
          );
        })}
      </ul>

      <button
        type="button"
        onClick={() => onChange([...tiers, { floor: '', ceiling: '', tnaPercent: '' }])}
        className={`${buttonSecondaryClass} self-start`}
      >
        Agregar franja
      </button>
    </fieldset>
  );
}
