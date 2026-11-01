package ar.rindetuplata.ingest;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Properties and the real pause of the history backfill. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(BackfillProperties.class)
class BackfillConfiguration {

	@Bean
	BackfillPause backfillPause() {
		return delay -> Thread.sleep(delay);
	}
}
