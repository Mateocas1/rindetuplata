package ar.rindetuplata.account;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The test double of the {@link Mailer} port: it records every message instead of talking SMTP, so a test can
 * assert the recipient, the configured magic link and the token it carries.
 */
public class RecordingMailer implements Mailer {

	private final List<MailMessage> sent = new CopyOnWriteArrayList<>();

	@Override
	public void send(MailMessage message) {
		sent.add(message);
	}

	public List<MailMessage> sent() {
		return List.copyOf(sent);
	}

	public MailMessage last() {
		return sent.isEmpty() ? null : sent.get(sent.size() - 1);
	}

	public void clear() {
		sent.clear();
	}
}
