package ar.rindetuplata.alert;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/**
 * One alert rule as the API shows it (T6c): the threshold is presented as the percent the user wrote, not as the
 * stored fraction, with the same 2-decimal rounding the rest of the API uses for presented money.
 */
public record AlertResponse(long id, String kind, Long instrumentId, BigDecimal thresholdPercent, boolean active,
		Instant createdAt) {

	private static final BigDecimal HUNDRED = new BigDecimal("100");
	private static final int PERCENT_SCALE = 2;

	public static AlertResponse of(AlertRule rule) {
		return new AlertResponse(rule.id(), rule.kind() == null ? null : rule.kind().name(), rule.instrumentId(),
				rule.thresholdRate().multiply(HUNDRED).setScale(PERCENT_SCALE, RoundingMode.HALF_EVEN), rule.active(),
				rule.createdAt());
	}

}
