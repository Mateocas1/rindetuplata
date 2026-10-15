package ar.rindetuplata.account;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A fixed-window counter per remote address for {@code POST /api/v1/auth/magic-link} (T11a, S4). The first request
 * of an address opens a window of {@code rindetuplata.auth.magic-link-per-ip-window}; it allows
 * {@code rindetuplata.auth.magic-link-per-ip-limit} requests, then answers how many seconds remain until the window
 * ends. State is in memory because the API runs as one instance (S16): a second instance would need a shared store.
 * Expired windows are swept at most once per window, so memory is bounded by the addresses seen in one window.
 */
class MagicLinkRateLimiter {

	private record Window(Instant end, int count) {
	}

	private final int limit;

	private final Duration window;

	private final Clock clock;

	private final Map<String, Window> windows = new ConcurrentHashMap<>();

	private Instant nextSweep;

	MagicLinkRateLimiter(int limit, Duration window, Clock clock) {
		this.limit = limit;
		this.window = window;
		this.clock = clock;
		this.nextSweep = clock.instant().plus(window);
	}

	/** Counts a request: empty when allowed, otherwise the seconds (at least 1) until the address may retry. */
	OptionalLong tryAcquire(String address) {
		Instant now = clock.instant();
		sweep(now);
		Window updated = windows.compute(address, (key, current) -> {
			if (current == null || !now.isBefore(current.end())) {
				return new Window(now.plus(window), 1);
			}
			return new Window(current.end(), current.count() + 1);
		});
		if (updated.count() <= limit) {
			return OptionalLong.empty();
		}
		long millis = Duration.between(now, updated.end()).toMillis();
		return OptionalLong.of(Math.max(1, (millis + 999) / 1000));
	}

	int trackedAddresses() {
		return windows.size();
	}

	private synchronized void sweep(Instant now) {
		if (now.isBefore(nextSweep)) {
			return;
		}
		windows.values().removeIf(entry -> !now.isBefore(entry.end()));
		nextSweep = now.plus(window);
	}
}
