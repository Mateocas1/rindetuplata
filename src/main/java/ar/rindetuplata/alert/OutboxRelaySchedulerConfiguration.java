package ar.rindetuplata.alert;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Wires the scheduled relay (T6d). With {@code rindetuplata.outbox.relay-enabled=false} neither the scheduler bean
 * nor the scheduling infrastructure is registered here, so a test that does not exercise the delivery never talks
 * to a broker; the relay bean itself stays available for a test that calls it directly.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "rindetuplata.outbox.relay-enabled", havingValue = "true", matchIfMissing = true)
class OutboxRelaySchedulerConfiguration {

	@Bean
	OutboxRelayScheduler outboxRelayScheduler(OutboxRelay relay) {
		return new OutboxRelayScheduler(relay);
	}

}
