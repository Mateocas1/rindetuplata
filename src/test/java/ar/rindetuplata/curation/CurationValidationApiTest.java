package ar.rindetuplata.curation;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * T6b rules for the admin write path validation (S12): every rule answers a 400 {@code ProblemDetail} that names the
 * offending field, an unknown id on confirm or audit is a 404, and a state-changing request without CSRF is a 403.
 */
class CurationValidationApiTest extends CurationIntegrationTest {

	private static final String VALID = """
			{"name":"Test Wallet","tiers":[{"ceiling":1000000,"tnaPercent":25}],"conditions":[],"note":null}
			""";

	private Cookie admin;

	@BeforeEach
	void loginAsAdmin() throws Exception {
		admin = loginNewSession(ADMIN_EMAIL);
	}

	@Test
	void anIdOutsideThePatternIsABadRequestNamingWalletId() throws Exception {
		expectBadRequest("walletId", "BAD_ID", VALID);
		expectBadRequest("walletId", "a", VALID);
		expectBadRequest("walletId", "a".repeat(41), VALID);
	}

	@Test
	void aBlankNameIsABadRequestNamingName() throws Exception {
		expectBadRequest("name", "test-wallet", """
				{"name":"  ","tiers":[{"ceiling":1000000,"tnaPercent":25}],"conditions":[]}
				""");
	}

	@Test
	void anEmptyTierListIsABadRequestNamingTiers() throws Exception {
		expectBadRequest("tiers", "test-wallet", """
				{"name":"Test Wallet","tiers":[],"conditions":[]}
				""");
	}

	@Test
	void aTnaPercentOutsideZeroToThreeHundredIsABadRequestNamingTheTierField() throws Exception {
		expectBadRequest("tiers[0].tnaPercent", "test-wallet", """
				{"name":"Test Wallet","tiers":[{"ceiling":1000000,"tnaPercent":0}],"conditions":[]}
				""");
		expectBadRequest("tiers[0].tnaPercent", "test-wallet", """
				{"name":"Test Wallet","tiers":[{"ceiling":1000000,"tnaPercent":301}],"conditions":[]}
				""");
	}

	@Test
	void aNegativeFloorIsABadRequestNamingTheTierField() throws Exception {
		expectBadRequest("tiers[0].floor", "test-wallet", """
				{"name":"Test Wallet","tiers":[{"floor":-1,"ceiling":1000000,"tnaPercent":25}],"conditions":[]}
				""");
	}

	@Test
	void aCeilingNotAboveTheFloorIsABadRequestNamingTheTierField() throws Exception {
		expectBadRequest("tiers[0].ceiling", "test-wallet", """
				{"name":"Test Wallet","tiers":[{"floor":1000000,"ceiling":1000000,"tnaPercent":25}],"conditions":[]}
				""");
	}

	@Test
	void overlappingTiersAreABadRequestNamingTiers() throws Exception {
		expectBadRequest("tiers", "test-wallet", """
				{"name":"Test Wallet","tiers":[{"ceiling":1000000,"tnaPercent":25},
				 {"floor":500000,"ceiling":2000000,"tnaPercent":20}],"conditions":[]}
				""");
	}

	@Test
	void unorderedTiersAreABadRequestNamingTiers() throws Exception {
		expectBadRequest("tiers", "test-wallet", """
				{"name":"Test Wallet","tiers":[{"floor":1000,"ceiling":2000,"tnaPercent":25},
				 {"floor":500,"ceiling":600,"tnaPercent":20}],"conditions":[]}
				""");
	}

	@Test
	void aConditionWithoutALabelIsABadRequestNamingTheCondition() throws Exception {
		expectBadRequest("conditions[0].condition", "test-wallet", """
				{"name":"Test Wallet","tiers":[{"ceiling":1000000,"tnaPercent":25}],
				 "conditions":[{"condition":"","tiers":[{"ceiling":null,"tnaPercent":30}]}]}
				""");
	}

	@Test
	void anInvalidConditionTierIsABadRequestNamingItsField() throws Exception {
		expectBadRequest("conditions[0].tiers[0].tnaPercent", "test-wallet", """
				{"name":"Test Wallet","tiers":[{"ceiling":1000000,"tnaPercent":25}],
				 "conditions":[{"condition":"Si consumís $1","tiers":[{"ceiling":null,"tnaPercent":0}]}]}
				""");
	}

	@Test
	void anUnknownIdOnConfirmOrAuditIsANotFound() throws Exception {
		expectProblem(mockMvc.perform(post("/api/v1/admin/wallet-rates/{walletId}/confirm", "no-such-wallet")
				.with(csrf()).cookie(admin)), 404, "Not Found")
				.andExpect(jsonPath("$.parameter").value("walletId"));
		expectProblem(mockMvc.perform(get("/api/v1/admin/wallet-rates/{walletId}/audit", "no-such-wallet")
				.cookie(admin)), 404, "Not Found")
				.andExpect(jsonPath("$.parameter").value("walletId"));
	}

	@Test
	void aStateChangingRequestWithoutCsrfIsForbidden() throws Exception {
		expectProblem(mockMvc.perform(put("/api/v1/admin/wallet-rates/{walletId}", "test-wallet")
				.cookie(admin).contentType(MediaType.APPLICATION_JSON).content(VALID)), 403, "Forbidden");
		expectProblem(mockMvc.perform(post("/api/v1/admin/wallet-rates/{walletId}/confirm", "naranja-x")
				.cookie(admin)), 403, "Forbidden");
	}

	private void expectBadRequest(String parameter, String walletId, String body) throws Exception {
		expectProblem(putWalletRate(admin, walletId, body), 400, "Bad Request")
				.andExpect(jsonPath("$.parameter").value(parameter));
	}
}
