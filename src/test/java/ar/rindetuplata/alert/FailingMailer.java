package ar.rindetuplata.alert;

import ar.rindetuplata.account.MailMessage;
import ar.rindetuplata.account.Mailer;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.mail.MailSendException;

/**
 * The test double of a mail server that is down: it counts every delivery attempt and always throws, so the
 * retry-then-dead-letter path is observable without a real SMTP failure.
 */
public class FailingMailer implements Mailer {

	private final AtomicInteger attempts = new AtomicInteger();

	@Override
	public void send(MailMessage message) {
		attempts.incrementAndGet();
		throw new MailSendException("SMTP is down");
	}

	public int attempts() {
		return attempts.get();
	}

}
