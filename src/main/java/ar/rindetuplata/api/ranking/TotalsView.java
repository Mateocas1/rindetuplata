package ar.rindetuplata.api.ranking;

/** How many instruments fell in each group before the limit was applied, so a caller can paginate (S15). */
public record TotalsView(int ranked, int ineligible, int excluded) {
}
