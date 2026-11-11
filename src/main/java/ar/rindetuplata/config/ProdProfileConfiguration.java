package ar.rindetuplata.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

/**
 * Registers the {@link ProdProfileGuard} when the {@code prod} profile is active. The bean factory
 * post-processor is static so it runs before any other bean is created: a bad configuration is refused before
 * Flyway, the connection pool or the broker are touched.
 */
@Configuration(proxyBeanMethods = false)
@Profile("prod")
class ProdProfileConfiguration {

	@Bean
	static ProdProfileGuard prodProfileGuard(Environment environment) {
		return new ProdProfileGuard(environment);
	}
}
