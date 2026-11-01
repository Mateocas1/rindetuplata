package ar.rindetuplata.api.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One bank in {@code GET /api/v1/entities/banks}: its slug, its instrument id and the latest valid plazo fijo
 * snapshot at or before today. Rates are TNA fractions at scale 6; {@code nonClientTna} is null when the bank offers
 * no plazo fijo to non-clients and {@code clientTna} when it publishes no client rate (S11 states the dates).
 */
@Schema(description = "A bank with the latest valid plazo fijo rates stored at or before today.")
public record BankRatesView(
		@Schema(description = "URL-safe identifier derived from the bank name: lowercase, accents stripped, "
				+ "non-alphanumerics as dashes. Stable; a name collision takes a numeric suffix in instrument id order.")
		String slug,
		String name,
		@Schema(description = "Id of the bank's instrument, usable with /api/v1/instruments/{id}/history.")
		String instrumentId,
		@Schema(description = "Source of the rates.") String sourceId,
		@Schema(description = "Non-client TNA as a fraction, scale 6; null when the bank offers none.")
		BigDecimal nonClientTna,
		@Schema(description = "Client TNA as a fraction, scale 6; null when the bank publishes none.")
		BigDecimal clientTna,
		@Schema(description = "The day the rates were captured.") LocalDate captureDate,
		@Schema(description = "The date the source says the rates are about.") LocalDate dataDate) {
}
