package ar.rindetuplata.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

/**
 * T11a per-IP rate limit (S4, S13): {@code POST /api/v1/auth/magic-link} allows
 * {@code rindetuplata.auth.magic-link-per-ip-limit} requests per window per remote address; beyond it the answer is
 * a 429 {@code ProblemDetail} with {@code Retry-After}, with no token and no mail, for known and unknown emails
 * alike, before the per-email cap, and an invalid email still answers 400 and counts. Each test uses its own
 * remote address because the counters live in the application context.
 */
@TestPropertySource(properties = {
		"rindetuplata.auth.magic-link-per-ip-limit=3",
		"rindetuplata.auth.magic-link-per-ip-window=1h"
})
class MagicLinkRateLimitApiTest extends AccountIntegrationTest {

	private ResultActions postFrom(String address, String email) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/magic-link").with(csrf())
				.with(request -> {
					request.setRemoteAddr(address);
					return request;
				})
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\"}"));
	}

	private long tokenRows(String email) {
		return jdbc.sql("select count(*) from one_time_tokens where username = :email").param("email", email)
				.query(Long.class).single();
	}

	@Test
	void theRequestBeyondTheLimitIsA429WithRetryAfterAndNoTokenNoMail() throws Exception {
		loginNewSession("known@example.com");
		mailer.clear();
		for (int i = 0; i < 3; i++) {
			postFrom("10.0.0.1", "ok" + i + "@example.com").andExpect(status().isAccepted());
		}
		assertThat(mailer.sent()).hasSize(3);

		ResultActions known = postFrom("10.0.0.1", "known@example.com");
		expectProblem(known, 429, "Too Many Requests");
		known.andExpect(header().string("Retry-After", "3600"))
				.andExpect(jsonPath("$.detail").isNotEmpty());
		expectProblem(postFrom("10.0.0.1", "nobody@example.com"), 429, "Too Many Requests");

		assertThat(mailer.sent()).as("the limited requests send nothing").hasSize(3);
		assertThat(tokenRows("known@example.com")).as("no token for the limited known email").isZero();
		assertThat(tokenRows("nobody@example.com")).as("no token for the limited unknown email").isZero();
	}

	@Test
	void anotherAddressIsNotAffected() throws Exception {
		for (int i = 0; i < 3; i++) {
			postFrom("10.0.0.2", "a" + i + "@example.com").andExpect(status().isAccepted());
		}
		expectProblem(postFrom("10.0.0.2", "a3@example.com"), 429, "Too Many Requests");

		postFrom("10.0.0.3", "b@example.com").andExpect(status().isAccepted());
	}

	@Test
	void theWindowResetsAfterItsDuration() throws Exception {
		for (int i = 0; i < 3; i++) {
			postFrom("10.0.0.4", "w" + i + "@example.com").andExpect(status().isAccepted());
		}
		ResultActions limited = postFrom("10.0.0.4", "w3@example.com");
		expectProblem(limited, 429, "Too Many Requests");

		clock.advance(Duration.ofMinutes(30));
		postFrom("10.0.0.4", "w3@example.com").andExpect(status().is(429))
				.andExpect(header().string("Retry-After", "1800"));

		clock.advance(Duration.ofMinutes(30));
		postFrom("10.0.0.4", "w3@example.com").andExpect(status().isAccepted());
	}

	@Test
	void anInvalidEmailStillAnswers400AndCountsTowardsTheLimit() throws Exception {
		expectProblem(postFrom("10.0.0.5", "not-an-email"), 400, "Bad Request");
		expectProblem(postFrom("10.0.0.5", "still-not"), 400, "Bad Request");
		postFrom("10.0.0.5", "fine@example.com").andExpect(status().isAccepted());

		expectProblem(postFrom("10.0.0.5", "not-an-email"), 429, "Too Many Requests");
	}

	@Test
	void theLimitIsCheckedBeforeThePerEmailCap() throws Exception {
		for (int i = 0; i < 3; i++) {
			postFrom("10.0.0.6", "capped@example.com").andExpect(status().isAccepted());
		}
		mailer.clear();

		// The email already holds 3 tokens (a capped request would be a silent 202), but the address is over its limit.
		expectProblem(postFrom("10.0.0.6", "capped@example.com"), 429, "Too Many Requests");
		assertThat(mailer.sent()).isEmpty();
		assertThat(tokenRows("capped@example.com")).isEqualTo(3);
	}
}
