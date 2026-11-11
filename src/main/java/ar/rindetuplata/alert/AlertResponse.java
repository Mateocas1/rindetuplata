package ar.rindetuplata.alert;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/**
 * One alert rule as the API shows it: its type, and the threshold presented as the percent the user wrote, not
 * as the stored fraction, with the same 2-decimal rounding the rest of the API uses for presented money. Only a
 * THRESHOLD rule has a threshold; the others show null.
 */
public record AlertResponse(long id, String type, String kind, Long instrumentId, BigDecimal thresholdPercent,
		boolean active, Instant createdAt) {

	private static final BigDecimal HUNDRED = new BigDecimal("100");
	private static final int PERCENT_SCALE = 2;

	public static AlertResponse of(AlertRule rule) {
		BigDecimal threshold = rule.thresholdRate() == null ? null
				: rule.thresholdRate().multiply(HUNDRED).setScale(PERCENT_SCALE, RoundingMode.HALF_EVEN);
		return new AlertResponse(rule.id(), rule.type().name(), rule.kind() == null ? null : rule.kind().name(),
				rule.instrumentId(), threshold, rule.active(), rule.createdAt());
	}

}
