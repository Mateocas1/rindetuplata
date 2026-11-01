package ar.rindetuplata.account;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** Helpers of the API-key tests (T6d): the real login of {@link AccountIntegrationTest} and the three endpoints. */
abstract class ApiKeyIntegrationTest extends AccountIntegrationTest {

	static final String KEYS_PATH = "/api/v1/account/api-keys";

	static final String OTHER_EMAIL = "otro@example.com";

	protected ResultActions createKey(Cookie session, String body) throws Exception {
		return mockMvc.perform(post(KEYS_PATH).cookie(session).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	/** Creates a key and returns the plaintext shown once. */
	protected String createdKey(Cookie session) throws Exception {
		String response = createKey(session, "{}").andExpect(status().isCreated()).andReturn().getResponse()
				.getContentAsString();
		return JsonPath.read(response, "$.key");
	}

	protected ResultActions listKeys(Cookie session) throws Exception {
		return mockMvc.perform(get(KEYS_PATH).cookie(session));
	}

	protected ResultActions revokeKey(Cookie session, long id) throws Exception {
		return mockMvc.perform(delete(KEYS_PATH + "/" + id).cookie(session).with(csrf()));
	}

	protected long keyId(String plaintext) {
		return jdbc.sql("select id from api_key where key_hash = :hash")
				.param("hash", sha256(plaintext)).query(Long.class).single();
	}

	protected long keyRows() {
		return jdbc.sql("select count(*) from api_key").query(Long.class).single();
	}

	protected long activeKeyRows(String email) {
		return jdbc.sql("""
				select count(*) from api_key k join app_user u on u.id = k.user_id
				where u.email = :email and k.revoked_at is null
				""").param("email", email).query(Long.class).single();
	}

	protected static String sha256(String text) {
		try {
			byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
					.digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
			return java.util.HexFormat.of().formatHex(digest);
		}
		catch (java.security.NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
