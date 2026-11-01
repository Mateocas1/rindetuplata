package ar.rindetuplata.api.instrument;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * One captured rate of an instrument: what it was, on which dates, and what the validation layer found in it.
 * {@code tea} is {@code rate} on the effective annual basis (null without a rate), the value the history chart plots.
 */
public record SnapshotPoint(
		LocalDate captureDate,
		LocalDate dataDate,
		String basis,
		BigDecimal rate,
		BigDecimal clientRate,
		boolean invalid,
		List<ValidationFlagView> flags,
		BigDecimal tea) {
}
