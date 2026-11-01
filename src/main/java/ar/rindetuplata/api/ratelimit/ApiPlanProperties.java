package ar.rindetuplata.api.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code rindetuplata.api.plans.*} (T6d, S6, L32b): the limits of the two plans of an API key. The anonymous plan
 * (no key) stays the per-address bucket of {@code rindetuplata.api.rate-limit.*}. {@code FREE} is every key a user
 * creates; {@code PARTNER} is a key the operator promoted in SQL. A field left out takes the documented default.
 */
@ConfigurationProperties("rindetuplata.api.plans")
public record ApiPlanProperties(@DefaultValue PlanValues free, @DefaultValue PlanValues partner) {

	private static final ApiPlanLimits FREE_DEFAULT = new ApiPlanLimits(600, 10, 20_000);

	private static final ApiPlanLimits PARTNER_DEFAULT = new ApiPlanLimits(3_000, 50, 500_000);

	public ApiPlanLimits freeLimits() {
		return free.resolve(FREE_DEFAULT);
	}

	public ApiPlanLimits partnerLimits() {
		return partner.resolve(PARTNER_DEFAULT);
	}

	/** The bound values of one plan; a null one is "not configured". */
	public record PlanValues(Integer capacity, Double refillPerSecond, Long dailyQuota) {

		ApiPlanLimits resolve(ApiPlanLimits defaults) {
			return new ApiPlanLimits(capacity == null ? defaults.capacity() : capacity,
					refillPerSecond == null ? defaults.refillPerSecond() : refillPerSecond,
					dailyQuota == null ? defaults.dailyQuota() : dailyQuota);
		}
	}
}
