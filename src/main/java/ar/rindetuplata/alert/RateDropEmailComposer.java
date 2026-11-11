package ar.rindetuplata.alert;

import ar.rindetuplata.account.MailMessage;
import org.springframework.stereotype.Component;

/**
 * The email of a RATE_DROP rule: which plazo fijo or wallet lowered its rate, from what to what, when
 * the previous rate was seen, and the data date and source behind the new one. Plain text, in Spanish.
 */
@Component
public class RateDropEmailComposer {

	static final String SUBJECT = "rindetuplata: bajó una tasa que seguís";

	private final AlertMailProperties properties;

	public RateDropEmailComposer(AlertMailProperties properties) {
		this.properties = properties;
	}

	public MailMessage compose(RateDroppedEvent event) {
		String body = """
				Hola,

				Bajó la tasa de %s: pasó de %s a %s %s.
				La tasa anterior la vimos el %s.

				Datos del %s · Fuente: %s
				""".formatted(event.instrumentName(), AlertEmailComposer.percent(event.previousRate()),
				AlertEmailComposer.percent(event.rate()), AlertEmailComposer.rateLabel(event.kind()),
				event.previousObservedOn(), event.dataDate(), event.sourceDisplayName())
				+ AlertEmailText.footer(event.ruleId(), properties.manageUrl());
		return new MailMessage(event.userEmail(), SUBJECT, body);
	}

}
