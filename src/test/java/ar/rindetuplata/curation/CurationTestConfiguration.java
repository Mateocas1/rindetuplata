package ar.rindetuplata.curation;

import ar.rindetuplata.account.RecordingMailer;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Testcontainers, a frozen clock and a recording mailer for the {@code curation} tests: "today" is always
 * 2026-10-20 in America/Argentina/Buenos_Aires, so the verification age the admin API reports is assertable, and
 * the magic-link token never leaves the test. The primary clock replaces the production one, which never runs here.
 */
@TestConfiguration(proxyBeanMethods = false)
public class CurationTestConfiguration {

	public static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

	/** 2026-10-20T09:00 in America/Argentina/Buenos_Aires (UTC-3). */
	public static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));
	}

	@Bean
	@Primary
	Clock fixedCurationClock() {
		return Clock.fixed(NOW, ZONE);
	}

	@Bean
	@Primary
	RecordingMailer recordingMailer() {
		return new RecordingMailer();
	}
}
