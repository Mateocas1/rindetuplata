package ar.rindetuplata.api.ranking;

import java.util.List;

/**
 * The full ranking response (S15): the request echo, the inflation real returns were measured against (S10, null
 * without a REM observation), the dólar MEP quote the break-evens used (S5, null without a stored quote), the
 * ranked page, the ineligible and excluded instruments with their reasons, and the
 * totals before the limit.
 */
public record RankingResponse(RankingRequestView request, InflationResponse inflation, MepQuoteView dollarMep,
		List<RankedEntryView> ranked,
		List<IneligibleEntryView> ineligible, List<ExcludedEntryView> excluded, TotalsView totals) {
}
