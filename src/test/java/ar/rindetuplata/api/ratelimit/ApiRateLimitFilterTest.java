package ar.rindetuplata.api.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * T1a: which requests the filter counts, the exact 429 {@code ProblemDetail} and when the {@code X-Build-Token}
 * bypass applies. A blank configured token disables the bypass, even for a blank or missing header.
 */
class ApiRateLimitFilterTest {

	private final TestClock clock = new TestClock();

	private ApiRateLimitFilter filter(String bypassToken) {
		return new ApiRateLimitFilter(new ApiRateLimiter(1, 0.5, clock), bypassToken);
	}

	private MockHttpServletResponse call(ApiRateLimitFilter filter, String method, String path, String address,
			String buildToken) throws ServletException, IOException {
		MockHttpServletRequest request = new MockHttpServletRequest(method, path);
		request.setRemoteAddr(address);
		if (buildToken != null) {
			request.addHeader("X-Build-Token", buildToken);
		}
		MockHttpServletResponse response = new MockHttpServletResponse();
		filter.doFilter(request, response, new MockFilterChain());
		return response;
	}

	@Test
	void theSecondRequestOfAnAddressIsA429ProblemDetailWithRetryAfter() throws Exception {
		ApiRateLimitFilter filter = filter("");
		assertThat(call(filter, "GET", "/api/v1/ranking", "1.1.1.1", null).getStatus()).isEqualTo(200);

		MockHttpServletResponse limited = call(filter, "GET", "/api/v1/ranking", "1.1.1.1", null);

		assertThat(limited.getStatus()).isEqualTo(429);
		assertThat(limited.getHeader("Retry-After")).isEqualTo("2");
		assertThat(limited.getContentType()).startsWith("application/problem+json");
		JsonNode body = new JsonMapper().readTree(limited.getContentAsString());
		assertThat(body.get("type").asString()).isEqualTo("about:blank");
		assertThat(body.get("title").asString()).isEqualTo("Too Many Requests");
		assertThat(body.get("status").asInt()).isEqualTo(429);
		assertThat(body.get("detail").asString()).isEqualTo("Too many requests from this address; try again later");
		assertThat(body.get("instance").asString()).isEqualTo("/api/v1/ranking");
	}

	@Test
	void everyPublicReadPathIsLimitedAndNothingElseIs() throws Exception {
		for (String path : new String[] { "/api/v1/ranking", "/api/v1/simulations", "/api/v1/instruments",
				"/api/v1/instruments/7/history" }) {
			ApiRateLimitFilter filter = filter("");
			call(filter, "GET", path, "1.1.1.1", null);
			assertThat(call(filter, "GET", path, "1.1.1.1", null).getStatus()).as(path).isEqualTo(429);
		}
		ApiRateLimitFilter filter = filter("");
		for (String path : new String[] { "/actuator/health", "/api/v1/me", "/v3/api-docs" }) {
			for (int i = 0; i < 3; i++) {
				assertThat(call(filter, "GET", path, "1.1.1.1", null).getStatus()).as(path).isEqualTo(200);
			}
		}
		for (int i = 0; i < 3; i++) {
			assertThat(call(filter, "POST", "/api/v1/ranking", "1.1.1.1", null).getStatus()).isEqualTo(200);
			assertThat(call(filter, "OPTIONS", "/api/v1/ranking", "1.1.1.1", null).getStatus()).isEqualTo(200);
		}
		assertThat(call(filter, "GET", "/api/v1/ranking", "1.1.1.1", null).getStatus())
				.as("the uncounted requests left the bucket full").isEqualTo(200);
	}

	@Test
	void theMatchingBuildTokenSkipsTheLimitAndAWrongOneDoesNot() throws Exception {
		ApiRateLimitFilter filter = filter("s3cret");
		for (int i = 0; i < 5; i++) {
			assertThat(call(filter, "GET", "/api/v1/ranking", "1.1.1.1", "s3cret").getStatus()).isEqualTo(200);
		}

		assertThat(call(filter, "GET", "/api/v1/ranking", "2.2.2.2", "wrong").getStatus()).isEqualTo(200);
		assertThat(call(filter, "GET", "/api/v1/ranking", "2.2.2.2", "wrong").getStatus()).isEqualTo(429);
		assertThat(call(filter, "GET", "/api/v1/ranking", "3.3.3.3", null).getStatus()).isEqualTo(200);
		assertThat(call(filter, "GET", "/api/v1/ranking", "3.3.3.3", null).getStatus()).isEqualTo(429);
	}

	@Test
	void aConfiguredTokenWithSurroundingWhitespaceMatchesTheTrimmedHeaderTheBuildSends() throws Exception {
		ApiRateLimitFilter filter = filter(" s3cret\n");
		for (int i = 0; i < 3; i++) {
			assertThat(call(filter, "GET", "/api/v1/ranking", "1.1.1.1", "s3cret").getStatus()).isEqualTo(200);
		}
	}

	@Test
	void aBlankConfiguredTokenNeverBypasses() throws Exception {
		ApiRateLimitFilter filter = filter("");
		assertThat(call(filter, "GET", "/api/v1/ranking", "1.1.1.1", "").getStatus()).isEqualTo(200);

		assertThat(call(filter, "GET", "/api/v1/ranking", "1.1.1.1", "").getStatus()).isEqualTo(429);
	}

	@Test
	void anUnknownAddressIsA429WhenTheLimiterIsFullButAKnownOneIsServed() throws Exception {
		ApiRateLimitFilter filter = new ApiRateLimitFilter(new ApiRateLimiter(2, 0.5, 1, clock), "");
		assertThat(call(filter, "GET", "/api/v1/ranking", "1.1.1.1", null).getStatus()).isEqualTo(200);

		MockHttpServletResponse refused = call(filter, "GET", "/api/v1/ranking", "2.2.2.2", null);

		assertThat(refused.getStatus()).isEqualTo(429);
		assertThat(Long.parseLong(refused.getHeader("Retry-After"))).isGreaterThanOrEqualTo(1);
		assertThat(call(filter, "GET", "/api/v1/ranking", "1.1.1.1", null).getStatus()).isEqualTo(200);
	}
}
