package ar.rindetuplata.alert;

import ar.rindetuplata.ranking.RankedEntry;
import ar.rindetuplata.ranking.RankedKind;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * One row of {@code alert_rule} (T6c, S3): the threshold in TNA fraction, the optional kind and instrument that
 * narrow what can match, and the owner. {@code kind} null means "any kind"; {@code instrumentId} null means "any
 * instrument of that kind". {@code email} is the owner's normalized address, read with the row so the outbox payload
 * of a match needs no second query.
 */
public record AlertRule(long id, long userId, String email, RankedKind kind, Long instrumentId,
		BigDecimal thresholdRate, boolean active, Instant createdAt) {

	/** Whether this rule can match that ranked entry: the kind filter and then the instrument filter. */
	public boolean matches(RankedEntry entry) {
		if (kind != null && entry.kind() != kind) {
			return false;
		}
		return instrumentId == null || String.valueOf(instrumentId).equals(entry.id());
	}

}
