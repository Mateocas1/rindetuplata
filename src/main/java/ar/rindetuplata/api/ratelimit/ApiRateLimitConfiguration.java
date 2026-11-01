package ar.rindetuplata.api.ratelimit;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the per-address token bucket of the public read API from {@link ApiRateLimitProperties} (T1a). */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ApiRateLimitProperties.class)
class ApiRateLimitConfiguration {

	@Bean
	ApiRateLimiter apiRateLimiter(ApiRateLimitProperties properties, Clock clock) {
		return new ApiRateLimiter(properties.capacity(), properties.refillPerSecond(), properties.maxTrackedAddresses(), clock);
	}
}
