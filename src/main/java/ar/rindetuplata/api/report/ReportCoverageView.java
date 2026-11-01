package ar.rindetuplata.api.report;

import io.swagger.v3.oas.annotations.media.Schema;

/** How much of the month a row's measurement covers. */
@Schema(description = "The days of the month the nominal return was measured over, out of the days of the month. "
		+ "Wallets, crypto and plazo fijo cover the whole month by construction; a fund covers the days from the "
		+ "previous month end to its last quote (at least the month minus 4 days).")
public record ReportCoverageView(int days, int total) {
}
