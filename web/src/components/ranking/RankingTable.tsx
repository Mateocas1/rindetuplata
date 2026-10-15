/**
 * The ranking table (S15, S11, S10, L-F).
 *
 * Semantic table, no JavaScript required: it is server-rendered and the island
 * only re-renders it with fresh data. Money earned for the reader's amount and
 * term is the primary column; the real return is a labelled badge; TNA, source,
 * data date and confidence are secondary. Every amount carries `tabular-nums`.
 *
 * Narrow screens: the table keeps its semantics inside a labelled, focusable
 * scroll region with a sticky first column and a visible cue, rather than
 * dropping columns (S11 requires source and date on every row).
 */
import { formatDate, formatDays, formatInteger, formatMoney, formatPercent } from '../../lib/format';
import { confidenceLabel, flagLabel, inflationIndexLabel, inflationIndexShortLabel, kindLabel, rateKindLabel } from '../../lib/labels';
import { instrumentPageHref } from '../../lib/slug';
import type { RankedEntry, Ranking } from '../../lib/types';
import RealReturnBadge from './RealReturnBadge';

const headCell = 'border-b border-border px-4 py-3 text-caption font-semibold text-muted';

function rowCell(isLast: boolean): string {
  return `px-4 py-3 align-top ${isLast ? '' : 'border-b border-border'}`;
}

function isStale(entry: RankedEntry): boolean {
  return entry.warnings.some((warning) => warning.code === 'STALE');
}

export default function RankingTable({
  ranking,
  pageSlugs,
}: {
  ranking: Ranking;
  /** Slugs the build actually produced; a row outside the set stays plain text (D1). */
  pageSlugs: string[];
}) {
  const { amount, days } = ranking.request;
  const pages = new Set(pageSlugs);
  const indexLabel =
    ranking.inflation === null ? 'sin dato de inflación' : inflationIndexLabel(ranking.inflation.index);
  // S10: the badge column names the index it uses, visibly, right in the header.
  const indexHeader =
    ranking.inflation === null
      ? 'Real vs. inflación'
      : `Real vs. inflación (${inflationIndexShortLabel(ranking.inflation.index)})`;

  return (
    <div className="flex flex-col gap-2">
      <p className="text-caption text-muted md:hidden">
        Deslizá la tabla para ver todas las columnas.
      </p>
      <div className="overflow-hidden rounded-lg border border-border bg-surface-raised">
        <div
          className="overflow-x-auto"
          role="region"
          aria-label={`Ranking para ${formatMoney(amount)} a ${formatDays(days)}`}
          tabIndex={0}
        >
          <table className="w-full min-w-[46rem] border-separate border-spacing-0 text-body-sm">
            <caption className="sr-only">
              Las {formatInteger(ranking.ranked.length)} mejores opciones para {formatMoney(amount)} a{' '}
              {formatDays(days)}, ordenadas por los pesos que ganás. La columna "real" compara
              contra la inflación esperada del {indexLabel}.
            </caption>
            <thead>
              <tr>
                <th scope="col" className={`${headCell} sticky start-0 z-10 bg-surface-raised text-start`}>
                  Opción
                </th>
                <th scope="col" className={`${headCell} text-end`}>
                  Ganás en {formatDays(days)}
                </th>
                <th scope="col" className={`${headCell} text-start`}>
                  {indexHeader}
                </th>
                <th scope="col" className={`${headCell} text-end`}>
                  TNA
                </th>
                <th scope="col" className={`${headCell} text-start`}>
                  Fuente y fecha
                </th>
              </tr>
            </thead>
            <tbody>
              {ranking.ranked.map((entry, index) => {
                const cell = rowCell(index === ranking.ranked.length - 1);
                const href = instrumentPageHref(pages, entry);
                return (
                  <tr key={entry.id}>
                    <th
                      scope="row"
                      className={`${cell} sticky start-0 z-10 bg-surface-raised text-start font-normal`}
                    >
                      {href === null ? (
                        <span className="block font-medium text-text">{entry.name}</span>
                      ) : (
                        <a
                          href={href}
                          className="block font-medium text-text underline decoration-border underline-offset-2 hover:decoration-accent"
                        >
                          {entry.name}
                        </a>
                      )}
                      {entry.entity !== null && entry.entity !== entry.name && (
                        <span className="block text-caption text-muted">{entry.entity}</span>
                      )}
                      <span className="mt-1 inline-flex rounded-pill bg-subtle-surface px-2 py-0.5 text-caption text-muted">
                        {kindLabel(entry.kind)}
                      </span>
                    </th>
                    <td className={`${cell} text-end`}>
                      <span className="block font-semibold tabular-nums">
                        {formatMoney(entry.earned)}
                      </span>
                      <span className="block text-caption text-muted tabular-nums">
                        {formatMoney(entry.finalAmount)} al vencimiento
                      </span>
                      {entry.upTo !== null && (
                        <span className="mt-1 block text-caption text-warning tabular-nums">
                          Hasta {formatMoney(entry.upTo.finalAmount)} · {entry.upTo.condition}
                        </span>
                      )}
                    </td>
                    <td className={cell}>
                      <RealReturnBadge realReturn={entry.realReturn} indexLabel={indexLabel} />
                    </td>
                    <td className={`${cell} text-end`}>
                      <span className="block tabular-nums">
                        {formatPercent(entry.rateUsed.fraction)}
                      </span>
                      <span className="block text-caption text-muted">
                        {rateKindLabel(entry.rateUsed.kind)}
                      </span>
                    </td>
                    <td className={`${cell} text-caption text-muted`}>
                      <span className="block">
                        {entry.source.displayName}
                        {entry.source.official ? ' · oficial' : ''}
                      </span>
                      <span className="block">Dato del {formatDate(entry.dataDate)}</span>
                      <span className="block">
                        {confidenceLabel(entry.confidence)}
                        {entry.verifiedAt !== null
                          ? ` · verificada el ${formatDate(entry.verifiedAt)}`
                          : ''}
                      </span>
                      {isStale(entry) && (
                        <span className="mt-1 inline-flex rounded-pill bg-warning-surface px-2 py-0.5 text-caption text-on-warning">
                          {flagLabel('STALE')}
                        </span>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </div>
      <p className="text-caption text-muted">
        El monto ganado ya tiene aplicados el tope y las franjas de cada producto y, para billeteras
        y fondos, el interés compuesto diario. El rendimiento real usa la inflación esperada (
        {indexLabel}
        {ranking.inflation === null
          ? ''
          : `: ${formatPercent(ranking.inflation.twelveMonthRate)} a 12 meses, ${formatPercent(
              ranking.inflation.overHorizon,
            )} en ${formatDays(days)} (observación del ${formatDate(ranking.inflation.observationDate)})`}
        ).{' '}
        <a
          className="text-accent underline decoration-from-font underline-offset-2 hover:no-underline"
          href="/metodologia/"
        >
          Cómo se calcula cada número
        </a>
        .
      </p>
    </div>
  );
}
