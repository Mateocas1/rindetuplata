package ar.rindetuplata.alert;

import ar.rindetuplata.account.Mailer;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * T6d: the alert email consumer (S4, S13). The event id is the dedupe key and T11b makes the dedupe atomic with the
 * send: the {@code processed_event} row is inserted ({@code on conflict do nothing}) in the same transaction as the
 * mail. An insert that changes no row is a duplicate and sends nothing; a concurrent redelivery waits on the row lock
 * of the first one and then sees the duplicate; a mail failure is thrown out of the listener on purpose, which rolls
 * the row back so the container's retry advice retries it and finally dead-letters it instead of looping. The window
 * left is a commit failure after the mail was sent, which resends once on redelivery.
 *
 * <p>An unreadable payload can never succeed: it is rejected without requeue on its first delivery, so it lands in
 * the dead-letter queue with no retries and no mail.
 *
 * <p>The listener starts unless {@code rindetuplata.outbox.consumer-enabled=false}, which is what a test without a
 * broker sets so no container ever tries to connect.
 */
@Component
public class AlertEmailConsumer {

	private static final Logger log = LoggerFactory.getLogger(AlertEmailConsumer.class);

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	private final Mailer mailer;

	private final AlertEmailComposer composer;

	private final ProcessedEventRepository processed;

	private final Clock clock;

	public AlertEmailConsumer(Mailer mailer, AlertEmailComposer composer, ProcessedEventRepository processed,
			Clock clock) {
		this.mailer = mailer;
		this.composer = composer;
		this.processed = processed;
		this.clock = clock;
	}

	@RabbitListener(queues = AlertEvents.QUEUE, autoStartup = "${rindetuplata.outbox.consumer-enabled:true}")
	@Transactional
	public void onAlertTriggered(Message message) {
		AlertTriggered event = read(message);
		if (!processed.markProcessed(event.eventId(), clock.instant())) {
			log.info("alert event {} was already delivered: no second email", event.eventId());
			return;
		}
		mailer.send(composer.compose(event));
	}

	private static AlertTriggered read(Message message) {
		try {
			return MAPPER.readValue(new String(message.getBody(), StandardCharsets.UTF_8), AlertTriggered.class);
		}
		catch (JacksonException e) {
			throw new AmqpRejectAndDontRequeueException("the alert event payload cannot be read", e);
		}
	}

}
