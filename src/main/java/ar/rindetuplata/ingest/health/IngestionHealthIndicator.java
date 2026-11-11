package ar.rindetuplata.ingest.health;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;

/**
 * The {@code ingestionFreshness} health contributor of the {@code ingestion} group: {@code UP} while the newest successful LIVE ingestion is inside its
 * window, {@code STALE} past it, and {@code STALE} too when none was ever recorded (an empty store has nothing to
 * rank). {@code STALE} maps to HTTP 200 on {@code /actuator/health}, so Docker's healthcheck, {@code depends_on} and
 * Caddy keep working while the data is old, and to 503 on {@code /actuator/health/ingestion}, which an external
 * monitor watches. A database failure surfaces as the exception it is and makes the indicator {@code DOWN}.
 */
public class IngestionHealthIndicator implements HealthIndicator {

	/** Data older than its window: not an outage of this process, so not {@code DOWN}. */
	public static final Status STALE = new Status("STALE");

	private final LastIngestionSuccessReader reader;

	private final IngestionFreshness freshness;

	private final Clock clock;

	IngestionHealthIndicator(LastIngestionSuccessReader reader, IngestionFreshness freshness, Clock clock) {
		this.reader = reader;
		this.freshness = freshness;
		this.clock = clock;
	}

	@Override
	public Health health() {
		Optional<LastIngestionSuccess> last = reader.newest();
		if (last.isEmpty()) {
			return Health.status(STALE).withDetail("reason", "no successful LIVE ingestion is recorded").build();
		}
		LastIngestionSuccess success = last.get();
		Instant now = clock.instant();
		return Health.status(freshness.isStale(success.captureDate(), now) ? STALE : Status.UP)
				.withDetail("lastSuccessDate", success.captureDate().toString())
				.withDetail("lastSuccessAt", success.recordedAt().toString())
				.withDetail("ageSeconds", age(success, now).toSeconds())
				.withDetail("staleAfter", freshness.staleAfter().toString())
				.withDetail("staleAt", freshness.staleAt(success.captureDate()).toString())
				.build();
	}

	/** Seconds since the newest success was recorded, or NaN when there is none; never negative. */
	double ageSeconds() {
		Instant now = clock.instant();
		return reader.newest().map(success -> (double) age(success, now).toSeconds()).orElse(Double.NaN);
	}

	private static Duration age(LastIngestionSuccess success, Instant now) {
		Duration age = Duration.between(success.recordedAt(), now);
		return age.isNegative() ? Duration.ZERO : age;
	}
}
