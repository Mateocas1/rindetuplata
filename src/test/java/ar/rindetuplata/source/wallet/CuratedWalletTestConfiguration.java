package ar.rindetuplata.source.wallet;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Testcontainers configuration for the curated wallet adapter tests: the V4 table is a real Postgres table, so the
 * adapter is exercised against the migration's seed and not against a classpath file.
 */
@TestConfiguration(proxyBeanMethods = false)
public class CuratedWalletTestConfiguration {

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));
	}
}
