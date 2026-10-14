package ar.rindetuplata.api.instrument;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** One captured rate of an instrument: what it was, on which dates, and what the validation layer found in it. */
public record SnapshotPoint(
		LocalDate captureDate,
		LocalDate dataDate,
		String basis,
		BigDecimal rate,
		BigDecimal clientRate,
		boolean invalid,
		List<ValidationFlagView> flags) {
}
