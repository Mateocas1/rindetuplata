package ar.rindetuplata.account;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** {@code rindetuplata.mail.*}: the sender the SMTP adapter puts on every message (T6a). */
@ConfigurationProperties("rindetuplata.mail")
public record MailProperties(@DefaultValue("rindetuplata <no-reply@rindetuplata.ar>") String from) {
}
