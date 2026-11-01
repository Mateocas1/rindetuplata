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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * T6d: the alert email consumer (S4, S13). T6c (L32 a) adds the rule types: the payload's {@code type} field picks the
 * event and its composer ({@code BEST_CHANGED}, {@code RATE_DROP}, {@code WEEKLY_DIGEST}), and a payload with no
 * type is the {@link AlertTriggered} every outbox row held before them, delivered as it always was. The event id is the dedupe key and T11b makes the dedupe atomic with the
 * send: the {@code processed_event} row is inserted ({@code on conflict do nothing}) in the same transaction as the
 * mail. An insert that changes no row is a duplicate and sends nothing; a concurrent redelivery waits on the row lock
 * of the first one and then sees the duplicate; a mail failure is thrown out of the listener on purpose, which rolls
 * the row back so the container's retry advice retries it and finally dead-letters it instead of looping. The window
 * left is a commit failure after the mail was sent, which resends once on redelivery.
 *
 * <p>An unreadable payload, or one whose type is unknown, can never succeed: it is rejected without requeue on its
 * first delivery, so it lands in the dead-letter queue with no retries and no mail.
 *
 * <p>The listener starts unless {@code rindetuplata.outbox.consumer-enabled=false}, which is what a test without a
 * broker sets so no container ever tries to connect.
 */
@Component
public class AlertEmailConsumer {

	private static final Logger log = LoggerFactory.getLogger(AlertEmailConsumer.class);

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	private final Mailer mailer;

	private final AlertEmailComposer triggered;

	private final BestChangedEmailComposer bestChanged;

	private final RateDropEmailComposer rateDrop;

	private final WeeklyDigestEmailComposer digest;

	private final ProcessedEventRepository processed;

	private final Clock clock;

	public AlertEmailConsumer(Mailer mailer, AlertEmailComposer triggered, BestChangedEmailComposer bestChanged,
			RateDropEmailComposer rateDrop, WeeklyDigestEmailComposer digest, ProcessedEventRepository processed,
			Clock clock) {
		this.mailer = mailer;
		this.triggered = triggered;
		this.bestChanged = bestChanged;
		this.rateDrop = rateDrop;
		this.digest = digest;
		this.processed = processed;
		this.clock = clock;
	}

	@RabbitListener(queues = AlertEvents.QUEUE, autoStartup = "${rindetuplata.outbox.consumer-enabled:true}")
	@Transactional
	public void onAlertTriggered(Message message) {
		AlertEvent event = read(message);
		if (!processed.markProcessed(event.eventId(), clock.instant())) {
			log.info("alert event {} was already delivered: no second email", event.eventId());
			return;
		}
		mailer.send(switch (event) {
			case AlertTriggered thresholdEvent -> triggered.compose(thresholdEvent);
			case BestChangedEvent bestChangedEvent -> bestChanged.compose(bestChangedEvent);
			case RateDroppedEvent rateDroppedEvent -> rateDrop.compose(rateDroppedEvent);
			case WeeklyDigestEvent weeklyDigestEvent -> digest.compose(weeklyDigestEvent);
		});
	}

	/** The event the payload holds: by its {@code type}, or today's {@link AlertTriggered} when it has none. */
	private static AlertEvent read(Message message) {
		String body = new String(message.getBody(), StandardCharsets.UTF_8);
		try {
			JsonNode type = MAPPER.readTree(body).get("type");
			if (type == null || type.isNull()) {
				return MAPPER.readValue(body, AlertTriggered.class);
			}
			return switch (AlertType.parse(type.asString()).orElseThrow(() -> new AmqpRejectAndDontRequeueException(
					"the alert event type '" + type.asString() + "' is unknown"))) {
				case THRESHOLD -> MAPPER.readValue(body, AlertTriggered.class);
				case BEST_CHANGED -> MAPPER.readValue(body, BestChangedEvent.class);
				case RATE_DROP -> MAPPER.readValue(body, RateDroppedEvent.class);
				case WEEKLY_DIGEST -> MAPPER.readValue(body, WeeklyDigestEvent.class);
			};
		}
		catch (JacksonException e) {
			throw new AmqpRejectAndDontRequeueException("the alert event payload cannot be read", e);
		}
	}

}
