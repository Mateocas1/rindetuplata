package ar.rindetuplata.account;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * T11a (S4): the authorities of a session are recomputed from the configured admin emails on every request, so an
 * email removed from {@code rindetuplata.auth.admin-emails} loses {@code /api/v1/admin/**} and its {@code ROLE_ADMIN}
 * in {@code GET /api/v1/me} without a new login, while the session keeps working. The test bean
 * {@link MutableAccountRoles} stands for that configuration change.
 */
class RolesRefreshApiTest extends AccountIntegrationTest {

	@Autowired
	private MutableAccountRoles roles;

	@AfterEach
	void restoreRoles() {
		roles.reset();
	}

	@Test
	void anAdminRevokedAfterLoginGets403OnAdminPathsAndMeShowsTheCurrentRoles() throws Exception {
		Cookie session = loginNewSession(ADMIN_EMAIL);
		mockMvc.perform(get("/api/v1/admin/wallet-rates").cookie(session)).andExpect(status().isOk());

		roles.revokeAdmin(ADMIN_EMAIL);

		expectProblem(mockMvc.perform(get("/api/v1/admin/wallet-rates").cookie(session)), 403, "Forbidden");
		mockMvc.perform(get("/api/v1/me").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value(ADMIN_EMAIL))
				.andExpect(jsonPath("$.roles", hasItem("ROLE_USER")))
				.andExpect(jsonPath("$.roles", not(hasItem("ROLE_ADMIN"))));
	}

	@Test
	void aSessionStillAdminInTheConfigurationKeepsAdminAccess() throws Exception {
		Cookie session = loginNewSession(ADMIN_EMAIL);

		mockMvc.perform(get("/api/v1/admin/wallet-rates").cookie(session)).andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/me").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.roles", hasItem("ROLE_ADMIN")));
	}

	@Test
	void aRequestWithoutASessionStaysUnauthorized() throws Exception {
		expectProblem(mockMvc.perform(get("/api/v1/me")), 401, "Unauthorized");
		expectProblem(mockMvc.perform(get("/api/v1/admin/wallet-rates")), 401, "Unauthorized");
	}
}
