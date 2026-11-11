package ar.rindetuplata.api.report;

import ar.rindetuplata.ranking.SourceView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;

/** The INDEC monthly inflation the real returns of the report were measured against, with its source. */
public record ReportInflationView(
		String index,
		@Schema(description = "The month's inflation as a fraction (1.9% is 0.019), scale 6.") BigDecimal value,
		@Schema(description = "The date the observation is stored under: the last day of the month it measures.")
		LocalDate observationDate,
		String confidence,
		SourceView source) {
}
