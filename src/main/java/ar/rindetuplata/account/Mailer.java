package ar.rindetuplata.account;

/**
 * The outbound mail port. The magic link and the alert emails both use it, so it
 * stays free of Spring Mail types and tests can replace it with a recording fake.
 */
public interface Mailer {

	void send(MailMessage message);
}
