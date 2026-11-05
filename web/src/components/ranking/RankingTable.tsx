/**
 * The ranking board (MVP S15, S11, S10; ui-clarity D6-D9, D13, D18, D19).
 *
 * Server-rendered, no JavaScript required: the island only re-renders it with fresh data. The pesos earned for
 * the reader's amount and term is the protagonist of every row; the rows rank by it with standard competition
 * positions, so equal earnings share a place ("=04", D7), and every row after the best says how far it is from
 * it in whole pesos (D8). The real result is a line in words from the shared `RealResult` (D6, D19).
 *
 * Provenance (S11, D18): the source and the date of every row live in its one quiet secondary line, with the
 * kind and the liquidity (D5); a caption above the board states the common source and the newest date once.
 *
 * Two views of the same rows (D9): from `md` up a semantic table; below it an ordered list of cards. The hidden
 * view is `display: none`, so assistive technology reads exactly one, and nothing scrolls sideways at 320px.
 * The switch sits at `md`, not `sm`: between 640 and 767px the five columns left the option ~130px wide and
 * wrapped the real-result sentence over three lines, where the card reads in one glance.
 * The cobalt rail marks the best option only (D14); the "Mejor" chip is ink.
 */
import type { ReactNode } from 'react';
import { liquidityForKind } from '../../lib/categories';
import { displayName } from '../../lib/displayName';
import {
  formatDate,
  formatDayMonth,
  formatDays,
  formatInteger,
  formatMoney,
  formatMoneyWhole,
  formatPercent,
} from '../../lib/format';
import {
  sourceLabel,
  flagLabel,
  inflationIndexLabel,
  inflationIndexShortLabel,
  kindLabel,
  rateKindShortLabel,
} from '../../lib/labels';
import { earnedFrom, gapToBest, formatPosition } from '../../lib/rankingPresentation';
import { instrumentPageHref } from '../../lib/slug';
import type { RankedEntry, Ranking } from '../../lib/types';
import RealResult from '../ui/RealResult';
import BreakEvenNote from './BreakEvenNote';
import { formatQueryAmount, rankPositions } from './positions';

const headCell = 'border-b border-border bg-subtle-surface px-4 py-3 align-bottom type-label';
const nameLink =
  'type-body font-medium text-text decoration-text underline-offset-2 hover:underline focus-visible:underline';

/** A curated rate names its load, not the English id of its source. */
function sourceName(entry: RankedEntry): string {
  const label = sourceLabel(entry);
  return entry.confidence !== 'CURATED_MANUAL' && entry.source.official ? `${label} (oficial)` : label;
}

function isStale(entry: RankedEntry): boolean {
  return entry.warnings.some((warning) => warning.code === 'STALE');
}

/** The source of a row and the day of its data: "ArgentinaDatos 14/10". */
function provenance(entry: RankedEntry): string {
  return `${sourceName(entry)} ${formatDayMonth(entry.dataDate)}`;
}

/** "Fuente: ArgentinaDatos · datos al 14/10/2026", for the rows of one table. */
function captionText(rows: RankedEntry[]): string {
  const sources = [...new Set(rows.map(sourceName))];
  const newest = rows.map((entry) => entry.dataDate).reduce((a, b) => (b > a ? b : a));
  const named =
    sources.length <= 3
      ? sources.length === 1
        ? sources[0]
        : `${sources.slice(0, -1).join(', ')} y ${sources.at(-1)}`
      : `${sources.slice(0, 2).join(', ')} y ${formatInteger(sources.length - 2)} más`;
  return `${sources.length === 1 ? 'Fuente' : 'Fuentes'}: ${named} · datos al ${formatDate(newest)}`;
}

/** Facts joined by a middle dot, as one line of text. */
function joined(parts: ReactNode[]): ReactNode[] {
  return parts.flatMap((part, index) => (index === 0 ? [part] : [' · ', part]));
}

interface Row {
  entry: RankedEntry;
  position: number;
  tied: boolean;
  /** Tied for first: the "Mejor" chip, and no gap. */
  best: boolean;
  /** Pesos below the best (negative), or null for the best ones. */
  gap: number | null;
  href: string | null;
  name: string;
}

/** "=04" on screen; "4, empate" for assistive technology (D7). */
function Position({ position, tied }: { position: number; tied: boolean }) {
  return (
    <>
      <span aria-hidden="true">{formatPosition(position, tied)}</span>
      <span className="sr-only">{tied ? `${position}, empate` : String(position)}</span>
    </>
  );
}

function Name({ row }: { row: Row }) {
  return row.href === null ? (
    <span className="type-body font-medium">{row.name}</span>
  ) : (
    <a href={row.href} className={nameLink}>
      {row.name}
    </a>
  );
}

function BestChip() {
  return (
    <span className="chip border border-border-strong">
      <span className="text-text">Mejor</span>
    </span>
  );
}

/**
 * The quiet line under a name: kind, liquidity, (the rate, in the card), the fund or account behind a brand,
 * folded fund classes, the source with its date and, when it applies, the stale flag.
 */
function Secondary({ entry, rate }: { entry: RankedEntry; rate?: boolean }) {
  const liquidity = liquidityForKind(entry.kind);
  const entity =
    entry.entity !== null && !entry.name.toUpperCase().includes(entry.entity.toUpperCase())
      ? displayName(entry.entity)
      : null;
  const folded = entry.foldedClasses ?? 0;
  const parts: ReactNode[] = [kindLabel(entry.kind)];
  if (liquidity !== null) parts.push(liquidity);
  if (rate === true) parts.push(`${formatPercent(entry.rateUsed.fraction)} ${rateKindShortLabel(entry.rateUsed.kind)}`);
  // A long fund or account name may wrap inside itself; every other fact keeps its words together.
  if (entity !== null) parts.push(<span key="entity" className="whitespace-normal">{entity}</span>);
  if (folded > 0) {
    parts.push(
      <span key="folded">
        {`+${formatInteger(folded)} ${folded === 1 ? 'clase' : 'clases'}`}
        <span className="sr-only">
          {' más del mismo fondo: se muestra la de mayor rendimiento para tu monto y plazo'}
        </span>
      </span>,
    );
  }
  parts.push(provenance(entry));
  if (isStale(entry)) {
    parts.push(
      <span key="stale" className="text-warning">
        {flagLabel('STALE')}
      </span>,
    );
  }
  return <>{joined(parts.map((part, index) => <Fact key={index}>{part}</Fact>))}</>;
}

/** One fact of the quiet line: its words stay together, so the line breaks between facts, never inside one. */
function Fact({ children }: { children: ReactNode }) {
  return <span className="whitespace-nowrap">{children}</span>;
}

/**
 * The "hasta" offer of a conditional rate, under the row's figure: what it would earn for the term, never the
 * final balance, since the column reads earnings (T7 fix round).
 */
function UpTo({ entry, amount }: { entry: RankedEntry; amount: number }) {
  if (entry.upTo === null) return null;
  return (
    <span className="mt-1 block type-data text-warning">
      <span className="whitespace-nowrap">Hasta {formatMoney(earnedFrom(amount, entry.upTo.finalAmount))}</span> ·{' '}
      {entry.upTo.condition}
    </span>
  );
}

/** The dólar comparison lives behind the row's own disclosure, so it costs nothing until opened. */
function MepDisclosure({ entry, ranking }: { entry: RankedEntry; ranking: Ranking }) {
  if (entry.breakEvenMep == null || ranking.dollarMep == null) return null;
  return (
    <details className="disclosure mt-2">
      <summary className="type-label hover:text-text">Contra el dólar MEP</summary>
      <div className="mt-2 flex flex-col gap-1">
        <BreakEvenNote breakEven={entry.breakEvenMep} quote={ranking.dollarMep} days={ranking.request.days} />
      </div>
    </details>
  );
}

export interface RankingTableProps {
  ranking: Ranking;
  /** Slugs the build actually produced; a row outside the set stays plain text (D1). */
  pageSlugs: string[];
  /** The home board shows the first options only; the rest follow behind a disclosure. */
  limit?: number;
  /** Continue the positions in the table that follows a limited one. */
  offset?: number;
  /**
   * Print the footnote under this table; by default the first table does. The home board turns it off and
   * places `RankingFootnote` after its disclosures, so the note closes the whole section.
   */
  footnote?: boolean;
}

export default function RankingTable({
  ranking,
  pageSlugs,
  limit,
  offset = 0,
  footnote = offset === 0,
}: RankingTableProps) {
  const { amount, days } = ranking.request;
  const pages = new Set(pageSlugs);
  const positions = rankPositions(ranking.ranked);
  const bestEarned = ranking.ranked[0]?.earned ?? 0;
  const end = limit === undefined ? undefined : offset + limit;
  const rows: Row[] = ranking.ranked.slice(offset, end).map((entry, index) => {
    const { position, tied } = positions[offset + index];
    const best = position === 1;
    return {
      entry,
      position,
      tied,
      best,
      gap: best ? null : gapToBest(bestEarned, entry.earned),
      href: instrumentPageHref(pages, entry),
      name: displayName(entry.name),
    };
  });
  if (rows.length === 0) return null;

  const indexLabel =
    ranking.inflation === null ? 'sin dato de inflación' : inflationIndexLabel(ranking.inflation.index);
  // S10: the real column names the index it uses, visibly, in its header.
  const indexHeader =
    ranking.inflation === null
      ? 'Contra la inflación'
      : `Contra la inflación (${inflationIndexShortLabel(ranking.inflation.index)})`;
  const primary = offset === 0;
  const query = `${formatQueryAmount(amount)} a ${formatDays(days)}`;
  const lastIndex = rows.length - 1;

  return (
    <div className="flex flex-col gap-3">
      <p className="type-label">{captionText(rows.map((row) => row.entry))}</p>

      <div className="card hidden overflow-hidden md:block">
        <table className="w-full border-separate border-spacing-0">
          <caption className="sr-only">
            {primary
              ? `Las ${formatInteger(rows.length)} mejores opciones para ${query}, ordenadas por los pesos que ganás.`
              : `Las opciones siguientes para ${query}.`}{' '}
            La columna de inflación compara contra la inflación esperada del {indexLabel}.
          </caption>
          <thead>
            <tr>
              <th scope="col" className={`${headCell} w-16 ps-4 pe-2 text-end`}>
                Nº
              </th>
              <th scope="col" className={`${headCell} ps-2 text-start`}>
                Opción
              </th>
              <th scope="col" className={`${headCell} text-end`}>
                Ganás en {formatDays(days)}
              </th>
              {/* From lg the real result keeps its sentence on one line; narrower, it may wrap. */}
              <th scope="col" className={`${headCell} text-start lg:min-w-72`}>
                {indexHeader}
              </th>
              <th scope="col" className={`${headCell} text-end`}>
                TNA
              </th>
            </tr>
          </thead>
          <tbody>
            {rows.map((row, index) => {
              const { entry } = row;
              // Every cell opens on the reading line (`type-body` as the strut) with an inline first figure,
              // so the position, the name, the pesos, the real result and the rate share one baseline.
              const cell = `py-4 align-top ${index === lastIndex ? '' : 'border-b border-border'}`;
              return (
                <tr key={entry.id} className="hover:bg-subtle-surface">
                  <td className={`${cell} relative ps-4 pe-2 text-end type-body`}>
                    {row.best && (
                      <span aria-hidden="true" className="absolute inset-y-0 start-0 w-0.5 bg-accent" />
                    )}
                    <span className="type-index">
                      <Position position={row.position} tied={row.tied} />
                    </span>
                  </td>
                  <th scope="row" className={`${cell} min-w-0 ps-2 pe-4 text-start font-normal`}>
                    <span className="flex flex-wrap items-baseline gap-x-2 gap-y-1">
                      <Name row={row} />
                      {row.best && <BestChip />}
                    </span>
                    <span className="mt-1 block type-data text-muted">
                      <Secondary entry={entry} />
                    </span>
                    <MepDisclosure entry={entry} ranking={ranking} />
                  </th>
                  <td className={`${cell} px-4 text-end type-body`}>
                    <span className="type-figure text-figure-row whitespace-nowrap">{formatMoney(entry.earned)}</span>
                    {row.gap !== null && (
                      <span className="mt-1 block type-data whitespace-nowrap text-muted">
                        {formatMoneyWhole(row.gap)} vs. la mejor
                      </span>
                    )}
                    <UpTo entry={entry} amount={ranking.request.amount} />
                  </td>
                  <td className={`${cell} px-4 type-body`}>
                    <RealResult realEarned={entry.realEarned} realReturn={entry.realReturn} indexLabel={indexLabel} />
                  </td>
                  <td className={`${cell} px-4 text-end type-body`}>
                    <span className="type-data">{formatPercent(entry.rateUsed.fraction)}</span>
                    <span className="block type-data whitespace-nowrap text-muted">
                      {rateKindShortLabel(entry.rateUsed.kind)}
                    </span>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>

      <ol
        className="flex flex-col gap-2 md:hidden"
        aria-label={primary ? `Ranking para ${query}` : `Opciones siguientes para ${query}`}
      >
        {rows.map((row) => {
          const { entry } = row;
          return (
            <li key={entry.id} className={row.best ? 'card border-s-2 border-s-accent p-4' : 'card p-4'}>
              <p className="flex items-baseline gap-2">
                <span className="type-index w-8 shrink-0">
                  <Position position={row.position} tied={row.tied} />
                </span>
                <span className="min-w-0 flex-1 break-words">
                  <Name row={row} />
                </span>
                {row.best && <BestChip />}
              </p>
              <p className="mt-3 flex flex-wrap items-baseline gap-x-3 gap-y-1">
                <span className="flex items-baseline gap-2">
                  <span className="type-label">Ganás en {formatDays(days)}</span>
                  <span className="type-figure text-figure-row whitespace-nowrap">{formatMoney(entry.earned)}</span>
                </span>
                {row.gap !== null && (
                  <span className="type-data whitespace-nowrap text-muted">{formatMoneyWhole(row.gap)} vs. la mejor</span>
                )}
              </p>
              <UpTo entry={entry} amount={ranking.request.amount} />
              <p className="mt-2">
                <RealResult realEarned={entry.realEarned} realReturn={entry.realReturn} indexLabel={indexLabel} />
              </p>
              <p className="mt-2 type-data text-muted">
                <Secondary entry={entry} rate />
              </p>
              <MepDisclosure entry={entry} ranking={ranking} />
            </li>
          );
        })}
      </ol>

      {footnote && <RankingFootnote ranking={ranking} />}
    </div>
  );
}

/** What every amount includes and which inflation the real result uses, once under the board (D11). */
export function RankingFootnote({ ranking }: { ranking: Ranking }) {
  const { inflation } = ranking;
  const { days } = ranking.request;
  return (
    <p className="type-body max-w-prose text-muted">
      Los montos ya tienen aplicados los topes, las franjas y, donde corresponde, el interés compuesto diario.{' '}
      {inflation === null
        ? 'Hoy no hay dato de inflación esperada, así que no comparamos contra la inflación.'
        : `La inflación esperada es la del ${inflationIndexLabel(inflation.index)}: ${formatPercent(
            inflation.twelveMonthRate,
          )} a 12 meses y ${formatPercent(inflation.overHorizon)} en ${formatDays(days)}, observada el ${formatDate(
            inflation.observationDate,
          )}.`}{' '}
      <a className="link" href="/metodologia/">
        Cómo se calcula cada número
      </a>
      .
    </p>
  );
}
