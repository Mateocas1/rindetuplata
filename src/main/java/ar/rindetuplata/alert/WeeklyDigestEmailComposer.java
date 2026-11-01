package ar.rindetuplata.alert;

import ar.rindetuplata.account.MailMessage;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * The email of a WEEKLY_DIGEST rule (T6c, L32 a): the top entries of the rule's filter in ranking order, each with
 * its TNA, real return, source and capture date, and a line when the top differs from the previous digest's. Plain
 * text, in Spanish.
 */
@Component
public class WeeklyDigestEmailComposer {

	static final String SUBJECT = "rindetuplata: tu resumen semanal";

	private final AlertMailProperties properties;

	public WeeklyDigestEmailComposer(AlertMailProperties properties) {
		this.properties = properties;
	}

	public MailMessage compose(WeeklyDigestEvent event) {
		List<WeeklyDigestEvent.Entry> entries = event.entries();
		String scope = AlertEmailText.scope(event.ruleKind());
		StringBuilder body = new StringBuilder("Hola,\n\n");
		body.append("Este es tu resumen de la semana del %s. ".formatted(event.weekStart()));
		if (entries.size() == 1) {
			body.append("La mejor opción %s:\n\n".formatted(scope));
		}
		else {
			body.append("Las %d mejores opciones %s, en orden de ranking:\n\n".formatted(entries.size(), scope));
		}
		for (int index = 0; index < entries.size(); index++) {
			body.append(line(index + 1, entries.get(index))).append('\n');
		}
		WeeklyDigestEvent.TopChange change = event.topChangedFrom();
		if (change != null) {
			body.append("\nLa mejor opción cambió desde tu resumen anterior (semana del %s): antes era %s, ahora es %s.\n"
				.formatted(change.weekStart(), change.name(), entries.get(0).name()));
		}
		body.append(AlertEmailText.footer(event.ruleId(), properties.manageUrl()));
		return new MailMessage(event.userEmail(), SUBJECT, body.toString());
	}

	private static String line(int position, WeeklyDigestEvent.Entry entry) {
		String real = entry.realReturn() == null ? "sin dato" : AlertEmailComposer.percent(entry.realReturn());
		return "%d. %s · %s %s · %s: %s · Fuente: %s · dato del %s".formatted(position, entry.name(),
				AlertEmailComposer.percent(entry.rate()), AlertEmailComposer.rateLabel(entry.kind()),
				AlertEmailText.REAL_RETURN_LABEL, real, entry.sourceDisplayName(), entry.captureDate());
	}

}
