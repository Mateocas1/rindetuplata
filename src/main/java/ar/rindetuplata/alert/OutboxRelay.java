package ar.rindetuplata.alert;

import java.time.Clock;
import java.time.Instant;
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
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * T6d: the transactional outbox relay (S4). One run claims the oldest due {@code outbox_event} rows and publishes
 * each one with publisher confirms: only after the broker acknowledged it is the row marked published. A failure
 * leaves the row for the next run, with {@code attempts} raised and {@code last_error} stored, which makes the
 * delivery at least once; the consumer's {@code processed_event} rows turn that into exactly one email per event.
 *
 * <p>T11b: the claim is a short transaction ({@code FOR UPDATE SKIP LOCKED}, so two relays never claim the same
 * row) that stamps {@code locked_until = now + lease} and commits before anything is published, so no row lock is
 * held while the relay waits for the broker. Every outcome is its own short update. A row is due while it is
 * unpublished, under {@code max-attempts} and not leased; a relay that crashed mid-publish therefore only delays
 * its rows until the lease expires. A row that reaches the attempt cap is logged at ERROR once and stays in the
 * table for the operator.
 *
 * <p>The template publishes with {@code mandatory=true}: a message the broker cannot route is acked and returned,
 * and the relay treats the return as a failure instead of marking the row published.
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

	private final TransactionTemplate transaction;

	public OutboxRelay(JdbcClient jdbc, RabbitTemplate rabbit, Clock clock, OutboxProperties properties,
			PlatformTransactionManager transactionManager) {
		this.jdbc = jdbc;
		this.rabbit = rabbit;
		this.clock = clock;
		this.properties = properties;
		this.transaction = new TransactionTemplate(transactionManager);
	}

	/** Publishes the due events, oldest first, at most {@code relay-batch-size} of them; returns how many went out. */
	public int relayPending() {
		List<PendingEvent> pending = transaction.execute(status -> claim());
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

	/** Selects the due rows with a skip-locked row lock and leases them; the caller's transaction commits it. */
	private List<PendingEvent> claim() {
		Instant now = clock.instant();
		List<PendingEvent> due = jdbc.sql("""
				select id, payload::text as payload
				from outbox_event
				where published_at is null
				  and attempts < :maxAttempts
				  and (locked_until is null or locked_until < :now)
				order by created_at, id
				limit :limit
				for update skip locked
				""")
			.param("maxAttempts", properties.maxAttempts())
			.param("now", utc(now))
			.param("limit", properties.relayBatchSize())
			.query((rs, rowNumber) -> new PendingEvent(rs.getObject("id", UUID.class), rs.getString("payload")))
			.list();
		for (PendingEvent event : due) {
			jdbc.sql("update outbox_event set locked_until = :lockedUntil where id = :id")
				.param("lockedUntil", utc(now.plus(properties.lease())))
				.param("id", event.id())
				.update();
		}
		return due;
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
		ReturnedMessage returned = correlation.getReturned();
		if (returned != null) {
			throw new AmqpException("the broker returned the alert event as unroutable: " + returned.getReplyText()
					+ " (exchange " + returned.getExchange() + ", routing key " + returned.getRoutingKey() + ")");
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
		jdbc.sql("update outbox_event set published_at = :publishedAt, locked_until = null where id = :id")
			.param("publishedAt", utc(clock.instant()))
			.param("id", eventId)
			.update();
	}

	private void markFailed(UUID eventId, RuntimeException failure) {
		int attempts = jdbc.sql("""
				update outbox_event set attempts = attempts + 1, last_error = :lastError, locked_until = null
				where id = :id
				returning attempts
				""")
			.param("lastError", describe(failure))
			.param("id", eventId)
			.query(Integer.class)
			.single();
		if (attempts == properties.maxAttempts()) {
			log.error("alert event {} failed {} times and will not be relayed again; it stays in outbox_event: {}",
					eventId, attempts, describe(failure));
		}
	}

	private static OffsetDateTime utc(Instant instant) {
		return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
	}

	private static String describe(RuntimeException failure) {
		String message = failure.getClass().getName() + ": " + failure.getMessage();
		return message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH);
	}

	/** One claimed outbox row: its id and the JSON the consumer reads. */
	private record PendingEvent(UUID id, String payload) {
	}

}
