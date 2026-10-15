package ar.rindetuplata.alert;

import static org.assertj.core.api.Assertions.assertThat;

import ar.rindetuplata.account.AccountTestConfiguration;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * T11b relay rule (S4): after {@code rindetuplata.outbox.max-attempts} failed publishes a row is no longer claimed
 * and is logged at ERROR exactly once; it stays in the table for the operator. The broker is unreachable on purpose
 * (a closed port), so every run is a failure; a failure also releases the lease so the next run may retry at once.
 */
@SpringBootTest(properties = {
		"rindetuplata.ingest.enabled=false",
		"rindetuplata.ingest.cron=0 0 0 1 1 *",
		"rindetuplata.outbox.relay-enabled=false",
		"rindetuplata.outbox.consumer-enabled=false",
		"rindetuplata.outbox.max-attempts=2",
		"spring.rabbitmq.host=127.0.0.1",
		"spring.rabbitmq.port=1"
})
@Import(AccountTestConfiguration.class)
class OutboxRelayAttemptCapIntegrationTest {

	@Autowired
	OutboxRelay relay;

	@Autowired
	JdbcClient jdbc;

	private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

	private Logger relayLogger;

	@BeforeEach
	void clean() {
		jdbc.sql("delete from outbox_event").update();
		relayLogger = (Logger) LoggerFactory.getLogger(OutboxRelay.class);
		appender.start();
		relayLogger.addAppender(appender);
	}

	@AfterEach
	void detach() {
		relayLogger.detachAppender(appender);
	}

	@Test
	void aRowThatReachesTheCapIsLoggedAtErrorOnceAndNeverClaimedAgain() {
		UUID eventId = UUID.randomUUID();
		jdbc.sql("""
				insert into outbox_event (id, event_type, aggregate_id, payload, created_at, attempts)
				values (:id, 'AlertTriggered', '7', cast(:payload as jsonb), :createdAt, 0)
				""")
			.param("id", eventId)
			.param("payload", OutboxRelayIntegrationTest.payload(eventId, 7, "Wallet Cap"))
			.param("createdAt", OffsetDateTime.ofInstant(Instant.parse("2026-10-14T12:00:00Z"), ZoneOffset.UTC))
			.update();

		relay.relayPending();
		assertThat(attempts(eventId)).isEqualTo(1);
		assertThat(lockedUntilIsNull(eventId)).as("a failure releases the lease").isTrue();
		assertThat(errorLogs()).as("not exhausted yet").isZero();

		relay.relayPending();
		assertThat(attempts(eventId)).as("the second failure reaches the cap").isEqualTo(2);
		assertThat(errorLogs()).as("the exhaustion is logged at ERROR once").isEqualTo(1);

		relay.relayPending();
		relay.relayPending();
		assertThat(attempts(eventId)).as("the exhausted row is no longer claimed").isEqualTo(2);
		assertThat(errorLogs()).as("and not logged again").isEqualTo(1);
		assertThat(jdbc.sql("select count(*) from outbox_event where id = :id and published_at is null")
			.param("id", eventId)
			.query(Long.class)
			.single()).as("the row stays for the operator").isEqualTo(1L);
	}

	private long errorLogs() {
		return appender.list.stream().filter(event -> event.getLevel() == Level.ERROR).count();
	}

	private int attempts(UUID eventId) {
		return jdbc.sql("select attempts from outbox_event where id = :id")
			.param("id", eventId)
			.query(Integer.class)
			.single();
	}

	private boolean lockedUntilIsNull(UUID eventId) {
		return jdbc.sql("select locked_until is null from outbox_event where id = :id")
			.param("id", eventId)
			.query(Boolean.class)
			.single();
	}

}
