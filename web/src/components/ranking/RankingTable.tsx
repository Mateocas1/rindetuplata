/**
 * The ranking board (S15, S11, S10, L-F).
 *
 * Semantic table, no JavaScript required: it is server-rendered and the island
 * only re-renders it with fresh data. The pesos earned for the reader's amount
 * and term is the protagonist of every row (`type-figure` at the row step); the
 * option's name is the reading step, the mono index gutter opens the row as
 * `01`-`08`, and the provenance is ONE quiet `type-data` line, never a stack of
 * small grey prose (T13b, L77).
 *
 * Narrow screens: the table keeps its semantics inside a labelled, focusable
 * scroll region with a sticky first column and a visible cue, rather than
 * dropping columns (S11 requires source and date on every row). Below `md` the
 * pesos earned is repeated inside that sticky cell, under the name, and the
 * wide copy is hidden there: the protagonist figure of the row is on screen
 * from 320 up without a sideways drag, and is never announced twice (T13e).
 * Row 01 carries the cobalt rail and the "Mejor" chip: the rail is the one
 * accent, doing the one job it shares with the headline and the glass card
 * (T13e: the chip itself is ink).
 *
 * The sticky cells stay at `z-0`: they must cover their own scrolled siblings,
 * but never the sticky site header above them (z-10), or a scrolled row would
 * paint over the page chrome.
 */
import { formatDate, formatDays, formatInteger, formatMoney, formatPercent } from '../../lib/format';
import {
  flagLabel,
  inflationIndexLabel,
  inflationIndexShortLabel,
  kindLabel,
  rateKindShortLabel,
} from '../../lib/labels';
import { instrumentPageHref } from '../../lib/slug';
import type { RankedEntry, Ranking } from '../../lib/types';
import RealReturnBadge from './RealReturnBadge';

/** Mono caps column heads, the same role as the row labels below them. */
const headCell = 'border-b border-border bg-subtle-surface px-4 py-3 type-label';
/** The index gutter and the option name share one sticky cell, so the name never scrolls away. */
const identCell = 'grid grid-cols-[2rem_minmax(0,1fr)] gap-x-3';

function rowCell(isLast: boolean): string {
  return `px-4 py-3 align-top ${isLast ? '' : 'border-b border-border'}`;
}

function isStale(entry: RankedEntry): boolean {
  return entry.warnings.some((warning) => warning.code === 'STALE');
}

/**
 * The one quiet provenance line of a row (S11, T13e): where the rate comes from
 * and the date of the data, on one line at 1280. The confidence level and the
 * verification date live on the instrument page and in the methodology, so the
 * row never becomes a wall of small grey prose (L77).
 */
function provenance(entry: RankedEntry): string {
  const source = entry.source.official
    ? `${entry.source.displayName} · oficial`
    : entry.source.displayName;
  return `${source} · ${formatDate(entry.dataDate)}`;
}

export interface RankingTableProps {
  ranking: Ranking;
  /** Slugs the build actually produced; a row outside the set stays plain text (D1). */
  pageSlugs: string[];
  /** The home board shows the first options only; the rest follow behind a disclosure. */
  limit?: number;
  /** Continue the gutter in the table that follows a limited one. */
  offset?: number;
}

export default function RankingTable({
  ranking,
  pageSlugs,
  limit,
  offset = 0,
}: RankingTableProps) {
  const { amount, days } = ranking.request;
  const pages = new Set(pageSlugs);
  const ranked = ranking.ranked.slice(offset);
  const rows = limit === undefined ? ranked : ranked.slice(0, limit);
  const indexLabel =
    ranking.inflation === null ? 'sin dato de inflación' : inflationIndexLabel(ranking.inflation.index);
  // S10: the badge column names the index it uses, visibly, right in the header.
  const indexHeader =
    ranking.inflation === null
      ? 'Real vs. inflación'
      : `Real vs. inflación (${inflationIndexShortLabel(ranking.inflation.index)})`;
  // The note belongs to the board, not to the continuation table.
  const primary = offset === 0;

  return (
    <div className="flex flex-col gap-2">
      <p className="type-label md:hidden">Deslizá la tabla para ver todas las columnas.</p>
      <div className="card overflow-hidden">
        <div
          className="relative overflow-x-auto focus-visible:-outline-offset-2"
          role="region"
          aria-label={`Ranking para ${formatMoney(amount)} a ${formatDays(days)}${
            primary ? '' : ', opciones siguientes'
          }`}
          tabIndex={0}
        >
          <table className="w-full min-w-[48rem] table-fixed border-separate border-spacing-0">
            {/* Fixed proportions: the option (with its index gutter) keeps the
                widest column, so a long fund class never wraps one word a line,
                the TNA kind fits in two or three words and the provenance line
                holds its source and date together (T13e). */}
            <colgroup>
              <col className="w-[30%]" />
              <col className="w-[18%]" />
              <col className="w-[13%]" />
              <col className="w-[15%]" />
              <col className="w-[24%]" />
            </colgroup>
            <caption className="sr-only">
              Las {formatInteger(rows.length)} mejores opciones para {formatMoney(amount)} a{' '}
              {formatDays(days)}, ordenadas por los pesos que ganás. La columna "real" compara
              contra la inflación esperada del {indexLabel}.
            </caption>
            <thead>
              <tr>
                <th scope="col" className={`${headCell} sticky start-0 z-0 text-start`}>
                  <span className={identCell}>
                    <span>Nº</span>
                    <span>Opción</span>
                  </span>
                </th>
                <th scope="col" className={`${headCell} text-end`}>
                  Ganás en {formatDays(days)}
                </th>
                <th scope="col" className={`${headCell} whitespace-nowrap text-start`}>
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
              {rows.map((entry, index) => {
                const position = offset + index + 1;
                const leader = position === 1;
                const last = position === ranking.ranked.length;
                const cell = rowCell(last);
                const href = instrumentPageHref(pages, entry);
                const entity =
                  entry.entity !== null && entry.entity !== entry.name ? entry.entity : null;
                return (
                  <tr key={entry.id} className="group hover:bg-subtle-surface">
                    <th
                      scope="row"
                      className={`${cell} sticky start-0 z-0 bg-surface-raised text-start font-normal group-hover:bg-subtle-surface`}
                    >
                      {leader && (
                        <span aria-hidden="true" className="absolute inset-y-0 start-0 w-0.5 bg-accent" />
                      )}
                      <span className={identCell}>
                        <span className="type-index">{String(position).padStart(2, '0')}</span>
                        <span className="min-w-0">
                          <span className="block">
                            {href === null ? (
                              <span className="type-body font-medium">{entry.name}</span>
                            ) : (
                              <a
                                href={href}
                                className="type-body font-medium text-text underline decoration-border underline-offset-2 hover:decoration-text"
                              >
                                {entry.name}
                              </a>
                            )}
                            {leader && (
                              <span className="chip ms-2 bg-subtle-surface">
                                <span className="text-text">Mejor</span>
                              </span>
                            )}
                          </span>
                          <span className="mt-1 block type-data text-muted">
                            {kindLabel(entry.kind)}
                            {entity === null ? '' : ` · ${entity}`}
                          </span>
                          {/* The first column is the only one on screen below md
                              (T13e): the row's protagonist figure lives here too. */}
                          <span className="mt-2 flex flex-wrap items-baseline gap-x-2 md:hidden">
                            <span className="type-label">Ganás en {formatDays(days)}</span>
                            <span className="type-figure text-figure-row">
                              {formatMoney(entry.earned)}
                            </span>
                          </span>
                          {isStale(entry) && (
                            <span className="block type-data text-warning">
                              {flagLabel('STALE')}
                            </span>
                          )}
                        </span>
                      </span>
                    </th>
                    <td className={`${cell} text-end`}>
                      <span className="hidden type-figure text-figure-row md:block">
                        {formatMoney(entry.earned)}
                      </span>
                      <span className="block type-data text-muted">
                        {formatMoney(entry.finalAmount)} al vencimiento
                      </span>
                      {entry.upTo !== null && (
                        <span className="mt-1 block type-data text-warning">
                          Hasta {formatMoney(entry.upTo.finalAmount)} · {entry.upTo.condition}
                        </span>
                      )}
                    </td>
                    <td className={cell}>
                      <RealReturnBadge realReturn={entry.realReturn} indexLabel={indexLabel} />
                    </td>
                    <td className={`${cell} text-end`}>
                      <span className="block type-data">
                        {formatPercent(entry.rateUsed.fraction)}
                      </span>
                      <span className="block type-data text-muted">
                        {rateKindShortLabel(entry.rateUsed.kind)}
                      </span>
                    </td>
                    <td className={`${cell} type-data text-muted`}>{provenance(entry)}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </div>
      {primary && (
        <p className="type-data text-muted">
          El monto ganado ya tiene aplicados el tope y las franjas de cada producto y, para
          billeteras y fondos, el interés compuesto diario. El retorno real es lo que le ganás a la
          inflación esperada
          {ranking.inflation === null
            ? ''
            : ` (${indexLabel}: ${formatPercent(ranking.inflation.twelveMonthRate)} a 12 meses, ${formatPercent(
                ranking.inflation.overHorizon,
              )} en ${formatDays(days)}, observación del ${formatDate(ranking.inflation.observationDate)})`}
          .{' '}
          <a
            className="underline decoration-border-strong underline-offset-2 hover:decoration-text"
            href="/metodologia/"
          >
            Cómo se calcula cada número
          </a>
          .
        </p>
      )}
    </div>
  );
}
