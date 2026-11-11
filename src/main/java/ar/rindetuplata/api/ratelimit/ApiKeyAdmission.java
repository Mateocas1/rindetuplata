package ar.rindetuplata.api.ratelimit;

import ar.rindetuplata.apikey.ApiKeyAuthenticator;
import ar.rindetuplata.apikey.ApiKeyPlan;
import ar.rindetuplata.apikey.ApiKeyPrincipal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * What a presented {@code X-Api-Key} is worth on the public read API. The key is resolved on every
 * request against the database, so a revoked key stops working at once and no cache can outlive it. A valid key gets
 * a token bucket of its own, sized by its plan, and a quota of counted requests per day in
 * {@code America/Argentina/Buenos_Aires}; a request the bucket refuses does not count towards the quota. Both live
 * in memory because the API runs as one instance: a restart resets the quotas and a second instance would
 * need a shared store. Quota entries of past days are dropped the first time a request sees a new day.
 */
public final class ApiKeyAdmission {

	static final String BUCKET_DETAIL = "Too many requests for this API key; try again later";

	static final String QUOTA_DETAIL =
			"The daily quota of this API key is used up; it resets at midnight (America/Argentina/Buenos_Aires)";

	private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

	/** The result of presenting a key. */
	public sealed interface Outcome permits Admitted, Unknown, Limited {
	}

	/** A valid key within its limits: serve the request. */
	public record Admitted() implements Outcome {
	}

	/** No active key matches: answer 401. */
	public record Unknown() implements Outcome {
	}

	/** A valid key over its bucket or daily quota: answer 429 with this {@code Retry-After}. */
	public record Limited(long retryAfterSeconds, String detail) implements Outcome {
	}

	private final ApiKeyAuthenticator authenticator;

	private final Map<ApiKeyPlan, ApiPlanLimits> limits;

	private final Map<ApiKeyPlan, ApiRateLimiter> buckets = new EnumMap<>(ApiKeyPlan.class);

	private final Map<Long, Used> quotas = new ConcurrentHashMap<>();

	private final AtomicReference<LocalDate> sweptDay = new AtomicReference<>();

	private final Clock clock;

	public ApiKeyAdmission(ApiKeyAuthenticator authenticator, Map<ApiKeyPlan, ApiPlanLimits> limits,
			int maxTrackedKeys, Clock clock) {
		this.authenticator = authenticator;
		this.limits = new EnumMap<>(limits);
		this.clock = clock;
		for (ApiKeyPlan plan : ApiKeyPlan.values()) {
			ApiPlanLimits planLimits = this.limits.get(plan);
			if (planLimits == null) {
				throw new IllegalArgumentException("no limits configured for the API key plan " + plan);
			}
			buckets.put(plan, new ApiRateLimiter(planLimits.capacity(), planLimits.refillPerSecond(), maxTrackedKeys, clock));
		}
	}

	public Outcome admit(String presentedKey) {
		Optional<ApiKeyPrincipal> principal = authenticator.authenticate(presentedKey);
		if (principal.isEmpty()) {
			return new Unknown();
		}
		ApiKeyPrincipal key = principal.get();
		OptionalLong refused = buckets.get(key.plan()).tryAcquire("key-" + key.id());
		if (refused.isPresent()) {
			return new Limited(refused.getAsLong(), BUCKET_DETAIL);
		}
		Instant now = clock.instant();
		LocalDate today = now.atZone(ZONE).toLocalDate();
		sweepPastDays(today);
		long quota = limits.get(key.plan()).dailyQuota();
		boolean counted = quotas.computeIfAbsent(key.id(), id -> new Used()).tryCount(today, quota);
		if (counted) {
			return new Admitted();
		}
		long untilMidnight = Duration.between(now, today.plusDays(1).atStartOfDay(ZONE).toInstant()).toSeconds();
		return new Limited(Math.max(1, untilMidnight), QUOTA_DETAIL);
	}

	private void sweepPastDays(LocalDate today) {
		LocalDate swept = sweptDay.get();
		if (!today.equals(swept) && sweptDay.compareAndSet(swept, today)) {
			quotas.values().removeIf(used -> used.isBefore(today));
		}
	}

	/** The requests one key made on one day. */
	private static final class Used {

		private LocalDate day;

		private long count;

		synchronized boolean tryCount(LocalDate today, long quota) {
			if (!today.equals(day)) {
				day = today;
				count = 0;
			}
			if (count >= quota) {
				return false;
			}
			count++;
			return true;
		}

		synchronized boolean isBefore(LocalDate today) {
			return day != null && day.isBefore(today);
		}
	}
}
