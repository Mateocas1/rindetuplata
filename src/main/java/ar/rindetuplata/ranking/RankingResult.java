package ar.rindetuplata.ranking;

import java.util.List;
import java.util.Objects;

/**
 * The full personalized ranking (S15): the request echo, the inflation it measured real return against,
 * the ranked page, the ineligible instruments with their reason, the excluded ones with theirs, and the
 * totals before the limit.
 */
public record RankingResult(RankingQuery query, InflationView inflation, List<RankedEntry> ranked,
		List<IneligibleEntry> ineligible, List<ExcludedEntry> excluded, RankingTotals totals) {

	public RankingResult {
		Objects.requireNonNull(query, "query");
		ranked = List.copyOf(ranked);
		ineligible = List.copyOf(ineligible);
		excluded = List.copyOf(excluded);
		Objects.requireNonNull(totals, "totals");
	}
}
