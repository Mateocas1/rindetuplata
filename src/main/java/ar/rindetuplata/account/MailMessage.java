package ar.rindetuplata.account;

/** One plain-text message: recipient, subject and body. Plain text keeps the MVP free of HTML templating. */
public record MailMessage(String to, String subject, String body) {
}
