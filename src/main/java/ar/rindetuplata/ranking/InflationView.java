package ar.rindetuplata.ranking;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * The inflation the ranking's real return is measured against (S10): the BCRA REM expected 12-month inflation,
 * compounded over the requested horizon and never divided linearly.
 */
public record InflationView(String index, BigDecimal twelveMonthRate, LocalDate observationDate,
		BigDecimal overHorizon) {

	/** The only index today's ranking and the simulator use (S10). */
	public static final String REM_12M_EXPECTED = "REM_12M_EXPECTED";

	public InflationView {
		Objects.requireNonNull(index, "index");
		Objects.requireNonNull(twelveMonthRate, "twelveMonthRate");
		Objects.requireNonNull(observationDate, "observationDate");
		Objects.requireNonNull(overHorizon, "overHorizon");
	}
}
