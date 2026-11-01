package ar.rindetuplata.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.rindetuplata.RindetuplataApplication;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.mock.env.MockEnvironment;

/**
 * L33b (S4, risk items 2 and 5): the {@code prod} profile refuses to start, naming every offending property, when a
 * value is still the local-development default or is insecure. Each rule is asserted alone (so a message names only
 * what is wrong), together, through relaxed environment-variable names, through a context that has the profile, and
 * through the real application, which must fail before it touches any database.
 */
class ProdProfileGuardTest {

	static final String SECURE_COOKIE = "rindetuplata.auth.secure-cookie";
	static final String ALLOWED_ORIGINS = "rindetuplata.web.allowed-origins";
	static final String MAGIC_LINK_URL = "rindetuplata.auth.magic-link-url";
	static final String MANAGE_URL = "rindetuplata.alert.manage-url";
	static final String MAIL_HOST = "spring.mail.host";
	static final String ADMIN_EMAILS = "rindetuplata.auth.admin-emails";
	static final String BYPASS_TOKEN = "rindetuplata.api.rate-limit.bypass-token";

	/** 32 characters: the shortest bypass token the guard accepts. */
	static final String TOKEN_32 = "0123456789abcdef0123456789abcdef";

	static Map<String, String> goodProperties() {
		Map<String, String> properties = new LinkedHashMap<>();
		properties.put(SECURE_COOKIE, "true");
		properties.put(ALLOWED_ORIGINS, "https://rindetuplata.ar");
		properties.put(MAGIC_LINK_URL, "https://rindetuplata.ar/ingresar");
		properties.put(MANAGE_URL, "https://rindetuplata.ar/alertas");
		properties.put(MAIL_HOST, "smtp.example.com");
		properties.put(ADMIN_EMAILS, "admin@example.com");
		properties.put(BYPASS_TOKEN, TOKEN_32);
		return properties;
	}

	static MockEnvironment environment(Map<String, String> properties) {
		MockEnvironment environment = new MockEnvironment();
		properties.forEach(environment::setProperty);
		return environment;
	}

	static Map<String, String> with(String key, String value) {
		Map<String, String> properties = goodProperties();
		properties.put(key, value);
		return properties;
	}

	@Test
	void aProductionShapedConfigurationHasNoViolation() {
		assertThat(ProdProfileGuard.violations(environment(goodProperties()))).isEmpty();
	}

	@Test
	void aBlankBypassTokenMeansTheBypassIsOffAndIsAccepted() {
		assertThat(ProdProfileGuard.violations(environment(with(BYPASS_TOKEN, "")))).isEmpty();
		assertThat(ProdProfileGuard.violations(environment(with(BYPASS_TOKEN, "   ")))).isEmpty();
	}

	static List<Arguments> singleViolations() {
		return List.of(
				Arguments.of(SECURE_COOKIE, "false"),
				Arguments.of(ALLOWED_ORIGINS, "http://localhost:4321"),
				Arguments.of(ALLOWED_ORIGINS, "http://rindetuplata.ar"),
				Arguments.of(ALLOWED_ORIGINS, "https://rindetuplata.ar,http://localhost:4321"),
				Arguments.of(ALLOWED_ORIGINS, "https://localhost:4321"),
				Arguments.of(ALLOWED_ORIGINS, "https://127.0.0.1"),
				Arguments.of(ALLOWED_ORIGINS, "https://[::1]:4321"),
				Arguments.of(ALLOWED_ORIGINS, ""),
				Arguments.of(MAGIC_LINK_URL, "http://localhost:4321/ingresar"),
				Arguments.of(MAGIC_LINK_URL, "http://rindetuplata.ar/ingresar"),
				Arguments.of(MAGIC_LINK_URL, "HTTP://rindetuplata.ar/ingresar"),
				Arguments.of(MAGIC_LINK_URL, "not a url"),
				Arguments.of(MANAGE_URL, "http://localhost:4321/alertas"),
				Arguments.of(MANAGE_URL, "http://rindetuplata.ar/alertas"),
				Arguments.of(MANAGE_URL, "https://app.localhost/alertas"),
				Arguments.of(MAIL_HOST, "localhost"),
				Arguments.of(MAIL_HOST, "LOCALHOST"),
				Arguments.of(MAIL_HOST, "127.0.0.1"),
				Arguments.of(MAIL_HOST, "::1"),
				Arguments.of(MAIL_HOST, ""),
				Arguments.of(ADMIN_EMAILS, ""),
				Arguments.of(ADMIN_EMAILS, "  , ,"),
				Arguments.of(BYPASS_TOKEN, "short"),
				Arguments.of(BYPASS_TOKEN, TOKEN_32.substring(1)));
	}

	@ParameterizedTest(name = "{0}={1}")
	@MethodSource("singleViolations")
	void eachRuleNamesExactlyTheOffendingProperty(String key, String value) {
		List<String> violations = ProdProfileGuard.violations(environment(with(key, value)));

		assertThat(violations).hasSize(1);
		assertThat(violations.get(0)).startsWith(key + " ");
	}

	@Test
	void everyOffendingPropertyIsNamedInOneFailure() {
		Map<String, String> bad = new LinkedHashMap<>();
		bad.put(SECURE_COOKIE, "false");
		bad.put(ALLOWED_ORIGINS, "http://localhost:4321");
		bad.put(MAGIC_LINK_URL, "http://localhost:4321/ingresar");
		bad.put(MANAGE_URL, "http://localhost:4321/alertas");
		bad.put(MAIL_HOST, "localhost");
		bad.put(ADMIN_EMAILS, "");
		bad.put(BYPASS_TOKEN, "short-secret-token");

		assertThat(ProdProfileGuard.violations(environment(bad))).hasSize(7);
		assertThatThrownBy(() -> ProdProfileGuard.check(environment(bad)))
				.isInstanceOf(IllegalStateException.class)
				.satisfies(failure -> {
					String message = failure.getMessage();
					assertThat(message).startsWith("Refusing to start with the 'prod' profile: 7 ");
					bad.keySet().forEach(property -> assertThat(message).contains(property + " "));
					// A secret never reaches the log, not even a rejected one.
					assertThat(message).doesNotContain("short-secret-token");
				});
	}

	@Test
	void theBypassTokenIsMeasuredAfterTrimmingBecauseTheApiTrimsIt() {
		assertThat(ProdProfileGuard.violations(environment(with(BYPASS_TOKEN, " " + TOKEN_32 + " ")))).isEmpty();
		assertThat(ProdProfileGuard.violations(environment(with(BYPASS_TOKEN, " " + TOKEN_32.substring(1) + " "))))
				.hasSize(1);
	}

	@Test
	void anEmptyEnvironmentFailsOnTheDevelopmentDefaults() {
		List<String> violations = ProdProfileGuard.violations(new MockEnvironment());

		// No admin, no cookie flag, localhost origins and links, no mail host: the local defaults must not pass.
		assertThat(violations).extracting(violation -> violation.substring(0, violation.indexOf(' ')))
				.containsExactly(SECURE_COOKIE, ALLOWED_ORIGINS, MAGIC_LINK_URL, MANAGE_URL, MAIL_HOST, ADMIN_EMAILS);
	}

	@Test
	void valuesFromEnvironmentVariablesBindThroughRelaxedNames() {
		StandardEnvironment environment = new StandardEnvironment();
		environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
		environment.getPropertySources().addLast(new SystemEnvironmentPropertySource(
				StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
				Map.of("RINDETUPLATA_AUTH_SECURECOOKIE", "false",
						"RINDETUPLATA_WEB_ALLOWEDORIGINS", "http://localhost:4321",
						"RINDETUPLATA_AUTH_MAGICLINKURL", "https://rindetuplata.ar/ingresar",
						"RINDETUPLATA_ALERT_MANAGEURL", "https://rindetuplata.ar/alertas",
						"SPRING_MAIL_HOST", "localhost",
						"RINDETUPLATA_AUTH_ADMINEMAILS", "",
						"RINDETUPLATA_API_RATELIMIT_BYPASSTOKEN", "tiny")));

		assertThat(ProdProfileGuard.violations(environment)).extracting(violation -> violation.substring(0, violation.indexOf(' ')))
				.containsExactly(SECURE_COOKIE, ALLOWED_ORIGINS, MAIL_HOST, ADMIN_EMAILS, BYPASS_TOKEN);
	}

	@Test
	void aMapPropertySourceFromAnotherLayerWinsOverTheDefaultsLikeAnyBinding() {
		StandardEnvironment environment = new StandardEnvironment();
		environment.getPropertySources().addFirst(new MapPropertySource("test", new LinkedHashMap<String, Object>(goodProperties())));

		assertThat(ProdProfileGuard.violations(environment)).isEmpty();
	}

	@Test
	void aContextWithTheProdProfileRefusesToStartAndNamesEveryProperty() {
		new ApplicationContextRunner()
				.withUserConfiguration(ProdProfileConfiguration.class)
				.withInitializer(context -> context.getEnvironment().setActiveProfiles("prod"))
				.run(context -> {
					assertThat(context).hasFailed();
					String message = NestedExceptionUtils.getMostSpecificCause(context.getStartupFailure()).getMessage();
					List.of(SECURE_COOKIE, ALLOWED_ORIGINS, MAGIC_LINK_URL, MANAGE_URL, MAIL_HOST, ADMIN_EMAILS)
							.forEach(property -> assertThat(message).contains(property + " "));
					assertThat(message).doesNotContain(BYPASS_TOKEN);
				});
	}

	@Test
	void aContextWithTheProdProfileStartsWhenEverythingIsProductionShaped() {
		new ApplicationContextRunner()
				.withUserConfiguration(ProdProfileConfiguration.class)
				.withInitializer(context -> context.getEnvironment().setActiveProfiles("prod"))
				.withPropertyValues(goodProperties().entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue())
						.toArray(String[]::new))
				.run(context -> assertThat(context).hasNotFailed());
	}

	@Test
	void withoutTheProdProfileTheDevelopmentDefaultsStillStart() {
		new ApplicationContextRunner()
				.withUserConfiguration(ProdProfileConfiguration.class)
				.run(context -> {
					assertThat(context).hasNotFailed();
					assertThat(context).doesNotHaveBean(ProdProfileGuard.class);
				});
	}

	@Test
	void theRealApplicationRefusesToStartUnderProdBeforeAnyDatabaseIsTouched() {
		// No datasource is configured: reaching Flyway or JPA would fail with a different message, so a message that
		// lists the properties proves the guard ran first.
		assertThatThrownBy(() -> new SpringApplicationBuilder(RindetuplataApplication.class)
				.web(WebApplicationType.NONE)
				.profiles("prod")
				.properties("rindetuplata.ingest.enabled=false", "spring.main.banner-mode=off")
				.run())
				.satisfies(failure -> {
					String message = NestedExceptionUtils.getMostSpecificCause(failure).getMessage();
					assertThat(message).startsWith("Refusing to start with the 'prod' profile: ");
					List.of(ALLOWED_ORIGINS, MAGIC_LINK_URL, MANAGE_URL, MAIL_HOST, ADMIN_EMAILS)
							.forEach(property -> assertThat(message).contains(property + " "));
				});
	}

	@Test
	void theProdProfileFileTurnsTheSecureCookieOnSoItIsNeverAnOptIn() {
		// Everything is production-shaped except the cookie, which is left to application-prod.properties.
		Map<String, String> properties = goodProperties();
		properties.remove(SECURE_COOKIE);
		properties.remove(ADMIN_EMAILS);
		String[] arguments = properties.entrySet().stream().map(entry -> "--" + entry.getKey() + "=" + entry.getValue())
				.toArray(String[]::new);

		assertThatThrownBy(() -> new SpringApplicationBuilder(RindetuplataApplication.class)
				.web(WebApplicationType.NONE)
				.profiles("prod")
				.properties("rindetuplata.ingest.enabled=false", "spring.main.banner-mode=off")
				.run(arguments))
				.satisfies(failure -> {
					String message = NestedExceptionUtils.getMostSpecificCause(failure).getMessage();
					assertThat(message).startsWith("Refusing to start with the 'prod' profile: 1 ");
					assertThat(message).contains(ADMIN_EMAILS + " ").doesNotContain(SECURE_COOKIE);
				});
	}
}
