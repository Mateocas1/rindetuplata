package ar.rindetuplata.api.ranking;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The inflation the real returns were measured against (S10): the REM 12-month expected rate, its observation
 * date and the rate compounded over the requested horizon, never divided linearly. Both rates are fractions.
 */
public record InflationResponse(String index, BigDecimal twelveMonthRate, LocalDate observationDate,
		BigDecimal overHorizon) {
}
