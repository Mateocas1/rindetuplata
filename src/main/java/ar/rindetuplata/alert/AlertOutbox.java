package ar.rindetuplata.alert;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * The write side of the transactional outbox: every alert event of every rule type is one {@code outbox_event}
 * row inserted by the caller's transaction, next to the state change that caused it, so a crash can never leave a
 * fired alert with no event to relay. The relay publishes the row to the one alert queue whatever its type.
 */
@Component
class AlertOutbox {

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	private final JdbcClient jdbc;

	private final Clock clock;

	AlertOutbox(JdbcClient jdbc, Clock clock) {
		this.jdbc = jdbc;
		this.clock = clock;
	}

	/** Enqueues {@code payload} as event {@code eventType} of the rule; must run inside the caller's transaction. */
	void enqueue(String eventType, long ruleId, AlertEvent payload) {
		UUID eventId = payload.eventId();
		jdbc.sql("""
				insert into outbox_event (id, event_type, aggregate_id, payload, created_at, attempts)
				values (:id, :eventType, :aggregateId, cast(:payload as jsonb), :createdAt, 0)
				""")
			.param("id", eventId)
			.param("eventType", eventType)
			.param("aggregateId", String.valueOf(ruleId))
			.param("payload", write(payload))
			.param("createdAt", OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC))
			.update();
	}

	private static String write(AlertEvent payload) {
		try {
			return MAPPER.writeValueAsString(payload);
		}
		catch (JacksonException e) {
			throw new IllegalStateException("Cannot encode the alert payload", e);
		}
	}

}
