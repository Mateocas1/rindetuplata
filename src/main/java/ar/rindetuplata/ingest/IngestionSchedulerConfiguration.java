package ar.rindetuplata.ingest;

import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Wires the daily scheduler. With {@code rindetuplata.ingest.enabled=false} neither the scheduler bean nor the
 * scheduling infrastructure is registered, so no cron ever fires; replays through {@link IngestionService} stay
 * available. A {@link Clock} bean, when the context has one (tests), wins over the configured zone.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "rindetuplata.ingest.enabled", havingValue = "true", matchIfMissing = true)
class IngestionSchedulerConfiguration {

	@Bean
	IngestionScheduler ingestionScheduler(IngestionService ingestionService, IngestionProperties properties,
			ObjectProvider<Clock> clock) {
		Clock resolved = clock.getIfAvailable(() -> Clock.system(properties.zoneId()));
		return new IngestionScheduler(ingestionService, resolved);
	}
}
