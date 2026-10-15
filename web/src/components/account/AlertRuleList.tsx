/**
 * The alert rules of `/alertas` (T12c).
 *
 * A list of one-line rows inside the panel's `card`, read the way the ranking
 * table reads: the rule is the row header, the threshold is the figure the
 * reader came for (`tabular-nums`, semibold) with its caption above it, and the
 * row's action trails it. The rows stay a `ul`/`li` list — a table would drop
 * the `listitem` role an end-to-end walk of this page relies on — while they
 * copy the ranking table's row treatment: dividers, muted captions, tabular
 * figures.
 *
 * The delete confirmation takes over the row that owns it: the confirm carries
 * the loss tone and the cancel is a peer action, so this view still has exactly
 * one primary button, the create form's.
 */
import { useEffect, useRef } from 'react';
import type { AlertRule, InstrumentSummary } from '../../lib/account';
import { instrumentLabelById, percentToFraction } from '../../lib/accountForm';
import { formatPercent } from '../../lib/format';
import { kindLabel } from '../../lib/labels';
import { buttonSecondaryClass, cardClass } from './ui';

const ROW = 'flex flex-col gap-3 border-b border-border px-4 py-4 last:border-b-0';
const ROW_FIGURE = 'flex flex-col gap-0.5 sm:items-end';

/**
 * The destructive confirmation keeps the loss role: `tokens.test.ts` measures
 * `on-accent` on `loss` in both appearances, so the fill is not a guess.
 */
const CONFIRM =
  'inline-flex min-h-10 items-center justify-center gap-2 rounded-md bg-loss px-4 py-2 text-body-sm font-semibold text-on-accent disabled:cursor-not-allowed disabled:opacity-60';

export interface AlertRuleListProps {
  rules: AlertRule[];
  instruments: InstrumentSummary[];
  /** The rule whose inline confirmation is open, if any. */
  confirmingId: number | null;
  /** The rule being deleted right now, if any. */
  deletingId: number | null;
  onRequestDelete: (id: number) => void;
  onCancelDelete: (id: number) => void;
  onConfirmDelete: (id: number) => void;
}

function ruleKindLabel(rule: AlertRule): string {
  return rule.kind === null ? 'Cualquier tipo' : kindLabel(rule.kind);
}

function ruleInstrumentLabel(rule: AlertRule, instruments: InstrumentSummary[]): string {
  if (rule.instrumentId === null) return 'Cualquier opción';
  return instrumentLabelById(instruments, rule.instrumentId) ?? `La opción #${rule.instrumentId}`;
}

export default function AlertRuleList({
  rules,
  instruments,
  confirmingId,
  deletingId,
  onRequestDelete,
  onCancelDelete,
  onConfirmDelete,
}: AlertRuleListProps) {
  const confirmRef = useRef<HTMLButtonElement>(null);

  // The confirmation takes over the row, so focus moves into it and back out.
  useEffect(() => {
    if (confirmingId !== null) confirmRef.current?.focus();
  }, [confirmingId]);

  if (rules.length === 0) {
    return (
      <div className={`${cardClass} flex flex-col gap-2`}>
        <p className="font-medium">Todavía no tenés alertas</p>
        <p className="text-body-sm text-muted">
          Una alerta te avisa por email cuando una opción supera la tasa que le pongas. Armá la
          primera con el formulario de arriba.
        </p>
      </div>
    );
  }

  return (
    <div className="card overflow-hidden">
      <ul className="flex flex-col">
        {rules.map((rule) => {
          const instrument = ruleInstrumentLabel(rule, instruments);
          const description = `Eliminar la alerta de ${ruleKindLabel(rule)} sobre ${instrument}`;
          const confirming = confirmingId === rule.id;
          return (
            <li
              key={rule.id}
              className={`${ROW} sm:flex-row sm:items-center sm:justify-between sm:gap-6 ${
                confirming ? 'bg-subtle-surface' : ''
              }`}
            >
              <div className="flex min-w-0 flex-col gap-1">
                <p className="font-medium">{ruleKindLabel(rule)}</p>
                <p className="text-caption text-muted">{instrument}</p>
                {rule.active === false && (
                  <p className="text-caption text-warning">Pausada: no envía avisos.</p>
                )}
              </div>

              <div className={ROW_FIGURE}>
                <p className="text-caption text-muted">Avisa si la tasa supera</p>
                <p className="font-semibold tabular-nums">
                  {formatPercent(percentToFraction(rule.thresholdPercent))}
                </p>
              </div>

              {confirming ? (
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
                      onClick={() => onConfirmDelete(rule.id)}
                      className={CONFIRM}
                    >
                      {deletingId === rule.id ? 'Eliminando…' : 'Sí, eliminar la alerta'}
                    </button>
                    <button
                      type="button"
                      onClick={() => onCancelDelete(rule.id)}
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
                  onClick={() => onRequestDelete(rule.id)}
                  className={`${buttonSecondaryClass} self-start sm:self-auto`}
                >
                  Eliminar
                </button>
              )}
            </li>
          );
        })}
      </ul>
    </div>
  );
}
