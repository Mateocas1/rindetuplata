package ar.rindetuplata.alert;

import static org.assertj.core.api.Assertions.assertThat;

import ar.rindetuplata.account.AccountTestConfiguration;
import ar.rindetuplata.account.MailMessage;
import ar.rindetuplata.account.RecordingMailer;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
 * T6d end to end (S4, S13): one row in the transactional outbox becomes one email in the saver's inbox, through
 * the real relay, the real broker and the real consumer. This is the rule that proves the three pieces fit.
 */
@SpringBootTest(properties = {
		"rindetuplata.ingest.enabled=false",
		"rindetuplata.ingest.cron=0 0 0 1 1 *",
		"rindetuplata.outbox.relay-enabled=false",
		"rindetuplata.outbox.consumer-enabled=true",
		"rindetuplata.alert.manage-url=https://app.rindetuplata.ar/alertas"
})
@Import({ AccountTestConfiguration.class, DeliveryTestConfiguration.class })
class AlertDeliveryEndToEndTest {

	@Autowired
	OutboxRelay relay;

	@Autowired
	RecordingMailer mailer;

	@Autowired
	RabbitTemplate template;

	@Autowired
	JdbcClient jdbc;

	@BeforeEach
	void clean() {
		mailer.clear();
		jdbc.sql("delete from outbox_event").update();
		jdbc.sql("delete from processed_event").update();
		try {
			for (int i = 0; i < 100 && template.receive(AlertEvents.QUEUE, 50) != null; i++) {
				// keep draining whatever a previous test left behind
			}
		}
		catch (AmqpException e) {
			// the queue may not be declared yet (a red test before the topology exists): nothing to drain
		}
	}

	@Test
	void anOutboxRowBecomesOneEmailForTheSaver() {
		UUID eventId = UUID.randomUUID();
		jdbc.sql("""
				insert into outbox_event (id, event_type, aggregate_id, payload, created_at, attempts)
				values (:id, 'AlertTriggered', '7', cast(:payload as jsonb), :createdAt, 0)
				""")
			.param("id", eventId)
			.param("payload", OutboxRelayIntegrationTest.payload(eventId, 12, "Wallet End To End"))
			.param("createdAt", OffsetDateTime.ofInstant(Instant.parse("2026-10-14T12:00:00Z"), ZoneOffset.UTC))
			.update();

		int published = relay.relayPending();

		assertThat(published).isEqualTo(1);
		MailMessage mail = awaitMail();
		assertThat(mail.to()).isEqualTo("saver@example.com");
		assertThat(mail.body()).contains("Wallet End To End").contains("test-source");
		assertThat(jdbc.sql("select count(*) from processed_event where event_id = :id")
			.param("id", eventId)
			.query(Long.class)
			.single()).isEqualTo(1);
	}

	private MailMessage awaitMail() {
		long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
		while (System.nanoTime() < deadline) {
			MailMessage last = mailer.last();
			if (last != null) {
				return last;
			}
			try {
				Thread.sleep(50);
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
		throw new AssertionError("the outbox row never became an email");
	}

}
