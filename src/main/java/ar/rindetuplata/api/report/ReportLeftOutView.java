package ar.rindetuplata.api.report;

import ar.rindetuplata.ranking.SourceView;
import io.swagger.v3.oas.annotations.media.Schema;

/** An instrument the month's report could not measure, with the reason (never dropped silently). */
public record ReportLeftOutView(
		String id,
		String kind,
		String name,
		String entity,
		SourceView source,
		@Schema(description = "NOT_CAPTURED_AT_START, GAP_IN_CAPTURES, MISSING_BASE_QUOTE, MISSING_END_QUOTE, "
				+ "NOT_OFFERED_TO_NON_CLIENTS, INVALID_DATA, FOREIGN_CURRENCY, RESTRICTED_CLASS or "
				+ "FUND_CLASS_NOT_FOUND.")
		String reason) {
}
