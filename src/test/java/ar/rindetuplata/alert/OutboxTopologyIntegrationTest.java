package ar.rindetuplata.alert;

import static org.assertj.core.api.Assertions.assertThat;

import ar.rindetuplata.account.AccountTestConfiguration;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.testcontainers.rabbitmq.RabbitMQContainer;

/**
 * T6d topology (S4): the alert events need a topic exchange, a durable email queue bound to the routing key, and a
 * dead-letter exchange with its own queue wired to the first one, because a message the consumer rejects must land
 * somewhere instead of looping. The management API is read to prove the {@code x-dead-letter-*} arguments are the
 * ones the design names, and a publish/receive pair proves the bindings work.
 */
@SpringBootTest(properties = {
		"rindetuplata.ingest.enabled=false",
		"rindetuplata.ingest.cron=0 0 0 1 1 *",
		"rindetuplata.outbox.relay-enabled=false",
		"rindetuplata.outbox.consumer-enabled=false"
})
@Import({ AccountTestConfiguration.class, DeliveryTestConfiguration.class })
class OutboxTopologyIntegrationTest {

	static final String EXCHANGE = "rindetuplata.events";

	static final String ROUTING_KEY = "alert.triggered";

	static final String QUEUE = "rindetuplata.alert-email";

	static final String DLX = "rindetuplata.events.dlx";

	static final String DLQ_ROUTING_KEY = "alert.triggered.dlq";

	static final String DLQ = "rindetuplata.alert-email.dlq";

	private static final String VHOST = "/";

	@Autowired
	AmqpAdmin admin;

	@Autowired
	RabbitTemplate template;

	@Autowired
	RabbitMQContainer rabbitmq;

	@Test
	void theExchangeTheQueueAndTheDeadLetterPairAreDeclared() {
		admin.initialize();

		assertThat(body("exchanges", VHOST, EXCHANGE))
			.as("a durable topic exchange carries the alert events")
			.contains("\"type\":\"topic\"")
			.contains("\"durable\":true");
		assertThat(body("queues", VHOST, QUEUE))
			.as("the email queue dead-letters to the design's exchange and routing key")
			.contains("\"x-dead-letter-exchange\":\"" + DLX + "\"")
			.contains("\"x-dead-letter-routing-key\":\"" + DLQ_ROUTING_KEY + "\"");
		assertThat(body("bindings", VHOST, "e", EXCHANGE, "q", QUEUE))
			.as("the queue is bound to the alert routing key")
			.contains("\"routing_key\":\"" + ROUTING_KEY + "\"");
		assertThat(body("exchanges", VHOST, DLX))
			.as("the dead-letter exchange exists")
			.contains("\"durable\":true");
		assertThat(body("queues", VHOST, DLQ))
			.as("the dead-letter queue exists")
			.isNotBlank();
		assertThat(body("bindings", VHOST, "e", DLX, "q", DLQ))
			.as("the dead-letter queue is bound to the dead-letter routing key")
			.contains("\"routing_key\":\"" + DLQ_ROUTING_KEY + "\"");
	}

	@Test
	void theBindingsRouteAPublishedMessageToTheQueueAndADeadLetterToOneOfItsOwn() {
		admin.initialize();

		template.convertAndSend(EXCHANGE, ROUTING_KEY, "alert.triggered payload");
		Message delivered = template.receive(QUEUE, 5000);
		assertThat(delivered).as("the event reaches the email queue").isNotNull();
		assertThat(new String(delivered.getBody())).isEqualTo("alert.triggered payload");

		template.convertAndSend(DLX, DLQ_ROUTING_KEY, "dead letter");
		Message dead = template.receive(DLQ, 5000);
		assertThat(dead).as("a dead letter reaches the dead-letter queue").isNotNull();
		assertThat(new String(dead.getBody())).isEqualTo("dead letter");
	}

	private RestClient management() {
		return RestClient.builder()
			.baseUrl(rabbitmq.getHttpUrl())
			.defaultHeaders(headers -> headers.setBasicAuth(rabbitmq.getAdminUsername(), rabbitmq.getAdminPassword()))
			.build();
	}

	/**
	 * Reads one management path, each argument an already-encoded path segment (the vhost is {@code /}, encoded by
	 * the path segment itself), retrying a few seconds because a declaration the broker just accepted may lag.
	 */
	private String body(String... segments) {
		long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
		RuntimeException last = null;
		while (System.nanoTime() < deadline) {
			try {
				return management().get()
					.uri(builder -> builder.path("/api").pathSegment(segments).build())
					.retrieve()
					.body(String.class);
			}
			catch (RestClientResponseException e) {
				last = e;
				sleep();
			}
		}
		throw new IllegalStateException("no management answer for /api/" + String.join("/", segments), last);
	}

	private static void sleep() {
		try {
			Thread.sleep(100);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

}
