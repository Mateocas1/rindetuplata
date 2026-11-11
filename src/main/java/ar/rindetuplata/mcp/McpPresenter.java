package ar.rindetuplata.mcp;

import ar.rindetuplata.api.JsonScales;
import ar.rindetuplata.api.instrument.ValidationFlagView;
import ar.rindetuplata.api.ranking.BreakEvenMepView;
import ar.rindetuplata.api.ranking.InflationResponse;
import ar.rindetuplata.api.ranking.MepQuoteView;
import ar.rindetuplata.api.ranking.RankedEntryView;
import ar.rindetuplata.api.ranking.RankingRequestView;
import ar.rindetuplata.api.ranking.RateUsedView;
import ar.rindetuplata.api.ranking.SimulationResponse;
import ar.rindetuplata.api.ranking.TotalsView;
import ar.rindetuplata.api.ranking.UpToView;
import ar.rindetuplata.api.report.MonthlyReportResponse;
import ar.rindetuplata.api.report.ReportCoverageView;
import ar.rindetuplata.api.report.ReportInflationView;
import ar.rindetuplata.api.report.ReportLeftOutView;
import ar.rindetuplata.api.report.ReportRowView;
import ar.rindetuplata.api.report.ReportTotalsView;
import ar.rindetuplata.domain.MepBreakEven;
import ar.rindetuplata.ranking.InflationView;
import ar.rindetuplata.ranking.MepQuote;
import ar.rindetuplata.ranking.RankedEntry;
import ar.rindetuplata.ranking.RankingResult;
import ar.rindetuplata.ranking.SimulationResult;
import ar.rindetuplata.ranking.SourceRegistry;
import ar.rindetuplata.ranking.SourceView;
import ar.rindetuplata.report.LeftOut;
import ar.rindetuplata.report.MonthlyInflation;
import ar.rindetuplata.report.MonthlyReport;
import ar.rindetuplata.report.ReportRow;
import ar.rindetuplata.source.ValidationFlag;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns the domain answers into the REST view records, at the same scales ({@link JsonScales}: money 2, fractions 6,
 * {@code HALF_EVEN}). {@code RankingApiPresenter} and the report mapping of {@code ReportController} do the same
 * but are package-private to the REST controllers, so this is a mirror; the equivalence test compares the rows of
 * both, field by field, so they cannot drift.
 */
final class McpPresenter {

	private static final String INDEC_CPI_MONTHLY = "INDEC_CPI_MONTHLY";

	private McpPresenter() {
	}

	static ToolResults.Ranking ranking(RankingResult result) {
		List<RankedEntryView> ranked = result.ranked().stream().map(McpPresenter::entry).toList();
		LocalDate dataDate = result.ranked().stream().map(RankedEntry::dataDate).max(Comparator.naturalOrder())
				.orElse(null);
		Map<String, SourceView> sources = new LinkedHashMap<>();
		result.ranked().forEach(entry -> sources.putIfAbsent(entry.source().id(), entry.source()));
		List<String> clientOf = result.query().clientOf().stream().map(String::valueOf).sorted().toList();
		List<String> kinds = result.query().kinds().stream().map(Enum::name).sorted().toList();
		return new ToolResults.Ranking(result.query().asOf(), dataDate, List.copyOf(sources.values()),
				new RankingRequestView(JsonScales.money(result.query().amount().presented()), result.query().days(),
						clientOf, kinds, result.query().asOf(), result.query().limit()),
				result.inflation() == null ? null : inflation(result.inflation()), mepQuote(result.dollarMep()),
				ranked, new TotalsView(result.totals().ranked(), result.totals().ineligible(),
						result.totals().excluded()));
	}

	static ToolResults.Simulation simulation(SimulationResult result, LocalDate asOf) {
		return switch (result) {
			case SimulationResult.Simulated simulated -> new ToolResults.Simulation(asOf,
					simulated.entry().dataDate(), simulated.entry().source(), SimulationResponse.SIMULATED,
					mepQuote(simulated.dollarMep()), entry(simulated.entry()), null, null);
			case SimulationResult.IneligibleInstrument ineligible -> new ToolResults.Simulation(asOf, null, null,
					SimulationResponse.INELIGIBLE, null, null,
					new SimulationResponse.IneligibleView(ineligible.id(), ineligible.name(),
							ineligible.ineligible().reason().name(), ineligible.ineligible().detail()), null);
			case SimulationResult.ExcludedInstrument excluded -> new ToolResults.Simulation(asOf, null, null,
					SimulationResponse.EXCLUDED, null, null, null,
					new SimulationResponse.ExcludedView(excluded.id(), excluded.name(), excluded.reason().name(),
							flags(excluded.flags())));
		};
	}

	static ToolResults.Report report(MonthlyReport report) {
		MonthlyInflation inflation = report.inflation();
		List<ReportRowView> rows = report.rows().stream().map(McpPresenter::row).toList();
		List<ReportLeftOutView> leftOut = report.leftOut().stream().map(McpPresenter::leftOut).toList();
		SourceView source = SourceRegistry.of(inflation.sourceId());
		return new ToolResults.Report(inflation.observationDate(), source, new MonthlyReportResponse(
				report.month().toString(), report.month().atDay(1), report.month().atEndOfMonth(),
				new ReportInflationView(INDEC_CPI_MONTHLY, JsonScales.rate(inflation.value()),
						inflation.observationDate(), inflation.confidence(), source),
				rows, leftOut, new ReportTotalsView(rows.size(), report.beatInflation(), leftOut.size())));
	}

	private static InflationResponse inflation(InflationView inflation) {
		return new InflationResponse(inflation.index(), JsonScales.rate(inflation.twelveMonthRate()),
				inflation.observationDate(), JsonScales.rate(inflation.overHorizon()));
	}

	private static MepQuoteView mepQuote(MepQuote quote) {
		return quote == null ? null
				: new MepQuoteView(JsonScales.money(quote.buy()), JsonScales.money(quote.sell()), quote.source(),
						quote.quoteDate(), quote.updatedAt());
	}

	private static BreakEvenMepView breakEvenMep(MepBreakEven breakEven) {
		return breakEven == null ? null
				: new BreakEvenMepView(JsonScales.rate(breakEven.devaluation()), JsonScales.money(breakEven.price()));
	}

	static RankedEntryView entry(RankedEntry entry) {
		return new RankedEntryView(entry.id(), entry.kind().name(), entry.name(), entry.entity(), entry.source(),
				entry.confidence().name(), entry.verifiedAt(), entry.captureDate(), entry.dataDate(),
				new RateUsedView(JsonScales.rate(entry.rateUsed().fraction()), entry.rateUsed().basis(),
						entry.rateUsed().kind().name()),
				JsonScales.money(entry.finalAmount().presented()), JsonScales.money(entry.earned().presented()),
				JsonScales.rate(entry.nominalReturn()), JsonScales.rate(entry.realReturn()),
				JsonScales.money(entry.realEarned()),
				entry.upTo() == null ? null
						: new UpToView(entry.upTo().condition(),
								JsonScales.money(entry.upTo().finalAmount().presented())),
				flags(entry.warnings()), entry.foldedClasses(), breakEvenMep(entry.breakEvenMep()));
	}

	private static List<ValidationFlagView> flags(List<ValidationFlag> flags) {
		if (flags == null) {
			return List.of();
		}
		return flags.stream()
				.sorted(Comparator.comparing((ValidationFlag flag) -> flag.severity().name())
						.thenComparing(flag -> flag.code().name()))
				.map(flag -> new ValidationFlagView(flag.code().name(), flag.severity().name(), flag.message()))
				.toList();
	}

	private static ReportRowView row(ReportRow row) {
		return new ReportRowView(row.id(), row.kind().name(), row.name(), row.entity(),
				SourceRegistry.of(row.sourceId()), JsonScales.rate(row.realized().nominal()),
				JsonScales.rate(row.realized().real()), row.realized().beatInflation(), row.foldedClasses(),
				new ReportCoverageView(row.coveredDays(), row.totalDays()));
	}

	private static ReportLeftOutView leftOut(LeftOut left) {
		return new ReportLeftOutView(left.id(), left.kind().name(), left.name(), left.entity(),
				SourceRegistry.of(left.sourceId()), left.reason().name());
	}
}
