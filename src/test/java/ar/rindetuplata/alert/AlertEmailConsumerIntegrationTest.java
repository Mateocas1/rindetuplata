package ar.rindetuplata.alert;

import static org.assertj.core.api.Assertions.assertThat;

import ar.rindetuplata.account.AccountTestConfiguration;
import ar.rindetuplata.account.MailMessage;
import ar.rindetuplata.account.RecordingMailer;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * T6d consumer rules (S13, S8 L-G): the alert email explains what fired and where the number came from, and the
 * same event delivered twice sends exactly one email and leaves exactly one {@code processed_event} row, because
 * the relay is at least once and a duplicated delivery must never spam the saver.
 */
@SpringBootTest(properties = {
		"rindetuplata.ingest.enabled=false",
		"rindetuplata.ingest.cron=0 0 0 1 1 *",
		"rindetuplata.outbox.relay-enabled=false",
		"rindetuplata.outbox.consumer-enabled=true",
		"rindetuplata.alert.manage-url=https://app.rindetuplata.ar/alertas"
})
@Import({ AccountTestConfiguration.class, DeliveryTestConfiguration.class })
class AlertEmailConsumerIntegrationTest {

	static final String MANAGE_URL = "https://app.rindetuplata.ar/alertas";

	@Autowired
	RecordingMailer mailer;

	@Autowired
	RabbitTemplate template;

	@Autowired
	JdbcClient jdbc;

	@BeforeEach
	void clean() {
		mailer.clear();
		jdbc.sql("delete from processed_event").update();
		drain();
	}

	@Test
	void theConsumerSendsOneEmailWithTheEventFacts() {
		UUID eventId = UUID.randomUUID();

		deliver(OutboxRelayIntegrationTest.payload(eventId, 12, "Wallet Consumer"));

		MailMessage mail = awaitMail();
		assertThat(mail.to()).as("the event names its recipient").isEqualTo("saver@example.com");
		assertThat(mail.subject()).contains("rindetuplata");
		assertThat(mail.body())
			.as("rule, instrument, both percentages, the data date, the source, the link and the disclaimer")
			.contains("Regla #12")
			.contains("Wallet Consumer")
			.contains("30%")
			.contains("25%")
			.contains("2026-10-14")
			.contains("test-source")
			.contains(MANAGE_URL)
			.contains("no da asesoramiento financiero");
		assertThat(processedCount(eventId)).as("the delivery is recorded").isEqualTo(1);
	}

	@Test
	void theSameEventDeliveredTwiceSendsOneEmailAndLeavesOneProcessedRow() {
		UUID eventId = UUID.randomUUID();
		String payload = OutboxRelayIntegrationTest.payload(eventId, 12, "Wallet Consumer");

		deliver(payload);
		awaitMail();
		deliver(payload);
		awaitQueueEmpty();

		assertThat(mailer.sent()).as("the second delivery is recognized and sends nothing").hasSize(1);
		assertThat(processedCount(eventId)).isEqualTo(1);
	}

	private void deliver(String payload) {
		template.convertAndSend(AlertEvents.EXCHANGE, AlertEvents.ROUTING_KEY, payload);
	}

	private MailMessage awaitMail() {
		long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
		while (System.nanoTime() < deadline) {
			MailMessage last = mailer.last();
			if (last != null) {
				return last;
			}
			sleep(50);
		}
		throw new AssertionError("no alert email arrived within 15 s");
	}

	/** Waits until the queue has nothing left to deliver, plus a grace for the last acknowledgement. */
	private void awaitQueueEmpty() {
		long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
		while (System.nanoTime() < deadline) {
			if (readyMessages() == 0) {
				sleep(300);
				if (readyMessages() == 0) {
					return;
				}
			}
			sleep(50);
		}
		throw new AssertionError("the alert queue did not drain within 10 s");
	}

	private int readyMessages() {
		return template.execute(channel -> channel.queueDeclarePassive(AlertEvents.QUEUE).getMessageCount());
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
	}

	private static void sleep(long millis) {
		try {
			Thread.sleep(millis);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

}
