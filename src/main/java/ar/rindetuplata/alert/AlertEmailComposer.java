package ar.rindetuplata.alert;

import ar.rindetuplata.account.MailMessage;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * Composes the alert email (T6d, S13): which rule fired, which instrument crossed it, the rate and the threshold in
 * percent, the data date and the source behind the number, the link to manage the alerts and the same informational
 * disclaimer as the rest of the product (S8 L-G). Plain text and in Spanish, like the magic link of T6a.
 */
@Component
public class AlertEmailComposer {

	static final String SUBJECT = "rindetuplata: se disparó tu alerta de rendimiento";

	private final AlertMailProperties properties;

	public AlertEmailComposer(AlertMailProperties properties) {
		this.properties = properties;
	}

	public MailMessage compose(AlertTriggered event) {
		String body = """
				Hola,

				Se disparó tu alerta: %s está rindiendo %s TNA, por encima de tu umbral de %s.

				Datos del %s · Fuente: %s

				Regla #%d. Podés ver el detalle y administrar tus alertas acá:
				%s

				rindetuplata compara rendimientos con datos públicos y no da asesoramiento financiero.
				""".formatted(event.instrumentName(), percent(event.rate()), percent(event.threshold()),
				event.dataDate(), event.sourceDisplayName(), event.ruleId(), properties.manageUrl());
		return new MailMessage(event.userEmail(), SUBJECT, body);
	}

	/** A TNA fraction as the percent a saver reads: {@code 0.30} becomes {@code 30%}, {@code 0.2875} becomes {@code 28.75%}. */
	static String percent(BigDecimal fraction) {
		return fraction.multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString() + "%";
	}

}
