package ar.rindetuplata.api.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A token bucket per remote address for the public read endpoints (T1a, S1). The first request of an address finds a
 * full bucket of {@code capacity} tokens; each request takes one and the bucket gets {@code refillPerSecond} back
 * every second, never above its capacity. An empty bucket answers how many seconds remain until one token is back,
 * and a refused request takes nothing. State is in memory because the API runs as one instance (S16): a second
 * instance would need a shared store. Buckets that have refilled completely are swept at most once per refill-to-full
 * period, so memory is bounded by the addresses seen in that period.
 */
public class ApiRateLimiter {

	private static final double NANOS_PER_SECOND = 1_000_000_000d;

	private final class Bucket {

		private double tokens = capacity;

		private Instant updated;

		Bucket(Instant now) {
			this.updated = now;
		}

		synchronized OptionalLong tryTake(Instant now) {
			refill(now);
			if (tokens >= 1) {
				tokens -= 1;
				return OptionalLong.empty();
			}
			return OptionalLong.of(Math.max(1, (long) Math.ceil((1 - tokens) / refillPerSecond)));
		}

		synchronized boolean isFull(Instant now) {
			refill(now);
			return tokens >= capacity;
		}

		private void refill(Instant now) {
			if (now.isAfter(updated)) {
				double elapsed = Duration.between(updated, now).toNanos() / NANOS_PER_SECOND;
				tokens = Math.min(capacity, tokens + elapsed * refillPerSecond);
				updated = now;
			}
		}
	}

	private final int capacity;

	private final double refillPerSecond;

	private final Clock clock;

	private final Duration sweepEvery;

	private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

	private Instant nextSweep;

	ApiRateLimiter(int capacity, double refillPerSecond, Clock clock) {
		if (capacity < 1) {
			throw new IllegalArgumentException("rate-limit capacity must be at least 1: " + capacity);
		}
		if (!(refillPerSecond > 0)) {
			throw new IllegalArgumentException("rate-limit refill per second must be positive: " + refillPerSecond);
		}
		this.capacity = capacity;
		this.refillPerSecond = refillPerSecond;
		this.clock = clock;
		this.sweepEvery = Duration.ofMillis(Math.max(1000, (long) Math.ceil(capacity / refillPerSecond * 1000)));
		this.nextSweep = clock.instant().plus(sweepEvery);
	}

	/** Takes a token: empty when allowed, otherwise the seconds (at least 1) until the address may retry. */
	public OptionalLong tryAcquire(String address) {
		Instant now = clock.instant();
		sweep(now);
		return buckets.computeIfAbsent(address, key -> new Bucket(now)).tryTake(now);
	}

	int trackedAddresses() {
		return buckets.size();
	}

	private synchronized void sweep(Instant now) {
		if (now.isBefore(nextSweep)) {
			return;
		}
		buckets.values().removeIf(bucket -> bucket.isFull(now));
		nextSweep = now.plus(sweepEvery);
	}
}
