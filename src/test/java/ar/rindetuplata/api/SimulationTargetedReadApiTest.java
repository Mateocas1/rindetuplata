package ar.rindetuplata.api;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.rindetuplata.source.Confidence;
import ar.rindetuplata.source.Segment;
import ar.rindetuplata.source.fund.ArgentinaDatosFundAdapter;
import ar.rindetuplata.source.fund.FundQuote;
import ar.rindetuplata.source.wallet.UalaWalletAdapter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Rule: {@code GET /api/v1/simulations} reads only what the requested instrument needs. The proof is
 * observable over HTTP: rows of the other kinds that the ranking cannot read (an unknown confidence) are stored, so
 * the full basket throws, and the targeted simulation of an instrument that does not depend on them still answers.
 */
class SimulationTargetedReadApiTest extends CachedRankingSupport {

	@Test
	void simulatingABankNeverReadsFundsWalletsOrCrypto() throws Exception {
		long bank = seedBank("Banco A", "0.30", null);
		poisonedFundClass("Poison Fondo - Clase A");
		poisonedRate("WALLET", UalaWalletAdapter.SOURCE.id(), "poison-wallet");
		poisonedRate("CRYPTO_YIELD", "argentinadatos-crypto", "Poison|ARS");

		assertThatThrownBy(() -> mockMvc.perform(get("/api/v1/ranking").with(fromThisClient())))
				.as("the poison really breaks the full basket").hasRootCauseInstanceOf(IllegalArgumentException.class);

		simulate(bank)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("SIMULATED"))
				.andExpect(jsonPath("$.entry.id").value(String.valueOf(bank)))
				.andExpect(jsonPath("$.entry.finalAmount").value(1024657.53));
	}

	@Test
	void simulatingAFundClassNeverReadsBanksWalletsOrCrypto() throws Exception {
		long fund = seedFundClass("Fondo Uno - Clase A");
		poisonedRate("PLAZO_FIJO_BANK", "poison-source", "Poison Banco");
		poisonedRate("WALLET", UalaWalletAdapter.SOURCE.id(), "poison-wallet");
		poisonedRate("CRYPTO_YIELD", "argentinadatos-crypto", "Poison|ARS");

		simulate(fund)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("SIMULATED"))
				.andExpect(jsonPath("$.entry.id").value(String.valueOf(fund)))
				.andExpect(jsonPath("$.entry.kind").value("FUND"));
	}

	@Test
	void simulatingAFundClassOnlyReadsThatClassSeries() throws Exception {
		long fund = seedFundClass("Fondo Uno - Clase A");
		poisonedFundClass("Poison Fondo - Clase A");

		simulate(fund).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SIMULATED"));
	}

	private org.springframework.test.web.servlet.ResultActions simulate(long id) throws Exception {
		return mockMvc.perform(get("/api/v1/simulations").with(fromThisClient())
				.param("instrumentId", String.valueOf(id)).param("amount", "1000000").param("days", "30"));
	}

	/** Two quotes a day apart ending on the capture day: a class the ranking can evaluate. */
	private long seedFundClass(String name) {
		for (var quote : List.of(quote(name, CAPTURE.minusDays(1), "1000"), quote(name, CAPTURE, "1000.5"))) {
			fundWriter.write(List.of(quote), quote.date());
		}
		recordIngestionRun();
		return jdbc.sql("select id from instrument where kind = 'FUND_CLASS' and display_name = :name")
				.param("name", name).query(Long.class).single();
	}

	private static FundQuote quote(String name, LocalDate date, String vcp) {
		int separator = name.indexOf(" - ");
		return new FundQuote(ArgentinaDatosFundAdapter.SOURCE, name, name.substring(0, separator),
				name.substring(separator + 3), "corto", Segment.SAVER, date, new BigDecimal(vcp), null, null, FETCHED,
				Confidence.AGGREGATOR_AUTOMATIC, List.of());
	}
}
