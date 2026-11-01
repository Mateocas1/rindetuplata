package ar.rindetuplata.api;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.rindetuplata.source.Confidence;
import ar.rindetuplata.source.Segment;
import ar.rindetuplata.source.fund.ArgentinaDatosFundAdapter;
import ar.rindetuplata.source.fund.FundQuote;
import ar.rindetuplata.store.InstrumentKind;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * T5a on the wire (S5, L24): Personal Pay, the wallet of "Delta Pesos - Clase X", appears as a WALLET once, can be
 * simulated through its class id, and reads FUND_CLASS_NOT_FOUND when the class is absent. Quotes are synthetic.
 */
class FciWalletsApiTest extends RankingApiIntegrationTest {

	@Test
	void personalPayShowsAsAWalletOnceAndItsClassIsNotAlsoAFund() throws Exception {
		seedFund("Delta Pesos - Clase X", "1000.5");
		seedFund("Fondo Uno - Clase A", "1000.4");

		mockMvc.perform(get("/api/v1/ranking").param("kind", "WALLET").param("kind", "FUND"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.ranked[*].name").value(contains("Personal Pay", "Fondo Uno - Clase A")))
				.andExpect(jsonPath("$.ranked[0].kind").value("WALLET"))
				.andExpect(jsonPath("$.ranked[0].entity").value("Delta Pesos - Clase X"))
				.andExpect(jsonPath("$.ranked[0].rateUsed.kind").value("FUND_TNA_EQUIVALENT"))
				.andExpect(jsonPath("$.ranked[0].rateUsed.fraction").value(0.1825))
				.andExpect(jsonPath("$.ranked[?(@.name=='Delta Pesos - Clase X')]").isEmpty());
	}

	@Test
	void personalPayIsSimulatedThroughItsClassId() throws Exception {
		seedFund("Delta Pesos - Clase X", "1000.5");
		long classId = reader.instrument(InstrumentKind.FUND_CLASS,
				ArgentinaDatosFundAdapter.SOURCE.id(), "Delta Pesos - Clase X").orElseThrow().id();

		mockMvc.perform(get("/api/v1/simulations")
				.param("instrumentId", String.valueOf(classId))
				.param("amount", "1000000")
				.param("days", "30"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("SIMULATED"))
				.andExpect(jsonPath("$.entry.kind").value("WALLET"))
				.andExpect(jsonPath("$.entry.name").value("Personal Pay"))
				.andExpect(jsonPath("$.entry.entity").value("Delta Pesos - Clase X"))
				.andExpect(jsonPath("$.entry.finalAmount").value(1015109.26));
	}

	@Test
	void personalPayReadsFundClassNotFoundWhenItsClassIsAbsent() throws Exception {
		seedFund("Fondo Uno - Clase A", "1000.4");

		mockMvc.perform(get("/api/v1/ranking").param("kind", "WALLET").param("kind", "FUND"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.excluded[?(@.id=='personal-pay')].reason")
						.value(contains("FUND_CLASS_NOT_FOUND")))
				.andExpect(jsonPath("$.ranked[*].name").value(not(hasItem("Personal Pay"))));
	}

	private void seedFund(String name, String latestVcp) {
		write(name, CAPTURE.minusDays(1), "1000");
		write(name, CAPTURE, latestVcp);
	}

	private void write(String name, LocalDate date, String vcp) {
		int separator = name.indexOf(" - ");
		fundWriter.write(List.of(new FundQuote(ArgentinaDatosFundAdapter.SOURCE, name, name.substring(0, separator),
				name.substring(separator + 3), "corto", Segment.SAVER, date, new BigDecimal(vcp), null, null,
				FETCHED, Confidence.AGGREGATOR_AUTOMATIC, List.of())), date);
		recordIngestionRun();
	}
}
