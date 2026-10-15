package ar.rindetuplata.alert;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The payload of one {@code AlertTriggered} outbox event (T6c, S4). Everything the T6d email needs to be sent and
 * understood without another query: who to notify, which rule and instrument fired, the rate and the threshold it
 * crossed, the dates of the data behind it and the source it came from.
 */
public record AlertTriggered(UUID eventId, long ruleId, String userEmail, String instrumentId, String instrumentName,
		String kind, BigDecimal rate, BigDecimal threshold, LocalDate captureDate, LocalDate dataDate,
		String sourceDisplayName) {
}
