package ar.rindetuplata.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.rindetuplata.api.ApiTestConfiguration;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * T6e (S6, L32 c): {@code POST /mcp} sits under the read-API rate limit, keyed like the REST API (the remote
 * address, one bucket shared with the GET endpoints). The clock is frozen, so no token refills during the test and
 * the single test method spends the bucket of 127.0.0.1 in a known order: capacity 3.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"rindetuplata.ingest.enabled=false",
		"rindetuplata.ingest.cron=0 0 0 1 1 *",
		"rindetuplata.outbox.relay-enabled=false",
		"rindetuplata.outbox.consumer-enabled=false",
		"rindetuplata.api.rate-limit.capacity=3",
		"rindetuplata.api.rate-limit.refill-per-second=0.5",
		"rindetuplata.api.rate-limit.bypass-token="
})
@Import(ApiTestConfiguration.class)
class McpRateLimitApiTest {

	private static final String INITIALIZE = """
			{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18",\
			"capabilities":{},"clientInfo":{"name":"raw-test","version":"1"}}}""";

	@LocalServerPort
	int port;

	private final HttpClient http = HttpClient.newHttpClient();

	@Test
	void theMcpEndpointSpendsTheBucketOfTheReadApiAndIsRefusedWithA429ProblemDetail() throws Exception {
		// Token 1: a POST to /mcp. Token 2: a GET of the REST ranking, the same address and the same bucket.
		assertThat(postInitialize().statusCode()).isEqualTo(200);
		assertThat(get("/api/v1/ranking").statusCode()).isEqualTo(200);

		// Token 3 is the initialize request of the SDK client; its notifications/initialized request is refused.
		HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport
				.builder("http://127.0.0.1:" + port).endpoint("/mcp").build();
		try (McpSyncClient client = McpClient.sync(transport).requestTimeout(Duration.ofSeconds(30)).build()) {
			assertThatThrownBy(client::initialize).hasStackTraceContaining("status code: 429");
		}

		HttpResponse<String> limited = postInitialize();
		assertThat(limited.statusCode()).isEqualTo(429);
		assertThat(limited.headers().firstValue("Retry-After")).hasValue("2");
		assertThat(limited.headers().firstValue("Content-Type")).hasValueSatisfying(
				type -> assertThat(type).startsWith("application/problem+json"));
		JsonNode body = new JsonMapper().readTree(limited.body());
		assertThat(body.get("type").asString()).isEqualTo("about:blank");
		assertThat(body.get("title").asString()).isEqualTo("Too Many Requests");
		assertThat(body.get("status").asInt()).isEqualTo(429);
		assertThat(body.get("detail").asString()).isEqualTo("Too many requests from this address; try again later");
		assertThat(body.get("instance").asString()).isEqualTo("/mcp");
		assertThat(get("/api/v1/instruments").statusCode()).as("the REST endpoints share the spent bucket")
				.isEqualTo(429);
	}

	private HttpResponse<String> postInitialize() throws Exception {
		return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/mcp"))
				.header("Content-Type", "application/json")
				.header("Accept", "application/json, text/event-stream")
				.POST(HttpRequest.BodyPublishers.ofString(INITIALIZE)).build(), HttpResponse.BodyHandlers.ofString());
	}

	private HttpResponse<String> get(String path) throws Exception {
		return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build(),
				HttpResponse.BodyHandlers.ofString());
	}
}
