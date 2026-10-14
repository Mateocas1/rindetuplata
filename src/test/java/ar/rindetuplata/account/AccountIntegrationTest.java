package ar.rindetuplata.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Shared setup of the {@code account} tests: the whole application over a Testcontainers Postgres, the scheduler
 * disabled, a recording mailer, a movable clock and one rolled-back transaction per test. Magic-link requests and
 * logins go through the real HTTP stack, so the assertions pin the public contract (status, body and cookies)
 * rather than internal storage.
 */
@SpringBootTest(properties = {
		"rindetuplata.ingest.enabled=false",
		"rindetuplata.ingest.cron=0 0 0 1 1 *",
		"rindetuplata.auth.magic-link-url=" + AccountIntegrationTest.MAGIC_LINK_URL,
		"rindetuplata.auth.admin-emails=" + AccountIntegrationTest.ADMIN_EMAIL,
		"rindetuplata.web.allowed-origins=" + AccountIntegrationTest.ALLOWED_ORIGIN
})
@AutoConfigureMockMvc
@Import(AccountTestConfiguration.class)
@Transactional
abstract class AccountIntegrationTest {

	static final String ADMIN_EMAIL = "admin@rindetuplata.ar";
	static final String USER_EMAIL = "saver@example.com";
	static final String ALLOWED_ORIGIN = "http://localhost:4321";
	static final String MAGIC_LINK_URL = "https://app.rindetuplata.ar/ingresar";
	static final String SESSION_COOKIE = "SESSION";

	/** The 36-character value Spring Security's one-time-token service puts in the link. */
	private static final Pattern TOKEN = Pattern.compile("token=([0-9a-fA-F-]{36})");

	@Autowired
	protected MockMvc mockMvc;

	@Autowired
	protected RecordingMailer mailer;

	@Autowired
	protected MutableClock clock;

	@Autowired
	protected JdbcClient jdbc;

	@BeforeEach
	void resetMailboxAndClock() {
		mailer.clear();
		clock.reset();
	}

	/** {@code POST /api/v1/auth/magic-link} with CSRF, the way the SPA calls it. */
	protected ResultActions postMagicLink(String email) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/magic-link").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\"}"));
	}

	/** {@code POST /api/v1/auth/login} with a token, optionally reusing an existing session cookie. */
	protected ResultActions login(String token, Cookie session) throws Exception {
		MockHttpServletRequestBuilder builder = post("/api/v1/auth/login").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"" + token + "\"}");
		if (session != null) {
			builder.cookie(session);
		}
		return mockMvc.perform(builder);
	}

	/** Requests a magic link, logs in with the mailed token and returns the session cookie. */
	protected Cookie loginNewSession(String email) throws Exception {
		postMagicLink(email).andExpect(status().isAccepted());
		MvcResult result = login(lastMailedToken(), null).andExpect(status().isOk()).andReturn();
		Cookie session = sessionCookie(result);
		assertThat(session).as("the login response sets a session cookie").isNotNull();
		return session;
	}

	/** The token inside the link of the last recorded email. */
	protected String lastMailedToken() {
		MailMessage message = mailer.last();
		assertThat(message).as("a magic-link email was recorded").isNotNull();
		Matcher matcher = TOKEN.matcher(message.body());
		assertThat(matcher.find()).as("the email body carries a token (%s)", message.body()).isTrue();
		return matcher.group(1);
	}

	protected Cookie sessionCookie(MvcResult result) {
		for (Cookie cookie : result.getResponse().getCookies()) {
			if (SESSION_COOKIE.equals(cookie.getName())) {
				return cookie;
			}
		}
		return null;
	}

	/** Asserts an RFC 9457 {@code ProblemDetail} with the given status and title. */
	protected void expectProblem(ResultActions actions, int status, String title) throws Exception {
		actions.andExpect(status().is(status))
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(status))
				.andExpect(jsonPath("$.title").value(title));
	}

	/** How many accounts exist. T6a exposes no endpoint that lists users, so this is the only observation. */
	protected long accountCount() {
		return jdbc.sql("select count(*) from app_user").query(Long.class).single();
	}
}
