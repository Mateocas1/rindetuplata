package ar.rindetuplata.account;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code rindetuplata.auth.*}: where the magic link points, how long a token lives, how many unexpired tokens one
 * email may hold (S13), which emails are admins, whether the session cookie is {@code Secure}, and how many
 * magic-link requests one remote address may make per window (T11a).
 */
@ConfigurationProperties("rindetuplata.auth")
public record AccountProperties(
		@DefaultValue("http://localhost:4321/ingresar") String magicLinkUrl,
		@DefaultValue("15m") Duration tokenTtl,
		@DefaultValue("3") int maxActiveTokens,
		@DefaultValue("") String adminEmails,
		@DefaultValue("false") boolean secureCookie,
		@DefaultValue("10") int magicLinkPerIpLimit,
		@DefaultValue("1h") Duration magicLinkPerIpWindow) {

	/** The configured admin emails, trimmed and lower-cased so they match a normalized stored email. */
	public Set<String> normalizedAdminEmails() {
		Set<String> admins = new LinkedHashSet<>();
		for (String email : adminEmails.split(",")) {
			String normalized = email.trim().toLowerCase(Locale.ROOT);
			if (!normalized.isEmpty()) {
				admins.add(normalized);
			}
		}
		return Set.copyOf(admins);
	}
}
