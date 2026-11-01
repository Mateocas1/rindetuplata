package ar.rindetuplata.api.ratelimit;

/**
 * What one plan of API key may do (T6d): a bucket of {@code capacity} tokens refilled {@code refillPerSecond} per
 * second, and {@code dailyQuota} counted requests per Argentine day.
 */
public record ApiPlanLimits(int capacity, double refillPerSecond, long dailyQuota) {

	public ApiPlanLimits {
		if (capacity < 1) {
			throw new IllegalArgumentException("plan capacity must be at least 1: " + capacity);
		}
		if (!(refillPerSecond > 0)) {
			throw new IllegalArgumentException("plan refill per second must be positive: " + refillPerSecond);
		}
		if (dailyQuota < 1) {
			throw new IllegalArgumentException("plan daily quota must be at least 1: " + dailyQuota);
		}
	}
}
