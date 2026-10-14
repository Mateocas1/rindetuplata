package ar.rindetuplata.curation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.rindetuplata.account.RecordingMailer;
import jakarta.servlet.http.Cookie;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Shared setup of the {@code curation} tests (T6b, S12): the whole application over a Testcontainers Postgres, a
 * frozen clock, a recording mailer, the scheduler disabled and one rolled-back transaction per test. Magic-link
 * requests and logins go through the real HTTP stack, so the admin API is exercised as an admin would.
 */
@SpringBootTest(properties = {
		"rindetuplata.ingest.enabled=false",
		"rindetuplata.ingest.cron=0 0 0 1 1 *",
		"rindetuplata.auth.magic-link-url=" + CurationIntegrationTest.MAGIC_LINK_URL,
		"rindetuplata.auth.admin-emails=" + CurationIntegrationTest.ADMIN_EMAIL,
		"rindetuplata.web.allowed-origins=" + CurationIntegrationTest.ALLOWED_ORIGIN
})
@AutoConfigureMockMvc
@Import(CurationTestConfiguration.class)
@Transactional
abstract class CurationIntegrationTest {

	static final String ADMIN_EMAIL = "admin@rindetuplata.ar";
	static final String USER_EMAIL = "saver@example.com";
	static final String ALLOWED_ORIGIN = "http://localhost:4321";
	static final String MAGIC_LINK_URL = "https://app.rindetuplata.ar/ingresar";
	static final String SESSION_COOKIE = "SESSION";

	/** The frozen "today" of these tests, in America/Argentina/Buenos_Aires. */
	static final String TODAY = "2026-10-20";

	/** The verifiedAt of the Naranja X row V4 seeds: 2026-10-13, seven days before {@link #TODAY}. */
	static final String SEED_VERIFIED_AT = "2026-10-13";

	/** The 36-character value Spring Security's one-time-token service puts in the link. */
	private static final Pattern TOKEN = Pattern.compile("token=([0-9a-fA-F-]{36})");

	@Autowired
	protected MockMvc mockMvc;

	@Autowired
	protected RecordingMailer mailer;

	@BeforeEach
	void resetMailbox() {
		mailer.clear();
	}

	/** Requests a magic link, logs in with the mailed token and returns the session cookie. */
	protected Cookie loginNewSession(String email) throws Exception {
		mockMvc.perform(post("/api/v1/auth/magic-link").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\"}")).andExpect(status().isAccepted());
		MvcResult result = mockMvc.perform(post("/api/v1/auth/login").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"" + lastMailedToken() + "\"}")).andExpect(status().isOk()).andReturn();
		Cookie session = sessionCookie(result);
		assertThat(session).as("the login response sets a session cookie").isNotNull();
		return session;
	}

	private String lastMailedToken() {
		assertThat(mailer.last()).as("a magic-link email was recorded").isNotNull();
		Matcher matcher = TOKEN.matcher(mailer.last().body());
		assertThat(matcher.find()).as("the email body carries a token (%s)", mailer.last().body()).isTrue();
		return matcher.group(1);
	}

	private Cookie sessionCookie(MvcResult result) {
		for (Cookie cookie : result.getResponse().getCookies()) {
			if (SESSION_COOKIE.equals(cookie.getName())) {
				return cookie;
			}
		}
		return null;
	}

	/** {@code PUT /api/v1/admin/wallet-rates/{walletId}} with CSRF, the way the admin panel calls it. */
	protected ResultActions putWalletRate(Cookie session, String walletId, String body) throws Exception {
		MockHttpServletRequestBuilder builder = put("/api/v1/admin/wallet-rates/{walletId}", walletId)
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(body);
		return mockMvc.perform(session == null ? builder : builder.cookie(session));
	}

	/** Asserts an RFC 9457 {@code ProblemDetail} with the given status and title, and returns it for chaining. */
	protected ResultActions expectProblem(ResultActions actions, int status, String title) throws Exception {
		return actions.andExpect(status().is(status))
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(status))
				.andExpect(jsonPath("$.title").value(title));
	}
}
