package ar.rindetuplata.alert;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The payload of a {@code RATE_DROP} outbox event (T6c, L32 a): the instrument, the TNA fraction stored by the
 * previous evaluation, the lower one the ranking used today, and the data date of that previous observation.
 */
public record RateDroppedEvent(String type, UUID eventId, long ruleId, String userEmail, String instrumentId,
		String instrumentName, String kind, BigDecimal previousRate, BigDecimal rate, LocalDate previousObservedOn,
		LocalDate captureDate, LocalDate dataDate, String sourceDisplayName) implements AlertEvent {
}
