package ar.rindetuplata.account;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/**
 * A clock the account tests move by hand, so the 15-minute token lifetime and the 3-unexpired-token cap are
 * assertable without waiting. It is the {@code @Primary Clock} of the account test context, so both the token
 * service and the account code see the same "now".
 */
final class MutableClock extends Clock {

	/** 2026-10-20T09:00 in America/Argentina/Buenos_Aires (UTC-3). */
	static final Instant BASE = Instant.parse("2026-10-20T12:00:00Z");
	static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

	private Instant instant = BASE;

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

	void advance(Duration amount) {
		instant = instant.plus(amount);
	}

	void reset() {
		instant = BASE;
	}
}
