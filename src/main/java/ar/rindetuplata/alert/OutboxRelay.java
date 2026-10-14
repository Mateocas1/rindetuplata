package ar.rindetuplata.alert;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * T6d: the transactional outbox relay (S4). One run claims the oldest unpublished {@code outbox_event} rows with
 * {@code FOR UPDATE SKIP LOCKED} (so two relays never publish the same row) and publishes each one with publisher
 * confirms: only after the broker acknowledged it is the row marked published. A failure leaves the row for the
 * next run, with {@code attempts} raised and {@code last_error} stored, which makes the delivery at least once; the
 * consumer's {@code processed_event} rows turn that into exactly one email per event.
 */
@Component
public class OutboxRelay {

	private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

	/** A failure reason is operator information, not a log file: one line is enough. */
	private static final int MAX_ERROR_LENGTH = 1000;

	private final JdbcClient jdbc;

	private final RabbitTemplate rabbit;

	private final Clock clock;

	private final OutboxProperties properties;

	public OutboxRelay(JdbcClient jdbc, RabbitTemplate rabbit, Clock clock, OutboxProperties properties) {
		this.jdbc = jdbc;
		this.rabbit = rabbit;
		this.clock = clock;
		this.properties = properties;
	}

	/** Publishes the pending events, oldest first, at most {@code relay-batch-size} of them; returns how many went out. */
	@Transactional
	public int relayPending() {
		List<PendingEvent> pending = jdbc.sql("""
				select id, payload::text as payload
				from outbox_event
				where published_at is null
				order by created_at, id
				limit :limit
				for update skip locked
				""")
			.param("limit", properties.relayBatchSize())
			.query((rs, rowNumber) -> new PendingEvent(rs.getObject("id", UUID.class), rs.getString("payload")))
			.list();
		int published = 0;
		for (PendingEvent event : pending) {
			try {
				publish(event);
				markPublished(event.id());
				published++;
			}
			catch (RuntimeException e) {
				markFailed(event.id(), e);
				log.warn("alert event {} stays pending: {}", event.id(), e.getMessage());
			}
		}
		return published;
	}

	private void publish(PendingEvent event) {
		CorrelationData correlation = new CorrelationData(event.id().toString());
		rabbit.convertAndSend(AlertEvents.EXCHANGE, AlertEvents.ROUTING_KEY, event.payload(), message -> {
			MessageProperties headers = message.getMessageProperties();
			headers.setContentType(MessageProperties.CONTENT_TYPE_JSON);
			headers.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
			headers.setMessageId(event.id().toString());
			return message;
		}, correlation);
		CorrelationData.Confirm confirm = awaitConfirm(correlation);
		if (!confirm.isAck()) {
			throw new AmqpException("the broker rejected the alert event: " + confirm.getReason());
		}
	}

	private CorrelationData.Confirm awaitConfirm(CorrelationData correlation) {
		try {
			return correlation.getFuture().get(properties.confirmTimeout().toMillis(), TimeUnit.MILLISECONDS);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new AmqpException("interrupted while waiting for the broker confirmation", e);
		}
		catch (TimeoutException e) {
			throw new AmqpException("the broker did not confirm the alert event in " + properties.confirmTimeout(), e);
		}
		catch (ExecutionException e) {
			throw new AmqpException("the broker confirmation failed: " + e.getCause(), e);
		}
	}

	private void markPublished(UUID eventId) {
		jdbc.sql("update outbox_event set published_at = :publishedAt where id = :id")
			.param("publishedAt", OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC))
			.param("id", eventId)
			.update();
	}

	private void markFailed(UUID eventId, RuntimeException failure) {
		jdbc.sql("update outbox_event set attempts = attempts + 1, last_error = :lastError where id = :id")
			.param("lastError", describe(failure))
			.param("id", eventId)
			.update();
	}

	private static String describe(RuntimeException failure) {
		String message = failure.getClass().getName() + ": " + failure.getMessage();
		return message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH);
	}

	/** One claimed outbox row: its id and the JSON the consumer reads. */
	private record PendingEvent(UUID id, String payload) {
	}

}
