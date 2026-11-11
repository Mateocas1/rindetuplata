package ar.rindetuplata.alert;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The payload of one {@code AlertTriggered} outbox event. Everything the email needs to be sent and
 * understood without another query: who to notify, which rule and instrument fired, the rate and the threshold it
 * crossed, the dates of the data behind it and the source it came from. It has no type field: it is the shape every
 * outbox row had before the alert types, and a payload without one is read as this event.
 */
public record AlertTriggered(UUID eventId, long ruleId, String userEmail, String instrumentId, String instrumentName,
		String kind, BigDecimal rate, BigDecimal threshold, LocalDate captureDate, LocalDate dataDate,
		String sourceDisplayName) implements AlertEvent {
}
