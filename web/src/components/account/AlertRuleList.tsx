/**
 * The alert rules of `/alertas` (T12c).
 *
 * A list of one-line rows inside the panel's `card`, read the way the ranking
 * table reads: the rule is the row header in `type-body`, its instrument in the
 * quiet mono `type-data` line, and the threshold is the figure the reader came
 * for (mono `type-data` at the row figure step, semibold) under its mono caps
 * `type-label` head. A rule of another type (T6c) is named by what it tells the
 * reader, shows the kind or option it watches, and says when it tells them. The rows stay a `ul`/`li` list — a table would drop the
 * `listitem` role an end-to-end walk of this page relies on — while they copy
 * the ranking table's row treatment: dividers, muted labels, tabular figures.
 *
 * The delete confirmation takes over the row that owns it: the confirm carries
 * the loss tone and the cancel is a peer action, so this view still has exactly
 * one primary button, the create form's.
 */
import { useEffect, useRef } from 'react';
import type { AlertRule, InstrumentSummary } from '../../lib/account';
import { alertTypeLabel, instrumentLabelById, percentToFraction } from '../../lib/accountForm';
import { formatPercent } from '../../lib/format';
import { kindLabel } from '../../lib/labels';
import { buttonSecondaryClass, cardClass } from './ui';

const ROW = 'flex flex-col gap-3 border-b border-border px-4 py-4 last:border-b-0';
const ROW_FIGURE = 'flex flex-col gap-0.5 sm:items-end sm:text-end';

/** The destructive confirmation: the `btn-danger` recipe (loss fill, measured pair). */
const CONFIRM = 'btn-danger';

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

/** A threshold rule is named by its kind, as it always was; every other type by what it tells the reader. */
function ruleTitle(rule: AlertRule): string {
  return rule.type === 'THRESHOLD' ? ruleKindLabel(rule) : alertTypeLabel(rule.type);
}

/** What the rule watches: its option (a threshold or "bajó una tasa" rule) or the kind it covers. */
function ruleTarget(rule: AlertRule, instruments: InstrumentSummary[]): string {
  return rule.type === 'THRESHOLD' || rule.type === 'RATE_DROP'
    ? ruleInstrumentLabel(rule, instruments)
    : ruleKindLabel(rule);
}

/** The right-hand fact of a row: the rate a threshold rule waits for, or when the other types tell the reader. */
function ruleFigure(rule: AlertRule): { head: string; value: string; figure: boolean } {
  switch (rule.type) {
    case 'THRESHOLD':
      return {
        head: 'Avisa si la tasa supera',
        value: rule.thresholdPercent === null ? '—' : formatPercent(percentToFraction(rule.thresholdPercent)),
        figure: true,
      };
    case 'BEST_CHANGED':
      return { head: 'Te avisamos', value: 'Si cambia la mejor', figure: false };
    case 'RATE_DROP':
      return { head: 'Te avisamos', value: 'Si baja su tasa', figure: false };
    case 'WEEKLY_DIGEST':
      return { head: 'Te avisamos', value: 'Cada semana', figure: false };
    default:
      return { head: 'Te avisamos', value: '—', figure: false };
  }
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
        <p className="type-body font-medium">Todavía no tenés alertas</p>
        <p className="type-body text-muted">
          Una alerta te avisa por email cuando una opción supera la tasa que le pongas, cuando cambia
          la mejor opción o cuando baja una tasa, o te manda un resumen cada semana. Armá la primera
          con el formulario de arriba.
        </p>
      </div>
    );
  }

  return (
    <div className="card overflow-hidden">
      <ul className="flex flex-col">
        {rules.map((rule) => {
          const title = ruleTitle(rule);
          const target = ruleTarget(rule, instruments);
          const figure = ruleFigure(rule);
          const description = `Eliminar la alerta de ${title} sobre ${target}`;
          const confirming = confirmingId === rule.id;
          return (
            <li
              key={rule.id}
              className={`${ROW} sm:grid sm:grid-cols-[minmax(0,1fr)_12.5rem_auto] sm:items-center sm:gap-6 ${
                confirming ? 'bg-subtle-surface' : ''
              }`}
            >
              <div className="flex min-w-0 flex-col gap-1">
                <p className="type-body font-medium">{title}</p>
                <p className="type-data text-muted">{target}</p>
                {rule.active === false && (
                  <p className="type-data text-warning">Pausada: no envía avisos.</p>
                )}
              </div>

              <div className={ROW_FIGURE}>
                <p className="type-label">{figure.head}</p>
                <p className={`type-data font-semibold ${figure.figure ? 'text-figure-row' : ''}`}>
                  {figure.value}
                </p>
              </div>

              {confirming ? (
                <div
                  role="group"
                  aria-label={`Confirmar: ${description}`}
                  className="flex flex-col gap-2"
                >
                  <p className="type-body">¿Eliminar esta alerta?</p>
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
