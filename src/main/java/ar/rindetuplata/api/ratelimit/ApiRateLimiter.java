package ar.rindetuplata.api.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HexFormat;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A token bucket per remote address for the public read endpoints (T1a, S1). The first request of an address finds a
 * full bucket of {@code capacity} tokens; each request takes one and the bucket gets {@code refillPerSecond} back
 * every second, never above its capacity. An empty bucket answers how many seconds remain until one token is back,
 * and a refused request takes nothing. State is in memory because the API runs as one instance (S16): a second
 * instance would need a shared store. Buckets that have refilled completely are swept at most once per refill-to-full
 * period, and the sweep is claimed with a compare-and-set so a request that finds none due takes no lock.
 * The number of tracked buckets is capped at {@code maxTrackedAddresses}: a new address that arrives at the cap
 * first triggers a sweep (at most once per second) and, if the map is still full, is refused like an empty bucket
 * (fail closed for unknown clients) while existing addresses keep working. The cap check is not atomic with the
 * insert, so a burst of concurrent new addresses can overshoot it by at most the number of request threads.
 * An IPv6 client is keyed by its /64 prefix, because a single subscriber controls a whole /64 and would otherwise
 * get a fresh bucket per address; IPv4 (and IPv4-mapped IPv6) clients are keyed by the full address.
 */
public class ApiRateLimiter {

	private static final double NANOS_PER_SECOND = 1_000_000_000d;

	private static final long DEMAND_SWEEP_EVERY_MILLIS = 1000;

	private static final int IPV6_PREFIX_BYTES = 8;

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

		synchronized OptionalLong peek(Instant now) {
			refill(now);
			if (tokens >= 1) {
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

	private final int maxTrackedAddresses;

	private final long sweepEveryMillis;

	private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

	private final AtomicLong nextSweepMillis;

	private final AtomicLong nextDemandSweepMillis = new AtomicLong(Long.MIN_VALUE);

	ApiRateLimiter(int capacity, double refillPerSecond, Clock clock) {
		this(capacity, refillPerSecond, 100_000, clock);
	}

	ApiRateLimiter(int capacity, double refillPerSecond, int maxTrackedAddresses, Clock clock) {
		if (capacity < 1) {
			throw new IllegalArgumentException("rate-limit capacity must be at least 1: " + capacity);
		}
		if (!(refillPerSecond > 0)) {
			throw new IllegalArgumentException("rate-limit refill per second must be positive: " + refillPerSecond);
		}
		if (maxTrackedAddresses < 1) {
			throw new IllegalArgumentException(
					"rate-limit max tracked addresses must be at least 1: " + maxTrackedAddresses);
		}
		this.capacity = capacity;
		this.refillPerSecond = refillPerSecond;
		this.maxTrackedAddresses = maxTrackedAddresses;
		this.clock = clock;
		this.sweepEveryMillis = Math.max(1000, (long) Math.ceil(capacity / refillPerSecond * 1000));
		this.nextSweepMillis = new AtomicLong(clock.instant().toEpochMilli() + sweepEveryMillis);
	}

	/** Takes a token: empty when allowed, otherwise the seconds (at least 1) until the address may retry. */
	public OptionalLong tryAcquire(String address) {
		Instant now = clock.instant();
		sweepIfDue(now);
		String key = bucketKey(address);
		Bucket bucket = buckets.get(key);
		if (bucket == null) {
			if (buckets.size() >= maxTrackedAddresses) {
				sweepOnDemand(now);
				if (buckets.size() >= maxTrackedAddresses) {
					return OptionalLong.of(secondsUntilNextSweep(now));
				}
			}
			bucket = buckets.computeIfAbsent(key, k -> new Bucket(now));
		}
		return bucket.tryTake(now);
	}

	/**
	 * Like {@link #tryAcquire} but takes nothing: empty when the address has a token (or no bucket yet), otherwise the
	 * seconds until it has one. The API-key path asks it before a key lookup, so an address that has spent its
	 * bucket on refused keys costs no database read (T6d, F1).
	 */
	public OptionalLong peek(String address) {
		Bucket bucket = buckets.get(bucketKey(address));
		return bucket == null ? OptionalLong.empty() : bucket.peek(clock.instant());
	}

	int trackedAddresses() {
		return buckets.size();
	}

	/** IPv6 literals collapse to their /64; everything else keeps the address as given. Never resolves a name. */
	static String bucketKey(String address) {
		if (address == null || address.indexOf(':') < 0) {
			return address;
		}
		try {
			int scope = address.indexOf('%');
			String literal = scope < 0 ? address : address.substring(0, scope);
			if (literal.indexOf(':') < 0) {
				// Not an IPv6 literal once the zone id is cut (e.g. "abc%:"): older JDKs could resolve such text by name.
				return address;
			}
			// The brackets make the JDK treat the text strictly as an IPv6 literal: it throws instead of doing a DNS lookup.
			InetAddress parsed = InetAddress.getByName("[" + literal + "]");
			if (parsed instanceof Inet6Address) {
				return HexFormat.of().formatHex(parsed.getAddress(), 0, IPV6_PREFIX_BYTES) + "/64";
			}
			return parsed.getHostAddress();
		}
		catch (UnknownHostException | RuntimeException ex) {
			return address;
		}
	}

	private void sweepIfDue(Instant now) {
		long nowMillis = now.toEpochMilli();
		long due = nextSweepMillis.get();
		if (nowMillis >= due && nextSweepMillis.compareAndSet(due, nowMillis + sweepEveryMillis)) {
			removeFullBuckets(now);
		}
	}

	private void sweepOnDemand(Instant now) {
		long nowMillis = now.toEpochMilli();
		long due = nextDemandSweepMillis.get();
		if (nowMillis >= due && nextDemandSweepMillis.compareAndSet(due, nowMillis + DEMAND_SWEEP_EVERY_MILLIS)) {
			removeFullBuckets(now);
		}
	}

	private void removeFullBuckets(Instant now) {
		buckets.values().removeIf(bucket -> bucket.isFull(now));
	}

	private long secondsUntilNextSweep(Instant now) {
		long millis = nextSweepMillis.get() - now.toEpochMilli();
		return Math.max(1, (long) Math.ceil(millis / 1000d));
	}
}
