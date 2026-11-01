package ar.rindetuplata;

import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * The containers every test configuration declares. Each start is retried: Docker publishes a container port on a
 * random ephemeral host port, which can collide with an ephemeral port the test JVM already holds for an outgoing
 * connection ("bind: address already in use"; frequent under rootless Docker, L20 of odd/tasks/post-mvp.md), and a
 * single failed start would otherwise fail every test of that context.
 */
public final class TestContainers {

	private static final int STARTUP_ATTEMPTS = 3;

	private TestContainers() {
	}

	public static PostgreSQLContainer postgres() {
		return new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine")).withStartupAttempts(STARTUP_ATTEMPTS);
	}

	public static RabbitMQContainer rabbitmq() {
		return new RabbitMQContainer(DockerImageName.parse("rabbitmq:4-management-alpine"))
			.withStartupAttempts(STARTUP_ATTEMPTS);
	}
}
