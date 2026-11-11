package ar.rindetuplata.ingest.health;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/**
 * Data is stale 36 hours after the expected 19:30 (America/Argentina/Buenos_Aires, UTC-3, no DST) run of its
 * capture date. The expected run comes from the cron the scheduler uses, so the two can never disagree.
 */
class IngestionFreshnessTest {

	static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

	static final LocalDate DAY = LocalDate.parse("2026-10-20");

	final IngestionFreshness freshness = new IngestionFreshness("0 30 19 * * *", ZONE, Duration.ofHours(36));

	@Test
	void theExpectedRunIsTheCronFireOfTheCaptureDateInTheZone() {
		assertThat(freshness.expectedRun(DAY)).isEqualTo(Instant.parse("2026-10-20T22:30:00Z"));
	}

	@Test
	void dataIsStaleAfterTheConfiguredDurationPastTheExpectedRun() {
		assertThat(freshness.staleAt(DAY)).isEqualTo(Instant.parse("2026-10-22T10:30:00Z"));
	}

	@Test
	void theDeadlineItselfIsStillFreshAndTheNextSecondIsStale() {
		Instant deadline = freshness.staleAt(DAY);

		assertThat(freshness.isStale(DAY, deadline.minusSeconds(1))).isFalse();
		assertThat(freshness.isStale(DAY, deadline)).isFalse();
		assertThat(freshness.isStale(DAY, deadline.plusSeconds(1))).isTrue();
	}

	@Test
	void aCustomDurationMovesTheDeadline() {
		IngestionFreshness twelveHours = new IngestionFreshness("0 30 19 * * *", ZONE, Duration.ofHours(12));

		assertThat(twelveHours.staleAt(DAY)).isEqualTo(Instant.parse("2026-10-21T10:30:00Z"));
	}

	@Test
	void aCronThatNeverFiresOnTheDayFallsBackToItsFirstFireAfterMidnightOfTheDay() {
		IngestionFreshness yearly = new IngestionFreshness("0 0 0 1 1 *", ZONE, Duration.ofHours(36));

		assertThat(yearly.expectedRun(DAY)).isEqualTo(Instant.parse("2027-01-01T03:00:00Z"));
	}
}
