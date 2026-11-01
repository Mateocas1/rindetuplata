package ar.rindetuplata.api.ratelimit;

import ar.rindetuplata.apikey.ApiKeyAuthenticator;
import ar.rindetuplata.apikey.ApiKeyPlan;
import java.time.Clock;
import java.util.Map;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the per-address token bucket of the public read API from {@link ApiRateLimitProperties} (T1a) and the
 * per-key buckets and quotas of the API-key plans from {@link ApiPlanProperties} (T6d).
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({ ApiRateLimitProperties.class, ApiPlanProperties.class })
class ApiRateLimitConfiguration {

	@Bean
	ApiRateLimiter apiRateLimiter(ApiRateLimitProperties properties, Clock clock) {
		return new ApiRateLimiter(properties.capacity(), properties.refillPerSecond(), properties.maxTrackedAddresses(), clock);
	}

	@Bean
	ApiKeyAdmission apiKeyAdmission(ApiKeyAuthenticator authenticator, ApiPlanProperties plans,
			ApiRateLimitProperties properties, Clock clock) {
		return new ApiKeyAdmission(authenticator,
				Map.of(ApiKeyPlan.FREE, plans.freeLimits(), ApiKeyPlan.PARTNER, plans.partnerLimits()),
				properties.maxTrackedAddresses(), clock);
	}
}
