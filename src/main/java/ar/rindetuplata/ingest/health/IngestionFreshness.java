package ar.rindetuplata.ingest.health;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.springframework.scheduling.support.CronExpression;

/**
 * When the data of a capture date goes stale (L33b): {@code stale-after} past the run the scheduler was expected to
 * make for that date. The expected run is the first fire of the scheduler's own cron at or after midnight of the date
 * in the scheduler's zone (19:30 in America/Argentina/Buenos_Aires by default), so this class and the scheduler can
 * never disagree about when the day's run happens. With the 36 hours default, one missed run gets a 12 hour grace
 * (the next 19:30 run is 24 hours later), and two missed runs make the data stale.
 */
public final class IngestionFreshness {

	private final CronExpression cron;

	private final ZoneId zone;

	private final Duration staleAfter;

	public IngestionFreshness(String cron, ZoneId zone, Duration staleAfter) {
		this.cron = CronExpression.parse(cron);
		this.zone = zone;
		this.staleAfter = staleAfter;
	}

	/** The instant the run of {@code captureDate} is expected, at midnight of the date when the cron never fires. */
	public Instant expectedRun(LocalDate captureDate) {
		ZonedDateTime midnight = captureDate.atStartOfDay(zone);
		ZonedDateTime fire = cron.next(midnight.minusNanos(1));
		return (fire == null ? midnight : fire).toInstant();
	}

	/** The last instant at which data of {@code captureDate} is still fresh. */
	public Instant staleAt(LocalDate captureDate) {
		return expectedRun(captureDate).plus(staleAfter);
	}

	/** Whether {@code now} is strictly past {@link #staleAt}. */
	public boolean isStale(LocalDate captureDate, Instant now) {
		return now.isAfter(staleAt(captureDate));
	}

	public Duration staleAfter() {
		return staleAfter;
	}
}
