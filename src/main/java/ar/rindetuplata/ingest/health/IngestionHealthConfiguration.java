package ar.rindetuplata.ingest.health;

import ar.rindetuplata.ingest.IngestionProperties;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Wires the {@code ingestion} health indicator and its age gauge (L33b, S4). The bean name makes the contributor
 * {@code ingestionFreshness}: Spring Boot refuses a contributor and a health group of the same name, and the group
 * must be {@code ingestion} to serve {@code /actuator/health/ingestion}.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({ IngestionProperties.class, IngestionHealthProperties.class })
class IngestionHealthConfiguration {

	/** Seconds since the newest successful LIVE ingestion was recorded; NaN (no sample) when none exists. */
	static final String AGE_GAUGE = "rindetuplata.ingest.last.success.age";

	@Bean
	IngestionHealthIndicator ingestionFreshnessHealthIndicator(JdbcClient jdbc, IngestionProperties ingest,
			IngestionHealthProperties health, Clock clock) {
		IngestionFreshness freshness = new IngestionFreshness(ingest.cron(), ingest.zoneId(), health.staleAfter());
		return new IngestionHealthIndicator(new LastIngestionSuccessReader(jdbc), freshness, clock);
	}

	@Bean
	MeterBinder ingestionAgeGauge(IngestionHealthIndicator indicator) {
		return registry -> Gauge.builder(AGE_GAUGE, indicator, IngestionHealthIndicator::ageSeconds)
				.baseUnit("seconds")
				.description("Seconds since the newest successful LIVE ingestion was recorded")
				.register(registry);
	}
}
