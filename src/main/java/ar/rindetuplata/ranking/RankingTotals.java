package ar.rindetuplata.ranking;

/**
 * How many instruments fell in each group before the limit was applied. {@code ranked} is the full ranked count,
 * so a caller can paginate without losing the size of the result.
 */
public record RankingTotals(int ranked, int ineligible, int excluded) {
}
