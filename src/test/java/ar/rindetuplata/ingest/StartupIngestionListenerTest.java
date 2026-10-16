package ar.rindetuplata.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/**
 * T14a (S20): the ready event hands today's ingestion to a background thread, so the HTTP endpoints and health are
 * never delayed while it runs.
 */
class StartupIngestionListenerTest {

	@Test
	void runsTheIngestionOnABackgroundThreadSoStartupIsNotDelayed() throws InterruptedException {
		CountDownLatch started = new CountDownLatch(1);
		CountDownLatch released = new CountDownLatch(1);
		AtomicBoolean finished = new AtomicBoolean();
		StartupIngestionListener listener = new StartupIngestionListener(() -> {
			started.countDown();
			await(released);
			finished.set(true);
		});

		listener.onApplicationReady();

		assertThat(finished).as("the ready event returned before the ingestion finished").isFalse();
		assertThat(started.await(10, TimeUnit.SECONDS)).as("the ingestion was started").isTrue();
		released.countDown();
	}

	private static void await(CountDownLatch latch) {
		try {
			latch.await();
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		}
	}
}
