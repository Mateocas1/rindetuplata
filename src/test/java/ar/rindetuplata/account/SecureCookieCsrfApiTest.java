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
 * T7a browser-cookie rule (S4, S13, S16): with {@code rindetuplata.auth.secure-cookie=true} (the VPS setting) the
 * {@code XSRF-TOKEN} cookie must be {@code Secure} even though this test talks plain HTTP to localhost, and it must
 * keep {@code HttpOnly=false}, {@code Path=/} and {@code SameSite=Lax} so the SPA can read nothing but still gets a
 * same-site cookie. Real HTTP, because only the raw {@code Set-Cookie} header carries every attribute.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"rindetuplata.ingest.enabled=false",
		"rindetuplata.ingest.cron=0 0 0 1 1 *",
		"rindetuplata.auth.secure-cookie=true",
		"rindetuplata.outbox.relay-enabled=false",
		"rindetuplata.outbox.consumer-enabled=false"
})
@Import(AccountTestConfiguration.class)
class SecureCookieCsrfApiTest {

	@LocalServerPort
	int port;

	private final HttpClient client = HttpClient.newHttpClient();

	@Test
	void theConfiguredSecureCookieMarksTheCsrfCookieSecureAndKeepsItsOtherAttributes() throws Exception {
		String setCookie = csrfSetCookie();

		assertThat(setCookie).as("the configured Secure flag reaches the raw cookie")
			.contains("Secure")
			.contains("Path=/")
			.contains("SameSite=Lax")
			.doesNotContain("HttpOnly");
	}

	private String csrfSetCookie() throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth/csrf"))
			.GET()
			.build();
		HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

		assertThat(response.statusCode()).isEqualTo(200);
		return response.headers().allValues("Set-Cookie").stream()
			.filter(value -> value.startsWith("XSRF-TOKEN="))
			.findFirst()
			.orElseThrow(() -> new AssertionError("no XSRF-TOKEN cookie in " + response.headers().map()));
	}

}
