package ar.rindetuplata.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.rindetuplata.apikey.ApiKeyAuthenticator;
import ar.rindetuplata.apikey.ApiKeyPlan;
import ar.rindetuplata.apikey.ApiKeyPrincipal;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.Filter;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Create (shown once), list and revoke under {@code /api/v1/account/api-keys} for the signed-in
 * user, with the session and CSRF rules of the other account endpoints, at most 3 active keys, and nothing but the
 * SHA-256 and a display prefix in the database.
 */
class ApiKeyAccountApiTest extends ApiKeyIntegrationTest {

	@Autowired
	FilterChainProxy securityFilterChains;

	@Autowired
	CsrfTokenRepository csrfTokenRepository;

	@Autowired
	ApiKeyAuthenticator authenticator;

	@Test
	void createAnswers201WithTheKeyOnceAndStoresOnlyItsHashAndPrefix() throws Exception {
		Cookie session = loginNewSession(USER_EMAIL);

		MvcResult result = createKey(session, "{\"name\":\"  mi script \"}")
				.andExpect(status().isCreated())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.name").value("mi script"))
				.andExpect(jsonPath("$.plan").value("FREE"))
				.andExpect(jsonPath("$.createdAt").isNotEmpty())
				.andExpect(jsonPath("$.key").value(org.hamcrest.Matchers.matchesPattern("rtp_[A-Za-z0-9_-]{43}")))
				.andExpect(jsonPath("$.prefix").isNotEmpty())
				.andReturn();

		String body = result.getResponse().getContentAsString();
		String key = JsonPath.read(body, "$.key");
		assertThat(JsonPath.<String>read(body, "$.prefix")).isEqualTo(key.substring(0, 10));
		Map<String, Object> row = jdbc.sql("select * from api_key").query().singleRow();
		assertThat(row.get("key_hash")).isEqualTo(sha256(key));
		assertThat(row.get("key_prefix")).isEqualTo(key.substring(0, 10));
		assertThat(row.get("plan")).isEqualTo("FREE");
		assertThat(row.get("revoked_at")).isNull();
		assertThat(row.toString()).as("no column of the row holds the plaintext").doesNotContain(key);
		assertThat(jdbc.sql("select count(*) from api_key where api_key::text like '%' || :key || '%'")
				.param("key", key).query(Long.class).single()).isZero();
	}

	@Test
	void theNameIsOptionalAndBoundedAtSixtyCharacters() throws Exception {
		Cookie session = loginNewSession(USER_EMAIL);

		createKey(session, "{}").andExpect(status().isCreated()).andExpect(jsonPath("$.name").doesNotExist());
		mockMvc.perform(post(KEYS_PATH).cookie(session)
				.with(csrf()))
				.andExpect(status().isCreated());

		expectProblem(createKey(session, "{\"name\":\"" + "x".repeat(61) + "\"}"), 400, "Bad Request");
		assertThat(keyRows()).isEqualTo(2);
	}

	@Test
	void theListShowsTheActiveKeysOfTheCallerWithoutThePlaintext() throws Exception {
		Cookie session = loginNewSession(USER_EMAIL);
		String first = createdKey(session);
		String second = createdKey(session);
		revokeKey(session, keyId(second)).andExpect(status().isNoContent());

		MvcResult result = listKeys(session)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].id").value(keyId(first)))
				.andExpect(jsonPath("$[0].prefix").value(first.substring(0, 10)))
				.andExpect(jsonPath("$[0].plan").value("FREE"))
				.andExpect(jsonPath("$[0].createdAt").isNotEmpty())
				.andExpect(jsonPath("$[0].key").doesNotExist())
				.andReturn();

		assertThat(result.getResponse().getContentAsString()).doesNotContain(first).doesNotContain(second)
				.doesNotContain(sha256(first)).doesNotContain("hash");
	}

	@Test
	void aFourthActiveKeyIsRefusedAndARevokedOneFreesASlot() throws Exception {
		Cookie session = loginNewSession(USER_EMAIL);
		String first = createdKey(session);
		createdKey(session);
		createdKey(session);

		expectProblem(createKey(session, "{}"), 400, "Bad Request");
		assertThat(keyRows()).as("nothing stored by the refused create").isEqualTo(3);

		revokeKey(session, keyId(first)).andExpect(status().isNoContent());
		createKey(session, "{}").andExpect(status().isCreated());
		assertThat(activeKeyRows(USER_EMAIL)).isEqualTo(3);
		assertThat(keyRows()).isEqualTo(4);
	}

	@Test
	void theCapIsPerUser() throws Exception {
		Cookie session = loginNewSession(USER_EMAIL);
		for (int i = 0; i < 3; i++) {
			createdKey(session);
		}
		Cookie other = loginNewSession(OTHER_EMAIL);

		createKey(other, "{}").andExpect(status().isCreated());

		assertThat(activeKeyRows(OTHER_EMAIL)).isEqualTo(1);
	}

	@Test
	void anotherUsersKeyCannotBeListedOrRevoked() throws Exception {
		Cookie owner = loginNewSession(USER_EMAIL);
		String ownerKey = createdKey(owner);
		long ownerKeyId = keyId(ownerKey);
		Cookie intruder = loginNewSession(OTHER_EMAIL);

		listKeys(intruder).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
		expectProblem(revokeKey(intruder, ownerKeyId), 404, "Not Found");

		assertThat(activeKeyRows(USER_EMAIL)).as("the key was not revoked").isEqualTo(1);
		listKeys(owner).andExpect(jsonPath("$", hasSize(1)));
	}

	@Test
	void revokingTwiceOrAnUnknownIdIsA404AndKeepsTheRevocationDate() throws Exception {
		Cookie session = loginNewSession(USER_EMAIL);
		long id = keyId(createdKey(session));
		revokeKey(session, id).andExpect(status().isNoContent());
		Object revokedAt = jdbc.sql("select revoked_at from api_key where id = :id").param("id", id)
				.query(java.time.OffsetDateTime.class).single();
		clock.advance(java.time.Duration.ofHours(1));

		expectProblem(revokeKey(session, id), 404, "Not Found");
		expectProblem(revokeKey(session, 987654321L), 404, "Not Found");

		assertThat(jdbc.sql("select revoked_at from api_key where id = :id").param("id", id)
				.query(java.time.OffsetDateTime.class).single()).isEqualTo(revokedAt);
	}

	@Test
	void withoutASessionEveryVerbIsA401ProblemDetailAndNothingChanges() throws Exception {
		Cookie owner = loginNewSession(USER_EMAIL);
		long id = keyId(createdKey(owner));
		long before = keyRows();

		expectProblem(mockMvc.perform(get(KEYS_PATH)), 401, "Unauthorized");
		expectProblem(mockMvc.perform(post(KEYS_PATH).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content("{}")), 401, "Unauthorized");
		expectProblem(mockMvc.perform(delete(KEYS_PATH + "/" + id).with(csrf())), 401, "Unauthorized");

		assertThat(keyRows()).isEqualTo(before);
		assertThat(activeKeyRows(USER_EMAIL)).isEqualTo(1);
	}

	@Test
	void aStateChangingRequestWithoutCsrfIsForbiddenAndNothingChanges() throws Exception {
		Cookie session = loginNewSession(USER_EMAIL);
		long id = keyId(createdKey(session));

		expectProblem(mockMvc.perform(post(KEYS_PATH).cookie(session)
				.contentType(MediaType.APPLICATION_JSON).content("{}")), 403, "Forbidden");
		expectProblem(mockMvc.perform(delete(KEYS_PATH + "/" + id).cookie(session)), 403, "Forbidden");

		assertThat(keyRows()).isEqualTo(1);
		assertThat(activeKeyRows(USER_EMAIL)).isEqualTo(1);
	}

	@Test
	void theRealCsrfFlowOfTheSpaWorksEndToEnd() throws Exception {
		Cookie session = loginNewSession(USER_EMAIL);
		restoreTheRealCsrfTokenRepository();
		MvcResult csrf = mockMvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
		String token = JsonPath.read(csrf.getResponse().getContentAsString(), "$.token");
		Cookie xsrf = csrf.getResponse().getCookie("XSRF-TOKEN");

		mockMvc.perform(post(KEYS_PATH).cookie(session, xsrf).header("X-XSRF-TOKEN", token)
				.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isCreated());
		expectProblem(mockMvc.perform(post(KEYS_PATH).cookie(session, xsrf)
				.contentType(MediaType.APPLICATION_JSON).content("{}")), 403, "Forbidden");
		expectProblem(mockMvc.perform(post(KEYS_PATH).cookie(session, xsrf).header("X-XSRF-TOKEN", "forged")
				.contentType(MediaType.APPLICATION_JSON).content("{}")), 403, "Forbidden");

		assertThat(keyRows()).isEqualTo(1);
	}

	@Test
	void anOperatorCanPromoteAKeyToPartnerAndTheLookupFollowsAtOnce() throws Exception {
		Cookie session = loginNewSession(USER_EMAIL);
		String key = createdKey(session);
		long id = keyId(key);

		assertThat(authenticator.authenticate(key)).contains(new ApiKeyPrincipal(id, ApiKeyPlan.FREE));
		jdbc.sql("update api_key set plan = 'PARTNER' where id = :id").param("id", id).update();

		assertThat(authenticator.authenticate(key)).contains(new ApiKeyPrincipal(id, ApiKeyPlan.PARTNER));
	}

	@Test
	void theTableRejectsAnUnknownPlan() throws Exception {
		long id = keyId(createdKey(loginNewSession(USER_EMAIL)));

		assertThrows(DataIntegrityViolationException.class,
				() -> jdbc.sql("update api_key set plan = 'GOLD' where id = :id").param("id", id).update());
	}

	@Test
	void theTableRejectsAHashThatIsNotLowerCaseHexSha256() throws Exception {
		long id = keyId(createdKey(loginNewSession(USER_EMAIL)));

		assertThrows(DataIntegrityViolationException.class,
				() -> jdbc.sql("update api_key set key_hash = 'abc' where id = :id").param("id", id).update());
	}

	@Test
	void theTableRejectsTwoKeysWithTheSameHash() throws Exception {
		Cookie session = loginNewSession(USER_EMAIL);
		createdKey(session);
		long second = keyId(createdKey(session));

		assertThrows(DataIntegrityViolationException.class,
				() -> jdbc.sql("update api_key set key_hash = (select key_hash from api_key where id <> :id limit 1) where id = :id")
						.param("id", second).update());
	}

	@Test
	void aKeyWithTheRightShapeButNoRowIsUnknown() {
		assertThat(authenticator.authenticate("rtp_" + "A".repeat(43))).isEmpty();
		assertThat(authenticator.authenticate("rtp_short")).isEmpty();
	}

	@Test
	void theThreeEndpointsAreInTheOpenApiDocument() throws Exception {
		String document = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse()
				.getContentAsString();

		assertThat(JsonPath.<String>read(document, "$.paths['" + KEYS_PATH + "'].post.summary")).isNotBlank();
		assertThat(JsonPath.<String>read(document, "$.paths['" + KEYS_PATH + "'].get.summary")).isNotBlank();
		assertThat(JsonPath.<String>read(document, "$.paths['" + KEYS_PATH + "/{id}'].delete.summary")).isNotBlank();
	}

	/** Same restore as {@code CsrfTokenApiTest}: {@code csrf()} swaps the shared filter's repository for good. */
	private void restoreTheRealCsrfTokenRepository() {
		for (SecurityFilterChain chain : securityFilterChains.getFilterChains()) {
			for (Filter filter : chain.getFilters()) {
				if (filter instanceof CsrfFilter csrfFilter) {
					ReflectionTestUtils.setField(csrfFilter, "tokenRepository", csrfTokenRepository);
				}
			}
		}
	}
}
