package ar.rindetuplata.curation;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;

/**
 * T6b rules for the curation write path (S12): a PUT creates or replaces a wallet rate, stamps today's verification
 * and the admin email, writes an audit row and publishes today's wallet snapshot so the ranking shows it at once; a
 * confirm re-verifies the same tiers; and the audit lists the actions newest first.
 */
class CurationWalletRateApiTest extends CurationIntegrationTest {

	private static final String CREATE = """
			{"name":"Test Wallet","tiers":[{"ceiling":1000000,"tnaPercent":25}],"conditions":[],"note":"Added by an admin"}
			""";

	private static final String REPLACE = """
			{"name":"Test Wallet","tiers":[{"ceiling":1000000,"tnaPercent":10}],"conditions":[],"note":"Replaced by an admin"}
			""";

	@Test
	void putCreatesAWalletRateAndPublishesTodaysSnapshot() throws Exception {
		Cookie admin = loginNewSession(ADMIN_EMAIL);

		putWalletRate(admin, "test-wallet", CREATE)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.walletId").value("test-wallet"))
				.andExpect(jsonPath("$.name").value("Test Wallet"))
				.andExpect(jsonPath("$.tiers[0].tnaPercent").value(25))
				.andExpect(jsonPath("$.tiers[0].ceiling").value(1000000))
				.andExpect(jsonPath("$.verifiedAt").value(TODAY))
				.andExpect(jsonPath("$.daysSinceVerified").value(0))
				.andExpect(jsonPath("$.updatedBy").value(ADMIN_EMAIL));

		// The list shows the new rate next to the seed, verified today.
		mockMvc.perform(get("/api/v1/admin/wallet-rates").cookie(admin))
				.andExpect(jsonPath("$", hasSize(2)))
				.andExpect(jsonPath("$[1].walletId").value("test-wallet"))
				.andExpect(jsonPath("$[1].verifiedAt").value(TODAY))
				.andExpect(jsonPath("$[1].daysSinceVerified").value(0))
				.andExpect(jsonPath("$[1].updatedBy").value(ADMIN_EMAIL));

		// The audit records who created it and what was stored.
		mockMvc.perform(get("/api/v1/admin/wallet-rates/{walletId}/audit", "test-wallet").cookie(admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].walletId").value("test-wallet"))
				.andExpect(jsonPath("$[0].action").value("CREATED"))
				.andExpect(jsonPath("$[0].actor").value(ADMIN_EMAIL))
				.andExpect(jsonPath("$[0].payload.tiers[0].tnaPercent").value(25))
				.andExpect(jsonPath("$[0].payload.verifiedAt").value(TODAY));

		// Today's wallet snapshot is in the ranking without waiting for the next ingestion.
		mockMvc.perform(get("/api/v1/ranking").param("kind", "WALLET"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.ranked", hasSize(1)))
				.andExpect(jsonPath("$.ranked[0].name").value("Test Wallet"))
				.andExpect(jsonPath("$.ranked[0].kind").value("WALLET"))
				.andExpect(jsonPath("$.ranked[0].rateUsed.fraction").value(0.25))
				.andExpect(jsonPath("$.ranked[0].captureDate").value(TODAY))
				.andExpect(jsonPath("$.ranked[0].verifiedAt").value(TODAY));
	}

	@Test
	void putReplacesAnExistingWalletRateAndItsSnapshot() throws Exception {
		Cookie admin = loginNewSession(ADMIN_EMAIL);
		putWalletRate(admin, "test-wallet", CREATE).andExpect(status().isOk());

		putWalletRate(admin, "test-wallet", REPLACE).andExpect(status().isOk())
				.andExpect(jsonPath("$.tiers[0].tnaPercent").value(10));

		mockMvc.perform(get("/api/v1/admin/wallet-rates").cookie(admin))
				.andExpect(jsonPath("$", hasSize(2)))
				.andExpect(jsonPath("$[1].tiers[0].tnaPercent").value(10));

		mockMvc.perform(get("/api/v1/admin/wallet-rates/{walletId}/audit", "test-wallet").cookie(admin))
				.andExpect(jsonPath("$", hasSize(2)))
				.andExpect(jsonPath("$[0].action").value("REPLACED"));

		// The same (instrument, capture date) row was replaced: one wallet for today, at the new rate.
		mockMvc.perform(get("/api/v1/ranking").param("kind", "WALLET"))
				.andExpect(jsonPath("$.ranked", hasSize(1)))
				.andExpect(jsonPath("$.ranked[0].name").value("Test Wallet"))
				.andExpect(jsonPath("$.ranked[0].rateUsed.fraction").value(0.10));
	}

	@Test
	void confirmReverifiesTodayWithoutChangingTheTiers() throws Exception {
		Cookie admin = loginNewSession(ADMIN_EMAIL);

		mockMvc.perform(post("/api/v1/admin/wallet-rates/{walletId}/confirm", "naranja-x").with(csrf()).cookie(admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.walletId").value("naranja-x"))
				.andExpect(jsonPath("$.verifiedAt").value(TODAY))
				.andExpect(jsonPath("$.daysSinceVerified").value(0))
				.andExpect(jsonPath("$.tiers[0].tnaPercent").value(20))
				.andExpect(jsonPath("$.tiers[0].ceiling").value(1000000))
				.andExpect(jsonPath("$.updatedBy").value(ADMIN_EMAIL));

		mockMvc.perform(get("/api/v1/admin/wallet-rates").cookie(admin))
				.andExpect(jsonPath("$[0].verifiedAt").value(TODAY))
				.andExpect(jsonPath("$[0].daysSinceVerified").value(0));

		mockMvc.perform(get("/api/v1/admin/wallet-rates/{walletId}/audit", "naranja-x").cookie(admin))
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].action").value("CONFIRMED"))
				.andExpect(jsonPath("$[0].payload.tiers[0].tnaPercent").value(20));

		mockMvc.perform(get("/api/v1/ranking").param("kind", "WALLET"))
				.andExpect(jsonPath("$.ranked[0].name").value("Naranja X"))
				.andExpect(jsonPath("$.ranked[0].rateUsed.fraction").value(0.20))
				.andExpect(jsonPath("$.ranked[0].captureDate").value(TODAY))
				.andExpect(jsonPath("$.ranked[0].verifiedAt").value(TODAY));
	}

	@Test
	void auditListsActionsNewestFirst() throws Exception {
		Cookie admin = loginNewSession(ADMIN_EMAIL);

		putWalletRate(admin, "test-wallet", CREATE).andExpect(status().isOk());
		putWalletRate(admin, "test-wallet", REPLACE).andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/admin/wallet-rates/{walletId}/confirm", "test-wallet").with(csrf()).cookie(admin))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/admin/wallet-rates/{walletId}/audit", "test-wallet").cookie(admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(3)))
				.andExpect(jsonPath("$[*].action").value(contains("CONFIRMED", "REPLACED", "CREATED")));
	}
}
