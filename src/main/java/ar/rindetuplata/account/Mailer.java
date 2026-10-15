package ar.rindetuplata.account;

/**
 * The outbound mail port. T6a uses it for the magic link (S13); T6d adds alert emails to the same port, so it
 * stays free of Spring Mail types and tests can replace it with a recording fake.
 */
public interface Mailer {

	void send(MailMessage message);
}
