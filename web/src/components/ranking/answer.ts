/**
 * The home answer: the H1 asks "¿Dónde rinde tu plata hoy?" and this one sentence answers it
 * for the ranking on screen, so it changes with the reader's amount and term.
 *
 * "Hoy, con $ 1.000.000 a 30 días, lo que más rinde es Reba: ganás $ 19.726,03, $ 3.806 por encima de la
 * inflación esperada."
 *
 * Ties at the top are named as ties: two options by name, more as "X y N opciones más". The real result
 * comes in whole pesos from the API's `realEarned`, falls back to the percentage for a response without it,
 * and is left out without an inflation observation. Presentation only: every amount comes from the API.
 */
import { displayName } from '../../lib/displayName';
import { formatDays, formatInteger, formatMoney, formatMoneyWhole, formatPercent } from '../../lib/format';
import type { RankedEntry, Ranking } from '../../lib/types';
import { formatQueryAmount, rankPositions } from './positions';

/** Who is first: one name, a two-way tie, or the first name and how many more share the place. */
function leaders(ranked: RankedEntry[]): string {
  const ranks = rankPositions(ranked);
  const tied = ranked.filter((_, index) => ranks[index].position === 1).map((entry) => displayName(entry.name));
  if (tied.length === 1) return `lo que más rinde es ${tied[0]}`;
  if (tied.length === 2) return `empatan ${tied[0]} y ${tied[1]}`;
  return `empatan ${tied[0]} y ${formatInteger(tied.length - 1)} opciones más`;
}

/** ", $ 3.806 por encima de la inflación esperada", or nothing without an inflation observation. */
function realClause(best: RankedEntry): string {
  if (best.realReturn === null) return '';
  const pesos = best.realEarned ?? null;
  const direction = pesos ?? best.realReturn;
  const amount = pesos === null ? formatPercent(Math.abs(best.realReturn)) : formatMoneyWhole(Math.abs(pesos));
  if (direction === 0 || amount === formatMoneyWhole(0)) return ', lo mismo que la inflación esperada';
  const relation = direction > 0 ? 'por encima de' : 'por debajo de';
  return `, ${amount} ${relation} la inflación esperada`;
}

/** The one sentence under the home H1, for the ranking it is given. */
export function answerSentence(ranking: Ranking): string {
  const { amount, days } = ranking.request;
  const query = `${formatQueryAmount(amount)} a ${formatDays(days)}`;
  const [best] = ranking.ranked;
  if (best === undefined) return `Hoy no encontramos opciones para ${query}.`;
  return `Hoy, con ${query}, ${leaders(ranking.ranked)}: ganás ${formatMoney(best.earned)}${realClause(best)}.`;
}
