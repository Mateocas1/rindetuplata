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
		return new IngestionScheduler(ingestionService, resolveClock(properties, clock));
	}

	/**
	 * T14a (S20): with {@code rindetuplata.ingest.run-on-startup=true} the application runs today's LIVE ingestion
	 * once, in the background, as soon as it is ready. It stays off by default; the scheduler's own clock and zone
	 * decide which day "today" is.
	 */
	@Bean
	@ConditionalOnProperty(name = "rindetuplata.ingest.run-on-startup", havingValue = "true")
	StartupIngestionListener startupIngestionListener(IngestionScheduler scheduler) {
		return new StartupIngestionListener(scheduler::runToday);
	}

	private static Clock resolveClock(IngestionProperties properties, ObjectProvider<Clock> clock) {
		return clock.getIfAvailable(() -> Clock.system(properties.zoneId()));
	}
}
