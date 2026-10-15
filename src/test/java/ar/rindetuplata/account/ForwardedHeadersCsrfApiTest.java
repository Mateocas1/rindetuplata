package ar.rindetuplata.account;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

/**
 * T7a deployment rule (S16): Caddy terminates TLS on the VPS, so the API must trust the private-network proxy and
 * read {@code X-Forwarded-Proto} ({@code server.forward-headers-strategy=native}); otherwise
 * {@code request.isSecure()} stays false and the {@code XSRF-TOKEN} cookie is not {@code Secure}. Real HTTP from
 * localhost, because the trusted-proxy decision depends on the connecting address.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"rindetuplata.ingest.enabled=false",
		"rindetuplata.ingest.cron=0 0 0 1 1 *",
		"rindetuplata.auth.secure-cookie=false",
		"rindetuplata.outbox.relay-enabled=false",
		"rindetuplata.outbox.consumer-enabled=false"
})
@Import(AccountTestConfiguration.class)
class ForwardedHeadersCsrfApiTest {

	@LocalServerPort
	int port;

	private final HttpClient client = HttpClient.newHttpClient();

	@Test
	void aForwardedHttpsRequestMarksTheCsrfCookieSecure() throws Exception {
		String setCookie = csrfSetCookie("https");

		assertThat(setCookie).as("the proxy's scheme is trusted, so the cookie is Secure")
			.contains("Secure");
	}

	@Test
	void aPlainRequestKeepsTheCsrfCookieUnsecuredAndSameSiteLax() throws Exception {
		String setCookie = csrfSetCookie(null);

		assertThat(setCookie).as("with no TLS the local cookie must not be Secure")
			.doesNotContain("Secure")
			.contains("Path=/")
			.contains("SameSite=Lax")
			.doesNotContain("HttpOnly");
	}

	private String csrfSetCookie(String forwardedProto) throws Exception {
		HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth/csrf"))
			.GET();
		if (forwardedProto != null) {
			builder.header("X-Forwarded-Proto", forwardedProto);
		}
		HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());

		assertThat(response.statusCode()).isEqualTo(200);
		return response.headers().allValues("Set-Cookie").stream()
			.filter(value -> value.startsWith("XSRF-TOKEN="))
			.findFirst()
			.orElseThrow(() -> new AssertionError("no XSRF-TOKEN cookie in " + response.headers().map()));
	}

}
