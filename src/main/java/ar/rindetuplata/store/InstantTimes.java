package ar.rindetuplata.store;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** {@code timestamptz} round trip: instants are written and read as UTC offset date-times. */
final class InstantTimes {

	private InstantTimes() {
	}

	static OffsetDateTime of(Instant instant) {
		return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
	}

	static Instant toInstant(OffsetDateTime value) {
		return value == null ? null : value.toInstant();
	}
}
