package ar.rindetuplata.api.instrument;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One monthly consumer-price observation of the history window (BCRA variable 27, the INDEC index), labeled with
 * the index it comes from (past inflation for history). {@code annualized} is {@code value} compounded over
 * twelve months, (1 + m)^12 - 1, so it can share an axis with an annual rate.
 */
public record MonthlyInflationPoint(
		String index,
		LocalDate observationDate,
		BigDecimal value,
		String confidence,
		String sourceId,
		BigDecimal annualized) {
}
