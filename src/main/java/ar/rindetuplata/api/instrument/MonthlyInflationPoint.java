package ar.rindetuplata.api.instrument;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One monthly consumer-price observation of the history window (BCRA variable 27, the INDEC index), labeled with
 * the index it comes from (S10: past inflation for history).
 */
public record MonthlyInflationPoint(
		String index,
		LocalDate observationDate,
		BigDecimal value,
		String confidence,
		String sourceId) {
}
