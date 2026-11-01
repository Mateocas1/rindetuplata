package ar.rindetuplata.api.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code rindetuplata.api.rate-limit.*}: the per-address token bucket of the public read API (T1a, S1). A bucket
 * holds {@code capacity} tokens and gets {@code refillPerSecond} back every second. {@code bypassToken} lets the
 * static site build, which makes hundreds of calls from one machine, skip the limit by sending it in
 * {@code X-Build-Token}; blank disables the bypass.
 */
@ConfigurationProperties("rindetuplata.api.rate-limit")
public record ApiRateLimitProperties(
		@DefaultValue("120") int capacity,
		@DefaultValue("2") double refillPerSecond,
		@DefaultValue("") String bypassToken) {

	@Override
	public String toString() {
		return "ApiRateLimitProperties[capacity=" + capacity + ", refillPerSecond=" + refillPerSecond
				+ ", bypassToken=" + (bypassToken.isBlank() ? "<disabled>" : "<set>") + "]";
	}
}
