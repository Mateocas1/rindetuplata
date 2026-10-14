package ar.rindetuplata.alert;

/**
 * The T6d event contract (S4, S13): the names the topology, the relay and the consumer share, so no part of the
 * delivery chain can drift from the others by a typo.
 */
public final class AlertEvents {

	/** The topic exchange every alert event is published to. */
	public static final String EXCHANGE = "rindetuplata.events";

	/** The routing key of one {@code AlertTriggered} event. */
	public static final String ROUTING_KEY = "alert.triggered";

	/** The durable queue a failed alert email is dead-lettered from and the consumer reads. */
	public static final String QUEUE = "rindetuplata.alert-email";

	/** The dead-letter exchange the email queue points at. */
	public static final String DEAD_LETTER_EXCHANGE = "rindetuplata.events.dlx";

	/** The routing key the dead-letter exchange routes the rejected message with. */
	public static final String DEAD_LETTER_ROUTING_KEY = "alert.triggered.dlq";

	/** Where a message the consumer finally rejected ends: the operator's queue to inspect. */
	public static final String DEAD_LETTER_QUEUE = "rindetuplata.alert-email.dlq";

	private AlertEvents() {
	}

}
