package ar.rindetuplata.api;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.rindetuplata.store.InstrumentKind;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * T5b rules for {@code GET /api/v1/instruments}: the optional {@code kind} filter, the latest snapshot of every
 * instrument (rate, basis, client rate, capture date, data date, confidence, verification date and flags), the
 * latest quote of a fund class (date and vcp, money scale 2), and the exact JSON scales.
 */
class InstrumentListApiTest extends ApiIntegrationTest {

	private static final String PROVINCIA = "BANCO DE LA PROVINCIA DE BUENOS AIRES";
	private static final String MERCADO_FONDO = "Mercado Fondo - Clase A";

	@Test
	void bankRowsCarryTheLatestSnapshotWithItsDatesConfidenceAndScales() throws Exception {
		seedAll();

		int expected = (int) ApiFixtures.banks().stream().map(bank -> bank.bankName()).distinct().count();
		mockMvc.perform(get("/api/v1/instruments").param("kind", "PLAZO_FIJO_BANK"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(expected)))
				.andExpect(jsonPath("$[?(@.displayName=='" + PROVINCIA + "')].id").value(hasSize(1)))
				.andExpect(jsonPath("$[?(@.displayName=='" + PROVINCIA + "')].kind").value(hasItem("PLAZO_FIJO_BANK")))
				.andExpect(jsonPath("$[?(@.displayName=='" + PROVINCIA + "')].entityName").value(hasItem(PROVINCIA)))
				.andExpect(jsonPath("$[?(@.displayName=='" + PROVINCIA + "')].sourceId")
						.value(hasItem("argentinadatos-plazo-fijo")))
				.andExpect(jsonPath("$[?(@.displayName=='" + PROVINCIA + "')].segment").value(hasItem(nullValue())))
				.andExpect(jsonPath("$[?(@.displayName=='" + PROVINCIA + "')].rate").value(hasItem(0.22)))
				.andExpect(jsonPath("$[?(@.displayName=='" + PROVINCIA + "')].clientRate").value(hasItem(0.21)))
				.andExpect(jsonPath("$[?(@.displayName=='" + PROVINCIA + "')].basis").value(hasItem("TNA")))
				.andExpect(jsonPath("$[?(@.displayName=='" + PROVINCIA + "')].captureDate")
						.value(hasItem("2026-10-14")))
				.andExpect(jsonPath("$[?(@.displayName=='" + PROVINCIA + "')].dataDate").value(hasItem("2026-10-14")))
				.andExpect(jsonPath("$[?(@.displayName=='" + PROVINCIA + "')].confidence")
						.value(hasItem("AGGREGATOR_AUTOMATIC")))
				.andExpect(jsonPath("$[?(@.displayName=='" + PROVINCIA + "')].verifiedAt")
						.value(hasItem(nullValue())))
				.andExpect(jsonPath("$[?(@.displayName=='" + PROVINCIA + "')].flags").value(hasItem(hasSize(0))))
				.andExpect(jsonPath("$[?(@.displayName=='" + PROVINCIA + "')].invalid").value(hasItem(false)))
				.andExpect(jsonPath("$[?(@.displayName=='" + PROVINCIA + "')].quoteDate")
						.value(hasItem(nullValue())))
				.andExpect(jsonPath("$[?(@.displayName=='" + PROVINCIA + "')].vcp").value(hasItem(nullValue())))
				.andExpect(content().string(containsString("\"rate\":0.220000")))
				.andExpect(content().string(containsString("\"clientRate\":0.210000")));
	}

	@Test
	void fundsCarryTheirLatestQuoteDateAndVcpInsteadOfARate() throws Exception {
		seedAll();

		int expected = (int) ApiFixtures.fundQuotes("argentinadatos/fci-mercado-dinero-ultimo.json").stream()
				.map(quote -> quote.name()).distinct().count() + 1; // plus the hand-made fallen class
		mockMvc.perform(get("/api/v1/instruments").param("kind", "FUND_CLASS"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(expected)))
				.andExpect(jsonPath("$[?(@.displayName=='" + MERCADO_FONDO + "')].kind").value(hasItem("FUND_CLASS")))
				.andExpect(jsonPath("$[?(@.displayName=='" + MERCADO_FONDO + "')].entityName")
						.value(hasItem("Mercado Fondo")))
				.andExpect(jsonPath("$[?(@.displayName=='" + MERCADO_FONDO + "')].sourceId")
						.value(hasItem("argentinadatos-fci-mercado-dinero")))
				.andExpect(jsonPath("$[?(@.displayName=='" + MERCADO_FONDO + "')].segment").value(hasItem("SAVER")))
				.andExpect(jsonPath("$[?(@.displayName=='" + MERCADO_FONDO + "')].rate").value(hasItem(nullValue())))
				.andExpect(jsonPath("$[?(@.displayName=='" + MERCADO_FONDO + "')].basis").value(hasItem(nullValue())))
				.andExpect(jsonPath("$[?(@.displayName=='" + MERCADO_FONDO + "')].clientRate")
						.value(hasItem(nullValue())))
				.andExpect(jsonPath("$[?(@.displayName=='" + MERCADO_FONDO + "')].captureDate")
						.value(hasItem(nullValue())))
				.andExpect(jsonPath("$[?(@.displayName=='" + MERCADO_FONDO + "')].dataDate")
						.value(hasItem("2026-10-09")))
				.andExpect(jsonPath("$[?(@.displayName=='" + MERCADO_FONDO + "')].quoteDate")
						.value(hasItem("2026-10-09")))
				.andExpect(jsonPath("$[?(@.displayName=='" + MERCADO_FONDO + "')].vcp").value(hasItem(25937.05)))
				.andExpect(jsonPath("$[?(@.displayName=='" + MERCADO_FONDO + "')].confidence")
						.value(hasItem("AGGREGATOR_AUTOMATIC")))
				.andExpect(jsonPath("$[?(@.displayName=='" + MERCADO_FONDO + "')].invalid").value(hasItem(false)))
				.andExpect(jsonPath("$[?(@.displayName=='" + MERCADO_FONDO + "')].flags").value(hasItem(hasSize(0))))
				.andExpect(content().string(containsString(
						"\"rate\":null,\"basis\":null,\"clientRate\":null,\"captureDate\":null,"
								+ "\"dataDate\":\"2026-10-09\"")))
				.andExpect(content().string(containsString("\"vcp\":25937.05")));
	}

	@Test
	void officialRowsCarryTheirFlagsAndAreListedWithTheirOwnDataDate() throws Exception {
		seedAll();

		int officialInstruments = reader.instrumentCount(InstrumentKind.PLAZO_FIJO_OFFICIAL);
		mockMvc.perform(get("/api/v1/instruments").param("kind", "PLAZO_FIJO_OFFICIAL"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(officialInstruments)))
				// BRUBANK's "PLAZO FIJO EN DOLARES" is the first row of the recorded response.
				.andExpect(jsonPath("$[0].kind").value("PLAZO_FIJO_OFFICIAL"))
				.andExpect(jsonPath("$[0].displayName").value("PLAZO FIJO EN DOLARES"))
				.andExpect(jsonPath("$[0].entityName").value("BRUBANK S.A.U."))
				.andExpect(jsonPath("$[0].sourceId").value("bcra-transparencia-plazos-fijos"))
				.andExpect(jsonPath("$[0].segment").value("FOREIGN_CURRENCY"))
				.andExpect(jsonPath("$[0].rate").value(0.001))
				.andExpect(jsonPath("$[0].basis").value("TEA"))
				.andExpect(jsonPath("$[0].clientRate").value(nullValue()))
				.andExpect(jsonPath("$[0].captureDate").value("2026-10-14"))
				.andExpect(jsonPath("$[0].dataDate").value("2026-10-13"))
				.andExpect(jsonPath("$[0].confidence").value("OFFICIAL_AUTOMATIC"))
				.andExpect(jsonPath("$[0].verifiedAt").value(nullValue()))
				.andExpect(jsonPath("$[0].invalid").value(false))
				.andExpect(jsonPath("$[0].flags", hasSize(1)))
				.andExpect(jsonPath("$[0].flags[0].code").value("TERM_CODE_UNDOCUMENTED"))
				.andExpect(jsonPath("$[0].flags[0].severity").value("WARNING"))
				// A rate of zero is kept and flagged INVALID, never dropped (S12).
				.andExpect(jsonPath("$[?(@.invalid==true)]", hasSize(
						(int) ApiFixtures.official().stream().filter(record -> record.isInvalid()).count())))
				.andExpect(content().string(containsString("\"rate\":0.001000")));
	}

	@Test
	void walletRowsCarryTheirConfidenceAndTheCuratedVerificationDate() throws Exception {
		seedAll();
		List<String> expected = List.of("Naranja X", "Ualá");

		mockMvc.perform(get("/api/v1/instruments").param("kind", "WALLET"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(2)))
				.andExpect(jsonPath("$[*].displayName").value(containsInAnyOrder(expected.toArray())))
				.andExpect(jsonPath("$[?(@.displayName=='Naranja X')].rate").value(hasItem(0.2)))
				.andExpect(jsonPath("$[?(@.displayName=='Naranja X')].sourceId")
						.value(hasItem("curated-wallet-rates")))
				.andExpect(jsonPath("$[?(@.displayName=='Naranja X')].dataDate").value(hasItem("2026-10-13")))
				.andExpect(jsonPath("$[?(@.displayName=='Naranja X')].verifiedAt").value(hasItem("2026-10-13")))
				.andExpect(jsonPath("$[?(@.displayName=='Naranja X')].confidence")
						.value(hasItem("CURATED_MANUAL")))
				.andExpect(jsonPath("$[?(@.displayName=='Ualá')].rate").value(hasItem(0.19)))
				.andExpect(jsonPath("$[?(@.displayName=='Ualá')].sourceId").value(hasItem("uala-cuenta-remunerada")))
				.andExpect(jsonPath("$[?(@.displayName=='Ualá')].verifiedAt").value(hasItem(nullValue())))
				.andExpect(jsonPath("$[?(@.displayName=='Ualá')].confidence")
						.value(hasItem("SCRAPED_ENTITY_WEB")))
				.andExpect(content().string(containsString("\"rate\":0.200000")))
				.andExpect(content().string(containsString("\"rate\":0.190000")));
	}

	@Test
	void withoutKindEveryKindIsListed() throws Exception {
		seedAll();

		int expected = 0;
		for (InstrumentKind kind : InstrumentKind.values()) {
			expected += reader.instrumentCount(kind);
		}
		mockMvc.perform(get("/api/v1/instruments"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(expected)))
				.andExpect(jsonPath("$[*].kind", hasItems(
						"PLAZO_FIJO_OFFICIAL", "PLAZO_FIJO_BANK", "FUND_CLASS", "CRYPTO_YIELD", "WALLET")));
	}
}
