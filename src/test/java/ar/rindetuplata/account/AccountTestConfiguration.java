package ar.rindetuplata.account;

import ar.rindetuplata.TestContainers;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Testcontainers, a movable clock and a recording mailer for the {@code account} tests. The primary {@link Mailer}
 * replaces the SMTP adapter, so no test reaches a real mail server; the primary {@link MutableClock} replaces the
 * production clock, so token expiry is assertable.
 */
@TestConfiguration(proxyBeanMethods = false)
public class AccountTestConfiguration {

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return TestContainers.postgres();
	}

	@Bean
	@Primary
	MutableClock accountClock() {
		return new MutableClock();
	}

	@Bean
	@Primary
	MutableAccountRoles mutableAccountRoles(AccountProperties properties) {
		return new MutableAccountRoles(properties);
	}

	@Bean
	@Primary
	RecordingMailer recordingMailer() {
		return new RecordingMailer();
	}
}
