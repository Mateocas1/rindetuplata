package ar.rindetuplata.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Per-IP rate limit through the real security chain: the public read endpoints allow
 * {@code rindetuplata.api.rate-limit.capacity} requests per address, then answer a 429 {@code ProblemDetail} with
 * {@code Retry-After}; the {@code X-Build-Token} bypass skips it; other paths are never counted. The clock is
 * frozen, so no token refills during a test, and each test uses its own remote address because the buckets live
 * in the application context.
 */
@TestPropertySource(properties = {
		"rindetuplata.api.rate-limit.capacity=3",
		"rindetuplata.api.rate-limit.refill-per-second=0.5",
		"rindetuplata.api.rate-limit.bypass-token=build-secret",
		"rindetuplata.web.allowed-origins=http://localhost:4321"
})
class ApiRateLimitApiTest extends ApiIntegrationTest {

	private ResultActions getFrom(String address, String path, String buildToken) throws Exception {
		MockHttpServletRequestBuilder request = get(path).with(r -> {
			r.setRemoteAddr(address);
			return r;
		});
		if (buildToken != null) {
			request.header("X-Build-Token", buildToken);
		}
		return mockMvc.perform(request);
	}

	@Test
	void theRequestBeyondTheCapacityIsA429ProblemDetailWithRetryAfter() throws Exception {
		for (int i = 0; i < 3; i++) {
			getFrom("10.1.0.1", "/api/v1/ranking", null).andExpect(status().isOk());
		}

		getFrom("10.1.0.1", "/api/v1/ranking", null)
				.andExpect(status().isTooManyRequests())
				.andExpect(header().string("Retry-After", "2"))
				.andExpect(header().string("Content-Type", "application/problem+json;charset=UTF-8"))
				.andExpect(jsonPath("$.type").value("about:blank"))
				.andExpect(jsonPath("$.title").value("Too Many Requests"))
				.andExpect(jsonPath("$.status").value(429))
				.andExpect(jsonPath("$.detail").value("Too many requests from this address; try again later"))
				.andExpect(jsonPath("$.instance").value("/api/v1/ranking"));
	}

	@Test
	void theBucketIsSharedByTheFourPublicReadPathsAndPerAddress() throws Exception {
		getFrom("10.1.0.2", "/api/v1/ranking", null).andExpect(status().isOk());
		getFrom("10.1.0.2", "/api/v1/simulations", null).andExpect(status().isBadRequest());
		getFrom("10.1.0.2", "/api/v1/instruments", null).andExpect(status().isOk());

		getFrom("10.1.0.2", "/api/v1/instruments/999999/history", null).andExpect(status().isTooManyRequests());
		getFrom("10.1.0.3", "/api/v1/ranking", null).andExpect(status().isOk());
	}

	@Test
	void theBuildTokenSkipsTheLimitAndAWrongTokenDoesNot() throws Exception {
		for (int i = 0; i < 6; i++) {
			getFrom("10.1.0.4", "/api/v1/ranking", "build-secret").andExpect(status().isOk());
		}

		for (int i = 0; i < 3; i++) {
			getFrom("10.1.0.5", "/api/v1/ranking", "not-the-secret").andExpect(status().isOk());
		}
		getFrom("10.1.0.5", "/api/v1/ranking", "not-the-secret").andExpect(status().isTooManyRequests());
	}

	@Test
	void otherPathsAreNeverCountedNorLimited() throws Exception {
		for (int i = 0; i < 6; i++) {
			// Health may be DOWN (503) without a broker in the test context; the point is that it is never a 429.
			getFrom("10.1.0.6", "/actuator/health", null)
					.andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(429));
		}

		getFrom("10.1.0.6", "/api/v1/ranking", null).andExpect(status().isOk());
	}

	@Test
	void theLimitedAnswerStillCarriesTheCorsHeadersOfAnAllowedOrigin() throws Exception {
		for (int i = 0; i < 3; i++) {
			getFrom("10.1.0.7", "/api/v1/ranking", null);
		}

		getFrom("10.1.0.7", "/api/v1/ranking", null).andExpect(status().isTooManyRequests());
		mockMvc.perform(get("/api/v1/ranking").header("Origin", "http://localhost:4321")
				.with(r -> {
					r.setRemoteAddr("10.1.0.7");
					return r;
				}))
				.andExpect(status().isTooManyRequests())
				.andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4321"));
	}
}
