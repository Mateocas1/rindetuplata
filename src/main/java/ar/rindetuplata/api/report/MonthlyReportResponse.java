package ar.rindetuplata.api.report;

import java.time.LocalDate;
import java.util.List;

/** {@code GET /api/v1/reports/monthly/{month}}: the "who beat inflation" report of one closed month. */
public record MonthlyReportResponse(
		String month,
		LocalDate from,
		LocalDate to,
		ReportInflationView inflation,
		List<ReportRowView> rows,
		List<ReportLeftOutView> leftOut,
		ReportTotalsView totals) {
}
