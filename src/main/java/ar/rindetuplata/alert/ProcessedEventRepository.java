package ar.rindetuplata.alert;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * The V6 {@code processed_event} table (T6d, S4): the consumer's idempotence. The row is inserted only after the
 * email went out, so "already processed" means "the saver already has that email", and a redelivery of the same
 * {@code AlertTriggered} event sends nothing.
 */
@Component
public class ProcessedEventRepository {

	private final JdbcClient jdbc;

	public ProcessedEventRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public boolean isProcessed(UUID eventId) {
		return jdbc.sql("select exists (select 1 from processed_event where event_id = :id)")
			.param("id", eventId)
			.query(Boolean.class)
			.single();
	}

	/** Records the delivery; a false return means a concurrent delivery recorded it first. */
	public boolean markProcessed(UUID eventId, Instant processedAt) {
		return jdbc.sql("insert into processed_event (event_id, processed_at) values (:id, :processedAt)"
				+ " on conflict (event_id) do nothing")
			.param("id", eventId)
			.param("processedAt", OffsetDateTime.ofInstant(processedAt, ZoneOffset.UTC))
			.update() == 1;
	}

}
