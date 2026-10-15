package ar.rindetuplata.alert;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * The database and a mail server that always fails. It is {@code AccountTestConfiguration} without its recording
 * mailer, because the dead-letter rule needs the failing port in its place: the primary {@link FailingMailer}
 * replaces the SMTP adapter, so the reply is deterministic and no test reaches a real server.
 */
@TestConfiguration(proxyBeanMethods = false)
public class FailingMailerTestConfiguration {

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));
	}

	@Bean
	@Primary
	FailingMailer failingMailer() {
		return new FailingMailer();
	}

}
