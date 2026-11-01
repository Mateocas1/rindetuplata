package ar.rindetuplata.api.report;

import java.util.List;

/** {@code GET /api/v1/reports/monthly}: the months that have a report, newest first. */
public record MonthlyReportListResponse(List<ReportMonthView> months) {
}
