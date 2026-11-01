package ar.rindetuplata.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import io.modelcontextprotocol.client.McpSyncClient;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * T6e (S6, L32 c, finding F1 of the T6d+T6e verification): the transport refuses a request body over
 * {@link McpConfiguration#MAX_REQUEST_BYTES} instead of the SDK's default of 16 MiB, so one request cannot carry
 * millions of ids; a normal call is untouched.
 */
class McpBodyLimitApiTest extends McpIntegrationTest {

	private final HttpClient http = HttpClient.newHttpClient();

	@Test
	void aBodyOverTheLimitIsRefusedAndAnOrdinaryCallStillWorks() throws Exception {
		String padding = "x".repeat(McpConfiguration.MAX_REQUEST_BYTES);
		String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"get_ranking\","
				+ "\"arguments\":{\"limit\":1},\"padding\":\"" + padding + "\"}}";

		HttpResponse<String> refused = http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/mcp"))
				.header("Content-Type", "application/json").header("Accept", "application/json, text/event-stream")
				.POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());

		assertThat(refused.statusCode()).as("the SDK's answer to an oversized body is 413 Content Too Large")
				.isEqualTo(413);
		assertThat(refused.body()).doesNotContain("ranked");

		try (McpSyncClient client = client()) {
			client.initialize();
			assertThat(call(client, "get_ranking", Map.of("limit", 1)).isError()).isNotEqualTo(true);
		}
	}
}
