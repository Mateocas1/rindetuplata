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
 *
 * T12b: the whole table sits in a `card`, each row header opens with its
 * position, the pesos earned is the one protagonist and every flag is a `chip`.
 * Row hover is instant (no transition; motion arrives with T12d).
 *
 * The sticky cells stay at `z-0`: they must cover their own scrolled siblings,
 * but never the sticky site header above them (z-10), or a scrolled row would
 * paint over the page chrome.
 */
import { formatDate, formatDays, formatInteger, formatMoney, formatPercent } from '../../lib/format';
import { confidenceLabel, flagLabel, inflationIndexLabel, inflationIndexShortLabel, kindLabel, rateKindLabel } from '../../lib/labels';
import { instrumentPageHref } from '../../lib/slug';
import type { RankedEntry, Ranking } from '../../lib/types';
import RealReturnBadge from './RealReturnBadge';

const headCell =
  'border-b border-border bg-subtle-surface px-4 py-3 text-caption font-semibold uppercase tracking-[0.05em] text-muted';

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
      <div className="card overflow-hidden">
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
                <th scope="col" className={`${headCell} sticky start-0 z-0 text-start`}>
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
                  <tr key={entry.id} className="group hover:bg-subtle-surface">
                    <th
                      scope="row"
                      className={`${cell} sticky start-0 z-0 bg-surface-raised text-start font-normal group-hover:bg-subtle-surface`}
                    >
                      <span className="block text-caption text-muted tabular-nums">
                        {index + 1}
                      </span>
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
                      <span className="chip mt-1 bg-subtle-surface text-muted">
                        {kindLabel(entry.kind)}
                      </span>
                    </th>
                    <td className={`${cell} text-end`}>
                      <span className="block text-body font-semibold tabular-nums">
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
                        <span className="chip mt-1 bg-warning-surface text-on-warning">
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
          className="link"
          href="/metodologia/"
        >
          Cómo se calcula cada número
        </a>
        .
      </p>
    </div>
  );
}
