package ar.rindetuplata.account;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the per-IP counter of the magic-link endpoint from {@link AccountProperties} (T11a). */
@Configuration(proxyBeanMethods = false)
class MagicLinkRateLimitConfiguration {

	@Bean
	MagicLinkRateLimiter magicLinkRateLimiter(AccountProperties properties, Clock clock) {
		return new MagicLinkRateLimiter(properties.magicLinkPerIpLimit(), properties.magicLinkPerIpWindow(), clock);
	}
}
