package ar.rindetuplata.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * A key on the public read API through the real security chain. A valid key is served with a bucket
 * of its own, an unknown or revoked one is a 401 {@code ProblemDetail} at once, and a key never authenticates a
 * user: the account, alert, curation and admin endpoints answer exactly as they do to an anonymous caller.
 */
class ApiKeyReadApiTest extends ApiKeyIntegrationTest {

	private static final int FREE_CAPACITY = 600;

	private ResultActions read(String key, String address) throws Exception {
		MockHttpServletRequestBuilder request = get("/api/v1/ranking").with(r -> {
			r.setRemoteAddr(address);
			return r;
		});
		if (key != null) {
			request.header("X-Api-Key", key);
		}
		return mockMvc.perform(request);
	}

	@Test
	void aValidKeyIsServedAndAnUnknownOneIsA401ProblemDetail() throws Exception {
		String key = createdKey(loginNewSession(USER_EMAIL));

		read(key, "10.2.0.1").andExpect(status().isOk());

		read("rtp_" + "A".repeat(43), "10.2.0.1")
				.andExpect(status().isUnauthorized())
				.andExpect(header().string("Content-Type", "application/problem+json;charset=UTF-8"))
				.andExpect(header().doesNotExist("Retry-After"))
				.andExpect(jsonPath("$.type").value("about:blank"))
				.andExpect(jsonPath("$.title").value("Unauthorized"))
				.andExpect(jsonPath("$.status").value(401))
				.andExpect(jsonPath("$.detail").value("The API key is unknown or has been revoked"))
				.andExpect(jsonPath("$.instance").value("/api/v1/ranking"));
		read("garbage", "10.2.0.1").andExpect(status().isUnauthorized());
	}

	@Test
	void aRevokedKeyIsRefusedAtOnceAndNeverFallsBackToTheAddress() throws Exception {
		Cookie session = loginNewSession(USER_EMAIL);
		String key = createdKey(session);
		read(key, "10.2.0.2").andExpect(status().isOk());

		revokeKey(session, keyId(key)).andExpect(status().isNoContent());

		read(key, "10.2.0.2").andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.detail").value("The API key is unknown or has been revoked"));
		read(null, "10.2.0.2").andExpect(status().isOk());
	}

	@Test
	void theBucketOfAFreeKeyHoldsSixHundredRequestsThenAnswers429WithRetryAfterAndOtherKeysAreUntouched()
			throws Exception {
		String key = createdKey(loginNewSession(USER_EMAIL));
		String other = createdKey(loginNewSession(OTHER_EMAIL));
		for (int i = 0; i < FREE_CAPACITY; i++) {
			read(key, "10.2.0.3").andExpect(status().isOk());
		}

		read(key, "10.2.0.4")
				.andExpect(status().isTooManyRequests())
				.andExpect(header().string("Retry-After", "1"))
				.andExpect(header().string("Content-Type", "application/problem+json;charset=UTF-8"))
				.andExpect(jsonPath("$.type").value("about:blank"))
				.andExpect(jsonPath("$.title").value("Too Many Requests"))
				.andExpect(jsonPath("$.status").value(429))
				.andExpect(jsonPath("$.detail").value("Too many requests for this API key; try again later"))
				.andExpect(jsonPath("$.instance").value("/api/v1/ranking"));
		read(other, "10.2.0.3").andExpect(status().isOk());
		read(null, "10.2.0.3").andExpect(status().isOk());
	}

	@Test
	void theHeaderOfAKeyIsIgnoredOnEveryNonPublicPathSoItNeverAuthenticatesAnyone() throws Exception {
		Cookie owner = loginNewSession(USER_EMAIL);
		String key = createdKey(owner);
		long id = keyId(key);
		long rowsBefore = keyRows();
		Cookie admin = loginNewSession(ADMIN_EMAIL);
		String adminKey = createdKey(admin);

		for (String presented : new String[] { key, adminKey, "rtp_" + "A".repeat(43) }) {
			expectProblem(mockMvc.perform(get("/api/v1/me").header("X-Api-Key", presented)), 401, "Unauthorized");
			expectProblem(mockMvc.perform(get("/api/v1/alerts").header("X-Api-Key", presented)), 401, "Unauthorized");
			expectProblem(mockMvc.perform(post("/api/v1/alerts").with(csrf()).header("X-Api-Key", presented)
					.contentType(MediaType.APPLICATION_JSON).content("{\"thresholdPercent\":25}")), 401, "Unauthorized");
			expectProblem(mockMvc.perform(get("/api/v1/admin/wallet-rates").header("X-Api-Key", presented)), 401,
					"Unauthorized");
			expectProblem(mockMvc.perform(post("/api/v1/admin/wallet-rates/x/confirm").with(csrf())
					.header("X-Api-Key", presented)), 401, "Unauthorized");
			expectProblem(mockMvc.perform(get("/api/v1/account/api-keys").header("X-Api-Key", presented)), 401,
					"Unauthorized");
			expectProblem(mockMvc.perform(post("/api/v1/account/api-keys").with(csrf()).header("X-Api-Key", presented)
					.contentType(MediaType.APPLICATION_JSON).content("{}")), 401, "Unauthorized");
			expectProblem(mockMvc.perform(delete("/api/v1/account/api-keys/" + id).with(csrf())
					.header("X-Api-Key", presented)), 401, "Unauthorized");
		}

		assertThat(keyRows()).as("no key was created or changed through a key").isEqualTo(rowsBefore + 1);
		assertThat(activeKeyRows(USER_EMAIL)).isEqualTo(1);
		assertThat(jdbc.sql("select count(*) from alert_rule").query(Long.class).single()).isZero();
	}

	@Test
	void aSessionStillAuthenticatesAndTheKeyHeaderDoesNotChangeWhoItIs() throws Exception {
		Cookie owner = loginNewSession(USER_EMAIL);
		Cookie admin = loginNewSession(ADMIN_EMAIL);
		String ownerKey = createdKey(owner);

		mockMvc.perform(get("/api/v1/me").cookie(owner).header("X-Api-Key", ownerKey))
				.andExpect(status().isOk()).andExpect(jsonPath("$.email").value(USER_EMAIL));
		mockMvc.perform(get("/api/v1/me").cookie(admin).header("X-Api-Key", ownerKey))
				.andExpect(status().isOk()).andExpect(jsonPath("$.email").value(ADMIN_EMAIL));
		expectProblem(mockMvc.perform(get("/api/v1/admin/wallet-rates").cookie(owner).header("X-Api-Key", ownerKey)),
				403, "Forbidden");
	}
}
