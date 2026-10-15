package ar.rindetuplata.alert;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * T6d dead-letter rule (S4, S13): when the mail cannot be sent the message is retried three times and then
 * dead-lettered instead of looping forever, and it never counts as processed, so the operator still sees the event
 * in the dead-letter queue. The broker is real: the dead letter proves the queue's {@code x-dead-letter-*} wiring.
 */
@SpringBootTest(properties = {
		"rindetuplata.ingest.enabled=false",
		"rindetuplata.ingest.cron=0 0 0 1 1 *",
		"rindetuplata.outbox.relay-enabled=false",
		"rindetuplata.outbox.consumer-enabled=true",
		"spring.rabbitmq.listener.simple.retry.max-retries=3",
		"spring.rabbitmq.listener.simple.retry.initial-interval=100ms"
})
@Import({ FailingMailerTestConfiguration.class, DeliveryTestConfiguration.class })
class AlertEmailDeadLetterIntegrationTest {

	@Autowired
	FailingMailer mailer;

	@Autowired
	RabbitTemplate template;

	@Autowired
	JdbcClient jdbc;

	@BeforeEach
	void clean() {
		jdbc.sql("delete from processed_event").update();
		drain();
	}

	@Test
	void aFailedMailIsRetriedThreeTimesThenDeadLettered() {
		UUID eventId = UUID.randomUUID();

		template.convertAndSend(AlertEvents.EXCHANGE, AlertEvents.ROUTING_KEY,
				OutboxRelayIntegrationTest.payload(eventId, 12, "Wallet Dead Letter"));

		Message dead = template.receive(AlertEvents.DEAD_LETTER_QUEUE, 20000);
		assertThat(dead).as("the message ends in the dead-letter queue instead of looping").isNotNull();
		assertThat(new String(dead.getBody())).contains(eventId.toString());
		assertThat(mailer.attempts()).as("the initial delivery plus three retries").isEqualTo(4);
		assertThat(processedCount(eventId)).as("a failed mail is never recorded as processed").isZero();
	}

	private long processedCount(UUID eventId) {
		return jdbc.sql("select count(*) from processed_event where event_id = :id")
			.param("id", eventId)
			.query(Long.class)
			.single();
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
		try {
			for (int i = 0; i < 100 && template.receive(AlertEvents.DEAD_LETTER_QUEUE, 50) != null; i++) {
				// the dead-letter queue belongs to the operator, not to the tests
			}
		}
		catch (AmqpException e) {
			// the dead-letter queue may not be declared yet: nothing to drain
		}
	}

}
