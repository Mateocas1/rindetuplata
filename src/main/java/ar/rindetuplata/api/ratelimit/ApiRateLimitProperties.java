package ar.rindetuplata.api.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code rindetuplata.api.rate-limit.*}: the per-address token bucket of the public read API. A bucket
 * holds {@code capacity} tokens and gets {@code refillPerSecond} back every second. {@code bypassToken} lets the
 * static site build, which makes hundreds of calls from one machine, skip the limit by sending it in
 * {@code X-Build-Token}; blank disables the bypass. {@code maxTrackedAddresses} caps the buckets held in memory:
 * beyond it a new address is refused (429) until a sweep frees room, while known addresses keep working.
 */
@ConfigurationProperties("rindetuplata.api.rate-limit")
public record ApiRateLimitProperties(
		@DefaultValue("120") int capacity,
		@DefaultValue("2") double refillPerSecond,
		@DefaultValue("") String bypassToken,
		@DefaultValue("100000") int maxTrackedAddresses) {

	@Override
	public String toString() {
		return "ApiRateLimitProperties[capacity=" + capacity + ", refillPerSecond=" + refillPerSecond
				+ ", maxTrackedAddresses=" + maxTrackedAddresses
				+ ", bypassToken=" + (bypassToken.isBlank() ? "<disabled>" : "<set>") + "]";
	}
}
