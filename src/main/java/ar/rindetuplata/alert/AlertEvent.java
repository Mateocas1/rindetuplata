package ar.rindetuplata.alert;

import java.util.UUID;

/**
 * The payload of one alert outbox event: the original {@link AlertTriggered} and the three events of the
 * new rule types. The event id is the consumer's dedupe key and the user email the recipient; everything else is what
 * that type's email needs without another query.
 */
public sealed interface AlertEvent permits AlertTriggered, BestChangedEvent, RateDroppedEvent, WeeklyDigestEvent {

	UUID eventId();

	String userEmail();

}
