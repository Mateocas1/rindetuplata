package ar.rindetuplata.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.rindetuplata.source.Confidence;
import ar.rindetuplata.source.dollar.DolarApiAdapter;
import ar.rindetuplata.source.dollar.DollarQuote;
import ar.rindetuplata.source.wallet.WalletTier;
import ar.rindetuplata.store.DollarQuoteWriter;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Rules for the dollar MEP break-even on {@code GET /api/v1/ranking} and
 * {@code GET /api/v1/simulations}: every ranked and simulated entry carries {@code breakEvenMep}, the response
 * carries the MEP quote it used once ({@code dollarMep}), and without a stored quote both are null and nothing else
 * changes. The recorded quote is the repository fixture (buy 1534.20, sell 1539.40, reported 2026-10-13T20:57Z) and
 * the clock says today is 2026-10-20.
 *
 * <p>Worked example: a bank at 30% TNA over 30 days returns 0.3 * 30 / 365 = 0.024658; the MEP buy price may rise
 * (1.024658 * 1539.40 / 1534.20 - 1) = 0.028130 before the dollar wins, and the break-even price is
 * 1.024658 * 1539.40 = 1577.36.
 */
class MepBreakEvenApiTest extends RankingApiIntegrationTest {

	@Autowired
	DollarQuoteWriter dollarWriter;

	@Autowired
	ObjectMapper mapper;

	@Test
	void aRankedEntryCarriesTheBreakEvenAndTheResponseTheQuoteItUsed() throws Exception {
		seedBank("Banco A", "0.30", null);
		seedMep("1534.20", "1539.40", "2026-10-14", "2026-10-13T20:57:00Z");

		mockMvc.perform(get("/api/v1/ranking"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.dollarMep.buy").value(1534.20))
				.andExpect(jsonPath("$.dollarMep.sell").value(1539.40))
				.andExpect(jsonPath("$.dollarMep.source.id").value("dolarapi-bolsa"))
				.andExpect(jsonPath("$.dollarMep.source.displayName").value("DolarApi"))
				.andExpect(jsonPath("$.dollarMep.source.official").value(false))
				.andExpect(jsonPath("$.dollarMep.quoteDate").value("2026-10-14"))
				.andExpect(jsonPath("$.dollarMep.updatedAt").value("2026-10-13T20:57:00Z"))
				.andExpect(jsonPath("$.ranked[0].breakEvenMep.devaluation").value(0.028130))
				.andExpect(jsonPath("$.ranked[0].breakEvenMep.price").value(1577.36))
				.andExpect(content().string(containsString("\"breakEvenMep\":{\"devaluation\":0.028130,\"price\":1577.36}")))
				// Nothing that was there changes.
				.andExpect(jsonPath("$.ranked[0].finalAmount").value(1024657.53))
				.andExpect(jsonPath("$.ranked[0].nominalReturn").value(0.024658))
				.andExpect(jsonPath("$.ranked[0].realReturn").value(nullValue()));
	}

	@Test
	void withoutAStoredQuoteTheFieldsAreAbsentAndNothingElseChanges() throws Exception {
		seedBank("Banco A", "0.30", null);

		mockMvc.perform(get("/api/v1/ranking"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.dollarMep").value(nullValue()))
				.andExpect(jsonPath("$.ranked[0].breakEvenMep").value(nullValue()))
				.andExpect(jsonPath("$.ranked[0].finalAmount").value(1024657.53))
				.andExpect(jsonPath("$.ranked[0].earned").value(24657.53))
				.andExpect(jsonPath("$.totals.ranked").value(1));
	}

	@Test
	void theLatestQuoteAtOrBeforeAsOfIsTheOneUsed() throws Exception {
		seedBank("Banco A", "0.30", null);
		seedMep("1500.00", "1510.00", "2026-10-14", null);
		seedMep("1534.20", "1539.40", "2026-10-19", null);
		seedMep("1900.00", "1910.00", "2026-10-25", null);

		mockMvc.perform(get("/api/v1/ranking"))
				.andExpect(jsonPath("$.dollarMep.quoteDate").value("2026-10-19"))
				.andExpect(jsonPath("$.dollarMep.sell").value(1539.40))
				.andExpect(jsonPath("$.dollarMep.updatedAt").value(nullValue()))
				.andExpect(jsonPath("$.ranked[0].breakEvenMep.price").value(1577.36));
	}

	@Test
	void aQuoteWithAZeroPriceIsNotUsed() throws Exception {
		seedBank("Banco A", "0.30", null);
		seedMep("0", "1539.40", "2026-10-14", null);

		mockMvc.perform(get("/api/v1/ranking"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.dollarMep").value(nullValue()))
				.andExpect(jsonPath("$.ranked[0].breakEvenMep").value(nullValue()));
	}

	@Test
	void ineligibleAndExcludedEntriesCarryNoBreakEven() throws Exception {
		seedBank("Banco Solo Clientes", null, "0.25");
		seedBank("Banco Largo", "0.30", null);
		seedMep("1534.20", "1539.40", "2026-10-14", null);

		// 29 days is under the 30-day minimum term: ineligible. The client-only bank is excluded.
		mockMvc.perform(get("/api/v1/ranking").param("days", "29"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.ineligible[0].name").value("Banco Largo"))
				.andExpect(jsonPath("$.ineligible[0].breakEvenMep").doesNotExist())
				.andExpect(jsonPath("$.excluded[0].breakEvenMep").doesNotExist())
				.andExpect(jsonPath("$.dollarMep.sell").value(1539.40));
	}

	@Test
	void aSimulatedEntryCarriesTheSameBreakEvenAsTheRankingAndTheQuoteOnTop() throws Exception {
		long bank = seedBank("Banco A", "0.30", null);
		seedMep("1534.20", "1539.40", "2026-10-14", "2026-10-13T20:57:00Z");

		mockMvc.perform(get("/api/v1/simulations")
				.param("instrumentId", String.valueOf(bank))
				.param("amount", "1000000")
				.param("days", "30"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("SIMULATED"))
				.andExpect(jsonPath("$.dollarMep.buy").value(1534.20))
				.andExpect(jsonPath("$.dollarMep.sell").value(1539.40))
				.andExpect(jsonPath("$.dollarMep.source.id").value("dolarapi-bolsa"))
				.andExpect(jsonPath("$.dollarMep.quoteDate").value("2026-10-14"))
				.andExpect(jsonPath("$.dollarMep.updatedAt").value("2026-10-13T20:57:00Z"))
				.andExpect(jsonPath("$.entry.breakEvenMep.devaluation").value(0.028130))
				.andExpect(jsonPath("$.entry.breakEvenMep.price").value(1577.36))
				.andExpect(jsonPath("$.entry.finalAmount").value(1024657.53));
	}

	@Test
	void everyKindSimulatesToTheBreakEvenTheRankingShows() throws Exception {
		seedBank("Banco A", "0.30", null);
		seedWallet("uala", "Ualá", List.of(new WalletTier(decimal("0"), null, decimal("0.19"))));
		seedCrypto("belo", "ARS", "0.20");
		seedRem("0.211", LocalDate.parse("2026-10-09"));
		seedMep("1534.20", "1539.40", "2026-10-14", null);

		JsonNode ranking = json(mockMvc.perform(get("/api/v1/ranking").param("days", "45")).andReturn()
				.getResponse().getContentAsString());
		assertThat(ranking.get("ranked")).hasSize(3);
		for (JsonNode entry : ranking.get("ranked")) {
			JsonNode simulation = json(mockMvc.perform(get("/api/v1/simulations")
					.param("instrumentId", entry.get("id").asString())
					.param("days", "45")).andReturn().getResponse().getContentAsString());
			assertThat(simulation.get("entry").get("breakEvenMep"))
					.as("break-even of %s", entry.get("name").asString())
					.isEqualTo(entry.get("breakEvenMep"));
			assertThat(entry.get("breakEvenMep").get("price").isNumber()).isTrue();
			assertThat(simulation.get("dollarMep")).isEqualTo(ranking.get("dollarMep"));
		}
	}

	@Test
	void withoutAStoredQuoteASimulationHasNoQuoteAndNoBreakEven() throws Exception {
		long bank = seedBank("Banco A", "0.30", null);

		mockMvc.perform(get("/api/v1/simulations").param("instrumentId", String.valueOf(bank)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("SIMULATED"))
				.andExpect(jsonPath("$.dollarMep").value(nullValue()))
				.andExpect(jsonPath("$.entry.breakEvenMep").value(nullValue()))
				.andExpect(jsonPath("$.entry.finalAmount").value(1024657.53));
	}

	@Test
	void anIneligibleSimulationCarriesNoQuote() throws Exception {
		long bank = seedBank("Banco A", "0.30", null);
		seedMep("1534.20", "1539.40", "2026-10-14", null);

		mockMvc.perform(get("/api/v1/simulations")
				.param("instrumentId", String.valueOf(bank))
				.param("days", "29"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("INELIGIBLE"))
				.andExpect(jsonPath("$.dollarMep").value(nullValue()));
	}

	@Test
	void aNewQuoteWrittenByAnIngestionRunShowsOnTheNextRequest() throws Exception {
		seedBank("Banco A", "0.30", null);
		seedMep("1500.00", "1510.00", "2026-10-14", null);
		mockMvc.perform(get("/api/v1/ranking")).andExpect(jsonPath("$.dollarMep.sell").value(1510.00));

		seedMep("1534.20", "1539.40", "2026-10-15", null);
		recordIngestionRun();

		mockMvc.perform(get("/api/v1/ranking"))
				.andExpect(jsonPath("$.dollarMep.sell").value(1539.40))
				.andExpect(jsonPath("$.ranked[0].breakEvenMep.price").value(1577.36));
	}

	private void seedMep(String buy, String sell, String captureDate, String updatedAt) {
		DollarQuote quote = new DollarQuote(DolarApiAdapter.SOURCE, decimal(buy), decimal(sell),
				updatedAt == null ? null : Instant.parse(updatedAt), FETCHED, Confidence.AGGREGATOR_AUTOMATIC,
				List.of());
		dollarWriter.write(List.of(quote), LocalDate.parse(captureDate));
	}

	private JsonNode json(String text) {
		return mapper.readTree(text);
	}
}
