package ar.rindetuplata.alert;

import ar.rindetuplata.TestContainers;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** The database and a {@link ControllableMailer} as the primary mail port, for the consumer's transaction rules. */
@TestConfiguration(proxyBeanMethods = false)
public class ControllableMailerTestConfiguration {

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return TestContainers.postgres();
	}

	@Bean
	@Primary
	ControllableMailer controllableMailer() {
		return new ControllableMailer();
	}

}
