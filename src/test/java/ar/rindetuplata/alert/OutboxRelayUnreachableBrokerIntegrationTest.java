package ar.rindetuplata.alert;

import static org.assertj.core.api.Assertions.assertThat;

import ar.rindetuplata.account.AccountTestConfiguration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * T6d failure rule (S4): when the broker cannot be reached the outbox row must survive, so the alert is delivered
 * later instead of being lost. There is no RabbitMQ container here on purpose: the connection factory points at a
 * closed port, which is the unreachable broker of the rule. The row stays unpublished, its {@code attempts} goes up
 * and {@code last_error} keeps the reason.
 */
@SpringBootTest(properties = {
		"rindetuplata.ingest.enabled=false",
		"rindetuplata.ingest.cron=0 0 0 1 1 *",
		"rindetuplata.outbox.relay-enabled=false",
		"rindetuplata.outbox.consumer-enabled=false",
		"spring.rabbitmq.host=127.0.0.1",
		"spring.rabbitmq.port=1"
})
@Import(AccountTestConfiguration.class)
class OutboxRelayUnreachableBrokerIntegrationTest {

	@Autowired
	OutboxRelay relay;

	@Autowired
	JdbcClient jdbc;

	@BeforeEach
	void clean() {
		jdbc.sql("delete from outbox_event").update();
	}

	@Test
	void aBrokerThatCannotBeReachedLeavesTheRowForTheNextRun() {
		UUID eventId = insert();

		int published = relay.relayPending();

		assertThat(published).as("nothing was published").isZero();
		assertThat(publishedAt(eventId)).as("the row is still pending").isNull();
		assertThat(attempts(eventId)).as("the failed attempt is counted").isEqualTo(1);
		assertThat(lastError(eventId)).as("the reason is stored for the operator").isNotBlank();
	}

	private UUID insert() {
		UUID eventId = UUID.randomUUID();
		jdbc.sql("""
				insert into outbox_event (id, event_type, aggregate_id, payload, created_at, attempts)
				values (:id, 'AlertTriggered', '7', cast(:payload as jsonb), :createdAt, 0)
				""")
			.param("id", eventId)
			.param("payload", OutboxRelayIntegrationTest.payload(eventId, 7, "Wallet Unreachable"))
			.param("createdAt", OffsetDateTime.ofInstant(Instant.parse("2026-10-14T12:00:00Z"), ZoneOffset.UTC))
			.update();
		return eventId;
	}

	private OffsetDateTime publishedAt(UUID eventId) {
		return jdbc.sql("select published_at from outbox_event where id = :id")
			.param("id", eventId)
			.query((rs, rowNumber) -> rs.getObject("published_at", OffsetDateTime.class))
			.optional()
			.orElse(null);
	}

	private int attempts(UUID eventId) {
		return jdbc.sql("select attempts from outbox_event where id = :id")
			.param("id", eventId)
			.query(Integer.class)
			.single();
	}

	private String lastError(UUID eventId) {
		return jdbc.sql("select last_error from outbox_event where id = :id")
			.param("id", eventId)
			.query(String.class)
			.single();
	}

}
