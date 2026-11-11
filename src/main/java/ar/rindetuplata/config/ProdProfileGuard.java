package ar.rindetuplata.config;

import ar.rindetuplata.account.AccountProperties;
import ar.rindetuplata.account.WebProperties;
import ar.rindetuplata.alert.AlertMailProperties;
import ar.rindetuplata.api.ratelimit.ApiRateLimitProperties;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.env.Environment;

/**
 * The startup guard of the {@code prod} profile: the application refuses to start, naming every offending
 * property at once, when a value is still a local-development default or is insecure on a public host. The checks
 * read the same properties the application binds (relaxed names included, so {@code RINDETUPLATA_AUTH_SECURECOOKIE}
 * counts), and a rejected secret is never printed.
 *
 * <ul>
 * <li>{@code rindetuplata.auth.secure-cookie} is {@code false}: the session and CSRF cookies would travel in clear;
 * <li>{@code rindetuplata.web.allowed-origins}, {@code rindetuplata.auth.magic-link-url} or
 * {@code rindetuplata.alert.manage-url} is blank, not {@code https}, or points at a local host
 * ({@code localhost}, {@code *.localhost}, a {@code 127.x.x.x} address, {@code ::1}, {@code 0.0.0.0});
 * <li>{@code spring.mail.host} is blank or a local host (the Mailpit default);
 * <li>{@code rindetuplata.auth.admin-emails} names nobody (no one could reach the admin pages);
 * <li>{@code rindetuplata.api.rate-limit.bypass-token} is set but, once trimmed as the API does, is shorter than 32
 * characters (blank is fine: the bypass is off).
 * </ul>
 */
public final class ProdProfileGuard implements BeanFactoryPostProcessor, PriorityOrdered {

	static final int MIN_BYPASS_TOKEN_LENGTH = 32;

	private final Environment environment;

	ProdProfileGuard(Environment environment) {
		this.environment = environment;
	}

	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
		check(environment);
	}

	@Override
	public int getOrder() {
		return HIGHEST_PRECEDENCE;
	}

	/** Throws an {@link IllegalStateException} listing every violation of {@code environment}, if there is one. */
	public static void check(Environment environment) {
		List<String> violations = violations(environment);
		if (!violations.isEmpty()) {
			throw new IllegalStateException("Refusing to start with the 'prod' profile: " + violations.size()
					+ " offending setting(s):\n - " + String.join("\n - ", violations));
		}
	}

	/** Each violation starts with the property name, followed by a space and the reason. */
	public static List<String> violations(Environment environment) {
		Binder binder = Binder.get(environment);
		AccountProperties account = binder.bindOrCreate("rindetuplata.auth", AccountProperties.class);
		WebProperties web = binder.bindOrCreate("rindetuplata.web", WebProperties.class);
		AlertMailProperties alert = binder.bindOrCreate("rindetuplata.alert", AlertMailProperties.class);
		ApiRateLimitProperties rateLimit = binder.bindOrCreate("rindetuplata.api.rate-limit", ApiRateLimitProperties.class);
		String mailHost = binder.bind("spring.mail.host", String.class).orElse("");

		List<String> violations = new ArrayList<>();
		if (!account.secureCookie()) {
			violations.add("rindetuplata.auth.secure-cookie is false: the session and CSRF cookies would travel without"
					+ " the Secure attribute (set it to true)");
		}
		originsViolation(web.allowedOrigins()).ifPresent(
				reason -> violations.add("rindetuplata.web.allowed-origins " + reason));
		urlViolation(account.magicLinkUrl()).ifPresent(
				reason -> violations.add("rindetuplata.auth.magic-link-url " + reason));
		urlViolation(alert.manageUrl()).ifPresent(
				reason -> violations.add("rindetuplata.alert.manage-url " + reason));
		if (mailHost.isBlank()) {
			violations.add("spring.mail.host is not set: no alert or magic-link email could be sent");
		}
		else if (isLocalHost(mailHost.strip())) {
			violations.add("spring.mail.host is " + mailHost.strip() + ": the local Mailpit default, not a real SMTP server");
		}
		if (account.normalizedAdminEmails().isEmpty()) {
			violations.add("rindetuplata.auth.admin-emails is blank: nobody could reach the admin endpoints");
		}
		String token = rateLimit.bypassToken().strip();
		if (!token.isEmpty() && token.length() < MIN_BYPASS_TOKEN_LENGTH) {
			violations.add("rindetuplata.api.rate-limit.bypass-token is " + token.length() + " characters long: a set"
					+ " token needs at least " + MIN_BYPASS_TOKEN_LENGTH + " (or leave it blank to disable the bypass)");
		}
		return List.copyOf(violations);
	}

	private static Optional<String> originsViolation(String origins) {
		List<String> entries = Arrays.stream(origins.split(",")).map(String::strip)
				.filter(entry -> !entry.isEmpty()).toList();
		if (entries.isEmpty()) {
			return Optional.of("is blank: the site could not call the API with credentials");
		}
		return entries.stream().map(ProdProfileGuard::urlViolation).flatMap(Optional::stream).findFirst();
	}

	/** Why {@code value} is not an https URL on a public host, or empty when it is one. */
	private static Optional<String> urlViolation(String value) {
		String trimmed = value.strip();
		if (trimmed.isEmpty()) {
			return Optional.of("is blank");
		}
		URI uri;
		try {
			uri = new URI(trimmed);
		}
		catch (URISyntaxException e) {
			return Optional.of("is not a valid URL: " + trimmed);
		}
		if (uri.getScheme() == null || !uri.getScheme().equalsIgnoreCase("https")) {
			return Optional.of("is " + trimmed + ": it must be an https URL");
		}
		if (uri.getHost() == null) {
			return Optional.of("is not a valid URL: " + trimmed);
		}
		if (isLocalHost(uri.getHost())) {
			return Optional.of("is " + trimmed + ": it points at this machine, not at the public site");
		}
		return Optional.empty();
	}

	static boolean isLocalHost(String host) {
		String normalized = host.strip().toLowerCase(Locale.ROOT);
		if (normalized.startsWith("[") && normalized.endsWith("]")) {
			normalized = normalized.substring(1, normalized.length() - 1);
		}
		return normalized.equals("localhost") || normalized.endsWith(".localhost") || normalized.startsWith("127.")
				|| normalized.equals("::1") || normalized.equals("0:0:0:0:0:0:0:1") || normalized.equals("0.0.0.0");
	}
}
