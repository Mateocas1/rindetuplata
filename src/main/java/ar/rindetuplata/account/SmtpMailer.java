package ar.rindetuplata.account;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * The SMTP adapter of the {@link Mailer} port, over Boot's auto-configured {@link JavaMailSender}. Local
 * development points it at Mailpit (T6d adds it to Compose); tests replace the port with a recording fake.
 */
@Component
public class SmtpMailer implements Mailer {

	private final JavaMailSender sender;

	private final MailProperties properties;

	public SmtpMailer(JavaMailSender sender, MailProperties properties) {
		this.sender = sender;
		this.properties = properties;
	}

	@Override
	public void send(MailMessage message) {
		SimpleMailMessage mail = new SimpleMailMessage();
		mail.setFrom(properties.from());
		mail.setTo(message.to());
		mail.setSubject(message.subject());
		mail.setText(message.body());
		sender.send(mail);
	}
}
