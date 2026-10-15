package ar.rindetuplata.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * T11b consumer rules (S4): the {@code processed_event} row is inserted in one transaction with the send. A
 * concurrent redelivery of one event waits on the row lock and then sees the duplicate (one mail, one row); a mail
 * failure rolls the row back so the retry sends it; a sequential duplicate sends nothing; an unreadable payload is
 * rejected without requeue. The listener is called directly (no broker), with real threads against Postgres.
 */
@SpringBootTest(properties = {
		"rindetuplata.ingest.enabled=false",
		"rindetuplata.ingest.cron=0 0 0 1 1 *",
		"rindetuplata.outbox.relay-enabled=false",
		"rindetuplata.outbox.consumer-enabled=false"
})
@Import(ControllableMailerTestConfiguration.class)
class AlertEmailConsumerTransactionTest {

	@Autowired
	AlertEmailConsumer consumer;

	@Autowired
	ControllableMailer mailer;

	@Autowired
	JdbcClient jdbc;

	@BeforeEach
	void clean() {
		mailer.reset();
		jdbc.sql("delete from processed_event").update();
	}

	@Test
	void concurrentRedeliveriesOfOneEventSendOneMailAndLeaveOneRow() throws Exception {
		UUID eventId = UUID.randomUUID();
		Message message = message(OutboxRelayIntegrationTest.payload(eventId, 12, "Wallet Race"));
		mailer.delay(400);
		int threads = 4;
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		try {
			List<Future<?>> deliveries = new ArrayList<>();
			for (int i = 0; i < threads; i++) {
				deliveries.add(pool.submit(() -> {
					start.await();
					consumer.onAlertTriggered(message);
					return null;
				}));
			}
			start.countDown();
			for (Future<?> delivery : deliveries) {
				delivery.get(60, TimeUnit.SECONDS);
			}
		}
		finally {
			pool.shutdownNow();
		}

		assertThat(mailer.sent()).as("one email for one event").hasSize(1);
		assertThat(mailer.last().to()).isEqualTo("saver@example.com");
		assertThat(processedCount(eventId)).as("one processed row").isEqualTo(1);
	}

	@Test
	void aMailFailureRollsTheRowBackSoTheRetrySendsTheEmail() {
		UUID eventId = UUID.randomUUID();
		Message message = message(OutboxRelayIntegrationTest.payload(eventId, 12, "Wallet Retry"));
		mailer.failNext(1);

		assertThatThrownBy(() -> consumer.onAlertTriggered(message)).as("the failure reaches the retry advice")
			.isInstanceOf(RuntimeException.class);
		assertThat(processedCount(eventId)).as("a failed mail leaves no row").isZero();
		assertThat(mailer.sent()).isEmpty();

		consumer.onAlertTriggered(message);

		assertThat(mailer.sent()).as("the retry sends the email").hasSize(1);
		assertThat(processedCount(eventId)).isEqualTo(1);
	}

	@Test
	void aSequentialDuplicateSendsNothing() {
		UUID eventId = UUID.randomUUID();
		Message message = message(OutboxRelayIntegrationTest.payload(eventId, 12, "Wallet Twice"));

		consumer.onAlertTriggered(message);
		consumer.onAlertTriggered(message);

		assertThat(mailer.sent()).hasSize(1);
		assertThat(processedCount(eventId)).isEqualTo(1);
	}

	@Test
	void anUnreadablePayloadIsRejectedWithoutRequeueAndSendsNothing() {
		Message poison = message("this is not an alert event");

		assertThatThrownBy(() -> consumer.onAlertTriggered(poison)).isInstanceOf(AmqpRejectAndDontRequeueException.class);
		assertThat(mailer.sent()).isEmpty();
		assertThat(jdbc.sql("select count(*) from processed_event").query(Long.class).single()).isZero();
	}

	private static Message message(String body) {
		return MessageBuilder.withBody(body.getBytes(StandardCharsets.UTF_8)).build();
	}

	private long processedCount(UUID eventId) {
		return jdbc.sql("select count(*) from processed_event where event_id = :id")
			.param("id", eventId)
			.query(Long.class)
			.single();
	}

}
