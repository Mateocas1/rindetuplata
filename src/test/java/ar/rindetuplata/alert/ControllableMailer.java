package ar.rindetuplata.alert;

import ar.rindetuplata.account.MailMessage;
import ar.rindetuplata.account.RecordingMailer;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.mail.MailSendException;

/**
 * A recording mailer a test can slow down (so concurrent deliveries overlap inside {@code send}) or make fail a
 * given number of times (so a retry after a rollback is observable).
 */
public class ControllableMailer extends RecordingMailer {

	private volatile long delayMillis;

	private final AtomicInteger failures = new AtomicInteger();

	public void delay(long millis) {
		this.delayMillis = millis;
	}

	public void failNext(int times) {
		failures.set(times);
	}

	public void reset() {
		delayMillis = 0;
		failures.set(0);
		clear();
	}

	@Override
	public void send(MailMessage message) {
		if (delayMillis > 0) {
			try {
				Thread.sleep(delayMillis);
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
		if (failures.getAndUpdate(n -> n > 0 ? n - 1 : 0) > 0) {
			throw new MailSendException("SMTP is down");
		}
		super.send(message);
	}

}
