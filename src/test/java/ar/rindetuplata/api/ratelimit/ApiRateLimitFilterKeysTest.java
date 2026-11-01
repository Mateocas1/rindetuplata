package ar.rindetuplata.api.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import ar.rindetuplata.apikey.ApiKeyPlan;
import ar.rindetuplata.apikey.ApiKeyPrincipal;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * T6d (S6, L32b): {@code X-Api-Key} on the public read paths. A valid key is limited by its own bucket instead of
 * the address, an unknown or revoked key is a 401 {@code ProblemDetail} (never a fall back to the address bucket),
 * and the header means nothing on any other path.
 */
class ApiRateLimitFilterKeysTest {

	private static final String GOOD = "rtp_good";

	private final TestClock clock = new TestClock();

	/** Anonymous bucket of 1; keys: FREE bucket of 2 and quota 100. */
	private final ApiRateLimitFilter filter = new ApiRateLimitFilter(new ApiRateLimiter(1, 0.5, clock), "s3cret",
			new ApiKeyAdmission(presented -> GOOD.equals(presented)
					? Optional.of(new ApiKeyPrincipal(7, ApiKeyPlan.FREE)) : Optional.empty(),
					Map.of(ApiKeyPlan.FREE, new ApiPlanLimits(2, 0.5, 100), ApiKeyPlan.PARTNER,
							new ApiPlanLimits(4, 0.5, 200)),
					1000, clock));

	private MockHttpServletResponse call(String method, String path, String address, String apiKey,
			String buildToken) throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest(method, path);
		request.setRemoteAddr(address);
		if (apiKey != null) {
			request.addHeader("X-Api-Key", apiKey);
		}
		if (buildToken != null) {
			request.addHeader("X-Build-Token", buildToken);
		}
		MockHttpServletResponse response = new MockHttpServletResponse();
		filter.doFilter(request, response, new MockFilterChain());
		return response;
	}

	private MockHttpServletResponse get(String path, String address, String apiKey) throws Exception {
		return call("GET", path, address, apiKey, null);
	}

	@Test
	void aValidKeyIsLimitedByItsOwnBucketWhateverTheAddressAndLeavesTheAddressBucketAlone() throws Exception {
		assertThat(get("/api/v1/ranking", "1.1.1.1", GOOD).getStatus()).isEqualTo(200);
		assertThat(get("/api/v1/ranking", "2.2.2.2", GOOD).getStatus()).isEqualTo(200);

		MockHttpServletResponse limited = get("/api/v1/ranking", "3.3.3.3", GOOD);

		assertThat(limited.getStatus()).isEqualTo(429);
		assertThat(limited.getHeader("Retry-After")).isEqualTo("2");
		assertThat(limited.getContentType()).startsWith("application/problem+json");
		JsonNode body = new JsonMapper().readTree(limited.getContentAsString());
		assertThat(body.get("type").asString()).isEqualTo("about:blank");
		assertThat(body.get("title").asString()).isEqualTo("Too Many Requests");
		assertThat(body.get("status").asInt()).isEqualTo(429);
		assertThat(body.get("detail").asString()).isEqualTo("Too many requests for this API key; try again later");
		assertThat(body.get("instance").asString()).isEqualTo("/api/v1/ranking");
		assertThat(get("/api/v1/ranking", "1.1.1.1", null).getStatus())
				.as("the keyed requests took nothing from the address bucket").isEqualTo(200);
	}

	@Test
	void anUnknownOrRevokedKeyIsA401ProblemDetailAndNeverFallsBackToTheAddress() throws Exception {
		for (String presented : new String[] { "rtp_unknown", "", "   " }) {
			MockFilterChain chain = new MockFilterChain();
			MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/instruments/9/history");
			String address = "4.4.4." + Math.abs(presented.hashCode() % 200);
			request.setRemoteAddr(address);
			request.addHeader("X-Api-Key", presented);
			MockHttpServletResponse response = new MockHttpServletResponse();

			filter.doFilter(request, response, chain);

			assertThat(response.getStatus()).as("key '%s'", presented).isEqualTo(401);
			assertThat(chain.getRequest()).as("the request never reaches the API").isNull();
			assertThat(response.getHeader("Retry-After")).isNull();
			assertThat(response.getContentType()).startsWith("application/problem+json");
			JsonNode body = new JsonMapper().readTree(response.getContentAsString());
			assertThat(body.get("type").asString()).isEqualTo("about:blank");
			assertThat(body.get("title").asString()).isEqualTo("Unauthorized");
			assertThat(body.get("status").asInt()).isEqualTo(401);
			assertThat(body.get("detail").asString()).isEqualTo("The API key is unknown or has been revoked");
			assertThat(body.get("instance").asString()).isEqualTo("/api/v1/instruments/9/history");
		}
		assertThat(get("/api/v1/ranking", "4.4.4.4", "rtp_unknown").getStatus()).isEqualTo(401);
		assertThat(get("/api/v1/ranking", "4.4.4.4", null).getStatus())
				.as("a refused key is charged to the address bucket (capacity 1 here)").isEqualTo(429);
	}

	@Test
	void theKeyHeaderMeansNothingOnPathsThatAreNotThePublicReadApi() throws Exception {
		for (String path : new String[] { "/api/v1/me", "/api/v1/alerts", "/api/v1/account/api-keys",
				"/api/v1/admin/wallet-rates", "/actuator/health" }) {
			for (String presented : new String[] { GOOD, "rtp_unknown" }) {
				MockFilterChain chain = new MockFilterChain();
				MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
				request.addHeader("X-Api-Key", presented);
				MockHttpServletResponse response = new MockHttpServletResponse();

				filter.doFilter(request, response, chain);

				assertThat(response.getStatus()).as(path).isEqualTo(200);
				assertThat(chain.getRequest()).as("%s reached the chain", path).isNotNull();
			}
		}
		MockHttpServletResponse post = call("POST", "/api/v1/ranking", "1.1.1.1", "rtp_unknown", null);
		assertThat(post.getStatus()).as("only GET of the public paths is read").isEqualTo(200);
		assertThat(get("/api/v1/ranking", "1.1.1.1", GOOD).getStatus()).as("the good key's bucket is untouched")
				.isEqualTo(200);
		assertThat(get("/api/v1/ranking", "1.1.1.1", GOOD).getStatus()).isEqualTo(200);
		assertThat(get("/api/v1/ranking", "1.1.1.1", GOOD).getStatus()).isEqualTo(429);
	}

	@Test
	void theBuildTokenStillSkipsEveryLimitAndAKeyChangesNothingAboutIt() throws Exception {
		for (int i = 0; i < 5; i++) {
			assertThat(call("GET", "/api/v1/ranking", "1.1.1.1", null, "s3cret").getStatus()).isEqualTo(200);
			assertThat(call("GET", "/api/v1/ranking", "1.1.1.1", "rtp_unknown", "s3cret").getStatus()).isEqualTo(200);
		}
	}

	@Test
	void withoutAKeyTheAddressBucketAppliesAsBefore() throws Exception {
		assertThat(get("/api/v1/ranking", "5.5.5.5", null).getStatus()).isEqualTo(200);

		MockHttpServletResponse limited = get("/api/v1/ranking", "5.5.5.5", null);

		assertThat(limited.getStatus()).isEqualTo(429);
		JsonNode body = new JsonMapper().readTree(limited.getContentAsString());
		assertThat(body.get("detail").asString()).isEqualTo("Too many requests from this address; try again later");
	}
}
