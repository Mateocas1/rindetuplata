package ar.rindetuplata.api;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.rindetuplata.source.wallet.WalletTier;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * T5c rules for {@code GET /api/v1/ranking} (S3, S15, S19): the request echo, the personalized amounts and their
 * order, the S19 client switch, the NOT_OFFERED_TO_NON_CLIENTS exclusion, the kind filter, the limit with totals,
 * and the REM inflation (S10) present versus absent.
 */
class RankingApiTest extends RankingApiIntegrationTest {

	@Test
	void defaultRequestRanksAMillionOverThirtyDaysAndEchoesTheQuery() throws Exception {
		seedBank("Banco A", "0.30", null);
		seedBank("Banco B", "0.20", null);
		seedBank("Banco C", "0.10", null);

		mockMvc.perform(get("/api/v1/ranking"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.request.amount").value(1000000.00))
				.andExpect(jsonPath("$.request.days").value(30))
				.andExpect(jsonPath("$.request.limit").value(50))
				.andExpect(jsonPath("$.request.asOf").value("2026-10-20"))
				.andExpect(jsonPath("$.request.clientOf").value(empty()))
				.andExpect(jsonPath("$.request.kinds")
						.value(containsInAnyOrder("CRYPTO", "FUND", "PLAZO_FIJO", "WALLET")))
				.andExpect(jsonPath("$.inflation").value(nullValue()))
				.andExpect(jsonPath("$.ranked", hasSize(3)))
				.andExpect(jsonPath("$.ranked[0].name").value("Banco A"))
				.andExpect(jsonPath("$.ranked[0].kind").value("PLAZO_FIJO"))
				.andExpect(jsonPath("$.ranked[0].entity").value("Banco A"))
				.andExpect(jsonPath("$.ranked[0].source.id").value("argentinadatos-plazo-fijo"))
				.andExpect(jsonPath("$.ranked[0].source.displayName").value("ArgentinaDatos"))
				.andExpect(jsonPath("$.ranked[0].source.official").value(false))
				.andExpect(jsonPath("$.ranked[0].confidence").value("AGGREGATOR_AUTOMATIC"))
				.andExpect(jsonPath("$.ranked[0].verifiedAt").value(nullValue()))
				.andExpect(jsonPath("$.ranked[0].captureDate").value("2026-10-14"))
				.andExpect(jsonPath("$.ranked[0].dataDate").value("2026-10-14"))
				.andExpect(jsonPath("$.ranked[0].rateUsed.fraction").value(0.30))
				.andExpect(jsonPath("$.ranked[0].rateUsed.basis").value("TNA"))
				.andExpect(jsonPath("$.ranked[0].rateUsed.kind").value("NON_CLIENT"))
				.andExpect(jsonPath("$.ranked[0].finalAmount").value(1024657.53))
				.andExpect(jsonPath("$.ranked[0].earned").value(24657.53))
				.andExpect(jsonPath("$.ranked[0].nominalReturn").value(0.024658))
				.andExpect(jsonPath("$.ranked[0].realReturn").value(nullValue()))
				.andExpect(jsonPath("$.ranked[0].upTo").value(nullValue()))
				.andExpect(jsonPath("$.ranked[0].warnings").value(empty()))
				.andExpect(jsonPath("$.ranked[1].name").value("Banco B"))
				.andExpect(jsonPath("$.ranked[1].finalAmount").value(1016438.36))
				.andExpect(jsonPath("$.ranked[2].name").value("Banco C"))
				.andExpect(jsonPath("$.ranked[2].finalAmount").value(1008219.18))
				.andExpect(jsonPath("$.ineligible").value(empty()))
				.andExpect(jsonPath("$.excluded", hasSize(1)))
				.andExpect(jsonPath("$.excluded[0].id").value("mercado-pago"))
				.andExpect(jsonPath("$.excluded[0].kind").value("WALLET"))
				.andExpect(jsonPath("$.excluded[0].name").value("Mercado Pago"))
				.andExpect(jsonPath("$.excluded[0].reason").value("INSUFFICIENT_HISTORY"))
				.andExpect(jsonPath("$.totals.ranked").value(3))
				.andExpect(jsonPath("$.totals.ineligible").value(0))
				.andExpect(jsonPath("$.totals.excluded").value(1))
				.andExpect(content().string(containsString("\"amount\":1000000.00")))
				.andExpect(content().string(containsString("\"finalAmount\":1024657.53")))
				.andExpect(content().string(containsString("\"earned\":24657.53")))
				.andExpect(content().string(containsString("\"nominalReturn\":0.024658")))
				.andExpect(content().string(containsString("\"rateUsed\":{\"fraction\":0.300000")));
	}

	@Test
	void clientOfSwitchesABankToItsClientRate() throws Exception {
		long id = seedBank("Banco Cliente", "0.22", "0.25");

		mockMvc.perform(get("/api/v1/ranking"))
				.andExpect(jsonPath("$.ranked[0].rateUsed.kind").value("NON_CLIENT"))
				.andExpect(jsonPath("$.ranked[0].rateUsed.fraction").value(0.22))
				.andExpect(jsonPath("$.ranked[0].finalAmount").value(1018082.19));

		mockMvc.perform(get("/api/v1/ranking").param("clientOf", String.valueOf(id)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.request.clientOf").value(contains(String.valueOf(id))))
				.andExpect(jsonPath("$.ranked[0].rateUsed.kind").value("CLIENT"))
				.andExpect(jsonPath("$.ranked[0].rateUsed.fraction").value(0.25))
				.andExpect(jsonPath("$.ranked[0].finalAmount").value(1020547.95))
				.andExpect(jsonPath("$.ranked[0].earned").value(20547.95));
	}

	@Test
	void bankWithNoNonClientRateIsExcludedByDefault() throws Exception {
		long id = seedBank("Banco Solo Clientes", null, "0.25");

		mockMvc.perform(get("/api/v1/ranking"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.ranked").value(empty()))
				.andExpect(jsonPath("$.excluded", hasSize(2)))
				.andExpect(jsonPath("$.excluded[0].id").value(String.valueOf(id)))
				.andExpect(jsonPath("$.excluded[0].kind").value("PLAZO_FIJO"))
				.andExpect(jsonPath("$.excluded[0].name").value("Banco Solo Clientes"))
				.andExpect(jsonPath("$.excluded[0].source.id").value("argentinadatos-plazo-fijo"))
				.andExpect(jsonPath("$.excluded[0].captureDate").value("2026-10-14"))
				.andExpect(jsonPath("$.excluded[0].dataDate").value("2026-10-14"))
				.andExpect(jsonPath("$.excluded[0].reason").value("NOT_OFFERED_TO_NON_CLIENTS"))
				.andExpect(jsonPath("$.excluded[0].flags").value(empty()))
				.andExpect(jsonPath("$.excluded[1].reason").value("INSUFFICIENT_HISTORY"))
				.andExpect(jsonPath("$.totals.ranked").value(0))
				.andExpect(jsonPath("$.totals.excluded").value(2));
	}

	@Test
	void kindFilterSelectsOnlyTheRequestedKind() throws Exception {
		long bank = seedBank("Banco Filtro", "0.30", null);
		long wallet = seedWallet("uala", "Ualá", List.of(new WalletTier(decimal("0"), null, decimal("0.19"))));
		seedCrypto("belo", "ARS", "0.20");

		mockMvc.perform(get("/api/v1/ranking").param("kind", "WALLET"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.request.kinds").value(contains("WALLET")))
				.andExpect(jsonPath("$.ranked", hasSize(1)))
				.andExpect(jsonPath("$.ranked[0].id").value(String.valueOf(wallet)))
				.andExpect(jsonPath("$.ranked[0].kind").value("WALLET"))
				.andExpect(jsonPath("$.ranked[0].name").value("Ualá"))
				.andExpect(jsonPath("$.ranked[0].rateUsed.kind").value("WALLET_BASE"))
				.andExpect(jsonPath("$.ranked[0].finalAmount").value(1015734.89));

		mockMvc.perform(get("/api/v1/ranking").param("kind", "PLAZO_FIJO"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.request.kinds").value(contains("PLAZO_FIJO")))
				.andExpect(jsonPath("$.ranked", hasSize(1)))
				.andExpect(jsonPath("$.ranked[0].id").value(String.valueOf(bank)))
				.andExpect(jsonPath("$.ranked[0].kind").value("PLAZO_FIJO"));
	}

	@Test
	void limitTrimsThePageWhileTotalsCountEveryCandidate() throws Exception {
		seedBank("Banco A", "0.30", null);
		seedBank("Banco B", "0.20", null);
		seedBank("Banco C", "0.10", null);

		mockMvc.perform(get("/api/v1/ranking").param("limit", "2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.request.limit").value(2))
				.andExpect(jsonPath("$.ranked", hasSize(2)))
				.andExpect(jsonPath("$.ranked[0].name").value("Banco A"))
				.andExpect(jsonPath("$.ranked[1].name").value("Banco B"))
				.andExpect(jsonPath("$.totals.ranked").value(3))
				.andExpect(jsonPath("$.totals.ineligible").value(0))
				.andExpect(jsonPath("$.totals.excluded").value(1));
	}

	@Test
	void remInflationIsPresentAndDrivesTheRealReturn() throws Exception {
		seedRem("0.211", LocalDate.parse("2026-10-09"));
		seedBank("Banco REM", "0.21", null);

		mockMvc.perform(get("/api/v1/ranking"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.inflation.index").value("REM_12M_EXPECTED"))
				.andExpect(jsonPath("$.inflation.twelveMonthRate").value(0.211))
				.andExpect(jsonPath("$.inflation.observationDate").value("2026-10-09"))
				.andExpect(jsonPath("$.inflation.overHorizon").value(0.015860))
				.andExpect(jsonPath("$.ranked[0].nominalReturn").value(0.017260))
				.andExpect(jsonPath("$.ranked[0].realReturn").value(0.001379))
				.andExpect(content().string(containsString("\"twelveMonthRate\":0.211000")))
				.andExpect(content().string(containsString("\"overHorizon\":0.015860")))
				.andExpect(content().string(containsString("\"realReturn\":0.001379")));
	}

	@Test
	void withoutARemObservationTheRankingIsReturnedWithNullRealReturn() throws Exception {
		seedBank("Banco Sin REM", "0.21", null);

		mockMvc.perform(get("/api/v1/ranking"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.inflation").value(nullValue()))
				.andExpect(jsonPath("$.ranked", hasSize(1)))
				.andExpect(jsonPath("$.ranked[0].realReturn").value(nullValue()))
				.andExpect(content().string(containsString("\"realReturn\":null")));
	}
}
