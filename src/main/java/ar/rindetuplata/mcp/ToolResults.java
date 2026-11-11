package ar.rindetuplata.mcp;

import ar.rindetuplata.api.instrument.InstrumentHistory;
import ar.rindetuplata.api.ranking.InflationResponse;
import ar.rindetuplata.api.ranking.MepQuoteView;
import ar.rindetuplata.api.ranking.RankedEntryView;
import ar.rindetuplata.api.ranking.RankingRequestView;
import ar.rindetuplata.api.ranking.SimulationResponse.ExcludedView;
import ar.rindetuplata.api.ranking.SimulationResponse.IneligibleView;
import ar.rindetuplata.api.ranking.TotalsView;
import ar.rindetuplata.api.report.MonthlyReportResponse;
import ar.rindetuplata.api.report.ReportMonthView;
import ar.rindetuplata.ranking.SourceView;
import java.time.LocalDate;
import java.util.List;

/**
 * What each tool answers. The rows are the very view records of the REST API, so a model reads the
 * same fields a client of {@code /api/v1} does; each result adds the {@code dataDate} of the newest datum it
 * rests on and its {@code source} (the sources, for a ranking), which are {@code null} only when the answer rests on
 * no datum (an empty history, no closed month, an instrument the simulation could not use).
 */
final class ToolResults {

	private ToolResults() {
	}

	/**
	 * The ranking page. {@code asOf} is the reference date of the request; {@code dataDate} the newest data date of
	 * the ranked rows. The ineligible and excluded instruments of the REST answer are counted in {@code totals} and not
	 * listed: {@code simulate_instrument} tells why one instrument is left out.
	 */
	record Ranking(LocalDate asOf, LocalDate dataDate, List<SourceView> sources, RankingRequestView request,
			InflationResponse inflation, MepQuoteView dollarMep, List<RankedEntryView> ranked, TotalsView totals) {
	}

	/** One simulated instrument; exactly one of {@code entry}, {@code ineligible} or {@code excluded} is set. */
	record Simulation(LocalDate asOf, LocalDate dataDate, SourceView source, String status, MepQuoteView dollarMep,
			RankedEntryView entry, IneligibleView ineligible, ExcludedView excluded) {
	}

	record History(LocalDate dataDate, SourceView source, InstrumentHistory history) {
	}

	record ReportList(LocalDate dataDate, SourceView source, List<ReportMonthView> months) {
	}

	record Report(LocalDate dataDate, SourceView source, MonthlyReportResponse report) {
	}
}
