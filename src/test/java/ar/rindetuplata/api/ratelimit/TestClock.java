package ar.rindetuplata.api.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/** A clock the rate-limit unit tests move by hand. */
final class TestClock extends Clock {

	private Instant instant = Instant.parse("2026-10-20T12:00:00Z");

	@Override
	public ZoneId getZone() {
		return ZoneId.of("America/Argentina/Buenos_Aires");
	}

	@Override
	public Clock withZone(ZoneId zone) {
		return Clock.fixed(instant, zone);
	}

	@Override
	public Instant instant() {
		return instant;
	}

	void advance(Duration amount) {
		instant = instant.plus(amount);
	}
}
