package ar.rindetuplata.account;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * The test double of the {@link Mailer} port: it records every message instead of talking SMTP, so a test can
 * assert the recipient, the configured magic link and the token it carries.
 */
public class RecordingMailer implements Mailer {

	private final List<MailMessage> sent = new CopyOnWriteArrayList<>();

	private volatile Consumer<MailMessage> onSend = message -> {
	};

	@Override
	public void send(MailMessage message) {
		onSend.accept(message);
		sent.add(message);
	}

	/** A probe run on the sending thread just before a message is recorded; {@link #clear()} removes it. */
	public void onSend(Consumer<MailMessage> probe) {
		this.onSend = probe;
	}

	public List<MailMessage> sent() {
		return List.copyOf(sent);
	}

	public MailMessage last() {
		return sent.isEmpty() ? null : sent.get(sent.size() - 1);
	}

	public void clear() {
		onSend = message -> {
		};
		sent.clear();
	}
}
