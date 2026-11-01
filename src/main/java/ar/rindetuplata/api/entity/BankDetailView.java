package ar.rindetuplata.api.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * {@code GET /api/v1/entities/banks/{slug}}: the bank's latest rates, its non-client TNA series over the window and
 * how often it paid the highest non-client TNA among banks. The window is the 90 calendar days ending today in
 * America/Argentina/Buenos_Aires, inclusive ({@code from}..{@code to}); only valid snapshots count.
 */
@Schema(description = "A bank's latest plazo fijo rates, its 90-day non-client TNA series and bestDays/observedDays.")
public record BankDetailView(
		String slug,
		String name,
		@Schema(description = "Id of the bank's instrument, usable with /api/v1/instruments/{id}/history.")
		String instrumentId,
		String sourceId,
		@Schema(description = "Non-client TNA as a fraction, scale 6; null when the bank offers none.")
		BigDecimal nonClientTna,
		@Schema(description = "Client TNA as a fraction, scale 6; null when the bank publishes none.")
		BigDecimal clientTna,
		@Schema(description = "The day the latest rates were captured.") LocalDate captureDate,
		@Schema(description = "The date the source says the latest rates are about.") LocalDate dataDate,
		@Schema(description = "First day of the window.") LocalDate from,
		@Schema(description = "Last day of the window (today).") LocalDate to,
		@Schema(description = "Non-client TNA per capture day inside the window, oldest first.")
		List<BankSeriesPoint> series,
		@Schema(description = "Capture days in the window on which this bank's non-client TNA was the highest "
				+ "among banks; a tie counts for every bank tied.")
		int bestDays,
		@Schema(description = "Capture days in the window on which this bank had a valid non-client TNA: the "
				+ "denominator of bestDays.")
		int observedDays) {
}
