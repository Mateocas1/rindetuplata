package ar.rindetuplata.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * T6a login rules (S13): a valid token logs in and creates the account only then; an unknown, used or expired
 * token is a 401 {@code ProblemDetail}. The token also works as a form parameter, because the magic-link page may
 * post a plain form.
 */
class MagicLinkLoginApiTest extends AccountIntegrationTest {

	@Test
	void aValidTokenLogsInAndCreatesTheAccountOnlyThen() throws Exception {
		assertThat(accountCount()).as("no account before any login").isZero();

		postMagicLink(USER_EMAIL).andExpect(status().isAccepted());
		assertThat(accountCount()).as("the magic-link request creates no account").isZero();

		login(lastMailedToken(), null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value(USER_EMAIL))
				.andExpect(jsonPath("$.roles").isArray());

		assertThat(accountCount()).as("the first login creates the account").isEqualTo(1);
	}

	@Test
	void aLoginRotatesTheSessionSoAnEarlierSessionIdNoLongerAuthenticates() throws Exception {
		Cookie earlier = loginNewSession(USER_EMAIL);
		postMagicLink(USER_EMAIL).andExpect(status().isAccepted());

		MvcResult result = login(lastMailedToken(), earlier).andExpect(status().isOk()).andReturn();
		Cookie rotated = sessionCookie(result);

		assertThat(rotated).as("the login answers a new session cookie").isNotNull();
		assertThat(rotated.getValue()).as("the session id changes on login").isNotEqualTo(earlier.getValue());
		mockMvc.perform(get("/api/v1/me").cookie(earlier)).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/me").cookie(rotated)).andExpect(status().isOk());
	}

	@Test
	void aLongValidEmailCanRequestALinkAndLogIn() throws Exception {
		String longEmail = "nombre.apellido.segundo.apellido.largo@consultora-ejemplo-servicios-financieros.com.ar";
		assertThat(longEmail).hasSizeGreaterThan(80);

		postMagicLink(longEmail).andExpect(status().isAccepted());
		Cookie session = sessionCookie(login(lastMailedToken(), null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value(longEmail))
				.andReturn());

		mockMvc.perform(get("/api/v1/me").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value(longEmail));
	}

	@Test
	void aUsedTokenIsUnauthorized() throws Exception {
		postMagicLink(USER_EMAIL);
		String token = lastMailedToken();
		login(token, null).andExpect(status().isOk());

		expectProblem(login(token, null), 401, "Unauthorized");
	}

	@Test
	void anExpiredTokenIsUnauthorized() throws Exception {
		postMagicLink(USER_EMAIL);
		String token = lastMailedToken();

		clock.advance(Duration.ofMinutes(16));

		expectProblem(login(token, null), 401, "Unauthorized");
	}

	@Test
	void anUnknownTokenIsUnauthorized() throws Exception {
		expectProblem(login("00000000-0000-0000-0000-000000000000", null), 401, "Unauthorized");
	}

	@Test
	void aTokenAlsoWorksAsAFormParameter() throws Exception {
		postMagicLink(USER_EMAIL);

		mockMvc.perform(post("/api/v1/auth/login").with(csrf())
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.param("token", lastMailedToken()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value(USER_EMAIL));
	}

	@Test
	void aReturningLoginKeepsTheSingleAccountAndRecordsTheLoginTime() throws Exception {
		loginNewSession(USER_EMAIL);
		clock.advance(Duration.ofHours(2));
		postMagicLink(USER_EMAIL).andExpect(status().isAccepted());

		login(lastMailedToken(), null).andExpect(status().isOk()).andExpect(jsonPath("$.email").value(USER_EMAIL));

		assertThat(accountCount()).as("a second login creates no second account").isEqualTo(1);
		assertThat(jdbc.sql("select last_login_at > created_at from app_user where email = :email")
				.param("email", USER_EMAIL).query(Boolean.class).single()).isTrue();
	}
}
