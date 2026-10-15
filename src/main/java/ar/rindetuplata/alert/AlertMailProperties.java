package ar.rindetuplata.alert;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code rindetuplata.alert.*}: where the alert email sends the saver to manage their rules (T6d, S13). It points at
 * the frontend page, like the magic link of T6a, and never at this API.
 */
@ConfigurationProperties("rindetuplata.alert")
public record AlertMailProperties(@DefaultValue("http://localhost:4321/alertas") String manageUrl) {
}
