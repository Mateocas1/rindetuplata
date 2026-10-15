package ar.rindetuplata.alert;

import ar.rindetuplata.account.Mailer;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * T6d: the alert email consumer (S4, S13). The event id is the dedupe key: a row in {@code processed_event} means
 * that email already went out, so a redelivery (the relay is at least once) sends nothing. The row is written only
 * after the mail port accepted the message, and a mail failure is thrown out of the listener on purpose, so the
 * container's retry advice retries it and finally dead-letters it instead of looping.
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
	public void onAlertTriggered(Message message) {
		AlertTriggered event = read(message);
		if (processed.isProcessed(event.eventId())) {
			log.info("alert event {} was already delivered: no second email", event.eventId());
			return;
		}
		mailer.send(composer.compose(event));
		processed.markProcessed(event.eventId(), clock.instant());
	}

	private static AlertTriggered read(Message message) {
		try {
			return MAPPER.readValue(new String(message.getBody(), StandardCharsets.UTF_8), AlertTriggered.class);
		}
		catch (JacksonException e) {
			throw new IllegalArgumentException("the alert event payload cannot be read", e);
		}
	}

}
