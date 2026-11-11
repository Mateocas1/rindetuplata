package ar.rindetuplata.alert;

import ar.rindetuplata.account.MailMessage;
import org.springframework.stereotype.Component;

/**
 * The email of a BEST_CHANGED rule: the new best option of the rule's filter with its rate, the one it
 * replaced and when the data last showed it, and the data date and source behind the number. Plain text, in Spanish.
 */
@Component
public class BestChangedEmailComposer {

	static final String SUBJECT = "rindetuplata: cambió la mejor opción";

	private final AlertMailProperties properties;

	public BestChangedEmailComposer(AlertMailProperties properties) {
		this.properties = properties;
	}

	public MailMessage compose(BestChangedEvent event) {
		StringBuilder body = new StringBuilder("Hola,\n\n");
		body.append("Cambió la mejor opción %s: ahora es %s, con %s %s.\n".formatted(
				AlertEmailText.scope(event.ruleKind()), event.instrumentName(),
				AlertEmailComposer.percent(event.rate()), AlertEmailComposer.rateLabel(event.kind())));
		body.append("Antes era %s (según los datos del %s).\n".formatted(event.previousName(),
				event.previousObservedOn()));
		if (event.realReturn() != null) {
			body.append("Rendimiento real a 30 días: %s.\n".formatted(AlertEmailComposer.percent(event.realReturn())));
		}
		body.append("\nDatos del %s · Fuente: %s\n".formatted(event.dataDate(), event.sourceDisplayName()));
		body.append(AlertEmailText.footer(event.ruleId(), properties.manageUrl()));
		return new MailMessage(event.userEmail(), SUBJECT, body.toString());
	}

}
