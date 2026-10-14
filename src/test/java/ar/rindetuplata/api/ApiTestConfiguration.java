package ar.rindetuplata.api;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Testcontainers and a frozen clock for the {@code api} tests: "today" is always 2026-10-20 in
 * America/Argentina/Buenos_Aires, so the default history window is assertable. The primary clock replaces the
 * production one, which never runs in these tests.
 */
@TestConfiguration(proxyBeanMethods = false)
public class ApiTestConfiguration {

	public static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

	/** 2026-10-20T09:00 in America/Argentina/Buenos_Aires (UTC-3). */
	public static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");
	public static final LocalDate TODAY = LocalDate.parse("2026-10-20");

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));
	}

	@Bean
	@Primary
	Clock fixedApiClock() {
		return Clock.fixed(NOW, ZONE);
	}
}
