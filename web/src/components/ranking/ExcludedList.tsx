/**
 * Ineligible and excluded options (S11, S15).
 *
 * Nothing is silently dropped: an option that cannot be ranked is listed here
 * with its reason in Spanish, grouped by reason so the common one does not hide
 * the rest. The API's own `detail` is developer-facing English, so the copy is
 * built from the reader's own amount and term (see `ineligibleExplanation`).
 */
import { formatDate, formatInteger } from '../../lib/format';
import { excludedReasonLabel, flagLabel, ineligibleExplanation, kindLabel } from '../../lib/labels';
import type { Ranking, ValidationFlag } from '../../lib/types';

export interface ExcludedListProps {
  ranking: Ranking;
}

function Flags({ flags }: { flags: ValidationFlag[] }) {
  if (flags.length === 0) return null;
  const labels = [...new Set(flags.map((flag) => flagLabel(flag.code)))];
  return <span> · {labels.join(', ')}</span>;
}

export default function ExcludedList({ ranking }: ExcludedListProps) {
  const { amount, days } = ranking.request;
  const total = ranking.ineligible.length + ranking.excluded.length;

  if (total === 0) return null;

  const grouped = new Map<string, Ranking['excluded']>();
  for (const entry of ranking.excluded) {
    const group = grouped.get(entry.reason);
    if (group === undefined) {
      grouped.set(entry.reason, [entry]);
    } else {
      group.push(entry);
    }
  }

  return (
    <details className="rounded-lg border border-border bg-surface-raised px-4 py-3">
      <summary className="cursor-pointer text-body-sm font-semibold text-accent">
        {formatInteger(total)} opciones quedaron afuera: mirá por qué
      </summary>
      <div className="mt-4 flex flex-col gap-6">
        {ranking.ineligible.length > 0 && (
          <section className="flex flex-col gap-2">
            <h3 className="text-body-sm font-semibold">
              No llegan con tu monto o tu plazo ({formatInteger(ranking.ineligible.length)})
            </h3>
            <ul className="flex flex-col gap-1">
              {ranking.ineligible.map((entry) => (
                <li key={entry.id} className="text-body-sm text-muted">
                  <span className="text-text">{entry.name}</span> · {kindLabel(entry.kind)} ·{' '}
                  {ineligibleExplanation(entry.reason, { days, amount })}
                </li>
              ))}
            </ul>
          </section>
        )}
        {[...grouped.entries()].map(([reason, entries]) => (
          <section key={reason} className="flex flex-col gap-2">
            <h3 className="text-body-sm font-semibold">
              {excludedReasonLabel(reason)} ({formatInteger(entries.length)})
            </h3>
            <ul className="flex flex-col gap-1">
              {entries.map((entry) => (
                <li key={entry.id} className="text-body-sm text-muted">
                  <span className="text-text">{entry.name}</span> · {kindLabel(entry.kind)} ·{' '}
                  {entry.source.displayName} · dato del {formatDate(entry.dataDate)}
                  <Flags flags={entry.flags} />
                </li>
              ))}
            </ul>
          </section>
        ))}
      </div>
    </details>
  );
}
