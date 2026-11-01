package ar.rindetuplata.api.report;

import ar.rindetuplata.ranking.SourceView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/**
 * One instrument measured over the month (T6a, S11). The returns are fractions at scale 6, half-even.
 * {@code foldedClasses} is how many sibling classes of the same fund were folded into this row; 0 for every other row.
 */
public record ReportRowView(
		String id,
		@Schema(description = "PLAZO_FIJO, WALLET, FUND or CRYPTO.") String kind,
		String name,
		String entity,
		SourceView source,
		@Schema(description = "The return the instrument delivered over the month, as a fraction (2% is 0.02).")
		BigDecimal nominal,
		@Schema(description = "(1 + nominal) / (1 + inflation) - 1 against the INDEC rate of the month.")
		BigDecimal real,
		@Schema(description = "True when the real return, as published, is above zero.") boolean beatInflation,
		int foldedClasses,
		ReportCoverageView coverage) {
}
