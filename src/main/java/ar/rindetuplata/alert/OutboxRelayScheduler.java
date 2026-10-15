package ar.rindetuplata.alert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * T6d: the outbox relay on a timer (S4). Every run tries to publish what the alert evaluation enqueued; a failure
 * is logged and never kills the scheduler, because the row is still there for the next run.
 */
public class OutboxRelayScheduler {

	private static final Logger log = LoggerFactory.getLogger(OutboxRelayScheduler.class);

	private final OutboxRelay relay;

	public OutboxRelayScheduler(OutboxRelay relay) {
		this.relay = relay;
	}

	@Scheduled(fixedDelayString = "${rindetuplata.outbox.relay-interval}")
	public void relay() {
		try {
			int published = relay.relayPending();
			if (published > 0) {
				log.info("outbox relay published {} alert event(s)", published);
			}
		}
		catch (RuntimeException e) {
			log.warn("outbox relay run failed: {}", e.getMessage(), e);
		}
	}

}
