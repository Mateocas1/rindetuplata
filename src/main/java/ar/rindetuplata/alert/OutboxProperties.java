package ar.rindetuplata.alert;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code rindetuplata.outbox.*}: the delivery knobs of T6d (S4). {@code relayEnabled} and {@code consumerEnabled}
 * switch the scheduled relay and the RabbitMQ listener off in a context that has no broker (every test that does
 * not exercise the delivery), while the batch size is the design's 50 rows per run.
 */
@ConfigurationProperties("rindetuplata.outbox")
public record OutboxProperties(
		@DefaultValue("true") boolean relayEnabled,
		@DefaultValue("true") boolean consumerEnabled,
		@DefaultValue("5s") Duration relayInterval,
		@DefaultValue("50") int relayBatchSize,
		@DefaultValue("5s") Duration confirmTimeout) {
}
