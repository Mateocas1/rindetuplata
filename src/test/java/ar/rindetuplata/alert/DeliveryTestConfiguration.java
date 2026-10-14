package ar.rindetuplata.alert;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * The T6d broker: a real RabbitMQ with the management plugin, so the topology, the publisher confirms and the
 * dead-lettering are observed where they actually happen. The container is exported as a bean as well, because the
 * topology assertions read the management HTTP API.
 */
@TestConfiguration(proxyBeanMethods = false)
public class DeliveryTestConfiguration {

	@Bean
	@ServiceConnection
	RabbitMQContainer rabbitMqContainer() {
		return new RabbitMQContainer(DockerImageName.parse("rabbitmq:4-management-alpine"));
	}

}
