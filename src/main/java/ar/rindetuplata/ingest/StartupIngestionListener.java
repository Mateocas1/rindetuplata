package ar.rindetuplata.ingest;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

/**
 * T14a (S20): the daily cron alone would leave a stack that boots fresh (the private preview) with an empty
 * ranking until 19:30, so with {@code rindetuplata.ingest.run-on-startup=true} the application runs today's LIVE
 * ingestion once, as soon as it is ready. The run goes to a background thread: HTTP and health are never delayed.
 * Repeating the day is the ingestion's own idempotency (a COMPLETED instance is a no-op), and the run it is handed
 * logs its own failure, so an ingestion problem never stops the application.
 */
class StartupIngestionListener {

	private final Runnable ingestion;

	StartupIngestionListener(Runnable ingestion) {
		this.ingestion = ingestion;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void onApplicationReady() {
		Thread.ofVirtual().name("startup-ingestion").start(this.ingestion);
	}
}
