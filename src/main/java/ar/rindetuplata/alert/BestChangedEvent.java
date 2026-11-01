package ar.rindetuplata.alert;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The payload of a {@code BEST_CHANGED} outbox event (T6c, L32 a): the new top of the rule's filter and the top it
 * replaced. {@code ruleKind} is the rule's kind filter (null for every kind), {@code kind} the new top's own kind;
 * {@code previousObservedOn} is the data date of the last evaluation that still saw the previous top. {@code type}
 * is the discriminator the consumer reads ({@code BEST_CHANGED}); events stored before the alert types have none.
 */
public record BestChangedEvent(String type, UUID eventId, long ruleId, String userEmail, String ruleKind,
		String instrumentId, String instrumentName, String kind, BigDecimal rate, BigDecimal realReturn,
		LocalDate captureDate, LocalDate dataDate, String sourceDisplayName, String previousName,
		LocalDate previousObservedOn) implements AlertEvent {
}
