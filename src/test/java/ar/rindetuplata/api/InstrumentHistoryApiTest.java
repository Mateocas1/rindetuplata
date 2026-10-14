package ar.rindetuplata.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.rindetuplata.source.plazofijo.ArgentinaDatosPlazoFijoAdapter;
import ar.rindetuplata.source.plazofijo.BankPlazoFijoRate;
import ar.rindetuplata.source.fund.ArgentinaDatosFundAdapter;
import ar.rindetuplata.source.fund.FundQuote;
import ar.rindetuplata.store.BankPlazoFijoWriter;
import ar.rindetuplata.store.FundQuoteWriter;
import ar.rindetuplata.store.InstrumentKind;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * T5b rules for {@code GET /api/v1/instruments/{id}/history}: the default window of the last 90 days ending today
 * in America/Argentina/Buenos_Aires, the maximum of 366 days, the rate snapshots of a rate instrument, the
 * TNA-equivalent between consecutive fund quotes (null when the vcp fell) and the INDEC monthly inflation series
 * (variable 27) labeled {@code INDEC_CPI_MONTHLY}.
 */
class InstrumentHistoryApiTest extends ApiIntegrationTest {

	private static final String PROVINCIA = "BANCO DE LA PROVINCIA DE BUENOS AIRES";
	private static final String MERCADO_FONDO = "Mercado Fondo - Clase A";

	@Test
	void defaultWindowIsTheLastNinetyDaysEndingTodayInBuenosAires() throws Exception {
		seedAll();
		long id = bankInstrumentId(PROVINCIA);

		mockMvc.perform(get("/api/v1/instruments/{id}/history", id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(String.valueOf(id)))
				.andExpect(jsonPath("$.kind").value("PLAZO_FIJO_BANK"))
				.andExpect(jsonPath("$.displayName").value(PROVINCIA))
				.andExpect(jsonPath("$.sourceId").value("argentinadatos-plazo-fijo"))
				.andExpect(jsonPath("$.from").value("2026-07-23"))
				.andExpect(jsonPath("$.to").value("2026-10-20"))
				.andExpect(jsonPath("$.quotes", hasSize(0)));
	}

	@Test
	void rateInstrumentHistoryReturnsOnePointPerCaptureDate() throws Exception {
		seedAll();
		long id = bankInstrumentId(PROVINCIA);

		mockMvc.perform(get("/api/v1/instruments/{id}/history", id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.points", hasSize(1)))
				.andExpect(jsonPath("$.points[0].captureDate").value("2026-10-14"))
				.andExpect(jsonPath("$.points[0].dataDate").value("2026-10-14"))
				.andExpect(jsonPath("$.points[0].basis").value("TNA"))
				.andExpect(jsonPath("$.points[0].rate").value(0.22))
				.andExpect(jsonPath("$.points[0].clientRate").value(0.21))
				.andExpect(jsonPath("$.points[0].invalid").value(false))
				.andExpect(jsonPath("$.points[0].flags", hasSize(0)))
				.andExpect(jsonPath("$.monthlyInflation", hasSize(3)))
				.andExpect(jsonPath("$.monthlyInflation[0].index").value("INDEC_CPI_MONTHLY"))
				.andExpect(jsonPath("$.monthlyInflation[0].observationDate").value("2026-07-31"))
				.andExpect(jsonPath("$.monthlyInflation[0].value").value(0.021))
				.andExpect(jsonPath("$.monthlyInflation[0].confidence").value("OFFICIAL_AUTOMATIC"))
				.andExpect(jsonPath("$.monthlyInflation[0].sourceId").value("bcra-estadisticas-monetarias"))
				.andExpect(jsonPath("$.monthlyInflation[2].observationDate").value("2026-09-30"))
				.andExpect(jsonPath("$.monthlyInflation[2].value").value(0.019))
				.andExpect(content().string(containsString("\"rate\":0.220000")))
				.andExpect(content().string(containsString("\"clientRate\":0.210000")))
				.andExpect(content().string(containsString("\"value\":0.021000")));
	}

	@Test
	void fundHistoryReturnsQuotesWithTheTnaEquivalentBetweenConsecutiveQuotes() throws Exception {
		seedAll();
		long id = reader.instrument(InstrumentKind.FUND_CLASS, ArgentinaDatosFundAdapter.SOURCE.id(),
				FundQuoteWriter.naturalKey(mercadoFondoQuote())).orElseThrow().id();

		mockMvc.perform(get("/api/v1/instruments/{id}/history", id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.kind").value("FUND_CLASS"))
				.andExpect(jsonPath("$.points", hasSize(0)))
				// 2026-10-08 has no earlier quote in the store: no TNA-equivalent.
				.andExpect(jsonPath("$.quotes", hasSize(2)))
				.andExpect(jsonPath("$.quotes[0].quoteDate").value("2026-10-08"))
				.andExpect(jsonPath("$.quotes[0].vcp").value(25923.77))
				.andExpect(jsonPath("$.quotes[0].tnaEquivalent").value(org.hamcrest.Matchers.nullValue()))
				// (25937.047 / 25923.768 - 1) * 365 = 0.18696491189089487...
				.andExpect(jsonPath("$.quotes[1].quoteDate").value("2026-10-09"))
				.andExpect(jsonPath("$.quotes[1].vcp").value(25937.05))
				.andExpect(jsonPath("$.quotes[1].tnaEquivalent").value(0.186965))
				.andExpect(content().string(containsString("\"vcp\":25937.05")))
				.andExpect(content().string(containsString("\"tnaEquivalent\":0.186965")));
	}

	@Test
	void fundQuotesUseThePreviousQuoteAsBaseEvenWhenItFallsOutsideTheRange() throws Exception {
		seedAll();
		long id = reader.instrument(InstrumentKind.FUND_CLASS, ArgentinaDatosFundAdapter.SOURCE.id(),
				FundQuoteWriter.naturalKey(mercadoFondoQuote())).orElseThrow().id();

		mockMvc.perform(get("/api/v1/instruments/{id}/history", id).param("from", "2026-10-09")
				.param("to", "2026-10-09"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.from").value("2026-10-09"))
				.andExpect(jsonPath("$.to").value("2026-10-09"))
				.andExpect(jsonPath("$.quotes", hasSize(1)))
				.andExpect(jsonPath("$.quotes[0].quoteDate").value("2026-10-09"))
				.andExpect(jsonPath("$.quotes[0].tnaEquivalent").value(0.186965));
	}

	@Test
	void aFallingVcpHasNoTnaEquivalent() throws Exception {
		seedAll();
		long id = reader.instrument(InstrumentKind.FUND_CLASS, ArgentinaDatosFundAdapter.SOURCE.id(),
				ApiFixtures.FALLEN_FUND).orElseThrow().id();

		mockMvc.perform(get("/api/v1/instruments/{id}/history", id))
				.andExpect(status().isOk())
				// The INVALID 2026-10-09 quote is stored for audit but is not part of the series: three quotes.
				.andExpect(jsonPath("$.quotes", hasSize(3)))
				.andExpect(jsonPath("$.quotes[0].quoteDate").value("2026-10-06"))
				.andExpect(jsonPath("$.quotes[0].vcp").value(100.00))
				.andExpect(jsonPath("$.quotes[0].tnaEquivalent").value(org.hamcrest.Matchers.nullValue()))
				// (100.500 / 100.000 - 1) * 365 = 1.825 exactly.
				.andExpect(jsonPath("$.quotes[1].quoteDate").value("2026-10-07"))
				.andExpect(jsonPath("$.quotes[1].tnaEquivalent").value(1.825))
				.andExpect(jsonPath("$.quotes[2].quoteDate").value("2026-10-08"))
				.andExpect(jsonPath("$.quotes[2].vcp").value(100.00))
				.andExpect(jsonPath("$.quotes[2].tnaEquivalent").value(org.hamcrest.Matchers.nullValue()));
	}

	@Test
	void aRangeOf366DaysIsAccepted() throws Exception {
		seedAll();

		mockMvc.perform(get("/api/v1/instruments/{id}/history", bankInstrumentId(PROVINCIA))
				.param("from", "2025-10-20").param("to", "2026-10-20"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.from").value("2025-10-20"))
				.andExpect(jsonPath("$.to").value("2026-10-20"));
	}

	private long bankInstrumentId(String bankName) {
		BankPlazoFijoRate rate = ApiFixtures.banks().stream().filter(bank -> bankName.equals(bank.bankName()))
				.findFirst().orElseThrow();
		return reader.instrument(InstrumentKind.PLAZO_FIJO_BANK, ArgentinaDatosPlazoFijoAdapter.SOURCE.id(),
				BankPlazoFijoWriter.naturalKey(rate)).orElseThrow().id();
	}

	private FundQuote mercadoFondoQuote() {
		return ApiFixtures.fundQuotes("argentinadatos/fci-mercado-dinero-ultimo.json").stream()
				.filter(quote -> MERCADO_FONDO.equals(quote.name())).findFirst().orElseThrow();
	}
}
