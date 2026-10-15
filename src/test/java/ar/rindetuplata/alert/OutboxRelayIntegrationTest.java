package ar.rindetuplata.alert;

import static org.assertj.core.api.Assertions.assertThat;

import ar.rindetuplata.account.AccountTestConfiguration;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * T6d relay rules (S4): an unpublished outbox row is published and marked as published, and one run claims at most
 * fifty rows, oldest first, so a backlog drains in order and never starves the day's newest events. The consumer is
 * off here, so the published messages stay in the queue and can be read back.
 */
@SpringBootTest(properties = {
		"rindetuplata.ingest.enabled=false",
		"rindetuplata.ingest.cron=0 0 0 1 1 *",
		"rindetuplata.outbox.relay-enabled=false",
		"rindetuplata.outbox.consumer-enabled=false"
})
@Import({ AccountTestConfiguration.class, DeliveryTestConfiguration.class })
class OutboxRelayIntegrationTest {

	static final String AGGREGATE = "7";

	@Autowired
	OutboxRelay relay;

	@Autowired
	RabbitTemplate template;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	Clock clock;

	@Autowired
	RabbitAdmin admin;

	@Autowired
	Binding alertEmailBinding;

	@BeforeEach
	void clean() {
		jdbc.sql("delete from outbox_event").update();
		drain();
	}

	@Test
	void anUnpublishedRowIsPublishedAndMarkedAsPublished() {
		UUID eventId = insert(payload(UUID.randomUUID(), 7, "Wallet Relay"), Instant.parse("2026-10-14T12:00:00Z"));

		int published = relay.relayPending();

		assertThat(published).as("one row, one publish").isEqualTo(1);
		assertThat(publishedAt(eventId)).as("the row is marked so the next run skips it").isNotNull();
		Message message = template.receive(AlertEvents.QUEUE, 5000);
		assertThat(message).as("the event reached the email queue").isNotNull();
		assertThat(body(message)).contains(eventId.toString());
	}

	@Test
	void anAlreadyPublishedRowIsNeverPublishedAgain() {
		UUID eventId = insert(payload(UUID.randomUUID(), 7, "Wallet Relay"), Instant.parse("2026-10-14T12:00:00Z"));
		relay.relayPending();
		template.receive(AlertEvents.QUEUE, 5000);

		int secondRun = relay.relayPending();

		assertThat(secondRun).as("nothing pending is left").isZero();
		assertThat(template.receive(AlertEvents.QUEUE, 300)).as("no second copy is published").isNull();
		assertThat(publishedAt(eventId)).isNotNull();
	}

	@Test
	void oneRunClaimsAtMostFiftyRowsOldestFirst() {
		Instant base = Instant.parse("2026-10-14T00:00:00Z");
		List<UUID> events = new ArrayList<>();
		for (int i = 0; i < 60; i++) {
			events.add(insert(payload(UUID.randomUUID(), 7, "Wallet " + i), base.plusSeconds(i)));
		}

		int published = relay.relayPending();

		assertThat(published).as("the batch is capped at 50").isEqualTo(50);
		for (int i = 0; i < 50; i++) {
			assertThat(publishedAt(events.get(i))).as("event %d is inside the oldest-50 batch", i).isNotNull();
		}
		for (int i = 50; i < 60; i++) {
			assertThat(publishedAt(events.get(i))).as("event %d stays for the next run", i).isNull();
		}
	}

	@Test
	void aRowThatReachedTheAttemptCapIsNeverClaimed() {
		UUID exhausted = insert(payload(UUID.randomUUID(), 7, "Wallet Exhausted"), Instant.parse("2026-10-14T12:00:00Z"),
				10, null);
		UUID due = insert(payload(UUID.randomUUID(), 7, "Wallet Due"), Instant.parse("2026-10-14T12:00:01Z"), 9, null);

		int published = relay.relayPending();

		assertThat(published).as("only the row under the cap goes out").isEqualTo(1);
		assertThat(publishedAt(exhausted)).as("the exhausted row stays in the table for the operator").isNull();
		assertThat(attempts(exhausted)).as("and is not attempted again").isEqualTo(10);
		assertThat(publishedAt(due)).isNotNull();
		assertThat(template.receive(AlertEvents.QUEUE, 5000)).as("the due event reached the queue").isNotNull();
		assertThat(template.receive(AlertEvents.QUEUE, 300)).as("the exhausted one did not").isNull();
	}

	@Test
	void aLeasedRowIsSkippedUntilItsLeaseExpires() {
		Instant now = clock.instant();
		UUID leased = insert(payload(UUID.randomUUID(), 7, "Wallet Leased"), Instant.parse("2026-10-14T12:00:00Z"), 0,
				now.plusSeconds(3600));
		UUID expired = insert(payload(UUID.randomUUID(), 7, "Wallet Expired"), Instant.parse("2026-10-14T12:00:01Z"), 0,
				now.minusSeconds(1));

		int published = relay.relayPending();

		assertThat(published).as("the expired lease is claimable, the live one is not").isEqualTo(1);
		assertThat(publishedAt(leased)).isNull();
		assertThat(publishedAt(expired)).isNotNull();
	}

	@Test
	void aMessageTheBrokerCannotRouteCountsAsAFailure() {
		UUID eventId = insert(payload(UUID.randomUUID(), 7, "Wallet Unroutable"), Instant.parse("2026-10-14T12:00:00Z"));
		admin.removeBinding(alertEmailBinding);
		try {
			int published = relay.relayPending();

			assertThat(published).as("the broker acks an unroutable message, the return makes it a failure").isZero();
			assertThat(publishedAt(eventId)).as("the row is still pending").isNull();
			assertThat(attempts(eventId)).isEqualTo(1);
			assertThat(lastError(eventId)).as("the reason says it was returned").containsIgnoringCase("return");
		}
		finally {
			admin.declareBinding(alertEmailBinding);
		}
	}

	@Test
	void aPublishedRowKeepsZeroAttemptsAndNoError() {
		UUID eventId = insert(payload(UUID.randomUUID(), 7, "Wallet Lease"), Instant.parse("2026-10-14T12:00:00Z"));

		relay.relayPending();

		assertThat(publishedAt(eventId)).isNotNull();
		assertThat(attempts(eventId)).as("a published row did not fail").isZero();
		assertThat(lastError(eventId)).isNull();
	}

	static String payload(UUID eventId, long ruleId, String instrumentName) {
		return """
				{"eventId":"%s","ruleId":%d,"userEmail":"saver@example.com","instrumentId":"42",\
				"instrumentName":"%s","kind":"WALLET","rate":0.30,"threshold":0.25,\
				"captureDate":"2026-10-14","dataDate":"2026-10-14","sourceDisplayName":"test-source"}"""
			.formatted(eventId, ruleId, instrumentName);
	}

	private UUID insert(String payload, Instant createdAt) {
		return insert(payload, createdAt, 0, null);
	}

	private UUID insert(String payload, Instant createdAt, int attempts, Instant lockedUntil) {
		UUID eventId = UUID.fromString(match(payload, "eventId"));
		jdbc.sql("""
				insert into outbox_event (id, event_type, aggregate_id, payload, created_at, attempts, locked_until)
				values (:id, 'AlertTriggered', :aggregate, cast(:payload as jsonb), :createdAt, :attempts, :lockedUntil)
				""")
			.param("attempts", attempts)
			.param("lockedUntil", lockedUntil == null ? null : OffsetDateTime.ofInstant(lockedUntil, ZoneOffset.UTC))
			.param("id", eventId)
			.param("aggregate", AGGREGATE)
			.param("payload", payload)
			.param("createdAt", OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC))
			.update();
		return eventId;
	}

	private static String match(String payload, String field) {
		String marker = "\"" + field + "\":\"";
		int start = payload.indexOf(marker) + marker.length();
		return payload.substring(start, payload.indexOf('"', start));
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
			.query((rs, rowNumber) -> rs.getString("last_error"))
			.list()
			.get(0);
	}

	private static String body(Message message) {
		return new String(message.getBody());
	}

	private void drain() {
		try {
			for (int i = 0; i < 100 && template.receive(AlertEvents.QUEUE, 50) != null; i++) {
				// keep draining whatever a previous test left behind
			}
		}
		catch (AmqpException e) {
			// the queue may not be declared yet (a red test before the topology exists): nothing to drain
		}
	}

}
