package ar.rindetuplata.api.report;

/** The counts of a month's report: rows measured, rows that beat inflation, instruments left out. */
public record ReportTotalsView(int ranked, int beatInflation, int leftOut) {
}
