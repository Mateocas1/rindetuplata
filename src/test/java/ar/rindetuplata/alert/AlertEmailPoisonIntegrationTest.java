package ar.rindetuplata.alert;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * T11b poison rule (S4): an unreadable body lands in the dead-letter queue on its first delivery, with no retries
 * and no mail. The retry interval is five seconds, so three retries would take at least fifteen: the message must
 * be in the DLQ well before the first retry would even start.
 */
@SpringBootTest(properties = {
		"rindetuplata.ingest.enabled=false",
		"rindetuplata.ingest.cron=0 0 0 1 1 *",
		"rindetuplata.outbox.relay-enabled=false",
		"rindetuplata.outbox.consumer-enabled=true",
		"spring.rabbitmq.listener.simple.retry.max-retries=3",
		"spring.rabbitmq.listener.simple.retry.initial-interval=5s"
})
@Import({ ControllableMailerTestConfiguration.class, DeliveryTestConfiguration.class })
class AlertEmailPoisonIntegrationTest {

	@Autowired
	ControllableMailer mailer;

	@Autowired
	RabbitTemplate template;

	@BeforeEach
	void clean() {
		mailer.reset();
		drain(AlertEvents.QUEUE);
		drain(AlertEvents.DEAD_LETTER_QUEUE);
	}

	@Test
	void anUnreadableBodyIsDeadLetteredOnTheFirstDeliveryWithoutRetriesOrMail() {
		long begin = System.nanoTime();

		template.convertAndSend(AlertEvents.EXCHANGE, AlertEvents.ROUTING_KEY, "this is not an alert event");

		Message dead = template.receive(AlertEvents.DEAD_LETTER_QUEUE, 30000);
		Duration elapsed = Duration.ofNanos(System.nanoTime() - begin);
		assertThat(dead).as("the poison message ends in the dead-letter queue").isNotNull();
		assertThat(new String(dead.getBody())).isEqualTo("this is not an alert event");
		assertThat(elapsed).as("no retry interval was waited").isLessThan(Duration.ofSeconds(4));
		assertThat(mailer.sent()).as("no mail for a poison message").isEmpty();
	}

	private void drain(String queue) {
		try {
			for (int i = 0; i < 100 && template.receive(queue, 50) != null; i++) {
				// whatever a previous test left behind
			}
		}
		catch (AmqpException e) {
			// the queue may not be declared yet: nothing to drain
		}
	}

}
