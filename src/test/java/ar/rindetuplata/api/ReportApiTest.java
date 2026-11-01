package ar.rindetuplata.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.rindetuplata.source.crypto.ArgentinaDatosCryptoAdapter;
import ar.rindetuplata.source.fund.ArgentinaDatosFundAdapter;
import ar.rindetuplata.source.plazofijo.ArgentinaDatosPlazoFijoAdapter;
import ar.rindetuplata.source.wallet.UalaWalletAdapter;
import ar.rindetuplata.store.InstrumentKind;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * T6a, L29 a: {@code GET /api/v1/reports/monthly} and {@code /api/v1/reports/monthly/{yyyy-MM}} over a September
 * 2026 history built by hand (the frozen clock says 2026-10-20, so September is the last closed month). Each kind has
 * a worked example: a wallet at a constant and at a changing TNA, a crypto APY, a plazo fijo locked at its first
 * capture, funds as the vcp ratio of two month ends, an FCI-backed wallet, and one instrument per way of being left
 * out. INDEC September is 1.9% (the recorded BCRA fixture).
 */
class ReportApiTest extends ApiIntegrationTest {

	private static final String SEPTEMBER = "/api/v1/reports/monthly/2026-09";
	private static final String UALA = UalaWalletAdapter.SOURCE.id();
	private static final String BANKS = ArgentinaDatosPlazoFijoAdapter.SOURCE.id();
	private static final String CRYPTO = ArgentinaDatosCryptoAdapter.SOURCE.id();
	private static final String FUNDS = ArgentinaDatosFundAdapter.SOURCE.id();

	/**
	 * The month list and every month report are cached by the data version (T4a, L33 a), and the tests seed the tables
	 * directly, so each test starts from a version no other test has used: a rolled-back test never leaves an answer
	 * that a later one with the same row counts could hit.
	 */
	@BeforeEach
	void startFromAFreshDataVersion() {
		recordIngestionRun();
	}

	// --- the month list -------------------------------------------------------------------------------------------

	@Test
	void aCachedMonthListIsServedUntilTheDataVersionMovesAndThenShowsTheNewHistory() throws Exception {
		seedSeptember();
		recordIngestionRun();
		mockMvc.perform(get("/api/v1/reports/monthly"))
				.andExpect(jsonPath("$.months", hasSize(1)))
				.andExpect(jsonPath("$.months[0].month").value("2026-09"));

		// INDEC September disappears behind the cache's back: no writer recorded a run, so the version is the same
		// and the cached list is still served (the cache is by version, not by content).
		jdbc.sql("delete from macro_observation where observation_date = date '2026-09-30'").update();
		mockMvc.perform(get("/api/v1/reports/monthly")).andExpect(jsonPath("$.months", hasSize(1)));

		// A write that records a source run (the daily job, a backfill) moves the version: the next call recomputes.
		recordIngestionRun();
		mockMvc.perform(get("/api/v1/reports/monthly")).andExpect(jsonPath("$.months", hasSize(0)));
	}

	@Test
	void aCachedMonthReportIsIdenticalToTheFreshOneAndRecomputedAfterAWrite() throws Exception {
		seedSeptember();
		recordIngestionRun();
		String fresh = mockMvc.perform(get(SEPTEMBER)).andExpect(status().isOk()).andReturn().getResponse()
				.getContentAsString();

		String cached = mockMvc.perform(get(SEPTEMBER)).andExpect(status().isOk()).andReturn().getResponse()
				.getContentAsString();

		assertThat(cached).isEqualTo(fresh);

		long extra = instrument(InstrumentKind.WALLET, UALA, "extra", "Extra Wallet", "Extra Entity");
		rates(extra, "2026-08-29", "2026-09-30", "TNA", "0.365", null);
		recordIngestionRun();

		mockMvc.perform(get(SEPTEMBER)).andExpect(status().isOk())
				.andExpect(jsonPath("$.rows[?(@.name=='Extra Wallet')]", hasSize(1)));
	}

	@Test
	void withNoStoredHistoryThereAreNoMonths() throws Exception {
		mockMvc.perform(get("/api/v1/reports/monthly"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith("application/json"))
				.andExpect(jsonPath("$.months", hasSize(0)));
	}

	@Test
	void onlyAClosedMonthWithInflationAndCoverageIsListed() throws Exception {
		seedSeptember();

		// INDEC has May to September (fixture), and an October observation that must not count: October
		// is still open on the frozen 2026-10-20. August and July have no stored history.
		insertInflation("2026-10-31", "0.020");

		mockMvc.perform(get("/api/v1/reports/monthly"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.months", hasSize(1)))
				.andExpect(jsonPath("$.months[0].month").value("2026-09"))
				.andExpect(jsonPath("$.months[0].monthlyInflation").value(0.019))
				.andExpect(jsonPath("$.months[0].ranked").value(7))
				.andExpect(jsonPath("$.months[0].beatInflation").value(5))
				.andExpect(content().string(containsString("\"monthlyInflation\":0.019000")));
	}

	@Test
	void monthsAreListedNewestFirst() throws Exception {
		seedSeptember();
		// A wallet captured since July 29 can be measured over August as well.
		long longWallet = instrument(InstrumentKind.WALLET, UALA, "long", "Long Wallet", "Long Entity");
		rates(longWallet, "2026-07-29", "2026-09-30", "TNA", "0.365", null);

		mockMvc.perform(get("/api/v1/reports/monthly"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.months[*].month", contains("2026-09", "2026-08")))
				.andExpect(jsonPath("$.months[1].monthlyInflation").value(0.017))
				.andExpect(jsonPath("$.months[1].ranked").value(1));
		// August: 31 days at 0.1% a day = 1.001^31 - 1 = 0.031470, against INDEC 1.7%.
		mockMvc.perform(get("/api/v1/reports/monthly/2026-08"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.rows", hasSize(1)))
				.andExpect(jsonPath("$.rows[0].nominal").value(0.03147))
				.andExpect(jsonPath("$.rows[0].coverage.total").value(31));
	}

	// --- the month report: rows -----------------------------------------------------------------------------------

	@Test
	void theReportStatesTheMonthItsInflationAndItsSource() throws Exception {
		seedSeptember();

		mockMvc.perform(get(SEPTEMBER))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.month").value("2026-09"))
				.andExpect(jsonPath("$.from").value("2026-09-01"))
				.andExpect(jsonPath("$.to").value("2026-09-30"))
				.andExpect(jsonPath("$.inflation.index").value("INDEC_CPI_MONTHLY"))
				.andExpect(jsonPath("$.inflation.value").value(0.019))
				.andExpect(jsonPath("$.inflation.observationDate").value("2026-09-30"))
				.andExpect(jsonPath("$.inflation.confidence").value("OFFICIAL_AUTOMATIC"))
				.andExpect(jsonPath("$.inflation.source.id").value("bcra-estadisticas-monetarias"))
				.andExpect(jsonPath("$.inflation.source.official").value(true))
				.andExpect(jsonPath("$.totals.ranked").value(7))
				.andExpect(jsonPath("$.totals.beatInflation").value(5))
				.andExpect(jsonPath("$.totals.leftOut").value(10));
	}

	@Test
	void rowsAreSortedByRealReturnWithTheirNominalRealCoverageAndVerdict() throws Exception {
		seedSeptember();

		mockMvc.perform(get(SEPTEMBER))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.rows[*].name", contains("Mixed Wallet", "Crypto Savings", "Alpha Wallet",
						"Fondo Uno - Clase A", "Banco Uno", "Personal Pay", "Fondo Dos - Clase A")))
				// Wallet at a changing TNA: 10 days at 0.1% a day, 20 days at 0.2%: 1.001^10 * 1.002^20 - 1.
				.andExpect(jsonPath("$.rows[0].kind").value("WALLET"))
				.andExpect(jsonPath("$.rows[0].nominal").value(0.051224))
				.andExpect(jsonPath("$.rows[0].real").value(0.031623))
				.andExpect(jsonPath("$.rows[0].beatInflation").value(true))
				.andExpect(jsonPath("$.rows[0].coverage.days").value(30))
				.andExpect(jsonPath("$.rows[0].coverage.total").value(30))
				// Crypto APY 50% for 30 days: 1.5^(30/365) - 1.
				.andExpect(jsonPath("$.rows[1].kind").value("CRYPTO"))
				.andExpect(jsonPath("$.rows[1].nominal").value(0.033887))
				.andExpect(jsonPath("$.rows[1].real").value(0.01461))
				// Wallet at a constant TNA of 36.5%: 1.001^30 - 1; real = 1.030439 / 1.019 - 1.
				.andExpect(jsonPath("$.rows[2].nominal").value(0.030439))
				.andExpect(jsonPath("$.rows[2].real").value(0.011226))
				// Fund: 102.5 / 100 - 1, over the 30 days from the August close to the September close.
				.andExpect(jsonPath("$.rows[3].kind").value("FUND"))
				.andExpect(jsonPath("$.rows[3].nominal").value(0.025))
				.andExpect(jsonPath("$.rows[3].real").value(0.005888))
				.andExpect(jsonPath("$.rows[3].coverage.days").value(30))
				// Plazo fijo: the first capture (30%) locked for the 30 days: 0.30 * 30 / 365; the later 50% is ignored.
				.andExpect(jsonPath("$.rows[4].kind").value("PLAZO_FIJO"))
				.andExpect(jsonPath("$.rows[4].nominal").value(0.024658))
				.andExpect(jsonPath("$.rows[4].real").value(0.005552))
				.andExpect(jsonPath("$.rows[4].beatInflation").value(true))
				// Below inflation: negative real return, no win.
				.andExpect(jsonPath("$.rows[5].nominal").value(0.015))
				.andExpect(jsonPath("$.rows[5].real").value(-0.003925))
				.andExpect(jsonPath("$.rows[5].beatInflation").value(false))
				.andExpect(jsonPath("$.rows[6].beatInflation").value(false))
				.andExpect(content().string(containsString("\"nominal\":0.051224")))
				.andExpect(content().string(containsString("\"real\":-0.003925")));
	}

	@Test
	void everyRowStatesItsEntityAndItsSource() throws Exception {
		seedSeptember();

		mockMvc.perform(get(SEPTEMBER))
				.andExpect(jsonPath("$.rows[0].id").isString())
				.andExpect(jsonPath("$.rows[0].entity").value("Mixed Entity"))
				.andExpect(jsonPath("$.rows[0].source.id").value(UALA))
				.andExpect(jsonPath("$.rows[4].entity").value("Banco Uno"))
				.andExpect(jsonPath("$.rows[4].source.id").value(BANKS));
	}

	@Test
	void fundClassesOfOneFundAreGroupedToTheBestClass() throws Exception {
		seedSeptember();

		// Class A returned 2.5% and class B 2.0%: one row, the best class, counting the folded sibling.
		mockMvc.perform(get(SEPTEMBER))
				.andExpect(jsonPath("$.rows[?(@.name=='Fondo Uno - Clase A')].foldedClasses", contains(1)))
				.andExpect(jsonPath("$.rows[?(@.name=='Fondo Uno - Clase B')]", hasSize(0)))
				.andExpect(jsonPath("$.rows[?(@.name=='Fondo Dos - Clase A')].foldedClasses", contains(0)))
				.andExpect(jsonPath("$.leftOut[?(@.name=='Fondo Uno - Clase B')]", hasSize(0)));
	}

	@Test
	void anFciBackedWalletIsOneWalletRowAndItsClassIsNotListedAgainAsAFund() throws Exception {
		seedSeptember();

		// "Delta Pesos - Clase X" is the class behind Personal Pay (101.5 / 100 - 1 = 1.5%).
		mockMvc.perform(get(SEPTEMBER))
				.andExpect(jsonPath("$.rows[?(@.name=='Personal Pay')].kind", contains("WALLET")))
				.andExpect(jsonPath("$.rows[?(@.name=='Personal Pay')].nominal", contains(0.015)))
				.andExpect(jsonPath("$.rows[?(@.name=='Delta Pesos - Clase X')]", hasSize(0)))
				.andExpect(jsonPath("$.leftOut[?(@.name=='Delta Pesos - Clase X')]", hasSize(0)));
	}

	@Test
	void aCryptoYieldInAForeignCurrencyIsNotPartOfThePesoReport() throws Exception {
		seedSeptember();

		mockMvc.perform(get(SEPTEMBER))
				.andExpect(jsonPath("$.rows[?(@.name=='Crypto Dollars')]", hasSize(0)))
				.andExpect(jsonPath("$.leftOut[?(@.name=='Crypto Dollars')]", hasSize(0)));
	}

	// --- the month report: instruments left out -------------------------------------------------------------------

	@Test
	void everyInstrumentLeftOutSaysWhy() throws Exception {
		seedSeptember();

		mockMvc.perform(get(SEPTEMBER))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.leftOut", hasSize(10)))
				// More than 3 days without a capture (9-11 to 9-19).
				.andExpect(leftOut("Gappy Wallet", "WALLET", "GAP_IN_CAPTURES"))
				// First seen on 9-14: no rate on 9-01.
				.andExpect(leftOut("Late Wallet", "WALLET", "NOT_CAPTURED_AT_START"))
				// First capture on 9-08, after the first business day (Tuesday 9-01).
				.andExpect(leftOut("Banco Tarde", "PLAZO_FIJO", "NOT_CAPTURED_AT_START"))
				.andExpect(leftOut("Banco Sin Tasa", "PLAZO_FIJO", "NOT_OFFERED_TO_NON_CLIENTS"))
				.andExpect(leftOut("Banco Invalido", "PLAZO_FIJO", "INVALID_DATA"))
				// Last August quote on 8-20: more than 4 days before the August close.
				.andExpect(leftOut("Fondo Cuatro - Clase A", "FUND", "MISSING_BASE_QUOTE"))
				// Last September quote on 9-20: more than 4 days before the September close.
				.andExpect(leftOut("Fondo Tres - Clase A", "FUND", "MISSING_END_QUOTE"))
				.andExpect(leftOut("Fondo Dolar USD - Clase A", "FUND", "FOREIGN_CURRENCY"))
				.andExpect(leftOut("Fondo Amnistia - Clase L Ley N° 27.743", "FUND", "RESTRICTED_CLASS"))
				// A mapped wallet whose class is not in the fund quotes says so.
				.andExpect(leftOut("Mercado Pago", "WALLET", "FUND_CLASS_NOT_FOUND"))
				.andExpect(jsonPath("$.leftOut[0].id").isString())
				.andExpect(jsonPath("$.leftOut[?(@.name=='Gappy Wallet')].entity", contains("Gappy Entity")));
	}

	@Test
	void anInstrumentLeftOutIsNeverAlsoARankedRow() throws Exception {
		seedSeptember();

		mockMvc.perform(get(SEPTEMBER))
				.andExpect(jsonPath("$.rows[?(@.name=='Gappy Wallet')]", hasSize(0)))
				.andExpect(jsonPath("$.rows[?(@.name=='Banco Tarde')]", hasSize(0)))
				.andExpect(jsonPath("$.rows[?(@.name=='Fondo Tres - Clase A')]", hasSize(0)));
	}

	// --- errors ---------------------------------------------------------------------------------------------------

	@Test
	void aMonthWithoutInflationIsA404ThatSaysSo() throws Exception {
		seedSeptember();

		expectNotFound("/api/v1/reports/monthly/2025-12", "INDEC");
	}

	@Test
	void aMonthWithInflationButNoStoredHistoryIsA404ThatSaysSo() throws Exception {
		seedSeptember();

		// August has an INDEC observation, but the stored history starts on 2026-08-29.
		expectNotFound("/api/v1/reports/monthly/2026-08", "history");
	}

	@Test
	void anOpenMonthIsA404EvenWithAnInflationObservation() throws Exception {
		seedSeptember();
		insertInflation("2026-10-31", "0.020");

		expectNotFound("/api/v1/reports/monthly/2026-10", "closed");
	}

	@Test
	void aFutureMonthIsA404() throws Exception {
		seedSeptember();

		expectNotFound("/api/v1/reports/monthly/2027-01", "closed");
	}

	@Test
	void aMalformedMonthIsA400NamingTheParameter() throws Exception {
		for (String malformed : new String[] { "2026-13", "2026-9", "septiembre", "2026-09-01", "26-09", "2026-00" }) {
			mockMvc.perform(get("/api/v1/reports/monthly/{month}", malformed))
					.andExpect(status().isBadRequest())
					.andExpect(content().contentTypeCompatibleWith("application/problem+json"))
					.andExpect(jsonPath("$.status").value(400))
					.andExpect(jsonPath("$.parameter").value("month"))
					.andExpect(jsonPath("$.detail").value(containsString(malformed)));
		}
	}

	// --- the surface ----------------------------------------------------------------------------------------------

	@Test
	void anonymousReadsAreOpenAndEveryOtherMethodIsRefused() throws Exception {
		seedSeptember();

		mockMvc.perform(get("/api/v1/reports/monthly")).andExpect(status().isOk());
		mockMvc.perform(get(SEPTEMBER)).andExpect(status().isOk());
		for (MockHttpServletRequestBuilder write : new MockHttpServletRequestBuilder[] {
				post("/api/v1/reports/monthly"), put("/api/v1/reports/monthly"), delete("/api/v1/reports/monthly"),
				post(SEPTEMBER), delete(SEPTEMBER) }) {
			mockMvc.perform(write)
					.andExpect(status().isForbidden())
					.andExpect(content().contentTypeCompatibleWith("application/problem+json"))
					.andExpect(jsonPath("$.status").value(403));
		}
	}

	@Test
	void theReportsArePublishedInTheOpenApiDocumentWithTheirRules() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.paths['/api/v1/reports/monthly'].get.description")
						.value(containsString("closed month")))
				.andExpect(jsonPath("$.paths['/api/v1/reports/monthly/{month}'].get.description")
						.value(containsString("(1 + nominal) / (1 + inflation) - 1")))
				.andExpect(jsonPath("$.paths['/api/v1/reports/monthly/{month}'].get.responses['404']").exists())
				.andExpect(jsonPath("$.paths['/api/v1/reports/monthly/{month}'].get.responses['400']").exists())
				.andExpect(jsonPath("$.paths['/api/v1/reports/monthly/{month}'].get.parameters[0].description")
						.value(containsString("yyyy-MM")));
	}

	@Test
	void readingAReportChangesNothingOfTheOtherEndpoints() throws Exception {
		seedAll();
		String instrumentsBefore = mockMvc.perform(get("/api/v1/instruments")).andReturn().getResponse()
				.getContentAsString();
		String rankingBefore = mockMvc.perform(get("/api/v1/ranking")).andReturn().getResponse().getContentAsString();

		mockMvc.perform(get("/api/v1/reports/monthly")).andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/reports/monthly/2026-09")).andExpect(status().isNotFound());

		mockMvc.perform(get("/api/v1/instruments")).andExpect(status().isOk())
				.andExpect(content().string(instrumentsBefore));
		mockMvc.perform(get("/api/v1/ranking")).andExpect(status().isOk())
				.andExpect(content().string(rankingBefore))
				.andExpect(jsonPath("$.months").doesNotExist());
	}

	// --- seeding --------------------------------------------------------------------------------------------------

	private ResultActions expectNotFound(String path, String detailPart) throws Exception {
		return mockMvc.perform(get(path))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith("application/problem+json"))
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.parameter").value("month"))
				.andExpect(jsonPath("$.detail").value(containsString(detailPart)));
	}

	private static org.springframework.test.web.servlet.ResultMatcher leftOut(String name, String kind,
			String reason) {
		return jsonPath("$.leftOut[?(@.name=='" + name + "' && @.kind=='" + kind + "')].reason", contains(reason));
	}

	/**
	 * September 2026 (30 days, Tuesday the 1st), with July to September INDEC from the recorded fixture:
	 * seven instruments the report measures and ten it leaves out, each for a different reason.
	 */
	private void seedSeptember() {
		macroWriter.write(ApiFixtures.monthlyInflation(), ApiFixtures.CAPTURE);

		long alpha = instrument(InstrumentKind.WALLET, UALA, "alpha", "Alpha Wallet", "Alpha Entity");
		rates(alpha, "2026-08-29", "2026-09-30", "TNA", "0.365", null);

		long mixed = instrument(InstrumentKind.WALLET, UALA, "mixed", "Mixed Wallet", "Mixed Entity");
		rates(mixed, "2026-08-29", "2026-09-10", "TNA", "0.365", null);
		rates(mixed, "2026-09-11", "2026-09-30", "TNA", "0.73", null);

		long gappy = instrument(InstrumentKind.WALLET, UALA, "gappy", "Gappy Wallet", "Gappy Entity");
		rates(gappy, "2026-08-29", "2026-09-10", "TNA", "0.365", null);
		rates(gappy, "2026-09-20", "2026-09-30", "TNA", "0.365", null);

		long late = instrument(InstrumentKind.WALLET, UALA, "late", "Late Wallet", "Late Entity");
		rates(late, "2026-09-14", "2026-09-30", "TNA", "0.365", null);

		long crypto = instrument(InstrumentKind.CRYPTO_YIELD, CRYPTO, "crypto-ars", "Crypto Savings", "Crypto Co");
		rates(crypto, "2026-08-29", "2026-09-30", "APY", "0.5", "{\"currency\":\"ARS\"}");
		long dollars = instrument(InstrumentKind.CRYPTO_YIELD, CRYPTO, "crypto-usd", "Crypto Dollars", "Crypto Co");
		rates(dollars, "2026-08-29", "2026-09-30", "APY", "0.05", "{\"currency\":\"USD\"}");

		long uno = instrument(InstrumentKind.PLAZO_FIJO_BANK, BANKS, "uno", "Banco Uno", "Banco Uno");
		snapshot(uno, "2026-09-01", "TNA", "0.30", false, null);
		snapshot(uno, "2026-09-15", "TNA", "0.50", false, null);
		long tarde = instrument(InstrumentKind.PLAZO_FIJO_BANK, BANKS, "tarde", "Banco Tarde", "Banco Tarde");
		snapshot(tarde, "2026-09-08", "TNA", "0.30", false, null);
		long sinTasa = instrument(InstrumentKind.PLAZO_FIJO_BANK, BANKS, "sin-tasa", "Banco Sin Tasa", "Banco Sin Tasa");
		snapshot(sinTasa, "2026-09-01", "TNA", null, false, null);
		long invalido = instrument(InstrumentKind.PLAZO_FIJO_BANK, BANKS, "invalido", "Banco Invalido", "Banco Invalido");
		snapshot(invalido, "2026-09-01", "TNA", "0.30", true, null);

		fund("Fondo Uno - Clase A", "100.000", "102.500");
		fund("Fondo Uno - Clase B", "100.000", "102.000");
		fund("Fondo Dos - Clase A", "100.000", "101.000");
		long tres = fundClass("Fondo Tres - Clase A");
		quote(tres, "2026-08-31", "100.000");
		quote(tres, "2026-09-20", "101.000");
		long cuatro = fundClass("Fondo Cuatro - Clase A");
		quote(cuatro, "2026-08-20", "100.000");
		quote(cuatro, "2026-09-30", "101.000");
		fund("Fondo Dolar USD - Clase A", "100.000", "101.000");
		fund("Fondo Amnistia - Clase L Ley N° 27.743", "100.000", "101.000");
		fund("Delta Pesos - Clase X", "100.000", "101.500");
	}

	private void fund(String name, String august, String september) {
		long id = fundClass(name);
		quote(id, "2026-08-31", august);
		quote(id, "2026-09-30", september);
	}

	private long fundClass(String name) {
		return instrument(InstrumentKind.FUND_CLASS, FUNDS, name, name, name.substring(0, name.indexOf(" - ")));
	}

	private long instrument(InstrumentKind kind, String sourceId, String key, String name, String entity) {
		return jdbc.sql("""
				insert into instrument (kind, source_id, natural_key, display_name, entity_name, segment, first_seen,
				                        last_seen)
				values (:kind, :source, :key, :name, :entity, 'SAVER', date '2026-08-29', date '2026-09-30')
				returning id
				""")
				.param("kind", kind.name()).param("source", sourceId).param("key", key).param("name", name)
				.param("entity", entity)
				.query(Long.class).single();
	}

	/** One snapshot on every day of the range. */
	private void rates(long id, String first, String last, String basis, String rate, String details) {
		for (LocalDate day = LocalDate.parse(first); !day.isAfter(LocalDate.parse(last)); day = day.plusDays(1)) {
			snapshot(id, day.toString(), basis, rate, false, details);
		}
	}

	private void snapshot(long id, String date, String basis, String rate, boolean invalid, String details) {
		jdbc.sql("""
				insert into rate_snapshot (instrument_id, capture_date, data_date, basis, rate, client_rate, details,
				                           confidence, fetched_at, flags, invalid)
				values (:id, cast(:date as date), cast(:date as date), :basis, cast(:rate as numeric),
				        case when cast(:rate as numeric) is null then 0.20 end, cast(:details as jsonb), 'AGGREGATOR_AUTOMATIC',
				        timestamptz '2026-10-14 19:06:23+00', cast('[]' as jsonb), :invalid)
				""")
				.param("id", id).param("date", date).param("basis", basis).param("rate", rate)
				.param("details", details).param("invalid", invalid)
				.update();
	}

	private void quote(long id, String date, String vcp) {
		jdbc.sql("""
				insert into fund_quote (instrument_id, quote_date, vcp, fetched_at, confidence, flags, invalid)
				values (:id, cast(:date as date), cast(:vcp as numeric), timestamptz '2026-10-14 19:06:23+00',
				        'AGGREGATOR_AUTOMATIC', cast('[]' as jsonb), false)
				""")
				.param("id", id).param("date", date).param("vcp", vcp)
				.update();
	}

	private void insertInflation(String date, String value) {
		jdbc.sql("""
				insert into macro_observation (variable, observation_date, source_id, value, fetched_at, confidence,
				                               flags)
				values ('MONTHLY_INFLATION', cast(:date as date), 'bcra-estadisticas-monetarias',
				        cast(:value as numeric), timestamptz '2026-10-14 19:06:23+00', 'OFFICIAL_AUTOMATIC',
				        cast('[]' as jsonb))
				""")
				.param("date", date).param("value", value)
				.update();
	}
}
