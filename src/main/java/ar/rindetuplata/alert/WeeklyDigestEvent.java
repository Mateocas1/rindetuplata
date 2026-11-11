package ar.rindetuplata.alert;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The payload of a {@code WEEKLY_DIGEST} outbox event: the top entries of the rule's filter in ranking
 * order, the Monday of the ISO week it covers and, when the top differs from the previous digest's, what it was.
 * {@code realReturn} is null when no inflation expectation is stored.
 */
public record WeeklyDigestEvent(String type, UUID eventId, long ruleId, String userEmail, String ruleKind,
		LocalDate weekStart, LocalDate dataDate, List<Entry> entries, TopChange topChangedFrom)
		implements AlertEvent {

	/** One line of the digest. */
	public record Entry(String instrumentId, String name, String kind, BigDecimal rate, BigDecimal realReturn,
			String sourceDisplayName, LocalDate captureDate, LocalDate dataDate) {
	}

	/** The previous digest's top, present only when it differs from this digest's top. */
	public record TopChange(String name, LocalDate weekStart) {
	}

}
