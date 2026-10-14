package ar.rindetuplata.store;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Testcontainers configuration for the {@code store} tests. The application-level
 * {@code ar.rindetuplata.TestcontainersConfiguration} is package private, so the store tests declare their own,
 * pinned to the same image.
 */
@TestConfiguration(proxyBeanMethods = false)
public class StoreTestConfiguration {

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));
	}
}
