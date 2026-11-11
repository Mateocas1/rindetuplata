package ar.rindetuplata.report;

import java.time.YearMonth;
import java.util.List;

/** The "who beat inflation" report of one closed month. Rows are sorted by real return, best first. */
public record MonthlyReport(YearMonth month, MonthlyInflation inflation, List<ReportRow> rows, List<LeftOut> leftOut) {

	public MonthlyReport {
		rows = List.copyOf(rows);
		leftOut = List.copyOf(leftOut);
	}

	public int beatInflation() {
		return (int) rows.stream().filter(row -> row.realized().beatInflation()).count();
	}
}
