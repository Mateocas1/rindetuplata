package ar.rindetuplata.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

import ar.rindetuplata.account.AccountTestConfiguration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * T11b relay rule (S4): the relay claims a row with a lease in a short transaction and publishes after it
 * committed, so no row lock is held while it waits for the broker. The publish is parked on a latch: meanwhile the
 * row carries a lease, a second relay run skips it, and another connection can lock it without waiting.
 */
@SpringBootTest(properties = {
		"rindetuplata.ingest.enabled=false",
		"rindetuplata.ingest.cron=0 0 0 1 1 *",
		"rindetuplata.outbox.relay-enabled=false",
		"rindetuplata.outbox.consumer-enabled=false"
})
@Import({ AccountTestConfiguration.class, DeliveryTestConfiguration.class })
class OutboxRelayLeaseIntegrationTest {

	@Autowired
	OutboxRelay relay;

	@MockitoSpyBean
	RabbitTemplate template;

	@Autowired
	JdbcClient jdbc;

	@BeforeEach
	void clean() throws InterruptedException {
		jdbc.sql("delete from outbox_event").update();
		awaitBroker();
	}

	/** The container can report started before the broker accepts connections: wait for the first one. */
	private void awaitBroker() throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
		while (true) {
			try {
				template.execute(channel -> null);
				return;
			}
			catch (AmqpException e) {
				if (System.nanoTime() > deadline) {
					throw e;
				}
				Thread.sleep(200);
			}
		}
	}

	@Test
	void noRowLockIsHeldWhileThePublishWaitsAndASecondRunSkipsTheLeasedRow() throws Exception {
		UUID eventId = UUID.randomUUID();
		jdbc.sql("""
				insert into outbox_event (id, event_type, aggregate_id, payload, created_at, attempts)
				values (:id, 'AlertTriggered', '7', cast(:payload as jsonb), :createdAt, 0)
				""")
			.param("id", eventId)
			.param("payload", OutboxRelayIntegrationTest.payload(eventId, 7, "Wallet Lease"))
			.param("createdAt", OffsetDateTime.ofInstant(Instant.parse("2026-10-14T12:00:00Z"), ZoneOffset.UTC))
			.update();
		CountDownLatch inPublish = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		doAnswer(invocation -> {
			inPublish.countDown();
			release.await(30, TimeUnit.SECONDS);
			return invocation.callRealMethod();
		}).when(template).convertAndSend(anyString(), anyString(), any(Object.class), any(MessagePostProcessor.class),
				any(CorrelationData.class));
		ExecutorService pool = Executors.newSingleThreadExecutor();
		try {
			Future<Integer> first = pool.submit(relay::relayPending);
			assertThat(inPublish.await(30, TimeUnit.SECONDS)).as("the first run reached the publish").isTrue();

			assertThat(lockedUntil(eventId)).as("the claim committed with a lease").isNotNull();
			assertThat(isLockable(eventId)).as("no row lock is held while the publish waits").isTrue();
			assertThat(relay.relayPending()).as("a second run skips the leased row").isZero();

			release.countDown();
			assertThat(first.get(30, TimeUnit.SECONDS)).as("the first run publishes it").isEqualTo(1);
		}
		finally {
			release.countDown();
			pool.shutdownNow();
		}
		assertThat(jdbc.sql("select published_at is not null from outbox_event where id = :id")
			.param("id", eventId)
			.query(Boolean.class)
			.single()).isTrue();
	}

	private OffsetDateTime lockedUntil(UUID eventId) {
		return jdbc.sql("select locked_until from outbox_event where id = :id")
			.param("id", eventId)
			.query((rs, rowNumber) -> rs.getObject("locked_until", OffsetDateTime.class))
			.list()
			.get(0);
	}

	/** True when another connection can take the row lock at once (NOWAIT would fail if a lock were held). */
	private boolean isLockable(UUID eventId) {
		return jdbc.sql("select id from outbox_event where id = :id for update nowait")
			.param("id", eventId)
			.query(UUID.class)
			.optional()
			.isPresent();
	}

}
