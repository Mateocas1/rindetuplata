package ar.rindetuplata.api;

import ar.rindetuplata.domain.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The JSON presentation rule of the public API: money at scale 2 and fractions (rates, returns) at scale 6, both
 * {@code HALF_EVEN}. Domain math keeps its own precision; rounding happens here, at the edge.
 */
public final class JsonScales {

	public static final int MONEY_SCALE = Money.PRESENTED_SCALE;
	public static final int RATE_SCALE = 6;
	public static final RoundingMode ROUNDING = Money.PRESENTED_ROUNDING;

	private JsonScales() {
	}

	/** An ARS amount rounded to centavos, or {@code null}. */
	public static BigDecimal money(BigDecimal value) {
		return value == null ? null : value.setScale(MONEY_SCALE, ROUNDING);
	}

	/** A rate or return given as a fraction, rounded to 6 decimals, or {@code null}. */
	public static BigDecimal rate(BigDecimal value) {
		return value == null ? null : value.setScale(RATE_SCALE, ROUNDING);
	}
}
