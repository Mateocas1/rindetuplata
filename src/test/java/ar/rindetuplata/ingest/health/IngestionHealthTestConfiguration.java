package ar.rindetuplata.ingest.health;

import ar.rindetuplata.TestContainers;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Testcontainers and a clock the health tests move by hand, so the 36-hour deadline is assertable to the second. */
@TestConfiguration(proxyBeanMethods = false)
public class IngestionHealthTestConfiguration {

	public static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return TestContainers.postgres();
	}

	@Bean
	@Primary
	MovableClock healthClock() {
		return new MovableClock();
	}

	/** A {@link Clock} whose instant the test sets. */
	public static final class MovableClock extends Clock {

		private volatile Instant instant = Instant.parse("2026-10-20T12:00:00Z");

		public void set(Instant instant) {
			this.instant = instant;
		}

		@Override
		public ZoneId getZone() {
			return ZONE;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return Clock.fixed(instant, zone);
		}

		@Override
		public Instant instant() {
			return instant;
		}
	}
}
