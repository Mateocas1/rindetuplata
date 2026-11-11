package ar.rindetuplata.api.report;

import ar.rindetuplata.api.ApiException;
import ar.rindetuplata.api.JsonScales;
import ar.rindetuplata.ranking.SourceRegistry;
import ar.rindetuplata.report.LeftOut;
import ar.rindetuplata.report.MonthlyInflation;
import ar.rindetuplata.report.MonthlyReport;
import ar.rindetuplata.report.MonthlyReportService;
import ar.rindetuplata.report.MonthlyReportUnavailableException;
import ar.rindetuplata.report.ReportRow;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.time.YearMonth;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The monthly "who beat inflation" report of the public API: which months have a report and, for one
 * closed month, the nominal and real return each instrument delivered against the INDEC inflation of that month,
 * sorted by real return, with the instruments it could not measure and why. Read-only; every row states its source.
 */
@RestController
@RequestMapping("/api/v1/reports/monthly")
public class ReportController {

	static final String METHOD = "Each instrument's nominal return is what it delivered over the closed month, from "
			+ "the stored history. Wallets and crypto compound daily at the latest known rate of each calendar day (a "
			+ "day whose latest capture is more than 3 days old disqualifies the instrument); a plazo fijo locks the "
			+ "non-client TNA of its first capture of the month, which must be dated on or before the first business "
			+ "day, as one term over the month's days (TNA * days / 365); a fund is the ratio of its last quote on "
			+ "or before the month end to its last quote on or before the previous month end, each at most 4 days "
			+ "before that end, and the classes of one fund are grouped to the best one. The real return is "
			+ "(1 + nominal) / (1 + inflation) - 1 against the INDEC monthly inflation of the same month.";

	private static final Pattern MONTH = Pattern.compile("\\d{4}-(?:0[1-9]|1[0-2])");

	private final MonthlyReportService reports;

	public ReportController(MonthlyReportService reports) {
		this.reports = reports;
	}

	@GetMapping
	@Operation(summary = "Months with a report", description = "The closed months that have a report, newest first. "
			+ "A month is listed only when it has ended, INDEC has published its inflation and the stored history "
			+ "covers it; until then the list is empty. " + METHOD)
	MonthlyReportListResponse months() {
		return new MonthlyReportListResponse(reports.months().stream()
				.map(summary -> new ReportMonthView(summary.month().toString(),
						JsonScales.rate(summary.inflation().value()), summary.ranked(), summary.beatInflation()))
				.toList());
	}

	@GetMapping("/{month}")
	@Operation(summary = "Who beat inflation in a closed month", description = METHOD + " Rows are sorted by real "
			+ "return, best first; instruments that could not be measured are returned apart with their reason.",
			responses = {
					@ApiResponse(responseCode = "200", description = "The report of the month."),
					@ApiResponse(responseCode = "400", description = "The month is not a valid yyyy-MM."),
					@ApiResponse(responseCode = "404", description = "The month is not closed yet, INDEC has not "
							+ "published its inflation, or the stored history does not cover it.") })
	MonthlyReportResponse month(
			@Parameter(description = "The month as yyyy-MM, for example 2026-10.") @PathVariable String month) {
		if (!MONTH.matcher(month).matches()) {
			throw ApiException.badRequest("month", "Parameter 'month' must be a month as yyyy-MM: '" + month + "'");
		}
		try {
			return present(reports.report(YearMonth.parse(month)));
		}
		catch (MonthlyReportUnavailableException e) {
			throw ApiException.notFound("month", e.getMessage());
		}
	}

	private static MonthlyReportResponse present(MonthlyReport report) {
		MonthlyInflation inflation = report.inflation();
		List<ReportRowView> rows = report.rows().stream().map(ReportController::row).toList();
		List<ReportLeftOutView> leftOut = report.leftOut().stream().map(ReportController::leftOut).toList();
		return new MonthlyReportResponse(report.month().toString(), report.month().atDay(1),
				report.month().atEndOfMonth(),
				new ReportInflationView(INDEC_CPI_MONTHLY, JsonScales.rate(inflation.value()),
						inflation.observationDate(), inflation.confidence(), SourceRegistry.of(inflation.sourceId())),
				rows, leftOut, new ReportTotalsView(rows.size(), report.beatInflation(), leftOut.size()));
	}

	private static final String INDEC_CPI_MONTHLY = "INDEC_CPI_MONTHLY";

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
