package ar.rindetuplata.api.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import ar.rindetuplata.apikey.ApiKeyPlan;
import ar.rindetuplata.apikey.ApiKeyPrincipal;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * T6d, review finding F1: a request whose key is not valid (malformed or unknown) is charged to the per-address
 * bucket of the anonymous plan, and once that bucket is empty the answer is the address 429 without any key lookup,
 * so a flood of well-formed random keys is bounded like any other flood and cannot turn into unlimited database reads.
 */
class ApiRateLimitFilterKeyFloodTest {

	private static final String GOOD = "rtp_good";

	private static final String ADDRESS_DETAIL = "Too many requests from this address; try again later";

	private final TestClock clock = new TestClock();

	private final AtomicInteger lookups = new AtomicInteger();

	/** Anonymous bucket of 3, refilled 0.5 per second; keys: FREE bucket of 100. */
	private final ApiRateLimitFilter filter = new ApiRateLimitFilter(new ApiRateLimiter(3, 0.5, clock), "",
			new ApiKeyAdmission(presented -> {
				lookups.incrementAndGet();
				return GOOD.equals(presented) ? Optional.of(new ApiKeyPrincipal(7, ApiKeyPlan.FREE))
						: Optional.empty();
			}, Map.of(ApiKeyPlan.FREE, new ApiPlanLimits(100, 10, 1000), ApiKeyPlan.PARTNER,
					new ApiPlanLimits(100, 10, 1000)), 1000, clock));

	private MockHttpServletResponse get(String address, String apiKey) throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/ranking");
		request.setRemoteAddr(address);
		if (apiKey != null) {
			request.addHeader("X-Api-Key", apiKey);
		}
		MockHttpServletResponse response = new MockHttpServletResponse();
		filter.doFilter(request, response, new MockFilterChain());
		return response;
	}

	@Test
	void afterTheAddressBucketIsSpentByUnknownKeysTheNextIsTheAddress429WithoutALookup() throws Exception {
		for (int i = 0; i < 3; i++) {
			MockHttpServletResponse refused = get("9.9.9.9", "rtp_" + "A".repeat(42) + i);
			assertThat(refused.getStatus()).as("attempt %d", i).isEqualTo(401);
		}
		assertThat(lookups).hasValue(3);

		MockHttpServletResponse limited = get("9.9.9.9", "rtp_" + "B".repeat(43));

		assertThat(limited.getStatus()).isEqualTo(429);
		assertThat(limited.getHeader("Retry-After")).isEqualTo("2");
		assertThat(limited.getContentType()).startsWith("application/problem+json");
		JsonNode body = new JsonMapper().readTree(limited.getContentAsString());
		assertThat(body.get("title").asString()).isEqualTo("Too Many Requests");
		assertThat(body.get("status").asInt()).isEqualTo(429);
		assertThat(body.get("detail").asString()).isEqualTo(ADDRESS_DETAIL);
		assertThat(body.get("instance").asString()).isEqualTo("/api/v1/ranking");
		assertThat(lookups).as("the refused request made no key lookup").hasValue(3);
		for (int i = 0; i < 20; i++) {
			assertThat(get("9.9.9.9", "rtp_" + "C".repeat(43)).getStatus()).isEqualTo(429);
		}
		assertThat(lookups).as("a sustained flood made no lookup at all").hasValue(3);
	}

	@Test
	void malformedKeysAreChargedToTheAddressToo() throws Exception {
		for (String presented : new String[] { "", "garbage", "rtp_short" }) {
			assertThat(get("9.9.9.8", presented).getStatus()).as(presented).isEqualTo(401);
		}

		assertThat(get("9.9.9.8", "").getStatus()).isEqualTo(429);
		assertThat(get("9.9.9.8", null).getStatus()).as("the same bucket the anonymous plan uses").isEqualTo(429);
	}

	@Test
	void aValidKeyFromTheSameAddressIsStillAdmittedAndNeverChargedToTheAddress() throws Exception {
		get("9.9.9.7", "rtp_" + "A".repeat(43));
		get("9.9.9.7", "rtp_" + "B".repeat(43));
		int before = lookups.get();

		for (int i = 0; i < 10; i++) {
			assertThat(get("9.9.9.7", GOOD).getStatus()).as("valid request %d", i).isEqualTo(200);
		}

		assertThat(lookups.get() - before).isEqualTo(10);
		assertThat(get("9.9.9.7", "rtp_" + "C".repeat(43)).getStatus())
				.as("one token was left: the valid requests took none").isEqualTo(401);
		assertThat(get("9.9.9.7", null).getStatus()).isEqualTo(429);
	}

	@Test
	void theAddressRefillsAndUnknownKeysAreLookedUpAgain() throws Exception {
		for (int i = 0; i < 3; i++) {
			get("9.9.9.6", "rtp_" + "A".repeat(43));
		}
		assertThat(get("9.9.9.6", "rtp_" + "A".repeat(43)).getStatus()).isEqualTo(429);

		clock.advance(Duration.ofSeconds(2));

		assertThat(get("9.9.9.6", "rtp_" + "A".repeat(43)).getStatus()).isEqualTo(401);
		assertThat(lookups).hasValue(4);
	}

	@Test
	void anotherAddressIsNotAffectedByTheFlood() throws Exception {
		for (int i = 0; i < 5; i++) {
			get("9.9.9.5", "rtp_" + "A".repeat(43));
		}

		assertThat(get("9.9.9.4", "rtp_" + "A".repeat(43)).getStatus()).isEqualTo(401);
		assertThat(get("9.9.9.4", GOOD).getStatus()).isEqualTo(200);
	}
}
