package ar.rindetuplata.curation;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;

/**
 * T6b authorization and listing rules (S12): {@code GET /api/v1/admin/wallet-rates} is admin-only and every entry
 * carries its {@code verifiedAt} and {@code daysSinceVerified}; a plain user is a 403 and an anonymous caller a 401.
 */
class CurationAuthorizationApiTest extends CurationIntegrationTest {

	@Test
	void adminListsWalletRatesWithTheirVerificationAge() throws Exception {
		Cookie admin = loginNewSession(ADMIN_EMAIL);

		mockMvc.perform(get("/api/v1/admin/wallet-rates").cookie(admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].walletId").value("naranja-x"))
				.andExpect(jsonPath("$[0].name").value("Naranja X"))
				.andExpect(jsonPath("$[0].tiers[0].tnaPercent").value(20))
				.andExpect(jsonPath("$[0].tiers[0].ceiling").value(1000000))
				.andExpect(jsonPath("$[0].tiers[0].floor").value(nullValue()))
				.andExpect(jsonPath("$[0].conditions").value(empty()))
				.andExpect(jsonPath("$[0].verifiedAt").value(SEED_VERIFIED_AT))
				.andExpect(jsonPath("$[0].daysSinceVerified").value(7))
				.andExpect(jsonPath("$[0].updatedBy").value("seed"));
	}

	@Test
	void aPlainUserIsForbidden() throws Exception {
		Cookie user = loginNewSession(USER_EMAIL);

		expectProblem(mockMvc.perform(get("/api/v1/admin/wallet-rates").cookie(user)), 403, "Forbidden");
	}

	@Test
	void anAnonymousCallerIsUnauthorized() throws Exception {
		expectProblem(mockMvc.perform(get("/api/v1/admin/wallet-rates")), 401, "Unauthorized");
	}
}
