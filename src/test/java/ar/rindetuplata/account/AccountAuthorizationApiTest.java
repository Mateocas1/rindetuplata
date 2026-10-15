package ar.rindetuplata.account;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;

/**
 * T6a authorization rules (S4, S13): {@code /api/v1/me} is 401 without a session and returns the email and roles
 * with one; an email in {@code rindetuplata.auth.admin-emails} gets {@code ROLE_ADMIN} and no other email does;
 * {@code /api/v1/admin/**} is a 403 {@code ProblemDetail} for a plain user.
 */
class AccountAuthorizationApiTest extends AccountIntegrationTest {

	@Test
	void meIsUnauthorizedWithoutASession() throws Exception {
		expectProblem(mockMvc.perform(get("/api/v1/me")), 401, "Unauthorized");
	}

	@Test
	void meReturnsTheEmailAndRolesOfTheLoggedInUser() throws Exception {
		Cookie session = loginNewSession(USER_EMAIL);

		mockMvc.perform(get("/api/v1/me").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value(USER_EMAIL))
				.andExpect(jsonPath("$.roles", hasItem("ROLE_USER")))
				.andExpect(jsonPath("$.roles", not(hasItem("ROLE_ADMIN"))));
	}

	@Test
	void anAdminEmailGetsRoleAdmin() throws Exception {
		Cookie session = loginNewSession(ADMIN_EMAIL);

		mockMvc.perform(get("/api/v1/me").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value(ADMIN_EMAIL))
				.andExpect(jsonPath("$.roles", hasItem("ROLE_ADMIN")));
	}

	@Test
	void adminPathsAreForbiddenForAPlainUser() throws Exception {
		Cookie session = loginNewSession(USER_EMAIL);

		expectProblem(mockMvc.perform(get("/api/v1/admin/wallet-rates").cookie(session)), 403, "Forbidden");
	}

	@Test
	void anAdminPassesAuthorizationIntoTheCurationApiT6bAdded() throws Exception {
		Cookie session = loginNewSession(ADMIN_EMAIL);

		// T6a asserted the placeholder 404 of the then-unmapped path; T6b mapped it, so the same request must now
		// reach the handler and answer 200, which still proves the ROLE_ADMIN check passed.
		mockMvc.perform(get("/api/v1/admin/wallet-rates").cookie(session))
				.andExpect(status().isOk());
	}
}
