package ar.rindetuplata.api.instrument;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * One instrument as {@code GET /api/v1/instruments} lists it: its identity plus the latest snapshot of its rate
 * (or, for a fund class, the latest quote). {@code id} is the instrument id as a string; {@code sourceId} names the
 * source of the datum and {@code dataDate} the date it is about (S11 states the source and the data date per row),
 * while {@code captureDate} is the day it was captured. {@code rate} and {@code clientRate} are fractions;
 * {@code vcp} is an ARS amount.
 */
public record InstrumentSummary(
		String id,
		String kind,
		String displayName,
		String entityName,
		String segment,
		String sourceId,
		BigDecimal rate,
		String basis,
		BigDecimal clientRate,
		LocalDate captureDate,
		LocalDate dataDate,
		String confidence,
		LocalDate verifiedAt,
		boolean invalid,
		List<ValidationFlagView> flags,
		LocalDate quoteDate,
		BigDecimal vcp) {
}
